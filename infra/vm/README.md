# VM: dev and prod environments (DEP-02)

One Ubuntu VM runs two isolated Soukly environments, `dev` and `prod`, behind one Caddy that serves both in HTTPS. This page covers creating the VM,
preparing it, the first launch, switching to the real domain and day-to-day operations. Backups
and restores are covered in [`infra/backup/README.md`](../backup/README.md). New versions are
deployed by the `deploy-dev` workflow (section 7) and `deploy-prod` (section 8).

```
                      Internet (22, 80, 443 only)
                                  │
            ┌─────────────────────┴──────────────────────┐
            │ soukly-edge: Caddy (80, 443), Let's Encrypt│
            └──────┬────────────────────────────┬────────┘
     soukly-edge-dev (172.30.10.0/24)   soukly-edge-prod (172.30.20.0/24)
   ┌───────────────┴──────────────┐   ┌─────────┴────────────────────┐
   │ soukly-dev                   │   │ soukly-prod                  │
   │ gateway, frontend, garage    │   │ gateway, frontend            │
   │ ─ internal network ─         │   │ ─ internal network ─         │
   │ postgres, redpanda, services,│   │ postgres, redpanda, services │
   │ mailpit                      │   │ (photos: Object Storage)     │
   └──────────────────────────────┘   └──────────────────────────────┘
```

| Path | Content |
|---|---|
| `infra/vm/setup.sh` | Idempotent preparation of the VM (run with `sudo`) |
| `infra/vm/gen-jwt-keys.sh` | RSA key pair of `user-service` for one environment |
| `infra/vm/deploy.sh` | Deployment of one commit to one environment, with automatic return (section 7) |
| `infra/edge/` | Shared Caddy: `compose.yml`, `Caddyfile`, one site file per environment, `.env.example` |
| `infra/compose/` | One environment: `compose.yml` (dev and prod), `compose.dev.yml` (Garage, Mailpit, lower limits), `.env.example` |
| `infra/backup/` | Nightly backup of the prod databases and the restore procedure |

On the VM, settings and secrets live outside the repository, readable by `deploy` only:

```
/opt/soukly/
├── src/       # checkout of the repository (owner deploy)
├── bin/deploy.sh               # installed by setup.sh (owner root)
├── edge/.env
├── dev/.env, dev/secrets/      # JWT keys of dev
├── dev/releases/<sha>/, dev/current, dev/active/, dev/deployments.log   # deploy-dev (section 7)
├── prod/.env, prod/secrets/    # JWT keys of prod
└── backups/backup.env
```

## 1. Create the VM

The default is **Oracle Cloud Always Free** (Ampere A1, arm64). Any other provider works the same
way, as long as it gives one server with **Ubuntu 24.04** (amd64 or arm64: the DEP-01 images exist
for both), a **fixed public IPv4** and a firewall in front of it. Size: about **4.3 GB** of memory
for dev alone, **9.5 GB** for dev and prod (section 6).

### Oracle Cloud (Always Free, default)

Always Free limits checked on 2026-10-16 in the
[Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
page: Ampere A1 Flex instances share **1,500 OCPU hours and 9,000 GB hours per month**, which
Oracle states as **2 OCPUs and 12 GB of memory** for Always Free tenancies (the ticket assumed 4
and 24), and **200 GB** of block volume (boot volumes included). Check the page again on the day
you create the VM, because Oracle changes these limits.

1. **Compute > Instances > Create instance**:
   - image: **Canonical Ubuntu 24.04** (the aarch64 build is selected with the shape);
   - shape: **Ampere > VM.Standard.A1.Flex**, **2 OCPUs, 12 GB** of memory;
   - networking: a new VCN with a public subnet, and **Assign a public IPv4 address**;
   - SSH keys: upload **your own** public key (your admin account, not the deploy key);
   - boot volume: **100 GB** (below the 200 GB limit).
2. If the console answers **"Out of capacity"**, retry later, pick another availability domain
   in the same form, or create the instance with a smaller shape and resize it later
   (**Edit > Shape**). The home region cannot be changed afterwards.
3. **Reserved public IP**: **Networking > IP management > Reserved public IPs > Reserve**, then
   on the instance, **Attached VNICs > IPv4 addresses > Edit**: switch from "Ephemeral" to the
   reserved IP. The dev address and the prod DNS records depend on it.
4. **Security list** of the subnet (**Networking > Virtual cloud networks > subnet > Security
   list**): keep only these ingress rules, from `0.0.0.0/0`, TCP: **22**, **80**, **443**.
   Add **UDP 443** if you want HTTP/3 (optional). Remove any other ingress rule.

### Hetzner Cloud (paid alternative)

1. **Console > Projects > New project** `soukly`.
2. **Security > SSH keys > Add SSH key**: paste **your own** public key (your admin account, not
   the deploy key).
3. **Firewalls > Create firewall** `soukly`: inbound rules, from any IPv4 and IPv6, **TCP 22**,
   **TCP 80**, **TCP 443** (and **UDP 443** for HTTP/3, optional); nothing else. Outbound stays
   open.
4. **Servers > Add server**:
   - location: Falkenstein, Nuremberg or Helsinki (the closest to Tunisia);
   - image: **Ubuntu 24.04**;
   - type: **Shared vCPU, x86**, **CX33** (or CX43, see the table);
   - networking: public **IPv4** and IPv6;
   - SSH keys: the key of step 2; firewalls: `soukly`;
   - name: `soukly`, then **Create & Buy now**.
5. The public IPv4 stays the server's as long as the server exists (**Primary IPs** in the
   console). Turn off its **auto delete** so that the address is kept even if the server is
   recreated: the dev address and the prod DNS records depend on it.
6. To grow later (dev only to dev and prod): power the server off, **Rescale**, pick the larger
   type (keep "CPU and RAM only" to be able to go back), power it on.

Hetzner's image only has the `root` account: the first run of `setup.sh` (section 2) creates your
admin account with `--admin-user`, because root logins are turned off afterwards.

## 2. Prepare the VM

From your computer, generate a key pair reserved for GitHub Actions. Keep the private key for
the DEP-03 and DEP-04 secrets; only the `.pub` file goes to the VM.

```bash
ssh-keygen -t ed25519 -C soukly-deploy -f ~/.ssh/soukly-deploy
scp ~/.ssh/soukly-deploy.pub ubuntu@<vm-ip>:/tmp/      # Hetzner: root@<vm-ip>
```

**Oracle** (and any provider that gives an admin account with `sudo`, `ubuntu` on Oracle images):

```bash
ssh ubuntu@<vm-ip>
git clone https://github.com/SofienAbdeddaim/soukly.git ~/soukly
sudo bash ~/soukly/infra/vm/setup.sh --deploy-key /tmp/soukly-deploy.pub
sudo bash ~/soukly/infra/vm/setup.sh      # second run: no error, nothing duplicated
```

**Hetzner** (and any provider that only gives `root`): the first run creates your admin account
(here `sofien`) with root's SSH keys and `sudo` without a password; every later login uses it.

```bash
ssh root@<vm-ip>
apt-get update && apt-get install -y git
git clone https://github.com/SofienAbdeddaim/soukly.git /root/soukly
bash /root/soukly/infra/vm/setup.sh --admin-user sofien --deploy-key /tmp/soukly-deploy.pub
exit

ssh sofien@<vm-ip>                                     # root is now refused
git clone https://github.com/SofienAbdeddaim/soukly.git ~/soukly
sudo bash ~/soukly/infra/vm/setup.sh                   # second run: no error, nothing duplicated
```

The script refuses to run as root without `--admin-user`, so that nobody is locked out. Until
PR #11 is merged, run `git -C ~/soukly checkout feat/DEP-02-oracle-vm` after each clone.

The script:

- sets the time zone to `Africa/Tunis`, upgrades the system and turns on automatic security
  updates (`unattended-upgrades`);
- installs Docker Engine and the Compose plugin from Docker's apt repository, as described in
  [Install Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/) (arm64 is
  supported), with log rotation (`json-file`, 10 MB, 5 files);
- opens 80 and 443 in the host firewall when the image has one. Oracle's Ubuntu images ship
  `iptables` rules (`/etc/iptables/rules.v4`) that end with a `REJECT` rule; the script inserts
  the `ACCEPT` rules just before it, in the file and in the live rules, without reloading the
  file, which would erase Docker's rules. Hetzner's image has no such rule: the Hetzner firewall
  of section 1 limits the ports;
- turns off root and password logins over SSH (it refuses to do so if your account has no
  authorized key) and enables `fail2ban` on SSH;
- creates the `deploy` user (no password, member of `docker`) with the given public key, and
  `/opt/soukly/{src,edge,dev,prod,backups}` owned by `deploy`; every `*.env` file there is set
  to mode `600`;
- creates the Docker networks `soukly-edge-dev` and `soukly-edge-prod`;
- installs the backup service and timer (enabled once `backup.env` exists).

Check the SSH rules from your computer:

```bash
ssh -o PubkeyAuthentication=no ubuntu@<vm-ip>        # refused: Permission denied (publickey)
ssh root@<vm-ip>                                     # refused
ssh -i ~/.ssh/soukly-deploy deploy@<vm-ip> docker ps # works
```

## 3. First launch

Everything below runs as `deploy` (`sudo -iu deploy` from your admin account). Until PR #11 is
merged, add `git checkout feat/DEP-02-oracle-vm` after the clone.

```bash
git clone https://github.com/SofienAbdeddaim/soukly.git /opt/soukly/src
cd /opt/soukly/src
```

### Settings and secrets

```bash
cp infra/edge/.env.example /opt/soukly/edge/.env
cp infra/compose/.env.example /opt/soukly/dev/.env
cp infra/compose/.env.example /opt/soukly/prod/.env
chmod 600 /opt/soukly/*/.env
```

Fill in each file. Every secret is generated separately for dev and for prod, for example
`openssl rand -hex 32`; never reuse a value between the two environments.

| Variable | dev | prod |
|---|---|---|
| `ENV_NAME` | `dev` | `prod` |
| `PUBLIC_BASE_URL`, `FRONT_BASE_URL`, `CORS_ALLOWED_ORIGINS` | `https://<DEV_HOST>` | `https://soukly.tn` |
| `GATEWAY_TRUSTED_PROXIES` | `172.30.10.0/24` | `172.30.20.0/24` |
| `JWT_SECRETS_DIR` | `/opt/soukly/dev/secrets` | `/opt/soukly/prod/secrets` |
| `S3_ENDPOINT` | `http://garage:3900` | object storage S3 endpoint (section 5) |
| `S3_PUBLIC_ENDPOINT` | `https://s3.<DEV_HOST>` | same as `S3_ENDPOINT` |
| `S3_REGION` | `garage` | storage region, for example `eu-marseille-1` (Oracle) or `fsn1` (Hetzner) |
| `PHOTO_PUBLIC_BASE_URL` | `https://photos.<DEV_HOST>` | public URL of `listings/` (section 5) |
| `MAIL_*` | `mailpit`, `1025`, no credentials | SMTP provider (to be chosen) |

`IMAGE_TAG` is the tag of the images to run: the full 40-character commit SHA built by the
build-images workflow (DEP-01). DEP-03 and DEP-04 rewrite it at each deployment.

The JWT keys are generated on the VM, never copied from elsewhere (admin account):

```bash
sudo bash /opt/soukly/src/infra/vm/gen-jwt-keys.sh dev
sudo bash /opt/soukly/src/infra/vm/gen-jwt-keys.sh prod
```

### Edge Caddy

`DEV_HOST` is the provisional address `dev.<public-ip-with-dashes>.sslip.io` (for
`203.0.113.10`: `dev.203-0-113-10.sslip.io`), which resolves to the VM without any DNS setup.
`PROD_HOST` stays empty until the domain points to the VM: Caddy then declares no prod site.

```bash
docker compose -p soukly-edge --env-file /opt/soukly/edge/.env -f infra/edge/compose.yml up -d --wait
docker logs soukly-edge-caddy-1 | grep '^edge'   # "dev site enabled for ..."
```

Let's Encrypt allows 50 certificates per registered domain per week. `sslip.io` is **not** on
the Public Suffix List (checked on 2026-10-16), so every sslip.io user shares that quota: issuance
can fail when it is spent, in which case Caddy retries and falls back to ZeroSSL. As soon as the
DNS of `soukly.tn` is ready, prefer `DEV_HOST=dev.soukly.tn` (section 4).

### Stacks

```bash
cd /opt/soukly/src/infra/compose
docker compose -p soukly-dev --env-file /opt/soukly/dev/.env -f compose.yml -f compose.dev.yml up -d --wait
docker compose -p soukly-prod --env-file /opt/soukly/prod/.env -f compose.yml up -d --wait
```

`--wait` returns once every service is healthy. Topics are created by `redpanda-init`
(`soukly.listing-events`, `soukly.user-events`, `soukly.social-events` and their `.dlq`), since
Redpanda runs without automatic topic creation. Never run `docker compose down -v` on the VM:
it deletes the databases.

Checks, from your computer:

```bash
curl -sI https://<DEV_HOST>/fr | grep -iE '^HTTP|x-robots-tag'   # 200, noindex, nofollow
curl -s https://<DEV_HOST>/api/listings                           # answered by the dev gateway
nmap -Pn -p 1-65535 <vm-ip>                                       # only 22, 80, 443 open
```

## 4. Switch to the real domain

1. At the `.tn` registrar, create two `A` records to the server's IPv4: `soukly.tn` and
   `www.soukly.tn` (and `dev`, `s3.dev`, `photos.dev` if dev moves to `dev.soukly.tn`).
   Wait until `dig +short soukly.tn` returns the IP.
2. In `/opt/soukly/edge/.env`: `PROD_HOST=soukly.tn` (and `DEV_HOST=dev.soukly.tn` if dev
   moves too), then reload Caddy, which obtains the certificates:
   ```bash
   docker compose -p soukly-edge --env-file /opt/soukly/edge/.env -f infra/edge/compose.yml up -d --wait
   ```
3. In `/opt/soukly/prod/.env`: `PUBLIC_BASE_URL`, `FRONT_BASE_URL` and `CORS_ALLOWED_ORIGINS`
   set to `https://soukly.tn`, then restart the prod stack (same `up -d --wait` command). Do the
   same in the dev file if dev moved, and update the Garage CORS origin by running the dev stack
   again (`garage-init` applies the new `PUBLIC_BASE_URL`).
4. Update the addresses declared in the Meta apps (SOC-01, SOC-02): site URL, OAuth redirect
   URIs, privacy policy URL.

The prod site sends `Strict-Transport-Security` (one year); `www.soukly.tn` redirects
permanently to `https://soukly.tn`.

## 5. Object storage (prod)

Prod photos and backups use an S3-compatible object storage; dev keeps Garage on the VM.

### Oracle Object Storage (default)

Prod photos and backups use Object Storage through its
[S3 compatibility API](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/s3compatibleapi.htm):
endpoint `https://<namespace>.compat.objectstorage.<region>.oraclecloud.com`, path-style
addresses and SigV4 signatures only. The namespace is shown in **Profile > Tenancy**.
Always Free includes 20 GB of Object Storage and 50,000 API requests per month.

1. Buckets (**Storage > Buckets**), both **private**, Standard tier: `soukly-listings` (photos)
   and `soukly-backups`.
2. A user per use, each in a group with a policy limited to its bucket, for example:
   ```
   Allow group soukly-photos to manage objects in compartment soukly where target.bucket.name='soukly-listings'
   Allow group soukly-backups to manage objects in compartment soukly where target.bucket.name='soukly-backups'
   ```
   Then, for each user, **Customer secret keys > Generate secret key**: the access key and
   secret go to `S3_ACCESS_KEY_ID`/`S3_SECRET_ACCESS_KEY` (prod `.env`) and
   `BACKUP_S3_ACCESS_KEY_ID`/`BACKUP_S3_SECRET_ACCESS_KEY` (`backup.env`).
3. Public read of the photos, `listings/` only: on `soukly-listings`, **Pre-authenticated
   requests > Create**, target **Objects with prefix** `listings/`, access **Permit object
   reads**, object listing **not** allowed, expiry far ahead (an expiry date is mandatory: note
   it and renew the request before it). The bucket stays private, so `incoming/` is never public.
   `PHOTO_PUBLIC_BASE_URL` is the request's URL up to `/o` (the object key is appended to it).
   [Pre-authenticated requests](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/usingpreauthenticatedrequests.htm).

**Open risk for ANN-02 (photo uploads):** the S3 compatibility API has no `PutBucketCors` and the
browser upload of a presigned `PUT` to Object Storage from `https://soukly.tn` has not been
confirmed in Oracle's documentation (presigned `GET` URLs are). Test it on the real bucket
before opening prod; if it fails, uploads go through a same-origin route or the service.

### Hetzner Object Storage (with a Hetzner server)

[Object Storage](https://docs.hetzner.com/storage/object-storage/overview/) (billed per
account: a monthly base price that includes 1 TB of storage and 1 TB of traffic; check the price
on hetzner.com), in the same location as the server. Endpoint
`https://<location>.your-objectstorage.com` (`fsn1`, `nbg1` or `hel1`), region = the location.

1. **Object Storage > Create bucket**, both **private**: `soukly-listings` (photos) and
   `soukly-backups`. Bucket names are global: if one is taken, pick another name and use it in
   `S3_BUCKET` / `BACKUP_S3_BUCKET`.
2. **Security > S3 credentials > Generate credentials**: the access key and the secret are shown
   once. They go to `S3_ACCESS_KEY_ID`/`S3_SECRET_ACCESS_KEY` (prod `.env`) and
   `BACKUP_S3_ACCESS_KEY_ID`/`BACKUP_S3_SECRET_ACCESS_KEY` (`backup.env`). S3 credentials are
   valid for every bucket of the project: to keep the backups out of reach of the application
   key, create `soukly-backups` and its credentials in a second project (`soukly-backups`).
3. Public read of the photos, `listings/` only (`incoming/` is never public, ANN-02), with a
   bucket policy ([access policies](https://docs.hetzner.com/storage/object-storage/faq/buckets-objects/)),
   then CORS for the uploads from the site
   ([CORS](https://docs.hetzner.com/storage/object-storage/howto-protect-objects/cors/)), from
   any computer with the AWS CLI and the credentials of step 2:
   ```bash
   export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=...   # never in a file of the repository
   E=https://fsn1.your-objectstorage.com
   aws --endpoint-url $E --region fsn1 s3api put-bucket-policy --bucket soukly-listings --policy '{
     "Version": "2012-10-17",
     "Statement": [{"Effect": "Allow", "Principal": "*", "Action": "s3:GetObject",
                    "Resource": "arn:aws:s3:::soukly-listings/listings/*"}]}'
   aws --endpoint-url $E --region fsn1 s3api put-bucket-cors --bucket soukly-listings --cors-configuration '{
     "CORSRules": [{"AllowedOrigins": ["https://soukly.tn"], "AllowedMethods": ["GET", "PUT", "HEAD"],
                    "AllowedHeaders": ["*"], "MaxAgeSeconds": 3600}]}'
   ```
   Check: an object under `listings/` opens at
   `https://soukly-listings.fsn1.your-objectstorage.com/listings/<key>`, one under `incoming/`
   answers `403`. `PHOTO_PUBLIC_BASE_URL` is `https://soukly-listings.fsn1.your-objectstorage.com`.

ANN-02 still has to check a presigned `PUT` from the browser on the real bucket before prod
opens (Hetzner documents presigned URLs and CORS; the exact policy syntax above is the standard
S3 one and is to be confirmed on the first try).

## 6. Operations

- **Logs**: `docker compose -p soukly-<env> logs -f <service>`; Caddy's access logs contain no
  `Authorization`, `Cookie` or `Set-Cookie` header, no query string and masked IP addresses.
- **Memory**: each service has a `mem_limit`. A container over its limit is killed by the
  kernel and restarted by Docker (`restart: unless-stopped`); the other containers, and the other
  environment, are not affected. `docker stats` shows the usage.

  | Service | dev | prod |
  |---|---|---|
  | `postgres` | 512 MB | 1 GB |
  | `redpanda` (`--memory=1G`) | 1.25 GB | 1.5 GB |
  | each Java service | 512 MB | 640 MB |
  | `frontend` | 64 MB | 64 MB |
  | `garage`, `mailpit` | 256 MB, 64 MB | n/a |
  | **total** | **about 4.2 GB** | **about 5.1 GB** |

  Dev alone with the edge Caddy is about 4.3 GB; dev and prod together are about 9.5 GB, which
  fits Oracle's 12 GB (or a 16 GB server elsewhere). These are starting values, below the ticket's (sized for
  24 GB): adjust after measuring with `docker stats`.
- **Dev emails** (Mailpit): open a tunnel to its fixed address and browse `http://localhost:8025`:
  ```bash
  ssh -L 8025:172.30.11.25:8025 ubuntu@<vm-ip>
  ```
- **Database or Redpanda by tunnel**: nothing is published on the host. Use `docker exec`, for
  example `docker exec -it soukly-dev-postgres-1 psql -U postgres`, or a tunnel to the
  container's address (`docker inspect soukly-dev-postgres-1`).
- **JWT key rotation** (AUTH-02): in `/opt/soukly/<env>/secrets`, rename `jwt-public.pem` to
  `jwt-previous-public.pem` and delete `jwt-private.pem`; run `gen-jwt-keys.sh <env>` again; in
  the `.env`, set `JWT_PREVIOUS_KEY_ID` to the current `JWT_KEY_ID` and give `JWT_KEY_ID` a new
  value; restart the stack. The JWKS then publishes both keys and the access tokens of the old key
  stay valid until they expire. After 15 minutes (their lifetime), empty `JWT_PREVIOUS_KEY_ID`,
  delete `jwt-previous-public.pem` and restart again. Sessions are not affected: refresh tokens
  do not depend on the key.

## 7. Deploy to dev

The `deploy-dev` workflow deploys **any branch, tag or commit** to dev, only by hand. The VM
returns to the previous version by itself when the new one does not start.

### Settings (once)

1. **Key reserved for deploy-dev**, generated on your computer (never reuse the prod key or
   your own key):
   ```bash
   ssh-keygen -t ed25519 -N '' -C soukly-deploy-dev -f ~/.ssh/soukly-deploy-dev
   scp ~/.ssh/soukly-deploy-dev.pub ubuntu@<vm-ip>:/tmp/
   ```
2. **On the VM** (admin account), update the checkout and run the setup again with that key:
   ```bash
   sudo -iu deploy git -C /opt/soukly/src pull
   sudo bash /opt/soukly/src/infra/vm/setup.sh --dev-key /tmp/soukly-deploy-dev.pub
   ```
   `setup.sh` installs `/opt/soukly/bin/deploy.sh` (owned by root) and adds the key to
   `~deploy/.ssh/authorized_keys` as
   `restrict,command="/opt/soukly/bin/deploy.sh dev" ssh-ed25519 ...`: this key opens no
   terminal, forwards nothing and runs nothing but `deploy.sh dev`, whatever command is sent.
   The commit SHA is the command sent by GitHub (`SSH_ORIGINAL_COMMAND`); anything that is not
   a full SHA is refused.
3. **Pinned host key** of the VM, from your computer (check the fingerprint against
   `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the VM):
   ```bash
   ssh-keyscan -t ed25519 <vm-ip>
   ```
4. **GitHub > Settings > Environments > New environment** `dev` (no reviewer):
   - secrets: `DEV_SSH_KEY` (content of `~/.ssh/soukly-deploy-dev`, the private key),
     `VM_HOST` (the VM's IPv4), `VM_SSH_KNOWN_HOSTS` (the line printed at step 3);
   - variable: `DEV_HOST` (as in `/opt/soukly/edge/.env`, for example
     `dev.203-0-113-10.sslip.io`).

   Then delete the private key from your computer if you do not need it elsewhere.

Check from your computer that the key is restricted:

```bash
K="-i $HOME/.ssh/soukly-deploy-dev deploy@<vm-ip>"
ssh $K 'cat /etc/passwd'        # refused: not a full commit SHA
ssh $K 'prod 0123456789abcdef0123456789abcdef01234567'   # refused: same
ssh -t $K                       # no terminal: PTY allocation request failed, then refused
```

### Deploying

*Actions > deploy-dev > Run workflow*:

- **Use workflow from**: the branch whose last commit is deployed when `ref` is empty;
- **ref** (optional): a branch (`feat/ANN-04-wizard`), a tag or a commit SHA (full or short);
  an unknown reference stops the run with `Reference not found: <ref>` before anything else;
- **force_build**: rebuild the images even if they already exist for that commit.

The run resolves the commit, calls `build-images` (images already published are reused), then
sends the SHA to the VM. `deploy.sh dev <sha>` on the VM:

1. refuses anything but `dev`/`prod` and a full SHA, and takes a lock per environment (a second
   deployment waits; the workflow also queues runs with `concurrency: deploy-dev`);
2. checks that the 5 images `ghcr.io/<owner>/soukly-*:<sha>` exist, otherwise stops without
   touching anything;
3. downloads the Compose files of that commit from the public repository
   (`codeload.github.com`, archive of `<sha>`, only `infra/compose`, `infra/postgres` and
   `infra/garage`) into `/opt/soukly/dev/releases/<sha>/`, and copies them into
   `/opt/soukly/dev/active/`, the fixed path the stack runs from;
4. runs `docker compose pull` and `up -d --wait` with `IMAGE_TAG=<sha>`, at most 5 minutes
   (`DEPLOY_TIMEOUT`, in seconds);
5. checks the site from the VM, through the edge Caddy and its certificate
   (`curl --resolve <host>:443:127.0.0.1`, host of `PUBLIC_BASE_URL`): `GET /` answers 200,
   `/federation.manifest.json` points to `PUBLIC_BASE_URL`, and `GET /api/listings?size=1`
   is answered through the gateway without a 5xx;
6. on success, makes the release `current`, writes `IMAGE_TAG` in `/opt/soukly/dev/.env`, keeps
   the last 5 releases and deletes the local images no release uses;
7. on failure, prints the last 200 log lines of each failing service, then starts the `current`
   release again with its own Compose files and checks it the same way.

Every deployment is written to `/opt/soukly/dev/deployments.log` (date, SHA, result). The run's
summary shows the reference, the short SHA, who launched it, the site link, the duration and
the result:

| Result | Meaning | What to do |
|---|---|---|
| `deployed` | the commit runs in dev | nothing |
| `failed, rolled back to <sha>` | the commit did not start; dev runs the previous version again | read the logs in the `deploy` job, fix, deploy again |
| `failed, rollback impossible` | first deployment, or the previous version did not start again either | read the logs, then on the VM `cat /opt/soukly/dev/deployments.log` and `docker compose -p soukly-dev ps -a`; deploy a commit known to work |
| `failed, images not built` | `build-images` failed: nothing was sent to the VM | read the `images` jobs |

The version that runs is shown by `GET https://<DEV_HOST>/api/actuator/info` (`app.version`).

Notes:

- The first deployment has no previous version to return to: deploy a commit known to work,
  for example the last commit of `main`. Commits older than DEP-02 cannot be deployed (they have
  no `infra/compose`).
- `deploy.sh` can be run by hand as `deploy`: `/opt/soukly/bin/deploy.sh dev <full sha>`.
- Once deploy-dev has run, the dev stack runs from `/opt/soukly/dev/active/infra/compose`: run
  any manual `docker compose -p soukly-dev ...` command from there, not from `/opt/soukly/src`
  (other paths would recreate the containers with other files).
- GitHub keeps at most one run waiting per concurrency group: if a third run is started while
  one runs and one waits, the waiting one is cancelled.

### Database migrations and rollbacks

Flyway migrations run when the services start and are **not** undone by a return to the
previous version. So every migration must stay compatible with the previous version of the
code: add a column or a table first, and remove or rename it only in a later version, once no
running version uses it (rule also in `CLAUDE.md` and the PR template).

## 8. Put in prod

The `deploy-prod` workflow puts the last commit of `main` in prod, only by hand and only after
your approval, then tags it `vX.Y.Z` with a GitHub release. The VM backs up the prod databases
before touching anything and returns to the previous version by itself when the new one does
not start; in that case no tag and no release are created.

### Settings (once)

1. **Prod domain**: prod is only deployed once `PROD_HOST` is set in `/opt/soukly/edge/.env` and
   equals the host of `PUBLIC_BASE_URL` in `/opt/soukly/prod/.env` (section 4). Until then every
   run stops with `prod domain not configured`, before any change.
2. **Key reserved for deploy-prod**, generated on your computer (never the dev key, never your
   own key):
   ```bash
   ssh-keygen -t ed25519 -N '' -C soukly-deploy-prod -f ~/.ssh/soukly-deploy-prod
   scp ~/.ssh/soukly-deploy-prod.pub ubuntu@<vm-ip>:/tmp/
   ```
   On Windows PowerShell, use `$env:USERPROFILE\.ssh\soukly-deploy-prod` as the path.
3. **On the VM** (admin account), update the checkout and run the setup with both keys:
   ```bash
   sudo -iu deploy git -C /opt/soukly/src pull
   sudo bash /opt/soukly/src/infra/vm/setup.sh --dev-key /tmp/soukly-deploy-dev.pub --prod-key /tmp/soukly-deploy-prod.pub
   ```
   (`--dev-key` can be left out when the dev key is already installed.) The prod key is added as
   `restrict,command="/opt/soukly/bin/deploy.sh prod" ssh-ed25519 ...`; `setup.sh` refuses a prod
   key identical to the dev key.
4. **GitHub > Settings > Environments > New environment** `production`:
   - **Required reviewers**: you; leave **Prevent self-review** off (you approve your own runs);
   - **Deployment branches and tags**: *Selected branches and tags*, rule `main`;
   - secrets: `PROD_SSH_KEY` (content of `~/.ssh/soukly-deploy-prod`), `VM_HOST` and
     `VM_SSH_KNOWN_HOSTS` (same values as in `dev`);
   - variable: `PROD_HOST` (for example `soukly.tn`).

   Then delete the private key from your computer.
5. **Version tags** (*Settings > Rules > Rulesets > New ruleset > New tag ruleset*): name
   `version tags`, enforcement *Active*, target *Include by pattern* `v*`, rules **Restrict
   updates**, **Restrict deletions** and **Block force pushes**. No bypass actor: nobody, the
   workflow included, can move or delete a published version. Leave **Restrict creations** off
   unless *GitHub Actions* is offered in the bypass list: the `GITHUB_TOKEN` of the workflow is
   not a bypass actor in GitHub's documentation, so restricting creation would block
   `deploy-prod` itself. Do not create `v*` tags by hand.

### Putting a version in prod

1. Deploy the last commit of `main` to dev with `deploy-dev` and check it.
2. *Actions > deploy-prod > Run workflow*, **Use workflow from** `main`, with:
   - **bump**: `patch` (default), `minor` or `major`. The first version is `v0.1.0` (`v1.0.0`
     with `major`);
   - **redeploy_tag** and **allow_untested** left empty (see below).
3. The `prepare` job computes the next version and writes in the run's summary the version, the
   previous one, the commit, the number of commits and the **draft release notes** (`feat` under
   *New features*, `fix` under *Fixes*, the rest under *Other*, without `chore` and `docs`,
   each line with its ticket and PR link). Read it.
4. The `images` job reuses the images built for dev (same commit).
5. The `deploy` job waits for your approval (*Review deployments > production > Approve*).
   Reject it to stop: nothing changes. On approval, `deploy.sh prod <sha>` on the VM:
   1. checks the prod domain, then that this commit was deployed successfully in dev
      (`/opt/soukly/dev/deployments.log`); otherwise stops with
      `version never deployed successfully in dev`;
   2. checks the 5 images, then **backs up the 3 prod databases** to the backup bucket under
      `<date>/predeploy-<sha>/`; if the backup fails, stops without touching prod;
   3. deploys and checks the site as for dev (section 7), and returns to the previous version
      when the new one does not start.
6. The `release` job runs only after a successful deployment: annotated tag `vX.Y.Z` on the
   commit, GitHub release with the notes, and the `vX.Y.Z` tag added to the 5 images (no
   rebuild).

The `summary` job ends every run with its result, the version, who launched it and the
duration:

| Result | Meaning |
|---|---|
| `released vX.Y.Z` | prod runs the new version; tag and release created |
| `failed, rolled back: prod still runs vA.B.C` | the version did not start; no tag, no release |
| `failed: see the deploy job` | refused before any change (domain, never deployed in dev, images, backup), or the previous version did not start either |
| `not deployed (approval refused or cancelled)` | nothing changed |
| `failed, images not built` | nothing was sent to the VM |

The version that runs is shown by `GET https://<PROD_HOST>/api/actuator/info` (`app.version`),
and every run is written to `/opt/soukly/prod/deployments.log`.

### Manual rollback

To put back an earlier version: *Run workflow* from `main` with **redeploy_tag** `vA.B.C`. The
commit of that tag is deployed (backup first, no dev check), with no new tag and no release.
Remember the migration rule below: the database is not rolled back.

### Urgent fix

When a fix cannot wait for dev: *Run workflow* with **allow_untested** checked. The dev check
is skipped (the VM logs a warning), the summary and the release notes say **Not tested in
dev**, and everything else is the same (approval, backup, rollback, tag). Deploy the same commit
to dev afterwards.

### Restoring a pre-deployment backup

Pre-deployment backups are kept 7 days with the nightly ones; restore them with the procedure
of [`infra/backup/README.md`](../backup/README.md#restore), using `<date>/predeploy-<sha>` as the
backup folder.

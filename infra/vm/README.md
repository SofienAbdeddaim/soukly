# Oracle VM: dev and prod environments (DEP-02)

One Oracle Cloud Always Free VM (Ampere A1, arm64) runs two isolated Soukly environments, `dev`
and `prod`, behind one Caddy that serves both in HTTPS. This page covers creating the VM,
preparing it, the first launch, switching to the real domain and day-to-day operations. Backups
and restores are covered in [`infra/backup/README.md`](../backup/README.md). Deployments of new
versions are done by the DEP-03 (dev) and DEP-04 (prod) workflows.

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
| `infra/edge/` | Shared Caddy: `compose.yml`, `Caddyfile`, one site file per environment, `.env.example` |
| `infra/compose/` | One environment: `compose.yml` (dev and prod), `compose.dev.yml` (Garage, Mailpit, lower limits), `.env.example` |
| `infra/backup/` | Nightly backup of the prod databases and the restore procedure |

On the VM, settings and secrets live outside the repository, readable by `deploy` only:

```
/opt/soukly/
├── src/       # checkout of the repository (owner deploy)
├── edge/.env
├── dev/.env, dev/secrets/      # JWT keys of dev
├── prod/.env, prod/secrets/    # JWT keys of prod
└── backups/backup.env
```

## 1. Create the VM (Oracle console)

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

## 2. Prepare the VM

From your computer, generate a key pair reserved for GitHub Actions. Keep the private key for
the DEP-03 and DEP-04 secrets; only the `.pub` file goes to the VM.

```bash
ssh-keygen -t ed25519 -C soukly-deploy -f ~/.ssh/soukly-deploy
scp ~/.ssh/soukly-deploy.pub ubuntu@<vm-ip>:/tmp/
```

On the VM, with your admin account (`ubuntu` on Oracle images):

```bash
git clone https://github.com/SofienAbdeddaim/soukly.git ~/soukly
sudo bash ~/soukly/infra/vm/setup.sh --deploy-key /tmp/soukly-deploy.pub
sudo bash ~/soukly/infra/vm/setup.sh      # second run: no error, nothing duplicated
```

The script:

- sets the time zone to `Africa/Tunis`, upgrades the system and turns on automatic security
  updates (`unattended-upgrades`);
- installs Docker Engine and the Compose plugin from Docker's apt repository, as described in
  [Install Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/) (arm64 is
  supported), with log rotation (`json-file`, 10 MB, 5 files);
- opens 80 and 443 in the host firewall. Oracle's Ubuntu images ship `iptables` rules
  (`/etc/iptables/rules.v4`, loaded at boot by `iptables-persistent`) that end with a `REJECT`
  rule; the script inserts the `ACCEPT` rules just before it, in the file and in the live rules,
  without reloading the file, which would erase Docker's rules;
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

Everything below runs as `deploy` (`sudo -iu deploy` from your admin account).

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
| `S3_ENDPOINT` | `http://garage:3900` | Object Storage S3 endpoint (section 5) |
| `S3_PUBLIC_ENDPOINT` | `https://s3.<DEV_HOST>` | same as `S3_ENDPOINT` |
| `S3_REGION` | `garage` | the Oracle region, for example `eu-marseille-1` |
| `PHOTO_PUBLIC_BASE_URL` | `https://photos.<DEV_HOST>` | URL of the read pre-authenticated request (section 5) |
| `MAIL_*` | `mailpit`, `1025`, no credentials | SMTP provider (to be chosen) |

`IMAGE_TAG` is the tag of the images to run (`sha-<commit>` from the build-images workflow,
DEP-01); DEP-03 and DEP-04 rewrite it at each deployment.

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

1. At the `.tn` registrar, create two `A` records to the reserved IP: `soukly.tn` and
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

## 5. Oracle Object Storage (prod)

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

  With the edge Caddy (128 MB), about 9.5 GB of the 12 GB, leaving about 2.5 GB for the
  system. These are starting values, below the ticket's (sized for 24 GB): adjust after measuring
  with `docker stats`.
- **Dev emails** (Mailpit): open a tunnel to its fixed address and browse `http://localhost:8025`:
  ```bash
  ssh -L 8025:172.30.11.25:8025 ubuntu@<vm-ip>
  ```
- **Database or Redpanda by tunnel**: nothing is published on the host. Use `docker exec`, for
  example `docker exec -it soukly-dev-postgres-1 psql -U postgres`, or a tunnel to the
  container's address (`docker inspect soukly-dev-postgres-1`).
- **JWT key rotation**: move the current `jwt-public.pem` aside for `JWT_PREVIOUS_PUBLIC_KEY_PATH`
  (AUTH-02), delete `jwt-private.pem`, run `gen-jwt-keys.sh <env>` again, change `JWT_KEY_ID`
  and restart the stack.

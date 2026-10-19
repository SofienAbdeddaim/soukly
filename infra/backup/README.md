# Prod database backups (DEP-02)

Every night at 03:00 (Africa/Tunis), `soukly-backup.timer` runs `pg-backup.sh` as `deploy`:

1. `pg_dump -Fc` (custom format) of `soukly_user`, `soukly_listing` and `soukly_social`, from the
   `postgres` container of the `soukly-prod` project;
2. upload to the private bucket `soukly-backups` of an S3-compatible object storage (Oracle
   Object Storage by default), under `<YYYY-MM-DD>/`;
3. deletion of the date folders older than 7 days (the last 7 days are kept, today included).

Any failure (no prod container, empty dump, upload refused) ends the run in error, visible in
`systemctl status soukly-backup` and `journalctl -u soukly-backup`. The script never prints a
secret.

**Upload tool:** the AWS CLI (`amazon/aws-cli:2.38.0`, the version already pinned by SETUP-02),
run in a throwaway container: nothing extra to install on the VM, and it works with any
S3-compatible storage. The checksum options are set to
`when_required` because recent AWS CLI versions send new integrity checksums by default, which
S3-compatible services may not accept. Retention is done by the script rather than by a bucket
lifecycle rule, so that it is visible in the repository and works the same on any S3 service.

## Setup (once, on the VM)

1. Create the `soukly-backups` bucket and its secret key ([`infra/vm/README.md`](../vm/README.md),
   section 5).
2. As `deploy`:
   ```bash
   cp /opt/soukly/src/infra/backup/backup.env.example /opt/soukly/backups/backup.env
   chmod 600 /opt/soukly/backups/backup.env   # then fill it in
   ```
3. As admin: `sudo bash /opt/soukly/src/infra/vm/setup.sh` (enables the timer now that
   `backup.env` exists).
4. First run by hand, then check the bucket:
   ```bash
   sudo systemctl start soukly-backup
   systemctl status soukly-backup          # "Done: 3 databases saved under soukly-backups/<date>/"
   systemctl list-timers soukly-backup     # next run at 03:00
   ```

## Restore

The same steps restore prod, or copy prod into dev to test a backup (done once for DEP-02).
`<env>` is the target stack, `<date>` the backup folder.

1. Download the backup (as `deploy`, with the settings of `backup.env`):
   ```bash
   set -a; . /opt/soukly/backups/backup.env; set +a
   mkdir -p /opt/soukly/backups/restore
   docker run --rm -v /opt/soukly/backups/restore:/work \
     -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY_ID" -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_ACCESS_KEY" \
     -e AWS_DEFAULT_REGION="$BACKUP_S3_REGION" \
     -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required \
     amazon/aws-cli:2.38.0 --endpoint-url "$BACKUP_S3_ENDPOINT" \
     s3 cp --recursive "s3://$BACKUP_S3_BUCKET/<date>/" /work/
   ```
2. Stop the services that use the databases (Postgres keeps running):
   ```bash
   cd /opt/soukly/src/infra/compose
   docker compose -p soukly-<env> --env-file /opt/soukly/<env>/.env -f compose.yml [-f compose.dev.yml] \
     stop gateway user-service listing-service social-publisher
   ```
3. Restore each database in one transaction, owned by the role of its service:
   ```bash
   for spec in "soukly_user user_svc" "soukly_listing listing_svc" "soukly_social social_svc"; do
     read -r db role <<< "$spec"
     docker exec -i soukly-<env>-postgres-1 pg_restore -U postgres -d "$db" \
       --clean --if-exists --no-owner --role="$role" --single-transaction --exit-on-error \
       < "/opt/soukly/backups/restore/$db.dump"
   done
   ```
4. Start the stack again (`up -d --wait`, same files as step 2) and check that the services read
   the data, for example
   `docker exec soukly-<env>-postgres-1 psql -U listing_svc -d soukly_listing -c '\dt'`.
5. Delete `/opt/soukly/backups/restore`. After copying prod into dev, remember that dev now holds
   prod's personal data: restore into dev only to test, then reset it.

Tested locally on 2026-10-16 with the dev and prod stacks of this ticket (a local Garage bucket
standing in for the object storage): backup of the 3 databases, deletion of a folder older than
7 days, failure on a missing bucket (exit status 1), and restore into dev in about 20 seconds,
with the rows readable by `listing_svc`.

#!/usr/bin/env bash
# Nightly backup of the prod databases (DEP-02), run by soukly-backup.timer as the deploy user.
#   1. pg_dump (custom format) of soukly_user, soukly_listing and soukly_social, from the postgres
#      container of the soukly-prod project;
#   2. upload to the private Oracle Object Storage bucket soukly-backups, through its S3
#      compatibility API, under <YYYY-MM-DD>/;
#   3. deletion of the date prefixes older than BACKUP_RETENTION_DAYS (7 by default).
# Any failure ends the script with a non-zero status, shown by `systemctl status soukly-backup`.
#
# Settings: /opt/soukly/backups/backup.env (mode 600), see backup.env.example. Never logs a secret.
set -euo pipefail

ENV_FILE="${BACKUP_ENV_FILE:-/opt/soukly/backups/backup.env}"
[[ -r "${ENV_FILE}" ]] || { echo "[backup] ERROR: cannot read ${ENV_FILE}" >&2; exit 1; }
set -a
# shellcheck source=/dev/null
. "${ENV_FILE}"
set +a

: "${BACKUP_S3_ENDPOINT:?BACKUP_S3_ENDPOINT is required}"
: "${BACKUP_S3_REGION:?BACKUP_S3_REGION is required}"
: "${BACKUP_S3_BUCKET:?BACKUP_S3_BUCKET is required}"
: "${BACKUP_S3_ACCESS_KEY_ID:?BACKUP_S3_ACCESS_KEY_ID is required}"
: "${BACKUP_S3_SECRET_ACCESS_KEY:?BACKUP_S3_SECRET_ACCESS_KEY is required}"
PROJECT="${BACKUP_COMPOSE_PROJECT:-soukly-prod}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
WORK_DIR="${BACKUP_WORK_DIR:-/opt/soukly/backups/work}"
AWS_CLI_IMAGE="amazon/aws-cli:2.38.0"
DATABASES=(soukly_user soukly_listing soukly_social)

log() { echo "[backup] $*"; }

# aws <args...>: AWS CLI in a pinned container, credentials passed by environment only.
aws() {
  docker run --rm \
    -e AWS_ACCESS_KEY_ID="${BACKUP_S3_ACCESS_KEY_ID}" \
    -e AWS_SECRET_ACCESS_KEY="${BACKUP_S3_SECRET_ACCESS_KEY}" \
    -e AWS_DEFAULT_REGION="${BACKUP_S3_REGION}" \
    -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
    -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required \
    -v "${WORK_DIR}:/work" -w /work \
    "${AWS_CLI_IMAGE}" --endpoint-url "${BACKUP_S3_ENDPOINT}" "$@"
}

container="$(docker ps -q \
  --filter "label=com.docker.compose.project=${PROJECT}" \
  --filter "label=com.docker.compose.service=postgres")"
[[ -n "${container}" ]] || { echo "[backup] ERROR: no running postgres container in ${PROJECT}" >&2; exit 1; }

day="$(date +%F)"
install -d -m 0700 "${WORK_DIR}"
trap 'rm -f "${WORK_DIR}"/*.dump' EXIT

for db in "${DATABASES[@]}"; do
  dump="${WORK_DIR}/${db}.dump"
  log "pg_dump ${db}"
  docker exec "${container}" pg_dump -U postgres -Fc "${db}" > "${dump}"
  [[ -s "${dump}" ]] || { echo "[backup] ERROR: empty dump for ${db}" >&2; exit 1; }
  log "upload ${db} ($(du -h "${dump}" | cut -f1)) to ${BACKUP_S3_BUCKET}/${day}/"
  aws s3 cp --only-show-errors "/work/${db}.dump" "s3://${BACKUP_S3_BUCKET}/${day}/${db}.dump"
done

# Retention: the last RETENTION_DAYS days are kept (today included), older date prefixes are removed.
cutoff="$(date -d "-$((RETENTION_DAYS - 1)) days" +%F)"
aws s3 ls "s3://${BACKUP_S3_BUCKET}/" | awk '$1 == "PRE" { sub("/$", "", $2); print $2 }' |
  while read -r prefix; do
    if [[ "${prefix}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ && "${prefix}" < "${cutoff}" ]]; then
      log "deleting ${prefix}/ (older than ${RETENTION_DAYS} days)"
      aws s3 rm --only-show-errors --recursive "s3://${BACKUP_S3_BUCKET}/${prefix}/"
    fi
  done

log "Done: ${#DATABASES[@]} databases saved under ${BACKUP_S3_BUCKET}/${day}/"

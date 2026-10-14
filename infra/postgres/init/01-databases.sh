#!/usr/bin/env bash
# Creates one database and one login role per backend service.
# Run once by the postgres image on an empty data directory (docker-entrypoint-initdb.d).
# Each role owns its database; CONNECT is revoked from PUBLIC so a role cannot open
# another service's database.
set -euo pipefail

: "${USER_SVC_DB_PASSWORD:?USER_SVC_DB_PASSWORD is required}"
: "${LISTING_SVC_DB_PASSWORD:?LISTING_SVC_DB_PASSWORD is required}"
: "${SOCIAL_SVC_DB_PASSWORD:?SOCIAL_SVC_DB_PASSWORD is required}"

create_service_db() {
  local db="$1" role="$2" password="$3"
  echo "Creating database ${db} owned by ${role}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v db="$db" -v role="$role" -v password="$password" <<'EOSQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"role";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
}

create_service_db soukly_user user_svc "$USER_SVC_DB_PASSWORD"
create_service_db soukly_listing listing_svc "$LISTING_SVC_DB_PASSWORD"
create_service_db soukly_social social_svc "$SOCIAL_SVC_DB_PASSWORD"

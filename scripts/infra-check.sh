#!/usr/bin/env bash
# Checks the local infrastructure started with `docker compose up -d --wait` (SETUP-02).
# Covers every acceptance scenario of the ticket and exits on the first failure.
# Only uses tools shipped in the containers (psql, rpk, aws-cli, curl): nothing to install on the host.
# The last step starts the `tools` profile (Redpanda Console, pgAdmin), then removes those two
# containers (their volumes are kept) so the script can run again from the same state.
set -euo pipefail

cd "$(dirname "$0")/.."

pass() { echo "  OK  $*"; }
fail() { echo "  KO  $*" >&2; exit 1; }
step() { echo; echo "== $*"; }

compose() { docker compose "$@"; }
# Runs a command in a throwaway aws-cli container on the Compose network (S3 credentials from .env).
toolbox() { compose --progress quiet run --rm --no-deps -T --entrypoint "$1" garage-init "${@:2}"; }

[[ -f .env ]] || fail ".env is missing: cp .env.example .env"

step "Startup"
for service in postgres redpanda garage mailpit; do
  health="$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q "$service")" 2>/dev/null || true)"
  [[ "$health" == "healthy" ]] || fail "$service is not healthy (${health:-not running})"
  pass "$service is healthy"
done
init_container="$(compose ps -a -q garage-init)"
[[ -n "$init_container" ]] || fail "garage-init has not run"
# `up --wait` does not wait for one-shot containers to exit: give garage-init a few seconds.
for _ in $(seq 1 60); do
  [[ "$(docker inspect -f '{{.State.Status}}' "$init_container")" == "exited" ]] && break
  sleep 1
done
[[ "$(docker inspect -f '{{.State.Status}} {{.State.ExitCode}}' "$init_container")" == "exited 0" ]] \
  || fail "garage-init did not exit with code 0"
pass "garage-init exited with code 0"
for service in redpanda-console pgadmin; do
  [[ -z "$(compose --profile tools ps -q "$service")" ]] || fail "$service is running without the tools profile"
done
pass "redpanda-console and pgadmin are not started"

step "Databases"
psql_as() {
  local role="$1" password_var="$2" db="$3"
  compose exec -T -e PSQL_PASSWORD_VAR="$password_var" postgres sh -c \
    'PGPASSWORD="$(printenv "$PSQL_PASSWORD_VAR")" psql -h 127.0.0.1 -U "$0" -d "$1" -tAc "select 1"' "$role" "$db"
}
[[ "$(psql_as listing_svc LISTING_SVC_DB_PASSWORD soukly_listing)" == "1" ]] \
  || fail "listing_svc cannot connect to soukly_listing"
pass "listing_svc connects to soukly_listing"
if psql_as listing_svc LISTING_SVC_DB_PASSWORD soukly_user >/dev/null 2>&1; then
  fail "listing_svc can connect to soukly_user"
fi
pass "listing_svc is refused on soukly_user"
[[ "$(psql_as user_svc USER_SVC_DB_PASSWORD soukly_user)" == "1" ]] || fail "user_svc cannot connect to soukly_user"
[[ "$(psql_as social_svc SOCIAL_SVC_DB_PASSWORD soukly_social)" == "1" ]] || fail "social_svc cannot connect to soukly_social"
pass "user_svc and social_svc connect to their own database"

step "Kafka (Redpanda)"
echo ping | compose exec -T redpanda rpk topic produce infra.check --allow-auto-topic-creation >/dev/null || fail "cannot produce to infra.check"
received="$(compose exec -T redpanda rpk topic consume infra.check --offset -1 --num 1 --format '%v\n')"
[[ "$received" == "ping" ]] || fail "consumed '${received}' instead of 'ping'"
pass "produced and consumed 'ping' on infra.check"

step "Storage (Garage S3)"
toolbox bash -c '
  set -euo pipefail
  export AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY_ID" AWS_SECRET_ACCESS_KEY="$S3_SECRET_ACCESS_KEY"
  s3() { aws --endpoint-url http://garage:3900 --region "$S3_REGION" s3 "$@"; }
  content="infra-check $(date +%s)"
  echo "$content" > /tmp/test.txt
  s3 cp --quiet /tmp/test.txt "s3://$S3_BUCKET/test.txt"
  s3 cp --quiet "s3://$S3_BUCKET/test.txt" /tmp/read.txt
  [[ "$(cat /tmp/read.txt)" == "$content" ]] || { echo "read content differs" >&2; exit 1; }
  s3 rm --quiet "s3://$S3_BUCKET/test.txt"
' || fail "S3 round trip with the soukly-app key failed"
pass "test.txt written and read back with the soukly-app key"
cors="$(toolbox curl -fsS -o /dev/null -D - -X OPTIONS \
  -H 'Origin: http://localhost:4200' -H 'Access-Control-Request-Method: PUT' \
  http://garage:3900/soukly-listings/test.txt | tr -d '\r' | grep -i '^access-control-allow-origin:' || true)"
[[ "${cors,,}" == "access-control-allow-origin: http://localhost:4200" ]] || fail "CORS preflight answered '${cors}'"
pass "CORS preflight allows http://localhost:4200"
# Website endpoint (ANN-02): listings/ is public, incoming/ never is. Host header = bucket + root_domain.
toolbox bash -c '
  set -euo pipefail
  export AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY_ID" AWS_SECRET_ACCESS_KEY="$S3_SECRET_ACCESS_KEY"
  s3() { aws --endpoint-url http://garage:3900 --region "$S3_REGION" s3 "$@"; }
  echo public > /tmp/test.txt
  s3 cp --quiet /tmp/test.txt "s3://$S3_BUCKET/listings/infra-check/test.txt"
  s3 cp --quiet /tmp/test.txt "s3://$S3_BUCKET/incoming/infra-check/test.txt"
  web() { curl -sS -o /dev/null -w "%{http_code}" -H "Host: $S3_BUCKET.web.localhost" "http://garage:3902/$1"; }
  listings="$(web listings/infra-check/test.txt)"; incoming="$(web incoming/infra-check/test.txt)"
  s3 rm --quiet "s3://$S3_BUCKET/listings/infra-check/test.txt"
  s3 rm --quiet "s3://$S3_BUCKET/incoming/infra-check/test.txt"
  [[ "$listings" == "200" && "$incoming" == "404" ]] || { echo "listings/ ${listings}, incoming/ ${incoming}" >&2; exit 1; }
' || fail "website endpoint does not serve listings/ only"
pass "website endpoint serves listings/ and answers 404 for incoming/"

step "Idempotence"
compose --progress quiet run --rm garage-init >/dev/null || fail "second garage-init run failed"
count="$(toolbox bash -c 'curl -fsS -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" http://garage:3903/v2/ListBuckets' \
  | grep -o "\"soukly-listings\"" | wc -l | tr -d ' ')"
[[ "$count" == "1" ]] || fail "expected one soukly-listings bucket, found ${count}"
keys="$(toolbox bash -c 'curl -fsS -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" http://garage:3903/v2/ListKeys' \
  | grep -o '"soukly-app"' | wc -l | tr -d ' ')"
[[ "$keys" == "1" ]] || fail "expected one soukly-app key, found ${keys}"
pass "garage-init ran again with code 0, no duplicate bucket or key"

step "Missing configuration"
if output="$(compose --env-file /dev/null config -q 2>&1)"; then
  fail "compose accepted an empty environment"
fi
grep -q 'POSTGRES_PASSWORD is not set' <<<"$output" || fail "error does not name the missing variable: ${output}"
pass "without .env, Compose fails and names the missing variable"

step "Tools profile"
compose --profile tools up -d --wait >/dev/null 2>&1 || fail "tools profile did not start"
for target in "redpanda-console:8080:8090:/" "pgadmin:80:5050:/misc/ping"; do
  IFS=: read -r service port host_port path <<<"$target"
  status="$(toolbox curl -sS -o /dev/null -w '%{http_code}' "http://${service}:${port}${path}")"
  [[ "$status" == "200" ]] || fail "$service answered ${status}"
  [[ "$(compose --profile tools port "$service" "$port")" == *":${host_port}" ]] || fail "$service is not published on ${host_port}"
  pass "$service answers 200 (published on localhost:${host_port})"
done
compose --progress quiet --profile tools rm -sf redpanda-console pgadmin >/dev/null
pass "tools profile stopped again"

echo
echo "All infrastructure checks passed."

#!/usr/bin/env bash
# Deploys one commit to one Soukly environment of the VM (DEP-03 for dev, DEP-04 for prod), with an
# automatic return to the previous version when the new one does not start correctly.
#
#   /opt/soukly/bin/deploy.sh <dev|prod> <full commit sha> [untested|redeploy]   # by hand, as deploy
#
# GitHub Actions calls it through a restricted SSH key of the deploy user (infra/vm/setup.sh):
# authorized_keys forces "deploy.sh <env>" and "<sha> [flag]" arrives in SSH_ORIGINAL_COMMAND, so
# the key can run nothing else and deploy no other environment.
#
# Prod only (DEP-04), before anything is changed: PROD_HOST must be set in the edge settings; the
# SHA must have been deployed successfully in dev (/opt/soukly/dev/deployments.log), unless the
# flag is "untested" (urgent fix) or "redeploy" (a version tag that already ran in prod); the prod
# databases are backed up (pg-backup.sh, label predeploy-<sha>), and a failed backup stops here.
#
# Steps: check the arguments, lock the environment, check that the 5 images of the commit exist on
# GHCR, download the Compose files of that commit from the public repository, pull and start the
# stack (IMAGE_TAG=<sha>), check the site through the edge Caddy. Success: the version becomes
# "current". Failure: the logs of the failing services are printed and the current version is
# started again.
#
# Exit status: 0 deployed; 2 failed and the previous version runs again; 1 failed without a
# working previous version (first deployment, or the return failed), or nothing was changed.
# The last line of the output is "RESULT: deployed <sha>", "RESULT: rolled-back <sha>" or
# "RESULT: failed".
set -euo pipefail

SOUKLY_ROOT="${SOUKLY_ROOT:-/opt/soukly}"
# Backup script of DEP-02 (installed by setup.sh); it takes a label as argument.
SOUKLY_BACKUP_CMD="${SOUKLY_BACKUP_CMD:-/usr/local/sbin/soukly-pg-backup}"
# Archive of one commit of the public repository (no credentials needed); %s is the SHA.
SOUKLY_SOURCE_URL="${SOUKLY_SOURCE_URL:-https://codeload.github.com/SofienAbdeddaim/soukly/tar.gz/%s}"
# Maximum time for the stack to become healthy (docker compose up --wait), in seconds.
DEPLOY_TIMEOUT="${DEPLOY_TIMEOUT:-300}"
# Time given to the edge Caddy and the gateway to answer the checks after start-up, in seconds.
CHECK_TIMEOUT="${CHECK_TIMEOUT:-60}"
KEEP_RELEASES=5
IMAGES=(gateway user-service listing-service social-publisher frontend)

log() { printf '[deploy %s] %s\n' "$(date +%H:%M:%S)" "$*"; }
fail() {
  log "ERROR: $*"
  echo "RESULT: failed"
  exit 1
}

# ---- Arguments ----
env_name="${1:-}"
case "${env_name}" in
  dev | prod) ;;
  *) fail "unknown environment '${env_name}' (dev or prod)" ;;
esac
# Through the restricted key, "<sha> [flag]" is the whole command sent by the client.
if [[ -n "${SSH_ORIGINAL_COMMAND+x}" ]]; then
  request="${SSH_ORIGINAL_COMMAND}"
else
  request="${2:-} ${3:-}"
fi
read -r sha flag extra <<< "${request}" || true
[[ "${sha:-}" =~ ^[0-9a-f]{40}$ && -z "${extra:-}" ]] \
  || fail "not a full commit SHA (40 lower-case hex characters)"
case "${env_name}:${flag:-}" in
  dev: | prod: | prod:untested | prod:redeploy) ;;
  *) fail "unknown option '${flag}' for ${env_name}" ;;
esac

env_dir="${SOUKLY_ROOT}/${env_name}"
env_file="${env_dir}/.env"
releases="${env_dir}/releases"
active="${env_dir}/active"
[[ -r "${env_file}" ]] || fail "${env_file} is missing"

# Reads one variable of the settings file without running it.
setting() { sed -n "s/^$1=//p" "${env_file}" | tail -n 1; }
owner="$(setting IMAGE_OWNER)"
owner="${owner:-sofienabdeddaim}"
public_url="$(setting PUBLIC_BASE_URL)"
host="${public_url#https://}"
host="${host%%/*}"
[[ "${public_url}" == https://* && "${host}" =~ ^[A-Za-z0-9.-]+$ ]] \
  || fail "PUBLIC_BASE_URL of ${env_file} is not an https address"

# ---- Prod: domain and dev check, before anything is changed ----
if [[ "${env_name}" == prod ]]; then
  prod_host="$(sed -n 's/^PROD_HOST=//p' "${SOUKLY_ROOT}/edge/.env" 2> /dev/null | tail -n 1)"
  [[ -n "${prod_host}" ]] || fail "prod domain not configured (PROD_HOST is empty in ${SOUKLY_ROOT}/edge/.env)"
  [[ "${prod_host}" == "${host}" ]] \
    || fail "PUBLIC_BASE_URL of ${env_file} (${host}) does not match PROD_HOST (${prod_host})"
  case "${flag:-}" in
    untested) log "WARNING: ${sha} was never deployed successfully in dev (untested)" ;;
    redeploy) log "redeploying a version that already ran in prod: no dev check" ;;
    *)
      grep -qE "^[^ ]+ ${sha} deployed$" "${SOUKLY_ROOT}/dev/deployments.log" 2> /dev/null \
        || fail "version never deployed successfully in dev: ${sha} (nothing changed)"
      ;;
  esac
fi

# ---- One deployment at a time per environment ----
exec 9> "${env_dir}/.deploy.lock"
if ! flock -n 9; then
  log "another ${env_name} deployment is running: waiting for it"
  flock 9
fi

started=$(date +%s)
log "deploying ${sha} to ${env_name} (${host})"

# ---- Images ----
missing=()
for image in "${IMAGES[@]}"; do
  if ! docker manifest inspect "ghcr.io/${owner}/soukly-${image}:${sha}" > /dev/null 2>&1; then
    missing+=("soukly-${image}")
  fi
done
((${#missing[@]} == 0)) || fail "images missing on GHCR for ${sha}: ${missing[*]} (nothing changed)"

# ---- Prod: database backup just before the deployment ----
if [[ "${env_name}" == prod ]]; then
  log "backing up the prod databases (predeploy-${sha})"
  "${SOUKLY_BACKUP_CMD}" "predeploy-${sha}" || fail "backup before deployment failed (nothing changed)"
fi

# ---- Compose files of the commit ----
install -d -m 0750 "${releases}" "${active}"
if [[ ! -f "${releases}/${sha}/infra/compose/compose.yml" ]]; then
  tmp="$(mktemp -d "${releases}/.download.XXXXXX")"
  # shellcheck disable=SC2059 # the URL template holds the %s of the SHA
  if ! curl -fsSL --retry 3 "$(printf "${SOUKLY_SOURCE_URL}" "${sha}")" \
    | tar -xz -C "${tmp}" --strip-components=1 --wildcards '*/infra/compose' '*/infra/postgres' '*/infra/garage'; then
    rm -rf "${tmp}"
    fail "cannot download the Compose files of ${sha}, or the commit has none (older than DEP-02); nothing changed"
  fi
  rm -rf "${releases:?}/${sha}"
  mv "${tmp}" "${releases}/${sha}"
fi
touch "${releases}/${sha}"

previous=""
if [[ -L "${env_dir}/current" ]]; then
  previous="$(basename "$(readlink "${env_dir}/current")")"
fi

compose_files() {
  local files=(-f "${active}/infra/compose/compose.yml")
  if [[ "${env_name}" == dev ]]; then files+=(-f "${active}/infra/compose/compose.dev.yml"); fi
  printf '%s\n' "${files[@]}"
}
compose() {
  local files
  mapfile -t files < <(compose_files)
  docker compose -p "soukly-${env_name}" --env-file "${env_file}" "${files[@]}" "$@"
}

# Fetches a URL from the VM through the edge Caddy, with the public host name and certificate.
fetch() { curl -fsS --max-time 10 --resolve "${host}:443:127.0.0.1" "https://${host}$1"; }

# Site checks after start-up, retried while Caddy and the gateway warm up.
check_site() {
  local deadline=$((SECONDS + CHECK_TIMEOUT)) status manifest
  while true; do
    status=0
    if ! fetch / > /dev/null; then
      status=1
    elif ! manifest="$(fetch /federation.manifest.json)" || [[ "${manifest}" != *"${public_url}"* ]]; then
      status=2
    else
      # Any answer of listing-service through the gateway; a 5xx means it is not reachable.
      code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 --resolve "${host}:443:127.0.0.1" \
        "https://${host}/api/listings?size=1" || true)"
      [[ "${code}" =~ ^[234][0-9][0-9]$ ]] || status=3
    fi
    ((status == 0)) && return 0
    if ((SECONDS >= deadline)); then
      case "${status}" in
        1) log "check failed: GET / did not answer 200" ;;
        2) log "check failed: /federation.manifest.json does not point to ${public_url}" ;;
        3) log "check failed: GET /api/listings?size=1 answered ${code:-nothing}" ;;
      esac
      return 1
    fi
    sleep 3
  done
}

# Starts the stack with the Compose files and images of one release, then checks it.
start_release() {
  local release="$1"
  # Copied in place (same paths at every deployment), so that the bind-mounted files keep their
  # path and the containers that did not change are not recreated.
  install -d "${active}/infra"
  cp -R "${releases}/${release}/infra/." "${active}/infra/"
  # Also the version shown by the services (APP_VERSION, see compose.yml).
  export IMAGE_TAG="${release}"
  log "pulling the images of ${release}"
  compose pull --quiet || return 1
  log "starting ${env_name} with ${release} (at most ${DEPLOY_TIMEOUT}s)"
  compose up -d --wait --wait-timeout "${DEPLOY_TIMEOUT}" --remove-orphans || return 1
  log "checking https://${host}"
  check_site
}

# Prints the last 200 lines of the services that are not running, not healthy or that failed.
show_failures() {
  local service state health code shown=false
  # "|" rather than a tab: read would merge consecutive tabs, and Health is empty for services
  # without a health check.
  while IFS='|' read -r service state health code; do
    if [[ "${state}" == exited && "${code}" == 0 ]]; then continue; fi
    if [[ "${state}" == running && ( -z "${health}" || "${health}" == healthy ) ]]; then continue; fi
    shown=true
    echo "::group::${service} (${state}${health:+, ${health}}${code:+, exit ${code}}): last 200 log lines"
    compose logs --no-color --tail 200 "${service}" 2>&1 || true
    echo "::endgroup::"
  done < <(compose ps -a --format '{{.Service}}|{{.State}}|{{.Health}}|{{.ExitCode}}' 2> /dev/null)
  if [[ "${shown}" == false ]]; then
    for service in gateway frontend; do
      echo "::group::${service}: last 200 log lines"
      compose logs --no-color --tail 200 "${service}" 2>&1 || true
      echo "::endgroup::"
    done
  fi
}

record() { printf '%s %s %s\n' "$(date -Iseconds)" "${sha}" "$1" >> "${env_dir}/deployments.log"; }

# Keeps the last releases of this environment (and always the current one), and deletes the local
# images that no release of any environment uses.
prune() {
  local current kept
  current="$(basename "$(readlink "${env_dir}/current")")"
  # shellcheck disable=SC2012 # release directories are named by SHA: no special characters
  ls -1t "${releases}" | tail -n +$((KEEP_RELEASES + 1)) | while read -r old; do
    [[ "${old}" == "${current}" ]] || rm -rf "${releases:?}/${old}"
  done
  kept=" $(find "${SOUKLY_ROOT}"/*/releases -mindepth 1 -maxdepth 1 -type d -printf '%f ' 2> /dev/null) "
  docker image ls --filter "reference=ghcr.io/${owner}/soukly-*" --format '{{.Repository}} {{.Tag}}' | while read -r repo tag; do
    [[ "${kept}" == *" ${tag} "* ]] || docker image rm "${repo}:${tag}" > /dev/null 2>&1 || true
  done
}

# ---- Deploy ----
if start_release "${sha}"; then
  ln -sfn "releases/${sha}" "${env_dir}/current"
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${sha}/" "${env_file}"
  record "deployed${flag:+ (${flag})}"
  prune
  log "deployed ${sha} in $(($(date +%s) - started))s"
  echo "RESULT: deployed ${sha}"
  exit 0
fi

log "the new version did not start correctly"
show_failures
if [[ -z "${previous}" || "${previous}" == "${sha}" || ! -d "${releases}/${previous}" ]]; then
  record "failed, no previous version"
  log "no previous version to return to"
  echo "RESULT: failed"
  exit 1
fi
log "returning to ${previous}"
if start_release "${previous}"; then
  record "failed, rolled back to ${previous}"
  log "${previous} runs again"
  echo "RESULT: rolled-back ${previous}"
  exit 2
fi
show_failures
record "failed, rollback to ${previous} failed"
log "the return to ${previous} failed too"
echo "RESULT: failed"
exit 1

#!/usr/bin/env bash
# Tests of infra/vm/deploy.sh (DEP-03) without a VM: docker and the site checks are replaced by
# stubs, the Compose files come from this checkout. Run from anywhere:
#   bash infra/vm/tests/deploy.test.sh
# Cases: success, failure with return to the previous version, failed site check, first deployment
# in failure, invalid SHA or environment, restricted key, missing images, lock, release pruning.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY="${HERE}/../deploy.sh"
REPO="$(cd "${HERE}/../../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

A=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
B=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
C=cccccccccccccccccccccccccccccccccccccccc

# ---- Stubs ----
mkdir -p "${WORK}/bin" "${WORK}/archives"
# docker: images exist unless their tag is in FAKE_MISSING; "compose up" fails when IMAGE_TAG is in
# FAKE_BAD; every call is appended to FAKE_CALLS.
cat > "${WORK}/bin/docker" <<'STUB'
#!/usr/bin/env bash
echo "docker $* [IMAGE_TAG=${IMAGE_TAG:-}]" >> "${FAKE_CALLS}"
case "$1 $2" in
  "manifest inspect") [[ " ${FAKE_MISSING:-} " != *" ${3##*:} "* ]] ;;
  "image ls") exit 0 ;;
  "image rm") exit 0 ;;
  compose*)
    for arg in "$@"; do
      case "${arg}" in
        up) [[ " ${FAKE_BAD:-} " != *" ${IMAGE_TAG} "* ]]; exit ;;
        ps)
          printf 'gateway\trunning\thealthy\t0\nredpanda-init\texited\t\t0\n'
          if [[ " ${FAKE_BAD:-} " == *" ${IMAGE_TAG} "* ]]; then
            printf 'listing-service\texited\t\t1\n'
          fi
          exit 0 ;;
        logs) echo "fake log lines of ${!#}"; exit 0 ;;
        pull) exit 0 ;;
      esac
    done ;;
  *) echo "unexpected docker call: $*" >&2; exit 99 ;;
esac
STUB
# curl: the site checks through Caddy (--resolve) answer as a healthy site unless IMAGE_TAG is in
# FAKE_SITE_DOWN; everything else (the archive download) is the real curl.
cat > "${WORK}/bin/curl" <<'STUB'
#!/usr/bin/env bash
if [[ " $* " == *" --resolve "* ]]; then
  [[ " ${FAKE_SITE_DOWN:-} " == *" ${IMAGE_TAG} "* ]] && { [[ " $* " == *"%{http_code}"* ]] && printf 502; exit 22; }
  case "$*" in
    *federation.manifest.json*) echo "{\"mfe\":\"${FAKE_PUBLIC_URL}/remotes/mfe/remoteEntry.json\"}" ;;
    *%{http_code}*) printf 404 ;;
    *) echo "<html></html>" ;;
  esac
  exit 0
fi
exec /usr/bin/curl "$@"
STUB
chmod +x "${WORK}/bin/docker" "${WORK}/bin/curl"

# Archives of the 3 commits: this checkout's infra directory.
for sha in "${A}" "${B}" "${C}" dddddddddddddddddddddddddddddddddddddddd eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee ffffffffffffffffffffffffffffffffffffffff 1111111111111111111111111111111111111111; do
  mkdir -p "${WORK}/src/soukly-${sha}"
  cp -R "${REPO}/infra" "${WORK}/src/soukly-${sha}/"
  tar -czf "${WORK}/archives/${sha}.tar.gz" -C "${WORK}/src" "soukly-${sha}"
done

export SOUKLY_ROOT="${WORK}/opt" FAKE_CALLS="${WORK}/calls" FAKE_PUBLIC_URL=https://dev.example.test
export SOUKLY_SOURCE_URL="file://${WORK}/archives/%s.tar.gz" CHECK_TIMEOUT=1 PATH="${WORK}/bin:${PATH}"
mkdir -p "${SOUKLY_ROOT}/dev"
cp "${REPO}/infra/compose/.env.example" "${SOUKLY_ROOT}/dev/.env"
sed -i "s|^PUBLIC_BASE_URL=.*|PUBLIC_BASE_URL=${FAKE_PUBLIC_URL}|" "${SOUKLY_ROOT}/dev/.env"

failures=0
run() { # run <expected exit> <name> <command...>: output in ${WORK}/out
  local expected="$1" name="$2" status
  shift 2
  : > "${FAKE_CALLS}"
  "$@" > "${WORK}/out" 2>&1
  status=$?
  if [[ "${status}" == "${expected}" ]]; then
    echo "ok   ${name}"
  else
    echo "FAIL ${name}: exit ${status}, expected ${expected}"
    sed 's/^/     | /' "${WORK}/out"
    failures=$((failures + 1))
  fi
}
check() { # check <name> <test...>
  local name="$1"
  shift
  if "$@"; then echo "ok   ${name}"; else echo "FAIL ${name}"; failures=$((failures + 1)); fi
}
out_has() { grep -qF -- "$1" "${WORK}/out"; }
current() { basename "$(readlink "${SOUKLY_ROOT}/dev/current" 2> /dev/null)"; }
no_compose_call() { ! grep -q 'docker compose' "${FAKE_CALLS}"; }

# ---- Arguments ----
run 1 "invalid SHA refused" bash "${DEPLOY}" dev "main; rm -rf /"
check "  nothing called" no_compose_call
run 1 "short SHA refused" bash "${DEPLOY}" dev "${A:0:7}"
run 1 "unknown environment refused" bash "${DEPLOY}" staging "${A}"
run 1 "restricted key: another command is refused" env SSH_ORIGINAL_COMMAND="bash -i" bash "${DEPLOY}" dev
run 1 "restricted key: no command (interactive login) is refused" env -u SSH_ORIGINAL_COMMAND bash "${DEPLOY}" dev
check "  nothing called" no_compose_call

# ---- First deployment in failure ----
FAKE_BAD="${A}" run 1 "first deployment in failure: no previous version" bash "${DEPLOY}" dev "${A}"
check "  result says failed" out_has "RESULT: failed"
check "  logs of the failing service shown" out_has "fake log lines of listing-service"
check "  no current version" test -z "$(current)"

# ---- Images missing ----
FAKE_MISSING="${A}" run 1 "missing images: stops before touching the stack" bash "${DEPLOY}" dev "${A}"
check "  missing images named" out_has "soukly-gateway"
check "  nothing called" no_compose_call

# ---- Success, through the restricted key (SHA in SSH_ORIGINAL_COMMAND) ----
run 0 "deploy A" env SSH_ORIGINAL_COMMAND="${A}" bash "${DEPLOY}" dev
check "  result says deployed" out_has "RESULT: deployed ${A}"
check "  current is A" test "$(current)" = "${A}"
check "  .env IMAGE_TAG is A" grep -qx "IMAGE_TAG=${A}" "${SOUKLY_ROOT}/dev/.env"
check "  started with IMAGE_TAG=A" grep -q "up -d --wait.*\[IMAGE_TAG=${A}\]" "${FAKE_CALLS}"
check "  compose files of the release" test -f "${SOUKLY_ROOT}/dev/active/infra/compose/compose.dev.yml"
check "  journal written" grep -q "${A} deployed" "${SOUKLY_ROOT}/dev/deployments.log"

# ---- Failure with return to A ----
FAKE_BAD="${B}" run 2 "deploy B that does not start: back to A" bash "${DEPLOY}" dev "${B}"
check "  result says rolled back to A" out_has "RESULT: rolled-back ${A}"
check "  logs of listing-service shown" out_has "fake log lines of listing-service"
check "  A started again" grep -q "up -d --wait.*\[IMAGE_TAG=${A}\]" "${FAKE_CALLS}"
check "  current is still A" test "$(current)" = "${A}"
check "  .env IMAGE_TAG is still A" grep -qx "IMAGE_TAG=${A}" "${SOUKLY_ROOT}/dev/.env"
check "  journal written" grep -q "${B} failed, rolled back to ${A}" "${SOUKLY_ROOT}/dev/deployments.log"

FAKE_SITE_DOWN="${C}" run 2 "deploy C whose site check fails: back to A" bash "${DEPLOY}" dev "${C}"
check "  failed check named" out_has "GET / did not answer 200"

FAKE_BAD="${B} ${A}" run 1 "return to A fails too" bash "${DEPLOY}" dev "${B}"
check "  result says failed" out_has "RESULT: failed"

# ---- Lock: a second deployment waits for the first ----
run 0 "deploy C" bash "${DEPLOY}" dev "${C}"
( exec 9> "${SOUKLY_ROOT}/dev/.deploy.lock"; flock 9; sleep 3 ) &
sleep 0.5
start=${SECONDS}
run 0 "deployment while another one holds the lock" bash "${DEPLOY}" dev "${A}"
check "  it waited" out_has "waiting for it"
check "  for the lock to be released" test $((SECONDS - start)) -ge 2
wait

# ---- Pruning: 5 releases kept ----
for sha in dddddddddddddddddddddddddddddddddddddddd eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee ffffffffffffffffffffffffffffffffffffffff 1111111111111111111111111111111111111111; do
  sleep 1 # distinct modification times
  bash "${DEPLOY}" dev "${sha}" > /dev/null 2>&1
done
check "5 releases kept" test "$(find "${SOUKLY_ROOT}/dev/releases" -mindepth 1 -maxdepth 1 -type d | wc -l)" -eq 5
check "  the oldest one deleted" test ! -d "${SOUKLY_ROOT}/dev/releases/${B}"

echo
if ((failures > 0)); then
  echo "${failures} check(s) failed"
  exit 1
fi
echo "all checks passed"

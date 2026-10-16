#!/bin/sh
# Entry point of the edge Caddy (DEP-02): enables the site file of each environment whose host is
# set (DEV_HOST, PROD_HOST), then starts Caddy. With PROD_HOST empty, no prod site is declared.
set -eu

enabled=/etc/caddy/sites
rm -rf "${enabled}"
mkdir -p "${enabled}"

for env_name in dev prod; do
  case "${env_name}" in
    dev) host="${DEV_HOST:-}" ;;
    prod) host="${PROD_HOST:-}" ;;
  esac
  if [ -z "${host}" ]; then
    echo "edge: no ${env_name} site (host empty)"
    continue
  fi
  # A host name only: no scheme, path, port or space.
  case "${host}" in
    *[!A-Za-z0-9.-]* | .* | *. | "")
      echo "edge: invalid ${env_name} host '${host}'" >&2
      exit 1
      ;;
  esac
  cp "/etc/caddy/sites-available/${env_name}.caddy" "${enabled}/"
  echo "edge: ${env_name} site enabled for ${host}"
done

exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile

#!/bin/sh
# Entry point of the front-end image (DEP-01): writes the federation manifest from
# PUBLIC_BASE_URL, then starts Caddy. Every remote found in /srv/remotes gets an entry:
#   { "mfe-account": "<PUBLIC_BASE_URL>/remotes/mfe-account/remoteEntry.json", ... }
# The legal pages (LEG-01) are copied to /srv/runtime/legal with their contact address:
# LEGAL_CONTACT_EMAIL, else privacy@<host of PUBLIC_BASE_URL>.
set -eu

if [ -z "${PUBLIC_BASE_URL:-}" ]; then
  echo "soukly-frontend: the environment variable PUBLIC_BASE_URL is missing (for example https://dev.203-0-113-10.sslip.io)." >&2
  exit 1
fi

base_url="${PUBLIC_BASE_URL%/}"
case "${base_url}" in
  http://* | https://*) ;;
  *)
    echo "soukly-frontend: PUBLIC_BASE_URL must start with http:// or https:// (got '${PUBLIC_BASE_URL}')." >&2
    exit 1
    ;;
esac
# The value is written into JSON as is: refuse anything that would need escaping.
case "${base_url}" in
  *[\"\\\ ]* | *"$(printf '\t')"*)
    echo "soukly-frontend: PUBLIC_BASE_URL must not contain spaces, quotes or backslashes." >&2
    exit 1
    ;;
esac

manifest=/srv/runtime/federation.manifest.json
{
  printf '{'
  separator=''
  for dir in /srv/remotes/*/; do
    name="$(basename "${dir}")"
    printf '%s\n  "%s": "%s/remotes/%s/remoteEntry.json"' "${separator}" "${name}" "${base_url}" "${name}"
    separator=','
  done
  printf '\n}\n'
} > "${manifest}"

echo "soukly-frontend: federation manifest written for ${base_url}"

host="${base_url#*://}"
host="${host%%/*}"
host="${host%%:*}"
contact_email="${LEGAL_CONTACT_EMAIL:-privacy@${host}}"
# The address is written into HTML and into a sed expression: a plain address only.
if ! printf '%s' "${contact_email}" | grep -Eq '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$'; then
  echo "soukly-frontend: LEGAL_CONTACT_EMAIL must be a plain email address (got '${contact_email}')." >&2
  exit 1
fi
rm -rf /srv/runtime/legal
cp -r /srv/legal /srv/runtime/legal
find /srv/runtime/legal -name '*.html' -exec sed -i "s/__LEGAL_CONTACT_EMAIL__/${contact_email}/g" {} +
echo "soukly-frontend: legal pages written with the contact address ${contact_email}"
exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile

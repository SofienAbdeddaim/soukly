#!/usr/bin/env bash
# Generates the RSA key pair that user-service signs access tokens with (AUTH-02), for one
# environment, on the VM (DEP-02). The keys never leave the VM and are never committed.
#
#   sudo bash infra/vm/gen-jwt-keys.sh dev
#   sudo bash infra/vm/gen-jwt-keys.sh prod
#
# Writes /opt/soukly/<env>/secrets/jwt-private.pem (PKCS#8 PEM) and jwt-public.pem, mode 600,
# owned by the UID of the service images (10001, DEP-01) so user-service can read them through a
# read-only mount. Existing keys are kept: delete them by hand to rotate (see infra/vm/README.md).
set -euo pipefail

SOUKLY_ROOT="${SOUKLY_ROOT:-/opt/soukly}"
SERVICE_UID=10001
KEY_BITS=3072

die() { printf '[gen-jwt-keys] ERROR: %s\n' "$*" >&2; exit 1; }

env_name="${1:-}"
[[ "${env_name}" == dev || "${env_name}" == prod ]] || die "usage: $0 <dev|prod>"
[[ $EUID -eq 0 ]] || die "run with sudo"
command -v openssl > /dev/null || die "openssl is missing"

secrets_dir="${SOUKLY_ROOT}/${env_name}/secrets"
private_key="${secrets_dir}/jwt-private.pem"
public_key="${secrets_dir}/jwt-public.pem"

install -d -m 0700 -o "${SERVICE_UID}" -g "${SERVICE_UID}" "${secrets_dir}"

if [[ -s "${private_key}" ]]; then
  echo "[gen-jwt-keys] ${private_key} already exists: kept"
else
  umask 077
  tmp="$(mktemp "${secrets_dir}/.jwt-private.XXXXXX")"
  openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:${KEY_BITS}" -out "${tmp}" 2> /dev/null
  mv "${tmp}" "${private_key}"
  echo "[gen-jwt-keys] ${private_key} generated (RSA ${KEY_BITS} bits)"
fi
openssl pkey -in "${private_key}" -pubout -out "${public_key}"

chown "${SERVICE_UID}:${SERVICE_UID}" "${private_key}" "${public_key}"
chmod 0600 "${private_key}" "${public_key}"
echo "[gen-jwt-keys] ${env_name}: keys ready in ${secrets_dir}"

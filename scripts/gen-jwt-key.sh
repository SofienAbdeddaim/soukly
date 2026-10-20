#!/usr/bin/env bash
# Generates the local RSA key pair that user-service signs its access tokens with (AUTH-02).
# Local development only: dev and prod keys are generated on the VM by infra/vm/gen-jwt-keys.sh.
#
#   scripts/gen-jwt-key.sh           # keeps an existing key
#   scripts/gen-jwt-key.sh --force   # replaces it (every issued access token becomes invalid)
#
# Writes .secrets/jwt/jwt-private.pem (PKCS#8 PEM, mode 600) and jwt-public.pem at the root of the
# repository. .secrets/ is ignored by git: never commit a private key, even a development one.
set -euo pipefail

KEY_BITS=2048

cd "$(dirname "$0")/.."
secrets_dir=".secrets/jwt"
private_key="${secrets_dir}/jwt-private.pem"
public_key="${secrets_dir}/jwt-public.pem"

command -v openssl > /dev/null || { echo "[gen-jwt-key] ERROR: openssl is missing" >&2; exit 1; }

mkdir -p "${secrets_dir}"
chmod 700 .secrets "${secrets_dir}"

if [[ -s "${private_key}" && "${1:-}" != "--force" ]]; then
  echo "[gen-jwt-key] ${private_key} already exists: kept (--force to replace it)"
else
  umask 077
  tmp="$(mktemp "${secrets_dir}/.jwt-private.XXXXXX")"
  openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:${KEY_BITS}" -out "${tmp}" 2> /dev/null
  mv "${tmp}" "${private_key}"
  echo "[gen-jwt-key] ${private_key} generated (RSA ${KEY_BITS} bits)"
fi
openssl pkey -in "${private_key}" -pubout -out "${public_key}"
chmod 600 "${private_key}" "${public_key}"
echo "[gen-jwt-key] keys ready in ${secrets_dir}"

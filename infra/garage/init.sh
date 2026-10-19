#!/usr/bin/env bash
# One-shot, idempotent Garage setup for local development (SETUP-02).
# Runs in the amazon/aws-cli image (bash, curl, jq, aws) and talks to:
#   - the Garage admin API v2 (layout, key import, bucket, permissions);
#   - the S3 API (PutBucketCors).
# GARAGE_WEBSITE=true also enables website access on the bucket (dev stack on the VM, DEP-02).
# Every step checks the current state first, so running it twice changes nothing.
set -euo pipefail

: "${GARAGE_ADMIN_TOKEN:?GARAGE_ADMIN_TOKEN is required}"
: "${S3_ACCESS_KEY_ID:?S3_ACCESS_KEY_ID is required}"
: "${S3_SECRET_ACCESS_KEY:?S3_SECRET_ACCESS_KEY is required}"
: "${S3_BUCKET:?S3_BUCKET is required}"
: "${S3_REGION:?S3_REGION is required}"

ADMIN_URL="${GARAGE_ADMIN_URL:-http://garage:3903}"
S3_URL="${GARAGE_S3_URL:-http://garage:3900}"
KEY_NAME="soukly-app"
CAPACITY="${GARAGE_CAPACITY:-10G}"
CORS_ORIGIN="${S3_CORS_ORIGIN:-http://localhost:4200}"

log() { echo "[garage-init] $*"; }

# admin <GET|POST> <endpoint> [json-body]
admin() {
  local method="$1" endpoint="$2" body="${3:-}"
  local args=(-fsS -X "$method" -H "Authorization: Bearer ${GARAGE_ADMIN_TOKEN}")
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" -d "$body")
  fi
  curl "${args[@]}" "${ADMIN_URL}/v2/${endpoint}"
}

log "Waiting for the admin API"
for _ in $(seq 1 30); do
  if admin GET GetClusterStatus >/dev/null 2>&1; then break; fi
  sleep 1
done
admin GET GetClusterStatus >/dev/null

# 1. Layout: give the single node a storage role if it has none yet.
layout_version="$(admin GET GetClusterLayout | jq -r '.version')"
if [[ "$layout_version" == "0" ]]; then
  node_id="$(admin GET GetClusterStatus | jq -r '.nodes[0].id')"
  log "Assigning layout to node ${node_id:0:16} (${CAPACITY})"
  capacity_bytes="$(numfmt --from=iec "$CAPACITY")"
  admin POST UpdateClusterLayout \
    "$(jq -nc --arg id "$node_id" --argjson cap "$capacity_bytes" \
      '{roles: [{id: $id, zone: "local", capacity: $cap, tags: []}]}')" >/dev/null
  admin POST ApplyClusterLayout '{"version": 1}' >/dev/null
else
  log "Layout already applied (version ${layout_version})"
fi

# 2. Application key, imported so its ID and secret are known in advance (.env).
if admin GET "GetKeyInfo?id=${S3_ACCESS_KEY_ID}" >/dev/null 2>&1; then
  log "Key ${KEY_NAME} already exists"
else
  log "Importing key ${KEY_NAME}"
  admin POST ImportKey \
    "$(jq -nc --arg id "$S3_ACCESS_KEY_ID" --arg secret "$S3_SECRET_ACCESS_KEY" --arg name "$KEY_NAME" \
      '{accessKeyId: $id, secretAccessKey: $secret, name: $name}')" >/dev/null
fi

# 3. Bucket.
if bucket_json="$(admin GET "GetBucketInfo?globalAlias=${S3_BUCKET}" 2>/dev/null)"; then
  log "Bucket ${S3_BUCKET} already exists"
else
  log "Creating bucket ${S3_BUCKET}"
  bucket_json="$(admin POST CreateBucket "$(jq -nc --arg alias "$S3_BUCKET" '{globalAlias: $alias}')")"
fi
bucket_id="$(jq -r '.id' <<<"$bucket_json")"

# 4. Read and write permissions for the key (setting them again is a no-op).
log "Granting read/write on ${S3_BUCKET} to ${KEY_NAME}"
admin POST AllowBucketKey \
  "$(jq -nc --arg bucket "$bucket_id" --arg key "$S3_ACCESS_KEY_ID" \
    '{bucketId: $bucket, accessKeyId: $key, permissions: {read: true, write: true, owner: false}}')" >/dev/null

# 5. CORS rule for direct uploads from the front. Garage only lets a bucket owner call
# PutBucketCors, so the key is made owner for this call and the owner flag is removed
# right after: the application key keeps read and write only. PutBucketCors replaces the
# whole configuration, so applying it again is a no-op.
set_owner() {
  admin POST "$1" \
    "$(jq -nc --arg bucket "$bucket_id" --arg key "$S3_ACCESS_KEY_ID" \
      '{bucketId: $bucket, accessKeyId: $key, permissions: {read: false, write: false, owner: true}}')" >/dev/null
}
log "Applying CORS rule for ${CORS_ORIGIN}"
set_owner AllowBucketKey
trap 'set_owner DenyBucketKey' EXIT
AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY_ID" AWS_SECRET_ACCESS_KEY="$S3_SECRET_ACCESS_KEY" \
  aws --endpoint-url "$S3_URL" --region "$S3_REGION" s3api put-bucket-cors \
  --bucket "$S3_BUCKET" \
  --cors-configuration "$(jq -nc --arg origin "$CORS_ORIGIN" \
    '{CORSRules: [{AllowedOrigins: [$origin], AllowedMethods: ["GET", "PUT", "HEAD"], AllowedHeaders: ["*"], MaxAgeSeconds: 3600}]}')"
set_owner DenyBucketKey
trap - EXIT

# 6. Optional website access, for public reads of the photos (dev stack on the VM, DEP-02).
# The edge Caddy only forwards listings/ to the website endpoint.
if [[ "${GARAGE_WEBSITE:-false}" == "true" ]]; then
  log "Enabling website access on ${S3_BUCKET}"
  admin POST "UpdateBucket?id=${bucket_id}" \
    '{"websiteAccess": {"enabled": true, "indexDocument": "index.html", "errorDocument": null}}' >/dev/null
fi

log "Done"

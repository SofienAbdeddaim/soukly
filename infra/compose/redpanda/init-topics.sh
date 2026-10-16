#!/usr/bin/env bash
# Creates the event topics of a Soukly environment and their dead-letter topics (DEP-02).
# Topic auto-creation is off outside the local machine, so every topic is declared here.
# Idempotent: an existing topic is left as it is. Runs in the Redpanda image (rpk), with the
# broker address in RPK_BROKERS.
set -euo pipefail

: "${RPK_BROKERS:?RPK_BROKERS is required}"

# topic partitions
TOPICS=(
  "soukly.listing-events 3"
  "soukly.user-events 3"
  "soukly.social-events 3"
  "soukly.listing-events.dlq 1"
  "soukly.user-events.dlq 1"
  "soukly.social-events.dlq 1"
)

for spec in "${TOPICS[@]}"; do
  read -r topic partitions <<< "${spec}"
  if rpk topic describe "${topic}" > /dev/null 2>&1; then
    echo "[redpanda-init] ${topic} already exists"
  else
    rpk topic create "${topic}" --partitions "${partitions}" --replicas 1
  fi
done

echo "[redpanda-init] Done"

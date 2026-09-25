#!/usr/bin/env bash
# Saves the demo's baseline — the state a reset goes back to — from ec1. Take it when the demo is as it
# should start, and nothing is moving (no process running, nothing in an outbox).
#
#   deploy/demo/snapshot.sh            # into ~/.local/share/ec-demo1/demo-baseline
#
# Opera and Salesforce are not in it: Opera is never touched by a reset; Salesforce is put back from
# the MDM's baseline (see reset.sh).
set -euo pipefail
cd "$(dirname "$0")"
. ./common.sh
P=$(pg_pod)
TMP=$(mktemp -d "${BASELINE}.new.XXXX" 2>/dev/null || { mkdir -p "$(dirname "$BASELINE")"; mktemp -d "${BASELINE}.new.XXXX"; })
for db in $DATABASES; do
  kubectl -n $NS exec "$P" -- sh -c "pg_dump -U \"\$POSTGRES_USER\" --clean --if-exists --no-owner --no-privileges -d $db" > "$TMP/$db.sql"
  echo "  $db: $(du -h "$TMP/$db.sql" | cut -f1)"
done
tables=$(for t in $ENGINE_TABLES; do printf -- "-t %s " "$t"; done)
kubectl -n $NS exec "$P" -- sh -c "pg_dump -U \"\$POSTGRES_USER\" --data-only --no-owner --no-privileges $tables -d $ENGINE_DB" > "$TMP/$ENGINE_DB.sql"
echo "  $ENGINE_DB (state tables): $(du -h "$TMP/$ENGINE_DB.sql" | cut -f1)"
date -u +%Y-%m-%dT%H:%M:%SZ > "$TMP/taken-at"
rm -rf "$BASELINE" && mv "$TMP" "$BASELINE"
echo "Baseline saved in $BASELINE ($(cat "$BASELINE/taken-at"))"

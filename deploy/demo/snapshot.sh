#!/usr/bin/env bash
# Saves the demo's baseline — the state a reset goes back to — from ec1. Take it when the demo is as it
# should start, and nothing is moving (no process running, nothing in an outbox).
#
#   deploy/demo/snapshot.sh            # into ~/.local/share/ec-demo1/demo-baseline
#
# Opera and Salesforce are not in it: Opera is never touched by a reset; Salesforce is put back from
# the MDM's baseline (see reset.sh). What is in it of Opera's is the context the baseline's reservations
# were written under (common.sh, opera_context): a reset puts it back, so they are found again.
set -euo pipefail
cd "$(dirname "$0")"
. ./common.sh
P=$(pg_pod)
TMP=$(mktemp -d "${BASELINE}.new.XXXX" 2>/dev/null || { mkdir -p "$(dirname "$BASELINE")"; mktemp -d "${BASELINE}.new.XXXX"; })
# Each dump is written and compressed in the pod, brought back checked (pod_get), and kept only when
# pg_dump finished it (dump_complete). A dump that cannot be had whole saves no baseline: the one there stays.
dump() { # dump <name> <pg_dump arguments...>
  local name=$1; shift
  kubectl -n $NS exec "$P" -- sh -c "pg_dump -U \"\$POSTGRES_USER\" --no-owner --no-privileges $* | gzip -c > /tmp/ec-snapshot-$name.sql.gz"
  if ! pod_get "/tmp/ec-snapshot-$name.sql.gz" "$TMP/$name.sql.gz" || ! dump_complete "$TMP/$name.sql.gz"; then
    echo "  $name: the dump did not come back whole — baseline NOT saved, the previous one stays"
    kubectl -n $NS exec "$P" -- rm -f "/tmp/ec-snapshot-$name.sql.gz"
    rm -rf "$TMP"
    exit 1
  fi
  kubectl -n $NS exec "$P" -- rm -f "/tmp/ec-snapshot-$name.sql.gz"
}
for db in $DATABASES; do
  dump "$db" --clean --if-exists -d "$db"
  echo "  $db: $(du -h "$TMP/$db.sql.gz" | cut -f1)"
done
tables=$(for t in $ENGINE_TABLES; do printf -- "-t %s " "$t"; done)
dump "$ENGINE_DB" --data-only $tables -d "$ENGINE_DB"
echo "  $ENGINE_DB (state tables): $(du -h "$TMP/$ENGINE_DB.sql.gz" | cut -f1)"
opera_context > "$TMP/opera-context"
echo "  Opera context: $(cat "$TMP/opera-context")"
date -u +%Y-%m-%dT%H:%M:%SZ > "$TMP/taken-at"
rm -rf "$BASELINE" && mv "$TMP" "$BASELINE"
echo "Baseline saved in $BASELINE ($(cat "$BASELINE/taken-at"))"

#!/usr/bin/env bash
# Offline tests for the baseline's integrity guards (common.sh, reset.sh): no cluster, a fake kubectl.
#
#   bash deploy/demo/test-baseline.sh
#
# What they pin down is the failure that left ec1's engine with processes and no steps: a dump that
# crossed kubectl exec's stream cut short, restored table by table, the error swallowed.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
FAILS=0
ok() { echo "ok   - $1"; }
ko() { echo "FAIL - $1"; FAILS=$((FAILS + 1)); }
check() { if eval "$2"; then ok "$1"; else ko "$1"; fi; }

# A fake kubectl: the "pod" is a directory; `cat` can be told to cut its output short a number of times.
mkdir -p "$T/bin" "$T/pod"
cat > "$T/bin/kubectl" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_LOG"
args=("$@")
for i in "${!args[@]}"; do [ "${args[$i]}" = "--" ] && cmd=("${args[@]:$((i + 1))}"); done
case "$*" in
  *"get pod -o name"*) echo "pod/ec-eventconductor-postgres-test"; exit 0 ;;
esac
case "${cmd[0]:-}" in
  cksum) cksum < "$FAKE_POD${cmd[1]}" | awk -v f="${cmd[1]}" '{print $1, $2, f}' ;;
  cat)
    left=$(cat "$FAKE_CUTS" 2>/dev/null || echo 0)
    if [ "$left" -gt 0 ]; then echo $((left - 1)) > "$FAKE_CUTS"; head -c 100 "$FAKE_POD${cmd[1]}"
    else cat "$FAKE_POD${cmd[1]}"; fi ;;
  sh) # `cat > file` from stdin (pod_put); cut short as above
    target=$(echo "${cmd[2]}" | sed -n "s/^cat > '\(.*\)'$/\1/p")
    if [ -n "$target" ]; then
      left=$(cat "$FAKE_CUTS" 2>/dev/null || echo 0)
      if [ "$left" -gt 0 ]; then echo $((left - 1)) > "$FAKE_CUTS"; head -c 100 > "$FAKE_POD$target"; cat > /dev/null
      else cat > "$FAKE_POD$target"; fi
    fi ;;
esac
EOF
chmod +x "$T/bin/kubectl"
export PATH="$T/bin:$PATH" FAKE_LOG="$T/kubectl.log" FAKE_POD="$T/pod" FAKE_CUTS="$T/cuts"
. "$HERE/common.sh"

# --- dump_complete -------------------------------------------------------------------------------
{ echo "COPY public.step_execution_entity (id) FROM stdin;"; seq 1 5000; echo '\.'; echo; echo "-- PostgreSQL database dump complete"; echo; } > "$T/full.sql"
head -c 20000 "$T/full.sql" > "$T/cut.sql"
gzip -c "$T/full.sql" > "$T/full.sql.gz"
gzip -c "$T/cut.sql" > "$T/cut.sql.gz"
check "a finished dump, compressed, is complete" 'dump_complete "$T/full.sql.gz"'
check "a finished dump, plain (older baseline), is complete" 'dump_complete "$T/full.sql"'
check "a dump cut short, compressed, is not" '! dump_complete "$T/cut.sql.gz"'
check "a dump cut short, plain, is not" '! dump_complete "$T/cut.sql"'
check "a compressed dump whose gzip stream is itself cut is not" \
  'head -c 2000 "$T/full.sql.gz" > "$T/cutgz.sql.gz"; ! dump_complete "$T/cutgz.sql.gz"'

# --- engine_restore_sql: the truncate and the rows in one script -----------------------------------
engine_restore_sql "$T/full.sql.gz" > "$T/engine.sql"
check "the engine's restore script empties every state table first" \
  '[ "$(head -1 "$T/engine.sql")" = "truncate $(echo $ENGINE_TABLES | tr " " ",");" ]'
check "and then loads the whole dump" 'tail -n +2 "$T/engine.sql" | cmp -s - "$T/full.sql"'

# --- pod_get / pod_put: a stream cut short is noticed and sent again --------------------------------
cp "$T/full.sql.gz" "$T/pod/dump.sql.gz"
echo 1 > "$FAKE_CUTS"
check "pod_get retries a transfer that came back cut short" \
  'pod_get /dump.sql.gz "$T/got.sql.gz" 2>/dev/null && cmp -s "$T/got.sql.gz" "$T/full.sql.gz"'
echo 5 > "$FAKE_CUTS"
check "pod_get fails when it never comes back whole" '! pod_get /dump.sql.gz "$T/got2.sql.gz" 2>/dev/null'
echo 1 > "$FAKE_CUTS"
check "pod_put retries a transfer that arrived cut short" \
  'pod_put "$T/full.sql.gz" /put.sql.gz 2>/dev/null && cmp -s "$T/pod/put.sql.gz" "$T/full.sql.gz"'
echo 5 > "$FAKE_CUTS"
check "pod_put fails when it never arrives whole" '! pod_put "$T/full.sql.gz" /put2.sql.gz 2>/dev/null'
echo 0 > "$FAKE_CUTS"

# --- reset.sh refuses an incomplete baseline before stopping anything ------------------------------
mkdir -p "$T/baseline"
date -u +%Y-%m-%dT%H:%M:%SZ > "$T/baseline/taken-at"
for db in $DATABASES; do cp "$T/full.sql.gz" "$T/baseline/$db.sql.gz"; done
cp "$T/cut.sql.gz" "$T/baseline/$ENGINE_DB.sql.gz"
: > "$FAKE_LOG"
out=$(EC_DEMO_BASELINE="$T/baseline" SF_DOMAIN=x SF_CLIENT_ID=x SF_CLIENT_SECRET=x bash "$HERE/reset.sh" 2>&1); rc=$?
check "reset.sh fails on a baseline whose engine dump was cut short" '[ $rc -ne 0 ]'
check "and says which dump" 'echo "$out" | grep -q "dump of $ENGINE_DB is incomplete"'
check "having stopped nothing" '! grep -q "scale" "$FAKE_LOG"'

cp "$T/full.sql.gz" "$T/baseline/$ENGINE_DB.sql.gz"
cp "$T/cut.sql" "$T/baseline/booking.sql"; rm "$T/baseline/booking.sql.gz"
: > "$FAKE_LOG"
out=$(EC_DEMO_BASELINE="$T/baseline" SF_DOMAIN=x SF_CLIENT_ID=x SF_CLIENT_SECRET=x bash "$HERE/reset.sh" 2>&1); rc=$?
check "reset.sh also refuses an older, plain dump cut short" '[ $rc -ne 0 ] && echo "$out" | grep -q "dump of booking is incomplete"'
check "having stopped nothing either" '! grep -q "scale" "$FAKE_LOG"'

echo
[ $FAILS -eq 0 ] && echo "All baseline tests passed" || { echo "$FAILS baseline test(s) FAILED"; exit 1; }

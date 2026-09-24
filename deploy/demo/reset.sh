#!/usr/bin/env bash
# Takes ec1's demo back to its baseline (snapshot.sh). What it does, in order:
#   1. notes which customers and change requests ec1 has now, to know what the demo created;
#   2. stops the services and the engine;
#   3. restores the services' databases and the engine's state tables;
#   4. Salesforce: deletes the contacts and Cases the demo created (ec1's only — the org is shared
#      with the local environment) and puts the baseline's contacts back as they were;
#   5. the MDM resumes Salesforce's events from now: the demo's are not replayed;
#   6. starts everything again.
# Opera is not touched: nothing is cancelled or deleted there. The demo is built to repeat on top of
# what it wrote (see docs/poc-acl/demo.md, «Resetear la demo»).
set -euo pipefail
cd "$(dirname "$0")"
. ./common.sh
[ -f "$BASELINE/taken-at" ] || { echo "No baseline in $BASELINE: take one with snapshot.sh"; exit 1; }
echo "Resetting ec1's demo to the baseline of $(cat "$BASELINE/taken-at")"

WORK=$(mktemp -d)
psql_value customer_mdm "select id from customer" > "$WORK/customers-now"
psql_value customer_mdm "select id from change_request" > "$WORK/requests-now" 2>/dev/null || : > "$WORK/requests-now"

echo "Stopping the services and the engine"
for d in $SERVICES; do kubectl -n $NS scale deploy/$d --replicas=0 >/dev/null; done
for d in $SERVICES; do kubectl -n $NS wait --for=delete pod -l app=$d --timeout=180s >/dev/null 2>&1 || true; done
sleep 5

echo "Restoring the databases"
for db in $DATABASES; do
  psql_in $db < "$BASELINE/$db.sql" > /dev/null && echo "  $db"
done
{ echo "truncate $(echo $ENGINE_TABLES | tr ' ' ',');"; cat "$BASELINE/$ENGINE_DB.sql"; } | psql_in $ENGINE_DB > /dev/null && echo "  $ENGINE_DB (state tables)"

echo "Salesforce"
psql_value customer_mdm "select id from customer" > "$WORK/customers-baseline"
psql_value customer_mdm "select id from change_request" > "$WORK/requests-baseline" 2>/dev/null || : > "$WORK/requests-baseline"
psql_value customer_mdm "select json_agg(json_build_object('id', id, 'firstName', first_name, 'lastName', last_name, 'email', email, 'phone', phone, 'birthDate', birth_date, 'nationality', nationality, 'documentType', document_type, 'documentNumber', document_number)) from customer where status <> 'MERGED' and salesforce_contact_id is not null" > "$WORK/contacts-baseline.json"
python3 salesforce.py "$WORK"
# The MDM resumes Salesforce's events from now, and its poll looks from now: what happened during
# the demo has just been undone, and must not come back.
echo "delete from salesforce_cursor where name like 'pubsub%'; update salesforce_cursor set until = now() where name = 'poll';" | psql_in customer_mdm

echo "Starting the services and the engine"
for d in $SERVICES; do kubectl -n $NS scale deploy/$d --replicas=1 >/dev/null; done
for d in $SERVICES; do kubectl -n $NS rollout status deploy/$d --timeout=420s | tail -1; done
rm -rf "$WORK"
echo "Done: ec1's demo is at its baseline."

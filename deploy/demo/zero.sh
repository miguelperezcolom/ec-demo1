#!/usr/bin/env bash
# Takes ec1 back to before anything was integrated, to walk the onboarding again step by step:
#   - the services lose what the integration made: reservations, integrations, mappings, the MDM's
#     customers, the front office's guests and stays, notifications, audit, the engine's processes;
#   - what is set up, not integrated, stays: the ERP's partners, the front office's rooms and
#     catalogs, the process definitions, content, users and Keycloak;
#   - Salesforce loses every contact and the MDM's change-request Cases.
# Opera is not touched, and never cleaned: what earlier runs wrote stays there. So each run gets a
# context of its own for the CRS locator in the reservations' external references — ECDEMO1-<MMddHHmm>,
# UTC, in the ec-demo-run ConfigMap — and a new random locator that repeats an old one does not find,
# and update, an old run's reservation. Within a run the integration still finds what it wrote by
# locator (under the run's context), so walking the onboarding again writes no reservation twice.
# Partners are found by their CorporateId and guest profiles through their reservation: no context.
#
# The demo's baseline is left alone, but reset.sh would bring the old state back: take a new one
# with snapshot.sh once the onboarding has been walked again.
set -euo pipefail
cd "$(dirname "$0")"
. ./common.sh
salesforce_env

echo "Stopping the services and the engine"
for d in $SERVICES; do kubectl -n $NS scale deploy/$d --replicas=0 >/dev/null; done
for d in $SERVICES; do kubectl -n $NS wait --for=delete pod -l app=$d --timeout=180s >/dev/null 2>&1 || true; done
sleep 5

echo "A new Opera context for this run"
set_opera_context "ECDEMO1-$(date -u +%m%d%H%M)"

wipe() { local db=$1; shift; echo "truncate $(echo "$@" | tr ' ' ',') cascade;" | psql_in "$db" && echo "  $db: $*"; }
echo "Emptying what the integration made"
wipe booking booking_entity crs_booking catalog_rate_plan outbox_message
wipe partners outbox_message
wipe crs_integration inbox_entry outbox_message
wipe integrations integration backfill_run outbox_message
wipe mapping cause mapping_entry partner_profile waiter waiter_cause outbox_message
wipe communication inbox_item inbox_seen notification resolution
wipe customer_mdm customer customer_source customer_xref consolidation change_request outbox_message
wipe front_office guest guest_kardex guest_preference stay stay_add_on stay_companion stay_incident folio folio_line
echo "update room set occupancy = 'FREE';" | psql_in front_office
wipe audit audit_record
wipe $ENGINE_DB $ENGINE_TABLES log_message_entity outbox_message_entity received_task sync_invocation

echo "Salesforce"
python3 - <<'EOF'
import json, sys, urllib.parse, urllib.request
from pathlib import Path
sys.path.insert(0, str(Path.cwd().parents[1] / "integration/customer-mdm-service/salesforce"))
import deploy
instance, access, _ = deploy.token()
def call(method, path):
    req = urllib.request.Request(instance + "/services/data/v67.0" + path, method=method,
                                 headers={"Authorization": "Bearer " + access})
    with urllib.request.urlopen(req) as r:
        raw = r.read()
        return json.loads(raw) if raw else None
def ids(soql):
    return [r["Id"] for r in call("GET", "/query?q=" + urllib.parse.quote(soql))["records"]]
cases = ids("SELECT Id FROM Case WHERE MdmRequestId__c != null")
for case in cases:
    call("DELETE", f"/sobjects/Case/{case}")
contacts = ids("SELECT Id FROM Contact")
for contact in contacts:
    call("DELETE", f"/sobjects/Contact/{contact}")
print(f"  deleted {len(cases)} Case(s) and {len(contacts)} contact(s)")
EOF
# The MDM resumes Salesforce's events from now: the deletions just made are not the MDM's to replay.
echo "delete from salesforce_cursor where name like 'pubsub%'; update salesforce_cursor set until = now() where name = 'poll';" | psql_in customer_mdm

echo "Starting the services and the engine"
for d in $SERVICES; do kubectl -n $NS scale deploy/$d --replicas=1 >/dev/null; done
for d in $SERVICES; do kubectl -n $NS rollout status deploy/$d --timeout=420s | tail -1; done
echo "Done: ec1 is at zero."

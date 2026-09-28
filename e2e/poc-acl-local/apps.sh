#!/usr/bin/env bash
# Starts the ten services of the PoC against the local infrastructure (infra.sh), each logging to
# logs/<name>.log. The PMS adapter points at opera-mock, with the double's fake credentials.
set -uo pipefail
R=$(cd "$(dirname "$0")/../.." && pwd)
L=$(dirname "$0")/logs
mkdir -p $L
# By port, not by name: an instance started by hand from another directory does not match a path
# pattern, survives, holds the port, and the fresh one fails to start behind it.
for port in 8108 8120 8121 8122 8123 8124 8125 8126 8127 8128 8129; do
  pid=$(ss -ltnp 2>/dev/null | grep ":$port " | grep -o "pid=[0-9]*" | head -1 | cut -d= -f2)
  [ -n "$pid" ] && kill $pid
done
sleep 3
DB=jdbc:postgresql://127.0.0.1:56432
COMMON="DB_USERNAME=workflow DB_PASSWORD=workflow KAFKA_BROKERS=127.0.0.1:59192"
# A module by its path under the repository (the modules are grouped by system); the jar and the
# log are named after its folder.
start() { local path=$1 name=$(basename $1); shift; env $COMMON "$@" nohup java -jar $R/$path/target/$name-0.1.0.jar > $L/$name.log 2>&1 & }
start systems/pms/opera-mock
start systems/crs/booking DB_URL=$DB/booking
start systems/erp DB_URL=$DB/partners
start integration/crs-integration-service DB_URL=$DB/crs_integration BOOKING_URL=http://localhost:8108 ERP_URL=http://localhost:8120
start integration/mapping-service DB_URL=$DB/mapping CRS_INTEGRATION_URL=http://localhost:8121 PMS_INTEGRATION_URL=http://localhost:8123 \
  INTEGRATIONS_URL=http://localhost:8126 IA_AGENT_URL=http://localhost:8095 RESEND_AFTER=20s
# No Opera credentials here: each hotel's integration carries its own (the scenario registers them).
start integration/pms-integration-service CRS_INTEGRATION_URL=http://localhost:8121 MAPPING_URL=http://localhost:8122 \
  INTEGRATIONS_URL=http://localhost:8126 RETRY_ALERT_AFTER=5s FRONT_OFFICE_URL=http://localhost:8128 FRONT_OFFICE_HOTELS=MRU01
# A throwaway key for the connection secrets: 32 zero bytes. A real one comes from a Secret.
start control-plane/integrations-service DB_URL=$DB/integrations CRS_INTEGRATION_URL=http://localhost:8121 \
  PMS_INTEGRATION_URL=http://localhost:8123 MAPPING_URL=http://localhost:8122 ERP_URL=http://localhost:8120 \
  INTEGRATIONS_CRYPTO_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= GATE_RECHECK=5s BACKFILL_TICK=1s \
  OPERA_GATEWAY_URL=http://localhost:8124 OPERA_APP_KEY=mock-app-key OPERA_CLIENT_ID=mock-client \
  OPERA_CLIENT_SECRET=mock-secret OPERA_ENTERPRISE_ID=RIUE
# The customer MDM cleans in the Salesforce org whose client credentials are in SF_ENV — only this
# service gets them. Without the file it still resolves identities; cleaning just waits.
#
# That org is the one ec1 uses too, and a Base Edition allows 15,000 API calls in a rolling 24 hours
# for everyone together. So the merge poll keeps its default (15 min, ~100 calls a day): the Pub/Sub
# events are what bring the merges, the poll is only a net. It was 20s here once — 4,320 polls a day,
# each stuck on a customer this stack did not have — and it spent the org's whole allowance, ec1's
# demo included. The MDM warns at startup below 5 min; /usage/salesforce says who spends what.
SF_ENV=${SF_ENV:-$HOME/.config/ec-demo1/salesforce.env}
SF=$([ -f "$SF_ENV" ] && grep -E '^SF_[A-Z_]+=' "$SF_ENV" | tr '\n' ' ')
start integration/customer-mdm-service DB_URL=$DB/customer_mdm CRS_INTEGRATION_URL=http://localhost:8121 $SF
start control-plane/audit-service DB_URL=$DB/audit
# The hotel's front office (MRU01's reservations are written into it too). Its UI logs in with the
# chain's Keycloak; its /api is what the connector calls.
start systems/front-office DB_URL=$DB/front_office
start control-plane/communication-service DB_URL=$DB/communication SMTP_HOST=localhost SMTP_PORT=51025 DEFAULT_RECIPIENT=ops@example.com
for port in 8124 8108 8120 8121 8122 8123 8125 8126 8127 8128 8129; do
  for i in $(seq 1 60); do curl -s localhost:$port/actuator/health 2>/dev/null | grep -q '"UP"' && break; sleep 2; done
  echo "$port $(curl -s localhost:$port/actuator/health | head -c 30)"
done
sleep 3

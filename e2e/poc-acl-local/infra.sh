#!/usr/bin/env bash
# Local end-to-end infrastructure for the PoC: Postgres (a database per service), Redpanda on tmpfs,
# and the real EventConductor orchestrator (ORCH_VERSION, default the one deploy/values pins), importing the definitions from the local ec-definitions
# branch. Everything on the host network so the services started with java -jar reach it all on
# localhost.
set -euo pipefail
docker rm -f poc-pg poc-rp poc-orch poc-mail >/dev/null 2>&1 || true
# A catch-all SMTP server with an inbox to look at: http://localhost:58025
docker run -d --name poc-mail -p 51025:1025 -p 58025:8025 axllent/mailpit >/dev/null
docker run -d --name poc-pg -e POSTGRES_USER=workflow -e POSTGRES_PASSWORD=workflow -e POSTGRES_DB=workflow \
  --tmpfs /var/lib/postgresql/data -p 56432:5432 postgres:16-alpine >/dev/null
docker run -d --name poc-rp --tmpfs /var/lib/redpanda/data:uid=101,gid=101,mode=0755 -p 59192:9092 \
  docker.redpanda.com/redpandadata/redpanda:v24.1.7 redpanda start --mode dev-container --smp 1 --memory 1G \
  --kafka-addr 0.0.0.0:9092 --advertise-kafka-addr 127.0.0.1:59192 >/dev/null
until docker exec poc-pg pg_isready -U workflow >/dev/null 2>&1; do sleep 1; done
sleep 2
for db in booking partners crs_integration mapping communication integrations customer_mdm front_office; do
  docker exec poc-pg psql -U workflow -d workflow -qc "create database $db" >/dev/null
done
docker run -d --name poc-orch --network host \
  -v ${EC_DEFINITIONS:-$(cd "$(dirname "$0")/../../../ec-definitions" && pwd)}:/defs:ro \
  -e SERVER_PORT=8105 -e WORKFLOW_MODE=kafka -e WORKFLOW_PERSISTENCE=jpa \
  -e DB_URL=jdbc:postgresql://127.0.0.1:56432/workflow -e DB_USERNAME=workflow -e DB_PASSWORD=workflow \
  -e KAFKA_BROKERS=127.0.0.1:59192 -e DDL_AUTO=none -e FLYWAY_ENABLED=true \
  -e WORKFLOW_SCHEMA_TABLE=flyway_schema_history -e DEFAULT_STEP_TIMEOUT_MS=900000 -e SECURITY_ENABLED=false \
  -e WORKFLOW_RETRY_BACKOFF_MAX_MS=5000 \
  -e WORKFLOW_GITIMPORT_REPOSITORIES_0_URL=file:///defs \
  -e WORKFLOW_GITIMPORT_REPOSITORIES_0_BRANCH=${EC_DEFINITIONS_BRANCH:-master} \
  -e WORKFLOW_GITIMPORT_REPOSITORIES_0_DIRECTORY=definitions/workflows \
  -e XDG_CONFIG_HOME=/tmp \
  miguelperezcolom/orchestrator-standalone-app:${ORCH_VERSION:-2.18.0} >/dev/null
echo "infra up"

#!/usr/bin/env bash
# Starts the six services of the PoC against the local infrastructure (infra.sh), each logging to
# logs/<name>.log. The PMS adapter points at opera-mock, with the double's fake credentials.
set -uo pipefail
R=$(cd "$(dirname "$0")/../.." && pwd)
L=$(dirname "$0")/logs
mkdir -p $L
# By port, not by name: an instance started by hand from another directory does not match a path
# pattern, survives, holds the port, and the fresh one fails to start behind it.
for port in 8108 8120 8121 8122 8123 8124; do
  pid=$(ss -ltnp 2>/dev/null | grep ":$port " | grep -o "pid=[0-9]*" | head -1 | cut -d= -f2)
  [ -n "$pid" ] && kill $pid
done
sleep 3
DB=jdbc:postgresql://127.0.0.1:56432
COMMON="DB_USERNAME=workflow DB_PASSWORD=workflow KAFKA_BROKERS=127.0.0.1:59192"
start() { local name=$1; shift; env $COMMON "$@" nohup java -jar $R/$name/target/$name-0.1.0.jar > $L/$name.log 2>&1 & }
start opera-mock
start booking DB_URL=$DB/booking
start partners DB_URL=$DB/partners
start crs-integration-service DB_URL=$DB/crs_integration BOOKING_URL=http://localhost:8108 PARTNERS_URL=http://localhost:8120
start mapping-service DB_URL=$DB/mapping CRS_INTEGRATION_URL=http://localhost:8121 PMS_INTEGRATION_URL=http://localhost:8123 IA_AGENT_URL=http://localhost:8095 RESEND_AFTER=20s
start pms-integration-service CRS_INTEGRATION_URL=http://localhost:8121 MAPPING_URL=http://localhost:8122 \
  OPERA_GATEWAY_URL=http://localhost:8124 OPERA_APP_KEY=mock-app-key OPERA_CLIENT_ID=mock-client \
  OPERA_CLIENT_SECRET=mock-secret OPERA_ENTERPRISE_ID=RIUE OPERA_HOTELS=RIUPMI,RIUCUN RETRY_ALERT_AFTER=30s
for port in 8124 8108 8120 8121 8122 8123; do
  for i in $(seq 1 60); do curl -s localhost:$port/actuator/health 2>/dev/null | grep -q '"UP"' && break; sleep 2; done
  echo "$port $(curl -s localhost:$port/actuator/health | head -c 30)"
done
sleep 3

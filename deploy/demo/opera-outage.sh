#!/usr/bin/env bash
# «Un proceso bloqueado espera, no falla», live: Opera goes away for the PMS connector, and comes back.
#
#   deploy/demo/opera-outage.sh on [--alert-after 2m] [--auto-off 15m]
#   deploy/demo/opera-outage.sh off [--restore-alert]
#   deploy/demo/opera-outage.sh status
#   deploy/demo/opera-outage.sh alert 10m        # only the alert threshold (restarts the connector)
#
# Opera is not touched. What is cut is the network: a NetworkPolicy (demo-opera-outage) lets
# pms-integration-service reach only the cluster's pods — Kafka, the services, DNS — so every call to
# OHIP (and its token endpoint) times out. The cluster's CNI (Cilium) enforces it; `on` checks it
# does, from inside the pod, before saying the outage is on. Nothing else of ec1 is affected.
#
# What the audience sees while it lasts: the step that writes to Opera fails and the engine retries
# it (ensure-guest-profile or upsert-reservation, attempts going up); the process waits, it does not
# fail. After RETRY_ALERT_AFTER of failing (10m by default, the manifest's) the connector raises a
# RETRYING_TOO_LONG notice to the inbox; --alert-after lowers it for the demo — that restarts the
# connector, so do it BEFORE the booking, and put 10m back after (off --restore-alert, or alert 10m).
#
# --auto-off (default 15m) lifts the cut by itself if nobody does: other people use ec1 too.
set -euo pipefail
cd "$(dirname "$0")"
. ./common.sh

POLICY=demo-opera-outage
DEPLOY=pms-integration-service
DEFAULT_ALERT=10m

opera_host() {
  kubectl -n "$NS" get secret ec-opera -o jsonpath='{.data.OPERA_GATEWAY_URL}' | base64 -d | sed -E 's#^https?://##; s#/.*$##'
}

alert_after() {
  local v
  v=$(kubectl -n "$NS" get deploy $DEPLOY -o jsonpath="{.spec.template.spec.containers[0].env[?(@.name=='RETRY_ALERT_AFTER')].value}")
  echo "${v:-$DEFAULT_ALERT}"
}

set_alert() {
  local want=$1
  if [ "$(alert_after)" = "$want" ]; then
    echo "  retry alert already after $want"
    return
  fi
  echo "  retry alert after $want (the connector restarts: ~1 min)"
  kubectl -n "$NS" set env deploy/$DEPLOY RETRY_ALERT_AFTER="$want" >/dev/null
  kubectl -n "$NS" rollout status deploy/$DEPLOY --timeout=300s | tail -1
}

# Whether the connector's pod reaches Opera now: a TCP connection to OHIP's 443, 5 s at most.
reaches_opera() {
  kubectl -n "$NS" exec deploy/$DEPLOY -c $DEPLOY -- nc -z -w 5 "$(opera_host)" 443 >/dev/null 2>&1
}

reaches_kafka() {
  kubectl -n "$NS" exec deploy/$DEPLOY -c $DEPLOY -- nc -z -w 5 redpanda 19092 >/dev/null 2>&1
}

seconds() { # 90s, 15m, 1h -> seconds
  local n=${1%[smh]} u=${1: -1}
  case $u in s) echo "$n" ;; m) echo $((n * 60)) ;; h) echo $((n * 3600)) ;; *) echo "$1" ;; esac
}

on() {
  local alert="" auto=15m
  while [ $# -gt 0 ]; do
    case $1 in
      --alert-after) alert=$2; shift 2 ;;
      --auto-off) auto=$2; shift 2 ;;
      *) echo "Unknown option $1"; exit 2 ;;
    esac
  done
  [ -n "$alert" ] && set_alert "$alert"
  local expires
  expires=$(date -u -v+"$(seconds "$auto")"S +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "+$(seconds "$auto") seconds" +%Y-%m-%dT%H:%M:%SZ)
  kubectl apply -f - >/dev/null <<EOF
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: $POLICY
  namespace: $NS
  labels: { app.kubernetes.io/part-of: ec-demo }
  annotations:
    ec-demo/what: "The demo's Opera outage (deploy/demo/opera-outage.sh): $DEPLOY reaches only the cluster's pods."
    ec-demo/expires-at: "$expires"
spec:
  podSelector:
    matchLabels: { app: $DEPLOY }
  policyTypes: [Egress]
  egress:
    - to:
        - namespaceSelector: {}
EOF
  echo "Opera outage ON at $(date -u +%H:%M:%SZ): $DEPLOY reaches only the cluster (NetworkPolicy $POLICY)"
  local i
  for i in 1 2 3 4 5 6; do
    if ! reaches_opera; then break; fi
    sleep 2
  done
  if reaches_opera; then
    echo "  FAIL: the pod still reaches $(opera_host):443 — the CNI is not enforcing it. Lifting the policy."
    kubectl -n "$NS" delete networkpolicy $POLICY >/dev/null
    exit 1
  fi
  echo "  checked from the pod: $(opera_host):443 unreachable; Kafka $(reaches_kafka && echo reachable || echo 'UNREACHABLE (!)')"
  echo "  retry alert after $(alert_after); lifts by itself at $expires unless 'off' comes first"
  # The safety net: lifts this very policy (the same expiry) if it is still there when it expires.
  nohup bash -c "sleep $(seconds "$auto"); \
    [ \"\$(kubectl -n $NS get networkpolicy $POLICY -o jsonpath='{.metadata.annotations.ec-demo/expires-at}' 2>/dev/null)\" = '$expires' ] \
    && kubectl -n $NS delete networkpolicy $POLICY >/dev/null 2>&1" >/dev/null 2>&1 &
}

off() {
  local restore=false
  [ "${1:-}" = "--restore-alert" ] && restore=true
  if kubectl -n "$NS" delete networkpolicy $POLICY >/dev/null 2>&1; then
    echo "Opera outage OFF at $(date -u +%H:%M:%SZ)"
  else
    echo "No Opera outage on"
  fi
  local i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if reaches_opera; then break; fi
    sleep 2
  done
  reaches_opera && echo "  checked from the pod: $(opera_host):443 reachable" || echo "  WARN: the pod does not reach Opera yet"
  if $restore; then
    set_alert $DEFAULT_ALERT
  elif [ "$(alert_after)" != "$DEFAULT_ALERT" ]; then
    echo "  retry alert still after $(alert_after): '$0 alert $DEFAULT_ALERT' puts the manifest's back (restarts the connector)"
  fi
}

status() {
  if kubectl -n "$NS" get networkpolicy $POLICY >/dev/null 2>&1; then
    echo "Opera outage: ON (until $(kubectl -n "$NS" get networkpolicy $POLICY -o jsonpath='{.metadata.annotations.ec-demo/expires-at}'))"
  else
    echo "Opera outage: off"
  fi
  echo "The connector reaches $(opera_host):443: $(reaches_opera && echo yes || echo no)"
  echo "Retry alert after: $(alert_after)"
}

case "${1:-status}" in
  on) shift; on "$@" ;;
  off) shift; off "$@" ;;
  status) status ;;
  alert) set_alert "${2:?duration, e.g. 2m}" ;;
  *) sed -n '2,20p' "$0"; exit 2 ;;
esac

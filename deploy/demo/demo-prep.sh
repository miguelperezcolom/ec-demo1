#!/usr/bin/env bash
# One command to get ec1 ready for a demo or a rehearsal (docs/poc-acl/demo.md, grabacion-demo.md).
#
#   deploy/demo/demo-prep.sh [--zero]              # [reset to zero, then] the health checks
#   deploy/demo/demo-prep.sh health                # only the checks: a PASS/FAIL table
#   deploy/demo/demo-prep.sh contracts             # only the task contracts: every task a definition
#                                                  #   references served by a live worker on its topic
#   deploy/demo/demo-prep.sh seed returning-customer [--create]
#                                                  # flow 2: whom to type in the booking wizard (same name
#                                                  #   and phone as a flow-1 holder, another email);
#                                                  #   --create makes that booking through the API instead
#   deploy/demo/demo-prep.sh seed arriving-today   # flow 4: a booking arriving today, in Opera and the
#                                                  #   front office, for the no show
#   deploy/demo/demo-prep.sh seed arriving-opera-today
#                                                  # the check-in / check-out: a booking arriving on Opera's
#                                                  #   business date (XMAR's does not follow the calendar;
#                                                  #   Opera checks in nothing else), and its clean rooms
#   deploy/demo/demo-prep.sh seed walk-in          # flow 5: nothing to create; the data to type
#
# Without --zero nothing is reset. --zero runs zero.sh first (ec1 and Salesforce's contacts to zero, a
# new Opera context: ~3 min) — flow 1 starts there, with no integration. Every seed is safe to run
# again: what it made is found by its tag (the booking's comments, «demo-prep:<seed>») and reused.
#
# The checks: every deployment Ready; the engine up; every task its definitions reference served by a
# live worker on its topic (check-contracts.sh --cluster); Opera's token and property XMAR readable (GET);
# Salesforce's token (GET) and its daily API allowance (WARN under SF_API_RESERVE calls left, 1000 by
# default; FAIL when spent — docs/poc-acl/demo.md); the MDM's customers pending or failed in
# Salesforce; MRU01's integration (none after zero.sh: that is flow 1's start); the dictionary and
# the open causes; no Opera outage left on (opera-outage.sh) and its alert threshold; the Opera
# context; the front office's free rooms. Exit status 1 if any check FAILs.
set -euo pipefail
cd "$(dirname "$0")"

case "${1:-}" in
  --zero)
    echo "== zero.sh: ec1 and Salesforce to zero"
    bash ./zero.sh
    echo
    echo "== Health"
    exec python3 ec1.py health
    ;;
  ""|health)
    exec python3 ec1.py health
    ;;
  contracts)
    exec python3 ec1.py contracts
    ;;
  seed)
    shift
    exec python3 ec1.py seed "$@"
    ;;
  -h|--help|help)
    sed -n '2,30p' "$0"
    ;;
  *)
    echo "Unknown: $1"; sed -n '2,30p' "$0"; exit 2
    ;;
esac

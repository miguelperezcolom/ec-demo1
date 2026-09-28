#!/usr/bin/env bash
# Every task a definition in ec-definitions references has a worker that serves it, on its topic.
#
#   deploy/demo/check-contracts.sh                 # ec-definitions at master (what ec1 imports)
#   deploy/demo/check-contracts.sh --ref <ref>     # at a branch, tag or commit
#   deploy/demo/check-contracts.sh --definitions <checkout>
#   deploy/demo/check-contracts.sh --cluster       # what the engine in ec1 imported, and live workers
#
# The workers' side is contracts/workers/<service>.tasks, which each worker service's build
# (ServedTasksTest) keeps equal to its TaskRegistrations. Exit status 1 on a gap. See
# check_contracts.py, and demo-prep.sh health, which runs the --cluster form.
set -euo pipefail
exec python3 "$(dirname "$0")/check_contracts.py" "$@"

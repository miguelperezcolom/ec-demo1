#!/usr/bin/env bash
# Runs the intranet suite against the local kind cluster (deploy/local/up.sh, *.localhost:8800).
#
# The two passwords it needs are read from the local credentials file and exported under the names
# the suite reads — never printed, never written anywhere else:
#   E2E_DEMO_PASSWORD   ← DEMO_PASSWORD        (the Keycloak user `demo`, and the first seeded employees)
#   E2E_USERS_PASSWORD  ← TEST_USERS_PASSWORD  (the seeded employees: ana.perez, carlos.diaz, isabel.torres…)
#
# Usage, from e2e/:   ./intranet/run-local.sh [playwright args]     e.g. --project=vaadin, -g "Portal"
set -euo pipefail
CREDENTIALS="${EC_DEMO1_CREDENTIALS:-$HOME/.local/share/ec-demo1-local/credentials.env}"
if [[ ! -r "$CREDENTIALS" ]]; then
  echo "no credentials file at $CREDENTIALS (set EC_DEMO1_CREDENTIALS)" >&2
  exit 1
fi
E2E_DEMO_PASSWORD="$(set -a; . "$CREDENTIALS"; printf '%s' "${DEMO_PASSWORD:-}")"
E2E_USERS_PASSWORD="$(set -a; . "$CREDENTIALS"; printf '%s' "${TEST_USERS_PASSWORD:-}")"
export E2E_DEMO_PASSWORD E2E_USERS_PASSWORD
cd "$(dirname "$0")/.."
exec npx playwright test --config intranet.config.ts "$@"

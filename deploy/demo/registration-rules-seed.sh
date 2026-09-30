#!/usr/bin/env bash
# The demo's sample registration rules — EXAMPLES, not legal advice: which of a guest's data a
# destination requires at the desk, so the front office has something to apply. Created through the
# registration-rules service's own API (the same path as the console), so they are published on
# registration-rules and every front office takes them, and audited as "demo-seed".
#
#   deploy/demo/registration-rules-seed.sh            # ec1: through a port-forward to the service
#   RULES_URL=http://localhost:8132 deploy/demo/registration-rules-seed.sh   # a local one
#   deploy/demo/registration-rules-seed.sh --force    # even if the service already has rules
#
# Once seeded, a check-in at MRU01 asks for the nationality, the birth date and the document of every
# pax (the scanner reads them), and the document's expiry and issuing country of a holder from outside
# the EU — refused, or forced with a reason, until the kárdex has them. Deactivate a rule on the control
# console (Registro → Reglas de registro) to stop applying it.
set -euo pipefail
cd "$(dirname "$0")/../.."

FORCE=false
[ "${1:-}" = "--force" ] && FORCE=true

PF=""
cleanup() { [ -n "$PF" ] && kill "$PF" 2>/dev/null || true; }
trap cleanup EXIT

URL=${RULES_URL:-}
if [ -z "$URL" ]; then
  PORT=${RULES_PORT:-18132}
  kubectl -n ec-demo1 port-forward svc/registration-rules "$PORT:8132" >/dev/null 2>&1 &
  PF=$!
  URL="http://localhost:$PORT"
  for _ in $(seq 1 30); do curl -sf "$URL/rules" >/dev/null 2>&1 && break; sleep 1; done
fi

existing=$(curl -sf "$URL/rules" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))')
if [ "$existing" != "0" ] && [ "$FORCE" != "true" ]; then
  echo "The service already has $existing rule(s): nothing seeded (--force to add the samples anyway)."
  exit 0
fi

post() {
  curl -sf -X POST "$URL/rules" -H 'Content-Type: application/json' -H 'X-User-Name: demo-seed' -d "$1" \
    | python3 -c 'import json,sys; r=json.load(sys.stdin); print("  ", r["id"], r["scope"], r["scopeCode"], "·", r["name"])'
}

echo "Seeding the sample registration rules at $URL"
# Mauritius (MRU01's country): every guest's document, nationality and birth date.
post '{"name":"Mauricio · registro de huéspedes (ejemplo)","scope":"COUNTRY","scopeCode":"MU",
  "nationalityMatch":"ANY","role":"ANY",
  "requiredFields":["DOCUMENT_TYPE","DOCUMENT_NUMBER","NATIONALITY","BIRTH_DATE"],
  "moments":["CHECK_IN","ONLINE_CHECK_IN"],
  "legalBasis":"Ejemplo · registro de huéspedes de Mauricio"}'
# Travellers from outside the EU, at MRU01: the document's expiry and issuing country of the holder.
post '{"name":"MRU01 · viajeros de fuera de la UE (ejemplo)","scope":"HOTEL","scopeCode":"MRU01",
  "nationalityMatch":"NOT_IN","nationalities":["EU","MU"],"role":"HOLDER",
  "requiredFields":["DOCUMENT_EXPIRY","DOCUMENT_ISSUING_COUNTRY"],
  "moments":["CHECK_IN","ONLINE_CHECK_IN"],
  "legalBasis":"Ejemplo · control de documentos de viaje"}'
# Spain (PMI01's country), 14 and over: the traveller's registration data set.
post '{"name":"España · registro de viajeros, 14 años o más (ejemplo)","scope":"COUNTRY","scopeCode":"ES",
  "nationalityMatch":"ANY","minAge":14,"role":"ANY",
  "requiredFields":["DOCUMENT_TYPE","DOCUMENT_NUMBER","DOCUMENT_ISSUING_COUNTRY","NATIONALITY","BIRTH_DATE","SEX",
                    "ADDRESS","CITY","POSTAL_CODE","COUNTRY_OF_RESIDENCE","SIGNATURE"],
  "moments":["CHECK_IN","ONLINE_CHECK_IN"],
  "legalBasis":"Ejemplo inspirado en el RD 933/2021 · SES.Hospedajes"}'
# Spain, under 14: who they are and the adult responsible for them.
post '{"name":"España · menores de 14 (ejemplo)","scope":"COUNTRY","scopeCode":"ES",
  "nationalityMatch":"ANY","maxAge":13,"role":"ANY",
  "requiredFields":["NATIONALITY","BIRTH_DATE","GUARDIAN"],
  "moments":["CHECK_IN","ONLINE_CHECK_IN"],
  "legalBasis":"Ejemplo inspirado en el RD 933/2021 · parentesco del menor"}'
# Spain, travellers from outside the EU: the document's expiry.
post '{"name":"España · viajeros de fuera de la UE (ejemplo)","scope":"COUNTRY","scopeCode":"ES",
  "nationalityMatch":"NOT_IN","nationalities":["EU"],"role":"ANY",
  "requiredFields":["DOCUMENT_EXPIRY"],
  "moments":["CHECK_IN","ONLINE_CHECK_IN"],
  "legalBasis":"Ejemplo · control de documentos de viaje"}'
echo "Done: see them on the control console, Registro → Reglas de registro."

#!/usr/bin/env python3
"""Puts Salesforce back for a demo reset, from what reset.sh leaves in WORK:

- deletes the Cases of change requests ec1 made after the baseline, and the contacts of customers
  ec1 created after it — ec1's only: the org is shared with the local environment, whose contacts
  ec1 does not know;
- writes the baseline customers' data back onto their contacts (Salesforce is the master; a demo
  may have changed them).

Credentials as deploy.py's: ~/.config/ec-demo1/salesforce.env."""
import json, sys, urllib.parse, urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "customer-mdm-service/salesforce"))
import deploy  # noqa: E402

work = Path(sys.argv[1])
lines = lambda name: {l.strip() for l in (work / name).read_text().splitlines() if l.strip()}
instance, access, _ = deploy.token()


def call(method, path, body=None):
    req = urllib.request.Request(instance + "/services/data/v67.0" + path, None if body is None else json.dumps(body).encode(),
                                 method=method, headers={"Authorization": "Bearer " + access, "Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def ids(soql):
    return [r["Id"] for r in call("GET", "/query?q=" + urllib.parse.quote(soql)).get("records", [])]


def quoted(values):
    return ",".join("'" + v.replace("\\", "\\\\").replace("'", "\\'") + "'" for v in values)


new_requests = sorted(lines("requests-now") - lines("requests-baseline"))
new_customers = sorted(lines("customers-now") - lines("customers-baseline"))
cases = ids(f"SELECT Id FROM Case WHERE MdmRequestId__c IN ({quoted(new_requests)})") if new_requests else []
for i in range(0, len(new_customers), 100):
    chunk = new_customers[i:i + 100]
    cases += ids(f"SELECT Id FROM Case WHERE MdmId__c IN ({quoted(chunk)})")
for case in sorted(set(cases)):
    call("DELETE", f"/sobjects/Case/{case}")
contacts = []
for i in range(0, len(new_customers), 100):
    contacts += ids(f"SELECT Id FROM Contact WHERE MDM_Id__c IN ({quoted(new_customers[i:i + 100])})")
for contact in contacts:
    call("DELETE", f"/sobjects/Contact/{contact}")
print(f"  deleted {len(set(cases))} Case(s) and {len(contacts)} contact(s) the demo created")

restored = 0
raw = (work / "contacts-baseline.json").read_text().strip()
for c in json.loads(raw) if raw and raw != "" else []:
    fields = {"FirstName": c["firstName"], "LastName": c["lastName"] or "?", "Email": c["email"], "Phone": c["phone"],
              "Birthdate": c["birthDate"], "Nationality__c": c["nationality"], "Document_Type__c": c["documentType"],
              "Document_Number__c": c["documentNumber"]}
    req = urllib.request.Request(f"{instance}/services/data/v67.0/sobjects/Contact/MDM_Id__c/{urllib.parse.quote(c['id'])}",
                                 json.dumps(fields).encode(), method="PATCH",
                                 headers={"Authorization": "Bearer " + access, "Content-Type": "application/json",
                                          "Sforce-Duplicate-Rule-Header": "allowSave=true"})
    urllib.request.urlopen(req).read()
    restored += 1
print(f"  {restored} baseline contact(s) written back as the baseline has them")

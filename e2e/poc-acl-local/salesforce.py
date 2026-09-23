"""The customer MDM's round trip through the real Salesforce org, against the local stack (after
scenario.py, which leaves PMI01 active and its codes mapped). Needs the org's client credentials
(SF_ENV, default ~/.config/ec-demo1/salesforce.env) and its metadata deployed
(customer-mdm-service/salesforce/deploy.py).

Two bookings of the same guest, spelt differently and with different emails: nothing certain links
them, so they are two provisional customers. Salesforce flags them as possible duplicates; a steward
merges them — here, through the API; in the demo, on Salesforce's screen — and the merge comes back
to the MDM, which keeps the survivor and carries its code to the absorbed guest's profile in Opera."""
import json, sys, time, urllib.parse, urllib.request
from pathlib import Path
import functools; print = functools.partial(print, flush=True)

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "customer-mdm-service/salesforce"))
sys.argv = sys.argv[:1]
import deploy  # the MDM's own credentials and REST helpers

BOOKING, MDM, OPERA = "http://localhost:8108", "http://localhost:8127", "http://localhost:8124"
TAG = time.strftime("%H%M%S")


def call(method, url, body=None):
    req = urllib.request.Request(url, json.dumps(body).encode() if body is not None else None, method=method,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def until(what, check, timeout=120, every=2):
    deadline = time.time() + timeout
    while time.time() < deadline:
        value = check()
        if value:
            print(f"  ✔ {what}")
            return value
        time.sleep(every)
    print(f"  ✘ {what}: not within {timeout}s"); sys.exit(1)


def book(first, last, email):
    return call("POST", BOOKING + "/bookings", {"hotelCode": "PMI01", "booking": {
        "channelCode": "TTOO", "partnerCode": "NORDTRAVEL", "externalReference": f"SF-{TAG}-{last}",
        "arrival": "2026-11-20", "departure": "2026-11-23",
        "holder": {"firstName": first, "lastName": last, "email": email, "nationality": "ES"},
        "rooms": [{"roomTypeCode": "DBL", "ratePlanCode": "TTOO", "boardCode": "AD", "adults": 1, "childrenAges": [],
                   "guests": [{"firstName": first, "lastName": last, "type": "Adult"}]}]}})["id"]


def crm_reference(locator):
    for p in call("GET", OPERA + "/_mock/profiles"):
        refs = {r.get("idContext"): r.get("id") for r in p.get("externalReferences", [])}
        if refs.get("RIUCRS") == "HOLDER-" + locator:
            return refs.get("CRM")


instance, access, _ = deploy.token()


def soql(q):
    return deploy.call(instance, access, "GET", "/query?q=" + urllib.parse.quote(q))["records"]


print("1. The same guest twice, spelt differently: two provisional customers")
surname = "Ortega" + TAG
l1 = book("Inés", surname, f"ines.{TAG}@example.com")
l2 = book("Ines", surname, f"ines.{TAG}@exmaple.com")
c1 = until(f"{l1}'s guest profile carries its customer code", lambda: crm_reference(l1))
c2 = until(f"{l2}'s too, a different one", lambda: crm_reference(l2))
assert c1 != c2, "nothing certain links them: they must be two customers"

print("2. Salesforce has them, and flags them as possible duplicates")
contacts = until("both are contacts, by their MDM id",
                 lambda: (lambda r: r if len(r) == 2 else None)(soql(f"SELECT Id, MDM_Id__c FROM Contact WHERE MDM_Id__c IN ('{c1}','{c2}')")))
by_mdm = {c["MDM_Id__c"]: c["Id"] for c in contacts}
until("a duplicate record set pairs them (rule MDM_Possible_Duplicate)",
      lambda: len(soql("SELECT Id FROM DuplicateRecordItem WHERE RecordId IN ('%s','%s')" % (by_mdm[c1], by_mdm[c2]))) == 2,
      timeout=60)

print("3. A steward merges them, keeping the first")
envelope = f'''<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:urn="urn:partner.soap.sforce.com" xmlns:urn1="urn:sobject.partner.soap.sforce.com">
<soapenv:Header><urn:SessionHeader><urn:sessionId>{access}</urn:sessionId></urn:SessionHeader></soapenv:Header>
<soapenv:Body><urn:merge><urn:request><urn:masterRecord><urn1:type>Contact</urn1:type><urn1:Id>{by_mdm[c1]}</urn1:Id></urn:masterRecord>
<urn:recordToMergeIds>{by_mdm[c2]}</urn:recordToMergeIds></urn:request></urn:merge></soapenv:Body></soapenv:Envelope>'''
answer = urllib.request.urlopen(urllib.request.Request(instance + "/services/Soap/u/67.0", envelope.encode(),
                                                       headers={"Content-Type": "text/xml", "SOAPAction": '""'})).read().decode()
assert "<success>true</success>" in answer, answer[:500]
merged_at = time.time()

print("4. The merge comes back, and the absorbed guest's profile in Opera takes the survivor's code")
until(f"{l2}'s guest profile now carries {c1}", lambda: crm_reference(l2) == c1, timeout=120)
print(f"   {time.time() - merged_at:.1f}s from the merge in Salesforce to the profile in Opera")
golden = call("GET", f"{MDM}/customers/{c2}")
assert golden["id"] == c1 and golden["status"] == "CONSOLIDATED" and c2 in golden["aliases"], golden
print(f"   {c2} now answers as {c1} ({golden['status']}), reservations {golden['reservations']}")
print("\nOK")

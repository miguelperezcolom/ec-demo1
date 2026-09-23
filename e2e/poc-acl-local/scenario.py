"""End-to-end scenario of the CRS -> PMS integration PoC against the local stack (infra.sh + apps.sh).
Every step checks what it expects and says what it saw; the first failed check stops the run."""
import json, time, urllib.request, subprocess, sys
import functools; print = functools.partial(print, flush=True)  # progress as it happens, not at the end

BOOKING, MAPPING, OPERA, INTEGRATIONS = "http://localhost:8108", "http://localhost:8122", "http://localhost:8124", "http://localhost:8126"

def call(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()

def until(what, check, timeout=120, every=2):
    deadline = time.time() + timeout
    while time.time() < deadline:
        value = check()
        if value:
            print(f"  ✔ {what}")
            return value
        time.sleep(every)
    print(f"  ✘ {what}: not within {timeout}s"); sys.exit(1)

def sql(q):
    return subprocess.run(["docker", "exec", "poc-pg", "psql", "-U", "workflow", "-d", "workflow", "-At", "-c", q],
                          capture_output=True, text=True).stdout.strip()

def causes():
    return {c["key"]: c["waiting"] for c in call("GET", MAPPING + "/causes")[1]}

def approve(type_, source, target, hotel=None, attributes=None):
    _, entry = call("POST", MAPPING + "/entries/proposals?by=e2e", {"type": type_, "hotelCode": hotel, "sourceCode": source,
                                                                     "targetCode": target, "attributes": attributes or {}})
    status, _ = call("POST", f"{MAPPING}/entries/{entry['id']}/approve?by=e2e")
    assert status == 200, status

def opera_reservation(locator):
    for r in call("GET", OPERA + "/_mock/reservations")[1]:
        if any(x["id"] == locator for x in r.get("externalReferences", [])):
            return r
    return None

def udf(r):
    return r["userDefinedFields"]["numericUDFs"][0]["value"]

def integration(hotel):
    return next((i for i in call("GET", INTEGRATIONS + "/integrations")[1] if i["crsHotelCode"] == hotel), None)

def at(hotel, status):
    return lambda: (integration(hotel) or {}).get("status") == status

def register(hotel, property_):
    # Only the hotel and the property: the connection comes from the chain's, as the form does.
    status, body = call("POST", INTEGRATIONS + "/integrations?by=e2e",
                        {"crsHotelCode": hotel, "pmsHotelCode": property_, "name": "Riu " + hotel})
    assert status == 201, (status, body)
    return body["id"]

print("1. The seeded partners wait for their profile type to be mapped")
until("four partner causes open", lambda: len([k for k in causes() if "PARTNER_TYPE" in k]) == 4)
for src, tgt in [("TRAVEL_AGENT", "Agent"), ("TOUR_OPERATOR", "Agent"), ("ONLINE_AGENCY", "Source"), ("COMPANY", "Company")]:
    approve("PARTNER_TYPE", src, tgt)

print("2. A booking of a hotel with no integration is held, and nothing reaches Opera")
_, created = call("POST", BOOKING + "/bookings", {"hotelCode": "PMI01", "booking": {
    "channelCode": "TTOO", "partnerCode": "NORDTRAVEL", "externalReference": "NT-2026-0042",
    "arrival": "2026-10-09", "departure": "2026-10-12",
    "holder": {"firstName": "Ana", "lastName": "García", "email": "ana@example.com", "nationality": "ES"},
    "rooms": [{"roomTypeCode": "DBL", "ratePlanCode": "TTOO", "boardCode": "AD", "adults": 2, "childrenAges": [7],
               "guests": [{"firstName": "Ana", "lastName": "García", "type": "Adult"},
                          {"firstName": "Leo", "lastName": "García", "type": "Child", "age": 7}]}],
    "comments": "Late arrival"}})
loc = created["id"]; print(f"  booking {loc}")
call("POST", f"{BOOKING}/bookings/{loc}/payments", {"type": "Deposit", "methodCode": "VISA", "amount": 150})
expected = {"CHANNEL/TTOO", "ROOM_TYPE/DBL", "RATE_PLAN/TTOO", "BOARD/AD", "PAYMENT_METHOD/VISA"}
until("held on the inactive integration and on the hotel's codes, the same causes for every process",
      lambda: "INTEGRATION_INACTIVE/PMI01" in causes()
      and {k.split("PMI01/", 1)[1] for k in causes() if k.startswith("MISSING_MAPPING/PMI01")} >= expected)
print("  waiting:", {k: v for k, v in causes().items() if "PMI01" in k})
assert opera_reservation(loc) is None, "nothing may reach Opera before the integration is active"

print("3. The hotel's integration is onboarded, gate by gate")
pmi = register("PMI01", "RIUPMI")
until("the connection verified, the catalogues contrasted: waiting for the mapping", at("PMI01", "MAPPING_PENDING"), timeout=60)
until("partners projected once there is a verified connection", lambda: len(call("GET", OPERA + "/_mock/profiles")[1]) == 4)
until("NORDTRAVEL's profile recorded", lambda: call("GET", MAPPING + "/partner-profiles/NORDTRAVEL")[0] == 200)
approve("CHANNEL", "TTOO", "TOUROP", attributes={"marketCode": "TOUR"})
approve("ROOM_TYPE", "DBL", "STDK", hotel="PMI01"); approve("RATE_PLAN", "TTOO", "TOPKG")
approve("BOARD", "AD", "BKFST"); approve("PAYMENT_METHOD", "VISA", "VA")
print("  still pending (the full contrast is noisy):", integration("PMI01")["pendingMappings"])
assert call("POST", f"{INTEGRATIONS}/integrations/{pmi}/approve-mapping?by=e2e")[0] == 200
r = until("the backfill projects the reservation before the activation", lambda: opera_reservation(loc), timeout=120)
until("ready to activate once the window is covered", at("PMI01", "READY_TO_ACTIVATE"), timeout=60)
i = integration("PMI01")
print(f"  backfill: {i['backfill']['status']} {i['backfill']['dispatched']} projected; history: {len(i['history'])} entries")
assert "INTEGRATION_INACTIVE/PMI01" in causes(), "live traffic still waits until a person activates"
assert call("POST", f"{INTEGRATIONS}/integrations/{pmi}/activate?by=e2e")[0] == 200
until("active, and what waited on the activation resumes", lambda: at("PMI01", "ACTIVE")() and "INTEGRATION_INACTIVE/PMI01" not in causes(), timeout=60)
assert sql("select status from process_entity where workflow_definition_id='alta-integracion' and business_key like 'alta-integracion:" + pmi + "%'") == "COMPLETED"

print("3b. A property nobody configured in Opera stops its onboarding until it is")
cun = register("CUN01", "RIUNEW")
until("pending configuration", at("CUN01", "PENDING_CONFIGURATION"), timeout=60)
call("POST", OPERA + "/_mock/properties/RIUNEW/configure")
until("configured in Opera: the onboarding goes on to the mapping", at("CUN01", "MAPPING_PENDING"), timeout=60)

r = until("the reservation in Opera", lambda: opera_reservation(loc))
until("the CRS knows where it landed", lambda: (call("GET", f"{BOOKING}/bookings/{loc}")[1].get("pmsReference") or {}).get("reservationId"))
rid = r["reservationIdList"][0]["id"]
until("Opera holds the CRS's latest version", lambda: udf(opera_reservation(loc)) == call("GET", f"{BOOKING}/bookings/{loc}")[1]["version"], timeout=60)
r = opera_reservation(loc); rate = r["roomStay"]["roomRates"][0]
print(f"  Opera {rid}: {rate['roomType']} {rate['ratePlanCode']} {rate['sourceCode']}/{rate['marketCode']} fixedRate={rate['fixedRate']}"
      f" nights={[n['base']['amountBeforeTax'] for n in rate['rates']['rate']]} packages={[p['packageCode'] for p in r['reservationPackages']]}"
      f" profile={r['reservationProfiles']['reservationProfile'][0]['reservationProfileType']} routing={r.get('routingInstructions')}"
      f" refs={[x['id'] + '@' + x['idContext'] for x in r['externalReferences']]} UDF={udf(r)}")
deposits = call("GET", f"{OPERA}/_mock/calls")[1]
assert rate["roomType"] == "STDK" and rate["ratePlanCode"] == "TOPKG" and rate["marketCode"] == "TOUR"
assert r["reservationProfiles"]["reservationProfile"][0]["reservationProfileType"] == "TravelAgent"
assert r.get("routingInstructions"), "a NO_FRONT partner routes the stay to its window"
assert sql("select count(*) from process_entity where workflow_definition_id='proyectar-reserva' and status='RUNNING'") == "0"
print("  every proyectar-reserva process finished:", sql("select status, count(*) from process_entity where workflow_definition_id='proyectar-reserva' group by 1"))

print("4. A modification is written over, in order, and the deposit is not applied twice")
_, b = call("GET", f"{BOOKING}/bookings/{loc}")
before = b["version"]
body = {"channelCode": "TTOO", "partnerCode": "NORDTRAVEL", "externalReference": "NT-2026-0042",
        "arrival": "2026-10-09", "departure": "2026-10-13", "holder": b["holder"],
        "rooms": [{"roomTypeCode": "DBL", "ratePlanCode": "TTOO", "boardCode": "AD", "adults": 2, "childrenAges": [7], "guests": []}],
        "comments": "Late arrival, one more night"}
assert call("PUT", f"{BOOKING}/bookings/{loc}", body)[0] == 200
until("Opera has the extra night and the new version", lambda: (lambda x: x and x["roomStay"]["departureDate"] == "2026-10-13" and udf(x) == before + 1)(opera_reservation(loc)))
dep_posts = [c for c in call("GET", OPERA + "/_mock/calls")[1] if c["method"] == "POST" and c["path"].endswith("/depositPayments")]
print(f"  deposit posted {len(dep_posts)} time(s)"); assert len(dep_posts) == 1

print("5. Opera fails for a while: the write is retried until it goes through")
call("POST", OPERA + "/_mock/faults?status=503&pathContains=/reservations&count=4")
body["comments"] = "Written through an outage"
call("PUT", f"{BOOKING}/bookings/{loc}", body)
until("the change reached Opera after the 503s", lambda: (lambda x: x and x.get("comments") and x["comments"][0]["comment"]["text"]["value"] == "Written through an outage")(opera_reservation(loc)), timeout=180)
print("  503s answered:", len([c for c in call("GET", OPERA + "/_mock/calls")[1] if c["status"] == 503]))

print("6. Opera refuses: the process waits on a named cause instead of failing")
approve("ROOM_TYPE", "JSU", "JRST", hotel="PMI01")
refused = []
for i in range(21):
    _, c = call("POST", BOOKING + "/bookings", {"hotelCode": "PMI01", "booking": {"channelCode": "TTOO", "partnerCode": "NORDTRAVEL",
        "arrival": "2026-11-02", "departure": "2026-11-04", "holder": {"firstName": "Guest", "lastName": f"N{i}"},
        "rooms": [{"roomTypeCode": "JSU", "ratePlanCode": "TTOO", "boardCode": "AD", "adults": 2, "childrenAges": [], "guests": []}]}})
    refused.append(c["id"])
key = until("a PMS_REJECTED cause for the 21st junior suite", lambda: next((k for k in causes() if k.startswith("PMS_REJECTED/")), None), timeout=180)
print("  ", key)
until("twenty junior suites in Opera", lambda: len([x for x in call("GET", OPERA + "/_mock/reservations")[1] if x["roomStay"]["roomRates"][0]["roomType"] == "JRST"]) == 20, timeout=120)

print("7. Cancelling frees the room; resolving the cause lets the refused one through")
first = refused[0]
assert call("POST", f"{BOOKING}/bookings/{first}/cancel", {"reasonCode": "CLI"})[0] == 200
approve("CANCELLATION_REASON", "CLI", "CUSTREQ")
until("the cancellation reached Opera", lambda: (lambda x: x and x["reservationStatus"] == "Cancelled")(opera_reservation(first)), timeout=120)
blocked = key.split("/")[2]
call("POST", MAPPING + "/causes/resolve?key=" + urllib.request.quote(key) + "&by=e2e")
until(f"{blocked} reached Opera once the cause was resolved", lambda: opera_reservation(blocked), timeout=120)

print("8. A cancellation of a reservation the PMS does not have yet waits for it")
_, c = call("POST", BOOKING + "/bookings", {"hotelCode": "PMI01", "booking": {"channelCode": "WEB",
    "arrival": "2026-12-01", "departure": "2026-12-03", "holder": {"firstName": "Late", "lastName": "Channel"},
    "rooms": [{"roomTypeCode": "DBL", "ratePlanCode": "TTOO", "boardCode": "AD", "adults": 2, "childrenAges": [], "guests": []}]}})
web = c["id"]
call("POST", f"{BOOKING}/bookings/{web}/cancel", {"reasonCode": "CLI"})
until("both wait: the projection for channel WEB, the cancellation for the projection",
      lambda: "MISSING_MAPPING/PMI01/CHANNEL/WEB" in causes() and f"NOT_YET_PROJECTED/PMI01/{web}" in causes(), timeout=120)
approve("CHANNEL", "WEB", "WEBDIR", attributes={"marketCode": "LEIS"})
until("projected and then cancelled in Opera", lambda: (lambda x: x and x["reservationStatus"] == "Cancelled")(opera_reservation(web)), timeout=180)

print("9. People were told")
def mails():
    return [m["Subject"] for m in call("GET", "http://localhost:58025/api/v1/messages")[1].get("messages", [])]
until("mail for new causes, a write retried too long, a refusal and onboardings that need someone",
      lambda: all(any(kind in s for s in mails()) for kind in ["[CAUSE_OPENED]", "[RETRYING_TOO_LONG]", "[PMS_REJECTED]", "[INTEGRATION_NEEDS_ATTENTION]"]), timeout=60)
print("  ", len(mails()), "mails, e.g.", mails()[:3])

print("\nOK — processes:", sql("select workflow_definition_id||' '||status||' '||count(*) from process_entity group by workflow_definition_id, status order by 1").replace("\n", " | "))
print("Opera calls:", len(call("GET", OPERA + "/_mock/calls")[1]))

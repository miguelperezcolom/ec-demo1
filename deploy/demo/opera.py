#!/usr/bin/env python3
"""Reads Opera (OHIP) for the demo scripts — GET only, nothing is ever written.

The connection is the chain's, from the cluster's ec-opera Secret (OPERA_* in the environment win);
no credential is printed.

  opera.py token                       # a token is granted (prints its lifetime)
  opera.py property XMAR               # the property is readable (prints its name)
  opera.py locator XMAR ABC123 [ctx]   # the reservations Opera holds under this CRS locator
                                       #   (context: ec-demo-run's, ECDEMO1 by default), as JSON
  opera.py rate-plans XMAR [text]      # the property's rate plans, filtered by a text
  opera.py rate-plan XMAR CODE         # one rate plan, whole (JSON)
  opera.py availability XMAR 2026-11-12 2026-11-16   # the room types Opera would sell those nights
  opera.py business-date XMAR          # Opera's business date: a check-in is only for arrivals on it
  opera.py rooms XMAR STDK             # the rooms of a type: housekeeping (Clean, Inspected, Dirty…) and
                                       #   front office status (Vacant, Occupied)
  opera.py reservation XMAR 39486034   # one reservation: status, room, and whether Opera would check it
                                       #   in now (verifyCheckIns, read-only)
  opera.py folios XMAR 39486034        # the folios its check-out closed (Opera's folio history): the invoice

Exit status 0 when the answer is what was asked for, 1 when not (no reservation, unreadable…).
"""
import base64
import sys
sys.dont_write_bytecode = True
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request

NS = "ec-demo1"


def secret(key):
    if os.environ.get(key):
        return os.environ[key]
    raw = subprocess.run(["kubectl", "-n", NS, "get", "secret", "ec-opera", "-o", f"jsonpath={{.data.{key}}}"],
                         check=True, capture_output=True, text=True).stdout
    return base64.b64decode(raw).decode()


GATEWAY = secret("OPERA_GATEWAY_URL").rstrip("/")
APP_KEY = secret("OPERA_APP_KEY")


def token():
    basic = base64.b64encode(f"{secret('OPERA_CLIENT_ID')}:{secret('OPERA_CLIENT_SECRET')}".encode()).decode()
    body = urllib.parse.urlencode({"grant_type": "client_credentials", "scope": "urn:opc:hgbu:ws:__myscopes__"}).encode()
    req = urllib.request.Request(GATEWAY + "/oauth/v1/tokens", data=body, method="POST", headers={
        "x-app-key": APP_KEY, "enterpriseId": secret("OPERA_ENTERPRISE_ID"), "Authorization": "Basic " + basic,
        "Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def get(hotel, path, access):
    req = urllib.request.Request(GATEWAY + path, headers={
        "x-app-key": APP_KEY, "x-hotelid": hotel, "Authorization": "Bearer " + access, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def context():
    try:
        ctx = subprocess.run(["kubectl", "-n", NS, "get", "configmap", "ec-demo-run", "-o",
                              "jsonpath={.data.OPERA_EXTERNAL_SYSTEM}"], capture_output=True, text=True).stdout.strip()
    except OSError:
        ctx = ""
    return ctx or "ECDEMO1"


def summary(res):
    ids = {i.get("type"): i.get("id") for i in res.get("reservationIdList", [])}
    room = (res.get("roomStay") or {})
    rates = (room.get("roomRates") or [{}])
    udfs = {u.get("name"): u.get("value") for u in (res.get("userDefinedFields") or {}).get("numericUDFs", [])}
    return {"reservationId": ids.get("Reservation"), "confirmation": ids.get("Confirmation"),
            "status": res.get("reservationStatus"), "arrival": room.get("arrivalDate"),
            "departure": room.get("departureDate"), "roomType": rates[0].get("roomType"),
            "ratePlan": rates[0].get("ratePlanCode"), "udfs": udfs}


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    what = argv[0]
    t = token()
    if what == "token":
        print(f"token granted, valid for {t.get('expires_in')}s")
        return 0
    access = t["access_token"]
    if what == "property":
        hotel = argv[1]
        info = get(hotel, f"/ent/config/v1/hotels/{hotel}", access)
        name = (info.get("hotelDetails") or info.get("hotelConfigInfo") or {}).get("hotelName") \
            or json.dumps(info)[:80]
        print(f"{hotel}: {name}")
        return 0
    if what == "locator":
        hotel, locator = argv[1], argv[2]
        ctx = argv[3] if len(argv) > 3 else context()
        found = get(hotel, f"/rsv/v1/hotels/{hotel}/reservations?externalReferenceIds={urllib.parse.quote(locator)}"
                           f"&externalSystemCodes={urllib.parse.quote(ctx)}", access)
        infos = (found.get("reservations") or {}).get("reservationInfo") or []
        out = []
        for info in infos:
            rid = next((i["id"] for i in info.get("reservationIdList", []) if i.get("type") == "Reservation"), None)
            whole = get(hotel, f"/rsv/v1/hotels/{hotel}/reservations/{rid}", access)
            for res in (whole.get("reservations") or {}).get("reservation") or []:
                out.append(summary(res))
        print(json.dumps({"hotel": hotel, "locator": locator, "context": ctx, "count": len(out), "reservations": out},
                         indent=2))
        return 0 if out else 1
    if what == "rate-plans":
        hotel, text = argv[1], (argv[2].lower() if len(argv) > 2 else "")
        offset = 0
        while True:
            page = get(hotel, f"/rtp/v1/ratePlans?hotelId={hotel}&limit=200&offset={offset}", access)
            plans = (page.get("ratePlanShortInfoList") or {}).get("ratePlanShortInfo") or []
            for p in plans:
                d = p.get("primaryDetails") or {}
                line = (f"{p.get('ratePlanCode')}\t{(d.get('description') or {}).get('defaultText', '')}\t"
                        f"sells {d.get('startSellDate', '?')}..{d.get('endSellDate', '?')}\t"
                        f"{(p.get('classifications') or {}).get('rateCategory', '').strip()}")
                if text in line.lower():
                    print(line)
            if len(plans) < 200:
                return 0
            offset += 200
    if what == "availability":
        hotel, start, end = argv[1], argv[2], argv[3]
        found = get(hotel, f"/par/v1/hotels/{hotel}/availability?roomStayStartDate={start}&roomStayEndDate={end}"
                           f"&adults=2&roomStayQuantity=1", access)
        types = sorted({r.get("roomType") for h in found.get("hotelAvailability", []) for s in h.get("roomStays", [])
                        for r in s.get("roomRates", [])} - {None})
        print(f"{hotel} {start}..{end}: " + (", ".join(types) or "nothing available"))
        return 0 if types else 1
    if what == "business-date":
        hotel = argv[1]
        ctx = get(hotel, f"/ent/config/v1/hotels/{hotel}/operaContext", access).get("hotelContext") or {}
        print(f"{hotel}: business date {ctx.get('businessDate')} (timezone {ctx.get('timezone')})")
        return 0 if ctx.get("businessDate") else 1
    if what == "rooms":
        hotel, room_type = argv[1], argv[2]
        found = get(hotel, f"/fof/v1/hotels/{hotel}/rooms?roomType={urllib.parse.quote(room_type)}&limit=100", access)
        rooms = (found.get("hotelRoomsDetails") or {}).get("room") or []
        for r in rooms:
            st = (r.get("housekeeping") or {}).get("roomStatus") or {}
            print(f"{r.get('roomId')}\t{st.get('roomStatus')}\t{st.get('frontOfficeStatus')}")
        return 0 if rooms else 1
    if what == "reservation":
        hotel, rid = argv[1], argv[2]
        whole = get(hotel, f"/rsv/v1/hotels/{hotel}/reservations/{rid}", access)
        res = ((whole.get("reservations") or {}).get("reservation") or [{}])[0]
        out = summary(res)
        out["room"] = ((res.get("roomStay") or {}).get("currentRoomInfo") or {}).get("roomId")
        out["comments"] = [((c.get("comment") or {}).get("text") or {}).get("value")
                           for c in res.get("comments") or []]
        try:
            verify = get(hotel, f"/fof/v1/hotels/{hotel}/reservations/{rid}/verifyCheckIns", access)
            out["checkInNow"] = "possible"
            out["suggestedRooms"] = [n for r in verify.get("reservation") or []
                                     for n in ((r.get("roomStay") or {}).get("currentRoomInfo") or {})
                                     .get("suggestedRoomNumbers") or []][:5]
        except urllib.error.HTTPError as e:
            out["checkInNow"] = f"no ({e.code}): " + e.read()[:200].decode(errors="replace")
        print(json.dumps(out, indent=2))
        return 0
    if what == "folios":
        hotel, rid = argv[1], argv[2]
        found = get(hotel, f"/csh/v1/hotels/{hotel}/folioHistory?reservationIdId={rid}&reservationIdType=Reservation"
                           f"&checkOut=true&limit=20", access)
        for f in found.get("folioHistory") or []:
            amount = f.get("folioAmount") or {}
            print(f"{f.get('folioNoWithPrefix')}\t{f.get('folioStatus')}\t{f.get('start')}\t{amount.get('amount')} "
                  f"{amount.get('currencyCode')}\twindow {f.get('folioWindowNo')}")
        return 0
    if what == "rate-plan":
        hotel, code = argv[1], argv[2]
        print(json.dumps(get(hotel, f"/rtp/v1/hotels/{hotel}/ratePlans/{urllib.parse.quote(code)}", access), indent=2))
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except urllib.error.HTTPError as e:
        print(f"Opera answered {e.code}: {e.read()[:300].decode(errors='replace')}", file=sys.stderr)
        sys.exit(1)
    except (urllib.error.URLError, OSError) as e:
        print(f"Opera unreachable: {e}", file=sys.stderr)
        sys.exit(1)

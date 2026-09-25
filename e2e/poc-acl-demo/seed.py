"""Seeds the demo: N future bookings of one hotel in the CRS (booking), the kind of mix a hotel that
already sells has — web, call centre, tour operators and an OTA; several boards and rate plans; some
with a deposit collected centrally. Deterministic for a given seed, so a rehearsal and the demo see
the same data.

    python3 e2e/poc-acl-demo/seed.py --hotel CUN01 --count 100
    python3 e2e/poc-acl-demo/seed.py --booking http://localhost:18108 --hotel CUN01   # a port-forward

It only talks to the booking API. Whether the bookings then reach Opera depends on the hotel's
integration: with none, or an inactive one, they wait — which is the point of the demo."""
import argparse, datetime, json, random, sys, urllib.request

FIRST = ["Ana", "Luis", "Marta", "Jonas", "Emma", "Pierre", "Giulia", "Sofía", "Liam", "Noah", "Olivia", "Hugo",
         "Ingrid", "Mateo", "Lucía", "Erik", "Chloé", "Carlos", "Nora", "Pablo", "Hanna", "Tom", "Laura", "Diego"]
LAST = ["García", "Müller", "Smith", "Rossi", "Dubois", "Johansson", "López", "Martín", "Andersson", "Brown",
        "Fernández", "Schmidt", "Moreau", "Nilsson", "Ruiz", "Bianchi", "Hansen", "Torres", "Wilson", "Sanz"]
COUNTRIES = ["ES", "DE", "GB", "IT", "FR", "SE", "NO", "US", "MX", "NL"]

# (weight, channel, partner, rate plans)
CHANNELS = [
    (40, "WEB", None, ["BAR", "NRF", "EB"]),
    (15, "CC", None, ["BAR", "EB"]),
    (15, "TTOO", "NORDTRAVEL", ["TTOO"]),
    (10, "TTOO", "VIAJESSOL", ["TTOO"]),
    (15, "OTA", "BOOKIT", ["BAR", "NRF"]),
    (5, "RECEP", None, ["BAR"]),
]
BOARDS = [(15, "SA"), (35, "AD"), (25, "MP"), (5, "PC"), (20, "TI")]


def call(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read().decode()
        return json.loads(raw) if raw.strip().startswith(("{", "[")) else raw


def weighted(rng, options):
    return rng.choices(options, weights=[o[0] for o in options])[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--booking", default="http://localhost:8108")
    ap.add_argument("--hotel", default="CUN01")
    ap.add_argument("--count", type=int, default=100)
    ap.add_argument("--seed", type=int, default=2026)
    args = ap.parse_args()

    rng = random.Random(args.seed)
    catalog = call("GET", args.booking + "/catalog")
    hotel = next((h for h in catalog["hotels"] if h["code"] == args.hotel), None)
    if hotel is None:
        sys.exit(f"No hotel {args.hotel} in the CRS catalog: {[h['code'] for h in catalog['hotels']]}")
    today = datetime.date.today()
    created = []
    for n in range(args.count):
        _, channel, partner, rates = weighted(rng, CHANNELS)
        room = rng.choice(hotel["roomTypes"])
        adults = rng.randint(1, min(2, room["maxOccupancy"]))
        children = [rng.randint(2, 12) for _ in range(rng.choice([0, 0, 0, 1, 2]))][: room["maxOccupancy"] - adults]
        arrival = today + datetime.timedelta(days=rng.randint(2, 150))
        departure = arrival + datetime.timedelta(days=rng.randint(2, 7))
        first, last = rng.choice(FIRST), rng.choice(LAST)
        guests = [{"firstName": first, "lastName": last, "type": "Adult"}]
        guests += [{"firstName": rng.choice(FIRST), "lastName": last, "type": "Adult"} for _ in range(adults - 1)]
        guests += [{"firstName": rng.choice(FIRST), "lastName": last, "type": "Child", "age": age} for age in children]
        booking = {
            "channelCode": channel,
            "partnerCode": partner,
            "externalReference": f"{partner[:2]}-{today.year}-{n:04d}" if partner else None,
            "arrival": arrival.isoformat(), "departure": departure.isoformat(),
            "holder": {"firstName": first, "lastName": last, "nationality": rng.choice(COUNTRIES),
                       "email": f"{first}.{last}@example.com".lower()},
            "rooms": [{"roomTypeCode": room["code"], "ratePlanCode": rng.choice(rates), "boardCode": weighted(rng, BOARDS)[1],
                       "adults": adults, "childrenAges": children, "guests": guests}],
        }
        answer = call("POST", args.booking + "/bookings", {"hotelCode": args.hotel, "booking": booking})
        created.append(answer["id"])
        if rng.random() < 0.3:
            call("POST", f"{args.booking}/bookings/{answer['id']}/payments",
                 {"type": "Deposit", "methodCode": rng.choice(["VISA", "MC", "AMEX"]), "amount": rng.choice([100, 150, 200, 300])})
        print(f"\r{len(created)}/{args.count}", end="", flush=True)
    print(f"\n{len(created)} bookings in {args.hotel}: {created[0]} … {created[-1]}")


if __name__ == "__main__":
    main()

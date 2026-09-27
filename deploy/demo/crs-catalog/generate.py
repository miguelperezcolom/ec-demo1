#!/usr/bin/env python3
"""
The simulated CRS's catalog for MRU01, derived from its Opera property's (XMAR, OHIP UAT).

MRU01 is integrated with a real Opera tenant, and the mapping agent pairs the CRS's codes with that
property's. So that it always finds a pair, MRU01's catalog is imported from XMAR's — but with the
CRS's own codes and names, never Opera's: the agent has to pair by meaning, not by an identical code.

What is imported, and how each one is named, is curated below (SELECTION): which of XMAR's 78 rate
plans the CRS sells, which of its 26 packages are really a board, the CRS's code, name, occupancy and
price for each. This script checks the selection against XMAR's catalog as the connector reads it —
every Opera code it names has to be there — and writes:

  booking/src/main/resources/crs-catalog/MRU01.json   the CRS side only, which the CRS loads at start
  deploy/demo/crs-catalog/MRU01-expected-pairs.md     each CRS code and the XMAR code it should map to
  deploy/demo/crs-catalog/XMAR.json                   the XMAR catalog it was checked against (--live)

The CRS never calls Opera: the catalog is versioned. Run again after XMAR's configuration changes.

  python3 deploy/demo/crs-catalog/generate.py --live   # read XMAR through the connector in the cluster
  python3 deploy/demo/crs-catalog/generate.py          # use the versioned XMAR.json
"""
import argparse
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
HERE = pathlib.Path(__file__).resolve().parent
CRS_FILE = ROOT / "booking/src/main/resources/crs-catalog/MRU01.json"
PAIRS_FILE = HERE / "MRU01-expected-pairs.md"
SNAPSHOT = HERE / "XMAR.json"

HOTEL = {"code": "MRU01", "name": "Riu Demo Mauricio", "currency": "EUR"}
PMS_HOTEL = "XMAR"

# (CRS code, CRS name, max occupancy, base price per night, XMAR room type)
ROOM_TYPES = [
    ("STD-KING", "Estándar con cama king", 2, 150, "STDK"),
    ("DBL-GARDEN", "Doble con balcón y vista jardín", 3, 170, "DBJB"),
    ("DBL-POOL", "Doble con balcón y vista piscina", 3, 185, "DBPB"),
    ("DBL-SIDESEA", "Doble con balcón y vista lateral al mar", 3, 195, "DBLB"),
    ("JS-STD", "Junior suite estándar con balcón", 3, 220, "SJSB"),
    ("JS-STD-KING", "Junior suite estándar con balcón, cama king", 3, 225, "SJSK"),
    ("JS-STD-SPECIAL", "Junior suite estándar especial", 3, 235, "SJSE"),
    ("JS-ACCESSIBLE", "Junior suite adaptada (movilidad reducida)", 3, 220, "SJSM"),
    ("JS-SIDESEA-KING", "Junior suite vista lateral al mar, cama king", 3, 240, "SJLK"),
    ("JS-SIDESEA", "Junior suite con balcón y vista lateral al mar", 3, 245, "SJLB"),
    ("JS-SEA", "Junior suite con balcón y vista al mar", 3, 270, "SJMB"),
    ("JS-SEA-KING", "Junior suite con balcón y vista al mar, cama king", 3, 275, "SJMK"),
    ("FAMILY", "Habitación familiar", 5, 260, "FAM"),
    ("JS-SWIMOUT", "Junior suite deluxe con salida a la piscina (swim-out)", 3, 320, "SJPE"),
    ("SUITE-SWIMUP", "Suite con acceso directo a la piscina (swim-up)", 3, 380, "SUPE"),
]

# (CRS code, CRS name, factor over the room's base price, XMAR rate plan). What the CRS sells MRU01
# with: direct sale, online agencies and tour operators — XMAR has no refundable/non-refundable pair
# and no early booking, so the CRS has none for this hotel either. Only plans whose name no other plan
# of XMAR shares: several share one (three are «RC FLEX XMU A26»), and a pair has to be clear.
#
# Two carry a board: EXP_BB and AGRO include the package BRKFST, so they are sold with DESAYUNO (the
# demo's generator does) and the connector does not add the package again. The rest carry none.
RATE_PLANS = [
    ("DIRECTA", "Venta directa 2026 (web y call center)", "1.00", "406484DIRXM"),
    ("FLEX-LOCAL", "Flexible Riu Class residentes locales 2026", "0.95", "40648544"),
    ("EXPEDIA-AD", "Expedia con desayuno", "0.92", "EXP_BB"),
    ("AGRO-MAYOR", "Mayorista AGRO", "0.85", "AGRO"),
    ("AGENCIAS-LOCALES", "Agencias de viaje locales 2026", "0.88", "406480LOAXU"),
    ("TUI-NL", "Contrato TUI Países Bajos 2026", "0.80", "406446TUNXM"),
    ("TUI-FR", "Contrato TUI Francia 2026", "0.80", "406451TUFXM"),
    ("DMC-MAURICIO", "Contrato receptivo DMC Mauricio (EUR) 2026", "0.82", "406477DMCEU"),
]

# (CRS code, CRS name, supplement per adult and night, XMAR package). Of XMAR's 26 packages only these
# are a board: the rest are charges (cot, extra bed, parking, tourist tax, late checkout), discounts
# (D5, DISC_12, FACTOR), day passes, an upsell, the food/drink breakdown of the all-inclusive (GENDTI,
# DPC, DPB20) and a drinks add-on (BEV). A board is written to Opera as a package of its own, so it has
# to be one XMAR sells separately (postingAttributes.sellSeparate): its breakfast BKF «Pensión Desayuno
# Adulto» (and BKFCH, the child's) is not — Opera refuses it alone with RSV10047, and it only comes inside
# rate plans the CRS does not sell (395600IN27/28, 399243TUUKX) — so breakfast is BRKFST, which XMAR
# sells separately and which EXP_BB and AGRO carry (read on OHIP UAT, 2026-09-27). The connector's
# catalog leaves the packages not sold separately out.
BOARDS = [
    ("SOLO-ALOJAMIENTO", "Solo alojamiento", "0", "NONE"),
    ("DESAYUNO", "Alojamiento y desayuno", "18", "BRKFST"),
    ("COMIDAS", "Pensión con comidas, sin bebidas", "55", "FOOD"),
    ("TODO-INCLUIDO", "Todo incluido", "95", "PENSTI"),
]

# (CRS code, CRS name, sells through a partner, XMAR source code, XMAR market code). In Opera a channel
# is a source code and a market code; the CRS has no markets, so they only show here, as the attribute
# the mapping should choose. House use (HSE) is not a sales channel.
CHANNELS = [
    ("WEB", "Web del hotel", False, "HWEB", "BAR"),
    ("CALLCENTER", "Central de reservas (call center)", False, "CRSN", "BAR"),
    ("TELEFONO", "Llamada directa al hotel", False, "PHN", "FIT"),
    ("EMAIL", "Petición por correo electrónico", False, "EML", "FIT"),
    ("WALKIN", "Cliente sin reserva en recepción (walk-in)", False, "WLK", "FIT"),
    ("GRUPOS", "Ventas de grupos", False, "GS", "GRP"),
    ("TTOO", "Turoperador (contrato negociado)", True, "CRSN", "NEG"),
    ("OTA", "Agencia de viajes online", True, "HWEB", "OTA"),
]

# (CRS code, CRS name, XMAR payment method). XMAR's CD «Credit» and CRE «Credito» say the same and
# are left out: neither would be a clear pair.
PAYMENT_METHODS = [
    ("VISA", "Tarjeta Visa", "VI"),
    ("VISA-MANUAL", "Tarjeta Visa tecleada a mano", "VI/OL"),
    ("MASTERCARD", "Tarjeta Mastercard", "MC"),
    ("TRANSFERENCIA", "Transferencia bancaria", "BT"),
    ("EFECTIVO", "Efectivo", "CASH"),
]

# (CRS code, CRS name, XMAR cancellation code). NOS is the CRS's no show everywhere (a no show the
# hotel reports cancels the booking with it): kept.
CANCELLATION_REASONS = [
    ("IMPAGO", "Impago", "CFPAGO"),
    ("DUPLICADA", "Reserva duplicada", "DUP"),
    ("CANCELA-TTOO", "Cancelada por el turoperador", "TTOO"),
    ("OTR", "Otros motivos", "OTROS"),
    ("NOS", "No show", "NOSHOW"),
]


def live_catalog():
    answer = subprocess.run(
        ["kubectl", "-n", "ec-demo1", "exec", "deploy/mapping-service", "-c", "mapping-service", "--", "wget", "-qO-",
         "-T", "60", f"http://pms-integration-service:8123/catalog?hotelId={PMS_HOTEL}"],
        check=True, capture_output=True, text=True)
    return json.loads(answer.stdout)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--live", action="store_true", help="read XMAR's catalog through the connector in the cluster")
    args = ap.parse_args()

    catalog = live_catalog() if args.live else json.loads(SNAPSHOT.read_text())
    opera = {}
    for e in catalog:
        opera.setdefault(e["type"], {})[e["code"]] = (e.get("description") or "").strip()
    if args.live and not any(opera.get("RATE_PLAN", {}).values()):
        sys.exit("The connector gives XMAR's rate plans without names: deploy the pms-integration-service that "
                 "reads them from the rate plan search first.")

    problems = []

    def pms(type_, code):
        if code not in opera.get(type_, {}):
            problems.append(f"{type_} {code} is not in XMAR's catalog")
            return ""
        return opera[type_][code]

    for type_, rows, col in [("ROOM_TYPE", ROOM_TYPES, 4), ("RATE_PLAN", RATE_PLANS, 3), ("BOARD", BOARDS, 3),
                             ("PAYMENT_METHOD", PAYMENT_METHODS, 2), ("CANCELLATION_REASON", CANCELLATION_REASONS, 2)]:
        codes = [r[0] for r in rows]
        if len(set(codes)) != len(codes):
            problems.append(f"{type_}: a CRS code repeats")
        for r in rows:
            if r[0] == r[col] and r[0] != "NOS":
                problems.append(f"{type_} {r[0]} is Opera's own code: the CRS's has to be its own")
    for r in RATE_PLANS:
        name = opera.get("RATE_PLAN", {}).get(r[3])
        if name and list(opera["RATE_PLAN"].values()).count(name) > 1:
            problems.append(f"RATE_PLAN {r[3]} «{name}» shares its name with another plan of XMAR")
    rows = []

    def row(kind, crs_code, crs_name, pms_type, pms_code, extra=""):
        rows.append((kind, crs_code, crs_name, pms_code, pms(pms_type, pms_code), extra))

    for c, n, occ, price, x in ROOM_TYPES:
        row("Tipo de habitación", c, n, "ROOM_TYPE", x)
    for c, n, f, x in RATE_PLANS:
        row("Tarifa", c, n, "RATE_PLAN", x)
    for c, n, s, x in BOARDS:
        row("Régimen", c, n, "BOARD", x)
    for c, n, partner, src, market in CHANNELS:
        row("Canal", c, n, "CHANNEL", src, f"mercado {market} «{pms('MARKET', market).removeprefix('Market code: ')}»")
    for c, n, x in PAYMENT_METHODS:
        row("Forma de pago", c, n, "PAYMENT_METHOD", x)
    for c, n, x in CANCELLATION_REASONS:
        row("Motivo de cancelación", c, n, "CANCELLATION_REASON", x)
    if problems:
        sys.exit("\n".join(problems))

    crs = {
        "hotel": HOTEL,
        "roomTypes": [{"code": c, "name": n, "maxOccupancy": occ, "basePrice": price} for c, n, occ, price, _ in ROOM_TYPES],
        "ratePlans": [{"code": c, "name": n, "factor": float(f)} for c, n, f, _ in RATE_PLANS],
        "boards": [{"code": c, "name": n, "supplementPerAdult": float(s)} for c, n, s, _ in BOARDS],
        "channels": [{"code": c, "name": n, "requiresPartner": p} for c, n, p, _, _ in CHANNELS],
        "cancellationReasons": [{"code": c, "name": n} for c, n, _ in CANCELLATION_REASONS],
        "paymentMethods": [{"code": c, "name": n} for c, n, _ in PAYMENT_METHODS],
    }
    CRS_FILE.parent.mkdir(parents=True, exist_ok=True)
    CRS_FILE.write_text(json.dumps(crs, ensure_ascii=False, indent=2) + "\n")

    lines = ["# MRU01 → XMAR: the pairs the mapping should find", "",
             "Generated by `deploy/demo/crs-catalog/generate.py` — do not edit by hand. Every CRS code of MRU01",
             "and the code of Opera's property XMAR it means. The CRS's catalog does not carry these: finding them",
             "is the mapping's (the agent's) job; this is the answer to check it against.", "",
             "| Tipo | Código CRS | Nombre en el CRS | Código XMAR | Nombre en XMAR |", "|---|---|---|---|---|"]
    for kind, c, n, x, xn, extra in rows:
        lines.append(f"| {kind} | `{c}` | {n} | `{x}` | {xn.removeprefix('Source code: ')}{' · ' + extra if extra else ''} |")
    PAIRS_FILE.write_text("\n".join(lines) + "\n")

    if args.live:
        SNAPSHOT.write_text("[\n" + ",\n".join(json.dumps(e, ensure_ascii=False) for e in catalog) + "\n]\n")
    print(f"{CRS_FILE.relative_to(ROOT)}: {len(ROOM_TYPES)} room types, {len(RATE_PLANS)} rate plans, "
          f"{len(BOARDS)} boards, {len(CHANNELS)} channels, {len(PAYMENT_METHODS)} payment methods, "
          f"{len(CANCELLATION_REASONS)} cancellation reasons")
    print(f"{PAIRS_FILE.relative_to(ROOT)}: {len(rows)} pairs")


if __name__ == "__main__":
    main()

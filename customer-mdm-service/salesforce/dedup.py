#!/usr/bin/env python3
"""Finds duplicate contacts the org's duplicate rules missed, plans their merge, and merges them.

    dedup.py              phase 1, read only: backup, plan (dedup_plan.md), nothing written to the org
    dedup.py --execute    phase 2: merges the AUTO groups of the plan through the SOAP API's merge()
    dedup.py --restore dedup-out/dedup_result_<ts>.json
                          takes that run's absorbed contacts out of the recycle bin, so a demo can merge
                          them again (the related records a merge moved stay on the master)

Options:
    --out DIR             where the backup, the plan and the result go (default: ./dedup-out)
    --include KEY,...     treats these REVISAR groups as AUTO (the group key, as the plan prints it)
    --exclude KEY,...     leaves these groups out

Credentials: SF_INSTANCE_URL and SF_ACCESS_TOKEN if set; otherwise the MDM's client credentials,
as deploy.py reads them (~/.config/ec-demo1/salesforce.env). The token is never printed or written.

Grouping and survivorship are pure functions (normalize, group, plan): test_dedup.py tests them
without a network.
"""
import argparse, json, os, re, sys, unicodedata, urllib.error, urllib.parse, urllib.request
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree
from xml.sax.saxutils import escape

ORG = "orgfarm-0ed11c4322"
FIELDS = ["Id", "FirstName", "LastName", "Name", "Email", "Phone", "MobilePhone", "AccountId", "Account.Name", "Title",
          "MailingStreet", "MailingCity", "CreatedDate", "LastModifiedDate", "OwnerId", "MDM_Id__c"]
# What counts as "informed" for the master, and what a master's empty field is filled from.
DATA_FIELDS = ["FirstName", "LastName", "Email", "Phone", "MobilePhone", "AccountId", "Title", "MailingStreet", "MailingCity"]
# Values that must agree in a group for it to be AUTO; empties never conflict.
CONFLICT_FIELDS = ["Email", "Phone", "MobilePhone", "AccountId"]
MAX_ABSORBED = 2  # per MergeRequest


# ---------------------------------------------------------------- normalize

def normalize(value):
    """Lower case, trimmed, spaces collapsed, no diacritics."""
    if not value:
        return ""
    text = unicodedata.normalize("NFKD", value)
    text = "".join(c for c in text if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", text).strip().lower()


def digits(value):
    return re.sub(r"\D", "", value or "")


def comparable(field, value):
    """The value as compared: emails lower case, phones digits only, the rest normalized."""
    if field in ("Phone", "MobilePhone"):
        return digits(value)
    if field == "Email":
        return (value or "").strip().lower()
    if field == "AccountId":
        return value or ""
    return normalize(value)


def key(contact):
    return (normalize(contact.get("FirstName")) + " " + normalize(contact.get("LastName"))).strip()


# ---------------------------------------------------------------- group

def filled(contact, field):
    value = contact.get(field)
    return value is not None and str(value).strip() != ""


def conflicts(contacts):
    """One line per field whose non-empty values differ in the group."""
    out = []
    for field in CONFLICT_FIELDS:
        values = sorted({comparable(field, c.get(field)) for c in contacts if filled(c, field)} - {""})
        if len(values) > 1:
            shown = sorted({c.get("Account", {}).get("Name") or c[field] if field == "AccountId" else c[field]
                            for c in contacts if filled(c, field)}, key=str)
            out.append(f"{field}: " + " / ".join(map(str, shown)))
    return out


def group(contacts, include=(), exclude=()):
    """Contacts by normalized first + last name. Returns (groups, shared): groups of two or more,
    each AUTO or REVISAR, and — for information only — contacts of different names that share an
    email or a phone."""
    by_key = {}
    for c in contacts:
        by_key.setdefault(key(c), []).append(c)
    groups = []
    for k in sorted(by_key):
        members = sorted(by_key[k], key=lambda c: (c.get("CreatedDate") or "", c["Id"]))
        if len(members) < 2 or k in exclude:
            continue
        reasons = conflicts(members)
        groups.append({"key": k, "contacts": members, "reasons": reasons,
                       "class": "AUTO" if not reasons or k in include else "REVISAR",
                       "reclassified": bool(reasons) and k in include})
    shared = []
    for label, fields in (("Email", ["Email"]), ("Teléfono", ["Phone", "MobilePhone"])):
        by_value = {}
        for c in contacts:
            for f in fields:
                v = comparable(f, c.get(f))
                if v:
                    by_value.setdefault(v, {})[c["Id"]] = c
        for v in sorted(by_value):
            names = {key(c) for c in by_value[v].values()}
            if len(names) > 1:
                shared.append({"field": label, "value": v,
                               "contacts": sorted(by_value[v].values(), key=lambda c: (key(c), c["Id"]))})
    return groups, shared


# ---------------------------------------------------------------- plan

def informed(contact):
    return sum(1 for f in DATA_FIELDS if filled(contact, f))


def plan(g, children=None):
    """Survivorship for one group: the master is the contact with most fields informed (the
    oldest on a tie); each field empty on the master takes the value of the most recent contact
    that has it. A master's value is never overwritten."""
    children = children or {}
    members = g["contacts"]
    master = sorted(members, key=lambda c: (-informed(c), c.get("CreatedDate") or "", c["Id"]))[0]
    absorbed = [c for c in members if c["Id"] != master["Id"]]
    newest_first = sorted(absorbed, key=lambda c: (c.get("CreatedDate") or "", c["Id"]), reverse=True)
    fills = {}
    for f in DATA_FIELDS:
        if filled(master, f):
            continue
        source = next((c for c in newest_first if filled(c, f)), None)
        if source:
            fills[f] = {"value": source[f], "from": source["Id"]}
    moved = {}
    for c in absorbed:
        for kind, n in children.get(c["Id"], {}).items():
            moved[kind] = moved.get(kind, 0) + n
    return {"key": g["key"], "class": g["class"], "reasons": g["reasons"], "reclassified": g.get("reclassified", False),
            "master": master["Id"], "absorbed": [c["Id"] for c in absorbed], "fills": fills, "children": moved,
            "mdm": sorted(c["MDM_Id__c"] for c in members if c.get("MDM_Id__c"))}


def batches(absorbed):
    return [absorbed[i:i + MAX_ABSORBED] for i in range(0, len(absorbed), MAX_ABSORBED)]


def render(plans, shared, contacts, generated):
    names = {c["Id"]: c.get("Name") or key(c) for c in contacts}
    auto = [p for p in plans if p["class"] == "AUTO"]
    review = [p for p in plans if p["class"] == "REVISAR"]
    lines = [f"# Plan de deduplicación de contactos", "", f"Generado {generated} sobre {len(contacts)} contactos.", "",
             "| Grupo | Clase | Maestro | Absorbe | Relleno en el maestro | Hijos a reasignar | MDM |",
             "| :---- | :---- | :------ | :------ | :-------------------- | :---------------- | :-- |"]
    for p in plans:
        fills = "<br>".join(f"{f} ← `{v['value']}` ({v['from']})" for f, v in p["fills"].items()) or "—"
        kids = ", ".join(f"{k} {n}" for k, n in sorted(p["children"].items()) if n) or "—"
        cls = p["class"] + (" (reclasificado)" if p["reclassified"] else "")
        lines.append(f"| {names[p['master']]} (`{p['key']}`) | {cls} | {p['master']} | {'<br>'.join(p['absorbed'])} | {fills} | {kids} | {len(p['mdm']) or '—'} |")
    lines += ["", "## Grupos REVISAR", ""]
    lines += [f"- `{p['key']}` ({1 + len(p['absorbed'])} contactos): " + "; ".join(p["reasons"]) for p in review] or ["Ninguno."]
    lines += ["", "## Informativo: nombres distintos que comparten email o teléfono", ""]
    lines += [f"- {s['field']} `{s['value']}`: " + ", ".join(f"{names[c['Id']]} ({c['Id']})" for c in s["contacts"]) for s in shared] or ["Ninguno."]
    merges = sum(len(batches(p["absorbed"])) for p in auto)
    lines += ["", "## Totales", "",
              f"- Grupos: {len(plans)} — AUTO {len(auto)}, REVISAR {len(review)}",
              f"- Contactos a absorber (AUTO): {sum(len(p['absorbed']) for p in auto)}, en {merges} llamadas merge()",
              f"- Contactos que se quedan (AUTO): {len(auto)}",
              f"- Grupos AUTO con contactos del MDM (`MDM_Id__c`): {sum(1 for p in auto if p['mdm'])} — el MDM consolidará sus clientes",
              f"- Coincidencias informativas: {len(shared)}", ""]
    return "\n".join(lines)


# ---------------------------------------------------------------- fetch

class Org:
    def __init__(self):
        instance, access = os.environ.get("SF_INSTANCE_URL"), os.environ.get("SF_ACCESS_TOKEN")
        if not (instance and access):
            sys.path.insert(0, str(Path(__file__).resolve().parent))
            import deploy
            instance, access, _ = deploy.token()
        if ORG not in instance:
            sys.exit(f"Aborting: the instance is not {ORG}")
        self.instance, self.access = instance.rstrip("/"), access
        self.version = self.get("/services/data/")[-1]["version"]

    def get(self, path):
        req = urllib.request.Request(self.instance + path, headers={"Authorization": "Bearer " + self.access})
        with urllib.request.urlopen(req) as r:
            return json.load(r)

    def query(self, soql):
        page = self.get(f"/services/data/v{self.version}/query?q=" + urllib.parse.quote(soql))
        records = page["records"]
        while not page["done"]:
            page = self.get(page["nextRecordsUrl"])
            records += page["records"]
        for r in records:
            r.pop("attributes", None)
            if isinstance(r.get("Account"), dict):
                r["Account"].pop("attributes", None)
        return records

    def soap(self, body):
        envelope = ('<?xml version="1.0" encoding="UTF-8"?>'
                    '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" '
                    'xmlns:urn="urn:partner.soap.sforce.com" xmlns:sobj="urn:sobject.partner.soap.sforce.com">'
                    f'<soapenv:Header><urn:SessionHeader><urn:sessionId>{escape(self.access)}</urn:sessionId></urn:SessionHeader></soapenv:Header>'
                    f'<soapenv:Body>{body}</soapenv:Body></soapenv:Envelope>')
        req = urllib.request.Request(f"{self.instance}/services/Soap/u/{self.version}", envelope.encode(), method="POST",
                                     headers={"Content-Type": "text/xml; charset=UTF-8", "SOAPAction": '""'})
        try:
            with urllib.request.urlopen(req) as r:
                return ElementTree.fromstring(r.read())
        except urllib.error.HTTPError as e:
            return ElementTree.fromstring(e.read())


def fetch(org):
    return org.query(f"SELECT {', '.join(FIELDS)} FROM Contact ORDER BY CreatedDate, Id")


def fetch_children(org):
    """Per contact: Cases, opportunity contact roles, Tasks and Events — what a merge moves."""
    counts = {}
    for kind, soql in (("Cases", "SELECT ContactId c, COUNT(Id) n FROM Case WHERE ContactId != null GROUP BY ContactId"),
                       ("Opportunities", "SELECT ContactId c, COUNT(Id) n FROM OpportunityContactRole GROUP BY ContactId"),
                       ("Tasks", "SELECT WhoId c, COUNT(Id) n FROM Task WHERE WhoId != null GROUP BY WhoId"),
                       ("Events", "SELECT WhoId c, COUNT(Id) n FROM Event WHERE WhoId != null GROUP BY WhoId")):
        for r in org.query(soql):
            if r["c"] and r["c"].startswith("003"):
                counts.setdefault(r["c"], {})[kind] = r["n"]
    return counts


# ---------------------------------------------------------------- merge

def merge_request(master, absorbed, fills):
    fields = "".join(f"<sobj:{f}>{escape(str(v))}</sobj:{f}>" for f, v in fills.items())
    ids = "".join(f"<urn:recordToMergeIds>{i}</urn:recordToMergeIds>" for i in absorbed)
    return (f"<urn:merge><urn:request><urn:masterRecord><sobj:type>Contact</sobj:type><sobj:Id>{master}</sobj:Id>{fields}"
            f"</urn:masterRecord>{ids}</urn:request></urn:merge>")


def merge_result(root):
    ns = {"s": "http://schemas.xmlsoap.org/soap/envelope/", "p": "urn:partner.soap.sforce.com"}
    fault = root.find(".//s:Fault", ns)
    if fault is not None:
        return {"success": False, "errors": [{"code": fault.findtext("faultcode"), "message": fault.findtext("faultstring")}]}
    r = root.find(".//p:result", ns)
    return {"success": r.findtext("p:success", namespaces=ns) == "true",
            "mergedRecordIds": [e.text for e in r.findall("p:mergedRecordIds", ns)],
            "updatedRelatedIds": [e.text for e in r.findall("p:updatedRelatedIds", ns)],
            "errors": [{"code": e.findtext("p:statusCode", namespaces=ns), "message": e.findtext("p:message", namespaces=ns)}
                       for e in r.findall("p:errors", ns)]}


def merge(org, p):
    """One group: the fills go with the first request; more than two absorbed chain on the same master."""
    out = {"key": p["key"], "master": p["master"], "absorbed": p["absorbed"], "requests": []}
    fills = {f: v["value"] for f, v in p["fills"].items()}
    for batch in batches(p["absorbed"]):
        r = merge_result(org.soap(merge_request(p["master"], batch, fills)))
        out["requests"].append({"absorbed": batch, **r})
        if not r["success"]:
            break
        fills = {}
    out["success"] = all(r["success"] for r in out["requests"]) and len(out["requests"]) == len(batches(p["absorbed"]))
    return out


def restore(org, result_file):
    ids = [i for g in json.loads(Path(result_file).read_text()) for q in g["requests"] if q["success"] for i in q["mergedRecordIds"]]
    ns = {"p": "urn:partner.soap.sforce.com"}
    for i in range(0, len(ids), 200):
        body = "<urn:undelete>" + "".join(f"<urn:ids>{x}</urn:ids>" for x in ids[i:i + 200]) + "</urn:undelete>"
        for r in org.soap(body).findall(".//p:result", ns):
            ok = r.findtext("p:success", namespaces=ns) == "true"
            print(f"  {'OK ' if ok else 'ERR'} {r.findtext('p:id', namespaces=ns) or ''}"
                  + ("" if ok else " — " + "; ".join(e.findtext("p:message", namespaces=ns) for e in r.findall("p:errors", ns))))


# ---------------------------------------------------------------- main

def main():
    args = argparse.ArgumentParser()
    args.add_argument("--execute", action="store_true")
    args.add_argument("--dry-run", action="store_true", help="the default")
    args.add_argument("--out", default="dedup-out")
    args.add_argument("--include", default="")
    args.add_argument("--exclude", default="")
    args.add_argument("--restore", metavar="RESULT_JSON")
    a = args.parse_args()
    include = {k.strip() for k in a.include.split(",") if k.strip()}
    exclude = {k.strip() for k in a.exclude.split(",") if k.strip()}
    out = Path(a.out)
    (out / "backup").mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")

    org = Org()
    print(f"{ORG}, API v{org.version}")
    if a.restore:
        return restore(org, a.restore)
    contacts = fetch(org)
    (out / "backup" / f"contacts_{stamp}.json").write_text(json.dumps(contacts, indent=1, ensure_ascii=False))
    children = fetch_children(org)
    groups, shared = group(contacts, include, exclude)
    plans = [plan(g, children) for g in groups]
    report = render(plans, shared, contacts, stamp)
    (out / "dedup_plan.md").write_text(report)
    (out / f"dedup_plan_{stamp}.json").write_text(json.dumps(plans, indent=1, ensure_ascii=False))
    print(report)
    print(f"Backup: {out / 'backup' / f'contacts_{stamp}.json'} — plan: {out / 'dedup_plan.md'}")
    if not a.execute:
        print("Dry run: nothing merged. Run with --execute to merge the AUTO groups.")
        return

    results = []
    for p in (p for p in plans if p["class"] == "AUTO"):
        r = merge(org, p)
        results.append(r)
        errors = [e for q in r["requests"] for e in q["errors"]]
        print(f"  {'OK ' if r['success'] else 'ERR'} {p['key']}: {p['master']} ← {', '.join(p['absorbed'])}"
              + ("" if r["success"] else " — " + "; ".join(f"{e['code']}: {e['message']}" for e in errors)))
    (out / f"dedup_result_{stamp}.json").write_text(json.dumps(results, indent=1, ensure_ascii=False))
    print(f"{sum(r['success'] for r in results)}/{len(results)} groups merged — log: {out / f'dedup_result_{stamp}.json'}")

    left, _ = group(fetch(org))
    print("Groups left:" if left else "Groups left: none")
    for g in left:
        print(f"  {g['class']} {g['key']} ({len(g['contacts'])}): {'; '.join(g['reasons'])}")
    print("The absorbed contacts are in the org's recycle bin: recoverable for 15 days.")


if __name__ == "__main__":
    main()

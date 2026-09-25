#!/usr/bin/env python3
"""Deploys src/ to the Salesforce org through the Metadata API's REST endpoint.

Uses the integration's own machine-to-machine credentials (client credentials flow), read from
SF_DOMAIN / SF_CLIENT_ID / SF_CLIENT_SECRET — by default from ~/.config/ec-demo1/salesforce.env.

    deploy.py --check    validate only: runs the tests, changes nothing
    deploy.py            deploy
"""
import io, json, urllib.error, os, sys, time, urllib.parse, urllib.request, uuid, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
API = "v67.0"


def env():
    path = Path(os.environ.get("SF_ENV", Path.home() / ".config/ec-demo1/salesforce.env"))
    if path.exists():
        for line in path.read_text().splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip())
    return os.environ["SF_DOMAIN"], os.environ["SF_CLIENT_ID"], os.environ["SF_CLIENT_SECRET"]


def token():
    domain, cid, secret = env()
    body = urllib.parse.urlencode({"grant_type": "client_credentials", "client_id": cid, "client_secret": secret}).encode()
    r = json.load(urllib.request.urlopen(f"https://{domain}/services/oauth2/token", body))
    return r["instance_url"], r["access_token"], r["id"].split("/")[-1]


def call(instance, access, method, path, body=None):
    req = urllib.request.Request(f"{instance}/services/data/{API}{path}", json.dumps(body).encode() if body else None,
                                 method=method, headers={"Authorization": f"Bearer {access}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        text = r.read()
        return json.loads(text) if text else None


def assign_permission_sets(instance, access, user_id):
    """The user the integration runs as gets every permission set in src/ — once."""
    for f in (HERE / "src/permissionsets").glob("*.permissionset"):
        q = urllib.parse.quote(f"SELECT Id, (SELECT Id FROM Assignments WHERE AssigneeId = '{user_id}') FROM PermissionSet WHERE Name = '{f.stem}'")
        ps = call(instance, access, "GET", f"/query?q={q}")["records"][0]
        if not ps.get("Assignments"):
            call(instance, access, "POST", "/sobjects/PermissionSetAssignment", {"AssigneeId": user_id, "PermissionSetId": ps["Id"]})
            print("assigned", f.stem, "to", user_id)


def package():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted((HERE / "src").rglob("*")):
            if f.is_file():
                z.write(f, "src/" + str(f.relative_to(HERE / "src")))
    return buf.getvalue()


def main():
    check = "--check" in sys.argv
    instance, access, user_id = token()
    tests = [f.stem for f in (HERE / "src/classes").glob("*Test.cls")] if (HERE / "src/classes").exists() else []
    options = {"deployOptions": {"checkOnly": check, "singlePackage": False, "rollbackOnError": True,
                                 **({"testLevel": "RunSpecifiedTests", "runTests": tests} if tests else {})}}
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"json\"\r\nContent-Type: application/json\r\n\r\n"
            f"{json.dumps(options)}\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"deploy.zip\"\r\n"
            f"Content-Type: application/zip\r\n\r\n").encode() + package() + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(f"{instance}/services/data/{API}/metadata/deployRequest", body, method="POST",
                                 headers={"Authorization": f"Bearer {access}",
                                          "Content-Type": f"multipart/form-data; boundary={boundary}"})
    try:
        deploy_id = json.load(urllib.request.urlopen(req))["id"]
    except urllib.error.HTTPError as e:
        sys.exit(f"{e.code}: {e.read().decode()}")
    print(("Validating" if check else "Deploying"), deploy_id)
    while True:
        time.sleep(5)
        req = urllib.request.Request(f"{instance}/services/data/{API}/metadata/deployRequest/{deploy_id}?includeDetails=true",
                                     headers={"Authorization": f"Bearer {access}"})
        result = json.load(urllib.request.urlopen(req))["deployResult"]
        if result["done"]:
            break
    print("status:", result["status"])
    details = result.get("details") or {}
    for f in details.get("componentFailures") or []:
        print("  component:", f.get("componentType"), f.get("fullName"), "-", f.get("problem"))
    tests_result = details.get("runTestResult") or {}
    for f in tests_result.get("failures") or []:
        print("  test:", f.get("name"), f.get("methodName"), "-", f.get("message"))
    for c in tests_result.get("codeCoverageWarnings") or []:
        print("  coverage:", c.get("name"), "-", c.get("message"))
    print("tests run:", tests_result.get("numTestsRun"), "failures:", tests_result.get("numFailures"))
    if result["status"] != "Succeeded":
        sys.exit(1)
    if not check:
        activate_flows(instance, access)
        assign_permission_sets(instance, access, user_id)
        case_layout(instance, access)


def activate_flows(instance, access):
    """A production org deploys flows as drafts: activate the latest version of each one in src/."""
    for f in (HERE / "src/flows").glob("*.flow"):
        q = urllib.parse.quote(f"SELECT Id, ActiveVersionId, LatestVersionId, LatestVersion.VersionNumber FROM FlowDefinition WHERE DeveloperName = '{f.stem}'")
        d = call(instance, access, "GET", f"/tooling/query?q={q}")["records"][0]
        if d["ActiveVersionId"] != d["LatestVersionId"]:
            call(instance, access, "PATCH", f"/tooling/sobjects/FlowDefinition/{d['Id']}",
                 {"Metadata": {"activeVersionNumber": d["LatestVersion"]["VersionNumber"]}})
            print("activated", f.stem, "v" + str(d["LatestVersion"]["VersionNumber"]))
        q = urllib.parse.quote(f"SELECT Id FROM Flow WHERE Definition.DeveloperName = '{f.stem}' AND Status = 'Obsolete'")
        for old in call(instance, access, "GET", f"/tooling/query?q={q}")["records"]:
            call(instance, access, "DELETE", f"/tooling/sobjects/Flow/{old['Id']}")


CASE_SECTION = "Cambio de datos de cliente (MDM)"
# What a steward decides on a customer data change request (a Case the MDM opened): the decision and
# the proposed data, which can be corrected before approving; where it came from, read only.
CASE_FIELDS = [("Decision__c", "Edit"), ("Motivo__c", "Edit"), ("Cambios__c", "Readonly"), ("Origen__c", "Readonly"),
               ("Nombre__c", "Edit"), ("Apellidos__c", "Edit"), ("Email__c", "Edit"), ("Telefono__c", "Edit"),
               ("Nacionalidad__c", "Edit"), ("FechaNacimiento__c", "Edit"), ("TipoDocumento__c", "Edit"),
               ("NumeroDocumento__c", "Edit"), ("MdmId__c", "Readonly"), ("MdmRequestId__c", "Readonly")]


def without_nulls(value):
    if isinstance(value, dict):
        return {k: without_nulls(v) for k, v in value.items() if v is not None}
    if isinstance(value, list):
        return [without_nulls(v) for v in value]
    return value


def case_layout(instance, access):
    """The change request's fields on the Case page, in a section of their own; added once."""
    q = urllib.parse.quote("SELECT Id FROM Layout WHERE TableEnumOrId = 'Case' AND Name = 'Case Layout'")
    records = call(instance, access, "GET", f"/tooling/query?q={q}")["records"]
    if not records:
        print("no Case Layout to add the change request's section to")
        return
    layout_id = records[0]["Id"]
    metadata = call(instance, access, "GET", f"/tooling/sobjects/Layout/{layout_id}")["Metadata"]
    if any(s.get("label") == CASE_SECTION for s in metadata.get("layoutSections", [])):
        return
    half = (len(CASE_FIELDS) + 1) // 2
    columns = [CASE_FIELDS[:half], CASE_FIELDS[half:]]
    metadata["layoutSections"].insert(1, {
        "label": CASE_SECTION, "style": "TwoColumnsTopToBottom", "customLabel": True,
        "detailHeading": True, "editHeading": True,
        "layoutColumns": [{"layoutItems": [{"field": f, "behavior": b} for f, b in column]} for column in columns]})
    # Salesforce does not take back its own metadata as it sent it: without the nulls, and without the
    # action list and the summary layout, whose enums it serialises in a form it then refuses (the layout
    # keeps the default actions; Lightning shows its compact layout, not the summary).
    metadata = without_nulls(metadata)
    metadata.pop("platformActionList", None)
    metadata.pop("summaryLayout", None)
    # and the classic quick actions; and a related list's empty quick action list, which it reads as a
    # mass action on its object (CaseComment) and refuses.
    metadata.pop("quickActionList", None)
    for related in metadata.get("relatedLists", []):
        if not related.get("quickActions"):
            related.pop("quickActions", None)
    call(instance, access, "PATCH", f"/tooling/sobjects/Layout/{layout_id}", {"Metadata": metadata})
    print("Case Layout: section", CASE_SECTION, "added")


if __name__ == "__main__":
    main()

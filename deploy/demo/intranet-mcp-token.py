#!/usr/bin/env python3
"""A Keycloak access token for the intranet's MCP server, for desktop AI clients (Claude Code, Claude
Desktop, Antigravity) — the person's own token, so the tools see exactly what the portal shows them.

    intranet-mcp-token.py login            # once: the device flow — open the URL it prints, sign in
    intranet-mcp-token.py                  # a fresh access token (stdout), refreshed silently
    intranet-mcp-token.py header-json      # {"Authorization": "Bearer …"} — Claude Code's headersHelper
    intranet-mcp-token.py logout           # forget the stored session

Client `intranet-mcp` of realm ec-demo1 (deploy/keycloak/ec-demo1-realm.json): its access tokens last
8 hours (the realm's default, for the consoles, is 5 minutes). The stored refresh token lasts as long as
the realm's session (24 hours): after that, `login` again. It is kept in
~/.config/ec-demo1/intranet-mcp-<host>.json, readable only by you; never commit or share it.
On a Mac, *.localhost is asked for at 127.0.0.1 directly (macOS' resolver does not know the name).

Environment: INTRANET_AUTH_URL (default http://auth.localhost:8800, the local cluster; ec1:
https://auth.ec1.mateu.io), INTRANET_MCP_CLIENT (default intranet-mcp). `login --password` signs in
with a username and password instead (INTRANET_MCP_USER / INTRANET_MCP_PASSWORD, or asked), for scripts.
"""
import getpass
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

AUTH = os.environ.get("INTRANET_AUTH_URL", "http://auth.localhost:8800").rstrip("/")
REALM = os.environ.get("INTRANET_MCP_REALM", "ec-demo1")
CLIENT = os.environ.get("INTRANET_MCP_CLIENT", "intranet-mcp")
OIDC = f"{AUTH}/realms/{REALM}/protocol/openid-connect"
STORE = os.path.expanduser(
    f"~/.config/ec-demo1/intranet-mcp-{urllib.parse.urlparse(AUTH).hostname}.json")


def request(url, data):
    """*.localhost (the local cluster) is 127.0.0.1, which macOS' resolver does not know: asked by address,
    with the name in the Host header — what Keycloak puts in the token's issuer."""
    parts = urllib.parse.urlsplit(url)
    if parts.hostname and parts.hostname.endswith(".localhost"):
        local = parts._replace(netloc=f"127.0.0.1:{parts.port or 80}")
        return urllib.request.Request(urllib.parse.urlunsplit(local), data=data, headers={"Host": parts.netloc})
    return urllib.request.Request(url, data=data)


def post(path, **form):
    data = urllib.parse.urlencode({"client_id": CLIENT, **form}).encode()
    try:
        with urllib.request.urlopen(request(f"{OIDC}/{path}", data), timeout=20) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        try:
            return json.loads(body)
        except ValueError:
            return {"error": f"http {e.code}", "error_description": body[:200]}


def save(tokens):
    os.makedirs(os.path.dirname(STORE), exist_ok=True)
    fd = os.open(STORE, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump({"refresh_token": tokens["refresh_token"]}, f)


def login_device():
    start = post("auth/device", scope="openid")
    if "device_code" not in start:
        sys.exit(f"login failed: {start.get('error')}: {start.get('error_description', '')}")
    print(f"Open {start.get('verification_uri_complete') or start['verification_uri']} and sign in"
          f" (code {start['user_code']}).", file=sys.stderr)
    interval = start.get("interval", 5)
    deadline = time.time() + start.get("expires_in", 600)
    while time.time() < deadline:
        time.sleep(interval)
        tokens = post("token", grant_type="urn:ietf:params:oauth:grant-type:device_code",
                      device_code=start["device_code"])
        if "access_token" in tokens:
            save(tokens)
            print("Signed in.", file=sys.stderr)
            return
        if tokens.get("error") == "slow_down":
            interval += 5
        elif tokens.get("error") != "authorization_pending":
            sys.exit(f"login failed: {tokens.get('error')}: {tokens.get('error_description', '')}")
    sys.exit("login timed out")


def login_password():
    user = os.environ.get("INTRANET_MCP_USER") or input("User: ")
    password = os.environ.get("INTRANET_MCP_PASSWORD") or getpass.getpass("Password: ")
    tokens = post("token", grant_type="password", username=user, password=password,
                  scope="openid")
    if "access_token" not in tokens:
        sys.exit(f"login failed: {tokens.get('error')}: {tokens.get('error_description', '')}")
    save(tokens)
    print("Signed in.", file=sys.stderr)


def access_token():
    try:
        with open(STORE) as f:
            refresh = json.load(f)["refresh_token"]
    except (OSError, ValueError, KeyError):
        sys.exit(f"Not signed in: run `{sys.argv[0]} login` first.")
    tokens = post("token", grant_type="refresh_token", refresh_token=refresh)
    if "access_token" not in tokens:
        sys.exit(f"The session expired ({tokens.get('error')}): run `{sys.argv[0]} login` again.")
    if tokens.get("refresh_token"):
        save(tokens)
    return tokens["access_token"]


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else "token"
    if command == "login":
        login_password() if "--password" in sys.argv else login_device()
    elif command == "logout":
        if os.path.exists(STORE):
            os.remove(STORE)
    elif command == "header-json":
        print(json.dumps({"Authorization": f"Bearer {access_token()}"}))
    elif command == "token":
        print(access_token())
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()

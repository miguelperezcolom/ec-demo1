# Runbook — verifying user → Keycloak propagation (and the SMTP relay)

How to confirm, on a live cluster, that creating/editing/deleting a user in the `users` console
reaches Keycloak through the identity outbox, and that Keycloak can send the set-password mail
through the postfix relay.

Read alongside the README section *Propagating users to Keycloak* and *Email and SMTP*. Everything
here is read-only except the deliberate create/update/delete of a throwaway test user and the
failure drill in step 7 (which you revert).

```sh
# Run once per shell. Everything below assumes these.
export NS=ec-demo1
TEST_ID=probe-$(date +%s)          # a throwaway user id; also becomes the Keycloak username
echo "test user id = $TEST_ID"
```

Helper to open a psql on the users database (local trust inside the pod, no password needed):

```sh
psql_users() { kubectl -n "$NS" exec -i deploy/ec-postgres -c postgres -- psql -U workflow -d users -tAc "$1"; }
```

Helper to run kcadm inside the Keycloak pod, logged in as the bootstrap admin:

```sh
KC_PW=$(kubectl -n "$NS" get secret keycloak-admin -o jsonpath='{.data.password}' | base64 -d)
kcadm() { kubectl -n "$NS" exec -i deploy/keycloak -- /opt/keycloak/bin/kcadm.sh "$@"; }
kc_login() { kcadm config credentials --server http://localhost:8080 --realm master --user admin --password "$KC_PW" >/dev/null; }
kc_login   # do this once; the config is cached in the pod for follow-up calls
```

---

## 0. Preconditions

```sh
# All three pods Running and Ready (1/1).
kubectl -n "$NS" get pods -l app=users
kubectl -n "$NS" get pods -l app=postfix
kubectl -n "$NS" get pods -l app=keycloak

# The relay password secret exists. Empty value = mail will queue, not send (see step 7).
kubectl -n "$NS" get secret postfix-relay -o jsonpath='{.data.password}' | base64 -d | wc -c
#   0  → not set yet: user sync still works, only the set-password email will fail
#  >0  → set: full path should work
```

---

## 1. Postfix relay is up and pointed at Gmail

```sh
# It should be listening on 25 and have loaded the relay config at boot.
kubectl -n "$NS" logs deploy/postfix | grep -iE "relayhost|starting|postfix/master" | tail
```

Expect a line showing the relay host `[smtp.gmail.com]:587`. No `fatal`/`panic`.

---

## 2. Keycloak has the SMTP server configured

```sh
kcadm get realms/ec-demo1 --fields smtpServer
```

Expect `host: postfix`, `port: 25`, `from: miguel@mateu.io`, `auth: false`. If it is empty, the
realm was imported before the `smtpServer` block was added — re-import or set it once by hand (see
Troubleshooting).

---

## 3. Create a user, watch it propagate

**Trigger:** open the demo console → **Users** (`https://ec1.mateu.io`, sign in as `demo`/`demo`),
and create a user whose **id is `$TEST_ID`** (echo it above), with an email you can receive at, and
Active status.

Now verify, in order:

```sh
# a) The outbox took the intent. Immediately after saving you may catch it pending;
#    within ~5s the relay should mark it delivered (deliveredAt set).
psql_users "select event_type, delivered_at is not null as delivered, attempts, abandoned
            from identity_outbox where aggregate_id='$TEST_ID' order by occurred_at;"
#   UserCreated | f | 0 | f      (just after save)
#   UserCreated | t | 0 | f      (after the next relay tick)

# b) The user now exists in Keycloak, keyed on username = the id.
kcadm get users -r ec-demo1 -q username="$TEST_ID" --fields username,email,enabled,requiredActions
#   expect one hit; requiredActions should contain UPDATE_PASSWORD

# c) The set-password mail was accepted by Gmail for delivery.
kubectl -n "$NS" logs deploy/postfix | grep -i "$TEST_ID\|to=<" | tail
#   look for status=sent (250 ... OK). status=deferred/bounced → see step 7 / Troubleshooting.
```

The `users` pod logs narrate the same story from its side:

```sh
kubectl -n "$NS" logs deploy/users | grep -iE "identity outbox|Created Keycloak user|set-password" | tail
```

---

## 4. Edit the user, watch the update propagate

**Trigger:** in the Users console, change the test user's name or email and save.

```sh
psql_users "select event_type, delivered_at is not null as delivered
            from identity_outbox where aggregate_id='$TEST_ID' order by occurred_at;"
#   a new UserUpdated row appears and flips to delivered

kcadm get users -r ec-demo1 -q username="$TEST_ID" --fields username,email,firstName
#   reflects the edit. No new password email on an update (by design).
```

---

## 5. Delete the user, watch the removal propagate

**Trigger:** in the Users console, delete the test user.

```sh
psql_users "select event_type, delivered_at is not null as delivered
            from identity_outbox where aggregate_id='$TEST_ID' order by occurred_at;"
#   a UserDeleted row appears and flips to delivered

kcadm get users -r ec-demo1 -q username="$TEST_ID" --fields username
#   expect [] — gone from Keycloak too. This is the security-relevant half: no orphaned login.
```

---

## 6. (Optional) the audit window

Delivered rows are kept for the retention window (default 7 days) then purged by the daily job.
Right after the steps above you should still see the delivered rows:

```sh
psql_users "select event_type, occurred_at, delivered_at from identity_outbox
            where aggregate_id='$TEST_ID' order by occurred_at;"
```

---

## 7. Failure drill — prove the retry, not just the happy path

Break the relay, make a change, watch the outbox retry rather than lose it, then heal it.

```sh
# Break delivery: scale postfix to zero so Keycloak's SMTP hop fails.
kubectl -n "$NS" scale deploy/postfix --replicas=0
```

Create another throwaway user in the console (id `$TEST_ID-b`). The *user sync itself* still
succeeds — it does not depend on mail — so this really tests the mail path's resilience. To instead
test the **outbox** retry, break the Admin API path (e.g. temporarily set a wrong
`KEYCLOAK_ADMIN_PASSWORD`), create a user, and watch:

```sh
watch -n2 "kubectl -n $NS exec -i deploy/ec-postgres -c postgres -- \
  psql -U workflow -d users -tAc \
  \"select event_type,attempts,delivered_at is not null,next_attempt_at,abandoned \
    from identity_outbox where aggregate_id like '${TEST_ID}-b%';\""
#   attempts climbs, next_attempt_at pushes out with backoff, delivered stays false.
#   After max-attempts (default 10) abandoned flips true and it stops — that is the poison-pill guard.
```

Heal it (restore the real password / scale postfix back) and the next tick delivers what had not
yet abandoned:

```sh
kubectl -n "$NS" scale deploy/postfix --replicas=1
# if you changed the admin password env, revert it and let the users pod roll
```

> A row that reached `abandoned=true` will **not** self-heal — that is intentional. Requeue it by
> clearing its state once the underlying cause is fixed:
> `psql_users "update identity_outbox set abandoned=false, attempts=0, next_attempt_at=now() where abandoned;"`

---

## 8. Cleanup

Delete any test users left in the console, then confirm nothing lingers:

```sh
psql_users "select count(*) from identity_outbox where aggregate_id like '${TEST_ID}%';"
kcadm get users -r ec-demo1 -q username="$TEST_ID" --fields username
```

---

## Troubleshooting

| symptom | likely cause | check / fix |
|---|---|---|
| outbox row stays `delivered=f`, `attempts` climbing | Keycloak Admin API unreachable or refusing | `kubectl -n $NS logs deploy/users \| grep -i "not delivered"` — the reason is logged verbatim |
| user in Keycloak but no email | relay password unset, or Gmail refused | `kubectl -n $NS get secret postfix-relay -o jsonpath='{.data.password}' \| base64 -d \| wc -c`; then postfix logs for `status=` |
| postfix log shows `status=bounced ... Username and Password not accepted` | App Password wrong / 2FA off on the Google account | regenerate the Gmail App Password, update `POSTFIX_RELAY_PASSWORD`, re-run `deploy.sh` |
| postfix `status=sent` but mail lands in spam | DNS not aligned for `@mateu.io` | verify SPF/DKIM/DMARC — see README *Email and SMTP* |
| `smtpServer` empty in step 2 | realm imported before the block existed | in Keycloak admin console → Realm settings → Email, set host `postfix` port `25`, or re-import the realm |
| `kcadm`/`psql_users` "deploy not found" | different release/pod names | `kubectl -n $NS get deploy` and substitute the real names |

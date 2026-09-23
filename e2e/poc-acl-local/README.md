# PoC ACL — end to end, locally

The whole CRS → Opera path on one machine, against the **real EventConductor orchestrator** and the
**Opera double** (`opera-mock`). Nothing reaches a real Opera tenant.

```sh
# build: integration-model first (installed), then the eight services — with clean: a Boot jar
# repackaged without it keeps the integration-model it copied last time
(cd integration-model && mvn -q install)
for m in booking partners crs-integration-service mapping-service pms-integration-service opera-mock \
         communication-service integrations-service; do
  (cd $m && mvn -q clean package -DskipTests); done

./e2e/poc-acl-local/infra.sh     # Postgres (a database per service), Redpanda, mailpit, the orchestrator
./e2e/poc-acl-local/apps.sh      # the eight services, logs in e2e/poc-acl-local/logs/
python3 e2e/poc-acl-local/scenario.py
```

`infra.sh` imports the definitions from a local clone of `ec-definitions` next to this repository
(`EC_DEFINITIONS` to point elsewhere), branch `EC_DEFINITIONS_BRANCH` (default `master`).

What `scenario.py` walks through, checking each step:

1. The seeded partners wait for their profile type to be mapped.
2. A booking of a hotel with no integration is held — on `INTEGRATION_INACTIVE:PMI01` and on its
   missing codes, the same causes for every process — and nothing reaches Opera.
3. The hotel's integration is registered and onboarded gate by gate (`alta-integracion`): the
   connection verified, the catalogues contrasted, the partners projected, the mapping approved,
   the backfill projecting the reservation before the activation, and the activation releasing
   what waited. The reservation is in Opera with translated codes, fixed nightly rates, the board
   as a package, the partner's profile, the stay routed to the partner's folio window and the CRS
   version in a UDF; and the CRS learns where it landed.
   3b. A property nobody configured in Opera stops its onboarding until it is configured.
4. A modification is written over, in order; the deposit is not applied twice.
5. Opera answers 503 for a while: the write is retried until it goes through, and an alert goes
   out while it keeps being retried.
6. Opera refuses (no room left): the process waits on a named `PMS_REJECTED` cause.
7. Cancelling frees the room; resolving the cause lets the refused reservation through.
8. A cancellation of a reservation Opera does not have yet waits for it, and follows it.
9. People were told: causes, retries, refusals, and onboardings that need someone.

Both scripts start from nothing every time: the databases and the broker are on tmpfs.

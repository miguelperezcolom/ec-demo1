# PoC ACL — end to end, locally

The whole CRS → Opera path on one machine, against the **real EventConductor orchestrator** and the
**Opera double** (`opera-mock`). Nothing reaches a real Opera tenant.

```sh
# build: integration-model first (installed), then the six services
(cd integration-model && mvn -q install)
for m in booking partners crs-integration-service mapping-service pms-integration-service opera-mock; do
  (cd $m && mvn -q package -DskipTests); done

./e2e/poc-acl-local/infra.sh     # Postgres (a database per service), Redpanda, orchestrator 2.18.0
./e2e/poc-acl-local/apps.sh      # the six services, logs in e2e/poc-acl-local/logs/
python3 e2e/poc-acl-local/scenario.py
```

`infra.sh` imports the definitions from a local clone of `ec-definitions` next to this repository
(`EC_DEFINITIONS` to point elsewhere), branch `EC_DEFINITIONS_BRANCH` (default `master`).

What `scenario.py` walks through, checking each step:

1. The seeded partners wait for their profile type to be mapped; approving it projects them.
2. A tour-operator booking with a deposit waits on six causes — shared by every process of that
   booking — and reaches Opera when they are approved: translated codes, fixed nightly rates, the
   board as a package, the partner's profile, the stay routed to the partner's folio window, the
   CRS version in a UDF; and the CRS learns where it landed.
3. A modification is written over, in order; the deposit is not applied twice.
4. Opera answers 503 for a while: the write is retried until it goes through, and an alert goes
   out while it keeps being retried.
5. Opera refuses (no room left): the process waits on a named `PMS_REJECTED` cause.
6. Cancelling frees the room; resolving the cause lets the refused reservation through.
7. A cancellation of a reservation Opera does not have yet waits for it, and follows it.

Both scripts start from nothing every time: the databases and the broker are on tmpfs.

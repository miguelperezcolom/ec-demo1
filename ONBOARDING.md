# Working on this

What someone joining needs, in the order they need it.

## 1. Look at it first

| | | |
|---|---|---|
| Console | https://ec1.mateu.io | `demo` / `demo` |
| Keycloak | https://auth.ec1.mateu.io | `admin` / *ask* |
| Grafana | https://grafana.ec1.mateu.io | `admin` / *ask* |
| Kafka console | https://kafka.ec1.mateu.io | `admin` / *ask* |

Only the console's demo user is in version control, because it is in the realm file and is meant
to be public. **The other three passwords live in `deploy/.secrets/credentials.env`, which is
git-ignored, and have to be sent to you out of band** — never pasted into an issue, a commit or a
chat that is logged. They are generated per deployment: a colleague who runs `deploy.sh` against a
different cluster gets different ones.

Grafana has two dashboards written for this deployment: **EventConductor** (is it keeping up,
where does the time go, is the relay the constraint) and **EventConductor — nodes** (CPU per
component, throttling, the JDBC pool, GC).

## 2. Understand what is where

Three repositories, and the split matters:

| repo | what it holds |
|---|---|
| **ec-demo1** (this one) | the deployment: chart values, manifests, the shell and gateway sources, the load rig |
| [**ec-definitions**](https://github.com/miguelperezcolom/ec-definitions) | the workflow, form and rule definitions the three engines import at startup — `master` is protected, so changes go through a PR |
| [**eventconductor**](https://github.com/miguelperezcolom/eventconductor) | the engine itself. Nothing here is built from it; it runs from published images |

A process is changed by a pull request to **ec-definitions**, not by an API call. A push to its
`master` fires two webhooks and both engines re-import within seconds — you do not need to restart
anything or ask anyone.

## 3. Before you change the deployment

```sh
./deploy/build-images.sh     # only when shell/ or gateway/ changed
./deploy/deploy.sh           # everything else, idempotent, safe to re-run
```

You need `kubectl` pointed at the CloudFleet cluster, plus `helm`, `docker` and `gh`. Ask for
cluster access first — it is not something you can grant yourself.

Four things about this cluster that will cost you an afternoon if nobody says them:

- **PostgreSQL is on an `emptyDir`.** That is what makes it local NVMe instead of a network
  volume, and it means the database dies with the pod — the engine's schema and Keycloak's
  database both. It happened once during an upgrade while every pod stayed `Running` and every
  endpoint answered 200. The pod is annotated `karpenter.sh/do-not-disrupt` now, but treat any
  data here as disposable.
- **The fleet's CPU limit can only be changed through the CloudFleet Fleet API.** `kubectl patch`
  on a NodePool is refused outright, and so is creating one.
- **Not every instance type exists.** Asking for one that does not — `ccx33`, here — gets you
  "no instance type met all requirements" from Karpenter and no node, which reads exactly like a
  quota problem. Probe with a throwaway pod before designing around a type.
- **Do not touch the `ingress-nginx` Helm release.** It holds the LoadBalancer the DNS points at.
  Reinstalling it changes the IP and every hostname breaks until DNS catches up.

## 4. Before you quote a number

Two measurements, and they cannot come from the same run — see the README for the full argument.

```sh
./deploy/loadgen.sh 20 1        # unsaturated  -> cost per transition
./deploy/loadgen.sh 5000 50     # saturated    -> throughput
./deploy/measure.sh             # reports both, and says which run each is valid for
```

Mistakes worth not repeating, all of them made here:

- **Measuring mid-burst.** Throughput sampled while the producer is still running reads less than
  half the steady-state figure.
- **Measuring right after a restart.** The first run against a cold JVM and a schema being created
  read 5.5 processes/s; the identical load minutes later read 17.2.
- **Reading cumulative counters as a rate.** `nr_throttled / nr_periods` from `cpu.stat` read once
  is not a percentage, and taking it for one produced a confident and completely wrong diagnosis.
- **Trusting an exit code for the outcome.** A load job reported success while producing 1500
  messages the engine silently discarded. Check that processes were created, not that the producer
  exited 0.

## 5. Known gaps

Documented in the README's notes, and worth knowing before you go hunting: the test worker cannot
be run at load in either persistence mode, and the engine's ceiling here is the outbox relay
publishing synchronously — not CPU, which sits below half everywhere. Neither is a mystery; both
are written down with the measurements behind them.

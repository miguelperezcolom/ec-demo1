# ec-demo1 — EventConductor on Kubernetes

A complete EventConductor deployment: the engine, a human-task front end, an identity provider,
a message broker, a database and a full observability stack — running on a CloudFleet/Hetzner
cluster, deployable from an empty cluster with one script.

The definitions live in a repository of their own,
[`ec-definitions`](https://github.com/miguelperezcolom/ec-definitions): the orchestrator, the forms
engine and the rule engine each clone it at startup and import what they find under `definitions/`,
so a process is changed by a pull request rather than by an API call.

```
                         https://ec1.mateu.io
                                 │
                        ingress-nginx (TLS, Let's Encrypt)
                                 │
                            gateway  ── requires a Keycloak token on the three backend paths
                    ┌────────────┼────────────┬──────────────┐
                    │            │            │              │
                 /_workflow   /_forms     /_worker          /**
                    │            │            │              │
              orchestrator     forms       worker          shell  ── the only page a user loads
                    │            │            │              │
                    └────── PostgreSQL ───────┴──────────────┘
                    └────── Redpanda (Kafka) ─┘

  https://auth.ec1.mateu.io     Keycloak (realm ec-demo1, client demo)
  https://grafana.ec1.mateu.io  Grafana ── Prometheus · Loki · Tempo
  https://kafka.ec1.mateu.io    Redpanda Console ── the event stream itself
```

## What is in here

| | |
|---|---|
| `shell/` | The Mateu shell — authenticates against Keycloak, hosts the other UIs as remote menus, carries the branding |
| `gateway/` | Spring Cloud Gateway — routes the console and enforces the token |
| `deploy/chart/eventconductor/` | The engine's Helm chart, vendored (see `VENDORED.md`) |
| `deploy/manifests/` | Keycloak, worker, shell, gateway, Kafka console, ingress, certificate issuers |
| `deploy/observability/` | Helm values for Prometheus, Grafana, Loki, Tempo and Alloy |
| `deploy/deploy.sh` | The whole thing, from an empty cluster |

New here? **[ONBOARDING.md](ONBOARDING.md)** — access, what lives in which repository, and the
four things about this cluster that otherwise cost an afternoon.

## Deploy

```sh
./deploy/build-images.sh      # shell + gateway → Docker Hub (only when their code changed)
./deploy/deploy.sh            # everything else, idempotent
```

Between the two, the DNS records have to exist, pointing at the address `deploy.sh` prints after
installing the ingress controller: `ec1` and `*.ec1` under your domain. Two records rather than
four, because the wildcard covers `auth.ec1`, `grafana.ec1`, `kafka.ec1` and whatever gets added
next. Certificates are issued automatically once the names resolve.

Passwords are generated on the first run into `deploy/.secrets/credentials.env`, which is
git-ignored. The demo user is `demo` / `demo`, from the realm file.

## The demo, in one pass

1. **Open the console** at `https://ec1.mateu.io` and log in as `demo` / `demo`. The menu bar is
   assembled from three separate applications; none of their screens is written in the shell.
2. **Workflow → Definitions.** The three definitions are already there — the orchestrator cloned
   this repository at startup. Open *Order fulfilment* to see its graph.
3. **Start a process** and give it a `TEST_CONFIG` variable. Nothing here implements a single
   business step: one test worker answers every task, and this variable is what tells it how.

   ```json
   {"default": {"durationMs": 400, "outcome": "COMPLETED"}}
   ```
4. **Watch it run.** It validates, then reserves stock and charges the card at the same time,
   then stops at *Review shipping* — a human task. The step stays `PENDING` and the forms engine
   creates a form execution for it; nothing advances until a person answers. That step is the one
   that names `topic: forms`; drop that line and the worker would answer it too.
5. **Answer it** from **Forms → My tasks**. Tick *Approve shipping*, pick a carrier, submit. The
   field values become process variables, and `approved == 'true'` is what routes the flow to
   *Ship order* rather than *Cancel order*.
6. **Then break it.** Start another one and make the charge fail:

   ```json
   {"default": {"durationMs": 400},
    "tasks": {"charge-card": {"outcome": "ERROR", "reason": "card declined"}}}
   ```

   The saga rolls back: *Release stock* and *Refund card* run in reverse order, and nothing had
   to be coded to make that happen — `compensable` and `compensationStepId` on the two steps is
   the whole of it.
7. **Run one with no human in it.** `payment-review` leaves its `USER_TASK` on the default topic,
   so the worker plays the reviewer:

   ```json
   {"tasks": {"verify-payment": {"variables": [{"name": "paymentReceived", "value": "true"}]}}}
   ```

   The variable it hands back is what the guards read, so the process routes to *Confirm booking*
   and the `JOIN·XOR` cancels the other branch. Swap the value for `"false"`, or use
   `{"outcome": "NO_REPLY"}` to let the 30-second deadline fire instead.
8. **Watch the events themselves** at `https://kafka.ec1.mateu.io`. This engine is event-driven end
   to end, so when a process does not move the question is always the same — was the message
   produced, and did anyone consume it. Four topics answer it: `upstream` (what was asked of the
   engine), `outbox` (every state change it recorded), `downstream` (tasks for workers) and `forms`
   (tasks for people). Consumer-group lag per partition is on the Groups tab.
9. **Look at what happened.** *Worker → Received tasks* shows every task the worker was handed
   and which scenario answered it. Grafana has the logs of every pod (Loki), the engine's metrics
   (Prometheus). Traces are wired but not yet arriving — see below.

## Measuring it

```sh
./deploy/loadgen.sh 20 1        # unsaturated
./deploy/measure.sh             # -> cost per transition

./deploy/loadgen.sh 5000 50     # saturated
./deploy/measure.sh             # -> throughput
```

Two numbers, and they cannot come from the same run.

**Cost per transition** is the gap between one step finishing and the next starting. Nothing but
the engine happens in that window — writing the transition, publishing it to the outbox, relaying
it, routing it, dispatching the next task — so it does not move when the workers get faster or
slower. It is the answer to *does the orchestrator resolve steps quickly*.

**Throughput** is steps and processes per second across the whole run. It answers *how much can
this deployment absorb*, and it includes the worker's simulated 200ms and every second a step
spent queued. Raise `durationMs` in `TEST_CONFIG` and it collapses without the engine having
changed at all.

Measured here, the same workflow on the same cluster:

| | transitions | throughput |
|---|---|---|
| 20 processes at 1/s | **37 ms** mean, 34 p50, 54 p95 | 3.6 steps/s |
| 5000 processes at 50/s | 24 355 ms mean | **135 steps/s** |

A factor of 650 between the two transition figures, and none of it is the engine getting slower —
under saturation that gap is queueing and stops describing the engine at all. Reading a low
arrival rate as "the engine is fast", or a saturated one as "the engine is slow", are the same
mistake pointing in opposite directions.

## What one orchestrator can carry

The question this deployment exists to answer: *how many processes a second, at N steps each,
before the orchestrator is the bottleneck.* Measured on `orchestration-only`, a definition with no
`ACTION` in it — only `START`, `END` and a chain of pass-through `JOIN`s, so no task is ever
dispatched and no worker is involved. Twelve transitions per process; `processes/s x 12` is the
orchestrator's transition rate and nothing else's.

**Measured as a ladder**, because one saturated run gives you a rate and cannot tell you whether
that rate is a ceiling. Four runs, each at an arrival rate the producer actually held — these are
the first figures on this page taken at the rate written on their label, and `loadgen.sh` now
prints the rate it achieved so the claim is checkable:

| arrivals/s | processes | throughput | transitions/s | drained |
|---|---|---|---|---|
| 20 | 1200 | 13.06/s | 157 | yes |
| 40 | 1600 | 13.34/s | 160 | yes |
| 80 | 2400 | 13.50/s | 162 | yes |
| 160 | 3200 | 13.76/s | **165** | yes |

**Eight times the arrival rate buys five percent more throughput.** That is the shape of a ceiling,
and it is a shape rather than a number — which is why the ladder is worth the four runs. All four
drained completely: 8400 processes, zero errors, zero timeouts. It queues; it does not fall over.

**The ceiling is not CPU, and it is not the hardware.** At the top of the ladder, with **2903
processes in flight**:

    orchestrator CPU     0.44 of 2.0     22%, and never throttled
    outbox pending       18 rows
    threads waiting on a JDBC connection 0
    PostgreSQL           0.39 cores
    Redpanda             0.14 of 2.0

Nearly three thousand processes waiting, and nothing in the deployment is busy. The outbox does not
even back up, and those 18 rows locate the constraint more precisely than the spare CPU does: the
relay is not slow at *reading* its backlog, it is slow at *publishing*. The queue forms in front of
it, not inside it.

It is the outbox relay publishing **synchronously**, one message at a time, waiting for `acks=all`
on each:

    batch deliver   2.44 s for a batch of 497   =>  4.9 ms per message
    redpanda CPU    0.14 of 2.0                     idle while this happens

That is deliberate and documented in the engine's own configuration: asynchronously, a send to a
broker that is down still reports success and the relay marks the row `Sent`. The outbox stops
being transactional. So the round-trip is the price of the guarantee, and it is the ceiling.

### Turning knobs up made it slower

| partitions | relay-concurrency | process-parallelism | transitions/s |
|---|---|---|---|
| 6 | 1 (default) | 1 (default) | 94, with 7335 rows stuck in the outbox |
| 6 | 4 | 8 | **184** |
| 6 | 12 | 16 | 160 |
| 24 | 4 | 16 | 125 — superseded, see the ladder: 165 |

The first row is the one worth reading twice: the defaults are a 500ms poll of at most 100 rows by
a single thread — 200 events/s whatever the hardware — and the backlog that produces looks exactly
like an engine that cannot keep up.

Past that, more threads made it worse. The relay's claim holds row locks for the length of its
transaction, so additional relay threads contend rather than parallelise, and more partitions
spread the same synchronous publishing thinner.

**Partitions cannot be reduced.** Raising `outbox` and `upstream` from 6 to 24 to test that
hypothesis is not undoable. Recreating the topics is the only way back, and it means dropping
whatever they hold — which is also why the 6-partition rows above cannot be re-measured.

**Those three rows were measured with the old rig**, before `loadgen.sh` was fixed to hold the rate
it is given, so each was fed below its label and each is a floor rather than a ceiling: 184 is
probably low too. The 24-partition row is the live configuration, and the ladder supersedes it —
165 transitions/s, not 125. That gap is not a change to the engine. It is what happens when a
deployment is finally fed at the rate it was always being asked for.

So, on this deployment as it stands:

    ~165 transitions/s   =>  13.8 processes/s at 12 steps
                             23.6 processes/s at  7 steps, if the orchestrator were the only cost

The measurement disagrees with that second line, and the disagreement is the useful part:
`notify-parallel` has 7 steps and runs at 19.2 processes/s, not 23.6. The missing 4 is the worker —
a real round trip over Kafka to a process that has to answer — which `orchestration-only`
deliberately does not have. The projection is the orchestrator's share of the budget, never the
whole of it, and the difference between the two is the only honest way to see the worker's.

## Giving it work

```sh
./deploy/loadgen.sh 1500 25 notify-parallel     # count, arrivals per second, definition
./deploy/loadgen.sh 500 10 payment-review       # a human task the worker answers
TEST_CONFIG='{"tasks":{"charge-card":{"outcome":"ERROR"}}}' ./deploy/loadgen.sh 200 5 order-fulfilment
```

A Job that produces `ProcessCreationRequested` events onto the same `upstream` topic everything
else uses, so it drives this deployment rather than a rig of its own. No image to build — the
Redpanda image already on the node ships `rpk`.

Measured on the topology above, EventConductor 2.5.0: **5000 instances of `notify-parallel` at
50/s, all 5000 completed, zero errors, in 260 seconds** — **19.2 processes/s and 135 step
executions/s**.

| | processes/s | steps/s | duration |
|---|---|---|---|
| shared nodes, 2.2.1 | 2.6 | 18 | 32 min |
| separated nodes, 2.2.1 | 12.7 | 89 | 6.5 min |
| separated nodes, 2.3.0 | 17.2 | 120 | 4.9 min |
| 2.5.0, at a rate actually held | **19.2** | **135** | **4.3 min** |

**Only the last row was taken at the rate on its label.** The first three say 50/s and were
produced at 29 — see the fourth lesson below. The gap between the last two is almost entirely that:
the same cluster and the same definition, measured on the same afternoon, read 17.6 processes/s at
29 arrivals/s and 19.2 at 50. Feeding it properly is worth more than the version bump, and at 29/s
it had moments with nothing to do.

Almost all of the first jump is placement, not tuning: the orchestrator was pinned at 11% of a
core on the shared topology and reached 860m once it had a node to itself, because it had been
sharing two shared vCPUs with the broker.

Four things those runs took to learn, all of them about measurement rather than about the engine.

An early attempt failed almost entirely — 4722 of 5000 in ERROR — because `defaultStepTimeoutMs`
was two minutes, sized against what the worker simulates (200ms) rather than against how long a
step waits in a burst. A deadline starts when the step starts, and under load a step is queued for
nearly all of it.

Throughput sampled mid-burst reads less than half the steady-state figure, because the orchestrator
is splitting its attention between accepting new processes and stepping the ones it has. Measure
end to end.

And a run started immediately after a restart measures the restart. The first 2.3.0 run read 5.5
processes/s against 17.2 for the identical load minutes later, with nothing changed but a warm JVM
and a schema that already existed.

And a rig that could not produce the rate it was given, silently. `loadgen.sh` slept a whole
second after each batch, on top of however long `rpk` took to start, connect, produce and exit — so
a run asking for 50/s produced 5000 events in 174 seconds rather than 100, at 29/s, and nothing
reported it because nothing was measuring the rate achieved. It now paces against a deadline and
prints `arrival rate: X/s produced, N/s requested` on every run, warning when a batch was already
late. A throughput figure is only as good as the arrival rate it was taken at, and that rate has to
be measured rather than assumed — including, and especially, when you wrote it on the command line
yourself.

`notify-parallel` is the useful default for volume: three parallel `ACTION`s and a barrier, no
human in it. `order-fulfilment` stops at its `USER_TASK`, so loading it builds a backlog of
waiting tasks instead — a different thing to watch, and also worth watching.

## The node topology

Five groups, each on hardware chosen for what it does, and kept apart by pod anti-affinity rather
than by hope — an instance-type selector alone lets Karpenter pack two components onto one node
the moment they both fit.

| group | instance | why |
|---|---|---|
| `postgres` | ccx13, alone | dedicated vCPU and **local NVMe**: a WAL commit blocks on fsync, and on the shared-vCPU line that syscall is stalled by noisy-neighbour CPU steal |
| `orchestrator` | ccx13, alone | the thing under test should not share a core with what it drives |
| `worker` | ccx13, alone | same |
| platform | cx43 | redpanda, forms, rules, keycloak, shell, gateway, kafka console — none of them blocks on fsync |
| observability | ccx23 | its own namespace and its own instance type; anti-affinity is namespace-scoped and could not keep it off the engine's nodes from here |

Two things worth knowing before changing it. **Postgres is on an `emptyDir`** — that is what makes
it local NVMe rather than a network volume, and it means the data dies with the pod. It is the
right trade for a load rig and the wrong one for anything else. And **this fleet has no `ccx33`**:
Karpenter answers a request for one with "no instance type met all requirements" and creates
nothing, which reads exactly like a quota problem and is not. Probe a type with a throwaway pod
before designing around it.

The fleet's own cpu limit is the binding constraint, and it can only be changed through the
CloudFleet Fleet API — `kubectl` is refused. This topology asks for 20 of the 24 it currently
allows.

## Notes worth knowing

- **A human task is opt-in.** The worker listens on `downstream`, the default destination for a
  step that names no topic — so it answers the whole workflow, human tasks included, which is what
  a definition under test wants. The forms engine listens on `forms` instead, and a `USER_TASK`
  reaches it only by naming `topic: forms`. `order-fulfilment`'s *Review shipping* does;
  `payment-review`'s *Verify payment* deliberately does not, which is how its 30-second deadline
  stays testable — `NO_REPLY` is a reviewer who never answered, on demand.

  The two cannot share `downstream`, and no consumer group fixes it: in different groups both
  receive every message and the worker answers the human task itself; in one group they compete
  for it.
- **The shell's Keycloak URL is compiled in.** Mateu writes `@KeycloakSecured` into the generated
  bootstrap page, so it cannot be an environment variable yet. Changing the hostname means
  rebuilding the shell image.
- **The gateway is what protects the backends.** The orchestrator, the forms engine and the
  worker only understand HTTP basic auth, so their UIs — which can pause definitions and cancel
  processes — would otherwise be open to anyone who typed the path. The gateway validates the
  realm's access token before any of them sees a request; see `SecurityConfig.java` for the two
  paths that stay public, and why they have to.
- **One replica of everything.** The Karpenter pool is capped at 8 CPU. The engine scales
  horizontally by design — raising `replicas` in `deploy/values/eventconductor.yaml` is the only
  change needed, since orchestrator instances coordinate through PostgreSQL advisory locks and
  the outbox rather than through a leader.
- **The rule engine is delo de mateyuployed at zero replicas.** None of these three workflows has a `RULE`
  step. Its Deployment and Service exist, so turning it on is a one-line change.
- **Traces work as of engine 2.5.0** — `eventconductor.step-over`, `eventconductor.dispatch-step`,
  `outbox relay` and the rest arrive in Tempo. It took three releases, and the last one is worth
  knowing about: the endpoint was configured under `management.otlp.tracing.endpoint`, which Boot 4
  deprecates *at level error* — the property is no longer bound and its metadata entry survives
  only to announce that. It reads back perfectly from the environment, so every check short of
  looking for the exporter bean said it was configured, including one done here. `OTEL_SERVICE_NAME`
  is set per engine as well, without which every span arrives as `unknown_service` and the three
  are indistinguishable.
- **Git webhooks are wired but return 500, and it is not this deployment's fault.** A push to
  master should reload the definitions instead of waiting for the next restart, and everything on
  this side is in place: the secret exists, the gateway routes `/workflow/webhooks/**` and
  `/forms/webhooks/**` publicly, and the engines are configured to verify the HMAC. The engine
  cannot serve them: all three webhook controllers declare `@PathVariable String provider` with no
  explicit name, and the published jars carry no `MethodParameters` attribute at all — the build
  configures `maven-compiler-plugin` itself, without a Spring Boot parent to add `-parameters`, so
  Spring cannot resolve the argument and every call dies with `IllegalArgumentException` before the
  signature is even checked. One line in the engine's root pom fixes it for all three:
  `<parameters>true</parameters>`. Until then, definitions reload on pod restart.
- **The worker exposes no metrics.** Same shape of gap: its image has actuator but no
  `micrometer-registry-prometheus`, so `/actuator/prometheus` is a 404. It is deliberately not
  annotated for scraping, rather than left as a target that is permanently down.
- **The Kafka console is behind HTTP basic auth**, because Redpanda Console's open-source build
  has no access control of its own and anyone who reaches it can produce and delete messages, not
  just read them. Its password is generated alongside the others.
- **Keycloak runs `start-dev`** behind the ingress, which is the right shape for a demo and not
  for production. It does keep its data in PostgreSQL, so accounts survive a restart.

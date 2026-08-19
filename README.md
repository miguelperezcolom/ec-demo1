# ec-demo1 — EventConductor on Kubernetes

A complete EventConductor deployment: the engine, a human-task front end, an identity provider,
a message broker, a database and a full observability stack — running on a CloudFleet/Hetzner
cluster, deployable from an empty cluster with one script.

It is also the **source of its own workflows**. The orchestrator and the forms engine clone this
repository at startup and import what they find under `definitions/`, so a process here is
changed by a pull request rather than by an API call.

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
```

## What is in here

| | |
|---|---|
| `definitions/workflows/*.ec` | Three workflow definitions, imported by the orchestrator from Git |
| `definitions/forms/*.ecform` | The forms their `USER_TASK` steps reference, imported by the forms engine |
| `shell/` | The Mateu shell — authenticates against Keycloak, hosts the other UIs as remote menus |
| `gateway/` | Spring Cloud Gateway — routes the console and enforces the token |
| `deploy/chart/eventconductor/` | The engine's Helm chart, vendored (see `VENDORED.md`) |
| `deploy/manifests/` | Keycloak, worker, shell, gateway, ingress, certificate issuers |
| `deploy/observability/` | Helm values for Prometheus, Grafana, Loki, Tempo and Alloy |
| `deploy/deploy.sh` | The whole thing, from an empty cluster |

## Deploy

```sh
./deploy/build-images.sh      # shell + gateway → Docker Hub (only when their code changed)
./deploy/deploy.sh            # everything else, idempotent
```

Between the two, the DNS records have to exist: `ec1`, `auth.ec1` and `grafana.ec1` under your
domain, pointing at the address `deploy.sh` prints after installing the ingress controller. A
wildcard `*.ec1` covers the last two and anything added later. Certificates are issued
automatically once the names resolve.

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
8. **Look at what happened.** *Worker → Received tasks* shows every task the worker was handed
   and which scenario answered it. Grafana has the logs of every pod (Loki), the engine's metrics
   (Prometheus). Traces are wired but not yet arriving — see below.

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
- **The rule engine is deployed at zero replicas.** None of these three workflows has a `RULE`
  step. Its Deployment and Service exist, so turning it on is a one-line change.
- **Traces do not flow yet, and it is not this deployment's fault.** Tempo is deployed, its OTLP
  endpoint accepts spans, and Grafana's datasource points at it correctly. The engine cannot
  produce any: the published `2.2.0` images carry the OpenTelemetry libraries but not the Spring
  Boot autoconfiguration that creates a `Tracer` and reads `management.otlp.tracing.endpoint` —
  Boot 4 moved it into `spring-boot-tracing` / `spring-boot-opentelemetry`, which are not on the
  classpath, so neither that property nor `management.tracing.sampling.probability` exists at all
  and both env vars are inert. `WorkflowTracingAutoConfiguration` then finds no tracer and runs
  every call untraced. Fixing it upstream is a dependency, not a config change; everything on this
  side is already in place for when it lands.
- **The worker exposes no metrics.** Same shape of gap: its image has actuator but no
  `micrometer-registry-prometheus`, so `/actuator/prometheus` is a 404. It is deliberately not
  annotated for scraping, rather than left as a target that is permanently down.
- **Keycloak runs `start-dev`** behind the ingress, which is the right shape for a demo and not
  for production. It does keep its data in PostgreSQL, so accounts survive a restart.

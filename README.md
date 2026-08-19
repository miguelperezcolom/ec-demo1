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
   then stops at *Review shipping* — a human task.
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
7. **Look at what happened.** *Worker → Received tasks* shows every task the worker was handed
   and which scenario answered it. Grafana has the logs of every pod (Loki), the engine's metrics
   (Prometheus) and the traces — one process is **one trace**, not one per hop, because the
   outbox row carries the producing trace's `traceparent`.

## Notes worth knowing

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
- **Keycloak runs `start-dev`** behind the ingress, which is the right shape for a demo and not
  for production. It does keep its data in PostgreSQL, so accounts survive a restart.

# Working on this

What someone joining needs, in the order they need it.

## 1. Look at it first

| | | |
|---|---|---|
| Console | https://ec1.mateu.io | `demo` / `demo` |
| Control console | https://console.ec1.mateu.io | `demo` / `demo`, and the realm role `ai-admin` |
| Keycloak | https://auth.ec1.mateu.io | `admin` / *ask* |
| Grafana | https://grafana.ec1.mateu.io | `admin` / *ask* |
| Kafka console | https://kafka.ec1.mateu.io | `admin` / *ask* |

Only the demo user is in version control, because it is in the realm file and is meant to be
public. It reaches both consoles: it holds the realm roles `user`, `admin` and `ai-admin`, and
`ai-admin` — a role of its own, kept separate from `admin` because only this host reaches the LLM
credentials — is what the control console requires. **Every other password lives in `deploy/.secrets/credentials.env`,
which is git-ignored, and has to be sent to you out of band** — never pasted into an issue, a
commit or a chat that is logged. They are generated per deployment: a colleague who runs
`deploy.sh` against a different cluster gets different ones.

That file also holds `CP_CRYPTO_KEY`, which is not a password to anything — it is the key the
control plane's stored LLM credentials are encrypted with. Losing it loses them; see §5.

Grafana has two dashboards written for this deployment: **EventConductor** (is it keeping up,
where does the time go, is the relay the constraint) and **EventConductor — nodes** (CPU per
component, throttling, the JDBC pool, GC).

## 2. Understand what is where

Three repositories, and the split matters:

| repo | what it holds |
|---|---|
| **ec-demo1** (this one) | the deployment: chart values, manifests, the load rig, and the eight applications it builds — two shells, the gateway, the booking, content, users and ia-agent services, and the IA control plane |
| [**ec-definitions**](https://github.com/miguelperezcolom/ec-definitions) | the workflow, form and rule definitions the three engines import at startup — `master` is protected, so changes go through a PR |
| [**eventconductor**](https://github.com/miguelperezcolom/eventconductor) | the engine itself. Nothing here is built from it; it runs from published images |

A process is changed by a pull request to **ec-definitions**, not by an API call. A push to its
`master` fires two webhooks and both engines re-import within seconds — you do not need to restart
anything or ask anyone.

## 3. Before you change the deployment

```sh
./deploy/build-images.sh     # only when one of the eight application modules changed
./deploy/deploy.sh           # everything else, idempotent, safe to re-run
```

You need `kubectl` pointed at the CloudFleet cluster, plus `helm`, `docker` and `gh`. Ask for
cluster access first — it is not something you can grant yourself.

Six things about this cluster that will cost you an afternoon if nobody says them:

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
- **The fleet's CPU budget has not been re-measured since the demo services and the control plane
  were added.** Seven more pods land in the platform group with the shell and the gateway. If they
  do not fit on the node already there, Karpenter asks for a second `cx43` and the fleet refuses —
  the pods sit `Pending` and it reads like a scheduling bug. Check the pods after the first deploy.
- **One database here is not disposable.** `cp-postgres` holds the control plane's catalogues and
  the encrypted LLM credentials, and it is the one thing in this namespace on a real
  PersistentVolume. Everything the first bullet says about data dying with the pod applies to the
  *engine's* PostgreSQL, not to this one — and deleting its PVC is not recoverable.

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

## 5. The chat panel needs a key you have to supply

`deploy.sh` generates every password except one. The console's chat panel is an LLM, and the
Anthropic key that pays for it is bought rather than derived, so the script writes a commented
`ANTHROPIC_API_KEY=` line into `deploy/.secrets/credentials.env` and creates the `ec-anthropic`
secret only once it is filled in.

That secret now goes to the **control plane**, not to the agent: on a brand-new deployment the
control plane seeds an Anthropic model with it. Without it everything else still works — the model
is catalogued with `credential: missing`, and **the console at
`https://console.ec1.mateu.io` is where you fix that**, under *LLMs → Anthropic → Replace
credential*. No redeploy, no restart; the agent picks it up within 30 seconds.

That is the shape of everything about the agent now. Its model, its system prompt, which MCP
servers it may reach and which documents it can search are all fields in that console, not
variables in a manifest.

The same is true of the embedding key that RAG retrieval needs — a second, separate credential,
because Anthropic has no embeddings API. Same place, same *Replace credential* action, on the
`embeddings` model rather than the `anthropic` one.

The gateway requires a Keycloak token on `/ai/**` for the same reason it requires one on the
engine's paths, and with less margin: every prompt that reaches the agent is billed.

`deploy.sh` also generates two things you will not be asked for and must not regenerate:
`CP_POSTGRES_PASSWORD` and `CP_CRYPTO_KEY`. The second is the AES key the control plane's stored
LLM credentials are encrypted with — **rotating it makes every one of them undecryptable**, because
nothing re-wraps them, and the only way back is entering the keys again. A re-run against an
existing cluster appends whichever of these is missing and leaves every password already stored in
the cluster exactly as it was.

## 6. Known gaps

Documented in the README's notes, and worth knowing before you go hunting: the test worker cannot
be run at load in either persistence mode, and the engine's ceiling here is the outbox relay
publishing synchronously — not CPU, which sits below half everywhere. Neither is a mystery; both
are written down with the measurements behind them.

Two more, both about the services added around the engine:

- **`booking`'s saga half has no definition to answer.** It consumes the `booking` topic, and no
  definition in `ec-definitions` names `topic: booking` yet. Adding `verify-booking-payment` there
  is a pull request to that repository, not a change here. Its CRUD and its MCP tools work now.
- **`users` serves gRPC on 9191 and nothing calls it.** Unauthenticated, inside the namespace only.
  Do not put it behind an ingress as it stands.
- **`ia-control-plane` serves `/internal/agents/{id}/config` with an API key in the clear.** Same
  shape of risk, higher stakes. It has no gateway route and must never get one; the protection is
  that absence plus the Service being cluster-internal.
- **RAG retrieval works, and needs a second credential you have to supply.** The catalogue and the
  store are wired end to end — ingest text on a source, and an agent composed with it gets a tool
  for it. But Anthropic has no embeddings API, so the embedding model is an OpenAI-shaped one and
  the Anthropic key does not pay for it. A fresh deploy seeds the source and the model with no
  credential; one field in the console fixes that. Until then the source is catalogued and
  unqueryable, and it says so.
- **The agent depends on the control plane to start serving.** A pod that has never reached it
  reports readiness DOWN, on purpose: it has no model. Losing the control plane later does *not*
  take the panel down — the agent keeps serving the last configuration it fetched and says
  `degraded` in `/actuator/health`. If the chat panel is 503, look at `ia-control-plane` and
  `cp-postgres` before looking at the agent.

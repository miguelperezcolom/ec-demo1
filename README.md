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
                            gateway  ── requires a Keycloak token on every backend path
      ┌──────────┬──────────┬────┴─────┬──────────┬─────────┬────────┬──────┐
      │          │          │          │          │         │        │      │
 /_workflow  /_forms   /_worker   /_booking  /_content  /_users    /ai     /**
      │          │          │          │          │         │        │      │
 orchestrator  forms     worker    booking    content    users   ia-agent  shell
      │          │          │          │          │         │        │      │
      │          │          │          │          │         │        │   the only
      │          │          │          │          │         │        │   page a user
      │          │          │          │          │         │        │   loads
      └── PostgreSQL ───────┴──────────┴──────────┴─────────┘        │
      └── Redpanda (Kafka) ─┴──────────┘                             │
                            └───────── MCP ───────────────────────────┘
                       (ia-agent calls the tools the engine and booking expose)

  https://console.ec1.mateu.io  the control console ── needs the `ai-admin` realm role
                                    │
                              (same gateway)
                                    │
                              /_ia-cp    /**
                                 │        │
                          ia-control-plane · control-shell
                                 │
                            cp-postgres ── its own volume, unlike everything above

  https://auth.ec1.mateu.io     Keycloak (realm ec-demo1, clients demo + control-plane)
  https://grafana.ec1.mateu.io  Grafana ── Prometheus · Loki · Tempo
  https://kafka.ec1.mateu.io    Redpanda Console ── the event stream itself
```

## What is in here

| | |
|---|---|
| `shell/` | The Mateu shell — authenticates against Keycloak, hosts the other UIs as remote menus, carries the branding |
| `gateway/` | Spring Cloud Gateway — routes the console and enforces the token |
| `booking/` | Bookings: a CRUD, the MCP tools the agent calls, and the worker side of the booking saga |
| `content/` | Content, labels and content types — a CRUD and nothing else |
| `users/` | Users, groups, roles and permissions, plus a gRPC endpoint that serves a user's roles and scopes |
| `ia-agent/` | The console's chat agent — an LLM that answers only by calling MCP tools |
| `ia-control-plane/` | The four catalogues the agent is configured from: LLMs and their credentials, MCP servers, RAG sources, and the agents that compose them |
| `control-shell/` | The control console's shell, on `console.ec1.mateu.io`, behind the `ai-admin` role |
| `grpc-interface/` | The generated stubs for `users`' gRPC contract. Not an application; no image |
| `deploy/chart/eventconductor/` | The engine's Helm chart, vendored (see `VENDORED.md`) |
| `deploy/manifests/` | Keycloak, the postfix mail relay, worker, shell, gateway, the four services, Kafka console, ingress, certificate issuers |
| `deploy/observability/` | Helm values for Prometheus, Grafana, Loki, Tempo and Alloy |
| `deploy/deploy.sh` | The whole thing, from an empty cluster |

New here? **[ONBOARDING.md](ONBOARDING.md)** — access, what lives in which repository, and the
four things about this cluster that otherwise cost an afternoon.

## Deploy

```sh
./deploy/build-images.sh      # the eight images this repo owns → Docker Hub (only when their code changed)
./deploy/deploy.sh            # everything else, idempotent
```

Between the two, the DNS records have to exist, pointing at the address `deploy.sh` prints after
installing the ingress controller: `ec1` and `*.ec1` under your domain. Two records rather than
five, because the wildcard covers `auth.ec1`, `grafana.ec1`, `kafka.ec1`, `console.ec1` and
whatever gets added next. The wildcard is in DNS only — each host still gets its own certificate,
issued automatically by HTTP-01 once the name resolves.

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
10. **Then look at the menus that are not the engine.** *Booking*, *Content* and *Users* are three
    more applications, each serving its own screens from its own pod — the shell states a path and
    nothing else about them.
11. **Ask the chat panel for something.** "Lista las reservas", "crea una reserva para Ana". It has
    no database and no screens: it answers by calling the MCP tools the orchestrator, the forms
    engine and the booking service advertise, and it is told to report a tool failure rather than
    answer around one. Ask it for something no tool covers and it will say so. It needs an
    Anthropic key — see the note below — and every prompt is billed.

## The services around the engine

Four applications that are not the engine, each one a pod, each one reached through the gateway on
a path of its own. They came from the `demo/` tree of the engine's own repository; what follows is
what they are here, not what they were there.

| | path | what it is |
|---|---|---|
| `booking` | `/_booking` | A booking CRUD, an MCP server, and a worker. The three are one pod on purpose: the tools the agent calls and the saga steps that confirm a booking act on the same aggregate |
| `content` | `/_content` | Content, labels and content types. A CRUD over its own database and nothing else |
| `users` | `/_users` | Users, groups, roles and permissions, plus `GetAuthInfo` over gRPC on 9191 |
| `ia-agent` | `/ai` | The chat panel's other half. It implements nothing: every answer is an MCP tool call or a refusal |

**They keep their own UIs.** Nothing about their screens is written in the shell — each declares a
`@UI` path, `ShellHome` names the same path in a `RemoteMenu`, and the gateway routes it. Adding
the fifth is those three lines and a manifest, and the path has to match in all three or the menu
renders empty.

**A database each, not a schema in the engine's.** The engine owns `workflow` through Flyway and
validates its schema against its own migration history at startup; three services running
`ddl-auto: update` inside it would be three writers with no shared history. `55-demo-db-init.yaml`
creates `booking`, `content` and `users` in the same PostgreSQL, the same way Keycloak's database
is created. They inherit that PostgreSQL's `emptyDir`, so treat what they hold as disposable.

**Only `booking` touches Kafka.** It consumes the `booking` topic and replies on `upstream` — one
consumer group of its own, subscribed to one topic, which is the arrangement the engine's own
configuration argues for at length. `content` and `users` arrived declaring a binder and three
bindings (`consumeOutbox`, `consumeUpstream`, `consumeWorkerEvent`) and implementing none of them,
under the engine's own group names — `orchestrator-outbox`, `orchestrator-upstream`,
`worker-group`. Deployed beside a real engine that would have taken partitions away from the
orchestrator and the test worker and dropped whatever it was handed. It is gone, not disabled.

### The agent

The chat panel posts to `/ai/api/agent/stream`. The agent asks the control plane which model,
which credential, which system prompt and which MCP servers; opens a fresh connection to each of
those servers — per prompt, not pooled, because Spring AI's auto-configured client holds one
persistent SSE connection and stays broken for the life of the pod once it drops — collects
whatever tools they advertise, and hands them to the LLM.

**It holds no configuration of its own.** Two environment variables, and that is the lot: where the
control plane is, and which agent it is.

The tools are the whole of what it can do. There is no path from a prompt to this deployment that
does not go through a server that chose to expose it, and the system prompt tells it to report a
tool failure rather than answer around it.

Two things follow from it being an LLM:

- **Every prompt is billed** to whatever credential the control plane serves. That is why the
  gateway requires a Keycloak token on `/ai/**` and not only on the CRUD paths. Mateu's chat client
  sends the bearer token itself, so requiring it costs nothing.
- **The key is not generated.** `deploy.sh` writes a commented `ANTHROPIC_API_KEY=` line into
  `deploy/.secrets/credentials.env`. If it is filled in, the control plane seeds its LLM with it;
  if not, the model is catalogued with `credential: missing` and the console is where you fix that.

The seeded model is `claude-sonnet-4-5`, a generation behind what the API offers. Changing it is
now a field in the console rather than a Deployment variable — which is most of what the control
plane is for.

### Propagating users to Keycloak

`users` is the source of truth for who a person is; Keycloak holds the copy that authenticates
them. Creating, editing or deleting a user here has to reach there, or the two drift — a user
edited in the console but not in Keycloak signs in under stale details, and one deleted here but
left there can still sign in at all. So every create, update and delete is propagated.

**It is identity only.** Username — which is this service's own user id — email, display name, and
whether the account is enabled. Roles and scopes are deliberately not sent: this service stays the
one place they are decided, and the gateway is meant to read them from it to enrich a token (the
gRPC half of that is [not yet wired](#notes-worth-knowing)). Copying them into Keycloak as well
would make two places that disagree.

**It goes through a transactional outbox, not a direct call.** Saving the user and calling Keycloak
cannot commit together — one is Postgres, the other an HTTP API — so the use case does not call
Keycloak at all. It writes the change to an `identity_outbox` table *in the same transaction* as the
user: either both land or neither does, and there is no window where the user is saved but the
intent to propagate is lost. A relay (`@Scheduled`, every few seconds) then drains the table,
delivers each change, and marks it done; failures are retried with a growing backoff and abandoned
after ten attempts so a permanently-bad change stops blocking the queue behind it. Delivered rows
are kept a week as an audit of what was propagated, then purged.

**Delivery is at-least-once, so it is idempotent.** The relay may hand the same change over twice
(delivered, then died before recording it), which is why the Keycloak side is an upsert keyed on
username — `POST` if absent, `PUT` if present, a losing `POST` race treated as an update — and a
deletion of an already-absent user is a success, not an error. Exactly-once across a database and a
foreign API is not on offer; at-least-once plus idempotent is the arrangement that is.

**The credential it uses is broad, on purpose-for-now.** The relay authenticates to the Admin API
as the realm's bootstrap admin, reusing the `keycloak-admin` Secret rather than a client of its own.
It needs only `manage-users`; the bootstrap admin can do anything. The clean version is a
confidential `users-service` client with a service account granted exactly that role, and it was
left out because it puts a client secret into the committed realm import while the bootstrap
password is a deploy-time Secret. The seam is one method in `KeycloakAdminClient`. Until then the
blast radius of this pod's credential is a Secret, not a file in git.

A user created this way has no credential yet, so it cannot sign in until one is set. That is what
[the SMTP relay](#email-and-smtp) is for: on create, the user is marked *must set password* and
Keycloak emails them the link. It is best-effort — a mail failure is logged and never fails the
create — so a user always lands in Keycloak; whether the email went is a separate, visible outcome.

### Email and SMTP

Keycloak has to send mail — the set-password link above, and password resets after — and it needs
somewhere to send it through. That somewhere is a **postfix relay**: a small, send-only pod that
exists to take mail off Keycloak and hand it to Gmail.

**It relays; it does not deliver.** Keycloak talks plain SMTP to `postfix:25` inside the cluster —
no TLS, no auth, because the hop never leaves the pod network — and postfix forwards everything to
`smtp.gmail.com:587` over an authenticated, encrypted connection. The split is the point. The one
secret, the Gmail App Password, lives only in postfix (the `postfix-relay` Secret), so the realm
import that points Keycloak at it carries **no credential and stays in version control** with
`auth: false`. And the actual delivery is Gmail's, with Gmail's reputation, rather than a cluster IP
that every provider would sink as spam.

**The App Password is pasted, not generated.** Like the Anthropic key, `deploy.sh` writes a
commented `POSTFIX_RELAY_PASSWORD=` line into `deploy/.secrets/credentials.env`. It needs a Google
account with 2-Step Verification on (a normal password will not authenticate over SMTP), and for
`miguel@mateu.io` that is the Workspace account for the domain. Without it, postfix starts but Gmail
refuses the relay and mail queues; user create/update/delete is unaffected, because that path does
not touch mail.

**DNS is what keeps the mail out of spam.** Because the envelope leaves through Gmail as an
authenticated `@mateu.io` sender, deliverability rides on the domain's existing Google email
authentication. For a Workspace domain this is normally already in place; verify, and add what is
missing:

| record | type | value | why |
|---|---|---|---|
| `mateu.io` | TXT (SPF) | `v=spf1 include:_spf.google.com ~all` | authorizes Google's servers to send as `@mateu.io` |
| `google._domainkey.mateu.io` | TXT (DKIM) | the key from Workspace → Apps → Gmail → *Authenticate email* | lets Google sign the mail so receivers can verify it |
| `_dmarc.mateu.io` | TXT (DMARC) | `v=DMARC1; p=none; rua=mailto:miguel@mateu.io` | asks receivers to report, and sets a policy once SPF/DKIM pass |

No `MX` change is needed — that governs *receiving*, and this relay only sends. If the account is a
personal `@gmail.com` with `mateu.io` as a "send as" alias rather than a Workspace domain, Gmail
rewrites the envelope sender and the `From:` will not align with these records; the Workspace domain
is the arrangement that makes the table above true.

### What is not wired yet

`booking`'s saga half needs a definition whose `ACTION` steps name `topic: booking`. The one it
was written for is `verify-booking-payment` — a human verifies a payment, a 30-second
`onTimeoutStepId` routes to cancellation, and an `XOR` join ends whichever branch wins — and it is
**not in [`ec-definitions`](https://github.com/miguelperezcolom/ec-definitions)**. Until it is
added there by a pull request, that pod joins its consumer group and is handed nothing. The CRUD
and the MCP tools do not depend on it.

The form it needs, `verify-payment`, is already there.

## The control console

A second console, on a host of its own: **`https://console.ec1.mateu.io`**, behind the `ai-admin`
realm role. Behind it is `ia-control-plane`, which holds the four catalogues the chat agent is
configured from.

| catalogue | what an entry is |
|---|---|
| **LLMs** | A model this deployment may call, and the API key that pays for it |
| **MCP servers** | A server an agent may be given the tools of. Not the tools — those the server declares at connection time, and a copy here would go stale in silence |
| **RAG sources** | A vector store, a collection inside it, and the model that embedded it. Searchable — see below |
| **Agents** | A prompt, one LLM, and the servers and sources it may reach. The only thing a running service is ever handed |

An agent refers to the other three by id and holds nothing of them, so a server's URL changes in
one place and every agent composed from it follows.

### Why a second host and not another menu

Two hosts mean two Keycloak clients, and that is the whole reason. A token minted for the demo
console is not a token for this one, so the gateway can demand `ai-admin` here and leave the demo
console alone. Both still enter through the same gateway, so there is still one place that checks
a token before any backend sees a request — and one place, `SecurityConfig.java`, where that rule
is written.

The realm defines three roles: `user` and `admin` for the demo console, and `ai-admin` for this
one. It is a role of its own and not the demo's `admin` on purpose — this host reaches the LLM API
keys and the demo's `admin` never does, so the two are separate grants and neither implies the
other. That is what lets an AI operator hold `ai-admin` without being able to drive workflows, and
a platform admin hold `admin` without being able to read a credential. The `demo` user carries all
three, because a single demo login is meant to reach everything; a real deployment would split
them. A new public client `control-plane` is in the realm file, with `directAccessGrantsEnabled`
off — unlike the demo client's — because trading a username and password for a token with no
browser is not a convenience anyone needs on the client that reaches the credentials.

### Why it has a database of its own

`postgres.localDisk: true` in `deploy/values/eventconductor.yaml` puts the engine's PostgreSQL on
an `emptyDir`. That is what makes it local NVMe rather than a network volume, which is what decides
how fast a WAL commit can fsync, which is the number this whole deployment exists to measure. The
price is that the data dies with the pod, and for an engine schema and three demo services' rows
that is the right trade.

It is the wrong trade for the only copy of this deployment's LLM credentials. A control plane whose
catalogue is gone after a pod restart is worse than the YAML file it replaces, because the point of
moving configuration into a UI is that it stays put. Flipping `localDisk` to false would have fixed
it in one line and slowed the measured path for everything, so instead there is a second, small
PostgreSQL — `cp-postgres`, 10Gi on `hcloud-volumes`, the same storage class Redpanda already uses.

### The credentials

Stored AES-256-GCM encrypted, with the key in the `ec-cp-crypto` secret and held nowhere else. In
the console the field is **write-only**: it shows `set` or `missing`, never the key, and saving the
edit form does not touch it — `UpdateLlmCommand` has no field for a credential, so replacing one is
a separate, confirmed action. That is not ceremony: a write-only field wired through an ordinary
update is how a working key gets blanked by someone changing a temperature.

Exactly one method in the service decrypts anything, and one endpoint serves the result:

```
GET /internal/agents/{agentId}/config     →  the resolved agent, API key in the clear
```

**It has no gateway route and must never get one.** The gateway routes `/_ia-cp/**` to this
service and nothing else; `/internal/**` on either host falls through to a shell's catch-all and
404s. Verified rather than assumed — a request for that path through the gateway reaches the
shell, not the control plane. The endpoint authenticates nothing itself, exactly like the users
service's gRPC port, and the same warning applies with more force.

What this protects: a database dump, a stolen volume snapshot, a backup, anyone with read access
to the table, and — the common case — a key appearing on a screen, in a listing, or in a log line.
What it does not protect against: someone holding both the database and `CP_CRYPTO_KEY`, which
includes anyone who can exec into the pod. That is the honest boundary.

**Rotating `CP_CRYPTO_KEY` makes every stored credential undecryptable.** Nothing re-wraps them, so
rotation means entering the keys again. `deploy.sh` generates it once into
`deploy/.secrets/credentials.env` and then leaves it alone, like the PostgreSQL passwords.

### Configuring it from git (GitOps)

The console is one way to change the catalogues; a git repo is the other. Turned on, the control
plane reads the four catalogues from YAML in a GitHub repo — one entry per file, a `kind` field
saying which catalogue — and reconciles itself to match whenever the repo changes. Off by default;
a deployment that does not set `GITOPS_ENABLED` and a repo is untouched. The schema, the layout and
the editor setup live in [`gitops/`](gitops/README.md).

**Git owns only what git created, and that is the whole of the model.** Each entry the reconciler
writes is recorded as git-managed. Remove its file and the next sync deletes it; but an entry made
in the console is in no such record, so a sync never touches it — not overwritten, not deleted, only
removable from the console. The two coexist because they are for different things: the console is
for trying a configuration out and quick fixes, the repo is the durable source. This is why there is
a small `gitops_managed` table beside the catalogues rather than a `source` column on each — the
provenance is the reconciler's concern, and keeping it out of the aggregates left them untouched.

**A push drives it, not a clock.** GitHub calls a webhook at `/cp-webhooks/github` on the control
host — public, because GitHub cannot carry an `ai-admin` token, and verified instead by HMAC over the
body with a shared secret, exactly like the engine's git webhooks. A reconcile also runs once at
startup, to catch a pod up on what it missed. The periodic poll is deliberately **off by default**:
its only job is to re-assert the repo on a timer, which would undo a console quick-fix before the
next push — so it exists for those who want drift corrected and stays out of the way otherwise.

**Secrets never enter the repo.** An LLM entry names an env var in `credentialEnv`; the control plane
resolves it from its own Secret at sync time and sets the credential through the same use case the
console does. The repo, public or private, holds only which env to read — never a key. The read
token for a private repo and the webhook's HMAC secret are the deployment's, in the `cp-gitops`
Secret; the fetch is all-or-nothing, so a half-failed read never reads as "the repo was emptied" and
deletes the catalogue.

**The seeder stands down when this is on.** Git provides the content, so seeding would only create
console-owned entries the repo then cannot manage — a confusing half-state. With GitOps enabled the
seeder logs that it is leaving the catalogues to git and does nothing.

### Resolving an agent degrades rather than fails

An agent composed months ago may name an MCP server since disabled, or one since deleted. Refusing
to serve the whole configuration would take a chat panel down over a missing tool, so the missing
pieces are dropped and reported:

```json
{ "llm": { "model": "claude-opus-5", "apiKey": "..." },
  "mcps": [ { "name": "Booking service", "url": "http://booking:8108" } ],
  "warnings": [ "MCP 'Forms engine' is disabled — skipped",
                "MCP 'ghost' is no longer in the catalogue — skipped" ] }
```

A missing or unusable **LLM** is the exception — there is no degraded mode without a model — and it
answers 409 saying which of the two it was, `disabled` or `missing its credential`, because the fix
differs. *Agents → Preview resolved configuration* runs exactly this and shows the warnings, which
is the only way a dropped server is visible before a user notices the agent got less capable.

### How the agent reads it

`ia-agent` has no model, no credential, no prompt and no server list of its own. It fetches an
agent's configuration and answers with what came back.

**Cached for 30 seconds, and the last good copy outlives the control plane.** Two separate
decisions. The cache keeps the control plane off the hot path — a burst of prompts is one fetch —
and 30 seconds is short enough that changing a model in the console lands within a prompt or two.
Serving the stale copy when a refresh fails is the more important half: a chat panel must not go
down because a catalogue is briefly unreachable, and the configuration from a minute ago is almost
certainly still right. Every stale answer is logged at warn and shows up in the pod's health
details, so "running on stale configuration" is visible rather than silent.

**Readiness follows the configuration.** A pod that has never reached the control plane reports
DOWN and stays out of the Service's endpoints — it has no model, so letting it answer would only
produce errors more slowly. Losing the control plane *later* does not do that: the pod stays UP
with a `degraded` detail. Liveness deliberately ignores all of this, because restarting a container
because another service is unreachable fixes nothing and throws away the cache that was keeping it
working.

**The readiness probe is also the refresh loop**, which is not obvious and is load-bearing: without
it, nothing would fetch until a prompt arrived, and no prompt can arrive while readiness is DOWN.
That deadlock was real, and it is what the probe now breaks.

**There is no fallback to local configuration.** A second source of truth that appears only when
the first is unreachable is how two configurations quietly diverge.

**One asymmetry in Spring AI shapes the code.** Model, temperature and max-tokens can be overridden
per request; an API key cannot — it lives inside `AnthropicApi`, which is constructor-injected into
the chat model. So a rotated credential is not a parameter change but a new client, and
`ChatClientRegistry` caches one per (provider, base URL, key). Changing a model builds nothing.

**A brand-new deployment seeds itself.** Moving the configuration out of a properties file would
otherwise mean `deploy.sh` producing a chat panel that does nothing until somebody typed four
things into a console. So `CatalogueSeeder` writes this deployment's own agent — an Anthropic LLM,
the three MCP servers, and `console-agent` composing them — **only when all four catalogues are
empty**. Not create-if-missing per entry: that would resurrect an MCP server someone deliberately
deleted, on every restart.

### Retrieval

A RAG source is not just catalogued: it can be written to and read from, and an agent composed with
one gets a tool for it.

**The retrieval happens in the control plane, not in the agent.** That is the one place in this
design where the control plane is on a data path, so it is worth saying why rather than leaving it
to be discovered. Searching needs two things — the store's connection and the embedding model's
credential — and both are already here. Doing it in the agent would mean sending a second
credential to a service that only needs an answer, and putting the same pgvector and embedding
plumbing in two modules, because ingestion needs exactly the same two things and is unambiguously
an admin action. What it costs: if the control plane is down, RAG tools fail. The chat panel does
not, because the agent caches its configuration and its MCP tools do not come through here. An
outage costs the agent its documents, not its voice.

**In the agent, a RAG source becomes a tool.** Not a prompt stuffed with context: classic retrieval
embeds the question before the model sees it and pastes the results into the system prompt, which
retrieves on every turn whether or not the question has anything to do with the documents. As a
tool, the model decides — and this agent is already told to answer only by calling tools and to
report a tool failure rather than answering around it, so an empty or unreachable source produces a
sentence saying so. An MCP tool and a RAG tool are both `ToolCallback`s, so the model sees one list.

The source's **description in the catalogue is the tool's description**, which is what the model
reads when deciding whether to search it. A vague one produces a tool that is never called.

**Only `PGVECTOR` is implemented.** The other two kinds can be catalogued and are refused with a
sentence when queried. The table, its index and the `vector` extension are created on first use,
which is what lets a source be declared before it holds anything — and is why the database user in
the connection URL needs rights to create an extension.

**Getting content in** is *Content → Ingest text* on the source: paste, and it is split, embedded
and stored. Deliberately the smallest thing that makes the catalogue demonstrable rather than a
document pipeline — no crawler, no upload, no incremental sync, and not idempotent. A source whose
content is loaded by something else is exactly what the catalogue is for.

The store this deployment provides is `cp-postgres`, which runs `pgvector/pgvector:pg16` — the same
PostgreSQL as before plus the extension. So configuration tables and document vectors share a
database, which is the right size for a demo; a source pointed somewhere else is a field in the
console and no code at all.

**One dependency is conspicuously absent**: `spring-ai-openai`. It is built against Spring Framework
6 and calls `HttpHeaders.addAll(MultiValueMap)`, gone in Framework 7, so on this module's Boot 4 it
compiles and then dies at the first request with a `NoSuchMethodError`. The choice was to move the
whole module back to Boot 3.4 or to write the one POST it was being used for; `/v1/embeddings` takes
a model and a list of strings and returns vectors, so it is the second. `PgVectorStore` itself is
fine on Boot 4 — it speaks JDBC, not HTTP.

### What it does not do yet

**Embeddings need their own credential.** Anthropic has no embeddings API, so a catalogued
embedding model is an OpenAI-shaped one and this deployment's Anthropic key cannot pay for it. A
fresh deploy seeds the source and the model with no credential; filling it in is one field in the
console.

**The seeded RAG source is not on the seeded agent.** A tool that always answers "nothing found" is
worse than no tool — it teaches the model the source is useless and costs a round trip per prompt to
prove it. Ingest something first, then add it.

**The id fields are text, not pickers.** An agent's MCP and RAG lists are comma-separated ids, so a
typo is not refused on save — it becomes a reference the resolver drops with a warning. The preview
button is what surfaces that.

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
| platform | cx43 | redpanda, forms, rules, keycloak, shell, gateway, kafka console, booking, content, users, ia-agent, ia-control-plane, control-shell — none of them blocks on fsync |
| `cp-postgres` | cx43, with the platform group | the control plane's own database. The one thing here on a PersistentVolume rather than an `emptyDir`, because it holds configuration rather than measurements |
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

**The four demo services and the three control-plane pods land in the platform group, and that
budget has not been re-measured since.** They carry the same anti-affinity as the shell and the gateway — off the postgres,
orchestrator and worker nodes — so they compete for the platform node's room with everything
already there, and between them they request about 1.6 CPU and 4.6 GiB. If that does not fit,
Karpenter provisions a second `cx43` and the fleet is asked for 8 more vCPU than it allows: the
node is never created and the pods sit `Pending`, which reads like a scheduling bug and is a quota.
Check `kubectl get pods -n ec-demo1` and `kubectl describe node` after the first deploy, and shrink
their requests or raise the fleet limit through the Fleet API — not `kubectl` — if they do not fit.

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
  processes — would otherwise be open to anyone who typed the path. The three demo services are
  worse: they authenticate nothing at all, and one of them arrived with a `permitAll()` chain and a
  `JwtDecoder` built from a hardcoded secret, which was removed rather than deployed. The chat
  agent is worse again, because an open prompt endpoint is a bill. The gateway validates the
  realm's access token on all seven prefixes before any of them sees a request; see
  `SecurityConfig.java` for the paths that stay public, and why they have to.
- **`ddl-auto: update` never drops a column.** Renaming a JPA field adds the new column and leaves
  the old one, `NOT NULL`, and every insert then fails on a column no code mentions any more. It
  happened here while building the control plane — `topK` maps to `topk`, not `top_k`, so naming it
  explicitly meant both existed. A fresh deployment never sees it; an upgraded one needs the old
  column dropped by hand. Four services here run `ddl-auto: update` and none of them ships
  migrations, which is the trade: no migration history to maintain, and renames are manual.
- **`users`' gRPC port authenticates nothing.** 9191 is on its Service and on no ingress, so it is
  reachable from inside the namespace and nowhere else — and from there anything can ask it for any
  user's roles and scopes. Nothing in this deployment calls it; the gateway validates Keycloak's
  token directly rather than enriching it from here, which is what the demo's own api-gw used it
  for. It is carried because it is half of what that service is, and it must not be exposed as it
  stands.
- **The control console's Keycloak client is compiled into its shell**, like the demo shell's:
  Mateu bakes `@KeycloakSecured` into the generated bootstrap page, so the hostname, realm and
  client id cannot be environment variables yet. Changing any of them means rebuilding
  `control-shell`.
- **One replica of everything.** The Karpenter pool is capped at 8 CPU. The engine scales
  horizontally by design — raising `replicas` in `deploy/values/eventconductor.yaml` is the only
  change needed, since orchestrator instances coordinate through PostgreSQL advisory locks and
  the outbox rather than through a leader.
- **The rule engine is deployed at zero replicas.** None of these three workflows has a `RULE`
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

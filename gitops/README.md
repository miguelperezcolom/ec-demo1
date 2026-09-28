# IA control plane — GitOps

The IA catalogues — agents, models, MCP servers, APIs offered as MCP servers, RAG sources, budgets
and routes — as YAML in a git repo, with the control plane reconciling itself to match on every
push. This directory is both the **public schema** those files validate against and a **worked
example** of the layout a config repo uses.

## How it fits together

```
config repo (private)                 this deployment
  ia/                                    ia-control-plane
    llms/anthropic.yaml    ── push ──▶     GitHubCatalogueSource  (reads ia/ over the API, with a token)
    mcp/orchestrator.yaml                  ReconcileCatalogueUseCase
    apimcp/booking-api.yaml ◀─ webhook ──  /cp-webhooks/github    (HMAC-verified, public on the control host)
    rag/handbook.yaml
    agents/console-agent.yaml
    budgets/daily-per-user.yaml
    routes/support-to-console-agent.yaml
```

One entry per file. The `kind` field (`llm` | `mcp` | `apimcp` | `rag` | `agent` | `budget` |
`route`) says which catalogue it is; the schema keys everything else off that. Budgets cap token
spend on a subject per window; routes pick which agent answers by the caller's context. Both
reconcile under the same provenance rule as the rest.

**`mcp` and `apimcp` are not two spellings of one thing.** An `mcp` entry is a server somebody else
runs, and it lists no tools on purpose — the server declares its own, they change without this
catalogue being told, and a copy here would go stale in silence. An `apimcp` entry is the mirror
image: nothing else knows what an API offers as tools, because the offer is composed — which
operations, under which names, described how. There the tool list *is* the entry, which is why it
is the one kind whose file carries a nested array, and why its own REST/SOAP flavour is spelled
`apiKind`: `kind` is already taken by the discriminator above.

Two rules keep that array safe to declare. **Omitting `tools` leaves the stored offer alone** — the
same convention `credentialEnv` follows, so editing a base url in a hurry cannot wipe descriptions
somebody wrote. **An empty list empties it**, which is the repo saying so on purpose, and leaves the
entry catalogued and visibly unusable.

## Agents that call agents (A2A)

An `agent` entry may list, under `peers`, other agents it is allowed to call:

```yaml
kind: agent
id: console-agent
llm: anthropic
peers:
  - mapping-agent     # becomes the tool ask_mapping_agent, described by mapping-agent's description
```

Each peer is offered to the agent's model as one tool that sends it a message over the
[A2A protocol](https://a2a-protocol.org) (`message/send`, JSON-RPC 2.0) and returns its answer. The
peer's `description` is the tool description the calling model reads, so write it for that reader.
The resolved configuration carries each peer's A2A address, built from the control plane's
`cp.a2a-base-url` (`A2A_BASE_URL`, `http://ia-agent:8095` by default) plus `/a2a/<id>`: every
ia-agent pod serves every agent there, with its Agent Card at `/a2a/<id>/.well-known/agent-card.json`.

The rules: an agent cannot list itself (the sync reports it as an error); peers are references like
`mcp` and `rag`, so one that is missing or disabled is dropped at read time with a warning; the
caller's token is forwarded, so the peer acts for the same person and spends against the same
budgets; a chain of calls stops at `IA_A2A_MAX_DEPTH` hops (2) and a call back to an agent already
on the chain is refused. Supported subset: text in, text out, blocking. No streaming, no push
notifications, no tasks, no agents outside this deployment.

## The rules that matter

- **Git owns only what git created.** An entry the reconciler writes is remembered as git-managed;
  remove its file and the next sync deletes it. An entry made in the **console** is never touched by
  a sync — not overwritten, not deleted — and can only be removed from the console. The two coexist:
  the console is for trying a configuration out or a quick fix, git is the durable source.
- **Sync is driven by the push, not a clock.** The webhook reconciles on a real change; the periodic
  poll is off by default so a console edit is not reverted between pushes.
- **Secrets never live in the repo.** An `llm` entry names an env var in `credentialEnv`; the control
  plane resolves it from its own Secret at sync time. The repo says *which* secret, never its value.

## Editor autocompletion

The schema is published at:

```
https://raw.githubusercontent.com/miguelperezcolom/ec-demo1/master/gitops/ia-catalogue.schema.json
```

Every example file starts with a modeline that binds it, which is the most portable way — it travels
with the file, no per-machine setup:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/miguelperezcolom/ec-demo1/master/gitops/ia-catalogue.schema.json
```

### VS Code

1. Install the **YAML** extension by Red Hat (`redhat.vscode-yaml`).
2. Either rely on the modeline above (nothing else to do), or map by path in `.vscode/settings.json`
   — see the one in this directory:

   ```json
   {
     "yaml.schemas": {
       "https://raw.githubusercontent.com/miguelperezcolom/ec-demo1/master/gitops/ia-catalogue.schema.json": ["ia/**/*.yaml"]
     }
   }
   ```

You get completion of field names and enum values (providers, transports, stores), hover docs, and
red squiggles on anything the schema rejects.

### IntelliJ IDEA / JetBrains

The modeline is honored out of the box in recent versions — open a file and it just works. To map by
path instead (or on an older IDE):

1. **Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings**.
2. Add a mapping: **Schema file or URL** = the raw URL above; **Schema version** = *JSON Schema 2020-12*.
3. Add a **file path pattern**: `ia/**/*.yaml` (or point it at a directory).

Completion and validation then work in the YAML editor the same way.

## Trying it against a repo

Point the control plane at a repo by setting, in `deploy/manifests/71-ia-control-plane.yaml`:

```yaml
- { name: GITOPS_ENABLED, value: "true" }
- { name: GITOPS_REPO,    value: "your-org/your-config-repo" }
- { name: GITOPS_PATH,    value: "ia" }
```

Put the GitHub read token in `deploy/.secrets/credentials.env` as `GITOPS_GITHUB_TOKEN=…` and re-run
`deploy.sh` (it lands in the `cp-gitops` Secret). Then add a webhook on the repo:

- **Payload URL**: `https://console.ec1.mateu.io/cp-webhooks/github`
- **Content type**: `application/json`
- **Secret**: the value of `GITOPS_WEBHOOK_SECRET` from `credentials.env`
- **Events**: just the push event

The `example/` tree here is a ready-made `ia/` directory — copy it into your config repo to start.

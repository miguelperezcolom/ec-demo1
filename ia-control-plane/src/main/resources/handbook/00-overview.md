# Deployment overview

This is EventConductor on Kubernetes: a workflow engine (orchestrator + forms + worker) with a set
of services around it, a chat agent, and a control plane that configures the agent.

## The services

- **orchestrator / forms / worker** — the engine. Processes, form definitions, human tasks.
- **booking** — a booking CRUD, an MCP server and a saga worker in one pod.
- **content** — content, labels and content types, a CRUD over its own database.
- **users** — users, groups, roles and permissions, plus a gRPC endpoint that serves a user's roles
  and scopes.
- **ia-agent** — the chat panel's agent. It answers only by calling MCP tools or searching RAG
  sources; it implements nothing itself.
- **ia-control-plane** — the four catalogues the agent is configured from: LLMs, MCP servers, RAG
  sources, and the agents that compose them.

## The two consoles

- The **demo console** at `ec1.mateu.io` — the shell, the CRUDs, the chat panel. Needs a Keycloak
  login (realm role `user`).
- The **control console** at `console.ec1.mateu.io` — the control plane's catalogues. Needs the
  `ai-admin` realm role, which is separate from the demo's `admin` because this host reaches the LLM
  credentials and the other does not.

## Everything enters through the gateway

One gateway validates a Keycloak token before any backend sees a request, and routes by host and
path. The backends authenticate nothing of their own; the gateway is the boundary.

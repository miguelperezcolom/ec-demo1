# The IA control plane

The control plane holds four catalogues and hands a resolved configuration to the agent.

- **LLMs** — a model this deployment may call, and the API key that pays for it (encrypted at rest).
- **MCP servers** — a server whose tools an agent may be given.
- **RAG sources** — a vector store, a collection, and the model that embedded it.
- **Agents** — a prompt, one LLM, and the servers and sources it may reach. The only thing a running
  service is ever handed.

An agent refers to the other three by id and holds nothing of them, so a model's key is rotated in
one place and every agent composed from it follows. Resolution happens at read time and drops what
is no longer usable, reporting what it dropped, rather than failing the whole configuration.

## Configuring it from git (GitOps)

The catalogues can be read from YAML in a GitHub repo instead of edited only in the console. When
enabled, git owns what git created: an entry made in the console is never overwritten or deleted by
a sync, and a git entry removed from the repo is cleaned up. A push drives a reconcile through a
webhook verified by HMAC; the periodic poll is off by default so a console edit is not reverted
between pushes. Secrets never enter the repo — an LLM names an environment variable and the control
plane resolves it at sync time.

## Retrieval (RAG)

A RAG source is a pgvector table plus an embedding model. This deployment embeds with a local
Text Embeddings Inference pod running a multilingual model, so no external embeddings vendor is
needed and questions in Spanish or English both work. The agent turns each RAG source on it into a
tool that searches the store; if a source has nothing ingested, the tool returns nothing, which is
why a source is only worth attaching to an agent once it holds content.

To check retrieval, ask the chat panel an operational question — for example how the identity sync
is verified, or what the `ai-admin` role is for — and the agent should answer from this handbook.

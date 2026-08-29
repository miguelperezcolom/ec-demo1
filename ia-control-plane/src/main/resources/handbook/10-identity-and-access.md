# Identity and access

## Roles

The Keycloak realm defines three realm roles:

- `user` — can use the demo shell and complete human tasks.
- `admin` — can drive workflow definitions and processes on the demo console.
- `ai-admin` — can configure the AI on the control console: agents, models, MCP and RAG sources, and
  the credentials they hold. It is separate from `admin` on purpose, because only the control host
  reaches the LLM API keys, so reaching them and driving workflows are separate grants.

The demo user carries all three; a real deployment would split them.

## Users propagate to Keycloak

The users service is the source of truth for who a person is. Creating, editing or deleting a user
there propagates to Keycloak through a transactional outbox: the intent is written in the same
database transaction as the user, and a relay delivers it at-least-once with an idempotent upsert
(or delete) keyed on username. Only identity is sent — roles and scopes stay in the users service.
A new user is created "must set password" and emailed a link to set one.

To verify propagation, watch the `identity_outbox` table in the users database and the Keycloak
admin API; a created row should flip to delivered within a few seconds. See the identity-sync
runbook for the exact commands.

## Email

Keycloak sends mail (the set-password link, password resets) through a small send-only postfix
relay in the cluster. Keycloak talks plain SMTP to it; postfix forwards to Gmail over authenticated
TLS. The Gmail App Password lives only in the postfix Secret, never in the realm import. If mail
queues instead of sending, check that the `postfix-relay` Secret holds a password and that the
domain's SPF and DKIM authorize Google.

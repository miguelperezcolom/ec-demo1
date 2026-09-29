# Contracts

What the services of ec-demo1 agree on, and how the build keeps them to it: the published language
(small libraries, one per context), the schema of every Kafka topic, and the tasks each worker serves
for the engine's definitions.

## The libraries

`integration-model` used to be one shared kernel every service took whole. It is split by context;
each service depends on the contexts it speaks and nothing else. The packages
(`io.mateu.ecdemo1.integration.model.*`) and the JSON are unchanged.

| Module | What | Topics | Used by |
|---|---|---|---|
| `contracts-reservation` | The canonical reservation (`Reservation`, `Room`, `Person`…), a projection asked for, a no-show reported, a reservation changed in the PMS | projection-requests, no-show-reports, pms-reservations | crs-integration, pms-integration, mapping, customer-mdm, integrations, front-office |
| `contracts-customer` | `CustomerEvent`, `GoldenRecord`, identity resolution, `CustomerCommand`, `CustomerNoticeChanged` (needs `contracts-reservation`: a passenger is a `Person`) | customers, customer-notices, customer-commands | crs-integration, pms-integration, customer-mdm, front-office |
| `contracts-partner` | The partner and its PMS profile | — (HTTP) | crs-integration, pms-integration, mapping, integrations |
| `contracts-mapping` | Code types, translations, causes, `MappingCommand` | mapping-commands | crs-integration, pms-integration, mapping, integrations |
| `contracts-integration` | An integration's lifecycle: status, connection, connectivity, future reservations and their codes (needs `contracts-mapping`); `ApiUsage`, what an external API (Salesforce, Opera) is spent | — (HTTP) | crs-integration, pms-integration, mapping, integrations, customer-mdm |
| `contracts-frontoffice` | `FrontOfficeCommand` (stays, catalogues, how the PMS took the reception: `RecordReception`), `FrontOfficeEvent` (check-in, check-out, no-show at the desk), the catalogue summary | front-office-commands, front-office-events | pms-integration, integrations, front-office |
| `contracts-communication` | Notifications asked for and resolved | notifications, notification-resolutions | pms-integration, customer-mdm, mapping, integrations, communication |
| `contracts-audit` | `AuditedAction` | audit | mapping, integrations, front-office, audit |
| `contracts-process` | The processes' vocabulary shared with ec-definitions: definition ids, gate messages, variable names, outcomes | — | crs-integration, pms-integration, mapping, integrations |

`IntegrationEvent` (the `integration-events` topic) is crs-integration-service's own: it is the only
producer and the only consumer, so it lives there.

Commands sit with the context that takes them, not in a `commands` module: a service that sends
mapping commands already speaks the mapping's language.

Build: `mvn install` in `contracts/` before any service (deploy/build-images.sh does).

## The schemas

`schemas/<topic>/v<N>.schema.json` — JSON Schema 2020-12, one file per topic and version, generated
from the records that are the topic's messages. Each carries who owns the topic (`x-owner`), what the
record key is (`x-key`), its producers and consumers, the discriminator of a polymorphic topic
(`x-discriminator`, a `oneOf` of the variants), and `examples`: one message per variant, as a real
producer writes it.

| Topic | Owner (generates it) | Producers | Consumers |
|---|---|---|---|
| crs-bookings | booking | booking | crs-integration |
| booking-commands | booking | crs-integration | booking |
| partners | erp | erp | crs-integration |
| partner-commands | erp | integrations, crs-integration | erp |
| integration-events | crs-integration | crs-integration | crs-integration |
| customers | contracts-schemas (customer-mdm's language) | customer-mdm | crs-integration, front-office |
| customer-notices | contracts-schemas | customer-mdm | front-office |
| customer-commands | contracts-schemas | front-office | customer-mdm |
| projection-requests | contracts-schemas | integrations | crs-integration |
| no-show-reports | contracts-schemas | — (the front office's no-show goes to the PMS: front-office-events) | crs-integration |
| mapping-commands | contracts-schemas | integrations | mapping |
| front-office-commands | contracts-schemas | pms-integration, integrations | front-office |
| front-office-events | contracts-schemas (front-office's language) | front-office | integrations |
| pms-reservations | contracts-schemas | pms-integration | integrations |
| notifications | contracts-schemas | integrations, mapping, customer-mdm, pms-integration | communication |
| notification-resolutions | contracts-schemas | integrations, mapping, customer-mdm, pms-integration | communication |
| audit | contracts-schemas | integrations, mapping, front-office | audit |
| human-tasks | communication (from EventConductor's `HumanTaskChanged`) | eventconductor-forms | communication |

The engine's own topics (`upstream`, and the task topics `booking`, `crs-integration`,
`pms-integration`, `mapping`, `integrations`) carry EventConductor's protocol; their payloads are the
task contracts in ec-definitions (`definitions/tasks/*.ectask`), checked below.

### How the build keeps them

- **Generated, never hand-edited.** The owner's build generates the schema (`contracts-testing`:
  `TopicSpec` → `SchemaFiles.publish`) and fails when the committed file differs. `mvn test
  -Dcontracts.write=true` (or `CONTRACTS_WRITE=true`) writes it; commit the file with the change.
  Every record component is required and, unless primitive, nullable (the services write nulls);
  a closed schema (the default) rejects a property it does not name.
- **Producers** validate what their real serialization path writes — the outbox writer or the
  StreamBridge send, with the application's own mapper — against the topic's schema
  (`Contracts.topic("…").assertValid(json)`).
- **Consumers** feed every example of the schema through their real consumer bean and check it reaches
  the handler parsed, not dropped as unreadable.

### Versions

A change a consumer of the old schema might not read — a property, variant or enum value removed, a
type changed, a property newly required — is **breaking**: the build says so, and it is published as a
new version (`TopicSpec.version(2)` → `v2.schema.json`) while `v1` stays for as long as anything reads
it. A new optional property, variant or definition is additive and stays in the same version (every
consumer here reads tolerantly: unknown properties ignored).

## Workers and ec-definitions

`workers/<service>.tasks` lists what each worker service serves — `<id>@<version> <topic>`, generated
from its `TaskRegistration`s by its `ServedTasksTest` (same `-Dcontracts.write=true` rule).
`deploy/demo/check-contracts.sh` reads ec-definitions (cloned at `--ref`, master by default, or
`--cluster`: what the engine in ec1 imported, plus a live consumer group on every task topic) and
fails on any ACTION step whose task has no contract, no topic, or no worker serving it on that topic.
`deploy/demo/demo-prep.sh health` runs the `--cluster` form ("Task contracts"); `demo-prep.sh
contracts` runs only that.

### The worker runtime (EventConductor 2.23.1)

The services use the SDK as it comes — no dispatcher, mapper or lookup of their own:

- variables are bound by the handler input's declared types, so a `String` locator such as `12E45`
  arrives as written; the SDK's mapper is private and the application's `ObjectMapper` is Boot's;
- a task with no `taskId` (an ACTION with no `task:`) is served by its step id — every definition in
  ec-definitions names its tasks, so this only covers a process started before they did;
- worker-kafka runs a task and publishes its reply on the listener thread before the offset is
  committed (at least once; handlers are idempotent).

Concurrency (`spring.cloud.stream.bindings.consumeWorkerEvent-in-0.consumer.concurrency`), at most one
thread per partition:

| Service | Topic | Partitions | Concurrency | Why |
|---|---|---|---|---|
| pms-integration | pms-integration | 3 (grown by `auto-add-partitions`) | 3 | every reservation's projection waits on Opera here; one thread would queue a backfill behind each call. What must not run at once is kept apart by the engine's LOCK, not the partition |
| mapping | mapping | 3 (same) | 3 | the preparations of every projection; they wait on the CRS and the integrations service |
| crs-integration | crs-integration | 1 | 1 | two steps that only write a command to the outbox |
| integrations | integrations | 1 | 1 | an onboarding's steps come one after another |
| booking | booking | 1 | 1 | a no-show now and then |

No DLQ (`enable-dlq` stays off): a task whose reply the broker keeps refusing is dropped after the
binder's attempts, and the engine runs it again when its step times out — every step in
ec-definitions has a timeout (PT2M) and retries. A dead-letter topic nobody replays would only
duplicate that.

---
title: Topics de Kafka
description: Cada topic de ec1 — su dueño, quién produce, quién consume y para qué.
---

El broker es **Redpanda** (`redpanda:19092`), y la consola web está en `kafka.ec1.mateu.io`. Los topics se
crean solos (`auto-create-topics: true` en el binder).

## Los del negocio

Cada uno tiene su esquema versionado en `contracts/schemas/<topic>/v<N>.schema.json` (ver
[Librerías y esquemas](/contratos/librerias-y-esquemas/)).

| Topic | Dueño del esquema | Productores | Consumidores | Qué lleva |
| :---- | :---------------- | :---------- | :----------- | :-------- |
| `crs-bookings` | booking | booking | crs-integration | `booking-created` / `-modified` / `-cancelled`: id, hotel, versión |
| `booking-commands` | booking | crs-integration | booking | `annotate-pms-reference` |
| `partners` | erp | erp | crs-integration | Un interlocutor cambió |
| `partner-commands` | erp | integrations, crs-integration | erp | `resync-partner`, `import-partner`, `record-pms-profile` |
| `integration-events` | crs-integration | crs-integration | crs-integration | Los eventos de negocio que enruta a procesos |
| `projection-requests` | contracts-schemas | integrations | crs-integration | Proyectar una reserva (backfill) |
| `mapping-commands` | contracts-schemas | integrations | mapping | `define-equivalence`, `request-agent-proposal`, `resolve-cause-if-open`, `record-partner-profile` |
| `pms-reservations` | contracts-schemas | pms-integration | integrations | `PmsReservationChanged`: una reserva se escribió en Opera |
| `front-office-commands` | contracts-schemas | pms-integration, integrations | front-office | `write-stay` (estado de Opera: también `IN_HOUSE`, `CHECKED_OUT`), `replace-catalogue`, `record-reception` (cómo tomó el PMS lo que hizo recepción: rechazo y motivo, la habitación, la factura del check-out) |
| `front-office-events` | contracts-schemas | front-office | integrations | `guest-checked-in`, `guest-checked-out`, `no-show-reported`: lo que hizo recepción, para que el PMS lo registre. Clave `hotel/estancia` |
| `customers` | contracts-schemas | customer-mdm | crs-integration, front-office | `CustomerChanged`, `CustomersMerged` |
| `customer-notices` | contracts-schemas | customer-mdm | front-office | `CustomerNoticeChanged`: un aviso de recepción de un cliente, entero, como lo confirmó Salesforce; gana la versión mayor |
| `customer-commands` | contracts-schemas | front-office | customer-mdm | `propose-change`, `record-scanned-identity` |
| `no-show-reports` | contracts-schemas | — | crs-integration | `ReportNoShow`. El front office ya no lo manda: su no-show sube al PMS (`front-office-events`) y de él al CRS por el motor |
| `notifications` | contracts-schemas | integrations, mapping, customer-mdm, pms-integration | communication | Un aviso |
| `notification-resolutions` | contracts-schemas | integrations, mapping, customer-mdm, pms-integration | communication | Lo que causó un aviso se resolvió |
| `audit` | contracts-schemas | integrations, mapping, front-office | audit | `AuditedAction` |
| `human-tasks` | communication (de `HumanTaskChanged` del motor) | forms | communication | Las tareas del motor de formularios, para la bandeja |

## Los del motor

Llevan el protocolo de EventConductor; sus cargas son los contratos de tarea de ec-definitions.

| Topic | Qué lleva |
| :---- | :-------- |
| `upstream` | Lo que se le pide al motor: arrancar procesos, las respuestas de los workers, los mensajes que reanudan |
| `outbox` | Cada cambio de estado que registra el motor |
| `downstream` | El destino por defecto de un paso sin topic. **Nadie lo escucha**: todos los pasos nombran el suyo |
| `booking`, `crs-integration`, `mapping`, `pms-integration`, `integrations` | Las tareas de cada worker (ver [Tareas](/referencia/tareas/)) |
| `forms` | Las tareas de usuario (`USER_TASK` con `topic: forms`) |

## Grupos de consumo

Cada consumidor tiene su grupo, `ec-demo1-<servicio>-<qué>` (p. ej. `ec-demo1-mapping-commands`,
`ec-demo1-front-office-commands`, `ec-demo1-pms-integration-worker`). Para desbloquear una partición
detenida por un registro que no se puede leer: ver [Problemas conocidos](/operacion/problemas-conocidos/#kafka).

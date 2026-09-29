---
title: Tareas
description: Cada tarea que el motor despacha en ec1 — su contrato, el topic del worker que la atiende y los procesos que la usan.
---

Las tareas son los contratos `.ectask` de
[ec-definitions](https://github.com/miguelperezcolom/ec-definitions/tree/master/definitions/tasks), todos
en su versión 1. Cada worker declara las que sirve en `contracts/workers/<servicio>.tasks`, y
`deploy/demo/check-contracts.sh` comprueba que coinciden (ver [Workers y tareas](/contratos/workers-y-tareas/)).

| Topic | Worker |
| :---- | :----- |
| `booking` | `booking` (el CRS) |
| `crs-integration` | `crs-integration-service` |
| `mapping` | `mapping-service` |
| `pms-integration` | `pms-integration-service` |
| `integrations` | `integrations-service` |

La descripción es la del contrato, recortada.

| Tarea | Topic | Procesos | Qué hace |
| :---- | :---- | :------- | :------- |
| `register-no-show@1` | `booking` | `registrar-no-show` | El CRS registra el no-show de una reserva: la cancela con motivo NOS y la deja costando su cargo. Una vez: el mismo aviso dos veces es un solo no-show. |
| `annotate-partner-profile@1` | `crs-integration` | `proyectar-interlocutor` | Anota en el maestro de interlocutores (ERP) qué perfil es el interlocutor en el PMS. Un comando por el outbox de crs-integration. |
| `annotate-pms-reference@1` | `crs-integration` | `proyectar-reserva` | Anota en el CRS el id que la reserva tiene en el PMS. Un comando a booking por el outbox de crs-integration, con el id de la ejecución: dos veces es una. |
| `activate@1` | `integrations` | `alta-integracion` | Activa la integración; el tráfico en tiempo real fluye. |
| `await-activation@1` | `integrations` | `alta-integracion` | La integración queda lista para activar; espera a que una persona la active. |
| `backfill-prepass@1` | `integrations` | `alta-integracion` | Pasada previa del backfill. |
| `contrast-catalogues@1` | `integrations` | `alta-integracion` | Contrasta los catálogos de la propiedad (Opera) y del CRS. |
| `request-mapping@1` | `integrations` | `alta-integracion` | Pide al mapping el mapeado de los códigos del hotel. |
| `start-backfill@1` | `integrations` | `alta-integracion` | Inicia el backfill, la llegada más próxima primero. |
| `sync-partners@1` | `integrations` | `alta-integracion` | Sincroniza con el PMS los interlocutores de las reservas del hotel. |
| `verify-connectivity@1` | `integrations` | `alta-integracion` | Verifica la conectividad con Opera; la puerta se abre cuando responde. |
| `fo-activate@1` | `integrations` | `alta-integracion-fo` | Activa la integración pms-fo; los cambios de Opera fluyen al front office. |
| `fo-await-activation@1` | `integrations` | `alta-integracion-fo` | La integración queda lista para activar; espera a que una persona la active. |
| `fo-start-backfill@1` | `integrations` | `alta-integracion-fo` | Inicia el backfill, las reservas de Opera de la ventana. |
| `fo-sync-catalogue@1` | `integrations` | `alta-integracion-fo` | Lleva el catálogo del PMS al front office. |
| `fo-verify-connectivity@1` | `integrations` | `alta-integracion-fo` | Verifica la conexión con Opera y con el front office. |
| `prepare-cancellation@1` | `mapping` | `proyectar-cancelacion` | Lee la reserva del CRS y comprueba que todos sus códigos tienen equivalencia en el PMS. Si falta alguna, abre sus causas y el proceso espera. |
| `prepare-partner@1` | `mapping` | `proyectar-interlocutor` | Lee el interlocutor del ERP y comprueba sus equivalencias. Si falta alguna, abre sus causas y el proceso espera. |
| `prepare-reservation@1` | `mapping` | `proyectar-reserva` | Lee la reserva del CRS y comprueba que todos sus códigos tienen equivalencia en el PMS. Si falta alguna, abre sus causas y el proceso espera. |
| `record-partner-profile@1` | `mapping` | `proyectar-interlocutor` | Registra qué perfil del PMS es el interlocutor, y en qué versión, y reanuda las reservas que esperaban a que existiera en el PMS. |
| `relaunch-process@1` | `mapping` | `proyectar-reserva`, `proyectar-cancelacion`, `proyectar-interlocutor` | Un proceso que esperaba sus causas y ya no tiene ninguna abierta pide su sucesor: una instancia nueva de sí mismo, que lee otra vez lo que proyecta. Dos veces, un sucesor. |
| `resolve-projection@1` | `mapping` | `proyectar-reserva` | La reserva ya está en el PMS: resuelve la causa de la cancelación que esperaba a que se proyectara. |
| `cancel-reservation@1` | `pms-integration` | `proyectar-cancelacion` | Cancela en Opera una reserva cancelada en el CRS, con su motivo; un no-show deja antes su cargo en la reserva. Si Opera aún no la tiene, espera a que se proyecte (R37). |
| `ensure-guest-profile@1` | `pms-integration` | `proyectar-reserva` | Asegura en Opera el perfil del huésped titular: quién es lo dice el MDM de clientes, y lo que el MDM no sabe, la reserva. Si Opera ya tiene esta versión de la reserva, no se escribe (salvo que el MDM la proyecte para reescribir al huésped). |
| `ensure-partner-profile@1` | `pms-integration` | `proyectar-interlocutor` | El interlocutor del ERP como perfil de Opera: el que el ERP ya conoce, el que Opera tenga con su CorporateId o uno nuevo. Si falta la equivalencia de su tipo o Opera lo rechaza, abre la causa y el proceso espera. |
| `project-stay@1` | `pms-integration` | `proyectar-estancia` | Relee de Opera una reserva —del CRS o nacida en Opera— y la manda al front office como estancia (`front-office-commands`), con el titular que diga el MDM. |
| `upsert-reservation@1` | `pms-integration` | `proyectar-reserva` | Graba la reserva en Opera: la busca por el localizador del CRS, compara la versión que Opera tiene (su UDF) con la que se graba, y crea o modifica —o no escribe nada si Opera ya tiene esta o una más nueva (`STALE`)—. Publica `pms-reservations`. |

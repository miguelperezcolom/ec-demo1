# Llamadas entre servicios: órdenes por Kafka, consultas por HTTP

**La regla.** Entre servicios van **mensajes**: el emisor escribe la orden en **su outbox**, en la
misma transacción que la decisión que la pide, y el relay la publica en Kafka. El receptor la
consume con **consumidor idempotente**: guarda el id del mensaje en su **inbox** (`inbox_entry`)
en la misma transacción que lo que hace, así que un mensaje repetido no hace nada. HTTP síncrono
solo cuando hace falta la respuesta **ahora**: una pantalla que espera, o leer datos (releer una
reserva, la ficha de un cliente). Es decir, **consultas e idas y vueltas de UI, no órdenes entre
servicios**.

Un paso del motor que escribe en otro sistema también es una orden. Deja la orden en el outbox y
contesta al motor cuando ya está escrita: el paso termina cuando la orden va de camino, no cuando
el otro sistema la ha aplicado.

## Órdenes que van por Kafka

| Topic | Emisor (outbox) | Consumidor (inbox) | Órdenes |
|---|---|---|---|
| `mapping-commands` | integrations-service | mapping-service (`consumeMappingCommands`, grupo `ec-demo1-mapping-commands`) | `define-equivalence` (la del hotel y los tipos de interlocutor), `request-agent-proposal`, `resolve-cause-if-open` (`INTEGRATION_INACTIVE:<hotel>` al activar o reanudar), `record-partner-profile` (importación de interlocutores) |
| `partner-commands` | integrations-service, crs-integration-service | erp (`consumePartnerCommands`, grupo `ec-demo1-erp-partner-commands`) | `resync-partner` (sincronizar interlocutores del alta), `import-partner` (importación desde Opera: crea o actualiza conservando lo que solo sabe el ERP, y registra el perfil de Opera), `record-pms-profile` (paso `annotate-partner-profile`) |
| `booking-commands` | crs-integration-service | booking (`consumeBookingCommands`, grupo `ec-demo1-booking-commands`) | `annotate-pms-reference` (paso `annotate-pms-reference`) |
| `projection-requests` | integrations-service (backfill) | crs-integration-service (`consumeProjectionRequests`, grupo `ec-demo1-crs-integration-projections`) | proyectar una reserva por «Proyectar Reserva». El backfill escribe las órdenes de una página y mueve su cursor en la misma transacción. |

- **El contrato es del receptor.** El formato de `mapping-commands` y `projection-requests` está en
  `integration-model` (`command/MappingCommand`, `command/ProjectReservation`). El ERP y el CRS son
  sistemas, no conocen el modelo de la integración: sus órdenes están en sus propios términos
  (`PartnerCommands`, `BookingCommands`) y el emisor las escribe así.
- **Cada orden lleva su id** (`commandId`). La deduplicación va por ese id. En los pasos del motor
  el id es el `taskExecutionId`, así que un paso repetido pide una sola vez. Además, cada orden es
  idempotente en sí misma.
- **La clave de Kafka** es el hotel, la causa, el interlocutor o la reserva. Así las órdenes sobre lo
  mismo llegan en orden: la importación de un interlocutor va antes que un `resync` del mismo.
- **Lo que el receptor rechaza** (un interlocutor que no tiene, datos que no acepta, un JSON
  ilegible) se registra en el log y se descarta, porque repetirlo daría el mismo rechazo. Lo demás
  (la base de datos caída) se reintenta: `max-attempts: 1000` con *backoff* de hasta 60 s.
- **Topics:** por auto-creación del binder (`auto-create-topics: true`), como los demás.
- **Sustituye al outbox HTTP** de integrations-service (tabla `remote_call` y su relay, PR #55). Al
  arrancar, lo que esa tabla aún tuviera sin enviar pasa al outbox de Kafka y la tabla se borra, en
  una sola transacción (`RemoteCallTableRetired`).
- **Consecuencia en el alta.** Tras «Importar interlocutores», los perfiles llegan al mapping de
  forma asíncrona. La puerta de interlocutores los ve en su siguiente relectura (`GATE_RECHECK`,
  30 s), no en la misma acción.

## Llamadas síncronas que quedan y por qué

### Lo que el alta de una integración necesita saber ya (integrations-service → otros)

| Llamada | Destino | Por qué síncrona |
|---|---|---|
| `verify` (`POST /connections/verify`) | pms-integration → Opera | El resultado *es* la decisión: la puerta de conectividad y el mensaje que ve la persona. |
| `operaProperties` (`POST /connections/properties`) | pms-integration → Opera | Opciones del formulario de alta: una pantalla que espera. |
| `pmsCatalog` (`GET /catalog?hotelId`) | pms-integration → Opera | «Verificar el estado inicial»: contrasta catálogos y decide si la propiedad está configurada. |
| `pendingMappings` (`GET /pending`) | mapping | Cuántos códigos faltan: abre o no la puerta de mapeado. |
| `gaps` (`POST /gaps`) y `futureUsage` (`GET /reservations/{h}/future/usage`) | mapping, crs-integration | Prepaso del backfill: qué huecos lo bloquean. |
| `hasPartnerProfile` (`GET /partner-profiles/{code}`) | mapping | Qué interlocutores del hotel faltan en el PMS: puerta de interlocutores. |
| `pmsPartners` (`GET /pms-partners`) y `erpPartner` (`GET /partners/{code}`) | pms-integration, erp | Importación: qué hay en Opera y qué es nuevo o cambia, que es el resumen que ve la persona. Las escrituras van por `partner-commands` y `mapping-commands`. |
| `crsHotels` (`GET /catalog`) | crs-integration | Opciones y etiquetas del formulario. |
| `future` (`GET /reservations/{h}/future`) | crs-integration | La página del backfill. Lectura: las órdenes de proyección van por Kafka. |

### El resto del repositorio

| De → a | Llamada | Clase | Por qué se queda |
|---|---|---|---|
| crs-integration → booking | `GET /bookings/{id}`, `/bookings/future`, `/catalog` | consulta | «El aviso solo avisa; el dato se relee»: el paso lee la reserva tal como está ahora. |
| crs-integration → erp | `GET /partners/{code}` | consulta | Igual, para el interlocutor. |
| crs-integration → integrations | `GET /integrations/views` (caché 30 s) | consulta | Qué hoteles tienen integración, para no arrancar procesos que no van a ninguna parte. |
| pms-integration → crs-integration, mapping, integrations, mdm | reserva, interlocutor, `/resolve`, `/partner-profiles`, conexión, cliente | consulta | Los pasos del conector leen lo que van a escribir en Opera. |
| pms-integration → mdm | `POST /identities/resolve` | consulta con efecto | El paso necesita ya el `customerId` (si no lo hay, el MDM crea un provisional). |
| mapping → crs-integration, pms-integration, integrations | catálogos, reserva, interlocutor, integraciones | consulta / UI | Pantallas de diccionario, herramientas MCP del agente, paso `prepare`. |
| mapping → ia-agent | `POST /ai/api/agent/chat` desde la pantalla | ida y vuelta de UI | La persona espera la propuesta en el diccionario. La petición de fondo del alta llega ahora por `mapping-commands`. |
| customer-mdm → booking, front-office | reservas, estancias | consulta | La ficha del cliente. |
| booking, front-office → mdm | `GET /reservations/{h}/{loc}/links` | consulta | Los enlaces a otros sistemas en la ficha de la reserva. |
| booking → erp | `GET /partners` | consulta | El formulario de reservas de demo. |
| front-office → crs-integration | `GET /walk-ins/offer`, `POST /walk-ins/quote` | UI | La recepción elige habitación y ve el precio. |
| front-office → crs-integration → booking | `POST /walk-ins` → `POST /bookings` | orden que necesita respuesta | La recepción necesita el localizador del CRS para abrir la estancia. Es idempotente por la referencia `FO-…`, y el reenvío programado del front office cubre la caída. |
| ia-agent → servidores MCP, api-mcp → APIs | herramientas | ida y vuelta de UI | Una persona conversa con el agente: cada herramienta es parte de su respuesta. |
| ia-agent, api-mcp → ia-control-plane | configuración del agente, RAG, catálogo | consulta | |
| pms-integration → Opera (OHIP) | Property APIs | conector a sistema externo | No es entre servicios nuestros: es el conector. |

audit-service y communication-service solo consumen Kafka. Las consolas no hacen llamadas de
servidor a servidor.

## Órdenes que aún van por HTTP (pendientes)

| De → a | Llamada | Qué falta |
|---|---|---|
| front-office → crs-integration | `POST /no-shows` | El front office no tiene Kafka (ni binder ni outbox) y otro agente lo está refactorizando. Hoy la respuesta se usa para el mensaje de recepción («no está en el CRS», «ya cancelada»). Con Kafka ese aviso llegaría después, por notificación. |
| front-office → customer-mdm | `POST /customers/{id}/change-requests` (cambio de kardex) | Kafka en el front office. Además la solicitud no es idempotente en el MDM (cada llamada crea un `CR-<uuid>`), así que un reenvío tras un *timeout* duplica: necesita el id del front office como clave. |
| pms-integration → front-office | `PUT /api/guests/{id}/kardex`, `PUT /api/reservations/{loc}`, `POST …/cancellation` | El front office no consume Kafka. Las tres son sobrescrituras idempotentes. La del kardex ya es un *relay* de `customers`: el front office podría suscribirse directamente. |
| pms-integration → mapping | `POST /causes/wait` | pms-integration no tiene base de datos ni outbox. La registra el paso antes de contestar al motor, que la reintenta si falla. Moverla pide un outbox en el conector, o enviar a `mapping-commands` antes de la respuesta. |
| pms-integration → customer-mdm | `PUT /customers/{id}/xrefs` | El MDM no consume Kafka. Es de mejor esfuerzo e idempotente por clave. |
| ia-agent → ia-control-plane | `POST /internal/usage` | Telemetría de uso, *fire-and-forget* sin reintento: fuera de la PoC. |

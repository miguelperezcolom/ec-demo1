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
| `customer-commands` | front-office (`command_outbox`) | customer-mdm-service (`consumeCustomerCommands`, grupo `ec-demo1-customer-mdm-commands`) | `propose-change` (un cambio del kárdex, para que Salesforce lo decida; su `commandId` es el id de la solicitud, `CR-FO-…`), `record-scanned-identity` (el documento escaneado de un pax: dato de confianza, ver abajo) |
| `no-show-reports` | — (el front office ya no la manda: su no show sube al PMS, `front-office-events`) | crs-integration-service (`consumeNoShowReports`, grupo `ec-demo1-crs-integration-no-shows`) | `ReportNoShow`: nadie de la reserva ha llegado; arranca `registrar-no-show` una vez por reserva. El no show del front office llega al CRS por el paso `report-no-show` de `registrar-no-show-pms` |
| `front-office-commands` | integrations-service (outbox; `replace-catalogue`) y pms-integration (paso `project-stay` de `proyectar-estancia`; `write-stay`) | front-office (`FrontOfficeCommands`, grupo `ec-demo1-front-office-commands`, inbox `command_inbox`) | La integración pms-fo: el catálogo del PMS con el que el front office lee sus estancias, y cada reserva **tal como Opera la tiene** (creada, cambiada, cancelada, no show, en casa, salida), ordenada por la última modificación de Opera; y `record-reception` (pms-integration, pasos de `registrar-checkin`, `-checkout`, `-no-show-pms`): Opera rechazó lo que hizo recepción y por qué, la habitación de Opera, la factura del check-out. Clave: `propiedad/reserva de Opera` |

## Eventos que van por Kafka

| Topic | Emisor | Consumidores | Qué dice |
|---|---|---|---|
| `pms-reservations` | pms-integration (tras grabar o cancelar en Opera, también si Opera ya la tenía) | integrations-service (`consumePmsReservations`, grupo `ec-demo1-integrations-pms-reservations`) | `PmsReservationChanged`: una reserva se ha escrito en Opera. El conector no sabe quién la consume; la integración pms-fo de la propiedad, si está activa, arranca `proyectar-estancia` |
| `front-office-events` | front-office (outbox, en la transacción de recepción) | integrations-service (`consumeFrontOfficeEvents`, grupo `ec-demo1-integrations-front-office-events`) | `guest-checked-in`, `guest-checked-out`, `no-show-reported`: lo que hizo recepción. El PMS es el maestro de la estancia: la integración pms-fo, si está activa, arranca `registrar-checkin`, `registrar-checkout` o `registrar-no-show-pms`. Clave: `hotel/estancia` |
| `customers` | customer-mdm-service | pms-integration **ya no**; front-office (`CustomerEvents`, grupo `ec-demo1-front-office-customers`) y crs-integration | El golden record del cliente cambió o dos clientes eran uno: el kárdex del front office lo toma directamente del MDM |

- **El contrato es del receptor.** El formato de `mapping-commands`, `projection-requests`,
  `customer-commands` y `no-show-reports` está en los contratos (`contracts/`: `MappingCommand` en
  `contracts-mapping`, `ProjectReservation` y `ReportNoShow` en `contracts-reservation`,
  `CustomerCommand` en `contracts-customer`), con su esquema en `contracts/schemas`. El ERP y el CRS son
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
- **El outbox del front office** es la tabla `command_outbox` (tema, clave, JSON), junto a su
  `audit_outbox` y con el mismo patrón: la orden se escribe en la transacción de la decisión de
  recepción (el cambio del kárdex, el escaneo, la marca de no show) y `CommandRelay` la publica. Las
  órdenes sobre un cliente van con su código como clave; las de una reserva, con `hotel/localizador`.
- **Consecuencias en recepción.** El front office ya no espera al MDM ni al CRS. Un cambio del kárdex
  que no cambia nada lo aprueba el MDM al momento y la decisión llega por la vía de siempre
  (`customers` → kárdex, que el front office consume directamente).
- **El paso `project-stay` publica sin outbox.** pms-integration no tiene base de datos: el paso lee
  Opera, publica `write-stay` con productor síncrono y solo entonces contesta al motor. Si Kafka no la
  toma, el paso falla y el motor lo reintenta; repetirla no hace nada, porque el front office ordena
  por la versión de Opera y deduplica por `commandId`. Lo mismo el evento `pms-reservations` en
  `upsert-reservation` y `cancel-reservation`, y `record-reception` en los pasos de recepción. Un no
  show sale con el aviso «Se comunica a Opera, el PMS, que lo anota y lo sube al CRS…»; si el CRS no la
  tiene o ya estaba cancelada, `report-no-show` lo registra y la estancia no cambia.
- **Un documento escaneado es dato de confianza.** El MDM rellena lo que el cliente no tiene
  (documento, fecha de nacimiento, nacionalidad) y lo proyecta al contacto de Salesforce **sin Case**.
  Lo que contradice (otro nombre, otra fecha de nacimiento, otro documento) va como solicitud de cambio
  (Case), como un cambio del kárdex. Si el documento ya es de otro cliente, es la misma persona: el MDM
  consolida el provisional en el que tiene el documento (supervivencia, alias, reservas re-apuntadas,
  `CustomersMerged`) y fusiona los dos contactos en Salesforce con `merge()` de la API SOAP. Solo si es
  seguro: el documento es de un único cliente, el pax no tenía otro y el nombre coincide. Si no, va como
  Case.
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

### Lo que la integración pms-fo necesita saber ya (integrations-service → otros)

| Llamada | Destino | Por qué síncrona |
|---|---|---|
| `GET /front-office/catalogue?hotelId` | pms-integration → Opera | El catálogo de la propiedad que se manda al front office (la orden va por `front-office-commands`). |
| `GET /front-office/reservations?hotelId&from&to&scope&modifiedSince` | pms-integration → Opera | La lista del backfill y cada sondeo: qué reservas de la ventana cambiaron desde el cursor. Lectura: las proyecciones arrancan procesos por el outbox. |
| `GET /api/pms-catalogue/summary` | front-office | La conectividad con el front office y la puerta del catálogo: si ya tiene el que se le mandó. |

### El resto del repositorio

| De → a | Llamada | Clase | Por qué se queda |
|---|---|---|---|
| crs-integration → booking | `GET /bookings/{id}`, `/bookings/future`, `/catalog` | consulta | «El aviso solo avisa; el dato se relee»: el paso lee la reserva tal como está ahora. |
| crs-integration → erp | `GET /partners/{code}` | consulta | Igual, para el interlocutor. |
| crs-integration → integrations | `GET /integrations/views` (caché 30 s) | consulta | Qué hoteles tienen integración, para no arrancar procesos que no van a ninguna parte. |
| pms-integration → crs-integration, mapping, integrations, mdm | reserva, interlocutor, `/resolve`, `/partner-profiles`, conexión, cliente | consulta | Los pasos del conector leen lo que van a escribir en Opera. |
| pms-integration → mdm | `POST /identities/resolve` | consulta con efecto | El paso necesita ya el `customerId` (si no lo hay, el MDM crea un provisional). |
| pms-integration → mdm | `GET /customers?xref=OPERA:<perfil>`, `GET /customers/{id}` | consulta | `project-stay`: qué cliente es el perfil de Opera de la reserva, y su golden record. |
| mapping → crs-integration, pms-integration, integrations | catálogos, reserva, interlocutor, integraciones | consulta / UI | Pantallas de diccionario, herramientas MCP del agente, paso `prepare`. |
| mapping → ia-agent | `POST /ai/api/agent/chat` desde la pantalla | ida y vuelta de UI | La persona espera la propuesta en el diccionario. La petición de fondo del alta llega ahora por `mapping-commands`. |
| customer-mdm → booking, front-office | reservas, estancias | consulta | La ficha del cliente. |
| booking, front-office → mdm | `GET /reservations/{h}/{loc}/links` | consulta | Los enlaces a otros sistemas en la ficha de la reserva. |
| booking → erp | `GET /partners` | consulta | El formulario de reservas de demo. |
| front-office → crs-integration | `GET /walk-ins/offer`, `POST /walk-ins/quote` | UI | La recepción elige habitación y ve el precio. |
| front-office → pms-integration → Opera | `GET /front-office/rooms?hotelId&roomType` | consulta de UI | El paso de habitación del check-in ofrece las habitaciones del tipo de la estancia con su estado en Opera (limpia, inspeccionada, sucia; libre u ocupada), para no elegir una que Opera rechazaría. Si no contesta, se ofrecen las del catálogo sin estado. El check-in en sí va por `front-office-events`. |
| front-office → crs-integration, mdm | `GET /reservations/{h}/{loc}`, `GET /customers?q=` | consulta de UI | El escáner de demo lee la reserva (nacionalidad, edad del niño) y si el pax ya es un cliente con documento. Si no contestan, se inventa el documento igual. |
| front-office → crs-integration → booking | `POST /walk-ins` → `POST /bookings` | orden que necesita respuesta | La recepción necesita el localizador del CRS para abrir la estancia. Es idempotente por la referencia `FO-…`, y el reenvío programado del front office cubre la caída. |
| ia-agent → servidores MCP, api-mcp → APIs | herramientas | ida y vuelta de UI | Una persona conversa con el agente: cada herramienta es parte de su respuesta. |
| ia-agent, api-mcp → ia-control-plane | configuración del agente, RAG, catálogo | consulta | |
| pms-integration → Opera (OHIP) | Property APIs | conector a sistema externo | No es entre servicios nuestros: es el conector. |

audit-service y communication-service solo consumen Kafka. Las consolas no hacen llamadas de
servidor a servidor.

## Órdenes que aún van por HTTP (pendientes)

| De → a | Llamada | Qué falta |
|---|---|---|
| pms-integration → mapping | `POST /causes/wait` | pms-integration no tiene base de datos ni outbox. La registra el paso antes de contestar al motor, que la reintenta si falla. Moverla pide un outbox en el conector, o enviar a `mapping-commands` antes de la respuesta. |
| pms-integration → customer-mdm | `PUT /customers/{id}/xrefs` | El MDM ya consume `customer-commands`, pero pms-integration no tiene outbox. Es de mejor esfuerzo e idempotente por clave (el perfil de Opera en `ensure-guest-profile`; el huésped del front office en `project-stay`). |
| ia-agent → ia-control-plane | `POST /internal/usage` | Telemetría de uso, *fire-and-forget* sin reintento: fuera de la PoC. |

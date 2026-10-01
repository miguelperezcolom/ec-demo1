---
title: El código, de punta a punta
description: Una reserva del CRS seguida por el código hasta Opera y el front office, y el check-in de vuelta — fichero, clase, método, topic y tarea en cada salto.
sidebar:
  label: El código, de punta a punta
---

[Del CRS a Opera](/integracion/crs-a-opera/) y [De Opera al front office](/integracion/pms-a-front-office/)
cuentan el viaje de una reserva en palabras de negocio. Esta página lo sigue **por el código**: en cada
salto, el fichero, la clase y el método que lo hacen, el topic o la llamada por la que sale y la tarea
del motor que lo atiende. Las rutas son relativas a la raíz del repositorio; los extractos están
recortados a las líneas que importan.

## El viaje entero

```text
 #  Quién (servicio · clase)                          Cómo sale                                  A dónde
 1  booking · CreateBookingUseCase / Booking.create   outbox → crs-bookings (booking-created)     crs-integration
 2  crs-integration · CrsEventHandler.handle          relee GET /bookings/{id}; outbox →          crs-integration
                                                      integration-events (ReservationCreated)
 3  crs-integration · ProcessRouter.route             outbox → upstream (ProcessCreationRequested) motor
 4  motor · proyectar-reserva / prepare               topic mapping, prepare-reservation@1         mapping
 5  mapping · TaskHandlers.prepareReservation         OK | WAIT (+ causas, Waiter) → upstream      motor
 6  motor · ensure-guest-profile                      topic pms-integration                        pms-integration
 7  motor · LOCK + upsert-reservation                 topic pms-integration, upsert-reservation@1  pms-integration
 8  pms-integration · TaskHandlers.upsertReservation  POST /resolve (mapping); OhipClient → OHIP   Opera
                                                      DONE | STALE | WAIT → upstream; pms-reservations
 9  motor · annotate-pms-reference                    topic crs-integration → booking-commands     booking
10  integrations · PmsReservationEvents.on (o sondeo) outbox → upstream: proyectar-estancia        motor
11  pms-integration · StayProjection.project          front-office-commands (WriteStay)            front office
12  front office · PmsStays.take / write              la estancia, por versión de Opera            —
    ── en recepción ──
13  front office · CheckInService → ReceptionReports  outbox → front-office-events (GuestCheckedIn) integrations
14  integrations · ReceptionEvents.on                 outbox → upstream: registrar-checkin         motor
15  pms-integration · ReceptionHandlers.checkIn       OperaFrontDesk.checkIn; RecordReception;     Opera, FO
                                                      pms-reservations → vuelve al paso 10
```

Dos reglas que se repiten en todos los saltos: **lo que cruza Kafka son avisos y referencias, no
datos** (cada paso relee lo que necesita), y **todo lo que sale de un servicio con base de datos sale
por su outbox**, en la transacción del cambio que lo provoca.

## 1. El CRS crea la reserva y la anuncia

`systems/crs/booking`. El caso de uso abre el span de la traza de la reserva y guarda el agregado:

```java
// application/usecases/booking/create/CreateBookingUseCase.java — handle (@Transactional)
return traces.inSpan("booking.create", () -> { ...
    repository.save(Booking.create(new BookingId(locatorValueGenerator.generate().toString()),
            hotel.code(), hotel.currency(), terms, payments, command.expectedTotal(), clock.instant()))
```

El agregado nace confirmado, en la **versión 1**, y registra **un** evento aunque traiga varios cobros:

```java
// domain/aggregates/booking/Booking.java — Booking.create
var booking = new Booking(id, hotelCode, currency, terms, List.of(), BookingStatus.Confirmed,
        null, null, now, now, 1);
(payments != null ? payments : List.<Payment>of()).forEach(booking::addPayment);
booking.send(new BookingCreated(eventId(), id.id(), hotelCode, booking.version, now));
```

`BookingCreated` es deliberadamente delgado — `eventId`, `bookingId`, `hotelCode`, `version`,
`occurredAt` — y su `partitionKey()` es el id de la reserva (`BookingEvent`), así que los eventos de
una reserva quedan en orden en una partición.

El repositorio escribe la reserva y sus eventos en la misma transacción, y comprueba que la versión
continúa la guardada:

```java
// infra/out/persistence/BookingDBRepository.java — save
var events = booking.popEvents().stream().map(BookingEvent.class::cast).toList();
...
if (!events.isEmpty() && events.getFirst().version() != stored + 1) {
    throw new IllegalStateException("Booking %s changed concurrently: ...");
}
...
events.forEach(event -> outbox.append(Outbox.Destination.BookingEvents, event));
```

`OutboxWriter` pone el nombre de cable de cada evento (`booking-created`, `booking-modified`,
`booking-cancelled`, en `EVENT_TYPES`) y lo deja en el outbox compartido
(`supporting/messaging`) sobre el binding `bookingEvents`, que en `application.yaml` es el topic
**`crs-bookings`**. El relay del outbox lo publica después; nadie hace un `send` a Kafka dentro de la
transacción.

## 2. La ACL del CRS relee y traduce

`integration/crs-integration-service`. `StreamFunctions.consumeCrsEvents` escucha `crs-bookings` (y
`partners`) y entrega el JSON a `CrsEventHandler`:

```java
// in/CrsEventHandler.java — handle (@Transactional)
if (!inbox.firstTime(CONSUMER, eventId)) { return; }   // CONSUMER = "crs-events"
translate(type, event).ifPresent(outbox::append);

// translate
    case "booking-created", "booking-modified", "booking-cancelled" -> {
        var booking = crs.booking(bookingId);           // GET /bookings/{id} al CRS
        if (booking.isEmpty()) { ... yield Optional.empty(); }
        var hotel = booking.get().hotelCode();
        yield Optional.of(switch (type) {
            case "booking-created" -> new ReservationCreated(eventId, occurredAt, hotel, bookingId, version);
```

Inbox y outbox en una transacción: un evento repetido no hace nada, y uno atendido siempre deja su
evento de negocio en camino. La **versión es la del evento**, no la de la relectura (que puede ir ya
por delante). `ReservationCreated` sale por el binding `integrationEvents`, topic
**`integration-events`**, con clave `hotelCode/locator` (`ReservationCreated.key()`).

## 3. El router arranca el proceso

El mismo servicio consume `integration-events` (`StreamFunctions.routeIntegrationEvents`): dos
etapas, para que el evento de negocio quede visible en el bus y la tabla de rutas cambie sin tocar la
traducción.

```java
// router/ProcessRouter.java — route
if (!inbox.firstTime(CONSUMER, event.eventId())) { return; }       // CONSUMER = "router"
var definitionId = properties.routes().get(typeOf(event));          // reservation-created → proyectar-reserva
if (hotelCode != null && !integrations.integrated(hotelCode)) { return; }
var processKey = definitionId + ":" + event.key() + ":" + event.eventId();
// variables: definitionId, processKey, eventId, version, hotelCode, locator
outbox.appendToEngine(new ProcessCreationRequested(definitionId, processKey, variables, null,
        AuthorizationContext.SYSTEM));
```

La tabla está en `application.yaml` (`crs.routes`). La clave de negocio
(`proyectar-reserva:MRU01/<localizador>:<eventId>`) es determinista: pedir el arranque dos veces lo
arranca una. El proceso lleva **referencias** (`hotelCode`, `locator`, `version`), nunca la reserva.
`appendToEngine` usa `EngineOutbox` (`supporting/messaging`), que deja la petición en el outbox hacia el
topic **`upstream`** del motor con el contexto de traza actual: cada tarea que el motor despache para
este proceso continuará esa traza.

## 4. El motor ejecuta `proyectar-reserva`

La definición es `definitions/workflows/proyectar-reserva.ec` en
[ec-definitions](https://github.com/miguelperezcolom/ec-definitions). Cada `ACTION` nombra su tarea y
el topic del worker; el motor publica la tarea en ese topic y espera la respuesta en `upstream`:

```yaml
- { id: prepare, type: ACTION, task: prepare-reservation, topic: mapping, retries: 10000 }
- { id: prepared, type: CHOICE }
- id: wait-prepare
  type: WAIT_FOR_MESSAGE
  messageName: causes-resolved
  correlationExpression: processKey
  preconditions: [ { stepId: prepared, expression: "prepareOutcome == 'WAIT'" } ]
```

Qué servicio atiende cada tarea está en `contracts/workers/*.tasks`, generado de los
`TaskRegistration` de cada servicio: `prepare-reservation@1 mapping` en `mapping-service.tasks`,
`upsert-reservation@1 pms-integration` en `pms-integration-service.tasks`, etc.

En cada servicio, el runtime del motor (`worker-kafka`) liga el topic de tareas
(`consumeWorkerEvent-in-0`), pasa las variables del proceso al `record` de entrada del handler y
publica su salida en `upstream`. Una excepción del handler es un fallo del paso, y el motor lo
reintenta (`retries: 10000`).

## 5. Preparar: el mapeado

`integration/mapping-service`. `worker/MappingTasks` registra
`new TaskRegistration<>(PREPARE_RESERVATION, 1, TOPIC, ...)` con `TOPIC = "mapping"` y
`handlers::prepareReservation`. El handler relee la reserva canónica de la ACL (`GET /reservations/{hotel}/{locator}`,
`CanonicalController`, que la traduce con `CrsTranslator`) y la pasa a `Preparation`:

```java
// prepare/Preparation.java — reservation
codes.add(new Code(CodeType.HOTEL, r.hotelCode()));
codes.add(new Code(CodeType.CHANNEL, r.channelCode()));
r.rooms().forEach(room -> {
    codes.add(new Code(CodeType.ROOM_TYPE, room.roomTypeCode()));
    codes.add(new Code(CodeType.RATE_PLAN, room.ratePlanCode()));
    codes.add(new Code(CodeType.BOARD, room.boardCode()));
});
r.payments().forEach(p -> codes.add(new Code(CodeType.PAYMENT_METHOD, p.methodCode())));
return check(r.hotelCode(), codes, r.partnerCode(), wait, wait.heldByActivation());
```

`check` mira **todos** los códigos en el `Dictionary` y junta todo lo que falta — integración
inactiva, equivalencias, interlocutor sin perfil en Opera — antes de contestar:

```java
// prepare/Preparation.java — check
var translation = dictionary.resolve(hotelCode, code.type(), code.code());
if (translation.isEmpty()) {
    missing.add(Cause.missingMapping(scope, code.type(), code.code()));
    ...
}
...
if (missing.isEmpty()) {
    return Outcome.OK;
}
causes.await(wait.processKey(), wait.engineProcessId(), wait.definitionId(), hotelCode,
        wait.subject(), wait.variables(), missing);
// ...y vuelve a mirar: una aprobación entre la comprobación y el registro de la espera
return Outcome.WAIT;
```

La salida es `PrepareOutcome(prepareOutcome)`: `OK` o `WAIT`. **Preparar no traduce el payload**: solo
garantiza que todo tiene traducción; el conector la pide otra vez al escribir (paso 8).

### Si falta una equivalencia

La causa tiene una clave estable (`Cause.missingMapping` en `contracts-mapping`):
`MISSING_MAPPING:MRU01:ROOM_TYPE:<código>`. `Causes.await` la guarda (`CauseRecord`), registra al
proceso como **`Waiter`** de esa causa (`WaiterCause`) y anuncia el aviso a la bandeja. El proceso
contesta `WAIT`, el `CHOICE` lo lleva a `wait-prepare` y queda esperando el mensaje
`causes-resolved` correlacionado por su `processKey`. No falla: espera.

Cuando una persona aprueba la equivalencia, `Causes.mappingApproved` resuelve la causa y suelta a
quien esperaba solo por ella:

```java
// causes/Causes.java — resolve
for (var waiter : waiters.waitingOn(causeKey)) {
    if (links.openCausesOf(waiter.getProcessKey()) == 0) {
        waiter.setStatus(WaiterStatus.RELEASED);
        signal(waiter);   // MessageReceived("causes-resolved", processKey) → upstream
    }
}
```

El motor no tiene bucles, así que el proceso no vuelve a preparar: avanza a `relaunch-prepare`
(`relaunch-process@1`, otra vez en `mapping`), y `Causes.relaunch` arranca un **sucesor** con clave
`<processKey>>r` y las mismas variables, que relee la reserva desde el principio. La señal se reenvía
hasta que el proceso pide su relanzamiento (el motor no guarda mensajes que llegan antes de la
espera). Ver [Causas, avisos y bandeja](/integracion/causas-y-avisos/).

## 6. El huésped: `ensure-guest-profile`

`integration/pms-integration-service`, `worker/TaskHandlers.ensureGuestProfile`. Relee la reserva,
resuelve la identidad en el MDM (`POST /identities/resolve`), y si Opera ya tiene esta versión deja el
perfil como está; si no, superpone el golden record del MDM a los datos de la reserva y asegura el
perfil (`OperaProfiles.ensureGuest`). Devuelve `GuestProfile(profileOutcome, guestProfileId,
customerId)`; `guestProfileId` pasa a ser variable del proceso y entrada del paso siguiente. Detalle
en [Clientes: MDM y Salesforce](/integracion/clientes/).

## 7–8. Grabar en Opera: `upsert-reservation`

Antes del paso, la definición toma el candado de la reserva (`lock-write`: `LOCK`, `lockName:
reservation`, `lockKey: hotelCode + '/' + locator`) y lo suelta después (`unlock-write`). OHIP no tiene escritura condicional: leer la versión y escribir son dos llamadas, y el candado (que
comparte `proyectar-cancelacion`) las serializa por reserva. El registro en `worker/PmsTasks.java`
envuelve el handler en `RetryWatch` (el aviso `RETRYING_TOO_LONG` pasado `RETRY_ALERT_AFTER`):

```java
new TaskRegistration<>(UPSERT_RESERVATION, 1, TOPIC, TaskHandlers.ReservationTask.class,
        TaskHandlers.Write.class, watched(watch, handlers::upsertReservation, ...));
```

El handler:

```java
// worker/TaskHandlers.java — upsertReservation
var r = reservation(task, input);                                   // GET /reservations/{hotel}/{locator} a la ACL
var resolved = integration.resolve(r.hotelCode(), new ArrayList<>(codes));   // POST /resolve al mapping
var missing = new ArrayList<>(resolved.missing());
if (!missing.isEmpty()) {
    await(input, r, missing);                                       // POST /causes/wait
    return new Write(Outcome.WAIT.name(), null);
}
var hotel = resolved.target(CodeType.HOTEL, r.hotelCode());         // MRU01 → XMAR
try {
    var body = payload.build(r, resolved, hotel, ...guestProfileId..., partner, ...);
    var existing = reservations.byLocator(hotel, r.locator());
    if (existing.isEmpty()) {
        reservationId = reservations.create(hotel, body);
    } else {
        var written = reservations.writtenVersion(existing.get());
        if (written >= r.version() || OperaReservations.cancelled(existing.get())) {
            ...
            return new Write(Outcome.STALE.name(), reservationId);
        }
        reservations.update(hotel, reservationId, body);
    }
    ...
    events.written(hotel, reservationId, r.hotelCode(), r.locator(), task.workflowDefinitionId());
    return new Write(Outcome.DONE.name(), reservationId);
} catch (PmsRejectedException e) {
    rejected(task, input, r, "reservation " + r.locator(), e);     // causa PMS_REJECTED
    return new Write(Outcome.WAIT.name(), null);
}
```

- **Traducción en el momento de escribir.** `/resolve` (`MappingController.resolve`) devuelve las
  traducciones y, como causas, lo que dejó de tener equivalencia desde que se preparó. El proceso
  espera igual que en el paso 5. `ReservationPayload.build` monta el cuerpo de OHIP con los códigos de
  Opera, el desglose diario, el UDF de versión y la referencia externa.
- **Buscar por localizador.** `OperaReservations.byLocator` busca por la referencia externa en el
  contexto de la ejecución y relee la reserva entera por id (la búsqueda no trae UDF):

  ```java
  // ohip/OperaReservations.java
  ohip.get(hotelId, "/rsv/v1/hotels/{h}/reservations?externalReferenceIds={id}&externalSystemCodes={ext}",
          hotelId, locator, properties.externalSystemCode())
  ```

  `writtenVersion` lee el UDF numérico de versión (`UDFN01`). Crear es
  `POST /rsv/v1/hotels/{h}/reservations` (el id sale de la cabecera `Location`); modificar,
  `PUT /rsv/v1/hotels/{h}/reservations/{id}`.
- **La guarda de versión.** Si Opera ya tiene esta versión o una más nueva no se escribe nada: `STALE`.
  Una versión rezagada nunca pisa una nueva, lleguen en el orden que lleguen. `supersedeRefusals`
  resuelve el rechazo de una versión anterior que ya no tiene sentido esperar.

### La clasificación de errores

Todas las llamadas pasan por `ohip/OhipClient.call`, que añade token, `x-app-key` y `x-hotelid`,
reintenta una vez un 401 con token nuevo y clasifica el resto:

```java
// ohip/OhipClient.java — classify
if (status.is5xxServerError() || status.value() == 429 || status.value() == 401) {
    return new PmsTransientException("OHIP %d on %s %s: %s".formatted(...), e);
}
return new PmsRejectedException(status.value(), errorCode(e), detail);
```

- `PmsTransientException` (5xx, 429, 401 tras renovar, `ResourceAccessException` por timeout o red)
  **se propaga**: el paso falla y el motor lo reintenta.
- `PmsRejectedException` (cualquier otro 4xx) lo captura el handler: causa
  `PMS_REJECTED:<hotel>:<localizador>:upsert-reservation` con el código de error de Opera, y `WAIT`.

### Lo que avisa el conector

`frontoffice/PmsEvents.written` publica `PmsReservationChanged` en **`pms-reservations`** (binding
`pmsReservations`, clave la reserva de Opera). El conector no tiene base de datos ni outbox: lo envía
con `StreamBridge` síncrono, y si Kafka no lo toma lanza una excepción, el paso falla y se reintenta;
repetirlo no cambia Opera. También lo publica en `STALE`: quien consume Opera la relee igual.

## 9. El resultado vuelve al motor y al CRS

`Write(writeOutcome, pmsReservationId)` llega al motor por `upstream`; `writeOutcome` decide el
`CHOICE written` y `pmsReservationId` queda como variable. El siguiente paso,
`annotate-pms-reference` (topic `crs-integration`), lo atiende `crsintegration/worker/TaskHandlers`:

```java
// integration/crs-integration-service/.../worker/TaskHandlers.java
public Void annotatePmsReference(PmsReference input, TaskContext task) {
    var locator = required(task, ProcessVariables.LOCATOR, input.locator());
    var pmsReservationId = required(task, ProcessVariables.PMS_RESERVATION_ID, input.pmsReservationId());
    new TransactionTemplate(transactions).executeWithoutResult(status -> outbox.appendToCrs(
            new SystemCommands.AnnotatePmsReference(task.taskExecutionId(), locator, pmsReservationId)));
```

La orden va por el outbox al topic **`booking-commands`**; en el CRS la consume
`infra/in/async/BookingCommandsConsumer.consumeBookingCommands` y
`AnnotatePmsReferenceUseCase.handle` llama a `booking.annotatePmsReference(...)`. Ese cambio no
versiona la reserva ni anuncia nada: anunciarlo la mandaría otra vez por la integración. Por último
`resolve-projection` (`mapping`) resuelve las causas `NOT_YET_PROJECTED` de esta reserva — una
cancelación, o un check-in de recepción, que llegaron antes que ella.

## 10. De Opera al front office: `proyectar-estancia`

`control-plane/integrations-service` consume `pms-reservations`
(`consumePmsReservations-in-0`):

```java
// frontoffice/PmsReservationEvents.java — on
var integration = integrations.findFirstByPmsHotelCodeAndStatusNot(event.pmsHotelCode(), DECOMMISSIONED)
        .filter(i -> i.is(FoIntegrationStatus.ACTIVE));
if (integration.isEmpty()) { return false; }
writes.write(() -> projections.project(i.id, event.pmsHotelCode(), event.pmsReservationId(),
        "evt-" + event.eventId(), "pms-write:" + event.origin()));
```

`StayProjections.project` deja en el outbox un `ProcessCreationRequested` de **`proyectar-estancia`**
con clave `proyectar-estancia:<hotel Opera>:<reserva Opera>:<versión>`. La misma puerta la usan
`FoPolling.pollAll` (`@Scheduled`, `integrations.front-office.poll`, 60 s por defecto, con la
`lastModifyDateTime` de Opera como versión y el cursor avanzando en la misma transacción) y
`FoBackfill`. Por cualquiera de las tres, la clave hace que el motor la arranque una vez.

La definición tiene un único paso: `project-stay` en `pms-integration`.

## 11. `project-stay`: releer Opera y mandar la estancia

`TaskHandlers.projectStay(StayTask input, ...)` llama a `stays.project(pmsHotelCode, pmsReservationId)`:

```java
// integration/pms-integration-service/.../frontoffice/StayProjection.java — project
var found = stays.byId(hotel, reservationId);          // Opera, tal como la tiene ahora
...
var customerId = profileId == null ? null : integration.customerByPmsProfile(profileId).orElse(null);
var command = StayMapper.toWriteStay(hotel, reservation, new StayMapper.Context(ohip.externalSystemCode(), ...));
send(command);   // StreamBridge → frontOfficeCommands → front-office-commands
```

`WriteStay` (`contracts-frontoffice`) lleva los códigos de Opera, el localizador del CRS si lo tiene
(su referencia externa), el cliente del MDM y `pmsVersion`. Como en el paso 8, se envía sin outbox: si
el broker no lo toma, el paso falla y se reintenta.

## 12. El front office aplica la estancia

`systems/front-office` no usa Spring Cloud Stream: `infra/pms/FrontOfficeCommands` arranca un
listener sobre `FrontOfficeCommand.TOPIC` (grupo `ec-demo1-front-office-commands`) y pasa cada
comando a `application/PmsStays.take`:

```java
// application/PmsStays.java — take: inbox por commandId; write(WriteStay w):
var current = found.flatMap(links::ofStay).map(PmsLinks.Link::pmsVersion).orElse(null);
if (current != null && w.pmsVersion() != null && current.compareTo(w.pmsVersion()) > 0) {
    // ya tiene una versión más nueva de Opera: se queda con la suya
```

Ignora los comandos de otra propiedad (`frontoffice.pms-hotel`), deduplica por `commandId`, ordena por
la versión de Opera y casa la estancia por la reserva de Opera, el localizador del CRS o el walk-in
(`stayOf`, `walkInOf`). `PmsLinks.link` guarda qué reserva y qué versión de Opera es cada estancia.

## 13. Recepción hace el check-in

`application/CheckInService.checkInNow` asigna habitación, marca la estancia en casa, ocupa la
habitación, abre el folio y, **en la misma transacción**, avisa al PMS:

```java
// systems/front-office/.../infra/pms/ReceptionReports.java — checkedIn
outbox.appendEvent(new FrontOfficeEvent.GuestCheckedIn("CI-" + UUID.randomUUID(), clock.instant(), hotel,
        stay.id(), refs.crsLocator(), pmsHotel, refs.pmsReservationId(), stay.roomNumber(), stay.pax(), by));
links.state(stay.id(), "Opera: pendiente — check-in enviado");
```

El evento sale por el outbox del front office al topic **`front-office-events`**. El PMS es el maestro
de la estancia: el front office no da el check-in por registrado hasta que Opera lo dice.

## 14. La integración pms-fo arranca `registrar-checkin`

`integrations-service`, `consumeFrontOfficeEvents-in-0` → `frontoffice/ReceptionEvents.on`:

```java
var definition = switch (event) {
    case FrontOfficeEvent.GuestCheckedIn ignored -> Definitions.REGISTER_CHECK_IN;   // registrar-checkin
    ...
};
var key = definition + ":" + hotelCode + "/" + locator + ":" + roomNumber;   // un check-in por habitación
```

Con `pmsHotelCode`, `stayId`, `pmsReservationId` y `roomNumber` como variables. La definición
(`registrar-checkin.ec`) toma un `LOCK`, ejecuta `assign-room` y `check-in-reservation` en
`pms-integration`, y cada uno puede esperar a su causa y relanzarse como en el paso 5.

## 15. Opera registra el check-in y la respuesta baja

`worker/ReceptionHandlers.checkIn`:

```java
var found = reservation(input, task);    // por reserva de Opera o por localizador; si no está: NOT_YET_PROJECTED y WAIT
...
desk.checkIn(hotel, id, room);           // OperaFrontDesk: POST /fof/v1/hotels/{h}/reservations/{id}/checkIns
outcomes.done(hotel, id, input.stayId(), ReceptionOperation.CHECK_IN, "En casa en Opera", room, null);
supersede(input, "checked in in Opera, room " + room, ASSIGN_ROOM, CHECK_IN);
events.written(hotel, id, input.hotelCode(), input.locator(), task.workflowDefinitionId());
return new CheckedIn(Outcome.DONE.name(), id);
```

Vuelven dos cosas al front office:

- `ReceptionOutcomes.done` manda `RecordReception` por `front-office-commands`: lo que `WriteStay` no
  dice — que Opera lo aceptó (o lo rechazó y por qué), la habitación en la que lo tiene.
- `PmsEvents.written` publica otra vez en `pms-reservations`, y el camino de los pasos 10–12 lleva la
  estancia con el estado de Opera (`IN_HOUSE`).

Si la reserva aún no estaba en Opera (un walk-in que el CRS está reservando), el paso espera con
`NOT_YET_PROJECTED`, y el `resolve-projection` del paso 9 lo libera cuando llega.

## Dónde mirar cuando algo no llega

| Síntoma | Dónde |
| :------ | :---- |
| Nada sale del CRS | La tabla del outbox de `booking` y su relay; `BookingDBRepository.save` |
| La ACL no arranca el proceso | `ProcessRouter.route`: inbox, ruta en `crs.routes`, hotel sin integración |
| El proceso espera | Causas abiertas en mapping (`CauseRecord`, `Waiter`); *Integrations → Causas* |
| El paso reintenta sin parar | `PmsTransientException` en los logs de pms-integration; aviso `RETRYING_TOO_LONG` |
| Opera no cambia pero el proceso acaba | `writeOutcome = STALE`: el UDF de versión ya era igual o mayor |
| El front office no la tiene | Integración pms-fo no activa (`PmsReservationEvents.on`), o el sondeo |

La traza de todo el viaje está en *Ver recorrido* ([El recorrido de una reserva](/integracion/recorrido/)):
los atributos que la cuentan (`booking.locator`, `mapping.translations`, `opera.action`,
`opera.version`…) son los `tag(...)` que se ven en los extractos de arriba.

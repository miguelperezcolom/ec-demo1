---
title: Patrones
description: Los patrones de diseño que aplica el código de ec-demo1 — qué problema resuelve cada uno aquí, dónde está y lo que cuesta.
---

Esta página recoge los patrones que **de verdad** usa el código, con las clases que los implementan.
No repite el porqué de la arquitectura (eso está en [Arquitectura](/guias/arquitectura/)) ni el detalle
funcional de los procesos ([Los procesos del motor](/guias/procesos/)) o de las causas
([Causas, avisos y bandeja](/integracion/causas-y-avisos/)): aquí se mira cómo está escrito.

| Patrón | Dónde, sobre todo |
| :----- | :---------------- |
| Outbox transaccional + inbox idempotente | `supporting/messaging` (`Outbox`, `OutboxRelay`, `Inbox`) |
| Capa anticorrupción y lenguaje publicado | `crs-integration-service`, `pms-integration-service`, `contracts/` |
| Lector tolerante | `TolerantReader` y los consumidores de cada servicio |
| Orquestación por un motor, con workers | ec-definitions + `TaskRegistration` + `worker-kafka` |
| Causas y procesos que esperan | `mapping-service`, `Causes` |
| Copias locales y transferencia de estado por eventos | front office: reglas de registro, avisos, nacionalidades |
| Idempotencia, deduplicación y guarda de versión | `dedupKey`, inbox, upserts por localizador, `STALE` |
| Resiliencia ante APIs externas | `OhipClient.classify`, `RetryWatch`, `SalesforceBudget`, `Backoff` |
| Puertos y adaptadores | `booking`, `erp`, `front-office` |
| El API del servicio como herramientas MCP | `*McpTools` de cada servicio |
| El gateway como frontera | `consoles/gateway` |

## Outbox transaccional e inbox idempotente

**El problema.** Un servicio decide algo en su base de datos y tiene que contárselo a otro por Kafka.
Si publica antes de confirmar, puede anunciar algo que luego se deshace; si publica después, una caída
entre medias lo pierde. Y Kafka entrega **al menos una vez**: el receptor verá repetidos.

**Cómo está hecho.** `supporting/messaging` es una librería que comparten todos los servicios con base
de datos. `Outbox.append` escribe el mensaje en la tabla `outbox_message` **dentro de la transacción del
llamante**, sea JPA o JDBC:

```java
// supporting/messaging/.../messaging/Outbox.java
public void append(String destination, String key, String type, String payload, Map<String, String> headers) {
    // …
    if (requireTransaction && !TransactionSynchronizationManager.isActualTransactionActive()) {
        throw new IllegalTransactionStateException("Appending to the outbox outside a transaction");
    }
    var trace = traces.current();
    jdbc.update("""
            insert into %s (binding, message_key, event_type, payload, headers, created_at, traceparent,
                            tracestate, attempts, abandoned)
            values (?, ?, ?, ?, ?, ?, ?, ?, 0, false)""".formatted(properties.outbox().table()), /* … */);
}
```

`OutboxRelay` (lo programa `RelayScheduler`) toma un lote con `for update … skip locked` —dos réplicas
se reparten el trabajo en vez de esperarse—, lo envía por el `OutboxTransport` del servicio
(`KafkaTemplateTransport` o `StreamBridgeTransport`) y lo marca enviado. El orden se guarda **por
clave**: un mensaje que falla retiene a los de su clave con un backoff que se duplica, y las demás
claves siguen. El contexto de la traza viaja con el mensaje, así que el consumidor continúa la traza
que lo escribió.

Cada servicio envuelve el outbox con sus destinos tipados. En `mapping-service`:

```java
// integration/mapping-service/.../mapping/outbox/Outbox.java
@Transactional(propagation = Propagation.MANDATORY)
public void appendNotification(NotificationRequested notification) {
    write(NOTIFICATIONS, notification.dedupKey(), "NotificationRequested",
            serialise(NotificationRequested.class, notification));
}
```

`Propagation.MANDATORY` hace que escribir en el outbox fuera de una transacción sea un error, no un
mensaje suelto. Las peticiones al motor (arrancar o cancelar un proceso) van por el mismo camino con
`EngineOutbox`, que serializa el `DomainEvent` de EventConductor.

En el otro lado, `Inbox.firstTime` registra el id del mensaje en `inbox_entry` (clave
`consumer, event_id`) en la **misma transacción** que el trabajo:

```java
// integration/mapping-service/.../mapping/commands/MappingCommands.java
@Transactional
public boolean handle(MappingCommand command) {
    // …
    if (!inbox.firstTime(CONSUMER, command.commandId())) {
        log.debug("Already taken: {}", command);
        return false;
    }
    switch (command) {
        case MappingCommand.DefineEquivalence c -> effects.defineUnlessInForce(c);
        // …
    }
}
```

O se confirman el registro y el trabajo, o ninguno: una reentrega encuentra el id sin registrar y
vuelve a intentarlo. En PostgreSQL es un `insert … on conflict do nothing`.

**Lo que cuesta.**

- Entrega **al menos una vez, nunca cero**: todo consumidor tiene que deduplicar. Un mensaje sin id no
  se puede deduplicar y su trabajo se ejecuta siempre.
- Latencia: el mensaje sale en la siguiente pasada del relay, no al confirmar.
- `MessagingSchema` crea y amplía las tablas solo de forma aditiva (nunca borra ni renombra), para que
  un servicio que tenía su propio outbox conserve sus filas. El front office aún arrastra sus tablas
  anteriores (`command_outbox`, `audit_outbox`, `command_inbox`): `LegacyOutboxes` pasa lo pendiente a
  las compartidas al arrancar y durante un rato después, por si el pod viejo de un despliegue sigue
  escribiendo.
- `pms-integration-service` no tiene base de datos ni outbox: publica con un productor síncrono y solo
  después contesta al motor (ver [Arquitectura](/guias/arquitectura/#cómo-se-hablan-los-servicios-órdenes-por-kafka-consultas-por-http)).

## Capa anticorrupción y lenguaje publicado

**El problema.** El CRS (`booking`) y Opera tienen cada uno su modelo, y ninguno debe filtrarse al
resto. Si el mapeado o el MDM entendiesen el JSON de OHIP, un cambio de Opera rompería media
plataforma.

**Cómo está hecho.** Dos adaptadores en los extremos (descritos en
[Arquitectura](/guias/arquitectura/#la-capa-anticorrupción)) y, en medio, un **lenguaje publicado**:
las librerías de `contracts/`, una por contexto (`contracts-reservation`, `contracts-mapping`…), de
las que cada servicio toma solo las que habla. `CrsEventHandler` es la traducción de entrada: lee el
evento del CRS como `JsonNode`, relee la reserva por HTTP y emite un `IntegrationEvent` canónico:

```java
// integration/crs-integration-service/.../crsintegration/in/CrsEventHandler.java
case "booking-created", "booking-modified", "booking-cancelled" -> {
    var bookingId = e.path("bookingId").asText();
    var booking = crs.booking(bookingId);
    // …
    yield Optional.of(switch (type) {
        case "booking-created" -> new ReservationCreated(eventId, occurredAt, hotel, bookingId, version);
        case "booking-modified" -> new ReservationModified(eventId, occurredAt, hotel, bookingId, version);
        default -> new ReservationCancelled(eventId, occurredAt, hotel, bookingId, version);
    });
}
```

El de salida es `OhipClient` y las clases `Opera*` de `pms-integration-service`: nadie más conoce OHIP.

Cada topic tiene su **esquema** generado de los records (`contracts/schemas/<topic>/v<N>.schema.json`)
con `x-owner`, `x-key`, `x-producers` y `x-consumers`. El build lo vigila desde los dos lados; el test
de `mapping-service` es representativo:

```java
// integration/mapping-service/src/test/.../mapping/contracts/MappingContractsTest.java
written(Outbox.NOTIFICATIONS).forEach(Contracts.topic("notifications")::assertValid);
// …
var examples = Contracts.topic("mapping-commands").examples();
examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));
verify(commands, times(examples.size())).handle(read.capture());
```

El productor valida lo que **de verdad** escribe su outbox; el consumidor pasa cada ejemplo del esquema
por su bean real. Las tareas del motor tienen su propia comprobación cruzada (`ServedTasksTest` y
`check-contracts.sh`, en [Workers y tareas](/contratos/workers-y-tareas/#la-comprobación-de-contratos)).

**Lo que cuesta.** Una capa más que mantener por cada sistema externo, y una traducción explícita en
cada frontera. Las reglas de versionado y cómo se regenera un esquema están en
[Librerías y esquemas](/contratos/librerias-y-esquemas/); no hay registro de esquemas en tiempo de
ejecución: el contrato se comprueba en el build, no en el broker.

## Lector tolerante

**El problema.** El mapper de Spring Boot rechaza las propiedades desconocidas: correcto para el API
propio, fatal para leer lo de otro. Un campo que el CRS añada mañana no debe romper la integración hoy.

**Cómo está hecho.** Un `TolerantReader` por servicio que lee de fuera (`crs-integration-service`,
`integrations-service`), y la misma configuración en los consumidores de `communication-service`,
`audit-service`, el front office y los clientes de la IA:

```java
// integration/crs-integration-service/.../crsintegration/config/TolerantReader.java
/** … A component and not an ObjectMapper bean, because declaring one would switch Spring Boot's off. */
@Component
public class TolerantReader {
    private final ObjectMapper mapper;

    public TolerantReader(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
```

Es un componente y no un `@Bean ObjectMapper` a propósito: declarar uno apagaría el de Boot.

**Lo que cuesta.** Lo que no se conoce se ignora en silencio, también un campo mal escrito. Por eso es
la otra mitad de las versiones de los esquemas: añadir una propiedad opcional es compatible
(misma versión) **porque** todos los consumidores leen así; quitar o cambiar una es una versión nueva.
Un mensaje que ni así se puede leer se registra como ilegible y se salta (p. ej.
`RegistrationRuleEvents.take`).

## Orquestación por un motor, con workers sobre Kafka

**El problema.** Proyectar una reserva son varios pasos en varios servicios, con esperas, reintentos y
concurrencia sobre la misma reserva. Repartir esa lógica en coreografía (cada servicio reacciona al
anterior) la haría invisible.

**Cómo está hecho.** EventConductor **orquesta**: las definiciones (`.ec` en ec-definitions) dicen el
orden, y cada paso `ACTION` es una tarea que el motor publica en el topic de un worker. El servicio
solo declara un `TaskRegistration` por tarea y el runtime `worker-kafka` hace el resto (ver
[Workers y tareas](/contratos/workers-y-tareas/)). `ProcessRouter` traduce cada evento canónico en un
arranque, con una clave de negocio derivada del evento:

```java
// integration/crs-integration-service/.../crsintegration/router/ProcessRouter.java
if (!inbox.firstTime(CONSUMER, event.eventId())) {
    return;
}
var definitionId = properties.routes().get(typeOf(event));
// …
var processKey = definitionId + ":" + event.key() + ":" + event.eventId();
```

Entre servicios, **las órdenes van por Kafka y las consultas por HTTP**: un paso relee la reserva del
CRS por HTTP (`TaskHandlers.reservation` → `integration.reservation(hotel, locator)`), pero lo que
escribe en otro sistema sale como orden por el outbox. La regla completa está en
[Arquitectura](/guias/arquitectura/#cómo-se-hablan-los-servicios-órdenes-por-kafka-consultas-por-http).

**Lo que cuesta.** El motor es un componente central del que dependen todos los flujos, con sus propias
limitaciones: no admite ciclos, descarta el sucesor que un `CHOICE` no elige, y la entrega de tareas es
al menos una vez, así que **cada handler es idempotente**. Una tarea sin handler solo se ve en el log
salvo con `eventconductor.worker.strict`. Las consultas por HTTP acoplan en el tiempo: si el CRS no
contesta, el paso falla y el motor lo reintenta.

## Causas y procesos que esperan

**El problema.** Falta una equivalencia de código, el interlocutor aún no está en Opera, Opera dice
«no». Reintentar no lo arregla, y fallar el proceso obligaría a alguien a relanzarlo a mano.

**Cómo está hecho.** El paso devuelve sus carencias, `Causes.await` las registra (una causa la
comparten todos los procesos que la esperan) y el proceso espera un mensaje correlado por su clave.
Resolver la causa libera a quienes ya no esperan nada más:

```java
// integration/mapping-service/.../mapping/causes/Causes.java
for (var waiter : waiters.waitingOn(causeKey)) {
    if (links.openCausesOf(waiter.getProcessKey()) == 0) {
        waiter.setStatus(WaiterStatus.RELEASED);
        waiter.setReleasedAt(clock.instant());
        signal(waiter);
        released++;
    }
}
```

Como el motor no admite ciclos, el proceso reanudado no vuelve atrás: pide un **sucesor**, cuya clave
deriva de la suya, de modo que pedirlo dos veces lo arranca una:

```java
// Causes.relaunch
var successorKey = processKey + ">r";
// …
if (waiter.getStatus() != WaiterStatus.RELAUNCHED) {
    outbox.appendToEngine(new ProcessCreationRequested(waiter.getDefinitionId(), successorKey, variables,
            null, AuthorizationContext.SYSTEM));
    waiter.setStatus(WaiterStatus.RELAUNCHED);
```

**Lo que cuesta.**

- El motor no avisa de que un proceso terminó o se canceló, así que `resendSilent` reenvía el mensaje
  de reanudación a los liberados que no contestan, pero solo durante `mapping.resend-for`; pasado ese
  tiempo se dan por idos.
- Hace falta una salida humana: **descartar** (`Causes.discard`) marca el proceso `DISCARDED` y pide
  al motor que lo cancele. Una espera registrada sin el id del motor no se puede cancelar desde aquí.
- Cada relanzamiento es una instancia más en el motor; es inofensivo, pero el recorrido de una reserva
  se lee como una cadena de procesos, no uno.

Funcionalmente, en [Causas, avisos y bandeja](/integracion/causas-y-avisos/).

## Copias locales y transferencia de estado por eventos

**El problema.** El front office tiene que registrar huéspedes y enseñar avisos aunque el plano de
control, el servicio de avisos o la red no contesten (F017). Preguntar en cada pantalla lo haría
depender de todos ellos.

**Cómo está hecho.** El mensaje lleva **el estado entero**, no solo «algo cambió»: `RegistrationRuleChanged`
es la regla completa con su versión, `NoticeChanged` el aviso, `CustomerEvent` el golden record. El
front office guarda su copia, una vez por evento (inbox) y ganando la versión más alta:

```java
// systems/front-office/.../frontoffice/application/RegistrationRequirementsService.java
public boolean take(RegistrationRuleChanged rule) {
    if (rule.eventId() != null && !inbox.firstTime(CONSUMER, rule.eventId())) {
        return false;
    }
    var kept = rules.save(rule);
```

```java
// systems/front-office/.../frontoffice/infra/persistence/JdbcRegistration.java
var updated = jdbc.update("update registration_rule set version = ?, scope_key = ?, active = ?, payload = ?, "
        + "updated_at = ? where rule_id = ? and version < ?", /* … */);
```

Igual `NoticeEvents` (avisos de cliente, reserva o agencia, guardados aunque la estancia aún no haya
llegado) y `CustomerNationalities` (la nacionalidad de cada cliente según el MDM, con
`NationalityBackfill` para los que ya estaban). El modelo de lectura de las estancias es
`StayReadModel` / `JdbcStayReadModel`.

Contraste deliberado: **la reserva no viaja así**. El evento del CRS es una notificación con id y
versión, y el adaptador **relee** la reserva (ver la capa anticorrupción): así se proyecta siempre la
última, y la guarda de versión descarta las viejas.

**Lo que cuesta.** Consistencia eventual: un cambio tarda en llegar, y una copia puede quedarse vieja
mientras Kafka no entrega. Hace falta resolver el orden (la versión) y poblar las copias la primera vez
(backfill). Y es estado duplicado que alguien tiene que saber que existe al cambiar el maestro.

## Idempotencia, deduplicación y guarda de versión

**El problema.** Todo se puede repetir: el relay reenvía, el motor reentrega tareas, una persona pulsa
dos veces, una causa bloquea tres mil reservas.

**Cómo está hecho**, en capas:

- **Por id de mensaje**: el inbox (arriba). Las órdenes llevan `commandId`; en un paso del motor es el
  `taskExecutionId`, así que un paso repetido pide una sola vez.
- **Por clave de negocio**: `NotificationRequested.dedupKey` — «la misma clave es un aviso: una causa
  que bloquea tres mil reservas se anuncia una vez»; `Deliveries` comprueba `existsByDedupKey` y la
  tabla `notification` tiene un índice único sobre ella. La clave de los procesos derivada del evento,
  y la del sucesor de la del que espera.
- **Upserts por identidad externa**: la reserva se busca en Opera por el localizador del CRS, el perfil
  de interlocutor por su `CorporateId`, el cargo por su referencia `FO:<línea>`.
- **Guarda de versión**: OHIP no tiene escritura condicional, así que `upsertReservation` compara la
  versión del CRS guardada en un UDF de Opera y no escribe si ya hay una igual o más nueva:

```java
// integration/pms-integration-service/.../pmsintegration/worker/TaskHandlers.java
var written = reservations.writtenVersion(existing.get());
if (written >= r.version() || OperaReservations.cancelled(existing.get())) {
    // …
    return new Write(Outcome.STALE.name(), reservationId);
}
reservations.update(hotel, reservationId, body);
```

El leer-comparar-escribir no es atómico; lo serializa el `LOCK` del motor sobre
`hotelCode/locator` (ver [El orden y la concurrencia](/guias/procesos/#el-orden-y-la-concurrencia)).
`ChargeHandlers` devuelve también `STALE` para un cargo o anulación que Opera ya tiene.

**Lo que cuesta.** El inbox crece sin límite (no se purga) y cada consumidor tiene que acordarse de
usarlo. La guarda de versión depende de un campo de Opera que nadie más debe tocar.

## Resiliencia ante APIs externas

**El problema.** Opera y Salesforce fallan de dos maneras muy distintas: «ahora no» (timeout, 5xx,
límite) y «no» (una regla de negocio). Tratarlas igual o reintenta sin fin algo imposible, o abandona
algo que se habría arreglado solo.

**Cómo está hecho.** `OhipClient.classify` convierte cada respuesta en una de dos excepciones:

```java
// integration/pms-integration-service/.../pmsintegration/ohip/OhipClient.java
/**
 * 5xx and 429 are Opera saying "not now"; 401 after a fresh token is credentials Opera will not
 * take — both are retried, and the retrying is what raises the alarm if it lasts. Every other
 * 4xx is Opera saying "no".
 */
RuntimeException classify(HttpMethod method, String uri, RestClientResponseException e) {
    var status = e.getStatusCode();
    var detail = detail(e);
    if (status.is5xxServerError() || status.value() == 429 || status.value() == 401) {
        return new PmsTransientException("OHIP %d on %s %s: %s".formatted(status.value(), method, uri, detail), e);
    }
    return new PmsRejectedException(status.value(), errorCode(e), detail);
}
```

`PmsTransientException` se propaga, la tarea falla y el motor la reintenta sin límite.
`PmsRejectedException` se captura en el handler y se convierte en una causa `PMS_REJECTED`: el proceso
espera. Para que reintentar sin límite no sea silencioso, `RetryWatch` (envuelto en cada tarea por
`watched(...)`) avisa una vez con `RETRYING_TOO_LONG` cuando un paso lleva más del umbral fallando, y
cierra el aviso cuando vuelve a ir.

En Salesforce el recurso escaso es la cuota diaria. `SalesforceBudget` pausa **todas** las llamadas al
primer `REQUEST_LIMIT_EXCEEDED` (cinco minutos, duplicando hasta una hora), deja que solo una llamada
compruebe si vuelve, y lee el uso de la cabecera `Sforce-Limit-Info` sin gastar una llamada en
preguntarlo. `Backoff` hace lo mismo para los trabajos programados que fallan:

```java
// integration/customer-mdm-service/.../mdm/salesforce/Backoff.java
public synchronized void failed() {
    failures++;
    next = clock.instant().plus(wait);
    wait = wait.multipliedBy(2).compareTo(longest) > 0 ? longest : wait.multipliedBy(2);
}
```

El consumo se publica (`ApiUsage`, `contracts-integration`) y se ve en el plano de control: ver
[Consumo de APIs externas](/operacion/consumo-apis-externas/).

**Lo que cuesta.** La clasificación es una apuesta: un 4xx que Opera devuelve por un problema pasajero
se tratará como rechazo (y esperará a una persona). `RetryWatch` vive en memoria: un reinicio retrasa
la alerta, nunca pierde el trabajo. La pausa de Salesforce detiene también lo que no tenía prisa.

## Puertos y adaptadores, donde los hay

**El problema.** Los sistemas que la PoC sustituye (`booking`, `erp`) y el front office tienen dominio
propio, y no debe depender de Kafka, JDBC ni Mateu.

**Cómo está hecho.** Esos tres separan `domain/`, `application/` e `infra/`. `booking` y `erp` lo
llevan más lejos: casos de uso y **puertos de salida** en `application/usecases` y `application/out`,
e `infra/in` (REST, MCP, UI, consumidores) e `infra/out` (los adaptadores). El inbox de `booking` es
un ejemplo mínimo: el puerto es suyo, el adaptador lo enchufa a la librería compartida:

```java
// systems/crs/booking/.../booking/application/out/inbox/Inbox.java
public interface Inbox {
    /** @return true the first time this consumer sees the message, false on every repetition */
    boolean firstTime(String consumer, String messageId);
}

// systems/crs/booking/.../booking/infra/out/inbox/SharedInbox.java
@Bean
Inbox inbox(io.mateu.ecdemo1.messaging.Inbox inbox) {
    return inbox::firstTime;
}
```

En el front office, `RegistrationRuleCopies` es un puerto del dominio y `JdbcRegistration` su
adaptador.

**Lo que cuesta.** Más tipos por cada dependencia. Por eso los servicios de integración y del plano de
control **no** son hexagonales: se organizan por funcionalidad (`causes/`, `commands/`, `outbox/`,
`worker/`…), porque son en sí mismos adaptadores con poco dominio. Ninguna regla automática (ArchUnit)
vigila las dependencias entre capas: es una convención. La estructura de cada módulo está en
[Estructura del proyecto](/diseno/estructura/).

## El API del servicio como herramientas MCP

**El problema.** Los agentes tienen que poder operar (crear una reserva, proponer un mapeado) sin un
camino paralelo que salte las reglas del servicio.

**Cómo está hecho.** Cada servicio que tiene operativa trae un `infra/in/mcp` (o `mcp/`) con un bean
de `@Tool` que llama a **los mismos casos de uso** que la REST y la UI: `BookingMcpTools` en
`booking`, `FrontDeskMcpTools` en el front office, y los de `mapping-service` e `integrations-service`.

```java
// systems/crs/booking/.../booking/infra/in/mcp/BookingMcpTools.java
@Tool(description = "Create a booking in a hotel. Returns its id. It is confirmed as it is made")
public String createBooking(@ToolParam(description = "CRS hotel code, e.g. PMI01") String hotelCode,
                            BookingRequest booking) {
    return attempt(() -> "Booking created with id "
            + createBookingUseCase.handle(new CreateBookingCommand(hotelCode, booking)));
}
```

Lo que el agente hace queda auditado igual que lo que hace una persona. Para APIs de terceros que no
tienen MCP, `api-mcp` las ofrece como herramientas (ver [API MCP](/ia/api-mcp/)).

**Lo que cuesta.** La descripción del `@Tool` es parte del contrato y el modelo la lee. Spring AI
descarta en silencio una herramienta que devuelve `Object`, por eso `getBooking` lanza en vez de
devolver un error. El MCP no ve la identidad de quien pregunta: lo que exige una persona (aprobar un
mapeado) pide su nombre y confirmación explícita. Los adaptadores de la ACL no tienen MCP.

## El gateway como frontera

**El problema.** Los backends no autentican, y hay cosas que solo sabe quien recibe la petición: desde
qué consola llega.

**Cómo está hecho.** `consoles/gateway` valida el token antes de que ningún backend vea nada
(ver [Consolas, gateway y seguridad](/guias/consolas-y-seguridad/#el-gateway-es-la-frontera)) y
**estampa el canal**: un solo `ia-agent` sirve a todas las consolas, y la ruta de cada host le pone de
qué consola viene el prompt.

```yaml
# consoles/gateway/src/main/resources/application.yaml
- id: control-plane-agent
  uri: ${IA_AGENT_URL:http://ia-agent:8095}
  predicates:
    - Host=${CONTROL_HOST:console.ec1.mateu.io},${RW_CONTROL_HOST:rw-console.ec1.mateu.io}
    - Path=/ai/**
  filters:
    - SetRequestHeader=X-Agent-Channel, control-plane
    - SetRequestHeader=X-Default-Agent, ${CONTROL_PLANE_AGENT:control-plane-agent}
```

`SetRequestHeader` **sustituye**: lo que mande el navegador con ese nombre nunca llega al agente, así
que las rutas del plano de control de IA pueden decidir por canal (`IaAgentController.CHANNEL_HEADER`).

**Lo que cuesta.** La seguridad descansa en una sola pieza y en una lista de hosts: un host de consola
que falte en `SecurityConfig` no falla cerrado. Lo que no debe salir (`/internal/**`, `api-mcp`, el
webhook de alertas) se protege no teniendo ruta, no con autenticación propia.

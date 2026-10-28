---
title: Causas, avisos y bandeja
description: Por qué un proceso espera en vez de fallar, quién se entera, por dónde, y cómo el agente de mapeado propone lo que falta.
---

## Causas en vez de errores

Cuando un proceso no puede seguir por algo que no se arregla reintentando, registra una **causa** en
`mapping-service` y espera. Una causa tiene clave `TIPO:hotel:…` (sin barras, para que viaje entera
en una ruta) y sabe cuántos procesos esperan detrás y desde cuándo; **se comparte entre procesos**:
seis reservas que esperan el mismo código esperan la misma causa y se liberan juntas al resolverla.

Los tipos (`CauseType`, `contracts-mapping`):

| Causa | Qué falta | Quién la resuelve |
| :---- | :-------- | :---------------- |
| `MISSING_MAPPING` | Una equivalencia de código (p. ej. `MISSING_MAPPING:MRU01:RATE_PLAN:EMPLEADOS-27`) | Una persona, aprobándola en el diccionario |
| `MISSING_PARTNER` | El interlocutor aún no está en Opera | `proyectar-interlocutor`, al terminar |
| `NOT_YET_PROJECTED` | La reserva aún no está en Opera (una cancelación que llegó antes) | `resolve-projection`, cuando llega |
| `PMS_REJECTED` | Opera rechazó la escritura de forma determinista (p. ej. sin disponibilidad) | Un cambio en el CRS que Opera acepte, una persona, o el [on request](#on-request-cuando-opera-no-tiene-habitación) |
| `INTEGRATION_INACTIVE` | El hotel no tiene integración activa | La activación de la integración |

Un fallo **transitorio** (timeout, 5xx) no es una causa: el motor reintenta el paso. Si dura más que
`RETRY_ALERT_AFTER` (10 min), el conector avisa.

Las causas abiertas se ven en *Mapping → Causes* del plano de control, y cada una en la bandeja de
quien la resuelve.

### Un rechazo de Opera, una causa para todas las reservas

Cuando Opera rechaza **escribir una reserva** (`upsert-reservation`) con un código de error, la causa
no es de la reserva sino del incidente: `PMS_REJECTED:<hotel>:upsert-reservation:<código>`, p. ej.
`PMS_REJECTED:MRU01:upsert-reservation:RSV00138` (no quedan habitaciones del tipo). Todas las reservas
que Opera rechaza igual esperan en esa misma causa: tres reservas sin cupo son **una** causa con tres
procesos detrás, no tres. Un rechazo sin código, y los de recepción y del folio (check-in, cargos,
cobros: cada uno se arregla en su estancia), siguen siendo de la reserva
(`PMS_REJECTED:<hotel>:<localizador>:<paso>`).

Como la clave compartida no nombra la reserva:

- cuando una versión más nueva de la reserva entra en Opera, o el paso pasa, `pms-integration-service`
  llama a `POST /causes/release-refusal?hotelCode=…&locator=…&step=…`: resuelve el rechazo propio de la
  reserva y saca **solo a sus procesos** de las causas compartidas de ese paso en ese hotel; la causa
  compartida se resuelve cuando ya no espera nadie;
- `GET /causes?subject=<localizador>` devuelve las causas en que esperan o esperaron los procesos de
  esa reserva (lo usa el recorrido de negocio de `journey-service`), y `GET /causes/waiting?key=…`, los
  procesos que esperan ahora en una causa, con su hotel y su localizador.

### On request: cuando Opera no tiene habitación

Un rechazo de Opera a escribir una reserva se puede **dejar entrar «on request»**: en la causa
(*Mapping → Causes*), **«Dejar entrar on-request»**, con confirmación. Desde ese momento la causa dice
*On request* —en la lista y en la ficha, con desde cuándo y quién— y **nada se escribe en Opera
todavía**: cada reserva que espera entra sola **cuando Opera tenga sitio para su estancia**.
**«Quitar on-request»** lo retira; las reservas vuelven a esperar a una persona o a un cambio del CRS.
Solo los rechazos de `upsert-reservation` admiten on request (ni recepción ni el folio).

- **Quién lo hace**: `OnRequestWatcher`, en `pms-integration-service`, cada 2 min
  (`pms-integration.on-request-check`). Por cada causa on request abierta busca la **primera** reserva
  que espera y a la que Opera ya vendería sus habitaciones (traduce sus tipos de habitación del CRS por
  el diccionario y pregunta por su estancia); la saca del rechazo (`release-refusal`) y su proceso la
  escribe. **Una por causa y vuelta**, para que dos reservas que quieren la última habitación no entren
  las dos.
- **Cómo sabe si hay sitio**: el tenant no tiene API de inventario, así que `OperaAvailability` usa la
  búsqueda de disponibilidad pidiendo 1…5 habitaciones hasta que un tipo desaparece; el recuento llega
  a «5 o más». Ver [De CRS a Opera](/integracion/crs-a-opera/#el-tenant-real).
- **Dura**: on request es la respuesta de una persona a ese rechazo en ese hotel; sigue aunque la causa
  se resuelva y se vuelva a abrir, hasta que alguien lo quite.

Por REST: `POST /causes/on-request?key=…&on=true|false&by=…`; `GET /causes` lleva `onRequestSince` y
`onRequestBy`.

### Descartar un proceso que espera

Resolver la causa no es la única salida: una persona puede **renunciar** a un proceso que espera
(F012) cuando lo que iba a hacer ya no importa — una reserva de prueba, una cancelación de una reserva
que nunca llegó a Opera. En la causa, *Descartar* en la fila del proceso, o *Descartar…* en la barra
para elegir uno o todos los de la causa. En la consola Redwood las filas no llevan acción: se descarta
desde *Descartar…* de la barra. El diálogo dice qué significa antes de hacerlo y pide el
**motivo**:

- el proceso queda `DISCARDED` con quién, cuándo y por qué, y no se reanuda aunque la causa se resuelva
  después, ni se le reenvía el mensaje de reanudación;
- **el motor lo cancela**: `mapping-service` publica por su outbox `ProcessCancellationRequested` (el
  mismo comando que el *Cancel* de *Admin → Workflow → Processes* de la consola de datos) con el id del motor del proceso, que guarda
  cuando el proceso registra la espera. Una espera registrada sin ese id (anteriores a 0.37.0, o por
  REST sin `engineProcessId`) no se puede cancelar desde aquí: el diálogo lo dice y enlaza a
  *Admin → Workflow → Processes*, en la consola de datos, para cancelarlo a mano;
- si ya no espera nadie en la causa, ofrece **resolverla**;
- queda auditado («Discard process», con el motivo), hecho o rechazado — un proceso que ya se reanudó
  no se puede descartar.

Por REST: `POST /causes/waiters/{processKey}/discard?reason=…&by=…`, o
`POST /causes/waiters/discard?processKey=…&reason=…&by=…` para las claves con `/`. Devuelve si se pidió
la cancelación al motor y qué causas abiertas se quedan sin nadie esperando. No hay herramienta MCP: el
agente de mapeado no descarta procesos.

Un proceso **liberado** que no contesta recibe el mensaje de reanudación otra vez cada
`mapping.resend-after` (30 s), pero solo durante `mapping.resend-for` (6 h) desde que se liberó: el
motor no avisa a los servicios de que un proceso se canceló o terminó, así que pasado ese tiempo se da
por ido. Sigue visible en su causa como `RELEASED`, para descartarlo.

## El agente de mapeado propone, una persona aprueba

Cuando falta una equivalencia, el diccionario ofrece **«Ask the agent»** (y el alta lo pide sola al
llegar a la puerta de mapeado). El agente tarda de segundos a minutos por hotel, así que el botón abre
un **diálogo con una barra de progreso**: un tramo por hotel, los segundos que lleva pensando y, al
final, su respuesta. Varios hoteles se preguntan uno tras otro. Al cerrar el diálogo, el listado se
vuelve a buscar con los mismos filtros y la misma página. El agente usa el MCP de `mapping-service`: lee las carencias y los dos
catálogos y **registra una propuesta** con su confianza y su porqué. La propuesta queda en estado
*propuesta*: nada entra en vigor sin una persona. Aprobar por MCP exige el nombre del usuario y
confirmación explícita, porque el MCP no ve la identidad de quien pregunta. **El LLM nunca está en el
camino del dato.**

Cada aprobación crea una versión nueva de la equivalencia, con autor y fecha. Una equivocada se corrige
aprobando otra versión; la que nunca debió existir se **retira**.

## Los avisos

La integración decide **qué** se notifica; `communication-service` decide **a quién** y **por dónde**.
Los tipos (`NotificationType`):

| Aviso | Cuándo |
| :---- | :----- |
| `CAUSE_OPENED` | Se abre una causa |
| `PROPOSAL_READY` | El agente dejó una propuesta de mapeado para revisar |
| `RETRYING_TOO_LONG` | Un paso que escribe en Opera lleva más del umbral fallando |
| `PMS_REJECTED` | Opera rechaza una escritura |
| `INTEGRATION_NEEDS_ATTENTION` | Una puerta del alta necesita a alguien |
| `CHECK_IN_INCOMPLETE` | Un check-in forzado sigue sin el documento de un huésped pasadas 24 h de la llegada (parte de viajeros); lo manda el front office a recepción |
| `PLATFORM_ALERT` | Salta una alerta de Prometheus de la plataforma (una API externa degradada, un servicio caído) |
| `PLATFORM_ALERT_CRITICAL` | Lo mismo, con severidad crítica: Opera o Salesforce sin responder, un pod caído |

Los avisos de la integración llegan por Kafka (`notifications`) y se cierran por Kafka
(`notification-resolutions`): un aviso se va de todas las bandejas cuando lo que lo causó se resuelve.
Las alertas de la plataforma no pasan por Kafka: Alertmanager las manda al webhook
`POST /alerts/alertmanager` de `communication-service` (solo dentro del clúster) y, cuando la alerta
se resuelve, la cierra igual en todas las bandejas. Cuáles hay y a quién llegan, en
[Observabilidad](/operacion/observabilidad/).

### Quién se entera, y por dónde

Lo decide **una sola tabla**, *Notifications → Recipients* en el plano de control. Cada destinatario dice:

- **a quién**: usuarios de Keycloak y/o roles, o una dirección de email;
- **qué**: tipos de aviso (ninguno, todos), si también las tareas del motor de formularios, y un hotel;
- **por dónde**: la **bandeja** de sus personas, el **Web Push** de sus navegadores en las consolas o
  en recepción (*Browser at the front desk*), **email** (por el relay `postfix`) o los **espacios de
  Google Chat** que nombra.

Cada aviso llega una vez por persona, navegador, dirección y espacio. Con la tabla vacía se siembran
tres: los administradores de la integración (rol `ai-admin`, bandeja y push, todos los tipos), Google
Chat, y los urgentes por email (rechazos de Opera y reintentos que no acaban, a `DEFAULT_RECIPIENT`).

### La bandeja

«Inbox (n)» en la cabecera de las cuatro consolas es la entrada a la bandeja; con algo urgente dice
«Inbox (n, m urgent)» y la campana se pone en rojo. La cuenta va en una pastilla (roja con algo urgente,
verde azulado si no) que se ve también sobre la cabecera oscura de Redwood. `/_inbox` escrito en la barra de direcciones de
cualquiera de las cuatro consolas también lleva a ella (el gateway lo redirige a `/inbox/pending`).
Tiene los avisos que son para mí —por nombre o por uno de mis roles— y las **tareas del motor de
formularios** (una tarea es un aviso más, en la bandeja de los roles que pide su formulario). *Open* lleva a la pantalla
que lo resuelve y lo marca como **visto**. Los enlaces de los avisos van a las consolas **Redwood**
(`CONSOLE_URL`, `DATA_CONSOLE_URL`); un enlace a la misma consola se abre en la que se está usando
(Vaadin o Redwood), y desde una consola Redwood un enlace a la Vaadin de la otra consola —los avisos
antiguos— se abre en su gemela Redwood; visto es de cada persona y no resuelve nada: la fila sigue
hasta que se resuelve, y entonces se va sola.

El Web Push se activa en cada navegador y en cada consola (son orígenes distintos) desde el menú de
usuario, que también manda una prueba. El navegador del front office es un canal aparte, para que al
mostrador solo le llegue lo que alguien decida que es suyo.

## Auditoría

`audit-service` (HLA F016) materializa, inmutables, las acciones con consecuencias —hechas o
rechazadas, por consola, REST o agente— con quién, cuándo, parámetros y respuesta. Llegan por el outbox
de cada servicio al topic `audit` (`AuditedAction`). La pantalla, en *Audit* (`/_audit`), busca por
texto libre y filtra por fecha, hotel, usuario, acción, servicio y resultado. Solo lectura.

Quién audita qué:

| Servicio | Qué | Dónde se audita | Quién |
|---|---|---|---|
| `integrations-service`, `mapping-service` | las acciones `@Audited` (activar, pausar, aprobar un mapeado…) | en el servicio (`AuditScope`, `AuditAspect`) | el usuario de la consola, o el agente por él |
| `front-office` | toda operación de recepción sobre una estancia (tabla en [Un sistema propio](/front-office/sistema-propio/#quién-hizo-qué-con-la-reserva)) | en el servicio de aplicación que la hace (`StayAudit`) | el usuario del token (`DeskUser`), o `reception-agent (persona)` |
| `booking` (el CRS) | crear, modificar, confirmar, cancelar, no-show, cobro y borrado de una reserva | en el caso de uso (`BookingAudit`) | el usuario del token (o `X-User-Name`), `console-agent (persona)` por las herramientas MCP, `motor` para el no-show del motor, `api` si nadie se identifica |

Cada acción sobre una reserva lleva en sus parámetros la estancia (`stayId`) y el localizador del CRS
(`locator`); `audit-service` los guarda como columnas indexadas. Con eso sirve, **solo dentro del
clúster**, el historial de una reserva: `GET /audit?stayId=…&locator=…&limit=…`, lo más reciente
primero (el gateway no enruta `/audit`; la pantalla `/_audit` sigue tras `ai-admin`). Lo leen el
front office («Historial» de la reserva, variable `AUDIT_URL`) y la ficha de la reserva en el CRS
(«History», `AUDIT_URL`).

Una operación hecha se audita **en su misma transacción** (o las dos, o ninguna); una rechazada o
fallida, **aparte**, porque la suya se deshace. Auditar nunca rompe la operación: si no se puede
escribir, se registra en el log. En los parámetros, lo que parece una tarjeta, un secreto o un token se
enmascara, y del kárdex se dice qué campos cambiaron, no sus valores.

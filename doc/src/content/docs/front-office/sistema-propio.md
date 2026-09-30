---
title: Un sistema propio
description: El front office del hotel — qué hace, de dónde toma cada dato y por qué es un sistema con identidad propia y no una fachada de Opera.
---

`systems/front-office` es la recepción del hotel (MRU01 ↔ XMAR en ec1), en `front.ec1.mateu.io`, detrás
de Keycloak. Llegó de la demo de Mateu (hito H13) y desde el H14 **consume el PMS**: sus reservas son
las de Opera, llegadas por la [integración pms-fo](/integracion/pms-a-front-office/). Es Spring Boot 4
con Mateu, pintado por Redwood.

## Qué hace

| Pantalla | Qué hace |
| :------- | :------- |
| *Bienvenida* | La página de inicio |
| *Reservas* | Las estancias: llegadas, en casa, salidas; la ficha con huéspedes, estancia y enlaces a los demás sistemas |
| Check-in | Asistente: identidad (con el escaneo del documento), habitación, extras, confirmación |
| Check-out y folios | Cierre de la estancia y sus cargos |
| *＋ Walk-in* | Asistente para vender en el mostrador: habitación, tarifa y régimen **del CRS**, precio pedido al CRS, titular |
| *Automatizaciones* | Solo para el personal |
| Kárdex | Los datos del huésped, con los cambios pendientes de Salesforce |

La cabecera tiene el **agente de recepción** (`reception-agent`), cuyo único servidor MCP es el del
front office. Lo atiende el mismo `ia-agent` que las consolas: el gateway manda el `/ai/**` de
`front.ec1` marcado con el canal `front-office` y `reception-agent` como agente por defecto. Y un selector **Modo** (Staff / Cliente) que
proyecta las pantallas para cada audiencia.

## De dónde toma cada dato

| Dato | Viene de | Por dónde |
| :--- | :------- | :-------- |
| Reservas y estancias | Opera | `front-office-commands` (`write-stay`), ordenadas por la versión de Opera |
| Catálogo (tipos, tarifas, paquetes, habitaciones) | Opera | `front-office-commands` (`replace-catalogue`) |
| El cliente (kárdex) | El MDM, que lo tiene de Salesforce | Topic `customers` |
| Avisos de recepción (de cliente, de reserva, de agencia) | El servicio de avisos; los de cliente, de Salesforce (su maestro) por el MDM | Topic `notices` |
| Precio y alta de un walk-in | El CRS | HTTP a `crs-integration-service`: la recepción necesita el localizador ya |
| Cómo tomó el PMS lo que hizo recepción, y la factura del check-out | Opera | `front-office-commands` (`record-reception`) |
| Habitaciones que ofrecer en el check-in, con su estado | Opera (catálogo y housekeeping) | Catálogo por `replace-catalogue`; el estado, HTTP a `pms-integration-service` (`GET /front-office/rooms`): la pantalla lo necesita ya |

Y lo que el front office **manda**, por su outbox:

- un **cambio de datos del cliente**, a Salesforce vía MDM (`customer-commands`, `propose-change`);
- un **documento escaneado**, al MDM (`customer-commands`, `record-scanned-identity`);
- lo que hizo **recepción** — check-in, check-out, no show —, al PMS (`front-office-events`), que lo
  registra en Opera; el no show sigue de Opera al CRS, que aplica su cargo;
- cada **cargo** que recepción pone en el folio —extras del check-in, late check-out, consumos— y cada
  anulación, al folio de Opera (`charge-posted`, `charge-voided`): el alojamiento no, que lo cobra Opera.

## El estado de Opera y la factura

La estancia dice dónde está en el PMS, en «En otros sistemas»: «Opera: pendiente — check-in enviado»
al hacerlo, y luego lo que conteste el PMS — «Opera: en casa · hab. 205», «Opera: salida registrada ·
factura XMAR377», «Opera: rechazado (check-in) — motivo». Si Opera asigna otra habitación, esa es la de
la estancia. Un walk-in que aún no está ni en el CRS ni en Opera espera: su check-in sube cuando su
reserva vuelve de Opera.

Una estancia cerrada tiene **«Abrir factura»**: el documento que Opera emitió en el check-out si lo dio
(el PMS es el maestro del folio); si no, una **«Factura proforma (front office)»** hecha con el folio del
front office, que lo dice en la página, con **los dos totales** —el del folio del front office y el de la
factura de Opera— y si coinciden. Como los cargos de recepción están en el folio de Opera, coinciden;
el alojamiento del folio del front office es el total de la estancia que da Opera **con los paquetes que
Opera postea aparte** (en XMAR, el desayuno BRKFST, 40 MUR la noche, fuera de la tarifa). Si aun así
difieren, la proforma dice por qué suele ser: el alojamiento (Opera factura sus noches con su tarifa; en
una salida anticipada, solo las pasadas), un cargo que Opera rechazó o que llegó tarde, o cargos
anteriores a subirlos a Opera.

**Gestionar folio** lista los cargos de recepción con dónde está cada uno en Opera («Opera: en el folio ·
88731245», «pendiente», «rechazado — motivo») y **«Anular»**: la línea queda en el folio, anulada y sin
contar, y Opera anula su posteo.

## Habitaciones listas

El paso de habitación del check-in ofrece las habitaciones de Opera del tipo de la estancia, **primero
las listas**: libres y con el estado de housekeeping que la propiedad exige para asignarlas
(`frontoffice.pms-ready-statuses`; XMAR, solo **inspeccionadas** — una «limpia, sin inspeccionar» la
rechaza con FOF00081). Las libres que aún no están listas se ven en gris con el motivo («No lista —
Limpia, sin inspeccionar en Opera») y se pueden elegir a propósito (Opera puede rechazarla); las ocupadas
o fuera de servicio no se pueden elegir. En la estancia que llega, un aviso dice si la habitación asignada
está **lista en Opera** o por qué no, con **«Comprobar»** para preguntarlo otra vez (lo de Opera se
guarda 20 s: una pantalla se repinta a cada clic). La sirve el front office (`/invoices/{estancia}`), nunca un
enlace a Opera, con un enlace firmado que caduca (una pestaña nueva no lleva el token).

## Avisos de recepción, al entrar y al salir

Los avisos (ver [Avisos de recepción](/integracion/avisos/)) llegan por el topic `notices` y se guardan
por su asunto: los del titular y de los acompañantes que son clientes de la cadena, los de la reserva y
los de la agencia que la vendió — del hotel o de la cadena.

- **Preparando la llegada.** La reserva enseña arriba los de la reserva y su agencia para antes de la
  llegada y para el check-in; los de cada huésped van bajo su fila.
- **Durante la estancia.** El panel de la estancia enseña arriba los avisos para la estancia.
- **Check-in.** El asistente empieza por un paso *Avisos* si hay alguno para el check-in (activo, vigente
  en las fechas de la estancia), y el carril de huéspedes de la reserva los lleva bajo cada pax. Uno
  **Bloqueante** pide marcar **«He leído el aviso»**; sin eso, `CheckInService` rechaza el check-in —
  venga del mostrador o del agente — y lo audita. Lo leído cubre los avisos de ese momento: uno nuevo
  hay que volver a leerlo. La reserva con un bloqueante sin leer no hace el check-in directo: abre el
  asistente.
- **Check-out.** Si algún huésped tiene un cambio de kárdex **rechazado** por Salesforce (campo, lo
  propuesto, lo que se queda, el motivo) o **pendiente** («la factura saldrá con el dato anterior»), o
  avisos de check-out, el modo check-out los muestra arriba con **«Entendido»**; sin él,
  `CheckOutService` rechaza el cobro y cierre, y lo audita.
- **El agente de recepción** tiene `getNotices` y los recibe en `getStay`; `prepareCheckIn` y
  `prepareCheckOut` los ponen en el resumen, y confirmar es declarar «He leído el aviso» / «Entendido»
  (auditado como `reception-agent (persona)`).

Todo reconocimiento y todo rechazo va al topic `audit` (`Read check-in notices`, `Check-in refused:
unread notices`, `Read check-out warnings`, `Check-out refused: unread warnings`).

## Quién hizo qué con la reserva

Toda operación de recepción con consecuencias sobre una estancia se audita **en el servicio de
aplicación que la hace** (`StayAudit`), no en la pantalla ni en la herramienta del agente: cuenta igual
la haga una persona en el mostrador o el agente de recepción por ella.

| Operación | Acción auditada | Servicio |
|---|---|---|
| Check-in (y forzado) | `Check-in` (+ `Forced check-in` con el motivo y lo que faltaba) | `CheckInService` |
| Check-out | `Check-out` | `CheckOutService` |
| Cambio de habitación | `Room change` (de, a) | `RoomChangeService` |
| Cargo al folio / anulación | `Charge posted` / `Charge voided` (código, concepto, importe) | `FolioService` |
| Late check-out | `Late check-out` | `FolioService` |
| Cobro / preautorización | `Payment taken` (método e importe; nunca datos de tarjeta) | `CheckInService` |
| Llave, wifi, firma | `Key encoded`, `Wifi created`, `Registration signed` | `CheckInService` |
| Ancillaries | `Add-on added` / `Add-on removed`, `Ancillaries chosen`, `Ancillaries closed` | `CheckInService` |
| Kárdex | `Kardex edit` (qué campos, no sus valores), `Contact updated`, `Document scanned` | `KardexService` |
| No-show de un pax | `No show` | `NoShowService` |
| Walk-in | `Walk-in` (con lo que contestó el CRS) | `WalkInService` |
| Incidencias | `Incident reported`, `Incident resolved` | `IncidentService` |

- **Quién.** El usuario del token de la petición (`DeskUser`). Cuando actúa el agente, las
  operaciones confirmadas se ejecutan como `reception-agent (persona)`, y `PendingConfirmations` solo
  audita por su cuenta lo que el servicio no auditó: **un registro por operación**.
- **Qué lleva.** La estancia, el localizador del CRS, el hotel, los parámetros (tarjetas, secretos y
  tokens enmascarados), si se hizo y lo que contestó o por qué no.
- **Fallos.** Se auditan también, aparte de la transacción que se deshace. Un rechazo que ya audita
  quien lo decide (avisos sin leer, check-in incompleto) no se audita dos veces.
- **Historial.** La ficha de la reserva tiene una sección **Historial**: quién hizo qué y cuándo, lo más
  reciente primero — la recepción, el agente y lo que el CRS hizo con su reserva —, leído de
  `audit-service` (`GET /audit`, dentro del clúster; `AUDIT_URL`). Si no contesta, lo dice y la página
  sigue.

## Por qué un sistema propio y no una fachada de Opera

El front office tiene datos y decisiones que no son de Opera:

- **Es un canal de venta.** El walk-in se vende en el mostrador, pero la reserva es del **CRS**: el
  front office abre la estancia al momento con su referencia `FO-XXXXXX`, pide la reserva al CRS al
  precio dado y, cuando baja de Opera, la reconoce y la escribe sobre la misma estancia.
- **Captura la identidad del cliente.** El escaneo del documento es dato de confianza que va al MDM y a
  Salesforce; un cambio de datos se guarda al momento y queda **pendiente de Salesforce** hasta que el
  maestro decide. Nada de eso es de Opera.
- **Lee con sus propias palabras.** Guarda el catálogo del PMS y lee las estancias con él («Pensión
  Desayuno Adulto», no `BKF`), y conserva lo que Opera no tiene (los acompañantes).
- **Sigue funcionando aunque Opera no conteste**: tiene su base de datos y su outbox; las órdenes salen
  cuando pueden y sus consumidores deduplican.

Y los requisitos de negocio del front office de destino van más allá de lo que la PoC construye: el
**check-in online y desde la app** (que no pasan por ninguna pantalla de Opera), y **pagos y
comunicaciones** por los servicios de Riu que ya se encargan de ellos, no necesariamente por Opera.

:::note[El no show]
En la PoC la recepción marca un no show, el PMS lo anota (`registrar-no-show-pms`) y el CRS lo cobra
(`registrar-no-show`). En producción lo marca
el **Night Audit de Opera** y el front office solo lo refleja: no es un argumento para el front office
propio.
:::

## Detalles técnicos

- **Jackson 3.** Es Spring Boot 4: `RestClient` lee con Jackson 3 (`tools.jackson`), así que una
  respuesta no se puede leer en un `com.fasterxml…JsonNode` (falla en tiempo de ejecución): se lee en
  `Map` o en un record.
- **Su API no es pública.** El gateway devuelve 404 a `/api/**` y al MCP en `front.ec1`; se usan solo
  desde dentro del clúster.
- Configuración: `FRONT_OFFICE_HOTEL=MRU01`, `FRONT_OFFICE_PMS_HOTEL=XMAR`, `CRS_INTEGRATION_URL`,
  `MDM_URL`.

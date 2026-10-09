---
title: Historial de clientes y Riu Class
description: customer-history guarda las estancias de cada cliente en la cadena; loyalty es una demo del programa Riu Class (nivel y puntos). Los dos se alimentan de las estancias cerradas del front office.
---

Dos servicios del plano de datos, los dos por el **código de cliente del MDM** (`C-…`) y los dos
alimentados por la estancia cerrada que publica el front office al hacer el check-out (`StayClosed`, en
`front-office-events`). Los usa el [reconocimiento en el check-in](/front-office/reconocer-al-cliente/).

**No usamos Data 360**: el histórico es un servicio propio con su base de datos. Es la primera pieza de lo
que en el diseño llamamos *vista 360*.

## La estancia cerrada: `StayClosed`

Se publica al hacer el check-out, en la misma transacción, por el outbox, **también si la estancia no
está vinculada al PMS** (el historial es de la cadena, no del PMS). Trae todo lo que necesita el histórico,
sin depender del orden de los eventos anteriores (es el cierre de estancia, CM-R13 del HLA):

- el hotel, la estancia, el localizador del CRS y la reserva del PMS (estos dos, si los hay);
- llegada, salida y noches; habitación, tipo y régimen;
- los huéspedes, el titular primero, con el id que les da el front office (el código del MDM si lo
  tienen); los huecos sin registrar (`pax-2`…) no van;
- los consumos por tipo —extras (`ADD_ON`), late check-out y consumos—, sin las líneas anuladas ni el
  alojamiento, su total y la moneda.

integrations-service la ignora: el PMS ya supo del check-out por `GuestCheckedOut`.

## customer-history

`systems/customer-history`, puerto 8133, base de datos `customer_history`, pantallas en `/_history`
(**Historial de clientes → Buscar**, para la central).

- **Guarda** una fila por estancia y cliente (`customer_stay`): los huéspedes con código `C-…`; los demás
  (`opera-…`, `wi-…`) no se pueden atar a un cliente. Es idempotente por estancia y cliente.
- **Las fusiones del MDM** (`CustomersMerged`, en `customers`) se guardan como alias (`customer_alias`),
  sin reescribir filas: una consulta por el superviviente incluye las estancias de los códigos absorbidos
  —provisionales y temporales—, y una por un código absorbido contesta con su superviviente.
- **API** (dentro del clúster, sin gateway):
  - `GET /customers/{code}/summary`, el resumen para el mostrador: estancias y noches, primera y última,
    las 3 últimas (hotel, fechas, habitación y tipo), hoteles distintos y el más repetido, y el gasto en
    recepción. Siempre 200: un cliente sin estancias da ceros. Con índices por código, en milisegundos.
  - `GET /customers/{code}/stays?page&size`, el detalle, consumos incluidos.
  - `POST /demo/stays` y `DELETE /demo/stays/{code}`: las estancias de demo (origen `DEMO`, idempotentes).
- **MCP**: `getCustomerHistory(code)`.
- **El gasto con varias monedas** suma solo la más frecuente, sin convertir: sumar rupias y euros no
  diría nada. Las estancias de demo van en MUR, como MRU01.

## loyalty (Riu Class, demo)

`systems/loyalty`, puerto 8134, base de datos `loyalty`, pantallas en `/_loyalty` (**Riu Class → Socios,
Acumulaciones**). Hace de servicio de fidelización de la cadena, que la PoC no tiene: cuando exista el real,
el front office apunta su `LoyaltyStatus` a él y este sobra.

- **Socios** (`member`): número (`RC…`), código de cliente, nivel (`SILVER`, `GOLD`, `PLATINUM`), puntos y
  fecha de alta.
- **Acumula puntos** de cada `StayClosed`: **100 por noche al titular, 50 a cada acompañante**, una vez por
  estancia y socio (`accrual`). El nivel sube con los puntos (GOLD desde 10.000, PLATINUM desde 40.000) y
  nunca baja por ellos; uno puesto a mano se respeta.
- **Las fusiones del MDM** mueven el socio al cliente superviviente.
- **API** (dentro del clúster): `GET /members/{número}`, `GET /members?customerCode=`, `PUT /members/{número}`
  (la siembra de demo y las altas).
- **MCP**: `getMember`, `findMemberByCustomer`, `listMembers`, `getAccruals`.
- En las pantallas se dan de alta socios y se ajustan nivel y puntos (es una demo: el ajuste no deja rastro).

El número Riu Class también está en el MDM, como referencia cruzada `RIU_CLASS` del cliente: es por lo que
el front office busca a un cliente cuando recepción le pregunta su número.

## Reset

Los dos se resetean con la demo (`reset@1` en `customer-history-tasks` y `loyalty-tasks`; `zero.sh` vacía
sus tablas). Los socios y las estancias de demo se vuelven a sembrar con
`deploy/demo/demo-prep.sh seed known-customers`.

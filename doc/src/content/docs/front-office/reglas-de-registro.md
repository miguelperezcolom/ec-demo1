---
title: Reglas de registro del kárdex
description: Qué datos del huésped exige la ley de cada destino al registrarlo — dónde se definen (el plano de control), cómo llegan al front office y cómo las aplica en el check-in.
---

Cada destino exige por ley unos datos de cada huésped al registrarlo: en España, el registro de
viajeros; en otros países, otros. Qué campos son obligatorios **depende de la nacionalidad del
huésped, de su edad y del hotel** (normalmente del país del hotel, a veces del hotel concreto). El AF
PMS-CRM lo dejaba pendiente («PDTE! Configuración de campos de Kardex… revisar si se realiza dentro
de OperaPMS o en la extensión funcional Riu Front Office»): OPERA Cloud no tiene una configuración
estándar para esto — Page Composer puede hacer obligatorio un campo con una condición, pero solo en
sus pantallas, no en lo que entra por OHIP —, así que en la PoC vive **fuera de Opera**.

## Dónde viven y por qué

- **Se definen en el plano de control**: el servicio `registration-rules`
  (`control-plane/registration-rules`), con su pantalla **Registro** en la consola de control
  (`console.ec1.mateu.io`, `rw-console.ec1.mateu.io`, detrás de `ai-admin`). Son **gobierno**, no
  operación: las mantiene cumplimiento o la central, valen para todos los hoteles de un país, y cada
  cambio queda auditado (quién, cuándo, qué) en el topic `audit`.
- **Se aplican en el front office**, que guarda una **copia local** de todas: el servicio las publica
  enteras en `registration-rules` y el front office las lee y las guarda (tabla `registration_rule`,
  gana la versión mayor, una vez por evento). Así el mostrador las sigue aplicando sin red o si el
  plano de control no responde (AF, F017) — el mismo patrón que los [avisos](/integracion/avisos/).
- **La misma lectura en los dos lados**: cómo se lee una regla (qué huésped le aplica, qué exige) es
  código compartido del contrato (`RegistrationRequirements`, en `contracts-registration`), así que la
  prueba del plano de control y el front office dicen lo mismo del mismo huésped.

## Qué es una regla

| Parte | Qué dice |
|---|---|
| Ámbito | `COUNTRY` (todos los hoteles de un país: código ISO, `ES`, `MU`…) o `HOTEL` (un hotel: código del CRS, `MRU01`) |
| Nacionalidad | cualquiera, solo una lista, o todas menos una lista; `EU` es la Unión Europea |
| Edad | mínima y máxima, en años cumplidos el día de llegada |
| Huésped | el titular, los acompañantes o cualquiera |
| Datos obligatorios | tipo, número, país de expedición y caducidad del documento; fecha y lugar de nacimiento; nacionalidad; sexo; dirección, ciudad, código postal y país de residencia; firma; adulto responsable y parentesco (menores) |
| Cuándo | antes de la llegada, en el check-in de recepción, en el check-in online |
| Base legal | el texto que lee recepción junto a lo que falta |
| Vigencia y estado | fechas desde/hasta; activa o no (no se borran: se desactivan) |

**Cómo se combinan.** A un huésped se le exige la **suma** de lo que piden todas las reglas que le
aplican; una regla de **hotel** puede además **eximir** de algún dato que pida su país (un hotel
relaja su país, no al revés).

**Lo que aún no se sabe.** Una nacionalidad desconocida **cuenta** (la regla se pide hasta saberla, y
la nacionalidad se pide si alguna regla la exige); una edad desconocida solo cuenta para las reglas
**sin** edad, para no pedir el tutor a todos los adultos cuya fecha de nacimiento aún no está.

**El país del hotel.** Ningún maestro lo tiene todavía (el catálogo del CRS no guarda país): se dice en
`registration.hotel-countries` (`HOTEL_COUNTRIES`) del servicio y en `frontoffice.hotel-country`
(`FRONT_OFFICE_HOTEL_COUNTRY`) de cada front office.

## Cómo se aplican en el front office

- **En los servicios, no en las pantallas.** Lo que falta a un pax es un paso más del check-in
  (`IncompleteCheckIns`): «Datos de registro de Ana Test (pax 1): nacionalidad, fecha de nacimiento —
  Ejemplo · …». El check-in se **rechaza** —lo pida el recepcionista, el agente de recepción o un
  check-in online—, el rechazo se audita, y se puede **forzar** con un motivo como cualquier paso que
  falta: queda «check-in incompleto» hasta completarlo.
- **De dónde salen los valores.** El escáner de documentos guarda tipo, número, nacionalidad y fecha
  de nacimiento del pax; el resto lo escribe recepción en el kárdex (el bloque «Documento» del paso
  Identidad), donde los campos obligatorios para ese pax salen marcados y la base legal se muestra
  encima. Se guardan aparte de la identidad (tabla `pax_registration_data`), y cada edición se audita
  por los campos tocados, no por sus valores.
- **Walk-in.** Además de nombre, apellidos y documento, el titular debe traer lo que las reglas le
  exigen de lo que el walk-in recoge (nacionalidad, tipo de documento); el resto se pide en el
  check-in, como a cualquier estancia.

## Probar una regla

En **Registro → Probar con un huésped** se elige hotel, nacionalidad, fecha de nacimiento, titular o
acompañante y momento, y **Evaluar** dice qué datos se le exigirían, por qué reglas y con qué base
legal — exactamente lo que le pediría el front office. El agente del plano de control tiene lo mismo
por MCP (`listRegistrationRules`, `evaluateRegistrationRules`) y puede **proponer** una regla en
borrador (`draftRegistrationRule`, se crea inactiva): la activa una persona.

## Reglas de ejemplo

`deploy/demo/registration-rules-seed.sh` crea unas reglas **de ejemplo — no son asesoramiento legal**:

| Regla | Ámbito | A quién | Exige |
|---|---|---|---|
| Mauricio · registro de huéspedes | país `MU` | cualquiera | tipo y número de documento, nacionalidad, fecha de nacimiento |
| MRU01 · viajeros de fuera de la UE | hotel `MRU01` | titular, salvo UE y MU | caducidad y país de expedición del documento |
| España · registro de viajeros, 14 años o más | país `ES` | cualquiera, 14+ | documento completo, nacionalidad, fecha de nacimiento, sexo, dirección completa, firma (inspirada en el RD 933/2021 · SES.Hospedajes) |
| España · menores de 14 | país `ES` | hasta 13 años | nacionalidad, fecha de nacimiento, adulto responsable y parentesco |
| España · viajeros de fuera de la UE | país `ES` | cualquiera, salvo UE | caducidad del documento |

Con ellas, un check-in en MRU01 pide a cada pax su documento, nacionalidad y fecha de nacimiento (el
escáner los lee), y al titular de fuera de la UE, además, la caducidad y el país de expedición.

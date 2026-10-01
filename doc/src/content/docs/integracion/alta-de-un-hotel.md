---
title: Alta de un hotel
description: La integración de cada hotel en integrations-service y su alta por puertas, del registro a la activación.
---

Una **integración** por hotel, en `integrations-service` (pantallas en `/_integrations` del plano de
control): su conexión con Opera —con el secreto cifrado en reposo bajo la clave de
`ec-integrations-crypto`— y su ciclo de vida. Hasta que está activa, las reservas del hotel **esperan**
con la causa `INTEGRATION_INACTIVE:<hotel>`: no se pierden ni se escriben.

## Crear la integración

*Integrations → CRS → PMS → New*. Se eligen dos cosas y las dos se **leen**, no se escriben: el **hotel del CRS**
sale del CRS y la **propiedad de Opera**, de Opera (el cliente OHIP no puede listar las de la cadena,
así que se ofrecen las configuradas, con el nombre que Opera les da). La conexión viene rellena con la
de la cadena. Al guardarla arranca el proceso `alta-integracion`, que avanza solo por sus puertas.

## Las puertas

| # | Puerta | Estado mientras espera | Qué la abre |
| -: | :----- | :--------------------- | :---------- |
| 1 | **Conectividad** con Opera: token y lectura de la propiedad | `CONNECTIVITY_FAILED` | Una conexión que Opera acepta |
| 2 | **Contraste de catálogos**: lo que la propiedad tiene en Opera frente a lo que emite el CRS | `PENDING_CONFIGURATION` | La propiedad configurada en Opera |
| 3 | **Mapeado**: los códigos sin equivalencia, con propuesta del agente | `MAPPING_PENDING` | Una persona aprueba el mapeado |
| 4 | **Interlocutores**: los que usan las reservas futuras del hotel se exportan del ERP a Opera | `SYNCING_PARTNERS` | Todos son perfiles en Opera |
| 5 | **Backfill**: pasada previa y volcado de las reservas futuras, de la llegada más próxima a la más lejana | `BACKFILL_BLOCKED`, `BACKFILLING` | Sin huecos, y la ventana próxima cubierta |
| 6 | **Activación** | `READY_TO_ACTIVATE` | Una persona la activa: `ACTIVE` |

Después, `PAUSED` y `DECOMMISSIONED`. Los estados son los de `IntegrationStatus`
(`contracts-integration`).

- **Cada puerta que necesita a alguien deja un aviso en la bandeja**, con el enlace a la integración
  (`/integrations/registry/<id>`), y se cierra sola cuando la integración la pasa.
- **El mapeado** se ve en *Mapping → Dictionary* filtrando por la integración (estado *Unmapped*); cada
  código, al abrirlo, junto a lo que ofrece Opera. Aprobar reanuda todo lo que esperaba.
- **Los interlocutores**: el que el ERP ya sabe qué perfil es no se toca; si no, se busca en Opera por
  su `CorporateId` y solo si no está se crea; el ERP anota cuál es. *Import partners*, en la
  integración, hace lo contrario y solo para sembrar la demo: crea o pone al día en el ERP las agencias,
  empresas y orígenes que la cadena tiene en Opera, sin escribir nada en Opera.
- **El backfill** no reescribe: lo que Opera ya tiene en esa versión no se escribe (ni la reserva ni el
  perfil). «Ya tiene» es encontrar el localizador del CRS como referencia externa bajo el contexto de la
  ejecución. Las órdenes de proyección van por `projection-requests` y el backfill escribe las de una
  página y mueve su cursor en la misma transacción. *Relaunch backfill* lo repite.
- **La activación libera lo retenido**: resuelve la causa `INTEGRATION_INACTIVE:<hotel>` y todos los
  procesos que la esperaban siguen.

Cada acción que decide algo sobre un hotel —alta, aprobar o retirar un mapeado, activar, pausar,
backfill, resolver una causa— queda en *Audit*, hecha o rechazada, por consola, API o agente.

## Lo que el alta necesita saber ya

El alta es el caso que justifica más llamadas síncronas, porque varias puertas **son** la respuesta de
otro sistema: verificar la conexión (`POST /connections/verify`), leer el catálogo de la propiedad
(`GET /catalog?hotelId`), cuántos códigos faltan (`GET /pending`), qué huecos bloquean el backfill,
qué interlocutores faltan. Las escrituras, en cambio, van por Kafka (`mapping-commands`,
`partner-commands`, `projection-requests`). El detalle está en
[`llamadas-sincronas.md`](https://github.com/miguelperezcolom/ec-demo1/blob/master/docs/poc-acl/llamadas-sincronas.md).

## En ec1

MRU01 (CRS) ↔ **XMAR** (Opera). El catálogo de MRU01 está importado del de XMAR, con códigos y nombres
propios del CRS (`JS-SEA` «Junior suite con balcón y vista al mar» ↔ `SJMB`, `DIRECTA` ↔ `406484DIRXM`,
`TODO-INCLUIDO` ↔ `PENSTI`…), para que el agente empareje por significado y no por código. La pareja
esperada de cada código está en
[`deploy/demo/crs-catalog/MRU01-expected-pairs.md`](https://github.com/miguelperezcolom/ec-demo1/blob/master/deploy/demo/crs-catalog/MRU01-expected-pairs.md).

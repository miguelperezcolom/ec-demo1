---
title: "Clientes: MDM y Salesforce"
description: El maestro de clientes — identidad al proyectar, Salesforce como maestro, fusiones, cambios propuestos por recepción y cómo se propaga cada decisión.
---

**Salesforce es el maestro del cliente**; `customer-mdm-service` está delante (hito H11, HLA CRM-MDM):
resuelve la identidad de cada pasajero, guarda dónde se conoce a cada cliente fuera de él (**xref**) y
una **proyección** de los datos de Salesforce, y publica lo que cambia. Nadie más escribe en Salesforce.

| Dónde | Qué hay |
| :---- | :------ |
| Salesforce | El contacto (el dato vigente), los Cases de cambio, las fusiones de un *steward* |
| MDM | El golden record proyectado, las xref (contacto de Salesforce, huésped del front office, perfiles de Opera), las solicitudes de cambio, las consolidaciones |
| Front office | El kárdex, que toma el cliente del topic `customers` |
| Opera | El perfil del huésped de cada reserva, reescrito cuando el cliente cambia |
| CRS | Nada: cuando enseña una reserva, pregunta al MDM (`GET /reservations/{h}/{loc}/links`) |

Pantallas: **Clientes** en el plano de datos (`/_customers`, solo consulta: buscar, la ficha con sus
datos vigentes, dónde está, sus reservas y estancias y sus solicitudes de cambio) y las técnicas en el
plano de control (`/_mdm`: golden records, consolidaciones).

## Identidad al proyectar

`ensure-guest-profile` llama a `POST /identities/resolve` con el titular y los huéspedes de cada
habitación. Idempotente por reserva y pasajero.

- **Match solo con certeza**: documento, o email con el mismo nombre; el titular que también es
  huésped de una habitación es el mismo cliente.
- **Lo demás es un provisional nuevo.** Fundir a dos personas distintas es peor que un duplicado, que
  es para lo que está la limpieza en Salesforce.
- **El MDM no es una puerta**: si no responde, el perfil de Opera se escribe sin código y la venta sigue.

## A Salesforce, y de vuelta

- **Proyección**: los clientes pendientes van a Salesforce como `Contact`, *upsert* por el campo externo
  `MDM_Id__c`, **hasta 200 por llamada** (sObject Collections). Lo que Salesforce rechaza queda marcado
  y no se reintenta hasta que el cliente cambia. Las reglas de duplicados de la org **proponen** (sin
  bloquear) y un *steward* fusiona.
- **Fusiones de vuelta**: el Platform Event `ClienteConsolidado__e` por la **Pub/Sub API** y, como red
  de seguridad, un `queryAll` de los contactos borrados **una vez al día**. Los dos acaban en la misma
  bandeja, deduplicada por cliente.
- **Supervivencia**: gana, campo a campo, lo que el *steward* dejó en el contacto superviviente; si no
  lo hay, lo que el MDM tenía; si tampoco, lo del absorbido. El absorbido queda como **alias**.
- **Cambios hechos en Salesforce** (a mano o al aprobar un Case) bajan con `ClienteActualizado__e` y
  `CambioClienteResuelto__e`; el MDM los proyecta como su golden record.

### Salesforce avisa por la Pub/Sub API; el MDM no sondea

Todo lo que Salesforce tiene que contar al MDM llega como **Platform Event** (de alto volumen) por la
**Pub/Sub API**: gRPC a `api.pubsub.salesforce.com:7443`, con el mismo token de *client credentials*
que las llamadas REST (cabeceras `accesstoken`, `instanceurl`, `tenantid`).

| Evento | Lo publica | Qué hace el MDM |
| :----- | :--------- | :-------------- |
| `ClienteConsolidado__e` | Flow `Mdm_Announce_Merge` (antes de borrar) | La fusión: supervivencia, alias, `CustomersMerged` |
| `CambioClienteResuelto__e` | Flow `Mdm_Apply_Change_Request` | La decisión de un Case de cambio |
| `ClienteActualizado__e` | Flow `Mdm_Announce_Contact_Change` | Lee el contacto y lo proyecta |
| `AvisoRecepcionCambiado__e` | Flow `Mdm_Announce_Notice_Change` | El aviso entero, sin leer nada |

- **Una suscripción por topic**, cada una en su hilo virtual, pidiendo de 25 en 25 (control de flujo
  *pull*: pide más cuando se han entregado los pedidos).
- **Replay id persistido**: tras cada evento — y tras cada *keepalive*, que trae el último replay id
  sin eventos — se guarda en `salesforce_cursor` (una fila por topic). Al reiniciar, cada suscripción
  **reanuda desde ahí** (`ReplayPreset.CUSTOM`): nada se pierde mientras el MDM estuvo parado, dentro
  de los **3 días** que Salesforce guarda los eventos. La deduplicación es la de cada bandeja (una
  fusión, una decisión o un aviso repetidos no hacen nada dos veces).
- **Reconexión con *backoff***: si el stream se corta, se reabre a 1 s, doblando hasta 60 s. Un
  `UNAUTHENTICATED` renueva el token; un replay id caducado (`INVALID_ARGUMENT`) se olvida y la
  suscripción empieza desde ahora.
- **Las redes de seguridad son diarias**: el `queryAll` de contactos borrados, la consulta de Cases de
  cambio abiertos y la de avisos escritos sin confirmar corren **una vez al día** (y las dos últimas solo
  si hay algo pendiente), a los 2 min de arrancar, y **en el acto** cuando una suscripción empieza sin
  replay id — la primera vez o tras perderlo —, que es cuando pudo haber un hueco.
- **Coste**: la Pub/Sub API **no gasta el cupo diario de llamadas** (`DailyApiRequests`); los eventos
  entregados cuentan contra la **asignación de entrega de eventos** (*event delivery allocation*, por
  24 h, compartida entre Platform Events de alto volumen y Change Data Capture, y contada por cada
  cliente suscrito). Con cuatro suscripciones y los eventos de una demo, es una fracción mínima.

### Por qué flows y no Apex

La org es una **Base Edition**: no admite desplegar Apex. El evento de fusión lo publica un **Flow
before delete** (`Mdm_Announce_Merge`), y ahí `MasterRecordId` aún está vacío: el evento dice qué
cliente se fue y el MDM lee el superviviente del contacto borrado con `queryAll`. Otro Flow
(`Mdm_Keep_Mdm_Id`) impide que una fusión reasigne `MDM_Id__c`. Los Flows llegan como borrador al
desplegarlos: `deploy.py` los activa. La org no admite más objetos personalizados, así que la solicitud
de cambio es un **Case** con campos propios (`MdmRequestId__c`, los datos propuestos, `Decision__c`).

Todo lo que necesita la org está en
[`integration/customer-mdm-service/salesforce/`](https://github.com/miguelperezcolom/ec-demo1/tree/master/integration/customer-mdm-service/salesforce):
campos, el evento, los Flows, el Permission Set `MDM_Integration`, las reglas de coincidencia y de
duplicados, y `deploy.py`. `dedup.py` fusiona los duplicados que las reglas dejaron (y `--restore` los
recupera de la papelera, para repetir la demo).

## Eventos, no llamadas

Lo que el MDM decide de un cliente lo publica en el topic **`customers`** (por su outbox, con clave el
cliente), con el golden record y las reservas del cliente:

- `CustomerChanged` — golden record nuevo, o la decisión de un cambio propuesto;
- `CustomersMerged` — fusión, con el superviviente.

El MDM no llama a nadie:

- el **front office** actualiza su kárdex;
- **crs-integration** vuelve a proyectar las reservas del cliente (origen `mdm-update-<cliente>-v<versión>`
  o `mdm-merge-<absorbido>`) en los hoteles con integración, y `ensure-guest-profile` reescribe el perfil
  del huésped en Opera **en su sitio**: el email y el teléfono cambian en la misma entrada.

Un sistema nuevo que necesite el cliente es un suscriptor más.

## Recepción propone, Salesforce decide

1. En el front office se cambian los datos del titular. El kárdex los guarda al momento, marcados
   **«Pendiente de Salesforce»**.
2. El front office lo manda al MDM por Kafka (`customer-commands`, `propose-change`, con su id de
   solicitud `CR-FO-…`: un reenvío no la duplica).
3. El MDM abre en Salesforce un **Case «Cambio de datos de cliente»** sobre el contacto.
4. En Salesforce se pone **Decisión** en *Aprobada* (se pueden corregir los datos antes) o *Rechazada*
   (con **Motivo**). Un flow aplica lo aprobado al contacto y anuncia la decisión.
5. Baja sola: aprobado, el dato se queda sin marca; rechazado, vuelven los datos del maestro con
   «Rechazado en Salesforce» y el motivo. Opera reescribe el perfil.

Si la decisión no llega por evento, el MDM pregunta por los Cases abiertos una vez al día (y al
arrancar), y solo mientras haya alguno.

## Avisos de recepción

Lo que recepción tiene que saber de un cliente al llegar, durante la estancia o al irse («pedir el
pasaporte original», «pago pendiente de la última estancia»). **Su maestro es Salesforce**; se crean allí
o desde la ficha del cliente en *Clientes*.

- **En Salesforce** un aviso es un **Case sobre el contacto con «Tipo de aviso»** (*Informativo*,
  *Importante*, *Bloqueante*): el asunto es el texto; *Mostrar el aviso en* (check-in, check-out,
  estancia), *Aviso desde/hasta* (opcionales) y *Aviso activo*. Desmarcarlo, o cerrar el Case, lo retira.
  No es un objeto propio porque la Base Edition no admite más objetos personalizados.
- Un flow publica **`AvisoRecepcionCambiado__e`** con el aviso **entero** al crearlo o cambiarlo: el MDM
  lo guarda sin leer nada de Salesforce y lo publica en **`customer-notices`** (`CustomerNoticeChanged`,
  clave el cliente, con versión).
- **Desde *Clientes*** (la ficha los lista; **Nuevo aviso** y **Avisos**, en su barra, abren un panel
  lateral) se añade, edita o desactiva: el MDM lo
  guarda como **pendiente** y lo escribe en Salesforce con los demás que esperan — una llamada para los
  nuevos (upsert por `MdmAvisoId__c`), otra para los cambios —, parado si el cupo se agota. Solo cuando su
  evento vuelve con lo pedido pasa a *Confirmado* y se publica: los hoteles ven lo que tiene Salesforce.
  Si el evento se pierde, una consulta al día (y al arrancar), solo por los escritos sin confirmar.
- Una fusión pasa los avisos del absorbido al superviviente.

El front office los guarda por cliente (titular y acompañantes que son clientes de la cadena) y los
enseña en el check-in y el check-out: ver [Un sistema propio](/front-office/sistema-propio/).

## El documento escaneado es dato de confianza

*Escanear* en recepción manda el documento del pasajero al MDM (`customer-commands`,
`record-scanned-identity`):

- lo que el cliente no tiene (documento, fecha de nacimiento, nacionalidad) se rellena y llega al
  contacto de Salesforce **sin Case**;
- lo que contradice al maestro (otro nombre, otra fecha, otro documento) va como Case;
- si el documento ya es de **otro cliente**, es la misma persona: el MDM consolida el provisional en el
  que tiene el documento (alias, reservas re-apuntadas, `CustomersMerged`) y fusiona los dos contactos
  en Salesforce con `merge()` de la API SOAP. Solo si es seguro: el documento es de un único cliente,
  el pasajero no tenía otro y el nombre coincide.

## Contactos marcados por calidad del dato

Los contactos se siguen creando al reservar — como el perfil de Opera —, pero cada uno dice cuánto
vale. Tres campos del contacto (sección *Calidad del dato (MDM)*), que **calcula el MDM** y nadie
edita a mano:

| Campo | Valores | De dónde |
| :---- | :------ | :------- |
| **Estado MDM** (`Estado_MDM__c`) | Provisional · Consolidado · Anonimizado | El estado del golden record |
| **Calidad del dato** (`Calidad_Dato__c`) | Solo nombre · Con contacto · Verificado (documento) | *Con contacto*: un email, un teléfono o un documento de la reserva. *Verificado*: recepción escaneó en el check-in el documento que tiene el cliente |
| **Origen** (`Origen__c`) | CRS · Canal · Touroperador | El canal de su primera reserva en el CRS: `TTOO` → Touroperador, `OTA` → Canal, el resto (web, call center, teléfono, walk-in…) → CRS |

- **Viajan con la proyección**: un contacto nuevo nace marcado, sin llamadas de más.
- **Lo que cambia la marca sin cambiar los datos** — el cliente consolidado, el documento verificado,
  el origen averiguado — lo manda `ContactMarking` cada 2 min, **solo los tres campos** y solo de los
  contactos cuya marca cambió (`markedAs` guarda la que tiene cada contacto): una llamada por cada 200,
  ninguna si no cambió nada. Como el flow de cambios del contacto no mira esos campos, marcar no
  genera `ClienteActualizado__e`. Su primera pasada **marcó todos los contactos que ya existían**.
- **El origen** se lee una vez del CRS (llamada interna, no a Salesforce) y se guarda en el cliente.
- **La regla de duplicados** `MDM_Possible_Duplicate` solo evalúa contactos con **email, teléfono o
  documento** (condiciones de la regla: `Email ≠ vacío OR Phone ≠ vacío OR Document_Number__c ≠
  vacío`): dos huéspedes que se llaman igual no son indicio de nada. La regla de coincidencia
  `MDM_Same_Person` no admite condiciones; son de la regla de duplicados, que es la que decide qué
  registros se evalúan.
- **Vistas de lista**: *Pendientes de identificar* (Solo nombre, no anonimizados); *Marketing:
  contactables* (Con contacto o Verificado); y *Cumpleaños de este mes*, la vista de marketing
  estándar, excluye *Solo nombre* y los anonimizados.

### Limpieza de los «Solo nombre» (RGPD)

Un contacto que es **solo un nombre** y cuyo cliente tiene **solo reservas canceladas o no-show** no
sirve para nada en el CRM cuando esas reservas pasaron: nadie se alojó, no se le puede contactar y no
se le distingue de un homónimo. Por **minimización de datos y limitación del plazo de conservación**
(RGPD art. 5.1.c y 5.1.e), `NameOnlyCleanup` lo **anonimiza** en Salesforce tras **N días sin
actividad** (`CLEANUP_AFTER`, 30 días por defecto):

- una vez al día (`CLEANUP_CRON`, 03:30), en lotes de 200 contactos por llamada; un día sin nada que
  limpiar no llama a nadie;
- **solo con certeza**: cada reserva del cliente (las suyas y las de los códigos fusionados en él)
  leída del CRS, todas canceladas o no-show (la cancelación `NOS`), y nada suyo — ficha ni pasajeros —
  más reciente que el plazo. Si el CRS no contesta o no tiene una reserva, se queda como está;
- **se anonimiza, no se borra**: nombre, email, teléfono, fecha de nacimiento, nacionalidad y documento
  se vacían, el apellido pasa a «Anonimizado» y *Estado MDM* a *Anonimizado*. Lo que apuntaba al
  contacto no se rompe;
- **el MDM guarda la referencia y el motivo**: el código de cliente, el id del contacto, cuándo y por
  qué (`anonymizedAt`, `anonymizedReason`, estado `ANONYMIZED`; se ve en la ficha de *Clientes*). Las
  reservas siguen en el CRS, su sistema de registro. El MDM marca al cliente **antes** de anonimizar,
  así que el `ClienteActualizado__e` que provoca el borrado de los datos no se lee de vuelta.

## El cupo de la API

La org tiene **15.000 llamadas en 24 h móviles**, compartidas por todo lo que la use: el MDM de ec1,
cualquier entorno local con las mismas credenciales, scripts, pruebas. El MDM las cuenta, se pausa si
Salesforce dice que se ha agotado y lo enseña en las consolas y en Grafana. Ver
[Consumo de Salesforce y Opera](/operacion/consumo-apis-externas/).

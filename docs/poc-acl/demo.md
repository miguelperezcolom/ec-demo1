# PoC ACL — guión de la demo

Estado a 2026-09-25, tarde. Todo lo que se enseña está desplegado en `ec1.mateu.io` y escribe en el
**tenant real de Opera** (OHIP UAT, propiedad **XMAR**); ya no hay doble de Opera en el despliegue
(`opera-mock` queda solo para la batería local de pruebas). El motor es EventConductor **2.23.1** y
las apps, Mateu **3.0-alpha.367**. Lo marcado *(pendiente)* no está construido todavía o espera una
decisión.

**ec1 está a cero** desde las 08:20Z (`deploy/demo/zero.sh`): sin integraciones, reservas, mapeados,
clientes ni procesos, y sin contactos en Salesforce, para recorrer el alta paso a paso. Hasta que se
recorra y se tome una línea base nueva, `reset.sh` y `npm run demo` no se pueden usar (ver
[Resetear la demo](#resetear-la-demo)).

La infraestructura, desde hoy: todo ec1 en **hel1**, en los nodos que elige Karpenter (hoy, uno
cx53 para todo ec1 y el de observabilidad), sin la topología del benchmark; el PostgreSQL del motor en
un **volumen** (ya no muere con su pod); los pods clave marcados para que Karpenter no los mueva; y el
DNS de `ec1.mateu.io` apuntando al balanceador de hel1. El motor tiene **solo los seis procesos de la
PoC** (ec-definitions #23).

## 1. Arquitectura (diagramas del HLA)

Del contexto a lo concreto, y parando en lo que la PoC ha construido de verdad. Los diagramas son
los del HLA *CRS-PMS Integration - Solution* y *CRM-MDM Integration - Solution*.

| # | Diagrama | Qué contar | En la PoC |
| -: | :------- | :--------- | :-------- |
| 1 | Context Model AS-IS | De dónde partimos | — |
| 2 | Context Model TO-BE | Rumbo (CRS) ↔ integración ↔ Opera Cloud por OHIP; el ERP como maestro de interlocutores; Salesforce como motor de limpieza del cliente | `booking` hace de Rumbo, `erp` (antes `partners`) de ERP; Opera y Salesforce son los reales |
| 3 | Container Model TO-BE | Los servicios: ACL del CRS, mapeado, conector PMS, integraciones, MDM, comunicación, auditoría, motor | Un servicio por contenedor, y el front office del hotel |
| 4 | El modelo mental: dos planos | Plano de datos (lo que fluye) y plano de control (quién lo gobierna) | Dos consolas: `ec1` y `console.ec1` |
| 5 | Grabar Reserva — System Model | El camino de una reserva de punta a punta | «Proyectar reserva», contra Opera real y el front office |
| 6 | Proyectar una reserva (secuencia) | Preparar → identidad del cliente (MDM) → perfil → grabar con guarda de versión → anotar en el CRS | Igual; el front office ya no es un paso: cuelga de Opera (pms-fo, «Proyectar estancia») |
| 7 | Un proceso bloqueado espera, no falla | Causas en vez de errores: una causa, N procesos | *Mapping → Causes*, y cada causa en la bandeja de quien la resuelve |
| 8 | Mapeado — System Model | Diccionario versionado, aprobación humana, propuesta del agente | Diccionario (filtrado por integración, con lo que falta por mapear) y el agente |
| 9 | Alta de una integración (secuencia y estados) | Del registro a la activación, por puertas | `integrations-service` y el proceso `alta-integracion` |
| 10 | Maestro de clientes (HLA CRM-MDM) | Identidad al proyectar, limpieza y fusión en Salesforce, supervivencia y propagación | `customer-mdm-service` + Salesforce |
| 11 | Auditoría y bandeja | Quién hizo qué (F016); lo que espera a cada persona, con el enlace a la pantalla que lo resuelve | `audit-service`, bandeja en `communication-service` |

Fuera de la PoC, y conviene decirlo: la subida PMS → CRS (OOO, no-show, conciliación diaria), el
cobro de la penalización y el backfill de cupo.

## 2. Recorrido por las consolas

Dos consolas, una por plano (diagrama 4). Cada servicio trae sus pantallas y la consola las federa;
cada consola tiene su versión Redwood (`rw.` y `rw-console.`) con los mismos backends. En la barra
superior de las cuatro, el **aviso de la bandeja** («Inbox (n)», lo que yo aún no he visto): es la
única entrada a la bandeja, que ya no tiene menú propio.

**Plano de datos — `https://ec1.mateu.io`**: lo que usa el negocio.

| Menú | Qué enseñar |
| :--- | :---------- |
| Call center | El CRS simulado: una reserva con habitaciones, huéspedes, desglose diario, cobros y su referencia en Opera; en *In other systems*, sus enlaces: la estancia en el front office, el titular y los huéspedes en Clientes y en Salesforce, y la reserva y los perfiles de Opera (como referencias: Opera Cloud no tiene enlace estable) |
| ERP | El maestro de interlocutores; cada uno sabe **qué perfil es en Opera** (*Opera profile*); *Resync* |
| Clientes | La cara de negocio del maestro de clientes, solo consulta: *Buscar clientes* por nombre, email, teléfono o documento; la ficha con sus datos vigentes (lo que decidió Salesforce), dónde está (contacto de Salesforce, huésped del front office, perfiles de Opera), sus reservas en el CRS y sus estancias en el front office — cada una con su enlace — y sus solicitudes de cambio; *Solicitudes de cambio*: todas, con su estado. Los cambios se piden en recepción y los decide Salesforce |
| Admin | Los procesos del motor, con sus pasos |

**Front office del hotel — `https://front.ec1.mateu.io`** (Redwood): recepción. **Consume el PMS**
(integración pms-fo, cadena CRS → PMS → front office): las reservas de XMAR — las que escribe la
integración y las nacidas en Opera — llegan aquí como estancias **tal como Opera las tiene**, con los
nombres del catálogo de Opera (tipo de habitación, régimen); check-in, huéspedes, folios. Cada
reserva enseña *En otros sistemas*: la reserva del CRS, sus clientes en Clientes y su contacto en
Salesforce (enlaces), y el perfil de Opera.

**Plano de control — `https://console.ec1.mateu.io`**: lo que gobierna la plataforma.

| Menú | Qué enseñar |
| :--- | :---------- |
| Integrations | Dos tipos. **CRS → PMS**: una por hotel del CRS, su conexión con Opera, en qué puerta del alta está, *Relaunch backfill*, *Import partners*. **PMS → Front office** (pms-fo): una por propiedad de Opera y su front office — conexión, catálogo, backfill, activación, y el sondeo de cambios de Opera (cursor, último sondeo); *Resync catalogue*, *Poll now*, *Relaunch backfill* |
| Mapping | Causes; Dictionary: se filtra por **integración** y muestra también lo **sin mapear** (*Unmapped*), *Ask the agent*, y aprobar, rechazar o **retirar** una entrada o las filas seleccionadas; Partners in the PMS |
| Customers | El maestro de clientes, lo técnico: golden records, su estado de proyección y las consolidaciones que llegan de Salesforce (la cara de negocio está en Clientes, en el plano de datos) |
| Notifications | Lo que se ha comunicado y a quién; **destinatarios**: quién se entera de qué y por dónde (§11) |
| Audit | Todas las acciones auditables: quién, cuándo, con qué parámetros y qué respuesta; búsqueda libre y filtros |
| Workflow / Forms | Las definiciones de proceso: los diez de la PoC (`alta-integracion`, `alta-integracion-fo`, `proyectar-reserva`, `proyectar-cancelacion`, `proyectar-estancia`, `proyectar-interlocutor`, `registrar-no-show`, y la recepción hacia el PMS: `registrar-checkin`, `registrar-checkout`, `registrar-no-show-pms`); formularios, hoy ninguno |
| IA | El agente de mapeado y los MCP de cada servicio |
| Usuarios | Quién puede hacer qué |

Todos los listados paginan.

## 3. El punto de partida

Hoy, **cero**: ninguna integración. Lo que hay es lo que no hace la integración — los hoteles y el
catálogo del CRS, los 259 interlocutores del ERP, las 15 habitaciones y los catálogos del front
office — y Opera con lo que ya tenía.

Un hotel del CRS **sin integración** vende y sus reservas se quedan en el CRS: no arranca ningún
proceso (antes esperaban en la causa `INTEGRATION_INACTIVE`, y se acumulaban). Cuando el hotel se da
de alta, el backfill las trae. Las de un hotel con integración **aún no activa** sí esperan, y se
reanudan al activarla.

Cuando esté hecha el alta de MRU01 con XMAR, el punto de partida de la demo será ese, y se guardará
como línea base.

**Desde cero hay dos altas**: la crs-pms de MRU01 (§4) y la **pms-fo de XMAR** (§4 bis). Sin la
segunda, las reservas llegan a Opera pero no al front office. Mejor hacerla **antes** (el front office
se llena con lo que Opera ya tiene y queda activa): así en el flujo 1 las reservas aparecen en el front
office en cuanto llegan a Opera.

## 4. Crear una integración

*Integrations → New*: se eligen dos cosas y las dos se leen, no se escriben — el **hotel del CRS**
sale del CRS y la **propiedad de Opera**, de Opera (el cliente OHIP no puede listar las de la cadena,
así que la integración ofrece las configuradas — XMAR y XMU — con el nombre que Opera les da). La
conexión viene rellena con la de la cadena. Al guardarla arranca `alta-integracion`, que avanza solo
por sus puertas:

1. **Conectividad** con Opera: token y lectura de la propiedad.
2. **Contraste de catálogos**: lo que la propiedad tiene en Opera frente a lo que emite el CRS.
3. **Mapeado**: los códigos sin equivalencia, con propuesta del agente, y la aprobación de una
   persona.
4. **Interlocutores**: los que usan las reservas futuras del hotel se **exportan del ERP a Opera** —
   el que el ERP ya sabe qué perfil es no se toca; si no, se busca en Opera por su código
   (CorporateId) y solo si no está se crea; el ERP anota cuál es.
5. **Backfill**: pasada previa, y volcado de las reservas futuras de la llegada más próxima a la
   más lejana, con la disponibilidad suspendida mientras dura.
6. **Lista para activar** cuando cubre la ventana próxima; activar abre el tráfico en tiempo real.

Cada puerta que necesita a alguien deja un aviso **en la bandeja** con el enlace a la integración, y
se cierra solo cuando la integración la pasa.

> *(pendiente de decidir)* **Contra qué propiedad hacer el alta en directo.** Un alta completa
> escribe en Opera las reservas futuras del hotel. Ahora mismo se está recorriendo **MRU01 → XMAR**
> desde cero; para la demo en directo, o se repite esa (la línea base tendría que tomarse antes del
> alta) o se hace la de otro hotel contra XMU, que está vacía.

## 4 bis. El front office cuelga del PMS: la integración pms-fo

Las reservas van en cadena **CRS → PMS → front office**: el front office no recibe lo que se mandó a
Opera, sino lo que **Opera tiene**. Es otra integración, *Integrations → PMS → Front office → New*:
propiedad de Opera (XMAR), el front office (MRU01), ámbito y horizonte (60 días). El ámbito, por
defecto, es `CHAIN` — **solo las nuestras**: las reservas que escribió la integración crs-pms, las que
llevan en la «Custom Reference» de Opera la de la ejecución de la demo (`EC-DEMO1` en la línea base;
tras un `zero.sh`, la del contexto nuevo, `ECDEMO1-<MMddHHmm>`, en el ConfigMap `ec-demo-run`). `ALL`
trae todas las reservas de la propiedad, también las nacidas en Opera. Al guardarla arranca
`alta-integracion-fo`:

1. **Conexión**: Opera legible y el front office responde.
2. **Catálogo**: el de la propiedad — 15 tipos de habitación, 78 tarifas, 25 paquetes, 367
   habitaciones — va al front office, que lee con él las estancias.
3. **Backfill**: las reservas de XMAR en casa o que llegan en los próximos 60 días, una
   `proyectar-estancia` cada una. Las estancias que ya había se reconocen (por la reserva de Opera, el
   localizador del CRS o el walk-in): no se duplican. Las nacidas en Opera abren `OP-<confirmación>`.
4. **Lista para activar**; al activar, fluyen los cambios.

Qué llega y cómo: lo que la integración crs-pms graba en Opera lo avisa el conector
(`pms-reservations`) y arranca `proyectar-estancia` al momento; lo que cambia en Opera por otras vías
lo encuentra el **sondeo** (cada 60 s: OHIP no tiene filtro «modificadas desde», así que se recorren las
reservas de la ventana y se proyectan las modificadas desde el cursor). En *Admin → Processes*, cada
`proyectar-estancia` lleva en la clave la reserva de Opera y su modificación (`…:2026-09-28T00:44:46`)
o el evento (`…:evt-…`).

**No `ALL` en la demo**: el UAT de Opera es compartido, y con `ALL` el front office de MRU01 pasa a tener
todas las reservas de XMAR en la ventana — el 2026-09-27, 814 estancias `OP-…` con huéspedes reales de
otros, con sus datos de contacto, a la vista con demo/demo. El ámbito no se cambia después de dar de alta
la integración: otro ámbito es otra integración (dar de baja la actual y dar de alta otra).

**Ojo, nombres de Opera**: el front office enseña las palabras de Opera — «Standard King», «Suite
Junior Standard Balcón», «Pensión Todo Incluido» —, pero el paquete `BRKFST` de XMAR se describe en
Opera como «BRKFST»: esa es su palabra, y el front office la enseña tal cual.

## 5. Se bloquea: espera, no falla

- La integración se queda en `MAPPING_PENDING`; los códigos, en *Mapping → Dictionary* filtrando por
  la integración (estado *Unmapped*); cada uno, al abrirlo, junto a lo que ofrece Opera.
- Si se aprueba sin completarlo, la pasada previa del backfill lo para en `BACKFILL_BLOCKED` con los
  huecos ordenados por cuántas reservas bloquean.
- Cada causa aparece **en la bandeja** de quien la resuelve — lo dicen los destinatarios (§11); de
  entrada, el rol `ai-admin` —, con el enlace a su pantalla; al resolverla desaparece de todas las
  bandejas.

## 6. Los mapeados que propone la IA

Al llegar a la puerta de mapeado, el alta pide al **agente de mapeado** una propuesta: en
*Mapping → Dictionary* las propuestas, con su confianza y su porqué. Nada entra en vigor sin una
persona; aprobar reanuda de golpe todo lo que esperaba. Una equivalencia equivocada se corrige
aprobando otra versión, y la que nunca debió existir se **retira**. Cada decisión queda en *Audit*.

**El catálogo de MRU01 está importado del de XMAR**, para que el agente encuentre siempre su pareja:
los 15 tipos de habitación de XMAR, 8 de sus tarifas (venta directa, OTA, turoperador), sus 4
regímenes de verdad (de sus 26 paquetes: `NONE`, `BKF`, `FOOD`, `PENSTI`), 8 canales, 5 formas de pago
y 5 motivos de cancelación — con **códigos y nombres propios del CRS** (`JS-SEA` «Junior suite con
balcón y vista al mar» ↔ `SJMB`, `DIRECTA` ↔ `406484DIRXM` «DIRECTOS XMU A26», `TODO-INCLUIDO` ↔
`PENSTI`…), así que empareja por significado, no por código. Los mercados no son del CRS: cada canal
lleva el suyo como atributo. MRU01 vende solo con esos códigos; PMI01 y CUN01, con los de la cadena
(`BAR`, `AD`, `CC`…). La pareja esperada de cada código está en
[`deploy/demo/crs-catalog/MRU01-expected-pairs.md`](../../deploy/demo/crs-catalog/MRU01-expected-pairs.md);
el catálogo se regenera con `python3 deploy/demo/crs-catalog/generate.py --live` (lee XMAR por el
conector, que ahora trae el nombre de cada tarifa) y queda versionado en
`systems/crs/booking/src/main/resources/crs-catalog/MRU01.json` — el CRS no llama a Opera al arrancar.

## 7. El backfill

*Relaunch backfill* en la integración: proyecta todas las reservas futuras del hotel; las que Opera
ya tiene en esa versión **no se escriben** (ni la reserva, ni el perfil del huésped), las que no, se
crean. «Ya tiene» es: encuentra el localizador del CRS como referencia externa **bajo el contexto de
la ejecución** (el de `ec-demo-run`); lo escrito bajo el de una ejecución anterior no se ve. Probado en XMAR: 3 reservas creadas, 2 intactas, ninguna duplicada al reanudar los procesos
retenidos.

## 8. Una reserva de punta a punta

Una reserva nueva de MRU01 por su *Central de reservas* (canal `CALLCENTER`; o por el chat del agente,
*«Crea 3 reservas en MRU01…»*, o con *Demo bookings* en la lista de reservas):

- En el motor: **un** `proyectar-reserva` por reserva. El CRS la crea confirmada y con sus cobros en
  un solo cambio (versión 1, un `booking-created`); cada cambio posterior es otro.
- En Opera (XMAR): la reserva con los códigos traducidos, tarifa fija por noche, el perfil del
  huésped, el del interlocutor cuando lo hay, y la versión del CRS en el UDF.
- **Cómo encontrarlas en Opera**: todas las que escribe la integración llevan **Custom Reference =
  `EC-DEMO1`** — en la búsqueda avanzada de reservas, ese filtro las lista todas. Una concreta, por el
  localizador del CRS en *Conf / Cxl / External*: va como referencia externa con el **contexto de la
  ejecución** — `ECDEMO1`, o `ECDEMO1-<MMddHHmm>` desde que `zero.sh` estrena uno en cada puesta a cero
  (ver [Resetear la demo](#resetear-la-demo)); nunca `CRS`, que es el del CRS real de este tenant. Las
  escritas antes del 2026-09-25 no llevan ninguna de las dos cosas.
- En el front office: la estancia, con su titular, leída de Opera (`proyectar-estancia`, segundos
  después): los nombres de Opera y, en `ec1.py show`, «from Opera <reserva> as modified <fecha>».
- En el CRS: dónde ha quedado en Opera.
- En *Customers*: los pasajeros resueltos contra el maestro de clientes.

### El recorrido de una reserva («Ver recorrido»)

Cada cambio de una reserva es **una** traza de OpenTelemetry que cruza el CRS, la integración, el
motor, el mapeado, el MDM, Opera y el front office (Tempo, dashboard *Booking traces* en Grafana).
`journey-service` la lee de Tempo desde dentro del clúster y la cuenta en palabras de negocio en la
consola del plano de datos: **Call center → la reserva → «Ver recorrido»**, la columna *Recorrido* de
las reservas de un cliente en *Clientes*, o *Recorrido de la reserva* en «En otros sistemas» de la
estancia en el front office. Ruta: `/journey/bookings/<localizador>`; `/journey/bookings` lista las
reservas con cambios en los últimos 7 días.

- **Tiempos**: *Hasta Opera* y *Hasta el front office* desde el cambio en el CRS, y el total.
- **Recorrido por sistemas**: un carril por sistema, cada salto una barra en el eje de tiempo; en ámbar
  lo que esperó (un candado de la reserva, una causa, reintentos), en rojo lo que falló; las líneas
  discontinuas, el momento en que Opera y la recepción la tuvieron.
- **Cambios de la reserva**, el más reciente primero (creada, modificada, cancelada, no-show, walk-in,
  backfill, reanudada tras resolver sus causas), cada uno con su recorrido; **Causas de esta reserva**.
- **Paso a paso**: cada salto con su hora, su desfase y su duración, y lo que hizo: las equivalencias
  usadas, cómo reconoció el MDM al cliente (nuevo, por email, por documento…), la reserva y el perfil de
  Opera con la versión del CRS escrita, la estancia y el contacto de Salesforce (que el MDM envía por su
  cuenta, fuera de la traza).
- **«Ver traza técnica»**: la misma traza en Grafana; y cada proceso, en *Admin → Processes*.

Las trazas llegan a Tempo unos segundos después: una reserva recién hecha dice *Sin trazas aún* o *En
curso* y la página vuelve a mirar sola.

## 9. El cliente se limpia en Salesforce

Los pasajeros de cada reserva se proyectan a Salesforce como contactos. Allí se fusionan los
duplicados (el golden record); la fusión vuelve al MDM (`ClienteConsolidado__e`), que aplica la
supervivencia y **propaga el código de cliente al perfil de Opera** de las reservas afectadas.

**O en recepción, al escanear el documento** (flujo 2). El cliente que vuelve con otro email es un
provisional en el MDM y un duplicado en Salesforce. En el front office, en su reserva, *Escanear* en
el titular: el escáner de demo reconoce por el nombre a un cliente de la cadena que ya tiene documento
y lee **ese** documento. El documento es la clave más fuerte, así que el MDM sabe que son la misma
persona:

1. Consolida el provisional en el cliente que tiene el documento, con la misma supervivencia que una
   fusión de Salesforce: alias, reservas re-apuntadas y `CustomersMerged`, que lleva el código a
   Opera y al front office. En *Customers → Consolidaciones* sale con vía `SCAN`.
2. Fusiona los dos contactos en Salesforce con `merge()` de la API SOAP (la misma que usa
   `dedup.py`). El contacto provisional va a la papelera con el superviviente como `MasterRecordId`,
   y Salesforce lo anuncia como cualquier fusión (`ClienteConsolidado__e`); el MDM la encuentra ya
   aplicada. Si Salesforce no deja fusionar, queda anotado en la consolidación y el duplicado se
   limpia a mano.

Solo fusiona si es seguro: el documento es de un único cliente, el pax no tenía otro documento y el
nombre del documento es el del cliente. Si algo no cuadra, el documento va como Case (ver 10).

**Cada contacto dice cuánto vale.** En Salesforce, la sección *Calidad del dato (MDM)* del contacto:
*Estado MDM* (Provisional/Consolidado/Anonimizado), *Calidad del dato* (Solo nombre / Con contacto /
Verificado (documento)) y *Origen* (CRS/Canal/Touroperador, del canal de su primera reserva). Tras
*Escanear*, el titular pasa a **Verificado (documento)** en un par de minutos. La regla de duplicados solo
compara contactos con email, teléfono o documento; la vista **Pendientes de identificar** lista los
«Solo nombre», y las de marketing (*Marketing: contactables*, *Cumpleaños de este mes*) los excluyen. Un
«Solo nombre» con solo reservas canceladas o no-show se **anonimiza** a los 30 días (el MDM guarda el
motivo; se ve en la ficha de *Clientes*).

**Sin sondeos.** Fusiones, decisiones, cambios de contacto y avisos llegan por la **Pub/Sub API** en
segundos, y tras un reinicio el MDM retoma desde el último *replay id*. Para enseñarlo: fusiona un par
de duplicados en Salesforce (o desmarca *Aviso activo* en un aviso) y mira *Customers →
Consolidaciones* (vía `EVENT`) o el aviso en la ficha. Las consultas de respaldo son diarias.

**El escáner de demo** no lee nada: se inventa un documento creíble y siempre el mismo para la misma
persona, sacado de su nombre. Si el pax ya tiene un documento real en la estancia, lee ese; si un
cliente de la cadena con su nombre tiene documento, lee el de ese cliente; si no, genera un DNI con
su letra correcta para un español o un pasaporte para el resto (con la nacionalidad de la reserva) y
una fecha de nacimiento de adulto o acorde con la edad del niño.

## 10. Recepción cambia los datos de un cliente

Dónde vive cada dato: **Salesforce es el maestro** del cliente; el **MDM** está delante (guarda las
**xref** — contacto de Salesforce, huésped del front office, perfiles de Opera — y una **proyección**
de los datos de Salesforce); el **front office** y **Opera** reciben esa proyección.

1. En el front office (detalle de la reserva o check-in) se cambian los datos del titular. El kárdex
   los guarda al momento y el titular, en el carril de huéspedes, lleva la marca **«Pendiente de
   Salesforce»** y una línea por campo cambiado («Teléfono: … — pendiente de Salesforce»); el
   formulario del kárdex los lista igual encima de los campos. Un documento inventado por recepción
   (`MAN-…`, `ESC-…`) no se propone.
2. El front office lo manda al MDM por Kafka (`customer-commands`, con su propio id de solicitud
   `CR-FO-…`, así que un reenvío no la duplica), y el MDM abre en Salesforce un **Case «Cambio de datos de cliente»**
   sobre el contacto, con los datos propuestos (sección *Cambio de datos de cliente (MDM)*).
3. En Salesforce se pone **Decisión** en *Aprobada* (se pueden corregir los datos antes) o
   *Rechazada* (con **Motivo**). Un flow aplica lo aprobado al contacto y anuncia la decisión.
4. Baja sola: el MDM actualiza su proyección. Aprobado, el front office deja el dato **sin marca**
   (se queda); rechazado, vuelven los datos del maestro y el titular lleva **«Rechazado en
   Salesforce»**, con lo propuesto, lo que se queda y el motivo del Case. Opera reescribe el perfil
   del huésped de las reservas del cliente **en su sitio**: el email y el teléfono cambian en la
   misma entrada, no se añade uno nuevo al lado.

**El escaneo no es un cambio que decidir** (flujos 2 y 3). *Escanear* en cualquier pax (titular o
acompañante) manda su documento al MDM por Kafka (`customer-commands`): tipo y número, nombre, fecha de
nacimiento, nacionalidad, y de qué reserva y pax es. Es dato de confianza:

- Lo que el cliente no tiene (documento, fecha de nacimiento, nacionalidad) se rellena al momento y
  llega al contacto de Salesforce (`Document_Type__c`, `Document_Number__c`, `Birthdate`,
  `Nationality__c`) **sin Case**.
- Lo que contradice al maestro (otro nombre, otra fecha de nacimiento, otro documento) no se pisa:
  el MDM abre un Case «Cambio de datos de cliente» con origen «… · documento escaneado», que se
  decide como los de arriba. Escanear otra vez lo mismo no abre otro.
- Los acompañantes son clientes del MDM (pasajeros de la reserva) y así tienen su contacto en
  Salesforce. Un pax que la reserva no traía pasa a ser cliente al escanearlo.

Cualquier cambio hecho a mano en el contacto de Salesforce baja igual. Qué enseñar en cada sitio:

| Dónde | Qué se ve |
| :---- | :-------- |
| Front office — la reserva del titular | «Pendiente de Salesforce» y una línea por campo; después sin marca (aprobado) o «Rechazado en Salesforce» con el motivo |
| Salesforce — el Case sobre el contacto | Los datos propuestos, qué cambia, de dónde viene; *Decisión* |
| Consola — *Customers* | El cliente con sus xref (Salesforce, front office, perfiles de Opera) y sus solicitudes de cambio |
| Opera | El perfil del huésped con el dato nuevo, en la misma entrada |

Probado en ec1 el 2026-09-24 con C-E572C893A59C (reserva CU838F): dos cambios aprobados en
Salesforce llegaron al front office y al perfil 20538296 de Opera; el segundo cambió el email en su
sitio. (Ese cliente y sus Cases se borraron al poner ec1 a cero.)

### 10 · avisos. Un aviso de recepción, y un kárdex rechazado a la salida

Los **avisos de recepción** son de Salesforce (un Case sobre el contacto con *Tipo de aviso*); se crean
allí o desde *Clientes*, y el front office los enseña al entrar y al salir.

1. **Consola → Clientes →** el titular de una llegada de hoy (`deploy/demo/demo-prep.sh seed
   arriving-today` si no hay) **→ Nuevo aviso** (un panel lateral): texto «Pedir el pasaporte original»,
   tipo **Bloqueante**, *Mostrar en el check-in* → **Guardar aviso**. (**Avisos** lista los suyos para
   editarlos o desactivarlos.) Sale *Pendiente de enviar* / *Enviado*;
   en unos segundos, **Confirmado** con el enlace a su Case en Salesforce (una llamada para escribirlo;
   vuelve por `AvisoRecepcionCambiado__e`, sin leer nada).
2. **Front office →** la reserva: el titular lleva «⛔ Aviso bloqueante: …» en el carril. **Confirmar
   check-in** abre el asistente por el paso **Avisos**; sin marcar **«He leído el aviso»** el check-in se
   rechaza; marcado, entra (auditado).
3. **Kárdex rechazado:** cambia el email del titular en recepción y, en Salesforce, pon el Case en
   *Rechazada* con un motivo. Al pulsar **Check-out**, arriba sale el bloque rojo: el campo, lo propuesto,
   lo que se queda y el motivo. El cobro se rechaza hasta pulsar **«Entendido»**. Un cambio aún
   *pendiente* avisa de que «la factura saldrá con el dato anterior».
4. El **agente de recepción** dice el aviso bloqueante antes de pedir confirmación del check-in, y el
   kárdex rechazado antes del check-out.

## 10 bis. No show: el hotel lo dice, el PMS lo anota y el CRS lo cobra

HLA F006, por la cadena: el no-show se detecta en el hotel, **sube al PMS** —el maestro de la
estancia—, el PMS lo sube al CRS —el maestro de la venta—, el CRS aplica su regla y el resultado baja
por la proyección de siempre.

1. En el front office, en la reserva (que llega hoy), se marca **No show** en cada huésped. Al marcar
   el último, el front office publica el evento `NoShowReported` (`front-office-events`, desde su outbox,
   en la misma transacción que la marca). La estancia dice «Opera: pendiente — no show enviado».
2. La integración pms-fo arranca **`registrar-no-show-pms`** (*Admin → Processes*): con el candado de la
   reserva, **anota el no show en la reserva de Opera** —un comentario «No show — reported by the front
   office of MRU01 (stay …) at …; the CRS applies its fee»— y la integración crs-pms lo **sube al CRS**
   (`report-no-show`). El estado «No Show» de Opera solo lo pone su auditoría nocturna: por OHIP no hay
   otra forma de anotarlo. Si Opera tiene a los huéspedes en casa, no es un no show: queda una causa.
3. Arranca **`registrar-no-show`**: el CRS **cancela la reserva como no show** (motivo `NOS`) y la deja
   costando el **25 % de su precio original** (configurable, `booking.no-show-fee-percent`). En *Call
   center* se ve cancelada, con su cargo y el precio original.
4. La cancelación baja sola (`proyectar-cancelacion`):
   - **Opera**: primero la reserva pasa a costar el cargo (sus noches, al 25 %) y después se cancela
     con el motivo **NOSHOW** («No Show»), con el cargo en la descripción.
   - **Front office**: la estancia pasa a **No show**, costando el cargo, y dice «Opera: no show
     anotado; el CRS aplica su cargo».

Hace falta la equivalencia `NOS → NOSHOW` (motivo de cancelación, MRU01): desde cero hay que
aprobarla en el alta, o la cancelación espera sin mapear en *Mapping → Dictionary*. Una reserva nacida en
Opera se anota en Opera y no sube (el CRS no la tiene). Un walk-in que el CRS aún no ha reservado se queda
en el front office.

Probado en ec1 el 2026-09-30 con **RBQ7DG** (Opera 39486178): `registrar-no-show-pms` →
`registrar-no-show` → `proyectar-cancelacion`, en 3 s; Opera cancelada con el comentario y NOSHOW; el
front office en No show costando 93,00 (el 25 % de 372,00).

## 10 quater. Check-in y check-out: el PMS los registra

El PMS es el maestro de la estancia: lo que hace recepción **sube** a Opera por el motor (reintentos,
causas), y lo que Opera tiene **vuelve** al front office por la proyección de la estancia. En la ficha
de la estancia, *En otros sistemas → Opera*, se ve dónde está: «pendiente — check-in enviado», «en casa
· hab. 5138», «salida registrada · factura XMAR385» o «rechazado (…) — el motivo de Opera».

**Check-in.** Recepción confirma el check-in (el botón, con las operaciones hechas, o el asistente). El
front office publica `GuestCheckedIn` y arranca **`registrar-checkin`**: con el candado de la reserva,
`assign-room` asigna en Opera la habitación que dio recepción (sin elegir, la primera que sugiere
Opera) y `check-in-reservation` hace el check-in. Al entrar, la estancia vuelve «en casa». Las
habitaciones que ofrece recepción son las de Opera del tipo de la reserva, con lo que dice su
housekeeping ahora: **XMAR solo asigna habitaciones inspeccionadas** — una «Limpia, sin inspeccionar en
Opera» se rechaza (FOF00081) —. Un rechazo es una causa con su aviso en la bandeja, y la estancia lo
dice; recepción puede **elegir otra habitación** (*⋯ → Cambiar habitación*): el check-in sube de nuevo
con ella, y al entrar se resuelve sola la causa de la anterior.

**Check-out.** Recepción cobra y confirma la salida; el front office publica `GuestCheckedOut` y
arranca **`registrar-checkout`**: `check-out-reservation` hace, con el **cajero de la integración**
(69721441, `OPERA_CASHIER_ID`), lo que Opera pide — si la salida no es su fecha de negocio, una **salida
anticipada** (que pasa la salida a hoy y postea la noche); **saldar el folio** con lo que cobró recepción
(un pago por lo que diga el folio de Opera, forma de pago `CASH`); **generar el folio** —la factura—; y
el check-out —. Después, `fetch-invoice` manda la **factura de Opera** al front office: número, fecha e
importe (XMAR385, 346 MUR).

**«Abrir factura».** En la estancia cerrada, *En otros sistemas → Factura*: abre en otra pestaña un PDF
servido por el front office (enlace firmado, caduca a las 8 h). Si Opera dio el documento de la factura,
es ese; **XMAR no guarda los documentos de sus folios** (el folio se genera, pero sin `storedFolioId`),
así que lo que se abre es la **«Factura proforma (front office)»**: el folio del front office, con el
número e importe de la factura de Opera y el aviso de que no es el documento del PMS, con **los dos
totales** —el del folio del front office y el de la factura de Opera— y si coinciden.

**Los cargos de recepción, al folio de Opera.** Cada cargo que recepción pone en el folio —los extras del
check-in, el late check-out (*Registrar petición → Late check-out*), un consumo (*Añadir cargo*)— sube a
Opera: `charge-posted` → **`registrar-cargo`** → `post-charge` lo postea en el folio de la reserva con el
cajero de la integración y el código de transacción de su tipo (late check-out, 1200 «Supl
Alojamiento»; minibar, 1402; room service y cenas, 1403; lavandería, 1516; lo demás, 1851 «Ingr.Serv.
Diversos»), con la referencia `FO:<línea>` — la clave de idempotencia: una línea se postea una vez —.
Anular una línea (*Gestionar folio → Anular*) la deja anulada en el folio y sube como `charge-voided` →
**`anular-cargo`** → el mismo importe en negativo en Opera. El alojamiento no sube: Opera cobra sus
noches. En *Gestionar folio*, cada cargo dice dónde está en Opera («Opera: en el folio · 88731245»). Así
el saldo que Opera cobra en el check-out incluye los cargos de recepción, y el total de su factura es el
del folio del front office; si aún difieren, la proforma lo dice y por qué (el alojamiento, que Opera
factura con su tarifa y, en una salida anticipada, solo las noches pasadas; un cargo rechazado o que llegó
tarde).

**Habitación lista.** El paso de habitación ofrece primero las habitaciones **listas** en Opera —libres e
inspeccionadas, lo que XMAR exige—; las libres que aún no lo están salen en gris con el motivo («No lista
— Limpia, sin inspeccionar en Opera») y se pueden elegir a propósito; las ocupadas no. En la estancia que
llega, un aviso dice «✓ Habitación 5142 lista — Inspected · Vacant en Opera» o por qué no, con
**«Comprobar»**.

**Ojo, la fecha de negocio de Opera.** La de XMAR es **2026-05-13** y no avanza (es un UAT sin auditoría
nocturna): Opera solo hace el check-in de lo que llega ese día. Una reserva que llega hoy por el
calendario se **rechaza** en el check-in (FOF00067 «The guest's arrival is not scheduled for today»):
queda una causa que no se puede resolver hasta que Opera cambie de día. Para el check-in y el check-out,
`demo-prep.sh seed arriving-opera-today`: una reserva `JS-SEA` (SJMB, con habitaciones inspeccionadas)
que llega en la fecha de Opera, y las habitaciones que Opera tiene inspeccionadas y libres. El no show no
depende de la fecha de Opera.

Probado en ec1 el 2026-09-30 con **Z9HJRJ** (Opera 39485828), desde la pantalla: check-in en la 5138 →
`registrar-checkin` en 2 s, Opera *InHouse* en la 5138, el front office «en casa · hab. 5138»; check-out
→ `registrar-checkout` en 4 s (salida anticipada, folio saldado y generado), Opera *CheckedOut*, factura
**XMAR385** (346 MUR), el front office «salida registrada · factura XMAR385» y la proforma. Y con
**FNPYDW**: la 206 («Limpia») rechazada (FOF00081), la causa en la bandeja, otra habitación (5136) desde
la pantalla y la causa resuelta sola al entrar. En el *Recorrido* de la reserva: «Check-out en
recepción → La integración pms-fo lo recibe → Proceso «Registrar check-out» → Hacer el check-out en
Opera».

## 10 ter. Walk-in: el front office vende, el CRS reserva

El CRS es el dueño de todas las reservas; el front office es un canal más (`WALKIN`).

1. En el front office, *Reservas → ＋ Walk-in*: habitación, tarifa y régimen **del CRS** para MRU01,
   fechas (llegada hoy), ocupación y titular con su documento.
2. **Calcular precio**: el front office lo pregunta al CRS (`crs-integration-service` →
   `POST /bookings/quote`), que lo calcula como si la hiciera, sin guardar nada. Si luego cambia algo
   que afecta al precio, hay que volver a pedirlo.
3. **Confirmar walk-in**: la estancia se abre **ya** con referencia propia `FO-XXXXXX` —se puede hacer
   el check-in al momento— y se pide al CRS la reserva con esa referencia y **al precio dado**; si el
   CRS ya no la cobra así, la rechaza y la estancia lo muestra. Si el CRS no responde, se reenvía sola.
   La cabecera de la estancia dice «Walk-in · pendiente del CRS», y luego «Walk-in · CRS <localizador>».
4. Baja como cualquier reserva (`proyectar-reserva`): a **Opera**, y de Opera al **front office**
   (`proyectar-estancia`), que la reconoce por el localizador que el CRS le dio al walk-in y la escribe
   **sobre la misma estancia** (sin crear otra ni deshacer el
   check-in): la cabecera pasa a «Walk-in · CRS <localizador> · Opera <reserva>» y el huésped pasa a ser
   el cliente del MDM, con el documento que tomó recepción.

Hace falta la equivalencia `WALKIN → WLK` (canal, MRU01), que el agente propone en el alta. El CRS no
modela disponibilidad: el presupuesto es un precio, no una habitación bloqueada.

## Flujo 6. Opera no responde: un proceso bloqueado espera, no falla

Los cinco flujos de la grabación (1, alta: §4–7; 2, cliente que vuelve: §9; 3, cambio de datos: §10;
4, no show: §10 bis; 5, walk-in: §10 ter) siguen con estos tres. Todos con la integración MRU01 → XMAR
**activa** (después del flujo 1).

Se corta de verdad la red entre el conector y Opera, sin tocar Opera: una NetworkPolicy
(`demo-opera-outage`) deja a `pms-integration-service` hablar solo con los pods del clúster (Kafka, los
servicios, DNS); Cilium la aplica y el script lo comprueba desde el pod antes de dar el corte por hecho.

1. **Antes** (fuera de cámara, ~1 min): `deploy/demo/opera-outage.sh on --alert-after 2m`. Baja el
   umbral del aviso de reintentos de 10 min (el del manifiesto) a 2 — eso **reinicia el conector** — y
   corta la red. El corte se levanta solo a los 15 min (`--auto-off`) si nadie lo hace: ec1 lo usan más.
2. Una reserva nueva de MRU01 (Call center, o `python3 deploy/demo/ec1.py book --channel WEB --room
   STD-KING --rate DIRECTA --board DESAYUNO --arrival 2026-11-10`).
3. **Espera, no falla** (*Admin → Processes*): el `proyectar-reserva` se queda en *Asegurar el perfil del
   huésped* con los intentos subiendo; no hay causa (un fallo transitorio no es una causa: se reintenta).
   Cada intento tarda ~30 s (el timeout de conexión con OHIP) y el motor vuelve a lanzarlo a los ~10 s:
   un intento cada ~40 s.
4. **Alguien se entera**: pasado el umbral, en el siguiente fallo, llega a la bandeja (y al email
   urgente, según *Recipients*) **«Writing <localizador> to the PMS keeps failing»**
   (`RETRYING_TOO_LONG`). Con `--alert-after 2m`, a los ~2 min 40 s de la reserva.
5. `deploy/demo/opera-outage.sh off`: en el siguiente reintento (≤ 40 s; en la prueba, 1 s) la reserva
   llega sola a Opera y al front office, **una vez**, y el aviso de la bandeja se resuelve solo.
6. Después: `deploy/demo/opera-outage.sh alert 10m` (reinicia el conector) o `off --restore-alert`.
   `demo-prep.sh` avisa si el umbral no está en 10m o si el corte sigue puesto.

`python3 deploy/demo/ec1.py show <localizador>` enseña de una vez el CRS, los procesos con su paso e
intentos, las causas, los avisos de la bandeja, Opera por localizador y la estancia.

**En pantalla**: la reserva → **«Ver recorrido»**: el paso de Opera en ámbar con sus reintentos y
*Hasta Opera* en minutos (ver [El recorrido de una reserva](#el-recorrido-de-una-reserva-ver-recorrido)).

Probado en ec1 el 2026-09-27: corte 17:42:35–17:46:25Z (4 min); reserva **ZMPBEY** a las 17:42:55;
cinco intentos fallidos de `ensure-guest-profile` («OHIP unreachable for a token … Connect timed
out»); aviso a las 17:46:06; al levantar el corte, en Opera a las 17:46:26 como **39484599** (una sola
bajo el localizador), en el front office y el aviso resuelto a las 17:46:27; ninguna causa.

## Flujo 7. Modificar y cancelar desde el CRS

El CRS cambia la reserva y la misma reserva de Opera cambia **en su sitio**: se busca por el
localizador, la versión del CRS va en el UDF (`UDFN01`) y solo se escribe una versión más nueva.

1. Una reserva de MRU01 en Opera (la del flujo 6 sirve).
2. **Modificar** en *Call center* (editar la reserva) o `python3 deploy/demo/ec1.py modify <localizador>
   --arrival 2026-11-12 --nights 4 --room JS-STD`: fechas y tipo de habitación. El CRS la reprecia y
   sube la versión.
3. Se ve: un `proyectar-reserva` más; en Opera **el mismo número de reserva** con las fechas, el tipo
   (`SJSB`) y la versión nuevas; en el front office la estancia con las fechas y la habitación nuevas,
   **leídas de Opera**: llega por el evento del conector y otra vez por el sondeo (dos
   `proyectar-estancia` con claves distintas; la segunda no cambia nada).
4. **Cancelar** en *Call center* o `ec1.py cancel <localizador> --reason OTR`: `proyectar-cancelacion`;
   Opera la cancela (motivo `OTROS`) y la estancia pasa a *Cancelada*.

Ojo con la **disponibilidad de XMAR**: Opera rechaza una modificación a un tipo sin habitaciones libres
esas noches (`RSV00138` «There are not enough rooms available on Room Type level»). No falla: es una
causa `PMS_REJECTED` con su aviso, y la reserva espera. Si después el CRS la cambia a algo que Opera sí
acepta, esa versión entra y **resuelve sola la causa de la anterior** (desde pms-integration 0.29.0), que
termina sin escribir nada. Para no llevarse sorpresas, qué tipos vende Opera esas noches: `python3
deploy/demo/opera.py availability XMAR 2026-11-13 2026-11-17` (solo GET; `STDK` no sale en esa lista y
aun así entra: las suites `SJ…` y `STD-KING` son las que aceptaron en noviembre).

Probado en ec1 el 2026-09-27 con **ZMPBEY** / Opera **39484599**: v2 a `JS-SEA` rechazada por Opera
(RSV00138, causa y aviso); v3 a `JS-STD` y del 12 al 16 de noviembre, en su sitio (`SJSB`, UDF 3); v4
del 13 al 17, en su sitio, y la causa de la v2 resuelta sola («pms-integration: v4 is in Opera»); la
cancelación, en Opera *Cancelled* y en el front office *CANCELLED*. Siempre una sola reserva en Opera.

Probado en ec1 el 2026-09-27 con la integración pms-fo ya activa: **5CMMKN** (MRU01, WEB, DIRECTA,
STD-KING, DESAYUNO, 10–13 de noviembre) en Opera como **39484606** y en el front office por el evento
(«Standard King / BRKFST», 558,00); modificada en el CRS a JS-STD del 12 al 16: Opera `SJSB`, UDF 2, y
el front office «Suite Junior Standard Balcón», 1090,00, con la versión de Opera `2026-09-28T00:44:46`
— la que trajo el sondeo (`proyectar-estancia:XMAR:39484606:2026-09-28T00:44:46`, cursor movido a esa
modificación).

## Flujo 8. Un código nuevo con la integración ya activa

El día a día después del alta: el equipo de producto abre una **tarifa nueva** en el CRS y la integración
se entera sola, por la primera reserva que la usa.

1. **Tarifa nueva en el CRS**: `python3 deploy/demo/ec1.py rate-plan MRU01 EMPLEADOS-27 "Empleados de la
   cadena de vacaciones 2027" 0.5` (`POST /catalog/hotels/MRU01/rate-plans` del CRS; se guarda, se
   vende al momento y el catálogo la lista). Repetirlo no cambia nada.
2. En *Mapping → Dictionary*, filtrando por MRU01, aparece **sin mapear** (*Unmapped*).
3. **Una reserva con ella** (Call center: la tarifa ya está en el asistente; o `ec1.py book --room JS-STD
   --rate EMPLEADOS-27 --board SOLO-ALOJAMIENTO --arrival 2026-11-20`). Se queda esperando: causa
   `MISSING_MAPPING:MRU01:RATE_PLAN:EMPLEADOS-27` en *Mapping → Causes* y su aviso en la bandeja (rol
   `ai-admin`), que lleva a la pantalla.
4. **Ask the agent** en el diccionario: propone `432040HLXMU` «STAFF ON HOLIDAY XMU A27» (confianza
   0,95: «Corresponde exactamente a la tarifa de empleados de vacaciones para 2027…»). Tarda ~10 s.
5. **Aprobar solo esa** propuesta: la causa se resuelve, el aviso se va y la reserva sigue sola hasta
   Opera (con la tarifa `432040HLXMU`) y el front office, en segundos.

La tarifa de Opera es de XMAR y se eligió por leerla (solo GET) entre las de la propiedad: vende del
2026-01-01 al 2027-10-31. Sus tipos de habitación son suites: con `JS-STD` entra. `zero.sh` borra las
tarifas abiertas así (tabla `catalog_rate_plan` del CRS): desde cero, el flujo 8 se puede repetir con el
mismo código; sin poner a cero, con otro (una tarifa ya mapeada no vuelve a esperar).

Probado en ec1 el 2026-09-27: `EMPLEADOS-27` abierta a las 17:55; reserva **66AYZ5** (JS-STD, 20–23 de
noviembre, solo alojamiento) esperando a las 17:55:41; propuesta del agente en 11 s; aprobada a las
17:57:07 y en Opera como **39484600** (`SJSB` / `432040HLXMU`) y en el front office.

## 11. Quién hizo qué, y qué me espera

- *Audit*: cada acción que decide algo sobre un hotel — alta, aprobar o retirar un mapeado, activar,
  pausar, backfill, resolver una causa — hecha o rechazada, por consola, API o agente, con quién,
  cuándo, parámetros y respuesta. Solo lectura.
- *Inbox* (desde el aviso de la barra superior): los avisos que son para mí — por nombre o por uno de
  mis roles — y las **tareas del motor de formularios** (una tarea es un aviso más, en la bandeja de los
  roles que pide su formulario). Al pulsar una fila se ve el detalle entero; *Open* lleva
  a la pantalla que lo resuelve y lo marca como **visto**, igual que *Mark as seen* sobre las filas
  seleccionadas. Visto es de cada persona y no resuelve nada: la fila pierde la marca *New* y deja de
  contar en el aviso, pero sigue en la bandeja hasta que se resuelve, y entonces se va sola.
- **Quién se entera de qué, y por dónde**: lo decide **una sola tabla**, *Notifications → Recipients*.
  Cada destinatario dice **a quién** (usuarios de Keycloak y/o roles, o una dirección de email),
  **qué** (tipos de aviso — ninguno, todos —, si también las tareas, y un hotel — vacío, todos) y **por
  dónde**: la **bandeja** de sus personas, la **notificación push** de sus navegadores en las consolas
  (*Browser (Web Push)*; un clic en la notificación abre la pantalla que lo resuelve) o en **recepción**
  (*Browser at the front desk*: los navegadores del front office), **email** o los
  **espacios de Google Chat** que nombra. Cada aviso llega a todos los destinatarios activos que lo
  quieren, una vez por persona, navegador, dirección y espacio; **urgente** es lo que alguien pidió por
  email. No queda nada de esto en la configuración del despliegue.
- De entrada (tabla vacía) hay tres: *Integration administrators* (rol `ai-admin`, bandeja y push, todos
  los tipos), *Google Chat* (los dos espacios, todos los tipos y las tareas) y *Urgent, by e-mail*
  (Opera rechaza una escritura, un reintento que no acaba). Qué enseñar: crear uno para un hotel — p. ej.
  el rol de recepción de MRU01 solo con sus causas, por push — y ver que un aviso de ese hotel le llega
  y uno de otro, no.

### Avisos en el navegador (Web Push)

Cada persona los activa **en cada navegador** — y en cada consola: son orígenes distintos —, y lo que
recibe lo deciden los destinatarios de arriba.

- **Activarlos**: la primera vez, abajo a la izquierda aparece «¿Avisos en este navegador? **Activar
  avisos**» (✕ lo descarta para siempre en ese navegador). Siempre están en el **menú de usuario**
  («Hola, …» arriba a la derecha): la línea **Avisos** dice el estado de este navegador — *activados*,
  *desactivados*, *bloqueados por el navegador* (se desbloquean en el candado de la barra de
  direcciones, y se recarga) o *no disponibles* — con **Activar avisos**, **Desactivar** y **Enviarme
  una prueba**.
- **Probar**: *Enviarme una prueba* manda, al momento, una notificación «Prueba de avisos» a ese
  navegador (solo al tuyo; no pasa por la bandeja ni por los destinatarios). Desde el chat del plano de
  control, la herramienta `sendTestPush` del MCP de comunicación la manda a **todos** los navegadores de
  un usuario (p. ej. «mándale una prueba de avisos a demo»). Un aviso de verdad llega a la vez que entra
  en la bandeja (≈5 s).
- **Qué llega a quién**: *Integration administrators* (rol `ai-admin`, bandeja y push) lleva todos los
  avisos de la integración a los navegadores de las **consolas** de quien tenga ese rol — `demo` lo
  tiene. A **recepción** (front office) **no llega nada hoy**: ningún destinatario usa *Browser at the
  front desk*. El navegador del front office es un canal aparte para que la misma persona, con las dos
  cosas abiertas, no reciba todo dos veces, y para que al mostrador solo le llegue lo que alguien decida
  que es suyo (p. ej. un destinatario *PMS_REJECTED* de MRU01 por *Browser at the front desk*).
- **Para la demo**: en el Chrome de la demo, entrar en `console.ec1.mateu.io`, *Activar avisos* →
  *Permitir*, y *Enviarme una prueba*. macOS: Chrome necesita permiso de notificaciones en *Ajustes del
  Sistema → Notificaciones → Google Chrome*, y *No molestar* apagado; si no, Chrome la acepta y no se ve.

## 12. Casos de negocio propuestos *(pendientes de decidir)*

De los comentarios de negocio, propuestos como H14–H17: check-in en 4 pasos; cliente nuevo en
recepción → MDM/CRM; penalización de cancelación decidida por Comercial; pago diferido con Gestión
de Cobros y factura de depósito.

## Resetear la demo

Dos scripts en `deploy/demo/`, y ninguno toca Opera:

- **`zero.sh` — antes de cualquier integración** (unos 3 minutos). Vacía lo que hace la integración:
  reservas del CRS (y las tarifas abiertas después, flujo 8), integraciones (crs-pms y pms-fo, con su cursor), mapeados, MDM, huéspedes, estancias y catálogo del PMS del front office (las
  habitaciones quedan libres), avisos, auditoría y los procesos del motor; y en Salesforce borra
  **todos** los contactos y los Cases del MDM (el org se comparte con el entorno local). Se queda lo
  que está configurado: interlocutores del ERP, habitaciones y catálogos del front office,
  definiciones, usuarios y Keycloak. El front office ya no se rellena solo con huéspedes de muestra.
- **`reset.sh` — a la línea base** (unos 4 minutos), la que guarda `snapshot.sh` en
  `~/.local/share/ec-demo1/demo-baseline`: restaura las bases de datos de nuestros servicios y el
  estado del motor, borra en Salesforce los contactos y Cases que creó la demo y devuelve los de la
  línea base a sus datos.

**Hoy no hay línea base**: la anterior (06:58Z, con MRU01 ↔ XMAR activa) se apartó a
`demo-baseline-pre-zero` al poner ec1 a cero, para que `reset.sh` no la trajera de vuelta; sin línea
base, se niega a arrancar. Cuando el alta desde cero esté recorrida, se toma otra (`snapshot.sh`, con
nada en marcha).

Opera no se toca ni para resetear: ni se cancela ni se borra nada. Por eso la demo se hace para
repetirse encima de lo que dejó escrito — reservas y partners se buscan antes de escribir (por
localizador y por CorporateId), y durante la demo solo se modifica **lo que se crea en la demo** (una
reserva nueva de MRU01, y el cambio de datos sobre **su** titular), nunca lo de la línea base. Cada
demo deja en XMAR una reserva y un perfil de huésped.

**Un contexto de Opera por ejecución.** El localizador del CRS va en Opera como referencia externa
bajo un contexto nuestro, y por él se busca la reserva antes de escribirla. Como Opera no se limpia,
tras poner ec1 a cero un localizador nuevo (aleatorio) podría repetir uno antiguo y la integración
**actualizaría la reserva vieja**. Por eso cada `zero.sh` estrena un contexto, `ECDEMO1-<MMddHHmm>`
(UTC; 16 caracteres, mayúsculas, dígitos y guion: OHIP admite hasta 80 y OPERA busca por el contexto
exacto, sin distinguir mayúsculas), y lo deja en el ConfigMap **`ec-demo-run`** (clave
`OPERA_EXTERNAL_SYSTEM`) antes de arrancar los servicios; `pms-integration-service` lo lee de ahí
(`envFrom`, opcional) y, sin ConfigMap, usa `ECDEMO1`. La línea base se escribió bajo el contexto de su
momento: `snapshot.sh` lo guarda con ella (`opera-context`) y `reset.sh` lo repone, así que tras un
reset se siguen encontrando sus reservas (una línea base sin ese fichero es de `ECDEMO1`). El contexto
en curso: `kubectl -n ec-demo1 get cm ec-demo-run -o jsonpath='{.data.OPERA_EXTERNAL_SYSTEM}'`. No
afecta a los perfiles: los de interlocutor se encuentran por su **CorporateId** y los de huésped a
través de su reserva (el tenant no admite referencias externas en perfiles, `OPERA_PROFILE_REFERENCES=false`),
así que una ejecución nueva reencuentra los interlocutores que ya estaban; cada reserva nueva lleva su
perfil de huésped nuevo.

Las bases de datos ya no se pierden al mover un pod: el PostgreSQL del motor está en un volumen desde
el 2026-09-25 (antes, en un `emptyDir`, se perdieron el 24 al actualizar el motor con el chart
equivocado — `deploy/chart/eventconductor/VENDORED.md`). Solo las pierde borrar su PVC.

## Probar la demo de punta a punta

Desde `e2e/` (usuario `demo` de Keycloak; credenciales de Opera y Salesforce en `~/.config/ec-demo1/`):

- `npx playwright test` — **las pantallas**: las cuatro consolas y cada una de sus pantallas (menús,
  bandeja, auditoría…). No escribe nada; se puede lanzar siempre.
- `npm run demo` — **la historia de la demo** contra ec1, y al final el reset a la línea base
  (`E2E_RESET=0` para no resetear). Unos 5 minutos:
  1. una reserva nueva de MRU01 llega a Opera (XMAR) y al front office (se busca por localizador en su
     listado);
  2. el cambio de datos de su titular pasa por Salesforce (Case aprobado) y baja al front office y al
     perfil de Opera, en su sitio;
  3. un **no show** de esa reserva: el CRS la cancela con su cargo del 25 % y lo que cuesta llega al
     front office y a Opera (cancelada con NOSHOW, sus noches sumando el cargo);
  4. una acción auditable aparece en *Audit*;
  5. la bandeja muestra lo que espera (las causas de CUN01). *Este paso ya no puede pasar*: un hotel
     sin integración no deja causas (ver §3); hay que cambiarlo por algo que sí espere.

  Hoy no se puede lanzar: necesita el alta de MRU01 hecha y una línea base nueva (su teardown hace
  `reset.sh`). Deja en XMAR una reserva y un perfil por ejecución (las reglas de la demo); en Salesforce, nada
  después del reset.

## Preparación de la demo

**`deploy/demo/demo-prep.sh`** — un comando para preparar una demo o un ensayo:

- `demo-prep.sh` (o `health`): la tabla PASS/FAIL — despliegues listos, motor, token de Opera y XMAR
  legible (GET), token de Salesforce (GET) y lo que le queda de **cupo diario de API** (WARN por debajo de
  `SF_API_RESERVE`, 1000 por defecto; FAIL si está agotado), clientes del MDM pendientes o fallidos en
  Salesforce (y cambios de contacto por leer), estado de la integración de MRU01 y de la pms-fo de XMAR
  (ninguna tras `zero.sh`: ahí empieza el flujo 1; su último sondeo y su cursor), diccionario y causas abiertas, que no quede un corte de Opera puesto ni el
  umbral del aviso bajado, el contexto de Opera y las habitaciones libres del front office. Sale con 1
  si algo falla. `--zero` pasa antes `zero.sh`; sin él no se resetea nada.
- `demo-prep.sh seed returning-customer [--create]` (flujo 2): a quién teclear en el asistente — el
  titular de una reserva del flujo 1, mismo nombre y teléfono, **otro email** —; `--create` la hace por
  la API.
- `demo-prep.sh seed arriving-today` (flujo 4): una reserva que llega hoy, esperando a que esté en Opera
  y en el front office, para el no show.
- `demo-prep.sh seed arriving-opera-today` (§10 quater): una reserva que llega en la **fecha de negocio
  de Opera** (2026-05-13 en XMAR), la única que Opera deja hacer check-in, y las habitaciones que tiene
  inspeccionadas y libres.
- `demo-prep.sh seed walk-in` (flujo 5): nada que crear; los datos a teclear y las habitaciones libres.

Se pueden lanzar a mitad de demo y repetir: lo que crean lleva la marca `demo-prep:<semilla>` en los
comentarios de la reserva y se reutiliza. Para los flujos 6–8, `opera-outage.sh` y `ec1.py` (book,
modify, cancel, show, rate-plan, ask-agent, proposal); `opera.py` lee Opera (solo GET: también
`business-date`, `rooms XMAR SJMB`, `reservation XMAR <id>` —estado, habitación y si Opera haría el
check-in ahora— y `folios XMAR <id>`, la factura).

### El cupo diario de la API de Salesforce

La org es una Base Edition: **15.000 llamadas en 24 h móviles** (`DailyApiRequests`), contando toda
llamada REST o SOAP de cualquiera — el MDM, los scripts, las pruebas, un `curl`. Pasado el cupo,
Salesforce contesta `REQUEST_LIMIT_EXCEEDED` a todo hasta que las llamadas de hace 24 h salen de la
ventana. No cuentan: pedir el token, los eventos por Pub/Sub (la suscripción del MDM) ni lo que se hace
a mano en la consola de Salesforce. El 2026-09-27 se agotó (a las 20:57Z quedaban 0; a las 21:05Z,
566 de 15.000).

Lo que gasta cada cosa (llamadas):

| Qué | Llamadas |
|---|---|
| MDM en reposo | ~100/día: el sondeo de fusiones cada 15 min (antes cada minuto, 1.440/día) y, solo mientras haya un Case abierto, el de decisiones cada 5 min (antes cada 30 s, 2.880/día) |
| Proyectar clientes | 1 por cada 200 pendientes (sObject Collections; antes 1 por cliente) |
| Flujo 1 (alta de MRU01, 10 reservas) | ~5: los titulares a Salesforce en uno o dos lotes |
| Flujo 2 (cliente que repite) | ~5: su contacto, y al fusionar en Salesforce el MDM lee los dos contactos (2) |
| Flujo 3 (recepción cambia datos) | ~5: el Case, y al decidirlo leer el contacto (y el motivo si se rechaza) |
| Escanear un documento | 1 si cambia datos (va en el siguiente lote); 1 SOAP `merge()` si fusiona |
| `demo-prep.sh health` | 2 (contar contactos y leer el cupo) |
| `zero.sh` / `reset.sh` | 2–3 consultas + 1 por cada 200 contactos o Cases borrados (antes 1 por registro) |
| Una consulta del front office al MDM (p. ej. el backfill pms-fo) | 0: el MDM contesta de su base de datos |

Una demo completa gasta menos de 100; el umbral de 1000 de `demo-prep.sh` deja para ensayos y pruebas.

**Si se agota:** el MDM no insiste. La primera negativa pausa todas sus llamadas 5 min, el doble en cada
negativa seguida hasta 1 h, y deja **un aviso** en el buzón de los administradores de la integración
(«Salesforce: daily API allowance spent»), que se cierra solo cuando Salesforce vuelve a contestar.
Mientras, nada se pierde: los clientes siguen *pendientes* (no fallidos), los contactos que Salesforce
dijo que cambiaron quedan marcados para leerlos, las fusiones y los Cases esperan, y el sondeo retoma
desde su cursor. Qué hacer:

1. `demo-prep.sh health` dice cuánto queda. Parar lo que llame a Salesforce por fuera (scripts, pruebas
   `npm run demo`, agentes).
2. Esperar: la ventana es móvil y el cupo vuelve a medida que salen las llamadas de hace 24 h — en la
   práctica, se recupera a lo largo de la mañana siguiente si el gasto fue por la tarde.
3. Al volver, el MDM se pone al día solo (primer intento tras la pausa). `MDM → Salesforce` en el health
   debe acabar sin pendientes ni fallidos.
4. Si la demo no puede esperar, los flujos 1, 4–8 no usan Salesforce; el 2 y el 3 sí.

- [ ] **Recorrer el alta desde cero**, MRU01 → XMAR (en curso): conectividad, contraste, mapeados
      (incluida `NOS → NOSHOW`), interlocutores, backfill y activación. **Antes del alta, crear
      reservas futuras de MRU01** por `CALLCENTER` (con algún interlocutor): el CRS está vacío, y sin
      ellas el contraste, los interlocutores y el backfill no tienen nada que llevar. Mientras no haya
      integración se quedan en el CRS, sin proceso; el backfill las trae.
- [x] Con la primera reserva escrita, comprobar en Opera que acepta el contexto **`ECDEMO1`** en la
      referencia externa (no es un sistema externo del catálogo de OPERA; si lo rechaza, el proceso
      se para con la causa «rechazada por el PMS» y se vuelve a `CRS`) y que el filtro **Custom
      Reference = `EC-DEMO1`** la encuentra.
- [ ] Tomar la **línea base nueva** (`snapshot.sh`) y cambiar el paso 5 de `npm run demo`, que busca
      las causas de CUN01; después, pasar las dos baterías.
- [ ] Decidir la propiedad para el alta en directo (§4) y, si es XMU, sembrar un hotel del CRS con
      reservas futuras (`e2e/poc-acl-demo/seed.py`; cuidado: todo lo sembrado acaba en Opera).
- [ ] Comprobar el agente de la consola (MCP de `booking`) y el agente de mapeado con el LLM real.
- [ ] Destinatarios reales en *Notifications → Recipients*: el email de *Urgent, by e-mail* (hoy un
      `example.com`, y el correo falla) y qué espacio de Google Chat recibe qué (el segundo está bloqueado
      por su administrador).
- [x] Probar el **no show** (§10 bis) desde la pantalla: en una reserva que venga del CRS y llegue hoy,
      marcar No show en todos los huéspedes (RBQ7DG, 2026-09-30: por el PMS y el CRS).
- [x] Check-in y check-out desde la pantalla contra Opera (§10 quater): Z9HJRJ, 2026-09-30.
- [ ] Probar el §10 desde la pantalla del front office (con usuario) y el Case desde la consola de
      Salesforce. Ojo: el Case lleva la sección *Cambio de datos de cliente (MDM)*; para poder añadirla
      se quitaron del layout de Case las acciones y el panel de resumen propios (quedan los de por
      defecto).
- [ ] Solo el titular viaja al maestro: los acompañantes no llevan código de cliente en el front office.
- [x] Mateu **3.0-alpha.361** en todas las apps (renovación del token tras un 401, el botón «atrás»):
      desplegado; las pantallas, 67/67.
- [x] Menos nodos: de 7 a 3 (ec1 entero en un cx53 de hel1, la observabilidad en el suyo, y uno de
      sistema del clúster); fuera `swapi`, una app vieja y su balanceador.
- **Datos de prueba que quedan en XMAR, por contexto** (Opera no se limpia; todos los contextos de la
  integración, con Custom Reference `EC-DEMO1` salvo los de `CRS`):
  - `ECDEMO1` (hasta que un `zero.sh` estrene contexto): las 14 reservas del alta de MRU01 del
    2026-09-27 — 39484567, 39484568, 39484573–39484581, 39484593, 39484597 y 39484582 (cancelada,
    no show) —, localizadores TEX39V, BVJJR6, V5M48N, HTJFGX, FEH9WH, 7YCUWJ, FVJ43M, TZFX5P, GB5STT,
    WKCBR5, DM95Z8, KF6HFC, M46BRN y KTQVZJ, cada una con su perfil de huésped.
  - `ECDEMO1`, de los flujos 6–8 y `seed arriving-today` probados el 2026-09-27: 39484599 (ZMPBEY,
    modificada y cancelada), 39484600 (66AYZ5, tarifa `432040HLXMU`) y 39484601 (3PJ492, llega el 27),
    cada una con su perfil de huésped.
  - `ECDEMO1-<MMddHHmm>`: lo que escriba cada ejecución desde su `zero.sh` (sus reservas y sus perfiles
    de huésped); ampliar esta lista al estrenarlo.
  - `ECDEMO1-09280207`, el ensayo de la grabación (2026-09-28, 02:07–04:05Z), cada una con su perfil de
    huésped: flujo 1, 39484308 (87J8RP), 39484623 (2FJWNE), 39484622 (AYGMUM), 39484620 (9PFB85),
    39484624 (3N4YCX), 39484621 (MS5DRX), 39484311 (3SERFS), 39484309 (6EYQ6Q), 39484619 (X35U7X) y
    39484631 (EQ3GJT, rechazada primero por falta de habitaciones y movida al 24 de noviembre); flujo 2,
    39484315 (RJ3T5V); flujo 4, 39484632 (77SAJF, cancelada como no show); flujo 5, 39484633 (7T8AX8,
    walk-in FO-A3NVW4); flujos 6–7, 39484634 (WMBH5M, modificada y cancelada); flujo 8, 39484316
    (7HHHWD, tarifa `432040HLXMU`).
  - `ECDEMO1-09280410`, la grabación del vídeo (2026-09-28, 04:10–05:30Z), cada una con su perfil de
    huésped: flujo 1, 39484641 (CDETQY), 39484636 (MD33Y5), 39484639 (FT5KSW), 39484317 (VR6QV2),
    39484638 (S5PQJS), 39484640 (S5YRAR), 39484318 (Y4G937), 39484643 (A9W5HH), 39484644 (MRKKM8) y
    39484645 (2CNCYB) — las tres últimas, rechazadas primero por falta de habitaciones (RSV00138) y
    cambiadas de tipo en el CRS; flujo 2, 39484646 (AKCBNY); flujo 4, 39484647 (KMNQ28, cancelada como no
    show); flujo 5, 39484320 (N529R5, walk-in FO-3FSJ7G); flujos 6–7, 39484648 (DPUQKZ, modificada y
    cancelada); flujo 8, 39484649 (79RE8S, tarifa `432040HLXMU`).
  - `CRS`, de antes de poner ec1 a cero: reservas 39481284, 39481745, 39481775, 39481943, 39481944,
    39482155 (y dos canceladas, y las `E2E-<fecha>` de cada `npm run demo`, canceladas como no show),
    sin Custom Reference.
  - Sin contexto, comunes a todas las ejecuciones: los perfiles de interlocutor 20538292 y 20538322
    (ECDEMO0001/0002), que cada alta vuelve a encontrar por su CorporateId; el perfil de huésped
    20538296 conserva un email antiguo como secundario (la API de Opera no permite borrarlo).
- En Opera, lo que necesita un administrador de OPERA: la interfaz de las referencias externas de
  perfil (OPERAWS-GEN01187). Sin eso, los perfiles van sin referencia externa.
- El cajero (FOF00094 «Invalid Cashier»), comprobado el 2026-09-29: el usuario de integración de la
  app (`RIUC-UAT-RIUE-MTCE13UA-RIU_HOTEL_CLIENTE_OHIP@RIUE`) no tiene cajero propio, así que toda
  llamada de caja **sin** `cashierId` falla. Se creó por OHIP un cajero para la PoC en XMAR:
  **`69721441` «EC-DEMO1 Integración»** (`InterfaceCashier`, MUR), con
  `POST /fof/config/v1/cashiers` (el número, de `GET /fof/config/v1/cashiers/nextAvailable`).
  Uso: pasar `"cashierId": 69721441` dentro de `criteria` en las llamadas de `csh` — depósitos
  (`POST …/reservations/{id}/depositPayments`), check-out (`POST …/reservations/{id}/checkOuts`),
  generar el folio/factura (`POST …/reservations/{id}/folios`), pagos. Comprobado sin mover dinero con
  `PUT /csh/v1/hotels/XMAR/depositfolios/action/validate`: sin `cashierId` → FOF00094; con
  `69721441` → 200. Leer el folio (`GET /csh/v1/hotels/XMAR/reservations/{id}/folios`) no lo necesita.
  Asignarlo al usuario de integración por OHIP (`PUT /fof/config/v1/cashiers` con `appUsers`) da 500:
  si se quiere que sea el cajero por defecto (y no pasarlo en cada llamada), un administrador de OPERA
  debe asociarlo al usuario en OPERA Cloud (gestión de usuarios → Cashier ID). El conector aún no
  pasa `cashierId` (`OPERA_POST_DEPOSITS` sigue en `false`). Ojo: `GET /csh/v1/cashiers/{id}/locks`
  no es de solo lectura — toma un bloqueo del cajero (y lo abre); se suelta con
  `DELETE /csh/v1/cashiersLock/{lockHandle}`.

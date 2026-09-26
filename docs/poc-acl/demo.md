# PoC ACL — guión de la demo

Estado a 2026-09-25, tarde. Todo lo que se enseña está desplegado en `ec1.mateu.io` y escribe en el
**tenant real de Opera** (OHIP UAT, propiedad **XMAR**); ya no hay doble de Opera en el despliegue
(`opera-mock` queda solo para la batería local de pruebas). El motor es EventConductor **2.22.1** y
las apps, Mateu **3.0-alpha.364**. Lo marcado *(pendiente)* no está construido todavía o espera una
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
| 2 | Context Model TO-BE | Rumbo (CRS) ↔ integración ↔ Opera Cloud por OHIP; el ERP como maestro de interlocutores; Salesforce como motor de limpieza del cliente | `booking` hace de Rumbo, `partners` de ERP; Opera y Salesforce son los reales |
| 3 | Container Model TO-BE | Los servicios: ACL del CRS, mapeado, conector PMS, integraciones, MDM, comunicación, auditoría, motor | Un servicio por contenedor, y el front office del hotel |
| 4 | El modelo mental: dos planos | Plano de datos (lo que fluye) y plano de control (quién lo gobierna) | Dos consolas: `ec1` y `console.ec1` |
| 5 | Grabar Reserva — System Model | El camino de una reserva de punta a punta | «Proyectar reserva», contra Opera real y el front office |
| 6 | Proyectar una reserva (secuencia) | Preparar → identidad del cliente (MDM) → perfil → grabar con guarda de versión → front office → anotar en el CRS | Igual |
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
| Call center | El CRS simulado: una reserva con habitaciones, huéspedes, desglose diario, cobros y su referencia en Opera |
| ERP | El maestro de interlocutores; cada uno sabe **qué perfil es en Opera** (*Opera profile*); *Resync* |
| Admin | Los procesos del motor, con sus pasos |

**Front office del hotel — `https://front.ec1.mateu.io`** (Redwood): recepción. Las reservas de
MRU01 que llegan a Opera llegan también aquí como estancias; check-in, huéspedes, folios.

**Plano de control — `https://console.ec1.mateu.io`**: lo que gobierna la plataforma.

| Menú | Qué enseñar |
| :--- | :---------- |
| Integrations | Una integración por hotel: su conexión con Opera, en qué puerta del alta está, *Relaunch backfill*, *Import partners* |
| Mapping | Causes; Dictionary: se filtra por **integración** y muestra también lo **sin mapear** (*Unmapped*), *Ask the agent*, y aprobar, rechazar o **retirar** una entrada o las filas seleccionadas; Partners in the PMS |
| Customers | El maestro de clientes: golden records y consolidaciones que llegan de Salesforce |
| Notifications | Lo que se ha comunicado y a quién; **destinatarios**: quién se entera de qué y por dónde (§11) |
| Audit | Todas las acciones auditables: quién, cuándo, con qué parámetros y qué respuesta; búsqueda libre y filtros |
| Workflow / Forms | Las definiciones de proceso: los seis de la PoC (`alta-integracion`, `proyectar-reserva`, `proyectar-cancelacion`, `proyectar-interlocutor`, `registrar-no-show`, `verify-booking-payment`); formularios, hoy ninguno |
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

## 7. El backfill

*Relaunch backfill* en la integración: proyecta todas las reservas futuras del hotel; las que Opera
ya tiene en esa versión **no se escriben** (ni la reserva, ni el perfil del huésped), las que no, se
crean. Probado en XMAR: 3 reservas creadas, 2 intactas, ninguna duplicada al reanudar los procesos
retenidos.

## 8. Una reserva de punta a punta

Una reserva nueva de MRU01 en *Call center* (o por el chat del agente, *«Crea 3 reservas en MRU01…»*):

- En Opera (XMAR): la reserva con los códigos traducidos, tarifa fija por noche, el perfil del
  huésped, el del interlocutor cuando lo hay, y la versión del CRS en el UDF.
- **Cómo encontrarlas en Opera**: todas las que escribe la integración llevan **Custom Reference =
  `EC-DEMO1`** — en la búsqueda avanzada de reservas, ese filtro las lista todas. Una concreta, por el
  localizador del CRS en *Conf / Cxl / External*: va como referencia externa con el contexto
  **`ECDEMO1`** (no `CRS`, que es el del CRS real de este tenant). Las escritas antes del 2026-09-25 no
  llevan ninguna de las dos cosas.
- En el front office: la estancia, con su titular.
- En el CRS: dónde ha quedado en Opera.
- En *Customers*: los pasajeros resueltos contra el maestro de clientes.

## 9. El cliente se limpia en Salesforce

Los pasajeros de cada reserva se proyectan a Salesforce como contactos. Allí se fusionan los
duplicados (el golden record); la fusión vuelve al MDM (`ClienteConsolidado__e`), que aplica la
supervivencia y **propaga el código de cliente al perfil de Opera** de las reservas afectadas.

## 10. Recepción cambia los datos de un cliente

Dónde vive cada dato: **Salesforce es el maestro** del cliente; el **MDM** está delante (guarda las
**xref** — contacto de Salesforce, huésped del front office, perfiles de Opera — y una **proyección**
de los datos de Salesforce); el **front office** y **Opera** reciben esa proyección.

1. En el front office (detalle de la reserva o check-in) se cambian los datos del titular. El kárdex
   los guarda al momento y la reserva muestra **«Kárdex: Pendiente de aprobación»** con lo que cambia.
2. El front office lo manda al MDM, que abre en Salesforce un **Case «Cambio de datos de cliente»**
   sobre el contacto, con los datos propuestos (sección *Cambio de datos de cliente (MDM)*).
3. En Salesforce se pone **Decisión** en *Aprobada* (se pueden corregir los datos antes) o
   *Rechazada* (con motivo). Un flow aplica lo aprobado al contacto y anuncia la decisión.
4. Baja sola: el MDM actualiza su proyección; el front office pasa el kárdex a **Aprobado** (o
   **Rechazado**, y vuelven los datos del maestro); Opera reescribe el perfil del huésped de las
   reservas del cliente **en su sitio**: el email y el teléfono cambian en la misma entrada, no se
   añade uno nuevo al lado.

Cualquier cambio hecho a mano en el contacto de Salesforce baja igual. Qué enseñar en cada sitio:

| Dónde | Qué se ve |
| :---- | :-------- |
| Front office — la reserva del titular | «Kárdex: Pendiente de aprobación · email … → …», y después Aprobado o Rechazado |
| Salesforce — el Case sobre el contacto | Los datos propuestos, qué cambia, de dónde viene; *Decisión* |
| Consola — *Customers* | El cliente con sus xref (Salesforce, front office, perfiles de Opera) y sus solicitudes de cambio |
| Opera | El perfil del huésped con el dato nuevo, en la misma entrada |

Probado en ec1 el 2026-09-24 con C-E572C893A59C (reserva CU838F): dos cambios aprobados en
Salesforce llegaron al front office y al perfil 20538296 de Opera; el segundo cambió el email en su
sitio. (Ese cliente y sus Cases se borraron al poner ec1 a cero.)

## 10 bis. No show: el hotel lo dice y el CRS lo cobra

HLA F006: el no-show se detecta en el hotel, sube al CRS como estado, el CRS aplica su regla y el
resultado baja por la proyección de siempre.

1. En el front office, en la reserva (que llega hoy), se marca **No show** en cada huésped. Al marcar
   el último, el front office avisa al CRS: la reserva entera es un no show.
2. Arranca el proceso **`registrar-no-show`** (*Admin → Processes*): el CRS **cancela la reserva como
   no show** (motivo `NOS`) y la deja costando el **25 % de su precio original** (configurable,
   `booking.no-show-fee-percent`). En *Call center* se ve cancelada, con su cargo y el precio original.
3. La cancelación baja sola (`proyectar-cancelacion`):
   - **Opera**: primero la reserva pasa a costar el cargo (sus noches, al 25 %) y después se cancela
     con el motivo **NOSHOW** («No Show»), con el cargo en la descripción. (El estado «No Show» de
     Opera solo lo pone su Night Audit; por API es una cancelación.)
   - **Front office**: la estancia pasa a **No show**, costando el cargo.

Hace falta la equivalencia `NOS → NOSHOW` (motivo de cancelación, MRU01): desde cero hay que
aprobarla en el alta, o la cancelación espera sin mapear en *Mapping → Dictionary*. Solo reservas que vienen del
CRS.

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
  dónde**: la **bandeja** de sus personas, la **notificación push** de sus navegadores (la consola ofrece
  «Enable notifications» la primera vez; un clic abre la pantalla que lo resuelve), **email** o los
  **espacios de Google Chat** que nombra. Cada aviso llega a todos los destinatarios activos que lo
  quieren, una vez por persona, navegador, dirección y espacio; **urgente** es lo que alguien pidió por
  email. No queda nada de esto en la configuración del despliegue.
- De entrada (tabla vacía) hay tres: *Integration administrators* (rol `ai-admin`, bandeja y push, todos
  los tipos), *Google Chat* (los dos espacios, todos los tipos y las tareas) y *Urgent, by e-mail*
  (Opera rechaza una escritura, un reintento que no acaba). Qué enseñar: crear uno para un hotel — p. ej.
  el rol de recepción de MRU01 solo con sus causas, por push — y ver que un aviso de ese hotel le llega
  y uno de otro, no.

## 12. Casos de negocio propuestos *(pendientes de decidir)*

De los comentarios de negocio, propuestos como H14–H17: check-in en 4 pasos; cliente nuevo en
recepción → MDM/CRM; penalización de cancelación decidida por Comercial; pago diferido con Gestión
de Cobros y factura de depósito.

## Resetear la demo

Dos scripts en `deploy/demo/`, y ninguno toca Opera:

- **`zero.sh` — antes de cualquier integración** (unos 3 minutos). Vacía lo que hace la integración:
  reservas del CRS, integraciones, mapeados, MDM, huéspedes y estancias del front office (las
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

- [ ] **Recorrer el alta desde cero**, MRU01 → XMAR (en curso): conectividad, contraste, mapeados
      (incluida `NOS → NOSHOW`), interlocutores, backfill y activación. **Antes del alta, crear
      reservas futuras de MRU01** en *Call center* (con algún interlocutor): el CRS está vacío, y sin
      ellas el contraste, los interlocutores y el backfill no tienen nada que llevar. Mientras no haya
      integración se quedan en el CRS, sin proceso; el backfill las trae.
- [ ] Con la primera reserva escrita, comprobar en Opera que acepta el contexto **`ECDEMO1`** en la
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
- [ ] Probar el **no show** (§10 bis) desde la pantalla: en una reserva que venga del CRS y llegue hoy,
      marcar No show en todos los huéspedes.
- [ ] Probar el §10 desde la pantalla del front office (con usuario) y el Case desde la consola de
      Salesforce. Ojo: el Case lleva la sección *Cambio de datos de cliente (MDM)*; para poder añadirla
      se quitaron del layout de Case las acciones y el panel de resumen propios (quedan los de por
      defecto).
- [ ] Solo el titular viaja al maestro: los acompañantes no llevan código de cliente en el front office.
- [x] Mateu **3.0-alpha.361** en todas las apps (renovación del token tras un 401, el botón «atrás»):
      desplegado; las pantallas, 67/67.
- [x] Menos nodos: de 7 a 3 (ec1 entero en un cx53 de hel1, la observabilidad en el suyo, y uno de
      sistema del clúster); fuera `swapi`, una app vieja y su balanceador.
- Datos de prueba que quedan en XMAR de antes de poner ec1 a cero (Opera no se limpia): reservas
  39481284, 39481745, 39481775, 39481943, 39481944, 39482155 (y dos canceladas, y las `E2E-<fecha>`
  de cada `npm run demo`, canceladas como no show), con la referencia en el contexto `CRS` y sin Custom
  Reference; perfiles de interlocutor 20538292 y 20538322 (ECDEMO0001/0002), que el alta volverá a
  encontrar por su CorporateId; el perfil de huésped 20538296 conserva un email antiguo como
  secundario (la API de Opera no permite borrarlo).
- En Opera, lo que necesita un administrador de OPERA: la interfaz de las referencias externas de
  perfil (OPERAWS-GEN01187) y un cajero para los depósitos (FOF00094). Sin eso, los perfiles van
  sin referencia externa y los depósitos no se apuntan al folio.

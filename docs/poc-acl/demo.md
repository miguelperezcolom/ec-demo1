# PoC ACL — guión de la demo

Estado a 2026-09-24 (tarde). Todo lo que se enseña está desplegado en `ec1.mateu.io` y escribe en el
**tenant real de Opera** (OHIP UAT, propiedad **XMAR**); ya no hay doble de Opera en el despliegue
(`opera-mock` queda solo para la batería local de pruebas). Lo marcado *(pendiente)* no está
construido todavía o espera una decisión.

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
| 8 | Mapeado — System Model | Diccionario versionado, aprobación humana, propuesta del agente | Diccionario, Pending (por integración) y el agente |
| 9 | Alta de una integración (secuencia y estados) | Del registro a la activación, por puertas | `integrations-service` y el proceso `alta-integracion` |
| 10 | Maestro de clientes (HLA CRM-MDM) | Identidad al proyectar, limpieza y fusión en Salesforce, supervivencia y propagación | `customer-mdm-service` + Salesforce |
| 11 | Auditoría y bandeja | Quién hizo qué (F016); lo que espera a cada persona, con el enlace a la pantalla que lo resuelve | `audit-service`, bandeja en `communication-service` |

Fuera de la PoC, y conviene decirlo: la subida PMS → CRS (OOO, no-show, conciliación diaria), el
cobro de la penalización y el backfill de cupo.

## 2. Recorrido por las consolas

Dos consolas, una por plano (diagrama 4). Cada servicio trae sus pantallas y la consola las federa;
cada consola tiene su versión Redwood (`rw.` y `rw-console.`) con los mismos backends. En la barra
superior de las cuatro, el **aviso de la bandeja** («Inbox (n)»).

**Plano de datos — `https://ec1.mateu.io`**: lo que usa el negocio.

| Menú | Qué enseñar |
| :--- | :---------- |
| Call center | El CRS simulado: una reserva con habitaciones, huéspedes, desglose diario, cobros y su referencia en Opera |
| ERP | El maestro de interlocutores; cada uno sabe **qué perfil es en Opera** (*Opera profile*); *Resync* |
| Inbox | Lo que me espera: avisos y tareas de mis roles, cada uno con su enlace |
| Admin | Los procesos del motor, con sus pasos |

**Front office del hotel — `https://front.ec1.mateu.io`** (Redwood): recepción. Las reservas de
MRU01 que llegan a Opera llegan también aquí como estancias; check-in, huéspedes, folios.

**Plano de control — `https://console.ec1.mateu.io`**: lo que gobierna la plataforma.

| Menú | Qué enseñar |
| :--- | :---------- |
| Integrations | Una integración por hotel: su conexión con Opera, en qué puerta del alta está, *Relaunch backfill*, *Import partners* |
| Mapping | Causes; Pending (se elige la **integración**); Dictionary (aprobar, rechazar, **retirar**); Partners in the PMS |
| Customers | El maestro de clientes: golden records y consolidaciones que llegan de Salesforce |
| Notifications | Lo que se ha comunicado y a quién; destinatarios |
| Audit | Todas las acciones auditables: quién, cuándo, con qué parámetros y qué respuesta; búsqueda libre y filtros |
| Inbox | La misma bandeja, en la consola de control |
| Workflow / Forms | Las definiciones de proceso y de formulario |
| IA | El agente de mapeado y los MCP de cada servicio |
| Usuarios | Quién puede hacer qué |

Todos los listados paginan.

## 3. El punto de partida

MRU01 (el hotel del CRS) está integrado con **XMAR** (Opera) y activo: sus reservas viajan a Opera
en tiempo real. Otro hotel del CRS, sin integración, vende y sus reservas **esperan** en la causa
`INTEGRATION_INACTIVE:<hotel>`.

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
> escribe en Opera las reservas futuras del hotel. XMU está vacía y es la candidata; XMAR ya tiene
> MRU01 activo.

## 5. Se bloquea: espera, no falla

- La integración se queda en `MAPPING_PENDING`; los códigos, en *Mapping → Pending* eligiendo la
  integración, junto a lo que ofrece Opera.
- Si se aprueba sin completarlo, la pasada previa del backfill lo para en `BACKFILL_BLOCKED` con los
  huecos ordenados por cuántas reservas bloquean.
- Cada causa aparece **en la bandeja** de los roles que la resuelven (por defecto `ai-admin`), con el
  enlace a su pantalla; al resolverla desaparece de todas las bandejas.

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

Probado en ec1 con C-E572C893A59C (reserva CU838F): dos cambios aprobados en Salesforce (Cases
500d100000H0YeFAAV y 500d100000H0dNlAAJ) llegaron al front office y al perfil 20538296 de Opera; el
segundo cambió el email en su sitio.

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

Hace falta la equivalencia `NOS → NOSHOW` (motivo de cancelación, MRU01), que ya está en la línea
base. Solo reservas que vienen del CRS: las de demostración del front office se quedan en local.

## 11. Quién hizo qué, y qué me espera

- *Audit*: cada acción que decide algo sobre un hotel — alta, aprobar o retirar un mapeado, activar,
  pausar, backfill, resolver una causa — hecha o rechazada, por consola, API o agente, con quién,
  cuándo, parámetros y respuesta. Solo lectura.
- *Inbox*: los avisos de mis roles y las **tareas del motor de formularios** (una tarea es un aviso
  más), cada uno con su enlace; se van solos cuando se resuelven.
- **Urgente** (Opera rechaza una escritura, un reintento que no acaba): además de la bandeja, por
  email y al **espacio de Google Chat**.

## 12. Casos de negocio propuestos *(pendientes de decidir)*

De los comentarios de negocio, propuestos como H14–H17: check-in en 4 pasos; cliente nuevo en
recepción → MDM/CRM; penalización de cancelación decidida por Comercial; pago diferido con Gestión
de Cobros y factura de depósito.

## Resetear la demo

`deploy/demo/reset.sh` devuelve ec1 a la **línea base** (`deploy/demo/snapshot.sh` la guarda, en
`~/.local/share/ec-demo1/demo-baseline`; unos 4 minutos):

- **Nuestros servicios y el motor**: se restauran sus bases de datos (CRS, ERP, integraciones,
  mapeado, MDM, front office, comunicación y bandeja, auditoría) y el estado del motor (procesos,
  pasos, tareas); se paran y arrancan.
- **Salesforce**: se borran los contactos y Cases que creó la demo (solo los de ec1; el org se
  comparte con el entorno local) y los contactos de la línea base vuelven a sus datos.
- **Opera no se toca**: ni se cancela ni se borra nada. Por eso la demo se hace para repetirse encima
  de lo que dejó escrito:
  - las reservas de CUN01 son siempre las mismas (localizadores de la línea base): la primera demo
    las escribe en XMU; después, el backfill las **encuentra ya en Opera** y no escribe nada;
  - durante la demo solo se modifica **lo que se crea en la demo** (una reserva nueva de MRU01, y el
    cambio de datos sobre **su** titular), nunca reservas o clientes de la línea base: Opera se
    quedaría con el cambio y no casaría con el estado reseteado. Cada demo deja en XMAR una reserva y
    un perfil de huésped.

La línea base (2026-09-25, 06:58Z): MRU01 ↔ XMAR activa (con la equivalencia `NOS → NOSHOW`) con sus reservas; **CUN01 sin integración con 21
reservas futuras** retenidas (para el alta en directo contra XMU); interlocutores importados; la
bandeja con las causas de CUN01; la auditoría vacía.

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
  4. un **no show** de esa reserva: el CRS la cancela con su cargo del 25 % y lo que cuesta llega al
     front office y a Opera (cancelada con NOSHOW, sus noches sumando el cargo);
  5. una acción auditable aparece en *Audit*, y la bandeja muestra lo que espera (las causas de CUN01).

  Deja en XMAR una reserva y un perfil por ejecución (las reglas de la demo); en Salesforce, nada
  después del reset.

## Preparación de la demo

- [ ] Decidir la propiedad para el alta en directo (§4) y, si es XMU, sembrar un hotel del CRS con
      reservas futuras (`e2e/poc-acl-demo/seed.py`; cuidado: todo lo sembrado acaba en Opera).
- [ ] Comprobar el agente de la consola (MCP de `booking`) y el agente de mapeado con el LLM real.
- [ ] Destinatarios de email reales (hoy el de por defecto es un `example.com` y el correo falla) y
      qué espacio de Google Chat recibe qué (el segundo espacio está bloqueado por su administrador).
- [ ] Probar el §10 desde la pantalla del front office (con usuario) y el Case desde la consola de
      Salesforce. Ojo: el Case lleva la sección *Cambio de datos de cliente (MDM)*; para poder añadirla
      se quitaron del layout de Case las acciones y el panel de resumen propios (quedan los de por
      defecto).
- [ ] Solo el titular viaja al maestro: los acompañantes no llevan código de cliente en el front office.
- [ ] Datos de prueba en XMAR que conviene conocer: reservas 39481284, 39481745, 39481775, 39481943,
      39481944, 39482155 (y dos canceladas); perfiles de interlocutor 20538292 y 20538322
      (ECDEMO0001/0002); el perfil de huésped 20538296 conserva un email antiguo como secundario, de
      antes del arreglo (la API de Opera no permite borrarlo). En Salesforce, los dos Cases de prueba
      y el contacto de C-E572C893A59C con los datos cambiados.
- En Opera, lo que necesita un administrador de OPERA: la interfaz de las referencias externas de
  perfil (OPERAWS-GEN01187) y un cajero para los depósitos (FOF00094). Sin eso, los perfiles van
  sin referencia externa y los depósitos no se apuntan al folio.

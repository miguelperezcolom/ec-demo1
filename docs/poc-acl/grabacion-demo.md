# Grabar el vídeo de la demo

Instrucciones para grabar la demo de la PoC de principio a fin: reset, los cinco flujos de la
presentación, los tres del día a día con la integración activa (6, Opera no responde; 7, modificar y
cancelar; 8, un código nuevo) y, en cada paso, dónde se ve viajar el dato. Escritas el 2026-09-27 para grabar al día
siguiente desde el trabajo.

## Dos modos: API o interfaces

- **Modo API, el que se usará primero.** Salesforce y Opera se enseñan con un **panel de
  evidencias**: una página local con el estilo de la presentación, que Claude genera en cada paso
  con lo que devuelven sus APIs, consultadas fuera del navegador para que ningún token llegue a la
  página. Cada panel lleva el título «Salesforce (API)» u «Opera Cloud (API)» y la hora de la
  consulta. En este modo la fusión del flujo 2 la hace el escaneo en recepción (plan B: `dedup.py`) y la aprobación del Case del
  flujo 3 con la API REST (*Decisión* = Aprobada), que dispara el mismo flow que la interfaz. No
  hace falta que el usuario entre en ninguna parte.
- **Modo interfaces.** El usuario entra en Salesforce y en Opera Cloud en un perfil de navegador de
  grabación (sección «Sesiones de Salesforce y Opera») y se graban sus pantallas reales.

## El prompt para Claude Code

Pegar esto en una sesión de Claude Code abierta en `~/IdeaProjects/ec-demo1`:

> Vamos a grabar el vídeo de la demo siguiendo `docs/poc-acl/grabacion-demo.md`. Primero comprueba
> los requisitos previos. Luego abre el navegador visible con el perfil de grabación, para que yo
> entre en Salesforce y en Opera Cloud. Cuando te diga que he entrado, pon ec1 a cero y graba los
> ocho flujos, toma por toma, con los rótulos. Al final monta el mp4, enséñame dónde está y borra
> el perfil.

## Requisitos previos (lo comprueba Claude)

- **`deploy/demo/demo-prep.sh`** en verde (sin `FAIL`): despliegues, motor, Opera y Salesforce (solo
  GET), integración, diccionario y causas, sin corte de Opera puesto. Tras el reset, `demo-prep.sh
  --zero` hace las dos cosas.

- **Versiones:** ec1 con todo desplegado y la batería de pantallas en verde
  (`cd e2e && npx playwright test tests/consoles.spec.ts`; un fallo suelto que pasa al repetir es
  intermitente).
- **Mateu y motor:** Mateu 3.0-alpha.370 o posterior en los shells y en el front office, y el motor
  en 2.22.2 o posterior.
- **Reorganización de módulos:** se habrá hecho el merge. `partners` se llama ahora `erp`, así que
  hay que comprobar que `deploy/demo/common.sh` (`$SERVICES`) y `zero.sh` usan los nombres nuevos.
- **Herramientas:** ffmpeg (`/opt/homebrew/bin/ffmpeg`) y Playwright (`e2e/node_modules`).
- **Salesforce:** credenciales de API en el secreto `ec-salesforce` (`deploy/demo/common.sh
  salesforce_env`). No se imprimen.
- **Opera Cloud:** hace falta la **URL de la interfaz del tenant**. La del gateway de la API
  (`mtce13ua.hospitality-api…`) no sirve; se la pedimos al usuario.
- **Salesforce Lightning:** `https://<org>.lightning.force.com`, que se deduce de `SF_DOMAIN` como
  hace el MDM para sus enlaces.

## Sesiones de Salesforce y Opera

1. Claude lanza Chromium **visible** con un perfil persistente propio
   (`chromium.launchPersistentContext('<CLAUDE_JOB_DIR>/tmp/rec-profile', { headless: false })`) y
   abre tres pestañas: `https://front.ec1.mateu.io`, Salesforce Lightning y la interfaz de Opera
   Cloud.
2. El usuario entra en las tres, con MFA si lo piden, y lo dice. Claude no ve ni guarda
   credenciales: solo queda la sesión en el perfil.
3. Todas las tomas se graban con ese mismo perfil (`recordVideo` en el contexto persistente).
4. Si una sesión caduca a mitad de la grabación (Opera Cloud caduca pronto), Claude para y pide al
   usuario que vuelva a entrar. No se reintenta sola.
5. **Qué se hace en cada sistema:**
   - **Opera:** solo lectura. Se busca y se enseña, nunca se modifica nada desde su interfaz.
   - **Salesforce:** se navega. **La aprobación del Case del flujo 3 se hace en su interfaz**, lo
     acordamos con el usuario. La fusión del flujo 2 la hace el escaneo en recepción; si no
     fusiona, se hace en su interfaz (Contacts → duplicados → Merge). Si la interfaz no deja fusionar, el plan B es `python3 dedup.py --execute
     --include "<nombre>"` fuera de cámara.
6. Al terminar, se borra el perfil (`rm -rf` del directorio), para que no quede ninguna sesión en
   disco.

## Técnica de grabación

- **Una toma por flujo:** cada flujo es un contexto con `recordVideo` a 1920×1080 y viewport
  1920×1080; las tomas se guardan en `<CLAUDE_JOB_DIR>/tmp/rec/`.
- **Rótulos:** antes de cada paso se inyecta en la página un `<div>` fijo abajo (fondo `#13212E`
  al 90 %, texto `#F4F1EA`, 32 px, IBM Plex Sans) con lo que pasa y por dónde viaja el dato. Se
  quita al cambiar de paso. Con Redwood, se inyecta después de que la página haya cargado; la
  carga tarda entre 20 y 30 s (memoria `redwood-jet-slow-bootstrap`).
- **Tarjetas de título:** una por flujo, con el mismo estilo que la presentación
  (https://claude.ai/artifact/SDNkBJQrfuVaLMxjhbn43v): fondo `#13212E`, «Flujo N» y el título. Se
  generan con ffmpeg (`color` + `drawtext`), 3 s cada una.
- **Esperas:** mientras se espera a algo asíncrono (procesos del motor, la propagación a Salesforce
  u Opera), Claude comprueba por API que el dato ha llegado y solo entonces refresca y enseña la
  pantalla. Las esperas largas se recortan en el montaje: se apuntan las marcas de tiempo y se
  cortan con ffmpeg.
- **Ritmo:** unos 2 s de pausa tras cada acción, para que se lea. Ratón visible: se inyecta un
  cursor, porque Playwright no lo pinta en el vídeo.
- **Montaje:** `ffmpeg -f concat` de las tarjetas y las tomas recortadas, en h264 y AAC mudo, y
  sale `demo-poc-acl-<fecha>.mp4` en `~/Movies/`. Se entrega también `guion-con-tiempos.md`, con
  lo que dice cada rótulo y su minuto, por si luego se le pone voz.
- **Consolas:**
  - **Renderizador:** Redwood en las consolas (`rw.ec1.mateu.io`, `rw-console.ec1.mateu.io`) y en
    el front office. Si algo de Redwood falla en una toma, se graba esa toma en Vaadin
    (`ec1.mateu.io`, `console.ec1.mateu.io`).
  - **Login:** demo/demo en Keycloak.
- **Duración:** entre 12 y 16 minutos en total (los flujos 6–8, unos 4 más: la espera del flujo 6 se
  recorta en el montaje).

## Reset

`deploy/demo/zero.sh`. Pone ec1 a cero y borra los contactos y los Cases del MDM en Salesforce;
está autorizado por el usuario. Opera no se limpia, así que cada grabación deja reservas de prueba
en XMAR: hay que evitar repetir tomas sin necesidad.

## Flujo 1: alta de un hotel

1. **Reservas de prueba.** Call center → Bookings → «+ 10 reservas demo» (confirmar).
   - **Rótulo:** «10 reservas en el CRS (MRU01). Sin integración, se quedan aquí.»
   - **Se enseña:** el listado con las 10.
2. **Alta de la integración.** Consola de control → Integrations → New, con MRU01 y XMAR.
   - **Rótulo:** «Alta de la integración: conexión y catálogos pasan solos; para en el mapeado.»
   - **Se enseña:** el estado y el historial de la integración. En el motor, el proceso
     `alta-integracion`, con su diagrama.
3. **Aviso de mapeado.** El aviso de la bandeja (el badge de la cabecera), y **Open**.
   - **Rótulo:** «La bandeja avisa: códigos sin equivalencia. Open lleva al diccionario, ya
     filtrado.»
4. **Propuesta del agente.** Diccionario → «Ask the agent».
   - **Rótulo:** «El agente de mapeado propone cada equivalencia, con su confianza y su porqué.»
   - **Se enseña:** las propuestas; se abre el detalle de una.
5. **Aprobación.** «Approve» sin nada seleccionado, y se confirma.
   - **Rótulo:** «Una persona aprueba: nada entra en vigor sin ella. El alta sigue: perfiles,
     backfill.»
   - **Se enseña:** la integración pasa a READY_TO_ACTIVATE, con el aviso «ready to activate» en
     la bandeja.
6. **Activación.** Activar la integración.
   - **Rótulo:** «Activada: desde ahora, tiempo real.»
7. **Evidencias.**
   - **Motor:** procesos `proyectar-reserva` COMPLETED y 0 causas abiertas en Mapping → Causes.
   - **Opera:** una de las reservas, con su número.
   - **Front office:** las 10 estancias.
   - **Salesforce:** los contactos creados.
   - **Rótulo:** «CRS → proyectar-reserva → MDM → Opera y front office; los clientes, a Salesforce.»
8. **El recorrido de una reserva** (el plano estrella). En Call center → Bookings, una de las diez →
   **«Ver recorrido»** (o, en Clientes, la columna *Recorrido* de sus reservas).
   - **Se enseña:** arriba, *Hasta Opera* y *Hasta el front office* en segundos; los carriles por
     sistema (CRS, integración, motor, mapeado, MDM, Opera, front office), con la línea en la que Opera
     y la recepción la tuvieron; la tabla de cambios: el primero, el backfill que **esperó** a su causa
     (el mapeado), y el que siguió al aprobarlo; *Causas de esta reserva* con quién las resolvió; y en
     *Paso a paso*, las equivalencias que se usaron y el cliente que el MDM reconoció o dio de alta.
   - **Rótulo:** «Una reserva, de punta a punta: cada salto, cuándo y cuánto tardó. Sale de sus trazas
     (OpenTelemetry), contado en palabras de negocio.»
   - Para quien pregunte cómo: «Ver traza técnica» abre la misma traza en Grafana (Tempo).

## Flujo 2: un cliente que vuelve

1. **Reserva nueva.** Call center → Bookings → New (el asistente): una reserva de MRU01 para el
   titular de una de las 10, con el **mismo nombre y teléfono y otro email**. El MDM solo junta a
   dos personas si coinciden el documento, o el email y el nombre; con otro email, el duplicado
   está asegurado.
   - **Rótulo:** «Una reserva nueva para alguien que ya es cliente, con otro email.»
2. **Llegada a Opera.**
   - **Se enseña:** la reserva en Opera y el proceso COMPLETED.
3. **Duplicado.**
   - **Rótulo:** «El MDM no está seguro: crea un cliente provisional. En Salesforce, un duplicado.»
   - **Se enseña:** Salesforce, con los dos contactos iguales y el aviso de posible duplicado de
     la regla `MDM_Possible_Duplicate`.
4. **Escaneo en recepción.** Front office → la reserva nueva → *Escanear* en el titular.
   - **Rótulo:** «Recepción escanea su documento. Es el que la cadena ya conoce: la misma persona.»
   - **Se enseña:** el diálogo del escaneo y el titular con el documento verificado.
   - **Rótulo:** «El MDM consolida el provisional y fusiona los dos contactos en Salesforce.»
   - **Se enseña:** Salesforce con un solo contacto, con documento, nacionalidad y fecha de
     nacimiento, y ningún Case nuevo.
   - Si el cliente original aún no tenía documento, antes se escanea el titular de su primera
     reserva, que rellena su contacto sin Case, y después el de la nueva. Plan B, si el escaneo no
     fusiona: la fusión en la interfaz de Salesforce («Salesforce limpia: se fusionan. La fusión
     vuelve al MDM (ClienteConsolidado__e).»).
5. **Evidencias.**
   - **Consola:** Clientes, con el cliente CONSOLIDATED y el absorbido como alias; en
     Consolidaciones, la vía `SCAN`.
   - **Front office:** las dos reservas con el mismo cliente, abriendo cada una.
   - **Rótulo:** «Un solo cliente en todas partes.»

## Flujo 3: recepción cambia un dato del cliente

1. **Cambio en el front office.** En la reserva del titular del flujo 2, se edita el teléfono
   (lápiz del huésped o kárdex) y se guarda.
   - **Rótulo:** «Recepción cambia el teléfono. El kárdex lo guarda ya, pendiente de Salesforce.»
   - **Se enseña:** el campo con la marca ámbar «Pendiente de Salesforce».
2. **Case en Salesforce.**
   - **Rótulo:** «El MDM abre un Case sobre el contacto, con los datos propuestos.»
   - **Se enseña:** el Case «Cambio de datos de cliente».
3. **Aprobación en la interfaz de Salesforce.** *Decisión* = Aprobada, y guardar.
   - **Rótulo:** «Salesforce decide. Un flow aplica lo aprobado y lo anuncia.»
4. **Evidencias.**
   - **Front office:** el teléfono nuevo, sin marca.
   - **Opera:** el perfil del huésped con el teléfono cambiado **en la misma entrada**.
   - **Consola:** Clientes, con la solicitud de cambio APPROVED.
   - **Rótulo:** «Baja sola: MDM → front office y Opera.»
5. **Cambio rechazado, más corto.** Se cambia el email, se rechaza en Salesforce con un motivo, y
   en el front office vuelve el dato del maestro con la marca roja y el motivo.

## Flujo 4: no show

1. **Reserva que llega hoy.** Call center → New: MRU01, llegada **hoy**, 2 noches, 2 adultos,
   titular nuevo (o `demo-prep.sh seed arriving-today`). Se espera a que esté en Opera y en el front office.
   - **Rótulo:** «Una reserva que llega hoy.»
2. **No show.** En el front office, en la estancia, se marca No show en cada huésped.
   - **Rótulo:** «Nadie se presenta. Al marcar el último, el no show sube al PMS.»
3. **PMS.** Consola: proceso `registrar-no-show-pms`; en Opera, el comentario «No show — reported by
   the front office…» en la reserva.
   - **Rótulo:** «El PMS, maestro de la estancia, lo anota y lo sube al CRS.»
4. **CRS.** Proceso `registrar-no-show`, y la reserva en Call center.
   - **Rótulo:** «El CRS aplica su regla: cancelada como no show (NOS), cargo del 25 %.»
   - **Se enseña:** la reserva cancelada, con el cargo y el precio original.
5. **Bajada.** `proyectar-cancelacion` COMPLETED.
   - **Se enseña:** Opera, con la reserva cancelada con NOSHOW y el cargo, y el front office, con
     la estancia en No show, el cargo y «Opera: no show anotado; el CRS aplica su cargo».
   - **Rótulo:** «La cancelación baja por la cadena: Opera y front office.»

### Flujo 4 bis (opcional): check-in y check-out, que registra el PMS

Solo con una reserva que llegue en la **fecha de negocio de Opera** (`demo-prep.sh seed
arriving-opera-today`: XMAR está en 2026-05-13 y no hace el check-in de otra cosa).

1. **Check-in.** En el front office, la estancia: *Cambiar* habitación → una **inspeccionada** de las
   que ofrece (las de Opera, con su housekeeping); escanear, wifi, llave, firma, cobro, extras;
   **Confirmar check-in**.
   - **Se enseña:** *En otros sistemas → Opera*: «pendiente — check-in enviado» y, en segundos, «en casa ·
     hab. …»; el proceso `registrar-checkin`; Opera *InHouse* en esa habitación.
   - **Rótulo:** «El PMS es el maestro de la estancia: el check-in sube a Opera.»
2. **Check-out.** *Check-out* → *Confirmar* el cobro.
   - **Se enseña:** `registrar-checkout`; «salida registrada · factura XMAR…»; *Factura → Abrir factura
     proforma (front office)*, con el número y el importe de la factura de Opera.
   - **Rótulo:** «Opera cierra el folio y emite la factura; el front office la muestra.»
3. **(Si se quiere enseñar una causa.)** Una habitación «Limpia, sin inspeccionar en Opera»: Opera la
   rechaza, la causa aparece en la bandeja y la estancia dice por qué; *⋯ → Cambiar habitación* a una
   inspeccionada y entra, y la causa se resuelve sola.

### Flujo 4 ter (opcional): check-in forzado, incompleto hasta completarlo

Con otra reserva en la fecha de negocio de Opera (`demo-prep.sh seed arriving-opera-today`), de 2 pax.

1. **Forzar.** *Confirmar check-in* → el asistente: escanear solo al titular, firma; en *Confirmar*, el
   aviso de lo que falta («Documento de … (pax 2)»), el **motivo** («El acompañante trae el pasaporte
   mañana») y **Forzar check-in**.
   - **Se enseña:** en *Reservas*, el badge «Check-in incompleto» (y la vista del mismo nombre); en la
     ficha, el aviso ámbar con lo que falta y «Completar»; Opera *InHouse*.
   - **Rótulo:** «Entra sin todo, con motivo y auditado; el PMS lo registra igual.»
2. **Check-out bloqueado.** *Check-out* → «⛔ Check-in incompleto…» y lleva a completar.
   - **Rótulo:** «No sale hasta completar el check-in: el parte de viajeros va primero.»
3. **Completar.** «Completar» → escanear el documento que faltaba → la ficha sin aviso; *Check-out*
   ya se puede.
   - **Rótulo:** «A las 24 h sin documento, el aviso pasa a rojo y recepción lo recibe en su bandeja.»

## Flujo 5: walk-in

1. **Formulario.** Front office → Reservas → «＋ Walk-in»: habitación, tarifa y régimen, llegada
   hoy, 2 adultos, titular nuevo con documento.
   - **Rótulo:** «Llega alguien sin reserva. Empieza en el front office.»
2. **Precio.** «Calcular precio».
   - **Rótulo:** «El precio lo pone el CRS.»
3. **Confirmación.** «Confirmar walk-in».
   - **Rótulo:** «La estancia se abre ya, para no esperar al check-in, y sube al CRS como una
     reserva más (WALKIN).»
   - **Se enseña:** la estancia `FO-…` con su chip.
4. **Evidencias.**
   - **Call center:** la reserva con canal WALKIN y la referencia `FO-…`.
   - **Opera:** la reserva.
   - **Front office:** el chip «Walk-in · CRS … · Opera …», en la misma estancia, sin duplicar.
   - **Rótulo:** «CRS → Opera → de vuelta a la misma estancia.»

## Flujo 6: Opera no responde

Preparación fuera de cámara, antes de la toma: `deploy/demo/opera-outage.sh on --alert-after 2m`
(reinicia el conector, ~1 min, y corta su red hacia Opera; se levanta sola a los 15 min).

1. **Reserva nueva.** Call center → New: MRU01, noviembre, STD-KING / DIRECTA / DESAYUNO.
   - **Rótulo:** «Opera no responde: el conector no llega a OHIP. Una reserva nueva.»
2. **Espera.** Admin → Processes: el `proyectar-reserva` en «Asegurar el perfil del huésped», con los
   intentos subiendo (uno cada ~40 s). Mapping → Causes, vacío.
   - **Rótulo:** «Un proceso bloqueado espera, no falla: el motor reintenta. No es una causa.»
3. **Aviso.** A los ~2 min 40 s, el aviso de la bandeja «Writing … to the PMS keeps failing».
   - **Rótulo:** «Si dura, alguien se entera: la bandeja (y el email urgente).»
   - Esta espera se recorta en el montaje.
4. **Vuelve Opera.** `opera-outage.sh off` fuera de cámara; se refresca.
   - **Se enseña:** el proceso COMPLETED, la reserva en Opera (una sola bajo su localizador, con el
     panel de evidencias o la búsqueda por *Conf / Cxl / External*), la estancia en el front office y
     el aviso ya resuelto.
   - **Rótulo:** «Opera vuelve y la reserva llega sola, una vez. El aviso se cierra solo.»
5. **Su recorrido.** En la reserva, **«Ver recorrido»**.
   - **Se enseña:** en el carril de Opera, *Asegurar el perfil del huésped* en ámbar, con sus
     reintentos, y *Hasta Opera* en minutos en vez de segundos; el resto del recorrido, igual que
     siempre.
   - **Rótulo:** «Donde se paró y cuánto: el recorrido lo cuenta sin abrir un log.»

Después de la toma: `opera-outage.sh alert 10m`.

## Flujo 7: modificar y cancelar desde el CRS

1. **Modificación.** Call center → la reserva del flujo 6 → editar: fechas (del 13 al 17 de
   noviembre) y tipo de habitación JS-STD. Antes, `python3 deploy/demo/opera.py availability XMAR
   2026-11-13 2026-11-17` para elegir un tipo que Opera venda esas noches.
   - **Rótulo:** «El CRS cambia fechas y habitación: versión nueva.»
2. **En su sitio.**
   - **Se enseña:** Opera, **el mismo número de reserva** con las fechas, el tipo y la versión (UDF)
     nuevos; el front office, la estancia con las fechas y la habitación nuevas.
   - **Rótulo:** «La misma reserva de Opera, cambiada en su sitio: se busca por localizador y solo
     entra una versión más nueva.»
3. **Cancelación.** Call center → Cancelar (motivo «Otros motivos»).
   - **Se enseña:** `proyectar-cancelacion` COMPLETED, Opera *Cancelled*, la estancia *Cancelada*.
   - **Rótulo:** «La cancelación baja igual: Opera y front office.»

## Flujo 8: un código nuevo con la integración activa

1. **Tarifa nueva.** Fuera de cámara: `python3 deploy/demo/ec1.py rate-plan MRU01 EMPLEADOS-27
   "Empleados de la cadena de vacaciones 2027" 0.5`. En cámara, Mapping → Dictionary (MRU01): el
   código, *Unmapped*.
   - **Rótulo:** «Producto abre una tarifa nueva en el CRS. La integración, ya activa, no la conoce.»
2. **Reserva con ella.** Call center → New: MRU01, JS-STD, EMPLEADOS-27, solo alojamiento, 20–23 de
   noviembre.
   - **Se enseña:** Mapping → Causes, la causa `MISSING_MAPPING … EMPLEADOS-27` con 1 proceso
     esperando; el aviso de la bandeja, y **Open**.
   - **Rótulo:** «La reserva espera al mapeado: una causa, y el aviso a quien la resuelve.»
3. **Propuesta.** Dictionary → «Ask the agent» (~10 s): `432040HLXMU` «STAFF ON HOLIDAY XMU A27».
   - **Rótulo:** «El agente la empareja por significado con la de Opera, con su porqué.»
4. **Aprobación.** Se selecciona **solo esa** propuesta y «Approve».
   - **Se enseña:** la causa resuelta, el proceso COMPLETED, Opera con la tarifa `432040HLXMU` y la
     estancia en el front office.
   - **Rótulo:** «Una persona aprueba y la reserva sigue sola hasta Opera.»

## Al terminar

- **Entrega:** el mp4 y `guion-con-tiempos.md` en `~/Movies/`.
- **Limpieza:**
  - se borra el perfil del navegador;
  - se borran los specs temporales de `e2e/tests/zz-*`;
  - `deploy/demo/opera-outage.sh status`: ningún corte puesto y el aviso en 10m;
  - lo que quede en XMAR se apunta en `docs/poc-acl/demo.md`, en la lista de datos de prueba que
    quedan en Opera.

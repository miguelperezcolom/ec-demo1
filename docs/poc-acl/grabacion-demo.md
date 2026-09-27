# Grabar el vídeo de la demo

Instrucciones para grabar la demo de la PoC de principio a fin: reset, los cinco flujos de la
presentación y, en cada paso, dónde se ve viajar el dato. Escritas el 2026-09-27 para grabar al día
siguiente desde el trabajo.

## Dos modos: API o interfaces

- **Modo API, el que se usará primero.** Salesforce y Opera se enseñan con un **panel de
  evidencias**: una página local con el estilo de la presentación, que Claude genera en cada paso
  con lo que devuelven sus APIs, consultadas fuera del navegador para que ningún token llegue a la
  página. Cada panel lleva el título «Salesforce (API)» u «Opera Cloud (API)» y la hora de la
  consulta. En este modo la fusión del flujo 2 se hace con `dedup.py` y la aprobación del Case del
  flujo 3 con la API REST (*Decisión* = Aprobada), que dispara el mismo flow que la interfaz. No
  hace falta que el usuario entre en ninguna parte.
- **Modo interfaces.** El usuario entra en Salesforce y en Opera Cloud en un perfil de navegador de
  grabación (sección «Sesiones de Salesforce y Opera») y se graban sus pantallas reales.

## El prompt para Claude Code

Pegar esto en una sesión de Claude Code abierta en `~/IdeaProjects/ec-demo1`:

> Vamos a grabar el vídeo de la demo siguiendo `docs/poc-acl/grabacion-demo.md`. Primero comprueba
> los requisitos previos. Luego abre el navegador visible con el perfil de grabación, para que yo
> entre en Salesforce y en Opera Cloud. Cuando te diga que he entrado, pon ec1 a cero y graba los
> cinco flujos, toma por toma, con los rótulos. Al final monta el mp4, enséñame dónde está y borra
> el perfil.

## Requisitos previos (lo comprueba Claude)

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
     acordamos con el usuario. La fusión del flujo 2 también se hace en su interfaz (Contacts →
     duplicados → Merge). Si la interfaz no deja fusionar, el plan B es `python3 dedup.py --execute
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
- **Duración:** entre 8 y 12 minutos en total.

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
4. **Fusión en Salesforce**, en su interfaz.
   - **Rótulo:** «Salesforce limpia: se fusionan. La fusión vuelve al MDM (ClienteConsolidado__e).»
5. **Evidencias.**
   - **Consola:** Clientes, con el cliente CONSOLIDATED y el absorbido como alias.
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
   titular nuevo. Se espera a que esté en Opera y en el front office.
   - **Rótulo:** «Una reserva que llega hoy.»
2. **No show.** En el front office, en la estancia, se marca No show en cada huésped.
   - **Rótulo:** «Nadie se presenta. Al marcar el último, el aviso sube al CRS.»
3. **CRS.** Consola: proceso `registrar-no-show`, y la reserva en Call center.
   - **Rótulo:** «El CRS aplica su regla: cancelada como no show (NOS), cargo del 25 %.»
   - **Se enseña:** la reserva cancelada, con el cargo y el precio original.
4. **Bajada.** `proyectar-cancelacion` COMPLETED.
   - **Se enseña:** Opera, con la reserva cancelada con NOSHOW y el cargo, y el front office, con
     la estancia en No show y el cargo.
   - **Rótulo:** «La cancelación baja por la cadena: Opera y front office.»

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

## Al terminar

- **Entrega:** el mp4 y `guion-con-tiempos.md` en `~/Movies/`.
- **Limpieza:**
  - se borra el perfil del navegador;
  - se borran los specs temporales de `e2e/tests/zz-*`;
  - lo que quede en XMAR se apunta en `docs/poc-acl/demo.md`, en la lista de datos de prueba que
    quedan en Opera.

# Plan: editar contenidos del content-hub de forma más WYSIWYG

Plan para retomar desde cualquier sitio. Se actualiza el **Estado** de cada paso al avanzar.

## Punto de partida (2026-11-01)

- **Content-hub** (`supporting/content-hub`, rama `feat/content-hub`, PR #269 en ec-demo1):
  - El cuerpo de un item (`ItemForm.bodyEs/bodyEn/bodyDe`) ya es `@Stereotype(richText)`.
  - Los **bloques** de una página (`BlockForm`) se editan como formulario plano:
    - `html` es un textarea de **HTML crudo**;
    - los medios se insertan tecleando `[[media:id]]`;
    - los enlaces son líneas `Texto | URL`;
    - se ven todos los campos sea cual sea el `BlockType`
      (FEATURED, AUTOMATIC_LISTING, BANNER, QUICK_LINKS, HTML, MEDIA).
  - Lo que se publica lo pinta `infra/in/ui/portal/PageRenderer.java` (+ `infra/in/render/ContentRenderer.java`).
  - "Ver como…" está en `ViewAsPage.java`.
- **Mateu** (repo `miguelperezcolom/mateu`, última release v3.0-beta.31):
  - Vaadin edita `richText` con Tiptap.
  - Redwood lo edita con un editor propio y pequeño, `mateu-rich-text-field`:
    - una barra de `oj-button` sobre un `contenteditable`;
    - código en `frontend/web/monorepo/apps/redwood/poc/richtext.mjs` (`defineRichTextField`);
    - se registra en `poc/make-amd.mjs` (~l.107);
    - plantillas en `poc/templates/fields-extra.html` (~l.143) y `poc/templates/listing-body.html` (~l.1216);
    - el editor de filas lo resuelve en `poc/core/rowEditor.mjs` (~l.546-617).
- **Spectra trae un editor rico**: `oj-sp-input-rich-text-2`.
  - Está en el mismo CDN y versión que ya carga Mateu:
    - `https://static.oracle.com/cdn/spectra-ui/oj-sp/2604.1.0/input-rich-text-2/loader.js`;
    - bundle `oj-sp-input-rich-text-bundle`.
  - Es un editor CKEditor 5 envuelto.
  - Propiedades:
    - `value` (string HTML, writeback) y `rawValue`;
    - `placeholder`, `required`, `readonly`, `disabled`;
    - `length {max,min,measure}`;
    - `displayOptions {editorMode, label, formattingToolbar, toolbarItems[]}`;
    - `mentions`/`tags` (DataProviders);
    - `primaryAction`/`secondaryAction`/`tertiaryAction`/`aiAction`.
  - Métodos: `validate`, `insertText`, `getPlainText`, `focus`, `reset`.
  - Comandos de la barra:
    - BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, SUB/SUPERSCRIPT;
    - HEADING, BULLETED_LIST, NUMBERED_LIST, INDENT/OUTDENT, ALIGNMENT;
    - LINK, FONT_COLOR, FONT_BACKGROUND_COLOR, FONT_FAMILY, FONT_SIZE;
    - HORIZONTAL_LINE, UPLOAD_IMAGE, ver código fuente, UNDO/REDO, REMOVE_FORMAT.
  - **Sin probar en vivo**: no sabemos cómo resuelve UPLOAD_IMAGE (si no encaja, se oculta ese botón).

## Orden decidido

1. **Nivel 2: Mateu, editar `richText` en Redwood con `oj-sp-input-rich-text-2`.** Sale en una beta.
2. **Nivel 1: content-hub, formulario de bloques amable.** Usa esa beta.
3. **Nivel 3: edición sobre la propia página.** Se decide después de ver 1 y 2.

## Nivel 2 — Mateu: `oj-sp-input-rich-text-2` para `richText` editable en Redwood

Trabajar en un worktree/clon aparte del repo Mateu: hay otros agentes en él.
Rama: `feat/redwood-spectra-rich-text`.

| # | Paso | Estado |
|---|---|---|
| 2.1 | Spike: cargar `oj-sp-input-rich-text-2` en una página Redwood de prueba (p. ej. `demo-museum` en Redwood). Comprobar: `value` entra y sale como HTML; evento de cambio (`valueChanged` de `value`) en blur o en cada tecla; `readonly`; cómo se comporta UPLOAD_IMAGE (¿base64 inline?, ¿evento?). Anotar aquí lo que salga. | hecho — el "being upgraded" se resolvió en #985 con `data-oj-binding-provider="none"` en el contenedor; `value`/`display-options` como atributos; commit en `focusout` desde `rawValue` |
| 2.2 | Sustituir `mateu-rich-text-field` por el componente de Spectra en las plantillas (`fields-extra.html`, `listing-body.html`). Cargar el loader de Spectra igual que los demás `oj-sp-*` que ya se usan (ver cómo se declaran los bundles en el `require` de la app VB). Conservar: la conversión de un Delta antiguo (`richTextHtml`), el saneado por allowlist al guardar (`richTextValueOf`/`sanitizeHtml`), el evento `valueChanged {value, updatedFrom:'internal'}` que esperan las cadenas. | hecho (#985) |
| 2.3 | Barra por defecto razonable (`toolbarItems`): títulos, negrita/cursiva/subrayado, listas, enlace, línea, deshacer/rehacer, ver código. Sin fuentes/colores. UPLOAD_IMAGE solo si 2.1 dice que encaja. | hecho (#985) |
| 2.4 | `mateu-rich-text-field` queda como respaldo si el componente no carga (CDN caído): decidirlo en 2.2. Actualizar el comentario de `richtext.mjs` ("JET has no rich text editor" → Spectra sí). | hecho (#985) |
| 2.5 | Tests: `poc/test*.mjs` (la cobertura de `tooling-tests` exige líneas ≥98 %, funciones ≥95 %, ramas ≥85 %), `npm run bridge && npm run build && npm run copy` (bundles regenerados: lo comprueba `bundle-freshness`), e2e si cubre richText en Redwood. CHANGELOG en Unreleased. | hecho (#985) |
| 2.6 | PR #985 abierto por el fork (2.2–2.5 hechos: `mateu-rich-text-field` monta `oj-sp-input-rich-text-2`, con su editor propio de respaldo). PR → CI verde (`e2e`, `tooling-tests`, `frontend-lib`) → merge → release `gh release create v3.0-beta.N --target master` (notas en viñetas) → esperar "Build and publish" y Maven Central (`io.mateu:mateu-mvc/vaadin/redwood`). | hecho: #985 y #986 (+#987) en **3.0-beta.34**; ec-demo1 y los demos desplegados con beta.34 (2026-11-01) |

### Notas del fork al cerrar #985

- `data-oj-binding-provider="none"` es lo que deja pintar un elemento JET montado a mano dentro de una página VB.
- La primera carga del editor tarda unos 9 s (descarga de CKEditor), solo una vez.
- El toast de Redwood pinta formateado un mensaje que lleva etiquetas HTML: en e2e, comprobar en la petición, no en el toast.
- `tests/renderer/**` corre también con el renderer Vaadin: una spec solo de Redwood debe saltarse fuera de `renderer-vb`.

### Resultados del spike (2.1, 2026-11-01)

Spike hecho inyectando el componente con JS en https://museums-rw.ec1.mateu.io/welcome:
`require(['oj-sp/input-rich-text-2/loader'])` + `document.createElement`.

- **Funciona (primer intento)**:
  - pinta el HTML (h2, negrita, enlaces);
  - es editable, con la barra por defecto: B/I/U/S, alineación, listas, sangría, quitar formato;
  - el primer `require` tardó ~9 s (descarga de CKEditor).
- **Eventos**:
  - `rawValueChanged` en cada tecla;
  - `valueChanged {value, updatedFrom:'internal'}` al perder el foco, con el HTML.
  - Es el mismo contrato que `mateu-rich-text-field`: las cadenas no cambian.
- **Normaliza enlaces**: añade `target="_blank" rel="noopener noreferrer"`. El saneado de Mateu debe dejar pasar `target`/`rel` en `<a>`, o se perderán al guardar.
- **Imagen**: CKEditor `Base64UploadAdapter` → la imagen va inline en base64 dentro del HTML. **No usar** `uploadImage`: los medios van por la biblioteca (`[[media:id]]`).
- **`toolbarItems`** se filtra por nombre de comando:
  - `heading`, `bold`, `italic`, `underline`, `strikethrough`, `bulletedList`, `numberedList`;
  - `link`, `horizontalLine`, `removeFormat`, `undo`, `redo`, `generateHtmlSupport` (ver código);
  - `alignment`, `indent`, `outdent`, `uploadImage`; los menús: `heading`, `fontFamily`, `fontSize`.
  - Cambiar `displayOptions` después de montado no cambió la barra.
- **Problema abierto**: tras recargar la página, crear el elemento a mano **se queda en "being upgraded"**.
  - Lo dice el busy context de `ojs/ojcontext`: no pinta, sin errores en consola.
  - Pasa incluso repitiendo exactamente el primer intento.
  - Hipótesis: cargarlo a mano, fuera de los `imports.components` de la página VB, deja alguna dependencia sin resolver.
  - **Siguiente paso**: probarlo como lo usará Mateu, declarado en `main-start-page.json` (`"oj-sp-input-rich-text-2": {"path": "oj-sp/input-rich-text-2/loader"}`) y pintado desde la plantilla, con una build local del bundle Redwood (`npm run bridge && npm run build && npm run copy`) servida por un demo en local.
  - Si sigue sin pintar de forma fiable, plan B: mantener `mateu-rich-text-field` y ampliarlo (títulos, enlace, línea) en vez de usar el de Spectra.

## Nivel 1 — content-hub: un formulario de bloques amable

Rama de ec-demo1: `feat/content-hub`, o una nueva desde ella.

| # | Paso | Estado |
|---|---|---|
| 1.1 | Subir `mateu.version` de los poms de ec-demo1 a la beta del nivel 2 (la cadena completa está en la memoria "Mateu release & shell redeploy": poms → shells → apply). | hecho: ec-demo1 en 3.0-beta.34 (c333853f) |
| 1.2 | `BlockForm.html` → `@Stereotype(FieldStereotype.richText)`. Comprobar que `PageRenderer`/`ContentRenderer` siguen resolviendo `[[media:id]]` y los embeds (embed.js) dentro del HTML que produce el editor. | hecho (6874aa9d) |
| 1.3 | Campos según el tipo de bloque (`VisibilitySupplier`/`isHidden`, como ya hace `ItemForm`): HTML → título + html; FEATURED → item; AUTOMATIC_LISTING → sección + cuántos; QUICK_LINKS → enlaces; MEDIA → medio; BANNER → lo que use. La audiencia del bloque, siempre. | hecho en el formulario; hecho: en el editor de un bloque (fila de la tabla) Mateu aplica ya `isHidden` y lo recalcula al cambiar el tipo (Mateu #1006, 3.0-beta.36); probado en el navegador |
| 1.4 | "Insertar medio": botón/acción que abre la biblioteca (`MediaCrud`) con miniaturas y, al elegir, añade `[[media:id]]` al html (o rellena `mediaId` en un bloque MEDIA). `mediaId` pasa a ser un select de la biblioteca. Lo mismo en el cuerpo de `ItemForm`. | hecho en parte (6874aa9d): `mediaId` es un select de la biblioteca; el botón que inserta `[[media:id]]` dentro del texto, pendiente |
| 1.5 | Enlaces como tabla editable (`List<LinkForm>` con texto y URL) en lugar de líneas `Texto \| URL`; migrar `of()`/`toBlock()`. | hecho (27956308); probado en el navegador: tabla Texto/URL dentro del bloque. La tabla de bloques muestra solo tipo y título (a540cca0): con todos los campos, «Edit» quedaba fuera de la pantalla |
| 1.6 | Vista previa junto al formulario que se refresca al editar (hoy "Previsualizar" repinta la página: ver commit d5ecd95a). | hecho (27956308): sección «Medios y vista previa» (vista previa del idioma por defecto + «Insertar medio»). botones bajo el formulario de edición: Mateu #1005 (beta.36) + #1014 (beta.37, la respuesta llega a un CRUD colgado del menú de una app y el formulario se repinta, así que la vista previa sigue a lo editado); probado en el navegador: insertar un vídeo lo muestra en la vista previa sin guardar, y «Traducir con IA» rellena en/de |
| 1.7 | Probar en local (kind, `deploy/local/up.sh`) en Vaadin y Redwood (`console.localhost:8800` / `rw.localhost:8800`, editorial → contenidos), con el usuario editor de la seed. Actualizar el manual de usuario de la intranet (docs) y el recorrido con capturas. | probado en el Chrome del Mac (2026-11-01), Vaadin y Redwood: editor de texto rico en ambos, bloques y enlaces, vista previa; recorrido con capturas (sección 5). Repetido con 3.0-beta.37 (2026-11-02): botones en edición, campos por tipo en bloques y fechas en es (Mateu #1004), todo bien |

## Nivel 3 — edición sobre la página

Decidido con el usuario el 2026-11-02 (rama `feat/editor-visual`):
- un web component propio (`<ec-visual-editor>`, JS sin framework) alojado como `Element` de Mateu en
  una ruta del backoffice, que funciona en los dos shells;
- pinta los bloques del **borrador** con los datos del portal (`PageRenderer`), en el idioma por defecto
  (los bloques son monolingües);
- texto de HTML/Banner editable en el sitio; panel lateral con el tipo, los campos del tipo y la
  audiencia del bloque como estrechamiento de la de la página, con el recuento;
- guardar por el mismo servicio y validaciones que el formulario; la revisión y la publicación, en el
  formulario.

| # | Paso | Estado |
|---|---|---|
| 3.1 | Dominio y aplicación: `Block.isNarrowing`, `ItemCommands.editBlocks` (+ tests) | hecho |
| 3.2 | API `VisualEditorController` (`/_intranet/api/editor`: página, preview/ver como, guardar, medio, código), autorizada como el backoffice (`Readers`) + `PageRenderer.draftHtml` (+ tests de autorización) | hecho |
| 3.3 | Componente `visual-editor.js` (seleccionar, texto en el sitio con barra, medios, pegado limpio, panel, audiencia y recuento, +, arrastrar/↑↓, eliminar, ver como, guardar, aviso de cambios sin guardar); `embed.js` lo carga con el token del usuario | hecho |
| 3.4 | Backoffice: Editorial → Editor visual (`/editorial/visual/{id}`) y «Editar sobre la página» en el formulario de una página | hecho |
| 3.5 | Desplegar en local y probar sin navegador (API con token de `demo`, en una página de prueba) | hecho (2026-11-02): página de prueba «Prueba editor visual»; leer, preview, ver como, guardar, 400 con un valor que el directorio no tiene, 409 con una versión vieja |
| 3.6 | Probar en el navegador, Vaadin (`ws.localhost:8800`) y Redwood (`rw.localhost:8800`) | hecho con Chromium sin cabeza (Playwright) en los dos: el componente carga, seleccionar, escribir, panel y recuento, ver como, guardar; falta una prueba a mano (arrastrar, pegar, insertar medio, tema oscuro) |
| 3.7 | Manual del backoffice y arquitectura | hecho |

Limitaciones conocidas:
- el aviso de «cambios sin guardar» al salir cubre cerrar/recargar la pestaña y los clics en menús,
  botones y enlaces fuera del editor; no la tecla «atrás» del navegador;
- el texto de los bloques solo en el idioma por defecto (los bloques no tienen traducciones);
- en Safari, la selección dentro de un shadow root es limitada: probado en Chrome.

---

# Plan paralelo: directorio de la intranet (organigrama + fichas)

Pedido el 2026-11-01. Va **en paralelo** con el editor: el nivel 2 lo lleva otro agente en el worktree de Mateu; el directorio, en ec-demo1.

## Punto de partida

- El content-hub ya guarda una copia del directorio de empleados:
  - la alimenta el servicio de usuarios por el topic `employee-directory`;
  - `application/out/EmployeeDirectory`, `infra/out/persistence/PgEmployeeDirectory`;
  - cada persona: userId, nombre, email, enabled y atributos (company, country, region, hotel, department, jobTitle, language: códigos del catálogo).
- Pantalla: solo `backoffice/DirectoryListing` (tabla plana, para administradores de contenido). Los empleados no ven nada.
- En usuarios (`control-plane/users`):
  - `User` tiene `EmployeeAttributes` (los atributos de audiencia, JSON en `UserEntity.attributesJson`; `ddl-auto: update`);
  - los eventos `UserCreated`/`UserUpdated` → `EmployeeDirectoryOutboxAppender` → `EmployeeDirectoryMessage`;
  - seed en `DemoDirectorySeeder`.
- **Falta la jerarquía**: nadie guarda quién reporta a quién.

## Diseño

- **Usuarios**: un VO nuevo `EmployeeProfile(managerId, phone)` en `User`.
  - No va en `EmployeeAttributes`: esos son los atributos de audiencia, códigos de catálogo.
  - Se persiste en su propia columna JSON `UserEntity.profileJson` (`ddl-auto` la añade; null = sin perfil).
  - Viaja en `UserCreated`/`UserUpdated`.
  - Se edita en `UserViewModel`: jefe = select de usuarios; teléfono.
  - `managerId` no puede ser el propio usuario.
- **Contrato `employee-directory`**: campos nuevos opcionales al nivel de `name`/`email`: `"managerId":"…","phone":"…"`.
  - Ausentes si no hay valor. Es aditivo: los consumidores actuales no se rompen.
  - Actualizar el javadoc de `EmployeeDirectoryMessage` y el test del appender.
- **Content-hub**:
  - el consumidor del directorio guarda `managerId` y `phone` (columnas nuevas en la tabla del directorio, `schema.sql`);
  - `EmployeeDirectory` los expone.
- **Portal (intranet)**, sección "Personas":
  1. Buscador: nombre, puesto, hotel o departamento, con filtros por hotel, departamento y región. Cada fila abre la ficha.
  2. Ficha: avatar de iniciales (sin fotos reales), puesto, departamento, hotel, región, email, teléfono, jefe (enlace), colaboradores directos (enlaces) y "mismo equipo" (mismo departamento y hotel).
  3. Organigrama por estructura: empresa → región → hotel → departamento → personas, en árbol (vista tree de Mateu). Los nombres de los códigos salen del catálogo de atributos.
  4. Organigrama por personas: la cadena de jefes de alguien y sus colaboradores, desde la ficha.
  - Solo para usuarios de la intranet (`@Readers`) y solo datos de trabajo. Los empleados deshabilitados no se muestran.
- **Seed**: jefes coherentes en `DemoDirectorySeeder`: dirección → directores de hotel → jefes de departamento → empleados. Teléfonos ficticios (`+34 600 000 xxx`).
- **Extra**: herramienta MCP en `IntranetMcpTools` para que el chat responda "¿quién es el jefe de recepción del TFS01?".

## Pasos

| # | Paso | Estado |
|---|---|---|
| D1 | Usuarios: `EmployeeProfile` en `User` (dominio, eventos, comandos, casos de uso, entidad/repositorio, DTO, `UserViewModel`) + tests | hecho (721e0693) |
| D2 | Contrato `employee-directory` con `managerId`/`phone` + snapshot (`PublishEmployeeDirectorySnapshotUseCase`) + tests. Además `labels` (código → etiqueta del catálogo) para que el content-hub no necesite copia del catálogo | hecho (721e0693, 968c4cfc) |
| D3 | Seed con jerarquía y teléfonos: regla plantilla → jefe de departamento → director de hotel → directora general (`rosa.camps`, nueva); director nuevo en CUN01 (`ricardo.flores`); a los usuarios ya sembrados sin perfil se les da uno | hecho (721e0693) |
| D4 | Content-hub: consumidor + `schema.sql` (`manager_id`, `phone`, `labels`) + `EmployeeDirectory` | hecho (b074c57e, 968c4cfc) |
| D5 | Portal: "Personas" (buscador + ficha) en el menú de la intranet — `People`, `PeopleListing`, `PersonPage` | hecho (3fdd4b36), probado en local en Vaadin y Redwood |
| D6 | Portal: organigrama (estructura + cadena de jefes) — `OrgChartPage` | hecho (3fdd4b36), probado en local en Vaadin y Redwood |
| D7 | MCP: consulta de directorio para el chat — `buscar_personas`, `ficha_persona` | hecho (570c72ea) |
| D8 | Desplegar en local (kind), probar en Vaadin y Redwood, actualizar manual/recorrido de la intranet | hecho: probado en el navegador (ficha, organigrama en Redwood) y capturas en el recorrido |

**Bug de Mateu encontrado (D5)**: `SpaFallback.candidate` (backend/shared/core/.../infra/SpaFallback.java) trata como fichero cualquier ruta con un punto, así que un enlace directo o recargar `/intranet/personas/ana.perez` da 404 (en Vaadin y Redwood; navegando dentro de la app funciona). Arreglo propuesto: considerar fichero solo si la extensión del último segmento es de un recurso web conocido (js, css, map, png, svg, ico, json, woff2…); la navegación ya se distingue por `Accept: text/html`. Arreglado en Mateu PR #986 (el usuario lo pidió el 2026-11-01); sale en la próxima beta junto con #985 (editor de Spectra) y #987 (cursor de mano solo en filas que abren algo). Tras la release: `deploy-after-release.sh` sube ec-demo1 (merge de #270 + la beta nueva) y los demos.

---

# Siguientes funcionalidades de la intranet (pedidas el 2026-11-01)

El usuario eligió, de una lista de cinco (solicitudes, agenda de eventos, traducir con IA, analítica editorial, comentarios), la **1** y la **3**. Empiezan cuando estén hechos la release de Mateu (#985/#986/#987) y el despliegue.

## F1 — Solicitudes con aprobación del responsable

El empleado pide algo desde la intranet (vacaciones, formación, material). La solicitud va a **su responsable** (el `managerId` del directorio, D1–D4) y después a RRHH. El empleado sigue el estado en la intranet. Es un proceso de **EventConductor**: une la intranet con el motor de la plataforma.

**Diseño (a validar contra el motor antes de escribir código):**
- **Proceso** `solicitud-empleado` en **ec-definitions** (cambia por PR; los motores reimportan solos al mergear):
  - arranque con los datos de la solicitud y el `managerId`;
  - **tarea humana** del responsable (motor de formularios): aprobar o rechazar, con comentario;
  - si aprueba, tarea de RRHH;
  - aviso por email al empleado en cada cambio (communication-service: plantilla nueva);
  - **timeout**: sin respuesta del responsable en N días, sube al responsable de su responsable (la cadena `People.chainOf`).
- **Tipos** de solicitud como catálogo, con los campos de cada una: vacaciones (fechas), formación (curso, coste), material (qué, motivo).
- **Intranet** (portal), nuevo menú **Mis solicitudes**:
  - nueva solicitud (formulario por tipo);
  - mis solicitudes con su estado;
  - **Por aprobar**, solo para quien tiene colaboradores directos: aprobar o rechazar desde la intranet, que completa la tarea humana del motor.
- **Integración**: comandos y estados por Kafka con outbox/inbox, como el resto de ec-demo1 (memoria "Prefer events over sync", "Commands over Kafka"). El content-hub manda "arranca proceso" y escucha los cambios de estado para su proyección "mis solicitudes".
- **Preguntas abiertas** (mirarlas en `eventconductor` y `ec-definitions` antes de empezar):
  - cómo se asigna una tarea humana a un usuario concreto (el responsable) y no a un rol;
  - cómo completa la intranet esa tarea (API del motor de formularios o un comando por Kafka);
  - cómo expresar el timeout con escalado.
  - Ver `doc/src/content/docs/guias/procesos.md` y el `.ectask` de `e2e/workflow-agents`.

**Resultado de F1.1 (investigación del motor, 2026-11-01):**

- **Tarea humana**: un paso `type: USER_TASK` con `formId` (un `.ecform` con `requiredRoles` y `fields`) y `topic: forms`.
  - Los valores enviados pasan a variables del proceso.
  - Precedente en ec-demo1: `reset-demo.ec` (paso `confirm`, form `confirmar-reset-demo`) en `~/IdeaProjects/ec-definitions-demopage`.
- **Asignar a una persona concreta**: `assignment: {assignee: "${manager}", candidateUsers, candidateGroups, dueDate…}`, **solo desde EventConductor 2.31**.
  - Se resuelve al crear la tarea.
  - El `assignee` se compara con el usuario autenticado, así que el `managerId` del directorio debe ser el login de Keycloak (lo es: los ids de usuario).
  - Los `requiredRoles` del form siguen aplicando: el form de aprobación no debe exigir un rol que los responsables no tengan.
  - Doc del motor: `eventconductor/doc/.../guides/human-tasks-assignment-and-escalation.md`.
- **Escalado**: `escalation: [{after: PT48H, action: reassign|notify, to…}, {after: P5D, escalateTo: otro-paso}]`, también **solo desde 2.31**.
  - En 2.27.1 solo hay `timeout` + `onTimeoutStepId`.
- **Completar la tarea**: por **REST** del motor de formularios, `POST /forms/api/tasks/{id}/complete` con `{"values":{…}}`.
  - Lo hace quien tiene la tarea; antes, `claim`.
  - La bandeja se consulta con `GET /forms/api/tasks?scope=mine|candidate&businessKey=…`.
  - No hay vía Kafka limpia para completar un form.
- **Arrancar**: `process-creation-requested` en `upstream` por el outbox, como ya hace ec-demo1 (`ProcessRouter.java`: `outbox.appendToEngine(new ProcessCreationRequested(...))`).
  - La business key, `solicitud:<id>`.
- **Estado hacia fuera**:
  - el topic `human-tasks` (CloudEvent `io.eventconductor.task.*`, cuerpo `HumanTaskChanged` con `businessKey`, `status`, `userId`…), que ya consume communication-service;
  - para el resultado final, pasos `PUBLISH_EVENT` del propio proceso a un topic nuestro (`solicitudes`).
  - `outbox` y `process-index` son internos del motor.
- **Versión**: ec-demo1 fija **2.27.1** (`deploy/values/eventconductor.yaml`, poms de `integration/*`).
  - **Para asignar al responsable y escalar hay que subir el motor a ≥ 2.31**: orquestador, formularios y reglas, y la dependencia de los workers.
  - El motor de formularios aplica la migración V8 al arrancar.
  - **Decisión del usuario pendiente**: subir el motor, o hacer F1 en 2.27.1 con visibilidad por rol (`requiredRoles`) + `timeout`, filtrando en la intranet "Por aprobar" por `managerId`.

| # | Paso | Estado |
|---|---|---|
| F1.1 | Resolver las preguntas abiertas en el motor y en ec-definitions; cerrar el diseño aquí | hecho — ver «Resultado de F1.1»; el usuario eligió subir el motor: 2.37.0 aplicado en local |
| F1.2 | Proceso `solicitud-empleado` + formularios en ec-definitions (PR) | hecho: ec-definitions #43 (proceso + 2 forms, plugin a 2.37.0) y #44 (la descripción de un workflow va en un varchar(255): el validador no lo comprueba) |
| F1.3 | Content-hub: tipos de solicitud, "nueva solicitud", outbox de arranque, proyección de estados (inbox) | hecho (bc9c4c42): `EmployeeRequest`, `RequestService`, `OutboxProcessStarter`, `RequestEventsListener`, `PgRequestRepository`, tabla `ch_request`; destino `solicitudes` en el orquestador |
| F1.4 | Portal: Mis solicitudes y Por aprobar (completar la tarea del responsable) | hecho (bc9c4c42): Nueva solicitud, Mis solicitudes (con historial), Por aprobar (`FormsHumanTasks`: REST del motor de formularios con el token del usuario; reclama si es candidato) |
| F1.5 | Emails de estado (communication-service) y escalado por timeout | hecho: correo al empleado al decidirse (Mailer, con la redirección local a `MAIL_REDIRECT_TO`); el escalado lo hace el motor (`escalation`, P2D) — no probado en vivo (dos días) |
| F1.6 | Probar en local de punta a punta (Ana pide vacaciones → Carlos aprueba → RRHH → Ana ve "aprobada") y documentar | hecho en local (2026-11-01): Ana pide vacaciones → Carlos aprueba (tarea asignada) → Isabel (RRHH, candidata: reclama) aprueba → proceso COMPLETED, solicitud APROBADA con 3 pasos; y Ana pide material → Carlos rechaza → RECHAZADA, correo `status=sent` por Gmail. Usuarios de prueba con contraseña local (`TEST_USERS_PASSWORD` en el fichero de credenciales local). Documentado en el manual del portal, arquitectura y topics. Repetido en el navegador (Mac, 2026-11-01): el comentario del aprobador salía de solo lectura (la página de un registro de Mateu es de lectura) → contenido propio con el campo editable (396177c3, feb6f91d); las fechas en Vaadin se interpretaban en formato de EE. UU. → Mateu #1004 |

### Subida de EventConductor a 2.37

**Decisión (2026-11-01):** el usuario aprueba subir el motor de 2.27.1 a **2.37.0**, para tener asignación por usuario y escalado (desde 2.31). Preparada en la rama `chore/eventconductor-2.37` (sale de `feat/content-hub`). Sin desplegar.

**Qué cambia en el motor (2.28 → 2.37), y si nos afecta:**

- **2.28, endurecimiento de seguridad.**
  - *Webhooks de git-import sin firma se rechazan.* Ya firmábamos: `ec-git-webhook` lleva `WORKFLOW_`/`FORMS_GITIMPORT_WEBHOOKSECRET`. Se añaden `RULES_` y `TASKS_GITIMPORT_WEBHOOKSECRET`, con el mismo `GIT_WEBHOOK_SECRET`. No hay webhook de reglas ni de contratos en GitHub. **Nada que hacer en GitHub**: los dos webhooks de ec-definitions (workflow y forms) siguen con el mismo secreto.
  - *Las apps standalone ya no imprimen una contraseña de admin generada.* Los tres motores van con `security.enabled: false`. La app instala entonces una cadena de filtros `permitAll`, sin usuario admin. `SECURITY_PASSWORD` no aplica.
  - *Consolas y MCP rechazan peticiones anónimas* sin cadena de filtros de Spring Security. Las apps standalone la tienen (`permitAll`): no cambia nada, `/_workflow` y `/_forms` siguen detrás del gateway, que exige token.
  - *gRPC del motor de reglas, apagado por defecto.* Ya estaba `grpcEnabled: false` y nada de ec-demo1 lee reglas por gRPC.
  - *`HTTP_CALL`: credenciales atadas a hosts.* Ninguna definición usa `HTTP_CALL`, `LLM_CALL`, `PUBLISH_EVENT` ni `AGENT_CALL`. Revisado en los tres checkouts de ec-definitions; solo hay `ACTION`, `CHOICE`, `LOCK`/`UNLOCK`, `WAIT_FOR_MESSAGE`, `FORK`/`JOIN` y un `USER_TASK`.
  - *Las páginas de tareas solo se fían del token reenviado con `workflow.security.trust-forwarded-token=true`* (Mateu 3.0-alpha.411). **Este sí nos afecta.** Sin él, reclamar o completar una tarea no ve a nadie autenticado (p. ej. el `confirm` de `reset-demo`), y la asignación por usuario de F1 no funcionaría. Se pone `WORKFLOW_SECURITY_TRUST_FORWARDED_TOKEN=true` en el pod de forms: el gateway verifica el token antes de reenviar `/_forms`.
  - Migraciones V45–V46. V45 rellena `process_entity.finished` de los procesos terminados: un `UPDATE` único, rápido con nuestro volumen.
- **2.29–2.30:** bucles y `forEach`, operaciones sobre procesos vivos, incidentes, atributos de búsqueda, operaciones por lotes, workers por HTTP. Migraciones V47–V50, solo tablas o columnas nuevas.
- **2.31:** asignación y escalado de `USER_TASK`, bandeja `/forms/api/tasks`, migración de procesos.
  - Migración **V8 de formularios**.
  - Las tareas reclamadas pasan a estado `ASSIGNED`. La bandeja de communication-service (`HumanTask.open()`) ya trataba `ASSIGNED` como abierta.
  - Los eventos de ciclo de vida van en el mismo `human-tasks`, con el mismo `HumanTaskChanged` y campos nuevos. El lector es tolerante.
- **2.32:** variables sensibles (V53). No declaramos ninguna, así que no hacen falta claves.
- **2.33:** updates, control de flujo, `CONTINUE_AS_NEW`, prioridad de tareas (V55, V56, V56.1).
  - `TaskExecutionRequested` gana `priority`.
  - No hay *record patterns* sobre los DTOs del motor en los workers: compilan sin cambios.
  - 2.33.1 corrige la pérdida de eventos en Kafka con contención.
- **2.34:** paso `AGENT`; `AGENT_CALL` desaparece. No usamos ninguno de los dos en ec-definitions. Migración V57: solo columnas.
- **2.35:** `HTTP_CALL`, `LLM_CALL` y `PUBLISH_EVENT` los sirven workers integrados en la imagen del orquestador.
  - Topics `http-calls`, `llm-calls` y `event-publications`, grupo `orchestrator-builtin-workers`; se autocrean.
  - No desplegamos `http-worker` ni `llm-worker` standalone, así que nada consume dos veces.
- **2.36–2.37:** el editor de grafos cubre `assignment`/`escalation`; gauge `eventconductor.incidents.open`.
  - La 2.37.0 trae también `WorkflowExecutionController` + V58, la API de ejecución desde el IDE que pedía `deploy/ide-execution/README.md`.

**Qué se ha tocado:**

- `deploy/values/eventconductor.yaml`: `imageTag` 2.37.0 en orquestador, forms y reglas; `WORKFLOW_SECURITY_TRUST_FORWARDED_TOKEN: "true"` en forms.
- `deploy/deploy.sh`: el secreto `ec-git-webhook` gana `RULES_` y `TASKS_GITIMPORT_WEBHOOKSECRET`. No hay valores en git.
- `<eventconductor.version>2.37.0` en los 19 poms que lo fijaban (`shared`, `worker-kafka`, `worker-api`). Sin cambios en el código de producción.
  - Compila todo el reactor afectado (`-am -amd`).
  - Tests de los 19 módulos: todos verdes salvo uno, el contrato de `human-tasks`.
- **Contrato `human-tasks` → v2.** `HumanTaskChanged` gana siete componentes (`event`, `candidateUsers`, `candidateGroups`, `priority`, `dueDate`, `escalationLevel`, `businessKey`), y el comprobador de contratos lo cuenta como incompatible (campos nuevos obligatorios).
  - `CommunicationContractsTest` publica ahora `contracts/schemas/human-tasks/v2.schema.json`, con ejemplos `created` y `claimed`.
  - `v1.schema.json` se queda tal cual: ya no se puede generar desde el record del motor.
  - El consumidor de communication-service lee los ejemplos de v1 y de v2.
- `deploy/demo/common.sh`: `ENGINE_TABLES` incluye el estado nuevo que va con procesos y tareas. Así `snapshot.sh`, `reset.sh` y `zero.sh` lo copian y lo vacían. Las tablas:
  - `form_execution_candidate_user` / `_group`;
  - `process_search_attribute` y `process_index_search_attribute`;
  - `process_update`;
  - `flow_control_key` / `_pending`;
  - `deferred_dispatch`.
- La documentación que citaba 2.27.1 (`doc/…/compilar.md`, `entorno-local.md`, `servicios.md`, `estado-del-repositorio.md`, `procesos.md`, `programaciones.md`).

**Cómo desplegarlo en local** (kind, `deploy/local/up.sh`), en este orden:

1. **Imágenes arm64 del motor.** Docker Hub solo tiene amd64. Hay que construir `orchestrator-`, `forms-` y `rule-standalone-app:2.37.0` desde el jar publicado, con la receta de `doc/…/desarrollo/entorno-local.md` y un checkout de eventconductor en `v2.37.0`. `up.sh` las carga en el nodo, porque lee las etiquetas de los values.
2. **Antes de nada, una línea base** (`deploy/demo/snapshot.sh`) por si hay que volver atrás. Flyway no deshace: volver a 2.27.1 con el esquema ya migrado no está probado. La vuelta segura es restaurar la base de datos `workflow` entera, no solo las tablas de estado.
3. **Motor primero:** `deploy/local/up.sh`, o `deploy.sh` sobre la copia local, que hace el `helm upgrade` del release `ec` con el chart vendorizado. Al arrancar se aplican, en este orden:
   - en el orquestador, V45 → V58 (V56.1 crea índices `CONCURRENTLY`; el motor toma un lock de sesión de Flyway);
   - en forms, V8.
   Hay que esperar a que los tres pods estén *Ready* y comprobar en los logs que las migraciones han terminado. Ninguna definición nueva hasta entonces.
4. **Después, los workers:** `deploy/local/load-images.sh` (reconstruye con los poms en 2.37.0) y `kubectl -n ec-demo1 rollout restart` de los servicios con worker. Con el motor nuevo y los workers viejos se convive: los campos nuevos de los DTOs son opcionales en el cable. Pero 2.33.1 corrige las réplicas perdidas en el worker-kafka, así que conviene no dejarlos atrás.
5. **Solo entonces** puede entrar en ec-definitions una definición con `assignment`/`escalation` (F1.2). La 2.31 lo exige: forms y workers en ≥ 2.31 antes de usar `escalation`.

**Prueba de humo** (después del paso 4):

- `deploy/demo/demo-prep.sh health`: todo *Ready*, motor arriba, Opera/Salesforce. Incluye `check-contracts.sh --cluster`: cada tarea referenciada tiene un worker vivo en su topic.
- En `/_workflow`, las definiciones importadas de ec-definitions siguen *ACTIVE*, sin errores de carga.
- Los flujos de `doc/src/content/docs/guias/procesos.md`, con los seeds de `demo-prep.sh`:
  - una reserva nueva del CRS → `proyectar-reserva` (con `LOCK`/`UNLOCK` y `WAIT_FOR_MESSAGE`) hasta Opera y front office;
  - una cancelación → `proyectar-cancelacion`;
  - `seed arriving-opera-today` → check-in/check-out (`registrar-checkin`/`-checkout`);
  - un cargo y su anulación → `registrar-cargo`/`anular-cargo`;
  - `seed arriving-today` → `registrar-no-show`.
- `reset-demo` desde la consola: su `USER_TASK` `confirm` tiene que aparecer en la bandeja y poder reclamarse y completarse con el usuario del token. Prueba el `trust-forwarded-token`.
- Ningún proceso en `ERROR` nuevo en *Incidents* (página nueva de la 2.30) y nada en el topic `dead-letter`.

**Lo que necesita al usuario / riesgos abiertos:**

- **ec-definitions** (no tocado): su `pom.xml` fija `eventconductor.plugin.version` 2.23.0. Para validar en CI definiciones con `assignment`/`escalation`, súbelo a 2.37.0 en el mismo PR de F1.2.
- `WORKFLOW_AGENTS_ENABLED=true` en `deploy.sh` sigue sustituyendo la imagen del orquestador por una construida desde `feat/workflow-agent-step`. Con 2.37.0 el paso `AGENT` ya viene en la imagen publicada. Falta comprobar si el ia-agent habla el mismo contrato (`run-agent@1` en `agent-calls`) y retirar ese override; no se ha tocado.
- Volver atrás tras migrar solo es posible restaurando la base de datos `workflow` completa (paso 2).
- ec1/Cloudfleet: la misma subida vale con `deploy.sh`, pero está fuera de alcance.


**Aplicada en local (2026-11-01, tarde):**
- Rama `chore/eventconductor-2.37` fusionada en `feat/content-hub`. En los poms, Mateu beta.33/34 y EC 2.37.0.
- Imágenes arm64 de orquestador, formularios y reglas construidas desde el tag `v2.37.0`, con un worktree aparte de eventconductor.
- Antes de nada, volcado completo de la BD del motor (`pg_dumpall`, en el scratchpad de la sesión): es el punto de vuelta.
- `up.sh` falló en el paso del motor por un fallo previo de `deploy.sh` en el bash 3.2 de macOS (`engine_args[@]` vacío con `set -u`); arreglado en 5191987d.
- Migraciones aplicadas: orquestador V58, formularios V8.
- Workers reconstruidos (`load-images.sh`) y las 28 apps reiniciadas. Ojo: en zsh las variables no se parten en palabras; usar `bash -c`.
- `demo-prep.sh health` contra el clúster local (con `KUBECONFIG` local: el contexto por defecto es ec1):
  - 39 despliegues listos; las 72 tareas con worker vivo; integraciones activas;
  - `dead-letter` vacío; 4 procesos en espera, las causas PMS_REJECTED que ya había.
- **Tareas humanas en 2.37**: el motor de formularios crea la tarea (nuevas columnas `escalation_level`, `restricted`…) y la cancela con el proceso.
  - Para F1: una tarea que no nombra a nadie **no sale** en `GET /forms/api/tasks?scope=candidate`. Comprobar con `assignee` el `scope=mine` del responsable.
- **Incidente evitado**: para probar, se arrancó `reset-demo` (v2) por REST con la idea de contestar «no».
  - Creó todos sus pasos de reset en CREATED (el motor los crea por adelantado) y se **canceló al momento**: solo corrió `start`, y los datos se comprobaron intactos.
  - **No usar `reset-demo` para pruebas**: para F1, una definición de prueba propia.
- Arrancar por REST: el cuerpo es `{definitionId, businessKey, variables:{…}}`. El de Kafka (`workflowDefinitionId`, `variables:[…]`) es otro.

## F3 — Traducir con IA en el editor

Los contenidos tienen español, inglés y alemán (`ItemForm` y sus secciones por idioma), y cada traducción se escribe a mano. Un botón **Traducir con IA** en el editor rellenará los idiomas vacíos desde el idioma por defecto (título, resumen, cuerpo HTML conservando la marca y los `[[media:id]]`). El editor la **revisa** antes de enviarla a revisión o publicar.

**Diseño:**
- **Acción** en `ItemForm` (toolbar, `@Editors`):
  - traduce solo los idiomas vacíos, o el elegido si ya tiene texto, pidiendo confirmación antes de sobrescribir;
  - deja un aviso "Traducido con IA, revísalo" que se quita al editar.
- **LLM**: el content-hub ya usa Spring AI. Usar el modelo del catálogo de IA (ec-ia-config / ia-control-plane) si el content-hub puede leerlo; si no, un `ChatClient` configurado con el modelo por defecto del despliegue.
  - En local no hay clave salvo la de los agentes (memoria "Local agent LLM settings"): decidir cuál usar.
- **Prompt**:
  - traducción fiel, el HTML intacto (solo el texto de los nodos), sin traducir los `[[media:…]]` ni las URLs;
  - el tono de comunicación interna;
  - salida como JSON {title, summary, body} validado; si el HTML de vuelta no pasa el saneado, se rechaza.
- También para los **bloques** de una página (título y contenido de cada `BlockForm`) y las **variantes de slot**.

| # | Paso | Estado |
|---|---|---|
| F3.1 | Elegir de dónde sale el modelo (catálogo de IA o config propia) y la clave en local | hecho: la clave del secreto del clúster `ec-anthropic` (la misma que usa ia-control-plane), opcional; modelo `content-hub.translation.model` (por defecto `claude-opus-5-5`) |
| F3.2 | Servicio `Translator` (aplicación) + adaptador Spring AI, con tests del contrato (HTML y marcas intactos) | hecho (ba8944e9): `Translator` + `ClaudeTranslator` con el SDK oficial `anthropic-java` 3.2.0 (no Spring AI): esfuerzo bajo, esquema JSON escrito a mano (la derivación del SDK choca con el jsonschema-generator de Spring AI), fallback de servidor ante rechazos, cuerpo saneado y marcas `[[media:…]]` verificadas; tests |
| F3.3 | Acción "Traducir con IA" en `ItemForm` (y bloques), aviso de revisión | hecho (ba8944e9): acción en `ItemForm` (solo si hay clave); bloques y variantes de slot, pendiente |
| F3.4 | Probar en local en Vaadin y Redwood; documentar en el manual del backoffice | hecho en local (2026-11-01, sin navegador: la acción del formulario): «bienvenida» traducida a en y de con el formato (`<b>`) y la marca `[[media:m1]]` intactos. Hizo falta devolver `List.of(this, Message…)`: un `Message` solo no devuelve el estado del formulario. Documentado en el manual del backoffice |

## N — Newsletter: recurrente, intro rica, aperturas y clics, suscripción

Lo que hay:
- Ediciones creadas a mano en *Editorial → Newsletter* (`NewsletterForm`/`NewsletterCrud`, `NewsletterService`, tabla `ch_newsletter`).
- Cada una se programa; `ContentJobs.newsletters` (cada 30 s) envía las que vencen por el relay del clúster.
- Cada grupo de empleados recibe solo lo que es para él.
- En local todo va al email del usuario como un digest con una sección por grupo (config `content-hub.newsletter`).
- Una edición enviada: "Boletín semanal", 2026-10-31.

El usuario pidió las cuatro mejoras (2026-11-01):

| # | Paso | Estado |
|---|---|---|
| N1 | **Recurrente**: una programación (p. ej. semanal, lunes 9:00) que crea sola el borrador de la edición con lo publicado desde la anterior (por tipo y sección, lo más reciente primero). El editor la revisa y la aprueba; sin aprobar no sale. Opción de envío automático sin revisión. | hecho (608ee80d): `WeeklyNewsletter`, cron en `ContentJobs`, «Preparar boletín de la semana» |
| N2 | **Intro con el editor rico** (`richText`, el editor de Spectra en Redwood tras #985) y vista previa del email tal como sale. | hecho (608ee80d): la intro es `richText`; vista previa = la que ya había |
| N3 | **Aperturas y clics**. Píxel de apertura y enlaces con redirección firmada (HMAC como los medios, `/_intranet/n/{edición}/{destinatario}/…`), guardados por edición y destinatario. Informe por hotel y departamento en el backoffice. Los empleados ficticios de la demo van al email del usuario: contar por destinatario original. | hecho (79263d1b): por GRUPO, no por persona (el correo de un grupo es uno para todos): `NewsletterTracking`, `NewsletterLinksSigner`, `NewsletterTrackingController`, informe en `NewsletterForm`; gateway deja pasar `/_intranet/n/**` |
| N4 | **Suscripción personal**: en el portal (p. ej. "Mis preferencias") cada empleado elige las secciones que quiere recibir y la frecuencia, dentro de lo que su audiencia le deja ver. `NewsletterService` filtra por ella; el default es todo. | hecho (4f15abb1): `NewsletterPreferences`/`ch_newsletter_pref`, `PreferencesPage` (Hydratable) |

## N5 — Newsletters y números (2026-11-02)

El usuario pidió partir la newsletter en NEWSLETTERS y sus NÚMEROS, y luego: «la newsletter en realidad es
como un sitio web», «es como una intranet, con sus páginas», «es como una intranet más». Así que:
**sitios** (la intranet es el de por defecto; cada newsletter, otro) y, encima, los números.

| # | Paso | Estado |
|---|---|---|
| N5.1 | **Sitios**: `Site` (`ch_site`), `site_id` en items (`ch_item`, `ch_item_doc`), árbol de secciones por sitio (`ch_navigation`, la intranet sigue en `main`), inicios por sitio (`ch_site_home_page`); listados, secciones, búsqueda, RAG y Drive acotados a un sitio (la intranet por defecto). Todo lo existente es de la intranet | hecho (66761ba8) |
| N5.2 | **Newsletter** (por sitio): maqueta, fuentes por sección, periodicidad (ninguna/semanal/mensual), envío sin revisión (por defecto, borrador); numeración; un slot = un número | hecho (bd0db49a) |
| N5.3 | **Números**: bloques (los de las páginas + «Sección de la newsletter»), items por sección, `DRAFT → SCHEDULED → SENT`/`CANCELLED`, contenido automático desde el número anterior, envío por grupo, prueba, estadísticas por número | hecho (bd0db49a) |
| N5.4 | **Suscripción por newsletter** (`ch_nl_subscription`) con secciones; baja por newsletter en el correo; «Mis preferencias» las lista | hecho (bd0db49a) |
| N5.5 | **Portal**: «Newsletters» y el sitio de cada una en `/intranet/n/{id}` (inicio, archivo, número, sección, páginas), su menú tras una costura (`NewsletterNavigation`) | hecho (bd0db49a); falta probarlo en el navegador (Vaadin y Redwood, móvil) |
| N5.6 | **Backoffice**: Newsletters (identidad, audiencia, secciones, maqueta, periodicidad, sus números y páginas), Números de newsletter (formulario, prueba, programar/cancelar, estadísticas, vista previa), editor visual sobre un número | hecho (bd0db49a); falta probarlo en el navegador |
| N5.7 | **Migración** de las ediciones a «Boletín de la intranet» (idempotente, al arrancar), eventos y preferencias conservados | hecho (bd0db49a); probado en arranque contra un Postgres vacío con una edición |
| N5.8 | Menú configurable de la newsletter (dueño: el id de su sitio) en lugar del derivado: sembrado (inicio, secciones, archivo, páginas), destinos `NEWSLETTER_HOME`/`ARCHIVE`/`ISSUE`, `PAGE`/`SECTION` por sitio (`SiteAwareRoutes`), editable en Configuración → Menús | hecho (tras #279) |
| N5.10 | Revisión en el navegador (Vaadin, tema oscuro): el número y la barra del sitio ilegibles sobre fondo oscuro → primero sobre papel claro; luego (pedido del usuario) la versión web sigue el tema, claro u oscuro, y solo el correo es claro (fix/sites-visibility-and-dark); un item con un bloque de tipo desconocido rompía el listado → se lee sin él | hecho |
| N5.9 | Desplegar en local y probar en el navegador | pendiente (lo despliega el agente principal) |

## H — Inicios por audiencia (home configurable)

Hoy `portal/HomePage` es fijo e igual para todos: saludo, pendientes de confirmar, 5 avisos y 8 noticias. Lo que cambia por persona es solo qué contenidos salen (cada uno filtrado por su audiencia). El usuario pidió (2026-11-01) poder definir **un inicio distinto por usuario o grupo**, con los mismos criterios que los contenidos.

**Diseño (simplificado a propuesta del usuario):** un inicio es **una página más**.
- Es un item `PAGE` normal: se crea, edita, versiona y publica como cualquier contenido, con **su audiencia de siempre**. Puede haber tantos como se quiera.
- Sin tipo nuevo, sin prioridad en el item y sin menú propio.
- Lo único que se añade es **qué páginas son inicios y en qué orden**, porque un lector puede cumplir varias.

| # | Paso | Estado |
|---|---|---|
| H1 | **Configuración → Inicio** (control plane, `content:admin`): una **lista ordenada de páginas** publicadas. Al lector le toca la **primera de la lista que puede ver** (la función de visibilidad de siempre); si no ve ninguna, el inicio de hoy (`HomePage`). `HomePage` resuelve eso y pinta la página con `PageRenderer`, cacheado por hash de perfil (`ProjectionCache`). Una página quitada de la lista vuelve a ser una página normal. | hecho (f4c99699): `HomePages`/`ch_home_page`, `HomesPage` en Configuración → Inicio, `HomePage.homeOf` |
| H2 | **Bloques dinámicos**, para que una página pueda mostrar lo que hoy pinta el inicio: `GREETING` (saludo con el nombre), `PENDING` (pendientes de confirmar), `NOTICES` y `LATEST` (últimos avisos / noticias y páginas, cuántos, sección opcional). Opcional: `SLOT` (una clave de slot). Sirven en cualquier página, no solo en inicios. | hecho (f4c99699): `GREETING`, `PENDING`, `NOTICES`, `LATEST` en `PageRenderer`; `SLOT` no |
| H3 | **Ver como…** del backoffice muestra también qué página de inicio le toca a ese perfil (o "el de por defecto"). | hecho (0396cc69) |
| H4 | **Ejemplo en la seed**: la página "Inicio Recepción Canarias" (audiencia: departamento RECEPCION y región CANARIAS), con saludo, pendientes, enlaces rápidos a turnos y al procedimiento, y un banner, primera en la lista. Probar con Ana Pérez (TFS01, recepción), que la ve, frente a un chef de Cancún, que sigue con el inicio por defecto, en Vaadin y Redwood. Documentar en los manuales (portal, backoffice, configuración). | hecho (f4c99699, docs 0396cc69); falta probarlo en el despliegue |

## M — Menús configurables

Hasta ahora el menú del portal estaba en código (`IntranetMenu`, un `@Menu` por función). El usuario pidió
(2026-11-02) hacerlo **configurable como datos y reutilizable**: la intranet lo usa ya; cada newsletter (que
pasa a ser "un sitio más", rama `feat/newsletter-issues`) tendrá el suyo con la misma pieza; y una entrada
puede llevar a **otra interfaz** del registro de interfaces (rama `feat/remote-uis`).

**Diseño acordado:** un menú es un árbol ordenado de entradas, de un **sitio** (`intranet` o el id de otro
sitio). Entrada: texto por idioma (es/en/de, reserva en el idioma del sitio), destino, audiencia ("quién lo
verá", el mismo modelo que los items) y activa. Destinos: `PAGE`, `SECTION`, `BUILTIN` (las funciones del
portal), `URL` (ruta o externa, pestaña nueva), `REMOTE` (otra interfaz) y los que añada otro tipo de sitio.
El portal lo dibuja por petición con `MenuSupplier`; una entrada que el lector no ve no sale, ni un submenú
vacío. Las rutas siguen saliendo de las páginas de siempre.

| # | Paso | Estado |
|---|---|---|
| M1 | Dominio: `SiteMenu` (árbol: añadir, cambiar, mover, subir/bajar, meter/sacar de submenú, borrar con lo que cuelga; lo que ve un lector), `MenuEntry`, `MenuTarget` (tipo abierto), `MenuNode` | hecho: `domain/menu`, `SiteMenuTest` |
| M2 | Aplicación: `SiteMenus` (por sitio, `content:admin`, `shown(MenuContext)`), un `MenuTargetResolver` por tipo (`StandardTargets`), `PortalFunction`, `DefaultMenus`; costuras `SiteRoutes` y `UiSources` | hecho: `application/menu`, `SiteMenusTest` |
| M3 | Persistencia `ch_menu` (un documento por sitio) y siembra idempotente del menú de siempre (`MenuSeeder`) | hecho: `PgMenuRepository`, `MenuStoreTest` |
| M4 | Portal: `IntranetMenu` como `MenuSupplier` (`PortalMenu`): funciones como `FieldLink`, páginas/secciones/rutas como `RouteLink`, submenús sin path, funciones fuera del primer nivel ocultas pero enrutadas; `MenuEntryPage` para enlaces externos e interfaces alojadas | hecho: `PortalMenuTest`; sin cambios en los shells |
| M5 | Configuración → Menús (`MenusCrud`, `MenuEntryForm` con sitio, posición, textos, destino, quién lo verá, activa y subir/bajar/meter/sacar) y Menús: vista previa (`MenuPreviewPage`); *Ver como* enseña el menú configurado | hecho |
| M6 | Probar en local en Vaadin (`intranet.localhost`) y Redwood (`rw-intranet.localhost`): el menú sembrado igual que antes, un submenú, una página con audiencia, un enlace externo; deep links | pendiente (despliegue lo coordina el agente principal) |
| M7 | Newsletter: su menú con `SiteMenus` y sus destinos propios (`MenuTargetResolver` con `appliesTo`) | pendiente, en `feat/newsletter-issues` |
| M8 | Registro de interfaces: implementación real de `UiSources` (`@Primary`) | hecho en `feat/remote-uis`: un solo `UiSources` con el `UiSource` del dominio; `PgUiSourceRepository` (`@Primary`), sin el bean vacío de `MenuConfig` |

## R — UIs externas (registro y uso como páginas y bloques)

Pedido el 2026-11-02 (rama `feat/remote-uis`). Diseño acordado con el usuario:
- **Registro** (configuración, plano de control, `content:admin`): `UiSource {id, nombre, tipo, dónde está,
  etiqueta, audiencia, permisos que exige, activa}`. Tipos: `MATEU_REMOTE` (el prefijo de una UI de Mateu en
  el gateway, `/_loyalty`), `WEB_COMPONENT` (la ruta de su script en el gateway + su etiqueta), `IFRAME`
  (una URL https de un origen permitido por el despliegue; último recurso). Nunca un host arbitrario,
  `javascript:`, `..` ni `//`.
- **Usos** (los editores solo eligen entre las registradas): una **pantalla externa** (`REMOTE_PAGE`: su URL
  guarda `ui:<fuente><ruta>`) y el bloque **«Componente remoto»** (`REMOTE`: fuente + ruta o atributos).
  Los **menús** (rama `feat/configurable-menus`) usan el registro por el puerto `UiSources`.
- **Seguridad**: el token del usuario viaja por el gateway como siempre y la UI remota aplica sus permisos;
  la audiencia de la fuente es una audiencia con nombre para la política (`ui-source:<id>`), así que lo que la
  usa se proyecta con ella (AND) y desaparece de todo si se estrecha, desactiva o borra; los permisos se
  miran en la puerta (`RemoteUis.usable`). Nada ensancha.

| # | Paso | Estado |
|---|---|---|
| R1 | Dominio: `UiSource`, `RemoteUi`, `BlockType.REMOTE`, `ItemType.REMOTE_PAGE`; validación (rutas del gateway, orígenes permitidos, atributos sin `on*`/`javascript:`) y la audiencia de la fuente en la proyección (`Block.visibilityAudience`, `ItemContent.visibilityAudience`) | hecho (1551b214), con tests |
| R2 | Registro: puerto `UiSources` (`find`, `all`), `PgUiSourceRepository` (`ch_ui_source`), `UiSourceCommands`, `RemoteUis`; Configuración → **UIs externas** (`UiSourcesCrud`/`UiSourceForm`, con el constructor de audiencias y «Dónde se usa») | hecho (1551b214) |
| R3 | Dibujo: portal (`MicroFrontend` para Mateu, `<ec-ui-source>` de `embed.js` para web component e iframe), editor visual (una tarjeta), correo (`RemoteUiRenderer.fallbackHtml`/`fallbackText`: «ver en la web») | hecho (1551b214, 7aa42c0a) |
| R4 | Editores: pantalla externa en `ItemForm`; campos del bloque en `BlockForm` y en el editor visual (el registro como lista) | hecho (7aa42c0a) |
| R5 | Ejemplos en la semilla: Riu Class (`/_loyalty`) y `<ec-push-toggle>`; «Riu Class · socios» (pantalla externa) y «Herramientas» (página con dos componentes remotos) | hecho (fa73a62c) |
| R6 | Desplegar en local y probar en el navegador, Vaadin y Redwood, en `ws.localhost:8800` (el gateway enruta `/_loyalty` en el plano de datos) | en parte: Vaadin, «Herramientas» con sus dos bloques (ver R10); Redwood y la pantalla externa, pendientes |
| R7 | En el host propio de la intranet (`intranet.*`) el gateway solo enruta `/_portal`, `/_inbox` y los medios: una UI de Mateu de otro servicio necesita su ruta allí (decidir cuáles) | pendiente |
| R8 | Manual del backoffice, arquitectura y operación | hecho |
| R9 | Un menú con `REMOTE` (rama de menús, #279): un solo `UiSources` con el `UiSource` del dominio; `PgUiSourceRepository` `@Primary`, sin el bean vacío de `MenuConfig`; `MenuEntryPage` dibuja web components e iframes con `RemoteUiRenderer` | hecho (rebase sobre master con #279) |
| R10 | Probado en el navegador (ws.localhost, Vaadin, ana.perez): «Herramientas» pinta los dos bloques remotos; pero la isla traía su cromo (miga «Intranet › Noticias › Acumulaciones» y el título de la pestaña). Arreglo en ec-demo1 (`RemoteUiRenderer.Mode`, clases `ec-remote-*`, estilo y guarda del título en `embed.js`) mientras Mateu no tenga una opción de «embebido» (lo que necesitaría: arquitectura → UIs externas) | hecho (8ed58aaa); falta volver a probarlo en el navegador (Vaadin y Redwood) |

Limitaciones conocidas:
- dentro de una pantalla externa, un enlace de la UI remota navega a su propia ruta (p. ej. `/loyalty/members/…`):
  en la consola de datos la resuelve su `RemoteMenu`; en el host de la intranet, no;
- los permisos que exige una fuente se miran al dibujar: un lector sin ellos ve la pantalla externa en los
  listados, pero al abrirla le sale «No puedes usar esta aplicación» (la audiencia, en cambio, sí la oculta en
  todo); con «ver como», los permisos son los del editor;
- un web component `.mjs` cargado con token (blob) no resuelve imports relativos: que el script sea un bundle;
- un iframe no lleva el token del usuario: el otro sitio inicia sesión por su cuenta.

## V — Historial y calendario

Pedido el 2026-11-02: (A) el historial de versiones de un contenido, con comparación y restaurar; (B) un calendario
editorial. Rama `feat/history-calendar`.

| # | Paso | Estado |
|---|---|---|
| V1 | **Historial**: cada paso del workflow (creado, editado, enviado a revisión, publicado, rechazado, retirado, restaurado) con quién y cuándo, en `ch_item_history`, aparte del item. Las versiones solo guardaban lo publicado: ahora cada guardado que cambia algo guarda también la copia del borrador (barato: un `ItemContent` en JSON). Las publicaciones previas al historial se leen de las versiones | hecho: `ItemHistory`, `HistoryEntry`/`HistoryAction`, `PgItemHistoryRepository`, anotado desde `ItemCommands` |
| V2 | **Comparar** dos estados (versión, copia guardada o borrador actual): por campo y por idioma (título, resumen, cuerpo como texto —sin etiquetas— con lo añadido y quitado resaltado), bloques (añadidos, quitados, movidos, cambiados), audiencia y ventana de publicación | hecho: `TextDiff`, `ContentDiff`, `DiffView` |
| V3 | **Restaurar esta versión** (`content:edit`): al borrador, nunca publica; queda en el historial | hecho: `ItemHistory.restore`; una fecha de publicación pasada se quita, caducidad o límite pasados se avisan |
| V4 | Pantalla `HistoryPage` (`/editorial/historial/{id}`), botón *Historial* en el formulario, *Editorial → Historial de versiones* | hecho |
| V5 | **Calendario editorial** (`/editorial/calendario`) con el arquetipo `CalendarPage` de Mateu: mes, semana y lista; publica, caduca, confirmar antes de, en revisión; filtros por tipo, sección y estado; pulsar abre el contenido | hecho: `EditorialCalendar`, `ItemsCalendarSource`, `EditorialCalendarPage` |
| V6 | **Punto de extensión** `CalendarSource` para que las ediciones programadas de las newsletters (y otros) añadan entradas | hecho: `NewsletterIssuesCalendarSource` (números programados y enviados, abren `/editorial/numeros/{id}`) |
| V7 | Tests (diff por palabras y de HTML, diferencias, restaurar, agregación del calendario, store) y documentación (manual del backoffice, arquitectura) | hecho |
| V8 | Probar en local en Vaadin | primera prueba del coordinador: el calendario fallaba (lector sin permisos: Mateu evaluaba el campo sin petición) y los selects del historial eran de solo lectura (ficha de un listado). Corregido: lector desde la petición en curso; selects como `FormField` en `detailContent`. Falta volver a probarlo, y Redwood |

## P — Plantillas y vista previa (2026-11-02)

Pedido: (A) **plantillas de página** — nombre, descripción, bloques con textos de ejemplo y audiencia por
defecto opcional; «Nueva página desde plantilla» y «Guardar como plantilla»; tres de ejemplo; por sitio si el
concepto de sitio está —lo está (#281)—, con plantillas globales. (B) **enlaces de vista previa para
revisores** — firmados, caducables (7 días), revocables, listados en el item; enseñan el BORRADOR como lo verán
los lectores (opcionalmente «ver como» un perfil) a cualquiera con sesión, de solo lectura, marcados «Borrador —
vista previa», registrando las vistas; revocar o publicar los anula.

**Permisos (decididos):** usar una plantilla, `content:edit` (solo hace un borrador); definirlas, `content:publish`
o `content:admin` (`@Publishers`), como el contenido de slots: son el punto de partida de todos. Enlaces: crear
y revocar, cualquier permiso editorial; abrir, cualquier empleado con sesión.

| # | Paso | Estado |
|---|---|---|
| P1 | Dominio `PageTemplate` (bloques que solo estrechan, audiencia por defecto, `siteId` o global, `fromPage`, `newPageContent`) y `PreviewLink` (`PreviewLinkStatus`: activo, caducado, revocado, publicado; vistas) | hecho: `domain/template`, `domain/preview`, tests de dominio |
| P2 | `Templates` (CRUD con `content:publish`, `newPage` con `content:edit` en el sitio elegido, `saveFromPage`, `seed`) y `PreviewLinks` (`create`/`revoke` con permiso editorial, `open` con sesión y firma); `PgTemplateRepository`, `PgPreviewLinkRepository`, `ch_page_template`, `ch_preview_link`, `ch_preview_key`; `PreviewLinksSigner` (HMAC como `NewsletterLinksSigner`, clave persistida sin `MEDIA_SECRET`) | hecho: `TemplatesTest`, `PreviewLinksTest`, `TemplatePreviewJsonTest` |
| P3 | Backoffice: *Plantillas* (`TemplatesCrud`, `@Publishers`), *Nueva página desde plantilla* (`/editorial/nuevaPagina`: sitio, plantilla, título, sección → formulario o editor visual), *Guardar como plantilla* en el formulario de una página; tres plantillas sembradas (`TemplateSeeder`) | hecho; falta probarlo en el navegador |
| P4 | Vista previa: *Enlace de vista previa* en el formulario → `PreviewLinksPage` (`/editorial/vistaPrevia/{id}`: días, ver como, crear, lista con estado y vistas, revocar); lista de solo lectura en el formulario; *Enlaces de vista previa* en el menú; `PreviewPage` en el portal (`/intranet/vista-previa/{id}.{firma}`) | hecho: `PreviewPageTest` (sesión, firma, caducidad, revocación, publicación, ver como); falta probarlo en el navegador |
| P5 | Números de newsletter: enlace de vista previa de un número, y plantillas como maqueta de número | pendiente (las páginas y artículos de una newsletter sí tienen ambas cosas: son items) |

## Cómo retomar

1. Leer este fichero y mirar la columna **Estado**.
2. Mateu: `gh pr list --repo miguelperezcolom/mateu --head feat/redwood-spectra-rich-text`.
3. ec-demo1: `git log feat/content-hub`.
4. Seguir por el primer paso pendiente.

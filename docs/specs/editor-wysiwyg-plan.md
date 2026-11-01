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
| 2.6 | PR #985 abierto por el fork (2.2–2.5 hechos: `mateu-rich-text-field` monta `oj-sp-input-rich-text-2`, con su editor propio de respaldo). PR → CI verde (`e2e`, `tooling-tests`, `frontend-lib`) → merge → release `gh release create v3.0-beta.N --target master` (notas en viñetas) → esperar "Build and publish" y Maven Central (`io.mateu:mateu-mvc/vaadin/redwood`). | pendiente |

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
| 1.1 | Subir `mateu.version` de los poms de ec-demo1 a la beta del nivel 2 (la cadena completa está en la memoria "Mateu release & shell redeploy": poms → shells → apply). | pendiente |
| 1.2 | `BlockForm.html` → `@Stereotype(FieldStereotype.richText)`. Comprobar que `PageRenderer`/`ContentRenderer` siguen resolviendo `[[media:id]]` y los embeds (embed.js) dentro del HTML que produce el editor. | pendiente |
| 1.3 | Campos según el tipo de bloque (`VisibilitySupplier`/`isHidden`, como ya hace `ItemForm`): HTML → título + html; FEATURED → item; AUTOMATIC_LISTING → sección + cuántos; QUICK_LINKS → enlaces; MEDIA → medio; BANNER → lo que use. La audiencia del bloque, siempre. | pendiente |
| 1.4 | "Insertar medio": botón/acción que abre la biblioteca (`MediaCrud`) con miniaturas y, al elegir, añade `[[media:id]]` al html (o rellena `mediaId` en un bloque MEDIA). `mediaId` pasa a ser un select de la biblioteca. Lo mismo en el cuerpo de `ItemForm`. | pendiente |
| 1.5 | Enlaces como tabla editable (`List<LinkForm>` con texto y URL) en lugar de líneas `Texto \| URL`; migrar `of()`/`toBlock()`. | pendiente |
| 1.6 | Vista previa junto al formulario que se refresca al editar (hoy "Previsualizar" repinta la página: ver commit d5ecd95a). | pendiente |
| 1.7 | Probar en local (kind, `deploy/local/up.sh`) en Vaadin y Redwood (`console.localhost:8800` / `rw.localhost:8800`, editorial → contenidos), con el usuario editor de la seed. Actualizar el manual de usuario de la intranet (docs) y el recorrido con capturas. | pendiente |

## Nivel 3 — edición sobre la página (por decidir)

Idea, sin compromiso:
- Un componente embebido en el backoffice que pinta la página con el mismo `PageRenderer` del portal.
- Clic en un bloque → edición en sitio (el editor rico del nivel 2).
- "+" entre bloques para añadir; arrastrar para reordenar.
- Panel lateral con tipo y audiencia del bloque; "Ver como…" sin salir.
- Punto delicado: estrechar la audiencia del bloque sin perder de vista la de la página.
- Decidir tras usar 1 y 2.

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
| D8 | Desplegar en local (kind), probar en Vaadin y Redwood, actualizar manual/recorrido de la intranet | desplegado y probado en local; manual del portal, arquitectura y topics actualizados (2e04674e); faltan capturas en el recorrido |

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

| # | Paso | Estado |
|---|---|---|
| F1.1 | Resolver las preguntas abiertas en el motor y en ec-definitions; cerrar el diseño aquí | pendiente |
| F1.2 | Proceso `solicitud-empleado` + formularios en ec-definitions (PR) | pendiente |
| F1.3 | Content-hub: tipos de solicitud, "nueva solicitud", outbox de arranque, proyección de estados (inbox) | pendiente |
| F1.4 | Portal: Mis solicitudes y Por aprobar (completar la tarea del responsable) | pendiente |
| F1.5 | Emails de estado (communication-service) y escalado por timeout | pendiente |
| F1.6 | Probar en local de punta a punta (Ana pide vacaciones → Carlos aprueba → RRHH → Ana ve "aprobada") y documentar | pendiente |

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
| F3.1 | Elegir de dónde sale el modelo (catálogo de IA o config propia) y la clave en local | pendiente |
| F3.2 | Servicio `Translator` (aplicación) + adaptador Spring AI, con tests del contrato (HTML y marcas intactos) | pendiente |
| F3.3 | Acción "Traducir con IA" en `ItemForm` (y bloques), aviso de revisión | pendiente |
| F3.4 | Probar en local en Vaadin y Redwood; documentar en el manual del backoffice | pendiente |

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
| N3 | **Aperturas y clics**. Píxel de apertura y enlaces con redirección firmada (HMAC como los medios, `/_intranet/n/{edición}/{destinatario}/…`), guardados por edición y destinatario. Informe por hotel y departamento en el backoffice. Los empleados ficticios de la demo van al email del usuario: contar por destinatario original. | pendiente |
| N4 | **Suscripción personal**: en el portal (p. ej. "Mis preferencias") cada empleado elige las secciones que quiere recibir y la frecuencia, dentro de lo que su audiencia le deja ver. `NewsletterService` filtra por ella; el default es todo. | hecho (4f15abb1): `NewsletterPreferences`/`ch_newsletter_pref`, `PreferencesPage` (Hydratable) |

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

## Cómo retomar

1. Leer este fichero y mirar la columna **Estado**.
2. Mateu: `gh pr list --repo miguelperezcolom/mateu --head feat/redwood-spectra-rich-text`.
3. ec-demo1: `git log feat/content-hub`.
4. Seguir por el primer paso pendiente.

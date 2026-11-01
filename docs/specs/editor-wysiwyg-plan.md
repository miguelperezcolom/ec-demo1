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
| 2.1 | Spike: cargar `oj-sp-input-rich-text-2` en una página Redwood de prueba (p. ej. `demo-museum` en Redwood). Comprobar: `value` entra y sale como HTML; evento de cambio (`valueChanged` de `value`) en blur o en cada tecla; `readonly`; cómo se comporta UPLOAD_IMAGE (¿base64 inline?, ¿evento?). Anotar aquí lo que salga. | pendiente |
| 2.2 | Sustituir `mateu-rich-text-field` por el componente de Spectra en las plantillas (`fields-extra.html`, `listing-body.html`). Cargar el loader de Spectra igual que los demás `oj-sp-*` que ya se usan (ver cómo se declaran los bundles en el `require` de la app VB). Conservar: la conversión de un Delta antiguo (`richTextHtml`), el saneado por allowlist al guardar (`richTextValueOf`/`sanitizeHtml`), el evento `valueChanged {value, updatedFrom:'internal'}` que esperan las cadenas. | pendiente |
| 2.3 | Barra por defecto razonable (`toolbarItems`): títulos, negrita/cursiva/subrayado, listas, enlace, línea, deshacer/rehacer, ver código. Sin fuentes/colores. UPLOAD_IMAGE solo si 2.1 dice que encaja. | pendiente |
| 2.4 | `mateu-rich-text-field` queda como respaldo si el componente no carga (CDN caído): decidirlo en 2.2. Actualizar el comentario de `richtext.mjs` ("JET has no rich text editor" → Spectra sí). | pendiente |
| 2.5 | Tests: `poc/test*.mjs` (la cobertura de `tooling-tests` exige líneas ≥98 %, funciones ≥95 %, ramas ≥85 %), `npm run bridge && npm run build && npm run copy` (bundles regenerados: lo comprueba `bundle-freshness`), e2e si cubre richText en Redwood. CHANGELOG en Unreleased. | pendiente |
| 2.6 | PR → CI verde (`e2e`, `tooling-tests`, `frontend-lib`) → merge → release `gh release create v3.0-beta.N --target master` (notas en viñetas) → esperar "Build and publish" y Maven Central (`io.mateu:mateu-mvc/vaadin/redwood`). | pendiente |

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

## Cómo retomar

1. Leer este fichero y mirar la columna **Estado**.
2. Mateu: `gh pr list --repo miguelperezcolom/mateu --head feat/redwood-spectra-rich-text`.
3. ec-demo1: `git log feat/content-hub`.
4. Seguir por el primer paso pendiente.

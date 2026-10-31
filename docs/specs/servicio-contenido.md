# Spec servicio de contenido e intranet

## Contexto y objetivo

Se construye un servicio de contenido de plataforma con visibilidad por audiencia, y la intranet de comunicación interna es su primer consumidor. El mismo servicio alimenta los slots de contenido de cualquier UI Mateu.

Se construye **como demo dentro de ec-demo1**, sobre la identidad que ya existe: Keycloak (realm `ec-demo1`) y el servicio de usuarios (`control-plane/users`), que es la fuente de verdad de usuarios, grupos, roles y permisos. La sección "Implementación en ec-demo1" concreta cómo encaja; el resto de la spec describe el servicio tal como se haría en producción.

La decisión de hacerlo in-house frente a comprar se apoya en tres hechos: el filtrado afecta a contenidos, menús, listados y búsqueda (no solo a páginas), Mateu resuelve la UI en servidor y nunca envía al cliente lo que el usuario no puede ver, y el mismo modelo sirve para el contenido editorial embebido en las aplicaciones.

Principio central: **todo lo que enumera contenido pasa por una única función de visibilidad.** Páginas, bloques, menús, listados, búsqueda, slots, newsletter y MCP son consumidores de ella; ninguno filtra por su cuenta.

Solo la fase de slots depende de la spec de Mateu (microfrontend resuelto en servidor y `@ContentSlot`).

## Alcance v1 y no-objetivos

Entra en v1:

- Contenido con audiencia: artículos, páginas, avisos, enlaces y documentos, con bloques dentro de las páginas.
- Navegación, listados y búsqueda filtrados por visibilidad.
- Multiidioma por traducciones del mismo item, con idioma de fallback.
- Workflow editorial: borrador, en revisión, publicado, retirado, con fechas de publicación y caducidad.
- Previsualización "ver como" un perfil.
- Slots de contenido para otras aplicaciones Mateu.
- Newsletter por email, segmentada.
- Seguimiento de lectura y confirmación obligatoria, solo en items marcados.
- Tools MCP de consulta con el token del usuario, incluida una búsqueda semántica (RAG) para el chat.
- Favoritos por usuario.
- Biblioteca de medios: subida de ficheros pequeños (imágenes, PDF, audio, vídeo) y, para el resto, URL o código de inserción de YouTube, Vimeo, Drive o cualquier sitio; los medios se incrustan en los contenidos.
- Búsqueda textual dentro del contenido.

Fase 2:

- Feedback opcional por item: comentarios, reacciones y encuestas.
- Valoración opcional por item y por slot (formato a decidir).
- Notificaciones push al móvil a través de la app nativa.

Fuera de v1, explícitamente:

- Chat y mensajería entre empleados.
- Contenido publicado por empleados: solo editores publican items; los empleados comentan, reaccionan y responden encuestas.
- Contenido público o anónimo: las webs públicas tienen su propio pipeline.
- Modelo de permisos propio: las audiencias se expresan siempre sobre atributos del empleado, y los roles editoriales son roles y permisos del servicio de usuarios.

## Arquitectura

Un servicio de dominio hexagonal con varios adaptadores de entrada; las UIs y la newsletter son consumidores, no contienen lógica de visibilidad.

| Componente | Responsabilidad | Tecnología |
| --- | --- | --- |
| Núcleo de contenido | Dominio, workflow, función de visibilidad | Spring Boot hexagonal |
| Directorio de empleados | Proyección local de empleados y sus atributos; universo de hashes | Puerto; adaptador que consume los eventos del servicio de usuarios |
| Motor de búsqueda | Índice con los hashes que ven cada documento y filtrado en la consulta | Puerto; adaptador FTS de PostgreSQL |
| Búsqueda semántica (RAG) | Fragmentos de cada traducción publicada, vectorizados, buscados por similitud con el mismo filtro | pgvector + servicio de embeddings del clúster |
| Biblioteca de medios | Ficheros subidos y medios externos, incrustables en el contenido | Núcleo; ficheros en la base de datos |
| Backoffice editorial | Edición, audiencias, workflow, ver como, slots, newsletter | App Mateu |
| Portal intranet | Home, secciones, artículos, búsqueda, confirmaciones | App Mateu |
| Remoto de slots | Expone fragmentos de UI por clave de slot | Endpoint Mateu, remoto registrado |
| Emisor de newsletter | Resuelve ediciones por grupo de hash y envía | Job del núcleo + SMTP |
| Adaptador MCP | Tools de consulta con el token del usuario | Patrón MCP ya usado en ec-demo1 |

Eventos de dominio (ver modelo de dominio) a Kafka por el outbox compartido, para reindexar e invalidar caché, y para que otros sistemas reaccionen si lo necesitan.

## Modelo de dominio

Ocho agregados y un servicio de dominio. Las referencias entre agregados son siempre por id; ninguno contiene a otro.

### Agregados

| Agregado | Raíz y contenido | Invariantes | Por qué este límite |
| --- | --- | --- | --- |
| Item | Item; traducciones, versiones, bloques; audiencia como VO | Transiciones de estado, fechas coherentes, versión publicada inmutable, traducción en el idioma por defecto | Traducciones, versiones y bloques cambian y se publican juntos |
| Navegación | Árbol completo de secciones | Sin ciclos, orden único entre hermanos | Mover o reordenar afecta a hermanos; cambia poco, sin contención |
| Slot | Slot por clave; variantes con audiencia VO y prioridad | Clave inmutable, prioridad única entre variantes, huérfano en vez de borrado | Metadatos vienen del descriptor de Mateu; el contenido, del editor |
| Audiencia con nombre | Nombre y expresión | Expresión válida sobre la lista cerrada de atributos | Se reutiliza; redefinirla afecta a todo lo que la referencia |
| Edición de newsletter | Edición; items por id; envíos por grupo de hash e idioma | No se modifica una vez enviada | Reintentar un grupo sin reenviar los demás |
| Empleado | Atributos VO y hash derivado | Atributos de la lista cerrada | Proyección escrita solo por el consumidor de eventos del servicio de usuarios; casi un read model |
| Favoritos | Uno por empleado; items por id | Sin duplicados | Separado de Empleado para no competir con la sincronización |
| Acuse | Clave (item, versión, empleado); tipo `LECTURA` o `CONFIRMACION` | Idempotente | Miles de acuses del mismo item no compiten por la versión de Item |

**Política de visibilidad** (servicio de dominio sin estado): evalúa audiencia + atributos + instante. La herencia restrictiva no es un invariante almacenado: la audiencia efectiva se calcula siempre como `contenedor AND propia`, así que se cumple aunque se redefina una audiencia con nombre. La validación al guardar es solo un aviso.

**Eventos que cruzan límites:** `ItemPublicado`, `ItemRetirado`, `AudienciaRedefinida`, `VarianteSlotPublicada`, `NavegacionCambiada`, `EmpleadoCambiado` (alta, cambio de atributos o baja, a partir de los eventos del servicio de usuarios). Con ellos se reindexa, se invalida caché y se resuelven newsletters.

Además, un scheduler emite `ItemEntraEnVigor` y `ItemCaducado` cuando se cruza una fecha de publicación o de caducidad. **Solo sirven para invalidar cachés y avisar a otros sistemas:** la visibilidad no los espera, porque el tiempo se filtra en cada consulta (ver Visibilidad).

Detalle por área:

### Contenido

- `Item`: id, tipo (`ARTICULO`, `PAGINA`, `AVISO`, `ENLACE`, `DOCUMENTO`), sección, audiencia, autor, idioma por defecto.
    - Estado: `BORRADOR` → `EN_REVISION` → `PUBLICADO` → `RETIRADO`. Desde `EN_REVISION` se puede volver a `BORRADOR` (rechazo).
    - Fechas de publicación (opcional, por defecto el momento de publicar) y caducidad (opcional). **Programado y caducado no son estados:** un item `PUBLICADO` con fecha de publicación futura está programado, y uno con la caducidad pasada está caducado. Ambos se derivan del instante, sin intervención ni job.
    - Traducciones: por idioma, título, resumen y cuerpo (HTML saneado). Sin traducción en el idioma del usuario, se usa la del idioma por defecto.
    - Versiones: cada publicación guarda una versión inmutable; editar un item publicado crea un borrador que no afecta a lo visible.
    - Flags: `seguimientoLectura`, `confirmacionObligatoria` (con fecha límite).
    - `versionConfirmacionVigente`: la versión desde la que cuentan las confirmaciones. Al publicar una versión nueva, el editor decide si exige reconfirmar: si lo exige, pasa a ser la nueva versión; si no, se mantiene y las confirmaciones anteriores siguen valiendo.
- `Bloque`: componente dentro de una página (destacado, listado automático, banner, enlaces rápidos, HTML). Audiencia opcional que solo puede restringir la de su página.
- `Seccion`: nodo del árbol de navegación, con orden y nombre traducido. Sin audiencia propia: visible si contiene algo visible.
- `Slot`: clave estable (`aplicacion.pantalla.nombre`), aplicación, descripción, fallback; cargado de los descriptores que genera Mateu. Su contenido son variantes (bloques sin página) con audiencia y prioridad.

### Audiencia

- `Audiencia`: expresión estructurada, árbol de `AND`/`OR` con hojas `atributo IN (valores)`. Vacía = todos los empleados.
- Audiencias con nombre y reutilizables ("Recepción Canarias") además de las ad hoc.
- Herencia restrictiva: la audiencia efectiva de bloques y variantes de slot es siempre el AND con la de su contenedor; al guardar solo se avisa si nadie la cumpliría.

### Usuario

- `Atributos`: lista cerrada (empresa, país, región, hotel, departamento, puesto, idioma). En la demo son atributos del usuario en el servicio de usuarios, con valores de un catálogo cerrado por atributo.
- El usuario de cada petición es el del token (su `UserId`, que es el `username` en Keycloak); sus atributos se leen del directorio local, no del token.
- `hashAtributos`: derivado de los atributos de visibilidad normalizados; clave de caché, de agrupación y de la proyección de visibilidad.
- `Directorio`: proyección local de empleados con sus atributos, alimentada por los eventos del servicio de usuarios. Fuente del universo de hashes y de los destinatarios (newsletter, y push en fase 2).
- `Favorito`: usuario, item, fecha. Se oculta si el item deja de ser visible; reaparece si vuelve a serlo.

### Comunicación

- `Acuse` de tipo `LECTURA`: usuario, item, versión, fecha. Solo en items con seguimiento.
- `Acuse` de tipo `CONFIRMACION`: usuario, item, versión, fecha. Un usuario tiene el item confirmado si tiene una confirmación con versión `>= versionConfirmacionVigente`.
- `EdicionNewsletter`: título, items ordenados, audiencia de envío, fecha, estado.

### Preparado para la fase 2

No se implementa ahora, pero el modelo no debe impedirlo:

- `ConfiguracionFeedback` por item: comentarios (desactivados / públicos / privados para editores), reacciones, encuesta, valoración, moderación previa o posterior.
- `Comentario`, `Reaccion`, `Encuesta` (anónima con participación separada de respuestas, umbral mínimo de 5 en desgloses), `Valoracion` (una por usuario y versión).
- `Dispositivo` (token de push por usuario y plataforma) y `Notificacion` (item, audiencia restringible, prioridad, métricas).

## Visibilidad

Una única función decide qué ve cada usuario, y es pura respecto a sus entradas: atributos del usuario, instante y consulta.

```java
List<ItemVisible> visibles(Atributos usuario, Instant ahora, Consulta consulta);
boolean esVisible(Atributos usuario, Instant ahora, ItemId item);
```

Un item es visible si se cumplen las tres condiciones:

1. Estado `PUBLICADO`, con `publicacion <= ahora` y (`caducidad` vacía o `caducidad > ahora`).
2. La expresión de audiencia del item se evalúa a verdadero con los atributos del usuario.
3. Para un bloque, además, la audiencia de su página.

Reglas:

- **Herencia restrictiva.** La audiencia efectiva de un bloque es `audienciaPágina AND audienciaBloque`. Al guardar, el backoffice avisa si la combinación no puede cumplirla nadie.
- **Hash de atributos.** `hash(atributos de visibilidad normalizados)` identifica a todos los usuarios que ven exactamente lo mismo. Solo entran atributos utilizables en audiencias: el idioma no (elige traducción, no visibilidad), y los derivados (país y región a partir del hotel) no multiplican combinaciones. Estimación a escala real: 2.000–5.000 hashes distintos; en la demo, unas decenas.
- **Visibilidad por audiencia materializada.** Proyección `item → hashes que lo ven` (con un token `TODOS` para audiencias vacías). La calcula la política de visibilidad, que sigue siendo la única que evalúa expresiones, sobre el universo de hashes que sale del directorio. Se recalcula con `ItemPublicado`, `AudienciaRedefinida`, `VarianteSlotPublicada` y con cada hash nuevo en el directorio. A escala real, recalcular todo son decenas de millones de evaluaciones (5.000 hashes × ~10.000 items y bloques): hay que medirlo, pero debería quedarse en segundos o pocos minutos.
- **Estado y tiempo en la consulta.** Estado y fechas se filtran en cada consulta, no se materializan: lo programado aparece a su hora sin esperar a ningún recálculo.
- **Un único filtro para todos los consumidores.** Listados, menú, búsqueda, slots y newsletter filtran por `hashes(item) && {hashUsuario, TODOS}` más el filtro temporal, dentro del almacén o del motor de búsqueda. No se traduce la expresión a SQL ni a consulta del motor.
- **Hash desconocido.** Si llega un usuario con un hash que aún no está materializado (alta reciente con combinación nueva, o evento del directorio todavía en camino), antes de responder se materializa ese hash de forma síncrona: un hash por todos los items son milisegundos. Después se consulta como siempre. Nunca se filtra en memoria.
- **Usuario fuera del directorio.** Si el usuario del token aún no está en el directorio, se le trata como sin atributos: solo ve contenido con audiencia `TODOS`.
- **Hashes huérfanos.** Un job periódico elimina de la proyección y de la caché los hashes que ya no tiene ningún empleado del directorio.
- **Consistencia eventual.** Entre publicar o redefinir una audiencia y que se refleje pasan unos segundos. Aceptable para comunicación interna; el backoffice muestra cuándo está aplicado.
- **Caché.** Menús, listados, home y slots se cachean por hash. Los eventos que recalculan la proyección, y `ItemEntraEnVigor`/`ItemCaducado`, invalidan la caché de los hashes afectados.
- **Cambio de atributos de un usuario.** Cambia su hash y, en cuanto llega el evento al directorio, lo que ve; no hay más estado por usuario que sincronizar.

## Consumidores

Todos llaman a la función de visibilidad; ninguno tiene lógica de filtrado propia.

- **Navegación.** Árbol de secciones; un nodo se muestra si él o un descendiente contiene algo visible. Las secciones vacías desaparecen. Caché por hash de atributos e idioma.
- **Listados.** "Últimas noticias", "avisos", listados automáticos de bloques: consultas con filtro de visibilidad, paginadas en el almacén.
- **Búsqueda.** Cada documento indexado lleva la lista de hashes que lo ven (proyección materializada); la consulta va acotada por el hash del usuario dentro del motor. Paginación, contadores y autocompletado salen ya filtrados. Una traducción = un documento del índice.
- **Slots.** El remoto de slots recibe la clave y la identidad, resuelve la variante de mayor prioridad visible para el usuario y devuelve el JSON de UI. Sin variante visible, respuesta vacía y la app pinta el fallback. Declara caché por hash de atributos.
- **Newsletter.** Para cada edición, se agrupan los destinatarios por hash de atributos, se calcula una vez por grupo qué items de la selección son visibles y en qué idioma, y se envía un email por grupo e idioma. Un grupo sin items visibles no recibe nada.
- **MCP.** Tools con el token del usuario y el mismo filtro:
    - `preguntar_intranet`: los fragmentos más parecidos a una pregunta (RAG), para que el agente responda citando los items;
    - `buscar_contenido`;
    - `leer_item`;
    - `confirmaciones_pendientes`: items visibles con confirmación obligatoria que el usuario aún no ha confirmado en la versión vigente.
- **Medios.** Un fichero solo se sirve a quien puede ver algún item publicado que lo usa (consulta en el almacén con el mismo filtro).

### Medios

- Biblioteca de medios: ficheros subidos (imágenes, PDF, audio, vídeo; hasta 20 MB, configurable) o medios externos (URL, o el código de inserción que da el proveedor: solo se guardan el proveedor y el id, nunca el HTML pegado).
- Se incrustan con un bloque `MEDIO` o con la marca `[[media:id]]` en el cuerpo de cualquier item o bloque HTML.
- Un `<img>` o un `<video>` no envían el token: las URLs de los medios van firmadas (HMAC, 2 horas) para el hash del perfil que pintó la página, y al servirlas se vuelve a comprobar la visibilidad.
- Mateu elimina los `<iframe>` del HTML: los vídeos externos y los PDF se pintan como un hueco (`data-ec-embed`) con miniatura y enlace, y un script que cargan los shells (`embed.js`, como `push.js`) lo cambia por el reproductor oficial, solo para una lista cerrada de hosts (youtube-nocookie, Vimeo, la vista previa de Drive y los propios medios).

### Búsqueda y chat

- La búsqueda textual usa `tsvector` de PostgreSQL (configuración `simple`, multilingüe, por prefijo) dentro de la misma consulta que filtra la visibilidad.
- Para el chat, cada traducción publicada se trocea en fragmentos de unos 700 caracteres que un job vectoriza con el servicio de embeddings del clúster (multilingual-e5-small, 384 dimensiones) en pgvector. La búsqueda por similitud se une a la proyección en la misma consulta, así que el agente solo recibe lo que la persona que chatea puede ver.

### Favoritos

- "Mis favoritos" es un listado más: favoritos del usuario cruzados con la función de visibilidad.

### Feedback (fase 2)

- Comentarios, reacciones, encuestas y valoraciones solo existen para quien puede ver el item: se comprueba con `esVisible` en cada lectura y escritura.
- Comentarios privados: el autor ve los suyos; los demás empleados no ven ninguno.
- Los comentarios no se indexan en la búsqueda ni se exponen por MCP.
- Retirar o caducar un item congela su feedback (solo lectura para editores).
- La valoración se muestra al pie del item y en slots si está activada; el agregado solo lo ven los editores.

### Notificaciones push (fase 2)

- Al publicar un item con notificación, se resuelven los destinatarios en el directorio de empleados con la proyección de visibilidad y se envía a sus dispositivos activos.
- El texto de la notificación es solo el título en el idioma del usuario; nada sensible, porque se ve en la pantalla de bloqueo.
- El deep link abre el item en la app; al abrir se vuelve a comprobar la visibilidad, por si los atributos o el item han cambiado.
- Franja horaria configurable sin envíos (según zona horaria del hotel del usuario), salvo prioridad urgente.
- Límite de notificaciones por usuario y día, para que el canal no se queme.
- El proveedor (FCM/APNs) va detrás de un puerto.

## Backoffice editorial

App Mateu para editores y responsables de comunicación. Roles mínimos: editor (crea y propone), publicador (aprueba y publica), administrador (secciones, audiencias con nombre, slots). Se definen como roles y permisos en el servicio de usuarios (ver "Implementación en ec-demo1").

- **Workflow.** Borrador → en revisión → publicado (inmediato o con fecha futura) → retirado. Un editor no puede publicar sin aprobación salvo que tenga rol de publicador.
- **Editor de audiencias.** Constructor visual de la expresión con los valores reales de cada atributo, y el número de empleados del directorio que la cumplen. Si sale 0, aviso antes de guardar.
- **Ver como.** Renderiza el portal tal como lo vería un perfil concreto, incluidos menú, listados y búsqueda. El perfil se elige de tres formas:
    - un empleado del directorio;
    - un conjunto de atributos;
    - una audiencia con nombre: el backoffice lista los perfiles (hashes) del directorio que la cumplen, con su número de empleados, y el editor elige uno.

    Usa la misma función de visibilidad, no una simulación.
- **Slots.** Lista de slots registrados por aplicación y pantalla, con su estado (vacío, con contenido, número de variantes). Edición de variantes por audiencia y prioridad. Slots que dejan de aparecer en los descriptores se marcan como huérfanos, no se borran.
- **Newsletter.** Componer edición eligiendo items, previsualizar por perfil, programar envío.
- **Confirmaciones.** Para items con confirmación obligatoria: porcentaje confirmado, desglose por hotel/departamento y lista de pendientes. Exportable.
- **Feedback y valoración (fase 2).** Activar comentarios, reacciones, encuesta y valoración al editar el item. Cola de moderación con comentarios denunciados (y pendientes, si la moderación es previa). Resultados de encuestas con exportación, respetando el umbral mínimo en modo anónimo. Informe de valoración por item y slot, ordenable para encontrar el contenido peor valorado.
- **Notificaciones (fase 2).** Al publicar, opción "notificar" con prioridad y audiencia. Antes de enviar, número de destinatarios y de dispositivos. Después, métricas de entrega y aperturas.

## Implementación en ec-demo1

### Módulo

- Módulo nuevo `supporting/content-hub` (artifact `content-hub`, paquete `io.mateu.ecdemo1.contenthub`), con las mismas versiones que `supporting/content`: Spring Boot 4.0.x, Java 21, Mateu `3.0-alpha.430`. `supporting/content` (contenidos, etiquetas y tipos) no se toca.
- Base de datos propia `content_hub` en `cp-postgres`, el servidor con pgvector (lo necesita el RAG); la crea el initContainer del despliegue. Librería compartida `supporting/messaging` para outbox e inbox.
- Puerto 8135. Manifest en `deploy/manifests/`, imagen en `deploy/build-images.sh` y arranque en `deploy/local/up.sh`, como los demás servicios.
- Dos UIs Mateu en el mismo servicio:
    - portal en `/_intranet`, en el shell principal;
    - trabajo editorial (contenidos, medios, contenido de slots, newsletter, ver como, confirmaciones) como menú *Editorial* de la misma UI, en el plano de datos, donde trabajan los editores: Mateu lo oculta y rechaza sus rutas sin un permiso `content:*` (`@EyesOnly`, con un `PrincipalResolver` que lee `X-User-Scopes`);
    - configuración (secciones, audiencias con nombre, slots, directorio, visibilidad) en `/_content-hub`, en la consola de control (`control-shell`, como `/_users`), con `ai-admin` y `content:admin`. Los slots se parten: el hueco es configuración, sus variantes son trabajo editorial (`content:publish`).

  Rutas en `consoles/gateway` (`application.yaml`) y autenticación obligatoria en su `SecurityConfig`.

### Cambios en el servicio de usuarios

- **Atributos en `User`.** VO `AtributosEmpleado` con la lista cerrada (empresa, país, región, hotel, departamento, puesto, idioma); todos opcionales. País y región se derivan del hotel cuando hay hotel.
- **Catálogo de valores.** Cada atributo toma sus valores de un catálogo cerrado, editable en la UI de usuarios: un CRUD nuevo "Valores de atributo". Los hoteles del catálogo coinciden con los códigos que ya usa la demo (por ejemplo, `MRU01`).
- **UI.** El formulario de usuario muestra los atributos, con selectores sobre el catálogo.
- **Eventos a Kafka.** `UserCreated`, `UserUpdated` y `UserDeleted` llevan los atributos y se publican además en el topic `employee-directory`, por el outbox compartido. Hoy ese outbox solo entrega a Keycloak (`IdentityTransport`), así que hace falta un segundo destino. La sincronización con Keycloak no cambia: los atributos **no** viajan en el token.
- **Carga inicial.** Al arrancar con el directorio vacío, `content-hub` pide una réplica completa por el topic `employee-directory-requests` (un topic propio: `projection-requests` ya lo consume otro servicio con otra carga), y el servicio de usuarios republica todos los usuarios en `employee-directory`.

### Identidad y roles editoriales

- El usuario de cada petición sale del token de Keycloak (`preferred_username`, que es el `UserId` del servicio de usuarios). Sus atributos salen del directorio local.
- Los roles editoriales son permisos del servicio de usuarios, con scopes `content:edit`, `content:publish` y `content:admin`, y tres roles de ejemplo: Editor, Publicador y Administrador de contenido.
- `content-hub` no consulta permisos: el gateway (`consoles/gateway`) los obtiene de `AuthService.getAuthInfo` y los añade como cabeceras `X-User-Roles` y `X-User-Scopes`, después de borrar las que mande el cliente. No reescribe el token: los shells y el front office lo verifican contra Keycloak y un token firmado por el gateway lo rechazarían. Si la cabecera no llega (el gateway no pudo preguntar), el rol `admin` del realm conserva todos los permisos.
- El MCP no pasa por el gateway (lo llama ia-agent dentro del clúster): `content-hub` verifica allí el token reenviado contra Keycloak.
- Los atributos de audiencia **no** se añaden a ese enriquecimiento: van por el directorio local, porque el newsletter, el ver como y el universo de hashes los necesitan sin que haya petición del usuario.

### Adaptadores

- **Búsqueda:** FTS de PostgreSQL. Columna `hashes text[]` con índice GIN en la proyección y en el índice de búsqueda; el filtro es `hashes && array[:hashUsuario, 'TODOS']`.
- **Newsletter:** SMTP a través del relay postfix del entorno (`deploy/manifests/11-postfix.yaml`). Como los empleados de la demo son ficticios, en la demo todo envío se redirige a un buzón configurable (`NEWSLETTER_REDIRECT_TO`), conservando en el asunto el grupo y el idioma.
- **MCP:** mismo patrón que `customer-mdm-service` (`McpToolsConfig` y `CustomerMcpTools`), con el token del usuario, y registrado en el agente como los demás servidores MCP.
- **Slots:** requieren la extensión del componente microfrontend de Mateu para renderizar en servidor (y `@ContentSlot`), que otro agente está desarrollando en el repo de Mateu. Mientras llega, el remoto de slots es REST: `GET /_intranet/api/slots/{clave}` con el token del usuario devuelve la variante visible de mayor prioridad con su HTML ya expandido, o 204 (la aplicación pinta el fallback), y la cabecera `X-Content-Cache-Key` con el hash del perfil. El slot de demo es `front-office.bienvenida.destacado`.
- **MCP:** registrado en el plano de control de IA como servidor `intranet` para `console-agent` y `reception-agent` (ejemplo en `gitops/example/ia/mcp/intranet.yaml`).

### Datos de demo

Una semilla, en el servicio de usuarios y en `content-hub`, con:

- unos 30 empleados repartidos por varios hoteles, departamentos y puestos, entre ellos un editor y un publicador;
- audiencias con nombre ("Recepción Canarias", "Dirección de hotel"…);
- secciones e items con audiencias distintas, al menos uno programado, uno caducado y uno con confirmación obligatoria;
- una edición de newsletter.

Debe dejar "ver como" listo para enseñar diferencias entre perfiles. Se integra en `supporting/demo-reset` para que el reset de la demo la restaure.

## Decisiones abiertas

En la demo quedan cerradas por la sección anterior: fuente de atributos, lista de atributos, directorio, motor de búsqueda y base de datos. Siguen abiertas:

| Decisión | Opciones | Recomendación | Quién decide |
| --- | --- | --- | --- |
| Editor de texto rico | Componente existente en Mateu / integrar uno | Validar con un artículo real en la fase 2 del plan | Arquitectura |
| Microfrontend resuelto en servidor y `@ContentSlot` | En desarrollo por otro agente en Mateu | Acordar con ese trabajo el contrato del remoto de slots (clave, identidad, caché por hash) antes de la fase 10 | Mateu |
| Formato de la valoración (fase 2) | Estrellas 1–5 / útil sí-no | Útil sí-no con comentario opcional: más respuestas y más accionable; el modelo admite ambos | Comunicación interna |

Para un despliegue real, fuera de la demo, quedan además: acceso del personal de hotel desde fuera de la red corporativa (Seguridad + Sistemas); revisión con el DPD del seguimiento de lectura, y en fase 2 de comentarios, encuestas y valoración (DPD + RRHH); informar al comité de empresa antes de activar el seguimiento de lectura (RRHH); proveedor de push y distribución de la app (fase 2); owner de producto (Comunicación interna, no IT).

## Plan por fases y criterios de aceptación

Cada fase termina en checkpoint con revisión antes de seguir.

1. **Dominio y visibilidad.** Modelo, estados, audiencias, función de visibilidad y proyección materializada item → hashes. Puerto del directorio con un adaptador en memoria (es la fuente del universo de hashes). Sin UI.
2. **Backoffice mínimo.** CRUD de items, secciones y audiencias; workflow.
3. **Portal y ver como.** Home, navegación, artículo, listados; caché por hash. "Ver como" en el backoffice, sobre el portal.
4. **Directorio real.** Atributos y catálogo en el servicio de usuarios, eventos a `employee-directory`, carga inicial por `projection-requests`, consumidor en `content-hub`. Roles editoriales leídos del claim `scope` del token enriquecido por el gateway. Semilla de demo.
5. **Búsqueda.** Indexado por eventos y consulta filtrada.
6. **Favoritos.**
7. **Lectura y confirmaciones.**
8. **Newsletter.**
9. **MCP.**
10. **Slots.** Remoto de slots, carga de descriptores, gestión en backoffice, primer slot en el front office. Requiere la extensión del microfrontend de Mateu, en desarrollo en paralelo.
11. **Despliegue de la demo.** Manifest, imagen, gateway, shells, integración en `demo-reset`, y un recorrido e2e en `e2e/`.

Fase 2, fuera del alcance de esta implementación:

1. **Feedback.** Configuración por item, comentarios con moderación, reacciones, encuestas anónimas y nominales.
2. **Valoración.** Por item y por slot, con informe para editores.
3. **Notificaciones push.** Registro de dispositivos, envío por audiencia con proveedor detrás de puerto, deep links, franjas y límites.

| Caso | Resultado esperado |
| --- | --- |
| Item con audiencia que el usuario no cumple | No aparece en página, menú, listado, búsqueda, favoritos, slot, newsletter ni MCP |
| Bloque con audiencia que no está contenida en la de su página | Solo lo ven quienes cumplen ambas; el backoffice avisa al guardar |
| Sección sin nada visible para el usuario | No aparece en el menú |
| Item publicado con fecha futura | Invisible hasta su fecha; visible después sin intervención ni job |
| Item caducado | Deja de ser visible en todos los consumidores |
| Editor sin rol de publicador | Puede enviar a revisión, no publicar |
| Búsqueda paginada | Contadores y páginas solo cuentan items visibles |
| Traducción inexistente en el idioma del usuario | Se muestra el idioma de fallback |
| Ver como un perfil | Resultado idéntico al de un usuario real con esos atributos |
| Cambio de atributos de un usuario en el servicio de usuarios | Lo que ve cambia en cuanto llega el evento al directorio (segundos) |
| Dos usuarios con los mismos atributos | Comparten entradas de caché |
| Favorito de un item que deja de ser visible | Desaparece de "Mis favoritos"; reaparece si vuelve a ser visible |
| Slot sin variante visible | Respuesta vacía; la app pinta el fallback |
| Newsletter | Un envío por grupo de hash e idioma; grupos sin items no reciben nada |
| Nueva versión con reconfirmación | Las confirmaciones anteriores no cuentan para la nueva versión |
| Nueva versión sin reconfirmación | Las confirmaciones anteriores siguen contando |
| Usuario con hash aún no materializado | Ve lo correcto (materialización síncrona de su hash, sin filtrar en memoria) |
| Usuario del token que no está en el directorio | Solo ve contenido con audiencia `TODOS` |
| Redefinir una audiencia con nombre | Todos los consumidores reflejan el cambio tras el recálculo; el backoffice indica cuándo está aplicado |
| Proyección recalculada desde cero | Resultado idéntico a evaluar cada expresión para cada hash |

Los criterios de feedback, valoración y push se definirán en la spec de la fase 2. El primer criterio se verifica con un test que recorre todos los consumidores con el mismo usuario y el mismo item.

## Prompt de arranque

Para pegar al abrir la sesión de implementación en ec-demo1:

```markdown
Vas a implementar la spec docs/specs/servicio-contenido.md en ec-demo1: un servicio de contenido con visibilidad por audiencia (módulo nuevo supporting/content-hub), cuyo primer consumidor es una intranet, sobre el Keycloak y el servicio de usuarios (control-plane/users) que ya existen.

Antes de nada:
- Lee la spec entera, el servicio de usuarios y supporting/messaging, y dime qué te parece ambiguo o contradictorio. No empieces hasta que lo aclaremos.
- Propón la estructura del módulo y el modelo de dominio por agregados, y espera mi aprobación.

Reglas de trabajo:
- Trabaja por las fases del "Plan por fases". Al terminar cada fase, detente, resume qué has hecho y por qué, y espera mi revisión.
- Arquitectura hexagonal, con la misma estructura de paquetes que control-plane/users (domain/aggregates, application, infra/in, infra/out). El dominio no depende de Spring, Mateu ni del almacén.
- Respeta los límites de agregado de la spec: referencias entre agregados solo por id. Si un caso de uso parece pedir modificar dos agregados en una transacción, para y propón cómo resolverlo con eventos.
- Entre servicios, eventos por outbox → Kafka antes que llamadas síncronas. Los roles editoriales vienen en el token que enriquece el api gateway (claim `scope`): no llames a AuthService.
- La política de visibilidad es la única que evalúa expresiones de audiencia. Los consumidores filtran por la proyección materializada item → hashes más el filtro temporal, siempre dentro de la consulta al almacén, nunca cargando y filtrando en memoria. Si necesitas filtrar de otra forma, es un error de diseño: para y avisa.
- El test que compara la proyección recalculada desde cero con la evaluación directa de cada expresión para cada hash es obligatorio desde la fase 1. Usa generación de casos (property-based) para las expresiones.
- Las decisiones que la spec deja abiertas no las cierres tú: usa un puerto con un adaptador provisional y déjalo anotado.
- Las UIs se hacen con Mateu, como las demás de ec-demo1.
- Cada criterio de aceptación tiene que acabar cubierto por un test; el de "no aparece en ningún consumidor" es el más importante.
- Compila y lanza los tests con JDK 21 y un repositorio Maven privado (-Dmaven.repo.local=...), porque otros agentes comparten ~/.m2.
- No implementes nada de la fase 2, pero no tomes decisiones de modelo que la impidan.
```

---
title: Operación de la intranet
description: Desplegar content-hub — manifiesto, base de datos, variables de entorno, redespliegue local, datos de demo, registro del MCP y problemas conocidos y límites.
---

## Despliegue

`deploy/manifests/90-content-hub.yaml`: un `Deployment` de **una réplica** con estrategia `Recreate` y su
`Service` en el puerto **8135**. Lo aplica `deploy.sh` (antes que `cp-postgres`: el initContainer
espera a que exista); la imagen es
`miguelperezcolom/ec-demo1-content-hub` (está en el `APPS` de `deploy/build-images.sh`).

- **initContainer `create-database`** (`pgvector/pgvector:pg16`): espera a `cp-postgres` y crea la base
  de datos **`content_hub`** si no existe, con las credenciales del Secret `ec-cp-postgres`. Las tablas
  (y la extensión `vector`) las crea el servicio al arrancar (`schema.sql`, solo aditivo).
- **initContainer `wait-for-kafka`**: espera a `redpanda:19092`.
- Sin ruta para el MCP: solo `/_intranet/**` (hosts de datos: portal y editorial) y `/_content-hub/**`
  (hosts de control: configuración) en el gateway.
- Sondas en `/actuator/health/readiness` y `/liveness`; métricas en `/actuator/prometheus`.
- Lo usan además: el gateway (rutas, `CONTENT_HUB_URL`, y `AUTH_GRPC_ADDRESS` para los permisos), los dos
  shells (`RemoteMenu` *Intranet* y `@Script` de `embed.js`), el servicio de usuarios (topics del
  directorio) y el servicio `embeddings` (RAG).

### Variables de entorno

| Variable | Valor en el manifiesto | Por defecto (`application.yaml`) | Para qué |
| :------- | :--------------------- | :------------------------------- | :------- |
| `SERVER_PORT` | `8135` | `8135` | |
| `DB_URL` | `jdbc:postgresql://cp-postgres:5432/content_hub` | `…127.0.0.1:5432/content_hub` | Su base de datos, en el PostgreSQL con pgvector |
| `DB_USERNAME`, `DB_PASSWORD` | (Secret `ec-cp-postgres`) | `workflow`, vacío | |
| `DB_POOL_SIZE` | `8` | `10` | |
| `KAFKA_BROKERS` | `redpanda:19092` | `localhost:9192` | `employee-directory`, `employee-directory-requests`, `content-events` |
| `KAFKA_ENABLED` | — | `true` | Apaga el consumidor del directorio (pruebas) |
| `SMTP_HOST`, `SMTP_PORT` | `postfix`, `25` | `localhost`, `25` | La newsletter |
| `NEWSLETTER_FROM` | — | `intranet@ec1.mateu.io` | Remitente |
| `NEWSLETTER_REDIRECT_TO` | `newsletter-demo@example.com` | vacío | **Todo** correo de newsletter va aquí, con el grupo y el idioma en el asunto. Vacío: a los empleados de verdad |
| `EMBEDDINGS_URL`, `EMBEDDINGS_MODEL` | `http://embeddings`, — | `http://embeddings`, `intfloat/multilingual-e5-small` | El RAG (`/v1/embeddings`) |
| `NOTIFICATIONS_CATCH_UP` | — | `PT24H` | Lo que se hizo efectivo hace más de esto ya no se notifica |
| `NOTIFICATIONS_REMINDER_BEFORE` | — | `P2D` | Cuánto antes de la fecha límite se recuerda confirmar |
| `NOTIFICATIONS_CLOSE_AFTER` | — | `P30D` | A partir de cuánto se cierra sola una notificación |
| `NOTIFICATIONS_BATCH_SIZE` | — | `200` | Lectores por transacción del reparto, y destinatarios por correo |
| `ANALYTICS_RETENTION` | — | `P90D` | Cuánto se guardan los eventos de uso en crudo (nunca menos de 30 días) |
| `PORTAL_URL` | `https://ec1.mateu.io/intranet` | `http://ec1.localhost:8800/intranet` | Los enlaces de la newsletter y el mensaje de «ver como» |
| `UI_SOURCES_IFRAME_ORIGINS` | — | vacío | Los únicos orígenes (`https://host[:puerto]`, separados por comas) a los que puede apuntar una UI externa de tipo iframe. Vacío: ninguna |
| `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWKS_URI` | `https://auth.ec1.mateu.io/realms/ec-demo1`, `http://keycloak:8080/…/certs` | iguales | Verificar el token de las llamadas MCP |
| `MEDIA_SECRET` | — | vacío | Firma las URL de los medios. Vacío: una clave aleatoria por arranque (las URL ya dibujadas dejan de valer al reiniciar) |
| `MEDIA_MAX_BYTES` | — | `20971520` (20 MB) | Tamaño máximo de un fichero subido |
| `PROJECTION_ASYNC` | — | `true` | Recalcular la visibilidad fuera del hilo de la petición (`false` en las pruebas) |
| `CONTENT_SEED` | — | `true` | La semilla de contenido de demo |
| `SEARCH_MAX_DISTANCE` | — | `0.3` | Distancia coseno máxima para que un pasaje cuente como coincidencia por significado |
| `INTRANET_AGENT_URL`, `INTRANET_AGENT_TIMEOUT` | `http://ia-agent:8095/a2a/intranet-agent`, — | igual, `PT30S` | El agente de la «Respuesta de la IA» |
| `INTRANET_TRANSLATOR_URL`, `INTRANET_TRANSLATOR_TIMEOUT` | `http://ia-agent:8095/a2a/intranet-translator`, — | igual, `PT60S` | El agente de «Traducir con IA» |
| `INTRANET_ALT_TEXT_URL`, `INTRANET_ALT_TEXT_TIMEOUT` | `http://ia-agent:8095/a2a/intranet-alt-text`, — | igual, `PT60S` | El agente de «Sugerir texto alternativo con IA» (su modelo tiene que ver imágenes) |
| `HEALTH_EXPIRY_DAYS`, `HEALTH_STALE_MONTHS`, `HEALTH_UNVIEWED_DAYS` | — | `14`, `12`, `90` | «Salud del contenido»: cuándo avisar de que algo caduca, está desactualizado o nadie lo abre |
| `HEALTH_STALE_SECTIONS` | — | vacío (todas) | Secciones (ids, separados por comas) donde importa que el contenido esté al día |
| `HEALTH_EXTERNAL_LINKS`, `HEALTH_MAX_EXTERNAL_PER_RUN` | — | `true`, `300` | Comprobar los enlaces externos, y cuántas URL como mucho por pasada (el resto, en la siguiente) |
| `HEALTH_NIGHTLY_CRON` | — | `0 15 3 * * *` | La pasada nocturna (hora de Madrid) |
| `GOOGLE_DRIVE_CREDENTIALS_FILE` | `/etc/google-drive/credentials.json` | vacío | El fichero de clave de la cuenta de servicio de Drive. Sin él, o si no se puede leer, Drive queda apagado |
| `GOOGLE_DRIVE_IMPERSONATE` | (Secret `ec-google-drive`, opcional) | vacío | Usuario de Workspace por el que actuar con delegación de dominio. Vacío: la cuenta ve lo compartido con su propia dirección |

Los trabajos programados se ajustan con `content-hub.jobs.crossings` (`PT30S`), `.newsletter` (`PT30S`),
`.embeddings` (`PT15S`), `.orphans` (`PT1H`) y `.drive` (`PT15M`).

### La IA: agentes del plano de control

content-hub **no tiene clave de ningún modelo**. La «Respuesta de la IA» y «Traducir con IA» llaman por A2A a
`ia-agent` (`POST /a2a/{agente}`, `message/send`) con el **token de la persona que pregunta**, nunca con uno de
servicio. Hacen falta dos agentes sin herramientas en el catálogo del plano de control, `intranet-agent` e
`intranet-translator`. Mientras no existan, o si fallan, el usuario ve un aviso breve; los resultados de la
búsqueda siguen funcionando y el formulario del editor no cambia. El motivo queda en el log, en `INFO`.

«Sugerir texto alternativo con IA» («Salud del contenido») usa un tercer agente, **`intranet-alt-text`**, con un
**modelo que vea imágenes** (el Claude por defecto lo hace): la imagen va en el mismo mensaje A2A como parte
`file` con sus bytes, y `ia-agent` se la pasa al modelo. Prompt de sistema sugerido: «Describes imágenes para
el atributo alt de una intranet de hoteles: una sola frase en español, de 125 caracteres como mucho, sin
comillas ni comentarios. Si no recibes ninguna imagen, responde exactamente SIN_IMAGEN.» Sin herramientas ni
RAG. Sin él, el botón da un aviso y nada cambia.

Los tres agentes viven solo en la base de datos del plano de control (tabla `agent`), no en el repositorio:
en un entorno nuevo hay que crearlos (en la consola del plano de control, «Agentes», o con un `insert`).

### Salud del contenido

Una pasada cada noche (`HEALTH_NIGHTLY_CRON`) y las que pidan los editores. La noche se **reclama en la base de
datos** (`ch_health_run.nightly_day`, único): un segundo disparo, un reinicio o otra réplica esa noche no hace
nada. Lo que guarda: `ch_health_run` (cada pasada y sus cuentas, o su error), `ch_health_finding` (los avisos
de la última, con cuándo se vio cada uno por primera vez), `ch_health_snooze` (los «revisado»),
`ch_health_link` (lo que respondió cada URL externa: lo bueno vale 7 días, lo malo 1) y `ch_media_alt` (los
textos alternativos). Nada se arregla ni se borra solo.

Los enlaces externos se piden con **HEAD** (GET si el servidor no entiende HEAD, cerrando el cuerpo sin leerlo),
5 s de espera, **un segundo entre dos peticiones al mismo host**, sin seguir redirecciones solas (se siguen a
mano, hasta 5) y con el User-Agent `ec-demo1-content-health/1.0 (…)`. **Nunca se pide una dirección interna**
(loopback, privadas, link-local, CGNAT, `fc00::/7`), ni directamente ni por una redirección: se marca «no se
comprueba». 401 y 403 cuentan como que existe; 429 se deja para otra pasada. Si el pod no tiene salida a
internet, las URL externas salen como rotas («no se puede conectar»): apágalo con `HEALTH_EXTERNAL_LINKS=false`.

Qué hosts son «el portal» (sus enlaces se comprueban como rutas internas): el de `PORTAL_URL` y los de
`content-hub.health.internal-hosts` (separados por comas).

### Google Drive

Sin configurar nada, un editor puede pegar la URL de un fichero: referencia, vista previa y enlaces funcionan
solo con ella. Lo que añade la cuenta de servicio es el nombre actual y «Modificado por X el …»:

1. Crea en Google Cloud una cuenta de servicio con la API de Drive activada y descarga su clave JSON. Se pide
   solo el alcance `drive.metadata.readonly`: puede leer los metadatos, no el contenido.
2. Crea el Secret, con la clave **`credentials.json`**, y reinicia el pod (los ficheros de un Secret opcional
   se montan al crearse el pod):

   ```bash
   kubectl -n ec-demo1 create secret generic ec-google-drive --from-file=credentials.json=clave.json
   kubectl -n ec-demo1 rollout restart deployment/content-hub
   ```

3. **Comparte cada fichero con la dirección de la cuenta de servicio** (`client_email` de la clave). Es el modo
   por defecto. La **delegación de dominio es opcional**: si la tienes autorizada en Workspace, añade al Secret
   `GOOGLE_DRIVE_IMPERSONATE` con el usuario por el que actuar, y la cuenta verá lo que vea ese usuario.

Un trabajo cada 15 minutos (y al minuto de arrancar) pregunta a Drive por cada fichero referenciado. **Un
fichero que la cuenta no puede ver no es un error para nadie**: referencia, vista previa y enlaces siguen
funcionando, solo falta el «Modificado por…», y el motivo queda en los detalles del medio y en el log, en
`INFO`. Un fallo posterior conserva lo último que se supo. La miniatura se guarda si Drive la da, pero la
respuesta suele traerla vacía y ninguna pantalla la usa.

## En local

En el clúster local (`deploy/local/up.sh`, ver [Entorno local](/desarrollo/entorno-local/)):

- **Portal**: `http://ec1.localhost:8800` → menú *Intranet* (`/intranet/inicio`); y solo el portal, en
  su propio host: `http://intranet.localhost:8800` (Vaadin) y `http://rw-intranet.localhost:8800`
  (Redwood).
- **Editorial**: el mismo host, menú *Editorial* (solo con permisos `content:*`: `editora`,
  `publicador`, o `demo` como administrador del realm).
- **Configuración**: `http://console.localhost:8800` → menú *Intranet* (`ai-admin` y `content:admin`:
  `demo`).

Redesplegar tras un cambio en `supporting/content-hub`:

```sh
deploy/local/load-images.sh 0.84.0 supporting/content-hub   # el tag de 90-content-hub.yaml
KUBECONFIG=$LOCAL_HOME/kubeconfig kubectl -n ec-demo1 rollout restart deploy/content-hub
```

Si el cambio toca el gateway, los shells o el servicio de usuarios, se cargan y reinician también
(`consoles/gateway`, `consoles/shell`, `consoles/control-shell`, `control-plane/users`). Con un manifiesto
nuevo o cambiado: `kubectl apply -f deploy/manifests/90-content-hub.yaml`.

## Datos de demo

Dos semillas, idempotentes:

- **Servicio de usuarios** (`DemoDirectorySeeder`, `USERS_DEMO_SEED=true`): el catálogo de valores de
  atributo, los permisos `content:edit|publish|admin` y los roles `content-editor`, `content-publisher` y
  `content-admin`, y unos **30 empleados**:

  | Hotel | Región | Quiénes |
  | :---- | :----- | :------ |
  | `TFS01` Tenerife, `LPA01` Gran Canaria | Canarias | Recepción, pisos, cocina, dirección, mantenimiento, comercial (p. ej. `ana.perez`, `carlos.diaz`, `sofia.mueller`) |
  | `PMI01` Palma | Baleares | `marta.ferrer`, `klaus.schmidt`, `antonia.vidal`… |
  | `MRU01` Mauricio | Mauricio | `priya.ramgoolam`, `raj.patel`… (en inglés) |
  | `CUN01` Cancún | Quintana Roo | `alejandra.lopez`, `emily.johnson`… |
  | Servicios centrales (sin hotel) | Baleares | `isabel.torres`, `miguel.navarro` |

  Y el equipo editorial: **`demo`** (administrador de contenido, dirección en servicios centrales),
  **`editora`** (Eva Morales, editora) y **`publicador`** (Pablo Ortega, publicador). Crea lo que falta en
  cada arranque y no toca lo que se haya editado.
- **content-hub** (`ContentSeeder`, `CONTENT_SEED=true`, solo con la tabla de items vacía): secciones
  (*La empresa*, *Personas*, *Operaciones*, *Dirección*…), audiencias con nombre («Recepción Canarias»,
  «Dirección de hotel», «Hoteles de España», «Equipos de cocina»), un vídeo externo, items con audiencias
  distintas —uno para todos con el vídeo, el cuadrante de turnos de Canarias, la carta de otoño para
  cocinas, la política de datos con confirmación obligatoria, uno solo para directores, uno programado y
  uno caducado—, una página con bloques (uno solo para recepción), el slot
  `front-office.bienvenida.destacado` y la newsletter «Boletín de la intranet» (un sitio con su página
  «Quiénes somos» y un primer número en borrador). En cada arranque, si faltan: las UIs
  externas **Riu Class** (`/_loyalty`) y **Notificaciones en este navegador** (`<ec-push-toggle>`), la
  pantalla externa «Riu Class · socios» y la página «Herramientas» con dos componentes remotos.

Para enseñar las diferencias: *Ver como* con `ana.perez` (recepción, Tenerife), `kevin.lee` (cocina,
Mauricio) y `raj.patel` (director, Mauricio).

:::caution[Los usuarios sembrados no pueden entrar hasta que se les fija contraseña]
Se crean sin contraseña y sin el correo de *fijar contraseña* (sus emails son de `example.com`). Para
entrar como uno: en Keycloak (`auth.localhost:8800`, realm `ec-demo1`) → *Users* → el usuario →
*Credentials* → *Set password* (no temporal). Además, **el perfil de usuario de Keycloak exige
apellido** (*Last name*): sin él, el login se queda en «Update account information». Rellénalo en la ficha
del usuario en Keycloak antes de entrar.
:::

## Registro del MCP

El servidor MCP de content-hub (SSE en `http://content-hub:8135/sse`) está en el catálogo de IA como
**`intranet`** (`gitops/example/ia/mcp/intranet.yaml`), y `console-agent` lo incluye en su lista `mcp`
(`gitops/example/ia/agents/console-agent.yaml`). Para dárselo a otro agente (por ejemplo
`reception-agent`) se añade `intranet` a su lista `mcp` en el catálogo, o desde el plano de control de IA. Ver [GitOps del catálogo](/ia/gitops/).

`ia-agent` reenvía el token de quien chatea; content-hub lo verifica contra Keycloak. Sin token, la
llamada se rechaza.

## Problemas conocidos y límites

- **Salud del contenido**: entre resolver el nombre de un host y pedirle la URL se resuelve otra vez (lo hace el
  cliente HTTP): un DNS que cambie de respuesta entre las dos podría llevar una petición a una dirección
  interna. Los avisos se recalculan enteros en cada pasada, en memoria: a la escala de la demo es poco; con
  decenas de miles de contenidos habría que trocearla.
- **El saneador de Mateu quita los `<iframe>`.** Los vídeos externos (YouTube, Vimeo, Drive) y los PDF se
  dibujan como marcadores con un enlace, y `embed.js` los convierte en reproductor. Si `embed.js` no carga
  (un shell sin el `@Script`, o la ruta pública quitada del gateway), se ven como enlace o miniatura. Solo
  admite `www.youtube-nocookie.com`, `player.vimeo.com`, `drive.google.com` y los medios propios.
- **`embed.js` vigila los shadow roots** con un `MutationObserver` y un sondeo cada 1,5 s: un reproductor
  puede tardar un instante en aparecer.
- **El MCP solo existe dentro del clúster** y pide el token del usuario: no es para clientes externos ni
  para llamadas de servicio a servicio sin usuario.
- **Vista previa de Drive**: la dibuja Google en un iframe, con la sesión de Google del navegador del lector.
  Sin acceso al fichero, el iframe muestra el aviso de Google; los enlaces siguen ahí. No se usa la cuenta de
  servicio para esto.
- **El umbral de `SEARCH_MAX_DISTANCE`** depende del modelo de embeddings y del contenido: demasiado alto trae
  resultados que no vienen a cuento; demasiado bajo no encuentra nada por significado. Ajústalo con contenido real.
- **La búsqueda no pagina**: mezclar dos clasificaciones da los 20 mejores, sin más páginas.
- **Por A2A, `ia-agent` no aplica los guardarraíles ni rechaza por presupuesto antes de gastar**: lee la
  configuración del agente sin saber quién llama (`/internal/agents/{id}/config`) y los guardarraíles solo corren
  en el chat (`IaAgentController`). Sí registra el consumo a nombre de la persona. Ver
  [Arquitectura](./arquitectura/#la-ia-por-el-plano-de-control).
- **Apellido en Keycloak**: ver el aviso de [Datos de demo](#datos-de-demo).
- **Slots por REST provisional.** `GET /_intranet/api/slots/{key}` sustituye al microfrontend resuelto en
  servidor y `@ContentSlot` de Mateu, que aún no se han publicado; los descriptores de las aplicaciones
  tampoco se cargan todavía (el slot de demo lo registra la semilla). Cuando Mateu lo publique, se sube
  `mateu.version` y el remoto pasa a ser un componente.
- **UIs externas y hosts.** Una UI de Mateu registrada se pide por su prefijo en el gateway desde el host donde
  se lee la intranet: en la consola de datos lo están los de sus menús; en `intranet.*`, solo `/_portal` y
  `/_inbox`, así que allí una pantalla de Riu Class no carga hasta que el gateway enrute `/_loyalty` en ese
  host. Dentro de una pantalla externa, un enlace de la UI remota navega a su ruta propia, que resuelve la
  consola de datos (su `RemoteMenu`) y no el host de la intranet.
- **UIs externas: permisos al dibujar.** Los permisos que exige una fuente se miran al pintar, no en la
  proyección: quien no los tiene ve la pantalla externa en los listados y, al abrirla, «No puedes usar esta
  aplicación». Un web component `.mjs` cargado con token no resuelve imports relativos; un iframe no lleva el
  token.
- **"Ver como" vive en memoria** (una hora por editor), igual que la clave aleatoria de las URL de medios
  si no se fija `MEDIA_SECRET`: un reinicio los pierde.
- **Una sola réplica.** Por lo anterior y por la caché local por hash, el manifiesto usa una réplica y
  `Recreate`. Escalar exigiría una caché compartida (o invalidación por `content-events`), "ver como"
  fuera del proceso y `MEDIA_SECRET` fijo.
- **Consistencia eventual**: entre publicar (o redefinir una audiencia) y verlo pasan unos segundos; el
  formulario del item dice cuándo está aplicado. El chat tarda unos segundos más (los embeddings).
- **Medios en la base de datos**, hasta 20 MB cada uno: lo grande va por URL externa.
- **Notificaciones**: `select * from ch_notification_job where done_at is null` dice qué reparto está pendiente y
  con cuántos intentos; `outcome` dice cómo acabó cada uno. Dependen de `communication-service`: se acumulan en el
  outbox si Kafka no está, y nada se pierde. Hay una fila abierta en su `inbox_item` por lector y notificación, y
  su bandeja carga todas las abiertas en memoria en cada refresco de la campana: a la escala de la demo no se nota,
  a miles de empleados habría que filtrar en la consulta.
- **El reset de la demo no toca** las notificaciones ni la analítica de la intranet: las bandejas las vacía
  `communication-service`, pero lo enviado y las cifras se quedan.
- **El reset de la demo no la restaura todavía**: la semilla de contenido solo corre con la tabla de
  items vacía, y `supporting/demo-reset` no incluye content-hub.
- **Los atributos no están en el token**: un empleado que aún no está en el directorio (evento en camino)
  solo ve lo que es para todos hasta que llega.

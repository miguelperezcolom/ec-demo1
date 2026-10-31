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
- Sin ruta para el MCP ni para `/api/**`: solo `/_intranet/**` (hosts de datos) y `/_content-hub/**`
  (hosts de control) en el gateway.
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
| `PORTAL_URL` | `https://ec1.mateu.io/intranet` | `http://console.localhost:8800/intranet` | Los enlaces de la newsletter y el mensaje de «ver como» |
| `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWKS_URI` | `https://auth.ec1.mateu.io/realms/ec-demo1`, `http://keycloak:8080/…/certs` | iguales | Verificar el token de las llamadas MCP |
| `MEDIA_SECRET` | — | vacío | Firma las URL de los medios. Vacío: una clave aleatoria por arranque (las URL ya dibujadas dejan de valer al reiniciar) |
| `MEDIA_MAX_BYTES` | — | `20971520` (20 MB) | Tamaño máximo de un fichero subido |
| `PROJECTION_ASYNC` | — | `true` | Recalcular la visibilidad fuera del hilo de la petición (`false` en las pruebas) |
| `CONTENT_SEED` | — | `true` | La semilla de contenido de demo |

Los trabajos programados se ajustan con `content-hub.jobs.crossings` (`PT30S`), `.newsletter` (`PT30S`),
`.embeddings` (`PT15S`) y `.orphans` (`PT1H`).

## En local

En el clúster local (`deploy/local/up.sh`, ver [Entorno local](/desarrollo/entorno-local/)):

- **Portal**: `http://ec1.localhost:8800` → menú *Intranet* (`/intranet/inicio`).
- **Backoffice**: `http://console.localhost:8800` → menú *Intranet* (requiere `ai-admin`).

Redesplegar tras un cambio en `supporting/content-hub`:

```sh
deploy/local/load-images.sh 0.76.0 supporting/content-hub   # el tag de 90-content-hub.yaml
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
  `front-office.bienvenida.destacado` y una edición de newsletter.

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

- **El saneador de Mateu quita los `<iframe>`.** Los vídeos externos (YouTube, Vimeo, Drive) y los PDF se
  dibujan como marcadores con un enlace, y `embed.js` los convierte en reproductor. Si `embed.js` no carga
  (un shell sin el `@Script`, o la ruta pública quitada del gateway), se ven como enlace o miniatura. Solo
  admite `www.youtube-nocookie.com`, `player.vimeo.com`, `drive.google.com` y los medios propios.
- **`embed.js` vigila los shadow roots** con un `MutationObserver` y un sondeo cada 1,5 s: un reproductor
  puede tardar un instante en aparecer.
- **El MCP solo existe dentro del clúster** y pide el token del usuario: no es para clientes externos ni
  para llamadas de servicio a servicio sin usuario.
- **Apellido en Keycloak**: ver el aviso de [Datos de demo](#datos-de-demo).
- **Slots por REST provisional.** `GET /_intranet/api/slots/{key}` sustituye al microfrontend resuelto en
  servidor y `@ContentSlot` de Mateu, que aún no se han publicado; los descriptores de las aplicaciones
  tampoco se cargan todavía (el slot de demo lo registra la semilla). Cuando Mateu lo publique, se sube
  `mateu.version` y el remoto pasa a ser un componente.
- **"Ver como" vive en memoria** (una hora por editor), igual que la clave aleatoria de las URL de medios
  si no se fija `MEDIA_SECRET`: un reinicio los pierde.
- **Una sola réplica.** Por lo anterior y por la caché local por hash, el manifiesto usa una réplica y
  `Recreate`. Escalar exigiría una caché compartida (o invalidación por `content-events`), "ver como"
  fuera del proceso y `MEDIA_SECRET` fijo.
- **Consistencia eventual**: entre publicar (o redefinir una audiencia) y verlo pasan unos segundos; el
  formulario del item dice cuándo está aplicado. El chat tarda unos segundos más (los embeddings).
- **Medios en la base de datos**, hasta 20 MB cada uno: lo grande va por URL externa.
- **El reset de la demo no la restaura todavía**: la semilla de contenido solo corre con la tabla de
  items vacía, y `supporting/demo-reset` no incluye content-hub.
- **Los atributos no están en el token**: un empleado que aún no está en el directorio (evento en camino)
  solo ve lo que es para todos hasta que llega.

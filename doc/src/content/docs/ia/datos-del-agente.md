---
title: Cómo lee los datos el agente
description: Las dos formas que tiene el agente de responder preguntas sobre los datos sin leerlos registro a registro — herramientas de búsqueda y SQL de solo lectura sobre vistas — y cómo se mide cuál funciona mejor.
---

Una pregunta como *«¿qué reservas hay para habitaciones dobles, de clientes españoles, que entren hoy?»*
no se puede responder bien con herramientas que solo listan y leen de una en una. Con `listArrivals` y
`getGuest`, o con `listBookings` y `getBooking`, el modelo tenía que hacer **una llamada por reserva**
para conocer sus detalles y filtrar. Eso son muchas idas y vueltas, muchos tokens y mucha latencia.

El agente tiene ahora dos formas de responder en **una sola llamada**:

| | Búsqueda | SQL de solo lectura |
| :-- | :-- | :-- |
| Herramientas | `searchStays` (front office), `searchBookings` (CRS) | `describe…Data` y `query…Data` de cada servicio |
| Para qué | Listas con filtros: lo habitual en recepción y reservas | Lo que ninguna herramienta responde: cuántos, sumas, agrupaciones, cruces |
| Fiabilidad | Alta: parámetros tipados sobre el modelo de dominio | Depende de que el modelo escriba bien la SQL |
| Coste de añadir un criterio | Tocar código | Ninguno, si la vista ya tiene la columna |

El contexto de sistema de cada servidor MCP le dice al modelo cuándo usar cada una. Las dos conviven, y
[las métricas](#cuál-funciona-mejor) dicen cuál se usa y cuánto cuesta cada una.

## Herramientas de búsqueda

### `searchStays` (front office)

Busca entre todas las estancias del hotel. Todos los filtros son opcionales y se aplican a la vez:

| Parámetro | |
| :-- | :-- |
| `statuses` | `ARRIVING`, `IN_HOUSE`, `DEPARTED`, `CANCELLED`, `NO_SHOW` |
| `arrivalFrom` / `arrivalTo` | Fecha de entrada, inclusive |
| `departureFrom` / `departureTo` | Fecha de salida, inclusive |
| `occupyingOn` | Una noche en la que la estancia está en el hotel |
| `roomType`, `board`, `agency` | Parte del texto, sin distinguir mayúsculas (`doble`, `TI`…) |
| `nationality` | ISO-2. Casa con el titular o con **cualquier pax**, salvo `holderOnly=true` |
| `text` | Parte del id o localizador, del nombre del huésped o del número de habitación |
| `limit` | 50 por defecto, 200 como máximo |

La pregunta del ejemplo es `statuses=[ARRIVING], arrivalFrom=arrivalTo=hoy, roomType=doble,
nationality=ES`. El filtrado lo hace **una consulta** sobre el read model de estancias
(`StayReadModel.search`). La nacionalidad de un pax es la que escaneó el mostrador o, si no, la del
maestro de clientes. Cada resultado es el mismo `StaySummary` que el de `listArrivals`, que ahora
también lleva la nacionalidad del titular.

### `searchBookings` (CRS)

| Parámetro | |
| :-- | :-- |
| `hotelCode`, `statuses` (`Pending`, `Confirmed`, `Cancelled`) | |
| `arrivalFrom` / `arrivalTo`, `departureFrom` / `departureTo` | Inclusive |
| `channel`, `partnerCode` | Código exacto, sin distinguir mayúsculas |
| `roomType`, `board`, `ratePlan` | Parte del **código o del nombre** del catálogo: `doble` encuentra `DBL-GARDEN` |
| `nationality`, `holderOnly` | Titular o cualquier huésped nombrado en las habitaciones |
| `text`, `limit` | Como en `searchStays` |

Las habitaciones, los huéspedes y los pagos de una reserva son JSON en `crs_booking`. Por eso la base de
datos filtra lo que son columnas (hotel, estado, fechas, texto) y el resto se filtra en memoria, sobre
como mucho 2000 reservas ordenadas por llegada. Cada resultado (`BookingFound`) trae sus habitaciones
con el código y el nombre del tipo, la tarifa, el régimen, la ocupación y las nacionalidades de sus
huéspedes.

## SQL de solo lectura (agent-sql)

Para las preguntas de cola larga (*«¿cuántas estancias de alemanes por tipo de habitación este mes?»*),
cada servicio publica **vistas** en un schema `agent` de su base de datos. El agente las lee con dos
herramientas que llevan el nombre del servicio: en un mismo agente no puede haber dos herramientas que se
llamen igual, y así las métricas las distinguen.

| Servicio | Herramientas | Vistas |
| :-- | :-- | :-- |
| front-office | `describeFrontOfficeData`, `queryFrontOfficeData` | `stays`, `stay_pax`, `guests`, `rooms`, `incidents`, `folio_lines`, `payments` |
| booking (CRS) | `describeCrsData`, `queryCrsData` | `bookings`, `booking_rooms`, `booking_guests`, `booking_payments` |

`describe…` devuelve las vistas con sus columnas y lo que significa cada una, sacado de los propios
comentarios de la base de datos (`COMMENT ON VIEW/COLUMN`). `query…` ejecuta una SELECT y devuelve las
columnas y hasta 200 filas. Si había más, lo indica (`truncated`) y le pide al modelo que agregue en SQL.

### Tres barreras

1. **El login `agent_reader`.** Es la barrera que importa. Las consultas se ejecutan con un login de
   PostgreSQL que no es superusuario y solo tiene `SELECT` sobre las vistas del schema `agent`. Una
   vista se ejecuta con los permisos de su dueño (el servicio), así que **la vista decide qué columnas y
   filas ve el agente**, y fuera de ella no hay nada: ni tablas, ni ficheros (`pg_read_file`), ni
   cambiar de rol (`set_config('role', …)`), ni escribir. Lo comprueba `AgentSqlPostgresTest` sobre un
   PostgreSQL real, saltándose el guard.
2. **Una transacción de solo lectura** con `statement_timeout` (5 s) y un máximo de filas.
3. **El guard** (`SqlGuard`, con JSqlParser): una sola sentencia, que sea una SELECT (`WITH` y `UNION`
   también lo son), que solo lea vistas del schema `agent` y que no llame a funciones que salen de los
   datos (`pg_*`, `set_config`, `dblink`, `lo_*`…). Sus errores están escritos para el modelo: le dicen
   qué vistas hay, o que solo se admite SELECT.

Las vistas no exponen **documentos, emails, teléfonos, fechas de nacimiento ni enlaces de pago**. Tienen
lo que necesita una pregunta sobre el hotel, no lo que identifica o permite contactar a una persona.

### Las piezas

- **`supporting/agent-sql`** es una librería autoconfigurada, como `messaging`. Aporta `AgentSql`
  (describe y query), `SqlGuard` y `AgentViews`. Se configura con `agent-sql.*`:

  | Propiedad | Por defecto | |
  | :-- | :-- | :-- |
  | `agent-sql.enabled` | `true` | Sin ella, no hay vistas ni herramientas SQL |
  | `agent-sql.schema` | `agent` | |
  | `agent-sql.views` | `classpath:agent-sql/views.sql` | Si existe `views-<plataforma>.sql` (`views-postgresql.sql`), tiene prioridad |
  | `agent-sql.username` / `password` | vacío | El login lector (`AGENT_DB_USERNAME` / `AGENT_DB_PASSWORD`) |
  | `agent-sql.url` | la del servicio | |
  | `agent-sql.timeout`, `agent-sql.max-rows` | `5s`, `200` | |

- **`AgentViews`** recrea el schema y las vistas **en cada arranque**, cuando ya existen las tablas del
  servicio (después del `ddl-auto` de Hibernate y de `spring.sql.init`). Después concede al login lector
  `USAGE` sobre el schema y `SELECT` sobre sus vistas. Si el script falla, el servicio sigue funcionando:
  el error queda en el log y las herramientas SQL no encuentran nada que leer.
- **Las vistas de cada servicio**: `systems/front-office/src/main/resources/agent-sql/views.sql`, válida
  en H2 y en PostgreSQL, y `systems/crs/booking/src/main/resources/agent-sql/views-postgresql.sql`, que
  despliega el JSON con `jsonb_array_elements` y por eso es solo para PostgreSQL. En los tests del CRS
  sobre H2 no hay vistas.

### En el despliegue

- `deploy.sh` genera `AGENT_DB_PASSWORD` en `deploy/.secrets/credentials.env` y crea el Secret
  `ec-agent-reader` (`AGENT_DB_USERNAME=agent_reader`, `AGENT_DB_PASSWORD`).
- El Job `demo-db-init` crea el rol `agent_reader` (`LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE`) o le
  actualiza la contraseña. Cambiar la contraseña solo requiere volver a ejecutar el Job y reiniciar los
  servicios.
- `front-office` y `booking` leen el Secret como `AGENT_DB_USERNAME` y `AGENT_DB_PASSWORD`.
- Si el rol todavía no existe cuando arranca un servicio, se registra un aviso y las consultas del agente
  se rechazan hasta que el servicio se reinicie con el rol ya creado.

:::caution[Sin login lector]
Si `agent-sql.username` está vacío, las consultas se ejecutan con el usuario del propio servicio, y entre
el modelo y todas las tablas solo queda el guard. Eso vale para los tests en H2, nunca para una base de
datos real. Sobre PostgreSQL el servicio lo avisa en el log al arrancar.
:::

### Publicar una vista nueva

1. Añádela al script de vistas del servicio, con `drop view if exists agent.<vista>;` seguido de
   `create view …`, en el lenguaje del negocio y sin datos personales que no hagan falta.
2. Documenta la vista y sus columnas con `comment on view` y `comment on column`: es lo que lee el
   modelo. Pon en el comentario los valores posibles de las columnas de estado.
3. Al reiniciar el servicio, la vista se crea y el login lector puede leerla. Las herramientas no cambian.

Para dar SQL a **otro servicio**: añade la dependencia `io.mateu.ecdemo1:agent-sql`, su script de
vistas, una clase de herramientas con nombres propios (`describe<Servicio>Data` y
`query<Servicio>Data`) registrada en su `ToolCallbackProvider`, y las variables `AGENT_DB_*` en su
manifiesto.

## Cuál funciona mejor

Cada prompt queda clasificado por **cómo llegó a los datos**, según las herramientas que llamó
(`ia.data.path` en `AgentObservability`):

| `ia_data_path` | El prompt llamó a… |
| :-- | :-- |
| `search` | alguna herramienta `search…` |
| `sql` | alguna `query…Data` o `describe…Data` |
| `search+sql` | de las dos |
| `other` | solo a otras herramientas (por ejemplo, un `getStay` por estancia) |
| `none` | a ninguna |

La etiqueta está en `ia_agent_prompt_seconds`, que da el número de prompts, la latencia y el
`ia_outcome`. Hay además dos métricas nuevas por prompt, con `gen_ai_agent_id` e `ia_data_path`:

- `ia_agent_prompt_tool_calls`: cuántas llamadas a herramientas hizo el prompt.
- `ia_agent_prompt_tokens`: cuántos tokens consumió (entrada más salida).

En Grafana, el dashboard **IA agents** tiene una fila *Data path* con una tabla por camino: prompts,
media de llamadas a herramientas, media de tokens, latencia media y porcentaje de prompts fallidos en el
rango elegido. Un camino es mejor si resuelve la pregunta con menos llamadas, menos tokens y menos
fallos. Para saber cuántas veces se usa cada herramienta, está la tabla *Tool calls in selected range, by
agent and tool* (`spring_ai_tool_seconds_count` por `spring_ai_tool_definition_name`).

Algunas consultas útiles:

```text
# Llamadas a herramientas por prompt, por camino, en la última semana
sum by (ia_data_path) (increase(ia_agent_prompt_tool_calls_sum[7d]))
  / sum by (ia_data_path) (increase(ia_agent_prompt_tool_calls_count[7d]))

# Uso de búsqueda frente a SQL, herramienta a herramienta
sum by (spring_ai_tool_definition_name) (increase(spring_ai_tool_seconds_count{
  spring_ai_tool_definition_name=~"search.*|query.*Data|describe.*Data"}[7d]))
```

Para ver **qué preguntas** acabaron en cada camino, la traza de cada prompt (`invoke_agent <agente>`)
guarda `ia.tools.called`, `ia.tool.calls`, los tokens y, según `IA_CAPTURE_CONTENT`, el mensaje del
usuario. En Tempo: `{ span.ia.tools.called =~ ".*query.*Data.*" }`.

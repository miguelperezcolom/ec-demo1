---
title: Cómo extenderlo
description: Recetas para los cambios habituales —un servicio nuevo, un topic, una tarea del motor, una pantalla, una herramienta para los agentes, otro hotel u otro PMS, un tipo de aviso—, con los ficheros que tocar y cómo probarlo.
---

Cada receta es la lista de lo que tocó un cambio real que ya hizo eso mismo, y el commit sirve de
modelo: `git show --stat <commit>` enseña el cambio entero. Lo que el build vigila (esquemas, tareas
servidas) falla solo si se olvida; lo demás —una ruta del gateway, una base de datos de la demo, un menú
en el e2e— no, y es lo que esta página intenta que no se olvide.

Cómo está organizado el código y por qué: [Estructura del proyecto](/diseno/estructura/) y
[Patrones](/diseno/patrones/).

## 1. Un servicio nuevo

Modelos: **`notices`** (plano de datos, [#145](https://github.com/miguelperezcolom/ec-demo1/pull/145),
`5897732`) y **`registration-rules`** (plano de control,
[#164](https://github.com/miguelperezcolom/ec-demo1/pull/164), `0a4ad3a`). Los dos son el mismo
esqueleto: Spring Boot 3.4, una base de datos propia, pantallas Mateu en su `@UI`, REST y MCP solo
dentro del clúster, y lo que publica por el outbox.

1. **El módulo.** Carpeta en el grupo que le toca (`systems/`, `integration/`, `control-plane/`,
   `ai/`, `supporting/`), copiando el `pom.xml` de `systems/notices` (dependencias de `messaging`,
   `ui-commons`, los `contracts-*` que habla y `contracts-testing` en test) y su `.gitignore`.
   `mateu.version` la de todos los módulos (ver [Compilar](/desarrollo/compilar/)).
2. **El `pom.xml` raíz**: el `<module>` en su grupo.
3. **`Dockerfile`**: el de `systems/notices`, con el nombre del jar y el puerto. El puerto, el siguiente
   libre en [Servicios](/referencia/servicios/) (hoy los últimos son 8131 y 8132).
4. **`application.yaml`**: `server.port`, `spring.application.name: ec-demo1-<servicio>`, el
   datasource (`DB_URL` → `postgres:5432/<base_de_datos>`), `ddl-auto: update`, las trazas
   (`OTLP_TRACING_ENDPOINT`, `TRACING_SAMPLING`) y `management.endpoints` con `health,prometheus`. Si
   tiene MCP, `spring.ai.mcp.server.name` y `spring.mvc.async.request-timeout: -1` (receta 5).
5. **`deploy/build-images.sh`**: la carpeta en `APPS`. La imagen sale como
   `miguelperezcolom/ec-demo1-<carpeta>`.
6. **El manifiesto**: `deploy/manifests/<NN>-<servicio>.yaml`, copiando `83-notices.yaml` (Deployment
   con `wait-for-postgres`, `nodeSelector` amd64, Service). Y su `kubectl apply` en `deploy/deploy.sh`,
   en el paso de los servicios.
7. **La base de datos**: el nombre en el bucle de `deploy/manifests/55-demo-db-init.yaml`. Es un Job:
   `deploy.sh` lo borra y lo vuelve a aplicar; a mano, `kubectl delete job demo-db-init -n ec-demo1`
   antes del `apply`.
8. **El gateway**, si tiene pantallas:
   - una ruta en `consoles/gateway/src/main/resources/application.yaml` con `Host=` los dos hosts de
     su plano (`CONSOLE_HOST`/`RW_CONSOLE_HOST` o `CONTROL_HOST`/`RW_CONTROL_HOST`) y
     `Path=/_<servicio>/**`, y la URL interna como variable (`NOTICES_URL`…);
   - la regla en `SecurityConfig.java`: `.authenticated()` en el plano de datos, o la ruta en la
     lista de `hasRole("ai-admin")` en el de control. Ver
     [Consolas, gateway y seguridad](/guias/consolas-y-seguridad/).

   Solo las pantallas: el REST y el MCP **no** se enrutan.
9. **El menú**: un `RemoteMenu("/_<servicio>")` en `consoles/shell/.../ShellHome.java` (datos) o
   `consoles/control-shell/.../ControlShellHome.java` (control). La misma clase da la consola Vaadin y la
   Redwood. Ver la receta 4.
10. **La demo**: la base de datos en `DATABASES` y el despliegue en `SERVICES` de
    `deploy/demo/common.sh`, para que `snapshot.sh` y `reset.sh` lo cubran; y, si tiene datos que una
    puesta a cero debe vaciar, `deploy/demo/zero.sh`. `notices` y `registration-rules` se olvidaron
    aquí y lo arregló después `37b0f9b` (#181): nada falla, el reset simplemente no los devuelve a la
    línea base.
11. **Si está en el camino CRS → Opera**, también en `e2e/poc-acl-local` (`infra.sh`, su base de
    datos; `apps.sh`, arrancarlo).
12. **La documentación**: su fila en [Servicios](/referencia/servicios/) y su bloque en
    [Configuración](/referencia/configuracion/), sus topics en [Topics de Kafka](/referencia/topics/), y
    su página si la merece (con su entrada en `doc/astro.config.mjs`). El `README.md` raíz tiene su
    lista de módulos.
13. **Desplegarlo**: imagen con tag propio, manifiestos de gateway y consolas con tag nuevo, `kubectl
    apply`, y un commit `deploy: …` (como `ff4334e`, que desplegó `registration-rules` 0.1.0). Ver
    [Despliegue](/operacion/despliegue/).

**Cómo probarlo.** Sus tests (`mvn -B -ntp -pl <módulo> -am test`): los de aplicación, como
`NoticesTest` con un repositorio en memoria (`InMemory`), y su `*ContractsTest` (receta 2). Después de
desplegar, la pantalla en `https://ec1.mateu.io/_<servicio>/…` y `npm test` en `e2e/`, con su menú y sus
pantallas añadidos a `e2e/tests/consoles.ts` (los de `Avisos` y `Registro` aún no están).

## 2. Un mensaje o un topic del lenguaje publicado

Modelos: el topic `registration-rules` (#164) y `notices` (#145); un mensaje nuevo en un topic
existente, `ChargePosted`/`RecordCharge` en `5cd0e2c`
([#135](https://github.com/miguelperezcolom/ec-demo1/pull/135)). El porqué de cada regla está en
[Librerías y esquemas](/contratos/librerias-y-esquemas/).

1. **El record**, en la librería del contexto que lo habla (`contracts/contracts-<contexto>`). Un
   contexto nuevo es un módulo nuevo: su `pom.xml`, su `<module>` y su línea en el comentario de
   `contracts/pom.xml`, y su fila en `contracts/README.md`. En un topic polimórfico, la variante nueva
   va en el `sealed interface` y en su `@JsonSubTypes`, con su nombre (el valor del discriminador).
2. **El `TopicSpec`** en
   `contracts/contracts-schemas/src/test/java/.../PublishedLanguageTest.java`: descripción, dueño
   (`ownedBy`), clave (`keyedBy`), productores y consumidores, los `messages` y **un ejemplo por
   variante** tal como lo escribe el productor. Si la librería es nueva, también su dependencia (test)
   en `contracts/contracts-schemas/pom.xml`.
3. **El esquema**: `mvn test -Dcontracts.write=true` en `contracts/` escribe
   `contracts/schemas/<topic>/v1.schema.json`; se sube con el cambio. Un cambio incompatible es
   `TopicSpec.version(2)`, no una edición de `v1`.
4. **El productor** escribe en el outbox de `messaging`, en la transacción del cambio:
   `outbox.append(<binding>, <clave>, <tipo>, <json>, …)` (ver `RuleOutbox`). El binding se lleva al
   topic en `application.yaml`:

   ```yaml
   spring.cloud.stream:
     kafka.bindings.ruleEvents.producer:
       sync: true    # el relay marca publicado solo cuando el broker lo confirmó
       configuration.key.serializer: org.apache.kafka.common.serialization.StringSerializer
     bindings.ruleEvents.destination: registration-rules
   ```

5. **El consumidor**: un bean funcional (`Consumer<Message<byte[]>>`, como `CustomerNoticesConsumer`)
   en `spring.cloud.function.definition`, con su binding `<bean>-in-0` (`destination` y `group`), y
   lectura tolerante. El front office, que es Boot 4, usa sus `KafkaListeners` en vez de Spring Cloud
   Stream (`RegistrationRuleEvents`).
6. **Un productor o consumidor más de un topic existente** también se declara en su `TopicSpec`
   (`producedBy`/`consumedBy`): `37b0f9b` añadió el front office como productor de `notifications`,
   que se había quedado fuera.
7. **Los dos lados del contrato**, en tests del servicio:
   - el productor valida lo que escribe su outbox, con el mapper de la aplicación:
     `Contracts.topic("<topic>").assertValid(json)`;
   - el consumidor pasa cada `Contracts.topic("<topic>").examples()` por su bean real.

   `NoticesContractsTest` hace las dos cosas.
8. **La documentación**: [Topics de Kafka](/referencia/topics/) y la tabla de
   [Librerías y esquemas](/contratos/librerias-y-esquemas/).

**Cómo probarlo.** `mvn -B -ntp -pl contracts/contracts-schemas -am test` (el esquema del repositorio
coincide con el generado) y `mvn -pl <productor>,<consumidor> -am test`. **Siempre `-am`**: con `-pl`
solo, el servicio compila contra la librería que haya en `~/.m2` y un verde no demuestra nada. En el
clúster, el mensaje se ve en la consola de Redpanda (`kafka.ec1.mateu.io`).

:::caution[Un valor de enum nuevo]
Añadir un valor a un enum es aditivo para el esquema, pero si ese enum se guarda con
`@Enumerated(STRING)` falla solo en ec1, por la restricción que Hibernate congeló al crear la tabla. Ver
[Problemas conocidos](/operacion/problemas-conocidos/) y la receta 7.
:::

## 3. Una tarea del motor, o un paso en un proceso

Modelo: los cargos de recepción al folio de Opera — `post-charge` y `reverse-charge` en
`pms-integration-service` (`5cd0e2c`, #135) y, en ec-definitions, sus contratos (PR #35) y los procesos
`registrar-cargo`/`anular-cargo` (PR #36). Cómo encaja cada pieza: [Workers y tareas](/contratos/workers-y-tareas/).

1. **El contrato**, en [ec-definitions](https://github.com/miguelperezcolom/ec-definitions):
   `definitions/tasks/<id>.ectask` con `id`, `version`, `group`, `topic` (el del worker), `input` y
   `output` tipados y descritos. Las salidas son las variables que verá el proceso (`chargeOutcome`:
   `DONE`, `STALE` o `WAIT`).
2. **El paso**, en `definitions/workflows/<proceso>.ec`: un `ACTION` con `task: <id>`, `topic:`,
   `timeout` y `retries`. Lo que el motor tiene de particular —descripciones de menos de 255 caracteres,
   sin ciclos, un `JOIN` detrás de un `CHOICE`— está en [Los procesos del motor](/guias/procesos/).
3. **Los nombres compartidos**, en `contracts/contracts-process`: el id del proceso en
   `Definitions.java` y sus variables en `ProcessVariables.java`, para que el servicio que lo arranca y
   el worker no repitan cadenas.
4. **El handler**, en el worker: un `@Bean TaskRegistration<Entrada, Salida>` en su clase de tareas
   (`worker/PmsTasks.java` en `pms-integration-service`), con records de entrada y salida y la lógica
   aparte (`ChargeHandlers`). **Idempotente**: la tarea se puede reentregar (el cargo lleva
   `FO:<línea>` como referencia y se busca antes de escribir). Lo que espera a una persona abre una
   causa y devuelve `WAIT` (ver [Causas, avisos y bandeja](/integracion/causas-y-avisos/)).
5. **`contracts/workers/<servicio>.tasks`**: lo regenera el `ServedTasksTest` del worker con
   `mvn test -Dcontracts.write=true`. Un worker nuevo necesita su `ServedTasksTest` (cuatro líneas,
   copiando el de `pms-integration-service`) y el binding `consumeWorkerEvent-in-0` con su topic.
6. **Quién lo arranca**, si el proceso es nuevo: en `registrar-cargo`, `integrations-service`
   (`ReceptionEvents`) arranca uno por cada `ChargePosted` del front office.
7. **El orden de los PR**: primero ec-demo1 (el worker desplegado, sirviendo la tarea), después
   ec-definitions. `master` de ec-definitions está protegida y el motor reimporta al hacer merge: un
   paso cuya tarea no sirve nadie deja el proceso esperando hasta el timeout.
8. **La documentación**: [Tareas](/referencia/tareas/) y el proceso en [Los procesos del motor](/guias/procesos/).

**Cómo probarlo.** Los tests del handler (`ChargeHandlersTest`) y `ServedTasksTest`. Antes del merge
en ec-definitions, `deploy/demo/check-contracts.sh --ref <rama>` (cada `ACTION` con contrato, topic y
un worker que lo sirve); después del despliegue, `check-contracts.sh --cluster`, que es lo que ejecuta
`demo-prep.sh health`. De punta a punta: `e2e/poc-acl-local` con `EC_DEFINITIONS_BRANCH=<rama>` (ver
[Entorno local](/desarrollo/entorno-local/)), y en ec1 el proceso en *Admin → Workflow → Processes*.

## 4. Una pantalla o una acción con Mateu

Modelos: los atajos del front office ([#165](https://github.com/miguelperezcolom/ec-demo1/pull/165),
`1227f52`, un fichero) y «Descartar» un proceso que espera
([#174](https://github.com/miguelperezcolom/ec-demo1/pull/174), `a6fc987`, con su arreglo de Redwood en
[#176](https://github.com/miguelperezcolom/ec-demo1/pull/176)). Lo que hay que saber de Mateu está en
[Mateu](/desarrollo/mateu/); aquí, dónde va cada cosa.

1. **El view model**, en el servicio dueño de los datos, nunca en la consola:
   `infra/in/ui/pages/` (o `ui/pages/`). Un CRUD como `RuleCrud` + `RuleViewModel` + `RuleRow` +
   `RuleFilters`; una página suelta como `EvaluateRules`.
2. **El menú**:
   - una entrada en una sección existente: un campo `@Menu` en la clase de menú del servicio
     (`RulesMenu`, `NoticesMenu`). La consola no cambia;
   - una sección nueva: el `@UI("/_<servicio>")` del servicio con un único `@Menu` de arriba, y su
     `RemoteMenu` en la consola con la misma etiqueta (receta 1, paso 9);
   - en el front office, un `RouteLink` con `@Audience("Staff")` en `FrontOfficeSuite` (así son los
     atajos de #165: `/reservas?vista=LLEGADAS_HOY`).
3. **Una acción**: `@Action` dice cómo se comporta, `@Toolbar` (o `@Button`) pone el botón, y si
   cambia el formulario devuelve `List.of(new Message(…), new State(this))`. Un diálogo es un
   componente propio (`new EmbeddedView(form)`), como `DiscardForm` en #174. `VisibilitySupplier` la
   oculta cuando no aplica.
4. **La lógica, fuera de la pantalla**: el botón llama a la aplicación (`Causes.discard`), que audita y
   publica. Lo mismo se expone por REST (y por MCP si debe, receta 5) sin duplicarla.
5. **Vaadin y Redwood**: el mismo view model lo pintan dos renderers y no siempre igual. En #176 una
   acción de fila dentro del grid de un formulario se despachaba al listado del que se abrió la página
   (`CausesPage`), no al view model; y Redwood pinta ese `ColumnActionGroup` como «[object Object]»,
   así que en Redwood la fila no lleva acción y se descarta desde la barra. Comprobar siempre en las dos
   consolas (`ec1` y `rw.ec1`, o `console` y `rw-console`).
6. **El e2e**: una pantalla nueva en `e2e/tests/consoles.ts` (`{ menu, entry, route }`), y un menú
   nuevo en la lista `menus` de las consolas de su plano.

**Cómo probarlo.** Un formulario que pinta mal no siempre da error: se mira en un navegador. Sin
consola, Playwright contra la ruta propia del servicio (`/_<servicio>/<menú>/<entrada>`) en local;
desplegado, `npm test` en `e2e/` recorre cada pantalla en las cuatro consolas. La lógica de la acción,
con tests de la aplicación (`CausesDiscardTest`). Ver [Pruebas](/desarrollo/pruebas/).

## 5. Exponer algo a los agentes

Modelo: el servidor MCP de `notices` (#145) y su entrada en el catálogo
([ec-ia-config #10](https://github.com/miguelperezcolom/ec-ia-config/pull/10)); el de
`registration-rules` (#164 y ec-ia-config #11). Cómo resuelve el agente sus herramientas:
[El agente](/ia/agente/).

1. **Las herramientas**, en el servicio dueño (`infra/in/mcp/<X>McpTools.java`): métodos `@Tool` con
   una descripción que diga qué hace y qué valores admite, `@ToolParam(required = false)` para lo
   opcional, y que llamen a la misma aplicación que la pantalla (así se audita igual).
   - **Nunca devolver `Object`**: Spring AI no ofrece esa herramienta y solo lo dice en el log
     («functional type»). Una lista de vistas (`List<NoticeView>`) o un `String`.
   - Implementar `McpSystemContext` (`getSystemContext()`): el contexto de dominio que el agente
     recoge de cada servidor.
2. **`McpToolsConfig`**: el `ToolCallbackProvider` y el prompt `system-context` (copiar el de
   `registration-rules`). Dependencia `spring-ai-starter-mcp-server-webmvc`;
   `spring.ai.mcp.server.name` y `spring.mvc.async.request-timeout: -1` (SSE).
3. **Sin ruta en el gateway**: el MCP se alcanza solo desde dentro del namespace, en
   `http://<servicio>:<puerto>`.
4. **El catálogo**, por GitOps en [ec-ia-config](https://github.com/miguelperezcolom/ec-ia-config)
   (un PR, el webhook reconcilia al hacer merge; ver [GitOps del catálogo](/ia/gitops/)):
   - `ia/mcp/<id>.yaml`: `kind: mcp`, `id`, `name`, `url`, `transport: SSE` y una `description` que el
     operador entienda;
   - el `id` en la lista `mcp:` del agente que debe recibirlo: `console-agent` (consola de datos),
     `control-plane-agent` (consola de control), `reception-agent` (front office) o `mapping-agent`;
   - si debe contestar otro agente según la pantalla, una regla en `ia/routes/`.

   Y el mismo cambio en el ejemplo de este repositorio, `gitops/example/ia/`, que documenta la forma.
5. **Lo que no se expone, a propósito**: decidirlo y decirlo. Descartar un proceso no tiene herramienta
   (#174: el agente de mapeado no descarta); `registration-rules` deja que el agente proponga una regla
   **inactiva** y la activa una persona. Ver [A2A y guardarraíles](/ia/a2a-y-guardarrailes/).

**Cómo probarlo.** Tests de las herramientas como métodos (`FrontDeskMcpToolsTest`) y, para el
transporte, uno sobre HTTP como `McpServerOverHttpTest` del front office. En la consola de control,
*IA → Agents → Preview resolved configuration* enseña el servidor en el agente (o el aviso de por qué
no). Después, preguntar en el chat de la consola y mirar la traza en Tempo: `execute_tool <herramienta>`
bajo `invoke_agent`.

## 6. Otro hotel, u otro PMS

### Otro hotel con Opera: casi todo es configuración

| Qué | Dónde | Código |
| :-- | :---- | :----- |
| El hotel del CRS y su catálogo | `booking` (y para la demo, `deploy/demo/crs-catalog/`) | No |
| La propiedad de Opera ofrecida | `OPERA_PROPERTIES` de `pms-integration-service` (listar las de la cadena da 403) | No |
| La conexión con Opera, el mapeado, los interlocutores, el backfill | La integración del hotel en `integrations-service`, por su alta (ver [Alta de un hotel](/integracion/alta-de-un-hotel/)) | No |
| Opera → front office | Una `FrontOfficeIntegration` (`alta-integracion-fo`, ver [De Opera al front office](/integracion/pms-a-front-office/)) | No |
| Quién se entera de los avisos del hotel | Un destinatario con `hotelCode` en *Notifications → Recipients* | No |
| El país del hotel para las reglas de registro | `HOTEL_COUNTRIES` de `registration-rules` y `FRONT_OFFICE_HOTEL_COUNTRY` de su front office | No |
| Códigos de transacción de los cargos y cajero | `opera.charges` y `OPERA_CASHIER_ID`: **uno para todo el conector**, no por propiedad | Sí, si otra propiedad usa otros |

**El front office es uno por hotel**: `FRONT_OFFICE_HOTEL`, `FRONT_OFFICE_PMS_HOTEL` y
`FRONT_OFFICE_CURRENCY` en `77-front-office.yaml`, con su base de datos `front_office`. Un segundo hotel
con front office propio es otro despliegue con otro nombre, otra base de datos, otro host (ingress,
ruta `front-office` del gateway con `FRONT_OFFICE_HOST`, redirect URI en el cliente `demo` de
Keycloak) y su `frontOfficeUrl` en la integración. Antes hay que cambiar una cosa en el código: los
grupos de consumidor del front office son fijos (`ec-demo1-front-office-commands`…), y dos front
offices en el mismo grupo se repartirían las particiones; cada uno descarta lo que no es de su
propiedad (`PmsStays`), así que perderían la mitad. El grupo tiene que llevar el hotel.

**Cómo probarlo.** En local, `scenario.py` da de alta PMI01 puerta a puerta contra `opera-mock`. En ec1,
el alta en la consola de control: cada puerta que se para deja un aviso en la bandeja.

### Otro PMS: la frontera es `pms-integration-service`

No hay un «puerto de PMS» dentro del conector: **el adaptador es el servicio entero**. Lo que no sabe de
Opera, y no cambia:

- los procesos de ec-definitions y los contratos `.ectask` del topic `pms-integration`
  (`upsert-reservation`, `ensure-guest-profile`, `check-in-reservation`, `post-charge`…): hablan de
  reservas, perfiles y cargos, con resultados `DONE`/`STALE`/`WAIT`;
- la reserva canónica (`contracts-reservation`) y lo que el front office recibe
  (`front-office-commands`, con códigos del PMS, traducidos por el mapeado de cada integración);
- las causas: un rechazo es `PMS_REJECTED`, un fallo transitorio se reintenta.

Lo que sí es de Opera: el paquete `ohip/` (`OhipClient`, `OperaReservations`, `OperaProfiles`,
`OperaFrontDesk`, `OperaCatalog`), que los handlers de `worker/` usan directamente; `OhipConnection`
en `contracts-integration`, que es la conexión que guarda cada integración; y las lecturas síncronas del
alta (`/connections/verify`, `/catalog`), que `integrations-service` hace a este servicio.

Un segundo PMS es, por tanto:

1. Un tipo de conexión en `contracts-integration` y en la integración, que diga qué PMS es.
2. En `pms-integration-service`, un puerto por operación (reservas, perfiles, recepción, catálogo) con
   la implementación de Opera detrás, y la elegida por la integración de la propiedad
   (`Connections.of(pmsHotelCode)`). Los handlers y los contratos de las tareas se quedan como están.
3. Un doble del PMS nuevo como `systems/pms/opera-mock`, para `e2e/poc-acl-local`: contra un tenant
   real no se pueden provocar los fallos.

Un worker aparte en otro topic también serviría, pero cada `ACTION` nombra su topic: obligaría a
duplicar los procesos o a elegir el topic con un `CHOICE`.

**Cómo probarlo.** Los tests de los handlers (`ChargeHandlersTest`, `ReceptionHandlersTest`) contra el
puerto, y `scenario.py` entero contra el doble nuevo.

## 7. Un tipo de aviso nuevo, o una alerta

Modelos: `CHECK_IN_INCOMPLETE`, un aviso que pide un servicio (`a87d0f8`,
[#143](https://github.com/miguelperezcolom/ec-demo1/pull/143)), y `PLATFORM_ALERT` /
`PLATFORM_ALERT_CRITICAL`, las alertas de Prometheus
([#163](https://github.com/miguelperezcolom/ec-demo1/pull/163), `ff8c6e5`). Cómo se reparten: [Causas,
avisos y bandeja](/integracion/causas-y-avisos/) y [Observabilidad](/operacion/observabilidad/).

### Un tipo de aviso

1. **El valor** en `NotificationType` (`contracts-communication`), con su javadoc.
2. **El esquema** de `notifications`: `mvn test -Dcontracts.write=true` en `contracts/` (un valor
   nuevo es aditivo, misma `v1`). Si el productor es nuevo, en `producedBy` de su `TopicSpec`.
3. **El productor**: un `NotificationRequested` al outbox, binding al topic `notifications`, con un
   `dedupKey` que haga de un episodio un solo aviso y un `link` a donde se actúa
   (`IncompleteCheckIns` en el front office). Cuando deja de aplicar, un `NotificationResolved` en
   `notification-resolutions` lo cierra.
4. **`communication-service`**, que no enruta un tipo que no conoce:
   - una casilla en `ui/pages/RecipientViewModel.java` (el campo y sus dos líneas, guardar y leer);
   - el valor en la descripción de `addRecipient` de `mcp/CommunicationMcpTools.java`.
5. **La base de datos**: `notification.type` es `@Enumerated(STRING)`. `StaleEnumChecks` quita las
   restricciones congeladas al arrancar en `communication-service` y en el mapeado; cualquier otro
   servicio que guarde el enum necesita lo mismo (ver [Problemas conocidos](/operacion/problemas-conocidos/)).
6. **La documentación**: la tabla de avisos de [Causas, avisos y bandeja](/integracion/causas-y-avisos/).

### Una alerta de la plataforma

1. **La regla** en `deploy/observability/prometheus-rules.yaml`, con
   `labels: { severity: warning|critical, notify: ec-demo1 }` y las anotaciones `summary`,
   `description` y `link`. Sin `notify: ec-demo1`, Alertmanager no la manda a nadie.
2. **Su test** en `deploy/observability/prometheus-rules.test.yaml`.
3. Aplicar con `deploy.sh` (paso de observabilidad) o `kubectl apply -f` de las reglas.

Una alerta no necesita tipo nuevo: llega por `POST /alerts/alertmanager` y es un `PLATFORM_ALERT`, o
`PLATFORM_ALERT_CRITICAL` si su `severity` es `critical`.

**Cómo probarlo.** Del aviso: el test del productor (`ForcedCheckInTest`), el de contrato del topic, y
`PlatformAlertsTest` o `CommunicationContractsTest` del lado de `communication-service`; en ec1, un
destinatario con el tipo marcado y el aviso en la bandeja (`/_inbox`). De la alerta:
`deploy/observability/test-rules.sh` (promtool, con Docker), que ya reproduce la caída de Opera del 29
de septiembre; y en ec1, `deploy/demo/opera-outage.sh on` corta Opera al conector de verdad, y unos
minutos de errores sin un acierto son `OperaNotAnswering` (ver [La demo](/operacion/demo/)).

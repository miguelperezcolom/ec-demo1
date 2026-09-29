---
title: Problemas conocidos
description: Lo que parece un fallo de la aplicación y no lo es — síntomas, causa y qué hacer, recogidos al construir y operar ec1.
---

Cada uno de estos cuesta una tarde la primera vez porque el síntoma no apunta a la causa.

## Base de datos

**Un valor nuevo de un enum falla solo en ec1** — `violates check constraint`.
Hibernate 6 crea `<tabla>_<columna>_check` con los valores de un `@Enumerated(STRING)` al crear la tabla,
y `ddl-auto: update` nunca la reescribe. En local funciona (la base de datos se crea en cada ejecución) y
en ec1 falla. Pasó con `cause_type_check` en el mapeado, que no tenía `INTEGRATION_INACTIVE`: cada reserva
de un hotel sin integración fallaba `prepare` en bucle. Al añadir un valor a un enum persistido, mirar
las restricciones de la base de datos desplegada
(`select conname, pg_get_constraintdef(oid) from pg_constraint where contype='c'`) y quitar o
rehacer la vieja en el mismo cambio (el mapeado lo hace al arrancar, `StaleEnumChecks`). Un campo nuevo
de tipo enum puede guardarse como texto para evitarlo.

**Un campo primitivo nuevo rompe la tabla** — `add column ... not null` sobre una tabla con filas.
PostgreSQL lo rechaza, `ddl-auto: update` lo registra y sigue, y cada consulta de la entidad falla
después. Darle un valor por defecto (`@Column(columnDefinition = "integer not null default 0")`) o usar
el tipo envoltorio.

**Un renombre deja la columna vieja** — `ddl-auto: update` nunca borra una columna: renombrar un campo
JPA añade la nueva y deja la vieja `NOT NULL`, y cada insert falla por una columna que ningún código
nombra. Hay que borrarla a mano.

## Kafka

**Un registro envenenado para una partición entera.** `rpk topic produce` comprime con snappy por defecto
y las imágenes de los servicios (Alpine, musl) no pueden descomprimirlo: el consumidor vuelve una y otra
vez sobre el registro. Producir siempre con `--compression none`. Para desbloquear: escalar el servicio
a 0, `rpk group seek <grupo> --to end --topics <topic>` y volver a escalarlo.

**`BrokerNotAvailable` a partir del segundo test** — el disco está casi lleno y Redpanda rechaza
escrituras por debajo de su umbral de espacio libre (lo dice su log: «no disk space»). En los tests,
Testcontainers monta sus datos en tmpfs; para uno arrancado a mano, igual (`--tmpfs /var/lib/redpanda/data`).

## Compilación

**`mvn -pl <módulo> test` en verde no prueba nada** en un cambio que cruza módulos: resuelve los módulos
hermanos desde `~/.m2`, que tiene lo último que se instaló. Usar `-am` (compila los que necesita desde el
código) o el build completo.

**`package` sin `clean` deja librerías viejas dentro del jar.** Un jar de Boot reempaquetado sin `clean`
conserva en `BOOT-INF/lib` la versión de los contratos que copió la vez anterior, aunque en `~/.m2` haya
una nueva: compila y pasa los tests (que usan la de `~/.m2`) y falla en ejecución con un valor nuevo
(«Unreadable notification, skipped»). Compilar las aplicaciones con `clean package`.

**Testcontainers no habla con Docker 29.** La 1.20 que gestiona Spring Boot 3.4 no conoce la versión de
API que queda en Docker 29: fijar `<testcontainers.version>1.21.4</testcontainers.version>` en el pom del
módulo.

## Mateu

Comportamientos que pintan mal sin ningún error:

- una `@Action` en un CRUD no se pinta sin `@Toolbar`, y si devuelve un `String` sustituye el
  formulario por ese texto: devolver `List.of(new Message(...), new State(this))`;
- un `@Lookup` con `@Stereotype(checkbox)` no pinta opciones: las casillas leen `options` del view model,
  que debe implementar `OptionsSupplier`;
- un formulario en un `Dialog` con `ModelViewComponent` no recibe su estado ni sus acciones: usar
  `new EmbeddedView(form)`, con `@Getter` en los campos de estado;
- un `Status` o un `@Notice` a `null` pintan el texto de la plantilla o la palabra «null»;
- un `@Section` en un campo oculto en ese modo pierde su encabezado;
- un `Listing` que no es `Navigable` no carga sin un `OnLoadTrigger("search")`;
- las validaciones de Jakarta se emiten para todos los campos, también los ocultos: `@NotEmpty` en un
  campo `@HiddenInCreate` hace que el formulario de alta no se pueda enviar.

Comprobar cada formulario nuevo en un navegador. Ver [Mateu](/desarrollo/mateu/).

## Spring y Spring AI

**Una herramienta MCP que no aparece**: Spring AI descarta en silencio un `@Tool` que devuelve `Object`
(solo un WARN al arrancar: «returns a functional type»). Devolver un tipo concreto; buscar «functional
type» en el log al arrancar un servicio MCP.

**El front office no lee una respuesta HTTP**: es Spring Boot 4 con Jackson 3, y `RestClient` no puede
leer en un `com.fasterxml…JsonNode` («Type definition error» en ejecución). Leer en `Map` o en un record.

## El clúster

**Todos los pods en `Pending`**: un `nodeSelector` con un tipo de instancia del que la región no tiene
stock. Fijar solo región y arquitectura. Ver [El clúster](/operacion/cluster/).

**El disco de la máquina de build se llena**: `buildx --push` guarda cada imagen también en local. Ver
[Despliegue](/operacion/despliegue/).

## El motor

- **Un paso sin `topic` espera para siempre**: nada escucha en `downstream`, el destino por defecto. Todos
  los pasos nombran su topic, y un `USER_TASK` llega al motor de formularios solo con `topic: forms`.
- **Una descripción de definición de más de 255 caracteres** hace que el import la salte con solo un WARN.
- **No hay ciclos** en una definición, y un paso sucesor de un `CHOICE` que el `CHOICE` no elige se
  descarta: cada punto de espera lleva su propia cadena (ver [Los procesos del motor](/guias/procesos/)).

## Salesforce y Opera

- **El cupo de Salesforce se agota** sin que ec1 haga nada: un MDM local con las mismas credenciales.
  Ver [Consumo de Salesforce y Opera](/operacion/consumo-apis-externas/).
- **Salesforce Base Edition**: no admite Apex, los Flows llegan como borrador, un Flow *before delete*
  no ve el `MasterRecordId` de una fusión, y no admite más objetos personalizados. Ver
  [Clientes](/integracion/clientes/).
- **OHIP**: un email o teléfono de perfil enviado sin su id se añade junto al viejo y no se puede borrar
  por API (actualizar en su sitio, leyendo el perfil con `fetchInstructions=Communication`); buscar un
  perfil por referencia externa que no existe devuelve 204, no 404. Ver
  [Del CRS a Opera](/integracion/crs-a-opera/).

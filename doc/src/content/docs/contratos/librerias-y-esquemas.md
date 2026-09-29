---
title: Librerías y esquemas
description: El lenguaje publicado que comparten los servicios — una librería por contexto — y el esquema versionado de cada topic de Kafka, con cómo lo vigila el build.
---

`contracts/` es lo que los servicios se comprometen a respetar entre sí: el **lenguaje publicado**
(librerías pequeñas, una por contexto), el **esquema de cada topic** de Kafka y las **tareas** que
sirve cada worker (ver [Workers y tareas](/contratos/workers-y-tareas/)). El detalle vive en
[`contracts/README.md`](https://github.com/miguelperezcolom/ec-demo1/blob/master/contracts/README.md).

## Una librería por contexto

`integration-model` era un único núcleo compartido que cada servicio tomaba entero. Se partió por
contexto y cada servicio depende solo de los que habla. Los paquetes
(`io.mateu.ecdemo1.integration.model.*`) y el JSON no cambiaron.

| Módulo | Qué tiene | Topics |
| :----- | :-------- | :----- |
| `contracts-reservation` | La reserva canónica (`Reservation`, `Room`, `Person`…), una proyección pedida, un no-show, una reserva cambiada en el PMS | `projection-requests`, `no-show-reports`, `pms-reservations` |
| `contracts-customer` | `CustomerEvent`, `GoldenRecord`, la resolución de identidad, `CustomerCommand` | `customers`, `customer-commands` |
| `contracts-partner` | El interlocutor y su perfil en el PMS | — (HTTP) |
| `contracts-mapping` | Tipos de código, traducciones, causas, `MappingCommand` | `mapping-commands` |
| `contracts-integration` | El ciclo de vida de una integración, la conexión, las reservas futuras y sus códigos; `ApiUsage` | — (HTTP) |
| `contracts-frontoffice` | `FrontOfficeCommand` (estancias, catálogos) | `front-office-commands` |
| `contracts-communication` | Avisos pedidos y resueltos | `notifications`, `notification-resolutions` |
| `contracts-audit` | `AuditedAction` | `audit` |
| `contracts-process` | El vocabulario de los procesos compartido con ec-definitions: ids de definición, mensajes de espera, nombres de variables, resultados | — |

Las órdenes van con el contexto que las recibe, no en un módulo de «comandos»: un servicio que manda
órdenes al mapeado ya habla su lenguaje. `IntegrationEvent` (topic `integration-events`) es de
`crs-integration-service`, que es su único productor y consumidor, y vive allí.

Se instalan antes que cualquier servicio: `mvn install` en `contracts/` (lo hace `build-images.sh`).

## El esquema de cada topic

`schemas/<topic>/v<N>.schema.json`: JSON Schema 2020-12, un fichero por topic y versión, **generado**
de los records que son los mensajes del topic. Cada uno dice quién es su dueño (`x-owner`), cuál es la
clave del registro (`x-key`), productores y consumidores, el discriminador de un topic polimórfico
(`x-discriminator`, un `oneOf` de las variantes) y `examples`: un mensaje por variante, tal como lo
escribe un productor real. La tabla completa está en [Topics de Kafka](/referencia/topics/).

### Cómo lo vigila el build

- **Generado, nunca a mano.** El build del dueño genera el esquema (`contracts-testing`: `TopicSpec` →
  `SchemaFiles.publish`) y **falla** si el fichero del repositorio es distinto. `mvn test
  -Dcontracts.write=true` (o `CONTRACTS_WRITE=true`) lo escribe; se sube con el cambio. Cada componente
  del record es obligatorio y, salvo los primitivos, admite null; un esquema cerrado rechaza una
  propiedad que no nombra.
- **Productores**: validan lo que escribe de verdad su camino de serialización —el outbox o el
  `StreamBridge`, con el mapper de la aplicación— contra el esquema del topic
  (`Contracts.topic("…").assertValid(json)`).
- **Consumidores**: pasan cada ejemplo del esquema por su bean consumidor real y comprueban que llega
  al handler bien leído, no descartado como ilegible.

### Versiones

Un cambio que un consumidor del esquema viejo podría no leer —quitar una propiedad, una variante o un
valor de enum, cambiar un tipo, hacer obligatoria una propiedad— es **incompatible**: el build lo dice y
se publica como versión nueva (`TopicSpec.version(2)` → `v2.schema.json`), mientras `v1` sigue mientras
algo la lea. Una propiedad, variante o definición nueva y opcional es aditiva y se queda en la misma
versión: todos los consumidores de aquí leen de forma tolerante (ignoran lo que no conocen).

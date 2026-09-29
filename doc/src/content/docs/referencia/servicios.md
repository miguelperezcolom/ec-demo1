---
title: Servicios
description: Cada despliegue de ec1 — módulo, imagen, puerto, ruta del gateway y base de datos.
---

Las imágenes propias son `miguelperezcolom/ec-demo1-<carpeta>`; el tag de cada una está en su manifiesto
de `deploy/manifests/` (los de esta tabla son los del momento de escribirla). Las bases de datos
`postgres:5432/<nombre>` están en el PostgreSQL del motor.

## Consolas

| Despliegue | Módulo | Puerto | Host / ruta |
| :--------- | :----- | -----: | :---------- |
| `gateway` | `consoles/gateway` | 8190 | Todos los hosts de consola y `front.ec1` |
| `shell` | `consoles/shell` | 8101 | `ec1.mateu.io/**` |
| `shell-redwood` | `consoles/shell` (`-Predwood`) | 8102 | `rw.ec1.mateu.io/**` |
| `control-shell` | `consoles/control-shell` | 8111 | `console.ec1.mateu.io/**` |
| `control-shell-redwood` | `consoles/control-shell` (`-Predwood`) | 8112 | `rw-console.ec1.mateu.io/**` |

## Sistemas

| Despliegue | Módulo | Puerto | Ruta | Base de datos |
| :--------- | :----- | -----: | :--- | :------------ |
| `booking` | `systems/crs/booking` | 8108 | `/_booking` | `booking` |
| `erp` | `systems/erp` | 8120 | `/_erp` | `partners` |
| `front-office` | `systems/front-office` | 8128 | `front.ec1.mateu.io` | `front_office` |

## Integración (plano de datos)

| Despliegue | Módulo | Puerto | Ruta | Base de datos |
| :--------- | :----- | -----: | :--- | :------------ |
| `crs-integration-service` | `integration/crs-integration-service` | 8121 | — | `crs_integration` |
| `pms-integration-service` | `integration/pms-integration-service` | 8123 | — | — (sin estado propio) |
| `mapping-service` | `integration/mapping-service` | 8122 | `/_mapping` (control) | `mapping` |
| `customer-mdm-service` | `integration/customer-mdm-service` | 8127 | `/_customers` (datos), `/_mdm` (control) | `customer_mdm` |
| `journey-service` | `integration/journey-service` | 8130 | `/_journey` | — (lee Tempo) |

## Plano de control

| Despliegue | Módulo | Puerto | Ruta | Base de datos |
| :--------- | :----- | -----: | :--- | :------------ |
| `integrations-service` | `control-plane/integrations-service` | 8126 | `/_integrations`, `/_api-usage` | `integrations` |
| `communication-service` | `control-plane/communication-service` | 8125 | `/_communication`, `/_inbox` | `communication` |
| `audit-service` | `control-plane/audit-service` | 8129 | `/_audit` | `audit` |
| `users` | `control-plane/users` | 8102, gRPC 9191 | `/_users` | `users` |

## IA

| Despliegue | Módulo | Puerto | Ruta | Base de datos |
| :--------- | :----- | -----: | :--- | :------------ |
| `ia-agent` | `ai/ia-agent` | 8095 | `/ai` (consolas) | — |
| `ia-agent-front-office` | `ai/ia-agent` (misma imagen) | 8095 | `/ai` (`front.ec1`) | — |
| `ia-control-plane` | `ai/ia-control-plane` | 8110 | `/_ia-cp`, `/cp-webhooks` | `cp-postgres:5432/controlplane` |
| `api-mcp` | `ai/api-mcp` | 8113 | — (solo dentro del clúster) | — |

## Soporte y terceros

| Despliegue | Qué es | Puerto |
| :--------- | :----- | -----: |
| `content` | `supporting/content`, un CRUD (`/_content`, base de datos `content`) | 8104 |
| `orchestrator`, `forms` | EventConductor (chart vendorizado): `/_workflow`, `/_forms` y sus `-admin` | 8105, 8106 |
| `postgres`, `redpanda` | El PostgreSQL y el Kafka del motor (y de todos) | 5432, 19092 |
| `cp-postgres` | `pgvector/pgvector:pg16`, la base de datos del plano de control de IA | 5432 |
| `keycloak` | `quay.io/keycloak/keycloak:26.0`, en `auth.ec1.mateu.io` | 8080 |
| `postfix` | `boky/postfix`, el relay de correo a Gmail | 25 |
| `embeddings` | Text Embeddings Inference con `intfloat/multilingual-e5-small` | 3000 |
| `kafka-console` | Redpanda Console, en `kafka.ec1.mateu.io`, con basic auth | 8080 |
| `docs` | Esta documentación, en `doc.ec1.mateu.io`, con basic auth | 80 |

`opera-mock` (`systems/pms/opera-mock`) no se despliega: solo existe para el
[entorno local](/desarrollo/entorno-local/).

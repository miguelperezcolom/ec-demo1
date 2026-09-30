---
title: Configuración
description: Las variables de entorno de cada servicio en ec1 y los Secrets de los que salen, tal como están en los manifiestos.
---

Todo sale de `deploy/manifests/*.yaml`. Los valores entre paréntesis vienen de un Secret o ConfigMap que
crea `deploy.sh` desde `deploy/.secrets/credentials.env` (git lo ignora): **ningún secreto está en el
repositorio**.

## Lo común

| Variable | Valor en ec1 | Para qué |
| :------- | :----------- | :------- |
| `SERVER_PORT` | El puerto de cada servicio | Ver [Servicios](/referencia/servicios/) |
| `DB_URL` | `jdbc:postgresql://postgres:5432/<base>` | Su base de datos |
| `DB_USERNAME`, `DB_PASSWORD` | (Secret `ec-postgres`) | |
| `DB_POOL_SIZE` | 4–8 | |
| `KAFKA_BROKERS` | `redpanda:19092` | |
| `OTEL_SERVICE_NAME` | El nombre del servicio | Cómo aparece en Tempo |
| `OTLP_TRACING_ENDPOINT` | `http://tempo.observability.svc.cluster.local:4318/v1/traces` | |
| `TRACING_SAMPLING` | `1.0` | Todas las trazas |
| `<SERVICIO>_URL` | `http://<servicio>:<puerto>` | A quién llama por HTTP |

## Por servicio

### gateway

`CONSOLE_HOST=ec1.mateu.io`, `CONTROL_HOST=console.ec1.mateu.io` (y `RW_CONSOLE_HOST`,
`RW_CONTROL_HOST`, `FRONT_OFFICE_HOST`, con sus valores por defecto en `application.yaml`), y la URL de
cada backend que enruta.

### pms-integration-service

| Variable | Valor | Para qué |
| :------- | :---- | :------- |
| `RETRY_ALERT_AFTER` | `10m` | Cuánto fallando antes del aviso `RETRYING_TOO_LONG` |
| `OPERA_VERSION_UDF` | `UDFN01` | El UDF donde se guarda la versión del CRS |
| `OPERA_PAY_AT_HOTEL_METHOD` | `CASH` | La forma de pago en hotel del tenant |
| `OPERA_PROFILE_REFERENCES` | `false` | Referencias externas en perfiles (el tenant no tiene la interfaz) |
| `OPERA_CRM_EXTERNAL_SYSTEM` | vacío | |
| `OPERA_POST_DEPOSITS` | `false` | Depósitos al folio (el tenant no tiene cajero para la integración) |
| `OPERA_PROPERTIES` | `XMAR,XMU` | Las propiedades que se ofrecen (listar las de la cadena da 403) |
| `OPERA_EXTERNAL_SYSTEM`, `OPERA_CUSTOM_REFERENCE` | (ConfigMap `ec-demo-run`, opcional) | El contexto de la ejecución de la demo |

Cómo se llega a cada propiedad no está aquí: es de la integración de cada hotel, en `integrations-service`.

### integrations-service

| Variable | Valor | Para qué |
| :------- | :---- | :------- |
| `OPERA_GATEWAY_URL`, `OPERA_APP_KEY`, `OPERA_CLIENT_ID`, `OPERA_CLIENT_SECRET`, `OPERA_ENTERPRISE_ID` | (Secret `ec-opera`) | La conexión de la cadena, con la que se rellena una integración nueva |
| `INTEGRATIONS_CRYPTO_KEY` | (Secret `ec-integrations-crypto`) | Cifra el secreto de conexión de cada integración. **No regenerar** |
| `INTEGRATIONS_MAPPING_ASK_AGENT` | `false` | |
| `FO_HORIZON_DAYS` | `60` | La ventana de la integración pms-fo |
| `FO_POLL` | `60s` | El sondeo de cambios en Opera |
| `USAGE_SALESFORCE_URLS` | `http://customer-mdm-service:8127` | De dónde lee el consumo de Salesforce |
| `CONSOLE_URL` | `https://console.ec1.mateu.io` | Los enlaces de los avisos |

### customer-mdm-service

| Variable | Valor | Para qué |
| :------- | :---- | :------- |
| `SF_DOMAIN`, `SF_CLIENT_ID`, `SF_CLIENT_SECRET` | (Secret `ec-salesforce`, opcional) | La org de Salesforce. Sin ellas resuelve identidades y no limpia |
| `FRONT_OFFICE_PUBLIC_URL` | `https://front.ec1.mateu.io` | Los enlaces al front office |

En `application.yaml`: `CONSOLIDATION_POLL`, `CHANGE_POLL`, `NOTICE_POLL` (`24h`: las redes de seguridad
bajo la Pub/Sub API — fusiones, decisiones de Cases, avisos sin confirmar), `PROJECTION_TICK` (`5s`),
`MARKING_TICK` (`2m`, las marcas de calidad que cambiaron), `CLEANUP_AFTER` (`30d`), `CLEANUP_CRON`
(`0 30 3 * * *`) y `CLEANUP_ENABLED` (`true`) — la anonimización de los «Solo nombre» —, y
`USAGE_LIMITS_STALE` (`2h`, cada cuánto se pregunta a `/limits`). `SF_PUBSUB_HOST`/`SF_PUBSUB_PORT`
(`api.pubsub.salesforce.com:7443`) y `SF_SUBSCRIBE` (`true`).

### communication-service

| Variable | Valor | Para qué |
| :------- | :---- | :------- |
| `SMTP_HOST`, `SMTP_PORT` | `postfix`, `25` | El relay de correo |
| `MAIL_FROM` | `integration@ec1.mateu.io` | |
| `DEFAULT_RECIPIENT` | `integration-admins@example.com` | El destinatario de los urgentes que se siembra con la tabla vacía |
| `GOOGLE_CHAT_WEBHOOK`, `GOOGLE_CHAT_WEBHOOK_2` | (Secret `ec-googlechat`) | Los dos espacios de Google Chat |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | (Secret `ec-webpush`) | Web Push |

### front-office

`FRONT_OFFICE_HOTEL=MRU01`, `FRONT_OFFICE_PMS_HOTEL=XMAR`, `CRS_INTEGRATION_URL`, `MDM_URL`, `AUDIT_URL` (el «Historial» de la reserva),
`CONSOLE_URL=https://ec1.mateu.io`.

### booking (el CRS)

`CUSTOMER_MDM_URL` (los enlaces de la ficha), `AUDIT_URL=http://audit-service:8129` (su «History»:
quién hizo qué con la reserva).

### journey-service

`TEMPO_URL=http://tempo.observability.svc.cluster.local:3200`,
`GRAFANA_URL=https://grafana.ec1.mateu.io`, y las URL del CRS, el MDM y el mapeado.

### ia-agent

| Variable | Valor | Para qué |
| :------- | :---- | :------- |
| `IA_CONTROL_PLANE_URL` | `http://ia-control-plane:8110` | De dónde saca su configuración. No tiene agente propio: el agente por defecto llega en cada petición (`X-Default-Agent`, del gateway) |
| `IA_CAPTURE_CONTENT` | `full` | Qué de la conversación va a Tempo (`none`, `redacted`, `full`) |
| `IA_CAPTURE_MAX_CHARS` | 16384 por defecto | Corte de cada valor capturado |
| `IA_A2A_MAX_DEPTH` | 2 por defecto | Saltos A2A |

### ia-control-plane

| Variable | Valor | Para qué |
| :------- | :---- | :------- |
| `CP_DEFAULT_AGENT_ID` | `console-agent` por defecto | El agente por defecto del catálogo: contesta cuando ninguna regla encaja y la petición no trae agente por defecto (`GET /internal/agents/default`) |
| `DB_URL` | `jdbc:postgresql://cp-postgres:5432/controlplane` | Su propia base de datos |
| `DB_USERNAME`, `DB_PASSWORD` | (Secret `ec-cp-postgres`) | |
| `CP_CRYPTO_KEY` | (Secret `ec-cp-crypto`) | Cifra las credenciales de los LLM. **No regenerar** |
| `ANTHROPIC_API_KEY` | (Secret `ec-anthropic`) | Solo para sembrar el LLM en un despliegue nuevo |
| `EMBEDDINGS_URL`, `EMBEDDINGS_MODEL` | `http://embeddings/v1`, `intfloat/multilingual-e5-small` | El modelo de embeddings de RAG |
| `GITOPS_ENABLED`, `GITOPS_REPO`, `GITOPS_BRANCH`, `GITOPS_PATH`, `GITOPS_POLL_ENABLED` | `true`, `miguelperezcolom/ec-ia-config`, `master`, `ia`, `false` | GitOps del catálogo |
| `GITOPS_GITHUB_TOKEN`, `GITOPS_WEBHOOK_SECRET` | (Secret `cp-gitops`) | |
| `A2A_BASE_URL` | `http://ia-agent:8095` por defecto | La base de las direcciones A2A |

### api-mcp

`IA_CONTROL_PLANE_URL`, `CATALOGUE_REFRESH_INTERVAL=30s`.

### users

`KEYCLOAK_ADMIN_PASSWORD` (Secret `keycloak-admin`): la propagación de usuarios a Keycloak.

### Terceros

- **keycloak**: `KC_HOSTNAME=https://auth.ec1.mateu.io`, base de datos en el PostgreSQL del motor,
  administrador inicial desde el Secret `keycloak-admin`.
- **postfix**: `RELAYHOST=[smtp.gmail.com]:587`, `RELAYHOST_TLS_LEVEL=encrypt`,
  `ALLOWED_SENDER_DOMAINS=mateu.io`, contraseña del Secret `postfix-relay`.
- **embeddings**: `HUGGINGFACE_HUB_CACHE=/data` (el modelo se descarga al arrancar).

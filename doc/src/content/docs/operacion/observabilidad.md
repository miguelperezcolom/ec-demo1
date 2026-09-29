---
title: Observabilidad
description: Prometheus, Grafana, Loki y Tempo en ec1 — los dashboards propios, las alertas y dónde mirar.
---

La pila está en el namespace `observability`, desplegada por `deploy.sh` desde
`deploy/observability/`: **kube-prometheus-stack** (Prometheus y Grafana, `grafana.ec1.mateu.io`),
**Loki** (logs de todos los pods, 7 días), **Tempo** (trazas) y **Alloy** (recolección).

- **Métricas**: Prometheus recoge `/actuator/prometheus` de los pods anotados con `prometheus.io/*` y los
  ServiceMonitors de `servicemonitors.yaml`. Retención de 24 h.
- **Logs**: todos los pods, en Loki.
- **Trazas**: cada servicio exporta por OTLP a `tempo.observability:4318`, con `OTEL_SERVICE_NAME` y
  muestreo completo. Tempo tiene `max_attribute_bytes` subido a 65536 para no cortar el contenido de las
  conversaciones de los agentes.

## Dashboards

Se cargan como ConfigMaps que recoge el sidecar de Grafana, **nunca importados a mano**: Grafana guarda
su base de datos en un `emptyDir`, así que un dashboard importado desde la interfaz dura lo que dura el
pod. `deploy.sh` los aplica con `apply_dashboard <nombre> <clave> <fichero>`.

| Dashboard | Fichero | Qué enseña |
| :-------- | :------ | :--------- |
| **EventConductor** | `eventconductor.json` | Si el motor da abasto, dónde se va el tiempo, si el relay es el cuello de botella |
| **EventConductor — nodes** | `nodes.json` | CPU por componente, throttling, el pool JDBC, GC |
| **Booking traces** | `booking-traces.json` | Las trazas de las reservas, el lado técnico de «Ver recorrido» |
| **IA agents** | `ia-agents.json` | Prompts y llamadas a herramientas recientes, con su contenido si se captura |
| **IA token usage** | `ia-tokens.json` | Tokens por agente |
| **External APIs** | `external-apis.json` | El consumo de Salesforce y de Opera |

:::note[La clave de cada ConfigMap]
El sidecar escribe todos los dashboards en un único directorio y Grafana deriva el id interno de esa
ruta: dos ficheros con el mismo nombre en el clúster (el chart ya trae un `nodes.json`) hacen que el
segundo se rechace sin aviso. Por eso el de los nodos se guarda con la clave `eventconductor-nodes.json`.
:::

## Alertas

`deploy/observability/prometheus-rules.yaml`, una `PrometheusRule` con la etiqueta `release: kps`, que es
por lo que el operador selecciona reglas:

| Alerta | Cuándo |
| :----- | :----- |
| `SalesforceAllowanceHigh` | La org ha gastado el 80 % de su cupo diario |
| `SalesforceAllowanceCritical` | El 95 %, o el MDM ha pausado sus llamadas porque se agotó |
| `SalesforceOurCallRateHigh` | Nuestro ritmo de llamadas, proyectado a 24 h, pasa de 10.000 |
| `SalesforceOthersSpending` | Otros (no ec1) han gastado más de 5.000 |
| `OperaThrottled` | OHIP ha contestado 429 |

**Alertmanager está apagado** en este despliegue: las alertas se ven en Prometheus y en la lista de
alertas de Grafana, pero no avisan a nadie.

## Dónde mirar

- **Un proceso**: *Workflow → Processes* en la consola (los pasos, los intentos); o Kafka en
  `kafka.ec1.mateu.io` —`upstream` (lo que se pide al motor), `outbox` (cada cambio que registra),
  los topics de tareas—.
- **Una reserva**: «Ver recorrido» ([El recorrido de una reserva](/integracion/recorrido/)).
- **Un agente**: *IA agents → Recent requests*, o Tempo con TraceQL, p. ej.
  `{resource.service.name="ia-agent" && span.ia.outcome != "success"}`.
- **Salesforce y Opera**: los KPI de la página de inicio de las consolas y el dashboard *External APIs*
  ([Consumo de Salesforce y Opera](/operacion/consumo-apis-externas/)).

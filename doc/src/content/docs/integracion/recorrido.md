---
title: El recorrido de una reserva
description: «Ver recorrido» — la traza de un cambio de reserva a través de todos los sistemas, contada en palabras de negocio.
---

Cada cambio de una reserva es **una traza de OpenTelemetry** que cruza el CRS, la integración, el
motor, el mapeado, el MDM, Opera y el front office. `journey-service` la lee de **Tempo** desde dentro
del clúster (el navegador nunca habla con Tempo) y la cuenta en palabras de negocio, preguntando al
CRS, al MDM y al mapeado lo que los spans no dicen. No guarda datos y no exporta trazas propias: leer
una traza no debe escribir más.

## Dónde se abre

En el plano de datos:

- **Call center → la reserva → «Ver recorrido»**;
- la columna *Recorrido* de las reservas de un cliente en *Clientes*;
- *Recorrido de la reserva* en «En otros sistemas» de la estancia, en el front office.

Rutas: `/journey/bookings/<localizador>`, y `/journey/bookings` lista las reservas con cambios en los
últimos 7 días.

## Qué enseña

- **Tiempos**: *Hasta Opera* y *Hasta el front office* desde el cambio en el CRS, y el total.
- **Recorrido por sistemas**: un carril por sistema y cada salto una barra en el eje de tiempo; en ámbar
  lo que esperó (el candado de la reserva, una causa, reintentos), en rojo lo que falló; las líneas
  discontinuas marcan cuándo la tuvieron Opera y la recepción.
- **Cambios de la reserva**, el más reciente primero (creada, modificada, cancelada, no-show, walk-in,
  backfill, reanudada tras resolver sus causas), cada uno con su recorrido; y **las causas** de la
  reserva.
- **Paso a paso**: cada salto con su hora, su desfase y su duración, y lo que hizo: las equivalencias
  usadas, cómo reconoció el MDM al cliente (nuevo, por email, por documento…), la reserva y el perfil de
  Opera con la versión del CRS escrita, la estancia y el contacto de Salesforce.
- **«Ver traza técnica»**: la misma traza en Grafana (dashboard *Booking traces*).

Las trazas llegan a Tempo unos segundos después: una reserva recién hecha dice *Sin trazas aún* o
*En curso*, y la página vuelve a mirar sola.

## Lo que lo hace posible

- Todos los servicios exportan a Tempo por OTLP (`OTLP_TRACING_ENDPOINT`) con muestreo completo
  (`TRACING_SAMPLING=1.0`) y su `OTEL_SERVICE_NAME`.
- Los consumidores de Kafka **continúan la traza** que trae el registro (cabecera `traceparent`), y el
  motor la pone en cada tarea: el worker ejecuta su handler en la traza del motor.
- Configuración: `TEMPO_URL` (la API de consulta de Tempo, `:3200`) y `GRAFANA_URL` para el enlace a la
  traza técnica.

---
title: Consumo de Salesforce y Opera
description: Cómo se cuentan las llamadas a Salesforce y a OHIP, dónde se ven y qué hacer si se agota el cupo de Salesforce.
---

La org de Salesforce es una **Base Edition**: **15.000 llamadas en 24 h móviles** (`DailyApiRequests`),
compartidas por todo lo que la use —el MDM de ec1, cualquier entorno local con las mismas credenciales,
scripts, pruebas, personas—. Pasado el cupo, Salesforce contesta `REQUEST_LIMIT_EXCEEDED` a todo hasta
que las llamadas de hace 24 h salen de la ventana. No cuentan pedir el token, los eventos por Pub/Sub ni
lo que se hace a mano en la consola de Salesforce.

Un MDM local olvidado, sondeando cada 20 s, llegó a gastar el cupo entero. Por eso cada llamada se
cuenta donde se hace.

## Cómo se cuenta

- **`customer-mdm-service`** etiqueta cada llamada a Salesforce con su propósito (`projection`,
  `change-case`, `decisions`, `reason`, `refresh`, `poll`, `merge`, `consolidation-read/write`,
  `limits`, `token`) en cubos por hora guardados en `salesforce_api_calls`, y como
  `salesforce_api_calls_total{purpose,outcome}`. El total de la org lo toma de la cabecera
  `Sforce-Limit-Info` de cada respuesta (`salesforce_org_api_used` / `_max`,
  `salesforce_budget_paused`); si nadie lo ha dicho en 15 min, pregunta a `/limits` (una llamada). Avisa
  al arrancar si `mdm.poll` es de menos de 5 min. `GET /usage/salesforce`, solo dentro del clúster.
- **`pms-integration-service`** cuenta cada llamada a OHIP por módulo y endpoint con un interceptor en
  `OhipClient` (`opera_api_calls_total{service,purpose,outcome}`, 429 = `limited`) y guarda las
  cabeceras de límite que mande OHIP. `GET /usage/opera`.
- **`integrations-service`** suma las dos (`integrations.usage.*`, en caché 15 s) y sirve, bajo
  `/_api-usage` en todos los hosts de consola, las tarjetas de la página de inicio y la página que hay
  detrás. **Ver una página no gasta ninguna llamada a Salesforce.**

## Dónde se ve

- **La página de inicio de las consolas** (`/inicio`): tres tarjetas —llamadas libres de Salesforce,
  nuestras de la última hora, llamadas a Opera de hoy—. Al pulsarlas se abre *APIs externas*
  (`/usage/apis`), con la cuota de la org, las nuestras frente a las de otros, las rechazadas y los
  desgloses por propósito y por endpoint de OHIP.
- **Grafana**: el dashboard *External APIs* y las alertas de
  [Observabilidad](/operacion/observabilidad/).
- **`deploy/demo/demo-prep.sh health`**: da WARN por debajo de `SF_API_RESERVE` llamadas libres (1000
  por defecto) y FAIL si está agotado.

## Lo que gasta cada cosa

| Qué | Llamadas |
| :-- | :------- |
| El MDM en reposo | ~100 al día: el sondeo de fusiones cada 15 min y, solo mientras haya un Case abierto, el de decisiones cada 5 min |
| Proyectar clientes | 1 por cada 200 pendientes |
| Un alta con 10 reservas | ~5 |
| Un cambio de datos desde recepción | ~5 |
| Escanear un documento | 1 si cambia datos; 1 `merge()` si fusiona |
| `zero.sh` / `reset.sh` | 2–3 consultas + 1 por cada 200 registros borrados |

Una demo completa gasta menos de 100.

## Si se agota

El MDM no insiste: la primera negativa **pausa** todas sus llamadas 5 min, el doble en cada negativa
seguida hasta 1 h, y deja **un aviso** en la bandeja de los administradores («Salesforce: daily API
allowance spent»), que se cierra solo cuando Salesforce vuelve a contestar. Mientras, nada se pierde:
los clientes siguen *pendientes*, los contactos que cambiaron quedan marcados para leerlos, las fusiones
y los Cases esperan y el sondeo retoma desde su cursor.

1. Mirar quién gasta: si «otros» crece, buscar **procesos locales** con las mismas credenciales (un
   `customer-mdm-service` de `e2e/poc-acl-local` olvidado) antes de sospechar de ec1.
2. Esperar: la ventana es móvil y el cupo vuelve a medida que salen las llamadas de hace 24 h.
3. Al volver, el MDM se pone al día solo.

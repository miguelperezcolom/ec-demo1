---
title: El agente (ia-agent)
description: Cómo contesta un prompt el agente — configuración pedida al plano de control, herramientas MCP por petición, disponibilidad y observabilidad.
---

`ia-agent` es la otra mitad del panel de chat. **No implementa nada**: cada respuesta es una llamada a
una herramienta o una negativa. No tiene pantallas, ni base de datos, ni configuración propia; sabe dos
cosas de sí mismo: dónde está el plano de control (`IA_CONTROL_PLANE_URL`) y qué agente es por defecto
(`AGENT_ID`).

La misma imagen corre en tres despliegues:

| Despliegue | Agente | Dónde |
| :--------- | :----- | :---- |
| `ia-agent` | `console-agent` | `/ai/**` de las consolas del plano de datos (`ec1`, `rw.ec1`) |
| `ia-agent-front-office` | `reception-agent` (solo el MCP del front office) | `/ai/**` de `front.ec1` |
| `ia-agent-control-plane` | `control-plane-agent` | `/ai/**` de las consolas de control (`console.ec1`, `rw-console.ec1`), con rol `ai-admin` |

El front office y el control plane tienen su propio pod porque el plano de control enruta por la ruta
de la pantalla, y la ruta no basta: las del front office (`/reservas`, `/bienvenida`…) no comparten un
prefijo que una regla pueda usar, y el control plane comparte pantallas con el plano de datos
(`/mapping`). Lo que dice de dónde viene la pregunta es el host. Las reglas de ruta del catálogo se
siguen aplicando antes: desde las pantallas de mapeado contesta `mapping-agent`.

:::note[Previsto, sin hacer]
Un solo despliegue para todos: el gateway pondría en cada petición el canal y el agente por defecto
según el host, `ia-agent` los pasaría a `/internal/agents/resolve` y las reglas de ruta del catálogo
decidirían con ellos. `AGENT_ID` dejaría de fijarse en el pod.
:::

## Un prompt

1. El panel de chat hace POST a `/ai/api/agent/stream` con el token de la sesión (el gateway lo exige:
   cada prompt se factura).
2. El agente resuelve **qué agente contesta** (`/internal/agents/resolve`, por la ruta de la pantalla,
   el rol y el locale) y pide su **configuración**: modelo, credencial, prompt, servidores MCP, fuentes
   RAG y agentes pares.
3. Abre una **conexión nueva a cada servidor MCP** —por prompt, no en un pool: el cliente MCP que
   autoconfigura Spring AI mantiene una conexión SSE persistente y, si se cae, queda roto para toda la
   vida del pod—, reúne las herramientas que anuncian, añade las de RAG y las de los pares, y se las da
   al LLM.
4. El prompt de sistema le dice que conteste solo llamando herramientas y que informe de un fallo de
   una herramienta en vez de inventar alrededor.

## La configuración, en caché

- **30 segundos de caché**, y la **última copia buena sobrevive** al plano de control: una ráfaga de
  prompts es una sola consulta, y un cambio en la consola llega en uno o dos prompts. Si la
  actualización falla, se sirve la copia anterior, con un aviso en el log y `degraded` en la salud.
- **La disponibilidad sigue a la configuración.** Un pod que nunca ha llegado al plano de control se
  declara DOWN y queda fuera del Service: sin modelo solo produciría errores más despacio. Perder el plano
  de control **después** no lo tumba. La sonda de *readiness* es también el bucle que refresca; la de
  *liveness* ignora todo esto (reiniciar no arregla que otro servicio no conteste).
- **Sin configuración local de respaldo**: una segunda fuente que solo aparece cuando falla la primera
  es como dos configuraciones divergen en silencio.
- Una clave nueva no es un parámetro sino un cliente nuevo: `ChatClientRegistry` guarda uno por
  (proveedor, URL base, clave). Cambiar de modelo no construye nada.

## Observabilidad

`ia-agent` y el plano de control envían trazas a Tempo, y Grafana tiene los dashboards **IA agents** e
**IA token usage**.

- **Una traza por prompt**: `invoke_agent <agente>` es la raíz, con el agente, el modelo, el resultado
  (`ia.outcome`: `success`, `refused`, `no_tools`, `error`) y los totales de tokens y llamadas. Debajo,
  `chat <modelo>` por cada ida y vuelta al LLM y `execute_tool <herramienta>` por cada herramienta, con
  su origen (`mcp` o `rag`). La llamada MCP lleva las cabeceras de traza, así que un servidor que traza
  añade su span a la misma traza.
- **El contenido de la conversación**, en Tempo y nunca en Loki, según `IA_CAPTURE_CONTENT`:
  `none` (solo tokens, tiempos y herramientas), `redacted` (emails, teléfonos, tarjetas, IBAN, DNI/NIE y
  pasaportes enmascarados antes de exportar) o `full`. Cada valor se corta en `IA_CAPTURE_MAX_CHARS`.

:::caution[Datos personales]
En ec1 los manifiestos ponen **`full`** porque es una demo: nombres, emails, teléfonos y reservas quedan
en Tempo durante su retención, legibles para quien entre en Grafana. Con datos reales, `redacted` o
`none`.
:::

- **Métricas** en `/actuator/prometheus`: `ia_agent_prompt_seconds`, `gen_ai_client_operation_seconds`,
  `gen_ai_client_token_usage_total` y `spring_ai_tool_seconds`, con la etiqueta `gen_ai_agent_id`.
- **El consumo** se informa al plano de control por agente (`UsageReporter`), que es contra lo que se
  comprueban los presupuestos.

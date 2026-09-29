---
title: El plano de control de IA
description: ia-control-plane — los catálogos de los que se configuran los agentes, cómo guarda las credenciales, cómo resuelve un agente y la recuperación (RAG).
---

`ia-control-plane` (sección **IA** del plano de control, `/_ia-cp`) guarda los catálogos de los que
se configuran los agentes y entrega a cada `ia-agent` su configuración resuelta. El agente no tiene
modelo, credencial, prompt ni lista de servidores propios.

## Los catálogos

| Catálogo | Qué es una entrada |
| :------- | :----------------- |
| **LLMs** | Un modelo que el despliegue puede llamar, y la clave de API que lo paga |
| **MCP servers** | Un servidor cuyas herramientas puede recibir un agente. No las herramientas: esas las declara el servidor al conectar, y una copia aquí caducaría en silencio |
| **APIs as MCP servers** | Una API existente con un conjunto elegido de operaciones, nombradas y descritas como herramientas. Aquí la lista de herramientas **es** la entrada. Ver [APIs como servidores MCP](/ia/api-mcp/) |
| **RAG sources** | Un almacén vectorial, una colección y el modelo que la embebió. Consultable |
| **Agents** | Un prompt, un LLM, y los servidores, fuentes y **otros agentes** a los que puede llegar. Lo único que recibe un servicio en marcha |
| **Budgets** | Un tope de tokens para un agente, modelo, usuario o tenant por periodo; pasado, el plano de control deja de servir a los agentes que lo gastarían |
| **Routes** | Qué agente contesta según quién pregunta y desde dónde, con sus guardarraíles. Ver abajo |

Un agente se refiere a su LLM, sus servidores, sus fuentes y sus pares **por id**, y no guarda nada de
ellos: la URL de un servidor cambia en un sitio y todos los agentes que lo usan la siguen. En el editor
se eligen del catálogo (combos y casillas), no se teclean.

## Las rutas

Una ruta elige qué agente contesta, con cuatro condiciones opcionales —un **rol** requerido, un
**tenant**, un **locale** y un **prefijo de ruta** de la pantalla desde la que se preguntó—; una
condición vacía es «me da igual». Se prueban por **prioridad** (menor primero) y gana la primera que
encaja. El agente de cada petición lo resuelve `ia-agent` preguntando a `/internal/agents/resolve`.

Una ruta puede poner **guardarraíles** de entrada y de salida alrededor del agente que elige. Ver
[A2A y guardarraíles](/ia/a2a-y-guardarrailes/).

## Las credenciales

Se guardan cifradas con **AES-256-GCM**, con la clave en el Secret `ec-cp-crypto` (`CP_CRYPTO_KEY`) y
en ningún otro sitio. En la consola el campo es de **solo escritura**: dice `set` o `missing`, nunca la
clave, y guardar el formulario no la toca; sustituirla es una acción aparte y confirmada.

Un único método descifra y un único endpoint entrega el resultado:

```
GET /internal/agents/{agentId}/config     →  el agente resuelto, con la clave en claro
```

**No tiene ruta en el gateway y no debe tenerla nunca.** No autentica nada por sí mismo; lo protege
que solo es alcanzable dentro del namespace.

:::danger[Rotar CP_CRYPTO_KEY]
Rotar la clave deja **todas** las credenciales guardadas sin poder descifrarse: nada las vuelve a
cifrar, y la única salida es volver a introducirlas. `deploy.sh` la genera una vez y luego no la toca.
:::

Por eso el plano de control tiene su propia base de datos, `cp-postgres` (pgvector sobre PostgreSQL
16, en un volumen): es la única copia de las credenciales de los LLM.

## Resolver un agente degrada, no falla

Un agente compuesto hace meses puede nombrar un servidor MCP desactivado o borrado. Negarse a servir la
configuración entera tumbaría un panel de chat por una herramienta que falta, así que lo que falta se
**quita y se avisa**:

```json
{ "llm": { "model": "…", "apiKey": "…" },
  "mcps": [ { "name": "Booking service", "url": "http://booking:8108" } ],
  "warnings": [ "MCP 'Forms engine' is disabled — skipped",
                "MCP 'ghost' is no longer in the catalogue — skipped" ] }
```

Lo mismo con las fuentes RAG y con los agentes pares. Un **LLM** que falta o no se puede usar es la
excepción —sin modelo no hay modo degradado— y responde 409 diciendo cuál de las dos cosas es,
desactivado o sin credencial. *Agents → Preview resolved configuration* ejecuta exactamente esto y
enseña los avisos.

## Recuperación (RAG)

Una fuente RAG se puede escribir y consultar, y un agente compuesto con ella recibe **una herramienta**
para buscar en ella.

- **La búsqueda se hace en el plano de control**, no en el agente: buscar necesita la conexión del
  almacén y la credencial del modelo de embeddings, y las dos están aquí. Si el plano de control cae,
  las herramientas RAG fallan; el chat no, porque el agente guarda su configuración.
- **Como herramienta, no como prompt relleno**: el modelo decide cuándo buscar. La **descripción de la
  fuente es la descripción de la herramienta**: una vaga produce una herramienta que nunca se llama.
- **Solo `PGVECTOR` está implementado.** La tabla, su índice y la extensión `vector` se crean en el
  primer uso.
- **Meter contenido**: *Content → Ingest text* en la fuente —pegar, y se trocea, se embebe y se
  guarda—. Lo mínimo para demostrarlo, no un pipeline de documentos.
- **Los embeddings son locales y multilingües**: el pod `embeddings` sirve Text Embeddings Inference con
  `intfloat/multilingual-e5-small`, un endpoint `/v1/embeddings` con forma de OpenAI dentro del clúster.
  Sin segundo proveedor ni segunda clave.

Un despliegue nuevo arranca con una fuente que ya contesta: el sembrado cataloga un *Ops handbook*, lo
pone en el agente y le ingiere un pequeño manual del despliegue
(`ai/ia-control-plane/src/main/resources/handbook/`).

## Sembrado y GitOps

Un despliegue nuevo se **siembra** solo —un LLM de Anthropic, los servidores MCP y el agente de la
consola— **solo cuando los catálogos están vacíos**, no «crear si falta» por entrada, que resucitaría lo
que alguien borró. Con GitOps activo el sembrado se aparta y deja los catálogos a git. En ec1 GitOps
está activo (`GITOPS_ENABLED=true`, repositorio `miguelperezcolom/ec-ia-config`, ruta `ia`): ver
[GitOps del catálogo](/ia/gitops/).

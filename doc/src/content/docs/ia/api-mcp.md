---
title: APIs como servidores MCP
description: api-mcp sirve como servidores MCP las APIs catalogadas — la oferta en el plano de control, la traducción en api-mcp.
---

`api-mcp` sirve las **APIs catalogadas** como servidores MCP, un endpoint por entrada:
`http://api-mcp:8113/<id>/sse`. No tiene datos ni pantallas.

## La oferta y la traducción

- El **catálogo** (*APIs as MCP servers*, en el plano de control) guarda la **oferta**: qué operaciones
  de qué API, con qué nombres y cómo descritas. Aquí la lista de herramientas **es** la entrada, porque
  nadie más la sabe: la compone un operador, no la declara un servidor.
- **`api-mcp`** guarda la **traducción**: lee el documento OpenAPI con un parser de verdad, construye el
  esquema de entrada de cada herramienta y hace la llamada.

El reparto explica por qué el plano de control no lleva ninguna librería de OpenAPI y no está en el
camino de cada llamada a una herramienta. `api-mcp` relee el catálogo cada
`CATALOGUE_REFRESH_INTERVAL` (30 s).

## Cómo recibe un agente estas herramientas

Un operador cataloga `http://api-mcp:8113/<id>/sse` como un **servidor MCP** normal y compone un agente
con él. Nada en el lado del agente sabe que este servidor es distinto de `booking`, y ese es el objetivo:
servirlo como MCP en vez de enseñarle al agente un segundo tipo de herramienta.

## Lo que no sirve, a propósito

- **Sin ruta en el gateway**: llama a APIs de terceros con credenciales guardadas, y un endpoint aquí
  sería una forma de hacer que lo hiciera. Solo se alcanza desde dentro del namespace, que es donde está
  `ia-agent`.
- **Herramientas con roles requeridos**: el catálogo dice que se comprueban donde se hace la llamada, y
  eso es `api-mcp`, pero el transporte MCP no le da al handler la petición HTTP, así que no hay de dónde
  leer los roles de quien llama. En vez de ofrecer una herramienta con la restricción perdida en
  silencio, `api-mcp` se niega a exponerla y lo dice en su log. Una herramienta sin roles se sirve
  normalmente.

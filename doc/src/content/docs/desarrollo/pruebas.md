---
title: Pruebas
description: Tests de cada servicio, tests de contrato, las pantallas de las cuatro consolas con Playwright y la historia de la demo.
---

## Tests de los servicios

Cada módulo tiene los suyos (`mvn -am test`). Además de los unitarios y de integración (con
Testcontainers para PostgreSQL y Redpanda), dos familias vigilan lo que cruza servicios:

- **Tests de contrato** (`*ContractsTest`): los productores validan lo que escriben contra el esquema del
  topic; los consumidores pasan cada ejemplo del esquema por su consumidor real.
- **`ServedTasksTest`** en cada worker: mantiene `contracts/workers/<servicio>.tasks` igual a sus
  `TaskRegistration`.

Ver [Contratos](/contratos/librerias-y-esquemas/).

## Las pantallas: `e2e/`

```sh
cd e2e && npm install && npx playwright install chromium
npm test                 # playwright test: las pantallas de las cuatro consolas
npm run demo             # la historia de la demo contra ec1 (demo.config.ts)
```

**`npm test`** recorre cada pantalla de cada consola, en los dos planos y con los dos renderers, contra
el clúster **desplegado** —solo un despliegue real tiene las cuatro consolas, el gateway que las enruta y
el Keycloak que las protege—. No escribe nada. Las mismas comprobaciones se repiten cuatro veces, y esa
repetición es la prueba: una pantalla que pinta en Vaadin y no en Redwood es un hueco del renderer; una
que pinta en un plano y no en el otro es un hueco de enrutado o de permisos.

Dos cosas que saber de lo que comprueba:

- **Los códigos de estado no prueban nada**: Mateu contesta una ruta que no puede resolver con un
  fragmento «Not found.» y un HTTP 200. Una pantalla cuenta como pintada solo si puso algo en pantalla y
  ningún marcador de «no soportado» del renderer.
- **La barra de menús se lee recorriendo shadow roots a mano**, porque los dos renderers anidan sus
  componentes distinto y un selector hecho para uno no devuelve nada en el otro.

**`npm run demo`** recorre la historia de la demo contra ec1 (una reserva nueva hasta Opera y el front
office, un cambio de datos por Salesforce, un no show, la auditoría, la bandeja) y al final hace el reset
a la línea base (`E2E_RESET=0` para no hacerlo). Necesita la integración de MRU01 dada de alta y una
línea base: ver [La demo](/operacion/demo/).

## De punta a punta en local

`e2e/poc-acl-local` recorre el camino entero contra el orquestador real y el doble de Opera: ver
[Entorno local](/desarrollo/entorno-local/).

## Formularios nuevos

Muchos comportamientos de Mateu pintan mal sin ningún error (ver
[Problemas conocidos](/operacion/problemas-conocidos/#mateu)). Un formulario nuevo se comprueba en un
navegador: Playwright contra la ruta propia del servicio (`/_<servicio>/<menú>/<entrada>`) funciona sin
la consola.

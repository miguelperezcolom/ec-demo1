---
title: GitOps del catálogo
description: Los catálogos de IA como YAML en un repositorio de git, reconciliados en cada push — el esquema, las reglas y cómo se conecta.
---

Los catálogos de IA —agentes, modelos, servidores MCP, APIs como MCP, fuentes RAG, presupuestos y
rutas— se pueden declarar como **YAML en un repositorio de GitHub**, y el plano de control se reconcilia
con él en cada push. `gitops/` en ec-demo1 tiene el **esquema público** y un **ejemplo** de la
estructura; el detalle está en
[`gitops/README.md`](https://github.com/miguelperezcolom/ec-demo1/blob/master/gitops/README.md).

```
repositorio de configuración              este despliegue
  ia/                                       ia-control-plane
    llms/anthropic.yaml      ── push ──▶      GitHubCatalogueSource  (lee ia/ del tarball de la rama, en una petición)
    mcp/orchestrator.yaml                     ReconcileCatalogueUseCase
    apimcp/booking-api.yaml  ◀─ webhook ──    /cp-webhooks/github    (verificado por HMAC)
    rag/handbook.yaml
    agents/console-agent.yaml
    budgets/daily-per-user.yaml
    routes/support-to-console-agent.yaml
```

Una entrada por fichero. El campo `kind` (`llm` | `mcp` | `apimcp` | `rag` | `agent` | `budget` |
`route`) dice de qué catálogo es.

## Las reglas

- **Git solo es dueño de lo que creó git.** Lo que escribe el reconciliador queda registrado como
  gestionado por git (tabla `gitops_managed`); si se borra su fichero, el siguiente sync lo borra. Lo
  creado en la **consola** no lo toca ningún sync —ni lo sobrescribe ni lo borra— y solo se quita desde
  la consola. Conviven: la consola es para probar y arreglos rápidos; git, la fuente duradera.
- **Lo mueve el push, no un reloj.** El webhook reconcilia con cada cambio real; un reconcile también
  corre al arrancar. El sondeo periódico está **apagado por defecto**, porque desharía un arreglo de
  consola antes del siguiente push.
- **Los secretos nunca están en el repositorio.** Una entrada `llm` nombra una variable de entorno en
  `credentialEnv` y el plano de control la resuelve de su propio Secret al sincronizar. El token de
  lectura (solo hace falta si el repositorio es privado) y el secreto HMAC del webhook son del
  despliegue (Secret `cp-gitops`). La lectura es una sola petición —el tarball de la rama—, así que no
  agota el límite de la API de GitHub al arrancar, y es todo o
  nada: una lectura a medias nunca se interpreta como «el repositorio está vacío».
- **`mcp` y `apimcp` no son lo mismo.** Un `mcp` es un servidor de otro y no lista herramientas; un
  `apimcp` es una oferta compuesta y su lista de herramientas **es** la entrada. Omitir `tools` deja la
  oferta guardada como está; una lista vacía la vacía a propósito.

## En ec1

```yaml
- { name: GITOPS_ENABLED, value: "true" }
- { name: GITOPS_REPO,    value: "miguelperezcolom/ec-ia-config" }
- { name: GITOPS_BRANCH,  value: "master" }
- { name: GITOPS_PATH,    value: "ia" }
- { name: GITOPS_POLL_ENABLED, value: "false" }
```

El webhook del repositorio apunta a `https://console.ec1.mateu.io/cp-webhooks/github`
(`application/json`, solo el evento push, con el secreto `GITOPS_WEBHOOK_SECRET` de
`deploy/.secrets/credentials.env`).

## Autocompletado en el editor

El esquema se publica en
`https://raw.githubusercontent.com/miguelperezcolom/ec-demo1/master/gitops/ia-catalogue.schema.json`
y cada fichero de ejemplo empieza con la línea que lo enlaza:

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/miguelperezcolom/ec-demo1/master/gitops/ia-catalogue.schema.json
```

Con la extensión **YAML** de Red Hat en VS Code, o en IntelliJ (que la reconoce sola), da
autocompletado de campos y valores, documentación al pasar el ratón y marca lo que el esquema rechaza.

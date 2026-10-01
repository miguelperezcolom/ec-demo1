---
title: Despliegue
description: Cómo se construyen las imágenes, cómo se despliega ec1 desde cero y cómo se despliega un cambio.
---

```sh
./deploy/build-images.sh TAG     # las imágenes de este repositorio → Docker Hub
./deploy/deploy.sh               # todo lo demás, idempotente
```

Hace falta `kubectl` apuntando al clúster de CloudFleet, `helm`, `docker` y `gh`. El acceso al
clúster se pide; no se lo concede uno mismo.

## `build-images.sh`

Instala primero lo que no es una aplicación pero de lo que compilan las demás —`grpc-interface`, los
contratos, `messaging` y `ui-commons`— y después, por cada módulo de la lista `APPS`, hace un
`clean package` y construye la imagen con `docker buildx build --platform linux/amd64 --push`. Las
imágenes se llaman `miguelperezcolom/ec-demo1-<carpeta>`; las consolas se construyen dos veces (Vaadin
y `-Predwood`, `-redwood` en el nombre). Al final construye la del sitio de documentación (`doc/`, sin
paso de Maven) con su propio tag, `DOCS_TAG`.

- **El tag se pasa siempre.** Es uno solo para todas las imágenes de esa ejecución, y sin él el script
  no construye nada. Como cada manifiesto fija el suyo, una ronda normal no usa este script entero (ver
  [Desplegar un cambio](#desplegar-un-cambio)).

- **`clean` no es paranoia.** El procesador de anotaciones de Mateu escribe el controlador de la página
  de arranque en `target/generated-sources`, y Maven no lo vuelve a ejecutar si solo cambió
  `mateu.version`: sin `clean`, la imagen lleva el bundle nuevo detrás de un arranque viejo. Y el
  segundo build de una consola reutilizaría el `target/` del primero: una imagen Redwood hecha sobre
  restos de Vaadin es una imagen Vaadin con otro nombre.
- **Solo `linux/amd64`**, como el `nodeSelector` de todos los despliegues: el pool de Karpenter puede
  dar nodos arm64, y una imagen de una sola arquitectura en un nodo arm64 es un pod que no arranca.
- El motor (orquestador, formularios, reglas) corre de imágenes publicadas, igual que Keycloak,
  PostgreSQL y Redpanda.

## `deploy.sh`

Despliega todo desde un clúster vacío, en seis pasos, y es **idempotente**: volver a ejecutarlo
converge el clúster.

1. Requisitos del clúster: el controlador de ingress y el emisor de certificados.
2. Namespaces y secretos.
3. El motor (PostgreSQL, Redpanda, orquestador, formularios y reglas), desde el chart vendorizado
   (`deploy/chart/eventconductor`, ver `VENDORED.md`).
4. Keycloak, consolas, gateway, ingress, servicios, plano de control.
5. Observabilidad (Prometheus, Grafana, Loki, Tempo, Alloy), dashboards y reglas de alertas.
6. Esperar a que todo esté listo.

**Las contraseñas se generan la primera vez** en `deploy/.secrets/credentials.env`, que git ignora, y
una segunda ejecución no las cambia (añade solo las que falten). Dos cosas no se generan porque se
compran o se piden: la clave de Anthropic (`ANTHROPIC_API_KEY`, línea comentada que hay que rellenar) y
la *App Password* del relay de correo. Tampoco crea el DNS: `ec1` y `*.ec1` tienen que apuntar al
balanceador del ingress antes de que Let's Encrypt emita nada; el comodín cubre cualquier host nuevo, y
cada uno recibe su certificado por HTTP-01 la primera vez que se resuelve.

:::danger[Lo que no se regenera]
`CP_CRYPTO_KEY` e `INTEGRATIONS_CRYPTO_KEY` cifran las credenciales de los LLM y los secretos de
conexión con Opera. Regenerarlas deja todo lo guardado sin poder descifrarse.
:::

## Desplegar un cambio

Lo habitual no es `deploy.sh` entero sino una ronda de módulos:

1. Compilar y subir **solo los módulos que cambiaron**, con un **tag nuevo** por módulo (cada
   manifiesto fija su tag, p. ej. `ec-demo1-pms-integration-service:0.39.3`, y `imagePullPolicy:
   IfNotPresent`: reutilizar un tag no despliega nada).
2. Subir los tags en `deploy/manifests/*.yaml`.
3. `kubectl apply -f` de esos manifiestos y esperar el `rollout status`.
4. Un commit `deploy: …` con las versiones, por PR.

Si cambió `contracts/`, `ui-commons` o la versión de Mateu, los que dependen de ellos también cambian:
se reconstruyen todos los afectados, con `clean`.

:::caution[El disco se llena]
`docker buildx build --push` con el builder por defecto **guarda también la imagen en local**: cada
ronda suma ~0,5 GB por imagen. Después de subir, se borran las copias locales de
`miguelperezcolom/ec-demo1-*` que no use ningún contenedor y se hace `docker builder prune -af`. Mirar
`df -h /` antes de una ronda grande.
:::

## Las bases de datos al actualizar

Los servicios usan `ddl-auto: update`, que **añade** pero nunca rehace: no borra una columna renombrada
ni reescribe la restricción de un enum. Un campo nuevo que en local funciona puede fallar en ec1, cuya
base de datos es más vieja. Ver [Problemas conocidos](/operacion/problemas-conocidos/).

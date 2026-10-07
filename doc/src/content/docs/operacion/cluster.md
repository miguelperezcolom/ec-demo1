---
title: El clúster
description: ec1 en CloudFleet/Hetzner — nodos, Karpenter, dormir y despertar, volúmenes, balanceador y DNS.
---

ec1 corre en un clúster de **CloudFleet sobre Hetzner**, en la región **hel1**. Todo el despliegue está
en el namespace `ec-demo1`; la observabilidad, en `observability`.

## Nodos

Un solo pool, y **Karpenter elige las máquinas**. Cada despliegue se fija solo a la región y a `amd64`
(las imágenes son de una arquitectura y la línea CAX de Hetzner es arm64); ningún tipo de instancia y
ninguna antiafinidad.

La observabilidad también: hasta el 07-10-2026 tenía un nodo `ccx23` propio en fsn1 —una separación que
venía del benchmark del motor— que, con la subida de precios de Hetzner, era casi la mitad del gasto
(~86 €/mes con su balanceador). Ahora va en hel1 con el resto, fijada solo a región y arquitectura.

- **No fijar un tipo de instancia.** Fijar `cx43` dejó todos los pods en `Pending` el día que hel1 no
  tenía ninguno a la venta: Karpenter dice «no instance type ... had a required offering», que parece una
  cuota y es falta de stock. Tampoco existe cualquier tipo del catálogo (no hay `ccx33`).
- **Karpenter mueve pods para consolidar**, y cada movimiento es un reinicio. Los que rompen una demo al
  reiniciarse llevan `karpenter.sh/do-not-disrupt`: el PostgreSQL del motor, Redpanda, el orquestador,
  Keycloak, el gateway, el front office, `cp-postgres` y el controlador de ingress. Uno nuevo igual de
  importante debería llevarlo también.
- **Después de un cambio de nodos, Karpenter reorganiza** durante unos 15 minutos (consolidación
  `WhenEmptyOrUnderutilized`, a los 5 min): desaloja pods «Underutilized» y prueba nodos más baratos.
  Como todo tiene una réplica, lo que mueve se cae un momento. El 07-10-2026, al quitar el nodo de fsn1,
  el MDM no respondía mientras el onboarding escribía en Opera, y 8 de 10 reservas llegaron sin código de
  cliente. Antes de un onboarding, un snapshot o una demo, esperar a que
  `kubectl get events -A --field-selector reason=Evicted` lleve 5 minutos en silencio.
- **El límite de CPU del fleet** (40) solo se cambia por la Fleet API de CloudFleet (la CLI
  `cloudfleet clusters fleets update`, que sobrescribe todo el fleet: se lee entero y se cambia solo el
  límite): `kubectl` no puede editar ni crear NodePools.
- **Alloy va con `priorityClassName: system-node-critical`**: es el DaemonSet que lee los logs de cada
  nodo, y en un nodo lleno se quedaba en `Pending` (20 horas, el 06-10-2026) sin que los logs de ese nodo
  llegaran a Loki.

## Dormir y despertar ec1

Cuando la demo no está en ec1 (ver [La demo vive en un solo sitio](/operacion/demo/#la-demo-vive-en-un-solo-sitio)),
ec1 **duerme sin ningún nodo de Hetzner**, que es casi todo el coste:

```sh
./deploy/sleep.sh     # todo a 0, el fleet a 0 CPU, sin nodos
./deploy/wake.sh      # el fleet a 40 CPU y cada carga a sus réplicas (unos minutos)
```

- `sleep.sh` guarda las réplicas de cada Deployment y StatefulSet en la anotación
  `ec-demo1/sleep-replicas` y los baja a 0 (los Deployments primero: el operador de Prometheus es uno y,
  vivo, volvería a subir sus StatefulSets). Pone el límite de CPU del fleet a 0 para que Karpenter no
  arranque un nodo para lo que queda —los pods de `kube-system` de CloudFleet, que se quedan en
  `Pending`— y borra los NodeClaims.
- **El último nodo no se drena solo**: el PodDisruptionBudget de `hcloud-csi-controller` (mínimo 1) rechaza
  su desalojo cuando no hay otro nodo. El script borra esos pods en vez de desalojarlos (borrar no es
  desalojar), cuando ya no queda ningún volumen conectado.
- **Se conserva**: el clúster de CloudFleet, los volúmenes (las bases de datos) y el DNS. **Se pierde** el
  volumen de **Loki**: su StatefulSet borra el PVC al bajar a 0. Son los logs, nada más.
- `wake.sh` sube primero los StatefulSets (las bases de datos, Redpanda) y después los Deployments, y
  espera a que estén listos. Después, `deploy/demo/demo-prep.sh` para comprobarlo.
- Ambos se niegan a correr si el contexto de `kubectl` no es el de ec1.

Para tocar algo con ec1 dormido (p. ej. leer su base de datos del plano de control) basta subir el límite
del fleet a 4 CPU y la réplica de esa carga: Karpenter arranca un nodo pequeño (`cx23`). `sleep.sh` lo
vuelve a dormir.

## Volúmenes

Los **dos PostgreSQL están en volúmenes** (`hcloud-volumes`): el del motor desde el 25-09-2026 —antes
estaba en un `emptyDir` de NVMe local, por el benchmark, y moría con su pod— y `cp-postgres`. **Borrar
un PVC es lo que pierde datos**; mover un pod ya no.

## Red, balanceador y DNS

- El **controlador de ingress** (`ingress-nginx`) también está en hel1, y `ec1.mateu.io` y `*.ec1`
  apuntan al balanceador de hel1 (77.42.12.63). CloudFleet crea uno por región con nodos: una región que
  se queda sin nodos pierde su balanceador, y el DNS no debe apuntar a él. Al despertar ec1, comprobar
  que la IP del servicio `ingress-nginx-controller` sigue siendo la del DNS.
- **No tocar la release de Helm de `ingress-nginx`**: tiene el LoadBalancer al que apunta el DNS;
  reinstalarla cambia la IP y todos los hosts se rompen hasta que el DNS se entere.
- El DNS de `ec1.mateu.io` está en Netlify: dos registros, `ec1` y `*.ec1`. El comodín cubre `auth`,
  `grafana`, `kafka`, `console`, `rw`, `rw-console`, `front`, `doc` y cualquier host que se añada.
- No hay ninguna NetworkPolicy en vigor salvo la que pone `deploy/demo/opera-outage.sh` mientras
  simula una caída de Opera.

## Un despliegue, una réplica

El pool está limitado, y todo corre con **una réplica**. El motor escala en horizontal por diseño
(subir `replicas` en `deploy/values/eventconductor.yaml` es el único cambio: las instancias se coordinan
por PostgreSQL y el outbox, no por un líder). El motor de reglas corre con una réplica, como el resto,
y lee las reglas de `ec-definitions/definitions/rules`, aunque ningún proceso tiene todavía pasos `RULE`.

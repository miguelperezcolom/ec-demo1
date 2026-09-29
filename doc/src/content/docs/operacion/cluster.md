---
title: El clúster
description: ec1 en CloudFleet/Hetzner — nodos, Karpenter, volúmenes, balanceador y DNS.
---

ec1 corre en un clúster de **CloudFleet sobre Hetzner**, en la región **hel1**. Todo el despliegue está
en el namespace `ec-demo1`; la observabilidad, en `observability`.

## Nodos

Un solo pool, y **Karpenter elige las máquinas**. Cada despliegue se fija solo a la región y a `amd64`
(las imágenes son de una arquitectura y la línea CAX de Hetzner es arm64); ningún tipo de instancia y
ninguna antiafinidad.

- **No fijar un tipo de instancia.** Fijar `cx43` dejó todos los pods en `Pending` el día que hel1 no
  tenía ninguno a la venta: Karpenter dice «no instance type ... had a required offering», que parece una
  cuota y es falta de stock. Tampoco existe cualquier tipo del catálogo (no hay `ccx33`).
- **Karpenter mueve pods para consolidar**, y cada movimiento es un reinicio. Los que rompen una demo al
  reiniciarse llevan `karpenter.sh/do-not-disrupt`: el PostgreSQL del motor, Redpanda, el orquestador,
  Keycloak, el gateway, el front office, `cp-postgres` y el controlador de ingress. Uno nuevo igual de
  importante debería llevarlo también.
- **El límite de CPU del fleet** (40) solo se cambia por la Fleet API de CloudFleet: `kubectl` no puede
  editar ni crear NodePools.

## Volúmenes

Los **dos PostgreSQL están en volúmenes** (`hcloud-volumes`): el del motor desde el 25-09-2026 —antes
estaba en un `emptyDir` de NVMe local, por el benchmark, y moría con su pod— y `cp-postgres`. **Borrar
un PVC es lo que pierde datos**; mover un pod ya no.

## Red, balanceador y DNS

- El **controlador de ingress** (`ingress-nginx`) también está en hel1, y `ec1.mateu.io` y `*.ec1`
  apuntan al balanceador de hel1. CloudFleet crea uno por región con nodos: una región que se queda sin
  nodos pierde su balanceador, y el DNS no debe apuntar a él.
- **No tocar la release de Helm de `ingress-nginx`**: tiene el LoadBalancer al que apunta el DNS;
  reinstalarla cambia la IP y todos los hosts se rompen hasta que el DNS se entere.
- El DNS de `ec1.mateu.io` está en Netlify: dos registros, `ec1` y `*.ec1`. El comodín cubre `auth`,
  `grafana`, `kafka`, `console`, `rw`, `rw-console`, `front` y cualquier host que se añada.
- No hay ninguna NetworkPolicy en vigor salvo la que pone `deploy/demo/opera-outage.sh` mientras
  simula una caída de Opera.

## Un despliegue, una réplica

El pool está limitado, y todo corre con **una réplica**. El motor escala en horizontal por diseño
(subir `replicas` en `deploy/values/eventconductor.yaml` es el único cambio: las instancias se coordinan
por PostgreSQL y el outbox, no por un líder). El motor de reglas está desplegado con cero réplicas:
ningún proceso tiene pasos `RULE`.

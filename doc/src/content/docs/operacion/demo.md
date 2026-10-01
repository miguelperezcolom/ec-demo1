---
title: "La demo: preparar y resetear"
description: Los scripts de deploy/demo — comprobar que ec1 está listo, sembrar datos, simular una caída de Opera, volver a la línea base o a cero.
---

El guion completo de la demo, flujo a flujo y con lo probado en ec1, está en
[`docs/poc-acl/demo.md`](https://github.com/miguelperezcolom/ec-demo1/blob/master/docs/poc-acl/demo.md);
cómo se graba, en
[`grabacion-demo.md`](https://github.com/miguelperezcolom/ec-demo1/blob/master/docs/poc-acl/grabacion-demo.md).
Esta página resume las herramientas de `deploy/demo/`.

## La regla: Opera no se toca

**Ni para resetear**: en Opera no se cancela ni se borra nada. La demo está hecha para repetirse encima
de lo que dejó escrito —reservas e interlocutores se buscan antes de escribir, por localizador y por
`CorporateId`— y durante la demo solo se modifica **lo que se crea en la demo** (una reserva nueva, el
cambio de datos sobre su titular), nunca lo de la línea base.

## Preparar

```sh
deploy/demo/demo-prep.sh [--zero]              # [poner a cero, y luego] las comprobaciones
deploy/demo/demo-prep.sh health                # solo las comprobaciones: una tabla PASS/FAIL
deploy/demo/demo-prep.sh contracts             # solo los contratos de tareas
deploy/demo/demo-prep.sh seed returning-customer [--create]   # flujo 2
deploy/demo/demo-prep.sh seed arriving-today   # flujo 4: una reserva que llega hoy
deploy/demo/demo-prep.sh seed arriving-opera-today   # check-in / check-out: una reserva que llega en
                                               # la fecha de negocio de Opera, y sus habitaciones limpias
deploy/demo/demo-prep.sh seed walk-in          # flujo 5: los datos a teclear
```

Las comprobaciones: todos los despliegues listos; el motor; cada tarea servida por un worker vivo en su
topic (`check-contracts.sh --cluster`); el token de Opera y XMAR legible (GET); el token de Salesforce y
su cupo diario; los clientes del MDM pendientes o fallidos en Salesforce; la integración de MRU01; el
diccionario y las causas abiertas; que no quede puesta una caída de Opera; el contexto de Opera; las
habitaciones libres del front office. Sale con 1 si algo falla. Las semillas se pueden repetir: lo que
crean lleva `demo-prep:<semilla>` en los comentarios de la reserva y se reutiliza.

Para los demás flujos, `ec1.py` (`book`, `modify`, `cancel`, `show`, `rate-plan`, `ask-agent`,
`proposal`) y `opera.py` (lee Opera, solo GET). `ec1.py show <localizador>` enseña de una vez el CRS,
los procesos con su paso e intentos, las causas, los avisos, Opera y la estancia.

## Simular una caída de Opera

```sh
deploy/demo/opera-outage.sh on [--alert-after 2m] [--auto-off 15m]
deploy/demo/opera-outage.sh off [--restore-alert]
deploy/demo/opera-outage.sh status
deploy/demo/opera-outage.sh alert 10m          # solo el umbral del aviso (reinicia el conector)
```

No toca Opera: corta la **red**. Una NetworkPolicy (`demo-opera-outage`) deja a `pms-integration-service`
hablar solo con los pods del clúster, así que cada llamada a OHIP agota su timeout; Cilium la aplica y
el script lo comprueba desde el pod. El proceso **espera, no falla**: el paso que escribe en Opera se
reintenta, y pasado el umbral llega `RETRYING_TOO_LONG` a la bandeja. `--alert-after` baja el umbral
para la demo (reinicia el conector: hacerlo **antes** de la reserva). El corte se levanta solo a los
15 min (`--auto-off`): ec1 lo usan más personas. Después, `off --restore-alert` (o `alert 10m`) devuelve
el umbral a sus 10 min.

## Volver atrás

| Script | Qué hace |
| :----- | :------- |
| `snapshot.sh` | Guarda la **línea base** de ec1 en `~/.local/share/ec-demo1/demo-baseline`, con nada en marcha (ningún proceso corriendo, nada en un outbox) |
| `reset.sh` | Vuelve a la línea base (~4 min): restaura las bases de datos de los servicios y el estado del motor; en Salesforce borra los contactos y Cases que creó la demo (solo los de ec1: la org se comparte con el entorno local) y devuelve los de la línea base a sus datos; el MDM retoma los eventos de Salesforce desde ahora; repone el contexto de Opera de la línea base |
| `zero.sh` | Vuelve a **antes de integrar nada** (~3 min): vacía reservas, integraciones, mapeados, clientes, huéspedes, estancias, notificaciones, avisos de recepción (los de `notices` y la copia del front office), auditoría y procesos; en Salesforce borra todos los contactos y los Cases del MDM. Se queda lo configurado: interlocutores, habitaciones y catálogos del front office, reglas de registro, definiciones, usuarios y Keycloak |

Las bases de datos que entran en la línea base y en `reset.sh` son las de `DATABASES` en
`deploy/demo/common.sh`: `audit`, `booking`, `communication`, `crs_integration`, `customer_mdm`,
`front_office`, `integrations`, `mapping`, `notices`, `partners` (la del ERP) y `registration_rules`.
Las de `users` y `content` no: un reset no toca usuarios ni contenidos.

### Un contexto de Opera por ejecución

El localizador del CRS va en Opera como referencia externa bajo un **contexto** propio, y por él se busca
la reserva antes de escribirla. Como Opera no se limpia, tras poner ec1 a cero un localizador nuevo
(aleatorio) podría repetir uno antiguo y la integración **actualizaría la reserva vieja**. Por eso cada
`zero.sh` estrena un contexto, `ECDEMO1-<MMddHHmm>` (UTC), y lo deja en el ConfigMap **`ec-demo-run`**
(clave `OPERA_EXTERNAL_SYSTEM`), que `pms-integration-service` lee con `envFrom`; sin ConfigMap usa
`ECDEMO1`. `snapshot.sh` guarda el contexto con la línea base y `reset.sh` lo repone. Las reservas
llevan además la *Custom Reference* de la ejecución, que es lo que filtra la integración pms-fo con
ámbito `CHAIN`.

```sh
kubectl -n ec-demo1 get cm ec-demo-run -o jsonpath='{.data.OPERA_EXTERNAL_SYSTEM}'
```

## Salesforce en la demo

- Los duplicados que la demo fusiona en Salesforce («Marcos Ruiz», «Lucía Fernández») son del **MDM
  local**, no de ec1: `reset.sh` no los devuelve tras una fusión. Los recupera
  `dedup.py --restore dedup-out/dedup_result_<ts>.json`.
- El cupo de la API es compartido: ver [Consumo de Salesforce y Opera](/operacion/consumo-apis-externas/).

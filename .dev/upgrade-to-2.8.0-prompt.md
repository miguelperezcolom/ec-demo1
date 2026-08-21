Actualiza este despliegue a EventConductor 2.8.0, que ya está en Maven Central y en Docker Hub.

Ahora mismo corre 2.7.0, con Flyway encendido y `ddlAuto: none` — o sea, el motor aplica sus propias
migraciones al arrancar. La 2.8.0 trae `V25`, que añade la columna `name` a `process_index`;
comprobado que esa columna todavía no existe en la base de datos, así que se creará sola. **No hay
nada que aplicar a mano.**

## 1. Subir la versión

Cuatro sitios, todos hoy en `2.7.0`:

- `deploy/values/eventconductor.yaml` — `imageTag` en orchestrator, forms y rules (líneas 90, 182, 240)
- `deploy/manifests/20-worker.yaml` — `worker-standalone-app:2.7.0`

## 2. Quitar el techo del relay del outbox — esto es lo que da la mejora

`deploy/values/eventconductor.yaml` tiene:

```yaml
WORKFLOW_OUTBOX_RELAYCONCURRENCY: "4"
```

En 2.8.0 los envíos del relay corren en **hilos virtuales** y el default pasa a `0`, que significa
"un hilo por clave de partición del lote". Pero **un valor explícito gana sobre el default** y se
convierte en un techo: si esa línea se queda, el despliegue publica exactamente igual que ahora y la
actualización no sirve de nada para el rendimiento.

Bórrala, o ponla a `"0"`.

Por qué importa: con 4, el productor de Kafka nunca tiene más de cuatro registros en el buffer, así
que amortiza un round trip de 4,9 ms entre cuatro mensajes en vez de entre el lote. Medido en un
broker real con 2 000 mensajes: **449 ms con el tope de 4, 207 ms sin él**.

## 3. Desplegar

```bash
helm upgrade --install ec deploy/chart/eventconductor -n ec-demo1 -f deploy/values/eventconductor.yaml
kubectl apply -f deploy/manifests/20-worker.yaml
```

Espera a que los rollouts terminen antes de medir nada.

## 4. Verificar — con un navegador, no solo con kubectl

Esto es lo importante, porque la última vez **todo parecía correcto y `/workflow/analytics` estaba
devolviendo 500 en producción** mientras los tests decían que no. La verificación de punta a punta es
la que encontró eso.

a) `kubectl get pods -n ec-demo1` → todo Running y los restarts sin subir. Si el orquestador
   reinicia al arrancar, mira sus logs: sería la migración.

b) Con un navegador real contra `https://ec1.mateu.io` (usuario `demo` / `demo` en Keycloak), abre
   `/workflow/processes`, `/workflow/steps` y `/workflow/analytics`, y comprueba que las tres
   devuelven **datos** y no un mensaje de error. Analytics es la que hay que mirar con más cuidado.

c) Mide el relay bajo carga: lanza `deploy/loadgen.sh` y compara transiciones/s. Las métricas que lo
   dicen son `eventconductor.outbox.batch.deliver` y la CPU de Redpanda — si el broker sube de 0,23
   de dos núcleos, es que por fin le estáis dando trabajo.

**Si tras quitar el tope el drenaje sigue muy por debajo de lo esperado, dilo en vez de darlo por
bueno.** Con round trips de 4,9 ms y solo 4 envíos en vuelo el techo teórico eran ~816 msg/s, y lo
observado eran 148 transiciones/s — siete veces por debajo. Puede haber una segunda causa que nadie
ha buscado todavía.

### Sobre comparar con el "antes"

La cifra de 148 transiciones/s se midió con la 2.6.x. Entre medias han entrado la agregación de
analytics en SQL (2.7.0) y los listados paginados en SQL, así que comparar contra ella mide **tres
cambios a la vez**. Si quieres atribuir la mejora al outbox en concreto, lo limpio es medir con la
2.8.0 ya desplegada y `relay-concurrency` a `4`, y luego otra vez sin el tope.

## Opcional, pero relacionado

Si quieres que el listado de procesos vea **toda la flota** en vez de un shard, hace falta
`workflow.projection.enabled=true` (el read model). Sin sharding no cambia nada práctico, así que
solo tiene sentido si vais a shardear.

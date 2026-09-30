# PoC ACL — conclusiones

Bajada de reservas (alta, modificación, cancelación) de un CRS simulado a Opera Cloud por las
Property APIs de OHIP, sobre EventConductor, siguiendo el HLA *CRS-PMS Integration*. Fecha de
cierre: 2026-09-22.

> **Actualización 2026-09-30.** Este documento se cerró el 2026-09-22 y se conserva como estaba; lo
> que ha cambiado desde entonces está en [Después del cierre](#después-del-cierre-2026-09-23--30), al
> final. Lo más importante: desde H12 (2026-09-23) el conector **escribe en el tenant real** (OHIP UAT,
> propiedad XMAR), y la PoC ha crecido hasta el front office del hotel y el maestro de clientes con
> Salesforce.

**No se ha escrito nada en el tenant de Opera** *(a 2026-09-22; ver la actualización)*. Todo el
camino se ha ejecutado contra `opera-mock`, un doble de OHIP construido a partir de las specs
públicas. Contra el tenant real solo se pidió un token OAuth.

## Qué se ha construido

| Pieza | Qué es | Producción | Test |
| :---- | :----- | ---------: | ---: |
| `pms-integration-service` | **El conector OHIP**: token, cabeceras y clasificación de errores; catálogo; perfiles de huésped e interlocutor por id externo; reserva con guarda de versión en UDF; depósitos idempotentes; cancelación; lock por reserva; aviso de reintento prolongado | 985 | 205 |
| `opera-mock` | Doble de OHIP: OAuth, validación y disponibilidad como `rsv`, fallos inyectables, registro de llamadas, UI | 954 | 105 |
| `mapping-service` | Diccionario cadena + propiedad con aprobación, causas y reanudación, perfiles de interlocutor, MCP, propuesta por agente, UI | 1 861 | 333 |
| `crs-integration-service` | ACL del lado CRS: inbox, relectura, traducción a modelo canónico, router evento → proceso | 706 | 219 |
| `integration-model` | Modelo canónico, eventos de negocio, tipos de código y de causa | 246 | — |
| `communication-service` | Avisos por email con deduplicación y destinatarios por tipo y hotel | 622 | 95 |
| `partners` | Maestro de interlocutores (papel del ERP) con outbox | 857 | 101 |
| `booking` | CRS simulado: modelo de reserva real, versión, eventos de dominio y outbox | +2 915 / −214 | (incl.) |
| Definiciones | `proyectar-reserva`, `proyectar-cancelacion`, `proyectar-interlocutor` | 318 (YAML) | — |
| e2e local | Infra, arranque y escenario de 7 pasos contra el orquestador real | 133 (py) | — |

Líneas sin blancos. Además: manifiestos y rutas de despliegue (H9), catálogo de MCP y agente de
mapeado en `ec-ia-config`, menús remotos en los shells.

El escenario local (`e2e/poc-acl-local`) pasa completo contra el orquestador real, en 2.16.5 y en 2.18.0 (la versión desplegada): interlocutores
que esperan su mapeado, una reserva que espera seis causas compartidas y se proyecta con códigos
traducidos, tarifas fijas por noche, régimen como paquete, perfil del interlocutor, ventana de
folio del interlocutor y versión en el UDF; modificación en orden sin duplicar el depósito; 503
reintentados con aviso; rechazo de Opera como causa con nombre; cancelación que espera a la
proyección; emails de cada aviso.

## Coste real de un conector

El AC pedía saber **cuánto cuesta desarrollar un conector contra una API de terceros**. Lo que dice
la PoC:

- **El conector en sí es la parte pequeña.** ~1 000 líneas de producción, de las que menos de la
  mitad son específicas de OHIP (forma de los payloads y rutas). El resto —clasificar errores,
  idempotencia por referencia externa, guarda de versión, lock— es patrón reutilizable para
  cualquier PMS.
- **Lo caro está alrededor.** El mapeado de códigos (diccionario, causas, reanudación, UI y agente)
  casi dobla al conector; la ACL del lado CRS y el modelo canónico suman lo mismo que él. Son
  inversiones que se hacen una vez y sirven para el siguiente conector.
- **El doble cuesta tanto como el conector.** Sin tenant disponible, construir `opera-mock` fue
  imprescindible para avanzar y para probar fallos (503, rechazos, falta de disponibilidad) que
  en un tenant real no se pueden provocar. Hay que contarlo en el coste de cualquier conector.
- **Lo que falta no es código sino validación contra el tenant real** (ver abajo). Es la parte de
  coste más incierta: depende de terceros (código de hotel, permisos de la app, configuración de
  Opera) y puede cambiar el diseño en un punto (R18).

Las horas y el reparto IA / manual están en [`cost-log.md`](cost-log.md).

## Incógnitas del HLA que la PoC cierra

- **Suspensión por causa sobre el motor: viable.** Un proceso que no puede seguir registra sus
  causas en `mapping-service` y espera un único mensaje correlado por su clave; al resolverse la
  última causa se relanza una instancia nueva. Las causas se comparten entre procesos (seis causas
  liberan todos los que las esperaban). Hubo que rediseñarla porque el motor no admite ciclos (ver
  problemas nuevos).
- **R28 — número de secuencia de la reserva.** Una columna de versión en el agregado del CRS,
  incrementada en cada cambio y publicada en el evento, basta. Se escribe en un UDF numérico y se
  compara antes de cada escritura: una proyección más antigua que lo ya grabado termina como
  `STALE` sin escribir.
- **R38 — de dónde leemos el interlocutor.** Del maestro (`partners`, papel del ERP), con su propio
  proceso (#3). La reserva que referencia un interlocutor aún no proyectado espera con causa
  `MISSING_PARTNER` y sigue sola al proyectarse.
- **Orden y concurrencia.** Guarda de versión + lock por reserva en el conector cubren eventos
  seguidos, reprocesos y relanzamientos sin mecanismos especiales.
- **Idempotencia.** Reserva localizada por referencia externa (localizador del CRS), perfiles por
  id externo, depósitos por referencia del cobro: repetir cualquier paso no duplica nada.
- **F014 — cobros de la central.** Se proyectan como depósitos; sin cobros la reserva va como pago
  en hotel.

## Lo que solo puede cerrar el tenant real (H5, aplazado)

- **Acceso**: código de hotel y APIs habilitadas para la app `Riu_Hotel_Cliente_OHIP`. Hoy el
  token es válido (cadena RIUC) pero las Property APIs responden 403.
- **R18 — ¿acepta Opera el salto de versión?** El diseño da por hecho un *upsert* de estado
  completo. Si Opera tuviera control de concurrencia propio (ETag, versión) cambiaría el mecanismo.
- **R19 — revalidación de `rsv`.** Por la API de propiedad Opera revalida códigos, perfiles y
  disponibilidad. El doble lo imita y los rechazos acaban como causa, pero hay que ver qué rechaza
  realmente y con qué códigos de error; decide si conviene la vía de canal para escribir.
- **Configuración del hotel**: UDF libre para la versión, códigos de origen / mercado / paquetes,
  formas de pago, ventanas de folio y *routing*.
- **Multi-habitación**: simplificado a una reserva con varias tarifas; en Opera probablemente sea
  una reserva por habitación enlazadas.
- **`x-hotelid` en APIs de cadena** (perfiles) y **límites de uso** (429) reales.

## Problemas nuevos que la PoC ha sacado

Del motor (EventConductor), a corregir allí:

- **`LOCK` falla en PostgreSQL** (la clave compuesta lleva un separador NUL). Se quitó de las
  definiciones y el lock está en el conector.
- **Una descripción de definición de más de 255 caracteres hace que el import la salte** sin error
  y el validador no avisa.
- **Sin ciclos** y **`JOIN` tras `CHOICE` descartado**: obliga a una cadena espera/relanzamiento por
  cada punto de suspensión, que alarga las definiciones.

De las librerías:

- **Spring AI** descarta en silencio los `@Tool` que devuelven `Object`.
- **Mateu** tiene varios comportamientos silenciosos (acciones sin `@Toolbar`, `null` pintado en
  avisos e insignias, listados que no cargan sin `OnLoadTrigger`).

De diseño, para el DT:

- **Responder al motor tras el *commit*, nunca dentro de la transacción** (un worker respondía antes
  de confirmar su escritura).
- **Lectores tolerantes** en cada adaptador: los contratos de terceros crecen.
- **Correspondencias que envejecen**: si cambia un mapeado o un perfil ya proyectado, lo grabado en
  Opera no se corrige solo. Hace falta la conciliación que el HLA ya preveía.
- **Pago en hotel por defecto** cuando no hay cobro: es una decisión de negocio a confirmar.
- **El MCP no ve la identidad del usuario**: aprobar un mapeado desde el agente exige nombre y
  confirmación explícita.

## Siguientes pasos

1. Conseguir código de hotel y permisos de la app; repetir el escenario contra el tenant **en un
   hotel de pruebas y con autorización expresa** para escribir (H5).
2. Llevar al motor las dos correcciones (`LOCK`, validación de la longitud de la descripción).
3. Desplegar en el clúster y probar el agente de mapeado con el LLM real.
4. Rotar el client secret de OHIP compartido durante la PoC.

## Después del cierre (2026-09-23 → 30)

### Qué se ha añadido

- **Tenant real (H12).** El conector escribe reservas y perfiles de huésped en XMAR. Las diferencias
  con las specs se corrigieron en el conector y se reprodujeron en `opera-mock`, que desde el
  2026-09-24 solo se usa en la batería local. Los interlocutores se importan de Opera al ERP en vez
  de crearse en Opera.
- **Maestro de clientes con Salesforce (H11)**: identidad al proyectar, limpieza y fusión en
  Salesforce, propagación al perfil de Opera; después, marcado por calidad del dato y eventos por
  Pub/Sub con redes diarias.
- **Front office del hotel (H13, H14 y siguientes)**, alimentado por lo que Opera tiene: check-in,
  check-out y no-show que registra el PMS; cargos al folio de Opera; walk-in que reserva el CRS;
  habitación lista según Opera; check-in forzado; avisos de recepción de cliente, reserva y agencia
  (servicio `notices`, desplegándose); auditoría de quién hizo qué sobre una reserva, en recepción y
  en el CRS (desplegándose).
- **IA**: agente de mapeado que propone todo lo pendiente, agentes que se llaman entre sí por A2A,
  guardarraíles por ruta, chat del control plane con su propio agente.
- **Operación**: consumo de las APIs de Salesforce y Opera en las homes y en Grafana (dashboard
  *External APIs* y alertas), documentación en `doc.ec1.mateu.io`.

El detalle, con sus PRs, está en el [plan](plan.md#después-de-h14-sin-número-de-hito).

### El coste, visto al final

A cómo lo describe quien dirigió la PoC:

- **Una primera versión de cada conector —Opera y Salesforce— en torno a una hora**, a partir del HLA
  y una instrucción («conecta Opera», «haz la conexión con Salesforce»): la IA entendió las APIs de
  OHIP y de Salesforce y su funcionamiento sin más indicaciones.
- **Toda la integración en torno a una semana** (22–28 de septiembre): los dos conectores, el
  mapeado, el MDM con Salesforce, el front office, las pantallas y Grafana.
- **Como referencia, en Viajes Urbis un conector costaba en torno a un mes de un desarrollador.**

Las horas por sesión no se llegaron a apuntar en [`cost-log.md`](cost-log.md); la comparación de
arriba es la estimación de quien dirigió el trabajo, no una medida.

### Lo que el tenant real ha cerrado, y lo que no

- **Se escribe y se relee en XMAR**: reserva con la versión del CRS en un UDF numérico, perfil de
  huésped, modificación, cancelación, check-in, check-out con su factura, no-show y cargos al folio.
- **Sigue sin configurar** en el tenant: las interfaces de las referencias externas en perfiles
  (OPERAWS-GEN01187). El usuario de integración no tiene cajero: se creó uno para la PoC
  (`69721441`), que el conector pasa en check-outs y folios; los depósitos siguen apagados.
- **Opera rechaza algunas reservas por falta de habitaciones** del tipo pedido (RSV00138): llegan como
  causa con nombre, como estaba previsto.

### Problemas nuevos

- **El motor**: el `LOCK` en PostgreSQL y la descripción larga que se saltaba al importar están
  corregidos en EventConductor 2.23.0/2.23.1; las definiciones ya no los rodean.
- **Una caída de Oracle (2026-09-29).** La API de reservas de OHIP UAT respondió 502 y después 500
  «Request failed while connecting to the downstream system» durante horas, desde ~01:35 (hora de
  Madrid). El token y otros módulos (configuración, perfiles) respondían bien: era el servicio de
  reservas de Oracle, no el cupo ni nada nuestro (sin ningún 429; nuestro ritmo, ~60 llamadas/hora).
  Los procesos reintentaron y avisaron en la bandeja sin perder ni duplicar nada.
- **El cupo de Salesforce se agotó (2026-09-28)**, y no por ec1: un `customer-mdm-service` local de
  días antes (`e2e/poc-acl-local`, poll cada 20 s, atascado en un cliente de prueba) compartía la org
  de 15.000 llamadas/24 h. Desde entonces el consumo de Salesforce y de Opera se ve en las homes de las
  consolas y en Grafana, con alertas al 80 %/95 %, por ritmo proyectado y por gasto de «otros».
- **Campos obligatorios del huésped por nacionalidad, edad u hotel.** OPERA Cloud no tiene una
  configuración estándar para ello: Page Composer permite un *Required* condicional en las pantallas
  de OPERA Cloud, pero es de la interfaz y no aplica a lo que entra por OHIP (check-in online, app,
  integraciones). Responde al «PDTE» de *Configuración de campos de Kardex* del AF PMS-CRM: esa
  configuración pertenece al Riu Front Office, que es quien captura los datos en todos los canales.
  Pendiente de confirmación por Oracle.

### Siguientes pasos (a 2026-09-30)

1. Terminar el despliegue de los avisos de reserva y agencia y de la auditoría de recepción.
2. Un solo despliegue de `ia-agent`: el canal y el agente por defecto viajando en cada petición
   (previsto, sin hacer).
3. Configurar en XMAR las interfaces de referencias externas en perfiles; decidir si se activan los
   depósitos.
4. Rotar el client secret de OHIP compartido durante la PoC.

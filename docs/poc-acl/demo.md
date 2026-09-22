# PoC ACL — guión de la demo

Borrador. Se completa bloque a bloque.

## 1. Arquitectura (diagramas del HLA)

Del contexto a lo concreto, y parando en lo que la PoC ha construido de verdad. Los diagramas son
los del HLA *CRS-PMS Integration - Solution* (sección entre corchetes).

| # | Diagrama | Sección del HLA | Qué contar | En la PoC |
| -: | :------- | :-------------- | :--------- | :-------- |
| 1 | Context Model AS-IS | Context | De dónde partimos | — |
| 2 | Context Model TO-BE | Context | Rumbo (CRS) ↔ integración ↔ Opera Cloud por OHIP; el maestro de interlocutores del lado ERP | `booking` hace de Rumbo, `partners` de maestro |
| 3 | Container Model TO-BE | Containers | Los servicios: ACL del CRS, mapeado, conector PMS, comunicación, motor | Los seis servicios, uno por contenedor |
| 4 | El modelo mental: dos planos | Arquitectura | Plano de datos (lo que fluye) y plano de control (quién lo gobierna) | Dos consolas: `ec1` y `console.ec1` |
| 5 | Grabar Reserva — System Model | Procesos | El camino de una reserva de punta a punta | «Proyectar reserva», completo |
| 6 | Proyectar una reserva (secuencia) | Procesos | Preparar → perfil → grabar con guarda de versión → anotar en el CRS | Igual, contra `opera-mock` |
| 7 | Un proceso bloqueado espera, no falla | Resiliencia | Causas en vez de errores: una causa, N procesos | `mapping-service`, pantalla Causes |
| 8 | Mapeado — System Model | Mapeado | Diccionario versionado, aprobación humana, propuesta del agente | Diccionario, Pending y el agente |
| 9 | Alta de una integración (secuencia) y ciclo de vida (estados) | Alta | Del registro a la activación, por puertas | `integrations-service` |

Fuera de la PoC, y conviene decirlo: la subida PMS → CRS (OOO, conciliación diaria) y el tenant
real de Opera.

## 2. Recorrido por las consolas

Dos consolas, una por plano (diagrama 4), mismo usuario `demo`. Cada servicio trae sus propias
pantallas y la consola las federa; cada consola tiene además su versión Redwood (`rw.` y
`rw-console.`), con los mismos backends.

**Plano de datos — `https://ec1.mateu.io`**: lo que usa el negocio.

| Menú | Pantallas | Qué enseñar |
| :--- | :-------- | :---------- |
| Booking | Bookings | El CRS simulado: una reserva con habitaciones, huéspedes, desglose diario y cobros |
| Partners | Partners | El maestro de interlocutores: turoperador, agencia, OTA, empresa; *Resync* |
| Opera | Reservations, Profiles, Calls, Faults, Properties | El doble de Opera: lo que «ha llegado», cada llamada OHIP, fallos a demanda |
| Admin | Processes, Executions, Tasks… | Los procesos del motor, con sus pasos |

**Plano de control — `https://console.ec1.mateu.io`**: lo que gobierna la plataforma.

| Menú | Pantallas | Qué enseñar |
| :--- | :-------- | :---------- |
| Integrations | Integrations | Una integración por hotel: su conexión con Opera y en qué puerta del alta está |
| Mapping | Causes, Pending, Dictionary, Partner profiles | Por qué esperan los procesos; el diccionario y su aprobación |
| Notifications | History, Recipients | Los avisos que ha mandado la integración y a quién |
| Workflow | Definitions, Analytics | Las definiciones de los procesos (`alta-integracion`, `proyectar-*`) |
| IA | Agents, Mcp servers, Routes… | El agente de mapeado y los MCP de cada servicio |
| Usuarios | — | Quién puede hacer qué |

## 3. Un hotel con 100 reservas

En *Booking → Bookings* (plano de datos), el hotel X tiene 100 reservas a futuro: distintas llegadas,
canales (web, call centre, turoperador, OTA), interlocutores, regímenes y algunas con depósito. Es
el punto de partida: un hotel que ya vende en el CRS y que todavía no está integrado con Opera.

> **Preparación:** script de datos que crea esas 100 reservas en un hotel sin integración
> (`e2e/poc-acl-demo/seed.py`, pendiente).

## 4. Crear la integración del hotel X

En *Integrations → Integrations* (plano de control), **New**: hotel del CRS, propiedad de Opera,
gateway OHIP, app key, client id, secreto y enterprise. Al guardarla arranca el proceso
`alta-integracion` (se ve en *Admin → Processes*) y la integración avanza sola por sus primeras
puertas: verifica la conexión con Opera y contrasta los catálogos. En el detalle se ve en qué
puerta está («Waiting for») y el historial de lo que ha ido pasando.

Mientras no esté activa, nada del hotel X llega a Opera en tiempo real: sus reservas esperan en la
causa `INTEGRATION_INACTIVE/X` (*Mapping → Causes*).

## 5. El proceso de alta en marcha

El proceso `alta-integracion` avanza por sus puertas, y en el detalle de la integración se ve cada
una (*Waiting for* y el historial), en *Admin → Processes* el proceso con sus pasos, y en
*Notifications → History* los avisos cuando algo necesita a una persona:

1. **Conectividad** con Opera: token y lectura de la propiedad.
2. **Contraste de catálogos**: qué tiene configurado la propiedad en Opera frente a lo que el CRS
   puede emitir.
3. **Mapeado**: los códigos del hotel sin equivalencia (*Mapping → Pending*, con propuesta del
   agente) y la aprobación de una persona.
4. **Interlocutores**: los que usan las reservas pendientes del hotel, proyectados como perfiles en
   Opera (*Opera → Profiles*).
5. **Backfill**: pasada previa (lo que esas reservas usan y aún falta), y volcado de las 100
   reservas de la llegada más próxima a la más lejana (*Opera → Reservations* se va llenando), con la
   disponibilidad de la propiedad suspendida mientras dura.
6. **Lista para activar** cuando el volcado cubre la ventana próxima.

## 6. Se bloquea: faltan mapeados

El alta no falla: **espera**, y dice por qué. Los códigos del hotel X no tienen equivalencia en
Opera, así que:

- La integración se queda en `MAPPING_PENDING` (*Waiting for: A person to approve the mapping*), con
  los códigos pendientes en *Mapping → Pending*.
- Si se aprueba sin completarlo, la **pasada previa del backfill** lo para en `BACKFILL_BLOCKED`, con
  los huecos que usan de verdad sus reservas **ordenados por cuántas bloquean** (en el detalle de la
  integración, *Backfill gaps*). No se lanzan cien procesos para que se queden esperando: se
  arreglan antes unas pocas líneas del diccionario.
- Los interlocutores cuyo tipo no está mapeado esperan en su causa (*Mapping → Causes*).
- En *Notifications → History* están los avisos a los responsables.

## 7. Los mapeados que ha propuesto la IA

Al llegar a la puerta de mapeado, el alta ha pedido al **agente de mapeado** una propuesta. En el
plano de control, *Mapping → Dictionary*: las propuestas del agente, cada una con su **confianza** y
el **porqué**, pendientes de revisión. Nada entra en vigor sin una persona.

## 8. Validarlos desbloquea el alta

Se aprueban (o se corrigen) las propuestas. Cuando el hotel ya no tiene códigos pendientes, la
puerta de mapeado se abre sola y el alta sigue: **se proyectan los interlocutores** que usan sus
reservas (*Opera → Profiles* los muestra con su tipo de perfil), y las causas que los retenían se
resuelven (*Mapping → Causes*).

## 9. El backfill

Pasada previa limpia → volcado de las 100 reservas, **de la llegada más próxima a la más lejana**, a
un ritmo limitado. *Opera → Reservations* se va llenando, y el detalle de la integración muestra el
avance (*Backfill*: n de 100, ventana cubierta) y la disponibilidad suspendida mientras dura.

## 10. En Opera están los datos

En el plano de datos, *Opera* (el doble de OHIP: **no se escribe en el tenant real**, y conviene
decirlo):

- *Reservations*: las 100 reservas, con los códigos ya traducidos (tipo de habitación, tarifa,
  origen y mercado, régimen como paquete), tarifa fija por noche, el perfil del huésped y el del
  interlocutor, la ventana de folio del interlocutor cuando paga él, y la versión del CRS en el UDF.
- *Profiles*: huéspedes e interlocutores.
- *Calls*: cada llamada OHIP que ha hecho el conector, con su respuesta.

Y en el CRS (*Booking*), cada reserva sabe dónde ha quedado en Opera (referencia del PMS).

## 11. La IA crea reservas y viajan al PMS

Con la integración activa, en el chat de la consola de datos: *«Crea 5 reservas en el hotel X para
la semana que viene…»*. El agente las crea en el CRS con las herramientas MCP de `booking`, y cada
una viaja sola a Opera por «Proyectar reserva»: se ve en *Admin → Processes*, en
*Opera → Reservations* y en la referencia del PMS de cada reserva en *Booking*.

## Preparación de la demo

- [ ] Script de datos: 100 reservas a futuro del hotel X.
- [ ] Comprobar que el agente de la consola (`console-agent`, creado a mano en el control plane) tiene
      el MCP de `booking`, y que la clave del LLM responde.
- [ ] Probar el agente de mapeado de punta a punta en el clúster (nunca se ha probado con el LLM real).

# PoC ACL — registro de coste

Una entrada por sesión de trabajo. **Pieza** permite separar el coste del conector OHIP
(`pms-integration-service`) del resto, que es lo que pide el AC.

- **IA**: qué se hizo con asistencia de IA (generación, revisión, investigación).
- **Manual**: qué se hizo a mano.
- **Bloqueos / retrabajo**: esperas por terceros, cosas que hubo que rehacer y por qué.

| Fecha | Hito | Pieza | Horas | IA | Manual | Bloqueos / retrabajo |
| :---- | :--- | :---- | ----: | :- | :----- | :------------------- |
| 2026-09-22 | — | Plan | | Lectura del HLA, análisis del repo y del motor, borrador del plan | Decisiones de alcance (sin CDC, Property APIs, `partners`, MCP, notificaciones) | |
| 2026-09-22 | — | Tenant Opera | | Prueba de token OAuth y lectura de sus claims | Obtener credenciales | Falta el código de hotel: las Property APIs responden 403 con `RIUC` / `RIUE` |
| 2026-09-22 | H1 | CRS simulado (`booking`) | | Modelo (habitaciones, huéspedes, desglose diario, cobros, versión), catálogo y tarificación, eventos de dominio y outbox, API REST y OpenAPI, MCP, UI de Mateu, 27 tests (dominio + Postgres/Redpanda con Testcontainers), smoke de la UI con Playwright | Revisión | UI: `@Action` sin `@Toolbar` no se pinta; la insignia de `Status` a null muestra su plantilla; `@Section` sobre un campo oculto pierde el encabezado. Testcontainers 1.20 no habla con Docker 29 (hay que usar 1.21.4). El alta completa desde la UI no está automatizada |
| 2026-09-22 | H2 | `integration-model`, `partners`, `crs-integration-service` | | Modelo canónico y eventos de negocio; maestro de interlocutores (dominio, outbox, REST, MCP, UI, datos de ejemplo); ACL del CRS: inbox, relectura, traducción, router evento → proceso, worker de anotación, API canónica; tests con Testcontainers (2 + 5) | Revisión | El `ObjectMapper` de estos servicios rechaza campos desconocidos: los adaptadores usan un lector tolerante. Disco al 93%: Redpanda rechaza escrituras y los tests fallan con `BrokerNotAvailable`; datos de Redpanda a tmpfs. Diseño: el motor no admite ciclos (suspensión rediseñada) y `LOCK` exige motor 2.17+ (se sube a 2.18.0) |
| 2026-09-22 | H3 | `mapping-service` | | Diccionario cadena + propiedad versionado con aprobación; correspondencia interlocutor → perfil; causas con procesos en espera, liberación, reenvío de la señal y relanzamiento; workers `prepare-*`, `record-partner-profile`, `resolve-projection`, `relaunch`; REST, MCP para el agente, UI (causas, pendientes, diccionario, perfiles), petición de propuesta a `ia-agent`; 5 tests | Revisión | Sin ciclos en el motor: suspensión como espera única + relanzamiento. Carrera aprobación/registro de la espera cerrada revisando tras registrar. El MCP no ve la identidad del usuario: aprobar por MCP exige nombre y confirmación explícita |
| 2026-09-22 | H4 | `pms-integration-service` (conector OHIP) | | Estudio de las specs públicas de OHIP (rsv, crm, csh, rmcfg, rtp, rsvcfg, lov); cliente OHIP (token, cabeceras, clasificación de errores); catálogo, perfiles, reserva con guarda de versión en UDF, depósitos, cancelación; lock por reserva; aviso de reintento prolongado | Revisión | Multi-habitación simplificado a una reserva con varias tarifas. OHIP exige `x-hotelid` incluso en perfiles de cadena. `rsv` revalida (códigos, perfil, disponibilidad): los rechazos son causas |
| 2026-09-22 | H4 | `opera-mock`, definiciones, e2e local | | Doble de OHIP con validación, fallos inyectados y UI; tres definiciones; entorno local con el orquestador real y escenario de 7 pasos | Revisión | **Motor 2.18.0: `LOCK` falla en PostgreSQL** (clave con NUL) → sin LOCK, lock en el conector. **Descripción de definición > 255 caracteres: el motor la salta al importar y el validador no avisa**. `JOIN` tras `CHOICE` se descarta → una espera por punto de suspensión. Spring AI descarta en silencio `@Tool` que devuelven `Object` (afectaba a `getBooking` de H1). Pasos resueltos por nombre en `mapping` (el test no lo cubría). `booking` respondía al motor dentro de la transacción |

## Resumen (al cerrar)

| Pieza | Horas | % IA | Notas |
| :---- | ----: | ---: | :---- |
| `pms-integration-service` (conector OHIP) | | | |
| Resto de la integración | | | |
| CRS simulado (`booking`, `partners`) | | | |
| Total | | | |

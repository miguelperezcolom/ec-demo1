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

## Resumen (al cerrar)

| Pieza | Horas | % IA | Notas |
| :---- | ----: | ---: | :---- |
| `pms-integration-service` (conector OHIP) | | | |
| Resto de la integración | | | |
| CRS simulado (`booking`, `partners`) | | | |
| Total | | | |

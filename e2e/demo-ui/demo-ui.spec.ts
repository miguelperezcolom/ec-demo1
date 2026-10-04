import { test } from '@playwright/test'

/**
 * WIP — paused until the pending UI changes are deployed (ec-demo1 #218 rate-plan screen, the Demo
 * page's state-aware actions, task Claim/Release from eventconductor #452, the Mateu Redwood/Vaadin
 * batches). Selectors get validated against the live UI after that deploy.
 *
 * The plan, step by step (test.describe.serial: a failure stops the chain), with what the
 * 2026-10-04 UI-only run used (/tmp/drun, screenshots in ec-demo1-ux/demo-run-2026-10-04):
 *
 *  0  reset — CTRL/demo/admin, oj-c-menu-button → menuitem «Resetear la demo…» → #mateuConfirmAccept;
 *     CTRL/inbox/pending row «Confirmar el reset de la demo» → «Open» (same row) → /forms/task/<id>;
 *     «Claim» → tick «Sí, resetear la demo…» → «Complete»; Demo page lists steps until notify-result «hecho».
 *  1  onboarding — DATA/booking/bookings «+ 10 reservas demo» → confirm «Crear»;
 *     CTRL/integrations/frontoffice «New» → Opera property XMAR, Front office MRU01 → Save → poll
 *     «A person to activate it» → ⋯ Activate → confirm; CTRL/integrations/registry «New» → selects
 *     MRU01, XMAR → Save → poll «A person to approve the mapping»; CTRL/mapping/dictionary?integration=MRU01
 *     «Ask the agent» → poll no «Unmapped» → ⋯ Approve (no selection = all) → confirm; registry/MRU01
 *     poll «A person to activate it» → ⋯ Activate. PMS_REJECTED (RSV00138) causes: change the room
 *     type in the booking edit (Redwood: pencil on the room row → «Room type code»), retry with the
 *     next candidate (JS-SIDESEA, JS-STD, JS-SEA…), then Resolve on CTRL/mapping/causes/<key> if still open.
 *  2  returning customer — wizard (⋯ New on bookings) for an existing holder, same phone, other email
 *     → duplicate contact; FO «Escanear» on the ORIGINAL booking's holder, then on the NEW one →
 *     CTRL/customers/consolidations shows vía SCAN; FO shows the same customer code on both.
 *  3  FO data change — FO stay → holder «A mano»/«Editar» → phone → «Guardar kárdex» → «Pendiente de
 *     Salesforce»; DATA/customers/changes shows the request with a Salesforce Case id. Approval: the
 *     Salesforce UI needs MFA; the cluster secret ec-salesforce (SF_DOMAIN/SF_CLIENT_ID/SF_CLIENT_SECRET,
 *     client credentials, as e2e/demo/clients.ts and deploy/demo/ec1.py use) allows PATCH
 *     Case.Decision__c = 'Aprobada' — a clearly labelled test fixture; skip if unavailable.
 *  4  no show — booking arriving today; FO «No show» on each pax → CRS Cancelled (NOS fee), FO no show.
 *  5  walk-in — FO /reservas «Walk-in» → Inicio → room/rate/board → Continuar ×2 → holder → «Confirmar walk-in».
 *  6  Opera outage — Demo page «Aviso de reintentos tras (min)» = 2 → ⋯ «Aplicar el umbral de aviso»;
 *     ⋯ «Encender la caída de Opera»; new booking retries; inbox «Writing <loc> to the PMS keeps failing»;
 *     ⋯ «Apagar la caída de Opera»; booking gets its Opera number, written once.
 *  7  modify + cancel from the CRS — edit dates + room type, Save; ⋯ «Cancel booking» → reason OTR.
 *  8  new rate plan EMPLEADOS-27 — Call center → Catalogue → Rate plans (PR #218; skip if absent);
 *     booking waits MISSING_MAPPING; «Ask the agent»; select only the EMPLEADOS-27 proposal → Approve.
 */
test.describe.serial('demo, UI only', () => {
    test.fixme('0 reset from the Demo page', async () => {})
    test.fixme('1 onboarding MRU01 → XMAR', async () => {})
    test.fixme('2 returning customer, consolidated by scanning', async () => {})
    test.fixme('3 front office data change → Salesforce Case', async () => {})
    test.fixme('4 no show', async () => {})
    test.fixme('5 walk-in', async () => {})
    test.fixme('6 Opera outage', async () => {})
    test.fixme('7 modify and cancel from the CRS', async () => {})
    test.fixme('8 new rate plan EMPLEADOS-27', async () => {})
})

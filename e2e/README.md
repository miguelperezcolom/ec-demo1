# e2e

Playwright against the deployed cluster (ec1). Four suites:

| Command | What | Writes? |
|---|---|---|
| `npx playwright test` | every screen of the four consoles (`tests/`) | no — run it any time |
| `npm run demo` | the demo's storyline through the internal APIs (`demo/`), then a reset | yes |
| `DEMO_E2E_RESET=1 npm run demo:ui` | **the whole demo through the UI** (`demo-ui/`): reset + flows 1–8 | **wipes the demo data** |
| `npm run intranet` | the intranet (content-hub) on the **local** cluster (`intranet/`), Vaadin and Redwood | only its own «E2E …» content |

## The intranet (`intranet`)

The employee portal, the editorial backoffice, the visual editor, the requests workflow and the control
plane's intranet configuration, against the local kind cluster (`deploy/local/up.sh`, `*.localhost:8800`).
From `e2e/`:

```sh
npm ci                                   # once
npm run intranet                         # both shells (projects `vaadin` and `redwood`)
npm run intranet -- --project=vaadin     # one shell
npm run intranet -- requests             # one spec file
npx playwright show-report intranet-report
```

`intranet/run-local.sh` reads `DEMO_PASSWORD` and `TEST_USERS_PASSWORD` from
`~/.local/share/ec-demo1-local/credentials.env` (another file: `EC_DEMO1_CREDENTIALS=…`) and exports them as
`E2E_DEMO_PASSWORD` / `E2E_USERS_PASSWORD`, the only names the specs read. They are never printed or
written anywhere; to run Playwright directly, export those two yourself. `intranet/global-setup.ts` signs
every user in once, untraced, and keeps their Keycloak session cookies in `e2e/.intranet-auth/`
(gitignored), so the traces kept on failure never contain a typed password.

| Spec | Users | Covers |
|---|---|---|
| `portal.spec.ts` | ana.perez, klaus.schmidt | `/intranet` and `/` open the reader's home; the menu; a deep link with a dotted id (`/intranet/personas/ana.perez`); the news search box; audience (Ana sees the Canarias reception notice, Klaus does not); language (Klaus reads German) |
| `editorial.spec.ts` | demo | Contenidos; an item's edit form (rich-text body, «Insertar medio» / «Previsualizar» / «Traducir con IA» under it); «Insertar medio» shows the medium in the preview **without saving** (Cancel); the blocks table and its row editor (Quick links → only the links table) |
| `visual-editor.spec.ts` | demo | on a page it creates: a Banner's text typed in place, a Quick links block with a link, restricted to COCINA, save, reload, persisted; «Ver como» a DIRECCION profile hides that block |
| `requests.spec.ts` | ana.perez, carlos.diaz, isabel.torres | a vacation request typed `d/m/yyyy` → «Mis solicitudes» pending → Carlos approves with a comment → Isabel (HR) approves → Ana sees «Aprobada» with its history. Sends Ana the decision emails (redirected locally) |
| `control-plane.spec.ts` | demo | Intranet → «Menús» and «Menús: vista previa» — skipped until `feat/configurable-menus` is in master (`E2E_CONFIGURABLE_MENUS=1` runs them) |

**Safe to re-run on the shared demo.** It never deletes, never resets and never saves over seeded content
(editorial forms are left with Cancel). What it writes is its own, named «E2E …» with a run tag: one
article with a German translation for PMI01's front desk (created once, reused by later runs), one draft
page per run and shell for the visual editor, and one vacation request per run and shell in a far-future
year. Tests a product bug makes fail are marked `test.fail()` with the reason: they pass while the bug is
there and turn red when it is fixed, so the mark gets removed.

Hosts default to `http://{intranet,rw-intranet,ws,rw,console,rw-console,auth}.localhost:8800`;
`E2E_SCHEME`, `E2E_DOMAIN` or one `E2E_*_HOST` per host (see `intranet/support.ts`) point it elsewhere.
Redwood shells take 20–40 s to paint the first time: the waits allow 2 minutes.

## The demo through the UI (`demo-ui`)

One command, from `e2e/`:

```sh
set -a; . ../deploy/.secrets/credentials.env; set +a   # DEMO_PASSWORD (never printed)
DEMO_E2E_RESET=1 npm run demo:ui
```

It runs in its own config (`demo-ui.config.ts`, `testDir ./demo-ui`), so the normal suite never picks
it up, and it refuses to start without `DEMO_E2E_RESET=1`. It takes about an hour. Every checkpoint's
screenshot is attached to the report (`npx playwright show-report demo-ui-report`). The steps are
serial: when one fails, the steps after it are reported as not run, and the error says what never happened.

What it does, all through the consoles (Redwood by default):

0. **Reset** from the control plane's *Demo* page (⋯ «Resetear la demo…» → Sí), confirmed in the inbox
   task «Confirmar el reset de la demo» (Claim → «Sí, resetear…» → Complete); waits for the Demo page to
   show the reset done. The reset cancels in Opera (a shared UAT) the reservations the demo wrote, and nothing else, then moves to a new Opera context.
1. **Onboarding MRU01 → XMAR**: «+ 10 reservas demo», PMS → front office and CRS → PMS integrations, the
   dictionary («Ask the agent» + Approve), backfill, activate.
2. **Returning customer**: a new booking for the holder of a demo booking (same name and phone, another
   email) → a duplicate customer. Then, in the front office, «Escanear» on the holder of the ORIGINAL
   booking and then on the NEW one: the MDM consolidates them (Customers → Consolidations, vía SCAN) and
   merges the two Salesforce contacts itself. Asserts the consolidation and that both bookings end up
   with the same customer code. (The front office does not show the code on an arriving stay, so it is
   read on the CRS booking's «In other systems».)
3. **Front office data change** → «Pendiente de Salesforce» → a Case (Clientes → Solicitudes de cambio).
   The Case is approved by a **test fixture**: Salesforce's UI needs MFA, so the spec reads the cluster
   secret `ec-salesforce` with kubectl at runtime (never printed) and sets `Decision__c = Aprobada` as a
   steward would; then it waits for the decision to come back to the MDM and the front office. Without
   kubectl access the step is skipped, saying so, after asserting the Case exists.
4. **No show** of a booking arriving today. 5. **Walk-in** (the front office's guided process).
6. **Opera outage** from the Demo page (warning threshold 2 min, on, a booking retries, the inbox's
   RETRYING_TOO_LONG notice, off — always switched off, even on failure — written once).
7. **Modify and cancel** that booking from the CRS. 8. **New rate plan EMPLEADOS-27** (Call center →
   Catalogue → Rate plans; skipped with a message if the screen is not deployed), the booking waits
   MISSING_MAPPING, the agent proposes 432040HLXMU, only that one is approved.

**Opera's availability moves** (shared UAT): when Opera refuses a room type (RSV00138, a PMS_REJECTED
cause), the spec changes the booking's room type through its edit page and tries the next one — the
order is `DEMO_ROOM_TYPES` (default `JS-SEA,JS-SIDESEA,JS-STD,JS-ACCESSIBLE,JS-STD-KING`).

Environment:

| Variable | Default | |
|---|---|---|
| `DEMO_E2E_RESET` | — | must be `1` |
| `DEMO_PASSWORD` | — | the Keycloak `demo` user's (from `deploy/.secrets/credentials.env`) |
| `DEMO_RENDERER` | `redwood` | `vaadin` switches the console hosts (best effort: written for Redwood) |
| `DEMO_DATA_HOST`, `DEMO_CONTROL_HOST`, `DEMO_FRONT_HOST` | `rw.ec1…`, `rw-console.ec1…`, `front.ec1…` | |
| `DEMO_ROOM_TYPES` | see above | the room types to try against RSV00138 |
| `DEMO_NAMESPACE` | `ec-demo1` | where the `ec-salesforce` secret is |

Selectors marked `// UNVERIFIED (probe denied)` were written from the source and the 2026-10-04 run's
page text, not checked against the deployed UI; each step's header comment lists them. The first real
run confirms them.

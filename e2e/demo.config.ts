import { defineConfig } from '@playwright/test'
import base from './playwright.config'

/**
 * The demo, end to end, against ec1 — not the screens (that is playwright.config.ts, safe to run any
 * time) but the storyline, which writes: one reservation and one guest profile in Opera (XMAR) per run,
 * and Cases in Salesforce. It follows the demo's rules — it only changes what it creates — and resets
 * ec1 to the demo's baseline at the end (deploy/demo/reset.sh; E2E_RESET=0 to leave it as it is).
 *
 *   npm run demo
 */
export default defineConfig({
    ...base,
    testDir: './demo',
    timeout: 600_000,
    globalSetup: './demo/setup.ts',
    globalTeardown: './demo/teardown.ts',
})

import { defineConfig } from '@playwright/test'
import base from './playwright.config'

/**
 * The demo run, through the UI only (Redwood consoles by default), from a reset to flow 8.
 *
 * It WIPES the demo data (the Demo page's reset), so it lives in its own config and directory and
 * refuses to start without DEMO_E2E_RESET=1. The normal suite (playwright.config.ts, testDir ./tests)
 * never picks it up.
 *
 *   DEMO_E2E_RESET=1 npm run demo:ui
 */
if (process.env.DEMO_E2E_RESET !== '1') {
    throw new Error('demo-ui wipes the demo data on ec1: set DEMO_E2E_RESET=1 to run it on purpose')
}

export default defineConfig({
    ...base,
    testDir: './demo-ui',
    // Redwood shells take 30–40 s to boot; integrations, the agent and Opera take minutes.
    timeout: 900_000,
    expect: { timeout: 90_000 },
    retries: 0,
    reporter: [['list'], ['html', { open: 'never', outputFolder: 'demo-ui-report' }]],
    use: {
        ...base.use,
        viewport: { width: 1920, height: 1080 },
        locale: 'es-ES',
        timezoneId: 'Europe/Madrid',
        screenshot: 'on',
        trace: 'retain-on-failure',
    },
})

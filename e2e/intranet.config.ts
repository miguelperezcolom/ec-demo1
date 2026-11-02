import { defineConfig } from '@playwright/test'
import base from './playwright.config'
import type { ShellOptions } from './intranet/support'

/**
 * The intranet (content-hub) end to end: the employee portal, the editorial backoffice, the visual
 * editor, the requests workflow and the control plane's configuration — against the LOCAL kind
 * cluster (deploy/local/up.sh, hosts *.localhost:8800) by default.
 *
 * Every test runs once per renderer: the `vaadin` and `redwood` projects differ only in the hosts.
 *
 *   ./intranet/run-local.sh                      # both shells
 *   ./intranet/run-local.sh --project=vaadin     # one
 *
 * It writes only what it creates itself (titles and details prefixed «E2E »), never deletes, and
 * never saves over seeded content: safe to re-run on the shared demo.
 */
export default defineConfig<ShellOptions>({
    ...base,
    testDir: './intranet',
    // every user signed in once, untraced (a trace keeps what was typed): see intranet/global-setup.ts
    globalSetup: './intranet/global-setup.ts',
    // A request goes through EventConductor and Kafka twice; Redwood shells take 20–40 s to boot.
    timeout: 300_000,
    expect: { timeout: 30_000 },
    workers: 1,
    fullyParallel: false,
    retries: 0,
    reporter: [['list'], ['html', { open: 'never', outputFolder: 'intranet-report' }]],
    use: {
        ...base.use,
        locale: 'es-ES',
        timezoneId: 'Europe/Madrid',
        actionTimeout: 30_000,
        navigationTimeout: 90_000,
    },
    projects: [
        { name: 'vaadin', use: { shell: 'vaadin' } },
        { name: 'redwood', use: { shell: 'redwood' } },
    ],
})

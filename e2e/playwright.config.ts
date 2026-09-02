import { defineConfig } from '@playwright/test'

/**
 * The suite runs against a DEPLOYED cluster, not against anything it starts itself.
 *
 * <p>That is deliberate and it is the whole point: what is being asserted is that the same
 * backends render through two different renderers on two different planes, and only a real
 * deployment has all four consoles, the gateway that routes them and the Keycloak that gates
 * them. A suite that booted its own apps would prove something about the apps and nothing about
 * the deployment.
 *
 * <p>Every host is overridable, so the same tests run against a staging cluster by exporting
 * four variables and nothing else.
 */
export default defineConfig({
    testDir: './tests',
    // The screens hit a real cluster over the internet; a listing's first search is a round trip
    // to PostgreSQL through a gateway, and the process list on this deployment is not small.
    timeout: 120_000,
    expect: { timeout: 30_000 },
    // Serial by default: the consoles share one Keycloak and one set of demo data, and a test
    // that creates a record while another counts rows is a flake nobody will reproduce.
    workers: 1,
    fullyParallel: false,
    retries: process.env.CI ? 1 : 0,
    reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
    use: {
        ignoreHTTPSErrors: false,
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure',
        viewport: { width: 1440, height: 900 },
    },
})

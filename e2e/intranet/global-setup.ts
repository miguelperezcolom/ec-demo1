import { chromium } from '@playwright/test'
import fs from 'node:fs'
import { AUTH_DIR, USERS, authFile, candidates, hostsFor } from './support'

/**
 * Signs every user of the suite in once, outside the tests, and keeps each one's Keycloak session
 * (its cookies) for the tests' contexts to start from.
 *
 * Why here: the tests record a trace on failure, and a trace keeps what was typed — a password typed
 * inside a test would end up in test-results/. Here nothing is traced; a test types a password only
 * if its session has expired meanwhile.
 */
export default async function globalSetup() {
    fs.mkdirSync(AUTH_DIR, { recursive: true })
    const portal = hostsFor('vaadin').portal
    const browser = await chromium.launch()
    try {
        for (const user of USERS) {
            const context = await browser.newContext()
            const page = await context.newPage()
            await page.goto(`${portal}/`, { waitUntil: 'domcontentloaded' })
            await page.locator('#username').waitFor({ timeout: 90_000 })
            let signedIn = false
            for (const password of candidates(user)) {
                await page.locator('#username').fill(user)
                await page.locator('#password').fill(password)
                await page.locator('#kc-login').click()
                signedIn = await page.waitForURL(u => !u.toString().includes('/realms/'), { timeout: 15_000 })
                    .then(() => true, () => false)
                if (signedIn) break
            }
            if (!signedIn) throw new Error(`Keycloak refused ${user} (E2E_USERS_PASSWORD / E2E_DEMO_PASSWORD)`)
            await context.storageState({ path: authFile(user) })
            await context.close()
        }
    } finally {
        await browser.close()
    }
}

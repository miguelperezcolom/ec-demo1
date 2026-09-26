import { test, expect } from '@playwright/test'
import { CONSOLES, inboxScreen, signIn, menuLabels, screenRendered } from './consoles'

/**
 * Every screen of every console, on both planes and through both renderers.
 *
 * <p>What each layer of this actually catches, since a suite that asserts everything usually
 * proves nothing:
 *
 * <ul>
 *   <li><b>Signing in</b> catches the Keycloak client not listing a console's host in its redirect
 *       URIs — which fails at the identity provider, before any of this deployment runs.</li>
 *   <li><b>The menu bar</b> catches a RemoteMenu whose path no longer matches the {@code @UI} the
 *       service declares. That renders an EMPTY menu rather than an error, so nothing else notices
 *       it.</li>
 *   <li><b>Each screen</b> catches three different things wearing the same face: a gateway route
 *       missing for a host, a Mateu route that resolves to "Not found." with an HTTP 200, and a
 *       component type the renderer does not cover — which Mateu paints as a placeholder rather
 *       than failing.</li>
 *   <li><b>The two planes</b> catch each other: Workflow and Forms exist on both and mean
 *       different things, so a screen appearing on the wrong one is a defect the console it is
 *       missing from cannot see.</li>
 * </ul>
 */
for (const console_ of CONSOLES) {

    test.describe(`${console_.name} (${console_.host})`, () => {

        test('signs in and mounts the shell', async ({ page }) => {
            await signIn(page, console_)
            expect(page.url()).toContain(console_.host)
        })

        test('mounts every menu the shell declares', async ({ page }) => {
            await signIn(page, console_)
            const labels = await menuLabels(page, { atLeast: console_.menus.length })

            const missing = console_.menus.filter(menu => !labels.some(l => l === menu))
            expect(missing,
                `${console_.name} is missing ${missing.join(', ')} — a RemoteMenu whose path does ` +
                `not match the @UI the service declares renders empty rather than failing. Saw: ` +
                `${labels.slice(0, 25).join(' | ')}`)
                .toEqual([])
        })

        // The inbox has no entry on the bar: the badge in the top bar is the way in. Its menu is
        // still declared, hidden, so a reload on the inbox resolves too — both are checked here.
        test('the inbox badge opens the inbox', async ({ page }) => {
            test.skip(console_.renderer === 'redwood', 'Redwood does not draw the header widgets yet; its inbox is a menu entry')
            await signIn(page, console_)
            const badge = page.locator('a', { hasText: /Inbox/ }).first()
            await expect(badge, `${console_.name} shows no inbox badge`).toBeVisible({ timeout: 60_000 })
            await badge.click()
            await expect
                .poll(async () => {
                    const { ok } = await screenRendered(page)
                    return ok && await page.locator('text=Mark as seen').count() > 0
                }, { message: 'the badge never opened the inbox', timeout: 60_000 })
                .toBe(true)
            await page.reload({ waitUntil: 'domcontentloaded' })
            await expect
                .poll(async () => await page.locator('text=Mark as seen').count() > 0,
                      { message: 'a reload on the inbox did not resolve it', timeout: 60_000 })
                .toBe(true)
        })

        for (const screen of console_.screens) {
            test(`${screen.menu} → ${screen.entry} renders`, async ({ page }) => {
                await signIn(page, console_)
                await page.goto(`https://${console_.host}${screen.route}`,
                                { waitUntil: 'domcontentloaded' })
                // The route load and its search are two round trips; the second is what fills the
                // grid, and asserting before it lands is the flakiest thing this suite could do.
                await expect
                    .poll(async () => (await screenRendered(page)).ok,
                          { message: `${screen.route} never rendered`, timeout: 60_000 })
                    .toBe(true)

                const { ok, why } = await screenRendered(page)
                expect(ok, `${console_.name} ${screen.route}: ${why}`).toBe(true)
            })
        }
    })
}

/**
 * The claim the Redwood consoles exist to make, asserted rather than assumed: the two renderers
 * are fed by the SAME backends, so the screens they reach are the same set. If this ever fails,
 * one of the two shells is mounting something the other is not — which means the difference
 * between them stopped being the pom.
 */
test('both renderers of a plane offer the same screens', async () => {
    // The one difference allowed, and on purpose: Redwood reaches the inbox from a menu entry,
    // because it draws no header widgets for the badge Vaadin has instead.
    const redwoodOnly = (route: string) => route === inboxScreen.route
    for (const plane of ['data', 'control'] as const) {
        const [vaadin, redwood] = CONSOLES.filter(c => c.plane === plane)
        expect(redwood.screens.map(s => s.route).filter(r => !redwoodOnly(r)).sort())
            .toEqual(vaadin.screens.map(s => s.route).sort())
        expect(redwood.menus.filter(m => m !== inboxScreen.menu).sort()).toEqual(vaadin.menus.slice().sort())
    }
})

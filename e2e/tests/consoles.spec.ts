import { test, expect } from '@playwright/test'
import { CONSOLES, signIn, menuLabels, screenRendered } from './consoles'

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
    for (const plane of ['data', 'control'] as const) {
        const [vaadin, redwood] = CONSOLES.filter(c => c.plane === plane)
        expect(redwood.screens.map(s => s.route).sort())
            .toEqual(vaadin.screens.map(s => s.route).sort())
        expect(redwood.menus.slice().sort()).toEqual(vaadin.menus.slice().sort())
    }
})

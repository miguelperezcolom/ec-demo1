import { test, expect } from './support'

/**
 * The control plane's intranet configuration (console.localhost / rw-console.localhost → Intranet), as
 * `demo` (ai-admin + content:admin). Read-only: the configured menu is never changed.
 *
 * «Menús» and «Menús: vista previa» come with the branch feat/configurable-menus. Until it is merged
 * into master these tests are skipped; set E2E_CONFIGURABLE_MENUS=1 to run them against a cluster
 * that already has it deployed.
 */
const MENUS = process.env.E2E_CONFIGURABLE_MENUS === '1'

test.describe('Control plane · Intranet', () => {

    test.skip(!MENUS, 'feat/configurable-menus is not merged into master (checked 2026-11-02): no «Menús» screens ' +
        'in master yet. E2E_CONFIGURABLE_MENUS=1 runs them anyway.')

    test('Menús lists the menu entries', async ({ as, hosts }) => {
        const demo = await as('demo')
        await demo.go(`${hosts.control}/intranet/menus`, /Menús/)
        // the seeded portal menu: some of its entries
        for (const entry of ['Inicio', 'Noticias', 'Avisos']) await demo.waitText(new RegExp(entry), `«Menús» does not list ${entry}`)
        await expect(demo.table()).toBeVisible()
    })

    test('Menús: vista previa renders the menu', async ({ as, hosts }) => {
        const demo = await as('demo')
        await demo.go(`${hosts.control}/intranet/menusVistaPrevia`, /vista previa/i)
        // as Ana: choose her, «Previsualizar» (read-only: nothing is saved)
        await demo.choose('employee', /Ana Pérez/)
        await demo.action(/^Previsualizar$/)
        for (const entry of ['Inicio', 'Noticias']) await demo.waitText(new RegExp(entry), `the preview does not draw ${entry}`)
        expect(await demo.text()).not.toMatch(/Not found\.|Página no encontrada|Page not found/)
    })
})

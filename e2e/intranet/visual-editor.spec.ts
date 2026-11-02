import { test, expect, RUN, Session } from './support'
import { createItem } from './content'

/**
 * The visual editor (/editorial/visual/{id}): a page edited as its readers will see it. On a page the
 * test creates itself («E2E visual …», a draft never published), so nothing seeded is touched.
 *
 * The editor is a web component (<ec-visual-editor>) with an open shadow root: Playwright's locators
 * pierce it, so roles and aria-labels reach inside.
 */
test.describe('Visual editor', () => {

    test('edit a banner in place, add quick links, save, reload; «Ver como» hides a block for another department',
        async ({ as, hosts, shell }) => {
        const demo = await as('demo')
        const title = `E2E visual ${RUN} ${shell}`
        const bannerText = `Texto del banner E2E ${RUN}`
        const linkLabel = `Enlace E2E ${RUN}`
        const linksTitle = `Enlaces E2E ${RUN}`

        const id = await createItem(demo, hosts, { type: /^Page$/, section: /^Operaciones$/, es: title })
        await openEditor(demo, hosts.data, id)
        const ve = demo.page.locator('ec-visual-editor')

        // 1. a Banner, its text typed on the page itself
        // (the «+ Añadir el primer bloque» button is named by its aria-label, like every «+»)
        await demo.click(ve.getByRole('button', { name: 'Añadir un bloque aquí' }).first(), 1_000)
        await demo.click(ve.getByRole('menuitem', { name: 'Banner', exact: true }), 2_500)
        const bannerBody = ve.locator('.blk').first().getByLabel('Contenido del bloque')
        await bannerBody.click()
        await demo.page.keyboard.press('ControlOrMeta+a')
        await demo.page.keyboard.type(bannerText, { delay: 20 })
        await expect(bannerBody).toContainText(bannerText)
        await expect(ve.getByText('● Cambios sin guardar')).toBeVisible()

        // 2. Quick links after it, with one link — restricted to the kitchen (COCINA)
        const adds = ve.getByRole('button', { name: 'Añadir un bloque aquí' })
        await demo.click(adds.last(), 1_000)
        await demo.click(ve.getByRole('menuitem', { name: 'Enlaces rápidos', exact: true }), 2_500)
        const panel = ve.getByRole('complementary', { name: 'Bloque seleccionado' })
        await expect(panel.getByRole('heading', { name: /Enlaces rápidos/ })).toBeVisible()
        await panel.getByLabel('Título', { exact: true }).fill(linksTitle)
        await demo.click(panel.getByRole('button', { name: '+ Enlace' }), 800)
        await panel.getByLabel('Texto del enlace 1').fill(linkLabel)
        await panel.getByLabel('URL del enlace 1').fill('/intranet/buscar')
        await panel.getByRole('group', { name: 'Departamentos' }).getByText(/^cocina$/i).click()
        await expect(panel.getByText(/Este bloque, además, solo:/)).toBeVisible()
        await demo.checkpoint('before-save')

        // 3. save, reload: it is all there
        await demo.click(ve.getByRole('button', { name: 'Guardar', exact: true }), 3_000)
        await expect(ve.getByText('● Cambios sin guardar')).toBeHidden({ timeout: 30_000 })
        await expect(ve.getByRole('alert')).toHaveCount(0)
        await openEditor(demo, hosts.data, id, true)
        await expect(ve.locator('.blk')).toHaveCount(2)
        await expect(ve.locator('.blk').first(), 'the banner text was not saved').toContainText(bannerText)
        const links = ve.locator('.blk').nth(1)
        await expect(links, 'the quick links block lost its restriction').toContainText(/solo .*COCINA/i)
        await links.click()
        await expect(panel.getByLabel('Título', { exact: true })).toHaveValue(linksTitle)
        await expect(panel.getByLabel('Texto del enlace 1')).toHaveValue(linkLabel)
        await expect(panel.getByLabel('URL del enlace 1')).toHaveValue('/intranet/buscar')

        // 4. «Ver como» a management (DIRECCION) profile: the kitchen-only block is struck out, the banner is there
        const viewAs = ve.locator('select.viewas')
        const option = await viewAs.locator('option').evaluateAll(
            os => os.map(o => o.textContent ?? '').find(t => /DIRECCION/.test(t)))
        expect(option, 'the directory has no DIRECCION profile to view the page as').toBeTruthy()
        await viewAs.selectOption({ label: option! })
        await expect(ve.getByText(/Así la ve el perfil/)).toBeVisible({ timeout: 30_000 })
        await expect(ve.locator('.blk.hidden'), 'the COCINA block is not hidden from a DIRECCION profile')
            .toContainText(`No lo ve este perfil: «${linksTitle}»`, { timeout: 30_000 })
        await expect(ve.locator('.blk:not(.hidden)').first()).toContainText(bannerText)
        await demo.checkpoint('view-as-direccion')
        await demo.click(ve.getByRole('button', { name: 'Volver a editar' }), 1_000)
    })
})

async function openEditor(s: Session, data: string, id: string, reload = false) {
    const ready = /La página la ven:/
    if (reload) await s.reload(ready)
    else await s.go(`${data}/editorial/visual/${id}`, ready)
}

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

    test('a row of two columns, 8 + 4, with blocks inside: saved, reloaded, and each in its place', async ({ as, hosts, shell }) => {
        const demo = await as('demo')
        const id = await createItem(demo, hosts, { type: /^Page$/, section: /^Operaciones$/, es: `E2E fila ${RUN} ${shell}` })
        await openEditor(demo, hosts.data, id)
        const ve = demo.page.locator('ec-visual-editor')
        const panel = ve.getByRole('complementary', { name: 'Bloque seleccionado' })

        await demo.click(ve.getByRole('button', { name: 'Añadir un bloque aquí' }).first(), 1_000)
        await demo.click(ve.getByRole('menuitem', { name: 'Fila', exact: true }), 1_500)
        await demo.click(panel.getByRole('button', { name: '8 + 4', exact: true }), 1_500)

        const row = ve.locator('.blk.ctr').first()
        const cells = row.locator('> .row > .cell:not(.add-cell)')
        await expect(cells).toHaveCount(2)
        // an HTML block in each column
        for (const [n, text] of [[0, `Izquierda ${RUN}`], [1, `Derecha ${RUN}`]] as const) {
            await demo.click(cells.nth(n).getByRole('button', { name: 'Añadir un bloque aquí' }).first(), 1_000)
            await demo.click(cells.nth(n).getByRole('menuitem', { name: 'HTML', exact: true }), 1_500)
            const body = cells.nth(n).getByLabel('Contenido del bloque')
            await body.click()
            await demo.page.keyboard.type(text, { delay: 10 })
        }
        const widths = async () => {
            const [a, b] = await Promise.all([cells.nth(0).boundingBox(), cells.nth(1).boundingBox()])
            expect(a!.y, 'side by side').toBeCloseTo(b!.y, -1)
            return a!.width / b!.width
        }
        expect(await widths(), '8 columns against 4').toBeGreaterThan(1.5)

        await demo.click(ve.getByRole('button', { name: 'Guardar', exact: true }), 2_500)
        await expect(ve.getByText('● Cambios sin guardar')).toBeHidden()
        await demo.page.reload({ waitUntil: 'domcontentloaded' })
        await expect(cells).toHaveCount(2, { timeout: 60_000 })
        await expect(cells.nth(0)).toContainText(`Izquierda ${RUN}`)
        await expect(cells.nth(1)).toContainText(`Derecha ${RUN}`)
        expect(await widths(), 'after the reload too').toBeGreaterThan(1.5)
    })

    test('the «+» offers each registered UI by its name, with a filter when there are many', async ({ as, hosts, shell }) => {
        const demo = await as('demo')
        const id = await createItem(demo, hosts, { type: /^Page$/, section: /^Operaciones$/, es: `E2E componentes ${RUN} ${shell}` })
        await openEditor(demo, hosts.data, id)
        const ve = demo.page.locator('ec-visual-editor')
        const add = ve.getByRole('button', { name: 'Añadir un bloque aquí' }).first()

        await demo.click(add, 1_000)
        await expect(ve.getByRole('menuitem', { name: 'Componente remoto', exact: true })).toHaveCount(0)
        await expect(ve.locator('.menu-filter')).toHaveCount(0)
        await demo.click(ve.getByRole('menuitem', { name: 'Riu Class', exact: true }), 2_500)
        const panel = ve.getByRole('complementary', { name: 'Bloque seleccionado' })
        await expect(panel.getByLabel('UI externa')).toHaveValue('riu-class')

        // Many entries: not in the seed, so the catalogue the editor holds grows here
        await ve.evaluate((el: any) => {
            for (let i = 0; i < 20; i++) el.state.catalog.components.push({ value: `c${i}`, label: `Módulo ${i}` })
        })
        await demo.click(ve.getByRole('button', { name: 'Añadir un bloque aquí' }).first(), 1_000)
        const filter = ve.getByRole('searchbox', { name: 'Filtrar los bloques' })
        await expect(filter).toBeFocused()
        await filter.fill('modulo 17')
        await expect(ve.locator('.menu button:not([hidden])')).toHaveText(['Módulo 17'])
        await filter.fill('zzz')
        await expect(ve.getByText('Nada coincide')).toBeVisible()
        await filter.fill('riu cl')
        await filter.press('Enter')
        await expect(ve.locator('.menu')).toHaveCount(0)
        await expect(panel.getByLabel('UI externa')).toHaveValue('riu-class')
    })

    test('one page after another from the list opens each, and the last «+» shows all its menu', async ({ as, hosts, shell }) => {
        const demo = await as('demo')
        // Its own pages: they are the newest, so they're on the list's first page whatever else there is
        const titles = [`E2E lista A ${RUN} ${shell}`, `E2E lista B ${RUN} ${shell}`]
        for (const es of titles) await createItem(demo, hosts, { type: /^Page$/, section: /^Operaciones$/, es })
        await demo.go(`${hosts.data}/editorial/visual`, new RegExp(titles[1]))
        const ve = demo.page.locator('ec-visual-editor')
        // The second one is what failed: the editor's code already there, alive before it knows its page
        for (const title of titles) {
            await demo.click(demo.page.getByText(title, { exact: true }).last(), 1_000)
            await expect(ve.getByRole('button', { name: 'Añadir un bloque aquí' }).first()).toBeVisible({ timeout: 30_000 })
            await expect(ve.getByText('Not Found')).toHaveCount(0)
            if (title === titles[0]) {
                await demo.page.goBack()
                await demo.waitText(new RegExp(titles[1]), 'the list did not come back')
            }
        }

        const adds = ve.getByRole('button', { name: 'Añadir un bloque aquí' })
        await adds.last().scrollIntoViewIfNeeded()
        await demo.click(adds.last(), 1_000)
        const cut = await ve.locator('.menu').evaluate(menu => {
            const box = menu.getBoundingClientRect()
            let bottom = window.innerHeight
            for (let n: Node | null = menu.parentNode; n; n = n instanceof ShadowRoot ? n.host : n.parentNode) {
                if (n instanceof Element && getComputedStyle(n).overflowY !== 'visible') {
                    bottom = Math.min(bottom, n.getBoundingClientRect().bottom)
                }
            }
            return box.bottom - bottom
        })
        expect(cut, 'pixels of the menu below what scrolls the page').toBeLessThanOrEqual(1)
    })

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
        const [ink, paper] = await ve.locator('.rt button').first().evaluate(b => {
            const style = getComputedStyle(b)
            return [style.color, style.backgroundColor]
        })
        expect(ink, 'the toolbar on a banner must not take the banner\'s white text').not.toBe(paper)

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

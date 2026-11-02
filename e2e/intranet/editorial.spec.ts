import { Locator } from '@playwright/test'
import { test, expect, Session } from './support'

/**
 * The editorial backoffice on the data plane (ws.localhost / rw.localhost → Editorial), as `demo`
 * (editor, publisher and admin). Nothing here is saved: every form is left with Cancel.
 */
test.describe('Editorial', () => {

    // A page with blocks of five types, seeded; never saved by this suite.
    const PAGE = 'portada-operaciones'
    // A notice whose body has no medium, seeded; never saved by this suite.
    const NOTICE = 'politica-datos'

    /** The number of media drawn as players or images in the page (the preview), through shadow roots. */
    const embeds = (s: Session) => s.page.evaluate(() => {
        let n = 0
        const walk = (root: ParentNode) => {
            for (const el of Array.from(root.querySelectorAll('*'))) {
                if (el.hasAttribute('data-ec-embed') || el.hasAttribute('data-ec-media')) n++
                const shadow = (el as HTMLElement).shadowRoot
                if (shadow) walk(shadow)
            }
        }
        walk(document)
        return n
    }).catch(() => -1)

    async function openEditForm(s: Session, data: string, id: string) {
        await s.go(`${data}/editorial/contenidos/${id}`, /Aplicado a los lectores/)
        await s.action(/^(Edit|Editar)$/)
        await expect.poll(() => s.page.url(), { message: 'Edit did not open the edit form' }).toMatch(new RegExp(`/${id}/edit$`))
        await expect(s.saveButton()).toBeVisible()
        await expect(s.cancelButton()).toBeVisible()
    }

    async function cancel(s: Session) {
        await s.click(s.cancelButton(), 1_000)
        await s.confirmIfAsked()
        await expect.poll(() => s.page.url(), { message: 'Cancel did not leave the edit form' }).not.toMatch(/\/edit$/)
    }

    test('Contenidos lists the items', async ({ as, hosts }) => {
        const demo = await as('demo')
        await demo.go(`${hosts.data}/editorial/contenidos`, /Contenidos/)
        // newest first, ten a page: whatever is on the first page, it has rows in a state
        await demo.waitText(/Publicado|Borrador/, 'the listing shows no items (or its search failed)')
        const text = await demo.text()
        for (const column of ['Título', 'Tipo', 'Estado', 'Autor']) expect(text).toContain(column)
        await expect(demo.table()).toBeVisible()
        // and its search finds a seeded item wherever it is
        const box = demo.searchBox()
        await box.click()
        await box.pressSequentially('Bienvenidos', { delay: 50 })
        await box.press('Enter')
        await expect(demo.page.getByText('Bienvenidos a la nueva intranet').filter({ visible: true }).first(),
            'the listing\'s search does not find «Bienvenidos a la nueva intranet»').toBeVisible()
    })

    test('an item\'s edit form has the rich-text editor and, under it, the editor buttons', async ({ as, hosts }) => {
        const demo = await as('demo')
        await openEditForm(demo, hosts.data, PAGE)

        // the body is a rich-text editor (Tiptap in Vaadin, Spectra's CKEditor or Mateu's own in Redwood),
        // not a textarea of HTML
        const body = demo.field('bodyEs')
        await body.scrollIntoViewIfNeeded()
        await expect(body.locator('[contenteditable="true"]').first(), 'the body is not a rich-text editor').toBeVisible()

        const title = await demo.field('titleEs').boundingBox()
        for (const name of [/^Insertar medio$/, /^Previsualizar$/, /^Traducir con IA$/i]) {
            const b = demo.button(name)
            await expect(b, `no ${name} button on the edit form`).toBeVisible()
            const box = await b.boundingBox()
            expect(box && title && box.y > title.y, `${name} is not under the form`).toBeTruthy()
        }
        await cancel(demo)
    })

    test('«Insertar medio» shows the medium in the preview without saving', async ({ as, hosts }) => {
        const demo = await as('demo')
        await openEditForm(demo, hosts.data, NOTICE)
        await demo.waitText(/Medio de la biblioteca para insertar/, "no «Medios y vista previa» on the edit form")
        const before = await embeds(demo)

        await demo.choose('medioAInsertar', /Bienvenida del CEO/)
        await demo.click(demo.button(/^Insertar medio$/), 3_000)
        await expect.poll(() => embeds(demo), { message: 'the preview does not show the inserted medium', timeout: 30_000 })
            .toBeGreaterThan(before)
        // the marker went into the default language's body
        await expect(demo.field('bodyEs')).toContainText('[[media:mvideo0001]]')
        await demo.checkpoint('medium-in-preview')

        // nothing was saved: Cancel, and the item's page still shows the body as it was
        await cancel(demo)
        await demo.go(`${hosts.data}/editorial/contenidos/${NOTICE}`, /Aplicado a los lectores/)
        await demo.page.waitForTimeout(3_000)
        expect(await embeds(demo), 'the medium was saved into the seeded notice').toBe(before)
    })

    test('the blocks table shows type and title; «Edit» opens only the type\'s fields', async ({ as, hosts }) => {
        const demo = await as('demo')
        await openEditForm(demo, hosts.data, PAGE)
        await demo.page.getByText('Bloques (solo páginas)').filter({ visible: true }).last().scrollIntoViewIfNeeded()
        // type and title only, one row per block (the table is the only place these pairs are drawn)
        for (const cell of ['Banner', 'Esta semana en la cadena', 'Quick links', 'Enlaces rápidos', 'Automatic listing', 'Últimas noticias']) {
            await expect(demo.page.getByText(cell, { exact: true }).filter({ visible: true }).first(),
                `the blocks table has no «${cell}»`).toBeVisible()
        }
        await expect(demo.page.getByText(/Contenido \(incrusta medios/).filter({ visible: true }),
            'the blocks table shows the HTML column').toHaveCount(0)

        await demo.click(await editOfRow(demo, 'Enlaces rápidos'), 3_000)
        // The row editor: a dialog (Redwood; Vaadin until Mateu beta.4x) or a panel beside the table
        // (Vaadin since). Either way it is titled «Edit block».
        await expect(demo.page.getByText(/^(Edit block|Editar bloque)$/).filter({ visible: true }).first(),
            'Edit did not open the row editor').toBeVisible()
        const dialog = demo.page.getByRole('dialog').filter({ hasText: /Edit block|Editar bloque/ })
        const inDialog = await dialog.count() > 0
        const editor = inDialog ? dialog.last() : demo.page.locator('body')
        await demo.checkpoint('quick-links-row-editor')
        // the links, as a table of Texto / URL (with the language when blocks are translated)…
        await expect(editor.getByText('Enlaces', { exact: true }).first(), 'the Quick links editor has no «Enlaces»').toBeVisible()
        await expect(editor.getByText(/^Texto( \(.+\))?$/).first(), 'the links are not a table with Texto / URL').toBeVisible()
        await expect(editor.getByText(/^URL( \(.+\))?$/).first()).toBeVisible()
        await expect(editor.getByText('Portal del empleado').first(), 'the seeded link is not in the table').toBeVisible()
        // …and none of the other types' fields
        for (const other of [/Item destacado/, /Sección listada/, /^Cuántos$/, /Contenido( \(.+\))? \(incrusta medios/, /^Medio de la biblioteca$/]) {
            await expect(editor.getByText(other).filter({ visible: true }), `the Quick links editor shows ${other}`).toHaveCount(0)
        }
        if (inDialog) await demo.click(editor.getByRole('button', { name: /^(Cancel|Cancelar)$/ }).first(), 1_000)
        await cancel(demo)
    })
})

/** The «Edit» button on the same row as `text` (by height: Vaadin's grid cells are not nested in rows). */
async function editOfRow(s: Session, text: string): Promise<Locator> {
    const cell = s.page.getByText(text, { exact: true }).filter({ visible: true }).first()
    await cell.scrollIntoViewIfNeeded()
    const box = await cell.boundingBox()
    if (!box) throw new Error(`no row «${text}» in the table`)
    const edits = s.page.getByRole('button', { name: /^(Edit|Editar)$/ })
    const n = await edits.count()
    for (let i = 0; i < n; i++) {
        const b = await edits.nth(i).boundingBox()
        if (b && Math.abs(b.y + b.height / 2 - (box.y + box.height / 2)) < 25) return edits.nth(i)
    }
    throw new Error(`the row «${text}» has no Edit button`)
}

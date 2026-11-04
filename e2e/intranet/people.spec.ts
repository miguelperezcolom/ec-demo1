import type { Page } from '@playwright/test'
import { test, expect } from './support'

/**
 * Personas: where human resources keep the employees. Only for people:edit, and with no roles or groups on it,
 * which stay in Usuarios on the control console. It writes nothing: the rules behind it are unit tests
 * (PersonAdministrationTest), and a person made here would stay in Keycloak.
 */
test.describe('Personas', () => {

    const openMenu = (page: Page) => page.getByRole('button', { name: /^(Secciones|Menú)$/ }).first().click()
    const ENTRY = /(^| \| )Personas( \| |$)/

    test('who holds people:edit has the menu, its form and the link from a person\'s card', async ({ as, hosts }) => {
        const demo = await as('demo')
        await demo.go(`${hosts.data}/personas/empleados`, /carlos\.diaz|Carlos Díaz/)
        await demo.go(`${hosts.data}/personas/empleados/carlos.diaz/edit`, /Responsable/)
        const form = await demo.text()
        for (const field of ['Departamento', 'Pasar todas a']) expect(form).toContain(field)
        for (const field of ['Roles', 'Groups']) expect(form, 'no way to give access from here').not.toContain(field)

        await demo.go(`${hosts.data}/intranet/personas/carlos.diaz`, /Editar esta persona/)
        await openMenu(demo.page)
        await demo.waitText(ENTRY, 'no «Personas» in the first-level menu')
    })

    test('anybody changes their own photo, and it shows on their card', async ({ as, hosts }) => {
        const klaus = await as('klaus.schmidt')
        // a small PNG; the server draws it again as its own square JPEG
        const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')
        await klaus.go(`${hosts.data}/miFoto`, /Guardar mi foto/)
        const [chooser] = await Promise.all([
            klaus.page.waitForEvent('filechooser'),
            klaus.page.getByRole('button', { name: /^(Subir|Sustituir|Reemplazar)$/ }).first().click(),
        ])
        await chooser.setFiles({ name: 'foto.png', mimeType: 'image/png', buffer: png })
        await klaus.page.waitForTimeout(1_000)
        await klaus.click(klaus.page.getByRole('button', { name: 'Guardar mi foto' }).first(), 2_000)
        await klaus.waitText(/Foto guardada/, 'the photo was not saved')

        // the directory gets it through Kafka
        const photoOnScreen = () => klaus.page.evaluate(() => {
            let found = false
            const walk = (n: any) => {
                if (n.shadowRoot) walk(n.shadowRoot)
                for (const k of n.children ?? []) {
                    if ((k.tagName === 'IMG' && k.src.includes('/_people/photos/')) || getComputedStyle(k).backgroundImage.includes('/_people/photos/')) found = true
                    walk(k)
                }
            }
            walk(document.body)
            return found
        })
        const shows = async () => {
            await klaus.page.goto(`${hosts.data}/intranet/personas/klaus.schmidt`, { waitUntil: 'domcontentloaded' })
            await klaus.waitText(/Cambiar mi foto/, 'no way to one\'s own photo from one\'s own card')
            await klaus.page.waitForTimeout(2_000)
            return photoOnScreen()
        }
        await expect.poll(shows, { timeout: 90_000, intervals: [5_000] }).toBe(true)

        // next to their comment: the seeded one on the welcome article, with no address written as text
        await klaus.go(`${hosts.data}/intranet/noticias/bienvenida`, /Sehr gut/)
        await klaus.page.waitForTimeout(2_000)
        expect(await photoOnScreen(), 'no photo next to their comment').toBe(true)
        expect(await klaus.text(), 'the address written in the avatar').not.toContain('_people')

        // and in the directory, on the row found by searching for them
        await klaus.go(`${hosts.data}/intranet/personas`, /Personas/)
        // the listing's own box, not the shell's hidden «Buscar o ir a…»
        const search = klaus.page.getByPlaceholder(/^Buscar…?$/).first()
        await search.fill('schmidt')
        await search.press('Enter')
        await expect.poll(photoOnScreen, { timeout: 15_000, message: 'no photo in the directory' }).toBe(true)

        // and it can be taken away again: nothing of this stays
        await klaus.go(`${hosts.data}/miFoto`, /Guardar mi foto/)
        // opening it shows the one there is, and saving nothing new doesn't lose it
        await klaus.click(klaus.page.getByRole('button', { name: 'Guardar mi foto' }).first(), 2_000)
        await klaus.waitText(/Elige una foto/, 'saving nothing new should only say so')
        expect(await shows(), 'saving nothing new must not lose the photo').toBe(true)
        await klaus.go(`${hosts.data}/miFoto`, /Guardar mi foto/)
        await klaus.click(klaus.page.getByRole('button', { name: 'Quitar mi foto' }).first(), 1_000)
        await klaus.click(klaus.page.getByRole('button', { name: /^(Sí|Yes)$/ }).first(), 2_000)
        await klaus.waitText(/Foto quitada/, 'the photo was not removed')
    })

    test('anybody else has none of it', async ({ as, hosts }) => {
        const ana = await as('ana.perez')
        await ana.go(`${hosts.data}/intranet/personas/carlos.diaz`, /Carlos Díaz/)
        expect(await ana.text()).not.toContain('Editar esta persona')
        await openMenu(ana.page)
        await ana.waitText(/(^| \| )Intranet( \| |$)/, 'the first-level menu did not open')
        expect(await ana.text(), 'a reader has «Personas» in the menu').not.toMatch(/(^| \| )Empleados( \| |$)/)

        await ana.page.goto(`${hosts.data}/personas/empleados`, { waitUntil: 'domcontentloaded' })
        await ana.page.waitForTimeout(15_000)
        expect(await ana.text()).not.toContain('maria.santana@example.com')
    })
})

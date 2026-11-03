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

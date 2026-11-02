import { test, expect, pageText } from './support'
import { ensureGermanItem, GERMAN_TITLE, SPANISH_TITLE } from './content'

/**
 * The employee portal on its own host (intranet.localhost / rw-intranet.localhost): what a reader
 * sees, decided by who they are.
 */
test.describe('Portal', () => {

    const MENU = ['Inicio', 'Noticias', 'Avisos', 'Documentos', 'Secciones', 'Personas', 'Organigrama', 'Buscar',
                  'Pendientes', 'Nueva solicitud', 'Mis solicitudes', 'Por aprobar', 'Encuestas', 'Mis preferencias']

    // The home greets the reader by name; the section index the shell draws for a bare /intranet is a
    // list of the same entries as links, without any of it.
    const HOME = /Hola, Ana Pérez/

    test('/intranet opens the reader\'s home, not a list of links', async ({ as, hosts }) => {
        const ana = await as('ana.perez')
        await ana.page.goto(`${hosts.portal}/intranet`, { waitUntil: 'domcontentloaded' })
        await ana.waitText(/Mis preferencias/, 'the shell never painted its menu', 120_000)
        await ana.waitText(HOME, '/intranet did not show Ana\'s home (greeting «Hola, Ana Pérez») — the menu index instead?', 60_000)
        // the home's own blocks, which the index of links does not have
        await ana.waitText(/Pendiente de confirmar/, 'the home has no «Pendiente de confirmar» block')
    })

    test('/ opens the reader\'s home', async ({ as, hosts }) => {
        const ana = await as('ana.perez')
        await ana.go(`${hosts.portal}/`, HOME)
        expect(ana.page.url()).toContain('/intranet/inicio')
        // Ana's home is the Canary Islands front desk one (the first home of the list she can see)
        await ana.waitText(/Recepción · Canarias/, 'Ana did not get the «Recepción · Canarias» home')
    })

    test('the menu has the portal\'s entries', async ({ as, hosts }) => {
        const ana = await as('ana.perez')
        await ana.go(`${hosts.portal}/intranet/inicio`, HOME)
        const text = await ana.text()
        const missing = MENU.filter(m => !text.includes(m))
        expect(missing, `menu entries missing: ${missing.join(', ')}`).toEqual([])
    })

    test('a deep link with a dotted id renders the person\'s card', async ({ as, hosts }) => {
        const ana = await as('ana.perez')
        // /intranet/personas/ana.perez typed in the address bar: the dot once made it look like a file
        await ana.go(`${hosts.portal}/intranet/personas/ana.perez`, /Recepcionista/)
        const text = await ana.text()
        expect(text).toContain('Ana Pérez')
        expect(text).toContain('ana.perez@example.com')
        expect(text, 'the card names her manager').toContain('Carlos Díaz')
        expect(text).not.toMatch(/Page not found|Not found\./)
    })

    test('the news search box accepts typing', async ({ as, hosts }) => {
        const ana = await as('ana.perez')
        await ana.go(`${hosts.portal}/intranet/noticias`, /Bienvenidos a la nueva intranet/)
        const box = ana.page.locator('vaadin-text-field input, oj-input-text input, oj-c-input-text input, input[type=search], input[type=text]')
            .filter({ visible: true }).first()
        await box.click()
        const nothing = 'zzqx sin resultados'
        await box.pressSequentially(nothing, { delay: 60 })
        // the value stays as typed: a box that re-renders under the cursor loses keystrokes
        await expect(box).toHaveValue(nothing)
        await box.press('Enter')
        // and it searches: the welcome article goes. (Vaadin turns the text into a «Text: …» chip and
        // empties the box; Redwood keeps it in the box.)
        // (visible: the grid keeps its old rows' cells in the DOM)
        await expect(ana.page.getByText('Bienvenidos a la nueva intranet').filter({ visible: true }),
            `the search for «${nothing}» did not filter the news`).toHaveCount(0)
        const kept = (await ana.text()).includes(nothing) || (await box.inputValue().catch(() => '')) === nothing
        expect(kept, 'the search text is still on screen (in the box or as a chip)').toBe(true)
    })

    test('audience: Ana (front desk, Canarias) sees the Canarias shift notice', async ({ as, hosts }) => {
        const ana = await as('ana.perez')
        await ana.go(`${hosts.portal}/intranet/avisos`, /Política de protección de datos 2026/)
        await ana.waitText(/Nuevo cuadrante de turnos de recepción en Canarias/, 'Ana does not see the Canarias notice')
    })

    test('audience: Klaus (front desk, Mallorca) does not see it', async ({ as, hosts }) => {
        const klaus = await as('klaus.schmidt')
        // the listing has loaded when a notice for everyone is in it
        await klaus.go(`${hosts.portal}/intranet/avisos`, /Política de protección de datos 2026/)
        await klaus.page.waitForTimeout(2_000)
        expect(await klaus.text()).not.toContain('Nuevo cuadrante de turnos de recepción en Canarias')
        // nor through the search, nor by its address
        await klaus.go(`${hosts.portal}/intranet/noticias/turnos-canarias`, /Intranet/)
        await klaus.page.waitForTimeout(5_000)
        expect(await klaus.text()).not.toContain('Los turnos de noviembre cambian')
    })

    test('language: Klaus reads in German what has a German translation', async ({ as, hosts }) => {
        // an article of the suite's own, with es and de, for the reception of PMI01 (Klaus's) only
        await ensureGermanItem(as, hosts)
        const klaus = await as('klaus.schmidt')
        await klaus.go(`${hosts.portal}/intranet/noticias`, /Bienvenidos a la nueva intranet|Willkommen/)
        await expect.poll(async () => {
            const t = await pageText(klaus.page)
            return t.includes(GERMAN_TITLE) ? 'de' : t.includes(SPANISH_TITLE) ? 'es' : 'none'
        }, { message: 'Klaus should read the article in German', timeout: 90_000, intervals: [5_000] }).toBe('de')
    })
})

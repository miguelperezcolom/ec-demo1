import { test, expect, RUN, Session } from './support'

/**
 * Requests (EventConductor process `solicitud-empleado`): Ana asks for holidays, her manager Carlos
 * approves with a comment, HR (Isabel) approves, and Ana sees it approved with its history.
 *
 * Every run asks for its own far-future dates and a detail «E2E <run>», so runs never meet. The
 * decision sends Ana an email, which the local demo redirects to one mailbox.
 */
test.describe('Requests', () => {

    test('vacation request: Ana → Carlos (manager) → Isabel (HR) → approved', async ({ as, hosts, shell }) => {
        test.setTimeout(600_000)
        // far-future, and different on every run: d/m/yyyy as a Spanish user types it (no zero padding),
        // with a day over 12 so that the date only reads one way (15/2/2041, never «15th month»)
        const seed = Date.now()
        const year = 2040 + (seed % 50)
        const month = 1 + (Math.floor(seed / 50) % 9)
        const day = 13 + (Math.floor(seed / 450) % 8)
        const from = `${day}/${month}/${year}`
        const to = `${day + 5}/${month}/${year}`
        const iso = (d: number) => `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
        const dates = `${iso(day)} → ${iso(day + 5)}`
        const detail = `E2E ${RUN} ${shell}: vacaciones de prueba`
        const comment = `Visto bueno E2E ${RUN}`

        // 1. Ana asks
        const ana = await as('ana.perez')
        await ana.go(`${hosts.portal}/intranet/nuevaSolicitud`, /Qué pides/)
        await ana.typeDate('desde', from)
        await ana.typeDate('hasta', to)
        await ana.fill('detalle', detail)
        await ana.checkpoint('new-request')
        await ana.action(/^Enviar$/)
        await ana.waitText(/Solicitud enviada a tu responsable/, 'the request was not sent')
        const sent = /Síguela en «Mis solicitudes» \(([^)]+)\)/.exec(await ana.text())
        expect(sent, 'the confirmation names the request').toBeTruthy()
        const requestId = sent![1]

        // 2. it is in «Mis solicitudes», pending
        await ana.go(`${hosts.portal}/intranet/solicitudes`, new RegExp(dates))
        await ana.go(`${hosts.portal}/intranet/solicitudes/${requestId}`, new RegExp(escape(detail)))
        expect(await ana.text(), 'the dates typed as d/m/yyyy were not read as such').toContain(dates)
        expect(await ana.text()).toMatch(/Pendiente/)

        // 3. Carlos, her manager, approves with a comment
        const carlos = await as('carlos.diaz')
        await approve(carlos, hosts.portal, dates, comment, /Responsable/)

        // 4. Isabel, HR, approves — the task reaches her once Carlos's decision went through the process
        const isabel = await as('isabel.torres')
        await approve(isabel, hosts.portal, dates, undefined, /RRHH/)

        // 5. Ana sees it approved, with its history: who decided and Carlos's comment
        await expect.poll(async () => {
            await ana.go(`${hosts.portal}/intranet/solicitudes/${requestId}`, new RegExp(escape(detail)))
            return /Aprobada/.test(await ana.text())
        }, { message: 'Ana never saw the request approved', timeout: 180_000, intervals: [10_000] }).toBe(true)
        const page = await ana.text()
        expect(page).toContain('Historial')
        expect(page, 'the history has the manager\'s comment').toContain(comment)
        await ana.checkpoint('approved')
        await ana.go(`${hosts.portal}/intranet/solicitudes`, new RegExp(dates))
        expect(await ana.text()).toMatch(/Aprobada/)
    })
})

/**
 * «Por aprobar» → the row of these dates → comment → Aprobar. The task appears when the process
 * creates it (Kafka, the engine): the listing is reloaded until it does.
 */
async function approve(s: Session, portal: string, dates: string, comment: string | undefined, role: RegExp) {
    await expect.poll(async () => {
        await s.go(`${portal}/intranet/porAprobar`, /Por aprobar/)
        await s.page.waitForTimeout(2_000)
        return (await s.text()).includes(dates)
    }, { message: `${s.user} never got the request of ${dates} in «Por aprobar»`, timeout: 180_000, intervals: [10_000] })
        .toBe(true)
    expect(await s.text(), `${s.user} should approve it as ${role}`).toMatch(role)
    await s.click(s.page.getByText(dates, { exact: true }).filter({ visible: true }).first(), 3_000)
    await s.waitText(/Comentario para el empleado/, `${s.user} could not open the request`)
    if (comment) {
        await s.fill('comentario', comment)
    }
    await s.checkpoint('approving')
    await s.action(/^Aprobar$/)
    await s.confirmIfAsked()
    await s.waitText(/Aprobada: pasa a RRHH|Aprobada\. Se le comunica al empleado/, `${s.user}'s approval was not acknowledged`)
}

function escape(text: string) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

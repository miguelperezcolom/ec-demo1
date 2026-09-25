import { test, expect, Page } from '@playwright/test'
import { CONSOLES, Console, signIn } from '../tests/consoles'
import { API } from './env'
import { api, until, Salesforce, Opera } from './clients'

/**
 * The demo's storyline on ec1, as docs/poc-acl/demo.md tells it, checked where the demo shows it:
 * in Opera, in the front office, in Salesforce and on the consoles. It only changes what it creates
 * (a new reservation of MRU01, and the change on its holder) — the demo's rule, so that a reset,
 * which never touches Opera, still leaves Opera consistent.
 */
test.describe.configure({ mode: 'serial' })

const RUN = `E2E-${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 12)}`
const control = CONSOLES.find(c => c.name === 'control · vaadin')!
const frontOffice: Console = { name: 'front office', host: process.env.FRONT_HOST ?? 'front.ec1.mateu.io',
    plane: 'data', renderer: 'redwood', menus: [], screens: [] }
const salesforce = new Salesforce()
const opera = new Opera()

let locator: string
let operaId: string
let customerId: string

/** The reservation's guest profile, where the connector looks for it: reservationGuests[].profileInfo. */
function guestProfileId(node: any): string | undefined {
    if (!node || typeof node !== 'object') return undefined
    for (const guest of node.reservationGuests ?? []) {
        const id = guest?.profileInfo?.profileIdList?.[0]?.id
        if (id) return id
    }
    for (const value of Object.values(node)) {
        const found = guestProfileId(value)
        if (found) return found
    }
    return undefined
}

/** The reservation's nightly rate lines, wherever the response nests them. */
function roomRates(node: any): any[] {
    if (!node || typeof node !== 'object') return []
    if (Array.isArray(node.roomRates)) return node.roomRates
    for (const value of Object.values(node)) {
        const found = roomRates(value)
        if (found.length) return found
    }
    return []
}

/** Everything the page shows, through the shadow roots both renderers use. */
async function pageText(page: Page): Promise<string> {
    // A page still navigating (a redirect, the shell mounting) has no text yet: the poll asks again.
    return page.evaluate(() => {
        let text = ''
        const walk = (root: ParentNode) => {
            for (const el of Array.from(root.querySelectorAll('*'))) {
                if (el.children.length === 0) text += ' ' + (el.textContent ?? '').trim()
                const shadow = (el as HTMLElement & { shadowRoot?: ShadowRoot }).shadowRoot
                if (shadow) walk(shadow)
            }
        }
        walk(document)
        return text
    }).catch(() => '')
}

async function shows(page: Page, url: string, ...texts: string[]) {
    await page.goto(url, { waitUntil: 'domcontentloaded' })
    await expect.poll(async () => { const t = await pageText(page); return texts.every(x => t.includes(x)) },
        { message: `${url} never showed ${texts.join(', ')}`, timeout: 90_000 }).toBe(true)
}

test('a new reservation of MRU01 reaches Opera and the front office', async ({ page }) => {
    const arrival = new Date(Date.now() + (90 + Math.floor(Math.random() * 60)) * 86_400_000)
    const departure = new Date(arrival.getTime() + 2 * 86_400_000)
    const day = (d: Date) => d.toISOString().slice(0, 10)
    const created = await api(API.booking, 'POST', '/bookings', { hotelCode: 'MRU01', booking: {
        channelCode: 'WEB', externalReference: RUN, arrival: day(arrival), departure: day(departure),
        holder: { firstName: 'Demo', lastName: RUN, email: `${RUN.toLowerCase()}@example.com`, nationality: 'ES' },
        rooms: [{ roomTypeCode: 'JSU', ratePlanCode: 'BAR', boardCode: 'SA', adults: 2, childrenAges: [],
                  guests: [{ firstName: 'Demo', lastName: RUN, type: 'Adult' }] }] } })
    locator = created.id

    // The CRS learns where it landed in Opera.
    operaId = await until(`${locator} never reached Opera`, async () =>
        (await api(API.booking, 'GET', `/bookings/${locator}`)).pmsReference?.reservationId)
    const inOpera = await opera.get('XMAR', `/rsv/v1/hotels/XMAR/reservations/${operaId}`)
    expect(JSON.stringify(inOpera)).toContain(RUN)

    // The front office has it as a stay, its holder a guest of the cardex by the MDM's code.
    const stay = await until(`${locator} never reached the front office`, () => api(API.frontOffice, 'GET', `/api/reservations/${locator}`))
    customerId = stay.guestId
    expect(customerId).toMatch(/^C-/)

    // In the desk's listing. (A link straight to /reservas/<locator> does not render the reservation:
    // it only opens by navigating inside the app.)
    await signIn(page, frontOffice)
    await page.goto(`https://${frontOffice.host}/reservas`, { waitUntil: 'domcontentloaded' })
    const search = page.locator('input:visible').first()
    await search.waitFor({ timeout: 60_000 })
    await search.fill(locator)
    await search.press('Enter')
    await expect.poll(async () => { const t = await pageText(page); return t.includes(locator) && t.includes(RUN) },
        { message: `the front office's listing never showed ${locator}`, timeout: 90_000 }).toBe(true)
})

test("the holder's change is decided in Salesforce and reaches the front office and Opera", async () => {
    const email = `${RUN.toLowerCase()}.nuevo@example.com`
    // What the front office sends when the desk changes the holder.
    const request = await api(API.mdm, 'POST', `/customers/${customerId}/change-requests`,
        { email, origin: `front office MRU01 (${RUN})` })
    expect(request.status).toBe('PENDING')

    const caseId = await until('the change never became a Case in Salesforce', async () =>
        (await api(API.mdm, 'GET', `/change-requests/${request.id}`)).salesforceCaseId)
    const opened = await salesforce.call('GET', `/sobjects/Case/${caseId}`)
    expect(opened.Decision__c).toBe('Pendiente')
    expect(opened.Email__c).toBe(email)

    // A steward approves it.
    await salesforce.call('PATCH', `/sobjects/Case/${caseId}`, { Decision__c: 'Aprobada' })

    await until('the MDM never learnt the decision', async () =>
        (await api(API.mdm, 'GET', `/change-requests/${request.id}`)).status === 'APPROVED')
    await until('the front office never got the new email', async () =>
        (await api(API.frontOffice, 'GET', `/api/guests/${customerId}`)).email === email)
    // Opera: the guest profile, the email changed in its entry — one primary, the new one.
    await until("Opera's guest profile never got the new email", async () => {
        const profileId = guestProfileId(await opera.get('XMAR', `/rsv/v1/hotels/XMAR/reservations/${operaId}`))
        if (!profileId) return false
        const profile = await opera.get('XMAR', `/crm/v1/profiles/${profileId}?fetchInstructions=Profile&fetchInstructions=Communication`)
        const emails = profile.profileDetails?.emails?.emailInfo ?? []
        return emails.filter((e: any) => e.email.primaryInd).map((e: any) => e.email.emailAddress).includes(email)
    })
})

test("a no-show: the CRS cancels it with its fee, and what it costs reaches the front office and Opera", async () => {
    const before = await api(API.booking, 'GET', `/bookings/${locator}`)
    // What the front office does when the last guest of the reservation is marked as a no-show.
    const reported = await api(API.crs, 'POST', '/no-shows', { hotelCode: 'MRU01', locator, reportedBy: `front office MRU01 (${RUN})` })
    expect(reported.status).toBe('REPORTED')

    // The CRS: cancelled as a no-show (NOS), costing 25% of its original price.
    const booking = await until('the CRS never cancelled it as a no-show', async () => {
        const b = await api(API.booking, 'GET', `/bookings/${locator}`)
        return b.status === 'Cancelled' && b.cancellation?.reasonCode === 'NOS' ? b : undefined
    })
    const fee = Number(booking.cancellation.fee)
    expect(Number(booking.originalAmount)).toBeCloseTo(Number(before.totalAmount), 2)
    expect(fee).toBeCloseTo(Number(before.totalAmount) * 0.25, 2)
    expect(Number(booking.totalAmount)).toBeCloseTo(fee, 2)

    // The front office: the stay is a no-show, and costs the fee.
    await until('the front office never showed the no-show', async () => {
        const stay = await api(API.frontOffice, 'GET', `/api/reservations/${locator}`)
        return stay.status === 'NO_SHOW'
    })
    // Opera: cancelled with the No Show reason, its rates brought down to the fee.
    await until("Opera's reservation never became a cancelled no-show costing the fee", async () => {
        const reservation = await opera.get('XMAR', `/rsv/v1/hotels/XMAR/reservations/${operaId}`)
        const text = JSON.stringify(reservation)
        const nights = roomRates(reservation).reduce((sum: number, r: any) => sum + Number(r.total?.amountBeforeTax ?? 0), 0)
        return text.includes('"Cancelled"') && text.includes('"code":"NOSHOW"') && Math.abs(nights - fee) < 0.01
    })
})

test('an auditable action is in the audit trail', async ({ page }) => {
    const integration = (await api(API.integrations, 'GET', '/integrations')).find((i: any) => i.crsHotelCode === 'MRU01')
    await api(API.integrations, 'POST', `/integrations/${integration.id}/recheck?by=${RUN}`)
    await signIn(page, control)
    await shows(page, `https://${control.host}/audit/actions`, RUN, 'Recheck')
})

test('the inbox shows what waits: the causes of the hotel with no integration', async ({ page }) => {
    await signIn(page, control)
    await shows(page, `https://${control.host}/inbox/pending`, 'CUN01')
})

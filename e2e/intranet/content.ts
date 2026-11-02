import { Hosts, Session, expect, pageText } from './support'

/**
 * The content the suite writes for itself, through the editorial UI (content-hub has no API to create
 * items): titles prefixed «E2E », never a seeded item.
 */

type As = (user: string) => Promise<Session>

/**
 * The German check needs an item with a German translation, and the seed has none. One article,
 * created once and reused by every later run (looked up by its title as Klaus sees it): es + de, for
 * the front desk of PMI01 only — Klaus's hotel — so nobody else's portal shows it.
 */
export const SPANISH_TITLE = 'E2E idioma: artículo en español'
export const GERMAN_TITLE = 'E2E Sprache: Artikel auf Deutsch'

export async function ensureGermanItem(as: As, hosts: Hosts) {
    const klaus = await as('klaus.schmidt')
    await klaus.go(`${hosts.portal}/intranet/noticias`, /Bienvenidos a la nueva intranet|Willkommen/)
    const seen = await klaus.text()
    if (seen.includes(GERMAN_TITLE) || seen.includes(SPANISH_TITLE)) return

    const demo = await as('demo')
    // created by an earlier run that stopped before publishing it? publish that one
    const id = await findItem(demo, hosts, SPANISH_TITLE) ?? await createItem(demo, hosts, {
        type: /^Article$/, section: /^Noticias$/,
        es: SPANISH_TITLE, summaryEs: 'Lo crea la suite e2e de la intranet para comprobar el idioma del lector.',
        de: GERMAN_TITLE, summaryDe: 'Von der E2E-Suite der Intranet angelegt, um die Sprache des Lesers zu prüfen.',
        hotels: ['PMI01'], departments: ['RECEPCION'],
    })
    await publish(demo, hosts, id)
    // the visibility is recomputed by events: a few seconds
    await expect.poll(async () => {
        await klaus.reload(/Bienvenidos a la nueva intranet|Willkommen/)
        const t = await pageText(klaus.page)
        return t.includes(GERMAN_TITLE) || t.includes(SPANISH_TITLE)
    }, { message: `Klaus never saw the article ${id} the suite published for him`, timeout: 120_000, intervals: [5_000] })
        .toBe(true)
}

export interface NewItem {
    type: RegExp
    section: RegExp
    es: string
    summaryEs?: string
    de?: string
    summaryDe?: string
    hotels?: string[]
    departments?: string[]
}

/** Editorial → Contenidos → New, filled and saved. Returns the new item's id (from the URL it lands on). */
export async function createItem(s: Session, hosts: Hosts, item: NewItem): Promise<string> {
    await s.go(`${hosts.data}/editorial/contenidos/new`, /Nuevo contenido/)
    await s.waitText(/Idioma por defecto/, 'the new item form never drew its fields')
    await s.choose('type', item.type)
    await s.choose('sectionId', item.section)
    await s.fill('titleEs', item.es)
    if (item.summaryEs) await s.fill('summaryEs', item.summaryEs)
    if (item.de) await s.fill('titleDe', item.de)
    if (item.summaryDe) await s.fill('summaryDe', item.summaryDe)
    for (const h of item.hotels ?? []) await s.check('hotels', h)
    for (const d of item.departments ?? []) await s.check('departments', d)
    await s.choose('notificar', /^No$/)
    await s.click(s.saveButton(), 4_000)
    // The crud opens the new item: /editorial/contenidos/{id}
    await expect.poll(() => s.page.url(), { message: 'saving the new item did not open it', timeout: 60_000 })
        .toMatch(/\/editorial\/contenidos\/(?!new)[^/?#]+$/)
    const id = s.page.url().split('/').pop()!
    await s.waitText(new RegExp(escape(item.es)), `the new item ${id} does not show its title`)
    return id
}

/** «Publicar» on an item's page, confirmed. */
export async function publish(s: Session, hosts: Hosts, id: string) {
    if (!s.page.url().endsWith(`/editorial/contenidos/${id}`)) {
        await s.go(`${hosts.data}/editorial/contenidos/${id}`, /Aplicado a los lectores/)
    }
    await s.action(/^Publicar$/)
    await s.confirmIfAsked()
    await s.waitText(/Publicado/, `${id} was not published`)
}

/** The id of the item titled `title` (exactly), through the listing's search; undefined if there is none. */
export async function findItem(s: Session, hosts: Hosts, title: string): Promise<string | undefined> {
    await s.go(`${hosts.data}/editorial/contenidos`, /Contenidos/)
    await s.waitText(/Publicado|Borrador/, 'the contents listing shows no items')
    const box = s.searchBox()
    await box.click()
    await box.pressSequentially(title, { delay: 20 })
    await box.press('Enter')
    // the row's Id column comes right before its title
    const idOf = new RegExp(`\\| ([\\w-]+) \\| ${escape(title)} \\|`)
    let id: string | undefined
    await expect.poll(async () => (id = idOf.exec(await s.text())?.[1]) !== undefined, { timeout: 15_000 })
        .toBe(true).catch(() => {})
    return id
}

export function escape(text: string) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

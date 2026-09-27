import { Page, expect } from '@playwright/test'

/**
 * The four consoles this deployment serves, and what each one is supposed to show.
 *
 * <p>Two PLANES and two RENDERERS. The planes differ in what they are for — running the product
 * versus administering the platform — and the deployment draws that line by host, with the gateway
 * requiring the `ai-admin` role on the control one. The renderers differ in nothing but which
 * Mateu frontend artifact the shell's pom depends on; the backends, the gateway routes and the
 * Keycloak clients are shared.
 *
 * <p>So the same expectations are asserted four times, and that repetition IS the test: a screen
 * that renders under Vaadin and not under Redwood is a renderer gap, and a screen that renders on
 * one plane and not the other is a routing or authorisation gap. Neither is visible from one
 * console alone.
 */

export type Plane = 'data' | 'control'

export interface Console {
    name: string
    host: string
    plane: Plane
    renderer: 'vaadin' | 'redwood'
    /** Top-level menu labels the shell mounts, in no particular order. */
    menus: string[]
    /** Every screen the console reaches, as the route a menu entry navigates to. */
    screens: Screen[]
}

/**
 * A screen, and how to tell it is THAT screen rather than merely a screen.
 *
 * <p>{@code title} is the heading the page shows when it defaults to the menu entry — compared
 * without case, so "Llms" and "LLMs" are the same screen. It is spelled out only where the page
 * names itself differently from the entry that opens it. {@code listing} (default true) also asks
 * for a table: every entry here is a listing except where it says otherwise.
 */
export interface Screen {
    menu: string
    entry: string
    route: string
    title?: string
    listing?: boolean
}

const host = (envVar: string, fallback: string) => process.env[envVar] ?? fallback

/** The data plane: what a person uses to get work done. */
const dataScreens: Screen[] = [
    // Workflow and Forms hang under Admin here — they are how the platform is driven,
    // where Booking is the product. The ROUTES are untouched by that grouping;
    // only where the entry sits in the bar changed.
    //
    // Steps and Tasks v 2 are gone from this plane on purpose: a step execution is diagnosis of
    // the engine, and two task lists side by side is a question the person using it cannot answer.
    // Both still resolve as routes, and WorkflowMenu/FormsMenu still carry them for embedders.
    { menu: 'Admin', entry: 'Processes', route: '/workflow/processes' },
    { menu: 'Admin', entry: 'Executions', route: '/forms/executions', title: 'Form executions' },
    { menu: 'Admin', entry: 'Tasks', route: '/forms/tasks' },
    { menu: 'Call center', entry: 'Bookings', route: '/booking/bookings' },
    // The CRS -> Opera integration PoC (docs/poc-acl): the partners master. The Opera double is no
    // longer deployed: the integration writes to the chain's real tenant.
    { menu: 'ERP', entry: 'Partners', route: '/partners/partners' },
]

/**
 * The control plane. Workflow and Forms appear on BOTH planes and mean different things: here they
 * are the definitions and the analytics, there they are the work in flight. Same pods, reached
 * through a second @UI each — which is exactly the kind of thing only an end-to-end test notices.
 */
const controlScreens: Screen[] = [
    { menu: 'Workflow', entry: 'Definitions', route: '/workflow/definitions', title: 'Workflow definitions' },
    // Charts and figures, not a listing: Vaadin happens to draw a grid in it and Redwood does not.
    { menu: 'Workflow', entry: 'Analytics', route: '/workflow/analytics', listing: false },
    { menu: 'Forms', entry: 'Forms', route: '/forms/forms' },
    // The IA pod serves its catalogues under /catalogues, whatever the shell's section is called.
    // These routes used to read /ia/..., which the pod does not have: the shell answered with its
    // own empty home and the suite, asking only that SOMETHING rendered, passed them.
    { menu: 'IA', entry: 'Agents', route: '/catalogues/agents' },
    { menu: 'IA', entry: 'Llms', route: '/catalogues/llms' },
    { menu: 'IA', entry: 'Mcp servers', route: '/catalogues/mcpServers' },
    // Beside the servers somebody else runs, not inside them: this catalogue owns its tool list
    // and that one deliberately owns none. Two screens because they are two aggregates.
    { menu: 'IA', entry: 'Api mcp servers', route: '/catalogues/apiMcpServers', title: 'APIs as MCP servers' },
    { menu: 'IA', entry: 'Rag sources', route: '/catalogues/ragSources' },
    { menu: 'IA', entry: 'Budgets', route: '/catalogues/budgets' },
    { menu: 'IA', entry: 'Routes', route: '/catalogues/routes' },
    // The integration PoC's operation: the hotels' integrations, mapping and causes, and the alerts.
    { menu: 'Integrations', entry: 'Integrations', route: '/integrations/registry' },
    { menu: 'Mapping', entry: 'Causes', route: '/mapping/causes' },
    { menu: 'Mapping', entry: 'Dictionary', route: '/mapping/dictionary' },
    { menu: 'Mapping', entry: 'Partner profiles', route: '/mapping/partnerProfiles', title: 'Partners in the PMS' },
    { menu: 'Customers', entry: 'Golden records', route: '/customers/golden' },
    { menu: 'Customers', entry: 'Consolidations', route: '/customers/consolidations' },
    { menu: 'Notifications', entry: 'History', route: '/notifications/history', title: 'Notifications' },
    { menu: 'Notifications', entry: 'Recipients', route: '/notifications/recipients' },
    { menu: 'Audit', entry: 'Audited actions', route: '/audit/actions' },
]

/**
 * The inbox as a menu entry — only in the Redwood shells, whose renderer does not draw the header
 * widgets yet: there the badge that replaced this entry is not shown, so the entry stays.
 */
export const inboxScreen: Screen = { menu: 'Inbox', entry: 'Pending', route: '/inbox/pending', title: 'Inbox' }

export const CONSOLES: Console[] = [
    {
        name: 'data · vaadin', plane: 'data', renderer: 'vaadin',
        host: host('CONSOLE_HOST', 'ec1.mateu.io'),
        menus: ['Admin', 'Call center', 'ERP'],
        screens: dataScreens,
    },
    {
        name: 'data · redwood', plane: 'data', renderer: 'redwood',
        host: host('RW_CONSOLE_HOST', 'rw.ec1.mateu.io'),
        menus: ['Admin', 'Call center', 'ERP', 'Inbox'],
        screens: [...dataScreens, inboxScreen],
    },
    {
        name: 'control · vaadin', plane: 'control', renderer: 'vaadin',
        host: host('CONTROL_HOST', 'console.ec1.mateu.io'),
        menus: ['IA', 'Usuarios', 'Workflow', 'Forms', 'Integrations', 'Mapping', 'Customers', 'Notifications', 'Audit'],
        screens: controlScreens,
    },
    {
        name: 'control · redwood', plane: 'control', renderer: 'redwood',
        host: host('RW_CONTROL_HOST', 'rw-console.ec1.mateu.io'),
        menus: ['IA', 'Usuarios', 'Workflow', 'Forms', 'Integrations', 'Mapping', 'Customers', 'Notifications', 'Audit', 'Inbox'],
        screens: [...controlScreens, inboxScreen],
    },
]

const USER = process.env.DEMO_USER ?? 'demo'
const PASSWORD = process.env.DEMO_PASSWORD ?? 'demo'

/**
 * Signs in through Keycloak and waits for the shell to be up.
 *
 * <p>Both planes use the same demo user, which carries all three realm roles. A real deployment
 * would split them; this one does not, and a test that assumed otherwise would be testing a
 * deployment nobody runs.
 */
export async function signIn(page: Page, console_: Console) {
    await page.goto(`https://${console_.host}/`, { waitUntil: 'domcontentloaded' })
    // The bootstrap page redirects to Keycloak on its own; give it the round trip.
    await page.waitForTimeout(3_000)
    if (page.url().includes('/realms/')) {
        await page.fill('#username', USER)
        await page.fill('#password', PASSWORD)
        await page.click('#kc-login')
    }
    await expect
        .poll(() => page.url(), { message: `never got back to ${console_.host}`, timeout: 60_000 })
        .toContain(console_.host)
    await menuLabels(page, { atLeast: 1 })
}

/**
 * The shell's top-level menu labels, read through whatever shadow roots the renderer used.
 *
 * <p>Piercing shadow DOM by hand rather than with a selector, because the two renderers nest their
 * components differently and a selector tuned to one silently returns nothing on the other — which
 * would make a renderer gap look like a passing test.
 */
export async function menuLabels(page: Page, opts: { atLeast: number }): Promise<string[]> {
    await expect
        .poll(async () => (await readMenuLabels(page)).length,
              { message: 'the shell never mounted a menu', timeout: 60_000 })
        .toBeGreaterThanOrEqual(opts.atLeast)
    return readMenuLabels(page)
}

const readMenuLabels = (page: Page) => page.evaluate(() => {
    const labels: string[] = []
    const walk = (root: ParentNode) => {
        for (const el of Array.from(root.querySelectorAll('*'))) {
            const tag = el.tagName.toLowerCase()
            if (tag.includes('menu') || tag.includes('tab') || tag === 'a' || tag === 'button') {
                const text = (el.textContent ?? '').trim()
                if (text && text.length < 30 && el.children.length === 0) labels.push(text)
            }
            const shadow = (el as HTMLElement & { shadowRoot?: ShadowRoot }).shadowRoot
            if (shadow) walk(shadow)
        }
    }
    walk(document)
    return Array.from(new Set(labels))
})

/**
 * Whether a screen actually rendered, rather than merely answering 200.
 *
 * <p>Mateu answers a route it cannot resolve with a fragment reading "Not found." and an HTTP 200,
 * so status codes prove nothing here. What proves it is a page that put something on screen and no
 * error banner on it.
 */
export async function screenRendered(page: Page): Promise<{ ok: boolean; why: string }> {
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
        if (/Not found\./.test(text)) return { ok: false, why: 'the route resolved to "Not found."' }
        // The renderer conformance suite paints this where a renderer does not cover a component
        // type. It is a legitimate render, but not a working screen, and it is the single most
        // useful thing this suite can report about Redwood.
        const unsupported = /not supported by (this|the) renderer|unsupported component/i.exec(text)
        if (unsupported) return { ok: false, why: `renderer placeholder: ${unsupported[0]}` }
        if (text.trim().length < 20) return { ok: false, why: 'the page rendered nothing' }
        return { ok: true, why: '' }
    })
}

/**
 * Whether the page on screen is the one that was asked for — its heading names it and, for a
 * listing, a table is there.
 *
 * <p>{@link screenRendered} alone accepts any page that paints something, and a route that lands on
 * the WRONG screen paints plenty: a deep link to /partners/partners that opened the ERP section
 * index (a heading and a link) passed it for as long as it was broken. Headings are read from both
 * renderers — Vaadin titles a page with an h2, Redwood with an h1 — and matched whole, without
 * case; the table is Vaadin's grid or JET's oj-table.
 */
export async function screenIs(page: Page, screen: Screen): Promise<{ ok: boolean; why: string }> {
    const title = (screen.title ?? screen.entry).toLowerCase()
    const listing = screen.listing ?? true
    // A page that is still navigating (the shell swapping in the remote, a token refresh) destroys
    // the context under evaluate(); that is "not yet", not "wrong screen", so the poll goes on.
    const seen = await page.evaluate(() => {
        const headings: string[] = []
        let table = false
        const walk = (root: ParentNode) => {
            for (const el of Array.from(root.querySelectorAll('*'))) {
                const tag = el.tagName.toLowerCase()
                if (/^h[1-6]$/.test(tag)) {
                    const text = (el.textContent ?? '').trim().replace(/\s+/g, ' ')
                    if (text) headings.push(text)
                }
                if (tag === 'vaadin-grid' || tag === 'oj-table') table = true
                const shadow = (el as HTMLElement & { shadowRoot?: ShadowRoot }).shadowRoot
                if (shadow) walk(shadow)
            }
        }
        walk(document)
        return { headings, table }
    }).catch(() => null)
    if (!seen) return { ok: false, why: 'the page was still navigating' }
    if (!seen.headings.some(h => h.toLowerCase() === title)) {
        return { ok: false, why: `no heading reads "${screen.title ?? screen.entry}" — saw ${seen.headings.map(h => `"${h}"`).join(', ') || 'none'}` }
    }
    if (listing && !seen.table) {
        return { ok: false, why: `"${screen.title ?? screen.entry}" is a listing and there is no table on screen` }
    }
    return { ok: true, why: '' }
}

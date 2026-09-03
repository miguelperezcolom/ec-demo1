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
    screens: { menu: string; entry: string; route: string }[]
}

const host = (envVar: string, fallback: string) => process.env[envVar] ?? fallback

/** The data plane: what a person uses to get work done. */
const dataScreens = [
    // Workflow, Forms and Worker hang under Admin here — they are how the platform is driven,
    // where Booking is the product. The ROUTES are untouched by that grouping;
    // only where the entry sits in the bar changed.
    //
    // Steps and Tasks v 2 are gone from this plane on purpose: a step execution is diagnosis of
    // the engine, and two task lists side by side is a question the person using it cannot answer.
    // Both still resolve as routes, and WorkflowMenu/FormsMenu still carry them for embedders.
    { menu: 'Admin', entry: 'Processes', route: '/workflow/processes' },
    { menu: 'Admin', entry: 'Executions', route: '/forms/executions' },
    { menu: 'Admin', entry: 'Tasks', route: '/forms/tasks' },
    { menu: 'Admin', entry: 'Received tasks', route: '/worker/receivedTasks' },
    { menu: 'Admin', entry: 'Task overrides', route: '/worker/taskOverrides' },
    { menu: 'Booking', entry: 'Bookings', route: '/booking/bookings' },
]

/**
 * The control plane. Workflow and Forms appear on BOTH planes and mean different things: here they
 * are the definitions and the analytics, there they are the work in flight. Same pods, reached
 * through a second @UI each — which is exactly the kind of thing only an end-to-end test notices.
 */
const controlScreens = [
    { menu: 'Workflow', entry: 'Definitions', route: '/workflow/definitions' },
    { menu: 'Workflow', entry: 'Analytics', route: '/workflow/analytics' },
    { menu: 'Forms', entry: 'Forms', route: '/forms/forms' },
    { menu: 'IA', entry: 'Agents', route: '/ia/agents' },
    { menu: 'IA', entry: 'Llms', route: '/ia/llms' },
    { menu: 'IA', entry: 'Mcp servers', route: '/ia/mcpServers' },
    // Beside the servers somebody else runs, not inside them: this catalogue owns its tool list
    // and that one deliberately owns none. Two screens because they are two aggregates.
    { menu: 'IA', entry: 'Api mcp servers', route: '/ia/apiMcpServers' },
    { menu: 'IA', entry: 'Rag sources', route: '/ia/ragSources' },
    { menu: 'IA', entry: 'Budgets', route: '/ia/budgets' },
    { menu: 'IA', entry: 'Routes', route: '/ia/routes' },
]

export const CONSOLES: Console[] = [
    {
        name: 'data · vaadin', plane: 'data', renderer: 'vaadin',
        host: host('CONSOLE_HOST', 'ec1.mateu.io'),
        menus: ['Admin', 'Booking'],
        screens: dataScreens,
    },
    {
        name: 'data · redwood', plane: 'data', renderer: 'redwood',
        host: host('RW_CONSOLE_HOST', 'rw.ec1.mateu.io'),
        menus: ['Admin', 'Booking'],
        screens: dataScreens,
    },
    {
        name: 'control · vaadin', plane: 'control', renderer: 'vaadin',
        host: host('CONTROL_HOST', 'console.ec1.mateu.io'),
        menus: ['IA', 'Usuarios', 'Workflow', 'Forms'],
        screens: controlScreens,
    },
    {
        name: 'control · redwood', plane: 'control', renderer: 'redwood',
        host: host('RW_CONTROL_HOST', 'rw-console.ec1.mateu.io'),
        menus: ['IA', 'Usuarios', 'Workflow', 'Forms'],
        screens: controlScreens,
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

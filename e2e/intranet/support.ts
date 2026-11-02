import { Browser, BrowserContext, Locator, Page, TestInfo, expect, test as base } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

/**
 * Helpers for the intranet suite (intranet.config.ts).
 *
 * Every test runs once per renderer. The two shells serve the SAME content-hub backend; only the
 * hosts change, so a test written once against roles, labels and Mateu's field ids holds for both:
 * Vaadin draws a field as `<mateu-field id="titleEs">`, Redwood as `[data-field-id="titleEs"]`.
 * Playwright's CSS engine pierces open shadow roots, so one selector covers both.
 */

export type Shell = 'vaadin' | 'redwood'
export interface ShellOptions { shell: Shell }

const SCHEME = process.env.E2E_SCHEME ?? 'http'
const DOMAIN = process.env.E2E_DOMAIN ?? 'localhost:8800'
const url = (envVar: string, sub: string) => `${SCHEME}://${process.env[envVar] ?? `${sub}.${DOMAIN}`}`

export interface Hosts {
    /** The intranet on a host of its own: the portal only. */
    portal: string
    /** The data plane: the portal (menu Intranet) and, for content:* holders, Editorial. */
    data: string
    /** The control plane: Intranet → configuration. */
    control: string
    /** Keycloak. */
    auth: string
}

export function hostsFor(shell: Shell): Hosts {
    return shell === 'vaadin'
        ? { portal: url('E2E_PORTAL_HOST', 'intranet'), data: url('E2E_DATA_HOST', 'ws'),
            control: url('E2E_CONTROL_HOST', 'console'), auth: url('E2E_AUTH_HOST', 'auth') }
        : { portal: url('E2E_RW_PORTAL_HOST', 'rw-intranet'), data: url('E2E_RW_DATA_HOST', 'rw'),
            control: url('E2E_RW_CONTROL_HOST', 'rw-console'), auth: url('E2E_AUTH_HOST', 'auth') }
}

// ── users ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * The passwords come from the environment only (intranet/run-local.sh sources them from
 * ~/.local/share/ec-demo1-local/credentials.env). They are never logged: a failed sign-in says
 * which user, never what was typed.
 */
export function candidates(user: string): string[] {
    const demo = process.env.E2E_DEMO_PASSWORD
    const users = process.env.E2E_USERS_PASSWORD
    if (!demo) throw new Error('E2E_DEMO_PASSWORD is not set — run through e2e/intranet/run-local.sh')
    if (user === 'demo') return [demo]
    if (!users) throw new Error('E2E_USERS_PASSWORD is not set — run through e2e/intranet/run-local.sh')
    // The seeded employees were given TEST_USERS_PASSWORD; the first ones (klaus.schmidt, editora…)
    // still have the demo's. The login form tries them in this order and remembers the one that worked.
    return [users, demo]
}

/** The index in candidates() of the password that worked for each user, this run. */
const working = new Map<string, number>()

/** Everyone the suite signs in as (global-setup.ts signs each in once). */
export const USERS = ['demo', 'ana.perez', 'klaus.schmidt', 'carlos.diaz', 'isabel.torres']

/** Where global-setup.ts keeps each user's Keycloak session (gitignored; cookies, no passwords). */
export const AUTH_DIR = path.join(__dirname, '..', '.intranet-auth')
export const authFile = (user: string) => path.join(AUTH_DIR, `${user}.json`)

// ── reading the page ─────────────────────────────────────────────────────────────────────────────

/** The page's text through every shadow root, joined with " | ". */
export function pageText(page: Page): Promise<string> {
    return page.evaluate(() => {
        const out: string[] = []
        const walk = (n: Node) => {
            if (n.nodeType === 3) { const t = (n.textContent ?? '').trim(); if (t) out.push(t); return }
            if (n.nodeType !== 1 && n.nodeType !== 11) return
            const el = n as HTMLElement
            if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE' || el.tagName === 'NOSCRIPT') return
            if (el.shadowRoot) walk(el.shadowRoot)
            for (const c of Array.from(n.childNodes)) walk(c)
        }
        walk(document.body)
        return out.join(' | ')
    // still navigating (the Keycloak round trip): "not yet"
    }).catch(() => '')
}

/** The error Mateu paints for an exception in an action («An unexpected error occurred. Reference: …»). */
export const SERVER_ERROR = /An unexpected error occurred\. Reference: \w+/
/** The shells' page for a route nothing resolves. */
export const NOT_FOUND = /Página no encontrada|Page not found/

/** A session of one user in its own browser context. */
export class Session {
    constructor(readonly user: string, readonly page: Page, readonly hosts: Hosts, readonly shell: Shell,
                private readonly info: TestInfo) {}

    get redwood() { return this.shell === 'redwood' }

    private async signInIfAsked() {
        if (!this.page.url().includes('/realms/')) return
        const username = this.page.locator('#username')
        if (!await username.count()) return
        const list = candidates(this.user)
        for (let i = working.get(this.user) ?? 0; i < list.length; i++) {
            await username.fill(this.user)
            await this.page.locator('#password').fill(list[i])
            await this.page.locator('#kc-login').click()
            await this.page.waitForTimeout(2_000)
            const refused = this.page.url().includes('/realms/')
                && await this.page.locator('#input-error, #kc-error-message, .kc-feedback-text').count() > 0
            if (!refused) { working.set(this.user, i); return }
        }
        throw new Error(`Keycloak refused ${this.user} with both passwords (E2E_USERS_PASSWORD, E2E_DEMO_PASSWORD)`)
    }

    /**
     * Goes to `target` and waits until the page shows `ready`. Redwood shells take 20–40 s to paint
     * the first time, and either shell sends the browser to Keycloak only once its script runs: the
     * poll signs in whenever the login form turns up.
     */
    async go(target: string, ready: RegExp, timeout = this.redwood ? 120_000 : 90_000) {
        await this.page.goto(target, { waitUntil: 'domcontentloaded' })
        await this.waitText(ready, `${target} never showed ${ready}`, timeout)
    }

    async reload(ready: RegExp, timeout = this.redwood ? 120_000 : 90_000) {
        await this.page.reload({ waitUntil: 'domcontentloaded' })
        await this.waitText(ready, `the reload of ${this.page.url()} never showed ${ready}`, timeout)
    }

    async waitText(re: RegExp, message: string, timeout = 60_000) {
        let notFoundSince = 0
        let reloads = 0
        await expect.poll(async () => {
            await this.signInIfAsked()
            const t = await pageText(this.page)
            if (re.test(t)) return true
            // The shared local cluster gets content-hub redeployed under us now and then: while it
            // restarts, the shell answers a route with its «not found» page and stays there. A
            // second look at the same URL (still a deep link) after it has had time to come back.
            if (NOT_FOUND.test(t) && reloads < 2) {
                notFoundSince ||= Date.now()
                if (Date.now() - notFoundSince > 10_000) {
                    reloads++
                    notFoundSince = 0
                    this.info.annotations.push({ type: 'reloaded', description: `«not found» on ${this.page.url()}; reloaded` })
                    await this.page.reload({ waitUntil: 'domcontentloaded' }).catch(() => {})
                }
            }
            if (SERVER_ERROR.test(t)) return `server error: ${SERVER_ERROR.exec(t)![0]}`
            return false
        }, { message, timeout, intervals: [1_000] }).toBe(true)
    }

    text() { return pageText(this.page) }

    /** A screenshot attached to the report. */
    async checkpoint(name: string) {
        const body = await this.page.screenshot().catch(() => undefined)
        if (body) await this.info.attach(`${this.user}-${name}`, { body, contentType: 'image/png' })
    }

    /** A Mateu field by its id, in either renderer. */
    field(id: string): Locator {
        return this.page.locator(`mateu-field#${id}, [data-field-id="${id}"]`).first()
    }

    /** The text input (or textarea) of a field. */
    input(id: string): Locator {
        return this.field(id).locator('input, textarea').first()
    }

    async fill(id: string, value: string) {
        const input = this.input(id)
        await input.scrollIntoViewIfNeeded()
        // forced: Redwood's floating label lies over an empty input and takes the click
        await input.click({ force: true })
        await input.fill(value)
        await input.press('Tab')
        await this.page.waitForTimeout(500)
    }

    /**
     * Types a date key by key, as a person does — the date pickers parse what is typed (fill() would
     * set the value without the keystrokes they listen to). Vaadin's picker opens its calendar while
     * typing: Enter commits the typed date and closes it.
     */
    async typeDate(id: string, value: string) {
        const input = this.input(id)
        await input.scrollIntoViewIfNeeded()
        await input.click({ force: true })
        await input.press('ControlOrMeta+a')
        await input.press('Backspace')
        await input.pressSequentially(value, { delay: 40 })
        if (!this.redwood) await input.press('Enter')
        await input.press('Tab')
        await this.page.waitForTimeout(800)
    }

    /** A select field (vaadin-select, oj-select-one…) → the option whose text matches. */
    async choose(id: string, option: RegExp) {
        const f = this.field(id)
        await f.scrollIntoViewIfNeeded()
        const opener = f.locator('vaadin-select, [role=combobox], oj-select-single, oj-c-select-single, oj-select-one, select').first()
        await (await opener.count() ? opener : f).click()
        await this.page.waitForTimeout(1_000)
        await this.page.getByRole('option', { name: option }).filter({ visible: true }).first().click()
        await this.page.waitForTimeout(1_000)
    }

    /**
     * One value of a multi-valued field (Hotel, Departamento…): a checkbox group in Vaadin, a
     * multi-select in Redwood.
     */
    async check(id: string, label: string) {
        const f = this.field(id)
        await f.scrollIntoViewIfNeeded()
        const box = f.getByRole('checkbox', { name: label, exact: true }).first()
        if (await box.count()) {
            await box.check({ force: true })
        } else {
            await f.locator('[role=combobox]').first().click()
            await this.page.waitForTimeout(1_000)
            await this.page.getByRole('option', { name: label, exact: true }).filter({ visible: true }).first().click()
            await this.page.keyboard.press('Escape')
        }
        await this.page.waitForTimeout(500)
    }

    /** The form's Save / Cancel: Redwood says «Cancelar», Vaadin «Cancel»; both «Save». */
    saveButton() { return this.button(/^(Save|Guardar)$/) }
    cancelButton() { return this.button(/^(Cancel|Cancelar)$/) }

    /**
     * A header action: a button when there is room for it, an item of the «Más acciones» (⋯) menu
     * when the toolbar overflows — both renderers move actions there as a form grows more of them.
     */
    async action(name: RegExp) {
        const direct = this.button(name)
        if (await direct.isVisible().catch(() => false)) {
            await this.click(direct, 2_000)
            return
        }
        await this.click(this.page.getByRole('button', { name: /^(Más acciones|More Actions|⋯)$/i })
            .filter({ visible: true }).first(), 1_000)
        // Redwood's items are menuitems; Vaadin's overlay items carry no role: by their text — the
        // overlay is the last thing in the page, so the last visible match is the item
        const item = this.page.getByRole('menuitem', { name }).or(this.page.getByText(name))
        await this.click(item.filter({ visible: true }).last(), 2_000)
    }

    /** A listing's table, whichever renderer drew it (Redwood keeps hidden templates around). */
    table(): Locator {
        return this.page.locator('vaadin-grid, oj-table, oj-c-table').filter({ visible: true }).first()
    }

    /** A listing's search box. */
    searchBox(): Locator {
        return this.page.locator('vaadin-text-field input, oj-input-text input, oj-c-input-text input, ' +
            '[role=search] input, input[type=search], input[type=text]').filter({ visible: true }).first()
    }

    button(name: string | RegExp): Locator {
        return this.page.getByRole('button', { name, exact: typeof name === 'string' }).first()
    }

    async click(loc: Locator, settle = 1_500) {
        await loc.scrollIntoViewIfNeeded().catch(() => {})
        await loc.click()
        await this.page.waitForTimeout(settle)
    }

    /** Mateu's confirmation dialog («Sí»), when an action asks. */
    async confirmIfAsked() {
        await this.page.waitForTimeout(1_500)
        const accept = this.page.locator('#mateuConfirmAccept')
            // Vaadin's confirm dialog is an alertdialog
            .or(this.page.getByRole('button', { name: /^(Sí|Yes)$/ }))
            .filter({ visible: true }).first()
        if (await accept.count()) {
            await accept.click()
            await this.page.waitForTimeout(2_000)
        }
    }
}

// ── the fixture ──────────────────────────────────────────────────────────────────────────────────

type Fixtures = {
    hosts: Hosts
    /** A signed-in session of `user`, in a context of its own (closed at the end of the test). */
    as: (user: string) => Promise<Session>
}

export const test = base.extend<ShellOptions & Fixtures>({
    shell: ['vaadin', { option: true }],
    hosts: async ({ shell }, use) => { await use(hostsFor(shell)) },
    as: async ({ browser, shell, hosts }, use, info) => {
        const contexts: BrowserContext[] = []
        const sessions = new Map<string, Session>()
        await use(async (user: string) => {
            const known = sessions.get(user)
            if (known) return known
            const context = await newContext(browser, info, user)
            contexts.push(context)
            const s = new Session(user, await context.newPage(), hosts, shell, info)
            // A confirm() the page raises (the visual editor's «¿Salir sin guardar?») is answered yes.
            s.page.on('dialog', d => d.accept().catch(() => {}))
            sessions.set(user, s)
            return s
        })
        for (const c of contexts) {
            if (info.status !== info.expectedStatus) {
                for (const p of c.pages()) {
                    const body = await p.screenshot().catch(() => undefined)
                    if (body) await info.attach(`at-failure-${contexts.indexOf(c)}`, { body, contentType: 'image/png' })
                }
            }
            await c.close()
        }
    },
})

async function newContext(browser: Browser, info: TestInfo, user: string) {
    const use = info.project.use
    return browser.newContext({
        viewport: use.viewport ?? { width: 1440, height: 900 },
        locale: use.locale, timezoneId: use.timezoneId,
        storageState: fs.existsSync(authFile(user)) ? authFile(user) : undefined,
    })
}

export { expect }

/** A tag unique to this run, for the names and texts the suite writes. */
export const RUN = `${new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '')}-${Math.random().toString(36).slice(2, 6)}`

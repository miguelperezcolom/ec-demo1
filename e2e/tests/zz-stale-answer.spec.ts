import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readFileSync } from 'fs'
import { join } from 'path'

// READ-ONLY probe (Mateu: stale answers die silently). With MATEU_BUNDLE set, the shell's
// mateu-vaadin.js (and its chunks) are swapped client-side for that local build — nothing is
// deployed. The IA agents listing's search is held back DELAY ms (and fulfilled with the real
// answer, or with a 500 when FAIL=1) while the user navigates to another section in-app.
const out = join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/stale-answer')
mkdirSync(out, { recursive: true })
const label = process.env.LABEL ?? 'after'
const bundle = process.env.MATEU_BUNDLE
const fail = process.env.FAIL === '1'
const delay = Number(process.env.DELAY ?? 6000)

const toastsOf = (page: Page) => page.evaluate(() =>
    Array.from(document.querySelectorAll('vaadin-notification-card')).map(c => (c.textContent ?? '').trim()))

test(`${label}: a held-back search answered ${fail ? 'with a 500' : 'late'} after navigating away`, async ({ page }) => {
    test.setTimeout(120000)
    const logs: string[] = []
    if (bundle) await page.route(/\/assets\/[\w-]+\.(js|css)(\?.*)?$/, r => {
        const name = new URL(r.request().url()).pathname.split('/').pop()!
        const local = join(bundle, name)
        if (!existsSync(local)) return r.continue()
        logs.push('swapped ' + name)
        return r.fulfill({ body: readFileSync(local), contentType: name.endsWith('.css') ? 'text/css' : 'text/javascript' })
    })
    page.on('console', m => { const t = m.text(); if (/mateu|dropped|no longer on screen|Action request failed/i.test(t)) logs.push(m.type() + ' ' + t.slice(0, 200)) })
    page.on('pageerror', e => logs.push('PAGEERROR ' + String(e).slice(0, 200)))
    const c = CONSOLES.find(x => x.name === 'control · vaadin')!
    await page.setViewportSize({ width: 1440, height: 900 })
    await signIn(page, c)
    await page.waitForTimeout(4000)

    let held = 0
    let released = 0
    await page.route(/\/mateu\/v3\/sync\/catalogues\/agents/, async r => {
        const body = r.request().postDataJSON?.() ?? {}
        if (body.actionId !== 'search') return r.continue()
        held++
        logs.push(`held search ${r.request().url()}`)
        const response = fail ? undefined : await r.fetch()
        await new Promise(res => setTimeout(res, delay))
        released++
        logs.push(`released search (${fail ? '500' : 'real answer'})`)
        return fail
            ? r.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"held back by the probe"}' })
            : r.fulfill({ response })
    })

    // 1) the agents listing, through the app (an in-app navigation, as a user would)
    await page.evaluate(() => {
        window.history.pushState({}, '', '/catalogues/agents')
        window.dispatchEvent(new PopStateEvent('popstate'))
    })
    for (let i = 0; i < 40 && held === 0; i++) await page.waitForTimeout(250)
    await page.screenshot({ path: join(out, `${label}-${fail ? 'fail' : 'late'}-1-agents-searching.png`) })

    // 2) while its search is held back, go elsewhere — MODE=popstate (a back/forward: the top ux is
    // rebuilt), llms (a click on another entry of the same remote: the content ux is reused), or
    // remote (a click through the hamburger to another section, another remote)
    const mode = process.env.MODE ?? 'popstate'
    if (mode === 'llms') {
        await page.getByText('Llms', { exact: true }).first().click()
    } else if (mode === 'remote') {
        await page.mouse.click(37, 28) // the hamburger: the sections
        await page.waitForTimeout(800)
        await page.screenshot({ path: join(out, `${label}-${fail ? 'fail' : 'late'}-${mode}-menu.png`) })
        await page.getByText('Audit', { exact: true }).first().click({ timeout: 8000 })
    } else {
        await page.evaluate(() => {
            window.history.pushState({}, '', '/audit/actions')
            window.dispatchEvent(new PopStateEvent('popstate'))
        })
    }
    await page.waitForTimeout(1500)
    await page.screenshot({ path: join(out, `${label}-${process.env.MODE ?? 'popstate'}-${fail ? 'fail' : 'late'}-2-audit-before-answer.png`) })

    // 3) the held answer lands; watch for anything of the agents listing or a toast
    for (let i = 0; i < 60 && released === 0; i++) await page.waitForTimeout(250)
    const toasts: string[] = []
    for (let i = 0; i < 12; i++) {
        await page.waitForTimeout(250)
        for (const t of await toastsOf(page)) if (!toasts.includes(t)) toasts.push(t)
    }
    await page.screenshot({ path: join(out, `${label}-${process.env.MODE ?? 'popstate'}-${fail ? 'fail' : 'late'}-3-after-answer.png`) })
    const text = await page.evaluate(() => {
        const walk = (n: Node, acc: string[]) => {
            if (n.nodeType === 3) acc.push(n.textContent ?? '')
            const el = n as Element
            if (el.shadowRoot) walk(el.shadowRoot, acc)
            n.childNodes.forEach(ch => walk(ch, acc))
            return acc
        }
        return walk(document.body, []).join(' ').replace(/\s+/g, ' ')
    })
    console.log(JSON.stringify({
        label, fail, held, released, url: page.url(), toasts,
        mentionsAgent: /mapping-agent|reception-agent/i.test(text),
        mentionsAudit: /audit/i.test(text),
        mode: process.env.MODE ?? 'popstate',
        logs,
    }, null, 1))
})

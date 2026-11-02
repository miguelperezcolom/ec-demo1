import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { mkdirSync } from 'fs'
import { join } from 'path'

// READ-ONLY look at the control plane drawn as HAMBURGER_SECTIONS (Mateu P9 delivery 2), before any
// release: the browser talks to ec1 for Keycloak and for every service (/_ia-cp, /_users…), but the
// SHELL itself — its page, its bundle and its /mateu/v3 — is answered by a control-shell running
// locally on the unreleased Mateu (LOCAL_VAADIN / LOCAL_REDWOOD). Nothing is deployed or changed.
const out = process.env.OUT ?? join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/hamburger-sections')
mkdirSync(out, { recursive: true })

// Keycloak sends the browser back with a redirect, and Playwright does not intercept a request that
// is part of a redirect chain: that one document is the deployed shell's page. Its bundle lives
// under the DEPLOYED build's version directory (Redwood), which is answered from the local one.
const viaLocalShell = async (page: Page, host: string, local: string) => {
  const index = await (await fetch(local + '/')).text()
  const localVersion = (index.match(/version_\d+/) || [''])[0]
  await page.route(`https://${host}/**`, async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.startsWith('/_') || url.pathname.startsWith('/ai/')) return route.continue()
    const path = localVersion ? url.pathname.replace(/^\/version_\d+\//, `/${localVersion}/`) : url.pathname
    const response = await route.fetch({ url: local + path + url.search, headers: { ...route.request().headers(), origin: `https://${host}` } })
    return route.fulfill({ response })
  })
}

const bandOf = (page: Page) => page.evaluate(() => {
  const pierce = (root: ParentNode, sel: string): Element | null => {
    const hit = root.querySelector(sel); if (hit) return hit
    for (const el of Array.from(root.querySelectorAll('*'))) { const sr = (el as HTMLElement).shadowRoot; if (sr) { const f = pierce(sr, sel); if (f) return f } }
    return null
  }
  const band = pierce(document, 'nav.mateu-section-band') || pierce(document, '.mateu-subheader')
  const items = band ? Array.from(band.querySelectorAll('vaadin-menu-bar-button, [data-nav-item]')).map((e) => (e.textContent || '').trim().replace(/\s+/g, ' ')).filter(Boolean) : []
  const active = band ? Array.from(band.querySelectorAll('.mateu-nav-active')).map((e) => (e.textContent || '').trim()) : []
  const title = band?.querySelector('.mateu-section-title, .mateu-subheader-title')?.textContent?.trim()
  return { title, items, active }
})

for (const [renderer, host, local, wait] of [
  ['vaadin', 'console.ec1.mateu.io', 'http://localhost:8111', 6000],
  ['redwood', 'rw-console.ec1.mateu.io', 'http://localhost:8112', 25000],
] as const) {
  if (process.env.ONLY && process.env.ONLY !== renderer) continue
  test(`control plane as sections: ${renderer}`, async ({ page }) => {
    test.setTimeout(300000)
    const c = CONSOLES.find((x) => x.host === host)!
    await viaLocalShell(page, host, local)
    page.on('console', (m) => { if (m.type() === 'error') console.log(renderer, 'CONSOLE', m.text().slice(0, 300)) })
    page.on('pageerror', (e) => console.log(renderer, 'PAGEERROR', String(e).slice(0, 300)))
    page.on('response', (r) => { if (r.status() >= 400) console.log(renderer, 'HTTP', r.status(), r.url(), (r.request().postData() || '').slice(0, 200)) })
    page.on('requestfailed', (r) => console.log(renderer, 'FAILED', r.url(), r.failure()?.errorText, (r.postData() || '').slice(0, 120)))
    await page.setViewportSize({ width: 1440, height: 900 })
    await signIn(page, c).catch(() => {}) // the band, not the bar, holds the menu now
    await page.waitForTimeout(wait)
    await page.screenshot({ path: join(out, `cp-${renderer}-home.png`) })
    // a cold load inside a service: its section and its second level in the band
    await page.goto(`https://${host}/catalogues/agents`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(wait)
    console.log(renderer, 'agents', JSON.stringify(await bandOf(page)))
    await page.screenshot({ path: join(out, `cp-${renderer}-ia-agents.png`) })
    // the hamburger: the sections
    if (renderer === 'vaadin') {
      await page.getByRole('button', { name: /^(Sections|Secciones)$/ }).click()
    } else {
      await page.locator('#mateuHamburger').click()
    }
    await page.waitForTimeout(2500)
    await page.screenshot({ path: join(out, `cp-${renderer}-hamburger.png`) })
    // choose Usuarios: its first screen, and the band follows
    if (renderer === 'vaadin') {
      await page.locator('#mateu-sections-panel').getByRole('button', { name: 'Usuarios' }).click()
    } else {
      await page.locator('#mateuSectionList li', { hasText: 'Usuarios' }).click()
    }
    await page.waitForTimeout(wait)
    console.log(renderer, 'usuarios', page.url(), JSON.stringify(await bandOf(page)))
    await page.screenshot({ path: join(out, `cp-${renderer}-usuarios.png`) })
  })
}

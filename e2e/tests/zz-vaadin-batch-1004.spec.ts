import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readFileSync } from 'fs'
import { join } from 'path'

// READ-ONLY look at the Vaadin consoles (and their Redwood counterparts) for the Vaadin UX batch of
// 2026-10-04. With MATEU_BUNDLE set, the shell's mateu-vaadin.js is swapped client-side for that
// local build (nothing is deployed). LABEL names the shots (before / after).
const out = join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/vaadin-batch-1004')
mkdirSync(out, { recursive: true })
const label = process.env.LABEL ?? 'before'
const bundle = process.env.MATEU_BUNDLE

const shots: Array<[string, string, string[]]> = [
  ['control · vaadin', 'cp-vaadin', ['/', '/catalogues/agents', '/catalogues/agents/mapping-agent']],
  ['data · vaadin', 'dp-vaadin', ['/', '/workflow/processes']],
  ['control · redwood', 'cp-redwood', ['/', '/catalogues/agents']],
  ['data · redwood', 'dp-redwood', ['/', '/workflow/processes']],
]

for (const [name, slug, routes] of shots) {
  if (process.env.ONLY && !slug.includes(process.env.ONLY)) continue
  test(`${label}: ${name}`, async ({ page }) => {
    test.setTimeout(420000)
    const redwood = slug.includes('redwood')
    // the whole local dist/assets (the entry and its vendor chunks, whose minified exports must match)
    if (bundle && !redwood) await page.route(/\/assets\/[\w-]+\.(js|css)(\?.*)?$/, r => {
      const name = new URL(r.request().url()).pathname.split('/').pop()!
      const local = join(bundle, name)
      if (!existsSync(local)) return r.continue()
      return r.fulfill({ body: readFileSync(local), contentType: name.endsWith('.css') ? 'text/css' : 'text/javascript' })
    })
    // STRIP=<url>: as if the app declared @App(accentStrip = <url>) — the shell's answer is
    // amended in the browser only (the deployed server knows nothing of it yet)
    if (process.env.STRIP && !redwood) await page.route(/\/mateu\/v3\//, async r => {
      const response = await r.fetch()
      const body = (await response.text()).replace(/"accentColor":"([^"]+)"/g, `"accentColor":"$1","accentStrip":"${process.env.STRIP}"`)
      return r.fulfill({ response, body })
    })
    page.on('pageerror', (e) => console.log(slug, 'PAGEERROR', String(e).slice(0, 300)))
    const c = CONSOLES.find(x => x.name === name)!
    await page.setViewportSize({ width: 1440, height: 900 })
    await signIn(page, c).catch(() => {})
    const wait = redwood ? 25000 : 9000
    for (const r of routes) {
      await page.goto(`https://${c.host}${r}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(wait)
      const n = r === '/' ? 'home' : r.split('/').pop()
      await page.screenshot({ path: join(out, `${label}-${slug}-${n}.png`) })
    }
    if (!redwood) {
      const toggle = page.getByRole('button', { name: /^(Sections|Secciones)$/ })
      if (await toggle.count()) {
        await toggle.first().click(); await page.waitForTimeout(1200)
        await page.screenshot({ path: join(out, `${label}-${slug}-hamburger-open.png`) })
        await page.screenshot({ path: join(out, `${label}-${slug}-header-zoom.png`), clip: { x: 0, y: 0, width: 1440, height: 110 } })
        await toggle.first().click(); await page.waitForTimeout(800)
      }
      // a listing → home: the sub-header folds away (mid-animation shot, then settled)
      await page.goto(`https://${c.host}${routes[1]}`, { waitUntil: 'domcontentloaded' }); await page.waitForTimeout(wait)
      const brand = page.locator('a.mateu-app-brand').first()
      if (await brand.count()) {
        await brand.click(); await page.waitForTimeout(90)
        await page.screenshot({ path: join(out, `${label}-${slug}-subheader-folding.png`), clip: { x: 0, y: 0, width: 1440, height: 160 } })
        await page.waitForTimeout(wait)
        await page.screenshot({ path: join(out, `${label}-${slug}-home-via-brand.png`) })
      }
    }
  })
}

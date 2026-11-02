import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync } from 'fs'
import { join, extname } from 'path'

// READ-ONLY check of the Ask Oracle FAB's place and size per page format (Mateu fix/redwood-subheader-
// no-accent). RW_STATIC=<dir>: the shell's static files are served from a local build (see
// zz-rw-subheader.spec.ts); the shells' backends still answer /mateu/v3/**. SHOT names the shots.
const staticDir = process.env.RW_STATIC
const label = process.env.SHOT ?? 'probe'
const out = process.env.OUT ?? join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/rw-ao-fab')
mkdirSync(out, { recursive: true })
const TYPES: Record<string, string> = { '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml', '.png': 'image/png' }
const swap = async (page: Page, host: string) => {
  if (!staticDir) return
  await page.route(`https://${host}/**`, (route) => {
    const url = new URL(route.request().url())
    let p = url.pathname.replace(/^\//, ''); try { p = decodeURIComponent(p) } catch { return route.continue() }
    if (!p || p.startsWith('mateu/') || p.startsWith('_') || route.request().method() !== 'GET') return route.continue()
    const local = readdirSync(staticDir).find((d) => d.startsWith('version_'))
    const file = join(staticDir, local ? p.replace(/^version_\d+\//, local + '/') : p)
    if (existsSync(file) && statSync(file).isFile()) return route.fulfill({ body: readFileSync(file), contentType: TYPES[extname(file)] ?? 'application/octet-stream' })
    return route.continue()
  })
}
const measure = (page: Page) => page.evaluate(() => {
  const fab = document.querySelector('.oj-sp-rw-chat-icon-cont') as HTMLElement | null
  const fmt = document.querySelector('.oj-sp-rw-ask-simple-uishell-to-edge-page') ? 'edgeToEdge'
    : document.querySelector('.oj-sp-rw-simple-uishell-full-width-page') ? 'fullWidth' : 'fixed'
  let right = 0
  for (const el of Array.from(document.querySelectorAll('oj-table, oj-sp-smart-filter-search, oj-sp-header-general-overview, .mateu-listing-paging')) as HTMLElement[]) {
    const r = el.getBoundingClientRect()
    if (r.width > 100 && r.height > 0) right = Math.max(right, Math.round(r.right))
  }
  const fr = fab?.getBoundingClientRect()
  const paging = document.querySelector('.mateu-listing-paging') as HTMLElement | null
  return { vw: window.innerWidth, fmt, contentRightGap: window.innerWidth - right,
    fab: fr ? { rightGap: Math.round(window.innerWidth - fr.right), size: Math.round(fr.width) + 'x' + Math.round(fr.height), bottom: Math.round(window.innerHeight - fr.bottom) } : null,
    pagingPadEnd: paging ? getComputedStyle(paging).paddingInlineEnd : null }
})

for (const [host, route, name] of [
  ['rw.ec1.mateu.io', '/erp/partners', 'fixed'],
  ['rw-console.ec1.mateu.io', '/workflow/definitions', 'fullWidth'],
  ['rw.ec1.mateu.io', '/booking/bookings', 'edgeToEdge'],
] as const) {
  test(`AO fab: ${name}`, async ({ page }) => {
    test.setTimeout(300000)
    const c = CONSOLES.find((x) => x.host === host)!
    await swap(page, host)
    await signIn(page, c)
    await page.setViewportSize({ width: 1600, height: 900 })
    await page.goto(`https://${host}${route}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(20000)
    for (const width of [1600, 1280, 390]) {
      await page.setViewportSize({ width, height: 900 })
      await page.waitForTimeout(1500)
      // the paging footer at the viewport's bottom, where the FAB is
      await page.evaluate(() => document.querySelector('.mateu-listing-paging')?.scrollIntoView({ block: 'end' }))
      await page.waitForTimeout(500)
      console.log(name, label, JSON.stringify(await measure(page)))
      await page.screenshot({ path: join(out, `ao-${name}-${width}-${label}.png`) })
    }
  })
}

test('AO fab follows a client-side navigation between formats', async ({ page }) => {
  test.setTimeout(300000)
  const host = 'rw.ec1.mateu.io'
  const c = CONSOLES.find((x) => x.host === host)!
  await swap(page, host)
  await signIn(page, c)
  await page.setViewportSize({ width: 1600, height: 900 })
  await page.goto(`https://${host}/booking/bookings`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(20000)
  console.log('nav before', label, JSON.stringify(await measure(page)))
  // ERP ▾ → Partners, from the sub-header band (no reload)
  await page.locator('.mateu-subheader oj-menu-button', { hasText: 'ERP' }).click()
  await page.waitForTimeout(800)
  await page.locator('oj-option', { hasText: 'Partners' }).first().click()
  await page.waitForTimeout(12000)
  console.log('nav after', label, page.url(), JSON.stringify(await measure(page)))
  await page.screenshot({ path: join(out, `ao-nav-after-${label}.png`) })
})

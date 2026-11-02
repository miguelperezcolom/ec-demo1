import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'fs'
import { join, extname } from 'path'

// READ-ONLY probe of Redwood listing column widths (Mateu fix/redwood-column-width) on the inbox.
// RW_STATIC=<dir> serves the local Redwood static build in place of the deployed one. Nothing is clicked.
const staticDir = process.env.RW_STATIC
const label = process.env.SHOT ?? 'probe'
const out = process.env.OUT ?? join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/rw-column-width')
mkdirSync(out, { recursive: true })
const TYPES: Record<string, string> = {
  '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.html': 'text/html',
  '.svg': 'image/svg+xml', '.png': 'image/png',
}
const swap = async (page: Page, host: string) => {
  if (!staticDir) return
  await page.route(`https://${host}/**`, (route) => {
    const url = new URL(route.request().url())
    let p = url.pathname.replace(/^\//, ''); try { p = decodeURIComponent(p) } catch { return route.continue() }
    if (!p || p.startsWith('mateu/') || p.startsWith('_') || route.request().method() !== 'GET') return route.continue()
    const local = readdirSync(staticDir).find((d) => d.startsWith('version_'))
    const file = join(staticDir, local ? p.replace(/^version_\d+\//, local + '/') : p)
    if (existsSync(file) && statSync(file).isFile()) {
      return route.fulfill({ body: readFileSync(file), contentType: TYPES[extname(file)] ?? 'application/octet-stream' })
    }
    return route.continue()
  })
}
for (const [name, host] of [['rw-console', 'rw-console.ec1.mateu.io'], ['rw', 'rw.ec1.mateu.io']] as const) {
  test(`${name}: inbox column widths`, async ({ page }) => {
    test.setTimeout(240000)
    await page.setViewportSize({ width: 1440, height: 900 })
    const c = CONSOLES.find((x) => x.host === host)!
    await swap(page, host)
    let n = 0
    page.on('response', async (r) => {
      if (r.url().includes('/mateu/v3/') && r.request().method() === 'POST') {
        try { const t = await r.text(); if (t.includes('ColumnWidth') || t.includes('"flexGrow"')) writeFileSync(join(out, `${name}-wire-${n++}.json`), t) } catch {}
      }
    })
    await signIn(page, c)
    await page.waitForTimeout(10000)
    await page.goto(`https://${host}/inbox/pending`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(40000)
    for (const scheme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: scheme })
      await page.waitForTimeout(1500)
      await page.screenshot({ path: join(out, `${name}-inbox-${scheme}-${label}.png`) })
    }
    const m = await page.evaluate(() => {
      const t = document.querySelector('#mateuTable') as HTMLElement | null
      if (!t) return null
      const headers = Array.from(t.querySelectorAll('th')).map((h) => {
        const r = h.getBoundingClientRect(); return { text: (h.textContent || '').trim(), left: Math.round(r.left), right: Math.round(r.right), width: Math.round(r.width) }
      })
      const row = t.querySelector('tbody tr')
      const cells = row ? Array.from(row.querySelectorAll('td')).map((td) => {
        const sp = td.querySelector('[title]') as HTMLElement | null
        return { w: Math.round(td.getBoundingClientRect().width), text: (td.textContent || '').trim().slice(0, 60), title: sp ? sp.title.slice(0, 60) : null,
          clipped: sp ? sp.scrollWidth > sp.clientWidth : null }
      }) : []
      const sc = t.querySelector('.oj-table-scroller') as HTMLElement | null
      return { vw: window.innerWidth, tableRight: Math.round(t.getBoundingClientRect().right), headers, cells,
        scroller: sc ? { scrollW: sc.scrollWidth, clientW: sc.clientWidth } : null, docScrollW: document.documentElement.scrollWidth }
    })
    console.log(name, label, JSON.stringify(m, null, 1))
  })
}

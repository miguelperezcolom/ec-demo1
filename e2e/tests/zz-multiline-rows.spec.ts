import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync } from 'fs'
import { join, extname } from 'path'

// READ-ONLY look at Mateu multi-line listing rows (@Line → GridColumn.line) on the bookings listing.
// The deployed backend knows nothing of @Line yet: the wire is amended in the browser (total, status,
// version and PMS → line 2). VAADIN_ASSETS / RW_STATIC swap in the local Mateu builds. Nothing is saved.
const out = join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/multiline-rows')
mkdirSync(out, { recursive: true })
const label = process.env.LABEL ?? 'after'
const vaadinAssets = process.env.VAADIN_ASSETS
const rwStatic = process.env.RW_STATIC
const LINE2 = (process.env.LINE2 ?? 'total,status,version,pmsReservationId').split(',')
const fake = process.env.FAKE !== '0'
const TYPES: Record<string, string> = {
  '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.html': 'text/html',
  '.svg': 'image/svg+xml', '.png': 'image/png',
}

const amend = (node: any): void => {
  if (!node || typeof node !== 'object') return
  if (Array.isArray(node)) { node.forEach(amend); return }
  if (node.type === 'GridColumn' && LINE2.includes(node.id)) node.line = 2
  if (process.env.CARDS && node.type === 'Crud') node.gridLayout = 'cards'
  if (process.env.DETAIL && node.type === 'Crud') node.detailPath = process.env.DETAIL
  for (const v of Object.values(node)) amend(v)
}

const swap = async (page: Page, host: string, redwood: boolean) => {
  if (fake) await page.route(/\/mateu\/v3\//, async (r) => {
    const response = await r.fetch()
    const text = await response.text()
    if (!text.includes('"GridColumn"')) return r.fulfill({ response, body: text })
    try { const json = JSON.parse(text); amend(json); return r.fulfill({ response, body: JSON.stringify(json) }) }
    catch { return r.fulfill({ response, body: text }) }
  })
  if (!redwood && vaadinAssets) await page.route(/\/assets\/[\w-]+\.(js|css)(\?.*)?$/, (r) => {
    const name = new URL(r.request().url()).pathname.split('/').pop()!
    const local = join(vaadinAssets, name)
    if (!existsSync(local)) return r.continue()
    return r.fulfill({ body: readFileSync(local), contentType: name.endsWith('.css') ? 'text/css' : 'text/javascript' })
  })
  if (redwood && rwStatic) await page.route(`https://${host}/**`, (route) => {
    const url = new URL(route.request().url())
    let p = url.pathname.replace(/^\//, ''); try { p = decodeURIComponent(p) } catch { return route.fallback() }
    if (!p || p.startsWith('mateu/') || p.startsWith('_') || route.request().method() !== 'GET') return route.fallback()
    const local = readdirSync(rwStatic).find((d) => d.startsWith('version_'))
    const file = join(rwStatic, local ? p.replace(/^version_\d+\//, local + '/') : p)
    if (existsSync(file) && statSync(file).isFile()) {
      return route.fulfill({ body: readFileSync(file), contentType: TYPES[extname(file)] ?? 'application/octet-stream' })
    }
    return route.fallback()
  })
}

for (const [slug, host] of [['vaadin', 'ec1.mateu.io'], ['redwood', 'rw.ec1.mateu.io']] as const) {
  if (process.env.ONLY && process.env.ONLY !== slug) continue
  test(`${label}: bookings multi-line rows (${slug})`, async ({ page }) => {
    test.setTimeout(420000)
    const redwood = slug === 'redwood'
    const c = CONSOLES.find((x) => x.host === host)!
    page.on('pageerror', (e) => console.log(slug, 'PAGEERROR', String(e).slice(0, 300)))
    await page.setViewportSize({ width: 1440, height: 900 })
    await swap(page, host, redwood)
    await signIn(page, c).catch((e) => console.log('signIn', String(e).slice(0, 200)))
    const wait = redwood ? 30000 : 10000
    await page.goto(`https://${host}/booking/bookings`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(wait)
    for (const scheme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: scheme })
      await page.waitForTimeout(1500)
      await page.screenshot({ path: join(out, `${label}-${slug}-bookings-${scheme}.png`) })
    }
    await page.emulateMedia({ colorScheme: 'light' })
    const probe = async () => page.evaluate((redwood) => {
      const deep = (root: Document | ShadowRoot, sel: string): Element[] => {
        const found = Array.from(root.querySelectorAll(sel))
        for (const el of Array.from(root.querySelectorAll('*'))) if ((el as any).shadowRoot) found.push(...deep((el as any).shadowRoot, sel))
        return found
      }
      if (redwood) {
        const t = document.querySelector('#mateuTable') as HTMLElement | null
        const headers = t ? Array.from(t.querySelectorAll('th')).map((h) => (h.textContent || '').trim()) : []
        const rows = t ? Array.from(t.querySelectorAll('tbody tr')) : []
        const first = rows[0] as HTMLElement | undefined
        const lines = first?.querySelector('.mateu-row-lines') as HTMLElement | null
        const rr = first?.getBoundingClientRect(); const lr = lines?.getBoundingClientRect()
        const tds = first ? Array.from(first.querySelectorAll('td')).slice(0, 3).map((td) => { const r = td.getBoundingClientRect(); const cs = getComputedStyle(td)
          return { x: Math.round(r.left - rr!.left), w: Math.round(r.width), pl: cs.paddingLeft, pos: cs.position, z: cs.zIndex, bg: cs.backgroundColor } }) : []
        const hit = lines ? (() => { const r = lines.getBoundingClientRect(); const el = document.elementFromPoint(r.left + 20, r.top + 8) as HTMLElement | null; return el ? el.tagName + '.' + el.className : null })() : null
        return { tds, hit, cls: t?.className, headers, rows: rows.length, rowH: rr && Math.round(rr.height),
          linesText: lines?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 200),
          linesBox: lr && { left: Math.round(lr.left - rr!.left), width: Math.round(lr.width), rowW: Math.round(rr!.width), top: Math.round(lr.top - rr!.top), h: Math.round(lr.height) } }
      }
      const grid = deep(document, 'vaadin-grid')[0] as any
      const headers = grid ? Array.from(grid.querySelectorAll('vaadin-grid-sort-column,vaadin-grid-column,vaadin-grid-filter-column')).map((c: any) => c.path ?? '') : []
      const lines = deep(document, '.row-lines')
      return { multiline: grid?.hasAttribute('data-multiline'), headers, linesCount: lines.length,
        firstLines: (lines[0]?.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 200) }
    }, redwood)
    console.log(slug, label, 'probe', JSON.stringify(await probe()))
    // narrow (phone-ish) width: the listing must still lay out
    await page.setViewportSize({ width: 600, height: 900 })
    await page.waitForTimeout(2500)
    await page.screenshot({ path: join(out, `${label}-${slug}-bookings-narrow.png`) })
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.waitForTimeout(2000)
    if (process.env.DETAIL && !redwood) {
      // @Details faked on the wire: a click on line 1 opens the detail under the extra lines; again closes it
      const cell = page.getByText('Carlos García').first()
      await cell.click(); await page.waitForTimeout(1500)
      await page.screenshot({ path: join(out, `${label}-${slug}-detail-open.png`) })
      await page.locator('.row-line-pair', { hasText: 'Version' }).first().click(); await page.waitForTimeout(1500)
      await page.screenshot({ path: join(out, `${label}-${slug}-detail-closed-by-line2.png`) })
    }
    if (process.env.PAGE2 === '1') {
      const next = redwood ? page.locator('#mateuListingPaging oj-button[data-page="next"]').first() : page.getByText('›', { exact: true }).last()
      console.log(slug, 'next count', await next.count())
      if (await next.count()) { await next.click(); await page.waitForTimeout(redwood ? 10000 : 5000)
        { const pr: any = await probe(); console.log(slug, 'page 2', pr.linesText ?? pr.firstLines, pr.linesCount ?? pr.rows, await page.getByText(/Page 2|Página 2/).count()) }
        await page.screenshot({ path: join(out, `${label}-${slug}-page2.png`) }) }
    }
    if (process.env.INTERACT === '1') {
      // sorting by a line-1 column (Arrival) — a read
      const header = redwood ? page.locator('#mateuTable th', { hasText: /Arrival|Llegada/ }).first()
        : page.locator('vaadin-grid-sort-column[path="arrival"]').locator('xpath=..').locator('vaadin-grid-sorter').first()
      const firstArrivals = async () => redwood
        ? page.locator('#mateuTable tbody tr').evaluateAll((trs) => trs.slice(0, 4).map((tr) => (tr.querySelectorAll('td')[4]?.textContent || '').trim()))
        : page.evaluate(() => { const find = (root: any): any => { const g = root.querySelector('vaadin-grid'); if (g) return g; for (const el of root.querySelectorAll('*')) if (el.shadowRoot) { const r = find(el.shadowRoot); if (r) return r } return null }
            const g = find(document); return (g?.items || []).slice(0, 4).map((i: any) => i.arrival) })
      console.log(slug, 'before sort', JSON.stringify(await firstArrivals()), 'sorter count', await header.count())
      if (redwood) console.log('th html', (await header.innerHTML()).replace(/\s+/g, ' ').slice(0, 600))
      const sorter = redwood ? header.locator('[class*="sort"]').first() : page.locator('vaadin-grid-sorter', { hasText: 'Arrival' }).first()
      console.log(slug, 'sorter by text', await sorter.count())
      page.on('request', (rq) => { const b = rq.postData() || ''; const m = b.match(/"sort"\s*:\s*\[[^\]]*\]/); if (m && rq.url().includes('/mateu/v3/')) console.log(slug, 'search sort →', m[0]) })
      if (await sorter.count()) { await header.hover(); await sorter.click({ force: true }); await page.waitForTimeout(redwood ? 8000 : 4000)
        console.log(slug, 'after sort', JSON.stringify(await firstArrivals()))
        await page.screenshot({ path: join(out, `${label}-${slug}-bookings-sorted.png`) }) }
      // selection checkbox on the first row (client state only)
      const box = redwood ? page.locator('#mateuTable tbody tr').first().locator('oj-selector, input[type=checkbox]').first()
        : page.locator('vaadin-grid vaadin-checkbox').nth(1)
      if (await box.count()) { await box.click().catch(() => {}); await page.waitForTimeout(1500)
        await page.screenshot({ path: join(out, `${label}-${slug}-bookings-selected.png`) }) }
      // a click on line 2 of the first row opens the booking (row click / rowRoute) — a navigation, read-only
      if (process.env.LINE1CLICK === '1') {
        const cell = page.getByText('Riu Demo Mauricio').first()
        const b = page.url(); await cell.click().catch((e) => console.log('click1', String(e).slice(0, 200)))
        await page.waitForTimeout(redwood ? 15000 : 6000)
        console.log(slug, 'line-1 click', b, '→', page.url())
        await page.goto(`https://${host}/booking/bookings`, { waitUntil: 'domcontentloaded' }); await page.waitForTimeout(wait)
      }
      const line2 = redwood ? page.locator('#mateuTable .mateu-row-line-pair', { hasText: 'Version' }).first() : page.locator('.row-line-pair', { hasText: 'Version' }).first()
      if (await line2.count()) {
        const before = page.url()
        await line2.click().catch((e) => console.log('click', String(e).slice(0, 200)))
        await page.waitForTimeout(redwood ? 15000 : 6000)
        console.log(slug, 'row click', before, '→', page.url())
        await page.screenshot({ path: join(out, `${label}-${slug}-row-click.png`) })
      }
    }
  })
}

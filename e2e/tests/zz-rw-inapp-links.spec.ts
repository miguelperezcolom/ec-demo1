import { test, expect, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync } from 'fs'
import { join, extname } from 'path'

// READ-ONLY probe for Mateu fix/redwood-inapp-links: «Ver recorrido» in a booking detail navigates
// in-app (no document load). RW_STATIC=<copied static dir> swaps the deployed Redwood bundle.
const staticDir = process.env.RW_STATIC
const out = process.env.OUT ?? '/tmp/rw-inapp-links'
mkdirSync(out, { recursive: true })
const TYPES: Record<string, string> = {
  '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.html': 'text/html',
  '.svg': 'image/svg+xml', '.png': 'image/png',
}
let swapped = 0
const swap = async (page: Page, host: string) => {
  if (!staticDir) return
  const local = readdirSync(staticDir).find((d) => d.startsWith('version_'))
  await page.route(`https://${host}/**`, (route) => {
    const url = new URL(route.request().url())
    let p = url.pathname.replace(/^\//, ''); try { p = decodeURIComponent(p) } catch { return route.continue() }
    if (!p || p.startsWith('mateu/') || p.startsWith('_') || route.request().method() !== 'GET') return route.continue()
    const file = join(staticDir, local ? p.replace(/^version_\d+\//, local + '/') : p)
    if (existsSync(file) && statSync(file).isFile()) {
      swapped++
      return route.fulfill({ body: readFileSync(file), contentType: TYPES[extname(file)] ?? 'application/octet-stream' })
    }
    return route.continue()
  })
}

const domDump = (page: Page) => page.evaluate(() => {
    const c = document.querySelector('.mateu-content') as HTMLElement
    const fl = c.querySelector('oj-sp-foldout-layout') as HTMLElement | null
    const info: string[] = []
    info.push('foldout=' + !!fl + (fl ? ` h=${fl.getBoundingClientRect().height|0} cls=${fl.className}` : ''))
    if (fl) for (const p of Array.from(fl.querySelectorAll('oj-sp-foldout-panel'))) {
      const b = (p as HTMLElement).getBoundingClientRect()
      info.push(`panel x=${b.left|0} w=${b.width|0} h=${b.height|0} vis=${getComputedStyle(p).visibility} txt=${(p.textContent||'').trim().replace(/\s+/g,' ').slice(0,60)}`)
    }
    const walk = (el: Element, d: number) => { if (d > 6) return; for (const ch of Array.from(el.children)) { const b = (ch as HTMLElement).getBoundingClientRect(); if ((ch.textContent||'').trim().length > 30) info.push(`${'  '.repeat(d)}${ch.tagName}#${ch.id}.${String((ch as HTMLElement).className).slice(0,50)} h=${b.height|0} disp=${getComputedStyle(ch).display} vis=${getComputedStyle(ch).visibility}`); walk(ch, d + 1) } }
    walk(c, 0)
    return info.slice(0, 60).join('\n')
  })

test('booking → Ver recorrido navigates in-app; Back returns', async ({ page }) => {
  test.setTimeout(400000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const host = 'rw.ec1.mateu.io'
  await swap(page, host)
  page.on('pageerror', (e) => console.log('PAGEERROR', String(e).slice(0, 300)))
  await signIn(page, CONSOLES.find((x) => x.host === host)!)
  await page.goto(`https://${host}/booking/bookings/ZUAAKJ`, { waitUntil: 'domcontentloaded' })
  const link = page.locator('a', { hasText: 'Ver recorrido' }).locator('visible=true').first()
  await expect.poll(async () => await page.locator('a', { hasText: 'Ver recorrido' }).count(), { timeout: 120000 }).toBeGreaterThan(0)
  await page.waitForTimeout(4000)
  console.log('all links', await page.locator('a', { hasText: 'Ver recorrido' }).count(), 'visible', await link.count())
  console.log('dot hrefs', JSON.stringify(await page.locator('a.pagination-dot').evaluateAll((els) => els.map((e) => e.getAttribute('href')))))
  await page.locator('a.pagination-dot[aria-label="Go to In other systems"]').click()
  await expect.poll(async () => await link.count(), { timeout: 30000 }).toBeGreaterThan(0)
  console.log('swapped files', swapped, 'bridge has inAppRouteOfLink:',
    await page.evaluate(() => new Promise((r) => (window as any).require(['resources/js/mateu-bridge'],
      (b: any) => r(typeof b.inAppRouteOfLink), () => r('n/a')))).catch(() => 'n/a'))
  console.log('href', await link.getAttribute('href'))
  await page.waitForTimeout(3000)
  await page.screenshot({ path: join(out, '1-booking.png') })
  await page.evaluate(() => { (window as any).__probeMarker = 'alive' })
  const docs: string[] = []
  page.on('request', (r) => { if (r.resourceType() === 'document' && r.frame() === page.mainFrame()) docs.push(r.url()) })
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') console.log('CONSOLE', m.type(), m.text().slice(0, 300)) })
  page.on('response', async (r) => { const u = r.url(); if (r.request().method() === 'POST' || u.includes('mateu')) console.log('NET', r.request().method(), r.status(), u.replace('https://rw.ec1.mateu.io', '').slice(0, 160), (r.request().postData() || '').slice(0, 200)) })
  const t0 = Date.now()
  await link.click()
  await expect.poll(() => page.evaluate(() => (window as any).__mateuLoadedFull || ''), { timeout: 60000 })
    .toContain('/journey/bookings/ZUAAKJ')
  await expect.poll(() => page.evaluate(() => {
    const c = document.querySelector('.mateu-content') as HTMLElement | null
    return !!c && !c.classList.contains('mateu-navigating') && (c.innerText || '').includes('Cómo acabó')
  }), { timeout: 60000 }).toBe(true)
  const elapsed = Date.now() - t0
  await page.waitForTimeout(1500)
  await page.screenshot({ path: join(out, '2-journey.png') })
  await page.waitForTimeout(8000)
  await page.screenshot({ path: join(out, '2b-journey-later.png') })
  console.log('journey text', JSON.stringify(await page.evaluate(() => (document.querySelector('.mateu-content') as HTMLElement)?.innerText.slice(0, 300))))
  const marker = await page.evaluate(() => (window as any).__probeMarker)
  console.log('RESULT journey: url', page.url(), 'elapsed ms', elapsed, 'document loads', JSON.stringify(docs), 'marker', marker)
  const t1 = Date.now()
  await page.goBack()
  await expect.poll(() => page.evaluate(() => (window as any).__mateuLoadedFull || ''), { timeout: 60000 })
    .toContain('/booking/bookings/ZUAAKJ')
  await expect.poll(async () => await page.locator('a', { hasText: 'Ver recorrido' }).count(), { timeout: 60000 }).toBeGreaterThan(0)
  console.log('RESULT back: url', page.url(), 'elapsed ms', Date.now() - t1, 'document loads', JSON.stringify(docs),
    'marker', await page.evaluate(() => (window as any).__probeMarker))
  await page.waitForTimeout(1500)
  await page.screenshot({ path: join(out, '3-back.png') })
  expect(docs).toEqual([])
  expect(marker).toBe('alive')
  expect(elapsed).toBeLessThan(5000)
})

test('direct load of the journey (reference)', async ({ page }) => {
  test.setTimeout(300000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const host = 'rw.ec1.mateu.io'
  await swap(page, host)
  await signIn(page, CONSOLES.find((x) => x.host === host)!)
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') console.log('DCONSOLE', m.type(), m.text().slice(0, 300)) })
  page.on('response', async (r) => { const u = r.url(); if (r.request().method() === 'POST') console.log('DNET', r.request().method(), r.status(), u.replace('https://rw.ec1.mateu.io', '').slice(0, 160), (r.request().postData() || '').slice(0, 200)) })
  page.on('console', (m) => { if (m.text().includes('[dbg') || m.type() === 'error') console.log('CDBG', m.type(), m.text().slice(0, 600)) })
  await page.goto(`https://${host}/journey/bookings/ZUAAKJ`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(45000)
  await page.screenshot({ path: join(out, '9-journey-direct.png') })
  console.log('direct dom', await domDump(page))
  console.log('direct journey text', JSON.stringify(await page.evaluate(() => (document.querySelector('.mateu-content') as HTMLElement)?.innerText.slice(0, 300))))
})

test('control: navigation-requested (existing chain) from the booking detail to the journey', async ({ page }) => {
  test.setTimeout(300000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const host = 'rw.ec1.mateu.io'
  await swap(page, host)
  await signIn(page, CONSOLES.find((x) => x.host === host)!)
  page.on('console', (m) => { if (m.text().includes('[dbg') || m.type() === 'error') console.log('CDBG', m.type(), m.text().slice(0, 600)) })
  await page.goto(`https://${host}/${process.env.FROM ?? 'booking/bookings/ZUAAKJ'}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(40000)
  await page.evaluate(() => document.getElementById('pageContent')!.dispatchEvent(new CustomEvent('navigation-requested', { detail: { route: '/journey/bookings/ZUAAKJ' }, bubbles: true, composed: true })))
  await page.waitForTimeout(12000)
  await page.screenshot({ path: join(out, '8-control.png') })
  console.log('control dom', await domDump(page))
  console.log('control journey text', JSON.stringify(await page.evaluate(() => (document.querySelector('.mateu-content') as HTMLElement)?.innerText.slice(0, 200))))
})

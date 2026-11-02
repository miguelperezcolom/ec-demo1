import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync } from 'fs'
import { join, extname } from 'path'

// READ-ONLY probe for the Redwood UX batch (Mateu feat/redwood-ux-batch-1004). RW_STATIC=<dir> serves a
// local Redwood static build in place of the deployed one. SHOT=before|after names the screenshots.
const staticDir = process.env.RW_STATIC
const label = process.env.SHOT ?? 'before'
const out = process.env.OUT ?? join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/redwood-batch-1004')
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
const shot = (page: Page, name: string) => page.screenshot({ path: join(out, `${name}-${label}.png`) })
const only = process.env.ONLY ?? ''
const setup = async (page: Page, host: string) => {
  const c = CONSOLES.find((x) => x.host === host)!
  await swap(page, host)
  page.on('pageerror', (e) => console.log('PAGEERROR', String(e).slice(0, 300)))
  await signIn(page, c).catch((e) => console.log('signIn', String(e).slice(0, 200)))
}
const go = async (page: Page, host: string, path: string, wait = 35000) => {
  await page.goto(`https://${host}${path}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(wait)
}

test('shell: hamburger + subheader (rw-console)', async ({ page }) => {
  test.skip(!!only && !only.includes('shell'))
  test.setTimeout(400000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const host = 'rw-console.ec1.mateu.io'
  await setup(page, host)
  await page.waitForTimeout(30000)
  await shot(page, 'shell-home')
  await page.locator('#mateuHamburger').click()
  await page.waitForTimeout(2500)
  await shot(page, 'shell-home-hamburger-open')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(1500)
  console.log('after Esc open?', await page.evaluate(() => !!document.querySelector('.mateu-sections-overlay.mateu-open')))
  if (label !== 'before') {
    // a section from the floating menu: the band slides in
    await page.locator('#mateuHamburger').click()
    await page.waitForTimeout(1500)
    await page.locator('#mateuSectionList li', { hasText: 'Usuarios' }).click()
    await page.waitForTimeout(150)
    await shot(page, 'shell-band-sliding-in')
    await page.waitForTimeout(25000)
    console.log('after pick open?', await page.evaluate(() => !!document.querySelector('.mateu-sections-overlay.mateu-open')), page.url())
    await shot(page, 'shell-section-picked')
    // click outside (the scrim) closes
    await page.locator('#mateuHamburger').click()
    await page.waitForTimeout(1500)
    await page.mouse.click(900, 600)
    await page.waitForTimeout(1500)
    console.log('after scrim open?', await page.evaluate(() => !!document.querySelector('.mateu-sections-overlay.mateu-open')), page.url())
  }
  await go(page, host, '/users/roles/new')
  await shot(page, 'form-roles-new')
  await page.locator('#mateuHamburger').click()
  await page.waitForTimeout(2500)
  await shot(page, 'shell-section-hamburger-open')
  console.log('hamburger box', JSON.stringify(await page.evaluate(() => {
    const r = (s: string) => { const e = document.querySelector(s); if (!e) return null; const b = e.getBoundingClientRect(); return [Math.round(b.left), Math.round(b.top), Math.round(b.width), Math.round(b.height)] }
    return { hamburger: r('#mateuHamburger'), logo: r('oj-sp-global-header img'), sub: r('.mateu-subheader'), list: r('#mateuSectionList') }
  })))
})

test('listing: search box (rw)', async ({ page }) => {
  test.skip(!!only && !only.includes('search'))
  test.setTimeout(300000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const host = 'rw.ec1.mateu.io'
  await setup(page, host)
  await page.waitForTimeout(10000)
  await go(page, host, '/workflow/processes')
  await shot(page, 'search-processes')
  if (process.env.SPY) {
    await page.evaluate(() => {
      const el = document.getElementById('mateuListHeader') as any
      const dp = el && el.smartFilters && el.smartFilters.suggestions
      ;(window as any).__calls = []
      if (!dp) { (window as any).__calls.push('no dp'); return }
      for (const m of ['fetchFirst', 'fetchByOffset', 'fetchByKeys', 'containsKeys', 'getCapability', 'getTotalSize', 'isEmpty']) {
        const orig = dp[m].bind(dp)
        dp[m] = (...a: any[]) => { (window as any).__calls.push(m + ' ' + JSON.stringify(a[0], (k, v) => v instanceof Set ? [...v] : v instanceof RegExp ? String(v) : v)?.slice(0, 200)); return orig(...a) }
      }
    })
  }
  const input = page.locator('#mateuListHeader input').first()
  await input.click()
  await page.waitForTimeout(2500)
  await shot(page, 'search-processes-focus')
  if (label !== 'before') {
    await page.locator('.oj-sp-smart-filters-suggestions-popup li', { hasText: 'Status' }).first().click({ timeout: 10000 }).catch((e) => console.log('pick', String(e).slice(0, 150)))
    await page.waitForTimeout(3000)
    await shot(page, 'search-processes-picked-status')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(1000)
    await input.click({ timeout: 10000 })
    await input.pressSequentially('busi', { timeout: 10000 })
    await page.waitForTimeout(2000)
    await shot(page, 'search-processes-typed')
    if (process.env.SPY) console.log('CALLS', JSON.stringify(await page.evaluate(() => (window as any).__calls), null, 1))
  }
})

const buttons = (page: Page) => page.evaluate(() => Array.from(document.querySelectorAll('oj-button, button, oj-c-button'))
  .filter((b) => (b as HTMLElement).offsetParent !== null).map((b) => (b.textContent || '').trim().replace(/\s+/g, ' ')).filter(Boolean).slice(0, 60))

test('wizard: new booking (rw)', async ({ page }) => {
  test.skip(!!only && !only.includes('wizard'))
  test.setTimeout(450000)
  await page.setViewportSize({ width: 1920, height: 1080 })
  const host = 'rw.ec1.mateu.io'
  await setup(page, host)
  await page.waitForTimeout(10000)
  let n = 0
  page.on('response', async (r) => {
    if (r.url().includes('/mateu/v3/') && r.request().method() === 'POST') {
      try { const t = await r.text(); require('fs').writeFileSync(join(out, `wire-${label}-${n++}.json`), JSON.stringify({ url: r.url(), rq: JSON.parse(r.request().postData() || '{}'), rs: t.slice(0, 400000) })) } catch {}
    }
  })
  if (process.env.WIZ_FILL) {
    // READ-ONLY: only the wizard's in-flight state is seeded (nothing is created — Create is never pressed)
    const seed = { hotelCode: 'MRU01', channelCode: 'CALLCENTER', arrival: '2026-11-20', departure: '2026-11-23',
      holderFirstName: 'Ana', holderLastName: 'Probe' }
    const rooms = [{ line: null, roomTypeCode: 'DBL', ratePlanCode: 'BAR', boardCode: 'BB', adults: 2, childrenAges: [], total: null },
      { line: null, roomTypeCode: 'SUP', ratePlanCode: 'BAR', boardCode: 'HB', adults: 1, childrenAges: [], total: null }]
    await page.route('**/_booking/mateu/v3/sync/booking/newBooking', async (route) => {
      const rq = JSON.parse(route.request().postData() || '{}')
      if (rq.actionId === 'next' && rq.componentState) {
        const cs = rq.componentState
        if (cs.position === 1 && !(cs.rooms || []).length && !process.env.NO_ROOMS) cs.rooms = rooms
        return route.continue({ postData: JSON.stringify(rq) })
      }
      if (rq.actionId === '' || rq.actionId == null) {
        const response = await route.fetch()
        const body = await response.json()
        for (const f of body.fragments || []) if (f.state && 'hotelCode' in f.state) { Object.assign(f.state, seed); Object.assign(f.state.step || {}, seed) }
        return route.fulfill({ response, json: body })
      }
      return route.continue()
    })
  }
  const start = process.env.WIZ_ROUTE ?? '/booking/bookings'
  await go(page, host, start)
  await shot(page, 'wizard-0')
  console.log('buttons', JSON.stringify(await buttons(page)))
  const steps = (process.env.WIZ_CLICKS ?? '').split('|').filter(Boolean)
  let i = 1
  for (const s of steps) {
    if (s.startsWith('wait:')) { await page.waitForTimeout(+s.slice(5)); continue }
    if (s.startsWith('fill:')) { const [, sel, val] = s.split(':'); await page.locator(sel).first().fill(val); continue }
    if (s.startsWith('sel:')) {
      const lab = s.slice(4)
      await page.getByRole('combobox', { name: lab }).last().click({ timeout: 10000 }).catch((e) => console.log('sel', lab, String(e).slice(0, 120)))
      await page.waitForTimeout(3000)
      await page.locator('[role="option"]:visible').first().click({ timeout: 10000 }).catch((e) => console.log('opt', lab, String(e).slice(0, 120)))
      await page.waitForTimeout(1500)
      continue
    }
    if (s === 'norooms') { process.env.NO_ROOMS = '1'; continue }
    if (s.startsWith('js:')) {
      const name = s.slice(3)
      await page.evaluate((n) => {
        const b = Array.from(document.querySelectorAll('oj-button, oj-c-button')).filter((e) => (e as HTMLElement).offsetParent !== null && (e.textContent || '').trim() === n).pop() as HTMLElement | undefined
        ;(b?.querySelector('button') as HTMLElement | null)?.click()
      }, name)
    } else await page.getByRole('button', { name: s, exact: true }).first().click().catch((e) => console.log('click', s, String(e).slice(0, 150)))
    await page.waitForTimeout(8000)
    await shot(page, `wizard-${i++}`)
    console.log('after', s, JSON.stringify(await buttons(page)).slice(0, 120), 'focus:', await page.evaluate(() => { const a = document.activeElement as HTMLElement | null; const host = a && a.closest('[data-field-id]'); return host ? host.getAttribute('data-field-id') : (a ? a.tagName : null) }))
  }
})

import { test, expect, Page, Route } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { readFileSync } from 'fs'

// READ-ONLY probe of Mateu P9 delivery 1 (robust menu federation) on the data console (Vaadin).
// With MATEU_BUNDLE set, the shell's mateu-vaadin.js is swapped client-side for that local build;
// with SIMULATE_SERVER=1 the shell's App answer is patched the way the new server writes it
// (routePrefix + shellLabel on each remote section, AUTO → MENU_ON_TOP for a shell with remotes),
// since ec1 still runs the released server. Nothing is deployed and no data is changed.
const bundle = process.env.MATEU_BUNDLE
const simulate = process.env.SIMULATE_SERVER === '1'

const patchApp = (json: any) => {
  const walk = (c: any) => {
    if (!c || typeof c !== 'object') return
    const m = c.metadata
    if (m && m.type === 'App' && Array.isArray(m.menu) && hasRemote(m.menu)) {
      m.variant = 'MENU_ON_TOP'
      const mark = (options: any[]) => options.forEach(o => {
        if (o.remote) {
          const seg = String(o.path || '').split('/').filter(Boolean).pop()
          o.routePrefix = seg ? '/' + seg : undefined
          // every section of ShellHome declares its label but the inbox
          o.shellLabel = !String(o.baseUrl).includes('_inbox')
        } else if (o.submenus) mark(o.submenus)
      })
      mark(m.menu)
    }
    for (const v of Object.values(c)) if (v && typeof v === 'object') walk(v)
  }
  walk(json)
  return json
}
const hasRemote = (menu: any[]): boolean => menu.some(o => o.remote || (o.submenus && hasRemote(o.submenus)))

const menuLabels = (page: Page) => page.evaluate(() => {
  const out: string[] = []
  const walk = (r: any) => r.querySelectorAll('*').forEach((el: any) => {
    if (el.shadowRoot) walk(el.shadowRoot)
    if (el.tagName === 'VAADIN-MENU-BAR-BUTTON' && el.closest('.menu-band')) out.push(el.textContent.trim() + (el.className.includes('unavailable') ? ' [unavailable]' : ''))
  })
  walk(document)
  return out.filter(t => t && t !== '···')
})

const crumbs = (page: Page) => page.evaluate(() => {
  const out: string[] = []
  const walk = (r: any) => r.querySelectorAll('*').forEach((el: any) => {
    if (el.shadowRoot) walk(el.shadowRoot)
    if (el.tagName === 'NAV' && el.classList.contains('breadcrumbs-bar')) out.push(el.textContent.replace(/\s+/g, ' ').trim())
  })
  walk(document)
  return out
})

const prepare = async (page: Page, hold?: { base: string, released: Promise<void> }, abort?: string) => {
  if (bundle) await page.route('**/assets/mateu-vaadin.js', r => r.fulfill({ body: readFileSync(bundle), contentType: 'text/javascript' }))
  await page.route('https://ec1.mateu.io/**/mateu/v3/**', async (route: Route) => {
    const url = route.request().url()
    const body = route.request().postData() || ''
    const isMenu = body.includes('"consumedRoute":"_empty"') && /"initiatorComponentId":"\/_[a-z-]+#/.test(body)
    if (isMenu && abort && url.includes(abort + '/mateu/')) return route.abort('connectionrefused')
    if (isMenu && hold && url.includes(hold.base + '/mateu/')) await hold.released
    if (simulate && !isMenu && !url.match(/ec1\.mateu\.io\/_/)) {
      const response = await route.fetch()
      const text = await response.text()
      try { return route.fulfill({ response, body: JSON.stringify(patchApp(JSON.parse(text))) }) } catch { return route.fulfill({ response, body: text }) }
    }
    return route.continue()
  })
}

const c = CONSOLES.find(x => x.name === 'data · vaadin')!

test('one remote down: its section unavailable, the others merged, labels steady', async ({ page }) => {
  test.setTimeout(300000)
  await signIn(page, c)
  await prepare(page, undefined, '/_erp')
  const seen: string[][] = []
  await page.goto('https://' + c.host + '/inicio', { waitUntil: 'domcontentloaded' })
  for (let i = 0; i < 20; i++) { seen.push(await menuLabels(page)); await page.waitForTimeout(1000) }
  const distinct = [...new Set(seen.filter(s => s.length).map(s => s.join(' | ')))]
  console.log('menu bar states over 20s:\n  ' + distinct.join('\n  '))
  const last = seen[seen.length - 1]
  expect(last.join(' | ')).toContain('ERP [unavailable]')
  await page.getByText('Call center', { exact: true }).first().click()
  await expect(page.getByText('Bookings', { exact: true }).first()).toBeVisible()
  await page.screenshot({ path: 'test-results/p9-erp-down.png' })
})

test('cold deep link: the section crumb before the remote answers', async ({ page }) => {
  test.setTimeout(300000)
  await signIn(page, c)
  let release: () => void = () => {}
  const released = new Promise<void>(r => { release = r })
  await prepare(page, { base: '/_workflow', released })
  await page.goto('https://' + c.host + '/workflow/processes', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(25000)
  const before = await crumbs(page)
  console.log('crumbs while /_workflow has not answered:', JSON.stringify(before), 'menu', JSON.stringify(await menuLabels(page)))
  await page.screenshot({ path: 'test-results/p9-cold-held.png' })
  release()
  await page.waitForTimeout(8000)
  const after = await crumbs(page)
  console.log('crumbs once it answered:', JSON.stringify(after))
  expect(before.join(' ')).toContain('Workflow')
})

test('hidden remote: a page under it has its trail', async ({ page }) => {
  test.setTimeout(300000)
  await signIn(page, c)
  await prepare(page)
  await page.goto('https://' + c.host + '/journey/bookings', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(25000)
  const trail = await crumbs(page)
  console.log('journey trail:', JSON.stringify(trail), 'menu', JSON.stringify(await menuLabels(page)))
  await page.screenshot({ path: 'test-results/p9-hidden.png' })
})

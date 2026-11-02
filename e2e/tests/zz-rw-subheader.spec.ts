import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync } from 'fs'
import { join, extname } from 'path'

// READ-ONLY probe of the Redwood MENU_ON_TOP sub-header (Mateu feat/redwood-menu-subheader).
// With RW_STATIC=<dir> (a local backend/shared/frontend/redwood/src/main/resources/static), every
// static file the Redwood shell asks for that exists there is served from it instead — the shell's
// own backend still answers /mateu/v3/**. Nothing is deployed and no data is changed.
// SHOT=<label> names the screenshots (before/after); OUT = where they go.
const staticDir = process.env.RW_STATIC
const label = process.env.SHOT ?? 'probe'
const out = process.env.OUT ?? join(process.env.HOME!, 'IdeaProjects/ec-demo1-ux/rw-subheader')
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
    // the deployed shell names its own build's version directory: answer it from the local one
    const local = readdirSync(staticDir).find((d) => d.startsWith('version_'))
    const file = join(staticDir, local ? p.replace(/^version_\d+\//, local + '/') : p)
    if (existsSync(file) && statSync(file).isFile()) {
      return route.fulfill({ body: readFileSync(file), contentType: TYPES[extname(file)] ?? 'application/octet-stream' })
    }
    return route.continue()
  })
}

const shoot = async (page: Page, name: string, scheme: 'light' | 'dark') => {
  await page.emulateMedia({ colorScheme: scheme })
  await page.waitForTimeout(1500)
  await page.screenshot({ path: join(out, `${name}-${scheme}-${label}.png`) })
}

for (const [name, host, route] of [
  ['rw', 'rw.ec1.mateu.io', '/booking/bookings'],
  ['rw-console', 'rw-console.ec1.mateu.io', '/mapping/dictionary'],
] as const) {
  test(`${name}: header + menu`, async ({ page }) => {
    test.setTimeout(240000)
    const c = CONSOLES.find((x) => x.host === host)!
    await swap(page, host)
    await signIn(page, c)
    await page.waitForTimeout(15000)
    await shoot(page, `${name}-home`, 'light')
    await shoot(page, `${name}-home`, 'dark')
    await page.goto(`https://${host}${route}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(25000)
    await shoot(page, `${name}-section`, 'light')
    await shoot(page, `${name}-section`, 'dark')
    const band = await page.evaluate(() => {
      const b = document.querySelector('.mateu-subheader') as HTMLElement | null
      if (!b) return null
      const active = Array.from(b.querySelectorAll('.mateu-nav-active')).map((e) => (e.textContent || '').trim())
      const items = Array.from(b.querySelectorAll('[data-nav-item]')).map((e) => (e.textContent || '').trim().replace(/\s+/g, ' '))
      const title = (b.querySelector('.mateu-subheader-title')?.textContent || '').trim()
      return { title, items, active, shadow: getComputedStyle(b).boxShadow, titleColor: getComputedStyle(b.querySelector('.mateu-subheader-title')!).color }
    })
    console.log(name, label, JSON.stringify(band))
    // a group opens its dropdown
    const group = page.locator('.mateu-subheader oj-menu-button').first()
    if (await group.count()) {
      await group.click()
      await page.waitForTimeout(1500)
      await page.screenshot({ path: join(out, `${name}-dropdown-light-${label}.png`) })
    }
  })
}

test('front office keeps its bottom tab bar', async ({ page }) => {
  test.setTimeout(240000)
  const host = 'front.ec1.mateu.io'
  await swap(page, host)
  await signIn(page, { name: 'fo', host, plane: 'data', renderer: 'redwood', menus: [], screens: [] })
  await page.waitForTimeout(20000)
  const seen = await page.evaluate(() => ({
    subheader: !!document.querySelector('.mateu-subheader'),
    tabs: !!document.querySelector('oj-sp-in-app-navigation'),
  }))
  console.log('fo', label, JSON.stringify(seen))
  await shoot(page, 'fo-home', 'light')
})

test('rw: unavailable section, chat drawer, scroll and a phone width', async ({ page }) => {
  test.setTimeout(240000)
  const host = 'rw.ec1.mateu.io'
  const c = CONSOLES.find((x) => x.host === host)!
  await swap(page, host)
  // ERP's pod does not answer its menu: the section stays, disabled, saying why
  await page.route(`https://${host}/_erp/mateu/**`, (route) => {
    const body = route.request().postData() || ''
    return body.includes('"consumedRoute":"_empty"') ? route.abort('connectionrefused') : route.fallback()
  })
  await signIn(page, c)
  await page.waitForTimeout(15000)
  const erp = await page.evaluate(() => {
    const el = Array.from(document.querySelectorAll('.mateu-subheader [data-nav-item]')).find((e) => (e.textContent || '').includes('ERP')) as HTMLElement | undefined
    return el ? { text: (el.textContent || '').trim(), disabled: el.hasAttribute('disabled') || (el as any).disabled, title: el.getAttribute('title') } : null
  })
  console.log('erp down', JSON.stringify(erp))
  await page.screenshot({ path: join(out, `rw-erp-unavailable-light-${label}.png`) })
  await page.goto(`https://${host}/booking/bookings`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(25000)
  const bandBox = () => page.evaluate(() => {
    const r = document.querySelector('.mateu-subheader')!.getBoundingClientRect()
    return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }
  })
  console.log('band', JSON.stringify(await bandBox()))
  await page.mouse.move(700, 600)
  await page.mouse.wheel(0, 800)
  await page.waitForTimeout(1000)
  console.log('band after scroll', JSON.stringify(await bandBox()), 'scrollY', await page.evaluate(() => window.scrollY))
  await page.screenshot({ path: join(out, `rw-scrolled-light-${label}.png`) })
  await page.mouse.wheel(0, -800)
  const chat = page.locator('#mateuChatToggle')
  if (await chat.count()) {
    await page.evaluate(() => window.scrollTo(0, 0))
    await chat.click()
    await page.waitForTimeout(2000)
    console.log('band with chat open', JSON.stringify(await bandBox()), 'composer bottom', await page.evaluate(() => Math.round(document.querySelector('.mateu-chat-composer')?.getBoundingClientRect().bottom ?? -1)))
    await page.screenshot({ path: join(out, `rw-chat-open-light-${label}.png`) })
    await chat.click()
  }
  for (const width of [1280, 1024, 800]) {
    await page.setViewportSize({ width, height: 900 })
    await page.waitForTimeout(1500)
    console.log('gutter at', width, JSON.stringify(await page.evaluate(() => ({
      title: Math.round(document.querySelector('.mateu-subheader-title')!.getBoundingClientRect().x),
      heading: Math.round((Array.from(document.querySelectorAll('h1')).find((h) => (h.textContent || '').includes('Bookings'))?.getBoundingClientRect().x) ?? -1),
      colorScheme: getComputedStyle(document.documentElement).colorScheme,
    }))))
  }
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(2000)
  console.log('band at 390', JSON.stringify(await bandBox()), 'page scrollWidth', await page.evaluate(() => document.documentElement.scrollWidth))
  await page.screenshot({ path: join(out, `rw-phone-light-${label}.png`) })
})

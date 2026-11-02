import { test, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { existsSync, readdirSync, readFileSync, statSync } from 'fs'
import { join, extname } from 'path'

// READ-ONLY: the chat mic shortcut (Ctrl+Shift+M) with a LOCAL Mateu build swapped in client-side.
// SpeechRecognition is stubbed: start/stop are recorded, nothing is dictated, no message is sent.
const VAADIN = process.env.MATEU_BUNDLE!
const RW = process.env.RW_STATIC!
const out = '/tmp/mic-shots'
const TYPES: Record<string, string> = { '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml', '.png': 'image/png' }

const stub = (page: Page) => page.addInitScript(() => {
  const w = window as any
  w.__mic = []
  class FakeRec {
    lang = ''; interimResults = false; onresult: any; onend: any; onerror: any
    start() { w.__mic.push('start') }
    stop() { w.__mic.push('stop'); setTimeout(() => this.onend && this.onend(), 10) }
  }
  w.SpeechRecognition = FakeRec; w.webkitSpeechRecognition = FakeRec
})
const calls = (page: Page) => page.evaluate(() => (window as any).__mic.join(','))
const deepFind = (page: Page, sel: string) => page.evaluate((sel) => {
  const walk = (root: any): Element | null => {
    const hit = root.querySelector(sel); if (hit) return hit
    for (const el of root.querySelectorAll('*')) if (el.shadowRoot) { const r = walk(el.shadowRoot); if (r) return r }
    return null
  }
  const el = walk(document)
  return el ? { title: el.getAttribute('title'), aria: el.getAttribute('aria-label'), keys: el.getAttribute('aria-keyshortcuts'), pressed: el.getAttribute('aria-pressed'), text: (el.textContent || '').trim() } : null
}, sel)

test('vaadin: Ctrl+Shift+M toggles the chat mic', async ({ page }) => {
  test.setTimeout(300000)
  await stub(page)
  await page.route(/\/assets\/[\w-]+\.(js|css)(\?.*)?$/, r => {
    const name = new URL(r.request().url()).pathname.split('/').pop()!
    const local = join(VAADIN, name)
    if (!existsSync(local)) return r.continue()
    return r.fulfill({ body: readFileSync(local), contentType: name.endsWith('.css') ? 'text/css' : 'text/javascript' })
  })
  page.on('pageerror', (e) => console.log('PAGEERROR', String(e).slice(0, 300)))
  await page.setViewportSize({ width: 1440, height: 900 })
  await signIn(page, CONSOLES.find(c => c.name === 'data · vaadin')!)
  await page.waitForTimeout(6000)
  await page.keyboard.press('Control+Shift+M')
  console.log('VAADIN closed panel ->', JSON.stringify(await calls(page)))
  await page.locator('.mateu-chat-toggle').first().click()
  await page.waitForTimeout(2000)
  console.log('VAADIN mic before', JSON.stringify(await deepFind(page, '.input-bar .mic-btn:not([title="Adjuntar ficheros"])')))
  await page.keyboard.press('Control+Shift+M')
  await page.waitForTimeout(500)
  console.log('VAADIN after 1st ->', await calls(page), JSON.stringify(await deepFind(page, '.input-bar .mic-btn:not([title="Adjuntar ficheros"])')))
  await page.screenshot({ path: join(out, 'vaadin-listening.png') })
  // focus in the message field
  await page.locator('mateu-chat .msg-input').click()
  await page.keyboard.press('Control+Shift+M')
  await page.waitForTimeout(500)
  console.log('VAADIN after 2nd (from input) ->', await calls(page), JSON.stringify(await deepFind(page, '.input-bar .mic-btn:not([title="Adjuntar ficheros"])')))
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur())
  await page.keyboard.press('Control+Shift+M')
  await page.waitForTimeout(300)
  console.log('VAADIN after 3rd (focus on body) ->', await calls(page))
  await page.locator('.mateu-chat-toggle').first().click()
  await page.waitForTimeout(1000)
  await page.keyboard.press('Control+Shift+M')
  await page.waitForTimeout(300)
  console.log('VAADIN after closing the panel ->', await calls(page))
  console.log('VAADIN input value', JSON.stringify(await page.locator('mateu-chat .msg-input').inputValue()))
})

test('redwood: Ctrl+Shift+M toggles the chat mic', async ({ page }) => {
  test.setTimeout(240000)
  await stub(page)
  const host = 'rw.ec1.mateu.io'
  if (RW) await page.route(`https://${host}/**`, (route) => {
    const url = new URL(route.request().url())
    let p = url.pathname.replace(/^\//, ''); try { p = decodeURIComponent(p) } catch { return route.continue() }
    if (!p || p.startsWith('mateu/') || p.startsWith('_') || route.request().method() !== 'GET') return route.continue()
    const local = readdirSync(RW).find((d) => d.startsWith('version_'))
    const file = join(RW, local ? p.replace(/^version_\d+\//, local + '/') : p)
    if (existsSync(file) && statSync(file).isFile()) return route.fulfill({ body: readFileSync(file), contentType: TYPES[extname(file)] ?? 'application/octet-stream' })
    return route.continue()
  })
  page.on('pageerror', (e) => console.log('PAGEERROR', String(e).slice(0, 300)))
  page.on('console', (m) => { if (m.type() === 'error') console.log('CONSOLE', m.text().slice(0, 300)) })
  page.on('response', (r) => { if (r.status() >= 400) console.log('HTTP', r.status(), r.url().slice(0, 200)) })
  page.on('requestfailed', (r) => console.log('REQFAILED', r.url().slice(0, 200), r.failure()?.errorText))
  await page.setViewportSize({ width: 1440, height: 900 })
  await signIn(page, CONSOLES.find(c => c.name === 'data · redwood')!)
  await page.waitForTimeout(30000)
  await page.keyboard.press('Control+Shift+M')
  console.log('RW closed panel ->', JSON.stringify(await calls(page)))
  await page.screenshot({ path: join(out, 'redwood-home.png') })
  await page.locator('#mateuChatToggle').click({ timeout: 20000 })
  await page.waitForTimeout(3000)
  const mic = () => page.evaluate(() => {
    const host = document.querySelector('#mateuChatMic'); if (!host) return null
    const b = host.querySelector('button')
    return { hostText: (host.textContent || '').trim(), btnAria: b?.getAttribute('aria-label'), btnTitle: b?.getAttribute('title'), hostTitle: host.getAttribute('title'), btnLabelledby: b?.getAttribute('aria-labelledby'), hostKeys: host.getAttribute('aria-keyshortcuts'), btnKeys: b?.getAttribute('aria-keyshortcuts'), label: b?.getAttribute('aria-label') ?? (host.textContent || '').trim(), title: b?.getAttribute('title') ?? host.getAttribute('title'), chroming: host.getAttribute('chroming') ?? (host as any).chroming, focus: document.activeElement?.tagName + '#' + (document.activeElement?.closest('[id]')?.id ?? '') }
  })
  console.log('RW mic before', JSON.stringify(await mic()))
  await page.keyboard.press('Control+Shift+M')   // focus is in the chat input after opening
  await page.waitForTimeout(800)
  console.log('RW after 1st ->', await calls(page), JSON.stringify(await mic()))
  await page.screenshot({ path: join(out, 'redwood-listening.png') })
  await page.keyboard.press('Control+Shift+M')
  await page.waitForTimeout(800)
  console.log('RW after 2nd ->', await calls(page), JSON.stringify(await mic()))
  // focus outside the chat (the page's body)
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur())
  await page.keyboard.press('Control+Shift+M')
  await page.waitForTimeout(800)
  console.log('RW after 3rd (focus on body) ->', await calls(page), JSON.stringify(await mic()))
  await page.locator('#mateuChatMic').hover(); await page.waitForTimeout(1500)
  await page.screenshot({ path: join(out, 'redwood-tooltip.png') })
})

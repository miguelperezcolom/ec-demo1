import { chromium } from '@playwright/test'
const url = process.argv[2]
const out = process.argv[3]
const b = await chromium.launch()
const p = await b.newPage({ viewport: { width: 1600, height: 1000 } })
const log = []
p.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') log.push(`console.${m.type()}: ${m.text().slice(0, 300)}`) })
p.on('pageerror', e => log.push('pageerror: ' + String(e).slice(0, 300)))
p.on('requestfailed', r => log.push(`failed: ${r.url()} ${r.failure()?.errorText}`))
p.on('response', r => { if (r.status() >= 400) log.push(`${r.status()} ${r.url()}`) })
await p.goto(url, { waitUntil: 'domcontentloaded' })
for (let i = 0; i < 12; i++) {
  await p.waitForTimeout(5000)
  if (p.url().includes('auth.localhost') && await p.locator('#username').count()) {
    await p.fill('#username', 'demo'); await p.fill('#password', process.env.DEMO_PASSWORD); await p.click('#kc-login')
    log.push('logged in')
  }
}
log.push('final url: ' + p.url())
log.push('text: ' + (await p.locator('body').innerText()).slice(0, 500).replace(/\n+/g, ' | '))
await p.screenshot({ path: out })
console.log(log.join('\n'))
await b.close()

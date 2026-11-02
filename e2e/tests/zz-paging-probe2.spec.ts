import { test } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { readFileSync } from 'fs'
// READ-ONLY probe across every Vaadin listing: page 0 -> next, log server pages and the pager.
test('paging probe all', async ({ page }) => {
  test.setTimeout(1800000)
  if (process.env.MATEU_BUNDLE) await page.route('**/assets/mateu-vaadin.js', r => r.fulfill({ body: readFileSync(process.env.MATEU_BUNDLE!), contentType: 'text/javascript' }))
  let log: string[] = []
  page.on('response', async r => {
    try {
      if (!r.url().includes('/mateu/') || r.request().method() !== 'POST') return
      const t = await r.text(); if (!t.includes('totalElements')) return
      const b = JSON.parse(r.request().postData() ?? '{}')
      const ss = b.parameters?._searchState ?? {}
      const out: string[] = []
      for (const k of ['pageNumber', 'pageSize', 'totalElements']) out.push(k + '=' + [...t.matchAll(new RegExp('"' + k + '":(\\d+)', 'g'))].map(x => x[1]).join(','))
      log.push(`  action=${b.actionId} req(page=${ss.page},size=${ss.size}) -> ${out.join(' ')}`)
    } catch (e) { }
  })
  const pagerText = () => page.evaluate(() => {
    const find = (root: any): any => { for (const el of root.querySelectorAll('*')) { if (el.tagName === 'MATEU-PAGINATION') return el; if (el.shadowRoot) { const f = find(el.shadowRoot); if (f) return f } } return null }
    const p = find(document); return p ? (p.shadowRoot?.textContent ?? '').replace(/\s+/g, ' ').trim() + ` [props total=${p.totalElements} size=${p.pageSize} n=${p.pageNumber}]` : 'NO PAGER'
  })
  for (const cname of ['data · vaadin', 'control · vaadin']) {
    const c = CONSOLES.find(x => x.name === cname)!
    await signIn(page, c)
    for (const s of c.screens.filter(s => s.listing !== false)) {
      log = []
      await page.goto('https://' + c.host + s.route, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(12000)
      const p0 = await pagerText()
      let p1 = '-'
      const next = page.getByTestId('page-next')
      if (await next.count() > 0 && await next.first().isEnabled()) {
        await next.first().click(); await page.waitForTimeout(7000); p1 = await pagerText()
      }
      console.log(`${cname} ${s.route}\n  P0: ${p0}\n  P1: ${p1}\n${log.join('\n')}`)
    }
  }
})

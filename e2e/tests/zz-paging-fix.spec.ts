import { test, expect, Page } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
import { readFileSync } from 'fs'

// READ-ONLY: the Vaadin control console, paging a listing after re-entering it from the menu and
// after a reload on ?page=N. With MATEU_BUNDLE set, the shell's mateu-vaadin.js is swapped
// client-side for that local build (nothing is deployed).
const bundle = process.env.MATEU_BUNDLE

const pagerAndRows = (page: Page) => page.evaluate(() => {
  const all: any[] = []
  const walk = (root: any) => { for (const el of root.querySelectorAll('*')) { all.push(el); if (el.shadowRoot) walk(el.shadowRoot) } }
  walk(document)
  const p = all.find(e => e.tagName === 'MATEU-PAGINATION')
  const grid = all.find(e => e.tagName === 'VAADIN-GRID')
  return { text: (p?.shadowRoot?.textContent ?? '').replace(/\s+/g, ' ').trim(), rows: (grid?.items ?? []).map((i: any) => i.crsCode) }
})

test('paging a Vaadin listing shows the asked page under a stable pager', async ({ page }) => {
  test.setTimeout(600000)
  if (bundle) await page.route('**/assets/mateu-vaadin.js', r => r.fulfill({ body: readFileSync(bundle), contentType: 'text/javascript' }))
  const sizes: string[] = []
  page.on('request', r => { if (r.method() === 'POST' && r.url().includes('/_mapping/')) { try { const b = JSON.parse(r.postData() ?? '{}'); if (b.actionId === 'search') sizes.push(String(b.parameters?._searchState?.size ?? b.componentState?.size)) } catch {} } })
  const c = CONSOLES.find(x => x.name === 'control · vaadin')!
  const menu = async (m: string, e: string) => { await page.getByText(m, { exact: true }).first().click(); await page.waitForTimeout(1500); await page.getByText(e, { exact: true }).first().click(); await page.waitForTimeout(12000) }
  const next = async () => { await page.getByTestId('page-next').click(); await page.waitForTimeout(7000) }
  await signIn(page, c)
  await page.goto('https://' + c.host + '/', { waitUntil: 'domcontentloaded' }); await page.waitForTimeout(15000)
  await menu('Mapping', 'Dictionary')
  const p1 = await pagerAndRows(page)
  await next()
  const p2 = await pagerAndRows(page)
  await menu('Audit', 'Audited actions')
  await menu('Mapping', 'Dictionary')
  await next()
  const again = await pagerAndRows(page)
  console.log('first visit p1', JSON.stringify(p1)); console.log('first visit p2', JSON.stringify(p2)); console.log('re-entered p2', JSON.stringify(again))
  expect(again.text).toBe(p2.text)
  expect(again.rows).toEqual(p2.rows)
  await page.reload({ waitUntil: 'domcontentloaded' }); await page.waitForTimeout(15000)
  const reloaded = await pagerAndRows(page)
  console.log('reloaded on ?page=1', JSON.stringify(reloaded), 'url', page.url())
  expect(reloaded.rows).toEqual(p2.rows)
  console.log('search sizes', sizes.join(','))
})

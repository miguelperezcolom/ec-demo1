import { test } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
test('flow 1: filter by integration and ask the agent', async ({ page }) => {
  test.setTimeout(500000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const c = CONSOLES.find(c => c.name === 'control · vaadin')!
  await signIn(page, c)
  await page.goto('https://' + c.host + '/mapping/dictionary', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(10000)
  await page.getByPlaceholder('Search').first().click(); await page.waitForTimeout(1500)
  await page.getByText('Integration', { exact: true }).first().click(); await page.waitForTimeout(1500)
  const f = page.getByPlaceholder('Integration').first(); await f.fill('MRU01'); await page.waitForTimeout(1500)
  await page.screenshot({ path: '/tmp/claude-501/demo3/6b-typed.png' })
  await page.getByRole('button', { name: 'Apply', exact: true }).first().click(); await page.waitForTimeout(8000)
  await page.screenshot({ path: '/tmp/claude-501/demo3/7-filtered.png' })
  console.log('url: ' + page.url())
  await page.getByRole('button', { name: 'Ask the agent', exact: true }).first().click(); await page.waitForTimeout(3000)
  await page.screenshot({ path: '/tmp/claude-501/demo3/8-asked.png' })
  console.log('body: ' + (await page.locator('body').innerText()).split('\n').filter(l => /agent|propos|MRU01/i.test(l)).slice(0, 5).join(' / '))
})

import { test } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
test('flow 1: open the notice and look at the dictionary', async ({ page }) => {
  test.setTimeout(400000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const c = CONSOLES.find(c => c.name === 'control · vaadin')!
  await signIn(page, c)
  await page.goto('https://' + c.host + '/inbox/pending', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(10000)
  await page.getByRole('button', { name: 'Open', exact: true }).first().click(); await page.waitForTimeout(12000)
  console.log('url: ' + page.url())
  await page.screenshot({ path: '/tmp/claude-501/demo3/4-dictionary.png' })
  await page.getByPlaceholder('Search').first().click(); await page.waitForTimeout(1500)
  await page.screenshot({ path: '/tmp/claude-501/demo3/5-filters.png' })
  console.log('buttons: ' + (await page.getByRole('button').allInnerTexts()).filter(t => t.trim()).join(' | '))
})

import { test } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
test('flow 1: status filter', async ({ page }) => {
  test.setTimeout(300000)
  await page.setViewportSize({ width: 1440, height: 1100 })
  const c = CONSOLES.find(c => c.name === 'control · vaadin')!
  await signIn(page, c)
  await page.goto('https://' + c.host + '/mapping/dictionary?integration=MRU01', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(10000)
  await page.locator('input[placeholder="Search"], [part="input-field"]').first().click().catch(() => {}); await page.waitForTimeout(1500)
  await page.screenshot({ path: '/tmp/claude-501/demo3/9a-menu.png' })
  await page.getByText('Status', { exact: true }).last().click(); await page.waitForTimeout(1500)
  await page.screenshot({ path: '/tmp/claude-501/demo3/9b-status.png' })
})

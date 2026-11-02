import { test } from '@playwright/test'
test('fo stay', async ({ page }) => {
  test.setTimeout(150000)
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto('https://front.ec1.mateu.io/reservas/EZQYHT', { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('#username', { timeout: 30000 })
  await page.fill('#username', 'demo'); await page.fill('#password', 'demo')
  await page.locator('#kc-login').click()
  await page.waitForTimeout(35000)
  await page.screenshot({ path: process.env.CLAUDE_JOB_DIR + '/tmp/fo-stay.png', fullPage: false })
})

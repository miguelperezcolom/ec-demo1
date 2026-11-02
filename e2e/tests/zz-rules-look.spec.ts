import { test } from '@playwright/test'
test('rules', async ({ page }) => {
  test.setTimeout(120000)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('https://console.ec1.mateu.io/registro/reglas', { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('#username', { timeout: 30000 })
  await page.fill('#username', 'demo'); await page.fill('#password', 'demo')
  await page.locator('#kc-login').click()
  await page.waitForTimeout(15000)
  await page.screenshot({ path: process.env.CLAUDE_JOB_DIR + '/tmp/rules.png' })
})

import { test } from '@playwright/test'
const OUT = '/Users/mguel/.claude/jobs/04423fc7/tmp/noshow'
test('no show from the front office', async ({ page }) => {
  test.setTimeout(400000)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('https://front.ec1.mateu.io/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(4000)
  if (page.url().includes('/realms/')) {
    await page.fill('#username', 'demo'); await page.fill('#password', 'demo'); await page.click('#kc-login')
  }
  await page.waitForTimeout(8000)
  await page.goto('https://front.ec1.mateu.io/reservas/KTQVZJ', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(15000)
  await page.screenshot({ path: OUT + '/1-reserva.png', fullPage: true })
  for (let i = 0; i < 2; i++) {
    const b = page.getByText('No show', { exact: true })
    console.log('No show buttons/labels: ' + await b.count())
    const btn = page.getByRole('button', { name: /^No show$/ })
    console.log('buttons: ' + await btn.count())
    await btn.first().click(); await page.waitForTimeout(8000)
    await page.screenshot({ path: OUT + `/2-noshow-${i + 1}.png`, fullPage: true })
    console.log('text: ' + (await page.locator('body').innerText()).slice(0, 300).replace(/\n/g, ' | '))
  }
  await page.waitForTimeout(30000)
  await page.reload({ waitUntil: 'domcontentloaded' }); await page.waitForTimeout(15000)
  await page.screenshot({ path: OUT + '/3-after.png', fullPage: true })
})

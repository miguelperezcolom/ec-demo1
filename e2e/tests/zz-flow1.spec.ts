import { test } from '@playwright/test'
import { CONSOLES, signIn } from './consoles'
test('flow 1 up to asking the agent', async ({ page }) => {
  test.setTimeout(700000)
  await page.setViewportSize({ width: 1440, height: 900 })
  const d = CONSOLES.find(c => c.name === 'data · vaadin')!
  await signIn(page, d)
  await page.goto('https://' + d.host + '/booking/bookings', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(10000)
  await page.getByText('+ 10 reservas demo').first().click(); await page.waitForTimeout(2500)
  await page.getByRole('button', { name: 'Crear', exact: true }).first().click(); await page.waitForTimeout(25000)
  await page.screenshot({ path: '/tmp/claude-501/demo4/1-bookings.png' })
  const c = CONSOLES.find(c => c.name === 'control · vaadin')!
  await signIn(page, c)
  await page.goto('https://' + c.host + '/integrations/registry', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(10000)
  await page.getByRole('button', { name: 'New', exact: true }).first().click(); await page.waitForTimeout(6000)
  const pick = async (label: string, text: RegExp) => {
    await page.getByLabel(label).first().click(); await page.waitForTimeout(2500)
    await page.getByRole('option').filter({ hasText: text }).first().click(); await page.waitForTimeout(1500)
  }
  await pick('Crs hotel code', /MRU01/)
  await pick('Opera property', /XMAR/)
  await page.getByRole('button', { name: 'Save', exact: true }).first().click(); await page.waitForTimeout(45000)
  await page.goto('https://' + c.host + '/inbox/pending', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(12000)
  await page.screenshot({ path: '/tmp/claude-501/demo4/2-inbox.png' })
  await page.getByRole('button', { name: 'Open', exact: true }).first().click(); await page.waitForTimeout(12000)
  console.log('after Open: ' + page.url())
  await page.screenshot({ path: '/tmp/claude-501/demo4/3-dictionary.png' })
  await page.getByRole('button', { name: 'Ask the agent', exact: true }).first().click(); await page.waitForTimeout(90000)
  await page.screenshot({ path: '/tmp/claude-501/demo4/4-after-agent.png' })
})

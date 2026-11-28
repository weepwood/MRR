import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 公共浮层材质及关闭按钮对齐`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.route('**/api/v1/archive-search-history**', route => route.fulfill({ json: { code: 200, data: [] } }))
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette: palette, colorScheme: theme, enableMotion: false }, menu: { mode: 'single' }, layout: { enableMobileAdaptation: true } })), { palette, theme })
    await page.goto('/')
    const trigger = page.getByRole('button', { name: '最近查询', exact: true })
    await trigger.click()
    const panel = page.getByRole('dialog', { name: '最近查询' })
    await expect(panel).toBeVisible()
    await expect(panel.getByText('暂无查询记录', { exact: true })).toBeVisible()
    await expect(panel).toHaveClass(/mrr-light-popover/)
    const measure = await panel.evaluate((el) => {
      const s = getComputedStyle(el)
      const title = el.querySelector('.recent-lookups__title')!.getBoundingClientRect()
      const close = el.querySelector('.home-history-close')!.getBoundingClientRect()
      return { radius: s.borderRadius, background: s.backgroundColor, delta: Math.abs(title.y + title.height / 2 - close.y - close.height / 2), closeHeight: close.height }
    })
    expect(Number.parseFloat(measure.radius)).toBeGreaterThan(4)
    expect(measure.delta).toBeLessThanOrEqual(1)
    expect(measure.closeHeight).toBe(28)
    await page.screenshot({ path: info.outputPath('history.png') })
    await panel.getByRole('button', { name: '关闭最近查询' }).click()
    await expect(panel).toBeHidden()
    await expect(trigger).toBeFocused()
    await page.locator('.account-area .account-trigger').click()
    await page.getByRole('menuitem', { name: '个人设置' }).click()
    const dialog = page.getByRole('dialog', { name: '个人设置' })
    await expect(dialog).toBeVisible()
    const check = async () => {
      const result = await dialog.evaluate((el) => {
        const h = el.querySelector('.el-dialog__header')!.getBoundingClientRect()
        const c = el.querySelector('.el-dialog__headerbtn')!.getBoundingClientRect()
        const i = el.querySelector('.el-dialog__close')!.getBoundingClientRect()
        return { header: Math.abs(h.y + h.height / 2 - c.y - c.height / 2), icon: Math.abs(c.y + c.height / 2 - i.y - i.height / 2), horizontal: Math.abs(c.x + c.width / 2 - i.x - i.width / 2) }
      })
      expect(result.header).toBeLessThanOrEqual(1)
      expect(result.icon).toBeLessThanOrEqual(1)
      expect(result.horizontal).toBeLessThanOrEqual(1)
    }
    await check()
    await page.screenshot({ path: info.outputPath('profile.png') })
    await page.setViewportSize({ width: 375, height: 900 })
    await check()
    await dialog.locator('.el-dialog__headerbtn').click()
    await expect(dialog).toBeHidden()
  })
}

test('主动退出需确认，取消和Escape不清除会话', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: false }, menu: { mode: 'single' } })))
  let requests = 0
  await page.route('**/api/v1/auth/logout', (route) => { requests++; return route.fulfill({ json: { code: 200 } }) })
  await page.goto('/')
  const account = page.locator('.account-area .account-trigger')
  await expect(account).toBeVisible()
  const sessionPresent = () => page.evaluate(() => Boolean(localStorage.getItem('mrr:auth:token')))
  expect(await sessionPresent()).toBe(true)
  const open = async () => {
    await account.click()
    await page.getByRole('menuitem', { name: '退出登录' }).click()
    const dialog = page.getByRole('dialog', { name: '确认退出登录？' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByRole('button', { name: '退出登录', exact: true })).toHaveClass(/el-button--danger/)
    return dialog
  }
  let dialog = await open()
  await page.screenshot({ path: info.outputPath('confirm-logout.png') })
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page).toHaveURL(/\/$/)
  expect(await sessionPresent()).toBe(true)
  expect(requests).toBe(0)
  dialog = await open()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  expect(await sessionPresent()).toBe(true)
  expect(requests).toBe(0)
  dialog = await open()
  await dialog.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login(?:\?|$)/)
  expect(await sessionPresent()).toBe(false)
  // Demo mode clears local session without calling the revocation endpoint.
  expect(requests).toBeLessThanOrEqual(1)
})

import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 实际浮层与长文本视觉`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { themePalette: palette, colorScheme: theme, enableMotion: false },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
      tabbar: { enable: true },
    })), { palette, theme })
    await page.goto('/users')
    const account = page.getByRole('button', { name: /账号菜单：/ }).filter({ visible: true }).first()
    await account.click()
    const menu = page.locator('.el-popper.mrr-account-menu:visible')
    await expect(menu).toBeVisible()
    await menu.screenshot({ path: info.outputPath('account.png') })
    await page.keyboard.press('Escape')
    await expect(account).toBeFocused()
    const tab = page.locator('.tabbar .tab').filter({ hasText: '用户管理' })
    await tab.locator('.tab-content').click({ button: 'right' })
    const context = page.locator('.el-popper.mrr-context-menu:visible')
    await expect(context).toBeVisible()
    await context.screenshot({ path: info.outputPath('context.png') })
    await page.keyboard.press('Escape')
    await page.goto('/records')
    await page.locator('.records-filter-bar .el-select__wrapper').click()
    const options = page.locator('.el-select__popper:visible').last()
    await expect(options).toBeVisible()
    await options.screenshot({ path: info.outputPath('select.png') })
    await page.keyboard.press('Escape')
    await page.goto('/runtime-errors')
    const trigger = page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' }).getByRole('button', { name: '详情', exact: true })
    await trigger.click()
    const dialog = page.getByRole('dialog', { name: '运行错误详情' })
    await expect(dialog).toBeVisible()
    await page.setViewportSize({ width: 375, height: 900 })
    await expect.poll(() => dialog.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
    await page.screenshot({ path: info.outputPath('dialog-mobile.png') })
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' }).getByRole('button', { name: '更多操作' })).toBeFocused()
    await page.goto('/logs')
    const disclosure = page.getByRole('button', { name: /高级筛选/ })
    await disclosure.click()
    await expect(page.getByLabel('客户端 IP', { exact: true })).toBeVisible()
    await page.screenshot({ path: info.outputPath('filter-mobile.png') })
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  })
}

test.describe('粗指针浮层操作尺寸', () => {
  test.use({ hasTouch: true, viewport: { width: 390, height: 900 } })
  test('账户菜单、选择器及详情关闭目标达到44px', async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { enableMotion: false },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })))
    await page.goto('/users')
    await page.getByRole('button', { name: '切换侧栏导航' }).click()
    await page.getByRole('button', { name: /账号菜单：/ }).filter({ visible: true }).first().click()
    const menu = page.locator('.mrr-account-menu:visible')
    await expect(menu).toBeVisible()
    expect(await menu.getByRole('menuitem').evaluateAll(els => els.every(el => el.getBoundingClientRect().height >= 44))).toBe(true)
    await menu.screenshot({ path: info.outputPath('touch-account.png') })
    await page.keyboard.press('Escape')
    await page.goto('/records')
    await page.locator('.records-filter-bar .el-select__wrapper').click()
    const select = page.locator('.el-select__popper:visible').last()
    await expect(select).toBeVisible()
    expect(await select.getByRole('option').evaluateAll(els => els.every(el => el.getBoundingClientRect().height >= 44))).toBe(true)
    await select.screenshot({ path: info.outputPath('touch-select.png') })
    await page.keyboard.press('Escape')
    await page.goto('/runtime-errors')
    const row = page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' })
    await row.getByRole('button', { name: '更多操作' }).click()
    await page.getByRole('menuitem', { name: '详情', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '运行错误详情' })
    await expect(dialog).toBeVisible()
    const close = dialog.locator('.el-dialog__headerbtn')
    expect((await close.boundingBox())!.height).toBeGreaterThanOrEqual(44)
    expect((await close.boundingBox())!.width).toBeGreaterThanOrEqual(44)
    await close.click()
    await expect(dialog).toBeHidden()
  })
})

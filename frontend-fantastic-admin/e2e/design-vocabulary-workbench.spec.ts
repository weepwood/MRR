import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 窄屏定位影像与完整病案摘要`, async ({ page }) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { themePalette: palette, colorScheme: theme, enableMotion: false },
      layout: { enableMobileAdaptation: true },
    })), { palette, theme })
    await page.setViewportSize({ width: 390, height: 900 })
    await page.goto('/archive-workbench')
    const search = page.getByRole('textbox', { name: '病案查询值' })
    await search.fill('26070000')
    await search.press('Enter')
    const shortcut = page.getByRole('button', { name: '定位影像核对' })
    const section = page.getByRole('region', { name: '影像核对分区' })
    const counts = page.getByRole('region', { name: '关联记录数' })
    await expect(shortcut).toBeVisible()
    await expect(counts.locator('.mrr-stat-tile')).toHaveCount(5)
    await expect(counts.getByText('未关联候选扫描')).toBeVisible()
    await expect.poll(() => counts.evaluate(el => el.getBoundingClientRect().height)).toBeLessThan(270)
    await expect.poll(() => counts.locator('.mrr-stat-tile__label').evaluateAll(els => els.every(el => el.scrollWidth <= el.clientWidth + 1))).toBe(true)
    await expect(page.locator('.archive-detail-stack .el-alert')).not.toHaveCount(0)
    await shortcut.focus()
    await shortcut.press('Enter')
    await expect(section).toBeFocused()
    await expect.poll(() => section.evaluate(el => Math.abs(el.getBoundingClientRect().top))).toBeLessThan(160)
    await section.getByRole('group', { name: '有效影像列表' }).getByRole('button').first().click()
    const image = page.getByAltText('待核对影像')
    await expect.poll(() => image.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0)).toBe(true)
    const source = await image.getAttribute('src')
    await page.getByRole('button', { name: '专注原图' }).click()
    await expect(image).toHaveAttribute('src', source!)
    await expect(page.locator('.archive-detail-stack .el-alert')).not.toHaveCount(0)
    const list = section.getByRole('group', { name: '有效影像列表' })
    const disclosure = section.locator('.scan-list-disclosure')
    const viewport = section.getByRole('region', { name: '原图滚动查看区' })
    const distance = () => viewport.evaluate(el => el.getBoundingClientRect().top - el.closest('[data-archive-section="images"]')!.getBoundingClientRect().top)
    const before = await distance()
    await disclosure.focus()
    await disclosure.press('Enter')
    await expect(list).toBeHidden()
    await expect(disclosure).toHaveAttribute('aria-expanded', 'false')
    await expect(disclosure).toBeFocused()
    await expect.poll(async () => before - await distance()).toBeGreaterThan(80)
    await expect(image).toHaveAttribute('src', source!)
    await disclosure.press('Enter')
    await expect(list).toBeVisible()
    await expect(disclosure).toHaveAttribute('aria-expanded', 'true')
    await disclosure.press('Enter')
    await page.setViewportSize({ width: 1440, height: 900 })
    await expect(list).toBeVisible()
    await expect(list.getByRole('button').first()).toBeFocused()
    await expect(shortcut).toBeHidden()
    await expect(page.getByRole('navigation', { name: '病案详情分区' })).toBeVisible()
    await expect(image).toHaveAttribute('src', source!)
    await page.getByRole('button', { name: '清空检索' }).click()
    await expect(shortcut).toHaveCount(0)
    await expect(section).toHaveCount(0)
  })
}

test('仅具备系统读取权限时不出现影像定位入口与影像分区', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.route('**/api/v1/auth/me', route => route.fulfill({
    json: { code: 200, data: { id: 20, username: 'synthetic-system-reader', roleCode: 'OPERATOR', status: 'active', permissions: ['system:read'], mustChangePassword: false } },
  }))
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 390, height: 900 })
  await page.goto('/archive-workbench')
  const search = page.getByRole('textbox', { name: '病案查询值' })
  await search.fill('26070000')
  await search.press('Enter')
  await expect(page.locator('.archive-header-card')).toBeVisible()
  await expect(page.getByRole('button', { name: '定位影像核对' })).toHaveCount(0)
  await expect(page.getByRole('region', { name: '影像核对分区' })).toHaveCount(0)
  await expect(page.getByRole('navigation', { name: '病案详情分区' }).getByRole('button', { name: '文件完整性' })).toHaveCount(0)
})

test('运行错误只读详情键盘焦点限制及Escape返回', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.goto('/runtime-errors')
  const trigger = page.getByRole('row').filter({ hasText: 'DEMO-ERR-001' }).getByRole('button', { name: '详情', exact: true })
  await trigger.focus()
  await trigger.press('Enter')
  const dialog = page.getByRole('dialog', { name: '运行错误详情' })
  await expect(dialog).toBeVisible()
  await expect.poll(() => dialog.evaluate(el => el.contains(document.activeElement))).toBe(true)
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab')
    expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true)
  }
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(trigger).toBeFocused()
})

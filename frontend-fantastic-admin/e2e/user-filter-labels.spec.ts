import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

async function openUsers(page: Page, colorScheme = 'light') {
  await page.addInitScript((colorScheme) => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme },
      layout: { enableMobileAdaptation: true },
    }))
  }, colorScheme)
  await page.goto('/users')
  await expect(page.locator('.user-identity').first()).toBeVisible()
}

for (const theme of ['light', 'dark']) {
  test(`${theme} 筛选标签关联真实控件，宽窄屏保持可用`, async ({ page }) => {
    await openUsers(page, theme)
    // 口径哨兵：四张指标卡全部为「本页」口径（当页 filter 计数），锁死现状；
    // 未来若引入全库口径卡需同步更新该断言与「已启用」卡的 note 文案。
    await expect(page.locator('.mrr-metric-card__scope[data-scope="page"]')).toHaveCount(4)
    const enabledCard = page.locator('.mrr-metric-card').filter({ hasText: '已启用' })
    await expect(enabledCard.locator('.mrr-metric-card__note')).toContainText('当前条件共')
    const bar = page.locator('.users-filter-bar')
    const keyword = bar.getByRole('textbox', { name: '关键词', exact: true })
    const role = bar.getByRole('combobox', { name: '角色', exact: true })
    const status = bar.getByRole('combobox', { name: '账号状态', exact: true })
    for (const width of [1440, 768, 375]) {
      await page.setViewportSize({ width, height: 900 })
      // 筛选栏改为用控件自身的 aria-label 提供可访问名称，页面上没有可见 <label>，
      // 因此不再点击标签，而是验证每个控件都能按名称被定位到（无障碍关联仍在）。
      for (const name of ['关键词', '角色', '账号状态']) {
        await expect(bar.getByLabel(name, { exact: true })).toBeVisible()
      }
      await keyword.focus()
      await page.keyboard.press('Tab')
      await expect(role).toBeFocused()
      await page.keyboard.press('Tab')
      await expect(status).toBeFocused()
      await expect.poll(() => bar.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
      for (const control of [keyword, role, status]) {
        await expect(control).toBeVisible()
      }
      await expect.poll(() => bar.locator('.el-input__wrapper, .el-select__wrapper, .el-button').evaluateAll(elements => elements.every(element => Math.abs(element.getBoundingClientRect().height - 33) <= 1))).toBe(true)
    }
  })
}

test('字段选值后名称不变，Enter查询与重置保留原参数', async ({ page }) => {
  await openUsers(page)
  const bar = page.locator('.users-filter-bar')
  const keyword = bar.getByLabel('关键词', { exact: true })
  const role = bar.getByLabel('角色', { exact: true })
  const status = bar.getByLabel('账号状态', { exact: true })
  await keyword.fill('admin')
  // Element Plus 的内部 combobox 输入负责焦点，指针点击可见选择器外壳。
  await bar.locator('.users-filter__select .el-select__wrapper').nth(0).click()
  await page.getByRole('option', { name: '管理员', exact: true }).click()
  await bar.locator('.users-filter__select .el-select__wrapper').nth(1).click()
  await page.getByRole('option', { name: '启用', exact: true }).click()
  const queryRequest = page.waitForRequest(request => request.method() === 'GET' && new URL(request.url()).pathname.endsWith('/auth/users'))
  await keyword.press('Enter')
  const params = new URL((await queryRequest).url()).searchParams
  expect(Object.fromEntries(params)).toMatchObject({ keyword: 'admin', roleCode: 'ADMIN', status: 'active', page: '1' })
  await expect(role).toHaveAccessibleName('角色')
  await expect(status).toHaveAccessibleName('账号状态')
  await expect(bar.getByRole('button', { name: '查询', exact: true })).toHaveAttribute('aria-busy', 'false')
  const resetRequest = page.waitForRequest(request => request.method() === 'GET' && new URL(request.url()).pathname.endsWith('/auth/users'))
  await bar.getByRole('button', { name: '重置', exact: true }).click()
  const resetParams = new URL((await resetRequest).url()).searchParams
  for (const name of ['keyword', 'roleCode', 'status']) {
    expect(resetParams.has(name)).toBe(false)
  }
  await expect(keyword).toHaveValue('')
})

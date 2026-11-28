import { expect, test } from '@playwright/test'
import { useBaseUserFixture } from './helpers/base-user-fixture'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await useBaseUserFixture(page)
})

test('用户筛选从首次加载到空结果及恢复均保持顶部指标与工具栏位置', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { pageTitleStyle: 'card', colorScheme: 'light', enableMotion: false },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
  let release: (() => void) | undefined
  let hold = true
  let fail = false
  await page.route('**/api/v1/auth/users?**', async (route) => {
    if (hold) { await new Promise<void>((resolve) => { release = resolve }) }
    if (fail) {
      await route.fulfill({ status: 500, json: { code: 500, message: '合成请求失败' } })
    }
    else {
      await route.fallback()
    }
  })
  const metrics = page.getByRole('region', { name: '当前页账号状态' })
  const values = metrics.locator('.mrr-metric-card__value')
  const panel = page.getByRole('region', { name: '用户列表', exact: true })
  const keyword = panel.getByRole('textbox', { name: '关键词' })
  try {
    await page.goto('/users')
    await expect.poll(() => Boolean(release)).toBe(true)
    await expect(values).toHaveText(['—', '—', '—', '—'])
    await expect(panel.locator('.el-card__header')).toHaveCount(0)
    await expect(metrics).toHaveAttribute('aria-busy', 'true')
    const position = async () => panel.locator('.users-toolbar').evaluate(el => el.getBoundingClientRect().top)
    const initial = await position()
    hold = false
    release?.()
    await expect(values).toHaveText(['0', '3', '0', '0'])
    await expect(metrics).toHaveAttribute('aria-busy', 'false')
    await expect.poll(async () => Math.abs(await position() - initial)).toBeLessThanOrEqual(1)

    hold = true
    release = undefined
    await keyword.fill('mrr-no-matching-user-98765')
    await keyword.press('Enter')
    await expect.poll(() => Boolean(release)).toBe(true)
    await expect(values).toHaveText(['0', '3', '0', '0'])
    await expect(metrics).toHaveAttribute('aria-busy', 'true')
    hold = false
    release?.()
    await expect(panel.getByText('暂无符合条件的用户记录')).toBeVisible()
    await expect(values).toHaveText(['0', '0', '0', '0'])
    await expect(metrics).toHaveAttribute('aria-busy', 'false')
    await expect.poll(async () => Math.abs(await position() - initial)).toBeLessThanOrEqual(1)

    fail = true
    await panel.getByRole('button', { name: '查询', exact: true }).click()
    await expect(values).toHaveText(['—', '—', '—', '—'])
    await expect(metrics).toHaveAttribute('aria-busy', 'false')
    await expect.poll(async () => Math.abs(await position() - initial)).toBeLessThanOrEqual(1)
    fail = false
    await panel.getByRole('button', { name: '重置', exact: true }).click()
    await expect(values).toHaveText(['0', '3', '0', '0'])
    await expect.poll(async () => Math.abs(await position() - initial)).toBeLessThanOrEqual(1)
  }
  finally {
    release?.()
  }
})

test('无标题工具栏焦点光环有完整边界，选择器浮层正常打开关闭', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { pageTitleStyle: 'card', enableMotion: false },
    layout: { enableMobileAdaptation: true },
  })))
  await page.goto('/users')
  const panel = page.getByRole('region', { name: '用户列表', exact: true })
  await expect(panel.locator('.user-identity').first()).toBeVisible()
  const keyword = panel.getByRole('textbox', { name: '关键词' })
  for (const width of [1440, 768, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await keyword.focus()
    await expect(panel.locator('.el-input__wrapper.is-focus').first()).toBeVisible()
    await expect.poll(() => panel.evaluate((el) => {
      const boundary = el.getBoundingClientRect()
      return [...el.querySelectorAll('.users-toolbar .el-input__wrapper, .users-toolbar .el-select__wrapper')].every((field) => {
        const r = field.getBoundingClientRect()
        return r.top >= boundary.top + 4 && r.left >= boundary.left + 4 && r.right <= boundary.right - 4
      })
    })).toBe(true)
    await panel.locator('.users-filter__select .el-select__wrapper').first().click()
    await expect(page.getByRole('option', { name: '管理员', exact: true })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('option', { name: '管理员', exact: true })).not.toBeVisible()
  }
})

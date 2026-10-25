import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

async function openUsers(page: Page, colorScheme: 'light' | 'dark' = 'light', radius = 0.75) {
  await page.addInitScript(({ colorScheme, radius }) => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme, radius, pageTitleStyle: 'card' },
      layout: { enableMobileAdaptation: true },
    }))
  }, { colorScheme, radius })
  await page.goto('/users')
  await expect(page.locator('.user-identity').first()).toBeVisible()
}

async function expectContained(page: Page, selector: string) {
  await expect(page.locator(selector).first()).toBeVisible()
  // 切换断点时壳层有 220ms 过渡，等待实际布局稳定，而非只等待 mode 更新。
  await expect.poll(() => page.locator(selector).evaluateAll(elements => elements
    .filter(element => element.scrollWidth > element.clientWidth + 1)
    .map(element => ({ class: element.className, width: element.clientWidth, scroll: element.scrollWidth })))).toEqual([])
}

test('共享页头、分区卡和指标卡跟随同一圆角设置', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await openUsers(page, 'light', 1.25)
  // 用户列表将筛选栏嵌入表格面板，只保留下边框，不属于独立圆角表面。
  for (const selector of ['.mrr-page-header', '.mrr-section-card', '.mrr-metric-card']) {
    await expect(page.locator(selector).first()).toHaveCSS('border-radius', '20px')
  }
})

test('静态指标保持稳定，说明文字完整换行', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openUsers(page)
  const card = page.locator('.mrr-metric-card').first()
  await card.hover()
  await expect(card).toHaveCSS('transform', 'none')
  await expect(page.locator('.mrr-metric-card__note').first()).toHaveCSS('white-space', 'normal')
  await expectContained(page, '.mrr-metric-card__note')
})

test('主操作按钮通过键盘聚焦时显示统一焦点环', async ({ page }) => {
  await openUsers(page)
  const query = page.getByRole('button', { name: '查询', exact: true })
  await query.focus()
  await page.keyboard.press('Tab')
  await page.keyboard.press('Shift+Tab')
  await expect(query).toBeFocused()
  await expect(query).toHaveCSS('outline-style', 'solid')
  await expect(query).toHaveCSS('outline-width', '2px')
})

for (const theme of ['light', 'dark'] as const) {
  test(`${theme} 主题共享布局适配桌面与窄屏`, async ({ page }) => {
    test.setTimeout(90_000)
    await openUsers(page, theme)
    for (const viewport of [
      { width: 1366, height: 768 },
      { width: 1440, height: 900 },
      { width: 1920, height: 1080 },
      { width: 768, height: 900 },
      { width: 375, height: 812 },
    ]) {
      await page.setViewportSize(viewport)
      await expect.poll(() => page.evaluate(() => document.body.dataset.mode)).toBe(viewport.width < 1024 ? 'mobile' : 'pc')
      await expectContained(page, '.mrr-page-header, .mrr-filter-bar, .mrr-section-card__header, .mrr-data-table-panel__pagination')
      const contentWidth = await page.evaluate(() => document.documentElement.scrollWidth)
      expect(contentWidth).toBeLessThanOrEqual(viewport.width)
      await expect(page.getByRole('button', { name: '查询', exact: true })).toBeVisible()
      await expect(page.locator('.el-pagination__jump')).toBeVisible()
    }
  })
}

test('长分区标题与操作区在中等宽度仍可完整呈现', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 900 })
  await openUsers(page)
  // 只扩展显示文案来检验共享容器，不改变业务数据或权限。
  await page.locator('.mrr-section-card h2').evaluate((element) => {
    element.textContent = 'LongSectionTitle'.repeat(12)
  })
  await expectContained(page, '.mrr-section-card__header, .mrr-section-card__heading')
  await expect(page.locator('.mrr-data-table-panel__count')).toBeVisible()
})

test('窄屏大量分页的前后翻页按钮与页码保持同一导航区', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.route('**/api/v1/auth/users?**', async (route) => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.total = 1000
    await route.fulfill({ response, json: body })
  })
  await openUsers(page)
  const footer = page.locator('.mrr-data-table-panel__pagination')
  await expect(footer.locator('.el-pagination__total')).toContainText('1000')
  const [previous, pager, next] = await Promise.all([
    footer.locator('.btn-prev').boundingBox(),
    footer.locator('.el-pager').boundingBox(),
    footer.locator('.btn-next').boundingBox(),
  ])
  expect(previous).not.toBeNull()
  expect(pager).not.toBeNull()
  expect(next).not.toBeNull()
  for (const button of [previous!, next!]) {
    expect(button.y).toBeGreaterThanOrEqual(pager!.y - 1)
    expect(button.y + button.height).toBeLessThanOrEqual(pager!.y + pager!.height + 1)
  }
  await expectContained(page, '.mrr-data-table-panel__pagination, .el-pager')
  const nextBatch = page.waitForResponse(response => response.url().includes('/api/v1/auth/users?page=2&'))
  await footer.locator('.btn-next').click()
  await nextBatch
  await expect(footer.locator('.el-pager .is-active')).toHaveText('2')
  await page.unrouteAll({ behavior: 'wait' })
})

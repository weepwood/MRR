import { expect, test } from '@playwright/test'

for (const theme of ['light', 'dark'] as const) {
  test(`${theme} 图表卡跟随全局圆角并完整呈现窄屏说明`, async ({ page }) => {
    await page.addInitScript((colorScheme) => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme, radius: 1.25, pageTitleStyle: 'card' },
        layout: { enableMobileAdaptation: true },
      }))
    }, theme)
    await page.goto('/statistics')
    const card = page.locator('.mrr-chart-card')
    await expect(card).toBeVisible()

    for (const width of [1440, 768, 375]) {
      await page.setViewportSize({ width, height: 1000 })
      await expect(card).toHaveCSS('border-radius', '20px')
      await expect(card.getByRole('heading', { name: '扫描趋势', level: 2 })).toBeVisible()
      const description = card.getByText('柱形展示扫描页数，折线展示扫描记录数；默认展示近 90 日，可在图表内滚动鼠标滚轮调整时间范围', { exact: true })
      await expect(description).toHaveCSS('white-space', 'normal')
      await expect.poll(() => description.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
      await expect(card.getByText(/共 \d+ 个统计日/)).toBeVisible()
      await expect.poll(() => card.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
      await expect(card.locator('canvas').first()).toBeVisible()
    }

    await page.goto('/audit-images')
    const auditCards = page.locator('.mrr-chart-card')
    await expect(auditCards).toHaveCount(2)
    for (const width of [1440, 375]) {
      await page.setViewportSize({ width, height: 1000 })
      for (const title of ['用户访问趋势', '病历访问趋势']) {
        await expect(page.getByRole('heading', { name: title, level: 2 })).toBeVisible()
      }
      await expect.poll(() => auditCards.evaluateAll(elements => elements.every(element => element.scrollWidth <= element.clientWidth + 1))).toBe(true)
      await expect(auditCards.first()).toHaveCSS('border-radius', '20px')
    }
  })
}

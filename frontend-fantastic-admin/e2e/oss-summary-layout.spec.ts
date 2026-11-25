import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('OSS 迁移桌面仅四张主卡，状态明细不重复计数', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { pageTitleStyle: 'card', colorScheme: 'light', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.route('**/api/v1/oss/migration/statistics', route => route.fulfill({
    json: { code: 200, data: {
      totalCount: 60,
      migratedCount: 31,
      pendingCount: 18,
      waitingSjhCount: 4,
      retryWaitCount: 8,
      migratingCount: 0,
      failedCount: 4,
      percentage: 51.67,
    } },
  }))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/oss-migration')
  const grid = page.getByRole('region', { name: '迁移核心指标' })
  const cards = grid.locator('.mrr-metric-card')
  await expect(cards).toHaveCount(4)
  await expect(cards.locator('.mrr-metric-card__label')).toHaveText(['总图片记录', '已迁移', '当前可领取', '暂不可领取'])
  await expect(cards.locator('.mrr-metric-card__value')).toHaveText(['60', '31', '18', '11'])
  await expect.poll(() => cards.evaluateAll(els => new Set(els.map(el => Math.round(el.getBoundingClientRect().top))).size)).toBe(1)
  await expect(grid.locator('.mrr-metric-card__note')).toHaveCount(0)
  const info = grid.getByRole('button', { name: '查看暂不可领取说明' })
  await info.hover()
  const detail = page.locator('.mrr-info-tip-content:visible')
  await expect(detail).toContainText('等待上架号 4')
  await expect(detail).toContainText('等待重试 8')
  await expect(detail).toContainText('正在迁移 0')
  await expect(detail).toContainText('永久失败 4')
  await expect(detail).toContainText('状态可能重叠')
  await page.keyboard.press('Escape')
  await expect(detail).toHaveCount(0)
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => cards.evaluateAll(els => els.every(el => el.scrollWidth <= el.clientWidth + 1))).toBe(true)
})

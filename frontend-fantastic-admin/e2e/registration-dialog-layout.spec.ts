import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('申请详情审核恢复共用业务弹窗与字段布局，四主题窄屏无溢出', async ({ page }) => {
  test.setTimeout(120_000)
  await isolateLocalVisualSettings(page)
  await page.goto('/users')
  await expect(page.locator('.user-identity').first()).toBeVisible()
  for (const palette of ['mrr', 'gr8r']) {
    for (const theme of ['light', 'dark']) {
      await page.evaluate(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme: theme, themePalette: palette, enableMotion: false, pageTitleStyle: 'card' },
        menu: { mode: 'single' },
        layout: { enableMobileAdaptation: true },
      })), { palette, theme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/users')
      await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', palette)
      await expect.poll(() => page.evaluate(() => document.documentElement.classList.contains('dark'))).toBe(theme === 'dark')
      const row = page.getByRole('row').filter({ hasText: '长说明申请演示' })
      const rejected = page.getByRole('row').filter({ hasText: '资料不全演示' })
      const summary = page.locator('.mrr-dialog-shell--business:visible')
      for (const width of [1440, 375]) {
        await page.setViewportSize({ width, height: 812 })
        const readGeometry = () => summary.evaluate((el) => {
          const r = el.getBoundingClientRect()
          // The fixed overlay uses the scrollbar-adjusted layout viewport.
          const overlay = el.closest('.el-overlay-dialog')!.getBoundingClientRect()
          const header = el.querySelector('.mrr-dialog-header')!
          const body = el.querySelector('.mrr-dialog-body')!
          const footer = el.querySelector('.mrr-overlay-footer')!
          const labels = [...el.querySelectorAll('.review-summary dt')].map(label => label.getBoundingClientRect().left)
          const values = [...el.querySelectorAll('.review-summary dd')].map(value => value.getBoundingClientRect().left)
          return {
            width: r.width,
            background: getComputedStyle(el).backgroundColor,
            card: getComputedStyle(document.body).getPropertyValue('--mrr-card').trim(),
            headerPadding: getComputedStyle(header).paddingLeft,
            bodyPadding: getComputedStyle(body).paddingLeft,
            footerPadding: getComputedStyle(footer).paddingLeft,
            titleFont: getComputedStyle(header.querySelector('.mrr-overlay-title')!).fontSize,
            labelsAligned: Math.max(...labels) - Math.min(...labels) <= 1,
            valuesAligned: Math.max(...values) - Math.min(...values) <= 1,
            fits: r.left >= -1 && r.right <= innerWidth + 1 && r.bottom <= innerHeight + 1,
            centered: Math.abs(overlay.left) <= 1 && Math.abs(overlay.top) <= 1 && Math.abs(overlay.right - document.documentElement.getBoundingClientRect().right) <= 1 && Math.abs(overlay.bottom - innerHeight) <= 1 && Math.abs(r.left + r.width / 2 - (overlay.left + overlay.width / 2)) <= 1 && Math.abs(r.top + r.height / 2 - (overlay.top + overlay.height / 2)) <= 1,
            scrollFits: body.scrollWidth <= body.clientWidth + 1,
            footerVisible: footer.getBoundingClientRect().bottom <= innerHeight + 1,
          }
        })
        await row.getByRole('button', { name: '查看 apply.longtext 的申请详情' }).click()
        await expect(summary).toBeVisible()
        await expect(summary.getByRole('heading', { name: '申请详情' })).toBeVisible()
        await expect(summary.getByText('授予角色', { exact: true })).toHaveCount(0)
        await expect.poll(async () => { const g = await readGeometry(); return g.centered && g.fits && g.scrollFits && g.footerVisible && g.labelsAligned && g.valuesAligned }).toBe(true)
        const detail = await readGeometry()
        expect(detail.headerPadding).toBe(detail.bodyPadding)
        expect(detail.bodyPadding).toBe(detail.footerPadding)
        await summary.getByRole('button', { name: '关闭', exact: true }).click()
        await expect(summary).toBeHidden()
        await row.getByRole('button', { name: '审核注册申请' }).click()
        await expect(summary.getByRole('heading', { name: '审核注册申请' })).toBeVisible()
        await expect(summary.getByText('授予角色', { exact: true })).toBeVisible()
        await expect.poll(readGeometry).toEqual(detail)
        await summary.getByRole('button', { name: '取消', exact: true }).click()
        await expect(summary).toBeHidden()
        await rejected.getByRole('button', { name: '恢复待审核' }).click()
        await expect(summary.getByRole('heading', { name: '恢复注册申请' })).toBeVisible()
        await expect(summary.getByText('原拒绝原因', { exact: true })).toBeVisible()
        await expect.poll(readGeometry).toEqual(detail)
        await summary.getByRole('button', { name: '取消', exact: true }).click()
        await expect(summary).toBeHidden()
      }
    }
  }
})

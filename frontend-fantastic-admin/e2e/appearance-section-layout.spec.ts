import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const palette of ['mrr', 'gr8r']) {
  for (const colorScheme of ['light', 'dark']) {
    test(`外观分区命令与内容卡片统一 ${palette}/${colorScheme}`, async ({ page }, testInfo) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(({ palette, colorScheme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
        app: { colorScheme, themePalette: palette, enableMotion: false, pageTitleStyle: 'card' },
        menu: { mode: 'single' },
        layout: { enableMobileAdaptation: true },
      })), { palette, colorScheme })
      await page.goto('/settings?section=appearance')
      const header = page.locator('.settings-panel-header')
      const panel = page.locator('.settings-content .app-config-panel')
      await expect(header.locator('.config-summary')).toBeVisible()
      await expect(header.locator('.config-summary')).toHaveCSS('border-top-width', '0px')
      await expect(header.locator('.config-summary')).toHaveCSS('margin-bottom', '0px')
      await expect(panel.locator('.config-summary')).toHaveCount(0)
      await expect(header.getByRole('button', { name: '导出配置' })).toBeVisible()
      for (const [tab, labels] of [
        ['工作区组件', ['标签栏', '工具栏', '快捷键']],
        ['主页、版权与其他', ['主页', '底部版权', '其他功能']],
      ] as const) {
        await panel.getByRole('tab', { name: tab, exact: true }).click()
        for (const width of [1920, 1440, 1024, 375]) {
          await page.setViewportSize({ width, height: 900 })
          const cards = panel.locator('.appearance-tab-content .config-section:visible')
          await expect(cards).toHaveCount(3)
          for (let i = 0; i < labels.length; i++) {
            await expect(cards.nth(i).locator('.subsection-heading strong')).toHaveText(labels[i])
          }
          await expect.poll(() => page.evaluate(() => {
            const header = document.querySelector('.settings-panel-header')!
            const outer = header.getBoundingClientRect()
            const cards = [...document.querySelectorAll('.settings-content .appearance-tab-content .config-section')].filter(e => e.getBoundingClientRect().width > 0)
            return header.scrollWidth <= header.clientWidth + 1 && cards.every((card) => {
              const r = card.getBoundingClientRect()
              const title = card.querySelector('.subsection-heading')!.getBoundingClientRect()
              const row = card.querySelector('.setting-item')!.getBoundingClientRect()
              return Math.abs(r.left - outer.left) <= 1 && Math.abs(r.right - outer.right) <= 1
                && title.top >= r.top && title.bottom <= row.top + 1
                && card.scrollWidth <= card.clientWidth + 1
            })
          })).toBe(true)
          if (width === 1440 || width === 375) {
            await page.screenshot({ path: testInfo.outputPath(`${labels[0]}-${width}.png`), animations: 'disabled' })
          }
        }
      }
    })
  }
}
test('外观分区命令保留真实导出、导入及抽屉独立入口', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/settings?section=appearance')
  const header = page.locator('.settings-panel-header')
  const download = page.waitForEvent('download')
  await header.getByRole('button', { name: '导出配置' }).click()
  expect((await download).suggestedFilename()).toMatch(/^mrr-interface-config-.*\.json$/)
  const chooser = page.waitForEvent('filechooser')
  await header.getByRole('button', { name: '导入配置' }).click()
  await (await chooser).setFiles({ name: 'appearance-test.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ home: { title: '合成外观测试' } })) })
  const panel = page.locator('.settings-content .app-config-panel')
  await panel.getByRole('tab', { name: '主页、版权与其他' }).click()
  await expect(panel.getByRole('textbox', { name: '主页名称' })).toHaveValue('合成外观测试')
  await page.getByRole('button', { name: '打开应用外观设置' }).click()
  const drawer = page.getByRole('dialog', { name: '应用外观设置' })
  await expect(drawer.locator('.config-summary')).toBeVisible()
  await expect(drawer.getByRole('button', { name: '导出配置' })).toBeVisible()
  await drawer.getByRole('tab', { name: '工作区组件' }).click()
  await expect(drawer.locator('.subsection-heading').first()).toContainText('标签栏')
})

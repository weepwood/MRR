import fs from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { themePalette: 'mrr', colorScheme: 'light', enableMotion: true },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
})

test('局部主题、尺寸和示例状态独立于全站配置', async ({ page }, testInfo) => {
  await page.goto('/ui-components')
  await page.getByLabel('查找组件').fill('按钮与操作')
  const preview = page.getByRole('region', { name: '局部主题与尺寸对照' })
  const surface = preview.locator('.local-theme-preview__surface')
  await expect(surface).toBeVisible()
  const before = await page.evaluate(() => ({
    storage: localStorage.getItem('MRR-ADMIN:app-settings'),
    palette: document.documentElement.dataset.mrrPalette,
    dark: document.documentElement.classList.contains('dark'),
    card: getComputedStyle(document.documentElement).getPropertyValue('--mrr-card'),
  }))
  await preview.getByLabel('局部预览输入').fill('保留示例')
  await preview.getByText('暖色暗', { exact: true }).click()
  await expect(surface).toHaveAttribute('data-preview-scheme', 'dark')
  await expect(surface).toHaveCSS('background-color', 'rgb(34, 34, 32)')
  const heights = await surface.locator('.local-theme-preview__sizes .el-button').evaluateAll(nodes => nodes.slice(0, 3).map(node => node.getBoundingClientRect().height))
  expect(heights).toEqual([26, 30, 36])
  await preview.getByText('暖色亮', { exact: true }).click()
  await expect(surface).toHaveCSS('background-color', 'rgb(255, 255, 255)')
  await expect(preview.getByLabel('局部预览输入')).toHaveValue('保留示例')
  expect(await page.evaluate(() => ({
    storage: localStorage.getItem('MRR-ADMIN:app-settings'),
    palette: document.documentElement.dataset.mrrPalette,
    dark: document.documentElement.classList.contains('dark'),
    card: getComputedStyle(document.documentElement).getPropertyValue('--mrr-card'),
  }))).toEqual(before)
  await preview.getByText('暖色暗', { exact: true }).click()
  await preview.screenshot({ path: testInfo.outputPath('local-dark.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 375, height: 900 })
  await expect.poll(() => preview.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
})

test('OCR数值字段适度定宽及移动监控指标构图', async ({ page }, testInfo) => {
  await page.goto('/ocr-batches')
  const form = page.locator('.ocr-batches__form')
  await expect(form).toBeVisible()
  const widths = await form.locator('.el-input-number').evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().width))
  expect(widths).toHaveLength(3)
  expect(widths.every(width => width <= 261 && width >= 200)).toBe(true)
  await expect(form.locator('.mrr-edit-form__hint')).toHaveCount(3)
  await page.setViewportSize({ width: 375, height: 900 })
  await expect.poll(() => form.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await page.goto('/monitoring')
  const grid = page.locator('.mrr-metric-grid--mobile-compact')
  await expect(grid).toBeVisible()
  await expect.poll(() => grid.evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(2)
  await expect.poll(() => grid.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await grid.screenshot({ path: testInfo.outputPath('monitoring-mobile.png'), animations: 'disabled' })
})

test('浮层快速关闭及重新打开，记录实际浏览器帧间隔', async ({ page }, testInfo) => {
  await page.goto('/ui-components')
  await page.getByLabel('查找组件').fill('对话框、抽屉与气泡')
  const trigger = page.getByRole('button', { name: '打开抽屉', exact: true })
  await trigger.click()
  const drawer = page.getByRole('dialog', { name: '抽屉示例' })
  await expect(drawer).toBeVisible()
  await page.evaluate(() => {
    const frames: number[] = []
    const start = performance.now()
    function sample(now: number) {
      frames.push(now)
      if (now - start < 800) { requestAnimationFrame(sample) }
      else { document.body.dataset.previewFrameSample = JSON.stringify(frames.map((v, i) => i ? v - frames[i - 1] : 0).slice(1)) }
    }
    requestAnimationFrame(sample)
  })
  await page.keyboard.press('Escape')
  await expect(drawer).toBeHidden()
  await expect(trigger).toBeFocused()
  await trigger.click()
  await expect(drawer).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(drawer).toBeHidden()
  await expect(trigger).toBeFocused()
  await expect.poll(() => page.locator('body').getAttribute('data-preview-frame-sample')).not.toBeNull()
  const frames = JSON.parse((await page.locator('body').getAttribute('data-preview-frame-sample'))!) as number[]
  expect(frames.length).toBeGreaterThan(2)
  const evidence = JSON.stringify({ count: frames.length, median: [...frames].sort((a, b) => a - b)[Math.floor(frames.length / 2)], max: Math.max(...frames) })
  await fs.mkdir(testInfo.outputDir, { recursive: true })
  await fs.writeFile(testInfo.outputPath('frame-intervals.json'), evidence)
  await testInfo.attach('frame-intervals-ms', { body: evidence, contentType: 'application/json' })
})

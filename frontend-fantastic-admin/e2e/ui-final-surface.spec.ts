import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'dark', enableMotion: false, pageTitleStyle: 'card' },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
})

test('暗色卡片继承统一表面，成功状态文本满足4.5对比度', async ({ page }) => {
  await page.goto('/data-relations')
  const card = page.locator('.mrr-metric-card').first()
  await expect(card).toBeVisible()
  await expect(page.locator('.relation-card').first()).toBeVisible()
  await expect.poll(() => page.evaluate(() => {
    const style = (selector: string) => getComputedStyle(document.querySelector(selector)!)
    return ['backgroundColor', 'borderTopColor', 'color'].every(key =>
      style('.mrr-metric-card').getPropertyValue(key.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`))
      === style('.mrr-page-header').getPropertyValue(key.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`)),
    ) && style('.relation-card').backgroundColor === style('.mrr-page-header').backgroundColor
  })).toBe(true)
  const ratio = await page.locator('.mrr-status-tag--success').first().evaluate((el) => {
    const style = getComputedStyle(el)
    const canvas = document.createElement('canvas')
    canvas.width = 1
    canvas.height = 1
    const ctx = canvas.getContext('2d')!
    function luminance(color: string) {
      ctx.clearRect(0, 0, 1, 1)
      ctx.fillStyle = color
      ctx.fillRect(0, 0, 1, 1)
      const rgb = [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3).map(v => v / 255).map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
      return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
    }
    const [high, low] = [luminance(style.color), luminance(style.backgroundColor)].sort((a, b) => b - a)
    return (high + 0.05) / (low + 0.05)
  })
  expect(ratio).toBeGreaterThanOrEqual(4.5)
})

test('图表主题重绘保留缩放与图例，主动更换范围仍生效', async ({ page }) => {
  await page.goto('/ui-components')
  await page.getByLabel('查找组件', { exact: true }).fill('图表与时间范围')
  await expect(page.locator('.gallery-preview canvas').first()).toBeVisible()
  const modulePath = '/src/plugins/echarts.ts'
  const getState = () => page.evaluate(async (path) => {
    const { echarts } = await import(/* @vite-ignore */ path)
    const chart = echarts.getInstanceByDom(document.querySelector('.gallery-preview .mrr-chart__canvas'))
    const option = chart.getOption()
    return { start: option.dataZoom?.[0]?.start, end: option.dataZoom?.[0]?.end, selected: option.legend[0].selected, color: option.series[0].lineStyle.color }
  }, modulePath)
  await page.evaluate(async (path) => {
    const { echarts } = await import(/* @vite-ignore */ path)
    const chart = echarts.getInstanceByDom(document.querySelector('.gallery-preview .mrr-chart__canvas'))
    chart.dispatchAction({ type: 'dataZoom', start: 20, end: 60 })
    chart.dispatchAction({ type: 'legendUnSelect', name: '服务端耗时' })
  }, modulePath)
  const before = await getState()
  expect(before.start).toBe(20)
  expect(before.end).toBe(60)
  await page.locator('html').evaluate(el => el.classList.remove('dark'))
  await expect.poll(async () => (await getState()).color).not.toBe(before.color)
  const after = await getState()
  expect(after.start).toBe(before.start)
  expect(after.end).toBe(before.end)
  expect(after.selected).toEqual(before.selected)
  await page.locator('.demo-trend-range .el-select__wrapper').click()
  await page.getByRole('option', { name: '全部记录日' }).click()
  await expect.poll(async () => (await getState()).start).toBeUndefined()
})

test('记录批量操作显示选中数量并拒绝空选择', async ({ page }) => {
  await page.goto('/records')
  const batch = page.getByRole('button', { name: '批量下载' })
  await expect(batch).toBeDisabled()
  await expect(page.locator('.records-batch-actions__count')).toHaveText('已选 0 条')
  const checkbox = page.locator('.records-table .el-table__body tr').first().getByRole('checkbox')
  await checkbox.focus()
  await checkbox.press('Space')
  await expect(batch).toBeEnabled()
  await expect(page.locator('.records-batch-actions__count')).toHaveText('已选 1 条')
  await checkbox.press('Space')
  await expect(batch).toBeDisabled()
})

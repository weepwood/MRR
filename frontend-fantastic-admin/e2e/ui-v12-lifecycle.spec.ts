import fs from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('图表反复挂载卸载与加载状态资源采样', async ({ page, browserName }, testInfo) => {
  test.setTimeout(90000)
  await isolateLocalVisualSettings(page)
  await page.goto('/ui-components')
  const search = page.getByLabel('查找组件', { exact: true })
  const samples: Array<{ cycle: number, canvases: number, heap?: number, nodes?: number, documents?: number, listeners?: number }> = []
  const cdp = browserName === 'chromium' ? await page.context().newCDPSession(page) : undefined
  for (let i = 0; i < 12; i++) {
    await search.fill('图表与时间范围')
    const chart = page.locator('.gallery-example .mrr-chart')
    await expect(chart).toHaveCount(2)
    await expect(chart.first().locator('canvas').first()).toBeVisible()
    const loading = page.locator('.el-switch').filter({ has: page.getByRole('switch', { name: '模拟图表加载' }) }).locator('.el-switch__core')
    await loading.click()
    await expect(chart.first()).toHaveAttribute('aria-busy', 'true')
    await loading.click()
    await expect(chart.first()).toHaveAttribute('aria-busy', 'false')
    await search.fill('不存在的组件')
    await expect(page.locator('.gallery-example .mrr-chart canvas')).toHaveCount(0)
    const sample: typeof samples[number] = { cycle: i + 1, canvases: await page.locator('canvas').count() }
    if (cdp && [2, 5, 8, 11].includes(i)) {
      // Let the existing 80ms resize debounce and native overlay animations settle before sampling.
      await page.waitForTimeout(500)
      await cdp.send('HeapProfiler.collectGarbage')
      const heap = await cdp.send('Runtime.getHeapUsage')
      const dom = await cdp.send('Memory.getDOMCounters')
      sample.heap = heap.usedSize
      sample.nodes = dom.nodes
      sample.documents = dom.documents
      sample.listeners = dom.jsEventListeners
    }
    samples.push(sample)
  }
  await fs.mkdir(testInfo.outputDir, { recursive: true })
  await fs.writeFile(testInfo.outputPath('lifecycle.json'), JSON.stringify(samples, null, 2))
  await cdp?.detach()
})

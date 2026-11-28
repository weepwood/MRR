import { closeSync, openSync, writeSync } from 'node:fs'
import fs from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('图表反复挂载卸载与加载状态资源采样', async ({ page, browserName }, testInfo) => {
  test.setTimeout(process.env.MRR_LIFECYCLE_HEAP === '1' ? 180000 : 90000)
  await isolateLocalVisualSettings(page)
  await page.goto('/ui-components')
  const search = page.getByLabel('查找组件', { exact: true })
  const samples: Array<{ cycle: number, canvases: number, heap?: number, nodes?: number, documents?: number, listeners?: number }> = []
  const cdp = browserName === 'chromium' ? await page.context().newCDPSession(page) : undefined
  const cycles = Number(process.env.MRR_LIFECYCLE_CYCLES || 12)
  if (process.env.MRR_LIFECYCLE_HEAP === '1' && (!process.env.MRR_PRODUCTION_MOCK_DIST || testInfo.project.use.baseURL !== 'http://127.0.0.1:9041')) {
    throw new Error('堆快照仅允许独立本地生产 Mock 环境')
  }
  const captureHeap = !!cdp && process.env.MRR_LIFECYCLE_HEAP === '1'
  await fs.mkdir(testInfo.outputDir, { recursive: true })
  await page.evaluate(() => {
    let previous = 0
    const intervals: number[] = []
    let frame = 0
    const sample = (now: number) => {
      if (previous) { intervals.push(now - previous) }
      previous = now
      frame = requestAnimationFrame(sample)
    }
    frame = requestAnimationFrame(sample)
    Object.assign(window, { __mrrStopFrameSampling: () => { cancelAnimationFrame(frame); return intervals } })
  })
  async function snapshot(name: string) {
    if (!cdp) { return }
    const fd = openSync(testInfo.outputPath(name), 'w')
    const onChunk = ({ chunk }: { chunk: string }) => { writeSync(fd, chunk) }
    cdp.on('HeapProfiler.addHeapSnapshotChunk', onChunk)
    try {
      await cdp.send('HeapProfiler.collectGarbage')
      await cdp.send('HeapProfiler.takeHeapSnapshot', { reportProgress: false })
    }
    finally {
      cdp.off('HeapProfiler.addHeapSnapshotChunk', onChunk)
      closeSync(fd)
    }
  }
  for (let i = 0; i < cycles; i++) {
    await search.fill('图表与时间范围')
    const chart = page.locator('.gallery-example .mrr-chart')
    await expect(chart).toHaveCount(2)
    await expect(chart.first().locator('canvas').first()).toBeVisible()
    if (i % 2 === 0) {
      await page.locator('.el-switch').filter({ has: page.getByRole('switch', { name: '组合图模式' }) }).locator('.el-switch__core').click()
      await expect(page.getByRole('img', { name: '双坐标轴组合图' })).toBeVisible()
    }
    const loading = page.locator('.el-switch').filter({ has: page.getByRole('switch', { name: '模拟图表加载' }) }).locator('.el-switch__core')
    await loading.click()
    await expect(chart.first()).toHaveAttribute('aria-busy', 'true')
    await loading.click()
    await expect(chart.first()).toHaveAttribute('aria-busy', 'false')
    await search.fill('不存在的组件')
    await expect(page.locator('.gallery-example .mrr-chart canvas')).toHaveCount(0)
    const sample: typeof samples[number] = { cycle: i + 1, canvases: await page.locator('canvas').count() }
    if (cdp && (i + 1) % Math.max(3, Math.floor(cycles / 4)) === 0) {
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
    if (captureHeap && (i + 1 === Math.floor(cycles / 4) || i + 1 === cycles)) {
      await page.waitForTimeout(1000)
      await snapshot(`heap-${i + 1}.heapsnapshot`)
    }
  }
  const frames = await page.evaluate(() => (window as unknown as { __mrrStopFrameSampling: () => number[] }).__mrrStopFrameSampling())
  const sorted = [...frames].sort((a, b) => a - b)
  const percentile = (q: number) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * q))] ?? 0
  await fs.writeFile(testInfo.outputPath('frames.json'), JSON.stringify({ samples: frames.length, p50: percentile(0.5), p95: percentile(0.95), p99: percentile(0.99), above50ms: frames.filter(v => v > 50).length, intervals: frames }, null, 2))
  await fs.writeFile(testInfo.outputPath('lifecycle.json'), JSON.stringify(samples, null, 2))
  await cdp?.detach()
})

import { expect, test } from '@playwright/test'

for (const path of ['/nginx-browser', '/oss-browser']) {
  test(`${path} 宽屏列表表头受工作区圆角裁切`, async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 })
    await page.goto(path)
    await page.getByRole('button', { name: '列表视图' }).click()
    await expect(page.getByRole('table', { name: '文件列表' })).toBeVisible()

    const clipping = await page.locator('.explorer-layout').evaluate((layout) => {
      const { left, top } = layout.getBoundingClientRect()
      const hitsTable = (x: number, y: number) => document.elementsFromPoint(x, y).some(node => node.closest('.file-table'))
      return {
        overflow: getComputedStyle(layout).overflow,
        outside: hitsTable(left + 1, top + 1),
        inside: hitsTable(left + 24, top + 16),
      }
    })
    expect(clipping).toEqual({ overflow: 'hidden', outside: false, inside: true })
  })
}

test('按名称排序后可用方向键选中并打开目录', async ({ page }) => {
  await page.goto('/oss-browser')
  const table = page.getByRole('table', { name: '文件列表' })
  await expect(table).toBeVisible()
  const name = table.getByRole('columnheader', { name: '名称' })
  await name.getByRole('button').click()
  await expect(name).toHaveAttribute('aria-sort', 'descending')
  const first = table.getByRole('button', { name: '0024', exact: true })
  const second = table.getByRole('button', { name: '0013', exact: true })
  await first.focus()
  await first.press('ArrowDown')
  await expect(second).toBeFocused()
  await expect(second).toHaveAttribute('aria-pressed', 'true')
  await second.press('Enter')
  await expect(page.getByLabel('当前位置')).toContainText('0013')
  const pane = page.getByRole('region', { name: '目录内容' })
  await expect(pane).toBeFocused()
  await pane.press('Alt+ArrowUp')
  await expect(page.getByRole('table', { name: '文件列表' })).toBeVisible()
})

test('图标视图使用方向键切换选择，并保留相同的目录导航', async ({ page }) => {
  await page.goto('/oss-browser')
  await page.getByRole('button', { name: '图标视图' }).click()
  const first = page.getByRole('button', { name: '0012', exact: true })
  const second = page.getByRole('button', { name: '0013', exact: true })
  await first.press('ArrowRight')
  await expect(second).toBeFocused()
  await expect(second).toHaveAttribute('aria-pressed', 'true')
  await second.press('Enter')
  await expect(page.getByLabel('当前位置')).toContainText('0013')
})

for (const path of ['/nginx-browser', '/oss-browser']) {
  test(`${path} 占满右侧内容区并可用 Esc 还原`, async ({ page }) => {
    const viewport = { width: 1440, height: 900 }
    await page.setViewportSize(viewport)
    await page.goto(path)
    const browser = page.locator('.storage-browser')
    const main = page.locator('.main')
    const initial = await browser.boundingBox()
    const contentArea = await main.boundingBox()
    expect(initial).not.toBeNull()
    expect(contentArea).not.toBeNull()

    await page.getByRole('button', { name: '占满右侧内容区' }).click()
    await expect(page.getByRole('button', { name: '退出占满区域' })).toHaveAttribute('aria-pressed', 'true')
    await expect(browser).toHaveCSS('position', 'fixed')

    const expanded = await browser.boundingBox()
    expect(expanded).not.toBeNull()
    // 与右侧内容区（.main）严格对齐：左边界贴侧边栏，底部贴视口。
    expect(expanded!.x).toBeCloseTo(contentArea!.x, 0)
    expect(expanded!.width).toBeCloseTo(contentArea!.width, 0)
    expect(expanded!.y).toBeCloseTo(contentArea!.y, 0)
    expect(expanded!.y + expanded!.height).toBeCloseTo(viewport.height, 0)
    expect(expanded!.height).toBeGreaterThan(initial!.height)

    // 页面标题和外壳留白被让出来，但站点导航仍然可见可操作。
    await expect(page.locator('.mrr-page-header')).toBeVisible()

    await page.keyboard.press('Escape')
    await expect(browser).not.toHaveCSS('position', 'fixed')
    await expect(page.getByRole('button', { name: '占满右侧内容区' })).toHaveAttribute('aria-pressed', 'false')
    const restored = await browser.boundingBox()
    expect(restored!.height).toBeCloseTo(initial!.height, 0)
  })
}

test('占满模式下长列表在盒内滚动，不再把页面撑高', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 620 })
  // 演示数据每个目录只有几项，这里塞一页长列表，验证高度由盒子而不是内容决定。
  await page.route('**/api/v1/oss/browser?*', route => route.fulfill({
    json: {
      code: 200,
      data: {
        configured: true,
        bucket: 'mrr-test',
        endpoint: 'https://oss.example.test',
        rootPrefix: 'medical-records/',
        prefix: 'medical-records/',
        truncated: false,
        maxKeys: 200,
        loadedDirectories: 0,
        loadedFiles: 80,
        loadedBytes: 80 * 240_000,
        entries: Array.from({ length: 80 }, (_, index) => ({
          name: `${String(index + 1).padStart(4, '0')}.jpg`,
          key: `medical-records/bulk/${String(index + 1).padStart(4, '0')}.jpg`,
          directory: false,
          size: 240_000 + index,
          lastModified: '2026-01-15T09:30:00+08:00',
          etag: `mock-etag-${index}`,
          storageClass: 'STANDARD',
        })),
      },
    },
  }))

  await page.goto('/oss-browser')
  await expect(page.getByRole('table', { name: '文件列表' })).toBeVisible()
  await page.getByRole('button', { name: '占满右侧内容区' }).click()

  const pane = page.getByRole('region', { name: '目录内容' })
  await expect(pane).toHaveCSS('overflow-y', 'auto')
  const metrics = await pane.evaluate(node => ({
    clientHeight: node.clientHeight,
    scrollHeight: node.scrollHeight,
    bottom: Math.round(node.getBoundingClientRect().bottom),
  }))
  // 列表比可视区高，却在盒子内部滚动，溢出不会顶到视口外。
  expect(metrics.scrollHeight).toBeGreaterThan(metrics.clientHeight)
  expect(metrics.bottom).toBeLessThanOrEqual(620)
})

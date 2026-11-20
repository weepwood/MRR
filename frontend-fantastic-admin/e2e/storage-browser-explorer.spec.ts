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
  test(`${path} 占满整个右侧内容区，盖住顶栏且锁住背后页面`, async ({ page }) => {
    const viewport = { width: 1440, height: 900 }
    await page.setViewportSize(viewport)
    await page.goto(path)
    const browser = page.locator('.storage-browser')
    const initial = await browser.boundingBox()
    const contentLeft = Math.round((await page.locator('.main').boundingBox())!.x)
    expect(initial).not.toBeNull()

    await page.getByRole('button', { name: '占满右侧内容区' }).click()
    await expect(page.getByRole('button', { name: '退出占满区域' })).toHaveAttribute('aria-pressed', 'true')
    await expect(browser).toHaveCSS('position', 'fixed')

    const expanded = await browser.boundingBox()
    expect(expanded).not.toBeNull()
    // 顶到视口顶部、右边缘贴视口：站点头部与工具栏不再占位，左侧导航仍保留。
    expect(expanded!.x).toBeCloseTo(contentLeft, 0)
    expect(expanded!.width).toBeCloseTo(viewport.width - contentLeft, 0)
    expect(expanded!.y).toBeCloseTo(0, 0)
    expect(expanded!.height).toBeCloseTo(viewport.height, 0)
    expect(expanded!.height).toBeGreaterThan(initial!.height)

    // 原先落在顶栏上的点现在命中占满层，说明工具栏确实被盖住而不是仍然露着。
    const covered = await page.evaluate(({ x, y }) => {
      return Boolean(document.elementFromPoint(x, y)?.closest('.storage-browser'))
    }, { x: contentLeft + 40, y: 24 })
    expect(covered).toBe(true)

    // 背后页面被锁住：滚动条消失、body 不可滚，避免列表滚到底后整页跟着走。
    expect(await page.evaluate(() => getComputedStyle(document.body).overflow)).toBe('hidden')

    await page.keyboard.press('Escape')
    await expect(browser).not.toHaveCSS('position', 'fixed')
    await expect(page.getByRole('button', { name: '占满右侧内容区' })).toHaveAttribute('aria-pressed', 'false')
    expect(await page.evaluate(() => getComputedStyle(document.body).overflow)).not.toBe('hidden')
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

  // 在列表里滚轮到底，页面本身不许跟着走。
  const pageScrollBefore = await page.evaluate(() => window.scrollY)
  const box = (await pane.boundingBox())!
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.wheel(0, 1200)
  await expect.poll(() => pane.evaluate(node => node.scrollTop)).toBeGreaterThan(0)
  expect(await page.evaluate(() => window.scrollY)).toBe(pageScrollBefore)
})

test('Nginx 路径栏粘贴文件相对路径会进入父目录并选中该文件', async ({ page }) => {
  await page.goto('/nginx-browser')
  await expect(page.getByRole('table', { name: '文件列表' })).toBeVisible()
  const address = page.getByRole('textbox', { name: '目录路径' })

  // 详情里「复制路径」得到的就是这个形式，末尾没有斜杠。
  await address.fill('样例目录/示例图标.png')
  await address.press('Enter')

  await expect(address).toHaveValue('样例目录/')
  await expect(page.getByRole('button', { name: '示例图标.png', exact: true })).toHaveAttribute('aria-pressed', 'true')
  // 选中即触发详情内的图片预览，说明确实定位到了这个文件。
  await expect(page.locator('.inline-preview img')).toHaveAttribute('alt', '示例图标.png')
})

test('OSS 路径栏粘贴文件 key 会进入父目录并选中该文件', async ({ page }) => {
  await page.goto('/oss-browser')
  await expect(page.getByRole('table', { name: '文件列表' })).toBeVisible()
  const address = page.getByRole('textbox', { name: '目录路径' })

  await address.fill('medical-records/0012/00123456-00789124/0001.jpg')
  await address.press('Enter')

  await expect(address).toHaveValue('medical-records/0012/00123456-00789124/')
  await expect(page.getByRole('button', { name: '0001.jpg', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

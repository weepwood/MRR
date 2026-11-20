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

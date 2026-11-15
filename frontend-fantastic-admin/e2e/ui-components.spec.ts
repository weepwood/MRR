import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

async function openGallery(page: Page, theme = 'light') {
  await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: theme, enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })), theme)
  await page.goto('/ui-components')
  await expect(page.getByRole('heading', { name: 'UI 组件', exact: true })).toBeVisible()
  await expect.poll(() => page.locator('html').evaluate(element => element.classList.contains('dark'))).toBe(theme === 'dark')
}

test('目录支持搜索、分类、无匹配恢复与重置', async ({ page }) => {
  await openGallery(page)
  await expect(page.getByRole('main', { name: '组件预览' }).locator('article')).toHaveCount(9)
  await page.getByRole('button', { name: /表单与选择/ }).click()
  await expect(page.getByRole('heading', { name: '输入与筛选', exact: true })).toBeVisible()
  await expect(page.getByRole('main', { name: '组件预览' }).locator('article')).toHaveCount(2)
  await page.getByLabel('查找组件', { exact: true }).fill('FaInput')
  await expect(page.getByRole('main', { name: '组件预览' }).locator('article')).toHaveCount(1)
  await page.getByLabel('查找组件', { exact: true }).fill('不存在的组件')
  await expect(page.getByText('没有匹配的组件，试试其他名称或分类。')).toBeVisible()
  await page.getByRole('button', { name: '显示全部组件' }).click()
  await expect(page.getByRole('main', { name: '组件预览' }).locator('article')).toHaveCount(9)
})

test('真实控件可编辑，禁用和加载按钮不可执行，示例不写业务数据', async ({ page }) => {
  await openGallery(page)
  const writes: string[] = []
  page.on('request', (request) => {
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && request.url().includes('/api/')) {
      writes.push(request.method())
    }
  })
  const primary = page.getByRole('button', { name: '主要操作', exact: true })
  await primary.click()
  await expect(page.getByText('已触发 1 次示例操作')).toBeVisible()
  await page.getByText('禁用', { exact: true }).click()
  await expect(primary).toBeDisabled()
  await page.getByText('加载', { exact: true }).click()
  await expect(primary).toBeDisabled()
  await page.getByRole('button', { name: '重置所有示例' }).click()
  await expect(primary).toBeEnabled()
  await expect(page.getByText('已触发 0 次示例操作')).toBeVisible()
  await page.getByRole('button', { name: /表单与选择/ }).click()
  await page.getByLabel('示例名称', { exact: true }).fill('可编辑示例')
  await expect(page.getByLabel('示例名称', { exact: true })).toHaveValue('可编辑示例')
  await page.getByRole('combobox', { name: '示例来源', exact: true }).click()
  await page.getByRole('option', { name: '归档示例', exact: true }).click()
  await expect(page.getByRole('combobox', { name: '示例来源', exact: true })).toContainText('归档示例')
  await page.getByRole('checkbox', { name: '选中此示例', exact: true }).uncheck()
  await expect(page.getByRole('slider', { name: /示例进度/ })).toBeVisible()
  await expect(page.getByText('复选框未选中，开关已启用。')).toBeVisible()
  expect(writes).toEqual([])
})

test('演示表格可筛选、翻页和触发行操作', async ({ page }) => {
  await openGallery(page)
  await page.getByRole('button', { name: /数据与组合/ }).click()
  await page.getByLabel('筛选示例', { exact: true }).fill('暗色')
  await page.getByLabel('筛选示例', { exact: true }).press('Enter')
  await expect(page.locator('.el-table__body tr')).toHaveCount(1)
  await expect(page.locator('.el-table')).toContainText('DEMO-003')
  await page.getByRole('button', { name: '查看示例', exact: true }).click()
  await expect(page.getByText('DEMO-003：已触发查看示例')).toBeVisible()
  await page.getByRole('button', { name: '重置筛选', exact: true }).click()
  await page.locator('.el-pagination .btn-next').click()
  await expect(page.locator('.el-table')).toContainText('DEMO-004')
  await expect(page.locator('.el-table__body tr')).toHaveCount(2)
})

test('失败与空态可恢复，对话框和抽屉可确认与关闭', async ({ page }) => {
  await openGallery(page)
  await page.getByRole('button', { name: /反馈与浮层/ }).click()
  await page.getByText('加载失败', { exact: true }).click()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByText('示例内容已就绪', { exact: true })).toBeVisible()
  await page.getByText('无数据', { exact: true }).click()
  await page.getByRole('button', { name: '恢复示例', exact: true }).click()
  await expect(page.getByText('示例内容已就绪', { exact: true })).toBeVisible()
  const trigger = page.getByRole('button', { name: '打开对话框', exact: true })
  await trigger.click()
  const modal = page.getByRole('dialog', { name: '对话框示例', exact: true })
  await expect(modal).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(modal).not.toBeVisible()
  await expect(trigger).toBeFocused()
  await page.getByRole('button', { name: '打开抽屉', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '抽屉示例', exact: true })
  await expect(drawer).toBeVisible()
  await drawer.getByRole('button', { name: '确定', exact: true }).click()
  await expect(drawer).not.toBeVisible()
  await expect(page.getByRole('button', { name: '打开抽屉', exact: true })).toBeFocused()
  await expect(page.getByText('抽屉示例已确认。')).toBeVisible()
})

for (const theme of ['light', 'dark'] as const) {
  test(`${theme} 主题目录、字段与浮层在宽窄屏可用`, async ({ page }) => {
    await openGallery(page, theme)
    for (const width of [1920, 1440, 1366, 768, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect(page.getByRole('heading', { name: 'UI 组件', exact: true })).toBeVisible()
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    }
    await page.getByRole('button', { name: /反馈与浮层/ }).click()
    await page.getByRole('button', { name: '打开对话框', exact: true }).click()
    const modal = page.getByRole('dialog', { name: '对话框示例', exact: true })
    await expect.poll(async () => (await modal.boundingBox())?.x ?? -1).toBeGreaterThanOrEqual(0)
    await expect.poll(() => modal.evaluate(element => element.getBoundingClientRect().right <= innerWidth)).toBe(true)
    await page.keyboard.press('Escape')
    await expect(modal).not.toBeVisible()
    await page.getByRole('button', { name: '打开抽屉', exact: true }).click()
    const drawer = page.getByRole('dialog', { name: '抽屉示例', exact: true })
    await expect(drawer).toBeVisible()
    await expect.poll(async () => (await drawer.boundingBox())?.x ?? -1).toBeGreaterThanOrEqual(0)
    await expect.poll(() => drawer.evaluate(element => element.getBoundingClientRect().right <= innerWidth)).toBe(true)
  })
}

test('未登录访问仍进入既有登录流程', async ({ browser }) => {
  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } })
  try {
    const page = await context.newPage()
    await page.goto('http://127.0.0.1:9000/ui-components')
    await expect(page).toHaveURL(/\/login\?/)
    await expect(page.getByRole('heading', { name: 'UI 组件', exact: true })).not.toBeVisible()
  }
  finally {
    await context.close()
  }
})

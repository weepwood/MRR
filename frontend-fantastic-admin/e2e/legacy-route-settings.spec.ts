import { expect, test } from '@playwright/test'

test.use({ actionTimeout: 10_000 })

test('旧文件系统路由配置回到显式业务菜单，深链刷新正常且不暴露组件目录', async ({ page }) => {
  // Four document loads plus assertions; keep per-operation deadlines independent.
  test.setTimeout(90_000)
  const warnings: string[] = []
  page.on('console', (message) => {
    if (message.text().includes('Parent route')) { warnings.push(message.text()) }
  })
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { routeBaseOn: 'filesystem', colorScheme: 'dark', enableMotion: false },
    menu: { mode: 'single' },
  })))
  await page.goto('/archive-workbench')
  await expect(page.getByRole('heading', { name: '病案工作台', exact: true })).toBeVisible()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect(page.locator('.sub-sidebar-container')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '病案工作台', exact: true })).toBeVisible()
  await page.goto('/settings/components/OcrSettings')
  await expect(page.getByText('页面不存在', { exact: false }).first()).toBeVisible()
  await page.goto('/operations-center')
  await expect(page.getByRole('heading', { name: '运维诊断中心', exact: true })).toBeVisible()
  expect(warnings).toEqual([])
})

test('系统信息仅显示必要的运行时依赖与构建身份', async ({ page }) => {
  await page.goto('/archive-workbench')
  await expect(page.getByRole('heading', { name: '病案工作台', exact: true })).toBeVisible()
  await page.keyboard.press('Control+i')
  const panel = page.getByRole('dialog', { name: '系统信息' })
  await expect(panel).toBeVisible()
  await expect(panel.getByText('Git Commit', { exact: true })).toBeVisible()
  await expect(panel.getByText('最低迁移版本', { exact: true })).toBeVisible()
  await expect(panel.getByText('主要运行时依赖（声明版本范围）')).toBeVisible()
  await expect(panel.getByText('vue', { exact: true })).toBeVisible()
  await expect(panel.getByText('开发环境依赖', { exact: true })).toHaveCount(0)
  await expect(panel.getByText('vite-plugin-pages', { exact: true })).toHaveCount(0)
  await expect(panel.getByText('@playwright/test', { exact: true })).toHaveCount(0)
})

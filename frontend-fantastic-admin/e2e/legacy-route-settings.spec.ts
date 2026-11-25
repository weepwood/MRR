import { expect, test } from '@playwright/test'

test('旧文件系统路由配置回到显式业务菜单，深链刷新正常且不暴露组件目录', async ({ page }) => {
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

import { expect, test } from '@playwright/test'

test.use({ storageState: 'e2e/.auth/anonymous.json' })

test('首次登录及刷新后，关闭手风琴时侧栏目录分别展开', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    menu: { mode: 'single', subMenuUniqueOpened: false },
  })))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/login')
  await page.locator('#login-account').fill('admin')
  await page.locator('#login-password').fill('demo-password')
  await page.getByRole('slider', { name: '滑动验证' }).press('End')
  await page.getByRole('button', { name: '登录系统' }).click()
  const sidebar = page.locator('.sub-sidebar-container')
  const group = (name: string) => sidebar.locator(`.menu-item-container[title="${name}"]`)
  const leaf = (name: string) => sidebar.locator(`.menu-item-container[title="${name}"]`)
  await expect(group('系统')).toBeVisible({ timeout: 30_000 })
  for (const afterReload of [false, true]) {
    if (afterReload) {
      await page.reload()
      await expect(group('系统')).toBeVisible()
    }
    // 首页可能自动展开所在目录，先显式收起各组，确保测试初始状态一致。
    for (const [name, child] of [['系统', '用户管理'], ['业务', '记录管理'], ['数据治理', '数据关系工作台'], ['运维', '运维诊断中心']]) {
      if (await leaf(child!).isVisible()) { await group(name!).click() }
      await expect(leaf(child!)).not.toBeVisible()
    }
    await group('业务').click()
    await expect(leaf('记录管理')).toBeVisible()
    await expect(leaf('数据关系工作台')).not.toBeVisible()
    await expect(leaf('运维诊断中心')).not.toBeVisible()
    await group('数据治理').click()
    await expect(leaf('记录管理')).toBeVisible()
    await expect(leaf('数据关系工作台')).toBeVisible()
    await expect(leaf('运维诊断中心')).not.toBeVisible()
    await group('业务').click()
    await expect(leaf('记录管理')).not.toBeVisible()
    await expect(leaf('数据关系工作台')).toBeVisible()
  }
})

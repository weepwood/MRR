import { expect, test } from '@playwright/test'

/**
 * 系统设置读取失败时不能保存（DESIGN.md §5.1）。
 *
 * 此前 GET /api/v1/settings 失败会被静默吞掉：表单显示本地缓存或默认值并被当作「已保存」，
 * 用户改一项后保存，请求体里是全部字段，回退值会整批覆盖服务器设置。
 */
const SETTINGS_GET = /\/api\/v1\/settings(?:\?.*)?$/

test('科室配色读取完成前禁用导入、导出、恢复和保存', async ({ page }) => {
  let releaseSettings!: () => void
  const pendingSettings = new Promise<void>((resolve) => { releaseSettings = resolve })
  await page.route(SETTINGS_GET, async (route) => {
    if (route.request().method() === 'GET') {
      await pendingSettings
    }
    await route.fallback()
  })

  const commands = ['添加科室配色', '导入配色', '导出配色', '恢复自动配色'].map(name => page.getByRole('button', { name }))
  try {
    await page.goto('/settings?section=department', { waitUntil: 'domcontentloaded' })
    for (const command of commands) {
      await expect(command).toBeDisabled({ timeout: 3000 })
    }
    await expect(page.getByRole('button', { name: '保存科室配色' })).toBeDisabled()
  }
  finally {
    releaseSettings()
  }

  for (const command of commands) {
    await expect(command).toBeEnabled()
  }
  await expect(page.getByRole('button', { name: '保存科室配色' })).toBeDisabled()
})

for (const [label, status, json] of [
  ['读取失败', 500, { code: 500, message: '合成测试：设置服务暂不可用' }],
  ['响应缺少设置对象', 200, { code: 200, data: null }],
] as const) {
  test(`设置${label}时显示错误且无法保存，重新加载成功后恢复`, async ({ page }) => {
    let failing = true
    const writes: string[] = []
    page.on('request', (request) => {
      if (request.method() === 'PUT' && /\/api\/v1\/settings/.test(request.url())) {
        writes.push(request.url())
      }
    })
    await page.route(SETTINGS_GET, async (route) => {
      if (route.request().method() === 'GET' && failing) {
        await route.fulfill({ status, json })
        return
      }
      await route.fallback()
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/settings?section=system', { waitUntil: 'domcontentloaded' })

    const failure = page.locator('.settings-content .app-error')
    await expect(failure).toBeVisible({ timeout: 20_000 })
    await expect(failure).toContainText('系统设置读取失败')
    await expect(page.getByText('系统标识', { exact: true })).toHaveCount(0)
    await expect(page.locator('.page-header .header-actions').getByRole('button', { name: '保存设置' })).toBeDisabled()
    await expect(page.locator('.page-header .header-actions').getByRole('button', { name: '恢复默认' })).toBeDisabled()

    // 其他服务器配置分区共用同一份基线，同样只显示错误，不会因为回退值被当作「未保存修改」而弹出离开确认。
    await page.locator('.settings-nav-item').filter({ hasText: '访问安全' }).click()
    await expect(page).toHaveURL(/section=security/)
    await expect(page.locator('.settings-content .app-error')).toBeVisible()
    await expect(page.getByRole('dialog', { name: '有未保存的设置' })).toHaveCount(0)
    expect(writes).toEqual([])

    failing = false
    await page.locator('.settings-content .app-error').getByRole('button', { name: '重新加载' }).click()
    await expect(page.locator('.settings-content .app-error')).toHaveCount(0)
    await expect(page.locator('.settings-content[aria-busy="false"]')).toBeVisible()
    await expect(page.locator('.page-header .header-actions').getByRole('button', { name: '恢复默认' })).toBeEnabled()
    expect(writes).toEqual([])
  })
}

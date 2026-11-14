import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

function schema(value = '7') {
  return { groups: [{ key: 'security', label: '安全策略' }], items: [{ key: 'loginMaxAttempts', group: 'security', type: 'integer', label: '登录失败上限', description: '同一账号的失败次数限制', unit: '次', min: 3, max: 20, allowedValues: [], effect: 'IMMEDIATE', sensitive: true, defaultValue: '5', effectiveValue: value, overrideValue: value }] }
}
async function openRuntime(page: Page) {
  await page.goto('/settings?section=runtime')
  const panel = page.locator('.settings-content > section.setting-section')
  const save = page.getByRole('button', { name: '保存运行参数', exact: true })
  return { panel, save, input: panel.getByRole('spinbutton') }
}

test('取消不提交，确认期间防重且固定提交内容', async ({ page }) => {
  const writes: unknown[] = []
  let serverValue = '7'
  await page.route(/\/api\/v1\/settings\/schema$/, route => route.fulfill({ json: { code: 200, data: schema(serverValue) } }))
  await page.route(/\/api\/v1\/settings$/, async (route) => {
    if (route.request().method() !== 'PUT') { return route.fallback() }
    const body = route.request().postDataJSON()
    writes.push(body)
    serverValue = body.loginMaxAttempts
    await route.fulfill({ json: { code: 200, data: null } })
  })
  const { panel, save, input } = await openRuntime(page)
  await expect(input).toHaveValue('7')
  await input.fill('10')
  await save.click()
  const dialog = page.getByRole('dialog', { name: '确认修改敏感配置' })
  await expect(save).toBeDisabled()
  await expect(input).not.toBeEditable()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  expect(writes).toEqual([])
  await expect(input).toBeEditable()
  await save.click()
  // 确认期间真实控件只读；响应式数据变化的快照隔离另由 Vitest 覆盖。
  await expect(input).not.toBeEditable()
  await dialog.getByRole('button', { name: '确认保存', exact: true }).click()
  await expect(input).toHaveValue('10')
  await expect(panel).toContainText('运行参数已同步')
  expect(writes).toEqual([{ loginMaxAttempts: '10' }])
})

for (const failure of ['write', 'read'] as const) {
  test(`${failure}失败后侧栏不误报同步，GET核对恢复且不重复写`, async ({ page }, testInfo) => {
    let reads = 0
    let writes = 0
    await page.route(/\/api\/v1\/settings\/schema$/, async (route) => {
      reads++
      const fail = failure === 'read' && reads === 2
      await route.fulfill({ status: fail ? 503 : 200, json: { code: fail ? 503 : 200, data: fail ? null : schema(writes ? '10' : '7') } })
    })
    await page.route(/\/api\/v1\/settings$/, async (route) => {
      if (route.request().method() !== 'PUT') { return route.fallback() }
      writes++
      await route.fulfill({ status: failure === 'write' ? 503 : 200, json: { code: failure === 'write' ? 503 : 200, data: null } })
    })
    const { panel, save, input } = await openRuntime(page)
    await expect(input).toHaveValue('7')
    await input.fill('10')
    await save.click()
    await page.getByRole('dialog', { name: '确认修改敏感配置' }).getByRole('button', { name: '确认保存', exact: true }).click()
    await expect(panel).toContainText(failure === 'write' ? '未能确认运行参数保存结果' : '运行参数已保存，但读取最新配置失败')
    await expect(page.locator('.sidebar-save-card')).toContainText('运行参数待重新核对')
    await expect(save).toBeDisabled()
    await expect(input).toHaveValue('10')
    await expect(page.getByRole('dialog', { name: '确认修改敏感配置' })).toBeHidden()
    await panel.screenshot({ path: testInfo.outputPath(`runtime-${failure}-failure.png`) })
    await panel.getByRole('button', { name: '重新加载', exact: true }).click()
    await page.getByRole('dialog', { name: '重新加载运行参数' }).getByRole('button', { name: '确认重新加载' }).click()
    await expect(page.locator('.sidebar-save-card')).toContainText('运行参数已同步')
    await expect(input).toBeEditable()
    expect(writes).toBe(1)
  })
}

test('首次加载失败侧栏保持未核对，重新读取后可编辑', async ({ page }) => {
  let reads = 0
  await page.route(/\/api\/v1\/settings\/schema$/, async (route) => {
    reads++
    await route.fulfill({ status: reads === 1 ? 503 : 200, json: { code: reads === 1 ? 503 : 200, data: reads === 1 ? null : schema() } })
  })
  const { panel, save, input } = await openRuntime(page)
  await expect(page.locator('.sidebar-save-card')).toContainText('运行参数待重新核对')
  await expect(save).toBeDisabled()
  await panel.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(input).toHaveValue('7')
  await expect(page.locator('.sidebar-save-card')).toContainText('运行参数已同步')
})

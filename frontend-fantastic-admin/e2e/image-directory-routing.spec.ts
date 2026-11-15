import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

const key = 'imageDirectoryRoutingRules'
const defaults = [
  { type: 'month', value: '2026.06', node: 'ba02' },
  { type: 'exact', value: '2026.06.05', node: 'ba03' },
]
async function setup(page: Page, override: string | null = null) {
  await page.route(/\/api\/v1\/settings\/schema$/, route => route.fulfill({ json: { code: 200, data: { groups: [{ key: 'archive', label: '档案浏览' }], items: [{ key, group: 'archive', type: 'text', sensitive: true, defaultValue: JSON.stringify(defaults), effectiveValue: override ?? JSON.stringify(defaults), overrideValue: override }] } } }))
  await page.goto('/settings?section=archive')
  const panel = page.getByRole('region', { name: '目录路由规则', exact: true })
  await expect(panel.getByText(override ? '自定义规则' : '内置规则', { exact: true })).toBeVisible()
  return panel
}

test('预览优先级、重复校验和单键保存，支持明暗及窄屏布局', async ({ page }, testInfo) => {
  const saved: unknown[] = []
  await page.route(new RegExp(`/api/v1/settings/${key}$`), async (route) => {
    saved.push(route.request().postDataJSON())
    await route.fulfill({ json: { code: 200, data: null } })
  })
  const panel = await setup(page)
  await panel.getByLabel('目录匹配预览').fill('2026.06.05')
  await expect(panel).toContainText('命中精确目录 2026.06.05 → BA03')
  await panel.getByLabel('目录匹配预览').fill('2026.06.06')
  await expect(panel).toContainText('命中年月 2026.06 → BA02')
  await panel.getByRole('button', { name: '添加目录规则' }).click()
  await panel.getByRole('textbox', { name: '规则 3 目录', exact: true }).fill('2026.06.05')
  await expect(panel.getByRole('alert')).toContainText('匹配条件重复')
  await expect(panel.getByRole('button', { name: '保存目录规则', exact: true })).toBeDisabled()
  await panel.getByRole('button', { name: '删除规则 3', exact: true }).click()
  await panel.getByRole('textbox', { name: '规则 2 目录', exact: true }).fill('2026.06.08')
  await panel.getByRole('button', { name: '保存目录规则', exact: true }).click()
  await page.getByRole('dialog', { name: '确认保存目录规则' }).getByRole('button', { name: '确认', exact: true }).click()
  await expect(panel).toContainText('目录规则已保存')
  await expect(page.getByRole('dialog', { name: '确认保存目录规则' })).toBeHidden()
  expect(saved).toEqual([{ value: JSON.stringify([defaults[0], { ...defaults[1], value: '2026.06.08' }]) }])
  await panel.scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('routing-light.png'), fullPage: true })
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await page.screenshot({ path: testInfo.outputPath('routing-dark.png'), fullPage: true })
  await page.setViewportSize({ width: 1024, height: 768 })
  await panel.scrollIntoViewIfNeeded()
  await expect(panel.getByRole('button', { name: '保存目录规则', exact: true })).toBeVisible()
  const bounds = await panel.boundingBox()
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(1024)
  for (const button of await panel.getByRole('button').all()) {
    const box = await button.boundingBox()
    if (box) { expect(box.x + box.width).toBeLessThanOrEqual(1024) }
  }
  await page.screenshot({ path: testInfo.outputPath('routing-narrow.png'), fullPage: true })
})

test('恢复发送DELETE，503未知结果禁止直接重试并可重新加载', async ({ page }) => {
  const methods: string[] = []
  await page.route(new RegExp(`/api/v1/settings/${key}$`), async (route) => {
    methods.push(route.request().method())
    await route.fulfill({ status: methods.length === 1 ? 200 : 503, json: { code: methods.length === 1 ? 200 : 503, data: null } })
  })
  const panel = await setup(page, '[]')
  await panel.getByRole('button', { name: '恢复内置目录规则' }).click()
  await page.getByRole('dialog', { name: '恢复内置目录规则' }).getByRole('button', { name: '确认', exact: true }).click()
  await expect(panel).toContainText('已恢复内置目录规则')
  await panel.getByRole('textbox', { name: '规则 2 目录', exact: true }).fill('2026.06.08')
  await panel.getByRole('button', { name: '保存目录规则', exact: true }).click()
  await page.getByRole('dialog', { name: '确认保存目录规则' }).getByRole('button', { name: '确认', exact: true }).click()
  await expect(panel).toContainText('服务器可能已接收变更')
  await expect(panel.getByRole('button', { name: '保存目录规则', exact: true })).toBeDisabled()
  expect(methods).toEqual(['DELETE', 'PUT'])
  await panel.getByRole('button', { name: '重新加载目录规则' }).click()
  await page.getByRole('dialog', { name: '确认重新加载' }).getByRole('button', { name: '确认', exact: true }).click()
  await expect(panel).toContainText('没有日期分流规则')
  await expect(panel.getByRole('button', { name: '添加目录规则' })).toBeEnabled()
})

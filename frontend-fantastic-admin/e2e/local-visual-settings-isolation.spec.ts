import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('本地视觉测试的写入不泄漏至共享设置接口，其他写入保持真实路由', async ({ page }) => {
  const forwarded: unknown[] = []
  await page.route('**/synthetic-fixture.html', route => route.fulfill({
    contentType: 'text/html',
    body: '<!doctype html><title>Isolated synthetic fixture</title>',
  }))
  await page.route('**/api/v1/settings', async (route) => {
    forwarded.push(route.request().postDataJSON())
    await route.fulfill({ json: { code: 200, data: null, message: 'forwarded' } })
  })
  await isolateLocalVisualSettings(page)
  await page.goto('/synthetic-fixture.html')

  const visual = { adminAppSettings: JSON.stringify({ app: { colorScheme: 'dark' } }) }
  const ordinary = { 'audit.retentionDays': '180' }
  const mixed = { ...visual, ...ordinary }
  const invalid = { adminAppSettings: 42 }
  const result = await page.evaluate(async ({ visual, ordinary, mixed, invalid }) => {
    const save = (body: unknown) => fetch('/proxy/api/v1/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then(response => response.json())
    const visualResult = await save(visual)
    const ordinaryResult = await save(ordinary)
    const mixedResult = await save(mixed)
    const invalidResult = await save(invalid)
    const read = await fetch('/proxy/api/v1/settings/ui').then(response => response.json())
    return { visualResult, ordinaryResult, mixedResult, invalidResult, read }
  }, { visual, ordinary, mixed, invalid })

  expect(result.visualResult.code).toBe(200)
  expect(result.ordinaryResult.message).toBe('forwarded')
  expect(result.mixedResult.message).toBe('forwarded')
  expect(result.invalidResult.message).toBe('forwarded')
  expect(result.read).toEqual({ code: 200, data: {} })
  expect(forwarded).toEqual([ordinary, mixed, invalid])
})

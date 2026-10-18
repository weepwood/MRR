import { expect, test } from '@playwright/test'

test('空影像查询显示追踪编号，管理员显式触发深入诊断', async ({ page }) => {
  let diagnoses = 0
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: { code: 200, data: [], meta: { requestId: '0123456789abcdef', reasonCode: 'NO_MATCHING_IMAGES', lookupStrategy: 'LEGACY_CODE' } } }))
  await page.route('**/api/v1/operations/query-diagnostics', async route => {
    diagnoses++
    expect(route.request().postDataJSON()).toMatchObject({ bah: '123', requestId: '0123456789abcdef' })
    await route.fulfill({ json: { code: 200, data: { readOnly: true, steps: [{ code: 'ARCHIVE_COMPAT', status: 'AMBIGUOUS', durationMs: 1, message: '存在多个候选，禁止自动关联', details: { sampledCount: 2 } }] } } })
  })
  await page.goto('/archive?bah=123')
  await expect(page.getByLabel('本次请求追踪编号')).toHaveValue('0123456789abcdef')
  expect(diagnoses).toBe(0)
  await page.getByRole('button', { name: '排查本次查询' }).click()
  await expect(page.getByRole('dialog', { name: '病案查询诊断' })).toContainText('存在多个候选，禁止自动关联')
  expect(diagnoses).toBe(1)
})

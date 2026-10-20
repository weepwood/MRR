import type { Page } from '@playwright/test'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const csv = 'bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\r\nE2E-SYNTHETIC-0001,Synthetic Patient,,,,Synthetic Unit,,\r\n'

async function login(page: Page, account: string) {
  await page.goto('/login')
  await page.locator('#login-account').fill(account)
  await page.locator('#login-password').fill(process.env.MRR_LIVE_PASSWORD!)
  const response = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST')
  await page.locator('button[type=submit]').click()
  const body = await (await response).json()
  expect(body.code).toBe(200)
  await expect(page).not.toHaveURL(/\/login/)
  return body.data.token as string
}

test.describe.configure({ mode: 'serial' })

test('real login, invalid file, dry run, commit and query', async ({ page, request }) => {
  const unauthorized = await request.get('/proxy/api/v1/patients')
  expect(unauthorized.status()).toBe(401)
  const token = await login(page, 'e2e_editor')
  const headers = { Authorization: `Bearer ${token}` }
  await page.goto('/data-exchange?dataset=patients')
  const panel = page.locator('.dataset-card').filter({ has: page.getByRole('heading', { name: '患者数据导入', exact: true }) })
  await expect(panel).toBeVisible()
  await panel.locator('input[type=file]').setInputFiles({ name: 'invalid.csv', mimeType: 'text/csv', buffer: Buffer.from('wrong\r\nvalue\r\n') })
  const invalid = page.waitForResponse(r => r.url().includes('/data-exchange/patients/import'))
  await panel.getByRole('button', { name: '校验文件', exact: true }).click()
  const rejected = await (await invalid).json()
  expect(rejected.data.canImport).toBe(false)
  expect(rejected.data.errorRows).toBeGreaterThan(0)
  await expect(panel.getByRole('button', { name: '确认导入', exact: true })).toBeDisabled()
  await panel.locator('input[type=file]').setInputFiles({ name: 'synthetic.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) })
  const dryRun = page.waitForResponse(r => r.url().includes('/data-exchange/patients/import'))
  await panel.getByRole('button', { name: '校验文件', exact: true }).click()
  const preview = await (await dryRun).json()
  expect(preview.code, preview.message).toBe(200)
  expect(preview.data.canImport).toBe(true)
  expect(preview.data.validRows).toBe(1)
  expect(preview.data.dryRun).toBe(true)
  const before = await request.get('/proxy/api/v1/patients?keyword=E2E-SYNTHETIC-0001', { headers })
  expect((await before.json()).data.total).toBe(0)
  await panel.getByRole('button', { name: '确认导入', exact: true }).click()
  const commit = page.waitForResponse(r => r.url().includes('/data-exchange/patients/import'))
  await page.getByRole('button', { name: '确认写入', exact: true }).click()
  expect((await (await commit).json()).data.insertedRows).toBe(1)
  const after = await request.get('/proxy/api/v1/patients?keyword=E2E-SYNTHETIC-0001', { headers })
  const result = await after.json()
  expect(result.data.total).toBe(1)
  expect(result.data.list[0].bah).toBe('E2E-SYNTHETIC-0001')
})

test('real read-only account receives 403 on write', async ({ page, request }) => {
  const token = await login(page, 'e2e_reader')
  const response = await request.post('/proxy/api/v1/data-exchange/patients/import?dryRun=false', {
    headers: { Authorization: `Bearer ${token}` },
    multipart: { file: { name: 'denied.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) } },
  })
  expect(response.status()).toBe(403)
})

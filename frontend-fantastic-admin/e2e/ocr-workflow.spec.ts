import { expect, test } from '@playwright/test'

const syntheticJobId = '11111111-1111-4111-8111-111111111111'
const reply = (data: unknown) => ({ code: 200, message: 'success', data })

test('历史 OCR 创建绑定预览范围，缺少验收证据时不能授权自动分类', async ({ page }) => {
  let created: Record<string, unknown> | undefined
  let list: unknown[] = []
  await page.route('**/api/v1/ocr/batches**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/preview')) {
      await route.fulfill({ json: reply({ eligibleInLimit: 2, upperId: 9002, truncated: false, readiness: 'READY', autoAvailable: false, policyChecksum: null, autoTypes: [] }) })
    }
    else if (route.request().method() === 'POST') {
      created = route.request().postDataJSON()
      list = [{ id: 'synthetic-batch', status: 'PAUSED', afterId: 0, upperId: 9002, cursorId: 0, maxPages: 10000, dispatchedCount: 0, processedCount: 0, successCount: 0, failedCount: 0, autoAppliedCount: 0, reviewRequiredCount: 0, autoApply: false, reasonCode: 'USER_PAUSED' }]
      await route.fulfill({ json: reply(list[0]) })
    }
    else {
      await route.fulfill({ json: reply(list) })
    }
  })
  await page.goto('/ocr-batches', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: '历史未分类影像识别' })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: /授权本批次/ })).toBeDisabled()
  await page.getByRole('button', { name: '预览范围', exact: true }).click()
  await expect(page.getByText(/扫描上界 9002/)).toBeVisible()
  await page.getByRole('button', { name: '创建此范围的识别批次' }).click()
  await expect(page.getByRole('cell', { name: 'synthetic-batch', exact: true })).toBeVisible()
  expect(created?.upperId).toBe(9002)
  expect(created?.autoApply).toBe(false)
  expect(created?.idempotencyKey).toMatch(/^batch-/)
  await page.screenshot({ path: 'test-results/ocr-batches-preview.png', fullPage: true })
})

test('档案袋可以识别单页并显示文字与待审核状态', async ({ page }) => {
  let created = false
  const itemCursors: string[] = []
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: reply([{ id: 9001, bah: 'E2E-OCR', sjh: 'E2E-OCR-SJH', filename: 'synthetic.png', pages: 1, btype: 0, uploadFlag: 1 }]) }))
  await page.route('**/api/v1/search/patient/**', route => route.fulfill({ json: reply({ bah: 'E2E-OCR', name: '合成测试样本', department: '合成测试科室' }) }))
  await page.route('**/api/v1/scan/9001/ocr?**', route => route.fulfill({ json: reply({ scanId: 9001, archiveId: 42, status: 'SUCCEEDED', confidence: 0.99, resultVersion: 'synthetic-result', text: '合成识别文字 无患者信息', processedAt: null }) }))
  await page.route('**/api/v1/archive-records/42/ocr/jobs', async (route) => {
    if (route.request().method() === 'POST') {
      created = true
      expect(route.request().postDataJSON().scanId).toBe(9001)
      await route.fulfill({ json: reply({ id: syntheticJobId, archiveId: 42, status: 'PENDING', plannedCount: 1, processedCount: 0, ocrSuccessCount: 0, failedCount: 0 }) })
    }
    else {
      await route.fulfill({ json: reply(created ? [{ id: syntheticJobId }] : []) })
    }
  })
  await page.route(`**/api/v1/ocr/jobs/${syntheticJobId}`, route => route.fulfill({ json: reply({ id: syntheticJobId, archiveId: 42, status: 'SUCCESS', plannedCount: 1, processedCount: 1, ocrSuccessCount: 1, failedCount: 0 }) }))
  await page.route('**/api/v1/classification/jobs/**/suggestions?**', route => route.fulfill({ json: reply([]) }))
  await page.route('**/api/v1/ocr/jobs/**/items?**', async (route) => {
    const after = new URL(route.request().url()).searchParams.get('after') || '0'
    itemCursors.push(after)
    await route.fulfill({ json: reply(after === '0' ? [{ scanId: 9001, status: 'SUCCEEDED', suggestedType: 1 }] : []) })
  })
  await page.goto('/archive?bah=E2E-OCR&sjh=E2E-OCR-SJH', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '识别文字', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '识别原文，可选择复制' })).toHaveValue('合成识别文字 无患者信息')
  await page.getByRole('button', { name: '识别 / 重试本页' }).click()
  await expect(page.getByText(/SUCCESS · 1 \/ 1 页/)).toBeVisible()
  await expect(page.getByRole('heading', { name: '分类建议与待处理页面' })).toBeVisible()
  await page.getByRole('button', { name: '返回首批 / 刷新' }).click()
  await expect.poll(() => itemCursors.filter(cursor => cursor === '0').length).toBeGreaterThan(1)
  await page.getByRole('button', { name: '下一批（最多 50 页）' }).click()
  await expect.poll(() => itemCursors.includes('9001')).toBe(true)
  await page.locator('.el-drawer__close-btn').click()
  await page.getByRole('button', { name: '识别文字', exact: true }).click()
  await expect(page.getByText(/SUCCESS · 1 \/ 1 页/)).toBeVisible()
})

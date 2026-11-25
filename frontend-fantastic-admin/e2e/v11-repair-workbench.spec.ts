import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'

test('修复工单必须显式确认，审批执行复验和审计按服务器状态更新', async ({ page }) => {
  let status = 'PROPOSED'
  const changes: string[] = []
  const order = () => ({ id: 901, issueId: 2, scanId: 3, targetArchiveId: 2, status, proposedBy: 11, approvedBy: status === 'PROPOSED' ? null : 12 })
  await page.route('**/api/v1/system/data-quality/repair-orders**', async (route) => {
    const url = new URL(route.request().url())
    if (route.request().method() === 'POST') {
      const action = url.pathname.split('/').at(-1)!
      changes.push(action)
      status = ({ approve: 'APPROVED', apply: 'APPLIED', verify: 'VERIFIED' } as Record<string, string>)[action] || status
      await route.fulfill({ json: { code: 200, data: order() } })
      return
    }
    if (url.pathname.endsWith('/events')) {
      const events = ['PROPOSED', ...changes.map(action => ({ approve: 'APPROVED', apply: 'APPLIED', verify: 'VERIFIED' } as Record<string, string>)[action])]
      await route.fulfill({ json: { code: 200, data: events.map((action, i) => ({ id: i + 1, actorId: i ? 12 : 11, action, beforeArchiveId: null, afterArchiveId: i >= 2 ? 2 : null, createdAt: '2026-07-21T00:00:00Z' })) } })
      return
    }
    await route.fulfill({ json: { code: 200, data: { enabled: true, actorId: 12, orders: [order()] } } })
  })
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '修复工单', exact: true }).click()
  const pane = page.getByRole('tabpanel').filter({ visible: true })
  await expect(pane.getByText('待审批', { exact: true })).toBeVisible()
  await expect(pane.getByRole('button', { name: '确认执行', exact: true })).toHaveCount(0)
  await pane.getByRole('button', { name: '审批通过', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '返回', exact: true }).click()
  expect(changes).toEqual([])
  for (const [label, target] of [['审批通过', '已审批'], ['确认执行', '已执行'], ['复验', '已复验']]) {
    await pane.getByRole('button', { name: label, exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: label, exact: true }).click()
    await expect(pane.locator('.el-table').getByText(target, { exact: true })).toBeVisible()
  }
  expect(changes).toEqual(['approve', 'apply', 'verify'])
  await expect(pane.getByRole('heading', { name: '工单 #901 · 事务审计' })).toBeVisible()
  await expect(pane.getByText('已复验 · 操作者 #12')).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('repair-workbench.png'), fullPage: true })
})

test('演示病案完整性显示固定成员与版本，写操作关闭', async ({ page }) => {
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '病案工作台', exact: true }).click()
  const input = page.getByRole('textbox', { name: '病案查询值', exact: true })
  await input.fill('26070000')
  await input.press('Enter')
  await expect(page.getByText('病案主档 #1', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '影像文件完整性' })).toBeVisible()
  await expect(page.getByText(/固定成员 2 \/ 2/)).toBeVisible()
  await expect(page.getByText('后台校验功能尚未启用')).toBeVisible()
  await expect(page.getByRole('button', { name: '开始校验', exact: true })).toHaveCount(0)
  await page.getByRole('combobox', { name: '完整性比较基准版本' }).selectOption('1')
  await page.getByRole('button', { name: '比较版本', exact: true }).click()
  await expect(page.getByText('新增成员', { exact: true })).toBeVisible()
  await expect(page.getByText('完整摘要不可比较', { exact: false })).toBeVisible()
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载比较摘要 JSON' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('manifest-comparison-1-1001.json')
  await page.getByRole('button', { name: '查看完整性报告', exact: true }).click()
  await expect(page.getByText('报告版本 #1001', { exact: false })).toBeVisible()
  await expect(page.getByText('未处理 0', { exact: false })).toBeVisible()
  await expect(page.getByText('异常全量汇总：UNVERIFIED 2', { exact: false })).toBeVisible()
  const reportPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载完整性报告 JSON', exact: true }).click()
  const report = await reportPromise
  expect(report.suggestedFilename()).toBe('manifest-report-1001.json')
  const stream = await report.createReadStream()
  const chunks = []
  for await (const chunk of stream) { chunks.push(chunk) }
  const data = JSON.parse(Buffer.concat(chunks).toString('utf8'))
  expect(data).toMatchObject({ reportVersion: 1, archiveId: 1, version: 1001, snapshotCount: 2, processed: 2, anomalies: 2, anomalyCounts: { UNVERIFIED: 2 }, scope: 'FIXED_MEMBERSHIP_OBSERVATION' })

  await page.screenshot({ path: test.info().outputPath('manifest-workbench.png'), fullPage: true })
})

test('独立病案工作台可从菜单打开，直接查询且兼容旧数据治理入口', async ({ page }) => {
  const globalRequests: string[] = []
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname
    if (path.endsWith('/system/data-relations/overview') || path.endsWith('/system/data-quality/summary') || path.endsWith('/system/data-quality/issues')) {
      globalRequests.push(path)
    }
  })
  await page.goto('/archive-workbench')
  await expect(page.getByRole('heading', { name: '病案工作台', exact: true })).toBeVisible()
  await expect(page.getByRole('textbox', { name: '病案查询值', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '执行检查', exact: true })).toHaveCount(0)
  await expect(page.getByText('关系健康度', { exact: true })).toHaveCount(0)
  const search = page.getByRole('textbox', { name: '病案查询值', exact: true })
  await search.fill('26070000')
  await search.press('Enter')
  await expect(page.getByText('病案主档 #1', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '影像文件完整性' })).toBeVisible()
  expect(globalRequests).toEqual([])
  const samples = page.getByRole('group', { name: '有效影像列表' })
  await expect(samples.getByRole('button')).toHaveCount(2)
  await expect(page.getByAltText('待核对影像')).toHaveCount(0)
  await samples.getByRole('button').first().click()
  const image = page.getByAltText('待核对影像')
  await expect(image).toBeVisible()
  await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true)
  await samples.getByRole('button').last().click()
  await expect(samples.getByRole('button').last()).toHaveAttribute('aria-pressed', 'true')
  await expect(image).toBeVisible()
  await page.getByRole('button', { name: '识别文字', exact: true }).click()
  await expect(page.getByRole('region', { name: '影像文字识别' })).toBeVisible()
  await page.getByRole('region', { name: '影像文字识别' }).getByRole('button', { name: '收起识别' }).click()
  await page.screenshot({ path: test.info().outputPath('standalone-archive-workbench.png'), fullPage: true })
  await page.getByRole('button', { name: '关闭原图' }).click()
  await expect(image).toHaveCount(0)
  await page.getByRole('link', { name: '数据关系工作台', exact: true }).last().click()
  await expect(page).toHaveURL(/data-relations$/)
  await expect(page.getByRole('tab', { name: '关系总览', exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: '病案工作台', exact: true })).toBeVisible()
  await page.getByRole('link', { name: '打开病案工作台', exact: true }).click()
  await expect(page).toHaveURL(/archive-workbench$/)
  await expect(search).toHaveValue('')
  await page.reload()
  await expect(search).toBeVisible()
  await page.goto('/data-relations')
  const navigation = page.getByRole('navigation', { name: '次导航' })
  const business = navigation.getByRole('button', { name: '业务', exact: true })
  const entry = navigation.getByRole('link', { name: '病案工作台', exact: true })
  if (!await entry.isVisible()) {
    await business.click()
  }
  await entry.click()
  await expect(page).toHaveURL(/archive-workbench$/)
  await expect(search).toBeVisible()
})

test('异常人工决策明确确认并保留独立历史，不改变修复工单', async ({ page }) => {
  const issue = { id: 2, check_code: 'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED', check_name: '合成缺失关联', severity: 'WARNING', entity_type: 'mr_scan', entity_id: '3', detail: '合成样本' }
  let review: Record<string, unknown> | null = null
  const changes: string[] = []
  await page.route('**/api/v1/system/data-quality/issues?*', route => route.fulfill({ json: { code: 200, data: [issue] } }))
  await page.route('**/api/v1/system/data-quality/issues/2/repair-preview', route => route.fulfill({ json: { code: 200, data: {
    issue,
    currentEntity: { entityId: '3', archiveId: null },
    candidateArchives: [],
    suggestedAction: 'LINK_ARCHIVE_ID',
    deterministic: false,
    readOnly: true,
    canApply: false,
    reason: '合成预览',
  } } }))
  await page.route('**/api/v1/system/data-quality/case-reviews/issue/2**', (route) => {
    if (route.request().method() === 'POST') {
      const params = new URL(route.request().url()).searchParams
      changes.push(params.get('action')!)
      expect(params.get('expectedRevision')).toBe('0')
      review = { id: '1', entityType: 'mr_scan', entityId: '3', checkCode: issue.check_code, issueId: '2', runId: '1', status: 'CONFIRMED', revision: 1, actorId: '12', updatedAt: '2026-11-25T00:00:00Z' }
      return route.fulfill({ json: { code: 200, data: review } })
    }
    return route.fulfill({ json: { code: 200, data: { enabled: true, latest: true, reviewedThisSample: !!review, review, events: review ? [{ revision: 1, issueId: '2', runId: '1', status: 'CONFIRMED', reason: 'CONFIRMED_EVIDENCE', actorId: '12', occurredAt: '2026-11-25T00:00:00Z' }] : [] } } })
  })
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '检查与异常' }).click()
  await page.getByRole('row').filter({ hasText: '合成缺失关联' }).getByRole('button', { name: '修复预览' }).click()
  await expect(page.getByText('本次样本尚未记录决策', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: '确认异常', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click()
  expect(changes).toEqual([])
  await page.getByRole('button', { name: '确认异常', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByText('本次样本已有决策', { exact: false })).toBeVisible()
  await expect(page.getByText('已确认样本 · 样本 #2 · 操作者 #12 · v1', { exact: true })).toBeVisible()
  expect(changes).toEqual(['CONFIRM'])
  await page.route('**/api/v1/system/data-quality/case-reviews/1/recheck', route => route.fulfill({ json: { code: 200, data: { caseId: '1', revision: 1, status: 'LINK_PRESENT_VERIFIED', observedAt: '2026-11-25T00:00:00Z' } } }))
  await page.getByRole('button', { name: '逐记录只读复验' }).click()
  await expect(page.getByRole('status').filter({ hasText: '当前唯一关联已核对' })).toBeVisible()
  await expect(page.getByText('不关闭案件，不代表其他异常已解决', { exact: false })).toBeVisible()
  expect(changes).toEqual(['CONFIRM'])
})

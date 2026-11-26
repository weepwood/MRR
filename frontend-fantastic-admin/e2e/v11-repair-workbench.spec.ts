import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.use({ actionTimeout: 10_000 })

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
      await route.fulfill({ json: { code: 200, data: events.map((action, i) => ({ id: i + 1, actorId: i ? 12 : 11, action, beforeArchiveId: action === 'VERIFIED' ? 2 : null, afterArchiveId: ['APPLIED', 'VERIFIED'].includes(action) ? 2 : null, createdAt: '2026-07-21T00:00:00Z' })) } })
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
  await expect(pane.getByRole('region', { name: '最近已加载关联证据' })).toContainText('复验时关联观察')
  await expect(pane.getByRole('region', { name: '最近已加载关联证据' })).toContainText('不代表当前数据库状态')
  await expect(pane.getByText('未关联 → 主档 #2', { exact: true })).toBeVisible()
  await expect(pane.getByText('主档 #2 → 主档 #2', { exact: true })).toBeVisible()
  await expect(pane.getByText('未关联 → 未关联', { exact: true })).toHaveCount(0)
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
  const compare = page.getByRole('combobox', { name: '完整性比较基准版本' })
  await expect(compare).toBeEnabled()
  await compare.focus()
  await compare.press('Enter')
  await expect(compare).toHaveAttribute('aria-expanded', 'true')
  await page.getByRole('option', { name: /^#1 ·/ }).click()
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

test('独立病案工作台直接查询、影像核对及窄屏布局', async ({ page }) => {
  // Multi-step workflow: keep the original per-action/assertion bounds.
  test.setTimeout(90_000)
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
  await expect(page.getByRole('button', { name: '查看病案主档 1 的关系' })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: '下一张', exact: true }).click()
  await expect(samples.getByRole('button').last()).toHaveAttribute('aria-pressed', 'true')
  await samples.getByRole('button').last().focus()
  await page.keyboard.press('Home')
  await expect(samples.getByRole('button').first()).toHaveAttribute('aria-pressed', 'true')
  await page.keyboard.press('End')
  await expect(samples.getByRole('button').last()).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: '专注原图' }).click()
  await expect(page.getByRole('button', { name: '识别文字', exact: true })).toHaveCount(0)
  await expect(image).toBeVisible()
  await page.getByRole('button', { name: '恢复布局' }).click()
  const controls = page.getByRole('group', { name: '原图显示控制' })
  const zoom = controls.getByRole('button', { name: '放大', exact: true })
  await expect(zoom).toBeEnabled()
  await zoom.click()
  await expect(controls.getByText('125%', { exact: true })).toBeVisible()
  await controls.getByRole('button', { name: '旋转 90°' }).click()
  await expect(image).toHaveCSS('transform', /matrix/)
  await expect(image).toHaveAttribute('style', /rotate\(90deg\)/)
  await controls.getByRole('button', { name: '适应窗口' }).click()
  await expect(controls.getByText('100%', { exact: true })).toBeVisible()
  await expect(image).toHaveAttribute('style', /rotate\(0deg\)/)
  await zoom.click()
  await page.getByRole('button', { name: '上一张', exact: true }).click()
  await expect(controls.getByText('100%', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一张', exact: true }).click()
  await expect(image).toBeVisible()

  await expect(image).toBeVisible()
  await page.getByRole('button', { name: '识别文字', exact: true }).click()
  await expect(page.getByRole('region', { name: '影像文字识别' })).toBeVisible()
  await page.getByRole('region', { name: '影像文字识别' }).getByRole('button', { name: '收起识别' }).click()
  await page.getByRole('navigation', { name: '病案详情分区' }).getByRole('button', { name: '文件完整性' }).click()
  await expect(page.getByRole('region', { name: '文件完整性分区' })).toBeFocused()
  await page.getByRole('navigation', { name: '病案详情分区' }).getByRole('button', { name: '影像核对' }).click()
  await expect(page.getByRole('region', { name: '影像核对分区' })).toBeFocused()
  await page.screenshot({ path: test.info().outputPath('standalone-archive-workbench.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('.archive-candidate').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '下一张', exact: true })).toBeDisabled()
  const narrow = await page.locator('.archive-stack--workspace').evaluate(el => ({ client: el.clientWidth, scroll: el.scrollWidth }))
  expect(narrow.scroll).toBeLessThanOrEqual(narrow.client + 2)
  await page.screenshot({ path: test.info().outputPath('standalone-archive-workbench-mobile.png'), fullPage: true })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: '关闭原图' }).click()
  await expect(image).toHaveCount(0)
})

test('独立病案工作台可从菜单打开，且兼容旧数据治理入口', async ({ page }) => {
  // Multi-step workflow: keep the original per-action/assertion bounds.
  test.setTimeout(90_000)
  await page.goto('/archive-workbench')
  const search = page.getByRole('textbox', { name: '病案查询值', exact: true })
  await expect(search).toBeVisible()
  await search.fill('26070000')
  await search.press('Enter')
  await expect(page.getByText('病案主档 #1', { exact: true })).toBeVisible()
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
  await expect(page.getByRole('tab', { name: '关系总览', exact: true })).toBeVisible()
  const navigation = page.getByRole('navigation', { name: '次导航' })
  await expect(navigation).toBeVisible()
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
  await page.route('**/api/v1/system/data-quality/case-reviews/1/recheck', route => route.fulfill({ status: 403, json: { code: 403, message: '合成访问拒绝' } }))
  await page.getByRole('button', { name: '逐记录只读复验' }).click()
  await expect(page.getByText('决策读取或保存失败，请刷新核对实际状态', { exact: true })).toBeVisible()
  await expect(page.getByText('已确认样本 · 样本 #2 · 操作者 #12 · v1', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '逐记录只读复验' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '确认异常', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '刷新决策' }).click()
  await expect(page.getByText('已确认样本 · 样本 #2 · 操作者 #12 · v1', { exact: true })).toBeVisible()
  expect(changes).toEqual(['CONFIRM'])
})

test('真实深色主题独立工作台与原图查看器在桌面和窄屏内可用', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'dark', enableMotion: false, pageTitleStyle: 'card' },
    layout: { enableMobileAdaptation: true },
  })))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/archive-workbench')
  await expect.poll(() => page.locator('html').evaluate(element => element.classList.contains('dark'))).toBe(true)
  const search = page.getByRole('textbox', { name: '病案查询值' })
  await search.fill('26070000')
  await search.press('Enter')
  await page.getByRole('group', { name: '有效影像列表' }).getByRole('button').first().click()
  const controls = page.getByRole('group', { name: '原图显示控制' })
  await expect(controls.getByRole('button', { name: '放大', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '识别文字', exact: true }).click()
  await expect(page.getByRole('region', { name: '影像文字识别' })).toBeVisible()
  const ocr = page.getByRole('region', { name: '影像文字识别' })
  await expect(ocr.getByText('正在读取', { exact: true })).toHaveCount(0)
  await expect.poll(() => ocr.getByRole('textbox').evaluate(el => el.getBoundingClientRect().height)).toBeLessThanOrEqual(440)
  await page.screenshot({ path: test.info().outputPath('archive-workspace-dark-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => page.locator('.archive-stack--workspace').evaluate(element => element.scrollWidth <= element.clientWidth + 2)).toBe(true)
  await expect(controls.getByRole('button', { name: '放大', exact: true })).toBeEnabled()
  await controls.getByRole('button', { name: '放大', exact: true }).click()
  await controls.getByRole('button', { name: '旋转 90°' }).click()
  await expect(page.getByAltText('待核对影像')).toHaveAttribute('style', /rotate\(90deg\)/)
  await page.screenshot({ path: test.info().outputPath('archive-workspace-dark-mobile.png'), fullPage: true })
})

test('异常样本按规则和级别筛选，明确区分无匹配与无样本', async ({ page }, testInfo) => {
  const issues = [
    { id: 1, check_code: 'DEMO_A', check_name: '合成规则甲', severity: 'WARNING', entity_type: 'mr_scan', entity_id: '1', detail: '合成样本甲' },
    { id: 2, check_code: 'DEMO_B', check_name: '合成规则乙', severity: 'CRITICAL', entity_type: 'mr_scan', entity_id: '2', detail: '合成样本乙' },
  ]
  let writes = 0
  await page.route('**/api/v1/system/data-quality/issues?*', route => route.fulfill({ json: { code: 200, data: issues } }))
  page.on('request', (request) => {
    if (request.url().includes('/system/data-quality/') && request.method() === 'POST') {
      writes++
    }
  })
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '检查与异常' }).click()
  const count = page.locator('.issue-sample-count')
  await expect(count).toHaveText('匹配 2 / 已加载 2 个样本')
  await page.locator('.issue-rule-select').click()
  await page.getByRole('option', { name: '合成规则甲' }).click()
  await expect(count).toHaveText('匹配 1 / 已加载 2 个样本')
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '异常级别筛选' }) }).click()
  await page.getByRole('option', { name: '严重', exact: true }).click()
  await expect(count).toHaveText('匹配 0 / 已加载 2 个样本')
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '异常级别筛选' }) }).click()
  await page.getByRole('option', { name: '警告', exact: true }).click()
  await expect(count).toHaveText('匹配 1 / 已加载 2 个样本')
  await page.getByRole('textbox', { name: '异常关键字筛选' }).fill('无匹配')
  await expect(count).toHaveText('匹配 0 / 已加载 2 个样本')
  await expect(page.getByText('没有符合筛选条件的样本', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重置异常样本筛选' }).click()
  await expect(count).toHaveText('匹配 2 / 已加载 2 个样本')
  expect(writes).toBe(0)
  await count.scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('quality-samples.png'), animations: 'disabled' })
})

test('规则直接定位样本，失败时明确保留旧结果并可重试', async ({ page }, testInfo) => {
  let failed = false
  await page.route('**/api/v1/system/data-quality/summary', route => route.fulfill({ json: { code: 200, data: {
    running: false,
    checks: [{ checkCode: 'A', checkName: '规则甲', severity: 'WARNING', issueCount: 20, sampledCount: 1 }, { checkCode: 'B', checkName: '规则乙', severity: 'CRITICAL', issueCount: 7, sampledCount: 0 }],
  } } }))
  await page.route('**/api/v1/system/data-quality/issues?*', route => failed
    ? route.fulfill({ status: 503, json: { code: 503, message: '合成失败' } })
    : route.fulfill({ json: { code: 200, data: [{ id: 1, check_code: 'A', check_name: '规则甲', severity: 'WARNING', entity_type: 'mr_scan', entity_id: '1' }] } }))
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '检查与异常' }).click()
  const region = page.getByRole('region', { name: '异常样本检查区' })
  await page.getByRole('button', { name: '查看规则 规则乙 的样本' }).click()
  await expect(region).toBeFocused()
  await expect(region.getByRole('status')).toContainText('匹配 0 / 已加载 1')
  await page.getByRole('button', { name: '查看规则 规则甲 的样本' }).click()
  await expect(region.getByRole('status')).toContainText('匹配 1 / 已加载 1')
  failed = true
  await region.getByRole('button', { name: '刷新样本' }).click()
  await expect(page.getByText('异常样本刷新失败，以下为上次成功读取的结果')).toBeVisible()
  await expect(region.getByRole('status')).toContainText('匹配 1 / 已加载 1')
  failed = false
  await region.getByRole('button', { name: '刷新样本' }).click()
  await expect(page.getByText('异常样本刷新失败，以下为上次成功读取的结果')).toHaveCount(0)
  await region.screenshot({ path: testInfo.outputPath('sample-drilldown.png'), animations: 'disabled' })
})

test('异常样本首次读取失败不可显示为无异常，成功空结果可恢复', async ({ page }) => {
  let failed = true
  await page.route('**/api/v1/system/data-quality/summary', route => route.fulfill({ json: { code: 200, data: { running: false, checks: [] } } }))
  await page.route('**/api/v1/system/data-quality/issues?*', route => failed
    ? route.fulfill({ status: 400, json: { code: 400, message: '合成失败' } })
    : route.fulfill({ json: { code: 200, data: [] } }))
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '检查与异常' }).click()
  const region = page.getByRole('region', { name: '异常样本检查区' })
  await expect(page.getByText('异常样本读取失败，不能判断当前是否存在异常')).toBeVisible()
  await expect(region.getByRole('status')).toHaveText('尚未取得样本')
  await expect(region.getByText('当前没有异常样本', { exact: true })).toHaveCount(0)
  await expect(page.getByText('规则结果尚未取得', { exact: true })).toBeVisible()
  failed = false
  await region.getByRole('button', { name: '刷新样本' }).click()
  await expect(region.getByRole('status')).toHaveText('匹配 0 / 已加载 0 个样本')
  await expect(region.getByText('当前没有异常样本', { exact: true })).toBeVisible()
})

test('异常样本并发刷新普通失败后访问拒绝必须清除已加载证据', async ({ page }) => {
  let denied = false
  const sample = { id: 51, check_code: 'SYNTHETIC', check_name: '合成独立规则', severity: 'WARNING', entity_type: 'mr_scan', entity_id: '51' }
  await page.route('**/api/v1/system/data-quality/summary', route => denied
    ? route.fulfill({ status: 400, json: { code: 400, message: '合成普通失败' } })
    : route.fulfill({ json: { code: 200, data: { running: false, checks: [{ checkCode: 'SYNTHETIC', checkName: '合成独立规则', severity: 'WARNING', issueCount: 1 }] } } }))
  await page.route('**/api/v1/system/data-quality/issues?*', async (route) => {
    if (denied) {
      // 故意延后拒绝，复现普通错误先到达的并发顺序。
      await new Promise(resolve => setTimeout(resolve, 600))
      await route.fulfill({ status: 403, json: { code: 403, message: '合成访问拒绝' } })
    }
    else {
      await route.fulfill({ json: { code: 200, data: [sample] } })
    }
  })
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '检查与异常' }).click()
  const region = page.getByRole('region', { name: '异常样本检查区' })
  await expect(region.getByRole('status')).toHaveText('匹配 1 / 已加载 1 个样本')
  await page.getByRole('button', { name: '查看规则 合成独立规则 的样本' }).click()
  denied = true
  await region.getByRole('button', { name: '刷新样本' }).click()
  await expect(region.getByRole('status')).toHaveText('尚未取得样本')
  await expect(region.getByText('合成独立规则', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '查看规则 合成独立规则 的样本' })).toHaveCount(0)
  await expect(page.getByText('异常样本刷新失败，以下为上次成功读取的结果')).toHaveCount(0)
  denied = false
  await region.getByRole('button', { name: '刷新样本' }).click()
  await expect(region.getByRole('status')).toHaveText('匹配 1 / 已加载 1 个样本')
})

import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

const patient = { id: 92001, bah: '00004217', name: '合成患者甲', department: '示例科室', bingqu: '示例病区', chuangwei: 'A-08', ruyuan: '2026-01-08', admissiontime: '2026-01-08 08:30:00' }
const record = { id: 92002, bah: '00004217', sjh: '00000608', boxNo: 'BOX-DEMO-01', expectedBoxNo: 'BOX-DEMO-02', status: 'MISPLACED', remark: '合成记录：核对实际箱号', updatedAt: '2026-01-08T09:15:00' }
const group = { boxNo: 'BOX-DEMO-01', recordCount: 1, abnormalCount: 1, updatedAt: '2026-01-08T09:15:00' }
async function mockLists(page: Page) {
  await page.route(/\/api\/v1\/(patients|archive-box-records)(?:\/|\?|$)/, async (route) => {
    const url = new URL(route.request().url())
    if (route.request().method() !== 'GET') {
      await route.abort()
      return
    }
    const detail = url.pathname.split('/').at(-1)
    let data: unknown
    if (url.pathname.endsWith('/patients')) {
      const empty = url.searchParams.get('keyword') === 'NO-MATCH'
      data = { list: empty ? [] : [patient], total: empty ? 0 : 21 }
    }
    else if (url.pathname.endsWith('/patients/analytics/summary')) {
      data = { totalRecords: 21, totalArchives: 20, yearArchives: 12, missingIdCardRecords: 1, confirmedMultiRecordGroups: 1, suspectedMultiRecordGroups: 1 }
    }
    else if (detail === 'missing-idcard') {
      data = { list: [patient], total: 1 }
    }
    else if (detail === 'multi-record-groups') {
      const confirmed = { matchType: 'IDCARD', confidence: 'HIGH', patientName: '合成患者甲', archiveCount: 2, archiveNumbers: ['00004217', '00004218'] }
      const suspected = { matchType: 'NAME_ONLY', confidence: 'LOW', patientName: '合成患者乙', archiveCount: 2, archiveNumbers: ['00004219', '00004220'] }
      const list = url.searchParams.get('includeSuspected') === 'false' ? [confirmed] : [confirmed, suspected]
      data = { list, total: list.length }
    }
    else if (detail === 'summary') {
      data = { totalRecords: 12, totalBoxes: 5, missingCount: 1, abnormalCount: 2 }
    }
    else if (detail === 'boxes') {
      data = { list: [group], total: 1 }
    }
    else {
      data = { list: [record], total: 1 }
    }
    await route.fulfill({ json: { code: 200, data } })
  })
}

test.beforeEach(async ({ page }) => {
  await mockLists(page)
})

test('患者筛选只作用于患者列表，保留 Enter、重置和病案号', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { enableMotion: false },
    layout: { enableMobileAdaptation: true },
  })))
  await page.goto('/patients')
  await expect(page.getByRole('heading', { name: '患者管理', level: 1 })).toBeVisible()
  const filter = page.getByRole('textbox', { name: '关键词', exact: true })
  await page.locator('label').filter({ hasText: /^关键词$/ }).click()
  await expect(filter).toBeFocused()
  await expect(page.getByRole('link', { name: '00004217', exact: true })).toHaveAttribute('href', /bah=00004217/)
  await filter.fill('NO-MATCH')
  const result = page.waitForResponse(response => /\/api\/v1\/patients\?/.test(response.url()) && new URL(response.url()).searchParams.get('keyword') === 'NO-MATCH')
  await filter.press('Enter')
  await result
  await expect(page.getByText('暂无患者记录')).toBeVisible()
  await expect(page.getByRole('tabpanel', { name: '患者表格' }).locator('.el-pagination')).toHaveCount(0)
  await page.getByRole('tab', { name: /身份证为空病案/ }).click()
  await expect(filter).toBeHidden()
  await expect(page.getByRole('cell', { name: '00004217', exact: true })).toBeVisible()
  await page.getByRole('tab', { name: /同一患者多病案/ }).click()
  await expect(filter).toBeHidden()
  await expect(page.getByText('仅姓名相同', { exact: true })).toBeVisible()
  await page.getByRole('switch', { name: '包含仅姓名相同的疑似分组' }).press('Space')
  await expect(page.getByText('仅姓名相同', { exact: true })).toBeHidden()
  await expect(page.getByText('身份证一致', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '患者表格', exact: true }).click()
  await expect(filter).toHaveValue('NO-MATCH')
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(filter).toHaveValue('')
  await expect(page.getByRole('cell', { name: '00004217', exact: true })).toBeVisible()
  await page.setViewportSize({ width: 375, height: 844 })
  await page.getByRole('tablist').scrollIntoViewIfNeeded()
  for (const tab of await page.getByRole('tab').all()) {
    await expect(tab).toBeInViewport({ ratio: 0.99 })
  }
})

test('患者汇总失败时，列表可见且指标不显示假零', async ({ page }) => {
  await page.route('**/api/v1/patients/analytics/summary?**', route => route.fulfill({
    status: 500,
    json: { code: 500, message: '合成测试：患者汇总暂不可用' },
  }))
  await page.goto('/patients')
  await expect(page.locator('.analytics-panel .el-alert')).toBeVisible()
  await expect(page.getByText('患者数据行', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('cell', { name: '00004217', exact: true })).toBeVisible()
})

test('窄屏装箱切换、筛选和表单完整可达', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { colorScheme: 'dark', enableMotion: false },
    layout: { enableMobileAdaptation: true },
  })))
  await page.goto('/archive-boxes')
  await expect(page.getByRole('heading', { name: '装箱明细', level: 2 })).toBeVisible()
  await expect(page.locator('.view-switcher .el-radio-button.is-active .el-radio-button__inner')).toHaveCSS('color', 'rgb(255, 255, 255)')
  const metrics = page.getByRole('region', { name: '装箱概况' })
  await expect(metrics.locator('.mrr-metric-card')).toHaveCount(4)
  const cards = await metrics.locator('.mrr-metric-card').evaluateAll(elements => elements.map(el => el.getBoundingClientRect().top))
  expect(cards).toHaveLength(4)
  expect(cards[0]).toBe(cards[1])
  expect(cards[2]).toBe(cards[3])
  await expect(page.getByText('存放在其他箱子', { exact: true }).filter({ visible: true })).toBeVisible()
  await page.getByText('箱号视图', { exact: true }).click()
  await expect(page.getByRole('heading', { name: '箱号列表', level: 2 })).toBeVisible()
  await expect(page.getByRole('textbox', { name: '搜索箱号', exact: true })).toBeVisible()
  const lookup = page.waitForResponse(response => /\/api\/v1\/archive-box-records\?/.test(response.url()) && new URL(response.url()).searchParams.get('boxNo') === 'BOX-DEMO-01')
  await page.getByRole('button', { name: 'BOX-DEMO-01', exact: true }).click()
  await lookup
  await expect(page.getByRole('textbox', { name: '实际箱号', exact: true })).toHaveValue('BOX-DEMO-01')
  await expect(page.getByRole('cell', { name: '00000608', exact: true })).toBeVisible()

  await expect.poll(() => page.getByRole('button', { name: '查询', exact: true }).evaluate((el) => {
    const rect = el.getBoundingClientRect()
    return rect.left >= 0 && rect.right <= document.documentElement.clientWidth
  })).toBe(true)
  await page.getByRole('button', { name: '新增装箱记录', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新增装箱记录', exact: true })
  await expect(dialog).toBeVisible()
  await expect.poll(() => dialog.locator('.el-dialog').evaluate((el) => {
    const rect = el.getBoundingClientRect()
    return rect.left >= 10 && rect.right <= innerWidth - 10 && el.scrollWidth <= el.clientWidth + 1
  })).toBe(true)
  const cancel = dialog.getByRole('button', { name: '取消', exact: true })
  await expect(cancel).toBeInViewport({ ratio: 0.99 })
  await cancel.click()
  await expect(dialog).toBeHidden()
})

test('箱号查询失败时可在当前视图重试', async ({ page }) => {
  await page.route('**/api/v1/archive-box-records/boxes?**', route => route.fulfill({
    status: 500,
    json: { code: 500, message: '合成测试：箱号列表暂不可用' },
  }), { times: 1 })
  await page.goto('/archive-boxes')
  await expect(page.getByRole('cell', { name: '00004217', exact: true })).toBeVisible()
  await page.getByText('箱号视图', { exact: true }).click()
  const failed = page.locator('.app-error')
  await expect(failed).toBeVisible()
  await expect(page.getByRole('heading', { name: '箱号列表', level: 2 })).toBeVisible()
  await failed.getByRole('button', { name: '重试' }).click()
  await expect(page.getByRole('button', { name: 'BOX-DEMO-01', exact: true })).toBeVisible()
  await expect(failed).toBeHidden()
})

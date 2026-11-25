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
  await page.screenshot({ path: test.info().outputPath('manifest-workbench.png'), fullPage: true })
})

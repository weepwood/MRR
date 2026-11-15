import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

async function selectFailures(page: Page) {
  await page.route(/\/api\/v1\/oss\/migration\/readiness(?:\?|$)/, route => route.fulfill({ json: { code: 200, data: { ready: false, noActiveJob: true, ossConfigured: true, sourcePathReadable: true, sampleSize: 0, warnings: [] } } }))
  await page.route(/\/api\/v1\/oss\/migration\/jobs(?:\?|$)/, route => route.fulfill({ json: { code: 200, data: { list: [], total: 0 } } }))
  await page.route(/\/api\/v1\/oss\/migration\/management\/logs(?:\?|$)/, route => route.fulfill({ json: { code: 200, data: { list: [101, 102].map(scanId => ({ id: scanId, scanId, migrationStatus: 'failed', localPath: 'SYNTHETIC' })), total: 2 } } }))
  await page.goto('/oss-migration')
  const card = page.locator('.el-card').filter({ has: page.getByText('迁移日志与失败处理', { exact: true }) })
  await card.locator('.el-table__header-wrapper .el-checkbox').click()
  await expect(card.getByRole('button', { name: '重置失败记录 2 条' })).toBeEnabled()
  return card
}

test('确认范围固定且只发一次POST，部分更新单独提示', async ({ page }) => {
  const bodies: unknown[] = []
  await page.route(/\/api\/v1\/oss\/migration\/retry$/, async (route) => {
    bodies.push(route.request().postDataJSON())
    await route.fulfill({ json: { code: 200, data: { updated: 1 } } })
  })
  const card = await selectFailures(page)
  const action = card.getByRole('button', { name: '重置失败记录 2 条' })
  await action.click()
  const dialog = page.getByRole('dialog', { name: '确认重置失败记录' })
  await expect(dialog).toContainText('本次选中的 2 条')
  await expect(action).toHaveClass(/is-loading/)
  // 模拟确认期间已开始的列表刷新清空选择；确认内容和实际范围必须一致。
  await card.locator('.el-table__header-wrapper .el-checkbox').evaluate((label: HTMLElement) => label.click())
  await dialog.getByRole('button', { name: '确认重置', exact: true }).click()
  await expect(card).toContainText('已重置 1/2 条记录')
  expect(bodies).toEqual([{ scanIds: [101, 102] }])
})

test('取消不POST，服务不可达时不重试并提示先核对', async ({ page }) => {
  let requests = 0
  await page.route(/\/api\/v1\/oss\/migration\/retry$/, async (route) => {
    requests++
    await route.fulfill({ status: 503, json: { code: 503, message: '合成故障' } })
  })
  const card = await selectFailures(page)
  await card.getByRole('button', { name: '重置失败记录 2 条' }).click()
  await page.getByRole('dialog', { name: '确认重置失败记录' }).getByRole('button', { name: '取消', exact: true }).click()
  expect(requests).toBe(0)
  await card.getByRole('button', { name: '重置失败记录 2 条' }).click()
  await page.getByRole('dialog', { name: '确认重置失败记录' }).getByRole('button', { name: '确认重置', exact: true }).click()
  await expect(card).toContainText('请求可能已生效，不要直接重复提交')
  expect(requests).toBe(1)
})

test('真实401走统一退出流程，失败重置不重试', async ({ page }) => {
  let requests = 0
  await page.route(/\/api\/v1\/oss\/migration\/retry$/, async (route) => {
    requests++
    await route.fulfill({ status: 401, json: { code: 401, message: '合成会话失效' } })
  })
  const card = await selectFailures(page)
  await card.getByRole('button', { name: '重置失败记录 2 条' }).click()
  await page.getByRole('dialog', { name: '确认重置失败记录' }).getByRole('button', { name: '确认重置', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(requests).toBe(1)
})

test('真实403保留权限拒绝反馈且不重试', async ({ page }) => {
  let requests = 0
  await page.route(/\/api\/v1\/oss\/migration\/retry$/, async (route) => {
    requests++
    await route.fulfill({ status: 403, json: { code: 403, message: '合成权限拒绝' } })
  })
  const card = await selectFailures(page)
  await card.getByRole('button', { name: '重置失败记录 2 条' }).click()
  await page.getByRole('dialog', { name: '确认重置失败记录' }).getByRole('button', { name: '确认重置', exact: true }).click()
  await expect(card).toContainText('当前会话或权限不允许重置')
  await expect(page).toHaveURL(/\/oss-migration/)
  expect(requests).toBe(1)
})

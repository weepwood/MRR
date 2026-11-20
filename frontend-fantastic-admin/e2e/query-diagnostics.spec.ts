import { expect, test } from '@playwright/test'

/**
 * 病案查询失败态 + 诊断弹窗。
 *
 * 这里守的是「出问题之后别再糊弄人」：文案要说清下一步怎么办、报错不能甩英文、
 * 诊断结果要按步骤读得懂（中文状态 + 样本表），而不是把 details 原样倒成 JSON。
 */
test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

function emptyResult(reasonCode: string) {
  return {
    code: 200,
    data: [],
    meta: { requestId: '0123456789abcdef', reasonCode, lookupStrategy: 'LEGACY_CODE' },
  }
}

test('空影像查询显示追踪编号，管理员显式触发深入诊断', async ({ page }) => {
  let diagnoses = 0
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  await page.route('**/api/v1/operations/query-diagnostics', async (route) => {
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

test('查询失败卡片给出下一步建议，追踪编号可一键复制', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({
    status: 500,
    json: { code: 500, message: '', data: null, meta: { requestId: '0123456789abcdef', reasonCode: 'QUERY_FAILED' } },
  }))

  await page.goto('/archive?bah=123')
  const card = page.locator('.query-failure')
  await expect(card).toBeVisible()

  // 只给结论不给动作等于把问题丢回给用户，必须点明「先重试、再找管理员」
  await expect(card).toContainText('重新查询')
  await expect(card).toContainText('追踪编号')
  // 报错文案走统一中文化，不能把 axios 的英文原文甩到界面上
  await expect(card).not.toContainText('Request failed')
  await expect(card.locator('.query-failure-title')).not.toBeEmpty()

  await card.getByRole('button', { name: '复制追踪编号' }).click()
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('0123456789abcdef')
})

test('不同失败原因给出不同建议，而不是一律「查不到」', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  await page.goto('/archive?bah=123')
  await expect(page.locator('.query-failure')).toContainText('请核对原始编号')

  await page.unroute('**/api/v1/img/search?**')
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('AMBIGUOUS_CODE') }))
  await page.goto('/archive?bah=456')
  await expect(page.locator('.query-failure')).toContainText('对应多条病案')
})

test('诊断弹窗按步骤中文化展示，样本用表格而不是原始 JSON', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('AMBIGUOUS_CODE') }))
  await page.route('**/api/v1/operations/query-diagnostics', route => route.fulfill({
    json: {
      code: 200,
      data: {
        readOnly: true,
        scope: '最多51条样本；不代表全量统计，不修改任何病案',
        steps: [
          { code: 'ARCHIVE_COMPAT', status: 'AMBIGUOUS', durationMs: 4, message: '存在多个候选，禁止自动关联', details: { samples: [{ id: 88213 }, { id: 99017 }], sampledCount: 2, truncated: true } },
          { code: 'SCAN_COMPAT', status: 'UNKNOWN', durationMs: 2001, message: '探测失败或超时；不能据此判定记录不存在', details: {} },
          { code: 'CACHE', status: 'INFO', durationMs: 0, message: '存在缓存不代表数据库当前状态', details: { hit: false } },
        ],
      },
    },
  }))

  await page.goto('/archive?bah=123')
  await page.getByRole('button', { name: '排查本次查询' }).click()

  const dialog = page.getByRole('dialog', { name: '病案查询诊断' })
  await expect(dialog).toBeVisible()
  // 状态与标题都翻成中文，code 只作为次要线索保留
  await expect(dialog).toContainText('主档兼容匹配')
  await expect(dialog).toContainText('多个候选')
  await expect(dialog).toContainText('探测失败')
  // 摘要按「通过 / 需关注 / 未检查」计数，一眼看结论
  await expect(dialog).toContainText('项需关注')
  // 样本使用可读表头，并限制展示字段。
  await expect(dialog.locator('.step-samples tbody tr')).toHaveCount(2)
  await expect(dialog.locator('.step-samples thead')).toContainText('记录 ID')
  await expect(dialog).not.toContainText('"sampledCount"')
  await expect(dialog).toContainText('只读探测')

  // 弹窗自己滚，不能把按钮顶到视口外
  const footprint = await page.evaluate(() => {
    const el = document.querySelector('.query-diagnostics')
    const rect = el?.getBoundingClientRect()
    return rect ? { bottom: rect.bottom, height: rect.height, viewport: window.innerHeight } : null
  })
  expect(footprint).not.toBeNull()
  expect(footprint!.bottom).toBeLessThanOrEqual(footprint!.viewport)
})

test('编辑输入框后仍诊断实际失败的查询', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  let submitted: unknown
  await page.route('**/api/v1/operations/query-diagnostics', async (route) => {
    submitted = route.request().postDataJSON()
    await route.fulfill({ json: { code: 200, data: { readOnly: true, steps: [] } } })
  })
  await page.goto('/archive?bah=123&sjh=456')
  await expect(page.getByLabel('本次请求追踪编号')).toHaveValue('0123456789abcdef')
  await page.locator('input[name="archive-bah"]').fill('789')
  await page.getByRole('button', { name: '排查本次查询' }).click()
  await expect.poll(() => submitted).toMatchObject({ bah: '123', sjh: '', requestId: '0123456789abcdef' })
  await expect(page.getByRole('dialog')).toContainText('病案号 123')
})

test('前端校验失败不继承上一笔请求的追踪编号和操作', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  await page.goto('/archive?bah=123')
  const card = page.locator('.query-failure')
  await expect(card.getByLabel('本次请求追踪编号')).toBeVisible()
  await page.locator('input[name="archive-bah"]').fill('10000001')
  await page.locator('.search-card').getByRole('button', { name: '查询', exact: true }).click()
  await expect(card).toContainText('必须输入上架号')
  await expect(card.getByLabel('本次请求追踪编号')).toHaveCount(0)
  await expect(card.getByRole('button', { name: '排查本次查询' })).toHaveCount(0)
  await expect(card.getByRole('button', { name: '重新查询' })).toHaveCount(0)
})

test('关闭诊断会取消未完成请求，重开后显示新报告', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  let releaseFirst!: () => void
  const firstGate = new Promise<void>((resolve) => { releaseFirst = resolve })
  let firstRequest: import('@playwright/test').Request | undefined
  const failedRequests: import('@playwright/test').Request[] = []
  page.on('requestfailed', request => failedRequests.push(request))
  await page.route('**/api/v1/operations/query-diagnostics', async (route) => {
    const isFirst = !firstRequest
    if (isFirst) {
      firstRequest = route.request()
      await firstGate
    }
    await route.fulfill({
      json: { code: 200, data: { readOnly: true, steps: [{ code: 'DATABASE', status: 'OK', message: isFirst ? '上一轮样本' : '新一轮样本', durationMs: 1, details: {} }] } },
    }).catch(() => {})
  })

  try {
    await page.goto('/archive?bah=123')
    const open = page.getByRole('button', { name: '排查本次查询' })
    const dialog = page.getByRole('dialog', { name: '病案查询诊断' })
    await open.click()
    await expect.poll(() => firstRequest?.method()).toBe('POST')
    await dialog.locator('.el-dialog__headerbtn').click()
    await expect(dialog).toBeHidden()
    await expect.poll(() => failedRequests.includes(firstRequest), { timeout: 3000 }).toBe(true)
    await open.click()
    await expect(dialog).toContainText('新一轮样本')
    releaseFirst()
    await expect(dialog).not.toContainText('上一轮样本')
  }
  finally {
    releaseFirst()
  }
})

test('诊断权限拒绝时只显示安全提示与关闭入口', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  await page.route('**/api/v1/operations/query-diagnostics', route => route.fulfill({
    status: 403,
    json: { code: 403, message: '合成底层错误内容' },
  }))
  await page.goto('/archive?bah=123')
  await page.getByRole('button', { name: '排查本次查询' }).click()
  const dialog = page.getByRole('dialog', { name: '病案查询诊断' })
  await expect(dialog).toContainText('当前账号没有病案查询诊断权限')
  await expect(dialog).not.toContainText('合成底层错误内容')
  await expect(dialog.getByRole('button', { name: '重新探测' })).toHaveCount(0)
})

test('诊断服务暂不可用时可用原条件重试', async ({ page }) => {
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  const attempts: unknown[] = []
  await page.route('**/api/v1/operations/query-diagnostics', async (route) => {
    attempts.push(route.request().postDataJSON())
    if (attempts.length === 1) {
      await route.fulfill({ status: 503, json: { code: 503, message: '合成底层错误内容' } })
      return
    }
    if (attempts.length === 2) {
      await route.fulfill({ json: { code: 200, data: { readOnly: true, steps: [null] } } })
      return
    }
    await route.fulfill({
      json: { code: 200, data: { readOnly: true, steps: [{ code: 'ARCHIVE_EXACT', status: 'NO_SAMPLE', message: '本次探测未返回样本', durationMs: 1, details: { sampledCount: 0 } }] } },
    })
  })
  await page.goto('/archive?sjh=123')
  await page.getByRole('button', { name: '排查本次查询' }).click()
  const dialog = page.getByRole('dialog', { name: '病案查询诊断' })
  await expect(dialog).toContainText('诊断服务暂时不可用')
  await expect(dialog).not.toContainText('0 项通过')
  await dialog.getByRole('button', { name: '重新探测' }).click()
  await expect(dialog).toContainText('诊断返回格式异常', { timeout: 5000 })
  await dialog.getByRole('button', { name: '重新探测' }).click()
  await expect(dialog).toContainText('无匹配样本')
  expect(attempts).toHaveLength(3)
  expect(attempts.slice(1)).toEqual([attempts[0], attempts[0]])
})

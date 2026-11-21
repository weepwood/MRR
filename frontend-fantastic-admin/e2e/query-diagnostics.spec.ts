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

test('从病案诊断追踪访问日志和运行错误，重置后恢复普通列表', async ({ page }) => {
  const requestId = '0123456789abcdef'
  const accessLog = {
    id: 921,
    requestId,
    requestUri: '/api/v1/img/search',
    method: 'GET',
    responseStatus: 500,
  }
  const errorGroup = { id: 72, errorId: 'ERR-SYNTHETIC-72', requestId, level: 'ERROR', occurrenceCount: 1 }
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
  await page.route('**/api/v1/operations/query-diagnostics', route => route.fulfill({
    json: { code: 200, data: { readOnly: true, steps: [{ code: 'REQUEST_RECEIVED', status: 'OK', message: '找到访问记录' }] } },
  }))
  await page.route('**/api/v1/logs/search?**', (route) => {
    const matches = new URL(route.request().url()).searchParams.get('keyword') === requestId
    return route.fulfill({ json: { code: 200, data: { list: matches ? [accessLog] : [], total: Number(matches) } } })
  })
  await page.route('**/api/v1/logs/921', route => route.fulfill({ json: { code: 200, data: accessLog } }))
  await page.route('**/api/v1/system-errors?**', (route) => {
    const matches = new URL(route.request().url()).searchParams.get('keyword') === requestId
    return route.fulfill({ json: { code: 200, data: { list: matches ? [errorGroup] : [], total: Number(matches) } } })
  })
  await page.route('**/api/v1/system-errors/72', route => route.fulfill({ json: { code: 200, data: errorGroup } }))
  await page.route('**/api/v1/system-errors/overview', route => route.fulfill({ json: { code: 200, data: null } }))
  await page.route('**/api/v1/system-errors/modules', route => route.fulfill({ json: { code: 200, data: [] } }))

  await page.goto('/archive?bah=123')
  await page.getByRole('button', { name: '排查本次查询' }).click()
  await page.getByRole('link', { name: '查看原请求访问日志' }).click()
  await expect(page).toHaveURL(`/logs?requestId=${requestId}`)
  await expect(page.getByLabel('关键字', { exact: true })).toHaveValue(requestId)
  await page.getByRole('row').filter({ hasText: requestId }).getByRole('button', { name: '查看详情' }).click()
  await page.getByRole('button', { name: '查看相关运行错误' }).click()

  await expect(page).toHaveURL(`/runtime-errors?requestId=${requestId}`)
  await page.getByRole('row').filter({ hasText: errorGroup.errorId }).getByRole('button', { name: '详情', exact: true }).click()
  await page.getByRole('button', { name: '查看访问记录' }).click()
  await expect(page).toHaveURL(`/logs?requestId=${requestId}`)
  await expect(page.getByRole('row').filter({ hasText: requestId })).toBeVisible()

  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page).toHaveURL('/logs')
  await expect(page.getByLabel('关键字', { exact: true })).toHaveValue('')
  await expect(page.getByText('暂无日志记录', { exact: true })).toBeVisible()
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
  await expect(dialog).toContainText('探测累计耗时 2005 ms')
  const priority = dialog.getByRole('button', { name: '扫描记录兼容匹配 · 探测失败' })
  await expect(priority).toBeVisible()
  await priority.focus()
  await page.keyboard.press('Enter')
  await expect(dialog.getByRole('heading', { name: '扫描记录兼容匹配' })).toBeFocused()
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

for (const viewport of [{ width: 1024, height: 400 }, { width: 320, height: 568 }, { width: 1366, height: 768 }]) {
  test(`诊断报告加载后操作区保持可见和稳定 ${viewport.width}×${viewport.height}`, async ({ page }) => {
    await page.setViewportSize(viewport)
    if (viewport.width === 320) {
      await page.emulateMedia({ reducedMotion: 'reduce' })
    }
    await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: emptyResult('NO_MATCHING_IMAGES') }))
    let release!: () => void
    const reportReady = new Promise<void>((resolve) => { release = resolve })
    const accessTime = '2026-11-21T02:02:03.123456+08:00'
    const bah = viewport.width === 320 ? 'SYNTH'.padEnd(64, '7') : '123'
    await page.route('**/api/v1/operations/query-diagnostics', async (route) => {
      await reportReady
      await route.fulfill({ json: { code: 200, data: {
        readOnly: true,
        scope: '每个探测步骤最多取 51 条样本。',
        steps: [
          { code: 'INPUT', status: 'OK', durationMs: 0, message: '按病案号查询', details: {} },
          { code: 'REQUEST_RECEIVED', status: 'OK', durationMs: 13, message: '已匹配原请求访问日志', details: { samples: [{ access_time: accessTime, response_status: 200, execute_time: 12345 }], sampledCount: 1 } },
          { code: 'DATABASE', status: 'OK', durationMs: 1, message: '数据库连接及默认 Schema', details: { samples: [{ expected_schema: true }], sampledCount: 1 } },
          { code: 'ARCHIVE_EXACT', status: 'OK', durationMs: 5, message: '主档原值精确匹配', details: { samples: [{ id: 234 }], sampledCount: 1 } },
          { code: 'ARCHIVE_COMPAT', status: 'AMBIGUOUS', durationMs: 4, message: '比较键有多个兼容候选，请核对原始编号', details: { samples: [{ id: 234 }, { id: 235 }], sampledCount: 2, truncated: true } },
          { code: 'SCAN_EXACT', status: 'OK', durationMs: 12, message: '扫描原值记录；0 为无效，NULL 状态需核实', details: { samples: Array.from({ length: 51 }, (_, i) => ({ id: 9900000000 + i, archive_id: i % 2 ? 234 : null, uploadflag: 1 })), sampledCount: 51, truncated: true } },
          { code: 'SCAN_COMPAT', status: 'UNKNOWN', durationMs: 2000, message: '探测失败或超时', details: {} },
          { code: 'CACHE', status: 'INFO', durationMs: 0, message: '以直接探测为准', details: { hit: false } },
          { code: 'CLIENT', status: 'CLIENT_CONFIRMATION_REQUIRED', durationMs: 0, message: '核对原请求与分类', details: {} },
        ],
      } } })
    })

    const dialog = page.getByRole('dialog', { name: '病案查询诊断' })
    const trigger = page.getByRole('button', { name: '排查本次查询' })
    const close = dialog.getByRole('button', { name: '关闭', exact: true })
    const footerBottom = () => close.evaluate(button => button.getBoundingClientRect().bottom)
    try {
      await page.goto(`/archive?bah=${bah}`)
      await page.evaluate(() => {
        document.addEventListener('animationstart', (event) => {
          if (event.animationName === 'dialog-fade-in' && event.target instanceof HTMLElement) {
            const style = getComputedStyle(event.target)
            event.target.dataset.animationDuration = style.animationDuration
            event.target.dataset.animationEasing = style.animationTimingFunction
          }
        })
      })
      await trigger.focus()
      await page.keyboard.press('Enter')
      await expect(dialog).toContainText('正在探测')
      if (viewport.width === 320) {
        await expect.poll(() => dialog.evaluate(el => getComputedStyle(el).animationDuration)).toBe('0.001s')
      }
      else {
        await expect(dialog).toHaveAttribute('data-animation-duration', '0.22s')
        await expect(dialog).toHaveAttribute('data-animation-easing', 'cubic-bezier(0.22, 1, 0.36, 1)')
      }
      await expect.poll(() => dialog.evaluate(el => el.getAnimations().length)).toBe(0)
      const before = await footerBottom()
      release()
      await expect(dialog.locator('.diagnostics-step')).toHaveCount(9)
      const after = await footerBottom()
      expect(after).toBeLessThanOrEqual(viewport.height)
      expect(Math.abs(after - before)).toBeLessThanOrEqual(2)
      await expect(dialog).toContainText('展示前 5 条，共采样 51 条')

      if (viewport.width === 320) {
        const reportBody = dialog.getByRole('region', { name: '诊断报告' })
        await expect(reportBody.locator('.meta-value')).toHaveText(`病案号 ${bah}`)
        expect(await reportBody.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1)
        const logSample = dialog.locator('.step-sample-list').filter({ hasText: accessTime })
        await expect(logSample.getByText('访问时间', { exact: true })).toBeVisible()
        await expect(logSample.getByText(accessTime, { exact: true })).toBeVisible()
        await expect(logSample.getByText('HTTP 状态', { exact: true })).toBeVisible()
        expect(await logSample.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1)
      }
      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()
      await expect(trigger).toBeFocused()
    }
    finally {
      release()
    }
  })
}

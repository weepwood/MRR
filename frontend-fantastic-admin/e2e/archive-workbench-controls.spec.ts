import { expect, test } from '@playwright/test'

test('桌面检索栏支持键盘调整、复位且窄屏保持单栏', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/archive-workbench')
  const divider = page.getByRole('separator', { name: '调整检索栏宽度' })
  const rail = page.getByRole('region', { name: '病案检索及候选' })
  const width = () => rail.evaluate(el => el.getBoundingClientRect().width)
  await expect(divider).toBeVisible()
  await expect.poll(width).toBeCloseTo(260, 0)
  const bounds = (await divider.boundingBox())!
  await page.mouse.move(bounds.x + bounds.width / 2, bounds.y + 15)
  await page.mouse.down()
  try {
    await page.mouse.move(bounds.x + bounds.width / 2 + 60, bounds.y + 15, { steps: 6 })
    await expect.poll(width).toBeCloseTo(320, 0)
  }
  finally { await page.mouse.up() }
  const nextBounds = (await divider.boundingBox())!
  await divider.evaluate(el => el.addEventListener('pointerdown', (event) => {
    el.setAttribute('data-test-pointer-id', String((event as PointerEvent).pointerId))
  }, { once: true }))
  await page.mouse.move(nextBounds.x + nextBounds.width / 2, nextBounds.y + 15)
  await page.mouse.down()
  try {
    await expect(divider).toHaveClass(/is-dragging/)
    await divider.dispatchEvent('pointercancel', { pointerId: Number(await divider.getAttribute('data-test-pointer-id')), pointerType: 'mouse' })
    await expect(divider).not.toHaveClass(/is-dragging/)
    await page.mouse.move(nextBounds.x + 20, nextBounds.y + 15)
    await expect.poll(width).toBeCloseTo(320, 0)
  }
  finally { await page.mouse.up() }
  await divider.focus()
  await divider.press('End')
  await expect(divider).toHaveAttribute('aria-valuenow', '360')
  await expect.poll(width).toBeGreaterThan(300)
  await divider.press('Home')
  await expect.poll(width).toBeCloseTo(220, 0)
  await divider.dblclick()
  await expect.poll(width).toBeCloseTo(260, 0)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(divider).toBeHidden()
  await expect.poll(() => page.locator('.archive-stack--workspace').evaluate(el => el.scrollWidth <= el.clientWidth + 2)).toBe(true)
  await expect(page.getByRole('textbox', { name: '病案查询值' })).toBeVisible()
})

test('事务审计慢请求允许切换工单，失败可独立重试和分页', async ({ page }) => {
  const order = (id: number) => ({ id, issueId: id, scanId: id, targetArchiveId: 2, status: 'VERIFIED', proposedBy: 11, approvedBy: 12 })
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  let fail = true
  let lists = 0
  await page.route('**/api/v1/system/data-quality/repair-orders**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.endsWith('/events')) {
      lists++
      await route.fulfill({ json: { code: 200, data: { enabled: false, actorId: 12, orders: [order(902), order(901)] } } })
      return
    }
    if (url.pathname.includes('/902/')) { await gate }
    const after = Number(url.searchParams.get('after') ?? 0)
    if (url.pathname.includes('/901/') && fail) {
      await route.fulfill({ status: 400, json: { code: 400, message: 'synthetic audit failure' } })
      return
    }
    const event = (id: number) => ({ id, actorId: 12, action: 'VERIFIED', beforeArchiveId: null, afterArchiveId: 2, createdAt: '2026-11-25T00:00:00Z' })
    await route.fulfill({ json: { code: 200, data: after ? [event(101)] : Array.from({ length: 100 }, (_, i) => event(i + 1)) } }).catch(() => {})
  })
  await page.goto('/data-relations')
  await page.getByRole('tab', { name: '修复工单', exact: true }).click()
  const pane = page.getByRole('tabpanel').filter({ visible: true })
  const audit = pane.getByRole('region', { name: '工单事务审计' })
  try {
    await pane.getByRole('row').filter({ hasText: '902' }).getByRole('button', { name: '审计' }).click()
    await expect(audit.getByText('正在读取审计记录…')).toBeVisible()
    await pane.getByRole('row').filter({ hasText: '901' }).getByRole('button', { name: '审计' }).click()
    await expect(audit.getByText('审计记录读取失败。')).toBeVisible()
    fail = false
    await audit.getByRole('button', { name: '重试审计' }).click()
    await expect(audit.getByText('已加载 100 条审计记录')).toBeVisible()
    await audit.getByRole('button', { name: '加载更多审计' }).click()
    await expect(audit.getByText('已加载 101 条审计记录')).toBeVisible()
  }
  finally { release() }
  await expect(audit.getByRole('heading', { name: '工单 #901 · 事务审计' })).toBeVisible()
  expect(lists).toBe(1)
})

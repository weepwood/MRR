import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

test.use({ actionTimeout: 10_000 })

/*
 * 旧实现的两个人为负担（已删除）：
 * 1. 每次查询弹「查询成功：已加载 N 条…」toast（读操作成功通知）；
 * 2. 320ms「最低加载时长」——按钮忙态被人为补足到 ≥320ms。
 */
const QUERY_BUSY_FLOOR_MS = 310
const TOAST_POLL_WINDOW_MS = 1500

async function openUsers(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: 'light' },
      layout: { enableMobileAdaptation: true },
    }))
  })
  await page.goto('/users')
  await expect(page.locator('.user-identity').first()).toBeVisible()
}

test('查询属读操作：反馈只走表格通道，不弹成功通知、不人为放慢', async ({ page }) => {
  await openUsers(page)
  const queryButton = page.getByRole('button', { name: '查询', exact: true })

  // 先用一轮查询热身（命中 mock 模块编译与首次响应抖动），不参与断言
  const warmupResponse = page.waitForResponse(response =>
    response.request().method() === 'GET' && new URL(response.url()).pathname.endsWith('/auth/users'))
  await queryButton.click()
  await warmupResponse
  await expect(queryButton).not.toHaveAttribute('aria-busy', 'true')
  await page.waitForTimeout(4000) // 让热身轮可能弹出的旧 toast 完全离场，不污染下一轮断言

  /*
   * 第 2 轮验证两件事。忙态周期用页面内 MutationObserver 记录 aria-busy
   * 翻转边沿（performance.now），测量不含 Playwright 协议往返——协议往返
   * 在 CI 慢环境下可 >100ms，会污染 <310ms 这类紧上界断言。
   */
  await queryButton.evaluate((button) => {
    const record = window as { __mrrQueryBusyEdges?: number[] }
    record.__mrrQueryBusyEdges = []
    new MutationObserver(() => {
      record.__mrrQueryBusyEdges!.push(performance.now())
    }).observe(button, { attributeFilter: ['aria-busy'], attributes: true })
  })

  const cycleStartedAt = Date.now()
  await queryButton.click()
  // 期望经历 false→true→false 两次翻转；5s 兜底避免死循环
  await page.waitForFunction(() => ((window as { __mrrQueryBusyEdges?: number[] }).__mrrQueryBusyEdges ?? []).length >= 2, undefined, { timeout: 10_000 })

  // 1) 读操作不弹「查询成功」通知：旧实现加载完成即弹、存活 3.2s，
  //    从点击起 1.5s 轮询必然捕获（忙态已翻转回 false 说明加载已完成）。
  //    这是回归检测窗口而非正确性窗口：慢响应下窗口可能静默通过，但方向上
  //    GREEN（不出现）不受响应时长影响。断言按文案收窄——未来查询链路出现
  //    合法的非成功类全局提示（如 warning）不误伤本用例。
  //    结果到达由 MrrDataTablePanel 顶边进度条与行淡入反馈，无需冗余 toast。
  const successToast = page.locator('.el-message', { hasText: '查询成功' })
  let toastSeen = false
  while (Date.now() - cycleStartedAt < TOAST_POLL_WINDOW_MS) {
    if (await successToast.count() > 0) {
      toastSeen = true
      break
    }
    await page.waitForTimeout(20)
  }
  expect(toastSeen, '「查询成功」读操作通知不应出现').toBe(false)

  // 2) 不人为放慢：旧实现 320ms 下限使忙态必然 ≥320ms；删除后忙态只反映
  //    真实在途时间（页面内测量：两次属性翻转的时间差）。
  const busyCycleMs = await page.evaluate(() => {
    const edges = (window as { __mrrQueryBusyEdges?: number[] }).__mrrQueryBusyEdges ?? []
    return edges.length >= 2 ? edges[1]! - edges[0]! : Number.NaN
  })
  expect(busyCycleMs, `查询按钮忙态周期 ${busyCycleMs}ms 应低于 ${QUERY_BUSY_FLOOR_MS}ms（旧实现有 320ms 人为下限）`).toBeLessThan(QUERY_BUSY_FLOOR_MS)
})

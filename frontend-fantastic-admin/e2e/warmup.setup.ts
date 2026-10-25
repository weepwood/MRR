import type { Page } from '@playwright/test'
import { expect, test as setup } from '@playwright/test'

/**
 * 预热 mock 开发服务器与 E2E 用到的全部路由模块图。
 *
 * <p>背景：`playwright test` 默认按 CPU 并行启动多个 worker，同时命中一个**冷启动**的
 * Vite dev server。某条路由第一次被访问时才按需编译它的模块图（并触发依赖预构建），
 * 冷启动编译耗时可达 15s 以上，恰好越过 `index.html` 中
 * `initialStartupTimeoutMs = 15000` 的启动看门狗，于是页面被「系统启动较慢」浮层覆盖，
 * 应用尚未挂载，用例便拿到空页面或始终停留在原地址而假失败。</p>
 *
 * <p>失败快照可直接印证这一点：`settings-layout` 只有看门狗浮层（`.settings-shell`
 * not found），`auth-storage-healing` 仅剩右上角通知区、`<html>` 仍带 `nprogress-busy`
 * 且 `toHaveURL` 始终停在 `/records`。也就是说失败属于测试基础设施的冷启动时序问题，
 * 而不是代码回归——同一服务预热后单 worker 复跑全部通过。</p>
 *
 * <p>因此这里用一个独立 project，在正式用例之前把**用例会访问的每条路由**都走一遍，
 * 让 Vite 完成依赖预构建与该路由模块图编译。再通过 `dependencies` 让正式用例等它跑完：
 * 相比 `globalSetup`，setup project 与 `webServer` 的先后顺序由 Playwright 保证，
 * 不会出现「预热跑在服务器启动之前」的竞态。</p>
 *
 * <p>新增用例若访问了下面清单之外的新页面，请把该路径补进来，否则首次访问仍可能撞上
 * 冷启动看门狗。</p>
 *
 * <p>注意：这里列的是**实际由守卫放行的页面**。像 {@code /password/change-required}
 * 这种「守卫会把当前会话重定向走」的页面，导航过去不会解析它的组件，因此无法通过本清单
 * 预热——这类页面的加载开销由用例自身的断言预算（{@code playwright.config.ts} 的
 * {@code expect.timeout}）覆盖。</p>
 */
const AUTHENTICATED_ROUTES = [
  '/',
  '/records',
  '/ocr-batches',
  '/archive',
  '/archive-boxes',
  '/settings',
  '/settings?section=archive',
  '/login-settings',
  '/users',
  '/patients',
  '/data-exchange',
  '/statistics-detail',
  '/auth-test',
  '/logs',
  '/runtime-logs',
  '/help',
] as const

/**
 * 需要以**未登录**身份预热的路由。
 *
 * <p>`/login` 在已登录会话下会被守卫重定向到首页，登录页自身的模块图不会被编译，
 * 因此必须单独用一个空 storageState 的浏览器上下文预热。</p>
 */
const ANONYMOUS_ROUTES = [
  '/login',
  '/login?mode=register',
] as const

/** 单次等待应用挂载的上限，需覆盖一次冷启动依赖预构建加模块编译。 */
const ROUTE_MOUNT_TIMEOUT_MS = 45_000

/** 看门狗触发后点「继续等待」会把窗口延长到 60s，这里最多重试 3 轮。 */
const MAX_ROUTE_ATTEMPTS = 3

async function isApplicationMounted(page: Page): Promise<boolean> {
  return page.evaluate(() => Boolean(document.querySelector('#app')?.children.length))
}

async function waitForApplicationMount(page: Page): Promise<boolean> {
  try {
    await expect
      .poll(() => isApplicationMounted(page), {
        timeout: ROUTE_MOUNT_TIMEOUT_MS,
        intervals: [300, 600, 1_200],
      })
      .toBe(true)
    return true
  }
  catch {
    return false
  }
}

async function attemptWarmRoute(page: Page, route: string) {
  await page.goto(route, { waitUntil: 'domcontentloaded' })

  if (await waitForApplicationMount(page)) {
    return
  }

  // 冷启动编译超过 15s 会弹出启动看门狗浮层；浮层本身不阻断挂载，
  // 点击「继续等待」可把等待窗口延长到 60s，再继续等一轮。
  const continueButton = page.locator('#browser-compat-continue')
  if (await continueButton.isVisible().catch(() => false)) {
    await continueButton.click()
    if (await waitForApplicationMount(page)) {
      return
    }
  }

  throw new Error('应用未在预期时间内挂载')
}

async function warmRoute(page: Page, route: string) {
  let lastMessage = '未知原因'

  for (let attempt = 1; attempt <= MAX_ROUTE_ATTEMPTS; attempt += 1) {
    try {
      await attemptWarmRoute(page, route)
      return
    }
    catch (error) {
      lastMessage = error instanceof Error ? error.message : String(error)
      // 依赖预构建完成时 Vite 会让客户端整页 reload，而该 reload 可能抢占
      // 当前导航，使 `page.goto` 抛 `net::ERR_ABORTED`。整页 reload 本身
      // 已经实现了「访问该路由」的意图，等待页面稳定后重试即可命中编译产物。
      await page.waitForLoadState('domcontentloaded').catch(() => undefined)
    }
  }

  throw new Error(`预热路由 ${route} 失败：${lastMessage}`)
}

setup('预热 mock 开发服务器与全部用例路由', async ({ browser, page }) => {
  setup.setTimeout(600_000)

  for (const route of AUTHENTICATED_ROUTES) {
    await warmRoute(page, route)
  }

  const anonymousContext = await browser.newContext()
  try {
    const anonymousPage = await anonymousContext.newPage()
    for (const route of ANONYMOUS_ROUTES) {
      await warmRoute(anonymousPage, route)
    }
  }
  finally {
    await anonymousContext.close()
  }

  // 所有模块均已编译，此时任意路由都应在看门狗窗口内正常挂载。
  // 这里刻意用宽松的导航加严格的挂载等待：导航本身可能被一次迟到的
  // 依赖优化 reload 抢占（ERR_ABORTED），但挂载必须在很短的时间内完成。
  await page.goto('/login', { waitUntil: 'domcontentloaded' }).catch(() => undefined)
  await expect
    .poll(() => isApplicationMounted(page), {
      timeout: 30_000,
      message: '预热完成后登录页仍未渲染',
    })
    .toBe(true)
})

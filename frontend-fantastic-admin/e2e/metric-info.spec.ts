import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
    app: { pageTitleStyle: 'card', colorScheme: 'light', enableMotion: false },
    menu: { mode: 'single' },
    layout: { enableMobileAdaptation: true },
  })))
})

test('指标说明通过信息入口显示，不占第三行，键盘与鼠标均可使用', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/data-relations')
  const info = page.getByRole('button', { name: '查看关系健康度说明' })
  const note = page.getByText('基于可用统计估算；未知项不计入', { exact: true })
  await expect(info).toBeVisible()
  await expect(page.locator('.mrr-metric-card__note, .mrr-metric-card__extra')).toHaveCount(0)
  await expect(note).not.toBeVisible()
  await info.hover()
  await expect(note).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(note).not.toBeVisible()
  await info.focus()
  await expect(note).toBeVisible()
  await info.press('Enter')
  await page.mouse.move(0, 0)
  await expect(note).toBeVisible()
  await info.press('Escape')
  await expect(note).not.toBeVisible()
  await expect(info).toBeFocused()
  const cards = page.locator('.mrr-metric-grid').first().locator('.mrr-metric-card')
  await expect.poll(() => cards.evaluateAll((els) => {
    const minimum = Number.parseFloat(getComputedStyle(els[0]).minHeight)
    return els.every(el => Math.abs(el.getBoundingClientRect().height - minimum) <= 1 && el.scrollWidth <= el.clientWidth + 1)
  })).toBe(true)
})

test('错误中心信息与整卡筛选互不干扰，运维查看日志不占第三行', async ({ page }) => {
  await page.goto('/runtime-errors')
  const action = page.getByRole('button', { name: '点击查看全部错误组' })
  const info = page.getByRole('button', { name: '查看累计错误组说明' })
  await expect(info).toBeVisible()
  let requests = 0
  await page.route('**/api/v1/system-errors?*', async (route) => {
    if (route.request().method() === 'GET' && new URL(route.request().url()).searchParams.has('level')) { requests++ }
    await route.fallback()
  })
  await info.click()
  await expect(page.getByText(/^累计 \d+ 次$/).filter({ visible: true })).toBeVisible()
  await expect(page.locator('button button')).toHaveCount(0)
  expect(requests).toBe(0)
  await info.press('Escape')
  await action.focus()
  await action.press('Enter')
  await page.getByRole('button', { name: '点击仅看 ERROR 错误组' }).click()
  await expect.poll(() => requests).toBeGreaterThan(0)
  await page.goto('/operations-center')
  await expect(page.getByRole('button', { name: '查看日志', exact: true })).toBeVisible()
  await expect(page.locator('.mrr-metric-card__extra')).toHaveCount(0)
  await page.getByRole('button', { name: '查看日志', exact: true }).click()
  await expect(page).toHaveURL(/\/logs(?:\?|$)/)
})

test('触屏信息入口可打开、关闭与外部关闭', async ({ browser, baseURL, storageState }) => {
  const context = await browser.newContext({ baseURL, storageState, hasTouch: true, viewport: { width: 375, height: 812 } })
  try {
    const page = await context.newPage()
    await isolateLocalVisualSettings(page)
    await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: false, pageTitleStyle: 'card' }, layout: { enableMobileAdaptation: true } })))
    await page.goto('/data-relations')
    const info = page.getByRole('button', { name: '查看关系健康度说明' })
    const note = page.getByText('基于可用统计估算；未知项不计入', { exact: true })
    await info.tap()
    await expect(note).toBeVisible()
    await info.tap()
    await expect(note).not.toBeVisible()
    await info.tap()
    await expect(note).toBeVisible()
    await page.getByRole('heading', { name: '数据关系工作台' }).tap()
    await expect(note).not.toBeVisible()
  }
  finally {
    await context.close()
  }
})

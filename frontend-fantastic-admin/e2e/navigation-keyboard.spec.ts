import type { Locator, Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

async function tabTo(page: Page, target: Locator) {
  await expect(target).toBeVisible()
  for (let step = 0; step < 40; step++) {
    if (await target.evaluate(element => element === document.activeElement)) {
      return
    }
    await page.keyboard.press('Tab')
  }
  await expect(target).toBeFocused()
}

for (const mode of ['side', 'head'] as const) {
  test(`${mode} 导航可以用 Tab 切换分组并打开记录管理`, async ({ page }) => {
    await page.addInitScript((menuMode) => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ menu: { mode: menuMode } }))
    }, mode)
    await page.goto('/users')

    await expect.poll(() => page.locator('.topbar-container').evaluate((toolbar) => {
      const workArea = toolbar.closest('.main-container')!.getBoundingClientRect()
      const bar = toolbar.getBoundingClientRect()
      return Math.max(Math.abs(workArea.left - bar.left), Math.abs(workArea.right - bar.right))
    })).toBeLessThanOrEqual(2)
    const group = page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: '业务', exact: true })
    await tabTo(page, group)
    await expect(group).toHaveCSS('outline-style', 'solid')
    await page.keyboard.press(mode === 'side' ? 'Enter' : 'Space')
    await expect(group).toHaveAttribute('aria-pressed', 'true')

    const records = page.getByRole('link', { name: '记录管理', exact: true })
    await tabTo(page, records)
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/records$/)
    await expect(records).toHaveAttribute('aria-current', 'page')
  })
}

test('单侧栏分组选中态、键盘焦点与旧浏览器展开降级', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ menu: { mode: 'single' } }))
    const supports = CSS.supports.bind(CSS)
    CSS.supports = (...args: [string, string?]) => args[0] === 'height' && args[1] === 'calc-size(auto, size)'
      ? false
      : args[1] === undefined ? supports(args[0]) : supports(args[0], args[1])
  })
  await page.goto('/users')
  const group = page.getByRole('button', { name: '系统', exact: true })
  const users = page.getByRole('link', { name: '用户管理', exact: true })
  await expect(users).toBeVisible()
  await tabTo(page, group)
  await expect(group).toHaveAttribute('aria-expanded', 'true')
  await page.keyboard.press('Enter')
  await expect(group).toHaveAttribute('aria-expanded', 'false')
  await expect(users).toBeHidden()
  await page.keyboard.press('Space')
  await expect(group).toHaveAttribute('aria-expanded', 'true')
  await tabTo(page, users)
})

test('收起侧栏的浮层支持键盘进入、退出及鼠标悬停', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: 'dark' },
      menu: { mode: 'single' },
    }))
  })
  await page.goto('/users')
  await expect(page.locator('html')).toHaveClass(/\bdark\b/)
  const collapse = page.getByRole('button', { name: '收起侧边栏', exact: true })
  await tabTo(page, collapse)
  await page.keyboard.press('Enter')
  await expect(page.locator('.sub-sidebar-container')).toHaveClass(/\bis-collapse\b/)
  const group = page.getByRole('button', { name: '系统', exact: true })
  const users = page.getByRole('link', { name: '用户管理', exact: true })
  await tabTo(page, group)
  await expect(group).toHaveAttribute('aria-expanded', 'false')
  await page.keyboard.press('Space')
  await expect(group).toHaveAttribute('aria-expanded', 'true')
  await expect(users).toBeFocused()
  await expect(users).toHaveCSS('outline-style', 'solid')
  await page.keyboard.press('Escape')
  await expect(group).toBeFocused()
  await expect(group).toHaveAttribute('aria-expanded', 'false')
  await expect(users).toBeHidden()

  await group.hover()
  await expect(users).toBeVisible()
  await users.hover()
  await expect(users).toBeVisible()
  await page.mouse.move(800, 450)
  await expect(users).toBeHidden()
})

test('移动侧栏关闭后退出 Tab 顺序，Escape 将焦点交回工具栏', async ({ page }) => {
  await page.setViewportSize({ width: 720, height: 900 })
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      layout: { enableMobileAdaptation: true },
      menu: { mode: 'side' },
    }))
  })
  await page.goto('/users')
  const sidebar = page.locator('#mrr-sidebar')
  const toggle = page.getByRole('button', { name: '切换侧栏导航' })
  await expect(sidebar).toBeHidden()
  await page.keyboard.press('Tab')
  expect(await sidebar.evaluate(element => element.contains(document.activeElement))).toBe(false)
  await tabTo(page, toggle)
  await page.keyboard.press('Enter')
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  const group = sidebar.getByRole('button', { name: '业务', exact: true })
  await tabTo(page, group)
  await page.keyboard.press('Space')
  await expect(group).toHaveAttribute('aria-pressed', 'true')
  await page.keyboard.press('Escape')
  await expect(sidebar).toBeHidden()
  await expect(toggle).toBeFocused()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
})

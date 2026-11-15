import { readFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'

function contrast(foreground: string, background: string) {
  const luminance = (color: string) => {
    const channels = color.match(/[\d.]+/g)!.slice(0, 3).map(Number).map((value) => {
      const channel = color.startsWith('color(srgb ') ? value : value / 255
      return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
    })
    return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
  }
  const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (values[0] + 0.05) / (values[1] + 0.05)
}

for (const theme of ['light', 'dark'] as const) {
  test(`${theme} 导航选中项可读、可聚焦，侧栏收起后仍可导航`, async ({ page }) => {
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, enableMotion: false },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), theme)
    await page.goto('/users')
    await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
    const current = page.locator('.sub-sidebar-container a[href="/users"]')
    await expect.poll(async () => {
      const colors = await current.evaluate((element) => {
        const style = getComputedStyle(element)
        return { text: style.color, background: style.backgroundColor }
      })
      return contrast(colors.text, colors.background)
    }).toBeGreaterThanOrEqual(4.5)
    await expect(current).toHaveAttribute('aria-current', 'page')
    await current.focus()
    await page.keyboard.press('Tab')
    await page.keyboard.press('Shift+Tab')
    await expect(current).toBeFocused()
    await expect(current).toHaveCSS('outline-style', 'solid')
    await expect(current).toHaveCSS('outline-width', '2px')
    await page.getByRole('button', { name: '收起侧边栏', exact: true }).click()
    await expect(page.getByRole('button', { name: '展开侧边栏', exact: true })).toBeVisible()
    await page.locator('.sub-sidebar-container .menu-item-container[title="系统"]').hover()
    const permissions = page.locator('.saas-popup-menu a[href="/permissions"]')
    await expect(permissions).toBeVisible()
    await permissions.press('Enter')
    await expect(page.getByRole('heading', { name: '权限管理', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '展开侧边栏', exact: true }).click()
    await page.locator('.sub-sidebar-container a[href="/users"]').click()
    for (const width of [1920, 1440, 1366, 768, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
    }
  })

  test(`${theme} 自定义主色保持导航对比度，内容表面与工作区分层`, async ({ page }) => {
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, pageTitleStyle: 'plain', enableMotion: false },
      menu: { mode: 'single' },
    })), theme)
    await page.goto('/users')
    await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
    const current = page.locator('.sub-sidebar-container a[href="/users"]')
    for (const primary of ['#2563eb', '#0f766e', '#ffff00', '#ffffff', '#000000']) {
      // 只替换设置最终写入的主色令牌，检查下游派生色，不触发服务端配置写入。
      await page.evaluate(primary => document.documentElement.style.setProperty('--color-primary', primary), primary)
      await expect.poll(async () => {
        const colors = await current.evaluate((element) => {
          const canvas = document.createElement('canvas')
          const context = canvas.getContext('2d')!
          const rgb = (color: string) => {
            context.fillStyle = color
            context.fillRect(0, 0, 1, 1)
            return `rgb(${Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3).join(', ')})`
          }
          const style = getComputedStyle(element)
          return { text: rgb(style.color), background: rgb(style.backgroundColor) }
        })
        return contrast(colors.text, colors.background)
      }, { message: `${theme} ${primary} 选中导航文字可读` }).toBeGreaterThanOrEqual(4.5)
    }
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
    const surfaces = await page.evaluate(() => {
      const card = document.querySelector('.mrr-section-card')!
      const row = document.querySelector('.el-table__body tr')!
      return {
        card: getComputedStyle(card).backgroundColor,
        row: getComputedStyle(row).backgroundColor,
        workspace: getComputedStyle(document.querySelector('.main')!).backgroundColor,
      }
    })
    expect(surfaces.card).not.toBe(surfaces.workspace)
    expect(surfaces.row).toBe(surfaces.card)
  })

  test(`${theme} 兼容层保留导航文字与键盘焦点`, async ({ page }) => {
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, enableMotion: false },
      menu: { mode: 'single' },
    })), theme)
    await page.goto('/users')
    const current = page.locator('.sub-sidebar-container a[href="/users"]')
    await expect(current).toBeVisible()
    // 在现代浏览器强制启用仓库的兼容分支；不等同于真实 Chrome 86 运行验证。
    const fallback = readFileSync(new URL('../src/assets/styles/browser-compatibility.css', import.meta.url), 'utf8')
      .replace('@supports not (color: color-mix(in srgb, #000 50%, #fff))', '@supports (display: block)')
    await page.addStyleTag({ content: fallback })
    await expect.poll(() => current.evaluate(element => getComputedStyle(element).getPropertyValue('--mrr-navigation-active-foreground').trim())).toMatch(/^hsl\(/)
    await current.focus()
    await page.keyboard.press('Tab')
    await page.keyboard.press('Shift+Tab')
    await expect(current).toBeFocused()
    await expect(current).toHaveCSS('outline-width', '2px')
    const colors = await current.evaluate((element) => {
      const style = getComputedStyle(element)
      return { text: style.color, background: style.backgroundColor }
    })
    expect(contrast(colors.text, colors.background)).toBeGreaterThanOrEqual(4.5)
  })
}

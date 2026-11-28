import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

const evidence = process.env.MRR_TOOLBAR_EVIDENCE
const capture = process.env.MRR_TOOLBAR_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 工具栏与账号按钮视觉保持`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, layout: { enableMobileAdaptation: true }, menu: { mode: 'single', enableSubMenuCollapseButton: true }, toolbar: { navSearch: true, fullscreen: true, pageReload: true, colorScheme: true } })), { palette, theme })
      for (const width of [1440, 720]) {
        await page.setViewportSize({ width, height: 900 })
        await page.goto('/users')
        await expect(page.locator('.topbar-container')).toBeVisible()
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        await page.mouse.move(0, 899)
        const buttons = page.locator('.topbar-container button, .sub-sidebar-container button').filter({ visible: true })
        const controls = await buttons.evaluateAll(nodes => nodes.filter(node => node.matches('.fa-button-control, .mrr-migrated-toolbar')).map((node) => {
          const b = node.getBoundingClientRect()
          const s = getComputedStyle(node)
          return { name: node.getAttribute('aria-label') || node.textContent?.trim(), x: b.x, y: b.y, width: b.width, height: b.height, font: s.fontSize, weight: s.fontWeight, radius: s.borderRadius, color: s.color, background: s.backgroundColor, border: s.borderColor }
        }))
        expect(controls.length).toBeGreaterThan(2)
        if (!capture) { expect(await buttons.evaluateAll(nodes => nodes.filter(node => node.matches('.fa-button-control')).length)).toBe(0) }
        if (evidence) {
          await fs.mkdir(evidence, { recursive: true })
          const key = path.join(evidence, `${palette}-${theme}-${width}`)
          const image = await page.locator('.topbar-container').screenshot({ animations: 'disabled' })
          if (capture) {
            await fs.writeFile(`${key}.json`, JSON.stringify(controls, null, 2))
            await fs.writeFile(`${key}-before.png`, image)
          }
          else {
            const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof controls
            await fs.writeFile(`${key}-after.json`, JSON.stringify(controls, null, 2))
            await fs.writeFile(`${key}-after.png`, image)
            expect(controls.map(c => c.name)).toEqual(old.map(c => c.name))
            for (const [i, current] of controls.entries()) {
              for (const k of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(current[k] - old[i][k]), `${key} ${current.name} ${k}`).toBeLessThanOrEqual(1) }
              for (const k of ['font', 'weight', 'radius', 'color', 'background', 'border'] as const) { expect(current[k], `${key} ${current.name} ${k}`).toBe(old[i][k]) }
            }
          }
        }
      }
    })
  }
}

test('工具栏保持主题切换、全屏、刷新、搜索及账号菜单键盘操作', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: 'light', enableMotion: false }, menu: { mode: 'single' }, toolbar: { navSearch: true, fullscreen: true, pageReload: true, colorScheme: true } })))
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/users')
  const toolbar = page.locator('.topbar-container')
  await toolbar.getByRole('button', { name: '切换为暗色主题' }).click()
  await expect(page.locator('html')).toHaveClass(/\bdark\b/)
  await toolbar.getByRole('button', { name: '切换为亮色主题' }).click()
  await expect(page.locator('html')).not.toHaveClass(/\bdark\b/)
  await toolbar.getByRole('button', { name: '进入全屏' }).click()
  await expect(toolbar.getByRole('button', { name: '退出全屏' })).toBeVisible()
  await toolbar.getByRole('button', { name: '退出全屏' }).click()
  await expect(toolbar.getByRole('button', { name: '进入全屏' })).toBeVisible()
  await toolbar.getByRole('button', { name: '刷新页面' }).click()
  await expect(page).toHaveURL(/\/users$/)
  await toolbar.getByRole('button', { name: /^搜索/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('textbox', { name: '搜索页面' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  const collapse = page.getByRole('button', { name: '收起侧边栏', exact: true })
  await collapse.click()
  await expect(page.locator('.sub-sidebar-container')).toHaveClass(/\bis-collapse\b/)
  const account = page.getByRole('button', { name: /账号菜单：/ }).first()
  await expect(account).toBeVisible()
  await account.focus()
  await page.keyboard.press('Enter')
  const menu = page.getByRole('menu')
  await expect(menu.getByText('个人设置', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(menu).toBeHidden()
  await expect(account).toBeFocused()
  await page.getByRole('button', { name: '展开侧边栏', exact: true }).click()
  await expect(page.locator('.sub-sidebar-container')).not.toHaveClass(/\bis-collapse\b/)
})

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 侧栏头像与标志视觉`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single', enableSubMenuCollapseButton: true } })), { palette, theme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/users')
      const sidebar = page.locator('.sub-sidebar-container')
      const logo = sidebar.locator('.logo-toggle')
      const account = sidebar.locator('.account-trigger')
      await expect(account).toBeVisible()
      for (const collapsed of [false, true]) {
        if (collapsed) {
          await logo.click()
          await expect(sidebar).toHaveClass(/\bis-collapse\b/)
          await expect(account).toHaveAttribute('aria-label', /账号菜单：/)
        }
        await page.mouse.move(800, 450)
        await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
        for (const [name, control] of [['account', account], ['logo', logo]] as const) {
          const measured = await control.evaluate((root) => {
            const rect = root.getBoundingClientRect()
            return [root, ...root.querySelectorAll('i, svg, img')].map((node) => {
              const b = node.getBoundingClientRect()
              const s = getComputedStyle(node)
              return { x: b.x - rect.x, y: b.y - rect.y, width: b.width, height: b.height, color: s.color, opacity: s.opacity }
            })
          })
          if (!evidence) { continue }
          await fs.mkdir(evidence, { recursive: true })
          const key = path.join(evidence, `${palette}-${theme}-${collapsed}-${name}`)
          const png = await control.screenshot({ animations: 'disabled' })
          if (capture) {
            await fs.writeFile(`${key}.json`, JSON.stringify(measured, null, 2))
            await fs.writeFile(`${key}-before.png`, png)
          }
          else {
            const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof measured
            await fs.writeFile(`${key}-after.json`, JSON.stringify(measured, null, 2))
            await fs.writeFile(`${key}-after.png`, png)
            expect(measured.length).toBe(old.length)
            for (const [i, current] of measured.entries()) {
              for (const prop of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(current[prop] - old[i][prop]), `${key} ${i} ${prop}`).toBeLessThanOrEqual(1) }
              for (const prop of ['color', 'opacity'] as const) { expect(current[prop], `${key} ${i} ${prop}`).toBe(old[i][prop]) }
            }
          }
        }
      }
    })
  }
}

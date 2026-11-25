import type { Locator } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'

const evidence = process.env.MRR_RESIDUAL_EVIDENCE
const capture = process.env.MRR_RESIDUAL_CAPTURE === 'true'

async function record(locator: Locator, key: string) {
  await expect(locator).toBeVisible()
  await expect.poll(() => locator.evaluate((el) => {
    for (let n: Element | null = el; n && n !== document.body; n = n.parentElement) {
      if (n.getAnimations().some(a => a.pending || a.playState === 'running')) { return false }
    }
    return true
  })).toBe(true)
  const data = await locator.evaluate((el) => {
    const r = el.getBoundingClientRect()
    const s = getComputedStyle(el)
    return { x: r.x, y: r.y, width: r.width, height: r.height, background: s.backgroundColor, color: s.color, radius: s.borderRadius, font: s.fontSize }
  })
  if (evidence) {
    await fs.mkdir(evidence, { recursive: true })
    const file = path.join(evidence, key)
    const png = await locator.screenshot({ animations: 'disabled' })
    if (capture) {
      await fs.writeFile(`${file}.json`, JSON.stringify(data))
      await fs.writeFile(`${file}-before.png`, png)
    }
    else {
      const old = JSON.parse(await fs.readFile(`${file}.json`, 'utf8')) as typeof data
      await fs.writeFile(`${file}-after.json`, JSON.stringify(data))
      await fs.writeFile(`${file}-after.png`, png)
      for (const k of ['width', 'height'] as const) { expect(Math.abs(data[k] - old[k]), `${key} ${k}`).toBeLessThanOrEqual(1) }
      for (const k of ['background', 'color', 'radius', 'font'] as const) { expect(data[k], `${key} ${k}`).toBe(old[k]) }
    }
  }
}

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`${palette}/${theme} 头像、进度及分隔线`, async ({ page }) => {
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single' } })), { palette, theme })
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto('/ui-components')
      await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
      if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
      const key = `${palette}-${theme}`
      const trigger = page.getByRole('button', { name: /账号菜单：/ }).filter({ visible: true }).first()
      const avatar = capture ? trigger.locator('span.rounded-full.bg-secondary').first() : trigger.locator('.el-avatar')
      await record(avatar, `${key}-avatar`)
      await trigger.click()
      const menu = page.getByRole('menu').filter({ hasText: '个人设置' }).filter({ visible: true }).last()
      await expect(menu).toBeVisible()
      await record(capture ? menu.locator('span.rounded-full.bg-secondary').first() : menu.locator('.el-avatar'), `${key}-menu-avatar`)
      await page.keyboard.press('Escape')
      await expect(menu).toBeHidden()
      await page.getByRole('button', { name: /反馈与浮层/ }).click()
      const progress = page.getByRole('progressbar', { name: '演示任务进度 64%' })
      await expect(progress).toHaveAttribute('aria-valuenow', '64')
      await record(capture ? progress : progress.locator('.el-progress-bar__outer'), `${key}-progress`)
      await page.keyboard.press('Control+i')
      const panel = page.getByRole('dialog', { name: '系统信息' })
      await expect(panel).toBeVisible()
      // A drawer can be marked visible before its first transition frame.
      await expect.poll(() => panel.evaluate((el) => {
        const r = el.getBoundingClientRect()
        // Linux uses a classic scrollbar; the fixed overlay follows the layout viewport.
        // Measure the actual containing overlay instead of assuming an overlay scrollbar.
        const overlay = el.closest('.el-overlay')
        if (!overlay) { throw new Error('Missing drawer overlay') }
        const edge = overlay.getBoundingClientRect().right
        return Math.abs(r.right - edge)
      })).toBeLessThanOrEqual(1)
      for (const label of ['产品版本', '数据库兼容性', '前端工程信息', '主要运行时依赖（声明版本范围）']) {
        const text = panel.getByText(label, { exact: true })
        await record(capture ? text : text.locator('..'), `${key}-${label}`)
      }
      await page.keyboard.press('Escape')
      await expect(panel).toBeHidden()
    })
  }
}

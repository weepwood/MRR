import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const colorScheme of ['light', 'dark']) {
  test(`暖色 ${colorScheme} 抽屉四图标同排、公共活动条与输入描边`, async ({ page }, testInfo) => {
    await isolateLocalVisualSettings(page)
    await page.emulateMedia({ reducedMotion: 'no-preference' })
    await page.addInitScript(colorScheme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme, themePalette: 'gr8r', enableMotion: true, pageTitleStyle: 'card' },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), colorScheme)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/users')
    const field = page.locator('.users-filter-bar .el-input__wrapper').first()
    await expect(field).toBeVisible()
    const border = colorScheme === 'light' ? 'rgb(153, 147, 138)' : 'rgb(113, 110, 102)'
    await page.mouse.move(0, 0)
    await expect.poll(() => field.evaluate(el => getComputedStyle(el).boxShadow)).toContain(border)
    await field.locator('input').focus()
    await expect.poll(() => field.evaluate(el => getComputedStyle(el).boxShadow)).toContain(colorScheme === 'light' ? 'rgb(83, 98, 206)' : 'rgb(157, 168, 247)')
    await page.getByRole('button', { name: '打开应用外观设置' }).click()
    const drawer = page.getByRole('dialog', { name: '应用外观设置' })
    const group = drawer.getByRole('tablist', { name: '外观设置分类' })
    const tabs = group.getByRole('tab')
    await expect(tabs).toHaveCount(4)
    for (const width of [1440, 760, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await expect.poll(() => group.evaluate((el) => {
        const items = [...el.querySelectorAll('[role=tab]')]
        const rects = items.map(node => node.getBoundingClientRect())
        return rects.every(r => Math.abs(r.top - rects[0].top) <= 1 && Math.abs(r.width - rects[0].width) <= 1)
          && items.every(node => node.querySelector('.appearance-tab-text')?.getBoundingClientRect().width === 1)
          && el.scrollWidth <= el.clientWidth + 1
      })).toBe(true)
    }
    await page.setViewportSize({ width: 1440, height: 900 })
    await expect.poll(() => group.evaluate((el) => {
      const bar = el.querySelector('.el-tabs__active-bar')!.getBoundingClientRect()
      const active = el.querySelector('[aria-selected=true]')!.getBoundingClientRect()
      return Math.abs(bar.left - active.left) <= 1 && Math.abs(bar.width - active.width) <= 1
    })).toBe(true)
    await drawer.screenshot({ path: testInfo.outputPath('drawer.png'), animations: 'disabled' })
    const motion = await group.evaluate(async (el) => {
      const bar = el.querySelector<HTMLElement>('.el-tabs__active-bar')!
      const target = el.querySelectorAll<HTMLElement>('[role=tab]')[3]
      const start = bar.getBoundingClientRect().left
      const end = target.getBoundingClientRect().left
      target.click()
      const samples: number[] = []
      const startTime = performance.now()
      while (performance.now() - startTime < 260) {
        await new Promise(requestAnimationFrame)
        samples.push(bar.getBoundingClientRect().left)
      }
      return { start, end, samples, final: bar.getBoundingClientRect().left }
    })
    expect(motion.samples.some(x => x > motion.start + 2 && x < motion.end - 2)).toBe(true)
    expect(Math.abs(motion.final - motion.end)).toBeLessThanOrEqual(1)
    await expect(tabs.last()).toHaveAttribute('aria-selected', 'true')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await tabs.first().click()
    await expect.poll(() => group.evaluate((el) => {
      const bar = el.querySelector('.el-tabs__active-bar')!
      const active = el.querySelector('[aria-selected=true]')!
      return Math.abs(bar.getBoundingClientRect().left - active.getBoundingClientRect().left) <= 1 && Number.parseFloat(getComputedStyle(bar).transitionDuration) <= 0.01
    })).toBe(true)
    await expect(drawer.getByRole('tablist', { name: '颜色主题' })).toBeVisible()
  })
}

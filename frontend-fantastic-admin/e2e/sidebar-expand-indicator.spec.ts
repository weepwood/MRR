import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

/**
 * 侧栏一级目录（系统 / 业务 …）的展开指示器。
 *
 * 这个箭头不是图标，而是「右边框 + 下边框 + rotate()」拼出来的：transform 就是
 * 它的形状。动效关闭与系统「减少动态效果」两条路径原来都用 `transform: none
 * !important` 把它一起抹平，只剩一个「┘」残角，展开与收起也没有区别。这里锁住
 * 「两条路径下箭头仍是箭头，且两种状态朝向不同」。
 */

const ARROW = '.menu-item .menu-leading-control-arrow'

async function arrowTransform(page: Page): Promise<string> {
  return page.evaluate((selector) => {
    const element = document.querySelector(selector)
    return element ? getComputedStyle(element).transform : 'missing'
  }, ARROW)
}

/** 旋转矩阵的 a 分量：0.707 表示确实转了 45°，1（或 'none'）表示残角状态。 */
function rotationComponent(transform: string): number {
  const match = transform.match(/matrix\(([^)]+)\)/)
  return match ? Number.parseFloat(match[1]!.split(',')[0]!) : 1
}

async function hoverGroup(page: Page) {
  await page.locator('.menu-item').first().hover()
  await page.waitForTimeout(200)
}

test.describe('侧栏展开指示器', () => {
  test('关闭动效与系统减少动效时箭头仍按状态旋转', async ({ page }) => {
    test.setTimeout(120_000)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/users', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.menu-item').first()).toBeVisible({ timeout: 20_000 })

    const group = page.locator('.menu-item-container').filter({ hasText: '系统' }).first()

    for (const mode of ['应用关闭动效', '系统减少动效'] as const) {
      if (mode === '系统减少动效') {
        await page.emulateMedia({ reducedMotion: 'reduce' })
      }
      else {
        await page.emulateMedia({ reducedMotion: 'no-preference' })
        await page.evaluate(() => document.documentElement.setAttribute('data-motion', 'off'))
      }

      // 展开态：图标淡出，箭头出现并转了 45°。
      await hoverGroup(page)
      await expect(page.locator(ARROW).first()).toHaveCSS('opacity', '1')
      const expandedTransform = await arrowTransform(page)
      expect(rotationComponent(expandedTransform), `${mode}：展开态箭头应保持旋转`).toBeCloseTo(0.707, 2)

      // 收起态：仍是箭头，但朝向与展开态不同，否则就是用户看到的“不动的图案”。
      await group.click()
      await page.waitForTimeout(600)
      await hoverGroup(page)
      const collapsedTransform = await arrowTransform(page)
      expect(rotationComponent(collapsedTransform), `${mode}：收起态箭头应保持旋转`).toBeCloseTo(0.707, 2)
      expect(collapsedTransform, `${mode}：展开与收起的箭头朝向应不同`).not.toBe(expandedTransform)

      // 复原为展开，便于下一轮从同一状态开始。
      await group.click()
      await page.waitForTimeout(600)
    }
  })
})

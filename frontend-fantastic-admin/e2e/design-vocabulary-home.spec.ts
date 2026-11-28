import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 首页复用档案编号样式并按悬停展示管理操作`, async ({ page }, info) => {
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { themePalette: palette, colorScheme: theme, enableMotion: false },
      layout: { enableMobileAdaptation: true },
    })), { palette, theme })
    await page.goto('/')
    const bah = page.getByRole('textbox', { name: '病案号', exact: true })
    const sjh = page.getByRole('textbox', { name: '上架号', exact: true })
    await expect(bah).toBeVisible()
    await bah.fill('123456')
    await expect(page.locator('.home-lookup .length-badge')).toHaveText('6/8')
    await sjh.focus()
    await expect(page.locator('.home-lookup .length-badge')).toHaveCount(0)
    await expect(bah).toHaveValue('123456')
    const style = () => bah.evaluate((el) => {
      const s = getComputedStyle(el)
      const w = getComputedStyle(el.closest('.el-input__wrapper')!)
      return [s.fontFamily, s.fontSize, s.fontVariantNumeric, s.letterSpacing, w.paddingLeft, w.paddingRight]
    })
    const homeStyle = await style()
    const actions = page.locator('.dashboard-workspace__actions')
    await page.mouse.move(0, 0)
    await bah.focus()
    await expect(actions).toHaveCSS('opacity', '0')
    await expect(actions).toHaveCSS('pointer-events', 'none')
    await expect(page.locator('.dashboard-header .dashboard-workspace__actions')).toHaveCount(1)
    await expect(page.getByRole('heading', { name: '常用模块', exact: true })).toHaveCount(0)
    await expect(page.locator('.home-lookup .search-hint')).toHaveClass(/\bsr-only\b/)
    await expect(page.locator('[data-widget-id="archive"] .home-lookup')).toHaveCount(1)
    await expect(page.getByRole('dialog', { name: '最近查询' })).toBeHidden()
    await page.locator('.dashboard-widget').first().hover()
    await expect(actions).toHaveCSS('opacity', '0')

    await expect(page.locator('.dashboard-workspace__count,.hidden-count')).toHaveCount(0)
    await page.screenshot({ path: info.outputPath('home-idle.png'), fullPage: true })
    await page.locator('.dashboard-header').hover()
    await expect(actions).toHaveCSS('opacity', '1')
    await page.screenshot({ path: info.outputPath('home-hover.png'), fullPage: true })
    await actions.getByRole('button', { name: '调整布局' }).click()
    await page.mouse.move(0, 0)
    await expect(actions).toHaveCSS('opacity', '1')
    await actions.getByRole('button', { name: '完成调整' }).click()
    await bah.focus()
    await page.mouse.move(0, 0)
    await expect(actions).toHaveCSS('opacity', '0')
    await actions.getByRole('button', { name: '管理模块' }).focus()
    await expect(actions).toHaveCSS('opacity', '1')
    await page.keyboard.press('Enter')
    await expect(page.getByRole('dialog', { name: '管理小组件' })).toBeVisible()
    await page.keyboard.press('Escape')
    await bah.fill('10000000')
    await sjh.clear()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.locator('#home-lookup-error')).not.toHaveClass(/\bsr-only\b/)
    await expect(page.locator('#home-lookup-error')).toContainText('上架号')
    await sjh.fill('1234')
    await expect(page.locator('#home-lookup-error')).toHaveClass(/\bsr-only\b/)
    await page.goto('/archive')
    await expect(page.locator('input[name="archive-bah"]')).toBeVisible()
    const archiveStyle = await page.locator('input[name="archive-bah"]').evaluate((el) => {
      const s = getComputedStyle(el)
      const w = getComputedStyle(el.closest('.el-input__wrapper')!)
      return [s.fontFamily, s.fontSize, s.fontVariantNumeric, s.letterSpacing, w.paddingLeft, w.paddingRight]
    })
    expect(homeStyle).toEqual(archiveStyle)
  })
}

test.describe('触控设备', () => {
  test.use({ hasTouch: true, viewport: { width: 390, height: 900 } })
  test('无悬停能力时保留管理操作', async ({ page }) => {
    await isolateLocalVisualSettings(page)
    await page.goto('/')
    await expect(page.locator('.dashboard-workspace__actions')).toHaveCSS('opacity', '1')
    await page.getByRole('button', { name: '管理模块' }).click()
    await expect(page.getByRole('dialog', { name: '管理小组件' })).toBeVisible()
  })
})

test('首页复用三字段查询，身份证仅通过POST换取令牌', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  const synthetic = '000000000000000'
  let posts = 0
  const urls: string[] = []
  page.on('request', req => urls.push(req.url()))
  await page.route('**/api/v1/search/archive-cases**', async (route) => {
    if (route.request().method() === 'POST') {
      posts++
      expect(route.request().postDataJSON()).toEqual({ idCard: synthetic })
    }
    await route.fulfill({ json: { code: 200, data: { token: 'synthetic-home-token', maskedIdCard: '000******000', cases: [{ bah: '00094001', sjh: '00001234' }] } } })
  })
  await page.goto('/')
  const card = page.locator('[data-widget-id="archive"]')
  await expect(card.locator('.search-card.is-embedded')).toBeVisible()
  await expect(card.locator('.search-field__label')).toHaveText(['病案号', '上架号', '身份证号'])
  const id = card.getByRole('textbox', { name: '身份证号', exact: true })
  await id.fill('123')
  await card.getByRole('button', { name: '查询', exact: true }).click()
  await expect(card.locator('#home-lookup-error')).toContainText('位')
  expect(posts).toBe(0)
  await id.fill(synthetic)
  await card.getByRole('textbox', { name: '病案号', exact: true }).fill('10000000')
  await expect(card.locator('.search-field__label').nth(1)).not.toContainText('必填')
  await page.screenshot({ path: info.outputPath('shared-search.png') })
  await id.press('Enter')
  await expect(page).toHaveURL(/\/archive\/embed\?/)
  expect(new URL(page.url()).searchParams.get('id')).toBe('synthetic-home-token')
  expect(posts).toBe(1)
  expect(urls.some(url => url.includes(synthetic))).toBe(false)
  expect(page.url()).not.toContain(synthetic)
})

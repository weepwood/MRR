import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { useBaseUserFixture } from './helpers/base-user-fixture'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => {
  await useBaseUserFixture(page)
})

async function openUsers(page: Page, colorScheme = 'light') {
  await isolateLocalVisualSettings(page)
  await page.addInitScript((colorScheme) => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme },
      layout: { enableMobileAdaptation: true },
    }))
  }, colorScheme)
  await page.goto('/users')
  await expect(page.locator('.user-identity').first()).toBeVisible()
}

for (const theme of ['light', 'dark']) {
  test(`${theme} 筛选标签关联真实控件，宽窄屏保持可用`, async ({ page }) => {
    await openUsers(page, theme)
    await expect(page.getByRole('region', { name: '当前页账号状态' }).locator('.mrr-metric-card__icon')).toHaveCount(4)
    await expect(page.locator('.mrr-metric-card__scope, .mrr-metric-card__note')).toHaveCount(0)
    await expect(page.locator('.mrr-page-header__description')).toContainText('下方状态按当前页统计')
    await expect(page.locator('.mrr-metric-card').filter({ hasText: '已启用' }).locator('.mrr-metric-card__value')).toHaveText('3')
    const bar = page.locator('.users-filter-bar')
    const keyword = bar.getByRole('textbox', { name: '关键词', exact: true })
    const role = bar.getByRole('combobox', { name: '角色', exact: true })
    const status = bar.getByRole('combobox', { name: '账号状态', exact: true })
    for (const width of [1440, 768, 375]) {
      await page.setViewportSize({ width, height: 900 })
      // 筛选栏改为用控件自身的 aria-label 提供可访问名称，页面上没有可见 <label>，
      // 因此不再点击标签，而是验证每个控件都能按名称被定位到（无障碍关联仍在）。
      for (const name of ['关键词', '角色', '账号状态']) {
        await expect(bar.getByLabel(name, { exact: true })).toBeVisible()
      }
      await keyword.focus()
      await page.keyboard.press('Tab')
      await expect(role).toBeFocused()
      await page.keyboard.press('Tab')
      await expect(status).toBeFocused()
      await expect.poll(() => bar.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
      for (const control of [keyword, role, status]) {
        await expect(control).toBeVisible()
      }
      await expect.poll(() => bar.locator('.el-input__wrapper, .el-select__wrapper, .el-button').evaluateAll(elements => elements.every(element => Math.abs(element.getBoundingClientRect().height - 33) <= 1))).toBe(true)
    }
  })
}

test('字段选值后名称不变，Enter查询与重置保留原参数', async ({ page }) => {
  await openUsers(page)
  const bar = page.locator('.users-filter-bar')
  const keyword = bar.getByLabel('关键词', { exact: true })
  const role = bar.getByLabel('角色', { exact: true })
  const status = bar.getByLabel('账号状态', { exact: true })
  await keyword.fill('admin')
  // Element Plus 的内部 combobox 输入负责焦点，指针点击可见选择器外壳。
  await bar.locator('.users-filter__select .el-select__wrapper').nth(0).click()
  await page.getByRole('option', { name: '管理员', exact: true }).click()
  await bar.locator('.users-filter__select .el-select__wrapper').nth(1).click()
  await page.getByRole('option', { name: '启用', exact: true }).click()
  const queryRequest = page.waitForRequest(request => request.method() === 'GET' && new URL(request.url()).pathname.endsWith('/auth/users'))
  await keyword.press('Enter')
  const params = new URL((await queryRequest).url()).searchParams
  expect(Object.fromEntries(params)).toMatchObject({ keyword: 'admin', roleCode: 'ADMIN', status: 'active', page: '1' })
  await expect(role).toHaveAccessibleName('角色')
  await expect(status).toHaveAccessibleName('账号状态')
  await expect(bar.getByRole('button', { name: '查询', exact: true })).toHaveAttribute('aria-busy', 'false')
  const resetRequest = page.waitForRequest(request => request.method() === 'GET' && new URL(request.url()).pathname.endsWith('/auth/users'))
  await bar.getByRole('button', { name: '重置', exact: true }).click()
  const resetParams = new URL((await resetRequest).url()).searchParams
  for (const name of ['keyword', 'roleCode', 'status']) {
    expect(resetParams.has(name)).toBe(false)
  }
  await expect(keyword).toHaveValue('')
})

test('用户列表采用公共默认表格、独立身份字段及单一计数', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await openUsers(page)
  const panel = page.locator('.users-table-panel')
  await expect(panel).not.toHaveClass(/mrr-data-table-panel--quiet/)
  await expect(panel.locator('.el-table')).toHaveClass(/el-table--striped/)
  await expect(panel.locator('.mrr-data-table-panel__count')).toHaveCount(0)
  await expect(panel.locator('.el-table .el-tag')).toHaveCount(0)
  await expect(panel.locator('.mrr-table-actions--quiet')).toHaveCount(3)
  await expect(panel.locator('.users-filter-bar')).toHaveCSS('border-bottom-width', '0px')
  await expect(panel.locator('.mrr-table-actions')).toHaveCount(3)
  await expect(panel.locator('.el-pagination__total')).toHaveCount(1)
  await expect(panel.locator('.el-pagination__total')).toHaveText('共 3 条')
  await expect(panel.locator('.el-pagination__jump')).toHaveCount(0)
  const identity = panel.locator('.user-identity').filter({ hasText: 'admin' })
  await expect(identity.getByText('admin', { exact: true })).toBeVisible()
  await expect(panel.getByRole('row').filter({ hasText: 'admin' }).getByText('系统管理员', { exact: true })).toBeVisible()
  await expect(panel.getByRole('columnheader', { name: '用户名', exact: true })).toBeVisible()
  await expect(panel.getByRole('columnheader', { name: '显示名称', exact: true })).toBeVisible()
  await expect.poll(() => panel.locator('.users-filter__field').evaluateAll((els) => {
    const widths = els.map(el => el.getBoundingClientRect().width)
    const tops = els.map(el => el.getBoundingClientRect().top)
    return widths[0] > widths[1] * 1.5 && Math.abs(widths[1] - widths[2]) <= 1 && Math.max(...tops) - Math.min(...tops) <= 1
  })).toBe(true)
  await expect.poll(() => panel.locator('.el-pagination').evaluate((el) => {
    const total = el.querySelector('.el-pagination__total')!.getBoundingClientRect()
    return Math.abs(total.left - el.getBoundingClientRect().left) <= 1
  })).toBe(true)
  const create = panel.getByRole('button', { name: '创建用户', exact: true })
  await expect(panel.locator('.el-card__header')).toHaveCount(0)
  await expect.poll(() => panel.locator('.users-toolbar').evaluate((el) => {
    const create = el.querySelector('.users-toolbar__create')!.getBoundingClientRect()
    const query = el.querySelector('.mrr-filter-bar__actions .el-button')!.getBoundingClientRect()
    return Math.abs(create.top - query.top) <= 1 && Math.abs(create.height - query.height) <= 1 && Math.abs(create.right - el.getBoundingClientRect().right) <= 1
  })).toBe(true)
  await create.click()
  await expect(page.getByRole('dialog', { name: '创建用户' })).toBeVisible()
  await page.getByRole('dialog', { name: '创建用户' }).getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('dialog', { name: '创建用户' })).not.toBeVisible()
})

test('每页条数使用下拉，回到第一页且只发一次查询', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.route('**/api/v1/auth/users?**', async (route) => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.total = 1000
    await route.fulfill({ response, json: body })
  })
  await openUsers(page)
  const footer = page.locator('.users-pagination')
  const size = footer.locator('.el-pagination__sizes .el-select__wrapper')
  await expect(footer.locator('.el-pagination__sizes')).toHaveCount(1)
  await expect(size).toHaveText('20 条/页')
  await footer.locator('.btn-next').click()
  await expect(footer.locator('.el-pager .is-active')).toHaveText('2')
  await expect(footer.locator('.el-pagination__sizes [role=combobox]')).toBeEnabled()
  const requests: string[] = []
  page.on('request', (request) => {
    if (request.method() === 'GET' && new URL(request.url()).pathname.endsWith('/auth/users')) { requests.push(request.url()) }
  })
  for (const [index, count] of [50, 100, 10, 20].entries()) {
    const response = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/auth/users'))
    await size.click()
    await page.getByRole('option', { name: `${count} 条/页`, exact: true }).click()
    await response
    await expect(size).toHaveText(`${count} 条/页`)
    await expect(footer.locator('.el-pagination__sizes [role=combobox]')).toBeEnabled()
    await expect(footer.locator('.el-pager .is-active')).toHaveText('1')
    expect(requests).toHaveLength(index + 1)
    expect(Object.fromEntries(new URL(requests[index]!).searchParams)).toMatchObject({ page: '1', size: String(count) })
  }
  await expect.poll(() => footer.locator('.el-pager .is-active').evaluate(el => el.getBoundingClientRect().height)).toBe(28)
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => footer.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true)
  await expect(size).toBeVisible()
  await expect.poll(() => footer.locator('.el-pager .is-active').evaluate(el => el.getBoundingClientRect().height)).toBe(32)
})

test('克制型用户操作菜单统一尺寸与危险语义，自身账号操作仍禁用', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await openUsers(page)
  const row = page.getByRole('row').filter({ hasText: '扫描操作员' })
  await row.getByRole('button', { name: '更多操作' }).click()
  const menu = page.locator('.mrr-table-action-menu--quiet:visible')
  await expect(menu).toBeVisible()
  const reset = menu.getByRole('menuitem', { name: '重置密码' })
  const disable = menu.getByRole('menuitem', { name: '禁用用户' })
  await expect(reset).toBeVisible()
  await expect(disable).toBeVisible()
  await expect.poll(() => menu.locator('.el-dropdown-menu__item').evaluateAll(els => els.map(el => el.getBoundingClientRect().height))).toEqual([32, 32])
  await expect.poll(() => menu.locator('.mrr-table-actions__menu-icon').evaluateAll(els => els.map(el => getComputedStyle(el).fontSize))).toEqual(['16px', '16px'])
  // Content-sized menu: measure actual glyph bounds, not the allocated flex box.
  await expect.poll(() => menu.evaluate((el) => {
    const boundary = el.getBoundingClientRect()
    return [...el.querySelectorAll('.el-dropdown-menu__item')].every((item) => {
      const icon = item.querySelector('.mrr-table-actions__menu-icon')!
      const label = item.querySelector('span:not(.mrr-table-actions__menu-icon)')!
      const range = document.createRange()
      range.selectNodeContents(label)
      const left = icon.getBoundingClientRect().left - boundary.left
      const right = boundary.right - range.getBoundingClientRect().right
      return left >= 10 && left <= 16 && Math.abs(left - right) <= 2
    })
  })).toBe(true)
  await expect(menu.locator('.el-popper__arrow')).toHaveCount(0)
  await expect(reset).not.toHaveClass(/menu-item--warning/)
  await expect(disable).toHaveClass(/menu-item--danger/)
  await disable.click()
  const dialog = page.getByRole('dialog', { name: '禁用用户' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await row.getByRole('button', { name: '更多操作' }).click()
  await reset.click()
  await expect(page.getByRole('dialog', { name: '重置用户密码', exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '重置用户密码', exact: true })).not.toBeVisible()
  await page.getByRole('row').filter({ hasText: '系统管理员' }).getByRole('button', { name: '更多操作' }).click()
  await expect(menu.getByRole('menuitem', { name: '重置密码' })).toHaveClass(/is-disabled/)
  await expect(menu.getByRole('menuitem', { name: '禁用用户' })).toHaveClass(/is-disabled/)
})

for (const palette of ['mrr', 'gr8r']) {
  for (const theme of ['light', 'dark']) {
    test(`${palette}/${theme} 业务按钮与查询控件统一高度和对齐`, async ({ page }) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette: palette, colorScheme: theme, enableMotion: false }, layout: { enableMobileAdaptation: true } })), { palette, theme })
      await page.goto('/users')
      const bar = page.locator('.users-filter-bar')
      const create = bar.getByRole('button', { name: '创建用户', exact: true })
      await expect(create).toHaveClass(/is-plain/)
      for (const width of [1920, 1440, 375, 320]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => bar.evaluate((el) => {
          const buttons = [...el.querySelectorAll('.el-button')]
          const fields = [...el.querySelectorAll('.el-input__wrapper, .el-select__wrapper')]
          const height = Number.parseFloat(getComputedStyle(el).getPropertyValue('--mrr-control-height'))
          const query = buttons[0].getBoundingClientRect()
          const business = buttons[buttons.length - 1].getBoundingClientRect()
          const sameHeight = [...buttons, ...fields].every(node => Math.abs(node.getBoundingClientRect().height - height) <= 1)
          const rightAligned = Math.abs(business.right - el.getBoundingClientRect().right) <= 1
          const sameRow = innerWidth === 320 || Math.abs(query.y - business.y) <= 1
          return sameHeight && rightAligned && sameRow && el.scrollWidth <= el.clientWidth + 1
        })).toBe(true)
      }
    })
  }
}

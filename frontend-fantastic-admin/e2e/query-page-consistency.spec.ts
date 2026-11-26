import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.beforeEach(async ({ page }) => { await isolateLocalVisualSettings(page) })

test.use({ actionTimeout: 10_000 })

const pages = [
  { path: '/logs', title: '日志管理', endpoint: '/api/v1/logs/search', empty: '暂无日志记录', detail: '接口访问审计详情' },
  { path: '/audit-images', title: '病历访问审计可视化', endpoint: '/api/v1/logs/audit/images', empty: '暂无审计记录', detail: '审计详情' },
]

async function openPage(page: Page, path: string, theme = 'light') {
  await page.addInitScript((colorScheme) => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme },
      layout: { enableMobileAdaptation: true },
    }))
  }, theme)
  await page.goto(path)
  await expect(page.locator('.el-table__row').first()).toBeVisible()
}

for (const item of pages) {
  for (const theme of ['light', 'dark']) {
    test(`${item.path} ${theme} 标题、字段标签、分页与详情在宽窄屏可用`, async ({ page }) => {
      test.setTimeout(90_000)
      const longUri = `/synthetic/${'x'.repeat(240)}`
      const longQuery = `synthetic=${'y'.repeat(240)}`
      await page.route(`**${item.endpoint}?*`, async (route) => {
        const response = await route.fetch()
        const body = await response.json()
        body.data.list[0] = { ...body.data.list[0], requestUri: longUri, queryString: longQuery }
        await route.fulfill({ response, json: body })
      })
      await page.route(/\/api\/v1\/logs\/\d+(?:\?.*)?$/, route => route.fulfill({
        json: { code: 200, message: 'success', data: { id: 1, requestUri: longUri, queryString: longQuery } },
      }))
      await openPage(page, item.path, theme)
      await expect(page.getByRole('heading', { level: 1, name: item.title, exact: true })).toBeVisible()
      const form = page.locator('form').first()
      const disclosure = form.getByRole('button', { name: /高级筛选/ })
      await expect(disclosure).toHaveAttribute('aria-expanded', 'false')
      await disclosure.click()
      for (const width of [1920, 1440, 1366, 768, 375]) {
        await page.setViewportSize({ width, height: 900 })
        for (const label of ['关键字', '用户', '客户端 IP', '状态码']) {
          const control = form.getByLabel(label, { exact: true })
          await form.locator('label').filter({ hasText: new RegExp(`^${label}$`) }).click()
          await expect(control).toBeFocused()
          const field = control.locator('xpath=ancestor::div[contains(concat(" ",normalize-space(@class)," ")," el-form-item ")][1]')
          const labelBox = await field.locator('label').boundingBox()
          const controlBox = await control.boundingBox()
          expect(labelBox!.x + labelBox!.width).toBeLessThanOrEqual(controlBox!.x + 1)
          expect(Math.abs(labelBox!.y + labelBox!.height / 2 - controlBox!.y - controlBox!.height / 2)).toBeLessThanOrEqual(5)
          await page.keyboard.press('Escape')
          // A teleported select popper can still cover the next field during its
          // leave transition. Wait for the actual collapsed and hidden state
          // before testing the next label's native focus behavior.
          if (await control.getAttribute('role') === 'combobox') {
            // Click a non-control outside the select to exercise real outside dismissal.
            // Tab may leave the teleported popper open even when aria-expanded is false.
            await page.getByRole('heading', { level: 1, name: item.title, exact: true }).click()
            await expect(control).not.toBeFocused()
            await expect(control).toHaveAttribute('aria-expanded', 'false')
            const popupId = await control.getAttribute('aria-controls')
            expect(popupId).toBeTruthy()
            await expect(page.locator(`[id="${popupId}"]`).locator('xpath=ancestor::*[contains(concat(" ",normalize-space(@class)," "), " el-select__popper ")][1]')).toBeHidden()
          }
        }
        for (const locator of [form, page.locator('.el-pagination')]) {
          await expect.poll(() => locator.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
        }
        await expect.poll(() => form.locator('.el-input__wrapper, .el-select__wrapper, .el-date-editor, .el-button').evaluateAll(elements => elements.every(element => Math.abs(element.getBoundingClientRect().height - 33) <= 1))).toBe(true)
      }
      await page.setViewportSize({ width: 768, height: 900 })
      await page.locator('.mrr-data-table-panel__content').getByRole('button', { name: '更多操作', exact: true }).first().click()
      await page.getByRole('menuitem', { name: item.path === '/logs' ? '查看详情' : '查看审计详情', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: item.detail, exact: true })
      await expect(dialog).toBeVisible()
      await expect(dialog.getByText(longUri, { exact: true })).toBeVisible()
      await expect(dialog.getByText(longQuery, { exact: true })).toBeVisible()
      await expect(dialog.locator('.el-descriptions__table')).toHaveCSS('table-layout', 'fixed')
      for (const width of [768, 375]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => dialog.evaluate((element) => {
          const box = element.getBoundingClientRect()
          return box.left >= 0 && box.right <= innerWidth && element.scrollWidth <= element.clientWidth + 1
        })).toBe(true)
        await expect.poll(() => dialog.locator('.el-descriptions').evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
      }
    })
  }

  test(`${item.path} Enter查询、重置与翻页保留条件语义`, async ({ page }) => {
    await openPage(page, item.path)
    const form = page.locator('form').first()
    const keyword = form.getByLabel('关键字', { exact: true })
    // 用户筛选已升级为可搜索下拉（MrrUserSearchSelect）：fill 只产生检索文本，
    // 需等 allow-create 的创建项出现后点选，值才会提交。
    await form.getByLabel('用户', { exact: true }).fill('synthetic-user')
    await page.locator('.el-select-dropdown__item').filter({ hasText: 'synthetic-user' }).first().click()
    const waitQuery = () => page.waitForRequest(request => request.method() === 'GET' && new URL(request.url()).pathname.endsWith(item.endpoint))
    // Move focus explicitly from the teleported select popup back to the keyword.
    // WebKit may keep the select's focus restoration queued after choosing a created option.
    await expect(page.locator('.el-select-dropdown__item').filter({ hasText: 'synthetic-user' })).not.toBeVisible()
    await form.getByRole('button', { name: '查询', exact: true }).click({ trial: true })
    await keyword.fill('synthetic-filter')
    await keyword.click()
    await expect(keyword).toBeFocused()
    const query = waitQuery()
    await keyword.press('Enter')
    expect(Object.fromEntries(new URL((await query).url()).searchParams)).toMatchObject({ keyword: 'synthetic-filter', username: 'synthetic-user', page: '1', size: '20' })
    await expect(page.getByText(item.empty, { exact: true })).toBeVisible()
    const reset = waitQuery()
    await form.getByRole('button', { name: '重置', exact: true }).click()
    const params = new URL((await reset).url()).searchParams
    expect(params.has('keyword')).toBe(false)
    expect(params.has('username')).toBe(false)
    await expect(keyword).toHaveValue('')
    await expect(page.locator('.el-table__row').first()).toBeVisible()
    const next = waitQuery()
    await page.locator('.el-pagination .btn-next').click()
    expect(new URL((await next).url()).searchParams.get('page')).toBe('2')
  })

  test(`${item.path} 折叠高级筛选保留隐藏条件、查询及重置语义`, async ({ page }) => {
    await openPage(page, item.path)
    await page.setViewportSize({ width: 375, height: 900 })
    const form = page.locator('form').first()
    const input = form.getByLabel('客户端 IP', { exact: true })
    const disclosure = form.getByRole('button', { name: /高级筛选/ })
    await expect(disclosure).toHaveAttribute('aria-expanded', 'false')
    await expect(input).toBeHidden()
    await disclosure.focus()
    await disclosure.press('Enter')
    await expect(input).toBeVisible()
    await input.fill('192.0.2.42')
    await expect(disclosure).toContainText('已填写 1 项')
    await expect(form.locator('.mrr-filter-bar__summary')).toContainText('列表已应用 0 项高级条件')
    const expandedHeight = (await form.boundingBox())!.height
    await disclosure.click()
    await expect(disclosure).toHaveAttribute('aria-expanded', 'false')
    await expect(input).toBeHidden()
    await expect(disclosure).toContainText('已填写 1 项')
    expect((await form.boundingBox())!.height).toBeLessThan(expandedHeight)
    const request = page.waitForRequest(req => req.method() === 'GET' && new URL(req.url()).pathname.endsWith(item.endpoint))
    await form.getByRole('button', { name: '查询', exact: true }).click()
    expect(new URL((await request).url()).searchParams.get('clientIp')).toBe('192.0.2.42')
    await expect(form.locator('.mrr-filter-bar__summary')).toContainText('列表已应用 1 项高级条件')
    await expect(form.getByRole('button', { name: '重置', exact: true })).toHaveAttribute('aria-disabled', 'false')
    const reset = page.waitForRequest(req => req.method() === 'GET' && new URL(req.url()).pathname.endsWith(item.endpoint))
    await form.getByRole('button', { name: '重置', exact: true }).click()
    expect(new URL((await reset).url()).searchParams.has('clientIp')).toBe(false)
    await expect(disclosure).toHaveText('高级筛选')
    await disclosure.click()
    await expect(input).toBeVisible()
    await expect(input).toHaveValue('')
  })

  for (const status of [403, 500]) {
    test(`${item.path} ${status} 错误与空态区分，重试可恢复`, async ({ page }) => {
      let release!: () => void
      const gate = new Promise<void>((resolve) => { release = resolve })
      let fail = true
      await page.route(`**${item.endpoint}?*`, async (route) => {
        await gate
        await route.fulfill(fail
          ? { status, json: { code: status, message: 'synthetic query error' } }
          : { json: { code: 200, message: 'success', data: { list: [], total: 0, page: 1, size: 20 } } })
      })
      await page.goto(item.path)
      const content = page.locator('.mrr-data-table-panel__content')
      await expect(content.getByLabel('内容加载中')).toBeVisible()
      release()
      await expect(content.getByRole('alert')).toBeVisible()
      await expect(content.getByText(item.empty, { exact: true })).toHaveCount(0)
      fail = false
      await content.getByRole('button', { name: '重试', exact: true }).click()
      await expect(content.getByText(item.empty, { exact: true })).toBeVisible()
      await expect(content.getByRole('alert')).toHaveCount(0)
    })
  }
}

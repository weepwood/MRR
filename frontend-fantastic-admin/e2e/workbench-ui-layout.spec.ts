import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: 'light', pageTitleStyle: 'card' },
      layout: { enableMobileAdaptation: true },
    }))
  })
  await page.goto('/data-relations')
  await expect(page.getByRole('heading', { name: '数据关系工作台' })).toBeVisible()
})

test('无图标指标在移动视口使用整行正文', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await expect.poll(() => page.evaluate(() => document.body.dataset.mode)).toBe('mobile')
  const card = page.locator('.mrr-metric-card--no-icon').first()
  await expect(card).toBeVisible()
  const widths = await card.evaluate(element => ({
    card: element.getBoundingClientRect().width,
    body: element.querySelector('.mrr-metric-card__body')!.getBoundingClientRect().width,
  }))
  expect(widths.body).toBeGreaterThan(widths.card - 40)
})

test('工作台字段有可访问名称，窄屏筛选与表格不撑开页面', async ({ page }) => {
  for (const width of [1366, 900, 768, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await page.getByRole('tab', { name: '病案关系查询', exact: true }).click()
    await expect(page.getByRole('textbox', { name: '病案查询值', exact: true })).toBeVisible()
    await page.getByRole('tab', { name: '检查与异常', exact: true }).click()
    await expect(page.getByRole('combobox', { name: '异常级别筛选', exact: true })).toBeVisible()
    await expect(page.getByRole('textbox', { name: '异常关键字筛选', exact: true })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    const input = page.getByRole('textbox', { name: '异常关键字筛选', exact: true })
    await expect.poll(async () => (await input.boundingBox())!.width).toBeGreaterThan(180)
  }
})

for (const theme of ['light', 'dark']) {
  test(`${theme} 主题病案详情表格与预览抽屉在窄屏内滚动`, async ({ page }) => {
    await page.addInitScript(theme => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, pageTitleStyle: 'card' },
      layout: { enableMobileAdaptation: true },
    })), theme)
    const archive = { id: 91001, bah: 'SYNTHETIC-001', sjh: '', patientName: '合成布局示例', scanCount: 1 }
    const issue = { id: 91001, severity: 'CRITICAL', checkName: '合成布局检查', entityType: 'mr_scan', entityId: '91001' }
    await page.route('**/api/v1/system/data-**/**', async (route) => {
      const url = route.request().url()
      const data = url.includes('/archives/search')
        ? [archive]
        : url.includes('/archives/91001')
          ? {
              archive,
              statistics: [],
              patients: [],
              boxes: [],
              scanSummary: {},
              scanSamples: [],
              migrationSummary: {},
              comparisons: [{ field: 'BAH', canonicalValue: archive.bah, source: 'mr_scan', sourceValue: archive.bah, status: 'EXACT' }],
              warnings: [],
            }
          : url.includes('/repair-preview')
            ? { issue, currentEntity: {}, candidateArchives: [], suggestedAction: '合成只读预览', deterministic: true, readOnly: true, canApply: false, reason: '仅验证布局' }
            : url.includes('/issues')
              ? [issue]
              : url.includes('/summary')
                ? { running: false, enabled: true, latestRun: null, checks: [] }
                : { relations: [], relationChecks: [], notes: [] }
      await route.fulfill({ json: { code: 200, data } })
    })
    await page.setViewportSize({ width: 375, height: 812 })
    await page.reload()
    await page.getByRole('tab', { name: '病案关系查询', exact: true }).click()
    await page.getByRole('textbox', { name: '病案查询值', exact: true }).fill(archive.bah)
    await page.getByRole('textbox', { name: '病案查询值', exact: true }).press('Enter')
    await expect(page.getByText('合成布局示例', { exact: true }).last()).toBeVisible()
    await expect.poll(() => page.locator('.relation-workbench').evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
    await page.getByRole('tab', { name: '检查与异常', exact: true }).click()
    await page.locator('.quality-stack').getByRole('button', { name: /更多/ }).click()
    await page.getByText('修复预览', { exact: true }).click()
    const drawer = page.getByRole('dialog', { name: '只读修复预览' })
    await expect(drawer).toBeVisible()
    await expect.poll(async () => (await drawer.boundingBox())!.x).toBeGreaterThanOrEqual(0)
    await expect.poll(() => drawer.evaluate(element => element.getBoundingClientRect().right <= document.documentElement.clientWidth + 1)).toBe(true)
  })
}

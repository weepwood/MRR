import { readFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

for (const [palette, theme] of [['mrr', 'light'], ['mrr', 'dark'], ['gr8r', 'light'], ['gr8r', 'dark']] as const) {
  for (const code of ['404', '403'] as const) {
    test(`${palette}/${theme} ${code}异常页面`, async ({ page }, info) => {
      await isolateLocalVisualSettings(page)
      const profile = { id: 72, username: 'synthetic-doctor', roleCode: 'DOCTOR', status: 'active', permissions: ['record:read'], mustChangePassword: false }
      await page.addInitScript(({ palette, theme, code, profile }) => {
        localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { themePalette: palette, colorScheme: theme, enableMotion: false, enablePermission: true } }))
        if (code === '403') {
          localStorage.setItem('mrr:auth:schema-version', '1')
          localStorage.setItem('mrr:auth:token', 'synthetic-doctor-token')
          localStorage.setItem('mrr:auth:account', profile.username)
          localStorage.setItem('mrr:auth:profile', JSON.stringify(profile))
          localStorage.setItem('mrr:auth:permissions', JSON.stringify(profile.permissions))
        }
      }, { palette, theme, code, profile })
      if (code === '403') {
        await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { code: 200, data: profile } }))
      }
      await page.clock.install()
      await page.goto(code === '404' ? '/synthetic-unknown-route' : '/users')
      const screen = page.locator(`[data-error-code="${code}"]`)
      await expect(screen).toBeVisible()
      const heading = screen.getByRole('heading', { name: code === '404' ? '页面不存在' : '暂无访问权限' })
      await expect(heading).toBeVisible()
      await expect(heading).toHaveCSS('font-size', '26px')
      await expect(screen.locator('svg')).toHaveAttribute('aria-hidden', 'true')
      await page.clock.pauseAt(new Date(Date.now() + 100))
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 820 })
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
        await expect(screen.getByRole('button', { name: /返回首页/ })).toBeVisible()
        await screen.screenshot({ path: info.outputPath(`${width}.png`), animations: 'disabled' })
      }
      await screen.getByRole('button', { name: /返回首页/ }).click()
      await expect(page).toHaveURL(/\/$/)
    })
  }
}

for (const theme of ['light', 'dark'] as const) {
  test(`${theme} 离线及维护页独立渲染`, async ({ page }, info) => {
    await page.emulateMedia({ colorScheme: theme })
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 820 })
      await page.goto('/offline.html')
      await expect(page.getByRole('heading', { name: '暂时无法连接 MRR 服务' })).toBeVisible()
      await expect(page.getByText(/不会离线保存业务接口响应/)).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
      await page.screenshot({ path: info.outputPath(`offline-${width}.png`) })
      await page.setContent(readFileSync('../deploy/windows/templates/maintenance.html', 'utf8'))
      await expect(page.getByRole('heading', { name: '系统暂时不可用' })).toBeVisible()
      await expect(page.locator('#maintenance-message')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width + 1)
      await page.screenshot({ path: info.outputPath(`maintenance-${width}.png`) })
    }
  })
}

test('公共业务错误插画与重试恢复', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  await page.goto('/ui-components')
  const section = page.locator('article[aria-labelledby="example-feedback"]')
  await expect(section).toBeVisible()
  await section.getByText('加载失败', { exact: true }).click()
  const error = section.getByRole('alert')
  await expect(error.locator('svg[data-error-art="failure"]')).toBeVisible()
  await expect(error.getByText('演示加载失败，点击重试恢复正常状态。')).toBeVisible()
  await section.screenshot({ path: info.outputPath('business-error.png') })
  await error.getByRole('button', { name: '重试' }).click()
  await expect(section.getByText('示例内容已就绪')).toBeVisible()
  await expect(error.locator('svg[data-error-art="failure"]')).toHaveCount(0)
})

test('404原有自动返回首页倒计时保持有效', async ({ page }) => {
  await page.clock.install()
  await page.goto('/synthetic-unknown-route')
  await expect(page.locator('[data-error-code="404"]')).toBeVisible()
  await page.clock.runFor(6000)
  await expect(page).toHaveURL(/\/$/)
})

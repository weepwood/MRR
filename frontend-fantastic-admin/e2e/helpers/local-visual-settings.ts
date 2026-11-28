import type { Page } from '@playwright/test'

/**
 * Visual tests seed local preferences. Prevent unrelated tests that save global
 * Mock settings from replacing those explicit preferences on initial load.
 * Also contain the corresponding pure visual write, so local-only tests cannot
 * change another worker's server defaults. Mixed/invalid/other settings fall
 * through, and tests of real server synchronization must not use this helper.
 */
export async function isolateLocalVisualSettings(page: Page) {
  await page.route('**/api/v1/settings/ui', route => route.fulfill({
    json: { code: 200, data: {} },
  }))
  await page.route('**/api/v1/settings', async (route) => {
    const request = route.request()
    if (request.method() !== 'PUT') {
      await route.fallback()
      return
    }
    let body: unknown
    try {
      body = request.postDataJSON()
    }
    catch {
      await route.fallback()
      return
    }
    if (body && typeof body === 'object' && !Array.isArray(body)
      && Object.keys(body).length === 1 && 'adminAppSettings' in body
      && typeof body.adminAppSettings === 'string' && body.adminAppSettings.length <= 65536) {
      await route.fulfill({ json: { code: 200, data: null, message: '设置保存成功' } })
      return
    }
    await route.fallback()
  })
}

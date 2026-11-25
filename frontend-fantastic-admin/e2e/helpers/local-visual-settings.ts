import type { Page } from '@playwright/test'

/**
 * Visual tests seed local preferences. Prevent unrelated tests that save global
 * Mock settings from replacing those explicit preferences on initial load.
 * Tests of server synchronization must not use this helper.
 */
export async function isolateLocalVisualSettings(page: Page) {
  await page.route('**/api/v1/settings/ui', route => route.fulfill({
    json: { code: 200, data: {} },
  }))
}

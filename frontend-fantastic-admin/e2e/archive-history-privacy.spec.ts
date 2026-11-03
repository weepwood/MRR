import { expect, test } from '@playwright/test'

test.use({ storageState: { cookies: [], origins: [] } })

test('档案袋历史不落盘，切换账号或注销后不返回前一会话记录', async ({ page }) => {
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({
    json: { code: 200, data: route.request().method() === 'GET' ? [] : {} },
  }))
  await page.goto('/login')
  await expect(page.locator('#app')).not.toBeEmpty()
  const result = await page.evaluate(async () => {
    const userPath = '/src/store/modules/user.ts'
    const historyPath = '/src/views/statistics/archive/composables/useArchiveSearchHistory.ts'
    const { useUserStore } = await import(userPath)
    const history = await import(historyPath)
    const user = useUserStore()
    user.setSession({ token: 'synthetic-browser-a', user: { id: 91001, username: 'synthetic-a', permissions: ['record:read'] } })
    history.addArchiveSearchHistory({ bah: 'SYNTHETIC-BROWSER-A' })
    const aCount = history.readArchiveSearchHistory().length
    const stored = localStorage.getItem(history.ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)
    user.clearSession()
    const loggedOutCount = history.readArchiveSearchHistory().length
    user.setSession({ token: 'synthetic-browser-b', user: { id: 91002, username: 'synthetic-b', permissions: ['record:read'] } })
    const b = await history.loadArchiveSearchHistory()
    return { aCount, stored, loggedOutCount, bCount: b.length }
  })
  expect(result).toEqual({ aCount: 1, stored: null, loggedOutCount: 0, bCount: 0 })
})

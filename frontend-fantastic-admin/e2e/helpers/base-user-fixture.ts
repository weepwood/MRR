import type { Page } from '@playwright/test'
import { createBaseMockUsers, filterMockUsers } from '../../src/mock-support/users'

// Geometry/zero-result regressions deliberately use the original tiny fixture.
// The expanded demo has its own integration coverage.
export async function useBaseUserFixture(page: Page) {
  await page.route('**/api/v1/auth/users?*', async (route) => {
    if (route.request().method() !== 'GET') { return route.fallback() }
    const query = new URL(route.request().url()).searchParams
    const list = filterMockUsers(createBaseMockUsers(), {
      keyword: query.get('keyword') ?? undefined,
      roleCode: query.get('roleCode') ?? undefined,
      status: query.get('status') ?? undefined,
    })
    const pageNumber = Number(query.get('page')) || 1
    const size = Number(query.get('size')) || 20
    await route.fulfill({ json: { code: 200, data: { list: list.slice((pageNumber - 1) * size, pageNumber * size), total: list.length } } })
  })
}

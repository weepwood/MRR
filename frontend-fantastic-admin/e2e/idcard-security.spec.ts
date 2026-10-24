import { expect, test } from '@playwright/test'

test('身份证查询只把受控令牌放入浏览器地址', async ({ page }) => {
  const idCard = '110101199001011234'
  let postedIdCard = ''

  await page.route('**/api/v1/search/archive-cases**', async (route) => {
    if (route.request().method() === 'POST') {
      postedIdCard = route.request().postDataJSON().idCard
    }
    await route.fulfill({
      contentType: 'application/json',
      json: {
        code: 200,
        data: { token: 'opaque-test-token', maskedIdCard: '110***********1234', cases: [] },
      },
    })
  })

  await page.goto('/idcard')
  await page.getByPlaceholder('请输入18位身份证号').fill(idCard)
  await page.getByRole('button', { name: '查询病案' }).click()

  await expect.poll(() => postedIdCard).toBe(idCard)
  await expect(page).toHaveURL(/\/idcard\/view\?id=opaque-test-token$/)
  expect(page.url()).not.toContain(idCard)
  await expect(page.getByText('110***********1234')).toBeVisible()
})

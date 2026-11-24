import { expect, test } from '@playwright/test'

const reply = (data: unknown) => ({ code: 200, message: 'success', data })

// headless Chromium 默认拒绝剪贴板权限；这里显式授予，用例才能断言「复制成功」路径
test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

const syntheticPatient = {
  id: 'synthetic-patient-1',
  bah: '00800001',
  name: '合成患者',
  idCard: '110101199001011111',
  sex: '女',
  birthday: '1990-01-01',
  age: 36,
  department: '内科',
  bingqu: '内科一病区',
  chuangwei: '12',
  ruyuan: '2026-11-20',
}

const syntheticPatientWithoutIdCard = {
  ...syntheticPatient,
  id: 'synthetic-patient-2',
  bah: '00800002',
  name: '合成患者乙',
  idCard: '',
}

/**
 * 身份证号是敏感个人信息。「查看（显示/隐藏）」与「复制」必须是两个显式动作：
 * 点号码只切换显示；复制是独立的显式按钮，反馈「身份证号已复制」。
 * 此前一次点击会同时把完整身份证号静默送进剪贴板，构成隐私合规隐患。
 */

test('点击号码只切换显示，复制是独立显式按钮', async ({ page }) => {
  await page.route('**/api/v1/settings', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: reply({
        patientIdCardRevealEnabled: 'true',
        patientIdCardCopyEnabled: 'true',
      }) })
    }
    else { await route.continue() }
  })
  await page.route('**/api/v1/patients**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/api/v1/patients') && route.request().method() === 'GET') {
      await route.fulfill({ json: reply({ list: [syntheticPatient, syntheticPatientWithoutIdCard], total: 2 }) })
    }
    else { await route.continue() }
  })
  await page.goto('/patients', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('cell', { name: '合成患者', exact: true })).toBeVisible()

  const idCell = page.locator('.patients-table .id-card-toggle')
  // 初始为掩码
  await expect(idCell).toHaveText(/110\*+/)

  // 点击号码：显示明文，且不出现「已复制」反馈
  await idCell.click()
  await expect(idCell).toHaveText(syntheticPatient.idCard)
  await page.waitForTimeout(400)
  expect(await page.getByText('身份证号已复制').count()).toBe(0)

  // 再次点击：回到掩码
  await idCell.click()
  await expect(idCell).toHaveText(/110\*+/)

  // 自动化合成数据：无身份证号的行不渲染任何可点控件，只显示占位符
  const rowWithoutIdCard = page.getByRole('row', { name: /合成患者乙/ })
  await expect(rowWithoutIdCard.locator('.id-card-toggle')).toHaveCount(0)
  await expect(rowWithoutIdCard.locator('.id-card-copy')).toHaveCount(0)
  await expect(rowWithoutIdCard.locator('.patient-id-card')).toHaveText('—')

  // 复制是独立的显式按钮
  const copyButton = page.getByRole('button', { name: '复制身份证号' })
  await expect(copyButton).toBeVisible()
  await copyButton.click()
  await expect(page.getByText('身份证号已复制')).toBeVisible({ timeout: 10_000 })
  // 剪贴板里是完整身份证号（证明复制动作真的发生在此，而不是点击号码的副作用）
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(syntheticPatient.idCard)
})

test('只开启显示不开启复制时，不渲染复制入口', async ({ page }) => {
  await page.route('**/api/v1/settings', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: reply({
        patientIdCardRevealEnabled: 'true',
        patientIdCardCopyEnabled: 'false',
      }) })
    }
    else { await route.continue() }
  })
  await page.route('**/api/v1/patients**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/api/v1/patients') && route.request().method() === 'GET') {
      await route.fulfill({ json: reply({ list: [syntheticPatient], total: 1 }) })
    }
    else { await route.continue() }
  })
  await page.goto('/patients', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('cell', { name: '合成患者', exact: true })).toBeVisible()

  await expect(page.getByRole('button', { name: '复制身份证号' })).toHaveCount(0)

  // 显示切换仍可用
  const idCell = page.locator('.patients-table .id-card-toggle')
  await idCell.click()
  await expect(idCell).toHaveText(syntheticPatient.idCard)
})

test('只开启复制时号码不再是可点击控件，复制按钮单独提供', async ({ page }) => {
  await page.route('**/api/v1/settings', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: reply({
        patientIdCardRevealEnabled: 'false',
        patientIdCardCopyEnabled: 'true',
      }) })
    }
    else { await route.continue() }
  })
  await page.route('**/api/v1/patients**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/api/v1/patients') && route.request().method() === 'GET') {
      await route.fulfill({ json: reply({ list: [syntheticPatient], total: 1 }) })
    }
    else { await route.continue() }
  })
  await page.goto('/patients', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('cell', { name: '合成患者', exact: true })).toBeVisible()

  // 只有复制开关打开时，号码是纯文本，不再承担点击复制职责
  await expect(page.locator('.patients-table .id-card-toggle')).toHaveCount(0)
  await expect(page.locator('.patients-table .patient-id-card')).toContainText(/110\*+/)
  await expect(page.getByRole('button', { name: '复制身份证号' })).toBeVisible()
})

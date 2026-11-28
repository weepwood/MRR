import type { Locator } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

const evidence = process.env.MRR_PASSWORD_VISUAL_EVIDENCE
const capture = process.env.MRR_PASSWORD_VISUAL_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires evidence directory') }

async function compare(region: Locator, key: string, selectors: string) {
  const data = await region.evaluate((root, selectors) => {
    const r = root.getBoundingClientRect()
    return {
      width: r.width,
      height: r.height,
      controls: [...root.querySelectorAll(selectors)].map((node) => {
        const surface = node.closest('.el-input__wrapper') ?? node
        const b = surface.getBoundingClientRect()
        const s = getComputedStyle(surface)
        const ink = getComputedStyle(node)
        return { x: b.x - r.x, y: b.y - r.y, width: b.width, height: b.height, radius: s.borderRadius, font: ink.fontSize, weight: ink.fontWeight, color: ink.color, background: s.backgroundColor }
      }),
    }
  }, selectors)
  expect(data.controls.length).toBeGreaterThan(0)
  if (!evidence) { return }
  await fs.mkdir(evidence, { recursive: true })
  const file = path.join(evidence, key)
  if (capture) {
    await fs.writeFile(`${file}.json`, JSON.stringify(data, null, 2))
    await region.screenshot({ path: `${file}-before.png`, animations: 'disabled' })
  }
  else {
    const old = JSON.parse(await fs.readFile(`${file}.json`, 'utf8')) as typeof data
    await fs.writeFile(`${file}-after.json`, JSON.stringify(data, null, 2))
    await region.screenshot({ path: `${file}-after.png`, animations: 'disabled' })
    expect(data.controls.length).toBe(old.controls.length)
    for (const k of ['width', 'height'] as const) { expect(Math.abs(data[k] - old[k]), `${key} ${k}`).toBeLessThanOrEqual(1) }
    for (const [i, control] of data.controls.entries()) {
      for (const k of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(control[k] - old.controls[i][k]), `${key} ${i} ${k}`).toBeLessThanOrEqual(1) }
      for (const k of ['radius', 'font', 'weight', 'color', 'background'] as const) { expect(control[k], `${key} ${i} ${k}`).toBe(old.controls[i][k]) }
    }
  }
}

for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test.describe(`${palette}/${theme}`, () => {
      test.use({ storageState: 'e2e/.auth/anonymous.json' })
      test('记住用户名与个人密码表单视觉', async ({ page }) => {
        await isolateLocalVisualSettings(page)
        await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false }, menu: { mode: 'single' } })), { palette, theme })
        await page.route('**/api/v1/public/status/developer-mode', route => route.fulfill({ json: { code: 200, data: { enabled: false, accessMode: 'DISABLED' } } }))
        await page.setViewportSize({ width: 1440, height: 1000 })
        await page.goto('/login')
        await expect(page.locator('#login-account')).toBeVisible()
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        else { await expect(page.locator('html')).not.toHaveAttribute('data-mrr-palette', 'gr8r') }
        const remember = page.getByRole('checkbox', { name: '记住用户名' })
        const options = page.locator('.login-form .form-options')
        for (const width of [1440, 390]) {
          await page.setViewportSize({ width, height: 1000 })
          for (const checked of [false, true]) {
            if (await remember.isChecked() !== checked) {
              await remember.focus()
              await remember.press('Space')
            }
            if (checked) { await expect(remember).toBeChecked() }
            else { await expect(remember).not.toBeChecked() }
            await remember.blur()
            await page.mouse.move(0, 0)
            await compare(options, `${palette}-${theme}-${width}-remember-${checked}`, 'button[role="checkbox"], .el-checkbox__inner, .remember-control label:not(.el-checkbox), .el-checkbox__label')
          }
        }
        await page.setViewportSize({ width: 1440, height: 1000 })
        await page.locator('#login-account').fill('admin')
        await page.locator('#login-password').fill('Synthetic-123')
        await page.getByRole('button', { name: '登录系统' }).click()
        await page.getByRole('button', { name: /账号菜单：/ }).first().click()
        await page.getByText('个人设置', { exact: true }).click()
        const form = page.getByRole('dialog').locator('form')
        await expect(form.getByPlaceholder('请输入原密码')).toBeVisible()
        await page.mouse.move(0, 0)
        await compare(form, `${palette}-${theme}-1440-password`, 'input, button[type="submit"]')
        if (!capture) {
          await expect(form.locator('.fa-input-control, .fa-button-control')).toHaveCount(0)
          await expect(form.locator('.el-input')).toHaveCount(3)
        }
      })
    })
  }
}

test('修改密码校验、失败恢复、成功退出及复选框禁用保持原语义', async ({ page }) => {
  let requests = 0
  let release!: () => void
  const pending = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/auth/password/edit', async (route) => {
    requests++
    expect(route.request().postDataJSON()).toMatchObject({ password: 'Old-Synthetic-123', newPassword: 'New-Synthetic-456' })
    if (requests === 1) {
      await pending
      await route.fulfill({ status: 400, json: { code: 400, message: '合成改密失败' } })
    }
    else { await route.fulfill({ json: { code: 200, data: null } }) }
  })
  await page.goto('/ui-components')
  await page.getByLabel('查找组件', { exact: true }).fill('ElCheckbox')
  const demo = page.getByRole('checkbox', { name: '选中此示例' })
  await demo.focus()
  await demo.press('Space')
  await expect(demo).not.toBeChecked()
  await expect(page.getByRole('checkbox', { name: '禁用复选框', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: /账号菜单：/ }).first().click()
  await page.getByText('个人设置', { exact: true }).click()
  const form = page.getByRole('dialog').locator('form')
  const old = form.getByPlaceholder('请输入原密码')
  const next = form.getByPlaceholder('请输入 6 到 64 位新密码')
  const confirmation = form.getByPlaceholder('请确认新密码')
  const submit = form.getByRole('button', { name: '保存', exact: true })
  await old.fill('Old-Synthetic-123')
  await next.fill('New-Synthetic-456')
  await confirmation.fill('Mismatch-789')
  await submit.click()
  await expect(form.getByText('两次输入的密码不一致')).toBeVisible()
  await expect(confirmation).toHaveAttribute('aria-invalid', 'true')
  expect(requests).toBe(0)
  await confirmation.fill('New-Synthetic-456')
  try {
    await confirmation.press('Enter')
    await expect.poll(() => requests).toBe(1)
    await expect(submit).toBeDisabled()
    await confirmation.press('Enter')
    expect(requests).toBe(1)
  }
  finally { release() }
  await expect(submit).toBeEnabled()
  expect(requests).toBe(1)
  await expect(confirmation).toHaveValue('New-Synthetic-456')
  await expect(page.getByRole('dialog')).toBeVisible()
  await submit.click()
  await expect.poll(() => requests).toBe(2)
  await expect(page).toHaveURL(/\/login/)
  await expect.poll(() => page.evaluate(() => localStorage.getItem('mrr:auth:token'))).toBeNull()
})

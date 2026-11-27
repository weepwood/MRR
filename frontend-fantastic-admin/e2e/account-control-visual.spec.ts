import type { Locator } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test.use({ storageState: 'e2e/.auth/anonymous.json' })
const evidence = process.env.MRR_ACCOUNT_VISUAL_EVIDENCE
const capture = process.env.MRR_ACCOUNT_VISUAL_CAPTURE === 'true'
if (capture && !evidence) { throw new Error('Capture requires MRR_ACCOUNT_VISUAL_EVIDENCE') }

async function measure(form: Locator) {
  return form.evaluate((el) => {
    const r = el.getBoundingClientRect()
    const controls = [...el.querySelectorAll('input:not([type="checkbox"]), textarea, button[type="submit"], .form-actions button[type="button"]')].filter(node => node.getBoundingClientRect().width > 0)
    return {
      form: { width: r.width, height: r.height },
      controls: controls.map((node) => {
        const surface = node.closest('.el-input__wrapper') ?? node
        const b = surface.getBoundingClientRect()
        const s = getComputedStyle(surface)
        const ink = getComputedStyle(node)
        return { x: b.x - r.x, y: b.y - r.y, width: b.width, height: b.height, font: ink.fontSize, weight: ink.fontWeight, radius: s.borderRadius, color: ink.color, background: s.backgroundColor }
      }),
    }
  })
}
for (const palette of ['mrr', 'gr8r'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`账户表单迁移视觉 ${palette}/${theme}`, async ({ page }) => {
      await isolateLocalVisualSettings(page)
      await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: theme, themePalette: palette, enableMotion: false } })), { palette, theme })
      await page.route('**/api/v1/public/status/developer-mode', route => route.fulfill({ json: { code: 200, data: { enabled: false, accessMode: 'DISABLED' } } }))
      for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: 1000 })
        await page.goto('/login')
        const login = page.locator('.login-form')
        await expect(login).toBeVisible()
        await expect.poll(() => page.locator('html').evaluate(el => el.classList.contains('dark'))).toBe(theme === 'dark')
        if (palette === 'gr8r') { await expect(page.locator('html')).toHaveAttribute('data-mrr-palette', 'gr8r') }
        else { await expect(page.locator('html')).not.toHaveAttribute('data-mrr-palette', 'gr8r') }
        await expect(page.locator('#login-account')).toBeVisible()
        const check = async (form: Locator, mode: string) => {
          await page.mouse.move(0, 0)
          const data = await measure(form)
          expect(data.controls.length).toBeGreaterThan(0)
          if (!capture) {
            await expect(form.locator('.fa-input-control, .fa-textarea-control, .fa-button-control')).toHaveCount(0)
            expect(data.controls.length).toBe(mode === 'login' ? 3 : mode === 'identity' ? 5 : 4)
            expect(data.controls.filter(c => c.height !== 64).every(c => c.height === 38)).toBe(true)
            expect(data.controls.every(c => c.x >= -1 && c.x + c.width <= data.form.width + 1)).toBe(true)
          }
          if (evidence) {
            await fs.mkdir(evidence, { recursive: true })
            const key = path.join(evidence, `${palette}-${theme}-${width}-${mode}`)
            if (capture) {
              await fs.writeFile(`${key}.json`, JSON.stringify(data, null, 2))
              await form.screenshot({ path: `${key}-before.png`, animations: 'disabled' })
            }
            else {
              const old = JSON.parse(await fs.readFile(`${key}.json`, 'utf8')) as typeof data
              await fs.writeFile(`${key}-after.json`, JSON.stringify(data, null, 2))
              await form.screenshot({ path: `${key}-after.png`, animations: 'disabled' })
              expect(data.controls.length).toBe(old.controls.length)
              for (const prop of ['width', 'height'] as const) { expect(Math.abs(data.form[prop] - old.form[prop]), `${key} form ${prop}`).toBeLessThanOrEqual(1) }
              for (const [i, control] of data.controls.entries()) {
                for (const prop of ['x', 'y', 'width', 'height'] as const) { expect(Math.abs(control[prop] - old.controls[i][prop]), `${key} ${i} ${prop}`).toBeLessThanOrEqual(1) }
                for (const prop of ['font', 'weight', 'radius', 'color', 'background'] as const) { expect(control[prop], `${key} ${i} ${prop}`).toBe(old.controls[i][prop]) }
              }
            }
          }
        }
        await check(login, 'login')
        await page.goto('/login?mode=register')
        const register = page.locator('.register-form')
        await expect(register.locator('#register-account')).toBeVisible()
        await check(register, 'identity')
        await register.locator('#register-account').fill('synthetic.user')
        await register.locator('#register-display-name').fill('合成示例')
        await register.locator('#register-password').fill('Synthetic-123')
        await register.locator('#register-password-confirm').fill('Synthetic-123')
        await register.getByRole('button', { name: '填写申请说明' }).click()
        await expect(register.locator('#register-contact')).toBeFocused()
        await register.locator('#register-contact').blur()
        await check(register, 'application')
      }
    })
  }
}

test('登录表单保留密码切换、失败恢复、请求体与记住用户名语义', async ({ page }) => {
  await page.route('**/api/v1/public/status/developer-mode', route => route.fulfill({ json: { code: 200, data: { enabled: false, accessMode: 'DISABLED' } } }))
  let requests = 0
  let release!: () => void
  const hold = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/auth/login', async (route) => {
    requests++
    expect(route.request().method()).toBe('POST')
    expect(route.request().postDataJSON()).toMatchObject({ username: 'synthetic.user', password: 'Synthetic-123' })
    if (requests === 1) {
      await hold
      await route.fulfill({ json: { code: 200, message: '合成认证失败', data: { message: '合成认证失败' } } })
    }
    else { await route.continue() }
  })
  await page.goto('/login')
  const account = page.locator('#login-account')
  const password = page.locator('#login-password')
  await account.fill('synthetic.user')
  await password.fill('Synthetic-123')
  await page.getByRole('button', { name: '显示密码', exact: true }).click()
  await expect(password).toHaveAttribute('type', 'text')
  await expect(password).toHaveValue('Synthetic-123')
  await page.getByRole('button', { name: '隐藏密码', exact: true }).click()
  await expect(password).toHaveAttribute('type', 'password')
  const remember = page.getByRole('checkbox', { name: '记住用户名' })
  if (!await remember.isChecked()) { await page.locator('.remember-checkbox .el-checkbox__label').click() }
  await expect(remember).toBeChecked()
  await password.press('Enter')
  try {
    await expect.poll(() => requests).toBe(1)
    await expect(page.getByRole('button', { name: '正在验证...' })).toBeDisabled()
  }
  finally { release() }
  await expect(page.getByText('合成认证失败', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录系统' })).toBeEnabled()
  await expect(password).toHaveValue('Synthetic-123')
  await page.getByRole('button', { name: '登录系统' }).click()
  await expect.poll(() => requests).toBe(2)
  await expect.poll(() => page.evaluate(() => localStorage.getItem('mrr:login:remembered-account'))).toBe('synthetic.user')
  await expect(page).not.toHaveURL(/\/login/)
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('Synthetic-123')
})

test('注册密码不一致阻止进入下一步，切换可见性及修正后保留输入', async ({ page }) => {
  let submissions = 0
  page.on('request', (request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/auth/register')) { submissions++ }
  })

  await page.route('**/api/v1/public/status/developer-mode', route => route.fulfill({ json: { code: 200, data: { enabled: false, accessMode: 'DISABLED' } } }))
  await page.goto('/login?mode=register')
  await page.locator('#register-account').fill('synthetic.user')
  await page.locator('#register-display-name').fill('合成用户')
  const password = page.locator('#register-password')
  const confirm = page.locator('#register-password-confirm')
  await password.fill('Synthetic-123')
  await confirm.fill('Synthetic-456')
  await page.getByRole('button', { name: '显示密码', exact: true }).first().click()
  await expect(password).toHaveAttribute('type', 'text')
  await expect(confirm).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '隐藏密码', exact: true }).click()
  await page.getByRole('button', { name: '填写申请说明' }).click()
  await expect(page.getByText('两次输入的密码不一致', { exact: true })).toBeVisible()
  await expect(confirm).toHaveAttribute('aria-invalid', 'true')
  await confirm.fill('Synthetic-123')
  await expect(confirm).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('#register-contact')).toBeFocused()
  await page.getByRole('button', { name: '返回身份信息' }).click()
  await expect(password).toHaveValue('Synthetic-123')
  await expect(confirm).toHaveValue('Synthetic-123')
  expect(submissions, '分步返回不得提交注册').toBe(0)
})

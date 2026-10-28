import { expect, test } from '@playwright/test'

test.use({ storageState: 'e2e/.auth/anonymous.json' })

test.describe('登录页统一设计', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/public/status/developer-mode', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { enabled: false, accessMode: 'DISABLED' } }),
      })
    })
  })

  test('展示统一品牌、登录表单和管理员账号说明', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.login-shell')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: 'MRR 病案文件管理系统' })).toBeVisible()
    await expect(page.locator('.form-header')).toHaveCount(0)
    await expect(page.getByText('Secure sign in', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('heading', { name: '登录 MRR' })).toHaveCount(0)
    await expect(page.getByText('使用管理员分配的账号进入系统工作区。', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('textbox', { name: '用户名' })).toBeVisible()
    await expect(page.getByLabel('密码', { exact: true })).toBeVisible()
    await expect(page.getByRole('checkbox', { name: '记住用户名' })).toBeVisible()
    await expect(page.getByText('系统仅记住用户名，不保存密码；如需保存密码，请使用浏览器密码管理器。', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '登录系统' })).toBeVisible()
    await expect(page.getByText('账号创建、角色调整或密码问题请联系系统管理员。', { exact: true })).toHaveCount(0)
    await expect(page.locator('.login-help')).toHaveCount(0)
    await page.getByRole('button', { name: '登录系统' }).click()
    const usernameError = page.getByText('请输入用户名', { exact: true })
    await expect(usernameError).toBeVisible()
    await expect(usernameError).toHaveCSS('position', 'static')
    const errorBox = await usernameError.boundingBox()
    const passwordLabelBox = await page.getByText('密码', { exact: true }).first().boundingBox()
    expect(errorBox).not.toBeNull()
    expect(passwordLabelBox).not.toBeNull()
    expect(errorBox!.y + errorBox!.height).toBeLessThanOrEqual(passwordLabelBox!.y)
    await expect(page.locator('.theme-control')).toHaveCount(0)
    await expect(page.locator('.brand-mark')).toHaveCount(0)
    await expect(page.getByText('注册新帐号', { exact: true })).toHaveCount(0)
    await expect(page.getByText('忘记密码了?', { exact: true })).toHaveCount(0)
  })

  test('窄屏登录校验提示显示在标签右侧且卡片高度保持稳定', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const shell = page.locator('.login-shell')
    await expect(shell).toBeVisible({ timeout: 20_000 })
    await page.waitForTimeout(400)
    const heightBeforeValidation = (await shell.boundingBox())!.height

    await page.getByRole('button', { name: '登录系统' }).click()
    const usernameError = page.getByText('请输入用户名', { exact: true })
    const passwordError = page.getByText('请输入密码', { exact: true })
    await expect(usernameError).toBeVisible()
    await expect(passwordError).toBeVisible()

    const usernameLabelBox = await page.locator('label[for="login-account"]').boundingBox()
    const usernameErrorBox = await usernameError.boundingBox()
    const passwordLabelBox = await page.locator('label[for="login-password"]').boundingBox()
    const passwordErrorBox = await passwordError.boundingBox()
    expect(usernameLabelBox).not.toBeNull()
    expect(usernameErrorBox).not.toBeNull()
    expect(passwordLabelBox).not.toBeNull()
    expect(passwordErrorBox).not.toBeNull()
    expect(usernameErrorBox!.x).toBeGreaterThanOrEqual(usernameLabelBox!.x + usernameLabelBox!.width)
    expect(passwordErrorBox!.x).toBeGreaterThanOrEqual(passwordLabelBox!.x + passwordLabelBox!.width)
    expect(Math.abs(usernameErrorBox!.y - usernameLabelBox!.y)).toBeLessThanOrEqual(3)
    expect(Math.abs(passwordErrorBox!.y - passwordLabelBox!.y)).toBeLessThanOrEqual(3)

    const heightAfterValidation = (await shell.boundingBox())!.height
    expect(Math.abs(heightAfterValidation - heightBeforeValidation)).toBeLessThanOrEqual(1)
  })

  test('匿名用户可以直接提交注册申请', async ({ page }) => {
    let registrationRequested = false
    await page.route('**/api/v1/auth/register', async (route) => {
      registrationRequested = true
      expect(route.request().method()).toBe('POST')
      expect(route.request().headers().authorization).toBeUndefined()
      expect(route.request().postDataJSON()).toMatchObject({
        username: 'doctor.apply',
        password: '123456',
        displayName: '申请医生',
        contactInfo: '内线 6123',
        applyRemark: '需要查阅本科室住院病案',
      })
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: '注册申请已提交，请等待管理员审核',
          data: { username: 'doctor.apply', status: 'pending' },
        }),
      })
    })

    await page.goto('/login?mode=register', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.form-header')).toHaveCount(0)
    await expect(page.getByText('注册申请由系统管理员审核。请填写可核验的显示名称、联系方式或申请说明。', { exact: true })).toHaveCount(0)
    await expect(page.getByText('Account application', { exact: true })).toHaveCount(0)
    await expect(page.getByText('申请系统账号', { exact: true })).toHaveCount(0)
    await expect(page.getByText('填写申请信息。管理员审核通过后，才可以使用注册账号登录。', { exact: true })).toHaveCount(0)

    const identitySection = page.locator('.form-section').filter({ hasText: '身份信息' })
    const applicationSection = page.locator('.form-section').filter({ hasText: '申请说明' })
    const progress = page.locator('.review-flow__steps')
    await expect(progress.getByText('1身份信息')).toBeVisible()
    await expect(progress.getByText('2申请说明')).toBeVisible()
    await expect(progress.getByText('3管理员审核')).toBeVisible()
    await expect(identitySection.getByLabel('用户名')).toBeVisible()
    await expect(identitySection.locator('#register-password')).toBeVisible()
    await expect(identitySection.locator('#register-password-confirm')).toBeVisible()
    await expect(identitySection.locator('#register-contact')).toHaveCount(0)
    await expect(applicationSection).toHaveCount(0)
    await page.getByRole('button', { name: '填写申请说明' }).click()
    await expect(page.getByText('请输入显示名称')).toBeVisible()
    await expect(applicationSection).toHaveCount(0)

    await page.getByLabel('用户名').fill('doctor.apply')
    await page.getByLabel('显示名称').fill('申请医生')
    await page.locator('#register-password').fill('123456')
    await page.locator('#register-password-confirm').fill('123456')
    await page.locator('#register-password-confirm').press('Enter')

    await expect(identitySection).toHaveCount(0)
    await expect(applicationSection.locator('#register-contact')).toBeVisible()
    await expect(applicationSection.locator('#register-remark')).toBeVisible()
    await expect(page.getByRole('heading', { name: '申请说明' })).toBeFocused()
    await expect(page.getByRole('button', { name: '提交注册' })).toBeVisible()
    await expect.poll(() => registrationRequested).toBe(false)

    await page.getByLabel('联系方式').fill('内线 6123')
    await page.getByLabel('申请说明').fill('需要查阅本科室住院病案')
    await page.getByRole('button', { name: '返回身份信息' }).click()

    await expect(identitySection.getByLabel('用户名')).toHaveValue('doctor.apply')
    await expect(page.getByRole('heading', { name: '身份信息' })).toBeFocused()
    await expect(applicationSection).toHaveCount(0)
    await page.getByRole('button', { name: '填写申请说明' }).click()
    await expect(applicationSection.locator('#register-contact')).toHaveValue('内线 6123')
    await expect(applicationSection.locator('#register-remark')).toHaveValue('需要查阅本科室住院病案')
    await page.getByRole('button', { name: '提交注册' }).click()

    await expect.poll(() => registrationRequested).toBe(true)
    await expect(page.getByText('注册申请已提交，请等待管理员审核。审核通过后即可登录。')).toBeVisible()
    await expect(page.locator('.form-header')).toHaveCount(0)
  })

  test('匿名读取系统标识并悬停显示管理员联系方式', async ({ page }) => {
    await page.route('**/api/v1/public/config/login-page', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            systemName: '医院病案影像平台',
            systemShortName: '院内系统',
            systemEnglishName: 'Hospital Medical Archive',
            organizationName: '测试医院',
            systemDescription: '统一管理院内病案影像。',
            loginHelpText: '账号问题请联系系统管理员。',
            systemAdminContactVisible: 'true',
            systemAdminDisplayName: 'MRR 运维组',
            systemAdminDepartment: '信息科',
            systemAdminPhone: '0571-12345678',
            systemAdminServiceHours: '工作日 08:00–17:30',
          },
        }),
      })
    })

    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    await expect(page.getByRole('heading', { name: '医院病案影像平台' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '医院病案影像平台' })).toBeVisible()
    const contact = page.getByRole('button', { name: '查看系统管理员信息' })
    await expect(contact).toBeVisible()
    await contact.hover()
    await expect(page.getByText('MRR 运维组')).toBeVisible()
    await expect(page.getByText('0571-12345678')).toBeVisible()
    await expect(page.getByText('工作日 08:00–17:30')).toBeVisible()
  })

  test('接入环境自检展开后展示地址、网络与浏览器信息', async ({ page }) => {
    let probeRequestCount = 0
    await page.route('**/api/v1/public/config/client-environment', async (route) => {
      probeRequestCount += 1
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: '操作成功',
          data: {
            clientIp: '192.168.10.24',
            ipVersion: 'IPV4',
            behindProxy: true,
            serverTime: '2026-10-16T17:15:20+08:00',
            serverZoneId: 'Asia/Shanghai',
          },
        }),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const footer = page.locator('.brand-footer')
    await expect(page.locator('.login-toolbar')).toHaveCount(0)
    await expect(footer).toBeVisible()
    await expect(page.getByText('医院内网部署 · 数据由本地服务管理', { exact: true })).toHaveCount(0)
    const badge = footer.getByRole('button', { name: /MRR v/ })
    await expect(badge).toBeVisible({ timeout: 20_000 })
    await expect(badge).not.toContainText('医院内网系统')
    await expect(badge.locator('.environment-chevron')).toHaveCount(0)
    await expect(badge).toContainText('客户端 IP')
    await expect(badge).toContainText('192.168.10.24')

    const panel = page.locator('#login-environment-panel')
    await expect(panel).toBeHidden()

    await badge.hover()
    await expect(panel).toBeVisible()
    await expect(page.locator('.login-environment-popper')).toBeVisible()
    await expect(panel).toContainText('网络')
    await expect(panel).toContainText('接入地址')
    await expect(panel).toContainText('客户端')
    await expect(panel).toContainText('192.168.10.24')
    await expect(panel).toContainText('客户端 IP')
    await expect(panel).toContainText('经反向代理转发')
    await expect(panel).toContainText('Asia/Shanghai')
    await expect(panel.getByText('本机当前时间')).toBeVisible()
    await expect(panel.locator('.environment-item').filter({ hasText: '本机当前时间' }).locator('dd')).toHaveText(/\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)
    await expect(panel.getByRole('button', { name: '复制环境信息' })).toBeVisible()
    await expect(panel.getByRole('button', { name: '重新检测' })).toBeVisible()

    const resolutionItem = panel.locator('.environment-item').filter({ hasText: '屏幕分辨率' })
    const pixelRatioItem = panel.locator('.environment-item').filter({ hasText: '像素比' })
    await expect(resolutionItem).not.toHaveClass(/environment-item--wide/)
    await expect(resolutionItem.locator('dd')).toHaveCSS('white-space', 'normal')
    const [resolutionBox, pixelRatioBox] = await Promise.all([resolutionItem.boundingBox(), pixelRatioItem.boundingBox()])
    expect(resolutionBox).not.toBeNull()
    expect(pixelRatioBox).not.toBeNull()
    expect(resolutionBox!.width).toBeLessThan(400)
    expect(Math.abs(resolutionBox!.y - pixelRatioBox!.y)).toBeLessThanOrEqual(2)
    expect(resolutionBox!.x).toBeLessThan(pixelRatioBox!.x)

    const initialProbeRequestCount = probeRequestCount
    await page.mouse.move(0, 0)
    await expect(panel).toBeHidden()
    await badge.hover()
    await expect(panel).toBeVisible()
    expect(probeRequestCount).toBe(initialProbeRequestCount)

    await page.mouse.move(0, 0)
    await expect(panel).toBeHidden()
  })

  test('接入环境弹窗可通过键盘打开和关闭', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const badge = page.locator('.brand-footer').getByRole('button', { name: /MRR v/ })
    const panel = page.locator('#login-environment-panel')
    for (const activationKey of ['Enter', 'Space']) {
      await badge.focus()
      await page.keyboard.press(activationKey)

      await expect(panel).toBeVisible()
      await expect(panel.getByRole('button', { name: '重新检测' })).toBeFocused()
      await page.keyboard.press('Escape')

      await expect(panel).toBeHidden()
      await expect(badge).toBeFocused()
    }
  })

  test('诊断请求尚未结束时键盘仍可聚焦并禁止重复检测', async ({ page }) => {
    let releaseProbe!: () => void
    const pendingProbe = new Promise<void>((resolve) => { releaseProbe = resolve })
    let probeRequests = 0
    await page.route('**/api/v1/public/config/client-environment', async (route) => {
      probeRequests++
      await pendingProbe
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { clientIp: '192.0.2.10', ipVersion: 'IPV4', behindProxy: false } }),
      })
    })

    await page.goto('/login', { waitUntil: 'domcontentloaded' })
    await expect.poll(() => probeRequests).toBe(1)
    const badge = page.locator('.brand-footer').getByRole('button', { name: /MRR v/ })
    await badge.focus()
    await page.keyboard.press('Enter')

    const panel = page.locator('#login-environment-panel')
    const refresh = panel.locator('.environment-actions button').first()
    await expect(panel).toBeVisible()
    await expect(refresh).toHaveText('检测中…')
    await expect(refresh).toHaveAttribute('aria-disabled', 'true')
    await expect(refresh).toBeFocused()

    await page.keyboard.press('Enter')
    expect(probeRequests).toBe(1)
    releaseProbe()
    await expect(refresh).toHaveAttribute('aria-disabled', 'false')

    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()
    await expect(badge).toBeFocused()
  })

  test('窄屏保持单列并隐藏非必要介绍', async ({ page }) => {
    await page.route('**/api/v1/public/config/login-page', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            systemAdminContactVisible: 'true',
            systemAdminDisplayName: 'MRR 运维组',
            systemAdminDepartment: '信息科',
            systemAdminPhone: '0571-12345678',
          },
        }),
      })
    })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const shell = page.locator('.login-shell')
    await expect(shell).toBeVisible({ timeout: 20_000 })
    await expect.poll(async () => shell.evaluate(
      element => element.getBoundingClientRect().width <= window.innerWidth,
    )).toBe(true)
    await expect(page.getByRole('textbox', { name: '用户名' })).toBeInViewport()
    await expect(page.locator('.feature-list')).toBeHidden()
    const environmentBadge = page.locator('.brand-footer').getByRole('button', { name: /MRR v/ })
    await expect(environmentBadge).toBeVisible()
    await environmentBadge.click()
    const environmentPopup = page.locator('.login-environment-popper')
    await expect(environmentPopup).toBeVisible()
    const popupBox = await environmentPopup.boundingBox()
    expect(popupBox).not.toBeNull()
    expect(popupBox!.x).toBeGreaterThanOrEqual(0)
    expect(popupBox!.x + popupBox!.width).toBeLessThanOrEqual(390)
    await page.mouse.move(0, 0)
    await expect(environmentPopup).toBeHidden()

    const adminContact = page.getByRole('button', { name: '查看系统管理员信息' })
    await expect(adminContact).toBeVisible()
    await adminContact.click()
    const adminContactPopup = page.locator('.mrr-admin-contact-popper')
    await expect(adminContactPopup).toBeVisible()
    const adminContactBox = await adminContactPopup.boundingBox()
    expect(adminContactBox).not.toBeNull()
    expect(adminContactBox!.x).toBeGreaterThanOrEqual(0)
    expect(adminContactBox!.x + adminContactBox!.width).toBeLessThanOrEqual(390)
  })

  test('320px 窄屏注册进度以三列横向显示且不重叠', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 800 })
    await page.goto('/login?mode=register', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.login-shell')).toHaveCSS('min-height', '0px')
    const progress = page.locator('.review-flow__steps')
    await expect(progress.getByText('1身份信息')).toBeVisible()
    await expect(progress.getByText('2申请说明')).toBeVisible()
    await expect(progress.getByText('3管理员审核')).toBeVisible()
    await expect.poll(() => progress.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)

    const steps = progress.locator('span')
    await expect(steps.first()).toHaveCSS('flex-direction', 'column')
    const boxes = await Promise.all([0, 1, 2].map(index => steps.nth(index).boundingBox()))
    expect(boxes.every(Boolean)).toBe(true)
    expect(boxes[0]!.x + boxes[0]!.width).toBeLessThanOrEqual(boxes[1]!.x)
    expect(boxes[1]!.x + boxes[1]!.width).toBeLessThanOrEqual(boxes[2]!.x)
    expect(Math.abs(boxes[0]!.y - boxes[1]!.y)).toBeLessThanOrEqual(2)
    expect(Math.abs(boxes[1]!.y - boxes[2]!.y)).toBeLessThanOrEqual(2)
  })

  test('637px 竖屏注册进度仍以清晰的三列步骤显示', async ({ page }) => {
    await page.setViewportSize({ width: 637, height: 845 })
    await page.goto('/login?mode=register', { waitUntil: 'domcontentloaded' })

    const progress = page.locator('.review-flow__steps')
    const steps = progress.locator('span')
    await expect(progress.getByText('1身份信息')).toBeVisible()
    await expect(progress.getByText('2申请说明')).toBeVisible()
    await expect(progress.getByText('3管理员审核')).toBeVisible()
    await expect(steps.first()).toHaveCSS('flex-direction', 'column')
    await expect.poll(() => progress.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)

    const boxes = await Promise.all([0, 1, 2].map(index => steps.nth(index).boundingBox()))
    expect(boxes.every(Boolean)).toBe(true)
    expect(boxes[0]!.x + boxes[0]!.width).toBeLessThanOrEqual(boxes[1]!.x)
    expect(boxes[1]!.x + boxes[1]!.width).toBeLessThanOrEqual(boxes[2]!.x)
    expect(Math.abs(boxes[0]!.y - boxes[1]!.y)).toBeLessThanOrEqual(2)
    expect(Math.abs(boxes[1]!.y - boxes[2]!.y)).toBeLessThanOrEqual(2)
  })

  test('登录与注册切换时卡片高度平滑过渡', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const card = page.locator('.login-shell')
    const loginHeight = (await card.boundingBox())!.height
    await page.getByRole('tab', { name: '注册申请' }).click()
    await expect(card).toHaveClass(/is-register/)
    await page.waitForTimeout(150)
    const transitionHeight = (await card.boundingBox())!.height
    await page.waitForTimeout(350)
    const registerHeight = (await card.boundingBox())!.height

    expect(transitionHeight).toBeGreaterThan(loginHeight + 1)
    expect(transitionHeight).toBeLessThan(registerHeight - 1)

    await page.getByRole('tab', { name: '账号登录' }).click()
    await expect(card).not.toHaveClass(/is-register/)
    await page.waitForTimeout(150)
    const reverseTransitionHeight = (await card.boundingBox())!.height
    await page.waitForTimeout(350)
    const returnedLoginHeight = (await card.boundingBox())!.height

    expect(reverseTransitionHeight).toBeLessThan(registerHeight - 1)
    expect(reverseTransitionHeight).toBeGreaterThan(returnedLoginHeight + 1)

    await page.getByRole('tab', { name: '注册申请' }).click()
    await page.waitForTimeout(100)
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.getByRole('tab', { name: '账号登录' }).click()
    await expect.poll(() => card.evaluate(element => element.style.height)).toBe('')
    await expect.poll(() => card.evaluate(element => element.style.minHeight)).toBe('')
  })

  test('竖屏登录与注册切换时卡片顶部保持稳定', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/login', { waitUntil: 'domcontentloaded' })

    const card = page.locator('.login-shell')
    await expect(card).toBeVisible()
    await page.waitForTimeout(400)
    const loginTop = (await card.boundingBox())!.y

    await page.getByRole('tab', { name: '注册申请' }).click()
    await expect(card).toHaveClass(/is-register/)
    const loginForm = page.locator('.login-form')
    await expect(loginForm).toHaveClass(/auth-mode-forward-leave-active/)
    await page.waitForTimeout(120)
    const forwardX = await loginForm.evaluate(element =>
      new DOMMatrixReadOnly(getComputedStyle(element).transform).m41,
    )
    expect(forwardX).toBeLessThan(0)
    await expect.poll(() => card.evaluate(element => element.style.transition)).toBe('')
    await expect.poll(() => card.evaluate(element => element.style.height)).toBe('')
    const registerForm = page.locator('.register-form')
    await expect(registerForm).toHaveClass(/auth-mode-forward-enter-active/)
    await page.waitForTimeout(100)
    const forwardEnterX = await registerForm.evaluate(element =>
      new DOMMatrixReadOnly(getComputedStyle(element).transform).m41,
    )
    expect(forwardEnterX).toBeGreaterThan(0)
    const transitionTop = (await card.boundingBox())!.y

    expect(Math.abs(transitionTop - loginTop)).toBeLessThanOrEqual(1)

    await page.waitForTimeout(350)
    const registerBox = (await card.boundingBox())!
    const registerTop = registerBox.y
    const documentHeight = await page.evaluate(() => document.documentElement.scrollHeight)
    expect(registerBox.y + registerBox.height).toBeLessThanOrEqual(documentHeight)
    expect(documentHeight).toBeGreaterThanOrEqual(844)

    await page.getByRole('tab', { name: '账号登录' }).click()
    await expect(card).not.toHaveClass(/is-register/)
    await expect(registerForm).toHaveClass(/auth-mode-backward-leave-active/)
    await page.waitForTimeout(120)
    const reverseMotion = await registerForm.evaluate(element =>
      new DOMMatrixReadOnly(getComputedStyle(element).transform).m41,
    )
    expect(reverseMotion).toBeGreaterThan(0)
    await expect(loginForm).toHaveClass(/auth-mode-backward-enter-active/)
    await page.waitForTimeout(100)
    const reverseEnterX = await loginForm.evaluate(element =>
      new DOMMatrixReadOnly(getComputedStyle(element).transform).m41,
    )
    expect(reverseEnterX).toBeLessThan(0)
    const reverseTransitionTop = (await card.boundingBox())!.y

    expect(Math.abs(reverseTransitionTop - registerTop)).toBeLessThanOrEqual(1)
  })
})

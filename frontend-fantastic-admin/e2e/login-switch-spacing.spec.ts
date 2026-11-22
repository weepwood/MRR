import { expect, test } from '@playwright/test'
import { sampleAuthModeSwitch } from './helpers/auth-mode-motion'

test.use({ storageState: 'e2e/.auth/anonymous.json' })

test('宽屏登录字段保持固定节奏，登录须知卡吸收富余且切换后保持', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/login')
  await expect(page.locator('#login-account')).toBeVisible()
  for (let round = 0; round < 3; round++) {
    if (round === 2) {
      await page.getByRole('button', { name: '登录系统' }).click()
      await expect(page.getByText('请输入用户名', { exact: true })).toBeVisible()
    }
    const gaps = await page.evaluate(() => {
      const username = document.querySelector('#login-account')!.getBoundingClientRect()
      const password = document.querySelector('#login-password')!.getBoundingClientRect()
      const labels = document.querySelectorAll('.login-form .field-label-row')
      const notice = document.querySelector('.login-notice')!.getBoundingClientRect()
      const card = document.querySelector('.login-fields-card')!.getBoundingClientRect()
      const tabs = document.querySelector('.auth-mode-switch')!.getBoundingClientRect()
      const submit = document.querySelector('.login-submit-btn')!.getBoundingClientRect()
      const panel = document.querySelector('.form-panel')!.getBoundingClientRect()
      const options = document.querySelector('.login-form .form-options')!.getBoundingClientRect()
      return {
        usernameHeight: username.height,
        passwordHeight: password.height,
        afterDivider: labels[0].getBoundingClientRect().top - document.querySelector('.login-fields-card__heading')!.getBoundingClientRect().bottom,
        username: username.top - labels[0].getBoundingClientRect().bottom,
        password: password.top - labels[1].getBoundingClientRect().bottom,
        nextPassword: labels[1].getBoundingClientRect().top - username.bottom,
        remember: options.top - password.bottom,
        noticeHeight: notice.height,
        afterNotice: card.top - notice.bottom,
        belowCard: submit.top - card.bottom,
        panelTop: tabs.top - panel.top,
        panelBottom: panel.bottom - submit.bottom,
      }
    })
    expect(gaps.usernameHeight).toBeCloseTo(38, 1)
    expect(gaps.passwordHeight).toBeCloseTo(38, 1)
    expect(gaps.username, '标签贴控件 8px').toBeCloseTo(8, 1)
    expect(gaps.password, '标签贴控件 8px').toBeCloseTo(8, 1)
    expect(gaps.nextPassword, '字段组之间 16px').toBeCloseTo(16, 1)
    expect(gaps.remember, '字段组与选项行之间 16px').toBeCloseTo(16, 1)
    expect(gaps.afterDivider, '分割线到首个字段也是固定 16px').toBeCloseTo(16, 1)
    expect(gaps.noticeHeight, '登录须知卡按内容自然高度渲染').toBeGreaterThanOrEqual(60)
    expect(gaps.afterNotice, '须知卡与账号信息卡之间固定 16px').toBeCloseTo(16, 1)
    expect(gaps.belowCard, '残余留白集中到提交按钮上方，不再出现大空洞').toBeGreaterThanOrEqual(16)
    expect(gaps.panelTop).toBeCloseTo(gaps.panelBottom, 1)
    expect(gaps.panelTop).toBeCloseTo(60, 1)
    await page.getByRole('tab', { name: '注册申请' }).click()
    await expect(page.locator('.login-form')).toHaveCount(0)
    await page.getByRole('tab', { name: '账号登录' }).click()
    await expect(page.locator('.register-form')).toHaveCount(0)
  }
})

for (const width of [390, 637, 1440]) {
  test(`${width}px 注册卡片滑出期间底部两个按钮保持完整高度`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1200 })
    await page.goto('/login?mode=register')
    await expect(page.getByRole('button', { name: '填写申请说明' })).toBeVisible()
    const samples = await page.evaluate(async () => {
      document.querySelector<HTMLButtonElement>('.auth-mode-switch button')!.click()
      const results = []
      const start = performance.now()
      do {
        await new Promise(requestAnimationFrame)
        const actions = document.querySelector('.register-form .form-actions')
        if (!actions) {
          break
        }
        const bottom = actions.getBoundingClientRect().bottom
        const stage = document.querySelector('.auth-mode-stage')!.getBoundingClientRect()
        const stageBottom = stage.bottom
        const shellBottom = document.querySelector('.login-shell')!.getBoundingClientRect().bottom
        // 退出卡片默认不接收鼠标事件；临时参与命中测试，以检查裁切而非交互状态。
        const form = actions.closest('form')!
        const pointerEvents = form.style.pointerEvents
        form.style.pointerEvents = 'auto'
        const buttonBottomsVisible = Array.from(actions.querySelectorAll('button')).every((button) => {
          const box = button.getBoundingClientRect()
          const left = Math.max(box.left, stage.left + 4)
          const right = Math.min(box.right, stage.right - 4)
          // 仅剩圆角边缘的窄条不参与检测，避免把透明圆角误判为遮挡。
          return right - left <= 32 || document.elementsFromPoint((left + right) / 2, box.bottom - 4).some(element => button.contains(element))
        })
        form.style.pointerEvents = pointerEvents
        results.push({ stageBottom, shellBottom, bottom, buttonBottomsVisible })
      } while (performance.now() - start < 400)
      return results
    })
    expect(samples.length).toBeGreaterThan(1)
    for (const sample of samples) {
      expect(sample.stageBottom, '动画窗口不能截断按钮底边').toBeGreaterThanOrEqual(sample.bottom)
      expect(sample.shellBottom, '登录外壳不能截断按钮底边').toBeGreaterThanOrEqual(sample.bottom)
      expect(sample.buttonBottomsVisible, '仍在窗口内的按钮底部应实际可见').toBe(true)
    }
  })
}

for (const width of [390, 637, 1440]) {
  test(`${width}px 选中底板与卡片同向穿梭并共用贝塞尔曲线`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await page.emulateMedia({ reducedMotion: 'no-preference' })
    await page.goto('/login')
    await expect(page.locator('#login-account')).toBeVisible()
    const switcher = page.locator('.auth-mode-switch')
    const travel = await switcher.evaluate((element) => {
      const style = getComputedStyle(element, '::before')
      return Number.parseFloat(style.width) + 4
    })
    const { forward, backward } = await sampleAuthModeSwitch(page, true)
    expect(forward.some(frame => frame.indicatorX > 1 && frame.indicatorX < travel - 1)).toBe(true)
    expect(forward.some(frame => frame.enterX < -20 && frame.leaveX > 40)).toBe(true)
    for (const frame of forward) {
      // 与 login.vue 的 --auth-switch-duration / --auth-switch-ease 对齐：overlay（220ms）+ --mrr-ease-out。
      expect(frame.timings).toEqual(Array.from({ length: 3 }).fill('cubic-bezier(0.22, 1, 0.36, 1)'))
      expect(frame.durations).toEqual(['0.22s', '0.22s', '0.22s'])
      expect(frame.pageOverflows).toBe(false)
      expect(Math.abs(frame.actionTopDelta), '进入注册时两个主按钮应沿同一水平线滑动').toBeLessThanOrEqual(1)
    }
    // 底板尚未到站就反向点击，验证动画能从当前位置返回。
    expect(backward[0].indicatorX).toBeGreaterThan(1)
    expect(backward[0].indicatorX).toBeLessThan(travel - 1)
    expect(backward.some(frame => frame.enterX > 20 && frame.leaveX < -40)).toBe(true)
    for (const frame of backward) {
      expect(frame.pageOverflows).toBe(false)
      expect(Math.abs(frame.actionTopDelta), '返回登录时两个主按钮应沿同一水平线滑动').toBeLessThanOrEqual(1)
    }
    await expect.poll(() => switcher.evaluate(element => new DOMMatrixReadOnly(getComputedStyle(element, '::before').transform).m41)).toBe(0)
    await expect(page.locator('.register-form')).toHaveCount(0)
    await expect(page.locator('#login-account')).toBeVisible()
    const viewport = page.locator('.auth-mode-stage')
    await expect(viewport).toHaveCSS('overflow', 'hidden')
  })
}

test('关闭动态效果时切换即时完成且支持键盘操作', async ({ page }) => {
  await page.goto('/login')
  await expect(page.locator('#login-account')).toBeVisible()
  await page.locator('html').evaluate(element => element.dataset.motion = 'off')
  const registerTab = page.getByRole('tab', { name: '注册申请' })
  await registerTab.focus()
  await page.keyboard.press('Enter')
  await expect(registerTab).toHaveAttribute('aria-selected', 'true')
  await expect(page.locator('.login-form')).toHaveCount(0)
  await expect(page.locator('#register-account')).toBeVisible()
  const duration = await page.locator('.auth-mode-switch').evaluate(element => Number.parseFloat(getComputedStyle(element, '::before').transitionDuration))
  expect(duration).toBeLessThanOrEqual(0.001)
})

for (const width of [390, 637, 1440]) {
  for (const reducedMotion of ['no-preference', 'reduce'] as const) {
    test(`${width}px / ${reducedMotion} 注册返回登录时字段间距逐帧稳定`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 1000 })
      await page.emulateMedia({ reducedMotion })
      await page.goto('/login')
      await expect(page.locator('#login-account')).toBeVisible()
      const measure = () => {
        const form = document.querySelector('.login-form')!
        const heading = form.querySelector('.login-fields-card__heading')!
        const label = form.querySelector('label[for="login-account"]')!
        return {
          headingGap: label.getBoundingClientRect().top - heading.getBoundingClientRect().bottom,
          fieldsHeight: form.querySelector('.form-options')!.getBoundingClientRect().bottom - form.querySelector('.field-label-row')!.getBoundingClientRect().top,
          formHeight: form.getBoundingClientRect().height,
          stageHeight: document.querySelector('.auth-mode-stage')!.getBoundingClientRect().height,
        }
      }
      const baseline = await page.evaluate(measure)
      await page.getByRole('tab', { name: '注册申请' }).click()
      await expect(page.locator('#register-account')).toBeVisible()
      await expect(page.locator('.login-form')).toHaveCount(0)
      const shared = await page.evaluate(() => ({
        formHeight: document.querySelector('.register-form')!.getBoundingClientRect().height,
        stageHeight: document.querySelector('.auth-mode-stage')!.getBoundingClientRect().height,
      }))
      // 在点击后的第一帧开始采样，覆盖退出表单仍在 DOM 中的整个阶段。
      const frames = await page.evaluate(async () => {
        const read = () => {
          const form = document.querySelector('.login-form')!
          const heading = form.querySelector('.login-fields-card__heading')!
          const label = form.querySelector('label[for="login-account"]')!
          return {
            headingGap: label.getBoundingClientRect().top - heading.getBoundingClientRect().bottom,
            fieldsHeight: form.querySelector('.form-options')!.getBoundingClientRect().bottom - form.querySelector('.field-label-row')!.getBoundingClientRect().top,
            formHeight: form.getBoundingClientRect().height,
            stageHeight: document.querySelector('.auth-mode-stage')!.getBoundingClientRect().height,
          }
        }
        const loginTab = Array.from(document.querySelectorAll<HTMLButtonElement>('[role="tab"]')).find(button => button.textContent?.includes('账号登录'))!
        loginTab.click()
        const samples = []
        const start = performance.now()
        do {
          await new Promise(requestAnimationFrame)
          samples.push(read())
        } while (performance.now() - start < 450)
        return samples
      })
      for (const frame of frames) {
        expect(Math.abs(frame.headingGap - baseline.headingGap), '标题到用户名的间距').toBeLessThanOrEqual(1)
        expect(Math.abs(frame.fieldsHeight - frames[0].fieldsHeight), '分配完留白后输入区在滑动期间保持稳定').toBeLessThanOrEqual(1)
        expect(Math.abs(frame.formHeight - shared.formHeight), '两种表单共用的按钮基线高度').toBeLessThanOrEqual(1)
      }
      for (let index = 0; index < 3; index++) {
        await page.getByRole('tab', { name: '注册申请' }).click()
        await page.getByRole('tab', { name: '账号登录' }).click()
      }
      await expect(page.locator('.register-form')).toHaveCount(0)
      await expect.poll(() => page.locator('.auth-mode-stage').evaluate(element => (element as HTMLElement).style.height)).toBe('')
      const settled = await page.evaluate(measure)
      // 位移合成可能引入浮点尾差，仍要求最终尺寸在 0.01px 内一致。
      expect(settled.headingGap).toBeCloseTo(baseline.headingGap, 2)
      expect(settled.fieldsHeight).toBeCloseTo(frames[0].fieldsHeight, 2)
      expect(settled.formHeight).toBeCloseTo(shared.formHeight, 2)
      expect(settled.stageHeight).toBeCloseTo(shared.stageHeight, 2)
      await expect(page.locator('#login-account')).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('login.png'), fullPage: true })
    })
  }
}

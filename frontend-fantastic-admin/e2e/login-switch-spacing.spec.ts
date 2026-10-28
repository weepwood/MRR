import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

test.use({ storageState: 'e2e/.auth/anonymous.json' })

test('宽屏登录标签间距一致，卡片上下留白一致且切换后保持', async ({ page }) => {
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
      const slideHeading = document.querySelector('.slide-check__heading')!.getBoundingClientRect()
      const track = document.querySelector('.slide-check__track')!.getBoundingClientRect()
      const card = document.querySelector('.login-fields-card')!.getBoundingClientRect()
      const tabs = document.querySelector('.auth-mode-switch')!.getBoundingClientRect()
      const submit = document.querySelector('.login-submit-btn')!.getBoundingClientRect()
      const panel = document.querySelector('.form-panel')!.getBoundingClientRect()
      const options = document.querySelector('.login-form .form-options')!.getBoundingClientRect()
      return {
        usernameHeight: username.height,
        passwordHeight: password.height,
        sliderHeight: track.height,
        afterDivider: labels[0].getBoundingClientRect().top - document.querySelector('.login-fields-card__heading')!.getBoundingClientRect().bottom,
        username: username.top - labels[0].getBoundingClientRect().bottom,
        password: password.top - labels[1].getBoundingClientRect().bottom,
        slider: track.top - slideHeading.bottom,
        nextPassword: labels[1].getBoundingClientRect().top - username.bottom,
        nextSlider: slideHeading.top - password.bottom,
        remember: options.top - track.bottom,
        aboveCard: card.top - tabs.bottom,
        belowCard: submit.top - card.bottom,
        panelTop: tabs.top - panel.top,
        panelBottom: panel.bottom - submit.bottom,
      }
    })
    expect(gaps.username).toBeCloseTo(gaps.slider, 1)
    expect(gaps.usernameHeight).toBe(38)
    expect(gaps.passwordHeight).toBe(38)
    expect(gaps.sliderHeight).toBe(38)
    expect(gaps.afterDivider).toBeCloseTo(gaps.slider, 1)
    expect(gaps.password).toBeCloseTo(gaps.slider, 1)
    expect(gaps.nextPassword).toBeCloseTo(gaps.slider, 1)
    expect(gaps.nextSlider).toBeCloseTo(gaps.slider, 1)
    expect(gaps.remember).toBeCloseTo(gaps.slider, 1)
    expect(gaps.slider, '七处红箭头间距应平分可用留白，并至少有 8px 间距').toBeGreaterThanOrEqual(8)
    expect(gaps.aboveCard).toBeCloseTo(gaps.belowCard, 1)
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

async function sampleSwitch(page: Page, mode: 'login' | 'register') {
  return page.evaluate(async (mode) => {
    const tabs = document.querySelectorAll<HTMLButtonElement>('.auth-mode-switch button')
    tabs[mode === 'register' ? 1 : 0].click()
    const samples = []
    const start = performance.now()
    do {
      await new Promise(requestAnimationFrame)
      const incoming = document.querySelector(`.${mode}-form`)!
      const outgoing = document.querySelector(mode === 'register' ? '.login-form' : '.register-form')
      const switcher = document.querySelector('.auth-mode-switch')!
      const incomingAction = incoming.querySelector('button[type="submit"]')!
      const outgoingAction = outgoing?.querySelector('button[type="submit"]')
      samples.push({
        actionTopDelta: outgoingAction ? incomingAction.getBoundingClientRect().top - outgoingAction.getBoundingClientRect().top : 0,
        indicatorX: new DOMMatrixReadOnly(getComputedStyle(switcher, '::before').transform).m41,
        enterX: new DOMMatrixReadOnly(getComputedStyle(incoming).transform).m41,
        leaveX: outgoing ? new DOMMatrixReadOnly(getComputedStyle(outgoing).transform).m41 : null,
        enterTiming: getComputedStyle(incoming).transitionTimingFunction,
        leaveTiming: outgoing ? getComputedStyle(outgoing).transitionTimingFunction : null,
        indicatorTiming: getComputedStyle(switcher, '::before').transitionTimingFunction,
        enterDuration: getComputedStyle(incoming).transitionDuration,
        leaveDuration: outgoing ? getComputedStyle(outgoing).transitionDuration : null,
        indicatorDuration: getComputedStyle(switcher, '::before').transitionDuration,
        pageOverflows: document.documentElement.scrollWidth > innerWidth,
      })
    } while (performance.now() - start < 160)
    return samples
  }, mode)
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
    const forward = await sampleSwitch(page, 'register')
    expect(forward.some(frame => frame.indicatorX > 1 && frame.indicatorX < travel - 1)).toBe(true)
    expect(forward.some(frame => frame.enterX < -20 && frame.leaveX !== null && frame.leaveX > 40)).toBe(true)
    expect(forward[0].enterTiming).toBe('cubic-bezier(0.4, 0, 0.2, 1)')
    expect(forward[0].leaveTiming).toBe(forward[0].enterTiming)
    expect(forward[0].indicatorTiming).toBe(forward[0].enterTiming)
    expect(forward[0].enterDuration).toBe('0.36s')
    expect(forward[0].leaveDuration).toBe(forward[0].enterDuration)
    expect(forward[0].indicatorDuration).toBe(forward[0].enterDuration)
    expect(forward.every(frame => !frame.pageOverflows)).toBe(true)
    expect(forward.every(frame => Math.abs(frame.actionTopDelta) <= 1), '进入注册时两个主按钮应沿同一水平线滑动').toBe(true)
    // 底板尚未到站就反向点击，验证动画能从当前位置返回。
    const reverse = await sampleSwitch(page, 'login')
    expect(reverse[0].indicatorX).toBeGreaterThan(1)
    expect(reverse.some(frame => frame.enterX > 20 && frame.leaveX !== null && frame.leaveX < -40)).toBe(true)
    expect(reverse.every(frame => !frame.pageOverflows)).toBe(true)
    expect(reverse.every(frame => Math.abs(frame.actionTopDelta) <= 1), '返回登录时两个主按钮应沿同一水平线滑动').toBe(true)
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

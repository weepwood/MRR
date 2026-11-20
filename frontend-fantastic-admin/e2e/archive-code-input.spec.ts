import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'

const reply = (data: unknown) => ({ code: 200, message: 'success', data })

/** 1×1 透明 PNG，让预览区有可渲染的影像。 */
const TINY_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

async function mockArchivePage(page: import('@playwright/test').Page) {
  await page.route('**/mock/scan-1.png', route => route.fulfill({ status: 200, contentType: 'image/png', body: TINY_PNG }))
  await page.route('**/api/v1/img/search?**', route => route.fulfill({
    json: reply([{
      id: 93001,
      brxh: 'BR-93001',
      bah: '00009301',
      sjh: '',
      filename: 'synthetic-1.png',
      btype: 3,
      pages: 1,
      uploadFlag: 1,
      img_url: '/mock/scan-1.png',
    }]),
  }))
  await page.route(/\/api\/v1\/search\/patient\/[^/?]+/, route => route.fulfill({
    json: reply({ bah: '00009301', name: '合成患者', department: '合成科室' }),
  }))
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({ json: reply([]) }))
}

/** 输入框里号码的真实渲染宽度与可编辑区宽度，用来判断「有没有被截断」。 */
async function measureInput(page: import('@playwright/test').Page, name: string) {
  return page.evaluate((inputName) => {
    const input = document.querySelector<HTMLInputElement>(`input[name="${inputName}"]`)
    if (!input) {
      return null
    }
    const style = getComputedStyle(input)
    const badge = input.closest('.el-input')?.querySelector('.length-badge')
    const probe = document.createElement('span')
    probe.textContent = input.value
    probe.style.position = 'absolute'
    probe.style.visibility = 'hidden'
    probe.style.whiteSpace = 'pre'
    probe.style.fontSize = style.fontSize
    probe.style.fontFamily = style.fontFamily
    probe.style.fontWeight = style.fontWeight
    probe.style.letterSpacing = style.letterSpacing
    document.body.appendChild(probe)
    const textWidth = probe.getBoundingClientRect().width
    probe.remove()
    return {
      value: input.value,
      clientWidth: input.clientWidth,
      textWidth: Math.round(textWidth * 10) / 10,
      fontSize: Number.parseFloat(style.fontSize),
      badgeText: badge?.textContent?.trim() ?? null,
      badgeFontSize: badge ? Number.parseFloat(getComputedStyle(badge).fontSize) : null,
    }
  }, name)
}

/**
 * 台账里的病案号、上架号都是 8 位，但录入时常省略前导零（00123456 记成 123456）。
 * 失焦后必须补回 8 位：输入框里留下的就是实际会查的号，用户才能逐位核对。
 */
test('病案号与上架号不足 8 位时，失焦后补前导零', async ({ page }) => {
  await mockArchivePage(page)
  await page.goto('/archive?bah=00009301')

  const bah = page.locator('input[name="archive-bah"]')
  const sjh = page.locator('input[name="archive-sjh"]')

  await bah.fill('123456')
  await sjh.fill('9999')
  // 点身份证框让前两个输入框失焦
  await page.locator('input[name="archive-id-card"]').click()

  await expect(bah).toHaveValue('00123456')
  await expect(sjh).toHaveValue('00009999')

  const bahBadge = page.locator('input[name="archive-bah"]').locator('xpath=ancestor::div[contains(@class,"el-input")]').locator('.length-badge')
  await expect(bahBadge).toHaveText('8/8')
})

test('已满 8 位与非数字的历史编号不被改写', async ({ page }) => {
  await mockArchivePage(page)
  await page.goto('/archive?bah=00009301')

  const bah = page.locator('input[name="archive-bah"]')
  const idCard = page.locator('input[name="archive-id-card"]')

  await bah.fill('00123456')
  await idCard.click()
  await expect(bah).toHaveValue('00123456')

  // 历史库里存在 SJH-A1、NULL 这类异常编号，补零逻辑不能碰它们
  await bah.fill('SJH-A1')
  await idCard.click()
  await expect(bah).toHaveValue('SJH-A1')
})

test('查询时按补齐后的 8 位号码请求', async ({ page }) => {
  await mockArchivePage(page)
  await page.goto('/archive?bah=00009301')

  await page.locator('input[name="archive-bah"]').fill('123456')
  await page.locator('.search-card').getByRole('button', { name: '查询', exact: true }).click()

  await expect.poll(() => new URL(page.url()).searchParams.get('bah')).toBe('00123456')
  await expect(page.locator('input[name="archive-bah"]')).toHaveValue('00123456')
})

/**
 * 侧栏最宽 320px，半栏只有 141px —— 字号一调大就最容易出现「18 位身份证末位被吃掉」。
 * 这条用例用真实渲染宽度守住：三种号码在默认宽度下都要完整显示，且字号不能缩回旧值。
 */
test('三种编号在默认侧栏宽度下完整显示，字号与徽标符合排版约定', async ({ page }) => {
  await mockArchivePage(page)
  await page.goto('/archive?bah=00009301')

  await page.locator('input[name="archive-bah"]').fill('00123456')
  await page.locator('input[name="archive-sjh"]').fill('00009999')
  await page.locator('input[name="archive-id-card"]').fill('110101199001011234')
  await page.waitForTimeout(200)

  for (const name of ['archive-bah', 'archive-sjh', 'archive-id-card']) {
    const input = await measureInput(page, name)
    expect(input, `${name} 应存在`).not.toBeNull()
    expect(input!.value.length, `${name} 已填满`).toBeGreaterThanOrEqual(8)
    // 文本比可编辑区还宽就会被截掉末位
    expect(input!.textWidth, `${name} 号码不应溢出（textWidth ≤ clientWidth）`)
      .toBeLessThanOrEqual(input!.clientWidth)
  }

  const bah = await measureInput(page, 'archive-bah')
  const idCard = await measureInput(page, 'archive-id-card')
  // 12px 是收紧前的字号，调大后不应再掉回来
  expect(bah!.fontSize).toBeGreaterThanOrEqual(13)
  expect(idCard!.fontSize).toBeGreaterThanOrEqual(13)
  // 徽标要让位给号码，不能再和号码抢字号
  expect(bah!.badgeFontSize ?? 0).toBeLessThanOrEqual(10)

  // 末位是 X 的 18 位号同样不能被裁
  await page.locator('input[name="archive-id-card"]').fill('11010119900101123X')
  const idCardX = await measureInput(page, 'archive-id-card')
  expect(idCardX!.value).toBe('11010119900101123X')
  expect(idCardX!.textWidth).toBeLessThanOrEqual(idCardX!.clientWidth)
})

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

test('短编号失焦后保留原值，位数徽标只在聚焦时显示', async ({ page }) => {
  await mockArchivePage(page)
  await page.goto('/archive?bah=00009301')

  const bah = page.locator('input[name="archive-bah"]')
  const sjh = page.locator('input[name="archive-sjh"]')

  await bah.fill('123456')
  await sjh.fill('9999')
  await page.locator('input[name="archive-id-card"]').click()

  await expect(bah).toHaveValue('123456')
  await expect(sjh).toHaveValue('9999')

  const bahBadge = bah.locator('xpath=ancestor::div[contains(@class,"el-input")]').locator('.length-badge')
  await expect(bahBadge).toHaveCount(0)
  await bah.click()
  await expect(bahBadge).toHaveText('6/8')
  await sjh.click()
  await expect(bahBadge).toHaveCount(0)
  await expect(sjh.locator('xpath=ancestor::div[contains(@class,"el-input")]').locator('.length-badge')).toHaveText('4/8')
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

test('短上架号和带前导零的上架号各自命中原始病案', async ({ page }) => {
  await mockArchivePage(page)
  const observedShelfCodes: string[] = []
  const archiveByShelfCode = new Map([['1234', '10000020'], ['00001234', '10000021']])
  await page.route('**/api/v1/img/search?**', async (route) => {
    const sjh = new URL(route.request().url()).searchParams.get('sjh') ?? ''
    observedShelfCodes.push(sjh)
    const bah = archiveByShelfCode.get(sjh)
    await route.fulfill({
      json: reply(bah
        ? [{
            id: Number(bah),
            brxh: 'SYNTH-ARCHIVE',
            bah,
            sjh,
            filename: 'synthetic-1.png',
            btype: 3,
            pages: 1,
            uploadFlag: 1,
            img_url: '/mock/scan-1.png',
          }]
        : []),
    })
  })
  await page.goto('/archive')

  const shelf = page.locator('input[name="archive-sjh"]')
  const search = page.locator('.search-card').getByRole('button', { name: '查询', exact: true })
  for (const sjh of archiveByShelfCode.keys()) {
    await shelf.fill(sjh)
    await search.click()
    await expect.poll(() => observedShelfCodes.at(-1)).toBe(sjh)
    await expect.poll(() => new URL(page.url()).searchParams.get('sjh')).toBe(sjh)
    await expect(shelf).toHaveValue(sjh)
    await expect(page.locator('.preview-stage img.preview-image')).toBeVisible()
  }

  await page.goto('/archive?sjh=1234')
  await expect.poll(() => observedShelfCodes.at(-1)).toBe('1234')
  await expect(shelf).toHaveValue('1234')
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
  // 徽标要让位给号码，不能再和号码抢字号（聚焦时才显示，字号 9px）
  await page.locator('input[name="archive-bah"]').focus()
  const bahFocused = await measureInput(page, 'archive-bah')
  expect(bahFocused!.badgeFontSize).not.toBeNull()
  expect(bahFocused!.badgeFontSize!).toBeLessThanOrEqual(10)
  // 未聚焦时徽标不渲染，号码独占 suffix 区
  await page.locator('input[name="archive-id-card"]').focus()
  const bahBlurred = await measureInput(page, 'archive-bah')
  expect(bahBlurred!.badgeText).toBeNull()

  // 末位是 X 的 18 位号同样不能被裁
  await page.locator('input[name="archive-id-card"]').fill('11010119900101123X')
  const idCardX = await measureInput(page, 'archive-id-card')
  expect(idCardX!.value).toBe('11010119900101123X')
  expect(idCardX!.textWidth).toBeLessThanOrEqual(idCardX!.clientWidth)
})

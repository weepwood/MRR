import type { Locator, Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

/**
 * 影像查询页（/idcard，身份证仅在当前页面内存）的键盘可达性。
 *
 * 数据来自 src/mock/scan-img.ts 的合成病案：
 * - 00800001（区间内，14 张，自动选中）、00800002（区间内，6 张）、00900001（区间外）；
 * - 只用了前 4 个类型，按页码轮转：01 类为 P1/P5/P9/P13，02 类为 P2/P6/P10/P14；
 * - 其余 11 个类型计数为 0。
 */
const ID_CARD = '000000200001010001'
const EMPTY_ID_CARD = '000000200001010002'

async function tabTo(page: Page, target: Locator) {
  await expect(target).toBeVisible()
  for (let step = 0; step < 60; step++) {
    if (await target.evaluate(element => element === document.activeElement)) {
      return
    }
    await page.keyboard.press('Tab')
  }
  await expect(target).toBeFocused()
}

function record(page: Page, bah: string) {
  return page.locator('.id-result-item').filter({ hasText: bah })
}

function typeButton(page: Page, label: RegExp) {
  return page.locator('.type-list').getByRole('button', { name: label })
}

async function submitIdCard(page: Page, idCard: string) {
  await page.goto('/idcard')
  await page.getByPlaceholder('请输入18位身份证号').fill(idCard)
  const lookup = page.waitForRequest(request => new URL(request.url()).pathname.endsWith('/api/v1/search/getBAHByID'))
  await page.getByRole('button', { name: '查询病案', exact: true }).click()
  const request = await lookup
  expect(request.method()).toBe('POST')
  expect(new URL(request.url()).search).toBe('')
  expect(request.postDataJSON()).toEqual({ idCard })
  expect(new URL(page.url()).pathname).toBe('/idcard')
  expect(page.url()).not.toContain(idCard)
}

async function openPage(page: Page) {
  await submitIdCard(page, ID_CARD)
  await expect(page.getByText('查询出 3 份住院病案')).toBeVisible()
  // 首个区间内病案自动选中并加载
  await expect(record(page, '00800001')).toHaveAttribute('aria-current', 'true')
  await expect(page.locator('.thumb-item')).toHaveCount(14)
}

test('病案列表：区间内可用键盘切换，区间外不可聚焦也不可选', async ({ page }) => {
  await openPage(page)

  const outOfRange = record(page, '00900001')
  await expect(outOfRange).toHaveAttribute('tabindex', '-1')
  await expect(outOfRange).toHaveAttribute('aria-disabled', 'true')

  const second = record(page, '00800002')
  await expect(second).toHaveAttribute('tabindex', '0')
  await tabTo(page, second)
  await expect(second).toHaveCSS('outline-style', 'solid')
  await page.keyboard.press('Enter')

  await expect(second).toHaveAttribute('aria-current', 'true')
  await expect(record(page, '00800001')).not.toHaveAttribute('aria-current', 'true')
  await expect(page.locator('.thumb-item')).toHaveCount(6)

  // 区间外病案即使被程序聚焦，按键与点击也不切换
  await outOfRange.focus()
  await page.keyboard.press('Enter')
  await outOfRange.click({ force: true })
  await expect(second).toHaveAttribute('aria-current', 'true')
  await expect(outOfRange).not.toHaveAttribute('aria-current', 'true')
})

test('类型筛选：原生按钮可用空格切换，计数为 0 的类型被禁用', async ({ page }) => {
  await openPage(page)

  const all = typeButton(page, /^全部/)
  await expect(all).toHaveAttribute('aria-pressed', 'true')

  const emptyType = typeButton(page, /^15-分娩记录/)
  await expect(emptyType).toBeDisabled()

  const progressNotes = typeButton(page, /^02-病程录/)
  await expect(progressNotes).toBeEnabled()
  await tabTo(page, progressNotes)
  await expect(progressNotes).toHaveCSS('outline-style', 'solid')
  await page.keyboard.press('Space')

  await expect(progressNotes).toHaveAttribute('aria-pressed', 'true')
  await expect(all).toHaveAttribute('aria-pressed', 'false')
  await expect(page.locator('.thumb-item')).toHaveCount(4)
  await expect(page.getByRole('button', { name: '第 2 页 02-病程录', exact: true })).toBeVisible()

  // “全部”在 DOM 中位于当前焦点之前，直接聚焦后用 Enter 触发
  await all.focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('.thumb-item')).toHaveCount(14)
})

for (const mode of ['缩略图', '列表'] as const) {
  test(`${mode}视图：缩略图可用 Tab 聚焦并用 Enter / 空格选中`, async ({ page }) => {
    await openPage(page)
    const modeButton = page.locator('.thumbs-toolbar').getByRole('button', { name: mode, exact: true })
    await modeButton.click()
    await expect(modeButton).toHaveAttribute('aria-pressed', 'true')

    const itemClass = mode === '缩略图' ? '.thumb-item' : '.thumb-row'
    await expect(page.locator(itemClass)).toHaveCount(14)

    const first = page.getByRole('button', { name: '第 1 页 01-病案首页', exact: true })
    const third = page.getByRole('button', { name: '第 3 页 03-手术记录', exact: true })
    const fourth = page.getByRole('button', { name: '第 4 页 04-术后病程录', exact: true })
    await expect(first).toHaveAttribute('aria-current', 'true')

    await tabTo(page, third)
    await expect(third).toHaveCSS('outline-style', 'solid')
    await page.keyboard.press('Enter')
    await expect(third).toHaveAttribute('aria-current', 'true')
    await expect(first).not.toHaveAttribute('aria-current', 'true')

    await tabTo(page, fourth)
    await page.keyboard.press('Space')
    await expect(fourth).toHaveAttribute('aria-current', 'true')
  })
}

test('无病案时显示空态，不渲染可聚焦的病案项', async ({ page }) => {
  await submitIdCard(page, EMPTY_ID_CARD)
  await expect(page.getByText('未查询到相关住院记录')).toBeVisible()
  await expect(page.locator('.id-result-item')).toHaveCount(0)
  await expect(page.getByText('请先选择左侧病案')).toBeVisible()
})

test('旧明文路径不再发起查询，刷新与返回均清除临时查询', async ({ page }) => {
  let lookups = 0
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.includes('/api/v1/search/getBAHByID')) {
      lookups++
    }
  })
  await page.goto(`/idcard/${ID_CARD}?legacy=synthetic`)
  await expect(page.getByPlaceholder('请输入18位身份证号')).toHaveValue('')
  expect(new URL(page.url()).pathname).toBe('/idcard')
  expect(new URL(page.url()).search).toBe('')
  expect(lookups).toBe(0)

  await submitIdCard(page, ID_CARD)
  await expect(page.getByText('查询出 3 份住院病案')).toBeVisible()
  await page.getByRole('button', { name: '返回查询', exact: true }).click()
  await expect(page.getByPlaceholder('请输入18位身份证号')).toHaveValue('')
  await submitIdCard(page, ID_CARD)
  await expect(page.getByText('查询出 3 份住院病案')).toBeVisible()
  await page.reload()
  await expect(page.getByPlaceholder('请输入18位身份证号')).toHaveValue('')
  expect(lookups).toBe(2)
})

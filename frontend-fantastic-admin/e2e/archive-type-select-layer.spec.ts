import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'

const reply = (data: unknown) => ({ code: 200, message: 'success', data })

/** 1×1 透明 PNG，仅用于让预览区渲染出可点击的 `<img>`。 */
const TINY_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

/** 影像档案袋共用的最小 mock：一条带影像地址的扫描记录。 */
async function mockArchivePage(page: import('@playwright/test').Page, scanCount = 1) {
  const scans = Array.from({ length: scanCount }, (_, index) => ({
    id: 93001 + index,
    brxh: `BR-9300${index + 1}`,
    bah: '00009301',
    sjh: '',
    filename: `synthetic-${index + 1}.png`,
    btype: 3,
    pages: index + 1,
    uploadFlag: 1,
    img_url: `/mock/scan-${index + 1}.png`,
  }))
  await page.route('**/mock/scan-*.png', route => route.fulfill({ status: 200, contentType: 'image/png', body: TINY_PNG }))
  await page.route('**/api/v1/img/search?**', route => route.fulfill({ json: reply(scans) }))
  await page.route('**/api/v1/img/updateImageType/**', route => route.fulfill({ json: reply(null) }))
  await page.route(/\/api\/v1\/search\/patient\/[^/?]+/, route => route.fulfill({
    json: reply({ bah: '00009301', name: '合成患者', department: '合成科室' }),
  }))
  await page.route('**/api/v1/archive-search-history**', route => route.fulfill({ json: reply([]) }))
}

/**
 * 影像分类下拉是 teleport 到 body 的 popper，天然拿不到组件里的层叠上下文；
 * 预览工具栏是 `position: fixed; z-index: 3001`，下拉向上展开时后半截会被工具栏盖住、
 * 点不动。这条用例守住「下拉必须浮在工具栏之上且每一项都能命中」。
 */
test('影像分类下拉浮在预览工具栏之上，每一项都可点击', async ({ page }) => {
  await mockArchivePage(page)

  await page.goto('/archive?bah=00009301')
  const select = page.locator('.preview-type-select')
  await expect(select).toBeVisible()
  await select.click()
  await expect(page.locator('.el-select__popper.preview-type-popper')).toBeVisible()

  const layers = await page.evaluate(() => {
    const popper = document.querySelector('.el-select__popper.preview-type-popper')
    const toolbar = document.querySelector('.preview-toolbar')
    if (!popper || !toolbar) {
      return null
    }
    const options = Array.from(popper.querySelectorAll('.el-select-dropdown__item'))
    const wrap = popper.querySelector('.el-select-dropdown__wrap')?.getBoundingClientRect()
    // 只看滚动视口内真正露出来的项：列表可滚动，被裁掉的项命中为空不代表被遮挡
    const visible = options.filter((option) => {
      const rect = option.getBoundingClientRect()
      const center = rect.top + rect.height / 2
      const insideWrap = wrap ? center > wrap.top + 1 && center < wrap.bottom - 1 : true
      return insideWrap && center > 0 && center < window.innerHeight
    })
    // 命中点取左缘 20px，避开 popper 自身箭头所在的底部中点
    const blocked = visible
      .filter((option) => {
        const rect = option.getBoundingClientRect()
        const hit = document.elementFromPoint(rect.left + 20, rect.top + rect.height / 2)
        return !hit || !option.contains(hit)
      })
      .map(option => (option.textContent || '').trim())
    return {
      popperZ: Number(getComputedStyle(popper).zIndex),
      toolbarZ: Number(getComputedStyle(toolbar).zIndex),
      optionCount: options.length,
      checkedCount: visible.length,
      blocked,
    }
  })

  expect(layers).not.toBeNull()
  expect(layers!.optionCount).toBeGreaterThan(1)
  expect(layers!.checkedCount).toBeGreaterThan(1)
  expect(layers!.popperZ).toBeGreaterThan(layers!.toolbarZ)
  expect(layers!.blocked).toEqual([])
})

/**
 * 改分类会先弹「确认分类变更」。这个确认弹窗的 z-index 来自 Element Plus 的自增基数（2000 出头），
 * 而全屏影像查看器是 3000、预览工具栏是 3001——两者都会把弹窗整个盖住，确认按钮点不到。
 * 这条用例守住「确认弹窗必须浮在全屏影像与预览工具栏之上，且遮罩把工具栏一起盖住」。
 */
test('确认分类变更弹窗浮在全屏影像与预览工具栏之上', async ({ page }) => {
  await mockArchivePage(page)

  await page.goto('/archive?bah=00009301')
  await expect(page.locator('.single-stage .fit-image')).toBeVisible()
  await page.locator('.single-stage .fit-image').click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()

  await page.locator('.preview-type-select').click()
  await page.locator('.el-select__popper.preview-type-popper .el-select-dropdown__item', { hasText: '08-检验单' }).click()

  const confirmButton = page.locator('.el-dialog .el-button', { hasText: '确认切换' })
  await expect(confirmButton).toBeVisible()

  const layers = await page.evaluate(() => {
    const dialog = document.querySelector('.el-dialog')
    const overlay = dialog?.closest('.el-overlay')
    const viewer = document.querySelector('.el-image-viewer__wrapper')
    const toolbar = document.querySelector('.preview-toolbar')
    const toolbarRect = toolbar?.getBoundingClientRect()
    const toolbarHit = toolbarRect
      ? document.elementFromPoint(toolbarRect.left + toolbarRect.width / 2, toolbarRect.top + 20)
      : null
    const button = Array.from(document.querySelectorAll('.el-dialog .el-button'))
      .find(item => (item.textContent || '').includes('确认切换'))
    const buttonRect = button?.getBoundingClientRect()
    const buttonHit = buttonRect
      ? document.elementFromPoint(buttonRect.left + buttonRect.width / 2, buttonRect.top + buttonRect.height / 2)
      : null
    return {
      hasDialog: Boolean(dialog),
      overlayZ: Number(overlay ? getComputedStyle(overlay).zIndex : 0),
      viewerZ: Number(viewer ? getComputedStyle(viewer).zIndex : 0),
      toolbarZ: Number(toolbar ? getComputedStyle(toolbar).zIndex : 0),
      toolbarCovered: toolbarHit ? !toolbar?.contains(toolbarHit) : false,
      buttonHittable: Boolean(button && buttonHit && button.contains(buttonHit)),
    }
  })

  expect(layers.hasDialog).toBe(true)
  expect(layers.overlayZ).toBeGreaterThan(layers.viewerZ)
  expect(layers.overlayZ).toBeGreaterThan(layers.toolbarZ)
  expect(layers.toolbarCovered).toBe(true)
  expect(layers.buttonHittable).toBe(true)

  // 真实点击一次：被遮挡时 Playwright 会因命中元素不符直接失败
  await confirmButton.click()
  await expect(page.locator('.el-dialog')).toHaveCount(0)
  await expect(page.locator('.preview-type-select')).toContainText('08-检验单')

  // 成功提示同样要压在全屏影像之上，否则改完分类看不到任何反馈
  const toast = page.locator('.el-message', { hasText: '影像类型已更新' })
  await expect(toast).toBeVisible()
  const readToastLayer = () => page.evaluate(() => {
    const element = document.querySelector('.el-message')
    const viewer = document.querySelector('.el-image-viewer__wrapper')
    if (!element || !viewer) {
      return null
    }
    const rect = element.getBoundingClientRect()
    const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)
    return {
      toastZ: Number(getComputedStyle(element).zIndex),
      viewerZ: Number(getComputedStyle(viewer).zIndex),
      hittable: Boolean(hit && element.contains(hit)),
    }
  })
  // 提示条自上而下入场，动画中途中心点可能还落在视口外。改用轮询等它落位：
  // 固定等待在并发跑（多个 worker 抢 dev server）时会拖到 3 秒后的离场动画，反而假失败。
  await expect.poll(async () => (await readToastLayer())?.hittable ?? false, { timeout: 2500 }).toBe(true)
  const toastLayer = await readToastLayer()
  expect(toastLayer).not.toBeNull()
  expect(toastLayer!.toastZ).toBeGreaterThan(toastLayer!.viewerZ)
})

/** 从 computed 颜色里取 0~1 的亮度，用来判断「亮底深字」还是「深底亮字」。 */
function luminanceOf(color: string) {
  const [r, g, b] = (color.match(/[\d.]+/g) ?? ['0', '0', '0']).slice(0, 3).map(Number)
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
}

/**
 * Element 默认把缩放条、翻页箭头、关闭按钮做成「深灰底 + 白图标」，只在纯黑遮罩上成立；
 * 浅色主题下就像贴了块深色补丁，与页面里其他浮层完全不同语言。
 * 这条用例守住「控件底色与图标都跟随主题」：浅色是亮底深字，暗色是深底亮字。
 */
test('全屏查看的操作控件跟随主题配色，浅色主题下不再是深色块', async ({ page }) => {
  await mockArchivePage(page)

  const readControls = () => page.evaluate(() => {
    const read = (selector: string) => {
      const element = document.querySelector(selector)
      if (!element) {
        return null
      }
      const style = getComputedStyle(element)
      return { background: style.backgroundColor, color: style.color }
    }
    return {
      actions: read('.el-image-viewer__actions'),
      inner: read('.el-image-viewer__actions__inner'),
      close: read('.el-image-viewer__close'),
      progress: read('.viewer-progress'),
    }
  })

  async function openViewerAs(colorScheme: 'light' | 'dark') {
    // addInitScript 在每次导航前按注册顺序执行，后注册的会覆盖前一次写入的主题
    await page.addInitScript((scheme) => {
      localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { colorScheme: scheme } }))
    }, colorScheme)
    await page.goto('/archive?bah=00009301')
    await page.locator('.single-stage .fit-image').click()
    await expect(page.locator('.el-image-viewer__actions')).toBeVisible()
    return page.evaluate(() => document.documentElement.classList.contains('dark'))
  }

  // 页码必须在胶囊内部，且不能再出现 Element 那个独立漂浮的页码节点
  const readProgressLayout = () => page.evaluate(() => {
    const bar = document.querySelector('.el-image-viewer__actions')
    const progress = document.querySelector('.viewer-progress')
    if (!bar || !progress) {
      return null
    }
    const barRect = bar.getBoundingClientRect()
    const progressRect = progress.getBoundingClientRect()
    return {
      insideBar: Boolean(progress.closest('.el-image-viewer__actions')),
      withinBar: progressRect.top >= barRect.top - 1
        && progressRect.bottom <= barRect.bottom + 1
        && progressRect.left >= barRect.left - 1
        && progressRect.right <= barRect.right + 1,
      legacyFloatingNodes: document.querySelectorAll('.el-image-viewer__progress').length,
    }
  })

  expect(await openViewerAs('light')).toBe(false)
  const light = await readControls()
  // 亮底（白色玻璃）+ 深色图标
  expect(luminanceOf(light.actions!.background)).toBeGreaterThan(0.8)
  expect(luminanceOf(light.inner!.color)).toBeLessThan(0.3)
  expect(luminanceOf(light.close!.background)).toBeGreaterThan(0.8)
  expect(luminanceOf(light.progress!.color)).toBeLessThan(0.6)

  const lightLayout = await readProgressLayout()
  expect(lightLayout).not.toBeNull()
  expect(lightLayout!.insideBar).toBe(true)
  expect(lightLayout!.withinBar).toBe(true)
  expect(lightLayout!.legacyFloatingNodes).toBe(0)

  expect(await openViewerAs('dark')).toBe(true)
  const dark = await readControls()
  // 深底 + 亮色图标
  expect(luminanceOf(dark.actions!.background)).toBeLessThan(0.35)
  expect(luminanceOf(dark.inner!.color)).toBeGreaterThan(0.7)
  expect(luminanceOf(dark.close!.background)).toBeLessThan(0.35)
  expect(luminanceOf(dark.progress!.color)).toBeGreaterThan(0.4)

  const darkLayout = await readProgressLayout()
  expect(darkLayout).not.toBeNull()
  expect(darkLayout!.insideBar).toBe(true)
  expect(darkLayout!.withinBar).toBe(true)
  expect(darkLayout!.legacyFloatingNodes).toBe(0)
})

/**
 * 一张足够大的合成影像，用来判断“点图片”和“点空白”的命中差异。
 */
const LARGE_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="620" height="860"><rect width="100%" height="100%" fill="#e8c9c9"/><text x="310" y="430" font-size="72" font-family="sans-serif" text-anchor="middle" fill="#333">P1</text></svg>'

/**
 * 全屏看图的两条体验约束：全屏层把背后页面虚化，图片本身不能被虚化；
 * 点图片不关闭、点图片之外的空白关闭（canvas 层原本铺满视口，会把空白点击全吃掉）。
 */
test('全屏查看：背景虚化、点图片不关闭、点空白关闭', async ({ page }) => {
  await mockArchivePage(page)
  // 后注册的 route 优先，用它换掉 1×1 占位图，保证图片区域有真实尺寸
  await page.route('**/mock/scan-1.png', route => route.fulfill({
    status: 200,
    contentType: 'image/svg+xml',
    body: LARGE_SVG,
  }))

  await page.goto('/archive?bah=00009301')
  await page.locator('.single-stage .fit-image').click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
  await expect(page.locator('.preview-toolbar')).toBeVisible()

  const maskStyle = await page.evaluate(() => {
    const wrapper = document.querySelector('.el-image-viewer__wrapper')
    const mask = document.querySelector('.el-image-viewer__mask')
    if (!wrapper || !mask) {
      return null
    }
    const wrapperStyle = getComputedStyle(wrapper)
    const maskStyle = getComputedStyle(mask)
    return {
      wrapperBackdrop: wrapperStyle.backdropFilter || wrapperStyle.getPropertyValue('backdrop-filter'),
      fadeToken: wrapperStyle.getPropertyValue('--el-transition-duration').trim(),
      maskBackdrop: maskStyle.backdropFilter || maskStyle.getPropertyValue('backdrop-filter'),
      maskOpacity: maskStyle.opacity,
      maskBackground: maskStyle.backgroundColor,
    }
  })
  expect(maskStyle).not.toBeNull()
  // 虚化必须挂在 wrapper 自己身上：mask 在 wrapper 里面，而 wrapper 淡入时 opacity<1
  // 会成为 backdrop root，挂里面的 blur 整段淡入都采不到背景，
  // 表现就是「图片已经全屏了，背景还清晰，动画结束才突然糊掉」。
  expect(maskStyle!.wrapperBackdrop).toContain('blur')
  expect(maskStyle!.maskBackdrop).not.toContain('blur')
  // 淡入要短到和图片一起出现，不能拖到 blur 生效之后
  expect(maskStyle!.fadeToken).toBe('120ms')
  // 遮罩不能再走半透明（opacity<1 会把虚化结果一起稀释掉）
  expect(maskStyle!.maskOpacity).toBe('1')
  expect(maskStyle!.maskBackground).toContain('0.58')

  // 图片仍压在遮罩之上：中心点命中的必须是 img，否则图片会被一起虚化/压暗
  const imageHit = await page.evaluate(() => {
    const image = document.querySelector('.el-image-viewer__img')
    if (!image) {
      return null
    }
    const rect = image.getBoundingClientRect()
    const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)
    return { tagName: hit?.tagName ?? '', className: String(hit?.className ?? '') }
  })
  expect(imageHit).not.toBeNull()
  expect(imageHit!.tagName).toBe('IMG')

  // 点图片本身不关闭
  const imageBox = await page.locator('.el-image-viewer__img').boundingBox()
  expect(imageBox).not.toBeNull()
  await page.mouse.click(imageBox!.x + imageBox!.width / 2, imageBox!.y + imageBox!.height / 2)
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()

  // 点图片之外的空白关闭（右侧预览面板的工具条是常驻的，只退全屏层）
  await page.mouse.click(80, 200)
  await expect(page.locator('.el-image-viewer__wrapper')).toHaveCount(0)
})

/**
 * 单页预览的影像必须按原始比例显示。曾经的实现给 fit-height 写死 `height: 100%`，
 * 横版图被 max-width 夹窄后高度不回缩，1600×900 被压成 802×834（纵向拉长约 85%）。
 * 这条用例覆盖「横版 / 竖版 × 适应高度 / 适应宽度」四种组合。
 */
function aspectSvg(width: number, height: number) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="#dfe7f3"/><rect x="4" y="4" width="${width - 8}" height="${height - 8}" fill="none" stroke="#3b6ea5" stroke-width="6"/></svg>`
}

async function measurePreviewImage(page: import('@playwright/test').Page) {
  return page.evaluate(() => {
    const image = document.querySelector('.single-stage .fit-image') as HTMLImageElement | null
    const stage = document.querySelector('.single-stage')
    if (!image || !stage) {
      return null
    }
    const rect = image.getBoundingClientRect()
    const stageRect = stage.getBoundingClientRect()
    const style = getComputedStyle(image)
    const stageStyle = getComputedStyle(stage)
    return {
      naturalRatio: image.naturalWidth / image.naturalHeight,
      boxRatio: rect.width / rect.height,
      objectFit: style.objectFit,
      boxWidth: rect.width,
      boxHeight: rect.height,
      innerWidth: stageRect.width - Number.parseFloat(stageStyle.paddingLeft) - Number.parseFloat(stageStyle.paddingRight),
      innerHeight: stageRect.height - Number.parseFloat(stageStyle.paddingTop) - Number.parseFloat(stageStyle.paddingBottom),
    }
  })
}

test('单页预览的影像按原始比例显示，横版不被压扁、竖版不被拉长', async ({ page }) => {
  await mockArchivePage(page)
  let natural = { width: 1600, height: 900 }
  await page.route('**/mock/scan-1.png', route => route.fulfill({
    status: 200,
    contentType: 'image/svg+xml',
    body: aspectSvg(natural.width, natural.height),
  }))

  await page.goto('/archive?bah=00009301')

  for (const fitMode of ['height', 'width']) {
    for (const size of [{ width: 1600, height: 900 }, { width: 900, height: 1600 }]) {
      natural = size
      await page.evaluate((mode) => {
        localStorage.setItem('MRR-ADMIN:archive-local-preferences', JSON.stringify({ archiveFitMode: mode }))
      }, fitMode)
      await page.reload()
      await expect(page.locator('.single-stage .fit-image')).toBeVisible()
      // 等图片解码完，naturalWidth 才有真实值
      await expect
        .poll(async () => (await measurePreviewImage(page))?.naturalRatio ?? 0)
        .toBeCloseTo(size.width / size.height, 2)

      const measured = await measurePreviewImage(page)
      expect(measured).not.toBeNull()
      // 要么尺寸盒比例就是原图比例，要么由 object-fit: contain 兜住画面比例，二者至少满足一个
      const boxMatchesNatural = Math.abs(measured!.boxRatio - measured!.naturalRatio) / measured!.naturalRatio < 0.02
      expect(measured!.objectFit === 'contain' || boxMatchesNatural).toBe(true)
      // 图片完整落在容器内，不因等比而被裁掉
      expect(measured!.boxWidth).toBeLessThanOrEqual(measured!.innerWidth + 1)
      expect(measured!.boxHeight).toBeLessThanOrEqual(measured!.innerHeight + 1)
    }
  }
})

/**
 * 全景平铺模式没有右侧预览面板，点开大图后原先只剩 Element 自带的进度条，
 * 改不了分类、翻不了页、也选不中。这条用例守住「平铺全屏里同样挂着分类 + 翻页 + 选中工具条，
 * 且工具条压在全屏影像之上可点」。
 */
test('平铺模式下的全屏查看同样带分类切换、翻页与选中工具条', async ({ page }) => {
  await mockArchivePage(page, 2)
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:archive-local-preferences', JSON.stringify({ archiveLayoutMode: 'wall' }))
  })

  await page.goto('/archive?bah=00009301')
  await expect(page.locator('.archive-wall')).toBeVisible()

  await page.locator('.archive-wall-image-stage').first().click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()

  // 工具条整体压在全屏影像之上，分类下拉、翻页、选中都可命中
  const toolbar = page.locator('.preview-toolbar')
  await expect(toolbar).toBeVisible()
  await expect(toolbar.locator('.preview-type-select')).toBeVisible()
  const toolbarLayer = await page.evaluate(() => {
    const bar = document.querySelector('.preview-toolbar')
    const viewer = document.querySelector('.el-image-viewer__wrapper')
    if (!bar || !viewer) {
      return null
    }
    const rect = bar.getBoundingClientRect()
    const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)
    return {
      toolbarZ: Number(getComputedStyle(bar).zIndex),
      viewerZ: Number(getComputedStyle(viewer).zIndex),
      hittable: Boolean(hit && bar.contains(hit)),
    }
  })
  expect(toolbarLayer).not.toBeNull()
  expect(toolbarLayer!.toolbarZ).toBeGreaterThan(toolbarLayer!.viewerZ)
  expect(toolbarLayer!.hittable).toBe(true)

  // 平铺模式与右侧预览面板共用同一份底部工具条：页码在胶囊内部，跟着当前影像走
  const viewerProgress = page.locator('.el-image-viewer__actions .viewer-progress')
  await expect(viewerProgress).toHaveText('1 / 2')

  // 翻页：键盘方向键交给全屏查看器，一次只走一张（页面级 keydown 不能重复处理）
  const firstCard = page.locator('.archive-wall-card').nth(0)
  const secondCard = page.locator('.archive-wall-card').nth(1)
  await expect(firstCard).toHaveClass(/active/)
  await page.keyboard.press('ArrowRight')
  await expect(secondCard).toHaveClass(/active/)
  await page.keyboard.press('ArrowLeft')
  await expect(firstCard).toHaveClass(/active/)

  // 工具条上的翻页按钮同样驱动全屏查看器与卡片选中态
  await toolbar.getByRole('button', { name: '下一张影像' }).click()
  await expect(secondCard).toHaveClass(/active/)
  await expect(viewerProgress).toHaveText('2 / 2')
  await expect(toolbar.getByRole('button', { name: '下一张影像' })).toBeDisabled()

  // 选中：工具条上的「P2 选中」直接操作当前查看的这张
  const selectButton = toolbar.locator('.preview-controls .el-button').last()
  await expect(selectButton).toContainText('P2 选中')
  await selectButton.click()
  await expect(secondCard).toHaveClass(/checked/)
  await expect(selectButton).toContainText('已选')

  // 分类下拉：teleport 到 body 后仍要浮在工具条之上，选中项能点
  await toolbar.locator('.preview-type-select').click()
  const popper = page.locator('.el-select__popper.preview-type-popper')
  await expect(popper).toBeVisible()
  const popperZ = await page.evaluate(() => Number(getComputedStyle(document.querySelector('.el-select__popper.preview-type-popper')!).zIndex))
  expect(popperZ).toBeGreaterThan(toolbarLayer!.toolbarZ)
  await popper.locator('.el-select-dropdown__item', { hasText: '08-检验单' }).click()

  const confirmButton = page.locator('.el-dialog .el-button', { hasText: '确认切换' })
  await expect(confirmButton).toBeVisible()
  await confirmButton.click()
  await expect(toolbar.locator('.preview-type-select')).toContainText('08-检验单')
})

test('平铺模式隐藏页码和分类后仍可勾选影像', async ({ page }) => {
  await mockArchivePage(page)
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:archive-local-preferences', JSON.stringify({
      archiveLayoutMode: 'wall',
      archiveWallShowMeta: false,
    }))
  })
  await page.goto('/archive?bah=00009301')

  const card = page.locator('.archive-wall-card').first()
  await expect(card.locator('.archive-wall-meta strong, .archive-wall-meta > span')).toHaveCount(0)
  await card.getByRole('button', { name: '选择第 1 张影像', exact: true }).click({ timeout: 3000 })
  await expect(card.getByRole('button', { name: '取消选择第 1 张影像', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('全屏分类确认期间切换影像时取消本次修改', async ({ page }) => {
  await mockArchivePage(page, 2)
  await page.addInitScript(() => {
    localStorage.setItem('MRR-ADMIN:archive-local-preferences', JSON.stringify({ archiveLayoutMode: 'wall' }))
  })
  const updates: { path: string, btype: number }[] = []
  await page.route('**/api/v1/img/updateImageType/**', async (route) => {
    updates.push({
      path: new URL(route.request().url()).pathname,
      btype: route.request().postDataJSON().btype,
    })
    await route.fulfill({ json: reply(null) })
  })
  await page.goto('/archive?bah=00009301')

  await page.locator('.archive-wall-image-stage').first().click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
  await page.locator('.preview-type-select').click()
  await page.locator('.el-select__popper.preview-type-popper .el-select-dropdown__item', { hasText: '08-检验单' }).click()

  const confirmButton = page.getByRole('button', { name: '确认切换' })
  await expect(confirmButton).toBeVisible()
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('.viewer-progress')).toHaveText('2 / 2')
  await confirmButton.click()

  await expect(page.locator('.el-message')).toBeVisible()
  expect(updates).toEqual([])
  await expect(page.locator('.el-message')).toContainText('当前影像已切换，请重新确认分类')
  await expect(page.locator('.preview-type-select')).toContainText('03-手术记录')
})

import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

const cases = [
  { route: 'users', key: '申请信息', fixed: { 序号: 64, 用户: 240, 角色: 96, 凭据状态: 152, 账号状态: 96, 最后登录: 176, 操作: 96 }, elastic: { 申请信息: 240 } },
  { route: 'records', key: '文件名', fixed: { 病案号: 128, 病人序号: 112, 上架号: 120, 页数: 80, 迁移状态: 112 }, elastic: { 文件名: 240, 目录: 140 } },
  { route: 'patients', key: '身份证号', fixed: { 病案号: 132, 姓名: 112, 身份证号: 220, 床位: 90, 入院日期: 120, 入院时间: 176, ID: 108 }, elastic: { 科室: 140, 病区: 130 } },
  { route: 'logs', key: '真实请求 URI', fixed: { '访问时间': 176, 'Request ID': 200, '用户': 132, '客户端 IP': 148, '方法': 92, '状态码': 92 }, elastic: { '真实请求 URI': 280 } },
  { route: 'audit-images', key: '访问类型', fixed: { '访问时间': 176, '用户': 132, '病案号': 128, '上架号': 128, '身份证号': 190, '客户端 IP': 148, '状态码': 92 }, elastic: { 访问类型: 160 } },
  { route: 'archive-boxes', key: '实际箱号', fixed: { 病案号: 124, 上架号: 124, 实际箱号: 144, 状态: 140, 原计划箱号: 144, 更新时间: 176 }, elastic: { 备注: 220 } },
  { route: 'ocr-batches', key: '待审核建议', fixed: { 状态: 124, 已识别: 88, 失败: 80, 自动归类: 96, 待审核建议: 112, 续跑起点: 120 }, elastic: { 批次: 200, 进度: 170, 等待原因: 150 } },
  { route: 'statistics', key: '设备 ID（CID）', fixed: { 页数: 88 }, elastic: { '病案号': 136, '上架号': 136, '日期': 132, '类型': 120, '操作员': 112, '设备 ID（CID）': 150 } },
  { route: 'runtime-errors', key: '错误摘要', fixed: { '最后发生': 176, '错误编号': 184, '模块': 132, 'Request ID': 176 }, elastic: { 错误摘要: 300 } },
  { route: 'oss-migration', key: '下次可重试', fixed: { 病案号: 128, 上架号: 128 }, elastic: { 文件名: 180 } },
  { route: 'oss-migration', key: 'MD5', fixed: { MD5: 250 }, elastic: { 迁移来源: 200, 错误信息: 280 } },
  { route: 'response-analysis', key: '接口模板', fixed: { '方法': 84, '请求数': 92, '错误率': 96, '客户端平均': 116, '客户端 P95': 116, '服务端平均': 116 }, elastic: { 接口模板: 220 } },
]

for (const [palette, theme] of [['mrr', 'light'], ['gr8r', 'dark']] as const) {
  test(`${palette}/${theme} 业务表格短列稳定、长列分配余量及窄屏内滚动`, async ({ page }) => {
    test.setTimeout(180_000)
    await isolateLocalVisualSettings(page)
    await page.addInitScript(({ palette, theme }) => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({
      app: { colorScheme: theme, themePalette: palette, enableMotion: false, pageTitleStyle: 'card' },
      menu: { mode: 'single' },
      layout: { enableMobileAdaptation: true },
    })), { palette, theme })
    for (const entry of cases) {
      await page.setViewportSize({ width: 1920, height: 900 })
      await page.goto(`/${entry.route}`)
      const table = page.locator('.el-table:visible').filter({ has: page.getByRole('columnheader', { name: entry.key, exact: true }) }).first()
      await expect(table).toBeVisible()
      for (const width of [1920, 1280, 375]) {
        await page.setViewportSize({ width, height: 900 })
        await expect.poll(() => page.evaluate(() => document.body.dataset.mode)).toBe(width < 1024 ? 'mobile' : 'pc')
        await expect.poll(() => table.evaluate((el, spec) => {
          const headers = [...el.querySelectorAll('.el-table__header thead tr:first-child th:not(.gutter)')]
          const sizes = Object.fromEntries(headers.map(h => [h.textContent!.trim(), h.getBoundingClientRect().width]))
          const invalid = Object.entries(spec.fixed).filter(([name, w]) => Math.abs((sizes[name] ?? -1000) - w) > 1)
          const tooSmall = Object.entries(spec.elastic).filter(([name, w]) => (sizes[name] ?? -1000) < w - 1)
          return { invalid, tooSmall, contained: document.documentElement.scrollWidth <= innerWidth + 1 }
        }, { fixed: entry.fixed as Record<string, number>, elastic: entry.elastic as Record<string, number> }), { message: `${entry.route}/${entry.key}/${width}` }).toEqual({ invalid: [], tooSmall: [], contained: true })
        if (entry.route === 'users') {
          await expect.poll(() => table.evaluate(el => [...el.querySelectorAll('td.user-index-column .cell')].every((cell) => {
            const style = getComputedStyle(cell)
            return style.paddingLeft === style.paddingRight && style.textAlign === 'center'
          }))).toBe(true)
          await expect.poll(() => table.evaluate(el => [...el.querySelectorAll('.mrr-status-tag')].every((tag) => {
            const cell = tag.closest('.cell')!
            return tag.getBoundingClientRect().width <= cell.clientWidth - Number.parseFloat(getComputedStyle(cell).paddingLeft) - Number.parseFloat(getComputedStyle(cell).paddingRight) + 1
          }))).toBe(true)
        }
        if (width === 375) {
          const scroll = table.locator('.el-scrollbar__wrap').first()
          await expect.poll(() => scroll.evaluate(el => el.scrollWidth > el.clientWidth)).toBe(true)
          await scroll.evaluate((el) => { el.scrollLeft = el.scrollWidth })
          await expect.poll(() => scroll.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
        }
      }
    }
  })
}

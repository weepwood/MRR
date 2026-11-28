import { expect, test } from '@playwright/test'
import { isolateLocalVisualSettings } from './helpers/local-visual-settings'

test('默认功能分区及单浮层编辑返回', async ({ page }, info) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: true, colorScheme: 'light' }, layout: { enableMobileAdaptation: true } })))
  await page.goto('/')
  const card = (id: string) => page.locator(`[data-widget-id="${id}"]`)
  for (const [id, color] of [['archive', 'blue'], ['records', 'blue'], ['statistics', 'teal'], ['statistics-detail', 'teal'], ['monitoring', 'amber'], ['audit-images', 'slate']]) {
    await expect(card(id)).toHaveClass(new RegExp(`dashboard-widget--color-${color}`))
  }
  await expect.poll(() => page.locator('.dashboard-grid > article').evaluateAll(els => els.map(el => (el as HTMLElement).dataset.widgetId))).toEqual(['archive', 'records', 'statistics', 'statistics-detail', 'monitoring', 'audit-images'])
  await page.screenshot({ path: info.outputPath('default-groups.png'), fullPage: true })
  await page.locator('.dashboard-header').hover()
  await page.getByRole('button', { name: '管理模块' }).click()
  const manager = page.getByRole('dialog', { name: '管理小组件' })
  const editor = page.getByRole('dialog', { name: '编辑小组件' })
  const trigger = manager.locator('.widget-manager-item').filter({ hasText: '影像档案袋' }).getByRole('button', { name: '编辑' })
  const overlays = page.locator('.el-overlay:visible')
  for (const close of ['取消', 'Escape', 'close', '保存']) {
    await trigger.click()
    await expect(editor).toBeVisible()
    await expect(overlays).toHaveCount(1)
    const before = (await editor.boundingBox())!
    if (close === 'Escape') {
      await page.keyboard.press('Escape')
    }
    else if (close === 'close') {
      await editor.locator('.el-dialog__headerbtn').click()
    }
    else { await editor.getByRole('button', { name: close, exact: true }).click() }
    await expect(manager).toBeVisible()
    await expect(editor).toHaveCount(0)
    await expect(overlays).toHaveCount(1)
    await expect(trigger).toBeFocused()
    const after = (await manager.boundingBox())!
    expect(Math.abs(after.width - before.width)).toBeLessThanOrEqual(1)
    expect(Math.abs(after.height - before.height)).toBeLessThanOrEqual(2)
  }
  await manager.getByRole('button', { name: '完成', exact: true }).click()
  await expect(overlays).toHaveCount(0)
})

test('旧布局保留且仅应用分区配色', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.goto('/')
  await expect(page.locator('[data-widget-id="archive"]')).toBeVisible()
  await page.locator('.dashboard-header').hover()
  await page.getByRole('button', { name: '管理模块' }).click()
  const firstManager = page.getByRole('dialog', { name: '管理小组件' })
  await firstManager.locator('.widget-manager-item').filter({ hasText: '影像档案袋' }).getByRole('button', { name: '编辑' }).click()
  await page.getByRole('dialog', { name: '编辑小组件' }).getByRole('button', { name: '保存', exact: true }).click()
  await firstManager.getByRole('button', { name: '完成', exact: true }).click()
  await page.evaluate(() => {
    const key = Object.keys(localStorage).find(k => k.startsWith('MRR-ADMIN:dashboard-widgets:'))
    if (!key) { throw new Error('missing active preference key') }
    localStorage.setItem(key, JSON.stringify([{ id: 'archive', title: '自定义档案', size: 'wide', color: 'pink', backgroundColor: 'slate', pinned: true, visible: true, order: 0 }, { id: 'records', size: 'small', color: 'default', pinned: false, visible: false, order: 1 }]))
  })
  await page.reload()
  await expect(page.locator('[data-widget-id="archive"]')).toHaveClass(/dashboard-widget--color-slate/)
  await expect(page.locator('[data-widget-id="archive"]')).toContainText('自定义档案')
  await expect(page.locator('[data-widget-id="records"]')).toHaveCount(0)
  await page.locator('.dashboard-header').hover()
  await page.getByRole('button', { name: '管理模块' }).click()
  const manager = page.getByRole('dialog', { name: '管理小组件' })
  await manager.getByRole('button', { name: '应用功能配色' }).click()
  await page.getByRole('button', { name: '应用配色', exact: true }).click()
  await expect(page.locator('[data-widget-id="archive"]')).toHaveClass(/dashboard-widget--color-blue/)
  await expect(manager).toBeVisible()
  await expect(page.locator('[data-widget-id="archive"]')).toContainText('自定义档案')
  await expect(page.locator('[data-widget-id="records"]')).toHaveCount(0)
})

test('直接编辑关闭时保留编辑内容直至遮罩退出', async ({ page }) => {
  await isolateLocalVisualSettings(page)
  await page.addInitScript(() => localStorage.setItem('MRR-ADMIN:app-settings', JSON.stringify({ app: { enableMotion: true } })))
  await page.goto('/')
  await page.locator('.dashboard-header').hover()
  await page.getByRole('button', { name: '调整布局' }).click()
  await page.locator('[data-widget-id="archive"]').getByRole('button', { name: '小组件设置' }).click()
  await page.getByRole('menuitem', { name: '编辑设置' }).click()
  const editor = page.getByRole('dialog', { name: '编辑小组件' })
  await expect(editor).toBeVisible()
  const trace = await editor.evaluate(async (el) => {
    const frames = []
    const shell = el.matches('.el-dialog') ? el as HTMLElement : el.querySelector<HTMLElement>('.el-dialog')!
    const button = [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === '取消')!
    button.click()
    for (let i = 0; i < 25; i++) {
      await new Promise(requestAnimationFrame)
      if (shell.offsetWidth) { frames.push({ title: shell.querySelector('.el-dialog__title')?.textContent?.trim(), width: shell.offsetWidth }) }
    }
    return frames
  })
  expect(trace.length).toBeGreaterThan(0)
  expect(trace.every(f => f.title === '编辑小组件' && Math.abs(f.width - 540) <= 1), JSON.stringify(trace)).toBe(true)
  await expect(editor).toBeHidden()
  await expect(page.getByRole('dialog', { name: '管理小组件' })).toHaveCount(0)
})

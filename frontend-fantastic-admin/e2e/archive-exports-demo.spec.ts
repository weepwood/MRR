import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import JSZip from 'jszip'

test('演示档案实际下载整份ZIP、选页ZIP与PDF，文件可独立解析', async ({ page }) => {
  await page.goto('/archive?bah=00800001')
  const actions = page.locator('.archive-bottom-actions')
  await expect(actions.getByRole('button', { name: /下载病案/ })).toBeEnabled({ timeout: 20000 })
  const whole = page.waitForEvent('download')
  await actions.getByRole('button', { name: /下载病案/ }).click()
  const wholeZip = await JSZip.loadAsync(await readFile((await (await whole).path())!))
  expect(Object.keys(wholeZip.files)).toHaveLength(15)
  expect(await wholeZip.file('image-91001.svg')?.async('string')).toContain('合成影像 · 非真实病案')

  await page.locator('.thumb-check').first().click()
  const selection = page.waitForEvent('download')
  await actions.getByRole('button', { name: /导出选中 ZIP（1）/ }).click()
  const zip = await JSZip.loadAsync(await readFile((await (await selection).path())!))
  expect(Object.keys(zip.files)).toHaveLength(2)

  const pdf = page.waitForEvent('download', { timeout: 12000 })
  await actions.getByRole('button', { name: /导出选中 PDF（1）/ }).click()
  const result = await pdf.catch(async (error) => { throw new Error(`${error.message}; ${await page.locator('.el-message').allTextContents()}`) })
  const bytes = await readFile((await result.path())!)
  expect(bytes.subarray(0, 8).toString()).toBe('%PDF-1.4')
  expect(bytes.toString('latin1')).toContain('/Type /Pages /Count 1')
  expect(bytes.toString('latin1')).toContain('/Filter /DCTDecode')
  expect(bytes.toString('latin1')).toMatch(/%%EOF\s*$/)
})

import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'

test('数据交换模板及当前患者合成CSV实际下载', async ({ page }) => {
  await page.goto('/data-exchange?dataset=patients')
  const template = page.getByRole('button', { name: '下载模板', exact: true })
  await expect(template).toBeVisible()
  const [templateDownload] = await Promise.all([page.waitForEvent('download'), template.click()])
  expect(templateDownload.suggestedFilename()).toBe('mrr-demo-patients-template.csv')
  expect(await readFile((await templateDownload.path())!, 'utf8')).toBe('\uFEFFbah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\r\n')
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '导出 CSV', exact: true }).click()])
  const csv = await readFile((await download.path())!, 'utf8')
  expect(csv).toContain('26070000,演示患者甲,,')
  expect(csv.split('\r\n')).toHaveLength(34)
  expect(csv).not.toContain('00000020000101')
})

test('审计页面导出真实合成明细而非JSON成功消息', async ({ page }) => {
  await page.goto('/audit-images')
  const button = page.getByRole('button', { name: '导出当前明细', exact: true })
  await expect(button).toBeEnabled()
  const [download] = await Promise.all([page.waitForEvent('download'), button.click()])
  expect(download.suggestedFilename()).toBe('mrr-demo-image-audit.csv')
  const csv = await readFile((await download.path())!, 'utf8')
  expect(csv).toContain('id,username,auditAction,auditTarget,responseStatus,accessTime,executeTime')
  expect(csv.split('\r\n')).toHaveLength(74)
})

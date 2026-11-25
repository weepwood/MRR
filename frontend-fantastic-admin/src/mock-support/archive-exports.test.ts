import JSZip from 'jszip'
import { describe, expect, it } from 'vitest'
import { createDemoArchiveExports } from './archive-exports'
import { createDemoRecords } from './records'

function data<T extends { code: number }>(result: T) {
  expect(result.code).toBe(200)
  if (!('data' in result)) { throw new Error('Missing data') }
  return result.data as Extract<T, { data: unknown }>['data']
}

describe('演示影像导出', () => {
  it('精确选页、历史档案及无效或跨档案选择', () => {
    const api = createDemoArchiveExports(createDemoRecords())
    expect(data(api.plan('ZIP', { bah: '00800001' })).selectedCount).toBe(14)
    expect(data(api.plan('PDF', { ids: ['91001', '91002'] })).executionMode).toBe('CLIENT_PDF')
    expect(api.plan('PDF', { ids: [1, 3] }).code).toBe(400)
    expect(api.plan('PDF', { ids: [1, 2] }).code).toBe(400)
    expect(api.plan('PDF', { ids: [16] }).code).toBe(400)
    expect(api.plan('PDF', { ids: [1, 1] }).code).toBe(400)
    expect(api.plan('ZIP', { bah: '26070000' }).code).toBe(400)
    expect(data(api.plan('ZIP', { bah: '26070000', sjh: '90000001' })).selectedCount).toBe(1)
    expect(data(api.pdfImages({ ids: [91001] })).imageUrls[0]).toContain('data:image/svg+xml')
    expect(api.create({ format: 'PDF', ids: [91001] }).code).toBe(503)
  })

  it('真实压缩包生成后才能标记成功；文件与任务长度、选页、区间一致', async () => {
    const api = createDemoArchiveExports(createDemoRecords())
    const params = { format: 'ZIP', ids: ['91002', '91001'], idempotencyKey: 'demo-test' }
    const task = data(api.create(params))
    expect(task.status).toBe('PENDING')
    expect(api.download(task.id).code).toBe(409)
    expect(data(api.create(params)).id).toBe(task.id)
    expect(api.create({ ...params, ids: ['91003'] }).code).toBe(409)
    expect(data(api.list({ format: 'ZIP' }))).toHaveLength(1)
    const completed = data(await api.get(task.id))
    expect(completed.status).toBe('SUCCESS')
    expect(completed.processedCount).toBe(2)
    const payload = data(api.download(task.id))
    const bytes = Uint8Array.from(atob(payload.base64), c => c.charCodeAt(0))
    expect(bytes.length).toBe(completed.outputBytes)
    const archive = await JSZip.loadAsync(bytes)
    expect(Object.keys(archive.files).sort()).toEqual(['README.txt', 'image-91001.svg', 'image-91002.svg'])
    expect(await archive.file('image-91001.svg')?.async('string')).toContain('合成影像 · 非真实病案')
    expect(await archive.file('image-91002.svg')?.async('string')).toContain('P2')
    const partial = data(api.download(task.id, 'bytes=1-9'))
    expect(Uint8Array.from(atob(partial.base64), c => c.charCodeAt(0))).toEqual(bytes.slice(1, 10))
    expect(api.download(task.id, 'bytes=1-9999999').code).toBe(416)
    expect(data(api.list({ format: 'ZIP' }))).toHaveLength(0)
  })
  it('取消待执行任务不生成文件，直接下载包含实际合成内容', async () => {
    const api = createDemoArchiveExports(createDemoRecords())
    const task = data(api.create({ format: 'ZIP', ids: [91001] }))
    expect(data(api.cancel(task.id)).status).toBe('CANCELLED')
    expect(data(await api.get(task.id)).status).toBe('CANCELLED')
    expect(api.download(task.id).code).toBe(409)
    const payload = data(await api.directZip({ bah: '00800001' }))
    const archive = await JSZip.loadAsync(payload.base64, { base64: true })
    expect(Object.keys(archive.files)).toHaveLength(15)
  })
})

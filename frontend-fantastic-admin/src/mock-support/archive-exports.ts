import type { createDemoRecords } from './records'
import type { ArchiveExportJob, ArchiveExportPlan } from '@/api/modules/archive-export'
import JSZip from 'jszip'
import { getMedicalRecordTypeLabel } from '@/constants/medical-record-types'
import { requiresSjhForBah, toMedicalRecordCodeSearchTerm } from '@/utils/medical-record-code'
import { archiveImages } from './archive-images'
import { pageSvg } from './image'
import { demoError, demoOk } from './response'

interface Image { id: number, bah: string, sjh: string, url: string }
type Params = Record<string, unknown>
interface Stored { job: ArchiveExportJob, images: Image[], bytes?: Uint8Array, processing?: Promise<void>, key: string, signature: string }

const MAX_IMAGES = 50
const MAX_DEMO_BYTES = 2 * 1024 * 1024
const MAX_JOBS = 12
const TTL = 15 * 60_000
const prefix = 'data:image/svg+xml;charset=utf-8,'

export function createDemoArchiveExports(records: ReturnType<typeof createDemoRecords>) {
  const jobs = new Map<string, Stored>()
  let sequence = 0

  function allImages(): Image[] {
    return [
      ...records.scans.filter(row => row.uploadFlag !== 0).map(row => ({
        id: row.id,
        bah: row.bah,
        sjh: row.sjh,
        url: pageSvg(row.bah, row.id, getMedicalRecordTypeLabel(row.btype)),
      })),
      ...archiveImages.map(row => ({ id: row.id, bah: row.bah, sjh: row.sjh, url: row.img_url })),
    ]
  }

  function select(params: Params): Image[] | undefined {
    const bah = toMedicalRecordCodeSearchTerm(params.bah)
    const sjh = toMedicalRecordCodeSearchTerm(params.sjh)
    const all = allImages()
    if (params.ids !== undefined) {
      if (!Array.isArray(params.ids) || !params.ids.length || params.ids.length > MAX_IMAGES) { return }
      const ids = params.ids.map(String)
      if (new Set(ids).size !== ids.length || ids.some(id => !/^[1-9]\d{0,8}$/.test(id))) { return }
      const found = ids.map(id => all.find(row => String(row.id) === id))
      if (found.some(row => !row)) { return }
      const selected = found as Image[]
      if (selected.some(row => row.bah !== selected[0].bah || (bah && toMedicalRecordCodeSearchTerm(row.bah) !== bah) || (sjh && toMedicalRecordCodeSearchTerm(row.sjh) !== sjh))) { return }
      if (requiresSjhForBah(selected[0].bah) && selected.some(row => !row.sjh || row.sjh !== selected[0].sjh)) { return }
      return selected
    }
    if ((!bah && !sjh) || (requiresSjhForBah(bah) && !sjh)) { return }
    const selected = all.filter(row => (!bah || toMedicalRecordCodeSearchTerm(row.bah) === bah) && (!sjh || toMedicalRecordCodeSearchTerm(row.sjh) === sjh))
    if (!selected.length || selected.length > MAX_IMAGES || selected.some(row => row.bah !== selected[0].bah)) { return }
    return selected
  }

  function plan(format: 'ZIP' | 'PDF', params: Params, countOnly = false) {
    const images = select(params)
    if (!images) { return demoError(400, '未找到有效合成影像，或选择超出演示范围') }
    const count = countOnly ? Number(params.selectedCount) : images.length
    if (!Number.isInteger(count) || count < 1 || count > images.length) { return demoError(400, '无效选页数量') }
    return demoOk<ArchiveExportPlan>({
      format,
      executionMode: format === 'ZIP' ? 'BACKEND_JOB' : 'CLIENT_PDF',
      selectedCount: count,
      totalCount: allImages().filter(row => row.bah === images[0].bah).length,
      clientPdfMaxImages: MAX_IMAGES,
      wholeArchive: params.ids === undefined && count === images.length,
      estimatedBytes: images.slice(0, count).reduce((n, image) => n + new TextEncoder().encode(decodeURIComponent(image.url.slice(prefix.length))).length, 0),
      sourceSummary: ['合成演示影像（SVG）'],
    })
  }

  async function zip(images: Image[]) {
    const archive = new JSZip()
    archive.file('README.txt', 'MRR synthetic demo only. No real medical records.\n')
    for (const row of images) {
      if (!row.url.startsWith(prefix)) { throw new Error('Unexpected demo image') }
      archive.file(`image-${row.id}.svg`, decodeURIComponent(row.url.slice(prefix.length)), { date: new Date('2026-01-01T00:00:00Z') })
    }
    const bytes = await archive.generateAsync({ type: 'uint8array', compression: 'DEFLATE' })
    if (bytes.byteLength > MAX_DEMO_BYTES) { throw new Error('Demo ZIP exceeds budget') }
    return bytes
  }

  function expire() {
    for (const [id, stored] of jobs) {
      if (Date.parse(stored.job.expiresAt!) <= Date.now()) {
        jobs.delete(id)
      }
    }
  }

  function create(params: Params) {
    expire()
    if (params.format !== 'ZIP') { return demoError(503, '演示 PDF 使用浏览器生成，不提供服务端 PDF 任务') }
    const images = select(params)
    if (!images) { return demoError(400, '无有效合成影像') }
    const key = String(params.idempotencyKey ?? '')
    if (key.length > 128) { return demoError(400, '无效任务键') }
    const signature = images.map(row => row.id).join(',')
    const existing = key && [...jobs.values()].find(item => item.key === key)
    if (existing) { return existing.signature === signature ? demoOk({ ...existing.job }) : demoError(409, '任务键已用于不同选择') }
    if (jobs.size >= MAX_JOBS) { return demoError(429, '演示任务数量已达上限，请稍后重试') }
    const job: ArchiveExportJob = {
      id: `demo-export-${++sequence}`,
      format: 'ZIP',
      scope: params.ids === undefined ? 'WHOLE_ARCHIVE' : 'SELECTED_IMAGES',
      status: 'PENDING',
      bah: images[0].bah,
      ...(params.sjh ? { sjh: images[0].sjh } : {}),
      plannedCount: images.length,
      processedCount: 0,
      failedCount: 0,
      estimatedBytes: images.reduce((n, row) => n + row.url.length, 0),
      outputBytes: 0,
      sourceSummary: '合成演示影像（SVG）',
      cancelRequested: false,
      fileName: 'mrr-synthetic-demo.zip',
      createdAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + TTL).toISOString(),
    }
    jobs.set(job.id, { job, images, key, signature })
    return demoOk({ ...job })
  }

  async function get(id: string) {
    expire()
    const entry = jobs.get(id)
    if (!entry) { return demoError(404, '演示任务不存在或已过期') }
    if (entry.job.status === 'PENDING') {
      entry.job.status = 'PROCESSING'
      entry.job.startedAt = new Date().toISOString()
      entry.processing = (async () => {
        try {
          const bytes = await zip(entry.images)
          if (entry.job.cancelRequested || jobs.get(id) !== entry) { return }
          entry.bytes = bytes
          entry.job.outputBytes = bytes.byteLength
          entry.job.processedCount = entry.images.length
          entry.job.status = 'SUCCESS'
        }
        catch {
          if (!entry.job.cancelRequested) { entry.job.status = 'FAILED'; entry.job.errorMessage = '合成压缩包生成失败' }
        }
        finally { entry.job.completedAt = new Date().toISOString() }
      })()
    }
    await entry.processing
    return demoOk({ ...entry.job })
  }

  function list(params: Params) {
    expire()
    const format = String(params.format ?? 'ZIP')
    const limit = Number(params.limit ?? 5)
    if (!['ZIP', 'PDF'].includes(format) || !Number.isInteger(limit) || limit < 1 || limit > MAX_JOBS) { return demoError(400, '无效任务查询') }
    return demoOk([...jobs.values()].reverse().map(row => row.job).filter(job => job.format === format && ['PENDING', 'PROCESSING'].includes(job.status)).slice(0, limit).map(job => ({ ...job })))
  }

  function cancel(id: string) {
    expire()
    const entry = jobs.get(id)
    if (!entry) { return demoError(404, '演示任务不存在或已过期') }
    if (['PENDING', 'PROCESSING'].includes(entry.job.status)) {
      entry.job.cancelRequested = true
      entry.job.status = 'CANCELLED'
      entry.job.completedAt = new Date().toISOString()
    }
    return demoOk({ ...entry.job })
  }

  function encode(bytes: Uint8Array, range?: unknown) {
    let start = 0
    let end = bytes.byteLength - 1
    if (range !== undefined && range !== '') {
      const match = /^bytes=(\d+)-(\d+)$/.exec(String(range))
      if (!match) { return demoError(416, '无效演示下载区间') }
      start = Number(match[1]); end = Number(match[2])
      if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || end < start || end >= bytes.byteLength) { return demoError(416, '无效演示下载区间') }
    }
    const segment = bytes.subarray(start, end + 1)
    let binary = ''
    for (let i = 0; i < segment.length; i += 8192) { binary += String.fromCharCode(...segment.subarray(i, i + 8192)) }
    return demoOk({ encoding: 'base64', mime: 'application/zip', base64: btoa(binary), start, end, total: bytes.byteLength })
  }

  function download(id: string, range?: unknown) {
    expire()
    const entry = jobs.get(id)
    return entry?.job.status === 'SUCCESS' && entry.bytes ? encode(entry.bytes, range) : demoError(409, '演示任务尚无可下载文件')
  }

  async function directZip(params: Params) {
    const images = select(params)
    if (!images) { return demoError(400, '无有效合成影像') }
    try { return encode(await zip(images)) }
    catch { return demoError(500, '合成压缩包生成失败') }
  }

  function pdfImages(params: Params) {
    const images = select(params)
    return images ? demoOk({ imageUrls: images.map(row => row.url) }) : demoError(400, '无有效合成影像')
  }

  return { plan, create, get, list, cancel, download, directZip, pdfImages }
}

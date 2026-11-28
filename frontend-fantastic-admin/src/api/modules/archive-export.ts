import type { AxiosRequestConfig } from 'axios'
import { ARCHIVE_EXPORT_MAX_BYTES, ArchiveExportBudgetError, ArchiveExportIntegrityError, requireArchiveExportBlob } from '@/utils/archive-export-budget'
import api, { getRequest, postRequest } from '../index'

export type ArchiveExportMode = 'CLIENT_PDF' | 'BACKEND_STREAM' | 'BACKEND_JOB'
export type ArchiveExportJobStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'EXPIRED'

export interface ArchiveExportPlan {
  format: 'ZIP' | 'PDF'
  executionMode: ArchiveExportMode
  selectedCount: number
  totalCount: number
  clientPdfMaxImages: number
  wholeArchive: boolean
  estimatedBytes: number
  sourceSummary: string[]
}

export interface ArchiveExportJob {
  id: string
  format: 'ZIP' | 'PDF'
  scope: 'WHOLE_ARCHIVE' | 'SELECTED_IMAGES'
  status: ArchiveExportJobStatus
  bah?: string
  sjh?: string
  plannedCount: number
  processedCount: number
  failedCount: number
  estimatedBytes: number
  outputBytes: number
  sourceSummary?: string
  fileName?: string
  sha256?: string
  cancelRequested: boolean
  errorMessage?: string
  expiresAt?: string
  createdAt?: string
  startedAt?: string
  completedAt?: string
}

export interface CreateArchiveExportJobRequest {
  format: 'ZIP' | 'PDF'
  bah?: string
  sjh?: string
  ids?: Array<string | number>
  idempotencyKey?: string
}

export function getArchiveZipExportPlan(bah?: string, sjh?: string) {
  return getRequest<ArchiveExportPlan>('/api/v1/archive-exports/plan/zip', {
    params: { bah: bah || undefined, sjh: sjh || undefined },
  })
}

/**
 * 兼容旧客户端的按数量规划接口。新前端必须使用选中 ID 规划，
 * 使阈值、来源和文件大小均基于本次实际选择。
 */
export function getArchivePdfExportPlan(bah: string | undefined, sjh: string | undefined, selectedCount: number) {
  return getRequest<ArchiveExportPlan>('/api/v1/archive-exports/plan/pdf', {
    params: {
      bah: bah || undefined,
      sjh: sjh || undefined,
      selectedCount,
    },
  })
}

export function getSelectedImagesPdfExportPlan(ids: Array<string | number>) {
  return postRequest<ArchiveExportPlan>('/api/v1/archive-exports/plan/pdf/selection', {
    ids: ids.map(String),
  })
}

export function createArchiveExportJob(request: CreateArchiveExportJobRequest) {
  return postRequest<ArchiveExportJob>('/api/v1/archive-exports/jobs', {
    ...request,
    ids: request.ids?.map(String),
  })
}

export function listActiveArchiveExportJobs(format: 'ZIP' | 'PDF', limit = 5) {
  return getRequest<ArchiveExportJob[]>('/api/v1/archive-exports/jobs', {
    params: { format, limit },
  })
}

export function getArchiveExportJob(id: string) {
  return getRequest<ArchiveExportJob>(`/api/v1/archive-exports/jobs/${encodeURIComponent(id)}`, { retry: true, maxRetryCount: 2, skipGlobalError: true })
}

export function cancelArchiveExportJob(id: string) {
  return postRequest<ArchiveExportJob>(`/api/v1/archive-exports/jobs/${encodeURIComponent(id)}/cancel`)
}

async function downloadWithinBudget(request: (config: AxiosRequestConfig) => Promise<Blob>): Promise<Blob> {
  const controller = new AbortController()
  let exceeded = false
  try {
    const blob = await request({
      responseType: 'blob',
      timeout: 1000 * 60 * 10,
      retry: false,
      signal: controller.signal,
      onDownloadProgress: ({ loaded, total }) => {
        if (loaded > ARCHIVE_EXPORT_MAX_BYTES || (total !== undefined && total > ARCHIVE_EXPORT_MAX_BYTES)) {
          exceeded = true
          controller.abort()
        }
      },
    })
    if (exceeded) {
      throw new ArchiveExportBudgetError()
    }
    return requireArchiveExportBlob(blob)
  }
  catch (error: unknown) {
    if (exceeded) {
      throw new ArchiveExportBudgetError()
    }
    throw error
  }
}

// The static fake-server serializes responses as JSON, not binary. Decode only
// the demo envelope here; production retains its existing HTTP range/ETag contract.
const isStaticDemo = import.meta.env.MODE === 'pages' || import.meta.env.MODE === 'mock'
interface DemoZipPayload {
  encoding: string
  mime: string
  base64: string
  start: number
  end: number
  total: number
}
function decodeDemoZip(payload: DemoZipPayload | undefined, range?: string, representation?: ArchiveExportRepresentation): Blob {
  const match = range ? /^bytes=(\d+)-(\d+)$/.exec(range) : null
  if (!payload || payload.encoding !== 'base64' || payload.mime !== 'application/zip'
    || typeof payload.base64 !== 'string' || payload.base64.length > 3 * 1024 * 1024
    || !Number.isSafeInteger(payload.total) || payload.total < 1 || payload.total > 2 * 1024 * 1024
    || (representation && payload.total !== representation.bytes)
    || (range && !match) || payload.start !== (match ? Number(match[1]) : 0)
    || payload.end !== (match ? Number(match[2]) : payload.total - 1)
    || payload.start < 0 || payload.end >= payload.total || payload.end < payload.start) {
    throw new ArchiveExportIntegrityError('演示导出文件表示无效')
  }
  const binary = atob(payload.base64)
  if (binary.length !== payload.end - payload.start + 1 || (payload.start === 0 && binary.length >= 2 && !binary.startsWith('PK'))) {
    throw new ArchiveExportIntegrityError('演示导出文件长度或签名无效')
  }
  return requireArchiveExportBlob(new Blob([Uint8Array.from(binary, char => char.charCodeAt(0))], { type: 'application/zip' }))
}
async function buildDemoPdf(payload: { imageUrls: string[] } | undefined) {
  if (!payload || !Array.isArray(payload.imageUrls) || !payload.imageUrls.length || payload.imageUrls.length > 50
    || payload.imageUrls.some(url => typeof url !== 'string' || !url.startsWith('data:image/svg+xml;charset=utf-8,') || url.length > 65536)) {
    throw new ArchiveExportIntegrityError('演示 PDF 影像清单无效')
  }
  const { createPdfFromImageUrls } = await import('@/views/statistics/archive/utils/client-pdf')
  return requireArchiveExportBlob(await createPdfFromImageUrls(payload.imageUrls))
}

export interface ArchiveExportRepresentation {
  bytes: number
  sha256?: string
}

export function downloadArchiveExportJob(id: string, range?: string, representation?: ArchiveExportRepresentation): Promise<Blob> {
  if (isStaticDemo && /^demo-export-\d+$/.test(id)) {
    return getRequest<DemoZipPayload>(`/api/v1/archive-exports/jobs/${encodeURIComponent(id)}/download`, { params: { demoRange: range }, retry: false }).then(result => decodeDemoZip(result.data, range, representation))
  }
  const digest = representation?.sha256
  const tag = digest && /^[a-f0-9]{64}$/i.test(digest) ? `"${digest}"` : undefined
  let integrityError: ArchiveExportIntegrityError | undefined
  return downloadWithinBudget(config => api.get<Blob, Blob>(`/api/v1/archive-exports/jobs/${encodeURIComponent(id)}/download`, {
    ...config,
    headers: range ? { Range: range, ...(tag ? { 'If-Range': tag } : {}) } : undefined,
    // Inspect HTTP metadata before the shared interceptor unwraps the Blob.
    // Preserve non-2xx responses for the existing permission/error handler.
    ...(representation
      ? { transformResponse: [(data, headers, status) => {
          if (status == null || status < 200 || status >= 300) { return data }
          if (tag && headers.get('etag') !== tag) { integrityError = new ArchiveExportIntegrityError('导出文件版本已变化，请刷新任务后重试') }
          if (range) {
            const match = /^bytes=(\d+)-(\d+)$/.exec(range)
            if (!match || status !== 206 || headers.get('content-range') !== `bytes ${match[1]}-${match[2]}/${representation.bytes}`) {
              integrityError ??= new ArchiveExportIntegrityError('导出区间响应与任务不一致，请刷新任务后重试')
            }
          }
          else if (status !== 200) {
            integrityError ??= new ArchiveExportIntegrityError('导出文件响应不完整')
          }
          return data
        }] }
      : {}),
  })).then((blob) => {
    if (integrityError) { throw integrityError }
    return blob
  })
}

export function downloadArchiveZip(bah?: string, sjh?: string): Promise<Blob> {
  if (isStaticDemo) { return getRequest<DemoZipPayload>('/api/v1/archive-exports/zip', { params: { bah, sjh }, retry: false }).then(result => decodeDemoZip(result.data)) }
  return downloadWithinBudget(config => api.get<Blob, Blob>('/api/v1/archive-exports/zip', {
    ...config,
    params: { bah: bah || undefined, sjh: sjh || undefined },
  }))
}

export function downloadArchivePdf(bah?: string, sjh?: string): Promise<Blob> {
  if (isStaticDemo) { return getRequest<{ imageUrls: string[] }>('/api/v1/archive-exports/pdf', { params: { bah, sjh }, retry: false }).then(result => buildDemoPdf(result.data)) }
  return downloadWithinBudget(config => api.get<Blob, Blob>('/api/v1/archive-exports/pdf', {
    ...config,
    params: { bah: bah || undefined, sjh: sjh || undefined },
  }))
}

export function downloadSelectedImagesPdf(ids: Array<string | number>): Promise<Blob> {
  if (isStaticDemo) { return postRequest<{ imageUrls: string[] }>('/api/v1/archive-exports/pdf/selection', { ids: ids.map(String) }, { retry: false }).then(result => buildDemoPdf(result.data)) }
  return downloadWithinBudget(config => api.post<Blob, Blob>(
    '/api/v1/archive-exports/pdf/selection',
    { ids: ids.map(String) },
    config,
  ))
}

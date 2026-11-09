import type { AxiosRequestConfig } from 'axios'
import { ARCHIVE_EXPORT_MAX_BYTES, ArchiveExportBudgetError, requireArchiveExportBlob } from '@/utils/archive-export-budget'
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
  return getRequest<ArchiveExportJob>(`/api/v1/archive-exports/jobs/${encodeURIComponent(id)}`)
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

export function downloadArchiveExportJob(id: string, range?: string): Promise<Blob> {
  return downloadWithinBudget(config => api.get<Blob, Blob>(`/api/v1/archive-exports/jobs/${encodeURIComponent(id)}/download`, {
    ...config,
    headers: range ? { Range: range } : undefined,
  }))
}

export function downloadArchiveZip(bah?: string, sjh?: string): Promise<Blob> {
  return downloadWithinBudget(config => api.get<Blob, Blob>('/api/v1/archive-exports/zip', {
    ...config,
    params: { bah: bah || undefined, sjh: sjh || undefined },
  }))
}

export function downloadArchivePdf(bah?: string, sjh?: string): Promise<Blob> {
  return downloadWithinBudget(config => api.get<Blob, Blob>('/api/v1/archive-exports/pdf', {
    ...config,
    params: { bah: bah || undefined, sjh: sjh || undefined },
  }))
}

export function downloadSelectedImagesPdf(ids: Array<string | number>): Promise<Blob> {
  return downloadWithinBudget(config => api.post<Blob, Blob>(
    '/api/v1/archive-exports/pdf/selection',
    { ids: ids.map(String) },
    config,
  ))
}

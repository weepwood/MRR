import type { BAHImageData, ImageTypeRequest } from '../types'
import api, { getRequest, postRequest, putRequest } from '../index'

export type ClassificationScope = 'UNCLASSIFIED' | 'LOW_CONFIDENCE' | 'ALL'

export interface ClassificationJob {
  id: number
  archiveId: number
  scopeType: ClassificationScope
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  totalCount: number
  processedCount: number
  suggestedCount: number
  noMatchCount: number
  failedCount: number
  cursorScanId?: number
  modelVersion?: string
  errorMessage?: string
  createdAt?: string
  startedAt?: string
  completedAt?: string
}

function resolveArchiveUserId(explicitUserId?: string): string | undefined {
  const explicit = String(explicitUserId || '').trim()
  if (explicit) {
    return explicit
  }

  if (typeof window === 'undefined') {
    return undefined
  }

  return new URLSearchParams(window.location.search).get('userid')?.trim() || undefined
}

/** GET /api/v1/img/{bah} — 获取唯一病案号下的图片数据 */
export function getImgApiByBah(bah: string) {
  return getRequest<BAHImageData[]>(`/api/v1/img/${bah}`)
}

/** GET /api/v1/img/search — 按病案号、上架号和调用方用户查询图片数据 */
export function getImgByCode(bah?: string, sjh?: string, forceRefresh = false, userid?: string) {
  return getRequest<BAHImageData[]>('/api/v1/img/search', {
    params: {
      bah,
      sjh,
      userid: resolveArchiveUserId(userid),
      ...(forceRefresh ? { _: Date.now() } : {}),
    },
    headers: forceRefresh
      ? {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache',
        }
      : undefined,
  })
}

/** GET /api/v1/img/image/{BAH}/{BRXH}/{FOLDER}/{FILENAME} — 获取单张图片（blob 流） */
export function getImage(bah: string, brxh: string, folder: string, filename: string) {
  return api.get<Blob>(`/api/v1/img/image/${bah}/${brxh}/${folder}/${filename}`, {
    responseType: 'blob',
  })
}

/** PUT /api/v1/img/updateImageType/{id} — 更新图片类型 */
export function updateImageType(imageId: string | number, data: ImageTypeRequest) {
  return putRequest<void>(`/api/v1/img/updateImageType/${imageId}`, data)
}

/** POST /api/v1/image-classification/archives/{archiveId}/jobs — 创建 OCR 分类任务 */
export function startImageClassification(archiveId: number, scope: ClassificationScope = 'UNCLASSIFIED') {
  return postRequest<ClassificationJob>(`/api/v1/image-classification/archives/${archiveId}/jobs`, {
    scope,
    createdBy: resolveArchiveUserId(),
  })
}

/** GET /api/v1/image-classification/jobs/{jobId} — 查询 OCR 分类进度 */
export function getImageClassificationJob(jobId: number) {
  return getRequest<ClassificationJob>(`/api/v1/image-classification/jobs/${jobId}`)
}

/** PUT /api/v1/image-classification/scans/{scanId}/confirm — 采用或修改单张建议 */
export function confirmImageClassification(scanId: number, btype?: number) {
  return putRequest(`/api/v1/image-classification/scans/${scanId}/confirm`, {
    btype,
    reviewedBy: resolveArchiveUserId(),
  })
}

/** POST /api/v1/image-classification/archives/{archiveId}/confirm-high-confidence */
export function confirmHighConfidenceClassifications(archiveId: number, minConfidence = 0.92) {
  return postRequest<{ confirmedCount: number }>(
    `/api/v1/image-classification/archives/${archiveId}/confirm-high-confidence`,
    {
      minConfidence,
      reviewedBy: resolveArchiveUserId(),
    },
  )
}

/** GET /api/v1/img/image/{cx} — 获取单张图片（blob 流） */
export function getImgByCx(cx: string) {
  return api.get<Blob>(`/api/v1/img/image/${cx}`, { responseType: 'blob' })
}

/** GET /api/v1/img/oss-image/{id} — 从 OSS URL 获取图片（blob 流） */
export function getImageFromOss(imageId: number | string) {
  return api.get<Blob>(`/api/v1/img/oss-image/${imageId}`, { responseType: 'blob' })
}

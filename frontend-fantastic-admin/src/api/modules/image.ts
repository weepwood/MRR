import type { BAHImageData, ImageTypeRequest } from '../types'
import api, { getRequest, putRequest } from '../index'

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

/** GET /api/v1/img/image/{cx} — 获取单张图片（blob 流） */
export function getImgByCx(cx: string) {
  return api.get<Blob>(`/api/v1/img/image/${cx}`, { responseType: 'blob' })
}

/** GET /api/v1/img/oss-image/{id} — 从 OSS URL 获取图片（blob 流） */
export function getImageFromOss(imageId: number | string) {
  return api.get<Blob>(`/api/v1/img/oss-image/${imageId}`, { responseType: 'blob' })
}

import type { BAHImageData, ImageTypeRequest } from '../types'
import api, { getRequest, putRequest } from '../index'

/** GET /api/v1/img/{bah} — 获取唯一病案号下的图片数据 */
export function getImgApiByBah(bah: string) {
  return getRequest<BAHImageData[]>(`/api/v1/img/${bah}`)
}

/** GET /api/v1/img/search — 按病案号和/或上架号查询图片数据 */
export function getImgByCode(bah?: string, sjh?: string) {
  return getRequest<BAHImageData[]>('/api/v1/img/search', {
    params: { bah, sjh },
  })
}

/** GET /api/v1/img/download/{bah} — 下载精确匹配的病案压缩包 */
export function downloadBah(bah: string, sjh?: string) {
  return api.get<Blob>(`/api/v1/img/download/${bah}`, {
    params: { sjh },
    responseType: 'blob',
  })
}

/** POST /api/v1/img/export-pdf — 将选中的影像按顺序导出为 PDF */
export function exportSelectedImagesPdf(ids: number[]) {
  return api.post<Blob>('/api/v1/img/export-pdf', ids, {
    responseType: 'blob',
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

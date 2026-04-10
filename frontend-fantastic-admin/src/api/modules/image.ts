import type { BAHImageData, ImageTypeRequest } from '../types'
import api from '../index'

/** GET /api/v1/img/{bah} — 获取病案号下的图片数据 */
export function getImgApiByBah(bah: string) {
  return api.get<BAHImageData[]>(`/api/v1/img/${bah}`)
}

/** GET /api/v1/img/download/{bah} — 下载病案压缩包 */
export function downloadBah(bah: string) {
  return api.get(`/api/v1/img/download/${bah}`, {
    responseType: 'blob',
  })
}

/** GET /api/v1/img/image/{BAH}/{BRXH}/{FOLDER}/{FILENAME} — 获取单张图片 */
export function getImage(bah: string, brxh: string, folder: string, filename: string) {
  return api.get(`/api/v1/img/image/${bah}/${brxh}/${folder}/${filename}`, {
    responseType: 'blob',
  })
}

/** PUT /api/v1/img/updateImageType/{id} — 更新图片类型 */
export function updateImageType(imageId: string | number, data: ImageTypeRequest) {
  return api.put(`/api/v1/img/updateImageType/${imageId}`, data)
}

/** GET /api/v1/img/image/{cx} — 获取单张图片（blob 流） */
export function getImgByCx(cx: string) {
  return api.get(`/api/v1/img/image/${cx}`, { responseType: 'blob' })
}

/** GET /api/v1/img/oss-image/{id} — 从 OSS URL 获取图片（blob 流） */
export function getImageFromOss(imageId: number | string) {
  return api.get(`/api/v1/img/oss-image/${imageId}`, { responseType: 'blob' })
}

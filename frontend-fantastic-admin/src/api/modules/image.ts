import api from '../index'
import type { BAHImageData, ImageTypeRequest } from '../types'

/** GET /v1/img-api/{bah} — 获取病案号下的图片数据 */
export function getImgApiByBah(bah: string) {
  return api.get<BAHImageData[]>(`/v1/img-api/${bah}`)
}

/** GET /v1/img-api/download/{bah} — 下载病案压缩包 */
export function downloadBah(bah: string) {
  return api.get(`/v1/img-api/download/${bah}`, {
    responseType: 'blob',
  })
}

/** GET /v1/img-api/image/{BAH}/{BRXH}/{FOLDER}/{FILENAME} — 获取单张图片 */
export function getImage(bah: string, brxh: string, folder: string, filename: string) {
  return api.get(`/v1/img-api/image/${bah}/${brxh}/${folder}/${filename}`, {
    responseType: 'blob',
  })
}

/** PUT /v1/img-api/updateImageType/{id} — 更新图片类型 */
export function updateImageType(imageId: string | number, data: ImageTypeRequest) {
  return api.put(`/v1/img-api/updateImageType/${imageId}`, data)
}

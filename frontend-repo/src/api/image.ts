import { authApi, searchApi } from './config'

export function getImgApiByBah(bah: string, config: any = {}) {
  return authApi.get(`/img-api/${bah}`, config)
}

export function downloadBah(bah: string, config: any = {}) {
  return authApi.get(`/img-api/download/${bah}`, config)
}

export function getImg(cx: string, config: any = {}) {
  return authApi.get(`/img-api/image/${cx}`, config)
}

export function getBAHByIdCard(idCard: string) {
  return searchApi.get(`/search/getBAHByID/${idCard}`)
}

export function getBAHByEncryptID(EncryptID: string, userId: string, iv: string, timestamp: string) {
  return searchApi.get('/search/getBAHByEncryptID', {
    params: { EncryptID, userId, iv, timestamp }
  })
}

export function updateImgType(imageId: string | number, btype: any) {
  return authApi.put(`/img-api/updateImageType/${imageId}`, btype)
}

// OSS API functions
export function getOssImageUrl(scanId: number | string) {
  return authApi.get(`/oss-api/url/${scanId}`)
}

export function uploadToOss(scanIds: number[]) {
  return authApi.post('/oss-api/upload', { scanIds })
}

export function uploadByBah(bah: string) {
  return authApi.post(`/oss-api/upload/bah/${bah}`)
}

export function getMigrationStatistics() {
  return authApi.get('/oss-api/migration/statistics')
}

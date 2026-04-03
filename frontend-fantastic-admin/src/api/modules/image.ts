import api from '../index'

export function getImgApiByBah(bah: string, config: any = {}) {
  return api.get(`/v1/img-api/${bah}`, config)
}

export function downloadBah(bah: string, config: any = {}) {
  return api.get(`/v1/img-api/download/${bah}`, config)
}

export function getImg(cx: string, config: any = {}) {
  return api.get(`/v1/img-api/image/${cx}`, config)
}

export function getBAHByIdCard(idCard: string) {
  return api.get(`/search/getBAHByID/${idCard}`)
}

export function getBAHByEncryptID(EncryptID: string, userId: string, iv: string, timestamp: string) {
  return api.get('/search/getBAHByEncryptID', {
    params: { EncryptID, userId, iv, timestamp },
  })
}

export function updateImgType(imageId: string | number, btype: any) {
  return api.put(`/v1/img-api/updateImageType/${imageId}`, btype)
}

import api from '../index'

export function getImgApiByBah(bah: string) {
  return api.get(`/v1/image/archive/${bah}`)
}

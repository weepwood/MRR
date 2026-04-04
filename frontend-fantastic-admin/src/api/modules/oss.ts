import api from '../index'

/** POST /v1/oss-api/upload — 按 Scan ID 批量上传到 OSS */
export function uploadToOss(scanIds: number[]) {
  return api.post('/v1/oss-api/upload', { scanIds })
}

/** POST /v1/oss-api/upload/bah/{bah} — 按病案号批量上传到 OSS */
export function uploadByBah(bah: string) {
  return api.post(`/v1/oss-api/upload/bah/${bah}`)
}

/** GET /v1/oss-api/url/{scanId} — 获取 OSS 签名 URL */
export function getOssUrl(scanId: number) {
  return api.get(`/v1/oss-api/url/${scanId}`)
}

/** GET /v1/oss-api/migration/statistics — 获取迁移统计 */
export function getMigrationStatistics() {
  return api.get('/v1/oss-api/migration/statistics')
}

/** GET /v1/oss-api/migration/pending — 获取待迁移列表 */
export function getPendingMigrations(limit: number = 50) {
  return api.get('/v1/oss-api/migration/pending', { params: { limit } })
}

/** GET /v1/oss-api/migration/logs — 获取迁移日志 */
export function getMigrationLogs(params: { status?: string, page?: number, size?: number } = {}) {
  return api.get('/v1/oss-api/migration/logs', { params })
}

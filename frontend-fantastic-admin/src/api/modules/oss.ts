import api from '../index'

/** POST /api/v1/oss/upload — 按 Scan ID 批量上传到 OSS */
export function uploadToOss(scanIds: number[]) {
  return api.post('/api/v1/oss/upload', { scanIds })
}

/** POST /api/v1/oss/upload/bah/{bah} — 按病案号批量上传到 OSS */
export function uploadByBah(bah: string) {
  return api.post(`/api/v1/oss/upload/bah/${bah}`)
}

/** POST /api/v1/oss/upload/folder/{folder} — 按文件夹上传到 OSS */
export function uploadByFolder(folder: string) {
  return api.post(`/api/v1/oss/upload/folder/${encodeURIComponent(folder)}`)
}

/** GET /api/v1/oss/url/{scanId} — 获取 OSS 签名 URL */
export function getOssUrl(scanId: number) {
  return api.get(`/api/v1/oss/url/${scanId}`)
}

/** GET /api/v1/oss/migration/statistics — 获取迁移统计 */
export function getMigrationStatistics() {
  return api.get('/api/v1/oss/migration/statistics')
}

/** GET /api/v1/oss/migration/pending — 获取待迁移列表 */
export function getPendingMigrations(params: { limit?: number, folder?: string } = {}) {
  return api.get('/api/v1/oss/migration/pending', { params })
}

/** GET /api/v1/oss/migration/pending-folders — 获取待迁移文件夹列表 */
export function getPendingFolders() {
  return api.get('/api/v1/oss/migration/pending-folders')
}

/** GET /api/v1/oss/migration/logs — 获取迁移日志 */
export function getMigrationLogs(params: { status?: string, page?: number, size?: number } = {}) {
  return api.get('/api/v1/oss/migration/logs', { params })
}

/** POST /api/v1/oss/migration/jobs — 创建异步迁移任务 */
export function createMigrationJob() {
  return api.post('/api/v1/oss/migration/jobs')
}

/** GET /api/v1/oss/migration/jobs/{id} — 查询任务进度 */
export function getMigrationJob(id: number) {
  return api.get(`/api/v1/oss/migration/jobs/${id}`)
}

/** GET /api/v1/oss/migration/jobs — 查询任务列表 */
export function getMigrationJobs(params: { page?: number, size?: number } = {}) {
  return api.get('/api/v1/oss/migration/jobs', { params })
}

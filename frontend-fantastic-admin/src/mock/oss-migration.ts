import type { MockScanRecord } from '../mock-support/records'
import type { MigrationJob } from '@/api/modules/oss'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { migrationSnapshot, resetDemoFailures, uploadDemoScans } from '../mock-support/migration'
import { demoRecords } from '../mock-support/records'
import { boundedInt, demoError, demoPage } from '../mock-support/response'

function ok<T>(data: T, message = '操作成功') {
  return {
    code: 200,
    message,
    data,
    timestamp: new Date().toISOString(),
  }
}

function snapshot() {
  return migrationSnapshot(demoRecords.scans)
}

const jobs: MigrationJob[] = [
  {
    id: 12,
    status: 'completed',
    mode: 'pilot',
    scopeValue: '25.03.15',
    totalCount: 500,
    processedCount: 500,
    failedCount: 2,
    rate: 100,
    createdBy: 'admin',
    createdAt: new Date(Date.now() - 3_600_000).toISOString(),
    completedAt: new Date(Date.now() - 3_000_000).toISOString(),
  },
  {
    id: 11,
    status: 'completed_with_errors',
    mode: 'batch',
    totalCount: 10000,
    processedCount: 10000,
    failedCount: 18,
    rate: 100,
    createdBy: 'admin',
    createdAt: new Date(Date.now() - 86_400_000).toISOString(),
    completedAt: new Date(Date.now() - 82_000_000).toISOString(),
  },
]

let nextJobId = 13

const logs = [
  {
    id: 1,
    scanId: 1,
    localPath: 'NGINX:scan:1',
    ossUrl: 'https://example.test/signed/1',
    migrationStatus: 'success',
    fileSize: 235000,
    checksumMd5: '0123456789abcdef0123456789abcdef',
    migratedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  },
  {
    id: 2,
    scanId: 6,
    localPath: 'NGINX:scan:6',
    migrationStatus: 'failed',
    errorMessage: 'Nginx 图片服务返回状态码 404',
    createdAt: new Date(Date.now() - 120000).toISOString(),
  },
  {
    id: 3,
    scanId: 7,
    localPath: 'NGINX:scan:7',
    migrationStatus: 'retry_wait',
    errorMessage: 'Nginx 连接超时',
    createdAt: new Date(Date.now() - 180000).toISOString(),
  },
]

function browserDirectory(name: string, key: string) {
  return {
    name,
    key,
    directory: true,
    size: 0,
  }
}

function browserFile(name: string, key: string, size: number, storageClass = 'STANDARD') {
  return {
    name,
    key,
    directory: false,
    size,
    lastModified: new Date(Date.now() - size).toISOString(),
    etag: `mock-etag-${name}`,
    storageClass,
  }
}

function browserEntries(prefix: string, continuationToken?: string) {
  if (prefix === 'medical-records/') {
    return [
      browserDirectory('0012', 'medical-records/0012/'),
      browserDirectory('0013', 'medical-records/0013/'),
      browserDirectory('0024', 'medical-records/0024/'),
    ]
  }
  if (prefix === 'medical-records/0012/') {
    return [
      browserDirectory('00123456-00789124', 'medical-records/0012/00123456-00789124/'),
      browserDirectory('00124567-00880211', 'medical-records/0012/00124567-00880211/'),
    ]
  }
  if (prefix === 'medical-records/0012/00123456-00789124/') {
    if (continuationToken === 'mock-page-2') {
      return Array.from({ length: 6 }, (_, index) => browserFile(
        `${String(index + 201).padStart(4, '0')}.jpg`,
        `medical-records/0012/00123456-00789124/${String(index + 201).padStart(4, '0')}.jpg`,
        260000 + index * 1200,
      ))
    }
    return [
      browserFile('0001.jpg', 'medical-records/0012/00123456-00789124/0001.jpg', 235000),
      browserFile('0002.jpg', 'medical-records/0012/00123456-00789124/0002.jpg', 248000),
      browserFile('0003.tif', 'medical-records/0012/00123456-00789124/0003.tif', 310000, 'IA'),
      browserFile('病案目录说明.txt', 'medical-records/0012/00123456-00789124/病案目录说明.txt', 1200),
    ]
  }
  return []
}

function browserPage(query: Record<string, unknown>) {
  const prefix = String(query.prefix || 'medical-records/')
  const continuationToken = query.continuationToken ? String(query.continuationToken) : undefined
  const entries = browserEntries(prefix, continuationToken)
  const truncated = prefix === 'medical-records/0012/00123456-00789124/' && !continuationToken
  return {
    configured: true,
    bucket: 'mrr-medical-records',
    endpoint: 'oss-cn-hangzhou.aliyuncs.com',
    region: 'cn-hangzhou',
    rootPrefix: 'medical-records/',
    prefix,
    entries,
    nextContinuationToken: truncated ? 'mock-page-2' : null,
    truncated,
    maxKeys: Number(query.maxKeys || 200),
    loadedDirectories: entries.filter(entry => entry.directory).length,
    loadedFiles: entries.filter(entry => !entry.directory).length,
    loadedBytes: entries.reduce((sum, entry) => sum + (entry.directory ? 0 : entry.size), 0),
  }
}

function filteredRecords(records: MockScanRecord[], query: Record<string, unknown>) {
  const limit = boundedInt(query.limit, 100, 500)
  const filtered = records.filter((item) => {
    return (!query.folder || item.folder === query.folder)
      && (!query.bah || item.bah === query.bah)
      && (!query.sjh || item.sjh === query.sjh)
  })
  return {
    list: filtered.slice(0, limit),
    returned: Math.min(filtered.length, limit),
    limit,
    hasMore: filtered.length > limit,
  }
}

export default defineFakeRoute([
  {
    url: '/api/v1/oss/browser/mock-content',
    headers: { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' },
    // 纯静态 Mock 产物只支持 response；开发服务器继续使用 rawResponse。
    response: () => 'OSS 文件浏览演示数据\n<示例>用于检查目录说明的文本显示。</示例>\n',
    rawResponse: (_request, response) => {
      response.setHeader('Content-Type', 'text/plain; charset=utf-8')
      response.setHeader('Cache-Control', 'no-store')
      response.end('OSS 文件浏览演示数据\n<示例>用于检查目录说明的文本显示。</示例>\n')
    },
  },
  {
    url: '/api/v1/oss/browser/url',
    response: ({ query }) => {
      const key = String((query as Record<string, unknown>)?.key || '')
      const demoImage = /\.(?:jpg|tif)$/i.test(key)
      return ok({
        key,
        ossUrl: demoImage ? '/pwa/icon-192.png' : '/proxy/api/v1/oss/browser/mock-content',
      })
    },
  },
  {
    url: '/api/v1/oss/browser',
    response: ({ query }) => ok(browserPage((query ?? {}) as Record<string, unknown>)),
  },
  {
    url: '/api/v1/oss/migration/statistics',
    response: () => ok(snapshot().statistics),
  },
  {
    url: '/api/v1/oss/migration/readiness',
    response: ({ query }) => {
      const state = snapshot()
      const activeJob = jobs.find(job => ['pending', 'running', 'cancelling'].includes(job.status ?? ''))
      const sample = state.pending.slice(0, boundedInt(query?.sampleSize, 100, 200))
      const warnings: string[] = []
      if (state.waiting.length) { warnings.push(`演示：${state.waiting.length} 条记录等待合法上架号`) }
      if (activeJob) { warnings.push('已有演示迁移任务，请先完成或取消') }
      if (!sample.length) { warnings.push('当前没有可领取的演示记录') }
      return ok({
        ready: !activeJob && sample.length > 0,
        ossConfigured: true,
        sourcePathConfigured: true,
        sourcePathReadable: true,
        noActiveJob: !activeJob,
        activeJob,
        pendingCount: state.pending.length,
        sampleSize: sample.length,
        sampleReadableCount: sample.length,
        sampleMissingCount: 0,
        sampleInvalidCount: 0,
        recommendedMode: state.statistics.migratedCount ? 'batch' : 'pilot',
        recommendedAction: '演示：限定数量的模拟迁移，不访问真实存储',
        warnings,
      })
    },
  },
  {
    url: '/api/v1/oss/migration/pending',
    response: ({ query }) => {
      const filtered = snapshot().pending.filter(item => !query?.folder || item.folder === query.folder)
      const limit = boundedInt(query?.limit, 100, 500)
      return ok({ list: filtered.slice(0, limit), total: filtered.length, limit })
    },
  },
  {
    url: '/api/v1/oss/migration/pending-folders',
    response: () => ok(snapshot().folders),
  },
  {
    url: '/api/v1/oss/migration/management/pending',
    response: ({ query }) => ok(filteredRecords(snapshot().pending, (query ?? {}) as Record<string, unknown>)),
  },
  {
    url: '/api/v1/oss/migration/management/waiting-sjh',
    response: ({ query }) => ok(filteredRecords(snapshot().waiting, (query ?? {}) as Record<string, unknown>)),
  },
  {
    url: '/api/v1/oss/migration/jobs',
    method: 'get',
    response: ({ query }) => ok(demoPage(jobs, query?.page, query?.size)),
  },
  {
    url: '/api/v1/oss/migration/jobs',
    method: 'post',
    response: ({ body }) => {
      const mode = String(body?.mode ?? '')
      if (!['pilot', 'batch', 'full'].includes(mode)) {
        return demoError(400, '无效的演示迁移模式')
      }
      const active = jobs.find(job => ['pending', 'running', 'cancelling'].includes(job.status ?? ''))
      if (active) {
        return ok({ ...active, reused: true })
      }
      const job: MigrationJob = {
        id: nextJobId++,
        status: 'pending',
        mode,
        scopeValue: String(body?.folder ?? '').slice(0, 100),
        totalCount: boundedInt(body?.limit, 500, 10000),
        processedCount: 0,
        failedCount: 0,
        rate: 0,
        createdBy: 'admin',
        createdAt: new Date().toISOString(),
      }
      jobs.unshift(job)
      return ok(job, '演示迁移任务已创建')
    },
  },
  {
    url: '/api/v1/oss/migration/jobs/:id/cancel',
    method: 'post',
    response: ({ params }) => {
      const job = jobs.find(item => item.id === Number(params.id))
      if (!job) { return demoError(404, '演示任务不存在') }
      if (job.status !== 'pending' && job.status !== 'running') {
        return demoError(409, '演示任务当前不可取消')
      }
      job.status = 'cancelled'
      job.cancelRequested = true
      job.completedAt = new Date().toISOString()
      return ok(job, '演示任务已取消')
    },
  },
  {
    url: '/api/v1/oss/migration/jobs/:id',
    response: ({ params }) => {
      const job = jobs.find(item => item.id === Number(params.id))
      return job ? ok(job) : demoError(404, '演示任务不存在')
    },
  },
  {
    url: '/api/v1/oss/migration/management/logs',
    response: ({ query }) => {
      const request = (query ?? {}) as Record<string, unknown>
      const filtered = logs.filter((item) => {
        return (!request.status || item.migrationStatus === request.status)
          && (!request.scanId || item.scanId === Number(request.scanId))
      })
      return ok(demoPage(filtered, request.page, request.size))
    },
  },
  {
    url: '/api/v1/oss/migration/retry',
    method: 'post',
    response: ({ body }) => {
      if (jobs.some(job => ['pending', 'running', 'cancelling'].includes(job.status ?? ''))) {
        return demoError(409, '演示迁移任务运行期间不能重置失败记录')
      }
      const ids = (body as { scanIds?: unknown } | undefined)?.scanIds
      if (!Array.isArray(ids) || !ids.length || ids.length > 500 || !ids.every(id => Number.isSafeInteger(id) && id > 0)) {
        return demoError(400, '请输入有效的演示扫描编号（最多500条）')
      }
      return ok({ updated: resetDemoFailures(demoRecords.scans, ids) }, '失败记录已重置')
    },
  },
  {
    url: '/api/v1/oss/upload',
    method: 'post',
    response: ({ body }) => {
      if (jobs.some(job => ['pending', 'running', 'cancelling'].includes(job.status ?? ''))) {
        return demoError(409, '演示迁移任务运行期间不能手动上传')
      }
      const ids = (body as { scanIds?: unknown } | undefined)?.scanIds
      if (!Array.isArray(ids) || !ids.length || ids.length > 500 || !ids.every(id => Number.isSafeInteger(id) && id > 0)) {
        return demoError(400, '请输入有效的演示扫描编号（最多500条）')
      }
      return ok(uploadDemoScans(demoRecords.scans, ids), '上传完成')
    },
  },
  {
    url: '/api/v1/oss/upload/bah/:bah',
    method: 'post',
    response: ({ params }) => {
      if (jobs.some(job => ['pending', 'running', 'cancelling'].includes(job.status ?? ''))) {
        return demoError(409, '演示迁移任务运行期间不能手动上传')
      }
      const bah = String(params.bah).trim()
      const ids = demoRecords.scans.filter(scan => scan.bah === bah && scan.uploadFlag).map(scan => scan.id)
      if (!ids.length) { return demoError(404, '未找到可上传的演示病案记录') }
      return ok({ bah, ...uploadDemoScans(demoRecords.scans, ids) }, '上传完成')
    },
  },
])

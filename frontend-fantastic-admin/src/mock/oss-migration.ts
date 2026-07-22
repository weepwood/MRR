import { defineFakeRoute } from 'vite-plugin-fake-server/client'

function ok<T>(data: T, message = '操作成功') {
  return {
    code: 200,
    message,
    data,
    timestamp: new Date().toISOString(),
  }
}

const pendingRecords = Array.from({ length: 18 }, (_, index) => ({
  id: index + 1,
  bah: `00789${String(index + 100).padStart(3, '0')}`,
  sjh: `0012${String(index + 3400).padStart(4, '0')}`,
  folder: index < 10 ? '25.03.15' : '25.03.16',
  filename: `${String(index + 1).padStart(4, '0')}.jpg`,
  migrationStatus: index % 5 === 0 ? 'retry_wait' : 'not_migrated',
  migrationAttempts: index % 5 === 0 ? 1 : 0,
  migrationNextRetryAt: index % 5 === 0 ? new Date(Date.now() - 60_000).toISOString() : null,
}))

const waitingRecords = Array.from({ length: 8 }, (_, index) => ({
  id: 100 + index,
  bah: `00888${String(index + 100).padStart(3, '0')}`,
  sjh: index % 2 === 0 ? '' : '12A',
  folder: '25.03.15',
  filename: `${String(index + 21).padStart(4, '0')}.jpg`,
  migrationStatus: 'waiting_sjh',
  migrationErrorCode: 'MISSING_SJH',
}))

const jobs = [
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

function filteredRecords(records: typeof pendingRecords | typeof waitingRecords, query: Record<string, unknown>) {
  const limit = Math.max(1, Math.min(Number(query.limit || 100), 500))
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
    url: '/api/v1/oss/browser/url',
    response: ({ query }) => {
      const key = String((query as Record<string, unknown>)?.key || '')
      return ok({
        key,
        ossUrl: `https://images.example.test/${encodeURIComponent(key)}?signature=mock`,
      })
    },
  },
  {
    url: '/api/v1/oss/browser',
    response: ({ query }) => ok(browserPage((query ?? {}) as Record<string, unknown>)),
  },
  {
    url: '/api/v1/oss/migration/statistics',
    response: () => ok({
      totalCount: 100,
      migratedCount: 52,
      pendingCount: 18,
      waitingSjhCount: 8,
      failedCount: 4,
      retryWaitCount: 6,
      migratingCount: 12,
      percentage: 52,
    }),
  },
  {
    url: '/api/v1/oss/migration/readiness',
    response: () => ok({
      ready: true,
      ossConfigured: true,
      sourcePathConfigured: true,
      sourcePathReadable: true,
      noActiveJob: true,
      pendingCount: 18,
      sampleSize: 18,
      sampleReadableCount: 17,
      sampleMissingCount: 1,
      sampleInvalidCount: 0,
      recommendedMode: 'pilot',
      recommendedAction: '先迁移 100 至 500 张真实图片，核对访问、下载与日志后再扩大批次',
      warnings: ['模拟数据中保留 1 条缺失文件，用于展示失败处理提示'],
    }),
  },
  {
    url: '/api/v1/oss/migration/pending-folders',
    response: () => ok([
      { folder: '25.03.15', cnt: 10 },
      { folder: '25.03.16', cnt: 8 },
    ]),
  },
  {
    url: '/api/v1/oss/migration/management/pending',
    response: ({ query }) => ok(filteredRecords(pendingRecords, (query ?? {}) as Record<string, unknown>)),
  },
  {
    url: '/api/v1/oss/migration/management/waiting-sjh',
    response: ({ query }) => ok(filteredRecords(waitingRecords, (query ?? {}) as Record<string, unknown>)),
  },
  {
    url: '/api/v1/oss/migration/jobs',
    method: 'get',
    response: ({ query }) => ok({
      list: jobs,
      total: jobs.length,
      page: Number((query as Record<string, unknown>)?.page || 1),
      size: Number((query as Record<string, unknown>)?.size || 20),
      totalPages: 1,
    }),
  },
  {
    url: '/api/v1/oss/migration/jobs',
    method: 'post',
    response: ({ body }) => ok({
      id: 13,
      status: 'pending',
      ...(body as Record<string, unknown>),
      totalCount: Number((body as Record<string, unknown>)?.limit || 500),
      processedCount: 0,
      failedCount: 0,
      rate: 0,
      createdBy: 'admin',
      createdAt: new Date().toISOString(),
    }, '迁移任务已创建'),
  },
  {
    url: '/api/v1/oss/migration/jobs/:id/cancel',
    method: 'post',
    response: ({ params }) => ok({
      id: Number((params as Record<string, string>).id),
      status: 'cancelling',
      mode: 'pilot',
      totalCount: 500,
      processedCount: 120,
      failedCount: 1,
      rate: 24,
      cancelRequested: true,
    }, '已提交安全取消请求'),
  },
  {
    url: '/api/v1/oss/migration/jobs/:id',
    response: ({ params }) => ok({
      ...jobs[0],
      id: Number((params as Record<string, string>).id),
    }),
  },
  {
    url: '/api/v1/oss/migration/management/logs',
    response: ({ query }) => {
      const request = (query ?? {}) as Record<string, unknown>
      const filtered = logs.filter((item) => {
        return (!request.status || item.migrationStatus === request.status)
          && (!request.scanId || item.scanId === Number(request.scanId))
      })
      return ok({
        list: filtered,
        total: filtered.length,
        page: Number(request.page || 1),
        size: Number(request.size || 20),
        totalPages: 1,
      })
    },
  },
  {
    url: '/api/v1/oss/migration/retry',
    method: 'post',
    response: ({ body }) => {
      const payload = (body ?? {}) as { scanIds?: unknown }
      return ok({
        updated: Array.isArray(payload.scanIds) ? payload.scanIds.length : 0,
      }, '失败记录已重置')
    },
  },
  {
    url: '/api/v1/oss/upload',
    method: 'post',
    response: ({ body }) => {
      const scanIds = ((body ?? {}) as { scanIds?: number[] }).scanIds ?? []
      return ok({
        results: scanIds.map(scanId => ({
          scanId,
          status: scanId % 7 === 0 ? 'retry_wait' : 'success',
          errorMessage: scanId % 7 === 0 ? 'Nginx 连接超时' : undefined,
        })),
        total: scanIds.length,
        success: scanIds.filter(scanId => scanId % 7 !== 0).length,
        failed: scanIds.filter(scanId => scanId % 7 === 0).length,
        waitingSjh: 0,
      }, '上传完成')
    },
  },
  {
    url: '/api/v1/oss/upload/bah/:bah',
    method: 'post',
    response: ({ params }) => ok({
      bah: (params as Record<string, string>).bah,
      results: [{ scanId: 1, status: 'success' }],
      total: 1,
      success: 1,
      failed: 0,
      waitingSjh: 0,
    }, '上传完成'),
  },
])

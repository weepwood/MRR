import { defineFakeRoute } from 'vite-plugin-fake-server/client'

function ok<T>(data: T, message = '操作成功') {
  return {
    code: 200,
    message,
    data,
    timestamp: new Date().toISOString(),
  }
}

export default defineFakeRoute([
  {
    url: '/api/v1/oss/migration/statistics',
    response: () => ok({
      totalCount: 100,
      migratedCount: 60,
      pendingCount: 16,
      waitingSjhCount: 20,
      failedCount: 2,
      retryWaitCount: 2,
      migratingCount: 0,
      percentage: 60,
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
      pendingCount: 16,
      sampleSize: 16,
      sampleReadableCount: 15,
      sampleMissingCount: 1,
      sampleInvalidCount: 0,
      recommendedMode: 'pilot',
      recommendedAction: '先迁移 100 至 500 张真实图片，核对访问、下载与日志后再扩大批次',
      warnings: ['模拟数据中保留 1 条缺失文件，用于展示失败处理提示'],
    }),
  },
  {
    url: '/api/v1/oss/migration/jobs/:id/cancel',
    method: 'post',
    response: ({ params }) => {
      const routeParams = params as Record<string, string>
      return ok({
        id: Number(routeParams.id),
        status: 'cancelling',
        mode: 'pilot',
        totalCount: 500,
        processedCount: 120,
        failedCount: 1,
        rate: 24,
        cancelRequested: true,
      }, '已提交安全取消请求')
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
])

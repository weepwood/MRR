import type { SystemErrorEvent, SystemErrorOverview, SystemErrorStatus } from '@/api/modules/system-errors'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { demoError, demoOk, demoPage, hoursAgo } from '../mock-support/response'

const samples = [
  { module: 'storage', message: '演示：影像来源读取超时', level: 'ERROR' },
  { module: 'database', message: '演示：连接池等待超过阈值', level: 'WARN' },
  { module: 'exports', message: '演示：导出任务读取失败', level: 'ERROR' },
] as const

const events: SystemErrorEvent[] = Array.from({ length: 24 }, (_, index) => {
  const sample = samples[index % samples.length]
  const id = index + 1
  const occurrenceCount = 2 + index % 9
  const lastSeenAt = hoursAgo(index * 3)
  return {
    id,
    errorId: `DEMO-ERR-${String(id).padStart(3, '0')}`,
    fingerprint: `demo-fingerprint-${id}`,
    level: sample.level,
    module: sample.module,
    loggerName: `demo.${sample.module}`,
    messageSummary: sample.message,
    requestId: `demo-request-${id}`,
    firstSeenAt: hoursAgo(index * 3 + 2),
    lastSeenAt,
    occurrenceCount,
    recent24hCount: index < 8 ? occurrenceCount : 0,
    status: index % 5 === 0 ? 'RESOLVED' : index % 4 === 0 ? 'ACKNOWLEDGED' : 'OPEN',
    resolvedAt: index % 5 === 0 ? lastSeenAt : undefined,
  }
})

function overview(): SystemErrorOverview {
  return {
    totalGroups: events.length,
    totalOccurrences: events.reduce((sum, event) => sum + event.occurrenceCount, 0),
    openGroups: events.filter(event => event.status === 'OPEN').length,
    acknowledgedGroups: events.filter(event => event.status === 'ACKNOWLEDGED').length,
    resolvedGroups: events.filter(event => event.status === 'RESOLVED').length,
    errorGroups: events.filter(event => event.level === 'ERROR').length,
    warnGroups: events.filter(event => event.level === 'WARN').length,
    recentOccurrences: events.reduce((sum, event) => sum + (event.recent24hCount || 0), 0),
    recentErrorGroups: events.filter(event => event.level === 'ERROR' && event.status !== 'RESOLVED' && (event.recent24hCount || 0) > 0).length,
  }
}

export default defineFakeRoute([
  { url: '/api/v1/system-errors/overview', response: () => demoOk(overview()) },
  { url: '/api/v1/system-errors/modules', response: () => demoOk([...new Set(events.map(event => event.module))]) },
  {
    url: '/api/v1/system-errors',
    response: ({ query }) => {
      const keyword = String(query?.keyword ?? '').toLowerCase().trim()
      const since = query?.since ? Date.parse(String(query.since)) : 0
      const filtered = events.filter(event =>
        (!keyword || [event.errorId, event.messageSummary, event.requestId, event.module].some(value => value?.toLowerCase().includes(keyword)))
        && (!query?.module || event.module === query.module)
        && (!query?.level || event.level === query.level)
        && (!query?.status || event.status === query.status)
        && (!since || Date.parse(event.lastSeenAt) >= since),
      )
      return demoOk(demoPage(filtered, query?.page, query?.size))
    },
  },
  {
    url: '/api/v1/system-errors/:id',
    response: ({ params }) => {
      const item = events.find(event => event.id === Number(params.id))
      return item ? demoOk({ ...item, stackTrace: '合成异常摘要：无真实运行堆栈' }) : demoError(404, '演示错误组不存在')
    },
  },
  {
    url: '/api/v1/system-errors/:id/status',
    method: 'POST',
    response: ({ params, body }) => {
      const item = events.find(event => event.id === Number(params.id))
      if (!item) { return demoError(404, '演示错误组不存在') }
      const status = String(body?.status ?? '') as SystemErrorStatus
      if (!['OPEN', 'ACKNOWLEDGED', 'RESOLVED'].includes(status)) {
        return demoError(400, '无效演示状态')
      }
      item.status = status
      item.acknowledgedBy = status === 'OPEN' ? undefined : 'demo-admin'
      item.resolvedAt = status === 'RESOLVED' ? new Date().toISOString() : undefined
      return demoOk(null)
    },
  },
])

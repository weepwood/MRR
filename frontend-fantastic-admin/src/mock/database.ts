import type { DatabaseOverview, DatabaseTableStat, DataQualityIssue, DataQualitySummary } from '@/api/modules/database-monitoring'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { boundedInt, demoOk, hoursAgo } from '../mock-support/response'

const tables: DatabaseTableStat[] = [
  { schemaname: 'app', table_name: 'mr_scan', live_rows: 64, dead_rows: 2, table_bytes: 131072, index_bytes: 65536, total_bytes: 196608, seq_scan: 12, idx_scan: 240, last_autovacuum: hoursAgo(4), last_autoanalyze: hoursAgo(2) },
  { schemaname: 'app', table_name: 'access_log', live_rows: 72, dead_rows: 0, table_bytes: 65536, index_bytes: 32768, total_bytes: 98304, seq_scan: 6, idx_scan: 180, last_autoanalyze: hoursAgo(2) },
  { schemaname: 'app', table_name: 'mr_patient', live_rows: 32, dead_rows: 1, table_bytes: 32768, index_bytes: 16384, total_bytes: 49152, seq_scan: 2, idx_scan: 96, last_autoanalyze: hoursAgo(3) },
]
const database: DatabaseOverview = {
  database: { name: 'mrr_demo', version: 'PostgreSQL 16（演示）', size_bytes: 8388608, uptime_seconds: 172800, max_connections: 100 },
  connections: { total: 12, active: 3, idle: 9, idle_in_transaction: 0, waiting: 0 },
  transactions: { commits: 18420, rollbacks: 12, deadlocks: 0, temp_files: 2, temp_bytes: 65536, cache_hit_ratio: 99.2 },
  contention: { lock_waiters: 0, long_transactions: 0, longest_transaction_seconds: 0 },
  hikari: { available: true, maximum_pool_size: 20, minimum_idle: 5, active: 3, idle: 5, total: 8, pending: 0 },
  tables,
}
const issues: DataQualityIssue[] = [
  { id: 1, check_code: 'MISSING_SJH', check_name: '上架号缺失', severity: 'WARNING', entity_type: 'mr_scan', entity_id: 'demo-scan-01', detail: '合成样本：上架号尚未补齐', detected_at: hoursAgo(2) },
  { id: 2, check_code: 'UNLINKED_SCAN', check_name: '影像关联异常', severity: 'CRITICAL', entity_type: 'mr_scan', entity_id: 'demo-scan-02', detail: '合成样本：影像尚未关联主档', detected_at: hoursAgo(2) },
]
let runId = 1
let checkedAt = hoursAgo(2)

function quality(): DataQualitySummary {
  return {
    enabled: true,
    running: false,
    latestRun: { id: runId, status: 'SUCCESS', triggered_by: 'demo-admin', check_count: 3, total_issues: issues.length, critical_count: 1, warning_count: 1, started_at: checkedAt, completed_at: checkedAt },
    checks: [
      ...issues.map(issue => ({ check_code: issue.check_code, check_name: issue.check_name, severity: issue.severity, issue_count: 1, sampled_count: 1, checked_at: checkedAt })),
      { check_code: 'DUPLICATE_ARCHIVE', check_name: '重复病案主档', severity: 'WARNING', issue_count: 0, sampled_count: 0, checked_at: checkedAt },
    ],
  }
}

export default defineFakeRoute([
  { url: '/api/v1/system/database/overview', response: () => demoOk(database) },
  { url: '/api/v1/system/database/tables', response: ({ query }) => demoOk(tables.slice(0, boundedInt(query?.limit, 20, 100))) },
  { url: '/api/v1/system/data-quality/summary', response: () => demoOk(quality()) },
  { url: '/api/v1/system/data-quality/issues', response: ({ query }) => demoOk(issues.slice(0, boundedInt(query?.limit, 100, 300))) },
  {
    url: '/api/v1/system/data-quality/run',
    method: 'POST',
    response: () => {
      runId++
      checkedAt = new Date().toISOString()
      return demoOk(quality(), '演示检查已完成')
    },
  },
])

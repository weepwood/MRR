import type { ExportCenterJob, IntegritySummary, MaintenanceStatus, OperationAuditEntry, OperationsDiagnosticRun, OperationsOverview, PermissionMatrix, ReadinessSnapshot } from '@/api/modules/operations'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { boundedInt, demoError, demoOk, hoursAgo } from '../mock-support/response'

const startedAt = hoursAgo(48)
const maintenance: MaintenanceStatus = { enabled: false, reason: '', updatedAt: startedAt, updatedBy: 'demo-admin' }
const audits: OperationAuditEntry[] = [
  { id: 1, request_id: 'demo-ops-001', username: 'demo-admin', request_uri: '/api/v1/operations/readiness', method: 'GET', response_status: '200', execute_time: 18, access_time: hoursAgo(1) },
]
const jobs: ExportCenterJob[] = [
  { id: 'demo-export-001', owner_username: 'demo-admin', format: 'ZIP', scope: 'WHOLE_ARCHIVE', status: 'SUCCESS', planned_count: 14, processed_count: 14, failed_count: 0, estimated_bytes: 7340032, output_bytes: 6200000, file_name: 'demo-archive.zip', created_at: hoursAgo(3), completed_at: hoursAgo(2), download_count: 0 },
  { id: 'demo-export-002', owner_username: 'demo-operator', format: 'PDF', scope: 'SELECTED_IMAGES', status: 'FAILED', planned_count: 8, processed_count: 4, failed_count: 1, estimated_bytes: 4194304, output_bytes: 0, created_at: hoursAgo(2), error_message: '演示：影像源读取超时', download_count: 0 },
  { id: 'demo-export-003', owner_username: 'demo-admin', format: 'ZIP', scope: 'WHOLE_ARCHIVE', status: 'PROCESSING', planned_count: 60, processed_count: 24, failed_count: 0, estimated_bytes: 31457280, output_bytes: 0, created_at: hoursAgo(0.1), download_count: 0 },
]
const integrity: IntegritySummary = {
  status: 'READY',
  sampleScope: '合成演示样本，不代表医院数据',
  generatedAt: hoursAgo(1),
  totalActiveScans: 64,
  archiveCoverage: 96.875,
  ossCoverage: 75,
  missingSjh: 1,
  brokenLinks: 2,
  duplicateArchiveGroups: 0,
  tables: [
    { table: 'mr_scan', total: 64, linked: 62, unlinked: 2, coverage: 96.875 },
    { table: 'mr_patient', total: 32, linked: 32, unlinked: 0, coverage: 100 },
  ],
}
let previousVersion = ''
let previousCreatedAt = ''

function readiness(): ReadinessSnapshot {
  return {
    ready: true,
    readOnly: maintenance.enabled,
    automaticReadOnly: false,
    maintenanceReadOnly: maintenance.enabled,
    mode: maintenance.enabled ? 'READ_ONLY_MAINTENANCE' : 'READ_WRITE',
    checkedAt: new Date().toISOString(),
    maintenance: { enabled: maintenance.enabled },
    checks: [
      { code: 'database', name: '数据库连接', passed: true, severity: 'CRITICAL', message: '演示：连接正常', details: { simulated: true } },
      { code: 'storage', name: '影像存储', passed: true, severity: 'CRITICAL', message: '演示：样例存储可用', details: { simulated: true } },
      { code: 'backup', name: '最近备份', passed: false, severity: 'WARNING', message: '演示：等待备份验收', details: { simulated: true } },
    ],
  }
}

function overview(): OperationsOverview {
  const counts = { PENDING: 0, PROCESSING: 0, SUCCESS: 0, FAILED: 0 }
  for (const job of jobs) {
    const status = job.status as keyof typeof counts
    if (status in counts) {
      counts[status]++
    }
  }
  return {
    generatedAt: new Date().toISOString(),
    readiness: readiness(),
    maintenance: { enabled: maintenance.enabled },
    runtime: { uptimeMs: Date.now() - Date.parse(startedAt), startedAt, javaVersion: '21（演示）', processors: 8, heapUsedBytes: 536870912, heapCommittedBytes: 1073741824, heapMaxBytes: 2147483648, applicationVersion: '演示快照' },
    taskSummary: { available: true, counts, active: counts.PENDING + counts.PROCESSING, failed: counts.FAILED },
    recentServerErrors: 3,
    recentServerErrorsAvailable: true,
    quickLinks: [{ label: '系统监控', path: '/monitoring' }, { label: '运行错误中心', path: '/runtime-errors' }, { label: '数据关系', path: '/data-relations' }],
  }
}

function diagnostics(): OperationsDiagnosticRun {
  const snapshot = readiness()
  const checks = snapshot.checks.map(check => ({ ...check, suggestion: check.passed ? '保持现状' : '在授权环境核查备份记录', actionLabel: '查看监控', actionPath: '/monitoring' }))
  return { startedAt: snapshot.checkedAt, completedAt: snapshot.checkedAt, mode: snapshot.mode, readOnly: snapshot.readOnly, summary: { total: checks.length, passed: checks.filter(check => check.passed).length, failed: checks.filter(check => !check.passed).length, critical: checks.filter(check => !check.passed && check.severity === 'CRITICAL').length, warnings: checks.filter(check => !check.passed && check.severity === 'WARNING').length }, checks }
}

function recordMaintenance(enabled: boolean, reason: string) {
  Object.assign(maintenance, { enabled, reason, updatedAt: new Date().toISOString(), updatedBy: 'demo-admin' })
  audits.unshift({ id: audits.length + 1, request_id: `demo-ops-${audits.length + 1}`, username: 'demo-admin', request_uri: enabled ? '/api/v1/operations/maintenance/enable' : '/api/v1/operations/maintenance/disable', method: 'POST', response_status: '200', execute_time: 12, access_time: maintenance.updatedAt })
  return demoOk({ ...maintenance })
}

function permissionMatrix(): PermissionMatrix {
  return {
    generatedAt: new Date().toISOString(),
    roles: [
      { code: 'ADMIN', name: '管理员（演示）', permissions: ['*'] },
      { code: 'AUDITOR', name: '审计员（演示）', permissions: ['audit:read'] },
    ],
    endpoints: [
      { key: 'GET /api/v1/operations/overview', method: 'GET', path: '/api/v1/operations/overview', operation: '运维总览（样例）', policy: 'PERMISSION', requiredPermissions: ['system:read'], roleAccess: { ADMIN: true, AUDITOR: false } },
      { key: 'GET /api/v1/logs/search', method: 'GET', path: '/api/v1/logs/search', operation: '审计查询（样例）', policy: 'PERMISSION', requiredPermissions: ['audit:read'], roleAccess: { ADMIN: true, AUDITOR: true } },
    ],
    previousVersion: previousVersion || undefined,
    previousCreatedAt: previousCreatedAt || undefined,
    diff: { available: Boolean(previousVersion), changeCount: 0, changes: [] },
  }
}

export default defineFakeRoute([
  { url: '/api/v1/operations/overview', response: () => demoOk(overview()) },
  { url: '/api/v1/operations/readiness', response: () => demoOk(readiness()) },
  { url: '/api/v1/operations/readiness/refresh', method: 'POST', response: () => demoOk(readiness()) },
  { url: '/api/v1/operations/maintenance', response: () => demoOk({ ...maintenance }) },
  {
    url: '/api/v1/operations/maintenance/enable',
    method: 'POST',
    response: ({ body }) => {
      const reason = String(body?.reason ?? '').trim()
      return reason ? recordMaintenance(true, reason.slice(0, 200)) : demoError(400, '请输入演示维护原因')
    },
  },
  { url: '/api/v1/operations/maintenance/disable', method: 'POST', response: () => recordMaintenance(false, '') },
  { url: '/api/v1/operations/diagnostics/run', method: 'POST', response: () => demoOk(diagnostics()) },
  { url: '/api/v1/operations/diagnostic-report', response: () => demoOk({ simulated: true, overview: overview(), diagnostics: diagnostics() }) },
  { url: '/api/v1/operations/integrity', response: () => demoOk(integrity) },
  { url: '/api/v1/operations/exports', response: ({ query }) => demoOk(jobs.slice(0, boundedInt(query?.limit, 100, 100))) },
  { url: '/api/v1/operations/operation-audit', response: ({ query }) => demoOk(audits.slice(0, boundedInt(query?.limit, 50, 100))) },
  { url: '/api/v1/operations/permission-matrix', response: () => demoOk(permissionMatrix()) },
  {
    url: '/api/v1/operations/permission-matrix/snapshots',
    method: 'POST',
    response: ({ body }) => {
      previousVersion = String(body?.version || 'demo-snapshot').slice(0, 100)
      previousCreatedAt = new Date().toISOString()
      return demoOk({ version: previousVersion, createdBy: 'demo-admin', saved: true })
    },
  },
  {
    url: '/api/v1/operations/image-source',
    response: ({ query }) => {
      // Only a known synthetic sample is resolved; arbitrary production identifiers are never echoed.
      const found = String(query?.bah ?? '') === '26070000' || String(query?.sjh ?? '') === '90000001' || String(query?.imageId ?? '') === '1'
      return demoOk({
        diagnosedAt: new Date().toISOString(),
        found,
        scan: found ? { id: 1, bah: '26070000', sjh: '90000001', filename: 'demo-page-001.jpg' } : {},
        selectedSource: found ? 'DIRECT' : '',
        selectedUrl: '',
        fallbackReason: found ? '演示：不连接真实影像源' : '未找到合成样本',
        steps: [{ code: 'mock_lookup', success: found, message: found ? '已匹配合成样本' : '样本不存在', details: { simulated: true } }],
      })
    },
  },
])

import type { AuthTestResult } from '@/api/modules/auth-test'

export const OSS_CHECKS = [
  { id: 'browser', name: 'OSS 目录可读性', description: '最多读取默认受管目录的 20 个对象，仅显示数量。', path: '/api/v1/oss/browser?maxKeys=20' },
  { id: 'readiness', name: '迁移就绪检查', description: '抽样检查 10 条源文件，不创建迁移任务。', path: '/api/v1/oss/migration/readiness?sampleSize=10' },
  { id: 'statistics', name: '迁移统计检查', description: '读取已有迁移统计，核对接口是否可用。', path: '/api/v1/oss/migration/statistics' },
  { id: 'jobs', name: '最近任务检查', description: '只查询最近 5 条任务，不启动、取消或重试任务。', path: '/api/v1/oss/migration/jobs?page=1&size=5' },
] as const

export type OssCheckId = typeof OSS_CHECKS[number]['id']
export interface OssCheckSummary {
  outcome: 'passed' | 'warning' | 'failed'
  summary: string
}
function object(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}
function count(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
}
const invalid: OssCheckSummary = { outcome: 'failed', summary: '响应格式不完整，无法确认检查结果。' }

/** Only return allowlisted status and counts; never retain the raw response. */
export function summarizeOssCheck(id: OssCheckId, result: AuthTestResult): OssCheckSummary {
  if (result.status === 401) { return { outcome: 'failed', summary: '当前会话未获认证，请检查登录状态。' } }
  if (result.status === 403) { return { outcome: 'failed', summary: '当前账号无权读取 OSS，请核对 record:read 权限。' } }
  if (result.status < 200 || result.status >= 300) { return { outcome: 'failed', summary: '读取接口失败，请检查后端服务状态。' } }
  const envelope = object(result.data)
  if (typeof envelope.code !== 'number' && typeof envelope.code !== 'string') { return invalid }
  if (envelope.code !== 200 && envelope.code !== '200') { return { outcome: 'failed', summary: '业务接口未成功，请通过运维诊断检查原因。' } }
  const data = object(envelope.data)
  if (id === 'browser') {
    if (data.configured === false) { return { outcome: 'warning', summary: 'OSS 未配置，本次未验证目录可读性。' } }
    if (data.configured !== true || !count(data.loadedDirectories) || !count(data.loadedFiles)) { return invalid }
    if (data.loadedDirectories + data.loadedFiles > 20) { return invalid }
    return { outcome: 'passed', summary: `目录读取成功：${data.loadedDirectories} 个目录，${data.loadedFiles} 个文件。仅代表本次有限读取。` }
  }
  if (id === 'readiness') {
    if (typeof data.ready !== 'boolean' || typeof data.ossConfigured !== 'boolean' || !count(data.sampleSize) || !count(data.sampleReadableCount)) { return invalid }
    if (data.sampleSize > 10 || data.sampleReadableCount > data.sampleSize || (data.ready && !data.ossConfigured)) { return invalid }
    return { outcome: data.ready ? 'passed' : 'warning', summary: `${data.ready ? '迁移检查已就绪' : '迁移尚未就绪'}；OSS ${data.ossConfigured ? '已配置' : '未配置'}；样本 ${data.sampleReadableCount}/${data.sampleSize} 可读。未启动迁移。` }
  }
  if (id === 'statistics') {
    if (!count(data.totalCount) || !count(data.migratedCount) || !count(data.failedCount)) { return invalid }
    return { outcome: 'passed', summary: `统计读取成功：总数 ${data.totalCount}，已迁移 ${data.migratedCount}，失败 ${data.failedCount}。接口可读不代表迁移均成功。` }
  }
  if (!Array.isArray(data.list) || data.list.length > 5) { return invalid }
  return { outcome: 'passed', summary: `任务列表读取成功：本次返回 ${data.list.length} 条任务。未读取或展示任务对象详情。` }
}

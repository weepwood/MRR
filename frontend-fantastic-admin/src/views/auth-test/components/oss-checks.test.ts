import type { AuthTestResult } from '@/api/modules/auth-test'
import { describe, expect, it } from 'vitest'
import { OSS_CHECKS, summarizeOssCheck } from './oss-checks'

const response = (data: unknown, status = 200): AuthTestResult => ({ status, statusText: '', durationMs: 1, headers: {}, data })

describe('oSS检查结果白名单', () => {
  it('四项固定只读路径有明确上限', () => {
    expect(OSS_CHECKS).toHaveLength(4)
    expect(OSS_CHECKS[0].path).toBe('/api/v1/oss/browser?maxKeys=20')
    expect(OSS_CHECKS[1].path).toContain('sampleSize=10')
    expect(OSS_CHECKS[3].path).toContain('size=5')
  })
  it.each([401, 403, 503])('hTTP %i 不误报通过，也不传播错误原文', (status) => {
    const result = summarizeOssCheck('browser', response({ message: 'private-endpoint-and-key' }, status))
    expect(result.outcome).toBe('failed')
    expect(JSON.stringify(result)).not.toContain('private')
  })
  it('hTTP200业务失败也失败', () => {
    expect(summarizeOssCheck('browser', response({ code: 500, message: 'secret' })).outcome).toBe('failed')
  })
  it('未配置与未就绪单独提示需关注', () => {
    expect(summarizeOssCheck('browser', response({ code: 200, data: { configured: false } })).outcome).toBe('warning')
    expect(summarizeOssCheck('readiness', response({ code: 200, data: { ready: false, ossConfigured: true, sampleSize: 10, sampleReadableCount: 3 } })).outcome).toBe('warning')
  })
  it.each([null, {}, { configured: true, loadedDirectories: 'private', loadedFiles: -1 }])('缺失或无效响应不得显示通过：%j', (data) => {
    expect(summarizeOssCheck('browser', response({ code: 200, data })).outcome).toBe('failed')
  })
  it('合法目录只返回数量，不传播敏感字段', () => {
    const result = summarizeOssCheck('browser', response({ code: '200', data: { configured: true, loadedDirectories: 1, loadedFiles: 0, entries: [{ name: 'private-record' }], endpoint: 'private-endpoint', rootPrefix: 'private-root' } }))
    expect(result.outcome).toBe('passed')
    expect(result.summary).toContain('1 个目录，0 个文件')
    expect(JSON.stringify(result)).not.toContain('private')
  })
  it('统计中的零值是合法结果，非有限计数被拒绝', () => {
    expect(summarizeOssCheck('statistics', response({ code: 200, data: { totalCount: 0, migratedCount: 0, failedCount: 0 } })).outcome).toBe('passed')
    expect(summarizeOssCheck('statistics', response({ code: 200, data: { totalCount: Infinity } })).outcome).toBe('failed')
  })
  it('任务只保留返回条数，不显示操作者、范围或错误', () => {
    const result = summarizeOssCheck('jobs', response({ code: 200, data: { list: [{ createdBy: 'private-user', scopeValue: 'private-record', errorMessage: 'private-path' }] } }))
    expect(result.outcome).toBe('passed')
    expect(result.summary).toContain('1 条任务')
    expect(JSON.stringify(result)).not.toContain('private')
    expect(summarizeOssCheck('jobs', response({ code: 200, data: { list: Array.from({ length: 6 }) } })).outcome).toBe('failed')
  })
})

describe('诊断边界一致性', () => {
  it.each([null, '<html>unexpected</html>', {}])('非业务响应识别为格式无效：%j', (body) => {
    expect(summarizeOssCheck('browser', response(body)).summary).toContain('响应格式不完整')
  })
  it.each([
    { ready: true, ossConfigured: false, sampleSize: 10, sampleReadableCount: 10 },
    { ready: true, ossConfigured: true, sampleSize: 10, sampleReadableCount: 11 },
    { ready: true, ossConfigured: true, sampleSize: 11, sampleReadableCount: 10 },
  ])('矛盾或越界就绪数据不误报通过：%j', (data) => {
    expect(summarizeOssCheck('readiness', response({ code: 200, data })).outcome).toBe('failed')
  })
  it('目录返回计数不能超过请求边界', () => {
    expect(summarizeOssCheck('browser', response({ code: 200, data: { configured: true, loadedDirectories: 11, loadedFiles: 10 } })).outcome).toBe('failed')
  })
})

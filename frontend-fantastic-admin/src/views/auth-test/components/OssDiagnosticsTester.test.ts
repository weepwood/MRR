import type { AuthTestResult } from '@/api/modules/auth-test'
import { shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { executeAuthTestRequest } from '@/api/modules/auth-test'
import OssDiagnosticsTester from './OssDiagnosticsTester.vue'

vi.mock('@/api/modules/auth-test', () => ({ executeAuthTestRequest: vi.fn() }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ token: 'synthetic-session' }) }))
const success: AuthTestResult = { status: 200, statusText: '', durationMs: 2, headers: {}, data: { code: 200, data: { configured: true, loadedDirectories: 0, loadedFiles: 0 } } }
const wrappers: ReturnType<typeof shallowMount>[] = []
function setup() {
  const wrapper = shallowMount(OssDiagnosticsTester, { global: { stubs: { 'el-card': true, 'el-alert': true, 'el-button': true, 'el-tag': true } } })
  wrappers.push(wrapper)
  return { wrapper, vm: wrapper.vm as unknown as { run: (ids: readonly ('browser' | 'jobs')[]) => Promise<void>, busy: boolean } }
}
beforeEach(() => { vi.clearAllMocks(); vi.mocked(executeAuthTestRequest).mockResolvedValue(success) })
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('oSS检查执行生命周期', () => {
  it('不自动请求；使用当前会话和固定GET记录白名单结论', async () => {
    const { wrapper, vm } = setup()
    expect(executeAuthTestRequest).not.toHaveBeenCalled()
    await vm.run(['browser'])
    expect(executeAuthTestRequest).toHaveBeenCalledWith({ method: 'GET', path: '/api/v1/oss/browser?maxKeys=20', token: 'synthetic-session' })
    expect(wrapper.emitted('record')?.[0]?.[0]).toEqual({ name: 'OSS 目录可读性', method: 'GET', path: '/api/v1/oss/browser?maxKeys=20', status: 200, durationMs: 2, outcome: 'passed' })
  })
  it('重复执行被阻止，离页后不记录迟到结果或继续下一项', async () => {
    let resolve!: (value: AuthTestResult) => void
    vi.mocked(executeAuthTestRequest).mockReturnValueOnce(new Promise(done => resolve = done))
    const { wrapper, vm } = setup()
    const task = vm.run(['browser', 'jobs'])
    await vm.run(['browser'])
    expect(executeAuthTestRequest).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    resolve(success)
    await task
    expect(executeAuthTestRequest).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('record')).toBeUndefined()
  })
  it('网络错误不暴露异常，执行状态可恢复', async () => {
    vi.mocked(executeAuthTestRequest).mockRejectedValueOnce(new Error('private-secret-endpoint'))
    const { wrapper, vm } = setup()
    await vm.run(['browser'])
    expect(vm.busy).toBe(false)
    expect(wrapper.emitted('record')?.[0]?.[0]).toMatchObject({ status: 0, outcome: 'failed' })
    expect(JSON.stringify(wrapper.emitted())).not.toContain('private')
    await vm.run(['browser'])
    expect(wrapper.emitted('record')).toHaveLength(2)
  })
})

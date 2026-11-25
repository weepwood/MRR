import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import NginxRuntimeCard from '../components/NginxRuntimeCard.vue'

const api = vi.hoisted(() => ({ getNginxRuntime: vi.fn(), getNginxLogTail: vi.fn() }))
vi.mock('@/api/modules/operations', () => api)
const session = reactive({ token: 'synthetic', read: true, logs: true })
vi.mock('@/utils/session', () => ({ hasPermission: (p: string) => p === 'system:read' ? session.read : session.logs }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session }))
const time = '2026-11-25T00:00:00Z'
function card() {
  return mount(NginxRuntimeCard, { global: { stubs: {
    'MrrSectionCard': { template: '<section><slot /></section>' },
    'AppError': { props: ['message'], template: '<p>{{ message }}</p>' },
    'el-button': { template: '<button><slot /></button>' },
    'el-tag': { template: '<span><slot /></span>' },
    'el-radio-group': { emits: ['update:modelValue'], template: '<div><slot /><button @click="$emit(\'update:modelValue\',\'error\')">切换错误</button></div>' },
    'el-radio-button': true,
    'el-table': { name: 'LogTableStub', props: ['data'], template: '<div><slot /></div>' },
    'el-table-column': true,
  } } })
}
const button = (w: ReturnType<typeof card>, label: string) => w.findAll('button').find(b => b.text() === label)!
describe('nginx 运行观察', () => {
  beforeEach(() => { vi.resetAllMocks(); session.read = true; session.logs = true; session.token = 'synthetic' })
  it('仅按需核对进程，不将匹配视为服务健康；没有日志权限不能请求', async () => {
    session.logs = false
    const w = card()
    expect(api.getNginxRuntime).not.toHaveBeenCalled()
    expect(api.getNginxLogTail).not.toHaveBeenCalled()
    expect(button(w, '读取脱敏日志')).toBeUndefined()
    api.getNginxRuntime.mockResolvedValue({ state: 'PROCESS_MATCHED', observedAt: time })
    await button(w, '核对本地进程').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('PID 对应本地安装程序正在运行')
    session.read = false
    await flushPromises()
    expect(w.text()).toBe('')
    w.unmount()
  })
  it('读取脱敏摘要并显示截断，切换日志类型取消请求，拒绝迟到响应', async () => {
    const w = card()
    const entry = { time, method: 'GET', status: 502, requestSeconds: 0.25, severity: null }
    api.getNginxLogTail.mockResolvedValueOnce({ state: 'AVAILABLE', kind: 'access', observedAt: time, truncated: true, omitted: 2, entries: [entry] })
    await button(w, '读取脱敏日志').trigger('click'); await flushPromises()
    expect(w.text()).toContain('已截断')
    expect(w.findComponent({ name: 'LogTableStub' }).props('data')).toEqual([entry])
    let resolve!: (v: unknown) => void
    api.getNginxLogTail.mockImplementationOnce(() => new Promise(r => resolve = r))
    await button(w, '读取脱敏日志').trigger('click')
    const signal = api.getNginxLogTail.mock.calls.at(-1)![1] as AbortSignal
    await button(w, '切换错误').trigger('click'); await flushPromises()
    expect(signal.aborted).toBe(true)
    resolve({ state: 'AVAILABLE', kind: 'access', observedAt: time, truncated: false, omitted: 0, entries: [entry] })
    await flushPromises()
    expect(w.findComponent({ name: 'LogTableStub' }).exists()).toBe(false)
    api.getNginxLogTail.mockResolvedValueOnce({ state: 'DISABLED', kind: 'error', observedAt: time, truncated: false, omitted: 0, entries: [] })
    await button(w, '读取脱敏日志').trigger('click'); await flushPromises()
    expect(api.getNginxLogTail.mock.calls.at(-1)![0]).toBe('error')
    expect(w.text()).toContain('未启用')
    session.logs = false
    await flushPromises()
    expect(button(w, '读取脱敏日志')).toBeUndefined()
    w.unmount()
  })
  it('错误与不一致响应不显示日志，退出会话清空', async () => {
    const w = card()
    api.getNginxLogTail.mockResolvedValueOnce({ state: 'AVAILABLE', kind: 'error', entries: [] })
    await button(w, '读取脱敏日志').trigger('click'); await flushPromises()
    expect(w.text()).toContain('运行观察或日志读取失败')
    api.getNginxRuntime.mockRejectedValueOnce(new Error('synthetic'))
    await button(w, '核对本地进程').trigger('click'); await flushPromises()
    expect(w.text()).toContain('运行观察或日志读取失败')
    session.token = 'other'
    await flushPromises()
    expect(w.text()).not.toContain('运行观察或日志读取失败')
    w.unmount()
  })
})

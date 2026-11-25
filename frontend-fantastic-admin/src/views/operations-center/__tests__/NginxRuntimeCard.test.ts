import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import NginxRuntimeCard from '../components/NginxRuntimeCard.vue'

const api = vi.hoisted(() => ({ getNginxRuntime: vi.fn(), getNginxLogTail: vi.fn(), observeWindowsGateway: vi.fn() }))
vi.mock('@/api/modules/operations', () => api)
const session = reactive({ token: 'synthetic', read: true, logs: true, manage: true })
vi.mock('@/utils/session', () => ({ hasPermission: (p: string) => p === 'system:read' ? session.read : p === 'system:manage' ? session.manage : session.logs }))
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
  beforeEach(() => { vi.resetAllMocks(); session.read = true; session.logs = true; session.manage = true; session.token = 'synthetic' })
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
  it('windows 查询必须明确操作且区分服务状态、监听及身份变化', async () => {
    const w = card()
    expect(api.observeWindowsGateway).not.toHaveBeenCalled()
    api.observeWindowsGateway.mockResolvedValueOnce({ state: 'OBSERVED', serviceState: 'RUNNING', listenerState: 'OBSERVED', ports: [80, 443], observedAt: time })
    await button(w, '核对 Windows 服务与 TCP 监听').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('MRR-Gateway 服务：运行中')
    expect(w.text()).toContain('80、443')
    expect(w.text()).toContain('不证明两者属于同一启动链')
    api.observeWindowsGateway.mockResolvedValueOnce({ state: 'OBSERVED', serviceState: 'RUNNING', listenerState: 'IDENTITY_CHANGED', ports: [], observedAt: time })
    await button(w, '核对 Windows 服务与 TCP 监听').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('查询期间进程身份变化')
    expect(w.text()).not.toContain('80、443')
    session.manage = false
    await flushPromises()
    expect(button(w, '核对 Windows 服务与 TCP 监听')).toBeUndefined()
    expect(w.text()).not.toContain('MRR-Gateway 服务：运行中')
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

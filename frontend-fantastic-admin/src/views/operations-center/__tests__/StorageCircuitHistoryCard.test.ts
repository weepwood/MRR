import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import StorageCircuitHistoryCard from '../components/StorageCircuitHistoryCard.vue'

const api = vi.hoisted(() => ({ getStorageCircuitHistory: vi.fn() }))
vi.mock('@/api/modules/operations', () => api)
const session = reactive({ token: 'synthetic', allowed: true })
vi.mock('@/utils/session', () => ({ hasPermission: () => session.allowed }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session }))
const event = { circuitId: '00000000-0000-4000-8000-000000000001', sequence: 1, sourceKind: 'NAS', nodeOrdinal: 1, eventType: 'OPENED', occurredAt: '2026-11-01T00:00:00Z', collectedAt: '2026-11-01T00:00:10Z' }
function result(hours = 24, events: unknown[] = [], enabled = true, truncated = false) {
  return { hours, events, enabled, truncated }
}
function card() {
  return mount(StorageCircuitHistoryCard, { global: { stubs: {
    'MrrSectionCard': { template: '<section><slot /></section>' },
    'AppEmpty': { props: ['description'], template: '<p>{{ description }}</p>' },
    'AppError': { props: ['message'], template: '<p>{{ message }}</p>' },
    'el-radio-group': { emits: ['update:modelValue'], template: '<div><slot /><button @click="$emit(\'update:modelValue\',168)">七天</button></div>' },
    'el-radio-button': true,
    'el-button': { template: '<button><slot /></button>' },
    'el-skeleton': true,
    'el-table': { name: 'CircuitTableStub', props: ['data', 'rowKey'], template: '<div v-for="row in data" :key="rowKey(row)"><slot /></div>' },
    'el-table-column': true,
  } } })
}
describe('持久存储熔断事件', () => {
  beforeEach(() => { vi.resetAllMocks(); session.allowed = true; session.token = 'synthetic' })
  it('展示真实事件计数、截断提示与独立运行实例键', async () => {
    api.getStorageCircuitHistory.mockResolvedValueOnce(result(24, [event], true, true))
    const wrapper = card()
    await flushPromises()
    expect(wrapper.text()).toContain('仅展示最近 100 条事件')
    expect(wrapper.text()).toContain('运行实例标识不用于跨重启匹配节点')
    const table = wrapper.findComponent({ name: 'CircuitTableStub' })
    expect(table.exists()).toBe(true)
    expect(table.props('data')).toEqual([event])
    expect((table.props('rowKey') as (row: typeof event) => string)(event)).toBe(`${event.circuitId}-1`)
    wrapper.unmount()
  })
  it('时间范围切换取消旧请求，权限撤销时清空', async () => {
    let resolve!: (value: unknown) => void
    api.getStorageCircuitHistory.mockImplementationOnce(() => new Promise(r => resolve = r))
    const wrapper = card()
    const signal = api.getStorageCircuitHistory.mock.calls[0][1] as AbortSignal
    api.getStorageCircuitHistory.mockResolvedValueOnce(result(168))
    await wrapper.findAll('button').find(b => b.text() === '七天')!.trigger('click')
    await flushPromises()
    expect(signal.aborted).toBe(true)
    resolve(result(24, [event]))
    await flushPromises()
    expect(wrapper.text()).toContain('当前范围暂无已采集事件')
    session.allowed = false
    await flushPromises()
    expect(wrapper.text()).toBe('')
    wrapper.unmount()
  })
  it('区分禁用、契约错误、请求失败和会话变更', async () => {
    api.getStorageCircuitHistory.mockResolvedValueOnce(result(24, [], false))
    const wrapper = card()
    await flushPromises()
    expect(wrapper.text()).toContain('持久熔断事件采集尚未启用')
    const refresh = () => wrapper.findAll('button').find(b => b.text() === '刷新事件')!
    api.getStorageCircuitHistory.mockResolvedValueOnce(result(168))
    await refresh().trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('熔断历史读取失败')
    api.getStorageCircuitHistory.mockRejectedValueOnce(new Error('synthetic'))
    await refresh().trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('熔断历史读取失败')
    api.getStorageCircuitHistory.mockResolvedValueOnce(result(24, [event]))
    await refresh().trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('已采集 1 条事件')
    session.token = 'other'
    await flushPromises()
    expect(wrapper.text()).not.toContain('已采集 1 条事件')
    wrapper.unmount()
  })
})

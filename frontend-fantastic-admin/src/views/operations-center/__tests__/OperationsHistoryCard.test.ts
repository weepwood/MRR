import type { OperationsHistorySample } from '@/api/modules/operations'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import OperationsHistoryCard from '../components/OperationsHistoryCard.vue'

const api = vi.hoisted(() => ({ getOperationsHistory: vi.fn() }))
vi.mock('@/api/modules/operations', () => api)
const session = reactive({ token: 'synthetic', allowed: true })
vi.mock('@/utils/session', () => ({ hasPermission: () => session.allowed }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session }))
const start = Date.parse('2026-11-01T00:00:00Z')
function sample(i: number, mode: OperationsHistorySample['mode'], nodes: number | null): OperationsHistorySample {
  return {
    sampledAt: new Date(start + i * 300000).toISOString(),
    readinessCheckedAt: new Date(start + i * 300000 - 1000).toISOString(),
    mode,
    criticalFailures: mode === 'READ_WRITE' ? 0 : 1,
    heapUsedBytes: 128 * 1048576,
    heapMaxBytes: 512 * 1048576,
    scanTotalBytes: i === 0 ? null : 2 * 1073741824,
    storageOpenNodes: nodes,
    storageWaiting: null,
  }
}
function payload(samples: OperationsHistorySample[], enabled = true, hours = 24) {
  return { enabled, hours, bucketSeconds: 300, from: new Date(start).toISOString(), to: new Date(start + hours * 3600000).toISOString(), samples }
}
function card() {
  return mount(OperationsHistoryCard, { global: { stubs: {
    'MrrSectionCard': { template: '<section><slot /></section>' },
    'AppEmpty': { props: ['description'], template: '<p>{{ description }}</p>' },
    'AppError': { props: ['message'], template: '<p>{{ message }}</p>' },
    'MrrChart': { name: 'MrrChart', props: ['option'], template: '<div class="chart-stub" />' },
    'el-radio-group': { props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /><button @click="$emit(\'update:modelValue\',168)">七天</button></div>' },
    'el-radio-button': true,
    'el-button': { template: '<button><slot /></button>' },
    'el-skeleton': true,
  } } })
}
describe('持久运维历史', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    session.allowed = true
    session.token = 'synthetic'
  })
  it('保留采样缺口和未知值，仅相邻采样生成观察事件', async () => {
    api.getOperationsHistory.mockResolvedValue(payload([
      sample(0, 'READ_WRITE', 0),
      sample(1, 'READ_ONLY_DEGRADED', 1),
      sample(3, 'READ_WRITE', 0),
      sample(4, 'READ_ONLY_MAINTENANCE', null),
    ]))
    const wrapper = card()
    await flushPromises()
    expect(wrapper.text()).toContain('正常读写 → 自动降级；熔断冷却节点 0 → 1')
    expect(wrapper.text()).toContain('正常读写 → 主动维护')
    expect(wrapper.text()).not.toContain('自动降级 → 正常读写')
    const charts = wrapper.findAllComponents({ name: 'MrrChart' })
    expect(charts).toHaveLength(3)
    const resource = charts[0].props('option') as any
    const risk = charts[1].props('option') as any
    const capacity = charts[2].props('option') as any
    expect(resource.series[0].data.slice(0, 4)).toEqual([[start, 128], [start + 300000, 128], [start + 600000, null], [start + 900000, 128]])
    expect(resource.series[0].connectNulls).toBe(false)
    expect(risk.series[2].data[0][1]).toBeNull()
    expect(capacity.series[0].data[0][1]).toBeNull()
    expect(capacity.series[0].data[1][1]).toBe(2)
    wrapper.unmount()
  })
  it('七天切换取消旧请求，迟到响应不能覆盖；权限撤销清理数据', async () => {
    let resolve!: (value: unknown) => void
    api.getOperationsHistory.mockImplementationOnce(() => new Promise(r => resolve = r))
    const wrapper = card()
    const oldSignal = api.getOperationsHistory.mock.calls[0][1] as AbortSignal
    api.getOperationsHistory.mockResolvedValueOnce(payload([], true, 168))
    await wrapper.findAll('button').find(b => b.text() === '七天')!.trigger('click')
    await flushPromises()
    expect(oldSignal.aborted).toBe(true)
    expect(api.getOperationsHistory.mock.calls[1][0]).toBe(168)
    resolve(payload([sample(0, 'READ_WRITE', 0)]))
    await flushPromises()
    expect(wrapper.text()).toContain('当前范围暂无历史采样')
    expect(wrapper.findAll('.chart-stub')).toHaveLength(0)
    session.allowed = false
    await flushPromises()
    expect(wrapper.text()).toBe('')
    wrapper.unmount()
  })
  it('明确区分关闭、无数据和契约错误；会话切换清理', async () => {
    api.getOperationsHistory.mockResolvedValueOnce(payload([], false))
    const wrapper = card()
    await flushPromises()
    expect(wrapper.text()).toContain('持久历史采集尚未启用')
    const refresh = () => wrapper.findAll('button').find(b => b.text() === '刷新历史')!
    api.getOperationsHistory.mockResolvedValueOnce(payload([], true, 168))
    await refresh().trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('历史采样读取失败')
    api.getOperationsHistory.mockRejectedValueOnce(new Error('synthetic'))
    await refresh().trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('历史采样读取失败')
    api.getOperationsHistory.mockResolvedValueOnce(payload([sample(0, 'READ_WRITE', 0)]))
    await refresh().trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.chart-stub')).toHaveLength(3)
    session.token = 'other'
    await flushPromises()
    expect(wrapper.findAll('.chart-stub')).toHaveLength(0)
    wrapper.unmount()
  })
})

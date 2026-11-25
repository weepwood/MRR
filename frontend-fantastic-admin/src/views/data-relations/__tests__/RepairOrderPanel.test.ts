import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RepairOrderPanel from '../RepairOrderPanel.vue'

const api = vi.hoisted(() => ({
  listRepairOrders: vi.fn(),
  getRepairEvents: vi.fn(),
  proposeRepairOrder: vi.fn(),
  actOnRepairOrder: vi.fn(),
}))
const confirm = vi.hoisted(() => vi.fn())
vi.mock('@/api/modules/repair-orders', () => api)
vi.mock('element-plus', () => ({ ElMessageBox: { confirm } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ token: 'synthetic' }) }))
const verified = { id: 1, issueId: 10, scanId: 4, targetArchiveId: 2, status: 'VERIFIED', proposedBy: 11, approvedBy: 12 }
const proposed = { ...verified, id: 2, issueId: 20, status: 'PROPOSED', proposedBy: 13, approvedBy: null, reopenedFrom: 1 }

function panel(issueId = 20) {
  return mount(RepairOrderPanel, {
    props: { issueId, deterministic: true },
    global: {
      stubs: {
        'MrrSectionCard': { template: '<section><slot name="actions" /><slot /></section>' },
        'AppEmpty': true,
        'AppError': true,
        'el-button': { template: '<button><slot /></button>' },
        'el-alert': true,
        'el-tag': true,
        'el-table': true,
        'el-table-column': true,
        'el-timeline': true,
        'el-timeline-item': true,
      },
    },
  })
}

describe('修复工单异常重开', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.listRepairOrders.mockResolvedValue({ data: { enabled: true, actorId: 13, orders: [verified] } })
    api.getRepairEvents.mockResolvedValue({ data: [] })
    confirm.mockResolvedValue(undefined)
  })
  it('新异常明确确认后重新提议，仍等待新审批并显示前序', async () => {
    const wrapper = panel()
    await flushPromises()
    const button = wrapper.findAll('button').find(b => b.text() === '重新提交工单')!
    confirm.mockRejectedValueOnce('cancel')
    await button.trigger('click')
    await flushPromises()
    expect(api.proposeRepairOrder).not.toHaveBeenCalled()
    api.proposeRepairOrder.mockResolvedValue({ data: proposed })
    api.listRepairOrders.mockResolvedValue({ data: { enabled: true, actorId: 13, orders: [proposed, { ...verified, status: 'SUPERSEDED' }] } })
    await button.trigger('click')
    await flushPromises()
    expect(api.proposeRepairOrder).toHaveBeenCalledExactlyOnceWith(20)
    expect(wrapper.text()).toContain('前序工单 #1')
    expect(wrapper.text()).not.toContain('重新提交工单')
    expect(api.actOnRepairOrder).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('装箱工单保留独立实体标识，不将同号影像当成目标', async () => {
    const wrapper = panel()
    await flushPromises()
    const box = { ...proposed, scanId: null, entityType: 'mr_archive_box_record', entityId: '9007199254740993' }
    api.proposeRepairOrder.mockResolvedValue({ data: box })
    api.listRepairOrders.mockResolvedValue({ data: { enabled: true, actorId: 13, orders: [box] } })
    await wrapper.findAll('button').find(b => b.text() === '重新提交工单')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('装箱 #9007199254740993')
    expect(wrapper.text()).not.toContain('影像 #')
    expect(api.actOnRepairOrder).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('审计读取独立，切换工单后迟到事件不得覆盖当前审计', async () => {
    const wrapper = panel()
    await flushPromises()
    const vm = wrapper.vm as any
    let resolve!: (value: unknown) => void
    api.getRepairEvents.mockImplementationOnce(() => new Promise((r) => { resolve = r }))
      .mockResolvedValueOnce({ data: [{ id: 2, action: 'APPROVED', actorId: 12 }] })
    void vm.select(verified)
    await flushPromises()
    expect(vm.busy).toBe(false)
    expect(vm.auditBusy).toBe(true)
    await vm.select(proposed)
    await flushPromises()
    resolve({ data: [{ id: 1, action: 'PROPOSED', actorId: 11 }] })
    await flushPromises()
    expect(vm.selected.id).toBe(2)
    expect(vm.events.map((event: any) => event.id)).toEqual([2])
    wrapper.unmount()
  })

  it('审计失败独立重试，不冒充空审计也不重新读取工单', async () => {
    const wrapper = panel()
    await flushPromises()
    const vm = wrapper.vm as any
    api.getRepairEvents.mockRejectedValueOnce(new Error('synthetic audit failure'))
    await vm.select(verified)
    await flushPromises()
    expect(wrapper.text()).toContain('审计记录读取失败')
    expect(vm.failed).toBe(false)
    expect(vm.auditFailed).toBe(true)
    api.getRepairEvents.mockResolvedValueOnce({ data: [{ id: 1, action: 'VERIFIED', actorId: 12 }] })
    await wrapper.findAll('button').find(b => b.text() === '重试审计')!.trigger('click')
    await flushPromises()
    expect(api.listRepairOrders).toHaveBeenCalledTimes(1)
    expect(vm.events).toHaveLength(1)
    expect(vm.auditFailed).toBe(false)
    wrapper.unmount()
  })

  it('审计按现有100条游标分页，追加失败保留旧事件', async () => {
    const wrapper = panel()
    await flushPromises()
    const vm = wrapper.vm as any
    api.getRepairEvents.mockResolvedValueOnce({ data: Array.from({ length: 100 }, (_, i) => ({ id: i + 1, action: 'VERIFIED', actorId: 12 })) })
      .mockRejectedValueOnce(new Error('synthetic paging failure'))
      .mockResolvedValueOnce({ data: [{ id: 101, action: 'VERIFIED', actorId: 12 }] })
    await vm.select(verified)
    await flushPromises()
    const button = (label: string) => wrapper.findAll('button').find(b => b.text() === label)!
    await button('加载更多审计').trigger('click')
    await flushPromises()
    expect(vm.events).toHaveLength(100)
    await button('重试审计').trigger('click')
    await flushPromises()
    expect(api.getRepairEvents).toHaveBeenLastCalledWith(1, 100)
    expect(vm.events).toHaveLength(101)
    expect(vm.auditMore).toBe(false)
    wrapper.unmount()
  })

  it('当前审计拒绝访问时清空工单与事件并关闭写入', async () => {
    const wrapper = panel()
    await flushPromises()
    const vm = wrapper.vm as any
    api.getRepairEvents.mockResolvedValueOnce({ data: Array.from({ length: 100 }, (_, i) => ({ id: i + 1 })) })
      .mockRejectedValueOnce({ code: 403 })
    await vm.select(verified)
    await wrapper.findAll('button').find(b => b.text() === '加载更多审计')!.trigger('click')
    await flushPromises()
    expect(vm.orders).toEqual([])
    expect(vm.events).toEqual([])
    expect(vm.selected).toBeNull()
    expect(vm.enabled).toBe(false)
    expect(vm.failed).toBe(true)
    wrapper.unmount()
  })

  it('同一旧异常不能重开已复验工单', async () => {
    const wrapper = panel(10)
    await flushPromises()
    expect(wrapper.text()).not.toContain('提交工单')
    wrapper.unmount()
  })
})

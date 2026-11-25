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

  it('同一旧异常不能重开已复验工单', async () => {
    const wrapper = panel(10)
    await flushPromises()
    expect(wrapper.text()).not.toContain('提交工单')
    wrapper.unmount()
  })
})

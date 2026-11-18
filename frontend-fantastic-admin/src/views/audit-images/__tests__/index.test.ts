import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AuditImagesPage from '../index.vue'

const api = vi.hoisted(() => ({
  getImageAuditAnalytics: vi.fn(),
  getLogById: vi.fn(),
  searchImageAuditLogs: vi.fn(),
}))

vi.mock('@/api/modules/logs', () => api)

const stubs = {
  'AuditAnalytics': { props: ['analytics', 'loading'], template: '<div class="audit-analytics" :data-count="analytics.totalAccesses" :data-loading="loading" />' },
  'AppEmpty': true,
  'AppError': { props: ['message'], template: '<p class="list-error">{{ message }}</p>' },
  'AppLoading': true,
  'el-alert': { template: '<div><slot name="title"/><slot/></div>' },
  'el-button': { template: '<button><slot/></button>' },
  'el-card': { template: '<section><slot name="header"/><slot/></section>' },
  'el-date-picker': true,
  'el-descriptions': true,
  'el-descriptions-item': true,
  'el-dialog': true,
  'el-form': { template: '<form><slot/></form>' },
  'el-form-item': { template: '<div><slot/></div>' },
  'el-icon': { template: '<i><slot/></i>' },
  'el-input': true,
  'el-option': { props: ['label'], template: '<span class="audit-option">{{ label }}</span>' },
  'el-pagination': true,
  'el-select': { template: '<div><slot/></div>' },
  'el-table': { props: ['data'], template: '<div class="audit-rows">{{ (data || []).map(row => row.id).join(",") }}</div>' },
  'el-table-column': true,
  'el-tag': { template: '<span><slot/></span>' },
}

describe('audit images page', () => {
  beforeEach(() => {
    api.searchImageAuditLogs.mockReset().mockResolvedValue({
      data: { list: [], total: 0, page: 1, size: 20 },
    })
    api.getImageAuditAnalytics.mockReset().mockResolvedValue({
      data: {
        totalAccesses: 0,
        uniqueUsers: 0,
        uniqueTargets: 0,
        abnormalAccesses: 0,
        averageDurationMs: 0,
        trend: [],
        actionDistribution: [],
        topUsers: [],
      },
    })
  })

  it('loads list and analytics together on entry and refresh', async () => {
    const wrapper = mount(AuditImagesPage, { global: { stubs } })
    await flushPromises()

    expect(api.searchImageAuditLogs).toHaveBeenCalledWith({ page: 1, size: 20 })
    expect(api.getImageAuditAnalytics).toHaveBeenCalledWith({})

    const refreshButton = wrapper.findAll('button').find(button => button.text() === '刷新')
    expect(refreshButton).toBeDefined()
    await refreshButton!.trigger('click')
    await flushPromises()

    expect(api.searchImageAuditLogs).toHaveBeenCalledTimes(2)
    expect(api.getImageAuditAnalytics).toHaveBeenCalledTimes(2)
  })

  it('only offers the four image access actions', async () => {
    const wrapper = mount(AuditImagesPage, { global: { stubs } })
    await flushPromises()

    const labels = wrapper.findAll('.audit-option').map(item => item.text())
    expect(labels).toEqual(expect.arrayContaining([
      '查询病案图片列表',
      '查看本地病案图片',
      '查看 OSS 病案图片',
      '下载病案压缩包',
    ]))
    expect(labels).not.toEqual(expect.arrayContaining(['禁用用户', '更新角色权限配置']))
  })

  it('旧列表和统计晚返回不覆盖刷新后的结果', async () => {
    let resolveList!: (value: unknown) => void
    let resolveStats!: (value: unknown) => void
    api.searchImageAuditLogs.mockReturnValueOnce(new Promise((resolve) => { resolveList = resolve }))
    api.getImageAuditAnalytics.mockReturnValueOnce(new Promise((resolve) => { resolveStats = resolve }))
    const wrapper = mount(AuditImagesPage, { global: { stubs } })
    api.searchImageAuditLogs.mockResolvedValue({ data: { list: [{ id: 2 }], total: 1 } })
    api.getImageAuditAnalytics.mockResolvedValue({ data: { totalAccesses: 2 } })
    await wrapper.findAll('button').find(button => button.text() === '刷新')!.trigger('click')
    await flushPromises()
    resolveList({ data: { list: [{ id: 1 }], total: 1 } })
    resolveStats({ data: { totalAccesses: 1 } })
    await flushPromises()
    expect(wrapper.find('.audit-rows').text()).toBe('2')
    expect(wrapper.find('.audit-analytics').attributes('data-count')).toBe('2')
    wrapper.unmount()
  })

  it('旧统计finally不能提前结束刷新统计的加载状态', async () => {
    let resolveStats!: (value: unknown) => void
    api.getImageAuditAnalytics.mockReturnValueOnce(new Promise((resolve) => { resolveStats = resolve }))
    const wrapper = mount(AuditImagesPage, { global: { stubs } })
    api.getImageAuditAnalytics.mockReturnValue(new Promise(() => {}))
    await wrapper.findAll('button').find(button => button.text() === '刷新')!.trigger('click')
    resolveStats({ data: { totalAccesses: 1 } })
    await flushPromises()
    expect(wrapper.find('.audit-analytics').attributes('data-loading')).toBe('true')
    wrapper.unmount()
  })

  it('当前列表异步失败仍显示可重试错误', async () => {
    api.searchImageAuditLogs.mockRejectedValueOnce(new Error('synthetic current failure'))
    const wrapper = mount(AuditImagesPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.find('.list-error').text()).toContain('synthetic current failure')
    wrapper.unmount()
  })

  it('旧统计失败不清空新统计或回填旧错误', async () => {
    let rejectOld!: (error: unknown) => void
    api.getImageAuditAnalytics.mockReturnValueOnce(new Promise((_, reject) => { rejectOld = reject }))
    const wrapper = mount(AuditImagesPage, { global: { stubs } })
    api.getImageAuditAnalytics.mockResolvedValue({ data: { totalAccesses: 2 } })
    await wrapper.findAll('button').find(button => button.text() === '刷新')!.trigger('click')
    await flushPromises()
    rejectOld(new Error('synthetic obsolete analytics'))
    await flushPromises()
    expect(wrapper.find('.audit-analytics').attributes('data-count')).toBe('2')
    expect(wrapper.find('[title="分析数据加载失败：synthetic obsolete analytics"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('卸载后统计不回填，也不能重新启动列表和统计查询', async () => {
    let resolveStats!: (value: unknown) => void
    api.getImageAuditAnalytics.mockReturnValueOnce(new Promise((resolve) => { resolveStats = resolve }))
    const wrapper = mount(AuditImagesPage, { global: { stubs } })
    const state = wrapper.vm as unknown as { analytics: { totalAccesses: number }, analyticsLoading: boolean, loadData: () => Promise<void> }
    wrapper.unmount()
    resolveStats({ data: { totalAccesses: 99 } })
    await flushPromises()
    await state.loadData()
    expect(state.analytics.totalAccesses).toBe(0)
    expect(state.analyticsLoading).toBe(false)
    expect(api.searchImageAuditLogs).toHaveBeenCalledTimes(1)
    expect(api.getImageAuditAnalytics).toHaveBeenCalledTimes(1)
  })
})

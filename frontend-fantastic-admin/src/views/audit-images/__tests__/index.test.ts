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
  'AuditAnalytics': { template: '<div class="audit-analytics" />' },
  'AppEmpty': true,
  'AppError': true,
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
  'el-table': true,
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
})

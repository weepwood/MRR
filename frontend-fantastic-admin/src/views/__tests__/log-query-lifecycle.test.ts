import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuditImagesPage from '../audit-images/index.vue'
import LogsPage from '../logs/index.vue'

const api = vi.hoisted(() => ({ searchSystemLogs: vi.fn(), searchImageAuditLogs: vi.fn(), getImageAuditAnalytics: vi.fn(), getLogById: vi.fn(), exportImageAuditLogs: vi.fn() }))
vi.mock('@/api/modules/logs', () => api)
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
const stubs = {
  'AppError': { props: ['message'], template: '<p class="list-error">{{ message }}</p>' },
  'AppLoading': true,
  'AppEmpty': true,
  'AuditAnalytics': true,
  'MrrTableActions': true,
  'MrrFilterActionButton': true,
  'el-button': { template: '<button><slot /></button>' },
  'el-card': { template: '<section><slot name="header" /><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': true,
  'el-date-picker': true,
  'el-select': true,
  'el-option': true,
  'el-table': { props: ['data'], template: '<div class="rows">{{ (data || []).map(row => row.id).join(",") }}</div>' },
  'el-table-column': true,
  'el-pagination': true,
  'el-dialog': true,
  'el-icon': true,
  'el-tag': true,
  'el-alert': true,
  'el-descriptions': true,
  'el-descriptions-item': true,
}
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.resetAllMocks()
  api.searchSystemLogs.mockResolvedValue({ data: { list: [{ id: 2 }], total: 1 } })
  api.searchImageAuditLogs.mockResolvedValue({ data: { list: [{ id: 2 }], total: 1 } })
  api.getImageAuditAnalytics.mockResolvedValue({ data: { totalAccesses: 2 } })
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

for (const { name, component, request } of [
  { name: '系统日志', component: LogsPage, request: api.searchSystemLogs },
  { name: '影像审计', component: AuditImagesPage, request: api.searchImageAuditLogs },
]) {
  describe(name, () => {
    it('旧请求晚失败不会覆盖刷新成功后的列表或错误提示', async () => {
      let rejectOld!: (error: unknown) => void
      request.mockReturnValueOnce(new Promise((_, reject) => { rejectOld = reject }))
      const wrapper = mount(component, { global: { stubs } })
      wrappers.push(wrapper)
      await wrapper.findAll('button').find(button => button.text() === '刷新')!.trigger('click')
      await flushPromises()
      rejectOld(new Error('synthetic obsolete error'))
      await flushPromises()
      expect(wrapper.find('.list-error').exists()).toBe(false)
      expect(wrapper.find('.rows').text()).toBe('2')
    })

    it.each([403, 500])('当前%s异步错误仍显示在页面，不变成伪空结果', async (status) => {
      request.mockRejectedValueOnce(Object.assign(new Error('synthetic current error'), { response: { status } }))
      const wrapper = mount(component, { global: { stubs } })
      wrappers.push(wrapper)
      await flushPromises()
      expect(wrapper.find('.list-error').text()).toContain('synthetic current error')
    })
  })
}

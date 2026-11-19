import type { ComponentPublicInstance } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RuntimeErrorsPage from '../index.vue'

const pushMock = vi.hoisted(() => vi.fn())
const api = vi.hoisted(() => ({
  searchSystemErrors: vi.fn(),
  getSystemErrorOverview: vi.fn(),
  getSystemErrorDetail: vi.fn(),
  updateSystemErrorStatus: vi.fn(),
}))
vi.mock('@/api/modules/system-errors', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: pushMock }) }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ permissions: ['system:error:read', 'system:error:manage', 'log:read'] }),
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn() } }))

const overviewFixture = {
  totalGroups: 12,
  totalOccurrences: 140,
  openGroups: 3,
  acknowledgedGroups: 2,
  resolvedGroups: 7,
  errorGroups: 9,
  warnGroups: 3,
  recentOccurrences: 5,
}
const rowFixture = {
  id: 1,
  errorId: 'ERR-20261119-AAAA1111',
  level: 'ERROR',
  module: 'handler',
  messageSummary: 'synthetic error message',
  requestId: 'req-synthetic-001',
  occurrenceCount: 6,
  status: 'OPEN',
  firstSeenAt: '2026-11-19T02:00:00',
  lastSeenAt: '2026-11-19T03:00:00',
  threadName: 'http-nio-18045-exec-1',
  loggerName: 'com.zjcxph.imgapi.handler.GlobalExceptionHandler',
  stackTrace: '',
  acknowledgedBy: null,
  resolvedAt: null,
}

const stubs = {
  'AppEmpty': { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
  'AppError': { props: ['message'], template: '<div class="query-error">{{ message }}</div>' },
  'AppLoading': { props: ['type'], template: '<div class="loading" />' },
  'MrrMetricCard': { props: ['label', 'value'], template: '<p class="metric">{{ label }} {{ value }}</p>' },
  'MrrFilterActionButton': { props: ['action'], template: '<button :data-action="action"><slot /></button>' },
  'el-button': { props: ['link'], template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>' },
  'el-card': { template: '<section><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { props: ['modelValue', 'placeholder'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': { name: 'ElSelect', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-option': { props: ['label'], template: '<span>{{ label }}</span>' },
  'el-tag': { props: ['type'], template: '<span class="tag"><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-table': { props: ['data'], template: '<div class="rows">{{ (data || []).map(row => row.errorId).join(",") }}<slot /></div>' },
  'el-table-column': true,
  'el-pagination': true,
  'el-drawer': { props: ['modelValue', 'title'], template: '<div v-if="modelValue" class="drawer"><slot /></div>' },
  'el-descriptions': { template: '<div class="descriptions"><slot /></div>' },
  'el-descriptions-item': { props: ['label'], template: '<div class="desc-item" :data-label="label"><slot /></div>' },
  'el-alert': { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
}

const wrappers: ReturnType<typeof mount>[] = []
function openPage() {
  const wrapper = mount(RuntimeErrorsPage, { global: { stubs } })
  wrappers.push(wrapper)
  return wrapper
}
/** script setup 的绑定不暴露在实例上，但 $ 内部持有 setupState；用于直调行内交互逻辑。 */
function setupState(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
  return (wrapper.vm as unknown as ComponentPublicInstance & { $: { setupState: Record<string, unknown> } }).$.setupState
}
function button(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('button').find(item => item.text().trim() === label)!
}

beforeEach(() => {
  vi.resetAllMocks()
  api.searchSystemErrors.mockResolvedValue({ data: { list: [rowFixture], total: 1 } })
  api.getSystemErrorOverview.mockResolvedValue({ data: overviewFixture })
  api.getSystemErrorDetail.mockResolvedValue({ data: rowFixture })
})

afterEach(() => {
  while (wrappers.length) { wrappers.pop()?.unmount() }
})

describe('运行错误中心', () => {
  it('概览指标卡点击即套用状态筛选，再次点击同卡取消', async () => {
    const wrapper = openPage()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenCalledTimes(1)

    await wrapper.find('[title="点击筛选待处理错误组"]').trigger('click')
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'OPEN' }))

    await wrapper.find('[title="点击筛选待处理错误组"]').trigger('click')
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ status: undefined }))
  })

  it('模块点击筛选切换查询条件，再次点击同值取消', async () => {
    const wrapper = openPage()
    await flushPromises()
    const state = setupState(wrapper)

    await (state.filterByModule as (module?: string) => void)('handler')
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: 'handler' }))

    await (state.filterByModule as (module?: string) => void)('handler')
    // 取消后条件清空，loadData 把空串规范化为 undefined（不发送该参数）
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: undefined }))
  })

  it('详情抽屉带 Request ID 时可跳转访问日志；不再提供运行日志跳转', async () => {
    const wrapper = openPage()
    await flushPromises()
    expect(wrapper.text()).not.toContain('查看同错误组日志')
    expect(wrapper.text()).not.toContain('查看该请求全部日志')

    await (setupState(wrapper).openDetail as (row: typeof rowFixture) => Promise<void>)(rowFixture)
    await flushPromises()
    const accessButton = button(wrapper, '查看访问记录')!
    expect(accessButton).toBeDefined()
    await accessButton.trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/logs', query: { requestId: 'req-synthetic-001' } })
  })

  it('详情展示真实线程名与可复制的编号', async () => {
    const wrapper = openPage()
    await flushPromises()
    await (setupState(wrapper).openDetail as (row: typeof rowFixture) => Promise<void>)(rowFixture)
    await flushPromises()
    expect(wrapper.text()).toContain('http-nio-18045-exec-1')
    expect(wrapper.text()).toContain('ERR-20261119-AAAA1111')
  })
})

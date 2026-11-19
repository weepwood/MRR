import type { ComponentPublicInstance } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LogsPage from '../index.vue'

const pushMock = vi.hoisted(() => vi.fn())
const api = vi.hoisted(() => ({
  searchSystemLogs: vi.fn(),
  getLogById: vi.fn(),
}))
vi.mock('@/api/modules/logs', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/logs', query: {} }),
  useRouter: () => ({ push: pushMock }),
}))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/api/modules/user', () => ({ default: { getUsers: vi.fn().mockResolvedValue({ data: { list: [], total: 0 } }) } }))

const rowFixture = {
  id: 1,
  requestId: 'req-synthetic-001',
  username: 'op_user',
  clientIp: '127.0.0.1',
  method: 'POST',
  requestUri: '/api/v1/archive-box-records',
  responseStatus: 200,
  executeTime: 35,
  accessTime: '2026-11-19 10:00:00',
  referer: '',
  queryString: '',
  requestBody: '',
  auditAction: '',
  auditTarget: '',
  auditDescription: '',
  errorMessage: '',
  userAgent: 'vitest',
}

const failedRowFixture = {
  ...rowFixture,
  id: 2,
  requestId: 'req-500-001',
  responseStatus: 500,
  errorMessage: 'java.lang.IllegalStateException: boom',
}

const stubs = {
  'AppEmpty': { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
  'AppError': { props: ['message'], template: '<div class="query-error">{{ message }}</div>' },
  'AppLoading': { props: ['type'], template: '<div class="loading" />' },
  'MrrPageShell': { template: '<div><slot /></div>' },
  'MrrPageHeader': { template: '<div class="page-header"><slot name="actions" /></div>' },
  'MrrDataTablePanel': { template: '<section class="panel"><slot name="filters" /><slot /><slot name="pagination" /></section>' },
  'MrrFilterBar': { template: '<div class="filter-bar"><slot /><slot name="actions" /></div>' },
  'MrrFilterActionButton': { props: ['action'], template: '<button :data-action="action" @click="$emit(\'click\')"><slot /></button>' },
  'MrrTableActions': { props: ['actions'], template: '<div class="table-actions" />' },
  'MrrUserSearchSelect': {
    props: ['modelValue', 'id', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<input class="user-search-stub" :value="modelValue" />',
  },
  'el-button': { props: ['link', 'loading'], template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { props: ['label'], template: '<div><slot /></div>' },
  'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" />' },
  'el-select': { props: ['modelValue'], template: '<div><slot /></div>' },
  'el-option': { props: ['label'], template: '<span>{{ label }}</span>' },
  'el-date-picker': true,
  'el-tag': { props: ['type'], template: '<span class="tag"><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-table': { props: ['data'], template: '<div class="rows">{{ (data || []).map(row => row.requestId).join(",") }}<slot /></div>' },
  'el-table-column': true,
  'el-pagination': true,
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue" class="dialog"><slot /></div>' },
  'el-descriptions': { template: '<div class="descriptions"><slot /></div>' },
  'el-descriptions-item': { props: ['label'], template: '<div class="desc-item" :data-label="label"><slot /></div>' },
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
}

const wrappers: ReturnType<typeof mount>[] = []
function openPage() {
  const wrapper = mount(LogsPage, { global: { stubs } })
  wrappers.push(wrapper)
  return wrapper
}
/** script setup 的绑定不暴露在实例上，但 $ 内部持有 setupState；用于直调行内交互逻辑。 */
function setupState(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
  return (wrapper.vm as unknown as ComponentPublicInstance & { $: { setupState: Record<string, unknown> } }).$.setupState
}

beforeEach(() => {
  vi.resetAllMocks()
  api.searchSystemLogs.mockResolvedValue({ data: { list: [rowFixture], total: 1 } })
  api.getLogById.mockResolvedValue({ data: failedRowFixture })
})

afterEach(() => {
  vi.useRealTimers()
  while (wrappers.length) { wrappers.pop()?.unmount() }
})

describe('日志管理', () => {
  it('列表正常渲染且头部显示自动刷新说明', async () => {
    const wrapper = openPage()
    await flushPromises()
    expect(wrapper.text()).toContain('req-synthetic-001')
    expect(wrapper.text()).toContain('每 30 秒自动刷新')
  })

  it('复制 Request ID 写入剪贴板并提示成功', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
    const wrapper = openPage()
    await flushPromises()
    const copyText = setupState(wrapper).copyText as (text: string, message: string) => Promise<void>
    await copyText('req-synthetic-001', 'Request ID 已复制')
    expect(writeText).toHaveBeenCalledWith('req-synthetic-001')
  })

  it('30 秒静默自动刷新：不置 loading，头部更新上次刷新时间', async () => {
    vi.useFakeTimers({ shouldClearNativeTimers: true })
    const wrapper = openPage()
    await vi.advanceTimersByTimeAsync(0)
    expect(api.searchSystemLogs).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(30_000)
    expect(api.searchSystemLogs).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.loading').exists()).toBe(false)
    expect(wrapper.text()).toContain('上次')
  })

  it('静默刷新失败保留旧数据，不进错误态', async () => {
    vi.useFakeTimers({ shouldClearNativeTimers: true })
    const wrapper = openPage()
    await vi.advanceTimersByTimeAsync(0)
    api.searchSystemLogs.mockRejectedValueOnce(new Error('network down'))

    await vi.advanceTimersByTimeAsync(30_000)
    expect(wrapper.text()).toContain('req-synthetic-001')
    expect(wrapper.find('.query-error').exists()).toBe(false)
  })

  it('失败请求详情可一键跳运行错误中心按 Request ID 检索', async () => {
    const wrapper = openPage()
    await flushPromises()
    await (setupState(wrapper).openDetail as (row: typeof failedRowFixture) => Promise<void>)(failedRowFixture)
    await flushPromises()

    const jumpButton = wrapper.findAll('button').find(item => item.text().trim() === '查看相关运行错误')!
    expect(jumpButton).toBeDefined()
    await jumpButton.trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/runtime-errors', query: { requestId: 'req-500-001' } })
  })

  it('成功请求详情不显示跳错误中心入口', async () => {
    api.getLogById.mockResolvedValue({ data: rowFixture })
    const wrapper = openPage()
    await flushPromises()
    await (setupState(wrapper).openDetail as (row: typeof rowFixture) => Promise<void>)(rowFixture)
    await flushPromises()
    expect(wrapper.findAll('button').some(item => item.text().trim() === '查看相关运行错误')).toBe(false)
  })
})

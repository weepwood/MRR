import type { ComponentPublicInstance } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RuntimeErrorsPage from '../index.vue'

const pushMock = vi.hoisted(() => vi.fn())
/** mock 的路由对象（reactive），供测试修改 query 模拟日志管理页跳转。 */
const routeState = vi.hoisted(() => ({ route: null as { path: string, query: Record<string, unknown> } | null }))
const api = vi.hoisted(() => ({
  searchSystemErrors: vi.fn(),
  getSystemErrorOverview: vi.fn(),
  getSystemErrorDetail: vi.fn(),
  listSystemErrorModules: vi.fn(),
}))
vi.mock('@/api/modules/system-errors', () => api)
vi.mock('vue-router', async () => {
  const { reactive } = await import('vue')
  const route = reactive({ path: '/runtime-errors', query: {} as Record<string, unknown> })
  routeState.route = route
  return { useRouter: () => ({ push: pushMock }), useRoute: () => route }
})
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ permissions: ['system:error:read', 'log:read'] }),
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
  exceptionType: 'java.lang.IllegalStateException',
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
  'el-dialog': { props: ['modelValue', 'title'], template: '<div v-if="modelValue" class="dialog"><slot /></div>' },
  'el-descriptions': { template: '<div class="descriptions"><slot /></div>' },
  'el-descriptions-item': { props: ['label'], template: '<div class="desc-item" :data-label="label"><slot /></div>' },
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
  api.listSystemErrorModules.mockResolvedValue({ data: ['controller', 'storage'] })
})

afterEach(() => {
  while (wrappers.length) { wrappers.pop()?.unmount() }
})

describe('运行错误中心', () => {
  it('页面只读：不出现处理状态操作与状态筛选', () => {
    const wrapper = openPage()
    expect(wrapper.text()).not.toContain('标记处理中')
    expect(wrapper.text()).not.toContain('解决')
    expect(wrapper.text()).not.toContain('重新打开')
    expect(wrapper.text()).not.toContain('待处理')
    expect(wrapper.text()).not.toContain('旧记录正文未保存')
  })

  it('概览指标卡点击即按级别筛选，再次点击同卡取消', async () => {
    const wrapper = openPage()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenCalledTimes(1)

    await wrapper.find('[title="点击仅看 ERROR 错误组"]').trigger('click')
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ level: 'ERROR' }))

    await wrapper.find('[title="点击仅看 ERROR 错误组"]').trigger('click')
    await flushPromises()
    // 取消后条件清空，loadData 把空串规范化为 undefined（不发送该参数）
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ level: undefined }))
  })

  it('时间范围筛选映射为 since 参数，取消后不再发送', async () => {
    const wrapper = openPage()
    await flushPromises()
    const state = setupState(wrapper)
    const queryState = state.query as { range: string }
    const handleSearch = state.handleSearch as () => Promise<void>

    queryState.range = '24h'
    await handleSearch()
    await flushPromises()
    expect(api.searchSystemErrors.mock.lastCall?.[0]?.since).toEqual(expect.any(String))

    queryState.range = ''
    await handleSearch()
    await flushPromises()
    // 取消后条件清空，loadData 把空值规范化为 undefined（不发送该参数）
    expect(api.searchSystemErrors.mock.lastCall?.[0]?.since).toBeUndefined()
  })

  it('模块点击筛选切换查询条件，再次点击同值取消', async () => {
    const wrapper = openPage()
    await flushPromises()
    const state = setupState(wrapper)

    await (state.filterByModule as (module?: string) => void)('handler')
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: 'handler' }))

    await (state.filterByModule as (module?: string) => void)('handler')
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: undefined }))
  })

  it('模块下拉挂载时拉取去重模块名，失败静默保留手输能力', async () => {
    const wrapper = openPage()
    await flushPromises()
    expect(api.listSystemErrorModules).toHaveBeenCalledTimes(1)
    expect(setupState(wrapper).moduleOptions).toEqual(['controller', 'storage'])

    // 检索失败不阻断筛选：选项清空，allow-create 仍可手输
    api.listSystemErrorModules.mockRejectedValueOnce(new Error('synthetic modules error'))
    await (setupState(wrapper).loadModules as () => Promise<void>)()
    await flushPromises()
    expect(setupState(wrapper).moduleOptions).toEqual([])
  })

  it('选中模块下拉即触发检索，清空后不再发送该参数', async () => {
    const wrapper = openPage()
    await flushPromises()
    const state = setupState(wrapper)
    const queryState = state.query as { module: string }
    const handleSearch = state.handleSearch as () => Promise<void>

    queryState.module = 'storage'
    await handleSearch()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: 'storage' }))

    queryState.module = ''
    await handleSearch()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: undefined }))
  })

  it('承接日志管理页跳转：requestId 查询参数自动填入关键字检索', async () => {
    openPage()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenCalledTimes(1)

    routeState.route!.query = { requestId: 'req-from-logs-001' }
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'req-from-logs-001' }))
  })

  it('详情弹窗带 Request ID 时可跳转访问日志', async () => {
    const wrapper = openPage()
    await flushPromises()

    await (setupState(wrapper).openDetail as (row: typeof rowFixture) => Promise<void>)(rowFixture)
    await flushPromises()
    const accessButton = button(wrapper, '查看访问记录')!
    expect(accessButton).toBeDefined()
    await accessButton.trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/logs', query: { requestId: 'req-synthetic-001' } })
  })

  it('详情弹窗展示真实线程名与可复制的编号', async () => {
    const wrapper = openPage()
    await flushPromises()
    await (setupState(wrapper).openDetail as (row: typeof rowFixture) => Promise<void>)(rowFixture)
    await flushPromises()
    expect(wrapper.text()).toContain('http-nio-18045-exec-1')
    expect(wrapper.text()).toContain('ERR-20261119-AAAA1111')
  })

  it('摘要为整体脱敏占位或为空时回退显示异常类型', async () => {
    const wrapper = openPage()
    await flushPromises()
    const summaryDisplay = setupState(wrapper).summaryDisplay as (row: { messageSummary?: string, exceptionType?: string }) => string
    expect(summaryDisplay({ messageSummary: '[MESSAGE OMITTED]', exceptionType: 'org.springframework.web.servlet.resource.NoResourceFoundException' }))
      .toBe('org.springframework.web.servlet.resource.NoResourceFoundException')
    expect(summaryDisplay({ messageSummary: '', exceptionType: 'x.XException' })).toBe('x.XException')
    expect(summaryDisplay({ messageSummary: '', exceptionType: undefined })).toBe('-')
    expect(summaryDisplay({ messageSummary: '真实正文', exceptionType: 'x.XException' })).toBe('真实正文')
    expect(wrapper.text()).not.toContain('旧记录正文未保存')
  })
})

import type { ComponentPublicInstance } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RuntimeErrorsPage from '../index.vue'

const pushMock = vi.hoisted(() => vi.fn())
const replaceMock = vi.hoisted(() => vi.fn())
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
  return { useRouter: () => ({ push: pushMock, replace: replaceMock }), useRoute: () => route }
})
const session = vi.hoisted(() => ({ user: null as { isSessionVerified: boolean, token: string, profile: { roleCode: string }, permissions: string[] } | null }))
vi.mock('@/store/modules/user', async () => {
  const { reactive } = await import('vue')
  session.user = reactive({ isSessionVerified: true, token: 'demo-token', profile: { roleCode: 'OPERATOR' }, permissions: ['system:error:read', 'log:read'] })
  return { useUserStore: () => session.user }
})
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
  'MrrDataTablePanel': { template: '<section><slot name="filters" /><slot /></section>' },
  'MrrFilterBar': { template: '<div><slot /><slot name="actions" /></div>' },
  'el-button': { props: ['link'], emits: ['click'], template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>' },
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
  session.user!.token = 'demo-token'
  session.user!.permissions = ['system:error:read', 'log:read']
  routeState.route!.path = '/runtime-errors'
  routeState.route!.query = {}
  replaceMock.mockImplementation(({ query }) => {
    routeState.route!.query = query
    return Promise.resolve()
  })
  api.searchSystemErrors.mockResolvedValue({ data: { list: [rowFixture], total: 1 } })
  api.getSystemErrorOverview.mockResolvedValue({ data: overviewFixture })
  api.getSystemErrorDetail.mockResolvedValue({ data: rowFixture })
  api.listSystemErrorModules.mockResolvedValue({ data: ['controller', 'storage'] })
})

afterEach(() => {
  while (wrappers.length) { wrappers.pop()?.unmount() }
})

describe('运行错误中心', () => {
  it('服务端403静默刷新清除上次成功结果，普通网络失败保持结果', async () => {
    const wrapper = openPage()
    await flushPromises()
    const state = setupState(wrapper)
    expect((state.list as unknown[]).length).toBeGreaterThan(0)
    api.searchSystemErrors.mockRejectedValueOnce(new Error('temporary network failure'))
    await (state.loadData as (options: { silent: boolean }) => Promise<void>)({ silent: true })
    expect((state.list as unknown[]).length).toBeGreaterThan(0)
    api.searchSystemErrors.mockRejectedValueOnce(Object.assign(new Error('synthetic denied'), { response: { status: 403 } }))
    await (state.loadData as (options: { silent: boolean }) => Promise<void>)({ silent: true })
    expect(state.list).toEqual([])
    expect(state.total).toBe(0)
    expect(state.error).toBe('synthetic denied')
  })

  it('概览未完成时列表独立展示，概览失败后单独重试', async () => {
    let rejectOverview!: (reason: unknown) => void
    api.getSystemErrorOverview.mockReturnValueOnce(new Promise((_, reject) => { rejectOverview = reject }))
    const wrapper = openPage()
    await flushPromises()
    expect(wrapper.get('.rows').text()).toBe(rowFixture.errorId)
    expect(wrapper.get('.metric').text()).toContain('—')
    rejectOverview(new Error('synthetic overview outage'))
    await flushPromises()
    expect(wrapper.get('.rows').text()).toBe(rowFixture.errorId)
    expect(wrapper.get('.overview-status').text()).toContain('概览加载失败')
    await wrapper.get('.overview-status button').trigger('click')
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.metric').text()).toContain('9')
    expect(wrapper.find('.overview-status').exists()).toBe(false)
  })

  it('列表失败不遮挡有效概览，旧概览失败保留上次数据并标记', async () => {
    api.searchSystemErrors.mockRejectedValueOnce(new Error('synthetic list failure'))
    const wrapper = openPage()
    await flushPromises()
    expect(wrapper.get('.query-error').text()).toContain('synthetic list failure')
    expect(wrapper.get('.metric').text()).toContain('9')
    api.getSystemErrorOverview.mockRejectedValueOnce(new Error('synthetic overview failure'))
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    expect(wrapper.get('.rows').text()).toBe(rowFixture.errorId)
    expect(wrapper.get('.metric').text()).toContain('9')
    expect(wrapper.get('.overview-status').text()).toContain('上次成功')
  })

  it('概览迟到成功或失败不会覆盖新代次，权限撤销清空列表和概览', async () => {
    let finishOld!: (value: unknown) => void
    api.getSystemErrorOverview.mockReturnValueOnce(new Promise((resolve) => { finishOld = resolve }))
    const wrapper = openPage()
    await flushPromises()
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    finishOld({ data: { ...overviewFixture, errorGroups: 999 } })
    await flushPromises()
    expect(wrapper.get('.metric').text()).toContain('9')
    expect(wrapper.text()).not.toContain('999')
    api.getSystemErrorOverview.mockRejectedValueOnce(Object.assign(new Error('synthetic denied'), { response: { status: 403 } }))
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    expect(setupState(wrapper).list).toEqual([])
    expect(wrapper.get('.metric').text()).toContain('—')
    expect(wrapper.get('.query-error').text()).toContain('synthetic denied')
  })

  it('旧概览拒绝不覆盖新成功，概览拒绝权限后迟到列表不能回填', async () => {
    let rejectOld!: (reason: unknown) => void
    api.getSystemErrorOverview.mockReturnValueOnce(new Promise((_, reject) => { rejectOld = reject }))
    const wrapper = openPage()
    await flushPromises()
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    rejectOld(Object.assign(new Error('old denied'), { response: { status: 403 } }))
    await flushPromises()
    expect(wrapper.get('.rows').text()).toBe(rowFixture.errorId)
    expect(wrapper.get('.metric').text()).toContain('9')
    expect(wrapper.find('.overview-status').exists()).toBe(false)

    let finishList!: (value: unknown) => void
    api.searchSystemErrors.mockReturnValueOnce(new Promise((resolve) => { finishList = resolve }))
    api.getSystemErrorOverview.mockRejectedValueOnce(Object.assign(new Error('current denied'), { response: { status: 403 } }))
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    finishList({ data: { list: [rowFixture], total: 1 } })
    await flushPromises()
    expect(setupState(wrapper).list).toEqual([])
    expect(setupState(wrapper).total).toBe(0)
    expect(wrapper.get('.metric').text()).toContain('—')
    expect(wrapper.get('.query-error').text()).toContain('current denied')
  })

  it('会话改变后忽略迟到概览，且不会显示上个会话的统计', async () => {
    let resolveOld!: (value: unknown) => void
    api.getSystemErrorOverview.mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
    const wrapper = openPage()
    await flushPromises()
    session.user!.token = 'new-synthetic-token'
    resolveOld({ data: overviewFixture })
    await flushPromises()
    expect(wrapper.get('.metric').text()).toContain('—')
    expect(setupState(wrapper).list).toEqual([])
  })

  it('首次进入追踪链接时直接检索 Request ID', async () => {
    routeState.route!.query = { requestId: 'req-from-logs-001' }
    const wrapper = openPage()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      page: 1,
      keyword: 'req-from-logs-001',
    }), 'demo-token')

    await wrapper.get('[data-action="reset"]').trigger('click')
    await flushPromises()
    expect(routeState.route!.query.requestId).toBeUndefined()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, keyword: undefined }), 'demo-token')
  })

  it('新的追踪检索清除缓存筛选，旧请求晚返回时保留当前结果', async () => {
    let finishOld!: (value: unknown) => void
    api.searchSystemErrors.mockReturnValueOnce(new Promise((resolve) => { finishOld = resolve }))
    const wrapper = openPage()
    Object.assign(setupState(wrapper).query as object, { level: 'WARN', module: 'storage', range: 'today' })
    routeState.route!.query = { requestId: 'req-from-logs-001' }
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
      keyword: 'req-from-logs-001',
      level: undefined,
      module: undefined,
      since: undefined,
    }, 'demo-token')
    finishOld({ data: { list: [{ ...rowFixture, errorId: 'ERR-SYNTHETIC-OLD' }], total: 50 } })
    await flushPromises()
    expect(wrapper.get('.rows').text()).toBe(rowFixture.errorId)
  })

  it('失败后重试成功可以恢复当前追踪结果', async () => {
    routeState.route!.query = { requestId: 'req-synthetic-001' }
    api.searchSystemErrors.mockRejectedValueOnce(new Error('temporary synthetic failure'))
    const wrapper = openPage()
    await flushPromises()
    expect(wrapper.get('.query-error').text()).toContain('temporary synthetic failure')
    await button(wrapper, '刷新').trigger('click')
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'req-synthetic-001' }), 'demo-token')
    expect(wrapper.get('.rows').text()).toBe(rowFixture.errorId)
  })

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
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ level: 'ERROR' }), 'demo-token')

    await wrapper.find('[title="点击仅看 ERROR 错误组"]').trigger('click')
    await flushPromises()
    // 取消后条件清空，loadData 把空串规范化为 undefined（不发送该参数）
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ level: undefined }), 'demo-token')
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
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: 'handler' }), 'demo-token')

    await (state.filterByModule as (module?: string) => void)('handler')
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: undefined }), 'demo-token')
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
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: 'storage' }), 'demo-token')

    queryState.module = ''
    await handleSearch()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ module: undefined }), 'demo-token')
  })

  it('承接日志管理页跳转：requestId 查询参数自动填入关键字检索', async () => {
    openPage()
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenCalledTimes(1)

    routeState.route!.query = { requestId: 'req-from-logs-001' }
    await flushPromises()
    expect(api.searchSystemErrors).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'req-from-logs-001' }), 'demo-token')
  })

  it('旧详情成功或失败不能覆盖新记录，也不能提前结束新记录加载', async () => {
    const wrapper = openPage()
    await flushPromises()
    const state = setupState(wrapper)
    const open = state.openDetail as (row: typeof rowFixture) => Promise<void>
    const next = { ...rowFixture, id: 2, errorId: 'ERR-SYNTHETIC-NEXT' }
    let old!: (value: unknown) => void
    let latest!: (value: unknown) => void
    api.getSystemErrorDetail.mockReturnValueOnce(new Promise((resolve) => { old = resolve }))
      .mockReturnValueOnce(new Promise((resolve) => { latest = resolve }))
    const a = open(rowFixture)
    const b = open(next)
    old({ data: { ...rowFixture, stackTrace: 'OLD SECRET SYNTHETIC' } })
    await a
    expect(state.detailLoading).toBe(true)
    latest({ data: next })
    await b
    expect((state.current as typeof next).id).toBe(2)
    expect(wrapper.text()).not.toContain('OLD SECRET SYNTHETIC')
  })

  it('错误详情身份不符或网络失败显示可重试状态，关闭后迟到结果无效', async () => {
    const wrapper = openPage()
    await flushPromises()
    const state = setupState(wrapper)
    const open = state.openDetail as (row: typeof rowFixture) => Promise<void>
    api.getSystemErrorDetail.mockResolvedValueOnce({ data: { ...rowFixture, id: 99 } })
    await open(rowFixture)
    expect(state.current).toBeNull()
    expect(wrapper.get('.query-error').text()).toContain('无法读取运行错误详情')
    api.getSystemErrorDetail.mockRejectedValueOnce(new Error('synthetic failure'))
    await open(rowFixture)
    expect(state.current).toBeNull()
    let finish!: (value: unknown) => void
    api.getSystemErrorDetail.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const pending = open(rowFixture)
    state.detailVisible = false
    await flushPromises()
    finish({ data: rowFixture })
    await pending
    expect(state.current).toBeNull()
    expect(state.detailLoading).toBe(false)
  })

  it.each(['token', 'permission'] as const)('会话%s变化时取消详情请求，迟到结果不得恢复', async (change) => {
    const wrapper = openPage()
    await flushPromises()
    let finish!: (value: unknown) => void
    api.getSystemErrorDetail.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const state = setupState(wrapper)
    const pending = (state.openDetail as (row: typeof rowFixture) => Promise<void>)(rowFixture)
    const signal = api.getSystemErrorDetail.mock.lastCall?.[1] as AbortSignal
    if (change === 'token') { session.user!.token = 'different-demo-token' }
    else { session.user!.permissions = ['log:read'] }
    expect(signal.aborted).toBe(true)
    finish({ data: rowFixture })
    await pending
    expect(state.current).toBeNull()
    expect(state.detailVisible).toBe(false)
    expect(state.detailLoading).toBe(false)
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

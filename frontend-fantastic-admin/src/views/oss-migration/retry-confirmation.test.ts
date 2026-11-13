import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, KeepAlive, nextTick, ref } from 'vue'
import * as api from '@/api/modules/oss'
import Page from './index.vue'

const { confirm, success, warning, error } = vi.hoisted(() => ({ confirm: vi.fn(), success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { success, warning, error }, ElMessageBox: { confirm } }))
vi.mock('./composables/useMigrationPolling', () => ({ useMigrationPolling: () => ({ startPolling: vi.fn(), stopPolling: vi.fn(), startOverviewRefresh: vi.fn(), stopOverviewRefresh: vi.fn() }) }))
vi.mock('@/composables/useTableActionLayout', () => ({ useTableActionLayout: () => ({ maxInlineActions: ref(1), actionColumnWidth: ref(120) }) }))
vi.mock('@/api/modules/oss', () => Object.fromEntries(['cancelMigrationJob', 'createMigrationJob', 'getMigrationJob', 'getMigrationJobs', 'getMigrationLogs', 'getMigrationReadiness', 'getMigrationStatistics', 'getPendingFolders', 'getPendingManagementRecords', 'getWaitingSjhRecords', 'retryMigrationScans', 'uploadByBah', 'uploadToOss'].map(name => [name, vi.fn()])))

interface State {
  handleRetryFailed: () => Promise<void>
  selectedLogs: { scanId: number, migrationStatus: string }[]
  loading: { retry: boolean, logs: boolean }
  currentJob?: { id: number, status: string }
  retryFeedback?: { type: string, message: string }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const globalOptions = { directives: { loading: () => {} }, stubs: { 'el-card': true, 'el-icon': true, 'el-switch': true, 'el-button': true, 'el-tag': true, 'el-alert': true, 'el-progress': true, 'el-table': true, 'el-table-column': true, 'el-form': true, 'el-form-item': true, 'el-radio-group': true, 'el-radio-button': true, 'el-select': true, 'el-option': true, 'el-input': true, 'el-input-number': true, 'el-tabs': true, 'el-tab-pane': true, 'el-pagination': true, 'el-dialog': true, 'el-drawer': true, 'el-descriptions': true, 'el-descriptions-item': true, 'MrrMetricCard': true, 'MrrTableActions': true, 'MrrFilterActionButton': true } }
const wrappers: ReturnType<typeof shallowMount>[] = []
function select(vm: State) {
  vm.selectedLogs = [1, 2, 1].map(scanId => ({ scanId, migrationStatus: 'failed' }))
}
async function setup() {
  const wrapper = shallowMount(Page, { global: globalOptions })
  wrappers.push(wrapper)
  await flushPromises()
  const vm = wrapper.vm as unknown as State
  select(vm)
  return { wrapper, vm }
}
beforeEach(() => {
  vi.clearAllMocks()
  confirm.mockResolvedValue(undefined)
  vi.mocked(api.getMigrationReadiness).mockResolvedValue({ data: {} } as Awaited<ReturnType<typeof api.getMigrationReadiness>>)
  vi.mocked(api.getMigrationJobs).mockResolvedValue({ data: { list: [] } } as Awaited<ReturnType<typeof api.getMigrationJobs>>)
  vi.mocked(api.getMigrationStatistics).mockResolvedValue({ data: {} } as Awaited<ReturnType<typeof api.getMigrationStatistics>>)
  vi.mocked(api.getPendingFolders).mockResolvedValue({ data: [] } as Awaited<ReturnType<typeof api.getPendingFolders>>)
  vi.mocked(api.getPendingManagementRecords).mockResolvedValue({ data: { list: [] } } as Awaited<ReturnType<typeof api.getPendingManagementRecords>>)
  vi.mocked(api.getMigrationLogs).mockResolvedValue({ data: { list: [] } } as Awaited<ReturnType<typeof api.getMigrationLogs>>)
  vi.mocked(api.retryMigrationScans).mockResolvedValue({ data: { updated: 2 } } as Awaited<ReturnType<typeof api.retryMigrationScans>>)
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('失败重置确认范围', () => {
  it('确认使用去重快照，不受随后选择变化影响', async () => {
    const { vm } = await setup()
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = vm.handleRetryFailed()
    expect(confirm.mock.calls[0]?.[0]).toContain('2 条')
    vm.selectedLogs = [{ scanId: 3, migrationStatus: 'failed' }]
    decision.resolve(undefined)
    await task
    expect(api.retryMigrationScans).toHaveBeenCalledWith([1, 2])
  })
  it('确认期间禁止重入，取消后恢复', async () => {
    const { vm } = await setup()
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = vm.handleRetryFailed()
    expect(vm.loading.retry).toBe(true)
    await vm.handleRetryFailed()
    expect(confirm).toHaveBeenCalledTimes(1)
    decision.reject('cancel')
    await task
    expect(vm.loading.retry).toBe(false)
    expect(api.retryMigrationScans).not.toHaveBeenCalled()
  })
  it('确认后出现活动任务时不提交', async () => {
    const { vm } = await setup()
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = vm.handleRetryFailed()
    vm.currentJob = { id: 9, status: 'running' }
    decision.resolve(undefined)
    await task
    expect(api.retryMigrationScans).not.toHaveBeenCalled()
  })
  it('日志加载时不打开确认或提交', async () => {
    const { vm } = await setup()
    vm.loading.logs = true
    await vm.handleRetryFailed()
    expect(confirm).not.toHaveBeenCalled()
    expect(api.retryMigrationScans).not.toHaveBeenCalled()
  })
  it.each([0, 1, undefined, -1, 3, 0.5])('非全部合法结果不误报成功：%s', async (updated) => {
    const { vm } = await setup()
    vi.mocked(api.retryMigrationScans).mockResolvedValueOnce({ data: { updated } } as Awaited<ReturnType<typeof api.retryMigrationScans>>)
    await vm.handleRetryFailed()
    expect(success).not.toHaveBeenCalled()
    expect(vm.retryFeedback?.type).toBe('warning')
  })
  it('全部更新给出确认范围内的成功数量', async () => {
    const { vm } = await setup()
    await vm.handleRetryFailed()
    expect(vm.retryFeedback?.type).toBe('success')
    expect(vm.retryFeedback?.message).toContain('2/2')
  })
  it.each([403, 0])('拒绝或网络异常不自动重试：%s', async (status) => {
    const { vm } = await setup()
    vi.mocked(api.retryMigrationScans).mockRejectedValueOnce({ response: status ? { status } : undefined, message: 'private-path' })
    await vm.handleRetryFailed()
    expect(api.retryMigrationScans).toHaveBeenCalledTimes(1)
    expect(success).not.toHaveBeenCalled()
    expect(vm.retryFeedback?.message).not.toContain('private')
    expect(vm.retryFeedback).toBeDefined()
    expect(vm.loading.retry).toBe(false)
  })
  it('卸载后即使确认返回也不发POST', async () => {
    const { vm, wrapper } = await setup()
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = vm.handleRetryFailed()
    wrapper.unmount()
    decision.resolve(undefined)
    await task
    expect(api.retryMigrationScans).not.toHaveBeenCalled()
  })
  it('缓存离开时已发请求保持防重，回来不接受迟到反馈', async () => {
    const visible = ref(true)
    const parent = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(Page) : h('div') }) }, { global: globalOptions })
    wrappers.push(parent)
    await flushPromises()
    const vm = parent.findComponent(Page).vm as unknown as State
    select(vm)
    const request = deferred<Awaited<ReturnType<typeof api.retryMigrationScans>>>()
    vi.mocked(api.retryMigrationScans).mockReturnValueOnce(request.promise)
    const task = vm.handleRetryFailed()
    await flushPromises()
    visible.value = false
    await nextTick()
    visible.value = true
    await nextTick()
    expect(vm.loading.retry).toBe(true)
    await vm.handleRetryFailed()
    expect(api.retryMigrationScans).toHaveBeenCalledTimes(1)
    request.resolve({ data: { updated: 2 } } as Awaited<ReturnType<typeof api.retryMigrationScans>>)
    await task
    expect(vm.loading.retry).toBe(false)
    expect(success).not.toHaveBeenCalled()
    expect(vm.retryFeedback).toBeUndefined()
  })
  it.each([0, -1, 1.5, 2147483648])('非法ID不进入确认：%s', async (scanId) => {
    const { vm } = await setup()
    vm.selectedLogs = [{ scanId, migrationStatus: 'failed' }]
    await vm.handleRetryFailed()
    expect(confirm).not.toHaveBeenCalled()
    expect(api.retryMigrationScans).not.toHaveBeenCalled()
  })
  it.each([1000, 1001])('唯一ID上限：%s', async (count) => {
    const { vm } = await setup()
    vm.selectedLogs = Array.from({ length: count }, (_, i) => ({ scanId: i + 1, migrationStatus: 'failed' }))
    vi.mocked(api.retryMigrationScans).mockResolvedValueOnce({ data: { updated: count } } as Awaited<ReturnType<typeof api.retryMigrationScans>>)
    await vm.handleRetryFailed()
    expect(api.retryMigrationScans).toHaveBeenCalledTimes(count === 1000 ? 1 : 0)
  })
  it('确认后日志开始加载则不提交', async () => {
    const { vm } = await setup()
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = vm.handleRetryFailed()
    vm.loading.logs = true
    decision.resolve(undefined)
    await task
    expect(api.retryMigrationScans).not.toHaveBeenCalled()
    expect(vm.loading.retry).toBe(false)
  })
  it('旧确认结束不能清除新确认的busy', async () => {
    const visible = ref(true)
    const parent = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(Page) : h('div') }) }, { global: globalOptions })
    wrappers.push(parent)
    await flushPromises()
    const vm = parent.findComponent(Page).vm as unknown as State
    select(vm)
    const old = deferred<void>()
    const current = deferred<void>()
    confirm.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    const a = vm.handleRetryFailed()
    visible.value = false
    await nextTick()
    visible.value = true
    await nextTick()
    const b = vm.handleRetryFailed()
    old.resolve(undefined)
    await a
    expect(vm.loading.retry).toBe(true)
    expect(api.retryMigrationScans).not.toHaveBeenCalled()
    current.reject('cancel')
    await b
    expect(vm.loading.retry).toBe(false)
  })
  it('已发请求离页后迟到错误不回填或重试', async () => {
    const { vm, wrapper } = await setup()
    const request = deferred<Awaited<ReturnType<typeof api.retryMigrationScans>>>()
    vi.mocked(api.retryMigrationScans).mockReturnValueOnce(request.promise)
    const task = vm.handleRetryFailed()
    await flushPromises()
    wrapper.unmount()
    request.reject(new Error('late private error'))
    await task
    expect(vm.retryFeedback).toBeUndefined()
    expect(error).not.toHaveBeenCalled()
    expect(warning).not.toHaveBeenCalled()
    expect(api.retryMigrationScans).toHaveBeenCalledTimes(1)
  })
})

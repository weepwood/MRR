import type { MigrationJob } from '@/api/modules/oss'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, KeepAlive, nextTick, ref } from 'vue'
import * as api from '@/api/modules/oss'
import Page from './index.vue'

const { start, stop, error } = vi.hoisted(() => ({ start: vi.fn(), stop: vi.fn(), error: vi.fn() }))
vi.mock('./composables/useMigrationPolling', () => ({ useMigrationPolling: () => ({ startPolling: start, stopPolling: stop, startOverviewRefresh: vi.fn(), stopOverviewRefresh: vi.fn() }) }))
vi.mock('@/composables/useTableActionLayout', () => ({ useTableActionLayout: () => ({ maxInlineActions: ref(1), actionColumnWidth: ref(120) }) }))
vi.mock('element-plus', () => ({ ElMessage: { error, success: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
vi.mock('@/api/modules/oss', () => Object.fromEntries(['cancelMigrationJob', 'createMigrationJob', 'getMigrationJob', 'getMigrationJobs', 'getMigrationLogs', 'getMigrationReadiness', 'getMigrationStatistics', 'getPendingFolders', 'getPendingManagementRecords', 'getWaitingSjhRecords', 'retryMigrationScans', 'uploadByBah', 'uploadToOss'].map(name => [name, vi.fn()])))

interface State {
  currentJob?: MigrationJob
  jobs: MigrationJob[]
  jobPage: number
  loading: { jobs: boolean, detail: boolean }
  loadJobs: () => Promise<void>
  showJobDetail: (job: MigrationJob) => Promise<void>
  jobDetail?: MigrationJob
  jobHistoryError: boolean
  jobHistoryPage?: number
  jobDetailError: boolean
  jobDrawerVisible: boolean
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const history = (id: number) => ({ data: { list: [{ id, status: 'completed' }], total: 20 } })
const globalOptions = { directives: { loading: () => {} }, stubs: { 'el-card': true, 'el-icon': true, 'el-switch': true, 'el-button': true, 'el-tag': true, 'el-alert': true, 'el-progress': true, 'el-table': true, 'el-table-column': true, 'el-form': true, 'el-form-item': true, 'el-radio-group': true, 'el-radio-button': true, 'el-select': true, 'el-option': true, 'el-input': true, 'el-input-number': true, 'el-tabs': true, 'el-tab-pane': true, 'el-pagination': true, 'el-dialog': true, 'el-drawer': true, 'el-descriptions': true, 'el-descriptions-item': true, 'MrrMetricCard': true, 'MrrTableActions': true, 'MrrFilterActionButton': true } }
const wrappers: ReturnType<typeof shallowMount>[] = []
async function setup() {
  const wrapper = shallowMount(Page, { global: globalOptions })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, vm: wrapper.vm as unknown as State }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.getMigrationReadiness).mockResolvedValue({ data: { activeJob: { id: 99, status: 'running' } } } as Awaited<ReturnType<typeof api.getMigrationReadiness>>)
  vi.mocked(api.getMigrationJobs).mockResolvedValue(history(1) as Awaited<ReturnType<typeof api.getMigrationJobs>>)
  vi.mocked(api.getMigrationStatistics).mockResolvedValue({ data: {} } as Awaited<ReturnType<typeof api.getMigrationStatistics>>)
  vi.mocked(api.getPendingFolders).mockResolvedValue({ data: [] } as Awaited<ReturnType<typeof api.getPendingFolders>>)
  vi.mocked(api.getPendingManagementRecords).mockResolvedValue({ data: { list: [] } } as Awaited<ReturnType<typeof api.getPendingManagementRecords>>)
  vi.mocked(api.getMigrationLogs).mockResolvedValue({ data: { list: [] } } as Awaited<ReturnType<typeof api.getMigrationLogs>>)
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('oSS任务监控一致性', () => {
  it('历史任务页不清除readiness确认的当前活动任务，也不停止追踪', async () => {
    const { vm } = await setup()
    expect(vm.currentJob?.id).toBe(99)
    vm.jobPage = 2
    await vm.loadJobs()
    expect(vm.currentJob?.id).toBe(99)
    expect(stop).not.toHaveBeenCalled()
  })
  it('迟到的历史页不覆盖新页，旧finally不提前结束loading', async () => {
    const { vm } = await setup()
    const old = deferred<ReturnType<typeof history>>()
    const current = deferred<ReturnType<typeof history>>()
    vi.mocked(api.getMigrationJobs).mockReturnValueOnce(old.promise as ReturnType<typeof api.getMigrationJobs>).mockReturnValueOnce(current.promise as ReturnType<typeof api.getMigrationJobs>)
    const a = vm.loadJobs()
    vm.jobPage = 2
    const b = vm.loadJobs()
    old.resolve(history(10))
    await a
    expect(vm.loading.jobs).toBe(true)
    current.resolve(history(20))
    await b
    expect(vm.jobs[0]?.id).toBe(20)
  })
  it('详情乱序返回时只显示最后选择的任务', async () => {
    const { vm } = await setup()
    const old = deferred<{ data: MigrationJob }>()
    vi.mocked(api.getMigrationJob).mockReturnValueOnce(old.promise as ReturnType<typeof api.getMigrationJob>).mockResolvedValueOnce({ data: { id: 2, status: 'completed' } } as Awaited<ReturnType<typeof api.getMigrationJob>>)
    const a = vm.showJobDetail({ id: 1 })
    await vm.showJobDetail({ id: 2 })
    old.resolve({ data: { id: 1, status: 'failed' } })
    await a
    expect(vm.jobDetail?.id).toBe(2)
  })
  it('详情失败不将列表摘要冒充完整详情，随后可重试', async () => {
    const { vm } = await setup()
    vi.mocked(api.getMigrationJob).mockRejectedValueOnce(new Error('synthetic failure'))
    await vm.showJobDetail({ id: 1, status: 'running' })
    expect(vm.jobDetail).toBeUndefined()
    expect(vm.jobDetailError).toBe(true)
    vi.mocked(api.getMigrationJob).mockResolvedValueOnce({ data: { id: 1, totalCount: 10 } } as Awaited<ReturnType<typeof api.getMigrationJob>>)
    await vm.showJobDetail({ id: 1 })
    expect(vm.jobDetail?.totalCount).toBe(10)
    expect(vm.jobDetailError).toBe(false)
  })
  it('关闭详情后不接受迟到响应', async () => {
    const { vm } = await setup()
    const pending = deferred<{ data: MigrationJob }>()
    vi.mocked(api.getMigrationJob).mockReturnValueOnce(pending.promise as ReturnType<typeof api.getMigrationJob>)
    const task = vm.showJobDetail({ id: 1 })
    vm.jobDrawerVisible = false
    await nextTick()
    pending.resolve({ data: { id: 1 } })
    await task
    expect(vm.jobDetail).toBeUndefined()
    expect(vm.loading.detail).toBe(false)
  })
  it('卸载后旧历史和详情不能回填', async () => {
    const { vm, wrapper } = await setup()
    const historyRead = deferred<ReturnType<typeof history>>()
    const detailRead = deferred<{ data: MigrationJob }>()
    vi.mocked(api.getMigrationJobs).mockReturnValueOnce(historyRead.promise as ReturnType<typeof api.getMigrationJobs>)
    vi.mocked(api.getMigrationJob).mockReturnValueOnce(detailRead.promise as ReturnType<typeof api.getMigrationJob>)
    const a = vm.loadJobs()
    const b = vm.showJobDetail({ id: 1 })
    wrapper.unmount()
    historyRead.resolve(history(20))
    detailRead.resolve({ data: { id: 1 } })
    await Promise.all([a, b])
    expect(vm.jobs[0]?.id).toBe(1)
    expect(vm.jobDetail).toBeUndefined()
  })
  it('最新历史失败保留旧页并可重试，旧失败不能污染新成功', async () => {
    const { vm } = await setup()
    vm.jobPage = 2
    vi.mocked(api.getMigrationJobs).mockRejectedValueOnce(new Error('current failure'))
    await vm.loadJobs()
    expect(vm.jobs[0]?.id).toBe(1)
    expect(vm.jobHistoryPage).toBe(1)
    expect(vm.jobHistoryError).toBe(true)
    const old = deferred<ReturnType<typeof history>>()
    vi.mocked(api.getMigrationJobs).mockReturnValueOnce(old.promise as ReturnType<typeof api.getMigrationJobs>).mockResolvedValueOnce(history(2) as Awaited<ReturnType<typeof api.getMigrationJobs>>)
    const task = vm.loadJobs()
    await vm.loadJobs()
    old.reject(new Error('old failure'))
    await task
    expect(vm.jobs[0]?.id).toBe(2)
    expect(vm.jobHistoryPage).toBe(2)
    expect(vm.jobHistoryError).toBe(false)
    expect(error).not.toHaveBeenCalled()
  })
  it('空详情和错误任务ID不能冒充所选详情', async () => {
    const { vm } = await setup()
    for (const data of [undefined, { id: 2 }]) {
      vi.mocked(api.getMigrationJob).mockResolvedValueOnce({ data } as Awaited<ReturnType<typeof api.getMigrationJob>>)
      await vm.showJobDetail({ id: 1 })
      expect(vm.jobDetail).toBeUndefined()
      expect(vm.jobDetailError).toBe(true)
    }
  })
  it('旧详情错误不能结束新请求的loading或覆盖新错误状态', async () => {
    const { vm } = await setup()
    const old = deferred<{ data: MigrationJob }>()
    const current = deferred<{ data: MigrationJob }>()
    vi.mocked(api.getMigrationJob).mockReturnValueOnce(old.promise as ReturnType<typeof api.getMigrationJob>).mockReturnValueOnce(current.promise as ReturnType<typeof api.getMigrationJob>)
    const a = vm.showJobDetail({ id: 1 })
    const b = vm.showJobDetail({ id: 2 })
    old.reject(new Error('old detail failure'))
    await a
    expect(vm.loading.detail).toBe(true)
    expect(vm.jobDetailError).toBe(false)
    current.resolve({ data: { id: 2 } })
    await b
    expect(vm.jobDetail?.id).toBe(2)
  })
  it('缓存离开使两类读失效，返回只补读所选历史页', async () => {
    const visible = ref(true)
    const parent = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(Page) : h('div') }) }, { global: globalOptions })
    wrappers.push(parent)
    await flushPromises()
    const vm = parent.findComponent(Page).vm as unknown as State
    vm.jobPage = 2
    const pending = deferred<ReturnType<typeof history>>()
    const detail = deferred<{ data: MigrationJob }>()
    vi.mocked(api.getMigrationJobs).mockReturnValueOnce(pending.promise as ReturnType<typeof api.getMigrationJobs>)
    vi.mocked(api.getMigrationJob).mockReturnValueOnce(detail.promise as ReturnType<typeof api.getMigrationJob>)
    const a = vm.loadJobs()
    const b = vm.showJobDetail({ id: 1 })
    visible.value = false
    await nextTick()
    pending.resolve(history(10))
    detail.resolve({ data: { id: 1 } })
    await Promise.all([a, b])
    expect(vm.jobs[0]?.id).toBe(1)
    expect(vm.jobDrawerVisible).toBe(false)
    expect(vm.jobDetail).toBeUndefined()
    visible.value = true
    await flushPromises()
    expect(api.getMigrationJobs).toHaveBeenLastCalledWith({ page: 2, size: 10 })
  })
})

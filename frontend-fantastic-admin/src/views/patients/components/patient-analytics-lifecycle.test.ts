import type { PatientMultiRecordGroup, PatientRecord } from '@/api/modules/patients'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import { getMissingIdCardPatients, getPatientAnalyticsSummary, getPatientMultiRecordGroups } from '@/api/modules/patients'
import Panel from './PatientAnalyticsPanel.vue'

const { store } = vi.hoisted(() => ({ store: { current: undefined as unknown as { sessionEpoch: number, isSessionVerified: boolean } } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => store.current }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => store.current.isSessionVerified }) }))
vi.mock('@/api/modules/patients', () => ({ getPatientAnalyticsSummary: vi.fn(), getMissingIdCardPatients: vi.fn(), getPatientMultiRecordGroups: vi.fn() }))
function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const summary = (n: number) => ({ data: { year: 2026, totalRecords: n, totalArchives: n, yearArchives: n, missingIdCardRecords: n, confirmedMultiRecordGroups: 0, suspectedMultiRecordGroups: 0, dateCounts: [], departmentCounts: [] } })
const missing = (id: number, page = 1) => ({ data: { list: [{ id }], total: 100, page, size: 10 } })
const multi = (name: string) => ({ data: { list: [{ matchType: 'IDCARD' as const, confidence: 'HIGH' as const, patientName: name, recordCount: 2, archiveCount: 2, archiveNumbers: [] }], total: 1, page: 1, size: 10 } })
interface State {
  summary?: { totalRecords: number }
  summaryLoading: boolean
  missingRecords: PatientRecord[]
  missingLoading: boolean
  missingError: string
  missingRowOffset: number
  multiGroups: PatientMultiRecordGroup[]
  includeSuspected: boolean
  loadSummary: () => Promise<void>
  loadMissingRecords: () => Promise<void>
  loadMultiGroups: () => Promise<void>
  handleMissingPageChange: (p: number) => void
}
const wrappers: ReturnType<typeof shallowMount>[] = []
async function setup() {
  const wrapper = shallowMount(Panel, { global: { directives: { loading: () => {} }, stubs: { 'el-alert': true, 'el-tabs': true, 'el-tab-pane': true, 'el-table': true, 'el-table-column': true, 'el-pagination': true, 'el-switch': true, 'el-tag': true, 'router-link': true, 'MrrMetricCard': true } } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper.vm as unknown as State
}
beforeEach(() => {
  store.current = reactive({ sessionEpoch: 1, isSessionVerified: true })
  vi.mocked(getPatientAnalyticsSummary).mockReset().mockResolvedValue(summary(1))
  vi.mocked(getMissingIdCardPatients).mockReset().mockResolvedValue(missing(1))
  vi.mocked(getPatientMultiRecordGroups).mockReset().mockResolvedValue(multi('current'))
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()) })

describe('患者统计请求归属', () => {
  it('摘要迟到结果不覆盖新刷新', async () => {
    const vm = await setup()
    const old = deferred<ReturnType<typeof summary>>()
    vi.mocked(getPatientAnalyticsSummary).mockReturnValueOnce(old.promise).mockResolvedValueOnce(summary(3))
    const a = vm.loadSummary()
    await vm.loadSummary()
    old.resolve(summary(2))
    await a
    expect(vm.summary?.totalRecords).toBe(3)
  })
  it('缺失证号旧分页不覆盖新页及行号', async () => {
    const vm = await setup()
    const old = deferred<ReturnType<typeof missing>>()
    vi.mocked(getMissingIdCardPatients).mockReturnValueOnce(old.promise).mockResolvedValueOnce(missing(3, 3))
    const a = vm.loadMissingRecords()
    vm.handleMissingPageChange(3)
    await flushPromises()
    old.resolve(missing(2))
    await a
    expect(vm.missingRecords).toEqual([{ id: 3 }])
    expect(vm.missingRowOffset).toBe(20)
  })
  it('切换疑似分组过滤后不接受旧结果', async () => {
    const vm = await setup()
    const old = deferred<ReturnType<typeof multi>>()
    vi.mocked(getPatientMultiRecordGroups).mockReturnValueOnce(old.promise).mockResolvedValueOnce(multi('filtered'))
    const a = vm.loadMultiGroups()
    vm.includeSuspected = false
    await nextTick()
    await flushPromises()
    old.resolve(multi('stale'))
    await a
    expect(vm.multiGroups[0]?.patientName).toBe('filtered')
  })
  it('会话失效清空摘要与两组列表并忽略迟到响应', async () => {
    const vm = await setup()
    const a = deferred<ReturnType<typeof summary>>()
    const b = deferred<ReturnType<typeof missing>>()
    const c = deferred<ReturnType<typeof multi>>()
    vi.mocked(getPatientAnalyticsSummary).mockReturnValueOnce(a.promise)
    vi.mocked(getMissingIdCardPatients).mockReturnValueOnce(b.promise)
    vi.mocked(getPatientMultiRecordGroups).mockReturnValueOnce(c.promise)
    const tasks = [vm.loadSummary(), vm.loadMissingRecords(), vm.loadMultiGroups()]
    store.current.sessionEpoch++
    store.current.isSessionVerified = false
    await nextTick()
    a.resolve(summary(9)); b.resolve(missing(9)); c.resolve(multi('stale'))
    await Promise.all(tasks)
    expect(vm.summary).toBeUndefined()
    expect(vm.missingRecords).toEqual([])
    expect(vm.multiGroups).toEqual([])
  })
  it('当前分页失败有明确错误状态，重试后恢复列表', async () => {
    const vm = await setup()
    vi.mocked(getMissingIdCardPatients).mockRejectedValueOnce(new Error('synthetic error'))
    await vm.loadMissingRecords()
    expect(vm.missingRecords).toEqual([])
    expect(vm.missingError).toBe('synthetic error')
    expect(vm.missingLoading).toBe(false)
    vi.mocked(getMissingIdCardPatients).mockResolvedValueOnce(missing(4))
    await vm.loadMissingRecords()
    expect(vm.missingError).toBe('')
    expect(vm.missingRecords).toEqual([{ id: 4 }])
  })
})

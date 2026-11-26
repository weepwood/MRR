import type { ArchiveBoxRecord } from '@/api/modules/archive-boxes'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { createArchiveBoxRecord, deleteArchiveBoxRecord, getArchiveBoxGroups, getArchiveBoxRecords, getArchiveBoxSummary, updateArchiveBoxRecord } from '@/api/modules/archive-boxes'
import Page from './index.vue'

const { store, confirm, success } = vi.hoisted(() => ({
  store: { current: undefined as unknown as { sessionEpoch: number, isSessionVerified: boolean, canEdit: boolean } },
  confirm: vi.fn(),
  success: vi.fn(),
}))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => store.current }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: (permission: string) => store.current.isSessionVerified && (permission !== 'record:edit' || store.current.canEdit) }) }))
vi.mock('@/api/modules/archive-boxes', () => ({
  createArchiveBoxRecord: vi.fn(),
  deleteArchiveBoxRecord: vi.fn(),
  getArchiveBoxGroups: vi.fn(),
  getArchiveBoxRecords: vi.fn(),
  getArchiveBoxSummary: vi.fn(),
  updateArchiveBoxRecord: vi.fn(),
}))
vi.mock('@/ui/components/ActionDialog', () => ({ actionDialog: { confirm } }))
vi.mock('element-plus', () => ({ ElMessage: { success, warning: vi.fn() } }))
vi.mock('@/composables/useTableActionLayout', () => ({ useTableActionLayout: () => ({ maxInlineActions: ref(1), actionColumnWidth: ref(120) }) }))

function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const record = (id: number): ArchiveBoxRecord => ({ id, bah: `synthetic-${id}`, boxNo: 'BOX-SYNTHETIC', status: 'NORMAL' })
const result = (id: number, page = 1) => ({ data: { list: [record(id)], page, size: 100, total: 300 } })
const groups = { data: { list: [{ boxNo: 'BOX-SYNTHETIC', recordCount: 1, abnormalCount: 0 }], page: 1, size: 100, total: 1 } }
interface State {
  records: ArchiveBoxRecord[]
  summary?: { totalRecords: number }
  summaryError: string
  loadSummary: () => Promise<void>
  openCreate: () => void
  recordRowOffset: number
  page: number
  loading: boolean
  error: string
  dialogVisible: boolean
  submitting: boolean
  loadRecords: () => Promise<void>
  handleViewChange: (v: string) => void
  openEdit: (row: ArchiveBoxRecord) => void
  submitForm: () => Promise<void>
  removeRecord: (row: ArchiveBoxRecord) => Promise<void>
}
const wrappers: ReturnType<typeof shallowMount>[] = []
async function setup() {
  const wrapper = shallowMount(Page, { global: { stubs: { 'el-button': true, 'el-dialog': true, 'el-form': true, 'el-form-item': true, 'el-table': true, 'el-table-column': true, 'el-pagination': true, 'el-radio-group': true, 'el-radio-button': true, 'el-input': true, 'el-select': true, 'el-option': true, 'el-tag': true, 'FaIcon': true, 'MrrMetricCard': true } } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper.vm as unknown as State
}
beforeEach(() => {
  store.current = reactive({ sessionEpoch: 1, isSessionVerified: true, canEdit: true })
  confirm.mockReset().mockResolvedValue(undefined)
  success.mockReset()
  vi.mocked(getArchiveBoxRecords).mockReset().mockResolvedValue(result(1))
  vi.mocked(getArchiveBoxGroups).mockReset().mockResolvedValue(groups)
  vi.mocked(getArchiveBoxSummary).mockReset().mockResolvedValue({ data: { totalRecords: 1, totalBoxes: 1, missingCount: 0, abnormalCount: 0 } })
  vi.mocked(updateArchiveBoxRecord).mockReset().mockResolvedValue({ data: record(1) })
  vi.mocked(createArchiveBoxRecord).mockReset().mockResolvedValue({ data: record(2) })
  vi.mocked(deleteArchiveBoxRecord).mockReset().mockResolvedValue({ data: 'ok' })
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()) })

describe('装箱操作归属', () => {
  it('旧页结果不得覆盖新页及序号', async () => {
    const vm = await setup()
    const old = deferred<ReturnType<typeof result>>()
    vi.mocked(getArchiveBoxRecords).mockReturnValueOnce(old.promise).mockResolvedValueOnce(result(3, 3))
    const a = vm.loadRecords()
    vm.page = 3
    await vm.loadRecords()
    old.resolve(result(2))
    await a
    expect(vm.records).toEqual([record(3)])
    expect(vm.recordRowOffset).toBe(200)
  })
  it('旧明细错误不影响当前箱号视图', async () => {
    const vm = await setup()
    const old = deferred<ReturnType<typeof result>>()
    const pending = deferred<typeof groups>()
    vi.mocked(getArchiveBoxRecords).mockReturnValueOnce(old.promise)
    vi.mocked(getArchiveBoxGroups).mockReturnValueOnce(pending.promise)
    const a = vm.loadRecords()
    vm.handleViewChange('boxes')
    old.reject(new Error('synthetic old error'))
    await a
    expect(vm.loading).toBe(true)
    expect(vm.error).toBe('')
    pending.resolve(groups)
    await flushPromises()
    expect(vm.loading).toBe(false)
    expect(vm.error).toBe('')
  })
  it('重复保存只写入一次', async () => {
    const vm = await setup()
    const pending = deferred<Awaited<ReturnType<typeof updateArchiveBoxRecord>>>()
    vi.mocked(updateArchiveBoxRecord).mockReturnValue(pending.promise)
    vm.openEdit(record(1))
    const a = vm.submitForm()
    const b = vm.submitForm()
    pending.resolve({ data: record(1) })
    await Promise.all([a, b])
    expect(updateArchiveBoxRecord).toHaveBeenCalledTimes(1)
  })
  it('会话变化后旧删除确认不得发起删除', async () => {
    const vm = await setup()
    const pending = deferred<void>()
    confirm.mockReturnValueOnce(pending.promise)
    const task = vm.removeRecord(record(1))
    store.current.sessionEpoch++
    pending.resolve()
    await task
    expect(deleteArchiveBoxRecord).not.toHaveBeenCalled()
  })
  it('旧保存响应不关闭新会话编辑弹窗', async () => {
    const vm = await setup()
    const pending = deferred<Awaited<ReturnType<typeof updateArchiveBoxRecord>>>()
    vi.mocked(updateArchiveBoxRecord).mockReturnValueOnce(pending.promise)
    vm.openEdit(record(1))
    const task = vm.submitForm()
    store.current.sessionEpoch++
    await flushPromises()
    vm.openEdit(record(2))
    pending.resolve({ data: record(1) })
    await task
    expect(vm.dialogVisible).toBe(true)
    expect(success).not.toHaveBeenCalled()
  })
  it('删除确认期间重复操作只删除一次，取消不写入', async () => {
    const vm = await setup()
    const pending = deferred<void>()
    confirm.mockReturnValueOnce(pending.promise)
    const a = vm.removeRecord(record(1))
    const b = vm.removeRecord(record(1))
    pending.resolve()
    await Promise.all([a, b])
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(deleteArchiveBoxRecord).toHaveBeenCalledTimes(1)
    expect(deleteArchiveBoxRecord).toHaveBeenCalledWith(1)
    confirm.mockRejectedValueOnce(new Error('synthetic cancel'))
    await vm.removeRecord(record(1))
    expect(deleteArchiveBoxRecord).toHaveBeenCalledTimes(1)
  })
  it('确认期间列表刷新或切换视图，不删除旧对象', async () => {
    const vm = await setup()
    const first = deferred<void>()
    confirm.mockReturnValueOnce(first.promise)
    const a = vm.removeRecord(record(1))
    await vm.loadRecords()
    first.resolve()
    await a
    expect(deleteArchiveBoxRecord).not.toHaveBeenCalled()
    const second = deferred<void>()
    confirm.mockReturnValueOnce(second.promise)
    const b = vm.removeRecord(record(1))
    vm.handleViewChange('boxes')
    vm.handleViewChange('records')
    await flushPromises()
    second.resolve()
    await b
    expect(deleteArchiveBoxRecord).not.toHaveBeenCalled()
  })
  it('编辑权限失效后关闭表单，不能保存或确认旧删除', async () => {
    const vm = await setup()
    vm.openEdit(record(1))
    store.current.canEdit = false
    await vm.submitForm()
    expect(vm.dialogVisible).toBe(false)
    expect(updateArchiveBoxRecord).not.toHaveBeenCalled()
    vm.openCreate()
    expect(vm.dialogVisible).toBe(false)
    store.current.canEdit = true
    const pending = deferred<void>()
    confirm.mockReturnValueOnce(pending.promise)
    const task = vm.removeRecord(record(1))
    store.current.canEdit = false
    pending.resolve()
    await task
    expect(deleteArchiveBoxRecord).not.toHaveBeenCalled()
  })
  it('写入失败后保留表单可重试，正常保存关闭', async () => {
    const vm = await setup()
    vm.openEdit(record(1))
    vi.mocked(updateArchiveBoxRecord).mockRejectedValueOnce(new Error('synthetic failure'))
    await vm.submitForm()
    expect(vm.dialogVisible).toBe(true)
    expect(vm.submitting).toBe(false)
    expect(success).not.toHaveBeenCalled()
    await vm.submitForm()
    expect(updateArchiveBoxRecord).toHaveBeenLastCalledWith(1, expect.objectContaining({ bah: 'synthetic-1', boxNo: 'BOX-SYNTHETIC' }))
    expect(vm.dialogVisible).toBe(false)
    expect(success).toHaveBeenCalledTimes(1)
  })
  it('旧摘要及旧列表在会话失效后不恢复，当前摘要失败可重试', async () => {
    const vm = await setup()
    vi.mocked(getArchiveBoxSummary).mockRejectedValueOnce(new Error('synthetic summary failure'))
    await vm.loadSummary()
    expect(vm.summary).toBeUndefined()
    expect(vm.summaryError).toBe('synthetic summary failure')
    await vm.loadSummary()
    expect(vm.summaryError).toBe('')
    expect(vm.summary?.totalRecords).toBe(1)
    const a = deferred<Awaited<ReturnType<typeof getArchiveBoxSummary>>>()
    const b = deferred<ReturnType<typeof result>>()
    vi.mocked(getArchiveBoxSummary).mockReturnValueOnce(a.promise)
    vi.mocked(getArchiveBoxRecords).mockReturnValueOnce(b.promise)
    const tasks = [vm.loadSummary(), vm.loadRecords()]
    store.current.sessionEpoch++
    store.current.isSessionVerified = false
    a.resolve({ data: { totalRecords: 9, totalBoxes: 9, missingCount: 9, abnormalCount: 9 } })
    b.resolve(result(9))
    await Promise.all(tasks)
    expect(vm.summary).toBeUndefined()
    expect(vm.records).toEqual([])
    expect(vm.loading).toBe(false)
  })
})

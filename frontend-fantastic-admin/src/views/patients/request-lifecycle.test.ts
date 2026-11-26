import type { PatientRecord } from '@/api/modules/patients'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { getPatients } from '@/api/modules/patients'
import { loadEffectiveSystemSettings } from '@/utils/system-settings'
import Page from './index.vue'

const { store } = vi.hoisted(() => ({ store: { current: undefined as unknown as { sessionEpoch: number, isSessionVerified: boolean } } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => store.current }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/modules/patients', () => ({ getPatients: vi.fn() }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => store.current.isSessionVerified }) }))
vi.mock('@/composables/useTableActionLayout', () => ({ useTableActionLayout: () => ({ maxInlineActions: ref(1), actionColumnWidth: ref(120) }) }))
vi.mock('@/utils/system-settings', () => ({ loadEffectiveSystemSettings: vi.fn().mockResolvedValue({ settings: { patientIdCardRevealEnabled: false, patientIdCardCopyEnabled: false } }), SYSTEM_SETTINGS_UPDATED_EVENT: 'synthetic-settings' }))

type Response = Awaited<ReturnType<typeof getPatients>>
function response(id: number, page = 1): Response {
  return { data: { list: [{ id }], total: 100, page, size: 20 } }
}
function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
interface State {
  tableData: PatientRecord[]
  loading: boolean
  error: string
  loadedRowOffset: number
  page: number
  loadData: () => Promise<void>
  handlePageChange: (p: number) => void
}
const wrappers: ReturnType<typeof shallowMount>[] = []
async function setup() {
  const wrapper = shallowMount(Page, { global: { stubs: { 'el-button': true, 'el-table': true, 'el-table-column': true, 'el-pagination': true, 'el-input': true, 'FaIcon': true } } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, vm: wrapper.vm as unknown as State }
}
beforeEach(() => {
  store.current = reactive({ sessionEpoch: 1, isSessionVerified: true })
  vi.mocked(loadEffectiveSystemSettings).mockResolvedValue({ settings: { patientIdCardRevealEnabled: false, patientIdCardCopyEnabled: false } } as Awaited<ReturnType<typeof loadEffectiveSystemSettings>>)
  vi.mocked(getPatients).mockReset().mockResolvedValue(response(1))
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()) })

describe('患者列表请求归属', () => {
  it('迟到旧页不会覆盖新页及行号', async () => {
    const { vm } = await setup()
    const old = deferred<Response>()
    vi.mocked(getPatients).mockReturnValueOnce(old.promise).mockResolvedValueOnce(response(3, 3))
    const first = vm.loadData()
    vm.handlePageChange(3)
    await flushPromises()
    old.resolve(response(2))
    await first
    expect(vm.tableData).toEqual([{ id: 3 }])
    expect(vm.loadedRowOffset).toBe(40)
  })
  it('旧失败不清空结果或结束当前加载', async () => {
    const { vm } = await setup()
    const old = deferred<Response>()
    const current = deferred<Response>()
    vi.mocked(getPatients).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    const a = vm.loadData()
    const b = vm.loadData()
    old.reject(new Error('synthetic old error'))
    await a
    expect(vm.loading).toBe(true)
    expect(vm.error).toBe('')
    expect(vm.tableData).toEqual([{ id: 1 }])
    current.resolve(response(2))
    await b
    expect(vm.tableData).toEqual([{ id: 2 }])
    expect(vm.loading).toBe(false)
  })
  it('会话失效清空旧列表并拒绝迟到响应', async () => {
    const { vm } = await setup()
    const pending = deferred<Response>()
    vi.mocked(getPatients).mockReturnValueOnce(pending.promise)
    const task = vm.loadData()
    store.current.sessionEpoch++
    store.current.isSessionVerified = false
    await flushPromises()
    pending.resolve(response(9))
    await task
    expect(vm.tableData).toEqual([])
    expect(vm.loading).toBe(false)
  })
})

import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import * as api from '@/api/modules/operations'
import Page from '../index.vue'

const { session, route, prompt, confirm, success, warning } = vi.hoisted(() => ({
  session: { current: undefined as unknown as { token: string, sessionEpoch: number, isSessionVerified: boolean, manage: boolean, roles: boolean } },
  route: { query: { tab: 'maintenance' } },
  prompt: vi.fn(),
  confirm: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session.current }))
vi.mock('@/utils/session', () => ({
  hasPermission: (p: string) => session.current.isSessionVerified && (p === 'system:manage' ? session.current.manage : p === 'role:read' ? session.current.roles : true),
  hasAnyPermission: () => true,
}))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ replace: vi.fn().mockResolvedValue(undefined), resolve: () => ({ meta: { auth: [] } }), push: vi.fn() }) }))
vi.mock('@/ui/components/ActionDialog', () => ({ actionDialog: { prompt, confirm } }))
vi.mock('element-plus', () => ({ ElMessage: { success, warning, error: vi.fn() } }))
vi.mock('@/api/modules/operations', () => ({
  diagnoseImageSource: vi.fn(),
  disableMaintenanceMode: vi.fn(),
  enableMaintenanceMode: vi.fn(),
  getDeploymentReadiness: vi.fn(),
  getExportCenter: vi.fn(),
  getIntegritySummary: vi.fn(),
  getMaintenanceStatus: vi.fn(),
  getOperationAudit: vi.fn(),
  getOperationsDiagnosticReport: vi.fn(),
  getOperationsOverview: vi.fn(),
  getPermissionMatrix: vi.fn(),
  runOperationsDiagnostics: vi.fn(),
  savePermissionMatrixSnapshot: vi.fn(),
  verifyImageContent: vi.fn(),
}))
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const readiness = { mode: 'READ_WRITE', automaticReadOnly: false, checks: [] } as unknown as api.ReadinessSnapshot
const snapshot = { readiness, runtime: {}, taskSummary: { counts: {} } } as api.OperationsOverview
interface State {
  maintenance?: api.MaintenanceStatus
  maintenanceError: string
  permissionError: string
  operationPending: boolean
  loading: { maintenance: boolean }
  enableMaintenance: () => Promise<void>
  disableMaintenance: (event: MouseEvent) => Promise<void>
  loadMaintenance: (refresh?: boolean) => Promise<void>
  savePermissionSnapshot: () => Promise<void>
  selectTab: (tab: string) => Promise<void>
}
const wrappers: { unmount: () => void }[] = []
async function setup(tab = 'maintenance') {
  route.query.tab = tab
  const wrapper = shallowMount(Page, { global: { directives: { loading: () => {} }, stubs: Object.fromEntries(['el-button', 'el-tabs', 'el-tab-pane', 'el-alert', 'el-tag', 'el-table', 'el-table-column', 'el-input', 'el-form', 'el-form-item', 'el-input-number', 'el-select', 'el-option', 'el-progress', 'FaIcon', 'MrrMetricCard'].map(name => [name, true])) } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper.vm as unknown as State
}
beforeEach(() => {
  vi.resetAllMocks()
  session.current = reactive({ token: 'synthetic', sessionEpoch: 1, isSessionVerified: true, manage: true, roles: true })
  prompt.mockResolvedValue({ value: 'synthetic' })
  confirm.mockResolvedValue(undefined)
  vi.mocked(api.getOperationsOverview).mockResolvedValue(snapshot)
  vi.mocked(api.getMaintenanceStatus).mockResolvedValue({ enabled: false })
  vi.mocked(api.getDeploymentReadiness).mockResolvedValue(readiness)
  vi.mocked(api.enableMaintenanceMode).mockResolvedValue({ enabled: true })
  vi.mocked(api.disableMaintenanceMode).mockResolvedValue({ enabled: false })
  vi.mocked(api.getPermissionMatrix).mockResolvedValue({ endpoints: [], roles: [], diff: { available: false, changes: [] } } as unknown as api.PermissionMatrix)
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('运维操作归属', () => {
  it('确认期间重复进入只执行一次', async () => {
    const vm = await setup()
    const gate = deferred<{ value: string }>()
    prompt.mockReturnValue(gate.promise)
    const a = vm.enableMaintenance()
    const b = vm.enableMaintenance()
    gate.resolve({ value: 'synthetic' })
    await Promise.all([a, b])
    expect(prompt).toHaveBeenCalledTimes(1)
    expect(api.enableMaintenanceMode).toHaveBeenCalledTimes(1)
  })
  it('会话切换使未完成的维护确认失效', async () => {
    const vm = await setup()
    const gate = deferred<{ value: string }>()
    prompt.mockReturnValueOnce(gate.promise)
    const task = vm.enableMaintenance()
    session.current.sessionEpoch++
    gate.resolve({ value: 'synthetic' })
    await task
    expect(api.enableMaintenanceMode).not.toHaveBeenCalled()
  })
  it('权限撤销后不执行旧退出确认', async () => {
    vi.mocked(api.getMaintenanceStatus).mockResolvedValue({ enabled: true })
    const vm = await setup()
    const gate = deferred<void>()
    confirm.mockReturnValueOnce(gate.promise)
    const task = vm.disableMaintenance(new MouseEvent('click'))
    session.current.manage = false
    gate.resolve()
    await task
    expect(api.disableMaintenanceMode).not.toHaveBeenCalled()
  })
  it('维护状态回读期间不启动变更', async () => {
    const vm = await setup()
    const gate = deferred<api.MaintenanceStatus>()
    vi.mocked(api.getMaintenanceStatus).mockReturnValueOnce(gate.promise)
    const read = vm.loadMaintenance()
    await vm.enableMaintenance()
    gate.resolve({ enabled: false })
    await read
    expect(prompt).not.toHaveBeenCalled()
    expect(api.enableMaintenanceMode).not.toHaveBeenCalled()
  })
  it('权限快照重复确认只提交一次', async () => {
    const vm = await setup('permissions')
    const gate = deferred<{ value: string }>()
    prompt.mockReturnValue(gate.promise)
    const a = vm.savePermissionSnapshot()
    const b = vm.savePermissionSnapshot()
    gate.resolve({ value: 'synthetic' })
    await Promise.all([a, b])
    expect(prompt).toHaveBeenCalledTimes(1)
    expect(api.savePermissionMatrixSnapshot).toHaveBeenCalledTimes(1)
  })
  it('离开权限页后不接受旧快照确认', async () => {
    const vm = await setup('permissions')
    const gate = deferred<{ value: string }>()
    prompt.mockReturnValueOnce(gate.promise)
    const task = vm.savePermissionSnapshot()
    await vm.selectTab('overview')
    gate.resolve({ value: 'synthetic' })
    await task
    expect(api.savePermissionMatrixSnapshot).not.toHaveBeenCalled()
  })
  it('读取失败不冒充正常状态，重试后才能启用', async () => {
    const vm = await setup()
    vi.mocked(api.getMaintenanceStatus).mockRejectedValueOnce(new Error('synthetic unavailable'))
    await vm.loadMaintenance()
    expect(vm.maintenance).toBeUndefined()
    expect(vm.maintenanceError).toContain('读取失败')
    await vm.enableMaintenance()
    expect(api.enableMaintenanceMode).not.toHaveBeenCalled()
    await vm.loadMaintenance()
    expect(vm.maintenanceError).toBe('')
    await vm.enableMaintenance()
    expect(api.enableMaintenanceMode).toHaveBeenCalledExactlyOnceWith('synthetic')
    expect(vm.maintenance?.enabled).toBe(true)
    expect(vm.operationPending).toBe(false)
  })
  it('写入结果未知后禁止重复变更，重新检查恢复', async () => {
    const vm = await setup()
    vi.mocked(api.enableMaintenanceMode).mockRejectedValueOnce(new Error('synthetic write failure'))
    await vm.enableMaintenance()
    expect(vm.maintenance).toBeUndefined()
    expect(vm.maintenanceError).toContain('未核对')
    await vm.enableMaintenance()
    expect(api.enableMaintenanceMode).toHaveBeenCalledTimes(1)
    await vm.loadMaintenance()
    await vm.enableMaintenance()
    expect(api.enableMaintenanceMode).toHaveBeenCalledTimes(2)
  })
  it('退出取消不写入，正常确认只写一次', async () => {
    vi.mocked(api.getMaintenanceStatus).mockResolvedValue({ enabled: true })
    const vm = await setup()
    confirm.mockRejectedValueOnce(new Error('synthetic cancel'))
    await vm.disableMaintenance(new MouseEvent('click'))
    expect(api.disableMaintenanceMode).not.toHaveBeenCalled()
    expect(vm.operationPending).toBe(false)
    await vm.disableMaintenance(new MouseEvent('click'))
    expect(api.disableMaintenanceMode).toHaveBeenCalledTimes(1)
    expect(vm.maintenance?.enabled).toBe(false)
  })
  it('权限快照保存失败后先刷新，再允许确认', async () => {
    const vm = await setup('permissions')
    vi.mocked(api.savePermissionMatrixSnapshot).mockRejectedValueOnce(new Error('synthetic write failure'))
    await vm.savePermissionSnapshot()
    expect(vm.permissionError).toContain('未核对')
    await vm.savePermissionSnapshot()
    expect(api.savePermissionMatrixSnapshot).toHaveBeenCalledTimes(1)
  })
  it('离开再返回维护页不使用旧确认', async () => {
    const vm = await setup()
    const gate = deferred<{ value: string }>()
    prompt.mockReturnValueOnce(gate.promise)
    const task = vm.enableMaintenance()
    await vm.selectTab('overview')
    await vm.selectTab('maintenance')
    gate.resolve({ value: 'synthetic' })
    await task
    expect(api.enableMaintenanceMode).not.toHaveBeenCalled()
    expect(vm.maintenance).toBeUndefined()
    await vm.loadMaintenance()
    expect(vm.maintenance?.enabled).toBe(false)
  })
})

import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, KeepAlive, nextTick, ref } from 'vue'
import * as api from '@/api/modules/settings'
import RuntimeSettings from './RuntimeSettings.vue'

const { confirm, success, error, info } = vi.hoisted(() => ({ confirm: vi.fn(), success: vi.fn(), error: vi.fn(), info: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm }, ElMessage: { success, error, info } }))
vi.mock('@/api/modules/settings', () => ({ getSettingsSchema: vi.fn(), saveSystemSettings: vi.fn(), deleteSetting: vi.fn() }))

function schema() {
  return { data: { groups: [{ key: 'security', label: '安全策略' }], items: [
    { key: 'loginMaxAttempts', group: 'security', type: 'integer', label: '登录失败上限', description: '', unit: '次', min: 3, max: 20, allowedValues: [], effect: 'IMMEDIATE', sensitive: true, defaultValue: '5', effectiveValue: '7', overrideValue: '7' },
    { key: 'accessLogCleanupBatchSize', group: 'security', type: 'integer', label: '单批删除条数', description: '', unit: '条', min: 100, max: 50000, allowedValues: [], effect: 'IMMEDIATE', sensitive: false, defaultValue: '100', effectiveValue: '100', overrideValue: null },
  ] } } as Awaited<ReturnType<typeof api.getSettingsSchema>>
}
interface State {
  numberValues: Record<string, number>
  savedMap: Record<string, string>
  overrides: Record<string, string>
  statusLabel: string
  canSave: boolean
  synced: boolean
  schema: NonNullable<ReturnType<typeof schema>['data']>
  saving: boolean
  loading: boolean
  isDirty: boolean
  saveSettings: () => Promise<boolean>
  loadSettings: () => Promise<unknown>
  clearOverride: (item: State['schema']['items'][number]) => Promise<unknown>
  fillWithConfigDefaults: () => void
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const global = { directives: { loading: () => {} }, stubs: Object.fromEntries(['el-alert', 'el-button', 'el-tag', 'el-form', 'el-form-item', 'el-switch', 'el-input-number', 'el-select', 'el-option', 'el-input', 'FaIcon'].map(name => [name, true])) }
const wrappers: { unmount: () => void }[] = []
async function setup() {
  const wrapper = shallowMount(RuntimeSettings, { global })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, vm: wrapper.vm as unknown as State }
}
beforeEach(() => {
  vi.resetAllMocks()
  confirm.mockResolvedValue(undefined)
  vi.mocked(api.getSettingsSchema).mockImplementation(async () => schema())
  vi.mocked(api.saveSystemSettings).mockResolvedValue({ data: undefined } as Awaited<ReturnType<typeof api.saveSystemSettings>>)
  vi.mocked(api.deleteSetting).mockResolvedValue({ data: undefined } as Awaited<ReturnType<typeof api.deleteSetting>>)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('运行参数提交安全', () => {
  it('固定确认快照，确认期间的其他编辑不进入本次payload', async () => {
    const { vm } = await setup()
    vm.numberValues.loginMaxAttempts = 10
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const saving = vm.saveSettings()
    vm.numberValues.loginMaxAttempts = 11
    vm.numberValues.accessLogCleanupBatchSize = 200
    decision.resolve()
    await saving
    expect(api.saveSystemSettings).toHaveBeenCalledExactlyOnceWith({ loginMaxAttempts: '10' })
  })
  it('从确认开始防重，保存/恢复/加载互斥', async () => {
    const { vm } = await setup()
    vm.numberValues.loginMaxAttempts = 10
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const saving = vm.saveSettings()
    expect(vm.saving).toBe(true)
    await vm.saveSettings()
    await vm.clearOverride(vm.schema.items[0])
    await vm.loadSettings()
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(api.getSettingsSchema).toHaveBeenCalledTimes(1)
    decision.reject('cancel')
    expect(await saving).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
    expect(api.deleteSetting).not.toHaveBeenCalled()
  })
  it('写结果未知时保留编辑，阻止直接重复写入直到重新读取', async () => {
    const { vm } = await setup()
    vm.numberValues.loginMaxAttempts = 10
    vi.mocked(api.saveSystemSettings).mockRejectedValueOnce(new Error('synthetic secret'))
    expect(await vm.saveSettings()).toBe(false)
    expect(vm.numberValues.loginMaxAttempts).toBe(10)
    expect(vm.isDirty).toBe(true)
    await vm.saveSettings()
    expect(api.saveSystemSettings).toHaveBeenCalledTimes(1)
    expect(success).not.toHaveBeenCalled()
    expect(error.mock.calls.flat().join('')).not.toContain('synthetic secret')
  })
  it('写成功但回读失败不宣布同步成功，不丢弃编辑', async () => {
    const { vm } = await setup()
    vm.numberValues.loginMaxAttempts = 10
    vi.mocked(api.getSettingsSchema).mockRejectedValueOnce(new Error('synthetic'))
    expect(await vm.saveSettings()).toBe(false)
    expect(success).not.toHaveBeenCalled()
    expect(vm.numberValues.loginMaxAttempts).toBe(10)
    await vm.saveSettings()
    expect(api.saveSystemSettings).toHaveBeenCalledTimes(1)
  })
  it('加载期间禁止填默认或发送写请求', async () => {
    const { vm } = await setup()
    const loading = deferred<ReturnType<typeof schema>>()
    vi.mocked(api.getSettingsSchema).mockReturnValueOnce(loading.promise)
    const task = vm.loadSettings()
    vm.fillWithConfigDefaults()
    expect(vm.numberValues.loginMaxAttempts).toBe(7)
    vm.numberValues.loginMaxAttempts = 10
    expect(await vm.saveSettings()).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
    loading.resolve(schema())
    await task
  })
  it.each(['save', 'restore'])('缓存离页后的旧确认不能执行 %s', async (action) => {
    const visible = ref(true)
    const wrapper = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(RuntimeSettings) : null }) }, { global })
    wrappers.push(wrapper)
    await flushPromises()
    const vm = wrapper.findComponent(RuntimeSettings).vm as unknown as State
    vm.numberValues.loginMaxAttempts = 10
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = action === 'save' ? vm.saveSettings() : vm.clearOverride(vm.schema.items[0])
    visible.value = false
    await nextTick()
    visible.value = true
    await nextTick()
    decision.resolve()
    await task
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
    expect(api.deleteSetting).not.toHaveBeenCalled()
  })
  it('已发出的写入在缓存离页后仍防重且不回填迟到结果', async () => {
    const visible = ref(true)
    const wrapper = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(RuntimeSettings) : null }) }, { global })
    wrappers.push(wrapper)
    await flushPromises()
    const vm = wrapper.findComponent(RuntimeSettings).vm as unknown as State
    vm.numberValues.loginMaxAttempts = 10
    const write = deferred<Awaited<ReturnType<typeof api.saveSystemSettings>>>()
    vi.mocked(api.saveSystemSettings).mockReturnValueOnce(write.promise)
    const task = vm.saveSettings()
    await flushPromises()
    visible.value = false
    await nextTick()
    visible.value = true
    await nextTick()
    await vm.saveSettings()
    expect(vm.saving).toBe(true)
    expect(api.saveSystemSettings).toHaveBeenCalledTimes(1)
    write.resolve({ data: undefined } as Awaited<ReturnType<typeof api.saveSystemSettings>>)
    await task
    expect(success).not.toHaveBeenCalled()
    expect(api.getSettingsSchema).toHaveBeenCalledTimes(1)
  })
  it('非敏感项单独保存沿用无需二次确认的行为，回读成功才返回true', async () => {
    const { vm } = await setup()
    vm.numberValues.accessLogCleanupBatchSize = 200
    expect(await vm.saveSettings()).toBe(true)
    expect(confirm).not.toHaveBeenCalled()
    expect(api.saveSystemSettings).toHaveBeenCalledExactlyOnceWith({ accessLogCleanupBatchSize: '200' })
    expect(vm.synced).toBe(true)
  })
  it('与目录规则组合后仍从分组、基线和批量保存排除其专属设置', async () => {
    const payload = schema()
    payload.data!.groups.push({ key: 'archive', label: '档案浏览' })
    payload.data!.items.push({ ...payload.data!.items[0], key: 'imageDirectoryRoutingRules', group: 'archive', type: 'text', defaultValue: '[]', effectiveValue: '[]', overrideValue: '[]' })
    vi.mocked(api.getSettingsSchema).mockResolvedValueOnce(payload)
    const { vm } = await setup()
    expect(vm.schema.items.map(item => item.key)).not.toContain('imageDirectoryRoutingRules')
    expect(vm.schema.groups.map(group => group.key)).not.toContain('archive')
    expect(vm.savedMap).not.toHaveProperty('imageDirectoryRoutingRules')
    vm.numberValues.loginMaxAttempts = 10
    expect(await vm.saveSettings()).toBe(true)
    expect(api.saveSystemSettings).toHaveBeenCalledExactlyOnceWith({ loginMaxAttempts: '10' })
  })
  it('恢复确认固定key与说明，并明确其他草稿会被丢弃', async () => {
    const { vm } = await setup()
    const item = vm.schema.items[0]
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = vm.clearOverride(item)
    item.key = 'accessLogCleanupBatchSize'
    item.label = 'changed label'
    decision.resolve()
    await task
    expect(confirm.mock.calls[0][0]).toContain('登录失败上限')
    expect(confirm.mock.calls[0][0]).toContain('其他未保存修改')
    expect(api.deleteSetting).toHaveBeenCalledExactlyOnceWith('loginMaxAttempts')
  })
  it.each([false, true])('恢复失败和恢复成功但回读失败均要求重新核对 applied=%s', async (applied) => {
    const { vm } = await setup()
    if (applied) { vi.mocked(api.getSettingsSchema).mockRejectedValueOnce(new Error('secret')) }
    else { vi.mocked(api.deleteSetting).mockRejectedValueOnce(new Error('secret')) }
    await vm.clearOverride(vm.schema.items[0])
    expect(vm.synced).toBe(false)
    expect(vm.statusLabel).toContain('待重新核对')
    expect(success).not.toHaveBeenCalled()
    const messages = error.mock.calls.flat().join('')
    expect(messages).toContain(applied ? '覆盖值已清除' : '未能确认恢复结果')
    expect(messages).not.toContain('secret')
    await vm.clearOverride(vm.schema.items[0])
    expect(api.deleteSetting).toHaveBeenCalledTimes(1)
    await vm.loadSettings()
    expect(vm.synced).toBe(true)
  })
  it('畸形GET不部分更新schema、覆盖、基线或编辑', async () => {
    const { vm } = await setup()
    vm.numberValues.loginMaxAttempts = 10
    const previous = { schema: vm.schema, overrides: { ...vm.overrides }, saved: { ...vm.savedMap } }
    const malformed = schema()
    malformed.data!.items[0].effectiveValue = '19'
    malformed.data!.items.push(null as unknown as State['schema']['items'][number])
    vi.mocked(api.getSettingsSchema).mockResolvedValueOnce(malformed)
    expect(await vm.loadSettings()).toBe(false)
    expect(vm.schema).toBe(previous.schema)
    expect(vm.overrides).toEqual(previous.overrides)
    expect(vm.savedMap).toEqual(previous.saved)
    expect(vm.numberValues.loginMaxAttempts).toBe(10)
    expect(vm.synced).toBe(false)
  })
  it('首次读取失败不显示已同步且不能保存，取消dirty重载不读取', async () => {
    vi.mocked(api.getSettingsSchema).mockRejectedValueOnce(new Error('secret'))
    const { vm } = await setup()
    expect(vm.statusLabel).not.toBe('运行参数已同步')
    expect(vm.canSave).toBe(false)
    await vm.loadSettings()
    vm.numberValues.loginMaxAttempts = 10
    confirm.mockRejectedValueOnce('cancel')
    expect(await vm.loadSettings()).toBe(false)
    expect(api.getSettingsSchema).toHaveBeenCalledTimes(2)
    expect(vm.numberValues.loginMaxAttempts).toBe(10)
  })
  it.each([false, true])('缓存离页后的慢GET不改基线或编辑，失败也不提示旧结果 reject=%s', async (reject) => {
    const visible = ref(true)
    const wrapper = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(RuntimeSettings) : null }) }, { global })
    wrappers.push(wrapper)
    await flushPromises()
    const vm = wrapper.findComponent(RuntimeSettings).vm as unknown as State
    const read = deferred<ReturnType<typeof schema>>()
    vi.mocked(api.getSettingsSchema).mockReturnValueOnce(read.promise)
    const task = vm.loadSettings()
    visible.value = false
    await nextTick()
    visible.value = true
    await nextTick()
    vm.numberValues.loginMaxAttempts = 10
    if (reject) { read.reject(new Error('secret')) }
    else { read.resolve(schema()) }
    expect(await task).toBe(false)
    expect(vm.numberValues.loginMaxAttempts).toBe(10)
    expect(vm.savedMap.loginMaxAttempts).toBe('7')
    expect(error).not.toHaveBeenCalled()
    expect(vm.canSave).toBe(false)
  })
  it('销毁后未确认操作不写入', async () => {
    const { wrapper, vm } = await setup()
    vm.numberValues.loginMaxAttempts = 10
    const decision = deferred<void>()
    confirm.mockReturnValueOnce(decision.promise)
    const task = vm.saveSettings()
    wrapper.unmount()
    decision.resolve()
    expect(await task).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
  })
})

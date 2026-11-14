import { flushPromises, mount as mountVue, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, KeepAlive, nextTick, ref } from 'vue'
import * as api from '@/api/modules/settings'
import { DIRECTORY_ROUTING_KEY, parseDirectoryRules, previewDirectoryRule, validateDirectoryRules } from './image-directory-routing'
import ImageDirectoryRoutingSettings from './ImageDirectoryRoutingSettings.vue'
import RuntimeSettings from './RuntimeSettings.vue'

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm }, ElMessage: { error: vi.fn() } }))
vi.mock('@/api/modules/settings', () => ({ getSettingsSchema: vi.fn(), setSetting: vi.fn(), deleteSetting: vi.fn(), saveSystemSettings: vi.fn() }))
const builtIn = [{ type: 'exact' as const, value: '2026.06.05', node: 'ba03' }]
function schema(override: string | null = null) {
  return { data: { groups: [{ key: 'archive', label: '档案浏览' }], items: [{ key: DIRECTORY_ROUTING_KEY, group: 'archive', type: 'text', defaultValue: JSON.stringify(builtIn), effectiveValue: override ?? JSON.stringify(builtIn), overrideValue: override }] } } as Awaited<ReturnType<typeof api.getSettingsSchema>>
}
interface State {
  rules: typeof builtIn
  save: (restore?: boolean) => Promise<void>
  load: () => Promise<void>
  busy: boolean
  needsReload: boolean
  loaded: boolean
  canRestore: boolean
  isDirty: boolean
  feedback: string
}
const wrappers: ReturnType<typeof shallowMount>[] = []
function mount(component = ImageDirectoryRoutingSettings) {
  const wrapper = shallowMount(component, { global: { directives: { auth: () => {}, loading: () => {} }, stubs: { 'el-tag': true, 'el-alert': true, 'el-input': true, 'el-select': true, 'el-option': true, 'el-button': true } } })
  wrappers.push(wrapper)
  return { wrapper, vm: wrapper.vm as unknown as State }
}
beforeEach(() => {
  vi.resetAllMocks()
  confirm.mockResolvedValue(undefined)
  vi.mocked(api.getSettingsSchema).mockResolvedValue(schema())
  vi.mocked(api.setSetting).mockResolvedValue({ data: undefined } as Awaited<ReturnType<typeof api.setSetting>>)
  vi.mocked(api.deleteSetting).mockResolvedValue({ data: undefined } as Awaited<ReturnType<typeof api.deleteSetting>>)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('目录路由编辑', () => {
  it('预览精确目录优先，不合并两位与四位年份', () => {
    const rules = [{ type: 'month' as const, value: '2026.06', node: 'ba01' }, ...builtIn]
    expect(previewDirectoryRule('2026.06.05', rules)).toContain('精确目录 2026.06.05 → BA03')
    expect(previewDirectoryRule('2026.06.06', rules)).toContain('年月 2026.06 → BA01')
    expect(previewDirectoryRule('26.06.05', rules)).toContain('未命中规则')
    expect(previewDirectoryRule('', rules)).toContain('默认节点')
  })
  it.each(['2026.02.30', '../2026.06.05', '2026.6.05', '0000.01.01'])('拒绝无效目录 %s', (value) => {
    expect(validateDirectoryRules([{ type: 'exact', value, node: 'ba01' }])).not.toBe('')
  })
  it('拒绝重复、未知字段和任意URL；空规则允许且有界', () => {
    expect(validateDirectoryRules([...builtIn, ...builtIn])).toContain('重复')
    expect(() => parseDirectoryRules('[{"type":"exact","value":"2026.06.05","node":"http://example.invalid"}]')).toThrow()
    expect(() => parseDirectoryRules('[{"type":"exact","value":"2026.06.05","node":"ba01","url":"secret"}]')).toThrow()
    expect(validateDirectoryRules([])).toBe('')
    expect(validateDirectoryRules(Array.from({ length: 201 }).fill(builtIn[0]))).toContain('200')
  })
  it('确认快照固定，确认期间禁止重复提交；单键保存不触碰其他设置', async () => {
    const { vm } = mount()
    await flushPromises()
    vm.rules = []
    let resolve!: () => void
    confirm.mockReturnValueOnce(new Promise<void>((yes) => { resolve = yes }))
    const saving = vm.save()
    expect(vm.busy).toBe(true)
    vm.rules = builtIn
    await vm.save()
    expect(confirm).toHaveBeenCalledTimes(1)
    resolve()
    await saving
    expect(api.setSetting).toHaveBeenCalledExactlyOnceWith(DIRECTORY_ROUTING_KEY, '[]')
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
    expect(vm.rules).toEqual([])
  })
  it('取消不写入，离开页面后未确认的保存也不写入', async () => {
    const { wrapper, vm } = mount()
    await flushPromises()
    vm.rules = []
    confirm.mockRejectedValueOnce('cancel')
    await vm.save()
    expect(api.setSetting).not.toHaveBeenCalled()
    let resolve!: () => void
    confirm.mockReturnValueOnce(new Promise<void>((yes) => { resolve = yes }))
    const saving = vm.save()
    wrapper.unmount()
    resolve()
    await saving
    expect(api.setSetting).not.toHaveBeenCalled()
  })
  it('未知提交结果保留编辑，禁止直接重试，重新读取后恢复', async () => {
    const { vm } = mount()
    await flushPromises()
    vm.rules = []
    vi.mocked(api.setSetting).mockRejectedValueOnce(new Error('synthetic secret'))
    await vm.save()
    expect(vm.needsReload).toBe(true)
    expect(vm.rules).toEqual([])
    expect(vm.feedback).toContain('可能已接收')
    expect(vm.feedback).not.toContain('secret')
    await vm.save()
    expect(api.setSetting).toHaveBeenCalledTimes(1)
    await vm.load()
    expect(vm.needsReload).toBe(false)
  })
  it('读取失败不允许保存；损坏覆盖仍允许明确恢复', async () => {
    vi.mocked(api.getSettingsSchema).mockRejectedValueOnce(new Error('synthetic'))
    const { vm } = mount()
    await flushPromises()
    await vm.save()
    expect(api.setSetting).not.toHaveBeenCalled()
    vi.mocked(api.getSettingsSchema).mockResolvedValueOnce(schema('broken'))
    await vm.load()
    expect(vm.canRestore).toBe(true)
    await vm.save(true)
    expect(api.deleteSetting).toHaveBeenCalledExactlyOnceWith(DIRECTORY_ROUTING_KEY)
    expect(vm.rules).toEqual(builtIn)
    expect(vm.needsReload).toBe(false)
  })
  it('迟到加载在组件离开后不更新，运行参数排除目录规则', async () => {
    let resolve!: (value: ReturnType<typeof schema>) => void
    vi.mocked(api.getSettingsSchema).mockReturnValueOnce(new Promise((yes) => { resolve = yes }))
    const { wrapper, vm } = mount()
    wrapper.unmount()
    resolve(schema())
    await flushPromises()
    expect(vm.loaded).toBe(false)
    const runtime = shallowMount(RuntimeSettings, { global: { directives: { loading: () => {} }, stubs: { 'el-alert': true, 'el-button': true } } })
    wrappers.push(runtime)
    await flushPromises()
    const state = runtime.vm as unknown as { schema: { items: unknown[] }, isDirty: boolean }
    expect(state.schema.items).toEqual([])
    expect(state.isDirty).toBe(false)
  })
  it.each([false, true])('缓存离页使旧保存/恢复确认失效 restore=%s', async (restore) => {
    vi.mocked(api.getSettingsSchema).mockResolvedValue(schema('[]'))
    const visible = ref(true)
    const root = mountVue({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(ImageDirectoryRoutingSettings) : null }) }, { global: { directives: { auth: () => {} }, stubs: { 'el-tag': true, 'el-alert': true, 'el-input': true, 'el-select': true, 'el-option': true, 'el-button': true } } })
    await flushPromises()
    const vm = root.findComponent(ImageDirectoryRoutingSettings).vm as unknown as State
    vm.rules = builtIn
    let resolve!: () => void
    confirm.mockReturnValueOnce(new Promise<void>((yes) => { resolve = yes }))
    const saving = vm.save(restore)
    visible.value = false
    await nextTick()
    visible.value = true
    await nextTick()
    resolve()
    await saving
    expect(api.setSetting).not.toHaveBeenCalled()
    expect(api.deleteSetting).not.toHaveBeenCalled()
    expect(vm.needsReload).toBe(true)
    root.unmount()
  })
  it('缓存离页后的在途写入仍防重，迟到结果不回填', async () => {
    const visible = ref(true)
    const root = mountVue({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(ImageDirectoryRoutingSettings) : null }) }, { global: { directives: { auth: () => {} }, stubs: { 'el-tag': true, 'el-alert': true, 'el-input': true, 'el-select': true, 'el-option': true, 'el-button': true } } })
    await flushPromises()
    const vm = root.findComponent(ImageDirectoryRoutingSettings).vm as unknown as State
    vm.rules = []
    let resolve!: (value: Awaited<ReturnType<typeof api.setSetting>>) => void
    vi.mocked(api.setSetting).mockReturnValueOnce(new Promise((yes) => { resolve = yes }))
    const saving = vm.save()
    await flushPromises()
    visible.value = false
    await nextTick()
    visible.value = true
    await nextTick()
    await vm.save()
    expect(vm.busy).toBe(true)
    expect(api.setSetting).toHaveBeenCalledTimes(1)
    resolve({ data: undefined } as Awaited<ReturnType<typeof api.setSetting>>)
    await saving
    expect(vm.feedback).not.toContain('已保存')
    expect(vm.needsReload).toBe(true)
    root.unmount()
  })
  it('重新加载从确认开始防重，不覆盖确认期间后的编辑', async () => {
    const { vm } = mount()
    await flushPromises()
    vm.rules = []
    let resolve!: () => void
    confirm.mockReturnValueOnce(new Promise<void>((yes) => { resolve = yes }))
    const loading = vm.load()
    await vm.load()
    expect(confirm).toHaveBeenCalledTimes(1)
    resolve()
    await loading
    expect(api.getSettingsSchema).toHaveBeenCalledTimes(2)
    vm.rules = []
    await flushPromises()
    expect(vm.isDirty).toBe(true)
  })
  it('后端判定重复JSON字段无效时不信任前端JSON.parse结果', async () => {
    const payload = schema('[{"type":"exact","value":"2026.06.05","node":"ba01","node":"ba02"}]')
    payload.data!.items[0].valid = false
    vi.mocked(api.getSettingsSchema).mockResolvedValueOnce(payload)
    const { vm } = mount()
    await flushPromises()
    expect(vm.canRestore).toBe(true)
    expect(vm.needsReload).toBe(true)
    await vm.save()
    expect(api.setSetting).not.toHaveBeenCalled()
  })
  it('超长纯空白覆盖仍可恢复，不伪装为有效内置状态', async () => {
    const payload = schema(' '.repeat(32769))
    payload.data!.items[0].valid = false
    payload.data!.items[0].effectiveValue = JSON.stringify(builtIn)
    vi.mocked(api.getSettingsSchema).mockResolvedValueOnce(payload)
    const { vm } = mount()
    await flushPromises()
    expect(vm.needsReload).toBe(true)
    expect(vm.canRestore).toBe(true)
    expect(() => parseDirectoryRules(`${' '.repeat(32769)}[]`)).toThrow()
  })
})

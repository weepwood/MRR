import type { EffectiveSystemSettings } from '@/utils/system-settings'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive } from 'vue'
import * as api from '@/api/modules/settings'
import { createDefaultSystemSettings, loadEffectiveSystemSettings } from '@/utils/system-settings'
import { useUnifiedSettings } from '../useUnifiedSettings'

const { success, error, warning, info, confirm } = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
  confirm: vi.fn(),
}))
vi.mock('element-plus', () => ({ ElMessage: { success, error, warning, info } }))
vi.mock('@/ui/components/ActionDialog', () => ({ actionDialog: { confirm } }))
vi.mock('vue-router', () => ({
  useRoute: () => reactive({ query: {} }),
  useRouter: () => ({ replace: vi.fn().mockResolvedValue(undefined) }),
}))
vi.mock('@/api/modules/settings', () => ({ saveSystemSettings: vi.fn() }))
vi.mock('@/utils/system-settings', async importOriginal => ({
  ...await importOriginal<typeof import('@/utils/system-settings')>(),
  loadEffectiveSystemSettings: vi.fn(),
}))

type State = ReturnType<typeof useUnifiedSettings>
type LoadResult = Awaited<ReturnType<typeof loadEffectiveSystemSettings>>

const serverSettings: EffectiveSystemSettings = { ...createDefaultSystemSettings(), systemName: '服务器名称', imageSource: 'oss' }
/** 本地缓存：开发者模式开着——正是读取失败后最不能被静默写回去的那类值。 */
const cachedSettings: EffectiveSystemSettings = { ...createDefaultSystemSettings(), systemName: '缓存名称', developerModeEnabled: true }

const fromServer = (): LoadResult => ({ settings: { ...serverSettings }, source: 'server', serverReachable: true })
const serverDown = (): LoadResult => ({ settings: { ...cachedSettings }, source: 'local', serverReachable: false })
const serverEmpty = (): LoadResult => ({ settings: { ...cachedSettings }, source: 'local', serverReachable: true })

const wrappers: { unmount: () => void }[] = []
async function setup(first: LoadResult) {
  vi.mocked(loadEffectiveSystemSettings).mockResolvedValueOnce(first)
  let state!: State
  const wrapper = mount(defineComponent({
    setup() {
      state = useUnifiedSettings()
      return () => null
    },
  }))
  wrappers.push(wrapper)
  await flushPromises()
  return state
}

beforeEach(() => {
  vi.resetAllMocks()
  confirm.mockResolvedValue(undefined)
  vi.mocked(api.saveSystemSettings).mockResolvedValue({ data: undefined } as Awaited<ReturnType<typeof api.saveSystemSettings>>)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('系统设置的可保存基线只来自服务器', () => {
  it('首次读取失败：不显示缓存值、不算修改、任何保存都不发请求', async () => {
    const state = await setup(serverDown())

    expect(state.loaded.value).toBe(false)
    expect(state.loadError.value).not.toBe('')
    expect(state.settings.value.developerModeEnabled).toBe(false)
    expect(state.settings.value.systemName).not.toBe('缓存名称')

    state.settings.value.systemShortName = '改动'
    expect(state.isDirty.value).toBe(false)
    expect(await state.handleSave()).toBe(false)
    expect(await state.handleSave({ confirmChanges: false })).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
  })

  it('重新加载成功后恢复：变更清单与实际提交一致', async () => {
    const state = await setup(serverDown())
    vi.mocked(loadEffectiveSystemSettings).mockResolvedValueOnce(fromServer())
    await state.handleReload()

    expect(state.loaded.value).toBe(true)
    expect(state.loadError.value).toBe('')
    expect(state.settings.value.systemName).toBe('服务器名称')
    expect(success).toHaveBeenCalledWith('设置已重新加载')

    state.settings.value.systemShortName = '改动'
    expect(state.changedKeys.value).toEqual(['systemShortName'])
    expect(await state.handleSave()).toBe(true)
    expect(api.saveSystemSettings).toHaveBeenCalledWith(expect.objectContaining({ systemShortName: '改动', imageSource: 'oss' }))
  })

  it('已读取成功后再次重新加载失败：保留当前表单与基线，不报成功', async () => {
    const state = await setup(fromServer())
    vi.mocked(loadEffectiveSystemSettings).mockResolvedValueOnce(serverDown())
    await state.handleReload()

    expect(success).not.toHaveBeenCalled()
    expect(error).toHaveBeenCalled()
    expect(state.loaded.value).toBe(true)
    expect(state.settings.value.systemName).toBe('服务器名称')
    expect(state.settings.value.developerModeEnabled).toBe(false)
    expect(state.isDirty.value).toBe(false)
  })

  it('服务器可达但为空（全新部署）：以默认值为基线，不采用本地缓存，可以保存', async () => {
    const state = await setup(serverEmpty())

    expect(state.loaded.value).toBe(true)
    expect(state.settings.value.systemName).toBe(createDefaultSystemSettings().systemName)
    expect(state.settings.value.developerModeEnabled).toBe(false)

    state.settings.value.systemShortName = '改动'
    expect(await state.handleSave()).toBe(true)
    expect(api.saveSystemSettings).toHaveBeenCalledWith(expect.objectContaining({ developerModeEnabled: 'false' }))
  })

  it('读取完成前不把默认值当作修改，离开页面不会触发「保存并离开」', async () => {
    let resolve!: (value: LoadResult) => void
    vi.mocked(loadEffectiveSystemSettings).mockReturnValueOnce(new Promise((yes) => { resolve = yes }))
    let state!: State
    wrappers.push(mount(defineComponent({ setup() { state = useUnifiedSettings(); return () => null } })))

    expect(state.isDirty.value).toBe(false)
    resolve(fromServer())
    await flushPromises()
    expect(state.loaded.value).toBe(true)
  })
  it('保存确认期间重复调用仅提交一次', async () => {
    const state = await setup(fromServer())
    state.settings.value.systemShortName = '合成改动'
    let resolve!: () => void
    const decision = new Promise<void>((yes) => { resolve = yes })
    confirm.mockReturnValue(decision)
    const first = state.handleSave()
    const second = state.handleSave()
    resolve()
    await Promise.all([first, second])
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(api.saveSystemSettings).toHaveBeenCalledTimes(1)
  })

  it('确认期间新增编辑不进入已经确认的提交', async () => {
    const state = await setup(fromServer())
    state.settings.value.systemShortName = '合成已确认'
    let resolve!: () => void
    confirm.mockReturnValueOnce(new Promise<void>((yes) => { resolve = yes }))
    const task = state.handleSave()
    state.settings.value.systemShortName = '合成未确认'
    resolve()
    expect(await task).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
    expect(state.isDirty.value).toBe(true)
    expect(state.settings.value.systemShortName).toBe('合成未确认')
  })

  it('确认保存期间不允许重载、恢复或丢弃草稿', async () => {
    const state = await setup(fromServer())
    state.settings.value.systemShortName = '合成草稿'
    let resolve!: () => void
    const decision = new Promise<void>((yes) => { resolve = yes })
    confirm.mockReturnValue(decision)
    vi.mocked(loadEffectiveSystemSettings).mockResolvedValue(fromServer())
    const save = state.handleSave()
    const reload = state.handleReload()
    const reset = state.handleReset()
    state.discardUnsavedChanges()
    resolve()
    await Promise.all([save, reload, reset])
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(loadEffectiveSystemSettings).toHaveBeenCalledTimes(1)
    expect(api.saveSystemSettings).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ systemShortName: '合成草稿' }))
  })

  it('已确认开发者模式配置在第二次确认期间变化时拒绝提交', async () => {
    const state = await setup(fromServer())
    state.settings.value.developerModeEnabled = true
    state.settings.value.developerModeAllowedSources = '127.0.0.1'
    let resolve!: () => void
    confirm.mockResolvedValueOnce(undefined).mockReturnValueOnce(new Promise<void>((yes) => { resolve = yes }))
    const task = state.handleSave()
    await flushPromises()
    expect(confirm).toHaveBeenCalledTimes(2)
    state.settings.value.systemShortName = '合成未确认'
    resolve()
    expect(await task).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
  })

  it('组件销毁后迟到确认不会发起保存', async () => {
    const state = await setup(fromServer())
    state.settings.value.systemShortName = '合成草稿'
    let resolve!: () => void
    confirm.mockReturnValueOnce(new Promise<void>((yes) => { resolve = yes }))
    const task = state.handleSave()
    wrappers.pop()!.unmount()
    resolve()
    expect(await task).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
    expect(success).not.toHaveBeenCalled()
  })

  it('请求在途的新草稿不会被成功响应标为已保存', async () => {
    const state = await setup(fromServer())
    state.settings.value.systemShortName = '合成已提交'
    let resolve!: (value: Awaited<ReturnType<typeof api.saveSystemSettings>>) => void
    vi.mocked(api.saveSystemSettings).mockReturnValueOnce(new Promise((yes) => { resolve = yes }))
    const task = state.handleSave()
    await flushPromises()
    expect(api.saveSystemSettings).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ systemShortName: '合成已提交' }))
    state.settings.value.systemShortName = '合成后续草稿'
    resolve({ data: undefined } as Awaited<ReturnType<typeof api.saveSystemSettings>>)
    expect(await task).toBe(true)
    expect(state.settings.value.systemShortName).toBe('合成后续草稿')
    expect(state.changedKeys.value).toEqual(['systemShortName'])
  })
})

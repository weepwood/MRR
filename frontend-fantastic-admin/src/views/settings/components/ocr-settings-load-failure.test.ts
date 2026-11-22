import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '@/api/modules/settings'
import AppError from '@/components/AppError/index.vue'
import OcrClassificationSettings from './OcrClassificationSettings.vue'

const { success, error, warning } = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { success, error, warning } }))
vi.mock('@/api/modules/settings', () => ({ getSystemSettings: vi.fn(), saveSystemSettings: vi.fn() }))
/*
 * 分类规则子组件与本用例无关（shallowMount 本来也会把它替换成桩），但模块本身仍会被加载：
 * ClassificationRules → useAuth / api/modules/classification → api/index → user store → @/router
 * → routes.ts → virtual:generated-pages。该虚拟模块只由完整 Vite 配置里的页面插件提供，
 * vitest.config.ts 没有注册，导入即失败。这里直接替换整个子组件模块，切断这条导入链。
 */
vi.mock('./ClassificationRules.vue', () => ({ default: { name: 'ClassificationRules', render: () => null } }))

interface State {
  isDirty: boolean
  saveSettings: () => Promise<boolean>
  loadSettings: () => Promise<void>
}

const serverValues = { ocrEnabled: 'true', ocrProfile: 'tesseract-local', ocrLanguages: 'chi_sim+eng' }
const global = { directives: { loading: () => {} } }
const wrappers: { unmount: () => void }[] = []

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((yes) => { resolve = yes })
  return { promise, resolve }
}

async function setup() {
  const wrapper = shallowMount(OcrClassificationSettings, { global })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, vm: wrapper.vm as unknown as State }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.saveSystemSettings).mockResolvedValue({ data: undefined } as Awaited<ReturnType<typeof api.saveSystemSettings>>)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('oCR 设置读取失败', () => {
  it('首次读取未完成时不算有未保存修改', async () => {
    const pending = deferred<Awaited<ReturnType<typeof api.getSystemSettings>>>()
    vi.mocked(api.getSystemSettings).mockReturnValueOnce(pending.promise)
    const wrapper = shallowMount(OcrClassificationSettings, { global })
    wrappers.push(wrapper)
    expect((wrapper.vm as unknown as State).isDirty).toBe(false)
    pending.resolve({ data: serverValues } as Awaited<ReturnType<typeof api.getSystemSettings>>)
    await flushPromises()
  })

  it('失败时显示错误与重试，不把默认值当作待保存，也不能写入服务器', async () => {
    vi.mocked(api.getSystemSettings).mockRejectedValueOnce(new Error('network'))
    const { wrapper, vm } = await setup()

    expect(wrapper.findComponent(AppError).exists()).toBe(true)
    expect(vm.isDirty).toBe(false)
    expect(await vm.saveSettings()).toBe(false)
    expect(api.saveSystemSettings).not.toHaveBeenCalled()
  })

  it('重试成功后恢复表单，修改后可以正常保存', async () => {
    vi.mocked(api.getSystemSettings).mockRejectedValueOnce(new Error('network'))
    const { wrapper, vm } = await setup()

    vi.mocked(api.getSystemSettings).mockResolvedValueOnce({ data: serverValues } as Awaited<ReturnType<typeof api.getSystemSettings>>)
    wrapper.findComponent(AppError).vm.$emit('retry')
    await flushPromises()

    expect(wrapper.findComponent(AppError).exists()).toBe(false)
    expect(vm.isDirty).toBe(false)
    expect(await vm.saveSettings()).toBe(true)
    expect(api.saveSystemSettings).toHaveBeenCalledWith(expect.objectContaining({ ocrEnabled: 'true', ocrProfile: 'tesseract-local' }))
  })
})

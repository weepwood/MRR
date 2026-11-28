import type { PatientRecord } from '@/api/modules/patients'
import { shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive } from 'vue'
import { updatePatient } from '@/api/modules/patients'
import Dialog from './PatientEditDialog.vue'

const { store, validate, success } = vi.hoisted(() => ({
  store: { current: undefined as unknown as { sessionEpoch: number, isSessionVerified: boolean } },
  validate: vi.fn(),
  success: vi.fn(),
}))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => store.current }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => store.current.isSessionVerified }) }))
vi.mock('@/api/modules/patients', () => ({ updatePatient: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { success, error: vi.fn() } }))

function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const patient = (id: number): PatientRecord => ({ id, bah: `synthetic-${id}` })
const Form = defineComponent({
  setup(_, { expose }) {
    expose({ validate, clearValidate: vi.fn() })
    return () => null
  },
})
interface State {
  submit: () => Promise<void>
  saving: boolean
}
const wrappers: ReturnType<typeof shallowMount>[] = []
function setup() {
  const wrapper = shallowMount(Dialog, {
    props: { modelValue: true, patient: patient(1) },
    global: { stubs: { 'el-form': Form, 'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' }, 'el-alert': true, 'el-form-item': true, 'el-button': true } },
  })
  wrappers.push(wrapper)
  return { wrapper, vm: wrapper.vm as unknown as State }
}
beforeEach(() => {
  store.current = reactive({ sessionEpoch: 1, isSessionVerified: true })
  validate.mockReset().mockResolvedValue(true)
  success.mockReset()
  vi.mocked(updatePatient).mockReset().mockResolvedValue({ data: patient(1) })
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()) })

describe('患者编辑提交归属', () => {
  it('异步校验期间重复提交只执行一次写入', async () => {
    const { vm } = setup()
    const validation = deferred<boolean>()
    validate.mockReturnValue(validation.promise)
    const a = vm.submit()
    const b = vm.submit()
    validation.resolve(true)
    await Promise.all([a, b])
    expect(updatePatient).toHaveBeenCalledTimes(1)
  })
  it('校验期间切换患者不向新患者发出旧保存动作', async () => {
    const { vm, wrapper } = setup()
    const validation = deferred<boolean>()
    validate.mockReturnValue(validation.promise)
    const task = vm.submit()
    await wrapper.setProps({ patient: patient(2) })
    validation.resolve(true)
    await task
    expect(updatePatient).not.toHaveBeenCalled()
  })
  it('旧写入结果不关闭新编辑窗口或触发成功提示', async () => {
    const { vm, wrapper } = setup()
    const pending = deferred<Awaited<ReturnType<typeof updatePatient>>>()
    vi.mocked(updatePatient).mockReturnValueOnce(pending.promise)
    const task = vm.submit()
    await Promise.resolve()
    await Promise.resolve()
    expect(updatePatient).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ patient: patient(2) })
    pending.resolve({ data: patient(1) })
    await task
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(success).not.toHaveBeenCalled()
  })
  it('会话变化期间的校验结果不能发起写入', async () => {
    const { vm } = setup()
    const validation = deferred<boolean>()
    validate.mockReturnValue(validation.promise)
    const task = vm.submit()
    store.current.sessionEpoch++
    validation.resolve(true)
    await task
    expect(updatePatient).not.toHaveBeenCalled()
  })
  it('校验失败及当前写入失败均可重试，正常成功保留对象和关闭行为', async () => {
    const { vm, wrapper } = setup()
    validate.mockResolvedValueOnce(false)
    await vm.submit()
    expect(vm.saving).toBe(false)
    expect(updatePatient).not.toHaveBeenCalled()
    vi.mocked(updatePatient).mockRejectedValueOnce(new Error('synthetic failure'))
    await vm.submit()
    expect(vm.saving).toBe(false)
    expect(wrapper.emitted('saved')).toBeUndefined()
    await vm.submit()
    expect(updatePatient).toHaveBeenLastCalledWith(1, expect.objectContaining({ bah: 'synthetic-1' }))
    expect(wrapper.emitted('saved')).toEqual([[patient(1)]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(success).toHaveBeenCalledTimes(1)
  })
  it('关闭再打开相同患者不接受旧请求结果', async () => {
    const { vm, wrapper } = setup()
    const pending = deferred<Awaited<ReturnType<typeof updatePatient>>>()
    vi.mocked(updatePatient).mockReturnValueOnce(pending.promise)
    const task = vm.submit()
    await Promise.resolve()
    await Promise.resolve()
    expect(updatePatient).toHaveBeenCalledTimes(1)
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    pending.resolve({ data: patient(1) })
    await task
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(vm.saving).toBe(false)
  })
})

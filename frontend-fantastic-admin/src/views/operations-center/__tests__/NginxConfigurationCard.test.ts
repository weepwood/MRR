import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import NginxConfigurationCard from '../components/NginxConfigurationCard.vue'

const api = vi.hoisted(() => ({ getNginxConfigStatus: vi.fn(), checkNginxConfig: vi.fn() }))
vi.mock('@/api/modules/operations', () => api)
const session = reactive({ token: 'synthetic', read: true, manage: true })
vi.mock('@/utils/session', () => ({ hasPermission: (p: string) => p === 'system:read' ? session.read : session.manage }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => session }))
const checkedAt = '2026-11-01T00:00:00Z'
const available = { enabled: true, state: 'AVAILABLE', configBytes: 1024, configModifiedAt: checkedAt }
function card() {
  return mount(NginxConfigurationCard, { global: { stubs: {
    'MrrSectionCard': { template: '<section><slot /></section>' },
    'AppError': { props: ['message'], template: '<p>{{ message }}</p>' },
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    'el-tag': { template: '<span><slot /></span>' },
    'el-alert': { props: ['title'], template: '<div>{{ title }}<slot /></div>' },
  } } })
}
const findButton = (wrapper: ReturnType<typeof card>, label: string) => wrapper.findAll('button').find(b => b.text() === label)!
describe('nginx 受控配置检查', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    session.token = 'synthetic'
    session.read = true
    session.manage = true
  })
  it('默认关闭、只读角色不能执行；权限改变后仅明确点击触发', async () => {
    api.getNginxConfigStatus.mockResolvedValue({ enabled: false, state: 'DISABLED', configBytes: null, configModifiedAt: null })
    const wrapper = card()
    await flushPromises()
    expect(findButton(wrapper, '校验配置语法').attributes('disabled')).toBeDefined()
    expect(api.checkNginxConfig).not.toHaveBeenCalled()
    session.manage = false
    api.getNginxConfigStatus.mockResolvedValue(available)
    await flushPromises()
    expect(findButton(wrapper, '校验配置语法')).toBeUndefined()
    session.manage = true
    await flushPromises()
    api.checkNginxConfig.mockResolvedValue({ status: 'PASSED', checkedAt, exitCode: 0 })
    expect(api.checkNginxConfig).not.toHaveBeenCalled()
    await findButton(wrapper, '校验配置语法').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('配置语法校验通过')
    expect(wrapper.text()).toContain('语法通过不代表服务在线')
    expect(api.checkNginxConfig).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('请求失败显示错误；权限变化取消在途校验并丢弃迟到响应', async () => {
    api.getNginxConfigStatus.mockResolvedValue(available)
    api.checkNginxConfig.mockRejectedValueOnce(new Error('synthetic'))
    const wrapper = card()
    await flushPromises()
    await findButton(wrapper, '校验配置语法').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('无法取得配置检查结果')
    let resolve!: (value: unknown) => void
    api.checkNginxConfig.mockImplementationOnce(() => new Promise(r => resolve = r))
    await findButton(wrapper, '校验配置语法').trigger('click')
    const signal = api.checkNginxConfig.mock.calls.at(-1)![0] as AbortSignal
    session.manage = false
    await flushPromises()
    expect(signal.aborted).toBe(true)
    resolve({ status: 'FAILED', checkedAt, exitCode: 1 })
    await flushPromises()
    expect(wrapper.text()).not.toContain('配置语法校验失败')
    session.read = false
    await flushPromises()
    expect(wrapper.text()).toBe('')
    wrapper.unmount()
  })
  it('状态请求失败可刷新，退出会话丢弃旧状态', async () => {
    api.getNginxConfigStatus.mockRejectedValueOnce(new Error('synthetic'))
    const wrapper = card()
    await flushPromises()
    expect(wrapper.text()).toContain('无法取得配置检查结果')
    let resolve!: (value: unknown) => void
    api.getNginxConfigStatus.mockImplementationOnce(() => new Promise(r => resolve = r))
    await findButton(wrapper, '刷新配置状态').trigger('click')
    session.token = 'other'
    resolve(available)
    await flushPromises()
    expect(wrapper.text()).not.toContain('安装文件可读')
    wrapper.unmount()
  })
})

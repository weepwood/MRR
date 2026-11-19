import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MrrUserSearchSelect from '../MrrUserSearchSelect/index.vue'

const api = vi.hoisted(() => ({ getUsers: vi.fn() }))
vi.mock('@/api/modules/user', () => ({ default: api }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))

const userFixture = { username: 'op_li', displayName: '李医生', roleCode: 'OPERATOR' }
const adminFixture = { username: 'br_admin', displayName: '', roleCode: 'ADMIN' }

const stubs = {
  'el-select': {
    name: 'ElSelectStub',
    props: ['modelValue', 'loading', 'remoteMethod', 'id', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<div class="select-stub"><slot /></div>',
  },
  'el-option': { props: ['label', 'value'], template: '<span class="option">{{ label }}</span>' },
}

function setupState(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
  return (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
}

beforeEach(() => {
  vi.resetAllMocks()
  api.getUsers.mockResolvedValue({ data: { list: [userFixture, adminFixture], total: 2 } })
})

describe('用户搜索选择框', () => {
  it('挂载时拉取一次默认用户列表并渲染选项', async () => {
    const wrapper = mount(MrrUserSearchSelect, { props: { modelValue: '' }, global: { stubs } })
    await flushPromises()
    expect(api.getUsers).toHaveBeenCalledWith({ keyword: undefined, size: 50 })
    const text = wrapper.text()
    expect(text).toContain('op_li（李医生）')
    expect(text).toContain('br_admin')
    wrapper.unmount()
  })

  it('远端检索携带关键字，失败静默清空选项', async () => {
    const wrapper = mount(MrrUserSearchSelect, { props: { modelValue: '' }, global: { stubs } })
    await flushPromises()

    await (setupState(wrapper).searchUsers as (keyword: string) => Promise<void>)('li')
    expect(api.getUsers).toHaveBeenLastCalledWith({ keyword: 'li', size: 50 })

    api.getUsers.mockRejectedValueOnce(new Error('network down'))
    await (setupState(wrapper).searchUsers as (keyword: string) => Promise<void>)('li')
    expect(wrapper.findAll('.option')).toHaveLength(0)
    wrapper.unmount()
  })

  it('选择选项把用户名写回 v-model，空值归一为空串', async () => {
    const wrapper = mount(MrrUserSearchSelect, { props: { modelValue: '' }, global: { stubs } })
    await flushPromises()

    await wrapper.findComponent({ name: 'ElSelectStub' }).vm.$emit('update:modelValue', 'op_li')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['op_li'])

    await wrapper.findComponent({ name: 'ElSelectStub' }).vm.$emit('update:modelValue', null)
    expect(wrapper.emitted('update:modelValue')?.[1]).toEqual([''])
    wrapper.unmount()
  })
})

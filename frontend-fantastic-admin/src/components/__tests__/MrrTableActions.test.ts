import type { MrrTableAction } from '../MrrTableActions/types'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MrrTableActions from '../MrrTableActions/index.vue'

const { mockAuth } = vi.hoisted(() => ({
  mockAuth: vi.fn((permission: string | string[]) => permission !== 'denied'),
}))

vi.mock('@/utils/composables/useAuth', () => ({
  default: () => ({ auth: mockAuth }),
}))

const stubs = {
  'FaIcon': {
    props: ['name'],
    template: '<i :data-icon="name" />',
  },
  'el-tooltip': {
    template: '<span class="tooltip-stub"><slot /></span>',
  },
  'el-button': {
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button class="button-stub" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
  'el-dropdown': {
    emits: ['command'],
    template: '<div class="dropdown-stub"><slot /><slot name="dropdown" /></div>',
  },
  'el-dropdown-menu': {
    template: '<div class="dropdown-menu-stub"><slot /></div>',
  },
  'el-dropdown-item': {
    props: ['command', 'disabled'],
    template: '<button class="dropdown-item-stub" :data-command="command" :disabled="disabled"><slot /></button>',
  },
}

const actions: MrrTableAction[] = [
  { key: 'edit', label: '编辑', icon: 'i-ri:edit-line', tone: 'primary', placement: 'inline' },
  { key: 'reset', label: '重置密码', icon: 'i-ri:key-2-line', placement: 'overflow' },
  { key: 'disable', label: '禁用', icon: 'i-ri:forbid-line', tone: 'danger' },
]

function mountActions(overrides: Partial<InstanceType<typeof MrrTableActions>['$props']> = {}) {
  return mount(MrrTableActions, {
    props: {
      actions,
      maxInline: 1,
      ...overrides,
    },
    global: { stubs },
  })
}

describe('MrrTableActions', () => {
  beforeEach(() => {
    mockAuth.mockClear()
    mockAuth.mockImplementation((permission: string | string[]) => permission !== 'denied')
  })

  it('按照最大直显数量拆分图标与更多操作', () => {
    const wrapper = mountActions()
    const exposed = wrapper.vm as unknown as {
      inlineActions: MrrTableAction[]
      overflowActions: MrrTableAction[]
    }

    expect(exposed.inlineActions.map(item => item.key)).toEqual(['edit'])
    expect(exposed.overflowActions.map(item => item.key)).toEqual(['disable', 'reset'])
    expect(wrapper.find('[aria-label="更多操作"]').exists()).toBe(true)
  })

  it('点击直显操作时发出 select 事件', async () => {
    const wrapper = mountActions()
    await wrapper.find('[aria-label="编辑"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['edit']])
  })

  it('过滤不可见和无权限操作', () => {
    const wrapper = mountActions({
      actions: [
        { key: 'hidden', label: '隐藏', icon: 'i-ri:eye-off-line', visible: false },
        { key: 'denied', label: '拒绝', icon: 'i-ri:close-line', permission: 'denied' },
        { key: 'view', label: '查看', icon: 'i-ri:eye-line', permission: 'record:read' },
      ],
    })
    const exposed = wrapper.vm as unknown as { availableActions: MrrTableAction[] }

    expect(exposed.availableActions.map(item => item.key)).toEqual(['view'])
    expect(mockAuth).toHaveBeenCalledWith('denied')
    expect(mockAuth).toHaveBeenCalledWith('record:read')
  })

  it('禁用操作不会触发事件并保留可访问名称', async () => {
    const wrapper = mountActions({
      actions: [{
        key: 'delete',
        label: '删除',
        icon: 'i-ri:delete-bin-line',
        tone: 'danger',
        disabled: true,
        disabledReason: '当前记录不能删除',
      }],
    })

    const button = wrapper.find('[aria-label="删除"]')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('没有可用操作时不渲染空操作组', () => {
    const wrapper = mountActions({
      actions: [{ key: 'hidden', label: '隐藏', icon: 'i-ri:eye-off-line', visible: false }],
    })
    expect(wrapper.find('.mrr-table-actions').exists()).toBe(false)
  })
})

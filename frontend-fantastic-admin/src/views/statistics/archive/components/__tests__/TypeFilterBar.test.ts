import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TypeFilterBar from '../TypeFilterBar.vue'

const typeStats = [
  { value: 1, label: '01-病案首页', count: 3 },
  { value: 3, label: '03-手术记录', count: 1 },
  { value: 99, label: '99-罕见类型', count: 0 },
  { value: 100, label: '100-测试类型', count: 0 },
]

function mountBar(props: Record<string, unknown> = {}) {
  return mount(TypeFilterBar, {
    props: {
      typeStats,
      totalCount: 4,
      allSelected: false,
      allIndeterminate: false,
      isTypeSelected: () => false,
      isTypeIndeterminate: () => false,
      ...props,
    },
    global: {
      stubs: {
        'el-checkbox': {
          props: ['modelValue', 'indeterminate', 'disabled', 'ariaLabel'],
          template: '<input type="checkbox" :aria-label="ariaLabel" :checked="modelValue" :disabled="disabled">',
        },
      },
    },
  })
}

describe('type filter bar', () => {
  it('默认只渲染有影像的类型，避免 0 计数灰项砌成视觉墙', () => {
    const wrapper = mountBar()

    const labels = wrapper.findAll('.type-tab-label').map(label => label.text())
    expect(labels).toContain('01-病案首页')
    expect(labels).toContain('03-手术记录')
    expect(labels).not.toContain('99-罕见类型')
    expect(labels).not.toContain('100-测试类型')
  })

  it('摘要行说明有影像类型占比，展开后可见全部类型且 0 计数项仍禁用', async () => {
    const wrapper = mountBar()

    expect(wrapper.get('.type-bar-summary').text()).toBe('2/4 类有影像')

    const toggle = wrapper.get('.type-bar-toggle')
    await toggle.trigger('click')
    // toggle 是唯一开关：aria-pressed 与文案应同步翻转
    expect(toggle.attributes('aria-pressed')).toBe('true')
    expect(toggle.text()).toBe('收起无影像类型')

    const labels = wrapper.findAll('.type-tab-label').map(label => label.text())
    expect(labels).toHaveLength(5) // 全部 + 4 个类型
    expect(labels).toContain('99-罕见类型')

    const disabledButtons = wrapper.findAll('button.type-tab').filter(button => button.attributes('disabled') !== undefined)
    expect(disabledButtons).toHaveLength(2)
  })

  it('当前选中的类型即使 0 计数也保持可见，不丢操作目标', () => {
    const wrapper = mountBar({ selectedType: 99 })

    const labels = wrapper.findAll('.type-tab-label').map(label => label.text())
    expect(labels).toContain('99-罕见类型')
    expect(labels).not.toContain('100-测试类型')
  })

  it('没有 0 计数类型时不渲染摘要行与展开开关', () => {
    const wrapper = mountBar({
      typeStats: [{ value: 1, label: '01-病案首页', count: 3 }],
      totalCount: 3,
    })

    expect(wrapper.find('.type-bar-summary').exists()).toBe(false)
    expect(wrapper.find('.type-bar-toggle').exists()).toBe(false)
  })

  it('切换病案（类型集重建）后回到收敛默认，不保留展开状态', async () => {
    const wrapper = mountBar()

    await wrapper.get('.type-bar-toggle').trigger('click')
    expect(wrapper.findAll('.type-tab-label').map(label => label.text())).toContain('99-罕见类型')

    // 新病案：typeStats 由 buildTypeStats 每次查询整体重建，引用必然变化
    const nextPatientStats = [
      { value: 1, label: '01-病案首页', count: 2 },
      { value: 99, label: '99-罕见类型', count: 0 },
    ]
    await wrapper.setProps({ typeStats: nextPatientStats, totalCount: 2 })

    // 组件不卸载的前提下，展开状态应复位为新病案的收敛默认
    const labels = wrapper.findAll('.type-tab-label').map(label => label.text())
    expect(labels).not.toContain('99-罕见类型')
    expect(labels).toContain('01-病案首页')
    expect(wrapper.get('.type-bar-toggle').attributes('aria-pressed')).toBe('false')
  })

  it('点击类型仍发出 selectType，勾选仍发出类型批量选择', async () => {
    const wrapper = mountBar()

    await wrapper.findAll('.type-tab').at(1).trigger('click') // 01-病案首页
    expect(wrapper.emitted('selectType')?.[0]).toEqual([1])

    await wrapper.findAll('.type-tab input[type="checkbox"]').at(1).setValue(true)
    expect(wrapper.emitted('toggleTypeSelection')?.[0]).toEqual([1])
  })
})

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MrrInfoTip from '../MrrInfoTip/index.vue'

const stubs = {
  'el-tooltip': {
    props: ['content', 'placement', 'showAfter'],
    template: '<div class="tooltip-stub" :data-content="content" :data-placement="placement"><slot name="content" /><slot /></div>',
  },
  'el-icon': { template: '<i><slot /></i>' },
}

describe('mrrInfoTip', () => {
  it('渲染为带可访问名称的原生按钮，图标不参与读屏', () => {
    const wrapper = mount(MrrInfoTip, { props: { label: '权限继承说明', content: '上级权限继承下级权限' }, global: { stubs } })
    const button = wrapper.get('button.mrr-info-tip')
    expect(button.attributes('type')).toBe('button')
    expect(button.attributes('aria-label')).toBe('权限继承说明')
    expect(wrapper.get('i').attributes('aria-hidden')).toBe('true')
  })

  it('默认向右展开，并把 content 交给 tooltip', () => {
    const wrapper = mount(MrrInfoTip, { props: { label: '说明', content: '文字' }, global: { stubs } })
    const tooltip = wrapper.get('.tooltip-stub')
    expect(tooltip.attributes('data-placement')).toBe('right')
    expect(tooltip.attributes('data-content')).toBe('文字')
  })

  it('长说明可以用默认插槽传入', () => {
    const wrapper = mount(MrrInfoTip, {
      props: { label: '导出说明', placement: 'top' },
      slots: { default: '<span class="rich">第一行<br>第二行</span>' },
      global: { stubs },
    })
    expect(wrapper.find('.rich').exists()).toBe(true)
    expect(wrapper.get('.tooltip-stub').attributes('data-placement')).toBe('top')
  })
})

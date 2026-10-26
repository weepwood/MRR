import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MrrMetricCard from '../MrrMetricCard/index.vue'

const stubs = {
  'FaIcon': {
    props: ['name'],
    template: '<i class="icon-stub" :data-icon="name" />',
  },
  'el-card': {
    template: '<div class="mrr-metric-card-stub"><slot /></div>',
  },
}

function mountCard(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(MrrMetricCard, {
    props: { label: '指标名称', value: 42, ...props },
    slots,
    global: { stubs },
  })
}

describe('mrrMetricCard', () => {
  it('渲染标签、数值与说明', () => {
    const wrapper = mountCard({ note: '口径说明' })
    expect(wrapper.find('.mrr-metric-card__label').text()).toBe('指标名称')
    expect(wrapper.find('.mrr-metric-card__value').text()).toBe('42')
    expect(wrapper.find('.mrr-metric-card__note').text()).toBe('口径说明')
  })

  it('无图标时不渲染图标区并占用整行', () => {
    const wrapper = mountCard()
    expect(wrapper.find('.mrr-metric-card__icon').exists()).toBe(false)
    expect(wrapper.classes()).toContain('mrr-metric-card--no-icon')
  })

  it('有图标时渲染图标区且不加无图标修饰类', () => {
    const wrapper = mountCard({ icon: 'i-lucide:user-round' })
    expect(wrapper.find('.mrr-metric-card__icon').exists()).toBe(true)
    expect(wrapper.find('.icon-stub').attributes('data-icon')).toBe('i-lucide:user-round')
    expect(wrapper.classes()).not.toContain('mrr-metric-card--no-icon')
  })

  it('缺省 tone 不追加语义修饰类，非缺省 tone 追加对应类', () => {
    expect(mountCard().classes()).not.toContain('mrr-metric-card--blue')
    expect(mountCard({ tone: 'danger' }).classes()).toContain('mrr-metric-card--danger')
    expect(mountCard({ tone: 'warning' }).classes()).toContain('mrr-metric-card--warning')
  })

  it('valueTone 只影响数值颜色，不改变卡片表面语义类', () => {
    const wrapper = mountCard({ valueTone: 'danger' })
    expect(wrapper.find('.mrr-metric-card__value').classes()).toContain('mrr-metric-card__value--danger')
    expect(wrapper.classes()).not.toContain('mrr-metric-card--danger')
  })

  it('compactValue 用于文本型数值', () => {
    const wrapper = mountCard({ value: '/proxy/', compactValue: true })
    expect(wrapper.find('.mrr-metric-card__value').classes()).toContain('mrr-metric-card__value--compact')
  })

  it('suffix 与 extra 插槽可独立渲染', () => {
    const wrapper = mountCard({ suffix: 'ms' }, { extra: '<span class="extra-stub">进度</span>' })
    expect(wrapper.find('.mrr-metric-card__suffix').text()).toBe('ms')
    expect(wrapper.find('.mrr-metric-card__extra .extra-stub').exists()).toBe(true)
  })

  it('未提供 extra 插槽时不渲染附加区', () => {
    expect(mountCard().find('.mrr-metric-card__extra').exists()).toBe(false)
  })
})

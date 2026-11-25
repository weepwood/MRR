import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MrrMetricCard from '../MrrMetricCard/index.vue'

const stubs = {
  'FaIcon': {
    props: ['name'],
    template: '<i class="icon-stub" :data-icon="name" />',
  },
  'MrrInfoTip': { props: ['label', 'content'], template: '<button :aria-label="label" :aria-description="content" />' },
  'el-card': {
    template: '<div class="mrr-metric-card-stub"><slot /></div>',
  },
}

function mountCard(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(MrrMetricCard, {
    props: { label: '指标名称', value: 42, icon: 'i-lucide:chart-no-axes-combined', ...props },
    slots,
    global: { stubs },
  })
}

describe('mrrMetricCard', () => {
  it('渲染标签、数值及独立说明入口', () => {
    const wrapper = mountCard({ note: '口径说明' })
    expect(wrapper.find('.mrr-metric-card__label').text()).toBe('指标名称')
    expect(wrapper.find('.mrr-metric-card__value').text()).toBe('42')
    expect(wrapper.get('button[aria-label="查看指标名称说明"]').attributes('aria-description')).toBe('口径说明')
    expect(wrapper.find('.mrr-metric-card__note').exists()).toBe(false)
  })

  it('仅在传入锚点时提供可聚焦链接，默认卡片保持不变', () => {
    expect(mountCard().find('a').exists()).toBe(false)
    const wrapper = mountCard({ anchor: '#monitor-issues' })
    expect(wrapper.get('a').attributes('href')).toBe('#monitor-issues')
    expect(wrapper.get('a').attributes('aria-label')).toBe('查看指标名称明细')
    expect(wrapper.find('.mrr-metric-card__value').text()).toBe('42')
  })

  it('所有指标渲染语义图标，调用方可指定图标', () => {
    expect(mountCard().find('.mrr-metric-card__icon').exists()).toBe(true)
    const wrapper = mountCard({ icon: 'i-lucide:user-round' })
    expect(wrapper.find('.icon-stub').attributes('data-icon')).toBe('i-lucide:user-round')
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

  it('单位保持独立，主操作与说明按钮互不嵌套', async () => {
    const wrapper = mountCard({ suffix: 'ms', note: '口径', actionLabel: '查看详情' })
    expect(wrapper.find('.mrr-metric-card__suffix').text()).toBe('ms')
    expect(wrapper.findAll('button')).toHaveLength(2)
    expect(wrapper.find('button button').exists()).toBe(false)
    await wrapper.get('button[aria-label="查看详情"]').trigger('click')
    expect(wrapper.emitted('activate')).toHaveLength(1)
    await wrapper.get('button[aria-label="查看指标名称说明"]').trigger('click')
    expect(wrapper.emitted('activate')).toHaveLength(1)
  })

  it('首屏不提示更新，数值变化后才追加 is-updated', async () => {
    const wrapper = mountCard()
    expect(wrapper.find('.mrr-metric-card__value').classes()).not.toContain('is-updated')

    await wrapper.setProps({ value: 43 })
    const value = wrapper.find('.mrr-metric-card__value')
    expect(value.classes()).toContain('is-updated')
    expect(value.text()).toBe('43')
  })

  it('数值未变化时不重播更新提示', async () => {
    const wrapper = mountCard()
    await wrapper.setProps({ note: '只改说明' })
    expect(wrapper.find('.mrr-metric-card__value').classes()).not.toContain('is-updated')
  })

  it('普通指标仅展示名称和数值，不渲染冗余说明或口径徽标', () => {
    const wrapper = mountCard()
    expect(wrapper.find('.mrr-metric-card__note').exists()).toBe(false)
    expect(wrapper.find('.mrr-metric-card__scope').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
  })
})

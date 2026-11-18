import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MrrChartCard from '../MrrChartCard.vue'

function mountCard(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(MrrChartCard, {
    props: { title: '扫描趋势', description: '默认近 90 日，可滚动调整范围', ...props },
    attrs: { 'data-testid': 'trend-card' },
    slots: { default: '<div class="chart-state">图表内容</div>', ...slots },
    global: {
      stubs: {
        'el-card': { template: '<section><header><slot name="header" /></header><slot /></section>' },
        'FaIcon': true,
      },
      directives: { loading: () => {} },
    },
  })
}

describe('mrrChartCard', () => {
  it('用标题语义呈现完整说明并透传根属性', () => {
    const wrapper = mountCard()
    expect(wrapper.find('h2').text()).toBe('扫描趋势')
    expect(wrapper.find('header').text()).toContain('默认近 90 日，可滚动调整范围')
    expect(wrapper.attributes('data-testid')).toBe('trend-card')
  })

  it('保留操作和摘要插槽，操作仍可触发且摘要位于图表之前', async () => {
    const wrapper = mountCard({}, {
      actions: '<button @click="event => event.target.textContent = \'已刷新\'">刷新</button>',
      summary: '<p class="summary">统计日 64</p>',
    })
    await wrapper.get('button').trigger('click')
    expect(wrapper.get('button').text()).toBe('已刷新')
    expect(wrapper.get('.summary').element.compareDocumentPosition(wrapper.get('.chart-state').element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('加载优先于空态，完成后显示指定空态文案', async () => {
    const wrapper = mountCard({ loading: true, empty: true, emptyDescription: '暂无扫描趋势数据' })
    expect(wrapper.get('.mrr-chart-card__body').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.mrr-chart-card__empty').exists()).toBe(false)
    expect(wrapper.find('.chart-state').exists()).toBe(true)
    await wrapper.setProps({ loading: false })
    expect(wrapper.get('.mrr-chart-card__body').attributes('aria-busy')).toBe('false')
    expect(wrapper.get('.mrr-chart-card__empty').text()).toBe('暂无扫描趋势数据')
    expect(wrapper.find('.chart-state').exists()).toBe(false)
  })

  it('有数据时切换加载状态不重建图表插槽，不创建空操作区', async () => {
    const wrapper = mountCard()
    const chart = wrapper.get('.chart-state')
    ;(chart.element as HTMLElement).dataset.zoom = '90'
    await wrapper.setProps({ loading: true })
    await wrapper.setProps({ loading: false })
    expect(wrapper.get('.chart-state').element).toBe(chart.element)
    expect((chart.element as HTMLElement).dataset.zoom).toBe('90')
    expect(wrapper.find('.mrr-section-card__actions').exists()).toBe(false)
    expect(wrapper.find('.mrr-chart-card__summary').exists()).toBe(false)
  })
})

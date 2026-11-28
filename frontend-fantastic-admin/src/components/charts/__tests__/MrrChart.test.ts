import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { shallowRef } from 'vue'
import MrrChart from '../MrrChart.vue'

vi.mock('@/composables/useMrrChart', () => ({
  useMrrChart: () => ({ chartRef: shallowRef(null) }),
}))

describe('mrrChart', () => {
  it('加载优先于空态，保留渲染节点及状态可访问性', async () => {
    const wrapper = mount(MrrChart, { props: { option: {}, loading: true, empty: true } })
    const canvas = wrapper.get('.mrr-chart__canvas').element
    expect(wrapper.attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.mrr-chart__empty').exists()).toBe(false)
    await wrapper.setProps({ loading: false })
    expect(wrapper.get('.mrr-chart__empty').text()).toBe('暂无图表数据')
    expect(wrapper.attributes('aria-busy')).toBe('false')
    await wrapper.setProps({ empty: false })
    expect(wrapper.find('.mrr-chart__empty').exists()).toBe(false)
    expect(wrapper.get('.mrr-chart__canvas').element).toBe(canvas)
    wrapper.unmount()
  })
})

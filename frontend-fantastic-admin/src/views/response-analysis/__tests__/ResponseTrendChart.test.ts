import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ResponseTrendChart from '../components/ResponseTrendChart.vue'

describe('response trend chart', () => {
  it('omits the extra heatmap heading while keeping its accessible name', () => {
    const wrapper = mount(ResponseTrendChart, {
      props: { data: [] },
      global: {
        stubs: {
          MrrLineChart: true,
          MrrRequestHeatmap: true,
        },
      },
    })

    const heatmapSection = wrapper.find('.trend-section')

    expect(heatmapSection.attributes('aria-label')).toBe('每日请求热力图')
    expect(heatmapSection.find('.section-heading').exists()).toBe(false)
    expect(heatmapSection.text()).not.toContain('每日请求热力图')
    expect(heatmapSection.text()).not.toContain('一个方块代表一天，颜色越深表示当天请求数量越多')
    expect(heatmapSection.text()).not.toContain('最近 365 天')
  })
})

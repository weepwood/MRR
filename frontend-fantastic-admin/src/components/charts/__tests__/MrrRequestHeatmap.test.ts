import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MrrRequestHeatmap from '../MrrRequestHeatmap.vue'

describe('mrr request heatmap month labels', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps every month label on the month header row', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 25, 12))

    const wrapper = mount(MrrRequestHeatmap, { props: { data: [] } })
    const labels = wrapper.findAll('.month-labels span')

    expect(labels).toHaveLength(13)
    expect(labels.every(label => label.element.style.gridRowStart === '1')).toBe(true)

    wrapper.unmount()
  })
})

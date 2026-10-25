import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import MrrRequestHeatmap from '../MrrRequestHeatmap.vue'

describe('mrr request heatmap month labels', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('keeps every month label on the month header row', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 25, 12))

    const wrapper = mount(MrrRequestHeatmap, { props: { data: [] } })
    const labels = wrapper.findAll('.month-labels span')

    expect(labels).toHaveLength(12)
    expect(labels.every(label => label.element.style.gridRowStart === '1')).toBe(true)

    wrapper.unmount()
  })

  it('hides a partial first month when it shares a week column with the next month', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 25, 12))

    const wrapper = mount(MrrRequestHeatmap, { props: { data: [] } })
    const labels = wrapper.findAll('.month-labels span')

    expect(labels).toHaveLength(12)
    expect(labels[0].text()).toBe('11月')
    expect(labels[0].element.style.gridColumnStart).toBe('1')

    wrapper.unmount()
  })

  it('coalesces rapid hover updates to the last day in one animation frame', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 25, 12))
    const frames: FrameRequestCallback[] = []
    vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => {
      frames.push(callback)
      return frames.length
    }))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())

    const wrapper = mount(MrrRequestHeatmap, { props: { data: [] } })
    const cells = wrapper.findAll('button.heatmap-cell')
    const firstCellRect = vi.spyOn(cells[0].element, 'getBoundingClientRect')
    const lastCellRect = vi.spyOn(cells[1].element, 'getBoundingClientRect')

    await cells[0].trigger('pointerenter')
    await cells[1].trigger('pointerenter')

    expect(frames).toHaveLength(1)
    expect(firstCellRect).not.toHaveBeenCalled()
    expect(lastCellRect).not.toHaveBeenCalled()
    expect(document.body.querySelector('.heatmap-tooltip')).toBeNull()

    frames[0](0)
    await nextTick()

    expect(firstCellRect).not.toHaveBeenCalled()
    expect(lastCellRect).toHaveBeenCalledTimes(1)
    expect(document.body.querySelector('.heatmap-tooltip')?.textContent).toContain('2025年10月27日')
    expect(document.body.querySelector('.heatmap-tooltip')?.getAttribute('style')).toContain('translate3d')

    wrapper.unmount()
  })
})

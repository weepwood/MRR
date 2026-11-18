import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { useMrrChart } from '../useMrrChart'

const mocks = vi.hoisted(() => ({ init: vi.fn(), observe: vi.fn(), setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn(), showLoading: vi.fn(), hideLoading: vi.fn() }))
vi.mock('@/plugins/echarts', () => ({ echarts: { init: mocks.init } }))
vi.mock('@/config/chart-theme', () => ({ applyMrrChartTheme: (option: unknown) => option, mrrChartColor: () => '#2563eb' }))
vi.mock('@vueuse/core', async importOriginal => ({
  ...await importOriginal<typeof import('@vueuse/core')>(),
  useResizeObserver: mocks.observe,
}))

describe('共享图表按尺寸初始化', () => {
  const wrappers: ReturnType<typeof mount>[] = []
  function setup(autoresize = true) {
    let chart!: ReturnType<typeof useMrrChart>
    const wrapper = mount(defineComponent({
      setup() {
        chart = useMrrChart({ series: [] }, false, { autoresize })
        return () => h('div', { ref: chart.chartRef })
      },
    }))
    wrappers.push(wrapper)
    return { wrapper, chart }
  }
  function sized(width: number) {
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(width)
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(width)
  }
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    sized(0)
    mocks.init.mockImplementation(element => ({ ...mocks, getDom: () => element }))
  })
  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('默认零尺寸图表不重复安排轮询定时器', async () => {
    setup()
    await nextTick()
    await vi.advanceTimersByTimeAsync(320)
    expect(mocks.init).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('尺寸观察器在容器恢复可见后初始化，后续只resize', async () => {
    setup()
    await nextTick()
    sized(320)
    const resizeObserver = mocks.observe.mock.calls[0][1]
    resizeObserver()
    await vi.advanceTimersByTimeAsync(80)
    expect(mocks.init).toHaveBeenCalledTimes(1)
    expect(mocks.setOption).toHaveBeenCalledTimes(1)
    resizeObserver()
    await vi.advanceTimersByTimeAsync(80)
    expect(mocks.init).toHaveBeenCalledTimes(1)
    expect(mocks.resize).toHaveBeenCalledTimes(1)
  })

  it('观察器迟到回调不能恢复已经卸载的图表', async () => {
    const { wrapper } = setup()
    await nextTick()
    const resizeObserver = mocks.observe.mock.calls[0][1]
    wrapper.unmount()
    sized(320)
    resizeObserver()
    await vi.advanceTimersByTimeAsync(320)
    expect(mocks.init).not.toHaveBeenCalled()
  })

  it('关闭自动适配时保留原初始化重试并在卸载时清理', async () => {
    const { wrapper } = setup(false)
    await nextTick()
    expect(vi.getTimerCount()).toBe(1)
    expect(mocks.observe).not.toHaveBeenCalled()
    sized(320)
    await vi.advanceTimersByTimeAsync(32)
    expect(mocks.init).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    expect(mocks.dispose).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('保留80ms防抖，连续尺寸变化只调用一次resize', async () => {
    sized(320)
    setup()
    await nextTick()
    const resizeObserver = mocks.observe.mock.calls[0][1]
    resizeObserver()
    resizeObserver()
    resizeObserver()
    await vi.advanceTimersByTimeAsync(79)
    expect(mocks.resize).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(mocks.resize).toHaveBeenCalledTimes(1)
    expect(mocks.init).toHaveBeenCalledTimes(1)
  })

  it('容器DOM更换时释放旧实例并初始化新实例，随后复用', async () => {
    sized(320)
    const { chart } = setup()
    await nextTick()
    const replacement = document.createElement('div')
    chart.chartRef.value = replacement
    const resizeObserver = mocks.observe.mock.calls[0][1]
    resizeObserver()
    await vi.advanceTimersByTimeAsync(80)
    expect(mocks.dispose).toHaveBeenCalledTimes(1)
    expect(mocks.init).toHaveBeenCalledTimes(2)
    expect(mocks.init).toHaveBeenLastCalledWith(replacement, undefined, { renderer: 'canvas' })
    expect(mocks.setOption).toHaveBeenCalledTimes(2)
    resizeObserver()
    await vi.advanceTimersByTimeAsync(80)
    expect(mocks.init).toHaveBeenCalledTimes(2)
    expect(mocks.resize).toHaveBeenCalledTimes(1)
  })
})

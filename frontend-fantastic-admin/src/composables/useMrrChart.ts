import type { MaybeRefOrGetter } from 'vue'
import type { EChartsType, ECOption } from '@/plugins/echarts'
import type { UseMrrChartOptions } from '@/types/chart'
import { useDebounceFn, useResizeObserver } from '@vueuse/core'
import {
  nextTick,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  shallowRef,
  toValue,
  watch,
} from 'vue'
import { applyMrrChartTheme, mrrChartColor } from '@/config/chart-theme'
import { echarts } from '@/plugins/echarts'

export function useMrrChart(
  option: MaybeRefOrGetter<ECOption>,
  loading: MaybeRefOrGetter<boolean> = false,
  options: UseMrrChartOptions = {},
) {
  const {
    renderer = 'canvas',
    autoresize = true,
    loadingText = '数据加载中',
  } = options

  const chartRef = shallowRef<HTMLElement | null>(null)
  const chartInstance = shallowRef<EChartsType | null>(null)
  let active = true
  let themeObserver: MutationObserver | null = null
  let themeFrame: number | null = null
  let themeSignature = ''
  let retryTimer: number | null = null

  /**
   * 只有「影响图表配色」的根节点变化才需要重绘：明暗模式、主色、配色方案。
   * <html> 的 class/style 还会被很多无关逻辑改写（切换主题时临时加/去
   * disable-color-scheme-transition-duration、底部操作栏写 --g-main-container-padding-bottom、
   * 圆角设置写 --radius 等），原实现每次都以 notMerge 全量重绘所有图表；
   * 一次明暗切换就会让页面上每张图重绘 3 次。
   */
  function readThemeSignature() {
    const root = document.documentElement
    const style = getComputedStyle(root)
    return [
      root.classList.contains('dark') ? 'dark' : 'light',
      root.dataset.mrrPalette ?? '',
      style.getPropertyValue('--color-primary').trim(),
      style.getPropertyValue('--mrr-card').trim(),
    ].join('|')
  }

  function scheduleThemeRender() {
    if (themeFrame !== null) {
      return
    }
    // 同一帧内的多次属性变更合并成一次检查。
    themeFrame = window.requestAnimationFrame(() => {
      themeFrame = null
      const next = readThemeSignature()
      if (next === themeSignature) {
        return
      }
      themeSignature = next
      render()
    })
  }

  function clearRetryTimer() {
    if (retryTimer !== null) {
      window.clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  function ensureChart() {
    const element = chartRef.value
    if (!element || !active) {
      return null
    }

    if (element.clientWidth === 0 || element.clientHeight === 0) {
      // 自动适配已有尺寸观察器；隐藏容器无需每32ms唤醒一次。
      if (!autoresize) {
        clearRetryTimer()
        retryTimer = window.setTimeout(render, 32)
      }
      return null
    }

    if (!chartInstance.value || chartInstance.value.getDom() !== element) {
      chartInstance.value?.dispose()
      chartInstance.value = echarts.init(element, undefined, { renderer })
    }

    return chartInstance.value
  }

  function syncLoading() {
    const chart = chartInstance.value
    if (!chart) {
      return
    }

    if (toValue(loading)) {
      chart.showLoading('default', {
        text: loadingText,
        color: mrrChartColor('primary'),
        maskColor: 'rgba(255, 255, 255, 0.72)',
        textColor: mrrChartColor('neutral'),
        fontSize: 12,
      })
    }
    else {
      chart.hideLoading()
    }
  }

  function render() {
    const chart = ensureChart()
    if (!chart) {
      return
    }

    chart.setOption(applyMrrChartTheme(toValue(option)), {
      notMerge: true,
      lazyUpdate: false,
    })
    syncLoading()
  }

  const resize = useDebounceFn(() => {
    if (!active) {
      return
    }
    if (!chartInstance.value || chartInstance.value.getDom() !== chartRef.value) {
      render()
      return
    }
    chartInstance.value?.resize()
  }, 80)

  if (autoresize) {
    useResizeObserver(chartRef, () => resize())
  }

  watch(
    () => toValue(option),
    () => nextTick(render),
    { deep: true },
  )

  watch(
    () => toValue(loading),
    () => nextTick(syncLoading),
  )

  onMounted(() => {
    active = true
    nextTick(render)

    themeSignature = readThemeSignature()
    themeObserver = new MutationObserver(scheduleThemeRender)
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'style', 'data-mrr-palette'],
    })
  })

  onActivated(() => {
    active = true
    // 缓存期间可能切换过主题：激活时的 render 会按当前主题重绘，这里同步签名避免重复绘制。
    themeSignature = readThemeSignature()
    nextTick(() => {
      render()
      resize()
    })
  })

  onDeactivated(() => {
    active = false
  })

  onBeforeUnmount(() => {
    active = false
    clearRetryTimer()
    if (themeFrame !== null) {
      window.cancelAnimationFrame(themeFrame)
      themeFrame = null
    }
    themeObserver?.disconnect()
    themeObserver = null
    chartInstance.value?.dispose()
    chartInstance.value = null
  })

  return {
    chartRef,
    chartInstance,
    render,
    resize,
  }
}

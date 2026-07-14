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
import { applyMrrChartTheme } from '@/config/chart-theme'
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
  let retryTimer: number | null = null

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
      clearRetryTimer()
      retryTimer = window.setTimeout(render, 32)
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
        color: '#2563eb',
        maskColor: 'rgba(255, 255, 255, 0.72)',
        textColor: '#64748b',
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

    themeObserver = new MutationObserver(() => {
      nextTick(render)
    })
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'style'],
    })
  })

  onActivated(() => {
    active = true
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

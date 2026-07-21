<script setup lang="ts">
import type { ECOption } from '@/plugins/echarts'
import type { MrrLineSeries } from '@/types/chart'
import { computed } from 'vue'
import { MRR_CHART_PALETTE } from '@/config/chart-theme'
import MrrChart from './MrrChart.vue'

defineOptions({ name: 'MrrLineChart' })

const props = withDefaults(defineProps<{
  categories: string[]
  series: MrrLineSeries[]
  loading?: boolean
  height?: number | string
  yAxisName?: string
  unit?: string
  showLegend?: boolean
  emptyDescription?: string
}>(), {
  loading: false,
  height: 320,
  yAxisName: '',
  unit: '',
  showLegend: true,
  emptyDescription: '暂无趋势数据',
})

const empty = computed(() => !props.categories.length || !props.series.some(item => item.data.length))

function formatValue(value: unknown) {
  const number = Number(value)
  if (!Number.isFinite(number)) {
    return String(value ?? '')
  }
  return `${number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}${props.unit ? ` ${props.unit}` : ''}`
}

const option = computed<ECOption>(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' },
    valueFormatter: formatValue,
  },
  legend: props.showLegend && props.series.length > 1
    ? {
        top: 0,
        right: 0,
        data: props.series.map(item => item.name),
      }
    : undefined,
  grid: {
    top: props.showLegend && props.series.length > 1 ? 42 : 24,
    right: 20,
    bottom: 28,
    left: 18,
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: props.categories,
    axisLabel: {
      hideOverlap: true,
      margin: 12,
    },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    name: props.yAxisName,
    nameGap: 16,
    minInterval: 1,
  },
  dataZoom: props.categories.length > 16
    ? [{ type: 'inside', startValue: Math.max(0, props.categories.length - 16), endValue: props.categories.length - 1 }]
    : undefined,
  series: props.series.map((item, index) => ({
    name: item.name,
    type: 'line',
    data: item.data,
    smooth: item.smooth ?? true,
    showSymbol: item.symbol ?? props.categories.length <= 12,
    symbol: 'circle',
    symbolSize: 7,
    connectNulls: false,
    lineStyle: {
      width: 2.5,
      type: item.dashed ? 'dashed' : 'solid',
      color: item.color ?? MRR_CHART_PALETTE[index % MRR_CHART_PALETTE.length],
    },
    itemStyle: {
      color: item.color ?? MRR_CHART_PALETTE[index % MRR_CHART_PALETTE.length],
      borderWidth: 2,
    },
    areaStyle: item.area
      ? {
          opacity: 0.12,
          color: item.color ?? MRR_CHART_PALETTE[index % MRR_CHART_PALETTE.length],
        }
      : undefined,
    emphasis: { focus: 'series' },
  })),
}))
</script>

<template>
  <MrrChart
    :option="option"
    :loading="loading"
    :empty="empty"
    :empty-description="emptyDescription"
    :height="height"
    aria-label="趋势折线图"
  />
</template>

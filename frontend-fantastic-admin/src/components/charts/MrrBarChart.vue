<script setup lang="ts">
import type { ECOption } from '@/plugins/echarts'
import type { MrrBarSeries } from '@/types/chart'
import { computed } from 'vue'
import { MRR_CHART_PALETTE } from '@/config/chart-theme'
import MrrChart from './MrrChart.vue'

defineOptions({ name: 'MrrBarChart' })

const props = withDefaults(defineProps<{
  categories: string[]
  series: MrrBarSeries[]
  loading?: boolean
  height?: number | string
  yAxisName?: string
  unit?: string
  showLegend?: boolean
}>(), {
  loading: false,
  height: 320,
  yAxisName: '',
  unit: '',
  showLegend: true,
})

const empty = computed(() => !props.categories.length || !props.series.some(item => item.data.length))

const option = computed<ECOption>(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    valueFormatter: value => `${Number(value).toLocaleString('zh-CN')}${props.unit ? ` ${props.unit}` : ''}`,
  },
  legend: props.showLegend && props.series.length > 1
    ? { top: 0, right: 0, data: props.series.map(item => item.name) }
    : undefined,
  grid: {
    top: props.showLegend && props.series.length > 1 ? 42 : 24,
    right: 18,
    bottom: 28,
    left: 18,
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: props.categories,
    axisLabel: { hideOverlap: true, margin: 12 },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    name: props.yAxisName,
    minInterval: 1,
  },
  dataZoom: props.categories.length > 18
    ? [{ type: 'inside', startValue: Math.max(0, props.categories.length - 18), endValue: props.categories.length - 1 }]
    : undefined,
  series: props.series.map((item, index) => ({
    name: item.name,
    type: 'bar',
    data: item.data,
    stack: item.stack,
    barMaxWidth: 32,
    itemStyle: {
      color: item.color ?? MRR_CHART_PALETTE[index % MRR_CHART_PALETTE.length],
      borderRadius: [6, 6, 2, 2],
    },
    emphasis: { focus: 'series' },
  })),
}))
</script>

<template>
  <MrrChart
    :option="option"
    :loading="loading"
    :empty="empty"
    :height="height"
    aria-label="柱状统计图"
  />
</template>

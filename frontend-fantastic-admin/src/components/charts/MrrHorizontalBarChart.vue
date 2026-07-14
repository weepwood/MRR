<script setup lang="ts">
import type { ECOption } from '@/plugins/echarts'
import type { MrrChartCountItem } from '@/types/chart'
import { computed } from 'vue'
import MrrChart from './MrrChart.vue'

defineOptions({ name: 'MrrHorizontalBarChart' })

const props = withDefaults(defineProps<{
  data: MrrChartCountItem[]
  loading?: boolean
  height?: number | string
  unit?: string
  color?: string
  showRank?: boolean
}>(), {
  loading: false,
  height: 240,
  unit: '',
  color: '#2563eb',
  showRank: true,
})

const empty = computed(() => !props.data.length)
const labels = computed(() => props.data.map((item, index) => (
  props.showRank ? `${index + 1}. ${item.label}` : item.label
)))

const option = computed<ECOption>(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    valueFormatter: value => `${Number(value).toLocaleString('zh-CN')}${props.unit ? ` ${props.unit}` : ''}`,
  },
  grid: {
    top: 8,
    right: 28,
    bottom: 8,
    left: 8,
    containLabel: true,
  },
  xAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { show: false },
    axisLine: { show: false },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'category',
    inverse: true,
    data: labels.value,
    axisLine: { show: false },
    axisLabel: {
      width: 126,
      overflow: 'truncate',
      margin: 12,
    },
  },
  series: [
    {
      type: 'bar',
      data: props.data.map(item => ({
        value: item.count,
        itemStyle: { color: item.color ?? props.color },
      })),
      barWidth: 12,
      showBackground: true,
      backgroundStyle: {
        color: 'rgba(148, 163, 184, 0.12)',
        borderRadius: 999,
      },
      itemStyle: {
        borderRadius: 999,
      },
      label: {
        show: true,
        position: 'right',
        formatter: params => Number(params.value).toLocaleString('zh-CN'),
        fontSize: 11,
      },
    },
  ],
}))
</script>

<template>
  <MrrChart
    :option="option"
    :loading="loading"
    :empty="empty"
    :height="height"
    aria-label="横向排行图"
  />
</template>

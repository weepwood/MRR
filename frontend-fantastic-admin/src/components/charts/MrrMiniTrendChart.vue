<script setup lang="ts">
import type { ECOption } from '@/plugins/echarts'
import { computed } from 'vue'
import { mrrChartColor } from '@/config/chart-theme'
import MrrChart from './MrrChart.vue'

defineOptions({ name: 'MrrMiniTrendChart' })

const props = withDefaults(defineProps<{
  data: number[]
  color?: string
  height?: number | string
  loading?: boolean
}>(), {
  height: 64,
  loading: false,
})

const option = computed<ECOption>(() => ({
  grid: { top: 6, right: 2, bottom: 2, left: 2 },
  xAxis: {
    type: 'category',
    show: false,
    data: props.data.map((_, index) => String(index + 1)),
  },
  yAxis: {
    type: 'value',
    show: false,
    scale: true,
  },
  series: [
    {
      type: 'line',
      data: props.data,
      smooth: true,
      showSymbol: false,
      silent: true,
      lineStyle: {
        width: 2,
        color: props.color ?? mrrChartColor('primary'),
      },
      areaStyle: {
        opacity: 0.1,
        color: props.color ?? mrrChartColor('primary'),
      },
    },
  ],
}))
</script>

<template>
  <MrrChart
    :option="option"
    :loading="loading"
    :empty="!data.length"
    :height="height"
    aria-label="迷你趋势图"
  />
</template>

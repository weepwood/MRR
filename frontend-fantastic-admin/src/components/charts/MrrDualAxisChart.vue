<script setup lang="ts">
import type { ECOption } from '@/plugins/echarts'
import type { MrrBarSeries, MrrLineSeries } from '@/types/chart'
import { computed } from 'vue'
import MrrChart from './MrrChart.vue'

defineOptions({ name: 'MrrDualAxisChart' })

const props = withDefaults(defineProps<{
  categories: string[]
  bars: MrrBarSeries[]
  lines: MrrLineSeries[]
  loading?: boolean
  height?: number | string
  leftAxisName?: string
  rightAxisName?: string
  leftUnit?: string
  rightUnit?: string
  zoomable?: boolean
  initialVisibleCount?: number
}>(), {
  loading: false,
  height: 320,
  leftAxisName: '',
  rightAxisName: '',
  leftUnit: '',
  rightUnit: '',
  zoomable: true,
  initialVisibleCount: 16,
})

const empty = computed(() => !props.categories.length || ![
  ...props.bars,
  ...props.lines,
].some(item => item.data.length))

const visibleCount = computed(() => Math.max(1, Math.floor(props.initialVisibleCount)))

const option = computed<ECOption>(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'cross' },
  },
  legend: {
    top: 0,
    right: 0,
    data: [...props.bars, ...props.lines].map(item => item.name),
  },
  grid: {
    top: 48,
    right: 28,
    bottom: 28,
    left: 24,
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: props.categories,
    axisLabel: { hideOverlap: true, margin: 12 },
    splitLine: { show: false },
  },
  yAxis: [
    {
      type: 'value',
      name: props.leftAxisName,
      minInterval: 1,
      axisLabel: {
        formatter: (value: string | number) => `${Number(value).toLocaleString('zh-CN')}${props.leftUnit ? ` ${props.leftUnit}` : ''}`,
      },
    },
    {
      type: 'value',
      name: props.rightAxisName,
      splitLine: { show: false },
      axisLabel: {
        formatter: (value: string | number) => `${Number(value).toLocaleString('zh-CN')}${props.rightUnit ? ` ${props.rightUnit}` : ''}`,
      },
    },
  ],
  dataZoom: props.zoomable && props.categories.length > visibleCount.value
    ? [{
        type: 'inside',
        startValue: Math.max(0, props.categories.length - visibleCount.value),
        endValue: props.categories.length - 1,
        zoomOnMouseWheel: true,
        moveOnMouseWheel: false,
        moveOnMouseMove: true,
      }]
    : undefined,
  series: [
    ...props.bars.map(item => ({
      name: item.name,
      type: 'bar' as const,
      yAxisIndex: 0,
      data: item.data,
      stack: item.stack,
      // 90 根逐日柱子时，30px 上限几乎填满类目宽度，柱与柱粘成一整块；
      // 留出 40% 类目间隙并收小圆角，单日起伏才能被逐根读出。
      barMaxWidth: 20,
      barCategoryGap: '40%',
      itemStyle: {
        color: item.color,
        borderRadius: [3, 3, 0, 0],
        opacity: 0.82,
      },
      emphasis: { focus: 'series' as const },
    })),
    ...props.lines.map(item => ({
      name: item.name,
      type: 'line' as const,
      yAxisIndex: 1,
      data: item.data,
      smooth: item.smooth ?? true,
      showSymbol: item.symbol ?? props.categories.length <= 12,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: {
        width: 2,
        type: item.dashed ? 'dashed' as const : 'solid' as const,
        color: item.color,
      },
      itemStyle: {
        color: item.color,
        borderWidth: 2,
      },
      emphasis: { focus: 'series' as const },
    })),
  ],
}))
</script>

<template>
  <MrrChart
    :option="option"
    :loading="loading"
    :empty="empty"
    :height="height"
    aria-label="双坐标轴组合图"
  />
</template>

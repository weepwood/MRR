<script setup lang="ts">
import type { ECOption } from '@/plugins/echarts'
import type { MrrChartCountItem } from '@/types/chart'
import { computed } from 'vue'
import { MRR_CHART_PALETTE } from '@/config/chart-theme'
import MrrChart from './MrrChart.vue'

defineOptions({ name: 'MrrDonutChart' })

const props = withDefaults(defineProps<{
  data: MrrChartCountItem[]
  loading?: boolean
  height?: number | string
  centerLabel?: string
  unit?: string
  legendPosition?: 'right' | 'bottom'
}>(), {
  loading: false,
  height: 240,
  centerLabel: '合计',
  unit: '',
  legendPosition: 'right',
})

const total = computed(() => props.data.reduce((sum, item) => sum + item.count, 0))
const empty = computed(() => total.value <= 0)

const option = computed<ECOption>(() => {
  const legendAtRight = props.legendPosition === 'right'

  return {
    title: {
      text: total.value.toLocaleString('zh-CN'),
      subtext: props.centerLabel,
      left: legendAtRight ? '31%' : 'center',
      top: legendAtRight ? '40%' : '38%',
      textAlign: 'center',
      textStyle: {
        fontSize: 21,
        fontWeight: 650,
      },
      subtextStyle: {
        fontSize: 11,
      },
    },
    tooltip: {
      trigger: 'item',
      valueFormatter: value => `${Number(value).toLocaleString('zh-CN')}${props.unit ? ` ${props.unit}` : ''}`,
    },
    legend: legendAtRight
      ? {
          orient: 'vertical',
          right: 8,
          top: 'middle',
          itemGap: 12,
        }
      : {
          orient: 'horizontal',
          left: 'center',
          bottom: 0,
        },
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: legendAtRight ? ['31%', '50%'] : ['50%', '45%'],
        avoidLabelOverlap: true,
        padAngle: 2,
        itemStyle: {
          borderRadius: 6,
          borderWidth: 2,
          borderColor: 'transparent',
        },
        label: { show: false },
        emphasis: {
          label: { show: false },
          scaleSize: 5,
        },
        data: props.data.map((item, index) => ({
          name: item.label,
          value: item.count,
          itemStyle: {
            color: item.color ?? MRR_CHART_PALETTE[index % MRR_CHART_PALETTE.length],
          },
        })),
      },
    ],
  }
})
</script>

<template>
  <MrrChart
    :option="option"
    :loading="loading"
    :empty="empty"
    :height="height"
    aria-label="环形分布图"
  />
</template>

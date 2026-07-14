<script setup lang="ts">
import type { ECOption } from '@/plugins/echarts'
import type { MrrChartRenderer } from '@/types/chart'
import { computed } from 'vue'
import { useMrrChart } from '@/composables/useMrrChart'

defineOptions({ name: 'MrrChart' })

const props = withDefaults(defineProps<{
  option: ECOption
  loading?: boolean
  empty?: boolean
  emptyDescription?: string
  height?: number | string
  renderer?: MrrChartRenderer
  ariaLabel?: string
}>(), {
  loading: false,
  empty: false,
  emptyDescription: '暂无图表数据',
  height: 320,
  renderer: 'canvas',
  ariaLabel: '数据图表',
})

const chartHeight = computed(() => typeof props.height === 'number' ? `${props.height}px` : props.height)
const { chartRef } = useMrrChart(
  () => props.option,
  () => props.loading,
  { renderer: props.renderer },
)

function setChartRef(element: unknown) {
  chartRef.value = element instanceof HTMLElement ? element : null
}
</script>

<template>
  <div
    class="mrr-chart"
    :class="{ 'is-empty': empty }"
    :style="{ height: chartHeight }"
    :aria-busy="loading"
  >
    <div
      :ref="setChartRef"
      class="mrr-chart__canvas"
      :aria-label="ariaLabel"
      role="img"
    />
    <div v-if="empty" class="mrr-chart__empty">
      <i class="i-ant-design:bar-chart-outlined" aria-hidden="true" />
      <span>{{ emptyDescription }}</span>
    </div>
  </div>
</template>

<style scoped>
.mrr-chart {
  position: relative;
  width: 100%;
  min-height: 160px;
}

.mrr-chart__canvas {
  width: 100%;
  height: 100%;
}

.mrr-chart__empty {
  position: absolute;
  inset: 0;
  display: grid;
  gap: 8px;
  place-content: center;
  justify-items: center;
  color: var(--el-text-color-placeholder);
  background: var(--el-bg-color);
}

.mrr-chart__empty i {
  font-size: 30px;
}

.mrr-chart__empty span {
  font-size: 13px;
}
</style>

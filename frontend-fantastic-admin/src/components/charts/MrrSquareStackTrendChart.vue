<script setup lang="ts">
import type { MrrSquareStackTrendItem } from '@/types/chart'
import { computed, ref } from 'vue'

defineOptions({ name: 'MrrSquareStackTrendChart' })

const props = withDefaults(defineProps<{
  data: MrrSquareStackTrendItem[]
  rows?: number
  height?: number
  unit?: string
  emptyDescription?: string
}>(), {
  rows: 18,
  height: 228,
  unit: '次',
  emptyDescription: '暂无请求趋势数据',
})

interface DisplayPoint extends MrrSquareStackTrendItem {
  total: number
  error: number
  success: number
  activeBlocks: number
  errorBlocks: number
  successBlocks: number
}

const viewportRef = ref<HTMLElement | null>(null)
const activeIndex = ref<number | null>(null)
const guideX = ref(0)
const tooltipPosition = ref({ x: 0, y: 0 })

const maxTotal = computed(() => Math.max(0, ...props.data.map(item => normalizeCount(item.total))))
const blockUnit = computed(() => Math.max(1, niceStep(maxTotal.value / Math.max(1, props.rows))))
const scaleMax = computed(() => blockUnit.value * props.rows)
const isEmpty = computed(() => !props.data.length)

const points = computed<DisplayPoint[]>(() => props.data.map((item) => {
  const total = normalizeCount(item.total)
  const error = Math.min(total, normalizeCount(item.error))
  const activeBlocks = total > 0
    ? Math.min(props.rows, Math.max(1, Math.ceil(total / blockUnit.value)))
    : 0
  const errorBlocks = error > 0
    ? Math.min(activeBlocks, Math.max(1, Math.ceil(error / blockUnit.value)))
    : 0

  return {
    ...item,
    total,
    error,
    success: Math.max(0, total - error),
    activeBlocks,
    errorBlocks,
    successBlocks: Math.max(0, activeBlocks - errorBlocks),
  }
}))

const activePoint = computed(() => activeIndex.value === null ? null : points.value[activeIndex.value] ?? null)
const labelStep = computed(() => Math.max(1, Math.ceil(points.value.length / 12)))
const chartStyle = computed(() => ({
  '--stack-height': `${props.height}px`,
  '--rows': props.rows,
  '--chart-min-width': `${Math.max(560, points.value.length * 34)}px`,
}))
const guideStyle = computed(() => ({ left: `${guideX.value}px` }))
const tooltipStyle = computed(() => ({
  left: `${tooltipPosition.value.x}px`,
  top: `${tooltipPosition.value.y}px`,
}))
const tickValues = computed(() => [
  scaleMax.value,
  blockUnit.value * Math.round(props.rows * 0.75),
  blockUnit.value * Math.round(props.rows * 0.5),
  blockUnit.value * Math.round(props.rows * 0.25),
  0,
])

function normalizeCount(value: unknown) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.round(number) : 0
}

function niceStep(rawValue: number) {
  if (!Number.isFinite(rawValue) || rawValue <= 1) {
    return 1
  }
  const magnitude = 10 ** Math.floor(Math.log10(rawValue))
  const normalized = rawValue / magnitude
  const factor = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return factor * magnitude
}

function formatNumber(value: number) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}

function formatPercent(error: number, total: number) {
  return total > 0 ? `${(error / total * 100).toFixed(2)}%` : '0.00%'
}

function blockState(point: DisplayPoint, blockIndexFromBottom: number) {
  if (blockIndexFromBottom < point.successBlocks) {
    return 'is-success'
  }
  if (blockIndexFromBottom < point.activeBlocks) {
    return 'is-error'
  }
  return 'is-inactive'
}

function shouldShowLabel(index: number) {
  return index === 0 || index === points.value.length - 1 || index % labelStep.value === 0
}

function tickStyle(index: number) {
  const percentage = tickValues.value.length <= 1 ? 0 : index / (tickValues.value.length - 1) * 100
  return { top: `${percentage}%` }
}

function handlePointerMove(index: number, event: PointerEvent) {
  const viewport = viewportRef.value
  if (!viewport) {
    return
  }

  const rect = viewport.getBoundingClientRect()
  const pointerX = event.clientX - rect.left
  const pointerY = event.clientY - rect.top
  const tooltipWidth = 224
  const tooltipHeight = 166

  activeIndex.value = index
  guideX.value = pointerX
  tooltipPosition.value = {
    x: pointerX + tooltipWidth + 24 > rect.width
      ? Math.max(8, pointerX - tooltipWidth - 14)
      : pointerX + 14,
    y: Math.min(Math.max(8, pointerY - 74), Math.max(8, rect.height - tooltipHeight - 8)),
  }
}

function clearActivePoint() {
  activeIndex.value = null
}
</script>

<template>
  <div class="square-trend" :style="chartStyle">
    <div class="chart-meta">
      <div class="legend" aria-label="图例">
        <span><i class="legend-success" />成功请求</span>
        <span><i class="legend-error" />错误请求</span>
        <span><i class="legend-capacity" />量级参考</span>
      </div>
      <span class="unit-hint">每格约 {{ formatNumber(blockUnit) }} {{ unit }}</span>
    </div>

    <div v-if="isEmpty" class="empty-state">
      <i class="i-ant-design:appstore-outlined" aria-hidden="true" />
      <span>{{ emptyDescription }}</span>
    </div>

    <div
      v-else
      ref="viewportRef"
      class="chart-viewport"
      role="img"
      aria-label="方块堆叠请求趋势图，蓝色表示成功请求，红色表示错误请求"
      @pointerleave="clearActivePoint"
    >
      <div class="axis-labels" aria-hidden="true">
        <span
          v-for="(tick, index) in tickValues"
          :key="`${tick}-${index}`"
          :style="tickStyle(index)"
        >
          {{ formatNumber(tick) }}
        </span>
      </div>

      <div class="plot-scroll">
        <div class="plot-content">
          <div class="grid-lines" aria-hidden="true">
            <span v-for="index in tickValues.length" :key="index" />
          </div>

          <div class="columns">
            <div
              v-for="(point, pointIndex) in points"
              :key="`${point.category}-${pointIndex}`"
              class="column-wrap"
              :class="{ 'is-active': activeIndex === pointIndex }"
              :aria-label="`${point.category}，请求 ${point.total} 次，错误 ${point.error} 次`"
              @pointerenter="handlePointerMove(pointIndex, $event)"
              @pointermove="handlePointerMove(pointIndex, $event)"
            >
              <div class="column-stack">
                <span
                  v-for="blockIndex in rows"
                  :key="blockIndex"
                  class="block"
                  :class="blockState(point, rows - blockIndex)"
                />
              </div>
              <span class="category-label" :class="{ 'is-hidden': !shouldShowLabel(pointIndex) }">
                {{ point.category }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <span v-if="activePoint" class="guide-line" :style="guideStyle" aria-hidden="true" />
      <span v-if="activePoint" class="guide-label" :style="guideStyle">{{ activePoint.category }}</span>

      <div v-if="activePoint" class="chart-tooltip" :style="tooltipStyle">
        <span class="tooltip-date">{{ activePoint.category }}</span>
        <strong>{{ formatNumber(activePoint.total) }} {{ unit }}</strong>
        <dl>
          <div><dt>成功请求</dt><dd>{{ formatNumber(activePoint.success) }}</dd></div>
          <div><dt>错误请求</dt><dd class="error-value">{{ formatNumber(activePoint.error) }}</dd></div>
          <div><dt>错误率</dt><dd>{{ formatPercent(activePoint.error, activePoint.total) }}</dd></div>
        </dl>
      </div>
    </div>
  </div>
</template>

<style scoped>
.square-trend {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.chart-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.legend span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.legend i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.legend-success { background: #2563eb; }
.legend-error { background: #ef4444; }
.legend-capacity { background: var(--el-fill-color-light); border: 1px solid var(--el-border-color-lighter); }

.unit-hint {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.chart-viewport {
  position: relative;
  min-width: 0;
  padding-bottom: 30px;
}

.axis-labels {
  position: absolute;
  top: 0;
  bottom: 30px;
  left: 0;
  width: 46px;
  font-size: 10px;
  color: var(--el-text-color-placeholder);
}

.axis-labels span {
  position: absolute;
  right: 7px;
  transform: translateY(-50%);
  white-space: nowrap;
}

.axis-labels span:last-child {
  transform: translateY(-100%);
}

.plot-scroll {
  margin-left: 46px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.plot-content {
  position: relative;
  min-width: var(--chart-min-width);
}

.grid-lines {
  position: absolute;
  inset: 0 0 30px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.grid-lines span {
  width: 100%;
  border-top: 1px dashed color-mix(in srgb, var(--el-border-color-lighter) 70%, transparent);
}

.columns {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(26px, 1fr));
  gap: 4px;
  align-items: end;
}

.column-wrap {
  display: grid;
  gap: 8px;
  justify-items: center;
  min-width: 26px;
  cursor: crosshair;
}

.column-stack {
  display: grid;
  grid-template-rows: repeat(var(--rows), minmax(0, 1fr));
  gap: 3px;
  width: 100%;
  height: var(--stack-height);
  align-items: stretch;
}

.block {
  width: min(10px, 72%);
  min-height: 4px;
  margin: 0 auto;
  border-radius: 2px;
  transition: opacity 140ms ease, transform 140ms ease, filter 140ms ease;
}

.block.is-success {
  background: #2563eb;
  box-shadow: inset 0 -1px 0 rgb(15 23 42 / 12%);
}

.block.is-error {
  background: #ef4444;
  box-shadow: 0 0 0 1px color-mix(in srgb, #ef4444 28%, transparent);
}

.block.is-inactive {
  background: color-mix(in srgb, var(--el-fill-color-light) 86%, transparent);
  border: 1px solid color-mix(in srgb, var(--el-border-color-lighter) 68%, transparent);
}

.column-wrap.is-active .block {
  filter: saturate(1.08);
  transform: scale(1.08);
}

.column-wrap:not(.is-active):hover .block.is-inactive {
  opacity: 0.82;
}

.category-label {
  min-height: 14px;
  overflow: hidden;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-label.is-hidden {
  visibility: hidden;
}

.guide-line {
  position: absolute;
  top: -2px;
  bottom: 30px;
  z-index: 3;
  pointer-events: none;
  border-left: 1px dashed var(--el-text-color-primary);
  opacity: 0.72;
}

.guide-label {
  position: absolute;
  bottom: 0;
  z-index: 4;
  padding: 3px 7px;
  font-size: 10px;
  color: #fff;
  pointer-events: none;
  background: var(--el-text-color-primary);
  border-radius: 5px;
  transform: translateX(-50%);
  white-space: nowrap;
}

.chart-tooltip {
  position: absolute;
  z-index: 5;
  width: 224px;
  padding: 13px 14px;
  pointer-events: none;
  color: var(--el-text-color-primary);
  background: color-mix(in srgb, var(--el-bg-color-overlay) 96%, transparent);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  box-shadow: 0 16px 36px rgb(15 23 42 / 16%);
  backdrop-filter: blur(12px);
}

.tooltip-date {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.chart-tooltip > strong {
  display: block;
  margin-bottom: 10px;
  font-size: 20px;
  line-height: 1.2;
}

.chart-tooltip dl {
  display: grid;
  gap: 7px;
  margin: 0;
}

.chart-tooltip dl div {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
}

.chart-tooltip dt {
  color: var(--el-text-color-secondary);
}

.chart-tooltip dd {
  margin: 0;
  font-weight: 600;
}

.chart-tooltip .error-value {
  color: #ef4444;
}

.empty-state {
  display: grid;
  gap: 8px;
  place-content: center;
  justify-items: center;
  min-height: calc(var(--stack-height) + 30px);
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-extra-light);
  border: 1px dashed var(--el-border-color);
  border-radius: 12px;
}

.empty-state i {
  font-size: 28px;
}

@media (width <= 600px) {
  .chart-meta {
    align-items: flex-start;
  }

  .legend {
    gap: 9px;
  }

  .axis-labels {
    width: 40px;
  }

  .plot-scroll {
    margin-left: 40px;
  }
}
</style>

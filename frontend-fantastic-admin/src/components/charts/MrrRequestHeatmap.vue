<script setup lang="ts">
import type { MrrRequestHeatmapItem } from '@/types/chart'
import { computed, ref } from 'vue'

defineOptions({ name: 'MrrRequestHeatmap' })

const props = withDefaults(defineProps<{
  data: MrrRequestHeatmapItem[]
  days?: number
  cellSize?: number
  cellGap?: number
  emptyDescription?: string
}>(), {
  days: 365,
  cellSize: 11,
  cellGap: 3,
  emptyDescription: '近一年暂无请求数据',
})

interface HeatmapDay extends MrrRequestHeatmapItem {
  date: string
  total: number
  error: number
  avgClientDurationMs: number
  avgServerDurationMs: number
  inRange: boolean
  level: number
}

interface MonthLabel {
  key: string
  label: string
  weekIndex: number
}

const TOOLTIP_WIDTH = 236
const TOOLTIP_HEIGHT = 190
const TOOLTIP_OFFSET = 10
const VIEWPORT_PADDING = 8

const activeDay = ref<HeatmapDay | null>(null)
const tooltipPosition = ref({ x: 0, y: 0 })

const normalizedData = computed(() => {
  const map = new Map<string, MrrRequestHeatmapItem>()
  for (const item of props.data) {
    const date = normalizeDateKey(item.date)
    if (!date) {
      continue
    }
    map.set(date, {
      ...item,
      date,
      total: normalizeNumber(item.total),
      error: normalizeNumber(item.error),
      avgClientDurationMs: normalizeNumber(item.avgClientDurationMs),
      avgServerDurationMs: normalizeNumber(item.avgServerDurationMs),
    })
  }
  return map
})

const rangeEnd = computed(() => startOfDay(new Date()))
const rangeStart = computed(() => addDays(rangeEnd.value, -(Math.max(1, props.days) - 1)))
const calendarStart = computed(() => addDays(rangeStart.value, -rangeStart.value.getDay()))
const calendarEnd = computed(() => addDays(rangeEnd.value, 6 - rangeEnd.value.getDay()))

const maxRequestCount = computed(() => Math.max(
  0,
  ...Array.from(normalizedData.value.values()).map(item => normalizeNumber(item.total)),
))

const calendarDays = computed<HeatmapDay[]>(() => {
  const days: HeatmapDay[] = []
  for (let cursor = calendarStart.value; cursor <= calendarEnd.value; cursor = addDays(cursor, 1)) {
    const date = toDateKey(cursor)
    const inRange = cursor >= rangeStart.value && cursor <= rangeEnd.value
    const source = normalizedData.value.get(date)
    const total = inRange ? normalizeNumber(source?.total) : 0
    const error = inRange ? Math.min(total, normalizeNumber(source?.error)) : 0

    days.push({
      date,
      total,
      error,
      avgClientDurationMs: inRange ? normalizeNumber(source?.avgClientDurationMs) : 0,
      avgServerDurationMs: inRange ? normalizeNumber(source?.avgServerDurationMs) : 0,
      inRange,
      level: inRange ? intensityLevel(total, maxRequestCount.value) : 0,
    })
  }
  return days
})

const weekCount = computed(() => Math.ceil(calendarDays.value.length / 7))
const gridWidth = computed(() => (
  weekCount.value * props.cellSize + Math.max(0, weekCount.value - 1) * props.cellGap
))
const chartStyle = computed(() => ({
  '--heatmap-cell-size': `${props.cellSize}px`,
  '--heatmap-cell-gap': `${props.cellGap}px`,
  '--heatmap-grid-width': `${gridWidth.value}px`,
  '--heatmap-week-count': weekCount.value,
}))

const monthLabels = computed<MonthLabel[]>(() => {
  const labels: MonthLabel[] = []
  const seen = new Set<string>()

  calendarDays.value.forEach((day, index) => {
    if (!day.inRange) {
      return
    }
    const monthKey = day.date.slice(0, 7)
    if (seen.has(monthKey)) {
      return
    }
    seen.add(monthKey)
    labels.push({
      key: monthKey,
      label: formatMonth(day.date),
      weekIndex: Math.floor(index / 7),
    })
  })

  return labels
})

const totalRequests = computed(() => calendarDays.value.reduce(
  (sum, day) => sum + (day.inRange ? day.total : 0),
  0,
))
const activeDays = computed(() => calendarDays.value.filter(day => day.inRange && day.total > 0).length)
const hasData = computed(() => totalRequests.value > 0)
const tooltipStyle = computed(() => ({
  left: `${tooltipPosition.value.x}px`,
  top: `${tooltipPosition.value.y}px`,
}))

function normalizeDateKey(value: string) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}-${match[2]}-${match[3]}` : ''
}

function normalizeNumber(value: unknown) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number : 0
}

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate())
}

function addDays(value: Date, amount: number) {
  const result = new Date(value)
  result.setDate(result.getDate() + amount)
  return result
}

function toDateKey(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function intensityLevel(value: number, maximum: number) {
  if (value <= 0 || maximum <= 0) {
    return 0
  }
  if (maximum === value) {
    return 4
  }
  const ratio = Math.log1p(value) / Math.log1p(maximum)
  return Math.max(1, Math.min(4, Math.ceil(ratio * 4)))
}

function formatMonth(value: string) {
  const month = Number(value.slice(5, 7))
  return `${month}月`
}

function formatDate(value: string) {
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  })
}

function formatNumber(value: number, maximumFractionDigits = 0) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits })
}

function formatDuration(value: number) {
  if (value >= 1000) {
    return `${formatNumber(value / 1000, 2)} s`
  }
  return `${formatNumber(value, 1)} ms`
}

function formatErrorRate(day: HeatmapDay) {
  return day.total > 0 ? `${(day.error / day.total * 100).toFixed(2)}%` : '0.00%'
}

function dayAriaLabel(day: HeatmapDay) {
  return `${formatDate(day.date)}，请求 ${formatNumber(day.total)} 次，错误 ${formatNumber(day.error)} 次`
}

function monthLabelStyle(label: MonthLabel) {
  return { gridColumnStart: label.weekIndex + 1 }
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(Math.max(value, minimum), maximum)
}

function showTooltip(day: HeatmapDay, event: PointerEvent | FocusEvent) {
  if (!day.inRange) {
    return
  }

  const target = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  if (!target) {
    return
  }

  const targetRect = target.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const maxX = Math.max(VIEWPORT_PADDING, viewportWidth - TOOLTIP_WIDTH - VIEWPORT_PADDING)
  const maxY = Math.max(VIEWPORT_PADDING, viewportHeight - TOOLTIP_HEIGHT - VIEWPORT_PADDING)
  const preferredTop = targetRect.top - TOOLTIP_HEIGHT - TOOLTIP_OFFSET
  const fallbackTop = targetRect.bottom + TOOLTIP_OFFSET

  activeDay.value = day
  tooltipPosition.value = {
    x: clamp(
      targetRect.left + targetRect.width / 2 - TOOLTIP_WIDTH / 2,
      VIEWPORT_PADDING,
      maxX,
    ),
    y: clamp(
      preferredTop >= VIEWPORT_PADDING ? preferredTop : fallbackTop,
      VIEWPORT_PADDING,
      maxY,
    ),
  }
}

function hideTooltip() {
  activeDay.value = null
}
</script>

<template>
  <section class="request-heatmap" :style="chartStyle" aria-label="最近一年每日请求量热力图">
    <header class="heatmap-header">
      <div>
        <strong>{{ formatNumber(totalRequests) }} 次请求</strong>
        <span>最近 {{ days }} 天 · {{ activeDays }} 天有请求记录</span>
      </div>
      <div class="heatmap-legend" aria-label="请求量颜色图例">
        <span>少</span>
        <i class="level-0" />
        <i class="level-1" />
        <i class="level-2" />
        <i class="level-3" />
        <i class="level-4" />
        <span>多</span>
      </div>
    </header>

    <div class="heatmap-viewport" @pointerleave="hideTooltip">
      <div class="heatmap-scroll" @scroll.passive="hideTooltip" @wheel.passive="hideTooltip">
        <div class="heatmap-content">
          <div class="month-labels" aria-hidden="true">
            <span
              v-for="label in monthLabels"
              :key="label.key"
              :style="monthLabelStyle(label)"
            >
              {{ label.label }}
            </span>
          </div>

          <div class="heatmap-body">
            <div class="weekday-labels" aria-hidden="true">
              <span />
              <span>一</span>
              <span />
              <span>三</span>
              <span />
              <span>五</span>
              <span />
            </div>

            <div class="heatmap-grid">
              <template v-for="day in calendarDays" :key="day.date">
                <button
                  v-if="day.inRange"
                  type="button"
                  class="heatmap-cell"
                  :class="`level-${day.level}`"
                  :aria-label="dayAriaLabel(day)"
                  @pointerenter="showTooltip(day, $event)"
                  @focus="showTooltip(day, $event)"
                  @blur="hideTooltip"
                />
                <span v-else class="heatmap-cell is-outside" aria-hidden="true" />
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!hasData" class="empty-hint">
      {{ emptyDescription }}，灰色方块仍表示对应日期。
    </div>

    <Teleport to="body">
      <div v-if="activeDay" class="heatmap-tooltip" :style="tooltipStyle">
        <span>{{ formatDate(activeDay.date) }}</span>
        <strong>{{ formatNumber(activeDay.total) }} 次请求</strong>
        <dl>
          <div>
            <dt>错误请求</dt>
            <dd :class="{ 'is-error': activeDay.error > 0 }">
              {{ formatNumber(activeDay.error) }} · {{ formatErrorRate(activeDay) }}
            </dd>
          </div>
          <div>
            <dt>客户端平均</dt>
            <dd>{{ formatDuration(activeDay.avgClientDurationMs) }}</dd>
          </div>
          <div>
            <dt>服务端平均</dt>
            <dd>{{ formatDuration(activeDay.avgServerDurationMs) }}</dd>
          </div>
        </dl>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.request-heatmap {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.heatmap-header {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: end;
  justify-content: space-between;
}

.heatmap-header > div:first-child {
  display: grid;
  gap: 3px;
}

.heatmap-header strong {
  font-size: 17px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.heatmap-header span {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.heatmap-legend {
  display: flex;
  gap: 4px;
  align-items: center;
}

.heatmap-legend span {
  padding: 0 3px;
  font-size: 10px;
  color: var(--el-text-color-placeholder);
}

.heatmap-legend i {
  width: var(--heatmap-cell-size);
  height: var(--heatmap-cell-size);
  border-radius: 2px;
}

.heatmap-viewport {
  min-width: 0;
}

.heatmap-scroll {
  padding: 3px 0 8px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
}

.heatmap-content {
  width: max-content;
  min-width: 100%;
}

.month-labels {
  display: grid;
  grid-template-columns: repeat(var(--heatmap-week-count), var(--heatmap-cell-size));
  column-gap: var(--heatmap-cell-gap);
  width: var(--heatmap-grid-width);
  height: 20px;
  margin-left: 34px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.month-labels span {
  overflow: visible;
  white-space: nowrap;
}

.heatmap-body {
  display: flex;
  gap: 6px;
  align-items: flex-start;
}

.weekday-labels {
  display: grid;
  grid-template-rows: repeat(7, var(--heatmap-cell-size));
  row-gap: var(--heatmap-cell-gap);
  width: 28px;
  flex: none;
  font-size: 9px;
  line-height: var(--heatmap-cell-size);
  color: var(--el-text-color-placeholder);
  text-align: right;
}

.heatmap-grid {
  display: grid;
  grid-auto-columns: var(--heatmap-cell-size);
  grid-auto-flow: column;
  grid-template-rows: repeat(7, var(--heatmap-cell-size));
  gap: var(--heatmap-cell-gap);
  width: var(--heatmap-grid-width);
}

.heatmap-cell {
  box-sizing: border-box;
  width: var(--heatmap-cell-size);
  height: var(--heatmap-cell-size);
  padding: 0;
  appearance: none;
  cursor: pointer;
  border: 1px solid color-mix(in srgb, var(--el-border-color-lighter) 82%, transparent);
  border-radius: 2px;
  outline: none;
  transition: border-color 120ms ease, filter 120ms ease, transform 120ms ease;
}

.heatmap-cell:hover,
.heatmap-cell:focus-visible {
  position: relative;
  z-index: 2;
  border-color: var(--el-text-color-primary);
  filter: saturate(1.08);
  transform: scale(1.18);
}

.heatmap-cell.is-outside {
  visibility: hidden;
  pointer-events: none;
}

.level-0 {
  background: color-mix(in srgb, var(--el-fill-color-light) 88%, transparent);
}

.level-1 {
  background: color-mix(in srgb, #2563eb 24%, var(--el-bg-color));
  border-color: color-mix(in srgb, #2563eb 16%, var(--el-border-color-lighter));
}

.level-2 {
  background: color-mix(in srgb, #2563eb 45%, var(--el-bg-color));
  border-color: color-mix(in srgb, #2563eb 30%, transparent);
}

.level-3 {
  background: color-mix(in srgb, #2563eb 70%, var(--el-bg-color));
  border-color: color-mix(in srgb, #2563eb 48%, transparent);
}

.level-4 {
  background: #2563eb;
  border-color: #1d4ed8;
}

.heatmap-tooltip {
  position: fixed;
  z-index: 4000;
  box-sizing: border-box;
  width: 236px;
  padding: 13px 14px;
  pointer-events: none;
  color: var(--el-text-color-primary);
  background: color-mix(in srgb, var(--el-bg-color-overlay) 97%, transparent);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  box-shadow: 0 14px 34px rgb(15 23 42 / 18%);
  backdrop-filter: blur(12px);
  will-change: left, top;
}

.heatmap-tooltip > span {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.heatmap-tooltip > strong {
  display: block;
  margin-bottom: 10px;
  font-size: 18px;
  line-height: 1.25;
}

.heatmap-tooltip dl {
  display: grid;
  gap: 7px;
  margin: 0;
}

.heatmap-tooltip dl div {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  font-size: 11px;
}

.heatmap-tooltip dt {
  color: var(--el-text-color-secondary);
}

.heatmap-tooltip dd {
  margin: 0;
  font-weight: 600;
  text-align: right;
}

.heatmap-tooltip dd.is-error {
  color: #ef4444;
}

.empty-hint {
  padding: 9px 11px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-extra-light);
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 8px;
}

@media (width <= 600px) {
  .heatmap-header {
    align-items: flex-start;
  }

  .heatmap-legend {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>

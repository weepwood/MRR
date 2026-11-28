<script setup lang="ts">
import type { DashboardSource } from './dashboard-data'
import type { SourceState } from './useDashboardData'
import { computed } from 'vue'

const props = defineProps<{
  source: DashboardSource
  state: SourceState
  size: 'small' | 'wide' | 'large'
  metricKeys?: string[]
  showChart?: boolean
  preview?: boolean
}>()
const emit = defineEmits<{ refresh: [] }>()
const metrics = computed(() => (props.state.snapshot?.metrics ?? [])
  .filter(item => !props.metricKeys || props.metricKeys.includes(item.key))
  .slice(0, props.size === 'small' ? 1 : props.size === 'wide' ? 2 : 3))
const bars = computed(() => props.size === 'large' && props.showChart !== false ? props.state.snapshot?.bars ?? [] : [])
const barMax = computed(() => Math.max(1, ...bars.value.map(item => item.value)))
const timestamp = computed(() => props.state.at ? new Date(props.state.at).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '')
</script>

<template>
  <div class="dashboard-data" :class="`dashboard-data--${size}`" @click.stop @keydown.stop>
    <div v-if="metrics.length" class="dashboard-data__metrics">
      <div v-for="metric in metrics" :key="metric.key" class="dashboard-data__metric">
        <span>{{ metric.label }}</span>
        <strong :class="metric.tone ? `tone-${metric.tone}` : undefined">{{ metric.value }}</strong>
        <div v-if="metric.progress !== undefined && showChart !== false" class="dashboard-data__track" role="progressbar" :aria-label="metric.label" :aria-valuenow="metric.progress" :aria-valuemin="0" :aria-valuemax="100">
          <span :style="{ width: `${metric.progress}%` }" />
        </div>
      </div>
    </div>
    <div v-if="bars.length" class="dashboard-data__bars">
      <span>记录数最多的类型（最多5类）</span>
      <div v-for="bar in bars" :key="bar.label" class="dashboard-data__bar">
        <span>{{ bar.label }}</span>
        <div class="dashboard-data__track">
          <span :style="{ width: `${bar.value / barMax * 100}%` }" />
        </div>
        <strong>{{ bar.value.toLocaleString('zh-CN') }}</strong>
      </div>
    </div>
    <div class="dashboard-data__foot">
      <span role="status">{{ state.status === 'loading' ? '读取中…' : state.status === 'error' ? (state.snapshot ? '刷新失败 · 上次快照' : '暂时无法获取') : state.status === 'stale' ? '已过期 · 上次快照' : state.status === 'ready' ? `快照 ${timestamp}` : source === 'statistics' ? '按需读取统计摘要' : '尚未读取' }}</span>
      <el-button v-if="!preview" text size="small" :disabled="state.status === 'loading' || state.status === 'ready'" @click="emit('refresh')">
        {{ state.status === 'idle' ? '读取' : '刷新' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.dashboard-data { display: grid; gap: var(--mrr-space-3); min-width: 0; }
.dashboard-data__metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-3); }
.dashboard-data--small .dashboard-data__metrics { grid-template-columns: minmax(0, 1fr); }
.dashboard-data__metric { display: grid; gap: var(--mrr-space-1); min-width: 0; }

.dashboard-data__metric > span,
.dashboard-data__bars,
.dashboard-data__foot { font-size: var(--mrr-font-size-xs); color: var(--mrr-muted-foreground); }
.dashboard-data__metric > strong { font-size: var(--mrr-font-size-title-compact); font-variant-numeric: tabular-nums; line-height: 1.3; overflow-wrap: anywhere; }
.dashboard-data__metric > strong.tone-success { color: var(--mrr-tone-success); }
.dashboard-data__metric > strong.tone-warning { color: var(--mrr-tone-warning); }
.dashboard-data__metric > strong.tone-danger { color: var(--mrr-tone-danger); }
.dashboard-data__track { height: 5px; overflow: hidden; background: var(--mrr-muted); border-radius: var(--mrr-radius-md); }
.dashboard-data__track > span { display: block; height: 100%; background: var(--mrr-primary); border-radius: inherit; }
.dashboard-data__bars { display: grid; gap: var(--mrr-space-2); }
.dashboard-data__bar { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: var(--mrr-space-2); align-items: center; }
.dashboard-data__bar > span { overflow-wrap: anywhere; }
.dashboard-data__bar strong { font-variant-numeric: tabular-nums; }
.dashboard-data__foot { display: flex; flex-wrap: wrap; gap: var(--mrr-space-2); align-items: center; justify-content: space-between; }

@media (pointer: coarse) { .dashboard-data__foot :deep(button) { min-height: 44px; } }
</style>

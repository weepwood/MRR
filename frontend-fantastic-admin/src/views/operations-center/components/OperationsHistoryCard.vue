<script setup lang="ts">
import type { OperationsHistory, OperationsHistorySample } from '@/api/modules/operations'
import type { ECOption } from '@/plugins/echarts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getOperationsHistory } from '@/api/modules/operations'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrChart from '@/components/charts/MrrChart.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'

const hours = ref<24 | 168>(24)
const history = ref<OperationsHistory>()
const loading = ref(false)
const failed = ref(false)
let generation = 0
let controller: AbortController | undefined
const allowed = computed(() => hasPermission('system:read'))

function clear() {
  generation++
  controller?.abort()
  controller = undefined
  history.value = undefined
  failed.value = false
  loading.value = false
}

async function load() {
  clear()
  if (!allowed.value) { return }
  const current = generation
  controller = new AbortController()
  loading.value = true
  try {
    const result = await getOperationsHistory(hours.value, controller.signal)
    if (current === generation) {
      if (result.hours !== hours.value || result.bucketSeconds !== 300 || result.samples.length > 2017) { throw new Error('Invalid history') }
      history.value = result
    }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { loading.value = false }
  }
}

watch([hours, allowed], () => void load(), { immediate: true, flush: 'sync' })
watch(() => useUserStore().token, clear, { flush: 'sync' })
onBeforeUnmount(clear)

const bucketMs = 300000
const slots = computed(() => {
  const data = history.value
  if (!data) { return [] }
  const start = Math.floor(Date.parse(data.from) / bucketMs) * bucketMs
  const end = Math.floor(Date.parse(data.to) / bucketMs) * bucketMs
  const count = Math.round((end - start) / bucketMs) + 1
  if (!Number.isFinite(count) || count < 1 || count > 2017) { return [] }
  const observed = new Map(data.samples.map(row => [Math.floor(Date.parse(row.sampledAt) / bucketMs) * bucketMs, row]))
  return Array.from({ length: count }, (_, i) => ({ at: start + i * bucketMs, sample: observed.get(start + i * bucketMs) }))
})
const latest = computed(() => history.value?.samples.at(-1))
const changes = computed(() => {
  const values = slots.value
  const events: { at: number, message: string }[] = []
  for (let i = 1; i < values.length; i++) {
    const before = values[i - 1].sample
    const after = values[i].sample
    if (!before || !after) { continue }
    const messages: string[] = []
    if (before.mode !== after.mode) { messages.push(`${modeLabel(before.mode)} → ${modeLabel(after.mode)}`) }
    if (before.storageOpenNodes != null && after.storageOpenNodes != null && before.storageOpenNodes !== after.storageOpenNodes) {
      messages.push(`熔断冷却节点 ${before.storageOpenNodes} → ${after.storageOpenNodes}`)
    }
    if (messages.length) { events.push({ at: Date.parse(after.sampledAt), message: messages.join('；') }) }
  }
  return events.reverse().slice(0, 30)
})

function modeLabel(mode: OperationsHistorySample['mode']) {
  return { READ_WRITE: '正常读写', READ_ONLY_DEGRADED: '自动降级', READ_ONLY_MAINTENANCE: '主动维护' }[mode]
}

function chart(series: { name: string, value: (row: OperationsHistorySample) => number | null }[]): ECOption {
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { top: 44, left: 12, right: 20, bottom: 36, containLabel: true },
    xAxis: { type: 'time', axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', min: 0 },
    dataZoom: [{ type: 'inside' }],
    series: series.map(item => ({ name: item.name, type: 'line', showSymbol: slots.value.length < 20, connectNulls: false, data: slots.value.map(slot => [slot.at, slot.sample ? item.value(slot.sample) : null]) })),
  }
}
const resourceChart = computed(() => chart([
  { name: 'JVM 已用堆 (MiB)', value: row => Math.round(row.heapUsedBytes / 1048576) },
  { name: 'JVM 堆上限 (MiB)', value: row => Math.round(row.heapMaxBytes / 1048576) },
]))
const capacityChart = computed(() => chart([
  { name: '扫描表及索引 (GiB)', value: row => row.scanTotalBytes == null ? null : Math.round(row.scanTotalBytes / 1073741824 * 100) / 100 },
]))
const riskChart = computed(() => chart([
  { name: '关键检查失败', value: row => row.criticalFailures },
  { name: '熔断冷却节点', value: row => row.storageOpenNodes },
  { name: '存储等待', value: row => row.storageWaiting },
]))
</script>

<template>
  <MrrSectionCard v-if="allowed" title="运行历史与容量趋势" description="5 分钟最多一条样本，保留 7 天；缺失区间不补零，不代表持续可用性或完整故障审计。">
    <div class="history-toolbar">
      <el-radio-group v-model="hours" aria-label="历史时间范围">
        <el-radio-button :value="24">
          24 小时
        </el-radio-button>
        <el-radio-button :value="168">
          7 天
        </el-radio-button>
      </el-radio-group>
      <el-button :loading="loading" @click="load">
        刷新历史
      </el-button>
    </div>
    <AppError v-if="failed" message="历史采样读取失败，请重试" @retry="load" />
    <el-skeleton v-else-if="loading" :rows="3" animated />
    <AppEmpty v-else-if="history && !history.enabled" size="compact" description="持久历史采集尚未启用" />
    <AppEmpty v-else-if="!history?.samples.length" size="compact" description="当前范围暂无历史采样" />
    <template v-else>
      <p class="history-note">
        已取得 {{ history.samples.length }} / {{ slots.length }} 个时间桶 · 最近采样 {{ formatDateTime(latest?.sampledAt) }} · 依赖检查 {{ formatDateTime(latest?.readinessCheckedAt) }}
      </p>
      <div class="history-charts">
        <MrrChart :option="resourceChart" :height="235" aria-label="JVM 堆内存历史" />
        <MrrChart :option="riskChart" :height="235" aria-label="运维风险历史" />
        <MrrChart :option="capacityChart" :height="235" aria-label="扫描表容量历史" />
      </div>
      <h3>相邻采样状态变化</h3>
      <AppEmpty v-if="!changes.length" size="compact" description="未观察到相邻采样的状态变化；不排除缺口内发生故障" />
      <ul v-else class="history-events">
        <li v-for="event in changes" :key="event.at">
          <time>{{ formatDateTime(new Date(event.at).toISOString()) }}</time>
          <span>{{ event.message }}</span>
        </li>
      </ul>
    </template>
  </MrrSectionCard>
</template>

<style scoped>
.history-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
}

.history-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.history-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
  gap: var(--mrr-space-3);
}

.history-events {
  display: grid;
  gap: var(--mrr-space-2);
  padding: 0;
  list-style: none;
}

.history-events li {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  padding: var(--mrr-space-2);
  border-bottom: 1px solid var(--el-border-color-light);
}

.history-events time {
  color: var(--el-text-color-secondary);
}
</style>

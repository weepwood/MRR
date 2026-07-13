<script setup lang="ts">
import type {
  ActuatorMetric,
  GcStatItem,
  GcStats,
  HealthInfo,
  MemoryInfo,
  RuntimeInfo,
  SystemInfo,
  ThreadStats,
} from '@/api/types'
import { useIntervalFn } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getMetric } from '@/api/modules/actuator'
import {
  getSystemHealth,
  getSystemInfo,
  getSystemMemory,
  getSystemOverview,
  getSystemProperties,
  getSystemRuntime,
} from '@/api/modules/system'

defineOptions({ name: 'MonitoringPage' })

const loading = ref(false)
const autoRefreshing = ref(false)
const autoRefresh = ref(true)
const lastRefresh = ref('')
const healthStatus = ref<HealthInfo>({})
const runtimeInfo = ref<RuntimeInfo>({})
const memoryInfo = ref<MemoryInfo>({ heap: {}, nonHeap: {} })
const systemInfo = ref<SystemInfo>({ application: {}, jvm: {}, operatingSystem: {} })
const properties = ref<Record<string, string>>({})
const gcStats = ref<GcStats>({})
const threadStats = ref<ThreadStats>({})
const hikariActive = ref<number | null>(null)
const hikariIdle = ref<number | null>(null)
const hikariPending = ref<number | null>(null)

// 过滤 GC 统计中的非聚合条目（排除 totalCollections、totalTimeMs）
const gcItems = computed<GcStatItem[]>(() => {
  const items: GcStatItem[] = []
  for (const [key, value] of Object.entries(gcStats.value)) {
    if (key.startsWith('total')) { continue }
    if (typeof value === 'object' && value !== null) {
      items.push(value as GcStatItem)
    }
  }
  return items
})

// ---- computed ----
const healthTone = computed(() => {
  const value = String(healthStatus.value.status || '').toUpperCase()
  if (value === 'UP') { return 'success' }
  if (value === 'WARNING') { return 'warning' }
  return 'danger'
})

const memoryPercent = computed(() => Number.parseFloat(String(memoryInfo.value.usagePercent || '0').replace('%', '')) || 0)

const totalGcCount = computed(() => Number(gcStats.value.totalCollections || 0))
const totalGcTime = computed(() => {
  const ms = Number(gcStats.value.totalTimeMs || 0)
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
})

const hikariMax = 20

const summaryCards = computed(() => [
  { label: '健康状态', value: healthStatus.value.status || 'UNKNOWN', note: '系统健康检查接口返回', tone: healthTone.value === 'success' ? 'green' : healthTone.value === 'warning' ? 'amber' : 'danger', icon: 'i-ant-design:heart-twotone' },
  { label: '运行时长', value: runtimeInfo.value.uptimeFormatted || '-', note: 'JVM 进程启动至今', tone: 'blue', icon: 'i-ant-design:clock-circle-twotone' },
  { label: '堆内存使用率', value: memoryInfo.value.usagePercent || '-', note: '来自系统内存指标', tone: 'violet', icon: 'i-ant-design:database-twotone' },
  { label: 'GC 累计', value: `${totalGcCount.value} 次`, note: `累计耗时 ${totalGcTime.value}`, tone: 'amber', icon: 'i-ant-design:sync-outlined' },
  { label: '线程', value: `${threadStats.value.currentCount ?? 0}/${threadStats.value.peakCount ?? 0}`, note: '当前 / 历史峰值', tone: 'teal', icon: 'i-ant-design:apartment-outlined' },
])

// ---- data loading ----
async function loadOverview() {
  try {
    const [overviewRes, healthRes, runtimeRes, memoryRes, infoRes, propertiesRes] = await Promise.all([
      getSystemOverview(),
      getSystemHealth(),
      getSystemRuntime(),
      getSystemMemory(),
      getSystemInfo(),
      getSystemProperties(),
    ])
    const overview = overviewRes.data ?? {}
    healthStatus.value = healthRes.data ?? (overview.health as HealthInfo) ?? {}
    runtimeInfo.value = runtimeRes.data ?? (overview.runtime as RuntimeInfo) ?? {}
    memoryInfo.value = memoryRes.data ?? (overview.memory as MemoryInfo) ?? {}
    systemInfo.value = infoRes.data ?? (overview.info as SystemInfo) ?? ({ application: {}, jvm: {}, operatingSystem: {} })
    properties.value = propertiesRes.data ?? (overview.properties as Record<string, string>) ?? {}
    gcStats.value = (overview.gc ?? {}) as GcStats
    threadStats.value = (overview.threads ?? {}) as ThreadStats
  }
  catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '监控信息加载失败'
    ElMessage.error(msg)
  }
}

function extractMetricValue(metric: ActuatorMetric): number | null {
  return metric?.measurements?.[0]?.value ?? null
}

async function loadActuatorMetrics() {
  try {
    const [activeRes, idleRes, pendingRes] = await Promise.allSettled([
      getMetric('hikaricp.connections.active'),
      getMetric('hikaricp.connections.idle'),
      getMetric('hikaricp.connections.pending'),
    ])
    if (activeRes.status === 'fulfilled') {
      hikariActive.value = extractMetricValue(activeRes.value.data ?? {})
    }
    if (idleRes.status === 'fulfilled') {
      hikariIdle.value = extractMetricValue(idleRes.value.data ?? {})
    }
    if (pendingRes.status === 'fulfilled') {
      hikariPending.value = extractMetricValue(pendingRes.value.data ?? {})
    }
  }
  catch { /* actuator 不可用时静默 */ }
}

async function loadAll(showLoading = true) {
  if (showLoading) { loading.value = true }
  else { autoRefreshing.value = true }
  await Promise.all([loadOverview(), loadActuatorMetrics()])
  lastRefresh.value = new Date().toLocaleTimeString()
  loading.value = false
  autoRefreshing.value = false
}

// ---- auto-refresh ----
const { pause, resume } = useIntervalFn(() => {
  if (autoRefresh.value) {
    loadAll(false)
  }
}, 10000)

// 页面可见性门控：隐藏时暂停轮询，可见时恢复，避免后台标签页浪费资源
function handleVisibilityChange() {
  if (document.hidden) {
    pause()
  }
  else if (autoRefresh.value) {
    resume()
  }
}

onMounted(() => {
  loadAll()
  resume()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  pause()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div class="header-copy">
        <p class="eyebrow">
          System Monitor
        </p>
        <h2>系统监控</h2>
        <p class="subtitle" aria-live="polite">
          {{ autoRefresh ? '每 10 秒自动刷新' : '自动刷新已暂停' }} · 上次刷新: {{ lastRefresh || '--' }}
        </p>
      </div>
      <div class="monitor-actions">
        <span class="health-pill" :class="healthTone">
          <i class="status-dot" aria-hidden="true" />
          {{ healthStatus.status || 'UNKNOWN' }}
        </span>
        <div class="refresh-actions">
          <el-button size="small" :type="autoRefresh ? 'primary' : 'default'" @click="autoRefresh = !autoRefresh">
            {{ autoRefresh ? '暂停刷新' : '恢复刷新' }}
          </el-button>
          <el-button size="small" type="primary" :loading="loading" @click="loadAll()">
            立即刷新
          </el-button>
        </div>
      </div>
    </div>

    <section class="mrr-metric-grid mrr-metric-grid--compact">
      <el-card
        v-for="item in summaryCards"
        :key="item.label"
        shadow="never"
        class="mrr-metric-card"
        :class="`mrr-metric-card--${item.tone}`"
      >
        <div class="mrr-metric-card__icon">
          <i :class="item.icon" />
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">{{ item.label }}</span>
          <strong
            class="mrr-metric-card__value"
            :class="item.label === '健康状态' ? healthTone : ''"
          >
            {{ item.value }}
          </strong>
          <p class="mrr-metric-card__note">
            {{ item.note }}
          </p>
        </div>
      </el-card>
    </section>

    <el-row :gutter="20" class="metric-row">
      <el-col :xs="24" :md="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-title">
              JVM 与应用信息
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="应用名称">
              {{ systemInfo.application?.name || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="启动时间">
              {{ systemInfo.application?.startTime || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="运行时长">
              {{ systemInfo.application?.runTime || runtimeInfo.uptimeFormatted || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Java 版本">
              {{ systemInfo.jvm?.javaVersion || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Java Vendor">
              {{ systemInfo.jvm?.javaVendor || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="可用处理器">
              {{ systemInfo.jvm?.availableProcessors || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="系统负载">
              {{ systemInfo.operatingSystem?.systemLoadAverage || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-title">
              内存概览
            </div>
          </template>
          <div class="metric">
            <div class="metric-top">
              <span>堆内存使用率</span>
              <strong>{{ memoryInfo.usagePercent || '0%' }}</strong>
            </div>
            <el-progress :percentage="memoryPercent" :stroke-width="12" :status="memoryPercent > 90 ? 'exception' : memoryPercent > 70 ? 'warning' : 'success'" />
          </div>
          <el-descriptions :column="1" border class="details-list memory-details">
            <el-descriptions-item label="Heap Used">
              {{ memoryInfo.heap?.used || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Heap Committed">
              {{ memoryInfo.heap?.committed || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Heap Max">
              {{ memoryInfo.heap?.max || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Non-Heap Used">
              {{ memoryInfo.nonHeap?.used || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Non-Heap Committed">
              {{ memoryInfo.nonHeap?.committed || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="metric-row">
      <el-col :xs="24" :md="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-title">
              GC 统计
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="累计收集次数">
              {{ totalGcCount }}
            </el-descriptions-item>
            <el-descriptions-item label="累计耗时">
              {{ totalGcTime }}
            </el-descriptions-item>
            <el-descriptions-item v-for="(item, idx) in gcItems" :key="idx" :label="item.name || String(idx)">
              {{ item.count ?? 0 }} 次 / {{ item.timeMs ?? 0 }}ms
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="monitor-card" shadow="never">
          <template #header>
            <div class="card-title">
              线程 &amp; 连接池
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="当前线程数">
              {{ threadStats.currentCount || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="守护线程">
              {{ threadStats.daemonCount || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="历史峰值">
              {{ threadStats.peakCount || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="累计创建">
              {{ threadStats.totalStarted || '-' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-descriptions :column="1" border class="details-list connection-details">
            <el-descriptions-item label="DB 活跃连接">
              {{ hikariActive !== null ? `${hikariActive}/${hikariMax}` : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="DB 空闲连接">
              {{ hikariIdle !== null ? hikariIdle : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="DB 等待连接">
              {{ hikariPending !== null ? hikariPending : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="monitor-card properties-card" shadow="never">
      <template #header>
        <div class="card-title">
          系统属性
        </div>
      </template>
      <div class="properties-grid">
        <article v-for="(value, key) in properties" :key="key" class="property-item">
          <div class="property-key">
            {{ key }}
          </div>
          <div class="property-value" :title="value">
            {{ value }}
          </div>
        </article>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding: 4px 2px;
}

.header-copy {
  min-width: 0;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: clamp(24px, 3vw, 30px);
  letter-spacing: -0.03em;
  text-wrap: balance;
}

.subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.monitor-actions,
.refresh-actions,
.health-pill,
.metric-top {
  display: flex;
  align-items: center;
}

.monitor-actions {
  flex: none;
  gap: 12px;
}

.refresh-actions {
  gap: 8px;
}

.health-pill {
  gap: 6px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid currentcolor;
  border-radius: 999px;
}

.health-pill.success,
.mrr-metric-card__value.success {
  color: #16a34a;
}

.health-pill.warning,
.mrr-metric-card__value.warning {
  color: #d97706;
}

.health-pill.danger,
.mrr-metric-card__value.danger {
  color: #dc2626;
}

.status-dot {
  width: 7px;
  height: 7px;
  background: currentcolor;
  border-radius: 50%;
}

.metric-row {
  row-gap: 20px;
}

.monitor-card {
  height: 100%;
  border-color: var(--divider);
  border-radius: 14px;
}

.monitor-card :deep(.el-card__header) {
  padding: 14px 18px;
  background: var(--surface-alt);
  border-bottom-color: var(--divider);
}

.monitor-card :deep(.el-card__body) {
  padding: 18px;
}

.card-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.metric {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--surface-alt);
  border: 1px solid var(--divider);
  border-radius: 10px;
}

.metric-top {
  gap: 12px;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-secondary);
}

.metric-top strong {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}

.details-list {
  margin-top: 14px;
}

.connection-details {
  margin-top: 12px;
}

.properties-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.property-item {
  padding: 14px;
  content-visibility: auto;
  overflow: hidden;
  background: var(--surface-alt);
  border: 1px solid var(--divider);
  border-radius: 10px;
}

.property-value {
  margin-top: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

@media (width <= 720px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .monitor-actions,
  .refresh-actions {
    width: 100%;
  }

  .monitor-actions {
    justify-content: space-between;
  }

  .refresh-actions :deep(.el-button) {
    flex: 1;
  }

  .monitor-card :deep(.el-card__body) {
    padding: 14px;
  }

  .properties-grid {
    grid-template-columns: 1fr;
  }
}
</style>

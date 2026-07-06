<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getMetric } from '@/api/modules/actuator'
import { getSystemHealth, getSystemInfo, getSystemMemory, getSystemOverview, getSystemProperties, getSystemRuntime } from '@/api/modules/system'

defineOptions({ name: 'MonitoringPage' })

const loading = ref(false)
const autoRefreshing = ref(false)
const autoRefresh = ref(true)
const lastRefresh = ref('')
const healthStatus = ref<any>({})
const runtimeInfo = ref<any>({})
const memoryInfo = ref<any>({ heap: {}, nonHeap: {} })
const systemInfo = ref<any>({ application: {}, jvm: {}, operatingSystem: {} })
const properties = ref<Record<string, string>>({})
const gcStats = ref<any>({})
const threadStats = ref<any>({})
const hikariActive = ref<number | null>(null)
const hikariIdle = ref<number | null>(null)
const hikariPending = ref<number | null>(null)

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
  { label: '健康状态', value: healthStatus.value.status || 'UNKNOWN', note: '系统健康检查接口返回' },
  { label: '运行时长', value: runtimeInfo.value.uptimeFormatted || '-', note: 'JVM 进程启动至今' },
  { label: '堆内存使用率', value: memoryInfo.value.usagePercent || '-', note: '来自系统内存指标' },
  { label: 'GC 累计', value: `${totalGcCount.value} 次`, note: `累计耗时 ${totalGcTime.value}` },
  { label: '线程', value: `${threadStats.value.currentCount || 0}/${threadStats.value.peakCount || 0}`, note: '当前 / 历史峰值' },
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
    const overview = overviewRes.data || {}
    healthStatus.value = healthRes.data || overview.health || {}
    runtimeInfo.value = runtimeRes.data || overview.runtime || {}
    memoryInfo.value = memoryRes.data || overview.memory || {}
    systemInfo.value = infoRes.data || overview.info || { application: {}, jvm: {}, operatingSystem: {} }
    properties.value = propertiesRes.data || overview.properties || {}
    gcStats.value = overview.gc || {}
    threadStats.value = overview.threads || {}
  }
  catch (error: any) {
    ElMessage.error(error?.message || '监控信息加载失败')
  }
}

async function loadActuatorMetrics() {
  try {
    const [activeRes, idleRes, pendingRes] = await Promise.allSettled([
      getMetric('hikaricp.connections.active'),
      getMetric('hikaricp.connections.idle'),
      getMetric('hikaricp.connections.pending'),
    ])
    if (activeRes.status === 'fulfilled') {
      // actuator 返回 { name, measurements: [{value}] }，无 code/data 包装
      const m = activeRes.value as any
      hikariActive.value = m?.measurements?.[0]?.value ?? null
    }
    if (idleRes.status === 'fulfilled') {
      const m = idleRes.value as any
      hikariIdle.value = m?.measurements?.[0]?.value ?? null
    }
    if (pendingRes.status === 'fulfilled') {
      const m = pendingRes.value as any
      hikariPending.value = m?.measurements?.[0]?.value ?? null
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
      <div>
        <p class="eyebrow">
          System Monitor
        </p>
        <h2>系统监控</h2>
        <p class="subtitle">
          自动每 10 秒刷新 · 上次刷新: {{ lastRefresh || '--' }}
          <span v-if="!autoRefresh" style="color: #d97706;">（已暂停）</span>
        </p>
      </div>
      <div class="flex gap-2">
        <el-button size="small" :type="autoRefresh ? 'primary' : 'default'" @click="autoRefresh = !autoRefresh">
          {{ autoRefresh ? '暂停刷新' : '恢复刷新' }}
        </el-button>
        <el-button size="small" type="primary" :loading="loading" @click="loadAll()">
          手动刷新
        </el-button>
      </div>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value" :class="item.label === '健康状态' ? healthTone : ''">
          {{ item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            JVM 与应用信息
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
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            内存概览
          </template>
          <div class="metric">
            <div class="metric-top">
              <span>堆内存使用率</span>
              <strong>{{ memoryInfo.usagePercent || '0%' }}</strong>
            </div>
            <el-progress :percentage="memoryPercent" :stroke-width="12" :status="memoryPercent > 90 ? 'exception' : memoryPercent > 70 ? 'warning' : 'success'" />
          </div>
          <el-descriptions :column="1" border style="margin-top: 16px;">
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

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            GC 统计
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="累计收集次数">
              {{ totalGcCount }}
            </el-descriptions-item>
            <el-descriptions-item label="累计耗时">
              {{ totalGcTime }}
            </el-descriptions-item>
            <el-descriptions-item v-for="(v, k) in gcStats" v-show="typeof v === 'object'" :key="k" :label="(v as any).name || k">
              {{ (v as any).count }} 次 / {{ (v as any).timeMs }}ms
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            线程 &amp; 连接池
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
          <el-descriptions :column="1" border style="margin-top: 12px;">
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

    <el-card shadow="never">
      <template #header>
        系统属性
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
.page-shell { display: grid; gap: 20px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; font-size: 13px; color: var(--text-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; }
.summary-label { font-size: 12px; color: var(--text-secondary); }
.summary-value { margin-top: 8px; font-size: 22px; font-weight: 800; color: var(--text-primary); }
.summary-note { margin-top: 8px; font-size: 12px; color: var(--text-secondary); }
.summary-value.success { color: #16a34a; }
.summary-value.warning { color: #d97706; }
.summary-value.danger { color: #dc2626; }
.metric { display: grid; gap: 8px; }
.metric-top { display: flex; gap: 12px; align-items: center; justify-content: space-between; }
.properties-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.property-item { padding: 14px; background: var(--surface); border: 1px solid rgb(148 163 184 / 16%); border-radius: 14px; }
.property-key { font-size: 12px; color: var(--text-secondary); }
.property-value { margin-top: 6px; color: var(--text-primary); word-break: break-all; }
.flex { display: flex; }
.gap-2 { gap: 8px; }
</style>

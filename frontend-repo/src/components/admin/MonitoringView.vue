<template>
  <div class="pmr-page admin-tool-view">
    <section class="pmr-page-header">
      <div>
        <p class="module-eyebrow">System Monitor</p>
        <h2 class="pmr-page-title">系统监控</h2>
        <p class="pmr-page-subtitle">查看 CPU、内存、磁盘和网络的运行状态。</p>
      </div>
      <div class="pmr-toolbar-actions">
        <el-button type="primary" :loading="loading" @click="refreshData">刷新监控</el-button>
      </div>
    </section>

    <section class="summary-grid">
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">健康状态</div>
        <div class="summary-value" :class="healthTone">{{ healthStatus.status || 'UNKNOWN' }}</div>
        <div class="summary-note">接口：/v1/system/health</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">运行时长</div>
        <div class="summary-value">{{ systemInfo.uptimeFormatted || '-' }}</div>
        <div class="summary-note">接口：/v1/system/runtime</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">内存占用</div>
        <div class="summary-value">{{ memoryInfo.usagePercent || '-' }}</div>
        <div class="summary-note">接口：/v1/system/memory</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">网络状态</div>
        <div class="summary-value" :class="networkTone">{{ networkInfo.label }}</div>
        <div class="summary-note">{{ networkInfo.detail }}</div>
      </el-card>
    </section>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">CPU 与负载</h3>
            <p class="pmr-panel-subtitle">根据系统负载和可用处理器做出简易负载指示。</p>
          </div>
          <span class="pmr-badge">{{ cpuSummary.label }}</span>
        </div>
      </template>

      <div class="metrics-grid">
        <article class="metric-card">
          <div class="metric-label">可用处理器</div>
          <div class="metric-value">{{ cpuSummary.processors }}</div>
          <div class="metric-note">来自 /v1/system/info</div>
        </article>
        <article class="metric-card">
          <div class="metric-label">系统负载</div>
          <div class="metric-value">{{ cpuSummary.loadAverage }}</div>
          <div class="metric-note">来自 /v1/system/info</div>
        </article>
        <article class="metric-card">
          <div class="metric-label">负载比例</div>
          <div class="metric-value">{{ cpuSummary.loadPercent }}%</div>
          <div class="metric-note">按负载 / 处理器数估算</div>
        </article>
      </div>

      <div class="bar-group">
        <div class="bar-row">
          <div class="bar-head">
            <span>CPU 估算负载</span>
            <strong>{{ cpuSummary.loadPercent }}%</strong>
          </div>
          <div class="bar-track">
            <span :style="{ width: `${cpuSummary.loadPercent}%` }"></span>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">内存详情</h3>
            <p class="pmr-panel-subtitle">同时查看堆内存、非堆内存和历史趋势。</p>
          </div>
          <span class="pmr-badge">{{ memoryInfo.usagePercent || '0%' }}</span>
        </div>
      </template>

      <div class="memory-layout">
        <article class="memory-overview">
          <div class="memory-number" :class="memoryTone">{{ memoryInfo.usagePercent || '-' }}</div>
          <div class="memory-meta">当前堆内存使用率</div>
          <div class="memory-bar">
            <span :style="{ width: `${memoryPercentValue}%` }"></span>
          </div>
        </article>

        <div class="memory-cards">
          <article class="memory-card">
            <h4>Heap</h4>
            <p>Init: {{ memoryInfo.heap?.init || '-' }}</p>
            <p>Used: {{ memoryInfo.heap?.used || '-' }}</p>
            <p>Committed: {{ memoryInfo.heap?.committed || '-' }}</p>
            <p>Max: {{ memoryInfo.heap?.max || '-' }}</p>
          </article>
          <article class="memory-card">
            <h4>Non-Heap</h4>
            <p>Init: {{ memoryInfo.nonHeap?.init || '-' }}</p>
            <p>Used: {{ memoryInfo.nonHeap?.used || '-' }}</p>
            <p>Committed: {{ memoryInfo.nonHeap?.committed || '-' }}</p>
            <p>Max: {{ memoryInfo.nonHeap?.max || '-' }}</p>
          </article>
        </div>
      </div>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">磁盘与系统信息</h3>
            <p class="pmr-panel-subtitle">展示启动参数、操作系统和磁盘可用空间。</p>
          </div>
        </div>
      </template>

      <div class="detail-grid">
        <article class="detail-card">
          <h4>系统概览</h4>
          <p>应用：{{ systemDetail.application?.name || '-' }}</p>
          <p>启动时间：{{ systemDetail.application?.startTime || '-' }}</p>
          <p>运行时长：{{ systemDetail.application?.runTime || '-' }}</p>
          <p>Java：{{ systemDetail.jvm?.javaVersion || '-' }}</p>
          <p>供应商：{{ systemDetail.jvm?.javaVendor || '-' }}</p>
          <p>Java Home：{{ systemDetail.jvm?.javaHome || '-' }}</p>
        </article>

        <article class="detail-card">
          <h4>磁盘空间</h4>
          <p>已用：{{ diskInfo.used }}</p>
          <p>总量：{{ diskInfo.quota }}</p>
          <p>占用率：{{ diskInfo.usagePercent }}</p>
          <p>来源：浏览器 Storage API</p>
        </article>

        <article class="detail-card">
          <h4>操作系统</h4>
          <p>名称：{{ systemDetail.operatingSystem?.name || '-' }}</p>
          <p>架构：{{ systemDetail.operatingSystem?.arch || '-' }}</p>
          <p>版本：{{ systemDetail.operatingSystem?.version || '-' }}</p>
          <p>处理器：{{ systemDetail.jvm?.availableProcessors || '-' }}</p>
        </article>
      </div>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">系统属性</h3>
            <p class="pmr-panel-subtitle">用于快速定位当前运行环境和编码设置。</p>
          </div>
        </div>
      </template>

      <div class="props-grid">
        <article v-for="item in propItems" :key="item.key" class="prop-item">
          <div class="prop-label">{{ item.label }}</div>
          <div class="prop-value" :title="systemProperties[item.key]">{{ systemProperties[item.key] || '-' }}</div>
        </article>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getSystemHealth, getSystemInfo, getSystemMemory, getSystemProperties, getSystemRuntime } from '@/utils/api'

const loading = ref(false)
const systemInfo = ref({})
const systemProperties = ref({})
const memoryInfo = ref({ heap: {}, nonHeap: {} })
const systemDetail = ref({ application: {}, jvm: {}, operatingSystem: {} })
const healthStatus = ref({})
const networkInfo = ref({ label: '未知', detail: '等待浏览器网络信息' })
const diskInfo = ref({ used: '-', quota: '-', usagePercent: '-' })

const propItems = [
  { key: 'os.name', label: '操作系统' },
  { key: 'os.arch', label: 'CPU 架构' },
  { key: 'os.version', label: '系统版本' },
  { key: 'java.version', label: 'Java 版本' },
  { key: 'java.vendor', label: 'Java 供应商' },
  { key: 'java.home', label: 'Java Home' },
  { key: 'user.name', label: '用户名' },
  { key: 'user.dir', label: '工作目录' },
  { key: 'user.home', label: '用户目录' },
  { key: 'file.encoding', label: '文件编码' }
]

const memoryPercentValue = computed(() => {
  const value = Number.parseFloat(String(memoryInfo.value.usagePercent || '').replace('%', ''))
  return Number.isNaN(value) ? 0 : Math.max(0, Math.min(100, value))
})

const memoryTone = computed(() => {
  const value = memoryPercentValue.value
  if (value >= 85) return 'danger'
  if (value >= 70) return 'warning'
  return 'success'
})

const healthTone = computed(() => {
  const value = String(healthStatus.value.status || '').toUpperCase()
  if (value === 'UP') return 'success'
  if (value === 'WARNING') return 'warning'
  return 'danger'
})

const networkTone = computed(() => {
  if (networkInfo.value.label === '在线') return 'success'
  if (networkInfo.value.label === '离线') return 'danger'
  return 'warning'
})

const cpuSummary = computed(() => {
  const processors = Number(systemDetail.value.jvm?.availableProcessors || navigator.hardwareConcurrency || 0)
  const rawLoadAverage = Number(systemDetail.value.operatingSystem?.systemLoadAverage || systemDetail.value.jvm?.systemLoadAverage || 0)
  const loadAverage = Number.isFinite(rawLoadAverage) && rawLoadAverage >= 0 ? rawLoadAverage : 0
  const loadPercent = processors > 0 ? Math.min(100, Math.round((loadAverage / processors) * 100)) : 0
  return {
    processors: processors || '-',
    loadAverage: loadAverage > 0 ? loadAverage.toFixed(2) : '-',
    loadPercent,
    label: loadPercent >= 75 ? '高负载' : loadPercent >= 50 ? '关注' : '稳定'
  }
})

const pickPayload = (response) => (response?.data?.code === 200 ? response.data.data || {} : response?.data || {})

const formatBytes = (bytes) => {
  const value = Number(bytes || 0)
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let size = value
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(2)} ${units[index]}`
}

const updateNetworkInfo = () => {
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection
  const online = navigator.onLine
  networkInfo.value = {
    label: online ? '在线' : '离线',
    detail: connection
      ? `${connection.effectiveType || 'unknown'} · ${connection.downlink || '-'} Mbps · RTT ${connection.rtt || '-'} ms`
      : online
        ? '浏览器未提供详细网络信息'
        : '当前设备未联网'
  }
}

const updateDiskInfo = async () => {
  try {
    if (!navigator.storage?.estimate) {
      diskInfo.value = { used: '-', quota: '-', usagePercent: '不支持' }
      return
    }
    const estimate = await navigator.storage.estimate()
    const used = Number(estimate.usage || 0)
    const quota = Number(estimate.quota || 0)
    const usagePercent = quota > 0 ? `${((used * 100) / quota).toFixed(2)}%` : '0%'
    diskInfo.value = {
      used: formatBytes(used),
      quota: formatBytes(quota),
      usagePercent
    }
  } catch {
    diskInfo.value = { used: '-', quota: '-', usagePercent: '-' }
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    const [runtimeRes, propRes, memoryRes, infoRes, healthRes] = await Promise.all([
      getSystemRuntime(),
      getSystemProperties(),
      getSystemMemory(),
      getSystemInfo(),
      getSystemHealth()
    ])
    systemInfo.value = { ...systemInfo.value, ...pickPayload(runtimeRes) }
    systemProperties.value = { ...systemProperties.value, ...pickPayload(propRes) }
    memoryInfo.value = { ...memoryInfo.value, ...pickPayload(memoryRes) }
    systemDetail.value = { ...systemDetail.value, ...pickPayload(infoRes) }
    healthStatus.value = { ...healthStatus.value, ...pickPayload(healthRes) }
    updateNetworkInfo()
    await updateDiskInfo()
  } catch (error) {
    ElMessage.error(error?.message || '监控信息加载失败')
  } finally {
    loading.value = false
  }
}

const refreshData = async () => {
  await loadAll()
  ElMessage.success('监控信息已刷新')
}

onMounted(loadAll)
</script>

<style scoped>
.module-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--pmr-color-text-secondary);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  padding: 20px;
}

.summary-label,
.metric-label,
.prop-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--pmr-color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.summary-value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  color: var(--pmr-color-text-primary);
  word-break: break-all;
}

.summary-value.success {
  color: var(--pmr-color-success-500);
}

.summary-value.warning {
  color: var(--pmr-color-warning-500);
}

.summary-value.danger {
  color: var(--pmr-color-danger-500);
}

.summary-note {
  margin-top: 8px;
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
}

.pmr-section {
  margin-top: 20px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-card,
.memory-card,
.detail-card {
  padding: 18px;
  border-radius: var(--pmr-radius-2xl);
  border: 1px solid var(--pmr-color-border-default);
  background: #ffffff;
}

.metric-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 800;
  color: var(--pmr-color-text-primary);
}

.metric-note,
.memory-meta,
.detail-card p {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
}

.bar-group {
  margin-top: 16px;
}

.bar-row,
.bar-head {
  display: grid;
  gap: 8px;
}

.bar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--pmr-color-text-primary);
}

.bar-track,
.memory-bar {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: #edf2fb;
}

.bar-track span,
.memory-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--pmr-color-action-primary), var(--pmr-color-action-primary-pressed));
}

.memory-layout {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: 16px;
}

.memory-overview {
  padding: 22px;
  border-radius: var(--pmr-radius-2xl);
  border: 1px solid var(--pmr-color-border-default);
  background: linear-gradient(180deg, #f9fbff 0%, #ffffff 100%);
}

.memory-number {
  font-size: 36px;
  font-weight: 800;
}

.memory-number.success {
  color: var(--pmr-color-success-500);
}

.memory-number.warning {
  color: var(--pmr-color-warning-500);
}

.memory-number.danger {
  color: var(--pmr-color-danger-500);
}

.memory-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.memory-card h4,
.detail-card h4 {
  margin: 0 0 12px;
  font-size: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.props-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.prop-item {
  padding: 16px;
  border-radius: var(--pmr-radius-2xl);
  border: 1px solid var(--pmr-color-border-default);
  background: #ffffff;
}

.prop-value {
  margin-top: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--pmr-color-text-primary);
}

.pmr-badge.success,
.pmr-badge.warning,
.pmr-badge.danger {
  color: #ffffff;
}

.pmr-badge.success {
  background: var(--pmr-color-success-500);
}

.pmr-badge.warning {
  background: var(--pmr-color-warning-500);
}

.pmr-badge.danger {
  background: var(--pmr-color-danger-500);
}

@media (max-width: 1180px) {
  .summary-grid,
  .metrics-grid,
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .memory-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .summary-grid,
  .metrics-grid,
  .detail-grid,
  .memory-cards {
    grid-template-columns: 1fr;
  }
}
</style>

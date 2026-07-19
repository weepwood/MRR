<script setup lang="ts">
import type { HealthInfo, MemoryInfo, RuntimeInfo, SystemInfo } from '@/api/types'
import type { DatabaseOverview, DataQualityIssue, DataQualitySummary } from '@/api/modules/database-monitoring'
import type { OperationsStatus } from '@/api/modules/system'
import { useIntervalFn } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  getDatabaseOverview,
  getDataQualityIssues,
  getDataQualitySummary,
  runDataQualityChecks,
} from '@/api/modules/database-monitoring'
import {
  getSystemHealth,
  getSystemInfo,
  getSystemMemory,
  getSystemOperations,
  getSystemRuntime,
} from '@/api/modules/system'

defineOptions({ name: 'MonitoringPage' })

const emptyDatabase = (): DatabaseOverview => ({
  database: {},
  connections: {},
  transactions: {},
  contention: {},
  hikari: {},
  tables: [],
})

const loading = ref(false)
const qualityRunning = ref(false)
const autoRefresh = ref(true)
const lastRefresh = ref('')
const healthStatus = ref<HealthInfo>({})
const runtimeInfo = ref<RuntimeInfo>({})
const memoryInfo = ref<MemoryInfo>({ heap: {}, nonHeap: {} })
const systemInfo = ref<SystemInfo>({ application: {}, jvm: {}, operatingSystem: {} })
const operationsStatus = ref<OperationsStatus>({})
const databaseOverview = ref<DatabaseOverview>(emptyDatabase())
const qualitySummary = ref<DataQualitySummary>({ enabled: true, running: false, latestRun: null, checks: [] })
const qualityIssues = ref<DataQualityIssue[]>([])

const databaseStatus = computed(() => {
  const component = healthStatus.value.components?.database as { status?: string } | undefined
  return normalizeStatus(component?.status || healthStatus.value.status)
})

const connectionUsage = computed(() => {
  const total = Number(databaseOverview.value.connections?.total || 0)
  const max = Number(databaseOverview.value.database?.max_connections || 0)
  return max > 0 ? Math.min(100, Number(((total / max) * 100).toFixed(1))) : 0
})

const hikariUsage = computed(() => {
  const active = Number(databaseOverview.value.hikari?.active || 0)
  const max = Number(databaseOverview.value.hikari?.maximum_pool_size || 0)
  return max > 0 ? Math.min(100, Number(((active / max) * 100).toFixed(1))) : 0
})

const latestRun = computed(() => qualitySummary.value.latestRun)
const criticalIssues = computed(() => Number(latestRun.value?.critical_count || 0))
const warningIssues = computed(() => Number(latestRun.value?.warning_count || 0))
const appStatus = computed(() => normalizeStatus(healthStatus.value.status))
const backupStatus = computed(() => normalizeStatus(operationsStatus.value.backup?.status))
const auditStatus = computed(() => normalizeStatus(operationsStatus.value.audit?.status))
const serverDisk = computed(() => operationsStatus.value.storage?.server)
const imageDisk = computed(() => operationsStatus.value.storage?.images)

const summaryCards = computed(() => [
  {
    label: '应用状态',
    value: appStatus.value,
    note: runtimeInfo.value.uptimeFormatted || '等待运行信息',
    tone: statusTone(appStatus.value),
    icon: 'i-ant-design:cloud-server-outlined',
  },
  {
    label: '数据库状态',
    value: databaseStatus.value,
    note: `${databaseOverview.value.connections?.total ?? 0}/${databaseOverview.value.database?.max_connections ?? '-'} 个连接`,
    tone: statusTone(databaseStatus.value),
    icon: 'i-ant-design:database-twotone',
  },
  {
    label: '最近备份',
    value: backupStatus.value,
    note: operationsStatus.value.backup?.completedAt
      ? `${formatAge(operationsStatus.value.backup?.ageHours)}前 · ${formatBytes(operationsStatus.value.backup?.dumpSizeBytes)}`
      : '尚无成功备份记录',
    tone: statusTone(backupStatus.value),
    icon: 'i-ant-design:save-twotone',
  },
  {
    label: '审计队列',
    value: formatNumber(operationsStatus.value.audit?.queuedEvents),
    note: auditStatus.value === 'UP' ? '关键审计正常写入数据库' : `状态 ${auditStatus.value}`,
    tone: statusTone(auditStatus.value),
    icon: 'i-ant-design:safety-certificate-twotone',
  },
  {
    label: '服务器磁盘',
    value: `${Number(serverDisk.value?.usedPercent || 0).toFixed(1)}%`,
    note: `可用 ${formatBytes(serverDisk.value?.usableBytes)}`,
    tone: diskTone(serverDisk.value?.usedPercent),
    icon: 'i-ant-design:hdd-twotone',
  },
  {
    label: '图片磁盘',
    value: imageDisk.value ? `${Number(imageDisk.value.usedPercent || 0).toFixed(1)}%` : '未配置',
    note: imageDisk.value ? `可用 ${formatBytes(imageDisk.value.usableBytes)}` : '未配置独立图片目录',
    tone: imageDisk.value ? diskTone(imageDisk.value.usedPercent) : 'blue',
    icon: 'i-ant-design:picture-twotone',
  },
])

function normalizeStatus(value: unknown) {
  return String(value || 'UNKNOWN').toUpperCase()
}

function statusTone(status: string) {
  if (status === 'UP' || status === 'SUCCESS') return 'green'
  if (status === 'DEGRADED' || status === 'STALE' || status === 'WARNING' || status === 'NOT_RUN') return 'amber'
  if (status === 'UNKNOWN') return 'blue'
  return 'danger'
}

function diskTone(value: unknown) {
  const used = Number(value || 0)
  return used >= 92 ? 'danger' : used >= 85 ? 'amber' : 'teal'
}

function formatNumber(value: unknown) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
}

function formatBytes(value: unknown) {
  const bytes = Number(value || 0)
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index > 1 ? 2 : 0)} ${units[index]}`
}

function formatDuration(value: unknown) {
  const seconds = Number(value || 0)
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return days > 0 ? `${days} 天 ${hours} 小时` : `${hours} 小时 ${minutes} 分钟`
}

function formatAge(value: unknown) {
  const hours = Number(value)
  if (!Number.isFinite(hours)) return '-'
  if (hours < 1) return `${Math.max(1, Math.round(hours * 60))} 分钟`
  if (hours < 24) return `${hours.toFixed(1)} 小时`
  return `${(hours / 24).toFixed(1)} 天`
}

function formatDate(value: unknown) {
  if (!value) return '-'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString()
}

function statusType(status?: string) {
  const normalized = normalizeStatus(status)
  if (normalized === 'SUCCESS' || normalized === 'UP') return 'success'
  if (normalized === 'RUNNING') return 'primary'
  if (['WARNING', 'DEGRADED', 'STALE', 'NOT_RUN'].includes(normalized)) return 'warning'
  if (normalized === 'UNKNOWN') return 'info'
  return 'danger'
}

async function loadAll(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const results = await Promise.allSettled([
      getSystemHealth(),
      getSystemRuntime(),
      getSystemMemory(),
      getSystemInfo(),
      getSystemOperations(),
      getDatabaseOverview(),
      getDataQualitySummary(),
      getDataQualityIssues(100),
    ])

    const [healthRes, runtimeRes, memoryRes, infoRes, operationsRes, databaseRes, qualityRes, issuesRes] = results
    if (healthRes.status === 'fulfilled') healthStatus.value = healthRes.value.data ?? {}
    if (runtimeRes.status === 'fulfilled') runtimeInfo.value = runtimeRes.value.data ?? {}
    if (memoryRes.status === 'fulfilled') memoryInfo.value = memoryRes.value.data ?? { heap: {}, nonHeap: {} }
    if (infoRes.status === 'fulfilled') systemInfo.value = infoRes.value.data ?? { application: {}, jvm: {}, operatingSystem: {} }
    if (operationsRes.status === 'fulfilled') operationsStatus.value = operationsRes.value.data ?? {}
    if (databaseRes.status === 'fulfilled') databaseOverview.value = databaseRes.value.data ?? emptyDatabase()
    if (qualityRes.status === 'fulfilled') qualitySummary.value = qualityRes.value.data ?? qualitySummary.value
    if (issuesRes.status === 'fulfilled') qualityIssues.value = issuesRes.value.data ?? []
    lastRefresh.value = new Date().toLocaleTimeString()
  }
  finally {
    loading.value = false
  }
}

async function runQualityCheck() {
  qualityRunning.value = true
  try {
    const response = await runDataQualityChecks()
    qualitySummary.value = response.data ?? qualitySummary.value
    const issues = await getDataQualityIssues(100)
    qualityIssues.value = issues.data ?? []
    ElMessage.success('数据质量检查已完成')
  }
  catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '数据质量检查失败')
  }
  finally {
    qualityRunning.value = false
  }
}

const { pause, resume } = useIntervalFn(() => {
  if (autoRefresh.value) void loadAll(false)
}, 15000)

function handleVisibilityChange() {
  if (document.hidden) pause()
  else if (autoRefresh.value) resume()
}

onMounted(() => {
  void loadAll()
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
        <p class="eyebrow">Single Server Operations</p>
        <h2>系统与数据库监控</h2>
        <p class="subtitle">
          内置单服务器运维 · {{ autoRefresh ? '每 15 秒自动刷新' : '自动刷新已暂停' }} · 上次刷新 {{ lastRefresh || '--' }}
        </p>
      </div>
      <div class="header-actions">
        <el-button :type="autoRefresh ? 'primary' : 'default'" @click="autoRefresh = !autoRefresh">
          {{ autoRefresh ? '暂停刷新' : '恢复刷新' }}
        </el-button>
        <el-button type="primary" :loading="loading" @click="loadAll()">
          立即刷新
        </el-button>
      </div>
    </div>

    <el-alert
      class="manager-alert"
      type="info"
      :closable="false"
      show-icon
      title="部署、回滚、立即备份和性能采样请在服务器上运行 C:\MRR\MRR-Manager.cmd；网页仅提供只读状态，避免暴露高权限运维接口。"
    />

    <section class="mrr-metric-grid mrr-metric-grid--compact">
      <el-card
        v-for="item in summaryCards"
        :key="item.label"
        shadow="never"
        class="mrr-metric-card"
        :class="`mrr-metric-card--${item.tone}`"
      >
        <div class="mrr-metric-card__icon"><i :class="item.icon" /></div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">{{ item.label }}</span>
          <strong class="mrr-metric-card__value">{{ item.value }}</strong>
          <p class="mrr-metric-card__note">{{ item.note }}</p>
        </div>
      </el-card>
    </section>

    <el-row :gutter="20" class="monitor-row">
      <el-col :xs="24" :lg="13">
        <el-card shadow="never" class="monitor-card">
          <template #header>
            <div class="card-header">
              <div><strong>单服务器运维状态</strong><p>备份、审计、磁盘与本地日志</p></div>
              <el-tag type="success">{{ operationsStatus.mode || 'SINGLE_SERVER' }}</el-tag>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="备份状态">
              <el-tag :type="statusType(backupStatus)">{{ backupStatus }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最近备份">{{ formatDate(operationsStatus.backup?.completedAt) }}</el-descriptions-item>
            <el-descriptions-item label="审计状态">
              <el-tag :type="statusType(auditStatus)">{{ auditStatus }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="待重放审计">{{ formatNumber(operationsStatus.audit?.queuedEvents) }}</el-descriptions-item>
            <el-descriptions-item label="应用日志">{{ formatBytes(operationsStatus.logs?.applicationBytes) }}</el-descriptions-item>
            <el-descriptions-item label="错误日志">{{ formatBytes(operationsStatus.logs?.errorBytes) }}</el-descriptions-item>
            <el-descriptions-item label="第二备份位置" :span="2">
              {{ operationsStatus.backup?.secondaryCopyPath || '未配置，建议使用 NAS 或另一块物理磁盘' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="operationsStatus.backup?.lastError" label="最近备份错误" :span="2">
              <span class="danger-text">{{ operationsStatus.backup.lastError }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="progress-grid">
            <div>
              <div class="progress-label"><span>服务器磁盘使用率</span><strong>{{ Number(serverDisk?.usedPercent || 0).toFixed(1) }}%</strong></div>
              <el-progress :percentage="Number(serverDisk?.usedPercent || 0)" :status="Number(serverDisk?.usedPercent || 0) >= 92 ? 'exception' : Number(serverDisk?.usedPercent || 0) >= 85 ? 'warning' : 'success'" />
            </div>
            <div v-if="imageDisk">
              <div class="progress-label"><span>图片磁盘使用率</span><strong>{{ Number(imageDisk.usedPercent || 0).toFixed(1) }}%</strong></div>
              <el-progress :percentage="Number(imageDisk.usedPercent || 0)" :status="Number(imageDisk.usedPercent || 0) >= 92 ? 'exception' : Number(imageDisk.usedPercent || 0) >= 85 ? 'warning' : 'success'" />
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="11">
        <el-card shadow="never" class="monitor-card">
          <template #header>
            <div class="card-header">
              <div><strong>应用运行状态</strong><p>JVM、内存与运行环境摘要</p></div>
              <el-tag :type="statusType(appStatus)">{{ appStatus }}</el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="应用">{{ systemInfo.application?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="运行时长">{{ runtimeInfo.uptimeFormatted || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Java">{{ systemInfo.jvm?.javaVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="堆内存使用率">{{ memoryInfo.usagePercent || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理器">{{ systemInfo.jvm?.availableProcessors || '-' }}</el-descriptions-item>
            <el-descriptions-item label="系统负载">{{ systemInfo.operatingSystem?.systemLoadAverage || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="monitor-card monitor-row">
      <template #header>
        <div class="card-header">
          <div><strong>PostgreSQL 运行状态</strong><p>实时连接、事务、锁等待和缓存情况</p></div>
          <el-tag :type="databaseStatus === 'UP' ? 'success' : 'danger'">{{ databaseStatus }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="数据库">{{ databaseOverview.database?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ databaseOverview.database?.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据库大小">{{ formatBytes(databaseOverview.database?.size_bytes) }}</el-descriptions-item>
        <el-descriptions-item label="运行时长">{{ formatDuration(databaseOverview.database?.uptime_seconds) }}</el-descriptions-item>
        <el-descriptions-item label="事务提交">{{ formatNumber(databaseOverview.transactions?.commits) }}</el-descriptions-item>
        <el-descriptions-item label="事务回滚">{{ formatNumber(databaseOverview.transactions?.rollbacks) }}</el-descriptions-item>
        <el-descriptions-item label="锁等待">{{ databaseOverview.contention?.lock_waiters ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="长事务">{{ databaseOverview.contention?.long_transactions ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="缓存命中率">{{ databaseOverview.transactions?.cache_hit_ratio ?? '-' }}%</el-descriptions-item>
      </el-descriptions>
      <div class="progress-grid progress-grid--two">
        <div>
          <div class="progress-label"><span>数据库连接使用率</span><strong>{{ connectionUsage }}%</strong></div>
          <el-progress :percentage="connectionUsage" :status="connectionUsage >= 90 ? 'exception' : connectionUsage >= 80 ? 'warning' : 'success'" />
        </div>
        <div>
          <div class="progress-label"><span>Hikari 连接池使用率</span><strong>{{ hikariUsage }}%</strong></div>
          <el-progress :percentage="hikariUsage" :status="hikariUsage >= 90 ? 'exception' : hikariUsage >= 80 ? 'warning' : 'success'" />
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="monitor-card monitor-row">
      <template #header>
        <div class="card-header">
          <div><strong>数据质量检查</strong><p>由管理员手动执行，检查结果保存在数据库</p></div>
          <div class="quality-actions">
            <el-tag :type="statusType(latestRun?.status)">{{ qualitySummary.running ? 'RUNNING' : latestRun?.status || 'NOT_RUN' }}</el-tag>
            <el-button type="primary" :loading="qualityRunning || qualitySummary.running" @click="runQualityCheck">立即检查</el-button>
          </div>
        </div>
      </template>
      <div class="quality-summary">
        <div><span>严重异常</span><strong class="danger-text">{{ formatNumber(criticalIssues) }}</strong></div>
        <div><span>警告</span><strong class="warning-text">{{ formatNumber(warningIssues) }}</strong></div>
        <div><span>检查项</span><strong>{{ latestRun?.check_count ?? 0 }}</strong></div>
        <div><span>完成时间</span><strong>{{ formatDate(latestRun?.completed_at) }}</strong></div>
      </div>
      <el-table :data="qualitySummary.checks" stripe border empty-text="尚未执行数据质量检查">
        <el-table-column prop="check_name" label="检查项" min-width="220" />
        <el-table-column prop="check_code" label="代码" min-width="210" />
        <el-table-column label="级别" width="110">
          <template #default="scope"><el-tag :type="scope.row.severity === 'CRITICAL' ? 'danger' : 'warning'">{{ scope.row.severity }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="issue_count" label="异常数" width="110" sortable />
        <el-table-column prop="sampled_count" label="已保存样本" width="120" />
      </el-table>
    </el-card>

    <el-row :gutter="20" class="monitor-row">
      <el-col :xs="24" :xl="13">
        <el-card shadow="never" class="monitor-card">
          <template #header><div class="card-header"><strong>异常样本</strong><span>最多显示最近 100 条</span></div></template>
          <el-table :data="qualityIssues" stripe border max-height="460" empty-text="没有异常样本">
            <el-table-column prop="severity" label="级别" width="100">
              <template #default="scope"><el-tag :type="scope.row.severity === 'CRITICAL' ? 'danger' : 'warning'" size="small">{{ scope.row.severity }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="check_name" label="检查项" min-width="180" />
            <el-table-column prop="bah" label="病案号" width="120" />
            <el-table-column prop="sjh" label="上架号" width="120" />
            <el-table-column prop="detail" label="说明" min-width="260" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :xl="11">
        <el-card shadow="never" class="monitor-card">
          <template #header><div class="card-header"><strong>数据库占用最大的表</strong><span>包含表与索引</span></div></template>
          <el-table :data="databaseOverview.tables" stripe border max-height="460" empty-text="暂无表统计">
            <el-table-column prop="table_name" label="表" min-width="180" />
            <el-table-column label="总大小" width="120"><template #default="scope">{{ formatBytes(scope.row.total_bytes) }}</template></el-table-column>
            <el-table-column prop="live_rows" label="有效行" width="110" />
            <el-table-column prop="dead_rows" label="死元组" width="110" />
            <el-table-column label="最近自动清理" min-width="180"><template #default="scope">{{ formatDate(scope.row.last_autovacuum) }}</template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-shell { padding: 20px; }
.page-header, .card-header, .header-actions, .quality-actions, .progress-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.page-header { margin-bottom: 18px; }
.page-header h2 { margin: 4px 0; }
.eyebrow {
  margin: 0;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .12em;
  text-transform: uppercase;
}
.subtitle, .card-header p, .card-header span {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.manager-alert { margin-bottom: 18px; }
.monitor-row { margin-top: 20px; }
.monitor-card { height: 100%; border-radius: 12px; }
.card-header > div:first-child { display: grid; gap: 4px; }
.progress-grid { display: grid; gap: 18px; margin-top: 22px; }
.progress-grid--two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.progress-label { margin-bottom: 8px; font-size: 13px; }
.quality-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.quality-summary > div {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}
.quality-summary span { color: var(--el-text-color-secondary); font-size: 13px; }
.quality-summary strong { font-size: 20px; }
.danger-text { color: var(--el-color-danger); }
.warning-text { color: var(--el-color-warning); }
@media (max-width: 900px) {
  .page-header, .card-header { align-items: flex-start; flex-direction: column; }
  .header-actions { flex-wrap: wrap; justify-content: flex-start; }
  .quality-summary, .progress-grid--two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .quality-summary, .progress-grid--two { grid-template-columns: 1fr; }
}
</style>

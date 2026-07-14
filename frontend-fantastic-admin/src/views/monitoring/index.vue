<script setup lang="ts">
import type { HealthInfo, MemoryInfo, RuntimeInfo, SystemInfo } from '@/api/types'
import type { DatabaseOverview, DataQualityIssue, DataQualitySummary } from '@/api/modules/database-monitoring'
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
const databaseOverview = ref<DatabaseOverview>(emptyDatabase())
const qualitySummary = ref<DataQualitySummary>({ enabled: true, running: false, latestRun: null, checks: [] })
const qualityIssues = ref<DataQualityIssue[]>([])

const databaseStatus = computed(() => {
  const component = healthStatus.value.components?.database as { status?: string } | undefined
  return String(component?.status || healthStatus.value.status || 'UNKNOWN').toUpperCase()
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

const summaryCards = computed(() => [
  {
    label: '数据库状态',
    value: databaseStatus.value,
    note: databaseOverview.value.database?.name || 'PostgreSQL',
    tone: databaseStatus.value === 'UP' ? 'green' : 'danger',
    icon: 'i-ant-design:database-twotone',
  },
  {
    label: '数据库连接',
    value: `${databaseOverview.value.connections?.total ?? 0}/${databaseOverview.value.database?.max_connections ?? '-'}`,
    note: `活跃 ${databaseOverview.value.connections?.active ?? 0} · 等待 ${databaseOverview.value.connections?.waiting ?? 0}`,
    tone: connectionUsage.value >= 90 ? 'danger' : connectionUsage.value >= 80 ? 'amber' : 'blue',
    icon: 'i-ant-design:link-outlined',
  },
  {
    label: 'Hikari 连接池',
    value: `${databaseOverview.value.hikari?.active ?? 0}/${databaseOverview.value.hikari?.maximum_pool_size ?? '-'}`,
    note: `空闲 ${databaseOverview.value.hikari?.idle ?? 0} · 等待 ${databaseOverview.value.hikari?.pending ?? 0}`,
    tone: hikariUsage.value >= 90 ? 'danger' : hikariUsage.value >= 80 ? 'amber' : 'teal',
    icon: 'i-ant-design:cluster-outlined',
  },
  {
    label: '严重数据异常',
    value: formatNumber(criticalIssues.value),
    note: latestRun.value?.completed_at ? `最近检查 ${formatDate(latestRun.value.completed_at)}` : '尚未执行检查',
    tone: criticalIssues.value > 0 ? 'danger' : 'green',
    icon: 'i-ant-design:warning-twotone',
  },
  {
    label: '数据库大小',
    value: formatBytes(databaseOverview.value.database?.size_bytes),
    note: `缓存命中率 ${databaseOverview.value.transactions?.cache_hit_ratio ?? '-'}%`,
    tone: 'violet',
    icon: 'i-ant-design:hdd-twotone',
  },
])

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

function formatDate(value: unknown) {
  if (!value) return '-'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString()
}

function statusType(status?: string) {
  if (status === 'SUCCESS' || status === 'UP') return 'success'
  if (status === 'RUNNING') return 'primary'
  if (status === 'WARNING') return 'warning'
  return 'danger'
}

async function loadAll(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const [healthRes, runtimeRes, memoryRes, infoRes, databaseRes, qualityRes, issuesRes] = await Promise.allSettled([
      getSystemHealth(),
      getSystemRuntime(),
      getSystemMemory(),
      getSystemInfo(),
      getDatabaseOverview(),
      getDataQualitySummary(),
      getDataQualityIssues(100),
    ])

    if (healthRes.status === 'fulfilled') healthStatus.value = healthRes.value.data ?? {}
    if (runtimeRes.status === 'fulfilled') runtimeInfo.value = runtimeRes.value.data ?? {}
    if (memoryRes.status === 'fulfilled') memoryInfo.value = memoryRes.value.data ?? { heap: {}, nonHeap: {} }
    if (infoRes.status === 'fulfilled') systemInfo.value = infoRes.value.data ?? { application: {}, jvm: {}, operatingSystem: {} }
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
    const message = error instanceof Error ? error.message : '数据质量检查失败'
    ElMessage.error(message)
  }
  finally {
    qualityRunning.value = false
  }
}

function openGrafana() {
  const configuredUrl = String(import.meta.env.VITE_GRAFANA_URL || '').trim()
  const url = configuredUrl || `${window.location.protocol}//${window.location.hostname}:3000`
  window.open(url, '_blank', 'noopener,noreferrer')
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
        <p class="eyebrow">System & Database Monitor</p>
        <h2>系统与数据库监控</h2>
        <p class="subtitle">
          {{ autoRefresh ? '每 15 秒自动刷新' : '自动刷新已暂停' }} · 上次刷新 {{ lastRefresh || '--' }}
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="openGrafana">
          Grafana 看板
        </el-button>
        <el-button :type="autoRefresh ? 'primary' : 'default'" @click="autoRefresh = !autoRefresh">
          {{ autoRefresh ? '暂停刷新' : '恢复刷新' }}
        </el-button>
        <el-button type="primary" :loading="loading" @click="loadAll()">
          立即刷新
        </el-button>
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
          <strong class="mrr-metric-card__value">{{ item.value }}</strong>
          <p class="mrr-metric-card__note">{{ item.note }}</p>
        </div>
      </el-card>
    </section>

    <el-row :gutter="20" class="monitor-row">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="monitor-card">
          <template #header>
            <div class="card-header">
              <div>
                <strong>PostgreSQL 运行状态</strong>
                <p>实时连接、事务、锁等待和缓存情况</p>
              </div>
              <el-tag :type="databaseStatus === 'UP' ? 'success' : 'danger'">
                {{ databaseStatus }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="数据库">{{ databaseOverview.database?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ databaseOverview.database?.version || '-' }}</el-descriptions-item>
            <el-descriptions-item label="运行时长">{{ formatDuration(databaseOverview.database?.uptime_seconds) }}</el-descriptions-item>
            <el-descriptions-item label="数据库大小">{{ formatBytes(databaseOverview.database?.size_bytes) }}</el-descriptions-item>
            <el-descriptions-item label="事务提交">{{ formatNumber(databaseOverview.transactions?.commits) }}</el-descriptions-item>
            <el-descriptions-item label="事务回滚">{{ formatNumber(databaseOverview.transactions?.rollbacks) }}</el-descriptions-item>
            <el-descriptions-item label="锁等待">{{ databaseOverview.contention?.lock_waiters ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="长事务">{{ databaseOverview.contention?.long_transactions ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="死锁累计">{{ databaseOverview.transactions?.deadlocks ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="缓存命中率">{{ databaseOverview.transactions?.cache_hit_ratio ?? '-' }}%</el-descriptions-item>
          </el-descriptions>

          <div class="progress-grid">
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
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="monitor-card">
          <template #header>
            <div class="card-header">
              <div>
                <strong>应用运行状态</strong>
                <p>JVM、内存与运行环境摘要</p>
              </div>
              <el-tag :type="statusType(String(healthStatus.status || 'UNKNOWN'))">
                {{ healthStatus.status || 'UNKNOWN' }}
              </el-tag>
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
          <div>
            <strong>数据质量监控</strong>
            <p>检查结果按批次落库，Prometheus 仅采集聚合数量</p>
          </div>
          <div class="quality-actions">
            <el-tag :type="statusType(latestRun?.status)">
              {{ qualitySummary.running ? 'RUNNING' : latestRun?.status || 'NOT_RUN' }}
            </el-tag>
            <el-button type="primary" :loading="qualityRunning || qualitySummary.running" @click="runQualityCheck">
              立即检查
            </el-button>
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
          <template #default="scope">
            <el-tag :type="scope.row.severity === 'CRITICAL' ? 'danger' : 'warning'">
              {{ scope.row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="issue_count" label="异常数" width="110" sortable />
        <el-table-column prop="sampled_count" label="已保存样本" width="120" />
      </el-table>
    </el-card>

    <el-row :gutter="20" class="monitor-row">
      <el-col :xs="24" :xl="13">
        <el-card shadow="never" class="monitor-card">
          <template #header>
            <div class="card-header"><strong>异常样本</strong><span>最多显示最近 100 条</span></div>
          </template>
          <el-table :data="qualityIssues" stripe border max-height="460" empty-text="没有异常样本">
            <el-table-column prop="severity" label="级别" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.severity === 'CRITICAL' ? 'danger' : 'warning'" size="small">
                  {{ scope.row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="check_name" label="检查项" min-width="180" />
            <el-table-column prop="bah" label="病案号" width="120" />
            <el-table-column prop="sjh" label="上架号" width="120" />
            <el-table-column prop="detail" label="说明" min-width="260" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" class="monitor-row">
      <el-col :xs="24" :xl="11">
        <el-card shadow="never" class="monitor-card">
          <template #header>
            <div class="card-header"><strong>数据库占用最大的表</strong><span>包含表与索引</span></div>
          </template>
          <el-table :data="databaseOverview.tables" stripe border max-height="460" empty-text="暂无表统计">
            <el-table-column prop="table_name" label="表" min-width="180" />
            <el-table-column label="总大小" width="120">
              <template #default="scope">{{ formatBytes(scope.row.total_bytes) }}</template>
            </el-table-column>
            <el-table-column prop="live_rows" label="有效行" width="110" />
            <el-table-column prop="dead_rows" label="死元组" width="110" />
            <el-table-column label="最近自动清理" min-width="180">
              <template #default="scope">{{ formatDate(scope.row.last_autovacuum) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-shell {
  padding: 20px;
}

.page-header,
.card-header,
.header-actions,
.quality-actions,
.progress-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-header {
  margin-bottom: 18px;
}

.page-header h2 {
  margin: 4px 0;
}

.eyebrow {
  margin: 0;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .12em;
  text-transform: uppercase;
}

.subtitle,
.card-header p,
.card-header span {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.monitor-row {
  margin-top: 20px;
}

.monitor-card {
  height: 100%;
  border-radius: 12px;
}

.card-header > div:first-child {
  display: grid;
  gap: 4px;
}

.progress-grid {
  display: grid;
  gap: 18px;
  margin-top: 22px;
}

.progress-label {
  margin-bottom: 8px;
  font-size: 13px;
}

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

.quality-summary span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.quality-summary strong {
  font-size: 20px;
}

.danger-text {
  color: var(--el-color-danger);
}

.warning-text {
  color: var(--el-color-warning);
}

@media (max-width: 900px) {
  .page-header,
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .quality-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

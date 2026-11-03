<script setup lang="ts">
import type {
  ExportCenterJob,
  ImageSourceDiagnosis,
  IntegritySummary,
  MaintenanceStatus,
  OperationAuditEntry,
  OperationsDiagnosticRun,
  OperationsOverview,
  PermissionMatrix,
  ReadinessSnapshot,
} from '@/api/modules/operations'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  diagnoseImageSource,
  disableMaintenanceMode,
  enableMaintenanceMode,
  getDeploymentReadiness,
  getExportCenter,
  getIntegritySummary,
  getMaintenanceStatus,
  getOperationAudit,
  getOperationsDiagnosticReport,
  getOperationsOverview,
  getPermissionMatrix,
  runOperationsDiagnostics,
  savePermissionMatrixSnapshot,
} from '@/api/modules/operations'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime as formatTime } from '@/utils/display-format'
import { hasAnyPermission, hasPermission } from '@/utils/session'

const route = useRoute()
const router = useRouter()
const validTabs = new Set(['overview', 'diagnostics', 'image', 'integrity', 'tasks', 'permissions', 'maintenance', 'audit'])
const initialTab = typeof route.query.tab === 'string' && validTabs.has(route.query.tab) ? route.query.tab : 'overview'
const activeTab = ref(initialTab)

const loading = reactive({
  overview: false,
  diagnostics: false,
  report: false,
  image: false,
  integrity: false,
  tasks: false,
  permissions: false,
  maintenance: false,
  audit: false,
})

const overview = ref<OperationsOverview>()
const overviewFailed = ref(false)
let overviewRequest: Promise<void> | undefined
const canManage = computed(() => hasPermission('system:manage'))
const canViewTasks = computed(() => hasPermission('record:read'))
const canViewLogs = computed(() => hasPermission('log:read'))
const quickLinks = computed(() => (overview.value?.quickLinks || []).filter((link) => {
  const permissions = router.resolve(link.path).meta.auth
  return Array.isArray(permissions) && hasAnyPermission(permissions)
}))
const diagnosticRun = ref<OperationsDiagnosticRun>()
const readiness = ref<ReadinessSnapshot>()
const maintenance = ref<MaintenanceStatus>()
const imageForm = reactive<{ bah: string, sjh: string, imageId?: number }>({
  bah: '',
  sjh: '',
  imageId: undefined,
})
const imageDiagnosis = ref<ImageSourceDiagnosis>()
const integrity = ref<IntegritySummary>()
const exportJobs = ref<ExportCenterJob[]>([])
const permissionMatrix = ref<PermissionMatrix>()
const auditEntries = ref<OperationAuditEntry[]>([])
const endpointKeyword = ref('')
const exportStatus = ref('')

const filteredEndpoints = computed(() => {
  const endpoints = permissionMatrix.value?.endpoints || []
  const keyword = endpointKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return endpoints
  }
  return endpoints.filter(item => [item.key, item.operation, item.requiredPermissions.join(',')]
    .some(value => value.toLowerCase().includes(keyword)))
})

const filteredExportJobs = computed(() => {
  if (!exportStatus.value) {
    return exportJobs.value
  }
  return exportJobs.value.filter(item => item.status === exportStatus.value)
})

const overviewChecks = computed(() => overview.value?.readiness.checks || [])
const failedReadinessChecks = computed(() => overviewChecks.value.filter(check => !check.passed)
  .sort((a, b) => Number(b.severity === 'CRITICAL') - Number(a.severity === 'CRITICAL')))
const criticalChecks = computed(() => failedReadinessChecks.value.filter(check => check.severity === 'CRITICAL'))
const taskCounts = computed(() => overview.value?.taskSummary.counts || {})
const tasksAvailable = computed(() => overview.value?.taskSummary.available === true)
const taskStates = [
  { code: 'PENDING', label: '等待', status: 'warning' },
  { code: 'PROCESSING', label: '处理中', status: undefined },
  { code: 'SUCCESS', label: '成功', status: 'success' },
  { code: 'FAILED', label: '失败', status: 'exception' },
] as const
const taskTotal = computed(() => taskStates.reduce((sum, state) => sum + (taskCounts.value[state.code] || 0), 0))

const heapUsage = computed(() => {
  const used = Number(overview.value?.runtime.heapUsedBytes || 0)
  const max = Number(overview.value?.runtime.heapMaxBytes || 0)
  return max > 0 ? Math.min(100, Number(((used / max) * 100).toFixed(1))) : 0
})

function formatPercent(value?: number) {
  return `${((value || 0) * 100).toFixed(2)}%`
}

function formatNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
}

function formatBytes(value?: number) {
  const bytes = Number(value || 0)
  if (!bytes) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index > 1 ? 2 : 1)} ${units[index]}`
}

function formatDuration(milliseconds?: number) {
  const totalMinutes = Math.floor(Number(milliseconds || 0) / 60000)
  const days = Math.floor(totalMinutes / 1440)
  const hours = Math.floor((totalMinutes % 1440) / 60)
  const minutes = totalMinutes % 60
  return days > 0 ? `${days} 天 ${hours} 小时` : `${hours} 小时 ${minutes} 分钟`
}

function modeLabel(mode?: ReadinessSnapshot['mode']) {
  if (mode === 'READ_WRITE') {
    return '正常读写'
  }
  if (mode === 'READ_ONLY_MAINTENANCE') {
    return '主动维护只读'
  }
  return '自动只读降级'
}

function modeType(mode?: ReadinessSnapshot['mode']): 'success' | 'danger' | 'warning' {
  if (mode === 'READ_WRITE') {
    return 'success'
  }
  if (mode === 'READ_ONLY_MAINTENANCE') {
    return 'warning'
  }
  return 'danger'
}

function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED' || status === 'EXPIRED') {
    return 'danger'
  }
  if (status === 'PROCESSING' || status === 'PENDING') {
    return 'warning'
  }
  return 'info'
}

function responseType(status?: string): 'success' | 'danger' | 'warning' | 'info' {
  if (!status) {
    return 'info'
  }
  if (status.startsWith('2') || status.startsWith('3')) {
    return 'success'
  }
  if (status.startsWith('5')) {
    return 'danger'
  }
  if (status.startsWith('4')) {
    return 'warning'
  }
  return 'info'
}

function loadOverview() {
  if (overviewRequest) {
    return overviewRequest
  }
  loading.overview = true
  overviewRequest = getOperationsOverview()
    .then((snapshot) => {
      overview.value = snapshot
      readiness.value = snapshot.readiness
      overviewFailed.value = false
    })
    .catch(() => {
      overviewFailed.value = true
    })
    .finally(() => {
      loading.overview = false
      overviewRequest = undefined
    })
  return overviewRequest
}

async function runFullDiagnostic() {
  if (!canManage.value || loading.diagnostics || loading.overview) {
    return
  }
  loading.diagnostics = true
  try {
    diagnosticRun.value = await runOperationsDiagnostics()
    ElMessage.success('全面体检已完成')
    await loadOverview()
  }
  finally {
    loading.diagnostics = false
  }
}

async function downloadDiagnosticReport() {
  loading.report = true
  try {
    const report = await getOperationsDiagnosticReport()
    const content = JSON.stringify(report, null, 2)
    const blob = new Blob([content], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `mrr-diagnostic-${new Date().toISOString().replace(/[:.]/g, '-')}.json`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('诊断报告已导出')
  }
  finally {
    loading.report = false
  }
}

async function runImageDiagnosis() {
  if (!imageForm.bah.trim() && !imageForm.sjh.trim() && !imageForm.imageId) {
    ElMessage.warning('病案号、上架号、图片 ID 至少填写一项')
    return
  }
  loading.image = true
  try {
    imageDiagnosis.value = await diagnoseImageSource({
      bah: imageForm.bah.trim() || undefined,
      sjh: imageForm.sjh.trim() || undefined,
      imageId: imageForm.imageId,
    })
  }
  finally {
    loading.image = false
  }
}

async function loadIntegrity() {
  loading.integrity = true
  try {
    integrity.value = await getIntegritySummary()
  }
  finally {
    loading.integrity = false
  }
}

async function loadTasks() {
  loading.tasks = true
  try {
    exportJobs.value = await getExportCenter(200)
    if (!overview.value) {
      await loadOverview()
    }
  }
  finally {
    loading.tasks = false
  }
}

async function loadPermissions() {
  loading.permissions = true
  try {
    permissionMatrix.value = await getPermissionMatrix(true)
  }
  finally {
    loading.permissions = false
  }
}

async function savePermissionSnapshot() {
  const version = await ElMessageBox.prompt('请输入权限矩阵版本名称', '保存权限版本', {
    confirmButtonText: '保存',
    cancelButtonText: '取消',
    inputPlaceholder: '例如 v0.7.0-before',
    inputValidator: value => Boolean(value && value.trim()) || '版本名称不能为空',
  }).then(result => result.value).catch(() => '')
  if (!version) {
    return
  }
  await savePermissionMatrixSnapshot(version.trim())
  ElMessage.success('权限矩阵快照已保存')
  await loadPermissions()
}

async function loadMaintenance(refresh = false) {
  loading.maintenance = true
  try {
    const [maintenanceStatus, readinessStatus] = await Promise.all([
      getMaintenanceStatus(),
      getDeploymentReadiness(refresh),
    ])
    maintenance.value = maintenanceStatus
    readiness.value = readinessStatus
  }
  finally {
    loading.maintenance = false
  }
}

async function enableMaintenance() {
  const reason = await ElMessageBox.prompt('请输入本次维护原因', '进入主动维护模式', {
    confirmButtonText: '进入维护模式',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：数据库升级、批量数据修复',
    inputValidator: value => Boolean(value && value.trim()) || '维护原因不能为空',
    type: 'warning',
  }).then(result => result.value).catch(() => '')
  if (!reason) {
    return
  }
  loading.maintenance = true
  try {
    maintenance.value = await enableMaintenanceMode(reason.trim())
    ElMessage.warning('系统已进入主动维护只读模式')
    await overviewRequest
    await loadOverview()
  }
  finally {
    loading.maintenance = false
  }
}

async function disableMaintenance() {
  await ElMessageBox.confirm(
    '关闭主动维护模式不会绕过数据库、磁盘、Nginx 或 OSS 故障导致的自动降级。是否继续？',
    '退出主动维护模式',
    { type: 'warning', confirmButtonText: '确认退出', cancelButtonText: '取消' },
  )
  loading.maintenance = true
  try {
    maintenance.value = await disableMaintenanceMode()
    ElMessage.success('主动维护模式已关闭')
    await overviewRequest
    await loadOverview()
  }
  finally {
    loading.maintenance = false
  }
}

async function loadAudit() {
  loading.audit = true
  try {
    auditEntries.value = await getOperationAudit(100)
  }
  finally {
    loading.audit = false
  }
}

async function navigate(path: string) {
  await router.push(path)
}

async function selectTab(tab: string) {
  activeTab.value = tab
  await handleTabChange(tab)
}

async function handleTabChange(name: string | number) {
  const tab = String(name)
  await router.replace({ query: { ...route.query, tab } })
  if (tab === 'integrity' && !integrity.value) {
    await loadIntegrity()
  }
  if (tab === 'tasks' && exportJobs.value.length === 0) {
    await loadTasks()
  }
  if (tab === 'permissions' && !permissionMatrix.value) {
    await loadPermissions()
  }
  if (tab === 'maintenance') {
    await loadMaintenance()
  }
  if (tab === 'audit' && auditEntries.value.length === 0) {
    await loadAudit()
  }
}

onMounted(async () => {
  await loadOverview()
  if (activeTab.value !== 'overview') {
    await handleTabChange(activeTab.value)
  }
})
</script>

<template>
  <div class="operations-center">
    <header class="page-header">
      <div>
        <p class="eyebrow">
          Operations & Diagnostics
        </p>
        <div class="title-line">
          <h1>运维诊断中心</h1>
          <el-tag v-if="activeTab !== 'overview' && readiness" :type="modeType(readiness.mode)" effect="dark" size="large">
            {{ modeLabel(readiness.mode) }}
          </el-tag>
        </div>
        <p class="subtitle">
          集中发现问题、执行维护、跳转处理、重新验证并保留操作记录。
        </p>
      </div>
      <div class="header-actions">
        <el-button v-if="canManage" :loading="loading.report" @click="downloadDiagnosticReport">
          导出诊断报告
        </el-button>
        <el-button v-if="activeTab === 'overview'" :loading="loading.overview" :disabled="loading.diagnostics || loading.maintenance" @click="loadOverview">
          刷新看板
        </el-button>
        <el-button v-if="canManage" type="primary" :loading="loading.diagnostics" :disabled="loading.overview" @click="runFullDiagnostic">
          一键全面体检
        </el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="diagnostics-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="运维总览" name="overview">
        <div class="overview-dashboard" :aria-busy="loading.overview">
          <el-alert
            v-if="overviewFailed"
            :title="overview ? '刷新失败，当前为上次成功数据' : '无法获取状态'"
            type="warning"
            :closable="false"
            show-icon
          >
            请重试刷新看板。若服务不可达，请在服务器查看 Windows 服务日志。
          </el-alert>
          <el-skeleton v-if="!overview && loading.overview" :rows="4" />

          <template v-if="overview">
            <el-alert
              :title="overviewFailed ? `上次记录：${modeLabel(overview.readiness.mode)}` : modeLabel(overview.readiness.mode)"
              :type="overviewFailed ? 'info' : overview.readiness.mode === 'READ_ONLY_DEGRADED' ? 'error' : overview.readiness.readOnly ? 'warning' : 'success'"
              :closable="false"
              show-icon
            >
              <p v-if="!overviewFailed && overview.readiness.automaticReadOnly">
                自动只读降级：{{ criticalChecks[0] ? `${criticalChecks[0].name}检查未通过` : '关键依赖状态待确认' }}。
              </p>
              <p v-if="!overviewFailed && overview.readiness.maintenanceReadOnly">
                主动维护已开启，业务写入暂停。
              </p>
              <p v-if="!overviewFailed && !overview.readiness.readOnly">
                当前允许业务写入。
              </p>
              <div class="snapshot-times">
                <span>总览快照 {{ formatTime(overview.generatedAt) }}</span>
                <span>依赖检查 {{ overviewChecks.length ? formatTime(overview.readiness.checkedAt) : '等待首次检查' }}</span>
              </div>
            </el-alert>

            <section class="mrr-metric-grid overview-metrics" aria-label="核心指标">
              <MrrMetricCard :label="overviewFailed ? '上次运行模式' : '运行模式'" icon="i-lucide:activity" compact-value :value="modeLabel(overview.readiness.mode)" :value-tone="modeType(overview.readiness.mode)" />
              <MrrMetricCard
                label="待处理检查"
                icon="i-lucide:clipboard-check"
                tone="amber"
                compact-value
                :value="overviewChecks.length ? formatNumber(failedReadinessChecks.length) : '待检查'"
                :note="overviewChecks.length ? `严重 ${criticalChecks.length} · 警告 ${failedReadinessChecks.length - criticalChecks.length}` : undefined"
              />
              <MrrMetricCard
                label="活跃导出任务"
                icon="i-lucide:list-checks"
                tone="teal"
                compact-value
                :value="tasksAvailable ? formatNumber(overview.taskSummary.active) : '不可用'"
                :note="tasksAvailable ? `等待 ${formatNumber(taskCounts.PENDING)} · 处理中 ${formatNumber(taskCounts.PROCESSING)}` : '暂未取得统计数据'"
              />
              <MrrMetricCard
                label="近 24 小时 5xx"
                icon="i-lucide:triangle-alert"
                compact-value
                :value="overview.recentServerErrorsAvailable ? formatNumber(overview.recentServerErrors) : '不可用'"
                :value-tone="overview.recentServerErrorsAvailable && overview.recentServerErrors > 0 ? 'danger' : undefined"
              >
                <template v-if="canViewLogs" #extra>
                  <el-button text type="primary" @click="navigate('/logs')">
                    查看日志
                  </el-button>
                </template>
              </MrrMetricCard>
            </section>

            <div class="two-column-layout">
              <MrrSectionCard title="待处理问题" description="严重问题优先">
                <el-empty
                  v-if="failedReadinessChecks.length === 0"
                  :description="overviewChecks.length ? '最近检查无未通过项目' : '等待首次依赖检查'"
                  :image-size="60"
                />
                <div v-else class="issue-list">
                  <div v-for="check in failedReadinessChecks" :key="check.code" class="issue-row">
                    <div>
                      <strong>{{ check.name }}</strong>
                      <p>{{ check.message }}</p>
                    </div>
                    <el-tag :type="check.severity === 'CRITICAL' ? 'danger' : 'warning'">
                      {{ check.severity === 'CRITICAL' ? '严重' : '警告' }}
                    </el-tag>
                    <el-button v-if="canManage" text type="primary" @click="selectTab('diagnostics')">
                      查看体检
                    </el-button>
                  </div>
                </div>
                <p v-if="failedReadinessChecks.length && !canManage" class="overview-hint">
                  请联系有运维管理权限的人员执行全面体检。
                </p>
              </MrrSectionCard>

              <MrrSectionCard title="后端运行">
                <el-descriptions :column="1" size="small">
                  <el-descriptions-item label="应用版本">
                    {{ overview.runtime.applicationVersion || '未知' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="启动时间">
                    {{ formatTime(overview.runtime.startedAt) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="运行时长">
                    {{ formatDuration(overview.runtime.uptimeMs) }}
                  </el-descriptions-item>
                </el-descriptions>
                <p>JVM 堆内存 {{ formatBytes(overview.runtime.heapUsedBytes) }} / {{ overview.runtime.heapMaxBytes > 0 ? formatBytes(overview.runtime.heapMaxBytes) : '上限未知' }}</p>
                <el-progress v-if="overview.runtime.heapMaxBytes > 0" aria-label="JVM 堆内存使用率" :percentage="heapUsage" />
              </MrrSectionCard>

              <MrrSectionCard title="依赖检查" description="最近一次部署就绪检查">
                <el-table :data="overviewChecks" empty-text="等待首次依赖检查">
                  <el-table-column prop="name" label="检查项" />
                  <el-table-column label="状态" width="100">
                    <template #default="{ row }">
                      <el-tag :type="row.passed ? 'success' : row.severity === 'CRITICAL' ? 'danger' : 'warning'">
                        {{ row.passed ? '通过' : '未通过' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </MrrSectionCard>

              <MrrSectionCard title="导出任务状态" description="横条表示所列四种状态中的占比">
                <template v-if="canViewTasks" #actions>
                  <el-button text type="primary" @click="selectTab('tasks')">
                    查看任务
                  </el-button>
                </template>
                <div v-if="tasksAvailable" class="overview-task-states">
                  <div v-for="state in taskStates" :key="state.code">
                    <div class="task-state-label">
                      <span>{{ state.label }}</span>
                      <strong>{{ formatNumber(taskCounts[state.code]) }}</strong>
                    </div>
                    <el-progress
                      :aria-label="`${state.label}任务占比`"
                      :percentage="taskTotal ? Math.round((taskCounts[state.code] || 0) / taskTotal * 100) : 0"
                      :show-text="false"
                      :status="state.status"
                    />
                  </div>
                </div>
                <p v-else>
                  暂未取得导出任务统计。
                </p>
              </MrrSectionCard>
            </div>

            <MrrSectionCard v-if="quickLinks.length" title="运维快捷入口">
              <div class="overview-links">
                <el-button v-for="link in quickLinks" :key="link.path" plain @click="navigate(link.path)">
                  {{ link.label }}
                </el-button>
              </div>
            </MrrSectionCard>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane label="全面体检" name="diagnostics">
        <div class="toolbar-row">
          <span>执行数据库、迁移、存储、备份、运行时、任务和错误检查，并给出处理入口。</span>
          <el-button v-if="canManage" type="primary" :loading="loading.diagnostics" :disabled="loading.overview" @click="runFullDiagnostic">
            {{ diagnosticRun ? '重新体检' : '开始全面体检' }}
          </el-button>
        </div>
        <el-empty v-if="!diagnosticRun" :description="canManage ? '点击“开始全面体检”执行实时检查' : '执行体检需要运维管理权限'" :image-size="96" />
        <template v-else>
          <section class="mrr-metric-grid mrr-metric-grid--four metric-row">
            <MrrMetricCard label="检查总数" :value="diagnosticRun.summary.total" />
            <MrrMetricCard label="通过" value-tone="success" :value="diagnosticRun.summary.passed" />
            <MrrMetricCard label="严重故障" value-tone="danger" :value="diagnosticRun.summary.critical" />
            <MrrMetricCard label="警告" value-tone="warning" :value="diagnosticRun.summary.warnings" />
          </section>
          <div class="diagnostic-list">
            <el-card v-for="check in diagnosticRun.checks" :key="check.code" shadow="never" class="check-card">
              <div class="check-row">
                <div class="check-copy">
                  <div class="check-title-line">
                    <strong>{{ check.name }}</strong>
                    <code>{{ check.code }}</code>
                  </div>
                  <p>{{ check.message }}</p>
                  <div v-if="!check.passed" class="suggestion-box">
                    <strong>处理建议</strong>
                    <span>{{ check.suggestion }}</span>
                  </div>
                </div>
                <div class="check-actions">
                  <el-tag :type="check.passed ? 'success' : check.severity === 'CRITICAL' ? 'danger' : 'warning'">
                    {{ check.passed ? '通过' : check.severity }}
                  </el-tag>
                  <el-button v-if="!check.passed && check.actionPath" size="small" @click="navigate(check.actionPath)">
                    {{ check.actionLabel }}
                  </el-button>
                </div>
              </div>
              <details v-if="Object.keys(check.details || {}).length" class="details-panel">
                <summary>查看检测详情</summary>
                <pre class="detail-json">{{ JSON.stringify(check.details, null, 2) }}</pre>
              </details>
            </el-card>
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="图片来源诊断" name="image">
        <el-card shadow="never">
          <el-form :model="imageForm" inline class="diagnosis-form">
            <el-form-item label="病案号">
              <el-input v-model="imageForm.bah" clearable placeholder="输入病案号" />
            </el-form-item>
            <el-form-item label="上架号">
              <el-input v-model="imageForm.sjh" clearable placeholder="输入上架号" />
            </el-form-item>
            <el-form-item label="图片 ID">
              <el-input-number v-model="imageForm.imageId" :min="1" controls-position="right" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading.image" @click="runImageDiagnosis">
                开始诊断
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <template v-if="imageDiagnosis">
          <section class="mrr-metric-grid mrr-metric-grid--three metric-row">
            <MrrMetricCard
              label="诊断结果"
              compact-value
              :value="imageDiagnosis.found ? '找到图片' : '未找到图片'"
              :value-tone="imageDiagnosis.found ? 'success' : 'danger'"
            />
            <MrrMetricCard label="最终来源" compact-value :value="imageDiagnosis.selectedSource || '-'" />
            <MrrMetricCard label="回退原因" compact-value :value="imageDiagnosis.fallbackReason || '未发生回退'" />
          </section>
          <el-card shadow="never" class="section-card">
            <template #header>
              解析与回退过程
            </template>
            <el-timeline>
              <el-timeline-item v-for="step in imageDiagnosis.steps" :key="step.code" :type="step.success ? 'success' : 'danger'" :hollow="!step.success">
                <div class="timeline-title">
                  <strong>{{ step.code }}</strong><span>{{ step.message }}</span>
                </div>
                <pre v-if="Object.keys(step.details || {}).length" class="detail-json">{{ JSON.stringify(step.details, null, 2) }}</pre>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </template>
      </el-tab-pane>

      <el-tab-pane label="病案数据完整性" name="integrity">
        <div class="toolbar-row">
          <span>指标基于后台生成的最近一次完整性快照，避免每次打开页面扫描大表。</span>
          <el-button :loading="loading.integrity" @click="loadIntegrity">
            刷新
          </el-button>
        </div>
        <el-alert v-if="integrity && integrity.status !== 'READY'" title="完整性快照尚未就绪或检查失败，不能判断为无异常" type="warning" :closable="false" />
        <template v-if="integrity?.status === 'READY'">
          <section class="mrr-metric-grid mrr-metric-grid--three metric-row">
            <MrrMetricCard label="样本 archive_id 覆盖率" :value="formatPercent(integrity.archiveCoverage)" />
            <MrrMetricCard label="样本 OSS 覆盖率" :value="formatPercent(integrity.ossCoverage)" />
            <MrrMetricCard label="样本缺失上架号" :value="formatNumber(integrity.missingSjh)" />
            <MrrMetricCard label="样本断链记录" value-tone="danger" :value="formatNumber(integrity.brokenLinks)" />
            <MrrMetricCard label="重复病案组" value-tone="danger" :value="formatNumber(integrity.duplicateArchiveGroups)" />
            <MrrMetricCard label="样本有效扫描图片" :value="formatNumber(integrity.totalActiveScans)" :note="integrity.sampleScope" />
          </section>
          <el-card shadow="never" class="section-card">
            <template #header>
              各业务表关联情况
            </template>
            <el-table :data="integrity.tables" border>
              <el-table-column prop="table" label="数据表" min-width="180" />
              <el-table-column prop="total" label="统计范围内总数" min-width="130" />
              <el-table-column prop="linked" label="已关联" min-width="130" />
              <el-table-column prop="unlinked" label="未关联" min-width="130" />
              <el-table-column label="覆盖率" min-width="130">
                <template #default="scope">
                  {{ formatPercent(scope.row.coverage) }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>
      </el-tab-pane>

      <el-tab-pane label="后台任务" name="tasks">
        <div class="toolbar-row">
          <div class="toolbar-actions">
            <el-select v-model="exportStatus" clearable placeholder="全部状态" class="export-status-filter">
              <el-option v-for="status in ['PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED']" :key="status" :label="status" :value="status" />
            </el-select>
            <el-button plain @click="navigate('/oss-migration')">
              OSS 迁移管理
            </el-button>
          </div>
          <el-button :loading="loading.tasks" @click="loadTasks">
            刷新
          </el-button>
        </div>
        <section class="mrr-metric-grid mrr-metric-grid--three task-summary">
          <MrrMetricCard label="等待与处理中" value-tone="warning" :value="tasksAvailable ? formatNumber(overview?.taskSummary.active) : '不可用'" />
          <MrrMetricCard label="失败" value-tone="danger" :value="tasksAvailable ? formatNumber(overview?.taskSummary.failed) : '不可用'" />
          <MrrMetricCard label="已完成" value-tone="success" :value="tasksAvailable ? formatNumber(taskCounts.SUCCESS) : '不可用'" />
        </section>
        <el-table :data="filteredExportJobs" border height="600" class="section-card">
          <el-table-column prop="created_at" label="创建时间" width="180">
            <template #default="scope">
              {{ formatTime(scope.row.created_at) }}
            </template>
          </el-table-column>
          <el-table-column prop="format" label="格式" width="80" />
          <el-table-column prop="scope" label="生成条件" min-width="150" />
          <el-table-column label="病案范围" min-width="180">
            <template #default="scope">
              {{ scope.row.bah || '-' }} / {{ scope.row.sjh || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="条数" width="100">
            <template #default="scope">
              {{ scope.row.processed_count }}/{{ scope.row.planned_count }}
            </template>
          </el-table-column>
          <el-table-column label="大小" width="110">
            <template #default="scope">
              {{ formatBytes(scope.row.output_bytes || scope.row.estimated_bytes) }}
            </template>
          </el-table-column>
          <el-table-column prop="sha256" label="SHA-256" min-width="220" show-overflow-tooltip />
          <el-table-column label="过期时间" width="180">
            <template #default="scope">
              {{ formatTime(scope.row.expires_at) }}
            </template>
          </el-table-column>
          <el-table-column prop="download_count" label="下载次数" width="100" />
          <el-table-column prop="error_message" label="失败原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="110" fixed="right">
            <template #default="scope">
              <el-tag :type="statusType(scope.row.status)">
                {{ scope.row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="权限矩阵" name="permissions">
        <div class="toolbar-row">
          <el-input v-model="endpointKeyword" clearable placeholder="搜索接口、操作或权限" class="endpoint-filter" />
          <div class="toolbar-actions">
            <el-tag v-if="permissionMatrix?.previousVersion" type="info">
              上一版本：{{ permissionMatrix.previousVersion }}
            </el-tag>
            <el-tag v-if="permissionMatrix?.diff.available" :type="permissionMatrix.diff.changes.length ? 'warning' : 'success'">
              变化 {{ permissionMatrix.diff.changes.length }} 项
            </el-tag>
            <el-button @click="savePermissionSnapshot">
              保存版本快照
            </el-button>
            <el-button :loading="loading.permissions" @click="loadPermissions">
              刷新
            </el-button>
          </div>
        </div>
        <el-table :data="filteredEndpoints" border height="600">
          <el-table-column prop="method" label="方法" width="90" fixed />
          <el-table-column prop="path" label="接口" min-width="260" show-overflow-tooltip fixed />
          <el-table-column prop="operation" label="操作" min-width="230" show-overflow-tooltip />
          <el-table-column prop="policy" label="策略" width="160" />
          <el-table-column label="所需权限" min-width="220">
            <template #default="scope">
              {{ scope.row.requiredPermissions.join(', ') || '-' }}
            </template>
          </el-table-column>
          <el-table-column v-for="role in permissionMatrix?.roles || []" :key="role.code" :label="role.name || role.code" width="110" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.roleAccess[role.code] ? 'success' : 'info'" effect="plain">
                {{ scope.row.roleAccess[role.code] ? '允许' : '拒绝' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-card v-if="permissionMatrix?.diff.available && permissionMatrix.diff.changes.length" shadow="never" class="section-card">
          <template #header>
            与上一版本的差异
          </template>
          <el-table :data="permissionMatrix.diff.changes" border>
            <el-table-column prop="type" label="变化" width="110" />
            <el-table-column prop="key" label="接口" min-width="300" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="维护模式" name="maintenance">
        <div class="toolbar-row">
          <span>主动维护用于版本升级、数据库维护和批量修复；自动降级由关键依赖检查独立控制。</span>
          <el-button :loading="loading.maintenance" @click="loadMaintenance(true)">
            重新检查
          </el-button>
        </div>
        <el-alert
          :title="maintenance?.enabled ? '主动维护模式已启用' : '主动维护模式未启用'"
          :type="maintenance?.enabled ? 'warning' : 'success'"
          :closable="false"
          show-icon
        >
          <template #default>
            <p v-if="maintenance?.enabled">
              原因：{{ maintenance.reason || '计划维护' }}；操作人：{{ maintenance.updatedBy || '-' }}；时间：{{ formatTime(maintenance.updatedAt) }}
            </p>
            <p v-else>
              系统是否允许写入仍取决于自动部署就绪检查。
            </p>
          </template>
        </el-alert>
        <section class="mrr-metric-grid mrr-metric-grid--three section-card">
          <MrrMetricCard label="有效模式" compact-value :value="modeLabel(readiness?.mode)" :value-tone="modeType(readiness?.mode)" />
          <MrrMetricCard
            label="自动降级"
            compact-value
            :value="readiness?.automaticReadOnly ? '已触发' : '未触发'"
            :value-tone="readiness?.automaticReadOnly ? 'danger' : 'success'"
          />
          <MrrMetricCard
            label="主动维护"
            compact-value
            :value="maintenance?.enabled ? '已启用' : '未启用'"
            :value-tone="maintenance?.enabled ? 'warning' : 'success'"
          />
        </section>
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <div><strong>维护操作</strong><p>所有操作通过现有访问日志保留 requestId、用户和结果</p></div>
              <div class="toolbar-actions">
                <el-button v-if="!maintenance?.enabled" type="warning" @click="enableMaintenance">
                  进入维护模式
                </el-button>
                <el-button v-else type="success" @click="disableMaintenance">
                  退出主动维护
                </el-button>
              </div>
            </div>
          </template>
          <el-alert v-if="readiness?.automaticReadOnly" title="当前同时存在自动只读降级。即使退出主动维护，系统仍会保持只读。" type="error" :closable="false" show-icon />
          <div class="issue-list maintenance-checks">
            <div v-for="check in readiness?.checks || []" :key="check.code" class="issue-row">
              <div><strong>{{ check.name }}</strong><p>{{ check.message }}</p></div>
              <el-tag :type="check.passed ? 'success' : check.severity === 'CRITICAL' ? 'danger' : 'warning'">
                {{ check.passed ? '通过' : check.severity }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="操作审计" name="audit">
        <div class="toolbar-row">
          <span>展示运维中心的写操作、失败请求和对应 requestId。</span>
          <div class="toolbar-actions">
            <el-button plain @click="navigate('/logs')">
              打开完整日志
            </el-button>
            <el-button :loading="loading.audit" @click="loadAudit">
              刷新
            </el-button>
          </div>
        </div>
        <el-table :data="auditEntries" border height="640">
          <el-table-column prop="access_time" label="时间" width="180">
            <template #default="scope">
              {{ formatTime(scope.row.access_time) }}
            </template>
          </el-table-column>
          <el-table-column prop="username" label="操作人" width="130" />
          <el-table-column prop="client_ip" label="来源 IP" width="150" />
          <el-table-column prop="method" label="方法" width="90" />
          <el-table-column prop="request_uri" label="运维操作" min-width="280" show-overflow-tooltip />
          <el-table-column prop="request_id" label="requestId" min-width="210" show-overflow-tooltip />
          <el-table-column prop="execute_time" label="耗时(ms)" width="110" />
          <el-table-column prop="error_message" label="错误" min-width="180" show-overflow-tooltip />
          <el-table-column label="结果" width="100" fixed="right">
            <template #default="scope">
              <el-tag :type="responseType(scope.row.response_status)">
                {{ scope.row.response_status || '-' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
/* stylelint-disable order/properties-order, media-feature-range-notation */
.operations-center {
  min-height: 100%;
}

.page-header,
.title-line,
.header-actions,
.toolbar-row,
.toolbar-actions,
.card-header,
.check-row,
.issue-row,
.check-title-line {
  display: flex;
  align-items: center;
}

.page-header,
.toolbar-row,
.card-header,
.check-row,
.issue-row {
  justify-content: space-between;
}

.page-header {
  gap: 20px;
  margin-bottom: 16px;
}

.eyebrow {
  margin: 0 0 5px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.title-line,
.header-actions,
.toolbar-actions,
.check-title-line {
  gap: 10px;
}

.page-header > div > p:last-child,
.toolbar-row,
.card-header p,
.issue-row p,
.check-copy > p {
  color: var(--el-text-color-secondary);
}

.page-header > div > p:last-child,
.card-header p,
.issue-row p,
.check-copy > p {
  margin: 4px 0 0;
}

.diagnostics-tabs {
  padding: 0 16px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-card);
  background: var(--el-bg-color);
}

.overview-dashboard,
.overview-task-states {
  display: grid;
  gap: 16px;
}

.snapshot-times,
.task-state-label {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

.task-state-label {
  justify-content: space-between;
  margin-block-end: 6px;
}

.overview-hint {
  margin-block-start: 12px;
}

.overview-metrics {
  margin-top: 16px;
}

/* 指标组统一使用 MrrMetricCard，只保留与页面节奏相关的间距。 */
.metric-row {
  margin-top: 16px;
}

.two-column-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 16px;
}

.section-card,
.diagnostic-list,
.task-summary {
  margin-top: 16px;
}

.card-header {
  gap: 16px;
}

.card-header > div:first-child {
  min-width: 0;
}

.issue-list,
.diagnostic-list {
  display: grid;
  gap: 10px;
}

.issue-row {
  gap: 16px;
  padding: 11px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.issue-row:last-child {
  border-bottom: 0;
}

.overview-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-links .el-button {
  margin: 0;
}

.export-status-filter {
  width: 160px;
}

.endpoint-filter {
  max-width: 360px;
}

.toolbar-row {
  gap: 16px;
  margin-bottom: 14px;
}

.toolbar-actions,
.header-actions {
  flex-wrap: wrap;
}

.check-card {
  border-left: 4px solid var(--el-border-color);
}

.check-copy {
  min-width: 0;
  flex: 1;
}

.check-title-line code {
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.check-actions {
  display: grid;
  flex: 0 0 auto;
  gap: 8px;
  justify-items: end;
}

.suggestion-box {
  display: grid;
  gap: 4px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--mrr-radius-md);
  background: var(--el-fill-color-light);
  font-size: 13px;
}

.details-panel {
  margin-top: 12px;
}

.details-panel summary {
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: 13px;
}

.detail-json {
  max-height: 240px;
  overflow: auto;
  margin: 10px 0 0;
  padding: 10px;
  border-radius: var(--mrr-radius-md);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.diagnosis-form {
  display: flex;
  flex-wrap: wrap;
}

.timeline-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.maintenance-checks {
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .two-column-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .page-header,
  .toolbar-row,
  .check-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions,
  .header-actions .el-button {
    width: 100%;
  }

  .overview-dashboard .issue-row {
    flex-wrap: wrap;
  }

  .check-actions {
    width: 100%;
    grid-auto-flow: column;
    justify-content: space-between;
    justify-items: stretch;
  }
}
</style>

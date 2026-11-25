<script setup lang="ts">
import type {
  ExportCenterJob,
  ImageContentVerification,
  ImageSourceDiagnosis,
  IntegritySummary,
  MaintenanceStatus,
  OperationAuditEntry,
  OperationsDiagnosticRun,
  OperationsOverview,
  PermissionMatrix,
  ReadinessSnapshot,
} from '@/api/modules/operations'
import { useElementSize } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
  verifyImageContent,
} from '@/api/modules/operations'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { actionDialog } from '@/ui/components/ActionDialog'
import { formatDateTime as formatTime } from '@/utils/display-format'
import { hasAnyPermission, hasPermission } from '@/utils/session'
import NginxConfigurationCard from './components/NginxConfigurationCard.vue'
import NginxRuntimeCard from './components/NginxRuntimeCard.vue'
import OperationsHistoryCard from './components/OperationsHistoryCard.vue'
import StorageCircuitHistoryCard from './components/StorageCircuitHistoryCard.vue'
import DeliveryStatusCard from './DeliveryStatusCard.vue'

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
  content: false,
  integrity: false,
  tasks: false,
  permissions: false,
  maintenance: false,
  audit: false,
})

const overview = ref<OperationsOverview>()
const overviewFailed = ref(false)
const dashboardRef = ref<HTMLElement>()
const { width: dashboardWidth } = useElementSize(dashboardRef)
const compactDashboard = computed(() => dashboardWidth.value > 0 && dashboardWidth.value < 850)
const networkChanges = ref<string[]>([])
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
const contentVerification = ref<ImageContentVerification>()
const contentError = ref('')
let contentGeneration = 0
const contentStatusLabels: Record<string, string> = {
  VERIFIED: '已有校验和匹配',
  READABLE_NO_BASELINE: '可读，缺少有效校验基线',
  SIZE_MISMATCH: '文件大小不匹配',
  CHECKSUM_MISMATCH: '校验和不匹配',
  EMPTY: '空文件',
  READ_FAILED: '读取或关闭失败',
  SOURCE_UNAVAILABLE: '来源不可用',
  MISSING_OR_UNREADABLE: '缺失或不可读',
  BUDGET_EXCEEDED: '超过读取预算，未完成',
  BUSY: '校验资源繁忙',
  TIMEOUT: '校验超时',
  INTERRUPTED: '校验中断',
}
function clearContentVerification() {
  contentGeneration++
  contentVerification.value = undefined
  contentError.value = ''
}
watch(() => imageForm.imageId, clearContentVerification, { flush: 'sync' })
watch(() => activeTab.value, clearContentVerification, { flush: 'sync' })
watch(() => [canManage.value, canViewTasks.value], clearContentVerification, { flush: 'sync' })
watch(() => useUserStore().token, clearContentVerification, { flush: 'sync' })
onBeforeUnmount(clearContentVerification)
async function runContentVerification() {
  const id = imageForm.imageId
  if (!canManage.value || !canViewTasks.value || !id || loading.content || loading.image) { return }
  clearContentVerification()
  const current = contentGeneration
  loading.content = true
  try {
    const result = await verifyImageContent(id)
    if (current === contentGeneration && result.imageId === id) { contentVerification.value = result }
  }
  catch {
    if (current === contentGeneration) { contentError.value = '影像正文校验失败，请重试' }
  }
  finally { loading.content = false }
}

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

const circuitLabels: Record<string, string> = { OPEN: '熔断冷却', HALF_OPEN_ACTIVE: '探针进行中', HALF_OPEN_READY: '等待探针', CLOSED: '未熔断', UNOBSERVED: '尚无请求' }
const circuitPriority: Record<string, number> = { OPEN: 0, HALF_OPEN_ACTIVE: 1, HALF_OPEN_READY: 2, CLOSED: 3, UNOBSERVED: 4 }
const networkSources = computed(() => Object.entries(overview.value?.networkRuntime || {}).map(([kind, source]) => ({
  kind,
  ...source,
  nodes: [...source.nodes].sort((a, b) => (circuitPriority[a.state] ?? 5) - (circuitPriority[b.state] ?? 5) || a.ordinal - b.ordinal),
})).sort((a, b) => (circuitPriority[a.nodes[0]?.state] ?? 5) - (circuitPriority[b.nodes[0]?.state] ?? 5) || b.waiting - a.waiting))
const storageHistory = computed(() => {
  const sources = [
    { kind: 'NAS', nodes: overview.value?.nasRuntime?.nodes || [] },
    ...networkSources.value,
  ]
  const nodes = sources.flatMap(source => source.nodes.map(node => ({ kind: source.kind, ...node })))
  const totals = nodes.reduce((acc, node) => ({
    opened: acc.opened + (node.history?.opened ?? 0),
    recovered: acc.recovered + (node.history?.recovered ?? 0),
    probes: acc.probes + (node.history?.probes ?? 0),
  }), { opened: 0, recovered: 0, probes: 0 })
  const events = nodes.flatMap(node => (node.history?.events || []).map(event => ({ ...event, kind: node.kind, ordinal: node.ordinal })))
    .sort((a, b) => b.at.localeCompare(a.at) || b.sequence - a.sequence)
  const end = Date.parse(overview.value?.generatedAt || '')
  const buckets = Array.from({ length: 6 }, (_, i) => {
    const from = end - (6 - i) * 300000
    const to = from + 300000
    const items = events.filter((event) => {
      const time = Date.parse(event.at)
      return time >= from && (i === 5 ? time <= to : time < to)
    })
    return { label: `${30 - i * 5}–${25 - i * 5} 分钟前`, opened: items.filter(event => event.type === 'OPENED').length, recovered: items.filter(event => event.type === 'RECOVERED').length }
  })
  return { ...totals, events: events.slice(0, 12), buckets, available: nodes.some(node => typeof node.history?.opened === 'number') }
})
const storageEventLabels: Record<string, string> = { OPENED: '进入熔断', PROBE_STARTED: '开始半开探针', METADATA_ONLY: '仅元数据成功', RECOVERED: '节点恢复' }
const storageEventAdvice: Record<string, string> = { OPENED: '检查源站、网络和实际占用；不要强制释放未关闭资源。', PROBE_STARTED: '等待探针实际结果，避免重复回源。', METADATA_ONLY: '仍需验证正文读取，不能据此判定恢复。', RECOVERED: '继续观察许可和后续读取，确认现场恢复。' }
const overviewChecks = computed(() => overview.value?.readiness.checks || [])
const indexCapacity = computed(() => overviewChecks.value.find(check => check.code === 'scan-code-key-indexes')?.details)
function capacityValue(key: string, bytes = false) {
  const value = indexCapacity.value?.[key]
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? (bytes ? formatBytes(value) : formatNumber(value)) : '未取得'
}
const indexState = computed(() => (({ READY: '已就绪', MISSING: '缺失或无效', BELOW_THRESHOLD: '低于建议阈值' } as Record<string, string>)[String(indexCapacity.value?.state)] || '未知'))
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
      const previous = overview.value?.networkRuntime
      networkChanges.value = Object.entries(snapshot.networkRuntime || {}).flatMap(([kind, source]) => {
        const before = previous?.[kind as keyof typeof previous]
        if (!before || before.nodes.length !== source.nodes.length) {
          return []
        }
        // Compare aggregate counts, not opaque ordinals across configuration changes.
        const states = ['OPEN', 'HALF_OPEN_ACTIVE', 'HALF_OPEN_READY']
        const count = (nodes: typeof source.nodes, state: string) => nodes.filter(node => node.state === state).length
        return states.some(state => count(before.nodes, state) !== count(source.nodes, state)) ? [kind] : []
      })
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
  const version = await actionDialog.prompt('请输入权限矩阵版本名称', '保存权限版本', {
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
  const reason = await actionDialog.prompt('请输入本次维护原因', '进入主动维护模式', {
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

async function disableMaintenance(event: MouseEvent) {
  const trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  try {
    await actionDialog.confirm(
      '关闭主动维护模式不会绕过数据库、磁盘、Nginx 或 OSS 故障导致的自动降级。是否继续？',
      '退出主动维护模式',
      { type: 'warning', confirmButtonText: '确认退出', cancelButtonText: '取消' },
    )
  }
  catch {
    // Keep the initiating control reachable after Escape, including Safari mouse activation.
    await nextTick()
    if (trigger?.isConnected) { trigger.focus({ preventScroll: true }) }
    return
  }
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
  <MrrPageShell class="operations-center">
    <MrrPageHeader
      eyebrow="Operations & Diagnostics"
      title="运维诊断中心"
      description="集中发现问题、执行维护、跳转处理、重新验证并保留操作记录。"
    >
      <template #badge>
        <el-tag v-if="activeTab !== 'overview' && readiness" :type="modeType(readiness.mode)" effect="dark" size="large">
          {{ modeLabel(readiness.mode) }}
        </el-tag>
      </template>
      <template #actions>
        <el-button v-if="canManage" :loading="loading.report" @click="downloadDiagnosticReport">
          导出诊断报告
        </el-button>
        <el-button v-if="activeTab === 'overview'" :loading="loading.overview" :disabled="loading.diagnostics || loading.maintenance" @click="loadOverview">
          刷新看板
        </el-button>
        <el-button v-if="canManage" type="primary" :loading="loading.diagnostics" :disabled="loading.overview" @click="runFullDiagnostic">
          一键全面体检
        </el-button>
      </template>
    </MrrPageHeader>

    <el-tabs v-model="activeTab" class="diagnostics-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="运维总览" name="overview">
        <div ref="dashboardRef" class="overview-dashboard" :class="{ 'overview-dashboard--compact': compactDashboard }" :aria-busy="loading.overview">
          <el-alert
            v-if="overviewFailed && overview"
            title="刷新失败，当前为上次成功数据"
            type="warning"
            :closable="false"
            show-icon
          >
            请重试刷新看板。若服务不可达，请在服务器查看 Windows 服务日志。
          </el-alert>
          <AppLoading v-if="!overview && loading.overview" type="card" :cols="3" />
          <AppError v-else-if="!overview && overviewFailed" message="无法获取状态，请重试刷新看板。若服务不可达，请在服务器查看 Windows 服务日志。" @retry="loadOverview" />

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

            <OperationsHistoryCard v-if="activeTab === 'overview'" />
            <StorageCircuitHistoryCard v-if="activeTab === 'overview'" />
            <NginxConfigurationCard v-if="activeTab === 'overview'" />
            <NginxRuntimeCard v-if="activeTab === 'overview'" />

            <MrrSectionCard v-if="indexCapacity" title="编号索引与容量" description="最近一次数据库目录快照 · 非服务器磁盘余量">
              <div class="mrr-metric-grid">
                <MrrMetricCard label="索引状态" compact-value :value="indexState" />
                <MrrMetricCard label="估算扫描记录" compact-value :value="capacityValue('estimatedRows')" />
                <MrrMetricCard label="扫描表及索引" compact-value :value="capacityValue('totalBytes', true)" />
                <MrrMetricCard label="两索引容量参考" compact-value :value="capacityValue('twoIndexReferenceBytes', true)" />
              </div>
              <p class="overview-hint">
                容量参考按三千万合成实测换算，不是可用空间或峰值上限；部署前需在服务器核验各卷、WAL 和临时空间。
              </p>
            </MrrSectionCard>

            <MrrSectionCard v-if="overview.nasRuntime" title="NAS 节点运行状态" description="本进程被动观测 · 不主动探测网络或文件">
              <p>
                全局许可 {{ overview.nasRuntime.inUse }} / {{ overview.nasRuntime.limit }}<template v-if="overview.nasRuntime.waiting !== undefined">
                  · 等待约 {{ overview.nasRuntime.waiting }}
                </template>
              </p>
              <AppEmpty v-if="!overview.nasRuntime.nodes.length" description="未配置 NAS 节点" />
              <div v-else class="issue-list">
                <div v-for="node in overview.nasRuntime.nodes" :key="node.ordinal" class="issue-row">
                  <div><strong>节点 {{ node.ordinal }}</strong><p>实际占用 {{ node.inUse }} / {{ node.limit }}</p></div>
                  <el-tag :type="node.state === 'OPEN' ? 'danger' : node.state === 'CLOSED' ? 'info' : 'warning'">
                    {{ ({ CLOSED: '未熔断', OPEN: '熔断冷却', HALF_OPEN_READY: '等待探针', HALF_OPEN_ACTIVE: '探针进行中' })[node.state] }}
                  </el-tag>
                </div>
              </div>
              <p class="overview-hint">
                编号按配置键排序，不显示节点名或路径；未熔断不等于现场可用。
              </p>
            </MrrSectionCard>

            <MrrSectionCard v-if="overview.networkRuntime" title="网络影像来源" description="异常节点优先 · 应用许可与 OSS SDK 采样分开显示">
              <template v-if="overviewFailed">
                <el-alert title="以下为上次成功快照" type="info" :closable="false" />
              </template>
              <p v-if="!overviewFailed && networkChanges.length" class="overview-hint" role="status">
                较上次成功快照，熔断或半开数量变化：{{ networkChanges.join('、') }}
              </p>
              <div class="issue-list">
                <div v-for="source in networkSources" :key="source.kind" class="issue-row network-runtime-row">
                  <div>
                    <strong>{{ source.kind }}</strong>
                    <p>应用许可 {{ source.inUse }} / {{ source.limit }} · 等待约 {{ source.waiting }}</p>
                    <p v-if="!source.nodes.length">
                      未配置节点
                    </p>
                    <p v-if="source.kind === 'OSS'">
                      <template v-if="source.poolSample?.sampledAt">
                        SDK 最近采样：租用 {{ source.poolSample.leased }} / {{ source.poolSample.max }} · 可用 {{ source.poolSample.available }} · 等待 {{ source.poolSample.pending }} · {{ formatTime(source.poolSample.sampledAt) }}
                      </template>
                      <template v-else>
                        SDK 池尚无有效采样
                      </template>
                    </p>
                  </div>
                  <div class="network-node-tags">
                    <el-tag v-for="node in source.nodes" :key="node.ordinal" :type="node.state === 'OPEN' ? 'danger' : node.state.startsWith('HALF_OPEN') ? 'warning' : 'info'">
                      节点 {{ node.ordinal }} · {{ circuitLabels[node.state] || '未知' }}
                    </el-tag>
                  </div>
                </div>
              </div>
              <p class="overview-hint">
                未熔断不等于现场健康。HTTP/Nginx 的 JDK 客户端不提供连接池内部实时指标，以上许可并非 TCP 连接数；OSS 为请求时采样，非实时池状态。
              </p>
              <el-button v-if="canViewLogs" text type="primary" @click="navigate('/logs')">
                查看运行日志
              </el-button>
              <el-button v-if="canManage" text type="primary" @click="selectTab('diagnostics')">
                查看体检
              </el-button>
            </MrrSectionCard>

            <MrrSectionCard v-if="storageHistory.available" title="存储异常事件" description="进程内累计计数与最近事件 · 重启清空，非持久审计">
              <div class="mrr-metric-grid">
                <MrrMetricCard label="进入熔断次数" compact-value :value="storageHistory.opened" />
                <MrrMetricCard label="半开探针次数" compact-value :value="storageHistory.probes" />
                <MrrMetricCard label="恢复次数" compact-value :value="storageHistory.recovered" />
              </div>
              <div v-if="storageHistory.events.length" class="storage-trend">
                <p class="overview-hint">
                  最近 30 分钟已保留事件（每 5 分钟，非完整统计）
                </p>
                <div v-for="bucket in storageHistory.buckets" :key="bucket.label" class="storage-trend-row">
                  <span>{{ bucket.label }}</span>
                  <el-tag type="danger" effect="plain">
                    熔断 {{ bucket.opened }}
                  </el-tag>
                  <el-tag type="success" effect="plain">
                    恢复 {{ bucket.recovered }}
                  </el-tag>
                </div>
              </div>
              <AppEmpty v-if="!storageHistory.events.length" description="本进程暂无熔断事件" />
              <div v-else class="issue-list">
                <div v-for="event in storageHistory.events" :key="`${event.kind}-${event.ordinal}-${event.sequence}`" class="issue-row">
                  <div>
                    <strong>{{ event.kind }} 节点 {{ event.ordinal }} · {{ storageEventLabels[event.type] }}</strong>
                    <p>{{ formatTime(event.at) }} · {{ storageEventAdvice[event.type] }}</p>
                  </div>
                  <el-tag :type="event.type === 'OPENED' ? 'danger' : event.type === 'RECOVERED' ? 'success' : 'warning'">
                    {{ storageEventLabels[event.type] }}
                  </el-tag>
                </div>
              </div>
              <p class="overview-hint">
                每节点保留最近 16 条，合计展示最近 12 条。累计次数不是故障率；重启前记录和未采样事件不可恢复。
              </p>
            </MrrSectionCard>

            <div class="two-column-layout">
              <MrrSectionCard title="待处理问题" description="严重问题优先">
                <AppEmpty
                  v-if="failedReadinessChecks.length === 0"
                  :icon="overviewChecks.length ? 'i-lucide:circle-check' : 'i-lucide:hourglass'"
                  :description="overviewChecks.length ? '最近检查无未通过项目' : '等待首次依赖检查'"
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

            <DeliveryStatusCard :mode="overview.imageDeliveryMode" :mode-stale="overviewFailed" />

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
        <AppEmpty
          v-if="!diagnosticRun"
          :icon="canManage ? 'i-lucide:stethoscope' : 'i-lucide:lock'"
          :description="canManage ? '点击“开始全面体检”执行实时检查' : '执行体检需要运维管理权限'"
        />
        <template v-else>
          <section class="mrr-metric-grid mrr-metric-grid--four metric-row">
            <MrrMetricCard label="检查总数" :value="diagnosticRun.summary.total" />
            <MrrMetricCard label="通过" value-tone="success" :value="diagnosticRun.summary.passed" />
            <MrrMetricCard label="严重故障" value-tone="danger" :value="diagnosticRun.summary.critical" />
            <MrrMetricCard label="警告" value-tone="warning" :value="diagnosticRun.summary.warnings" />
          </section>
          <div class="diagnostic-list">
            <el-card
              v-for="check in diagnosticRun.checks"
              :key="check.code"
              shadow="never"
              class="check-card"
              :class="check.passed ? 'check-card--passed' : check.severity === 'CRITICAL' ? 'check-card--critical' : 'check-card--warning'"
            >
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
        <!-- 条件区补标题与说明，与本页其他分区卡同一层级；原来是一张无标题的 el-card，只有三个输入框。 -->
        <MrrSectionCard title="诊断目标" description="填写病案号、上架号或图片 ID 中的任一项，查看该影像的来源解析与回退过程。">
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
            <el-form-item v-if="canManage && canViewTasks">
              <el-button :disabled="!imageForm.imageId || loading.image" :loading="loading.content" @click="runContentVerification">
                校验单张正文
              </el-button>
            </el-form-item>
          </el-form>
        </MrrSectionCard>
        <MrrSectionCard v-if="contentVerification || contentError" title="单张正文校验" description="最多读取 8 MiB（另读 1 字节判断超限），仅反映本次受控来源，不代表整案完整性。">
          <el-alert v-if="contentError" type="error" :title="contentError" :closable="false" />
          <template v-else-if="contentVerification">
            <el-tag :type="contentVerification.status === 'VERIFIED' ? 'success' : 'warning'">
              {{ contentStatusLabels[contentVerification.status] || '未知结果' }}
            </el-tag>
            <p class="overview-hint">
              读取 {{ formatNumber(contentVerification.bytesRead) }} 字节 · {{ formatTime(contentVerification.checkedAt) }}
            </p>
          </template>
        </MrrSectionCard>
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
          <MrrSectionCard class="section-card" title="解析与回退过程">
            <el-timeline>
              <el-timeline-item v-for="step in imageDiagnosis.steps" :key="step.code" :type="step.success ? 'success' : 'danger'" :hollow="!step.success">
                <div class="timeline-title">
                  <strong>{{ step.code }}</strong><span>{{ step.message }}</span>
                </div>
                <pre v-if="Object.keys(step.details || {}).length" class="detail-json">{{ JSON.stringify(step.details, null, 2) }}</pre>
              </el-timeline-item>
            </el-timeline>
          </MrrSectionCard>
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
          <MrrSectionCard class="section-card" title="各业务表关联情况">
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
          </MrrSectionCard>
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
        <MrrSectionCard v-if="permissionMatrix?.diff.available && permissionMatrix.diff.changes.length" class="section-card" title="与上一版本的差异">
          <el-table :data="permissionMatrix.diff.changes" border>
            <el-table-column prop="type" label="变化" width="110" />
            <el-table-column prop="key" label="接口" min-width="300" />
          </el-table>
        </MrrSectionCard>
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
        <MrrSectionCard class="section-card" title="维护操作" description="所有操作通过现有访问日志保留 requestId、用户和结果">
          <template #actions>
            <div class="toolbar-actions">
              <el-button v-if="!maintenance?.enabled" type="warning" @click="enableMaintenance">
                进入维护模式
              </el-button>
              <el-button v-else type="success" @click="disableMaintenance">
                退出主动维护
              </el-button>
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
        </MrrSectionCard>
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
  </MrrPageShell>
</template>

<style scoped>
/* stylelint-disable order/properties-order, media-feature-range-notation */
.operations-center {
  min-height: 100%;
}

.toolbar-row,
.toolbar-actions,
.check-row,
.issue-row,
.check-title-line {
  display: flex;
  align-items: center;
}

.toolbar-row,
.check-row,
.issue-row {
  justify-content: space-between;
}

.toolbar-actions,
.check-title-line {
  gap: var(--mrr-space-2);
}

.toolbar-row,
.issue-row p,
.check-copy > p {
  color: var(--el-text-color-secondary);
}

.issue-row p,
.check-copy > p {
  margin: var(--mrr-space-1) 0 0;
}

.diagnostics-tabs {
  padding: 0 var(--mrr-card-inset) var(--mrr-card-inset);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
  background: var(--el-bg-color);
}

.overview-dashboard,
.overview-task-states {
  display: grid;
  gap: var(--mrr-space-4);
}

.snapshot-times,
.task-state-label {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-1) var(--mrr-space-4);
}

.task-state-label {
  justify-content: space-between;
  margin-block-end: var(--mrr-space-2);
}

.overview-hint {
  margin-block-start: var(--mrr-space-3);
}

.overview-metrics {
  margin-top: var(--mrr-space-4);
}

/* 指标组统一使用 MrrMetricCard，只保留与页面节奏相关的间距。 */
.metric-row {
  margin-top: var(--mrr-space-4);
}

.two-column-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: var(--mrr-space-4);
}

.two-column-layout > * {
  min-width: 0;
}

.overview-dashboard--compact .two-column-layout {
  grid-template-columns: minmax(0, 1fr);
}

.section-card,
.diagnostic-list,
.task-summary {
  margin-top: var(--mrr-space-4);
}

.issue-list,
.diagnostic-list {
  display: grid;
  gap: var(--mrr-space-3);
}

.issue-row {
  gap: var(--mrr-space-4);
  padding: var(--mrr-space-3) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.issue-row:last-child {
  border-bottom: 0;
}

.overview-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
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
  gap: var(--mrr-space-4);
  margin-bottom: var(--mrr-space-3);
}

.toolbar-actions {
  flex-wrap: wrap;
}

/*
 * 左侧状态条按检查结果着色：原来所有检查都是同一条灰边，几十项检查里要逐张看标签才知道哪项失败。
 * 状态仍由右侧文字标签表达，色条只是辅助扫读（DESIGN.md §1：状态不只靠颜色）。宽度与本页其他强调条一致取 3px。
 */
.check-card {
  border-left: 3px solid var(--mrr-border-strong);
}

.check-card--passed {
  border-left-color: var(--mrr-tone-success);
}

.check-card--warning {
  border-left-color: var(--mrr-tone-warning);
}

.check-card--critical {
  border-left-color: var(--mrr-tone-danger);
}

.check-copy {
  min-width: 0;
  flex: 1;
}

.check-title-line code {
  padding: 2px var(--mrr-space-2);
  border-radius: max(0px, calc(var(--mrr-radius-sm) - 2px));
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
}

.check-actions {
  display: grid;
  flex: 0 0 auto;
  gap: var(--mrr-space-2);
  justify-items: end;
}

.suggestion-box {
  display: grid;
  gap: var(--mrr-space-1);
  margin-top: var(--mrr-space-3);
  padding: var(--mrr-space-2) var(--mrr-space-3);
  border-radius: var(--mrr-radius-md);
  background: var(--el-fill-color-light);
  font-size: var(--mrr-font-size-sm);
}

.details-panel {
  margin-top: var(--mrr-space-3);
}

.details-panel summary {
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: var(--mrr-font-size-sm);
}

.detail-json {
  max-height: 240px;
  overflow: auto;
  margin: var(--mrr-space-2) 0 0;
  padding: var(--mrr-space-3);
  border-radius: var(--mrr-radius-md);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: var(--mrr-font-size-xs);
  white-space: pre-wrap;
  word-break: break-all;
}

.diagnosis-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--mrr-space-4);
  align-items: flex-end;
}

.diagnosis-form :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 0;
}

.timeline-title {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-1);
}

.maintenance-checks {
  margin-top: var(--mrr-space-3);
}

@media (max-width: 1100px) {
  .two-column-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .toolbar-row,
  .check-row {
    align-items: flex-start;
    flex-direction: column;
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

.network-runtime-row {
  flex-wrap: wrap;
}

.network-node-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.storage-trend {
  display: grid;
  gap: 6px;
}

.storage-trend-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;

  > span:first-child {
    min-width: 100px;
  }
}
</style>

<script setup lang="ts">
import type {
  MigrationJob,
  MigrationJobPayload,
  MigrationReadiness,
  MigrationRecordQuery,
  MigrationScanRecord,
  OssUploadBatchResult,
} from '@/api/modules/oss'
import type { MigrationLogRecord, MigrationStatistics, OssUploadResult } from '@/api/types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import {
  CircleClose,
  Document,
  Refresh,
  Search,
  UploadFilled,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  cancelMigrationJob,
  createMigrationJob,
  getMigrationJob,
  getMigrationJobs,
  getMigrationLogs,
  getMigrationReadiness,
  getMigrationStatistics,
  getPendingFolders,
  getPendingManagementRecords,
  getWaitingSjhRecords,
  retryMigrationScans,
  uploadByBah,
  uploadToOss,
} from '@/api/modules/oss'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'

defineOptions({ name: 'OssMigrationPage' })

type MigrationMode = 'pilot' | 'batch' | 'full'
type RecordTab = 'pending' | 'waiting'
type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface JobFormState {
  mode: MigrationMode
  limit: number
  folder: string
  confirmation: string
}

interface FolderSummary {
  folder: string
  cnt: number
}

interface RecordFilterState {
  folder: string
  bah: string
  sjh: string
  limit: number
}

const ACTIVE_STATUSES = new Set(['pending', 'running', 'cancelling'])
const TERMINAL_STATUSES = new Set([
  'cancelled',
  'interrupted',
  'completed',
  'completed_with_errors',
  'failed',
])
const AUTO_REFRESH_INTERVAL = 15_000
const JOB_POLL_INTERVAL = 2_000

const jobActions: MrrTableAction[] = [{
  key: 'detail',
  label: '查看任务详情',
  icon: 'i-ri:eye-line',
  tone: 'primary',
  placement: 'inline',
}]
const logViewAction: MrrTableAction = {
  key: 'view-image',
  label: '查看 OSS 图片',
  icon: 'i-ri:image-line',
  tone: 'primary',
  placement: 'inline',
}
const {
  maxInlineActions: jobMaxInlineActions,
  actionColumnWidth: jobActionColumnWidth,
} = useTableActionLayout(jobActions.length, 1)
const {
  maxInlineActions: logMaxInlineActions,
  actionColumnWidth: logActionColumnWidth,
} = useTableActionLayout(1, 1)

const MODE_LABELS: Record<string, string> = {
  pilot: '试迁移',
  batch: '限定批次',
  full: '全量迁移',
  retry: '失败重试',
}

const STATUS_LABELS: Record<string, { label: string, type: TagType }> = {
  pending: { label: '等待启动', type: 'warning' },
  running: { label: '迁移中', type: 'primary' },
  cancelling: { label: '正在取消', type: 'warning' },
  cancelled: { label: '已取消', type: 'info' },
  interrupted: { label: '已中断', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  completed_with_errors: { label: '完成但有失败', type: 'warning' },
  failed: { label: '失败', type: 'danger' },
  success: { label: '成功', type: 'success' },
  retry_wait: { label: '等待重试', type: 'warning' },
  waiting_sjh: { label: '等待上架号', type: 'info' },
  migrating: { label: '迁移中', type: 'primary' },
  migrated: { label: '已迁移', type: 'success' },
  verified: { label: '已验证', type: 'success' },
  not_migrated: { label: '未迁移', type: 'info' },
  skipped: { label: '已跳过', type: 'info' },
}

const stats = ref<MigrationStatistics>({})
const readiness = ref<MigrationReadiness>()
const folders = ref<FolderSummary[]>([])
const records = ref<MigrationScanRecord[]>([])
const recordTab = ref<RecordTab>('pending')
const recordReturned = ref(0)
const recordHasMore = ref(false)
const selectedRecords = ref<MigrationScanRecord[]>([])
const jobs = ref<MigrationJob[]>([])
const jobTotal = ref(0)
const jobPage = ref(1)
const jobSize = ref(10)
const currentJob = ref<MigrationJob>()
const jobDetail = ref<MigrationJob>()
const jobDrawerVisible = ref(false)
const logs = ref<MigrationLogRecord[]>([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = ref(20)
const logStatus = ref('')
const logScanId = ref<number>()
const selectedLogs = ref<MigrationLogRecord[]>([])
const bahInput = ref('')
const uploadResult = ref<OssUploadBatchResult>()
const uploadResultVisible = ref(false)
const autoRefresh = ref(true)
const lastUpdatedAt = ref<Date>()
let pollTimer: ReturnType<typeof setInterval> | undefined
let overviewTimer: ReturnType<typeof setInterval> | undefined

const loading = reactive({
  page: false,
  overview: false,
  readiness: false,
  records: false,
  jobs: false,
  logs: false,
  start: false,
  cancel: false,
  manual: false,
  bah: false,
  retry: false,
  detail: false,
})

const jobForm = reactive<JobFormState>({
  mode: 'pilot',
  limit: 500,
  folder: '',
  confirmation: '',
})

const recordFilters = reactive<RecordFilterState>({
  folder: '',
  bah: '',
  sjh: '',
  limit: 100,
})

const summaryCards = computed(() => [
  {
    label: '总图片记录',
    value: stats.value.totalCount ?? 0,
    note: 'uploadflag 有效的扫描图片',
    tone: 'blue',
  },
  {
    label: '已迁移',
    value: stats.value.migratedCount ?? 0,
    note: `整体完成 ${stats.value.percentage ?? 0}%`,
    tone: 'green',
  },
  {
    label: '当前可领取',
    value: stats.value.pendingCount ?? 0,
    note: '上架号完整且已到重试时间',
    tone: 'amber',
  },
  {
    label: '等待上架号',
    value: stats.value.waitingSjhCount ?? 0,
    note: '保留原图，补齐后自动恢复',
    tone: 'blue',
  },
  {
    label: '等待重试',
    value: stats.value.retryWaitCount ?? 0,
    note: '后续任务到期后可再次领取',
    tone: 'amber',
  },
  {
    label: '正在迁移',
    value: stats.value.migratingCount ?? 0,
    note: '已原子领取、正在处理',
    tone: 'blue',
  },
  {
    label: '永久失败',
    value: stats.value.failedCount ?? 0,
    note: '核对 Nginx 原图或对象冲突',
    tone: 'danger',
  },
])

const isMigrationStarted = computed(() => (stats.value.migratedCount ?? 0) > 0)
const hasActiveJob = computed(() => Boolean(
  currentJob.value && ACTIVE_STATUSES.has(currentJob.value.status ?? ''),
))
const canStartJob = computed(() => Boolean(readiness.value?.ready && !hasActiveJob.value))
const startDisabledReason = computed(() => {
  if (hasActiveJob.value) {
    return '已有迁移任务正在运行'
  }
  if (!readiness.value?.ossConfigured) {
    return 'OSS 配置未通过'
  }
  if (!readiness.value?.sourcePathReadable) {
    return 'Nginx 抽样读取未通过'
  }
  if ((readiness.value?.pendingCount ?? 0) <= 0) {
    return '当前没有可领取记录'
  }
  return ''
})
const selectedPendingIds = computed(() => selectedRecords.value
  .map(item => item.id)
  .filter((id): id is number => id != null))
const failedLogScanIds = computed(() => Array.from(new Set(
  selectedLogs.value
    .filter(item => item.migrationStatus === 'failed' && item.scanId != null)
    .map(item => item.scanId as number),
)))
const jobPercentage = computed(() => Math.min(100, Number(currentJob.value?.rate || 0)))
const currentRecordTitle = computed(() => recordTab.value === 'pending'
  ? '当前可领取记录'
  : '等待补齐上架号')
const currentRecordDescription = computed(() => recordTab.value === 'pending'
  ? '仅显示当前满足领取条件的记录，可用于小规模手工验证。'
  : '这些记录不会上传、不会失败；上架号补齐后会自动进入待迁移集合。')

watch(() => jobForm.mode, (mode) => {
  jobForm.confirmation = ''
  jobForm.limit = mode === 'pilot' ? 500 : 10000
})

watch(recordTab, () => {
  selectedRecords.value = []
  void loadRecords()
})

watch(autoRefresh, (enabled) => {
  if (enabled) {
    startOverviewRefresh()
  }
  else {
    stopOverviewRefresh()
  }
})

function statusMeta(status?: string) {
  return STATUS_LABELS[status ?? ''] ?? { label: status || '-', type: 'info' as TagType }
}

function modeLabel(mode?: string) {
  return MODE_LABELS[mode ?? ''] ?? mode ?? '-'
}

function formatDate(value?: string | Date | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN')
}

function formatNumber(value?: number | null) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function formatFileSize(value?: number | null) {
  if (!value || value <= 0) {
    return '-'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeUploadResult(response: Awaited<ReturnType<typeof uploadToOss>>) {
  if (response.data) {
    return response.data
  }
  return {
    results: response.results ?? [],
    total: response.total,
    success: response.success,
    failed: response.failed,
    waitingSjh: response.waitingSjh,
    bah: response.bah,
  }
}

function handleRecordSelection(selection: MigrationScanRecord[]) {
  selectedRecords.value = selection
}

function handleLogSelection(selection: MigrationLogRecord[]) {
  selectedLogs.value = selection
}

function logSelectable(row: MigrationLogRecord) {
  return row.migrationStatus === 'failed' && row.scanId != null
}

async function loadStats() {
  const response = await getMigrationStatistics()
  stats.value = response.data ?? {}
}

async function loadReadiness(showMessage = false) {
  loading.readiness = true
  try {
    const response = await getMigrationReadiness(100)
    readiness.value = response.data
    if (response.data?.activeJob) {
      currentJob.value = response.data.activeJob
      startPolling()
    }
    if (showMessage) {
      ElMessage.success('迁移前检查已重新执行')
    }
  }
  finally {
    loading.readiness = false
  }
}

async function loadFolders() {
  const response = await getPendingFolders()
  folders.value = response.data ?? []
}

function recordQuery(): MigrationRecordQuery {
  return {
    limit: recordFilters.limit,
    folder: recordFilters.folder || undefined,
    bah: recordFilters.bah.trim() || undefined,
    sjh: recordFilters.sjh.trim() || undefined,
  }
}

async function loadRecords() {
  loading.records = true
  try {
    const response = recordTab.value === 'pending'
      ? await getPendingManagementRecords(recordQuery())
      : await getWaitingSjhRecords(recordQuery())
    records.value = response.data?.list ?? []
    recordReturned.value = response.data?.returned ?? records.value.length
    recordHasMore.value = Boolean(response.data?.hasMore)
    selectedRecords.value = []
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '加载迁移记录失败'))
  }
  finally {
    loading.records = false
  }
}

function resetRecordFilters() {
  recordFilters.folder = ''
  recordFilters.bah = ''
  recordFilters.sjh = ''
  recordFilters.limit = 100
  void loadRecords()
}

async function loadJobs() {
  loading.jobs = true
  try {
    const response = await getMigrationJobs({ page: jobPage.value, size: jobSize.value })
    jobs.value = response.data?.list ?? []
    jobTotal.value = response.data?.total ?? 0
    const active = jobs.value.find(job => ACTIVE_STATUSES.has(job.status ?? ''))
    if (active) {
      currentJob.value = active
      startPolling()
    }
    else if (currentJob.value && ACTIVE_STATUSES.has(currentJob.value.status ?? '')) {
      stopPolling()
      currentJob.value = undefined
    }
  }
  finally {
    loading.jobs = false
  }
}

async function loadLogs() {
  loading.logs = true
  try {
    const response = await getMigrationLogs({
      status: logStatus.value || undefined,
      scanId: logScanId.value,
      page: logPage.value,
      size: logSize.value,
    })
    logs.value = response.data?.list ?? []
    logTotal.value = response.data?.total ?? 0
    selectedLogs.value = []
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '加载迁移日志失败'))
  }
  finally {
    loading.logs = false
  }
}

async function refreshOverview(silent = false) {
  if (!silent) {
    loading.overview = true
  }
  try {
    await Promise.all([loadStats(), loadReadiness(), loadJobs()])
    lastUpdatedAt.value = new Date()
  }
  catch (error: unknown) {
    if (!silent) {
      ElMessage.error(errorMessage(error, '刷新迁移概览失败'))
    }
  }
  finally {
    loading.overview = false
  }
}

async function refreshAll() {
  loading.page = true
  try {
    await Promise.all([
      loadStats(),
      loadReadiness(),
      loadFolders(),
      loadRecords(),
      loadJobs(),
      loadLogs(),
    ])
    lastUpdatedAt.value = new Date()
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '加载 OSS 迁移管理数据失败'))
  }
  finally {
    loading.page = false
  }
}

function stopPolling() {
  if (!pollTimer) {
    return
  }
  clearInterval(pollTimer)
  pollTimer = undefined
}

function startPolling() {
  if (pollTimer || currentJob.value?.id == null) {
    return
  }
  pollTimer = setInterval(async () => {
    const jobId = currentJob.value?.id
    if (jobId == null) {
      stopPolling()
      return
    }
    try {
      const response = await getMigrationJob(jobId)
      if (response.data) {
        currentJob.value = response.data
      }
      if (TERMINAL_STATUSES.has(currentJob.value?.status ?? '')) {
        stopPolling()
        await refreshAll()
      }
    }
    catch {
      stopPolling()
    }
  }, JOB_POLL_INTERVAL)
}

function stopOverviewRefresh() {
  if (!overviewTimer) {
    return
  }
  clearInterval(overviewTimer)
  overviewTimer = undefined
}

function startOverviewRefresh() {
  stopOverviewRefresh()
  overviewTimer = setInterval(() => {
    void refreshOverview(true)
  }, AUTO_REFRESH_INTERVAL)
}

function applyRecommendedMode() {
  const mode = readiness.value?.recommendedMode
  if (mode === 'pilot' || mode === 'batch') {
    jobForm.mode = mode
  }
}

async function handleStartJob() {
  if (!canStartJob.value) {
    ElMessage.warning(startDisabledReason.value || '迁移前检查未通过')
    return
  }
  if (jobForm.mode === 'full' && jobForm.confirmation !== '确认全量迁移') {
    ElMessage.warning('请输入完整确认短语：确认全量迁移')
    return
  }

  const scopeText = jobForm.folder ? `目录 ${jobForm.folder}` : '全部待迁移记录'
  const countText = jobForm.mode === 'full' ? '当前快照内全部记录' : `最多 ${jobForm.limit} 条`
  try {
    await ElMessageBox.confirm(
      `将创建${modeLabel(jobForm.mode)}任务，范围为${scopeText}，处理${countText}。单个文件失败不会阻塞后续记录，是否开始？`,
      '确认创建迁移任务',
      { confirmButtonText: '创建任务', cancelButtonText: '取消', type: 'warning' },
    )
  }
  catch {
    return
  }

  const payload: MigrationJobPayload = {
    mode: jobForm.mode,
    folder: jobForm.folder || undefined,
    limit: jobForm.mode === 'full' ? undefined : jobForm.limit,
    confirmation: jobForm.mode === 'full' ? jobForm.confirmation : undefined,
  }
  loading.start = true
  try {
    const response = await createMigrationJob(payload)
    if (response.data) {
      currentJob.value = response.data
      startPolling()
    }
    ElMessage.success(response.message || '迁移任务已创建')
    await refreshOverview()
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '创建迁移任务失败'))
  }
  finally {
    loading.start = false
  }
}

async function handleCancelJob() {
  const jobId = currentJob.value?.id
  if (jobId == null) {
    return
  }
  try {
    await ElMessageBox.confirm(
      '取消会在当前图片处理完成后停止。已经成功迁移的图片不会回滚，是否继续？',
      '安全取消迁移任务',
      { confirmButtonText: '安全取消', cancelButtonText: '继续运行', type: 'warning' },
    )
  }
  catch {
    return
  }

  loading.cancel = true
  try {
    const response = await cancelMigrationJob(jobId)
    if (response.data) {
      currentJob.value = response.data
    }
    ElMessage.success('已提交安全取消请求')
    startPolling()
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '取消任务失败'))
  }
  finally {
    loading.cancel = false
  }
}

function showUploadResult(result: OssUploadBatchResult) {
  uploadResult.value = result
  uploadResultVisible.value = true
  const failed = result.failed ?? 0
  const waiting = result.waitingSjh ?? 0
  if (failed > 0 || waiting > 0) {
    ElMessage.warning(`上传完成：成功或跳过 ${result.success ?? 0}，失败 ${failed}，等待上架号 ${waiting}`)
  }
  else {
    ElMessage.success(`上传完成：${result.success ?? 0}/${result.total ?? 0} 成功或已跳过`)
  }
}

async function handleManualUpload() {
  const ids = selectedPendingIds.value
  if (!ids.length) {
    ElMessage.warning('请先选择当前可领取记录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将立即手工上传选中的 ${ids.length} 张图片。手工上传只用于小规模验证，是否继续？`,
      '确认手工上传',
      { confirmButtonText: '开始上传', cancelButtonText: '取消', type: 'warning' },
    )
  }
  catch {
    return
  }

  loading.manual = true
  try {
    const response = await uploadToOss(ids)
    showUploadResult(normalizeUploadResult(response))
    await refreshAll()
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '手工上传失败'))
  }
  finally {
    loading.manual = false
  }
}

async function handleBahUpload() {
  const bah = bahInput.value.trim()
  if (!bah) {
    ElMessage.warning('请输入病案号')
    return
  }
  loading.bah = true
  try {
    const response = await uploadByBah(bah)
    showUploadResult(normalizeUploadResult(response as Awaited<ReturnType<typeof uploadToOss>>))
    await refreshAll()
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '病案手工上传失败'))
  }
  finally {
    loading.bah = false
  }
}

async function handleRetryFailed() {
  if (!failedLogScanIds.value.length) {
    ElMessage.warning('请在迁移日志中选择永久失败记录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将把 ${failedLogScanIds.value.length} 条永久失败记录重置为待迁移。请确保已经处理原图、权限或对象冲突，是否继续？`,
      '确认重置失败记录',
      { confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning' },
    )
  }
  catch {
    return
  }

  loading.retry = true
  try {
    const response = await retryMigrationScans(failedLogScanIds.value)
    ElMessage.success(`已重置 ${response.data?.updated ?? 0} 条记录，请重新执行试迁移`)
    await refreshAll()
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '重置失败记录失败'))
  }
  finally {
    loading.retry = false
  }
}

async function showJobDetail(job: MigrationJob) {
  if (job.id == null) {
    return
  }
  loading.detail = true
  jobDrawerVisible.value = true
  try {
    const response = await getMigrationJob(job.id)
    jobDetail.value = response.data ?? job
  }
  catch (error: unknown) {
    jobDetail.value = job
    ElMessage.error(errorMessage(error, '加载任务详情失败'))
  }
  finally {
    loading.detail = false
  }
}

function resetLogFilters() {
  logStatus.value = ''
  logScanId.value = undefined
  logPage.value = 1
  void loadLogs()
}

function openOssUrl(url?: string) {
  if (!url) {
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

function logActions(row: MigrationLogRecord): MrrTableAction[] {
  return row.ossUrl ? [logViewAction] : []
}

function handleJobAction(action: string, row: MigrationJob) {
  if (action === 'detail') {
    void showJobDetail(row)
  }
}

function handleLogAction(action: string, row: MigrationLogRecord) {
  if (action === 'view-image' && row.ossUrl) {
    openOssUrl(row.ossUrl)
  }
}

onMounted(() => {
  void refreshAll()
  startOverviewRefresh()
})

onBeforeUnmount(() => {
  stopPolling()
  stopOverviewRefresh()
})
</script>

<template>
  <div v-loading="loading.page" class="page-shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">
          OSS Migration Control
        </p>
        <h2>OSS 迁移管理</h2>
        <p class="subtitle">
          统一通过 Nginx 获取原图，按上架号分组上传；缺少上架号的记录保留等待补齐。
        </p>
      </div>
      <div class="page-actions">
        <div class="refresh-state">
          <span>最后刷新：{{ formatDate(lastUpdatedAt) }}</span>
          <el-switch
            v-model="autoRefresh"
            inline-prompt
            active-text="自动"
            inactive-text="手动"
          />
        </div>
        <el-button
          :icon="Refresh"
          :loading="loading.overview"
          @click="refreshAll"
        >
          刷新全部
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="!isMigrationStarted"
      title="当前尚未开始正式 OSS 迁移"
      description="建议先使用 100～500 张真实图片试迁移，核对影像档案袋访问、ZIP/PDF 导出、日志与 OSS 费用后，再逐步扩大批次。"
      type="info"
      :closable="false"
      show-icon
    />

    <section class="metric-grid">
      <article
        v-for="item in summaryCards"
        :key="item.label"
        class="metric-card"
        :class="`metric-card--${item.tone}`"
      >
        <span>{{ item.label }}</span>
        <strong>{{ formatNumber(item.value) }}</strong>
        <small>{{ item.note }}</small>
      </article>
    </section>

    <div class="primary-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <strong>迁移前检查</strong>
              <p>只抽样检查配置和 Nginx 可读性，不上传文件、不更新迁移状态。</p>
            </div>
            <div class="header-actions">
              <el-tag :type="readiness?.ready ? 'success' : 'warning'">
                {{ readiness?.ready ? '可开始迁移' : '需要处理' }}
              </el-tag>
              <el-button
                :loading="loading.readiness"
                :icon="Refresh"
                @click="loadReadiness(true)"
              >
                重新检查
              </el-button>
            </div>
          </div>
        </template>

        <div class="check-grid">
          <div class="check-item" :class="{ ok: readiness?.ossConfigured }">
            <span>OSS 客户端</span>
            <strong>{{ readiness?.ossConfigured ? '已配置' : '未通过' }}</strong>
          </div>
          <div class="check-item" :class="{ ok: readiness?.sourcePathReadable }">
            <span>Nginx 图片源</span>
            <strong>{{ readiness?.sourcePathReadable ? '可读取' : '不可读取' }}</strong>
          </div>
          <div class="check-item" :class="{ ok: readiness?.noActiveJob }">
            <span>活动任务</span>
            <strong>{{ readiness?.noActiveJob ? '无' : '已有任务' }}</strong>
          </div>
          <div class="check-item" :class="{ ok: (readiness?.sampleReadableCount ?? 0) > 0 }">
            <span>抽样文件</span>
            <strong>{{ readiness?.sampleReadableCount ?? 0 }} / {{ readiness?.sampleSize ?? 0 }} 可读</strong>
          </div>
        </div>

        <div class="facts">
          <span>抽样缺失 {{ readiness?.sampleMissingCount ?? 0 }}</span>
          <span>路径异常 {{ readiness?.sampleInvalidCount ?? 0 }}</span>
          <span>当前可领取 {{ formatNumber(readiness?.pendingCount) }}</span>
          <span>等待上架号 {{ formatNumber(stats.waitingSjhCount) }}</span>
        </div>

        <el-alert
          v-if="readiness?.recommendedAction"
          :title="readiness.recommendedAction"
          type="success"
          :closable="false"
          show-icon
        >
          <template #default>
            <el-button link type="success" @click="applyRecommendedMode">
              应用推荐阶段
            </el-button>
          </template>
        </el-alert>

        <ul v-if="readiness?.warnings?.length" class="warning-list">
          <li v-for="warning in readiness.warnings" :key="warning">
            {{ warning }}
          </li>
        </ul>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <strong>创建迁移任务</strong>
              <p>同一时刻只运行一个任务；创建时固定数据快照上界。</p>
            </div>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="迁移阶段">
            <el-radio-group v-model="jobForm.mode">
              <el-radio-button value="pilot">
                试迁移
              </el-radio-button>
              <el-radio-button value="batch">
                限定批次
              </el-radio-button>
              <el-radio-button value="full">
                全量迁移
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="jobForm.mode !== 'full'" label="最大处理数量">
            <el-input-number
              v-model="jobForm.limit"
              :min="1"
              :max="jobForm.mode === 'pilot' ? 1000 : 100000"
              :step="jobForm.mode === 'pilot' ? 100 : 1000"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item label="目录范围（可选）">
            <el-select
              v-model="jobForm.folder"
              clearable
              filterable
              placeholder="全部待迁移目录"
            >
              <el-option
                v-for="item in folders"
                :key="item.folder"
                :label="`${item.folder}（${formatNumber(item.cnt)}）`"
                :value="item.folder"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="jobForm.mode === 'full'" label="全量迁移确认短语">
            <el-input v-model="jobForm.confirmation" placeholder="请输入：确认全量迁移" />
          </el-form-item>

          <el-button
            type="primary"
            :icon="VideoPlay"
            :loading="loading.start"
            :disabled="!canStartJob"
            @click="handleStartJob"
          >
            创建{{ modeLabel(jobForm.mode) }}任务
          </el-button>
          <p v-if="startDisabledReason" class="disabled-reason">
            {{ startDisabledReason }}
          </p>
        </el-form>
      </el-card>
    </div>

    <el-card v-if="currentJob" shadow="never" class="current-job-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>当前任务 #{{ currentJob.id }}</strong>
            <p>
              {{ modeLabel(currentJob.mode) }} · {{ currentJob.scopeValue || '全部目录' }} ·
              创建者 {{ currentJob.createdBy || '-' }}
            </p>
          </div>
          <div class="header-actions">
            <el-tag :type="statusMeta(currentJob.status).type">
              {{ statusMeta(currentJob.status).label }}
            </el-tag>
            <el-button :icon="Document" @click="showJobDetail(currentJob)">
              查看详情
            </el-button>
            <el-button
              v-if="hasActiveJob"
              type="danger"
              plain
              :icon="CircleClose"
              :loading="loading.cancel"
              @click="handleCancelJob"
            >
              安全取消
            </el-button>
          </div>
        </div>
      </template>

      <el-progress
        :percentage="jobPercentage"
        :status="currentJob.status === 'failed' ? 'exception' : undefined"
        :stroke-width="18"
        text-inside
      />
      <div class="facts">
        <span>计划 {{ formatNumber(currentJob.totalCount) }}</span>
        <span>已处理 {{ formatNumber(currentJob.processedCount) }}</span>
        <span>失败 {{ formatNumber(currentJob.failedCount) }}</span>
        <span>快照上界 ID {{ currentJob.maxScanId ?? '-' }}</span>
        <span>开始 {{ formatDate(currentJob.startedAt) }}</span>
      </div>
      <el-alert
        v-if="currentJob.errorMessage"
        :title="currentJob.errorMessage"
        type="warning"
        :closable="false"
      />
    </el-card>

    <el-card shadow="never" class="workspace-card">
      <el-tabs v-model="recordTab" class="workspace-tabs">
        <el-tab-pane label="迁移记录" name="pending">
          <template #label>
            <span>当前可领取（{{ formatNumber(stats.pendingCount) }}）</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="waiting">
          <template #label>
            <span>等待上架号（{{ formatNumber(stats.waitingSjhCount) }}）</span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <div class="section-header">
        <div>
          <strong>{{ currentRecordTitle }}</strong>
          <p>{{ currentRecordDescription }}</p>
        </div>
        <div class="result-meta">
          <el-tag type="info">
            当前返回 {{ recordReturned }} 条
          </el-tag>
          <el-tag v-if="recordHasMore" type="warning">
            仍有更多，请缩小条件
          </el-tag>
        </div>
      </div>

      <el-form class="filter-grid" label-position="top">
        <el-form-item label="目录">
          <el-select
            v-model="recordFilters.folder"
            clearable
            filterable
            placeholder="全部目录"
          >
            <el-option
              v-for="item in folders"
              :key="item.folder"
              :label="item.folder"
              :value="item.folder"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="病案号">
          <el-input
            v-model="recordFilters.bah"
            clearable
            placeholder="精确病案号"
            @keyup.enter="loadRecords"
          />
        </el-form-item>
        <el-form-item label="上架号">
          <el-input
            v-model="recordFilters.sjh"
            clearable
            placeholder="精确上架号"
            @keyup.enter="loadRecords"
          />
        </el-form-item>
        <el-form-item label="返回数量">
          <el-input-number
            v-model="recordFilters.limit"
            :min="20"
            :max="500"
            :step="20"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item class="filter-actions" label="操作">
          <el-button type="primary" :icon="Search" @click="loadRecords">
            查询
          </el-button>
          <el-button @click="resetRecordFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="recordTab === 'waiting'"
        title="等待上架号记录不会进入失败或重试"
        description="补齐上架号后无需修改迁移状态，下一次查询和迁移任务会自动把它识别为可领取记录。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-table
        v-loading="loading.records"
        :data="records"
        stripe
        row-key="id"
        empty-text="当前条件下没有记录"
        @selection-change="handleRecordSelection"
      >
        <el-table-column v-if="recordTab === 'pending'" type="selection" width="48" />
        <el-table-column prop="id" label="Scan ID" width="100" />
        <el-table-column prop="bah" label="病案号" width="140" />
        <el-table-column prop="sjh" label="上架号" width="140">
          <template #default="{ row }">
            {{ row.sjh || '未填写' }}
          </template>
        </el-table-column>
        <el-table-column prop="folder" label="原目录" width="130" />
        <el-table-column prop="filename" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="migrationAttempts" label="尝试" width="80">
          <template #default="{ row }">
            {{ row.migrationAttempts ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="migrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.migrationStatus).type" size="small">
              {{ statusMeta(row.migrationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="migrationNextRetryAt" label="下次可重试" width="180">
          <template #default="{ row }">
            {{ formatDate(row.migrationNextRetryAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="migrationErrorCode" label="错误码" width="160">
          <template #default="{ row }">
            {{ row.migrationErrorCode || '-' }}
          </template>
        </el-table-column>
      </el-table>

      <div v-if="recordTab === 'pending'" class="record-actions">
        <div class="manual-bah">
          <span>按病案号验证</span>
          <el-input
            v-model="bahInput"
            placeholder="输入病案号"
            clearable
            @keyup.enter="handleBahUpload"
          />
          <el-button
            :loading="loading.bah"
            :disabled="hasActiveJob"
            @click="handleBahUpload"
          >
            上传整份病案
          </el-button>
        </div>
        <el-button
          type="primary"
          :icon="UploadFilled"
          :disabled="!selectedPendingIds.length || hasActiveJob"
          :loading="loading.manual"
          @click="handleManualUpload"
        >
          手工上传 {{ selectedPendingIds.length }} 条
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>迁移任务历史</strong>
            <p>分页查看试迁移、批次迁移和全量迁移的执行结果。</p>
          </div>
          <el-button :icon="Refresh" :loading="loading.jobs" @click="loadJobs">
            刷新任务
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading.jobs" :data="jobs" stripe empty-text="暂无迁移任务">
        <el-table-column prop="id" label="任务" width="80" />
        <el-table-column label="阶段" width="110">
          <template #default="{ row }">
            {{ modeLabel(row.mode) }}
          </template>
        </el-table-column>
        <el-table-column prop="scopeValue" label="目录范围" min-width="140">
          <template #default="{ row }">
            {{ row.scopeValue || '全部目录' }}
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="计划" width="100" />
        <el-table-column prop="processedCount" label="已处理" width="100" />
        <el-table-column prop="failedCount" label="失败" width="90" />
        <el-table-column label="完成率" width="150">
          <template #default="{ row }">
            <el-progress :percentage="Math.min(100, Number(row.rate || 0))" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          :width="jobActionColumnWidth"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <MrrTableActions
              :actions="jobActions"
              :max-inline="jobMaxInlineActions"
              @select="handleJobAction($event, row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="jobPage"
          v-model:page-size="jobSize"
          :page-sizes="[10, 20, 50]"
          :total="jobTotal"
          layout="total, sizes, prev, pager, next"
          @size-change="loadJobs"
          @current-change="loadJobs"
        />
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>迁移日志与失败处理</strong>
            <p>按结果或 Scan ID 定位记录；核对问题后才能重置永久失败。</p>
          </div>
          <el-button
            :disabled="!failedLogScanIds.length || hasActiveJob"
            :loading="loading.retry"
            @click="handleRetryFailed"
          >
            重置失败记录 {{ failedLogScanIds.length }} 条
          </el-button>
        </div>
      </template>

      <el-form class="log-filter" inline>
        <el-form-item label="结果">
          <el-select v-model="logStatus" clearable placeholder="全部状态">
            <el-option label="成功" value="success" />
            <el-option label="永久失败" value="failed" />
            <el-option label="等待重试" value="retry_wait" />
            <el-option label="等待上架号" value="waiting_sjh" />
            <el-option label="已跳过" value="skipped" />
          </el-select>
        </el-form-item>
        <el-form-item label="Scan ID">
          <el-input-number
            v-model="logScanId"
            :min="1"
            controls-position="right"
            placeholder="精确 ID"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadLogs">
            查询
          </el-button>
          <el-button @click="resetLogFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading.logs"
        :data="logs"
        stripe
        empty-text="暂无迁移日志"
        @selection-change="handleLogSelection"
      >
        <el-table-column type="selection" width="48" :selectable="logSelectable" />
        <el-table-column prop="scanId" label="Scan ID" width="100" />
        <el-table-column prop="localPath" label="迁移来源" min-width="160" show-overflow-tooltip />
        <el-table-column prop="migrationStatus" label="结果" width="120">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.migrationStatus).type" size="small">
              {{ statusMeta(row.migrationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="文件大小" width="110">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="checksumMd5" label="MD5" min-width="190" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="错误信息" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.errorMessage || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.migratedAt || row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          :width="logActionColumnWidth"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <MrrTableActions
              :actions="logActions(row)"
              :max-inline="logMaxInlineActions"
              @select="handleLogAction($event, row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="logPage"
          v-model:page-size="logSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="logTotal"
          layout="total, sizes, prev, pager, next"
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </el-card>

    <el-dialog v-model="uploadResultVisible" title="手工上传结果" width="min(900px, 92vw)">
      <div v-if="uploadResult" class="upload-summary">
        <el-tag type="info">
          总数 {{ uploadResult.total ?? uploadResult.results.length }}
        </el-tag>
        <el-tag type="success">
          成功或跳过 {{ uploadResult.success ?? 0 }}
        </el-tag>
        <el-tag type="warning">
          等待上架号 {{ uploadResult.waitingSjh ?? 0 }}
        </el-tag>
        <el-tag type="danger">
          失败 {{ uploadResult.failed ?? 0 }}
        </el-tag>
      </div>
      <el-table :data="uploadResult?.results ?? []" stripe max-height="520">
        <el-table-column prop="scanId" label="Scan ID" width="100" />
        <el-table-column prop="status" label="结果" width="130">
          <template #default="{ row }: { row: OssUploadResult }">
            <el-tag :type="statusMeta(row.status).type">
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="文件大小" width="120">
          <template #default="{ row }: { row: OssUploadResult }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="说明" min-width="240">
          <template #default="{ row }: { row: OssUploadResult }">
            {{ row.errorMessage || '上传成功或已有一致对象' }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-drawer v-model="jobDrawerVisible" title="迁移任务详情" size="460px">
      <div v-loading="loading.detail">
        <el-descriptions v-if="jobDetail" :column="1" border>
          <el-descriptions-item label="任务 ID">
            {{ jobDetail.id }}
          </el-descriptions-item>
          <el-descriptions-item label="阶段">
            {{ modeLabel(jobDetail.mode) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta(jobDetail.status).type">
              {{ statusMeta(jobDetail.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目录范围">
            {{ jobDetail.scopeValue || '全部目录' }}
          </el-descriptions-item>
          <el-descriptions-item label="请求数量">
            {{ formatNumber(jobDetail.requestedCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="快照上界 ID">
            {{ jobDetail.maxScanId ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理进度">
            {{ formatNumber(jobDetail.processedCount) }} / {{ formatNumber(jobDetail.totalCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="失败数量">
            {{ formatNumber(jobDetail.failedCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建者">
            {{ jobDetail.createdBy || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDate(jobDetail.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">
            {{ formatDate(jobDetail.startedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ formatDate(jobDetail.completedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="错误信息">
            {{ jobDetail.errorMessage || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 18px;
}

.page-header,
.card-header,
.section-header,
.page-actions,
.header-actions,
.result-meta,
.facts,
.record-actions,
.manual-bah,
.upload-summary,
.refresh-state {
  display: flex;
  align-items: center;
}

.page-header,
.card-header,
.section-header,
.record-actions {
  gap: 16px;
  justify-content: space-between;
}

.page-header h2,
.card-header p,
.section-header p,
.subtitle,
.eyebrow,
.disabled-reason {
  margin: 0;
}

.page-header h2 {
  margin-top: 4px;
  font-size: 26px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtitle,
.card-header p,
.section-header p,
.refresh-state,
.disabled-reason {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}

.page-actions,
.header-actions,
.result-meta,
.facts,
.record-actions,
.manual-bah,
.upload-summary,
.refresh-state {
  flex-wrap: wrap;
  gap: 12px;
}

.page-actions {
  justify-content: flex-end;
}

.refresh-state {
  justify-content: flex-end;
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 14px;
}

.metric-card {
  display: grid;
  gap: 8px;
  padding: 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.metric-card strong {
  font-size: 28px;
}

.metric-card small,
.metric-card span {
  color: var(--el-text-color-secondary);
}

.metric-card--blue {
  border-top: 3px solid var(--el-color-primary);
}

.metric-card--green {
  border-top: 3px solid var(--el-color-success);
}

.metric-card--amber {
  border-top: 3px solid var(--el-color-warning);
}

.metric-card--danger {
  border-top: 3px solid var(--el-color-danger);
}

.primary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(340px, 0.7fr);
  gap: 18px;
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.check-item {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--el-border-radius-base);
}

.check-item.ok {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
}

.facts {
  margin: 14px 0;
  color: var(--el-text-color-secondary);
}

.warning-list {
  display: grid;
  gap: 6px;
  padding-left: 20px;
  margin: 14px 0 0;
  color: var(--el-color-warning-dark-2);
}

.disabled-reason {
  font-size: 12px;
}

.current-job-card {
  border-left: 3px solid var(--el-color-primary);
}

.workspace-card :deep(.el-card__body) {
  padding-top: 8px;
}

.workspace-tabs {
  margin-bottom: 16px;
}

.section-header {
  margin-bottom: 14px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr)) minmax(180px, auto);
  gap: 12px;
  align-items: end;
  margin-bottom: 14px;
}

.filter-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-actions :deep(.el-form-item__content) {
  flex-wrap: nowrap;
}

.record-actions {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.manual-bah :deep(.el-input) {
  width: 220px;
}

.log-filter {
  padding: 14px;
  margin-bottom: 14px;
  background: var(--el-fill-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.log-filter :deep(.el-select) {
  width: 160px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.upload-summary {
  margin-bottom: 14px;
}

:deep(.el-input-number),
:deep(.el-select) {
  width: 100%;
}

@media (width <= 1100px) {
  .primary-grid {
    grid-template-columns: 1fr;
  }

  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 720px) {
  .check-grid,
  .filter-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .card-header,
  .section-header,
  .record-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-actions,
  .manual-bah {
    width: 100%;
  }

  .manual-bah :deep(.el-input) {
    width: 100%;
  }
}
</style>

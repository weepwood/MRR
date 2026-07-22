<script setup lang="ts">
import type { MigrationJob, MigrationJobPayload, MigrationReadiness } from '@/api/modules/oss'
import type { MigrationLogRecord, MigrationStatistics, ScanRecord } from '@/api/types'
import { CircleClose, Refresh, UploadFilled, VideoPlay } from '@element-plus/icons-vue'
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
  getPendingMigrations,
  retryMigrationScans,
  uploadByBah,
  uploadToOss,
} from '@/api/modules/oss'

defineOptions({ name: 'OssMigrationPage' })

type MigrationMode = 'pilot' | 'batch' | 'full'
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

const ACTIVE_STATUSES = new Set(['pending', 'running', 'cancelling'])
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
  migrating: { label: '迁移中', type: 'primary' },
  migrated: { label: '已迁移', type: 'success' },
  verified: { label: '已验证', type: 'success' },
  not_migrated: { label: '未迁移', type: 'info' },
  skipped: { label: '已跳过', type: 'info' },
}

const stats = ref<MigrationStatistics>({})
const readiness = ref<MigrationReadiness>()
const pendingList = ref<ScanRecord[]>([])
const folders = ref<FolderSummary[]>([])
const jobs = ref<MigrationJob[]>([])
const currentJob = ref<MigrationJob>()
const logs = ref<MigrationLogRecord[]>([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = ref(20)
const logStatus = ref('')
const selectedPending = ref<ScanRecord[]>([])
const selectedLogs = ref<MigrationLogRecord[]>([])
const bahInput = ref('')
let pollTimer: ReturnType<typeof setInterval> | undefined

const loading = reactive({
  page: false,
  start: false,
  cancel: false,
  manual: false,
  bah: false,
  retry: false,
  logs: false,
})

const jobForm = reactive<JobFormState>({
  mode: 'pilot',
  limit: 500,
  folder: '',
  confirmation: '',
})

const summaryCards = computed(() => [
  { label: '总图片记录', value: stats.value.totalCount ?? 0, note: 'uploadflag 有效的扫描图片', tone: 'blue' },
  { label: '已迁移', value: stats.value.migratedCount ?? 0, note: '已写入 OSS Object Key', tone: 'green' },
  { label: '待处理', value: stats.value.pendingCount ?? 0, note: '包含未迁移与等待重试', tone: 'amber' },
  { label: '永久失败', value: stats.value.failedCount ?? 0, note: '需核对后人工重置', tone: 'danger' },
])
const isMigrationStarted = computed(() => (stats.value.migratedCount ?? 0) > 0)
const hasActiveJob = computed(() => Boolean(
  currentJob.value && ACTIVE_STATUSES.has(currentJob.value.status ?? ''),
))
const canStartJob = computed(() => Boolean(readiness.value?.ready && !hasActiveJob.value))
const failedLogScanIds = computed(() => Array.from(new Set(
  selectedLogs.value
    .filter(item => item.migrationStatus === 'failed' && item.scanId != null)
    .map(item => item.scanId as number),
)))

watch(() => jobForm.mode, (mode) => {
  jobForm.confirmation = ''
  jobForm.limit = mode === 'pilot' ? 500 : 10000
})

function statusMeta(status?: string) {
  return STATUS_LABELS[status ?? ''] ?? { label: status || '-', type: 'info' as TagType }
}

function modeLabel(mode?: string) {
  return MODE_LABELS[mode ?? ''] ?? mode ?? '-'
}

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function handlePendingSelection(selection: ScanRecord[]) {
  selectedPending.value = selection
}

function handleLogSelection(selection: MigrationLogRecord[]) {
  selectedLogs.value = selection
}

async function loadStats() {
  const response = await getMigrationStatistics()
  stats.value = response.data ?? {}
}

async function loadReadiness() {
  const response = await getMigrationReadiness(100)
  readiness.value = response.data
  if (response.data?.activeJob) {
    currentJob.value = response.data.activeJob
    startPolling()
  }
}

async function loadPending() {
  const response = await getPendingMigrations({
    limit: 100,
    folder: jobForm.folder || undefined,
  })
  pendingList.value = response.data?.list ?? []
  selectedPending.value = []
}

async function loadFolders() {
  const response = await getPendingFolders()
  folders.value = response.data ?? []
}

async function loadJobs() {
  const response = await getMigrationJobs({ page: 1, size: 10 })
  jobs.value = response.data?.list ?? []
  const active = jobs.value.find(job => ACTIVE_STATUSES.has(job.status ?? ''))
  if (active) {
    currentJob.value = active
    startPolling()
  }
}

async function loadLogs() {
  loading.logs = true
  try {
    const response = await getMigrationLogs({
      status: logStatus.value || undefined,
      page: logPage.value,
      size: logSize.value,
    })
    logs.value = response.data?.list ?? []
    logTotal.value = response.data?.total ?? 0
    selectedLogs.value = []
  }
  finally {
    loading.logs = false
  }
}

async function refreshAll() {
  loading.page = true
  try {
    await Promise.all([
      loadStats(),
      loadReadiness(),
      loadPending(),
      loadFolders(),
      loadJobs(),
      loadLogs(),
    ])
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '加载 OSS 迁移数据失败'))
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
      if (!ACTIVE_STATUSES.has(currentJob.value?.status ?? '')) {
        stopPolling()
        await refreshAll()
      }
    }
    catch {
      stopPolling()
    }
  }, 2000)
}

async function handleStartJob() {
  if (!canStartJob.value) {
    ElMessage.warning('迁移前检查未通过，或已有任务正在运行')
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
    await Promise.all([loadJobs(), loadReadiness()])
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

async function handleManualUpload() {
  const ids = selectedPending.value
    .map(item => item.id)
    .filter((id): id is number => id != null)
  if (!ids.length) {
    ElMessage.warning('请先选择待迁移记录')
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
    ElMessage.success(`手工上传完成：${response.data?.success ?? 0}/${ids.length} 成功或已跳过`)
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
    ElMessage.success(`病案手工上传完成：${response.data?.success ?? 0}/${response.data?.total ?? 0}`)
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
    ElMessage.warning('请在迁移日志中选择失败记录')
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

function handleLogFilterChange() {
  logPage.value = 1
  void loadLogs()
}

onMounted(() => {
  void refreshAll()
})
onBeforeUnmount(stopPolling)
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
          先检查、再试迁移、按批次扩大，确认稳定后才允许全量迁移。
        </p>
      </div>
      <el-button :icon="Refresh" @click="refreshAll">
        刷新
      </el-button>
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
        <strong>{{ item.value.toLocaleString('zh-CN') }}</strong>
        <small>{{ item.note }}</small>
      </article>
    </section>

    <div class="primary-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <strong>迁移前检查</strong>
              <p>只抽样检查，不上传文件，也不回填数据库。</p>
            </div>
            <el-tag :type="readiness?.ready ? 'success' : 'warning'">
              {{ readiness?.ready ? '可开始试迁移' : '需要处理' }}
            </el-tag>
          </div>
        </template>

        <div class="check-grid">
          <div class="check-item" :class="{ ok: readiness?.ossConfigured }">
            <span>OSS 客户端配置</span>
            <strong>{{ readiness?.ossConfigured ? '已配置' : '未通过' }}</strong>
          </div>
          <div class="check-item" :class="{ ok: readiness?.sourcePathReadable }">
            <span>图片源</span>
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
          <span>缺失 {{ readiness?.sampleMissingCount ?? 0 }}</span>
          <span>路径异常 {{ readiness?.sampleInvalidCount ?? 0 }}</span>
          <span>待迁移 {{ (readiness?.pendingCount ?? 0).toLocaleString('zh-CN') }}</span>
        </div>
        <el-alert
          v-if="readiness?.recommendedAction"
          :title="readiness.recommendedAction"
          type="success"
          :closable="false"
          show-icon
        />
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
              <p>同一时刻只运行一个任务，任务创建时固定数据快照上界。</p>
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
              @change="loadPending"
            >
              <el-option
                v-for="item in folders"
                :key="item.folder"
                :label="`${item.folder}（${item.cnt}）`"
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
        </el-form>
      </el-card>
    </div>

    <el-card v-if="currentJob" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>当前任务 #{{ currentJob.id }}</strong>
            <p>{{ modeLabel(currentJob.mode) }} · {{ currentJob.scopeValue || '全部目录' }} · 创建者 {{ currentJob.createdBy || '-' }}</p>
          </div>
          <div class="header-actions">
            <el-tag :type="statusMeta(currentJob.status).type">
              {{ statusMeta(currentJob.status).label }}
            </el-tag>
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
        :percentage="Math.min(100, Number(currentJob.rate || 0))"
        :stroke-width="18"
        text-inside
      />
      <div class="facts">
        <span>计划 {{ currentJob.totalCount ?? 0 }}</span>
        <span>已处理 {{ currentJob.processedCount ?? 0 }}</span>
        <span>失败 {{ currentJob.failedCount ?? 0 }}</span>
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

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>待迁移抽样</strong>
            <p>页面最多显示 100 条；大量迁移请使用任务，不要手工全选上传。</p>
          </div>
          <el-button
            type="primary"
            plain
            :icon="UploadFilled"
            :disabled="!selectedPending.length || hasActiveJob"
            :loading="loading.manual"
            @click="handleManualUpload"
          >
            手工上传 {{ selectedPending.length }} 条
          </el-button>
        </div>
      </template>
      <el-table :data="pendingList" stripe @selection-change="handlePendingSelection">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="bah" label="病案号" width="130" />
        <el-table-column prop="sjh" label="上架号" width="130" />
        <el-table-column prop="folder" label="目录" min-width="130" show-overflow-tooltip />
        <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="migrationStatus" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.migrationStatus).type" size="small">
              {{ statusMeta(row.migrationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="manual-bah">
        <span>单份病案验证</span>
        <el-input
          v-model="bahInput"
          placeholder="输入病案号"
          clearable
          @keyup.enter="handleBahUpload"
        />
        <el-button :loading="loading.bah" :disabled="hasActiveJob" @click="handleBahUpload">
          按病案号手工上传
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>迁移任务历史</strong>
            <p>用于确认试迁移与批次迁移是否稳定。</p>
          </div>
        </div>
      </template>
      <el-table :data="jobs" stripe>
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
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>迁移日志</strong>
            <p>核对源文件或权限后，可人工重置永久失败记录。</p>
          </div>
          <div class="header-actions">
            <el-select
              v-model="logStatus"
              clearable
              placeholder="全部状态"
              @change="handleLogFilterChange"
            >
              <el-option label="成功" value="success" />
              <el-option label="失败" value="failed" />
            </el-select>
            <el-button
              :disabled="!failedLogScanIds.length || hasActiveJob"
              :loading="loading.retry"
              @click="handleRetryFailed"
            >
              重置失败记录 {{ failedLogScanIds.length }} 条
            </el-button>
          </div>
        </div>
      </template>
      <el-table
        v-loading="loading.logs"
        :data="logs"
        stripe
        @selection-change="handleLogSelection"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="scanId" label="Scan ID" width="100" />
        <el-table-column prop="localPath" label="源路径" min-width="230" show-overflow-tooltip />
        <el-table-column prop="migrationStatus" label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.migrationStatus).type" size="small">
              {{ statusMeta(row.migrationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="220" show-overflow-tooltip />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.migratedAt || row.createdAt) }}
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
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 18px;
}

.page-header,
.card-header,
.header-actions,
.facts,
.manual-bah {
  display: flex;
  align-items: center;
}

.page-header,
.card-header {
  gap: 16px;
  justify-content: space-between;
}

.page-header h2,
.card-header p,
.subtitle,
.eyebrow {
  margin: 0;
}

.page-header h2 {
  margin-top: 4px;
  font-size: 26px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--el-color-primary);
  letter-spacing: 0.12em;
}

.subtitle,
.card-header p {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.metric-card--blue { border-top: 3px solid var(--el-color-primary); }
.metric-card--green { border-top: 3px solid var(--el-color-success); }
.metric-card--amber { border-top: 3px solid var(--el-color-warning); }
.metric-card--danger { border-top: 3px solid var(--el-color-danger); }

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

.facts,
.header-actions,
.manual-bah {
  flex-wrap: wrap;
  gap: 12px;
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

:deep(.el-input-number),
:deep(.el-select) {
  width: 100%;
}

.manual-bah {
  justify-content: flex-end;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.manual-bah :deep(.el-input) {
  width: 220px;
}

.header-actions :deep(.el-select) {
  width: 140px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (width <= 1100px) {
  .metric-grid,
  .primary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 720px) {
  .metric-grid,
  .primary-grid,
  .check-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

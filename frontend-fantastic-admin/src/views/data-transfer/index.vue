<script setup lang="ts">
import type { UploadUserFile } from 'element-plus'
import type {
  DataTransferEntityType,
  DataTransferFile,
  DataTransferImportMode,
  DataTransferJob,
  DataTransferJobDetail,
} from '@/api/modules/data-transfer'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  cancelDataTransfer,
  createDataExport,
  createInboxImport,
  createUploadImport,
  downloadDataTransferErrors,
  downloadDataTransferFile,
  downloadDataTransferTemplate,
  executeDataTransfer,
  getDataTransferInbox,
  getDataTransferJob,
  getDataTransferJobs,
  pauseDataTransfer,
  resumeDataTransfer,
  retryDataTransfer,
} from '@/api/modules/data-transfer'

defineOptions({ name: 'DataTransferPage' })

const activeTab = ref('import')
const jobs = ref<DataTransferJob[]>([])
const inboxFiles = ref<string[]>([])
const uploadFiles = ref<UploadUserFile[]>([])
const selectedInboxFiles = ref<string[]>([])
const selectedDetail = ref<DataTransferJobDetail | null>(null)
const detailVisible = ref(false)
const uploadProgress = ref(0)
const loading = reactive({
  jobs: false,
  inbox: false,
  createImport: false,
  createExport: false,
  detail: false,
})

const importForm = reactive<{
  entityType: DataTransferEntityType
  importMode: DataTransferImportMode
  sourceType: 'UPLOAD' | 'INBOX'
}>({
  entityType: 'MR_STATISTICS',
  importMode: 'SKIP_DUPLICATES',
  sourceType: 'UPLOAD',
})

const exportForm = reactive<{
  entityType: DataTransferEntityType
  startId?: number
  endId?: number
  rowsPerPart: number
}>({
  entityType: 'MR_STATISTICS',
  rowsPerPart: 1_000_000,
})

const activeJobs = computed(() => jobs.value.filter(job => [
  'CREATED', 'UPLOADED', 'VALIDATING', 'IMPORTING', 'EXPORTING', 'MERGING',
].includes(job.status)).length)

let refreshTimer: number | undefined

function entityLabel(value: string) {
  return value === 'MR_SCAN' ? '扫描影像索引 mr_scan' : '病案统计 mr_statistics'
}

function directionLabel(value: string) {
  return value === 'EXPORT' ? '导出' : '导入'
}

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    CREATED: '已创建',
    UPLOADING: '上传中',
    UPLOADED: '待执行',
    VALIDATING: '校验中',
    WAITING_CONFIRM: '待确认',
    IMPORTING: '导入中',
    EXPORTING: '导出中',
    MERGING: '合并中',
    PAUSED: '已暂停',
    CANCELLED: '已取消',
    COMPLETED: '已完成',
    COMPLETED_WITH_ERRORS: '完成但有错误',
    FAILED: '失败',
  }
  return labels[value] ?? value
}

function statusType(value: string) {
  if (value === 'COMPLETED') { return 'success' }
  if (value === 'COMPLETED_WITH_ERRORS' || value === 'PAUSED') { return 'warning' }
  if (value === 'FAILED' || value === 'CANCELLED') { return 'danger' }
  return 'primary'
}

function formatNumber(value?: number) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function formatSize(value?: number) {
  const bytes = Number(value ?? 0)
  if (bytes < 1024) { return `${bytes} B` }
  if (bytes < 1024 ** 2) { return `${(bytes / 1024).toFixed(1)} KB` }
  if (bytes < 1024 ** 3) { return `${(bytes / 1024 ** 2).toFixed(1)} MB` }
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`
}

function formatDate(value?: string) {
  if (!value) { return '-' }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function loadJobs(silent = false) {
  if (!silent) { loading.jobs = true }
  try {
    const response = await getDataTransferJobs(100)
    jobs.value = response.data ?? []
    if (selectedDetail.value) {
      const current = jobs.value.find(job => job.id === selectedDetail.value?.job.id)
      if (current && !['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED'].includes(current.status)) {
        await loadDetail(current.id, false)
      }
    }
  }
  finally {
    if (!silent) { loading.jobs = false }
  }
}

async function loadInbox() {
  loading.inbox = true
  try {
    const response = await getDataTransferInbox()
    inboxFiles.value = response.data ?? []
    selectedInboxFiles.value = selectedInboxFiles.value.filter(item => inboxFiles.value.includes(item))
  }
  finally {
    loading.inbox = false
  }
}

async function loadDetail(jobId: number, openDrawer = true) {
  loading.detail = true
  try {
    const response = await getDataTransferJob(jobId)
    selectedDetail.value = response.data ?? null
    if (openDrawer) { detailVisible.value = true }
  }
  finally {
    loading.detail = false
  }
}

async function handleTemplateDownload(entityType: DataTransferEntityType) {
  const blob = await downloadDataTransferTemplate(entityType)
  saveBlob(blob, entityType === 'MR_SCAN' ? 'mr-scan-template.csv' : 'mr-statistics-template.csv')
}

async function createImportJob() {
  loading.createImport = true
  uploadProgress.value = 0
  try {
    let job: DataTransferJob | undefined
    if (importForm.sourceType === 'UPLOAD') {
      const files = uploadFiles.value
        .map(item => item.raw)
        .filter((file): file is File => Boolean(file))
      if (files.length === 0) {
        ElMessage.warning('请选择至少一个 CSV 文件')
        return
      }
      const response = await createUploadImport(
        importForm.entityType,
        importForm.importMode,
        files,
        (event) => {
          uploadProgress.value = event.total ? Math.round(event.loaded * 100 / event.total) : 0
        },
      )
      job = response.data
    }
    else {
      if (selectedInboxFiles.value.length === 0) {
        ElMessage.warning('请选择服务器 inbox 文件')
        return
      }
      const response = await createInboxImport({
        entityType: importForm.entityType,
        importMode: importForm.importMode,
        filenames: selectedInboxFiles.value,
      })
      job = response.data
    }

    if (!job) {
      throw new Error('创建任务后未返回任务信息')
    }
    await executeDataTransfer(job.id)
    ElMessage.success(`导入任务 #${job.id} 已启动`)
    uploadFiles.value = []
    selectedInboxFiles.value = []
    activeTab.value = 'jobs'
    await loadJobs()
  }
  catch (error: any) {
    ElMessage.error(error?.message || '创建导入任务失败')
  }
  finally {
    loading.createImport = false
  }
}

async function createExportJob() {
  loading.createExport = true
  try {
    const response = await createDataExport({
      entityType: exportForm.entityType,
      startId: exportForm.startId,
      endId: exportForm.endId,
      rowsPerPart: exportForm.rowsPerPart,
    })
    if (!response.data) {
      throw new Error('创建任务后未返回任务信息')
    }
    ElMessage.success(`导出任务 #${response.data.id} 已启动`)
    activeTab.value = 'jobs'
    await loadJobs()
  }
  catch (error: any) {
    ElMessage.error(error?.message || '创建导出任务失败')
  }
  finally {
    loading.createExport = false
  }
}

async function handleAction(job: DataTransferJob, action: 'execute' | 'pause' | 'resume' | 'cancel' | 'retry') {
  if (action === 'cancel') {
    await ElMessageBox.confirm('取消后，已经提交的数据会保留，未处理文件不会继续执行。确定取消吗？', '取消任务', {
      type: 'warning',
    })
  }
  const actions = {
    execute: executeDataTransfer,
    pause: pauseDataTransfer,
    resume: resumeDataTransfer,
    cancel: cancelDataTransfer,
    retry: retryDataTransfer,
  }
  await actions[action](job.id)
  ElMessage.success('操作成功')
  await loadJobs()
  if (detailVisible.value) { await loadDetail(job.id, false) }
}

async function downloadFile(file: DataTransferFile) {
  const blob = await downloadDataTransferFile(file.id)
  saveBlob(blob, file.downloadName || file.originalFilename)
}

async function downloadErrors(jobId: number, file: DataTransferFile) {
  const blob = await downloadDataTransferErrors(jobId, file.id)
  saveBlob(blob, `errors-file-${file.id}.csv.gz`)
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  await Promise.all([loadJobs(), loadInbox()])
  refreshTimer = window.setInterval(() => {
    if (activeJobs.value > 0 || detailVisible.value) {
      void loadJobs(true)
    }
  }, 3000)
})

onBeforeUnmount(() => {
  if (refreshTimer) { window.clearInterval(refreshTimer) }
})
</script>

<template>
  <div class="data-transfer-page">
    <div class="page-header">
      <div>
        <h1>数据交换中心</h1>
        <p>通过任务化 CSV 导入导出数据库数据。大规模数据使用 PostgreSQL COPY 和暂存表处理，不在浏览器中解析。</p>
      </div>
      <el-button :loading="loading.jobs" @click="loadJobs()">
        刷新任务
      </el-button>
    </div>

    <el-alert
      title="mr_scan 约 3000 万条时，请拆分为每个 50 万至 100 万行的 CSV，并优先放入服务器 inbox 目录。"
      type="warning"
      :closable="false"
      show-icon
      class="capacity-alert"
    />

    <el-tabs v-model="activeTab" class="transfer-tabs">
      <el-tab-pane label="数据导入" name="import">
        <div class="form-grid">
          <el-card shadow="never" class="form-card">
            <template #header>
              <div class="card-title">
                <strong>创建导入任务</strong>
                <span>CSV 必须使用标准表头和 UTF-8 编码</span>
              </div>
            </template>

            <el-form label-position="top">
              <el-form-item label="数据类型">
                <el-radio-group v-model="importForm.entityType">
                  <el-radio-button value="MR_STATISTICS">
                    mr_statistics（约 20 万条）
                  </el-radio-button>
                  <el-radio-button value="MR_SCAN">
                    mr_scan（约 3000 万条）
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="重复数据处理">
                <el-radio-group v-model="importForm.importMode">
                  <el-radio value="SKIP_DUPLICATES">
                    跳过重复
                  </el-radio>
                  <el-radio value="UPSERT">
                    更新已存在记录
                  </el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="文件来源">
                <el-radio-group v-model="importForm.sourceType" @change="importForm.sourceType === 'INBOX' && loadInbox()">
                  <el-radio-button value="UPLOAD">
                    浏览器上传
                  </el-radio-button>
                  <el-radio-button value="INBOX">
                    服务器 inbox
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>

              <el-form-item v-if="importForm.sourceType === 'UPLOAD'" label="CSV 文件">
                <el-upload
                  v-model:file-list="uploadFiles"
                  drag
                  multiple
                  :auto-upload="false"
                  accept=".csv,.csv.gz"
                  class="upload-area"
                >
                  <div class="upload-icon i-ant-design:cloud-upload-outlined" />
                  <div>拖放 CSV 到这里，或点击选择</div>
                  <template #tip>
                    <div class="upload-tip">
                      Web 单文件建议不超过 2GB；更大的文件使用服务器 inbox。
                    </div>
                  </template>
                </el-upload>
              </el-form-item>

              <el-form-item v-else label="服务器文件">
                <div class="inbox-panel" v-loading="loading.inbox">
                  <el-checkbox-group v-model="selectedInboxFiles">
                    <el-checkbox v-for="file in inboxFiles" :key="file" :value="file" border>
                      {{ file }}
                    </el-checkbox>
                  </el-checkbox-group>
                  <el-empty v-if="!loading.inbox && inboxFiles.length === 0" description="inbox 中没有 CSV 文件" :image-size="72" />
                </div>
              </el-form-item>

              <el-progress v-if="loading.createImport && uploadProgress > 0" :percentage="uploadProgress" class="upload-progress" />

              <div class="form-actions">
                <el-button @click="handleTemplateDownload(importForm.entityType)">
                  下载 CSV 模板
                </el-button>
                <el-button type="primary" :loading="loading.createImport" @click="createImportJob">
                  创建并开始导入
                </el-button>
              </div>
            </el-form>
          </el-card>

          <el-card shadow="never" class="guide-card">
            <template #header>
              <strong>导入规则</strong>
            </template>
            <dl>
              <dt>空上架号</dt>
              <dd>CSV 留空并写入 NULL，不使用 00000000 等占位号。</dd>
              <dt>编号格式</dt>
              <dd>1～8 位纯数字会自动补齐为 8 位；异常编码保留并进入校验。</dd>
              <dt>日期格式</dt>
              <dd>使用 YYYY-MM-DD 或 YYYY/MM/DD。</dd>
              <dt>大表处理</dt>
              <dd>每个文件独立提交；失败文件可以单独重试，已完成文件不会重复执行。</dd>
              <dt>去重规则</dt>
              <dd>统计表按业务字段指纹去重；扫描表按文件夹、病人序号、病案号和文件名去重。</dd>
            </dl>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="数据导出" name="export">
        <el-card shadow="never" class="form-card export-card">
          <template #header>
            <div class="card-title">
              <strong>创建分卷导出任务</strong>
              <span>结果以 UTF-8 CSV.GZ 保存，可从任务详情逐卷下载</span>
            </div>
          </template>
          <el-form label-position="top" class="export-form">
            <el-form-item label="数据类型">
              <el-select v-model="exportForm.entityType">
                <el-option label="病案统计 mr_statistics" value="MR_STATISTICS" />
                <el-option label="扫描影像索引 mr_scan" value="MR_SCAN" />
              </el-select>
            </el-form-item>
            <el-form-item label="起始 ID（不含，可空）">
              <el-input-number v-model="exportForm.startId" :min="0" :controls="false" placeholder="全部" />
            </el-form-item>
            <el-form-item label="结束 ID（包含，可空）">
              <el-input-number v-model="exportForm.endId" :min="1" :controls="false" placeholder="当前最大 ID" />
            </el-form-item>
            <el-form-item label="每卷行数">
              <el-input-number v-model="exportForm.rowsPerPart" :min="10000" :max="2000000" :step="100000" />
            </el-form-item>
            <el-form-item class="export-submit">
              <el-button type="primary" :loading="loading.createExport" @click="createExportJob">
                创建导出任务
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="`任务记录${activeJobs ? ` (${activeJobs})` : ''}`" name="jobs">
        <el-card shadow="never" class="jobs-card">
          <el-table v-loading="loading.jobs" :data="jobs" row-key="id">
            <el-table-column prop="id" label="任务" width="90">
              <template #default="{ row }">
                #{{ row.id }}
              </template>
            </el-table-column>
            <el-table-column label="方向" width="80">
              <template #default="{ row }">
                {{ directionLabel(row.direction) }}
              </template>
            </el-table-column>
            <el-table-column label="数据类型" min-width="190">
              <template #default="{ row }">
                {{ entityLabel(row.entityType) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="light">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" min-width="180">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.progress || 0)" :stroke-width="8" />
                <small>{{ row.completedFiles }}/{{ row.totalFiles }} 文件 · {{ row.currentStage || '-' }}</small>
              </template>
            </el-table-column>
            <el-table-column label="处理结果" min-width="230">
              <template #default="{ row }">
                <div class="count-line">
                  <span>新增 {{ formatNumber(row.insertedRows) }}</span>
                  <span>更新 {{ formatNumber(row.updatedRows) }}</span>
                  <span>跳过 {{ formatNumber(row.skippedRows) }}</span>
                  <span class="danger-text">错误 {{ formatNumber(row.invalidRows) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="loadDetail(row.id)">
                  详情
                </el-button>
                <el-button v-if="['CREATED', 'UPLOADED', 'FAILED'].includes(row.status)" link type="primary" @click="handleAction(row, 'execute')">
                  开始
                </el-button>
                <el-button v-if="['IMPORTING', 'EXPORTING', 'VALIDATING', 'MERGING'].includes(row.status)" link @click="handleAction(row, 'pause')">
                  暂停
                </el-button>
                <el-button v-if="row.status === 'PAUSED'" link type="primary" @click="handleAction(row, 'resume')">
                  继续
                </el-button>
                <el-button v-if="['FAILED', 'COMPLETED_WITH_ERRORS'].includes(row.status) && Number(row.failedFiles || 0) > 0" link type="warning" @click="handleAction(row, 'retry')">
                  重试
                </el-button>
                <el-button v-if="!['COMPLETED', 'COMPLETED_WITH_ERRORS', 'CANCELLED'].includes(row.status)" link type="danger" @click="handleAction(row, 'cancel')">
                  取消
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" title="任务详情" size="78%" destroy-on-close>
      <div v-loading="loading.detail" v-if="selectedDetail" class="detail-content">
        <div class="detail-summary">
          <div><span>任务</span><strong>#{{ selectedDetail.job.id }}</strong></div>
          <div><span>状态</span><strong>{{ statusLabel(selectedDetail.job.status) }}</strong></div>
          <div><span>总行数</span><strong>{{ formatNumber(selectedDetail.job.totalRows) }}</strong></div>
          <div><span>有效</span><strong>{{ formatNumber(selectedDetail.job.validRows) }}</strong></div>
          <div><span>错误</span><strong class="danger-text">{{ formatNumber(selectedDetail.job.invalidRows) }}</strong></div>
          <div><span>耗时阶段</span><strong>{{ selectedDetail.job.currentStage || '-' }}</strong></div>
        </div>

        <el-alert v-if="selectedDetail.job.errorMessage" :title="selectedDetail.job.errorMessage" type="error" :closable="false" />

        <h3>文件明细</h3>
        <el-table :data="selectedDetail.files" row-key="id">
          <el-table-column prop="sequenceNo" label="#" width="60" />
          <el-table-column prop="originalFilename" label="文件" min-width="220" show-overflow-tooltip />
          <el-table-column label="大小" width="110">
            <template #default="{ row }">
              {{ formatSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="150">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="行数" width="120">
            <template #default="{ row }">
              {{ formatNumber(row.totalRows) }}
            </template>
          </el-table-column>
          <el-table-column label="新增/更新/跳过/错误" min-width="210">
            <template #default="{ row }">
              {{ formatNumber(row.insertedRows) }} / {{ formatNumber(row.updatedRows) }} /
              {{ formatNumber(row.skippedRows) }} / {{ formatNumber(row.invalidRows) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button v-if="selectedDetail?.job.direction === 'EXPORT' && row.status === 'COMPLETED'" link type="primary" @click="downloadFile(row)">
                下载分卷
              </el-button>
              <el-button v-if="row.invalidRows > 0" link type="warning" @click="downloadErrors(selectedDetail!.job.id, row)">
                错误报告
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="selectedDetail.errors.length">
          <h3>错误样本（最多显示 200 条）</h3>
          <el-table :data="selectedDetail.errors" max-height="360">
            <el-table-column prop="sourceRowNo" label="CSV 行" width="90" />
            <el-table-column prop="errorCode" label="错误代码" width="190" />
            <el-table-column prop="errorMessage" label="原因" min-width="220" />
            <el-table-column prop="rawData" label="原始数据" min-width="360" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.data-transfer-page {
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.page-header h1 {
  margin: 0;
  font-size: 26px;
}

.page-header p {
  max-width: 860px;
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.capacity-alert {
  margin-bottom: 18px;
}

.transfer-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.7fr);
  gap: 18px;
}

.form-card,
.guide-card,
.jobs-card {
  border-radius: 12px;
}

.card-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.card-title span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload),
.upload-area :deep(.el-upload-dragger) {
  width: 100%;
}

.upload-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  color: var(--el-color-primary);
}

.upload-tip {
  color: var(--el-text-color-secondary);
}

.upload-progress {
  margin-bottom: 18px;
}

.inbox-panel {
  width: 100%;
  min-height: 160px;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.inbox-panel :deep(.el-checkbox-group) {
  display: grid;
  gap: 10px;
}

.inbox-panel :deep(.el-checkbox) {
  width: 100%;
  margin: 0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.guide-card dl {
  margin: 0;
}

.guide-card dt {
  margin-top: 16px;
  font-weight: 700;
}

.guide-card dt:first-child {
  margin-top: 0;
}

.guide-card dd {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.65;
}

.export-card {
  max-width: 1120px;
}

.export-form {
  display: grid;
  grid-template-columns: 1.3fr repeat(3, 1fr) auto;
  align-items: end;
  gap: 16px;
}

.export-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.export-form :deep(.el-select),
.export-form :deep(.el-input-number) {
  width: 100%;
}

.count-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  font-size: 13px;
}

.danger-text {
  color: var(--el-color-danger);
}

.detail-content h3 {
  margin: 26px 0 12px;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.detail-summary > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: var(--el-fill-color-light);
  border-radius: 10px;
}

.detail-summary span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (width <= 1100px) {
  .form-grid,
  .export-form {
    grid-template-columns: 1fr;
  }

  .detail-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 680px) {
  .data-transfer-page {
    padding: 14px;
  }

  .page-header,
  .card-title {
    flex-direction: column;
  }

  .detail-summary {
    grid-template-columns: 1fr 1fr;
  }
}
</style>

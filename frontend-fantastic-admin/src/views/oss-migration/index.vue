<script setup lang="ts">
import { Link, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { getMigrationLogs, getMigrationStatistics, getPendingMigrations, uploadByBah, uploadToOss } from '@/api/modules/oss'
import type { MigrationLogRecord, MigrationStatistics, OssUploadResult, ScanRecord } from '@/api/types'

defineOptions({ name: 'OssMigrationPage' })

// ==================== State ====================
const stats = ref<MigrationStatistics>({})
const pendingList = ref<ScanRecord[]>([])
const logList = ref<MigrationLogRecord[]>([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = ref(20)
const logStatusFilter = ref('')

const loading = reactive({
  stats: false,
  pending: false,
  logs: false,
  upload: false,
  bahUpload: false,
})

const bahInput = ref('')
const uploadResults = ref<OssUploadResult[]>([])
const selectedPending = ref<ScanRecord[]>([])

// ==================== Computed ====================
const progressPercentage = computed(() => {
  return stats.value?.percentage ?? 0
})

const progressStatus = computed(() => {
  const pct = progressPercentage.value
  if (pct >= 100) return 'success'
  if (pct >= 50) return ''
  return 'warning'
})

const summaryCards = computed(() => [
  { label: '总记录数', value: stats.value?.totalCount ?? 0, color: '#409eff', icon: 'i-ant-design:database-twotone' },
  { label: '已迁移', value: stats.value?.migratedCount ?? 0, color: '#67c23a', icon: 'i-ant-design:check-circle-twotone' },
  { label: '待迁移', value: stats.value?.pendingCount ?? 0, color: '#e6a23c', icon: 'i-ant-design:clock-circle-twotone' },
  { label: '失败', value: stats.value?.failedCount ?? 0, color: '#f56c6c', icon: 'i-ant-design:close-circle-twotone' },
])

// ==================== Data Loading ====================
async function loadStats() {
  loading.stats = true
  try {
    const res = await getMigrationStatistics()
    stats.value = res.data || res || {}
  }
  catch (err: any) {
    ElMessage.error(err?.message || '加载统计数据失败')
  }
  finally {
    loading.stats = false
  }
}

async function loadPending() {
  loading.pending = true
  try {
    const res = await getPendingMigrations(50)
    const data = res.data || res || {}
    pendingList.value = Array.isArray(data.list) ? data.list : []
  }
  catch (err: any) {
    ElMessage.error(err?.message || '加载待迁移列表失败')
  }
  finally {
    loading.pending = false
  }
}

async function loadLogs() {
  loading.logs = true
  try {
    const res = await getMigrationLogs({
      status: logStatusFilter.value || undefined,
      page: logPage.value,
      size: logSize.value,
    })
    const data = res.data || res || {}
    logList.value = Array.isArray(data.list) ? data.list : []
    logTotal.value = Number(data.total || 0)
  }
  catch (err: any) {
    ElMessage.error(err?.message || '加载迁移日志失败')
  }
  finally {
    loading.logs = false
  }
}

async function refreshAll() {
  await Promise.all([loadStats(), loadPending(), loadLogs()])
}

// ==================== Upload Actions ====================
async function handleBahUpload() {
  const bah = bahInput.value.trim()
  if (!bah) {
    ElMessage.warning('请输入病案号')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认上传病案号 ${bah} 下的所有图片到 OSS？`,
      '确认上传',
      { confirmButtonText: '开始上传', cancelButtonText: '取消', type: 'info' },
    )
  }
  catch {
    return
  }

  loading.bahUpload = true
  uploadResults.value = []
  try {
    const res = await uploadByBah(bah)
    const data = res.data || res || {}
    uploadResults.value = Array.isArray(data.results) ? data.results : []
    const successCount = uploadResults.value.filter(r => r.status === 'success').length
    ElMessage.success(`上传完成：${successCount}/${uploadResults.value.length} 成功`)
    await refreshAll()
  }
  catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  }
  finally {
    loading.bahUpload = false
  }
}

async function handleBatchUpload() {
  if (!selectedPending.value.length) {
    ElMessage.warning('请先选择要上传的记录')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认上传选中的 ${selectedPending.value.length} 条记录到 OSS？`,
      '确认批量上传',
      { confirmButtonText: '开始上传', cancelButtonText: '取消', type: 'info' },
    )
  }
  catch {
    return
  }

  loading.upload = true
  uploadResults.value = []
  try {
    const ids = selectedPending.value.map(s => s.id!).filter(Boolean)
    const res = await uploadToOss(ids)
    const data = res.data || res || {}
    uploadResults.value = Array.isArray(data.results) ? data.results : []
    const successCount = uploadResults.value.filter(r => r.status === 'success').length
    ElMessage.success(`上传完成：${successCount}/${uploadResults.value.length} 成功`)
    await refreshAll()
  }
  catch (err: any) {
    ElMessage.error(err?.message || '批量上传失败')
  }
  finally {
    loading.upload = false
  }
}

function handlePendingSelection(rows: ScanRecord[]) {
  selectedPending.value = rows
}

// ==================== Helpers ====================
function statusTag(status?: string) {
  const map: Record<string, { type: string, label: string }> = {
    success: { type: 'success', label: '成功' },
    failed: { type: 'danger', label: '失败' },
    pending: { type: 'warning', label: '待迁移' },
    migrating: { type: 'info', label: '迁移中' },
    migrated: { type: 'success', label: '已迁移' },
    verified: { type: 'success', label: '已验证' },
    not_migrated: { type: 'info', label: '未迁移' },
    skipped: { type: 'info', label: '已跳过' },
  }
  return map[status || ''] || { type: 'info', label: status || '-' }
}

function formatBytes(bytes?: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function formatDate(d?: string) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN')
}

// 获取 OSS 图片的完整 URL
// 后端已经为成功的记录生成了预签名 URL，直接返回即可
function getOssImageUrl(ossUrl?: string) {
  return ossUrl || ''
}

// ==================== Lifecycle ====================
onMounted(refreshAll)
</script>

<template>
  <div class="page-shell">
    <!-- Header -->
    <div class="page-header">
      <div>
        <p class="eyebrow">
          OSS Migration
        </p>
        <h2>OSS 迁移管理</h2>
        <p class="subtitle">
          管理本地图片到 OSS 的迁移，支持按病案号和批量上传。
        </p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="refreshAll">
        刷新数据
      </el-button>
    </div>

    <!-- Statistics Cards -->
    <section class="summary-grid">
      <el-card shadow="never" class="stat-card total-count">
        <div class="stat-icon"><i :class="summaryCards[0].icon" /></div>
        <div class="stat-body">
          <div class="stat-label">{{ summaryCards[0].label }}</div>
          <div class="stat-value">{{ summaryCards[0].value.toLocaleString() }}</div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card migrated-count">
        <div class="stat-icon"><i :class="summaryCards[1].icon" /></div>
        <div class="stat-body">
          <div class="stat-label">{{ summaryCards[1].label }}</div>
          <div class="stat-value">{{ summaryCards[1].value.toLocaleString() }}</div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card pending-count">
        <div class="stat-icon"><i :class="summaryCards[2].icon" /></div>
        <div class="stat-body">
          <div class="stat-label">{{ summaryCards[2].label }}</div>
          <div class="stat-value">{{ summaryCards[2].value.toLocaleString() }}</div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card failed-count">
        <div class="stat-icon"><i :class="summaryCards[3].icon" /></div>
        <div class="stat-body">
          <div class="stat-label">{{ summaryCards[3].label }}</div>
          <div class="stat-value">{{ summaryCards[3].value.toLocaleString() }}</div>
        </div>
      </el-card>
    </section>

    <!-- Progress Bar -->
    <el-card shadow="never">
      <div class="progress-section">
        <div class="progress-header">
          <span class="progress-title">迁移进度</span>
          <span class="progress-pct">{{ progressPercentage }}%</span>
        </div>
        <el-progress
          :percentage="progressPercentage"
          :status="progressStatus"
          :stroke-width="16"
          :text-inside="true"
        />
      </div>
    </el-card>

    <!-- Upload by BAH -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>按病案号上传</span>
        </div>
      </template>
      <el-form inline @submit.prevent="handleBahUpload">
        <el-form-item label="病案号">
          <el-input
            v-model="bahInput"
            placeholder="输入8位病案号"
            clearable
            style="width: 200px"
            @keyup.enter="handleBahUpload"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading.bahUpload"
            :icon="UploadFilled"
            @click="handleBahUpload"
          >
            上传到 OSS
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Upload Results -->
      <div v-if="uploadResults.length" class="upload-results">
        <h4>上传结果（{{ uploadResults.length }} 条）</h4>
        <el-table :data="uploadResults" stripe size="small" max-height="300">
          <el-table-column prop="scanId" label="Scan ID" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type as any" size="small">
                {{ statusTag(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="OSS 链接" min-width="250">
            <template #default="{ row }">
              <a
                v-if="row.ossUrl && row.status === 'success'"
                :href="row.ossUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="oss-link"
              >
                <el-icon><Link /></el-icon>
                <span class="link-text">查看图片</span>
              </a>
              <span v-else class="no-link">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="fileSize" label="文件大小" width="120">
            <template #default="{ row }">
              {{ formatBytes(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="checksumMd5" label="MD5" min-width="200" show-overflow-tooltip />
          <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
    </el-card>

    <!-- Pending Migrations Table -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>待迁移记录 ({{ pendingList.length }})</span>
          <el-button
            type="primary"
            size="small"
            :loading="loading.upload"
            :disabled="!selectedPending.length"
            @click="handleBatchUpload"
          >
            批量上传 ({{ selectedPending.length }})
          </el-button>
        </div>
      </template>
      <el-table
        v-loading="loading.pending"
        :data="pendingList"
        stripe
        size="small"
        max-height="400"
        @selection-change="handlePendingSelection"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="bah" label="病案号" width="120" />
        <el-table-column prop="brxh" label="病人序号" width="100" />
        <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="folder" label="目录" min-width="120" show-overflow-tooltip />
        <el-table-column prop="migrationStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.migrationStatus).type as any" size="small">
              {{ statusTag(row.migrationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Migration Logs -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>迁移日志</span>
          <div class="log-filters">
            <el-select
              v-model="logStatusFilter"
              clearable
              placeholder="全部状态"
              size="small"
              style="width: 130px"
              @change="loadLogs"
            >
              <el-option label="全部" value="" />
              <el-option label="成功" value="success" />
              <el-option label="失败" value="failed" />
              <el-option label="待迁移" value="pending" />
            </el-select>
            <el-button size="small" :icon="Refresh" @click="loadLogs">
              刷新
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading.logs" :data="logList" stripe size="small">
        <el-table-column prop="scanId" label="Scan ID" width="90" />
        <el-table-column prop="localPath" label="本地路径" min-width="200" show-overflow-tooltip />
        <el-table-column label="OSS 链接" min-width="150">
          <template #default="{ row }">
            <a
              v-if="row.ossUrl && row.migrationStatus === 'success'"
              :href="getOssImageUrl(row.ossUrl)"
              target="_blank"
              rel="noopener noreferrer"
              class="oss-link"
            >
              <el-icon><Link /></el-icon>
              <span class="link-text">查看</span>
            </a>
            <span v-else class="no-link">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="migrationStatus" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.migrationStatus).type as any" size="small">
              {{ statusTag(row.migrationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">
            {{ formatBytes(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="checksumMd5" label="MD5" width="180" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="migratedAt" label="迁移时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.migratedAt) }}
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
  gap: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

/* el-card 本身只承载顶部彩条，内容由 __body 控制 */
.stat-card {
  position: relative;
  overflow: hidden;
  border-top: 3px solid transparent;
}

.stat-card.total-count { border-top-color: #409eff; }
.stat-card.migrated-count { border-top-color: #67c23a; }
.stat-card.pending-count { border-top-color: #e6a23c; }
.stat-card.failed-count { border-top-color: #f56c6c; }

/* 穿透 el-card__body，实现 icon + body 横排 */
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 22px;
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
}

.stat-card.total-count .stat-icon { background: rgba(64,158,255,0.09); color: #409eff; }
.stat-card.migrated-count .stat-icon { background: rgba(103,194,58,0.09); color: #67c23a; }
.stat-card.pending-count .stat-icon { background: rgba(230,162,60,0.09); color: #e6a23c; }
.stat-card.failed-count .stat-icon { background: rgba(245,108,108,0.09); color: #f56c6c; }

.stat-body { flex: 1; min-width: 0; }

.stat-label {
  font-size: 11px;
  color: #86868b;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 6px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1f2b42;
  line-height: 1.2;
  word-break: break-all;
}

.progress-section {
  padding: 4px 0;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.progress-title {
  font-size: 16px;
  font-weight: 600;
}

.progress-pct {
  font-size: 20px;
  font-weight: 800;
  color: #409eff;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-results {
  margin-top: 16px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}

.upload-results h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}

.oss-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  text-decoration: none;
  transition: all 0.2s;
  padding: 4px 8px;
  border-radius: 4px;
}

.oss-link:hover {
  color: #66b1ff;
  background-color: #ecf5ff;
}

.oss-link .el-icon {
  font-size: 14px;
}

.link-text {
  font-size: 13px;
}

.no-link {
  color: #c0c4cc;
  font-size: 13px;
}

.log-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 600px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
import type { MigrationLogRecord, MigrationStatistics, OssUploadResult, ScanRecord } from '@/api/types'
import { Folder, FolderOpened, Link, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { getMigrationLogs, getMigrationStatistics, getPendingFolders, getPendingMigrations, uploadByBah, uploadByFolder, uploadToOss } from '@/api/modules/oss'
import AppLoading from '@/components/AppLoading/index.vue'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'

defineOptions({ name: 'OssMigrationPage' })

// ==================== Types ====================
interface FolderNode {
  id: string
  label: string
  folder?: string
  count?: number
  children?: FolderNode[]
  isLeaf: boolean
}

// ==================== State ====================
const stats = ref<MigrationStatistics>({})
const pendingList = ref<ScanRecord[]>([])
const error = ref('')
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
  folderUpload: false,
  folders: false,
})

const bahInput = ref('')
const folderInput = ref('')
const uploadResults = ref<OssUploadResult[]>([])
const selectedPending = ref<ScanRecord[]>([])
const folderTree = ref<FolderNode[]>([])
const selectedFolder = ref('')

// ==================== Computed ====================
const progressPercentage = computed(() => {
  return stats.value?.percentage ?? 0
})

const progressStatus = computed(() => {
  const pct = progressPercentage.value
  if (pct >= 100) { return 'success' }
  if (pct >= 50) { return '' }
  return 'warning'
})

const summaryCards = computed(() => [
  { label: '总记录数', value: stats.value?.totalCount ?? 0, color: '#409eff', icon: 'i-ant-design:database-twotone' },
  { label: '已迁移', value: stats.value?.migratedCount ?? 0, color: '#67c23a', icon: 'i-ant-design:check-circle-twotone' },
  { label: '待迁移', value: stats.value?.pendingCount ?? 0, color: '#e6a23c', icon: 'i-ant-design:clock-circle-twotone' },
  { label: '失败', value: stats.value?.failedCount ?? 0, color: '#f56c6c', icon: 'i-ant-design:close-circle-twotone' },
])

const pendingTitle = computed(() => {
  return selectedFolder.value ? `待迁移记录 - ${selectedFolder.value} (${pendingList.value.length})` : `待迁移记录 (${pendingList.value.length})`
})

const totalPendingCount = computed(() => {
  let total = 0
  function walk(nodes: FolderNode[]) {
    for (const n of nodes) {
      if (n.isLeaf && n.count) { total += n.count }
      if (n.children) { walk(n.children) }
    }
  }
  walk(folderTree.value)
  return total
})

// ==================== Folder Tree ====================
function buildFolderTree(folders: { folder: string, cnt: number }[]): FolderNode[] {
  const hasNested = folders.some(f => f.folder.includes('/'))

  if (!hasNested) {
    return folders
      .map(f => ({
        id: f.folder,
        label: f.folder,
        folder: f.folder,
        count: f.cnt,
        isLeaf: true,
      }))
      .sort((a, b) => a.label.localeCompare(b.label))
  }

  const rootMap = new Map<string, { node: FolderNode, childMap: Map<string, FolderNode> }>()

  for (const f of folders) {
    const parts = f.folder.split('/')
    const rootKey = parts[0]
    if (!rootMap.has(rootKey)) {
      rootMap.set(rootKey, {
        node: { id: rootKey, label: rootKey, folder: rootKey, children: [], isLeaf: false },
        childMap: new Map(),
      })
    }
    const entry = rootMap.get(rootKey)!

    if (parts.length === 1) {
      entry.node.count = (entry.node.count || 0) + f.cnt
    }
    else {
      const childKey = f.folder
      if (!entry.childMap.has(childKey)) {
        entry.childMap.set(childKey, {
          id: childKey,
          label: parts.slice(1).join('/'),
          folder: childKey,
          count: f.cnt,
          isLeaf: true,
        })
      }
    }
  }

  const result: FolderNode[] = []
  for (const [, entry] of rootMap) {
    entry.node.children = Array.from(entry.childMap.values())
    const childSum = entry.node.children.reduce((s, c) => s + (c.count || 0), 0)
    entry.node.count = (entry.node.count || 0) + childSum
    if (entry.node.children.length === 0) {
      delete entry.node.children
      entry.node.isLeaf = true
    }
    result.push(entry.node)
  }

  result.sort((a, b) => a.label.localeCompare(b.label))
  return result
}

async function loadFolders() {
  loading.folders = true
  try {
    const res = await getPendingFolders()
    folderTree.value = buildFolderTree(res.data ?? [])
  }
  catch (err: any) {
    console.error('[OSS] load folders error:', err)
  }
  finally {
    loading.folders = false
  }
}

function handleFolderClick(node: FolderNode) {
  if (node.isLeaf && node.folder) {
    selectedFolder.value = node.folder
    loadPendingByFolder(node.folder)
  }
  else {
    selectedFolder.value = ''
    loadPending()
  }
}

async function loadPendingByFolder(folder: string) {
  loading.pending = true
  try {
    const res = await getPendingMigrations({ folder })
    pendingList.value = res.data?.list ?? []
    selectedPending.value = []
  }
  catch (err: any) {
    console.error('[OSS] load pending by folder error:', err)
    ElMessage.error(err?.message || '加载文件夹记录失败')
  }
  finally {
    loading.pending = false
  }
}

async function loadPending() {
  selectedFolder.value = ''
  loading.pending = true
  error.value = ''
  try {
    const res = await getPendingMigrations({ limit: 50 })
    pendingList.value = res.data?.list ?? []
  }
  catch (err: any) {
    console.error('[OSS] load pending error:', err)
    error.value = err?.message || '加载待迁移列表失败'
    ElMessage.error(err?.message || '加载待迁移列表失败')
  }
  finally {
    loading.pending = false
  }
}

// ==================== Data Loading ====================
async function loadStats() {
  loading.stats = true
  try {
    const res = await getMigrationStatistics()
    stats.value = res.data ?? {}
  }
  catch {
    // silent
  }
  finally {
    loading.stats = false
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
    const data = res.data ?? { list: [], total: 0 }
    logList.value = data.list
    logTotal.value = data.total
  }
  catch {
    // silent
  }
  finally {
    loading.logs = false
  }
}

function handleLogFilterChange() {
  logPage.value = 1
  loadLogs()
}

async function refreshAll() {
  await Promise.all([loadStats(), loadFolders()])
  await loadPending()
  nextTick(() => loadLogs())
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

async function handleFolderUpload() {
  const folder = folderInput.value.trim()
  if (!folder) {
    ElMessage.warning('请输入文件夹路径')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认上传文件夹 ${folder} 下的所有图片到 OSS？`,
      '确认上传',
      { confirmButtonText: '开始上传', cancelButtonText: '取消', type: 'info' },
    )
  }
  catch {
    return
  }

  loading.folderUpload = true
  uploadResults.value = []
  try {
    const res = await uploadByFolder(folder)
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
    loading.folderUpload = false
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
  if (!bytes) { return '-' }
  if (bytes < 1024) { return `${bytes} B` }
  if (bytes < 1024 * 1024) { return `${(bytes / 1024).toFixed(1)} KB` }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function formatDate(d?: string) {
  if (!d) { return '-' }
  return new Date(d).toLocaleString('zh-CN')
}

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
          管理本地图片到 OSS 的迁移，支持按文件夹/病案号/批量上传。
        </p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="refreshAll">
        刷新数据
      </el-button>
    </div>

    <!-- Statistics Cards -->
    <section class="summary-grid">
      <el-card shadow="never" class="stat-card total-count">
        <div class="stat-icon">
          <i :class="summaryCards[0].icon" />
        </div>
        <div class="stat-body">
          <div class="stat-label">
            {{ summaryCards[0].label }}
          </div>
          <div class="stat-value">
            {{ summaryCards[0].value.toLocaleString() }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card migrated-count">
        <div class="stat-icon">
          <i :class="summaryCards[1].icon" />
        </div>
        <div class="stat-body">
          <div class="stat-label">
            {{ summaryCards[1].label }}
          </div>
          <div class="stat-value">
            {{ summaryCards[1].value.toLocaleString() }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card pending-count">
        <div class="stat-icon">
          <i :class="summaryCards[2].icon" />
        </div>
        <div class="stat-body">
          <div class="stat-label">
            {{ summaryCards[2].label }}
          </div>
          <div class="stat-value">
            {{ summaryCards[2].value.toLocaleString() }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card failed-count">
        <div class="stat-icon">
          <i :class="summaryCards[3].icon" />
        </div>
        <div class="stat-body">
          <div class="stat-label">
            {{ summaryCards[3].label }}
          </div>
          <div class="stat-value">
            {{ summaryCards[3].value.toLocaleString() }}
          </div>
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

    <!-- Folder Tree + Pending Table (side-by-side) -->
    <div class="folder-pending-row">
      <el-card shadow="never" class="folder-tree-card">
        <template #header>
          <div class="card-header">
            <span>待迁移文件夹</span>
            <el-tag size="small" type="info">{{ totalPendingCount }}</el-tag>
          </div>
        </template>
        <div v-loading="loading.folders" class="tree-wrapper">
          <el-tree
            :data="folderTree"
            :props="{ children: 'children', label: 'label' }"
            node-key="id"
            highlight-current
            @node-click="handleFolderClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon :size="16">
                  <FolderOpened v-if="!data.isLeaf" />
                  <Folder v-else />
                </el-icon>
                <span class="tree-label">{{ data.label }}</span>
                <el-tag v-if="data.count" size="small" type="info" class="tree-count">
                  {{ data.count }}
                </el-tag>
              </span>
            </template>
          </el-tree>
        </div>
      </el-card>

      <el-card shadow="never" class="pending-table-card">
        <template #header>
          <div class="card-header">
            <span>{{ pendingTitle }}</span>
            <div class="pending-actions">
              <el-button
                v-if="selectedFolder"
                size="small"
                @click="loadPending()"
              >
                显示全部
              </el-button>
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
          </div>
        </template>
        <AppLoading v-if="loading.pending" type="table" :rows="6" />
        <AppError v-else-if="error" :message="error" @retry="loadPending" />
        <AppEmpty v-else-if="!pendingList.length" description="暂无待迁移记录" />
        <el-table v-else
          :data="pendingList"
          stripe
          size="small"
          max-height="450"
          @selection-change="handlePendingSelection"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="bah" label="病案号" width="110" />
          <el-table-column prop="brxh" label="病人序号" width="90" />
          <el-table-column prop="filename" label="文件名" min-width="180" show-overflow-tooltip />
          <el-table-column prop="folder" label="目录" min-width="110" show-overflow-tooltip />
          <el-table-column prop="migrationStatus" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.migrationStatus).type as any" size="small">
                {{ statusTag(row.migrationStatus).label }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- Upload by Folder -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>按文件夹上传</span>
        </div>
      </template>
      <el-form inline @submit.prevent="handleFolderUpload">
        <el-form-item label="文件夹">
          <el-input
            v-model="folderInput"
            placeholder="输入文件夹路径"
            clearable
            style="width: 260px;"
            @keyup.enter="handleFolderUpload"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading.folderUpload"
            :icon="UploadFilled"
            @click="handleFolderUpload"
          >
            上传到 OSS
          </el-button>
        </el-form-item>
      </el-form>
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
            style="width: 200px;"
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
              style="width: 130px;"
              @change="handleLogFilterChange"
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
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  border-top: 3px solid transparent;
}

.stat-card.total-count { border-top-color: #409eff; }
.stat-card.migrated-count { border-top-color: #67c23a; }
.stat-card.pending-count { border-top-color: #e6a23c; }
.stat-card.failed-count { border-top-color: #f56c6c; }

.stat-card :deep(.el-card__body) {
  display: flex;
  gap: 18px;
  align-items: center;
  padding: 20px 22px;
}

.stat-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  font-size: 26px;
  border-radius: 14px;
}

.stat-card.total-count .stat-icon { color: #409eff; background: rgb(64 158 255 / 9%); }
.stat-card.migrated-count .stat-icon { color: #67c23a; background: rgb(103 194 58 / 9%); }
.stat-card.pending-count .stat-icon { color: #e6a23c; background: rgb(230 162 60 / 9%); }
.stat-card.failed-count .stat-icon { color: #f56c6c; background: rgb(245 108 108 / 9%); }

.stat-body { flex: 1; min-width: 0; }

.stat-label {
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text-primary);
  word-break: break-all;
}

.progress-section {
  padding: 4px 0;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
  align-items: center;
  justify-content: space-between;
}

/* ===== Folder Tree + Pending Table Row ===== */
.folder-pending-row {
  display: flex;
  gap: 20px;
}

.folder-tree-card {
  flex-shrink: 0;
  width: 300px;
}

.pending-table-card {
  flex: 1;
  min-width: 0;
}

.tree-wrapper {
  max-height: 480px;
  overflow-y: auto;
}

.tree-node {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  padding: 2px 0;
  font-size: 13px;
}

.tree-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-count {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 11px;
}

.pending-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.empty-pending {
  padding: 32px 0;
  font-size: 14px;
  color: #909399;
  text-align: center;
}

.upload-results {
  padding-top: 12px;
  margin-top: 16px;
  border-top: 1px solid #ebeef5;
}

.upload-results h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}

.oss-link {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 4px 8px;
  color: #409eff;
  text-decoration: none;
  border-radius: 4px;
  transition: all 0.2s;
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
  font-size: 13px;
  color: #c0c4cc;
}

.log-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (width <= 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .folder-pending-row {
    flex-direction: column;
  }

  .folder-tree-card {
    width: 100%;
  }
}

@media (width <= 600px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

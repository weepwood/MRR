<script setup lang="ts">
import type {
  ExportCenterJob,
  ImageSourceDiagnosis,
  IntegritySummary,
  PermissionMatrix,
  ReadinessSnapshot,
} from '@/api/modules/operations'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  diagnoseImageSource,
  getDeploymentReadiness,
  getExportCenter,
  getIntegritySummary,
  getPermissionMatrix,
  savePermissionMatrixSnapshot,
} from '@/api/modules/operations'

const activeTab = ref('image')
const loading = reactive({
  image: false,
  integrity: false,
  exports: false,
  permissions: false,
  readiness: false,
})

const imageForm = reactive<{ bah: string, sjh: string, imageId?: number }>({
  bah: '',
  sjh: '',
  imageId: undefined,
})
const imageDiagnosis = ref<ImageSourceDiagnosis>()
const integrity = ref<IntegritySummary>()
const exportJobs = ref<ExportCenterJob[]>([])
const permissionMatrix = ref<PermissionMatrix>()
const readiness = ref<ReadinessSnapshot>()
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

const criticalFailures = computed(() => readiness.value?.checks.filter(
  item => !item.passed && item.severity === 'CRITICAL',
) || [])
const warningFailures = computed(() => readiness.value?.checks.filter(
  item => !item.passed && item.severity === 'WARNING',
) || [])

function formatPercent(value?: number) {
  return `${((value || 0) * 100).toFixed(2)}%`
}

function formatBytes(value?: number) {
  const bytes = Number(value || 0)
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 ** 2) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  if (bytes < 1024 ** 3) {
    return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  }
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`
}

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function statusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED' || status === 'EXPIRED') {
    return 'danger'
  }
  if (status === 'PROCESSING') {
    return 'warning'
  }
  return 'info'
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

async function loadExports() {
  loading.exports = true
  try {
    exportJobs.value = await getExportCenter(200)
  }
  finally {
    loading.exports = false
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

async function loadReadiness(refresh = false) {
  loading.readiness = true
  try {
    readiness.value = await getDeploymentReadiness(refresh)
  }
  finally {
    loading.readiness = false
  }
}

async function handleTabChange(name: string | number) {
  const tab = String(name)
  if (tab === 'integrity' && !integrity.value) {
    await loadIntegrity()
  }
  if (tab === 'exports' && exportJobs.value.length === 0) {
    await loadExports()
  }
  if (tab === 'permissions' && !permissionMatrix.value) {
    await loadPermissions()
  }
  if ((tab === 'readiness' || tab === 'readonly') && !readiness.value) {
    await loadReadiness()
  }
}

onMounted(() => {
  void loadReadiness()
})
</script>

<template>
  <div class="operations-center">
    <div class="page-header">
      <div>
        <h1>运维诊断中心</h1>
        <p>集中检查图片来源、病案完整性、导出文件、权限变化和部署状态。</p>
      </div>
      <el-tag
        v-if="readiness"
        :type="readiness.readOnly ? 'danger' : 'success'"
        size="large"
        effect="dark"
      >
        {{ readiness.readOnly ? '只读降级' : '正常读写' }}
      </el-tag>
    </div>

    <el-tabs v-model="activeTab" class="diagnostics-tabs" @tab-change="handleTabChange">
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
          <div class="summary-grid compact-grid">
            <el-card shadow="never">
              <div class="metric-label">
                诊断结果
              </div>
              <div class="metric-value" :class="imageDiagnosis.found ? 'success-text' : 'danger-text'">
                {{ imageDiagnosis.found ? '找到图片' : '未找到图片' }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                最终来源
              </div>
              <div class="metric-value">
                {{ imageDiagnosis.selectedSource || '-' }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                回退原因
              </div>
              <div class="metric-value small-value">
                {{ imageDiagnosis.fallbackReason || '未发生回退' }}
              </div>
            </el-card>
          </div>

          <el-card shadow="never" class="section-card">
            <template #header>
              解析与回退过程
            </template>
            <el-timeline>
              <el-timeline-item
                v-for="step in imageDiagnosis.steps"
                :key="step.code"
                :type="step.success ? 'success' : 'danger'"
                :hollow="!step.success"
              >
                <div class="timeline-title">
                  <strong>{{ step.code }}</strong>
                  <span>{{ step.message }}</span>
                </div>
                <pre v-if="Object.keys(step.details || {}).length" class="detail-json">{{ JSON.stringify(step.details, null, 2) }}</pre>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </template>
      </el-tab-pane>

      <el-tab-pane label="病案数据完整性" name="integrity">
        <div class="toolbar-row">
          <span>指标基于当前数据库实时汇总。</span>
          <el-button :loading="loading.integrity" @click="loadIntegrity">
            刷新
          </el-button>
        </div>
        <template v-if="integrity">
          <div class="summary-grid">
            <el-card shadow="never">
              <div class="metric-label">
                archive_id 覆盖率
              </div>
              <div class="metric-value">
                {{ formatPercent(integrity.archiveCoverage) }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                OSS 覆盖率
              </div>
              <div class="metric-value">
                {{ formatPercent(integrity.ossCoverage) }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                缺失上架号
              </div>
              <div class="metric-value">
                {{ integrity.missingSjh.toLocaleString() }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                断链记录
              </div>
              <div class="metric-value danger-text">
                {{ integrity.brokenLinks.toLocaleString() }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                重复病案组
              </div>
              <div class="metric-value danger-text">
                {{ integrity.duplicateArchiveGroups.toLocaleString() }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                有效扫描图片
              </div>
              <div class="metric-value">
                {{ integrity.totalActiveScans.toLocaleString() }}
              </div>
            </el-card>
          </div>
          <el-card shadow="never" class="section-card">
            <template #header>
              各业务表关联情况
            </template>
            <el-table :data="integrity.tables" border>
              <el-table-column prop="table" label="数据表" min-width="180" />
              <el-table-column prop="total" label="总数" min-width="130" />
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

      <el-tab-pane label="导出文件中心" name="exports">
        <div class="toolbar-row">
          <el-select v-model="exportStatus" clearable placeholder="全部状态" style="width: 160px;">
            <el-option
              v-for="status in ['PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED']"
              :key="status"
              :label="status"
              :value="status"
            />
          </el-select>
          <el-button :loading="loading.exports" @click="loadExports">
            刷新
          </el-button>
        </div>
        <el-table :data="filteredExportJobs" border height="620">
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
          <el-table-column label="最近下载" width="180">
            <template #default="scope">
              {{ formatTime(scope.row.last_downloaded_at) }}
            </template>
          </el-table-column>
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
          <el-input v-model="endpointKeyword" clearable placeholder="搜索接口、操作或权限" style="max-width: 360px;" />
          <div class="toolbar-actions">
            <el-tag v-if="permissionMatrix?.previousVersion" type="info">
              上一版本：{{ permissionMatrix.previousVersion }}
            </el-tag>
            <el-tag
              v-if="permissionMatrix?.diff.available"
              :type="permissionMatrix.diff.changes.length ? 'warning' : 'success'"
            >
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
        <el-table :data="filteredEndpoints" border height="620">
          <el-table-column prop="method" label="方法" width="90" fixed />
          <el-table-column prop="path" label="接口" min-width="260" fixed show-overflow-tooltip />
          <el-table-column prop="operation" label="操作" min-width="230" show-overflow-tooltip />
          <el-table-column prop="policy" label="策略" width="150" />
          <el-table-column label="所需权限" min-width="200">
            <template #default="scope">
              {{ scope.row.requiredPermissions.join(', ') || '-' }}
            </template>
          </el-table-column>
          <el-table-column
            v-for="role in permissionMatrix?.roles || []"
            :key="role.code"
            :label="role.name || role.code"
            width="110"
            align="center"
          >
            <template #default="scope">
              <el-tag :type="scope.row.roleAccess[role.code] ? 'success' : 'info'" effect="plain">
                {{ scope.row.roleAccess[role.code] ? '允许' : '拒绝' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-card
          v-if="permissionMatrix?.diff.available && permissionMatrix.diff.changes.length"
          shadow="never"
          class="section-card"
        >
          <template #header>
            与上一版本的差异
          </template>
          <el-table :data="permissionMatrix.diff.changes" border>
            <el-table-column prop="type" label="变化" width="110" />
            <el-table-column prop="key" label="接口" min-width="300" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="部署就绪检查" name="readiness">
        <div class="toolbar-row">
          <span>检查数据库迁移、Nginx、图片源、临时目录、OSS 和备份时间。</span>
          <el-button type="primary" :loading="loading.readiness" @click="loadReadiness(true)">
            重新检查
          </el-button>
        </div>
        <div v-if="readiness" class="readiness-list">
          <el-alert
            :title="readiness.ready ? '部署条件满足' : '存在未通过的部署检查'"
            :type="readiness.ready ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
          <el-card v-for="check in readiness.checks" :key="check.code" shadow="never" class="check-card">
            <div class="check-row">
              <div>
                <div class="check-title">
                  {{ check.name }}
                </div>
                <div class="check-message">
                  {{ check.message }}
                </div>
              </div>
              <el-tag :type="check.passed ? 'success' : check.severity === 'CRITICAL' ? 'danger' : 'warning'">
                {{ check.passed ? '通过' : check.severity }}
              </el-tag>
            </div>
            <pre v-if="Object.keys(check.details || {}).length" class="detail-json">{{ JSON.stringify(check.details, null, 2) }}</pre>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="只读降级模式" name="readonly">
        <template v-if="readiness">
          <el-alert
            :title="readiness.readOnly ? '系统当前处于只读降级模式' : '系统当前允许正常写入'"
            :type="readiness.readOnly ? 'error' : 'success'"
            :closable="false"
            show-icon
          >
            <template #default>
              <p v-if="readiness.readOnly">
                写入、导入、导出任务创建和 OSS 迁移会返回 503；登录、搜索、查看病案和诊断接口继续可用。
              </p>
              <p v-else>
                部署关键检查均未触发自动降级。
              </p>
            </template>
          </el-alert>
          <div class="summary-grid compact-grid section-card">
            <el-card shadow="never">
              <div class="metric-label">
                运行模式
              </div>
              <div class="metric-value small-value">
                {{ readiness.mode }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                关键故障
              </div>
              <div class="metric-value danger-text">
                {{ criticalFailures.length }}
              </div>
            </el-card>
            <el-card shadow="never">
              <div class="metric-label">
                警告项
              </div>
              <div class="metric-value">
                {{ warningFailures.length }}
              </div>
            </el-card>
          </div>
          <el-card v-if="criticalFailures.length" shadow="never" class="section-card">
            <template #header>
              触发只读模式的原因
            </template>
            <el-table :data="criticalFailures" border>
              <el-table-column prop="name" label="检查项" width="200" />
              <el-table-column prop="message" label="原因" min-width="360" />
              <el-table-column prop="code" label="代码" width="200" />
            </el-table>
          </el-card>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
/* stylelint-disable order/properties-order, media-feature-range-notation */
.operations-center {
  min-height: 100%;
  padding: 20px;
}

.page-header,
.toolbar-row,
.check-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
}

.page-header p,
.toolbar-row,
.check-message {
  color: var(--el-text-color-secondary);
}

.diagnostics-tabs {
  padding: 0 16px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
}

.diagnosis-form {
  display: flex;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.compact-grid {
  grid-template-columns: repeat(3, minmax(180px, 1fr));
}

.metric-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 700;
}

.small-value {
  font-size: 16px;
  word-break: break-all;
}

.success-text {
  color: var(--el-color-success);
}

.danger-text {
  color: var(--el-color-danger);
}

.section-card {
  margin-top: 16px;
}

.toolbar-row {
  margin-bottom: 14px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.timeline-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-json {
  max-height: 220px;
  overflow: auto;
  margin: 10px 0 0;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.readiness-list {
  display: grid;
  gap: 12px;
}

.check-title {
  font-weight: 600;
}

.check-message {
  margin-top: 4px;
  font-size: 13px;
}

@media (max-width: 900px) {
  .compact-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .toolbar-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

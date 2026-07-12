<script setup lang="ts">
import type { ImageAuditFilterParams } from '@/api/modules/logs'
import type { ImageAuditAnalytics, LogRecord } from '@/api/types'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { getImageAuditAnalytics, getLogById, searchImageAuditLogs } from '@/api/modules/logs'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import { useCrudList } from '@/composables/useCrudList'
import AuditAnalytics from './components/AuditAnalytics.vue'

defineOptions({ name: 'ImageAuditPage' })

// 业务状态（非 CRUD 通用部分）
const detailVisible = ref(false)
const currentLog = ref<LogRecord | null>(null)
const error = ref('')
const analyticsError = ref('')
const analyticsLoading = ref(true)

function createEmptyAnalytics(): ImageAuditAnalytics {
  return {
    totalAccesses: 0,
    uniqueUsers: 0,
    uniqueTargets: 0,
    abnormalAccesses: 0,
    averageDurationMs: 0,
    trend: [],
    actionDistribution: [],
    topUsers: [],
  }
}

const analytics = ref<ImageAuditAnalytics>(createEmptyAnalytics())

const actionOptions = [
  { label: '查询病案图片列表', value: 'LIST' },
  { label: '查看本地病案图片', value: 'VIEW_IMAGE' },
  { label: '查看 OSS 病案图片', value: 'VIEW_OSS_IMAGE' },
  { label: '下载病案压缩包', value: 'DOWNLOAD' },
]
const statusOptions = [{ label: '全部', value: '' }, { label: '2xx', value: '2' }, { label: '4xx', value: '4' }, { label: '5xx', value: '5' }, { label: '200', value: '200' }, { label: '302', value: '302' }, { label: '404', value: '404' }]

// CRUD 列表逻辑：复用 useCrudList composable
interface AuditQuery {
  keyword: string
  username: string
  clientIp: string
  auditAction: string
  responseStatus: string
  timeRange: string[]
}

function buildAuditParams(source: AuditQuery): ImageAuditFilterParams {
  const params: ImageAuditFilterParams = {}
  for (const [key, value] of Object.entries({
    keyword: source.keyword,
    username: source.username,
    clientIp: source.clientIp,
    auditAction: source.auditAction,
    responseStatus: source.responseStatus,
  })) {
    const trimmed = String(value || '').trim()
    if (trimmed) {
      params[key as keyof ImageAuditFilterParams] = trimmed
    }
  }
  if (source.timeRange?.length === 2) {
    params.startTime = source.timeRange[0]
    params.endTime = source.timeRange[1]
  }
  return params
}

const {
  list,
  total,
  loading,
  pageNum,
  pageSize,
  query,
  loadData: loadListData,
  resetFilters: resetListFilters,
  handleSizeChange,
} = useCrudList<
  LogRecord,
  AuditQuery
>({
  fetchApi: async (params) => {
    error.value = ''
    try {
      const { page, size, ...filters } = params
      return searchImageAuditLogs({ ...buildAuditParams(filters), page, size })
    }
    catch (err: any) {
      error.value = err?.message || '加载失败'
      return { list: [], total: 0, page: 1, size: 20 }
    }
  },
  defaultQuery: { keyword: '', username: '', clientIp: '', auditAction: '', responseStatus: '', timeRange: [] },
  immediate: false,
})

async function loadAnalytics() {
  analyticsLoading.value = true
  analyticsError.value = ''
  try {
    const res = await getImageAuditAnalytics(buildAuditParams(query))
    analytics.value = {
      totalAccesses: res.data?.totalAccesses ?? 0,
      uniqueUsers: res.data?.uniqueUsers ?? 0,
      uniqueTargets: res.data?.uniqueTargets ?? 0,
      abnormalAccesses: res.data?.abnormalAccesses ?? 0,
      averageDurationMs: res.data?.averageDurationMs ?? 0,
      trend: res.data?.trend ?? [],
      actionDistribution: res.data?.actionDistribution ?? [],
      topUsers: res.data?.topUsers ?? [],
    }
  }
  catch (err: unknown) {
    analytics.value = createEmptyAnalytics()
    analyticsError.value = err instanceof Error ? err.message : '分析数据加载失败'
  }
  finally {
    analyticsLoading.value = false
  }
}

async function loadData() {
  await Promise.all([loadListData(), loadAnalytics()])
}

async function handleSearch() {
  pageNum.value = 1
  await loadData()
}

async function resetFilters() {
  await Promise.all([resetListFilters(), loadAnalytics()])
}

async function openDetail(row: LogRecord) {
  try {
    const res = await getLogById(row.id!)
    currentLog.value = { ...(res.data || {}), ...row }
  }
  catch {
    currentLog.value = row
  }
  finally {
    detailVisible.value = true
  }
}
function formatDateTime(value: unknown) { if (!value) { return '-' }; const date = new Date(String(value)); return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false }) }
function statusTagType(status: string | number) { const code = Number(status); if (Number.isNaN(code)) { return 'info' }; if (code >= 500) { return 'danger' }; if (code >= 400) { return 'warning' }; if (code >= 200 && code < 400) { return 'success' }; return 'info' }
function actionLabel(value?: string) { return actionOptions.find(item => item.value === value)?.label || value || '-' }

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Sensitive Audit
        </p><h2>病案图片访问审计</h2><p class="subtitle">
          专门展示用户查询、查看、下载病案图片等敏感操作记录。
        </p>
      </div>
      <el-button :loading="loading || analyticsLoading" @click="loadData">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>
    <el-alert type="warning" show-icon :closable="false" title="审计范围：/api/v1/img/{病案号}、/api/v1/img/image/*、/api/v1/img/oss-image/*、/api/v1/img/download/{病案号}" />
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="用户 / IP / URI / 病案号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="用户">
          <el-input v-model="query.username" clearable placeholder="用户名" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户端 IP">
          <el-input v-model="query.clientIp" clearable placeholder="127.0.0.1" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="敏感操作">
          <el-select v-model="query.auditAction" clearable placeholder="全部" style="width: 200px;">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态码">
          <el-select v-model="query.responseStatus" clearable placeholder="全部" style="width: 140px;">
            <el-option v-for="item in statusOptions" :key="item.label" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问时间">
          <el-date-picker v-model="query.timeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button><el-button @click="resetFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="analyticsError" type="error" show-icon :closable="false">
      <template #title>
        分析数据加载失败：{{ analyticsError }}
        <el-button text type="primary" @click="loadAnalytics">
          重试
        </el-button>
      </template>
    </el-alert>
    <AuditAnalytics :analytics="analytics" :loading="analyticsLoading" />

    <el-card shadow="never" class="detail-card">
      <template #header>
        <div class="section-header">
          <div>
            <strong>访问审计明细</strong>
            <span>完整记录每次病案图片查询、查看与下载行为</span>
          </div>
          <el-tag type="info" effect="plain">
            共 {{ total.toLocaleString('zh-CN') }} 条
          </el-tag>
        </div>
      </template>
      <AppLoading v-if="loading" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadListData" />
      <AppEmpty v-else-if="!list.length" description="暂无审计记录" />
      <el-table v-else :data="list" stripe style="margin-top: 12px;">
        <el-table-column prop="accessTime" label="访问时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.accessTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="120">
          <template #default="{ row }">
            {{ row.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="auditDescription" label="敏感操作" min-width="170">
          <template #default="{ row }">
            <el-tag type="danger">
              {{ row.auditDescription || actionLabel(row.auditAction) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditTarget" label="审计对象" min-width="140">
          <template #default="{ row }">
            {{ row.auditTarget || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端 IP" min-width="140" />
        <el-table-column prop="requestUri" label="请求 URI" min-width="300" show-overflow-tooltip />
        <el-table-column prop="responseStatus" label="状态码" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.responseStatus)">
              {{ row.responseStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executeTime" label="耗时(ms)" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">
              <el-icon><View /></el-icon>详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="loadListData" />
      </div>
    </el-card>
    <el-dialog v-model="detailVisible" title="审计详情" width="min(760px, 92vw)">
      <el-descriptions v-if="currentLog" :column="2" border>
        <el-descriptions-item label="ID">
          {{ currentLog.id || '-' }}
        </el-descriptions-item><el-descriptions-item label="用户">
          {{ currentLog.username || '-' }}
        </el-descriptions-item><el-descriptions-item label="敏感操作">
          {{ currentLog.auditDescription || actionLabel(currentLog.auditAction) }}
        </el-descriptions-item><el-descriptions-item label="审计对象">
          {{ currentLog.auditTarget || '-' }}
        </el-descriptions-item><el-descriptions-item label="客户端 IP">
          {{ currentLog.clientIp || '-' }}
        </el-descriptions-item><el-descriptions-item label="状态码">
          {{ currentLog.responseStatus || '-' }}
        </el-descriptions-item><el-descriptions-item label="访问时间" :span="2">
          {{ formatDateTime(currentLog.accessTime) }}
        </el-descriptions-item><el-descriptions-item label="请求 URI" :span="2">
          {{ currentLog.requestUri || '-' }}
        </el-descriptions-item><el-descriptions-item label="Referer" :span="2">
          {{ currentLog.referer || '-' }}
        </el-descriptions-item><el-descriptions-item label="User-Agent" :span="2">
          {{ currentLog.userAgent || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell { display: grid; gap: 20px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; color: var(--text-secondary); }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.section-header { display: flex; gap: 16px; align-items: center; justify-content: space-between; }
.section-header > div { display: grid; gap: 4px; }
.section-header strong { font-size: 15px; color: var(--text-primary); }
.section-header span { font-size: 12px; font-weight: 400; color: var(--text-secondary); }
.pager { display: flex; justify-content: center; margin-top: 16px; }

@media (width <= 700px) {
  .page-header { align-items: stretch; }
  .section-header { align-items: flex-start; }
  :deep(.el-form--inline .el-form-item) { display: flex; width: 100%; margin-right: 0; }
  :deep(.el-form--inline .el-form-item__content) { flex: 1; min-width: 0; }

  :deep(.el-form--inline .el-input),
  :deep(.el-form--inline .el-select),
  :deep(.el-form--inline .el-date-editor) { width: 100% !important; }
}
</style>

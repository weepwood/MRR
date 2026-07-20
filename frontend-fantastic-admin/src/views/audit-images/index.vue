<script setup lang="ts">
import type { ImageAuditFilterParams } from '@/api/modules/logs'
import type { ImageAuditAnalytics, LogRecord } from '@/api/types'
import { Download, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { exportImageAuditLogs, getImageAuditAnalytics, getLogById, searchImageAuditLogs } from '@/api/modules/logs'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import { useCrudList } from '@/composables/useCrudList'
import AuditAnalytics from './components/AuditAnalytics.vue'

defineOptions({ name: 'ImageAuditPage' })

const detailVisible = ref(false)
const currentLog = ref<LogRecord | null>(null)
const error = ref('')
const analyticsError = ref('')
const analyticsLoading = ref(true)
const exportLoading = ref(false)

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
    topTargets: [],
  }
}

const analytics = ref<ImageAuditAnalytics>(createEmptyAnalytics())
const actionOptions = [
  { label: '查询病案图片列表', value: 'LIST' },
  { label: '查看本地病案图片', value: 'VIEW_IMAGE' },
  { label: '查看 OSS 病案图片', value: 'VIEW_OSS_IMAGE' },
  { label: '下载病案压缩包', value: 'DOWNLOAD' },
]
const statusOptions = [
  { label: '全部', value: '' },
  { label: '2xx', value: '2' },
  { label: '4xx', value: '4' },
  { label: '5xx', value: '5' },
  { label: '200', value: '200' },
  { label: '302', value: '302' },
  { label: '404', value: '404' },
]

interface AuditQuery {
  keyword: string
  username: string
  clientIp: string
  auditAction: string
  responseStatus: string
  timeRange: string[]
}

interface AuditIdentifiers {
  bah: string
  sjh: string
  idCard: string
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
    if (trimmed) params[key as keyof ImageAuditFilterParams] = trimmed
  }
  if (source.timeRange?.length === 2) {
    params.startTime = source.timeRange[0]
    params.endTime = source.timeRange[1]
  }
  return params
}

function parseEncodedParams(value?: string) {
  const params = new URLSearchParams()
  if (!value) return params
  const source = value.startsWith('?') ? value.slice(1) : value
  for (const pair of source.split('&')) {
    const separator = pair.indexOf('=')
    if (separator < 0) continue
    const key = pair.slice(0, separator)
    const rawValue = pair.slice(separator + 1)
    try {
      params.set(decodeURIComponent(key.replaceAll('+', ' ')), decodeURIComponent(rawValue.replaceAll('+', ' ')))
    }
    catch {
      params.set(key, rawValue)
    }
  }
  return params
}

function parseJsonIdentifiers(body?: string) {
  if (!body?.trim().startsWith('{')) return {} as Record<string, unknown>
  try {
    return JSON.parse(body) as Record<string, unknown>
  }
  catch {
    return {} as Record<string, unknown>
  }
}

function auditIdentifiers(row?: LogRecord | null): AuditIdentifiers {
  if (!row) return { bah: '-', sjh: '-', idCard: '-' }
  const queryParams = parseEncodedParams(row.queryString)
  const body = parseJsonIdentifiers(row.requestBody)
  const target = String(row.auditTarget || '').trim()
  let bah = String(row.bah || queryParams.get('bah') || body.bah || '').trim()
  let sjh = String(row.sjh || queryParams.get('sjh') || body.sjh || '').trim()
  const idCard = String(
    row.patientId
      || queryParams.get('idCard')
      || queryParams.get('idcard')
      || queryParams.get('patientId')
      || queryParams.get('patientid')
      || body.idCard
      || body.idcard
      || body.patientId
      || body.patientid
      || '',
  ).trim()

  if (target && target !== 'search') {
    if (target.startsWith('sjh:')) {
      sjh ||= target.slice(4)
    }
    else if (target.includes(':')) {
      const [targetBah, targetSjh] = target.split(':', 2)
      bah ||= targetBah
      sjh ||= targetSjh
    }
    else {
      bah ||= target
    }
  }

  return { bah: bah || '-', sjh: sjh || '-', idCard: idCard || '-' }
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
} = useCrudList<LogRecord, AuditQuery>({
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
      topTargets: res.data?.topTargets ?? [],
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

async function exportAudit(scope: 'all' | 'user' | 'target', value?: string) {
  exportLoading.value = true
  try {
    const response = await exportImageAuditLogs({ ...buildAuditParams(query), scope, value })
    const blob = response.data
    if (!(blob instanceof Blob)) throw new TypeError('导出接口未返回有效的文件数据')
    const disposition = String(response.headers?.['content-disposition'] || '')
    const matchedName = disposition.match(/filename=([^;]+)/i)?.[1]?.replaceAll('"', '')
    const filename = matchedName || `image-audit-${scope}-${Date.now()}.csv`
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('审计数据已导出')
  }
  catch (err: any) {
    ElMessage.error(err?.message || '导出失败')
  }
  finally {
    exportLoading.value = false
  }
}

function formatDateTime(value: unknown) {
  if (!value) return '-'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

function statusTagType(status: string | number) {
  const code = Number(status)
  if (Number.isNaN(code)) return 'info'
  if (code >= 500) return 'danger'
  if (code >= 400) return 'warning'
  if (code >= 200 && code < 400) return 'success'
  return 'info'
}

function actionLabel(value?: string) {
  return actionOptions.find(item => item.value === value)?.label || value || '-'
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">Medical Record Audit</p>
        <h2>病历访问审计可视化</h2>
        <p class="subtitle">按用户和病历双向追溯访问关系，并导出完整审计证据。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="exportLoading" @click="exportAudit('all')"><el-icon><Download /></el-icon>导出当前明细</el-button>
        <el-button :loading="loading || analyticsLoading" @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字"><el-input v-model="query.keyword" clearable placeholder="病案号 / 上架号 / 身份证号 / URI" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="用户"><el-input v-model="query.username" clearable placeholder="用户名" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="客户端 IP"><el-input v-model="query.clientIp" clearable placeholder="127.0.0.1" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="访问类型"><el-select v-model="query.auditAction" clearable placeholder="全部" style="width: 190px;"><el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="状态码"><el-select v-model="query.responseStatus" clearable placeholder="全部" style="width: 120px;"><el-option v-for="item in statusOptions" :key="item.label" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="访问时间"><el-date-picker v-model="query.timeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm:ss" /></el-form-item>
        <el-form-item><el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="analyticsError" type="error" show-icon :closable="false" :title="`分析数据加载失败：${analyticsError}`" />
    <AuditAnalytics :analytics="analytics" :loading="analyticsLoading" @export-user="value => exportAudit('user', value)" @export-target="value => exportAudit('target', value)" />

    <el-card shadow="never" class="detail-card">
      <template #header>
        <div class="section-header">
          <div><strong>最近访问明细</strong><span>病案号、上架号和身份证号独立展示</span></div>
          <div class="section-actions"><el-tag type="info" effect="plain">共 {{ total.toLocaleString('zh-CN') }} 条</el-tag><el-button size="small" :loading="exportLoading" @click="exportAudit('all')"><el-icon><Download /></el-icon>导出明细</el-button></div>
        </div>
      </template>
      <AppLoading v-if="loading" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadListData" />
      <AppEmpty v-else-if="!list.length" description="暂无审计记录" />
      <el-table v-else :data="list" stripe>
        <el-table-column prop="accessTime" label="访问时间" min-width="180"><template #default="{ row }">{{ formatDateTime(row.accessTime) }}</template></el-table-column>
        <el-table-column prop="username" label="用户" min-width="120"><template #default="{ row }">{{ row.username || '-' }}</template></el-table-column>
        <el-table-column label="病案号" min-width="130"><template #default="{ row }">{{ auditIdentifiers(row).bah }}</template></el-table-column>
        <el-table-column label="上架号" min-width="130"><template #default="{ row }">{{ auditIdentifiers(row).sjh }}</template></el-table-column>
        <el-table-column label="身份证号" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ auditIdentifiers(row).idCard }}</template></el-table-column>
        <el-table-column prop="auditDescription" label="访问类型" min-width="170"><template #default="{ row }"><el-tag effect="plain">{{ row.auditDescription || actionLabel(row.auditAction) }}</el-tag></template></el-table-column>
        <el-table-column prop="clientIp" label="客户端 IP" min-width="140" />
        <el-table-column prop="responseStatus" label="状态码" width="100"><template #default="{ row }"><el-tag :type="statusTagType(row.responseStatus)">{{ row.responseStatus || '-' }}</el-tag></template></el-table-column>
        <el-table-column prop="executeTime" label="耗时(ms)" width="105" />
        <el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button size="small" type="primary" text @click="openDetail(row)"><el-icon><View /></el-icon>详情</el-button></template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="loadListData" /></div>
    </el-card>

    <el-dialog v-model="detailVisible" title="审计详情" width="min(820px, 92vw)">
      <el-descriptions v-if="currentLog" :column="2" border>
        <el-descriptions-item label="Request ID">{{ currentLog.requestId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="访问类型">{{ currentLog.auditDescription || actionLabel(currentLog.auditAction) }}</el-descriptions-item>
        <el-descriptions-item label="病案号">{{ auditIdentifiers(currentLog).bah }}</el-descriptions-item>
        <el-descriptions-item label="上架号">{{ auditIdentifiers(currentLog).sjh }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ auditIdentifiers(currentLog).idCard }}</el-descriptions-item>
        <el-descriptions-item label="客户端 IP">{{ currentLog.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态码">{{ currentLog.responseStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="访问时间" :span="2">{{ formatDateTime(currentLog.accessTime) }}</el-descriptions-item>
        <el-descriptions-item label="请求 URI" :span="2">{{ currentLog.requestUri || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接口模板" :span="2">{{ currentLog.endpointTemplate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Referer" :span="2">{{ currentLog.referer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="查询参数" :span="2">{{ currentLog.queryString || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell { display: grid; gap: 18px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.header-actions, .section-actions { display: flex; gap: 10px; align-items: center; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: .12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; color: var(--text-secondary); }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.section-header { display: flex; gap: 16px; align-items: center; justify-content: space-between; }
.section-header > div:first-child { display: grid; gap: 4px; }
.section-header strong { font-size: 16px; }
.section-header span { font-size: 12px; color: var(--text-secondary); }
.pager { display: flex; justify-content: center; margin-top: 16px; }
@media (max-width: 760px) {
  .page-header, .section-header { flex-direction: column; align-items: stretch; }
  .header-actions, .section-actions { flex-wrap: wrap; }
  :deep(.el-form--inline .el-form-item) { display: flex; width: 100%; margin-right: 0; }
  :deep(.el-form--inline .el-form-item__content) { flex: 1; min-width: 0; }
  :deep(.el-form--inline .el-input), :deep(.el-form--inline .el-select), :deep(.el-form--inline .el-date-editor) { width: 100% !important; }
}
</style>

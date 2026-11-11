<script setup lang="ts">
/* eslint-disable antfu/if-newline, curly, style/indent-binary-ops, vue/singleline-html-element-content-newline */
import type { ImageAuditFilterParams } from '@/api/modules/logs'
import type { ImageAuditAnalytics, LogRecord } from '@/api/types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { useMediaQuery } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { Download, RefreshCw as Refresh } from 'lucide-vue-next'
import { onMounted, onScopeDispose, ref } from 'vue'
import { exportImageAuditLogs, getImageAuditAnalytics, getLogById, searchImageAuditLogs } from '@/api/modules/logs'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { rangePickerPopperOptions } from '@/ui/provider/picker-options'
import AuditAnalytics from './components/AuditAnalytics.vue'

defineOptions({ name: 'ImageAuditPage' })

const detailVisible = ref(false)
const isNarrowScreen = useMediaQuery('(max-width: 760px)')
const currentLog = ref<LogRecord | null>(null)
const error = ref('')
const analyticsError = ref('')
const analyticsLoading = ref(true)
const exportLoading = ref(false)

const detailActions: MrrTableAction[] = [{
  key: 'detail',
  label: '查看审计详情',
  icon: 'i-lucide:eye',
  tone: 'primary',
  placement: 'inline',
}]
const {
  maxInlineActions: detailMaxInlineActions,
  actionColumnWidth: detailActionColumnWidth,
} = useTableActionLayout(detailActions.length, 1)

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
  fetchApi: async (params, { isCurrent }) => {
    error.value = ''
    try {
      const { page, size, ...filters } = params
      return await searchImageAuditLogs({ ...buildAuditParams(filters), page, size })
    }
    catch (err: any) {
      if (isCurrent()) error.value = err?.message || '加载失败'
      return { list: [], total: 0, page: 1, size: 20 }
    }
  },
  defaultQuery: { keyword: '', username: '', clientIp: '', auditAction: '', responseStatus: '', timeRange: [] },
  immediate: false,
})

let analyticsGeneration = 0
let disposed = false
onScopeDispose(() => {
  disposed = true
  analyticsGeneration++
  analyticsLoading.value = false
})

async function loadAnalytics() {
  if (disposed) return
  const token = ++analyticsGeneration
  const isCurrent = () => !disposed && token === analyticsGeneration
  analyticsLoading.value = true
  analyticsError.value = ''
  try {
    const res = await getImageAuditAnalytics(buildAuditParams(query))
    if (!isCurrent()) return
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
    if (!isCurrent()) return
    analytics.value = createEmptyAnalytics()
    analyticsError.value = err instanceof Error ? err.message : '分析数据加载失败'
  }
  finally {
    if (isCurrent()) analyticsLoading.value = false
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

function handleDetailAction(action: string, row: LogRecord) {
  if (action === 'detail') {
    void openDetail(row)
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
  <MrrPageShell>
    <MrrPageHeader title="病历访问审计可视化" eyebrow="Medical Record Audit" description="按用户和病历双向追溯访问关系，并导出完整审计证据。">
      <template #actions>
        <el-button :loading="exportLoading" @click="exportAudit('all')"><el-icon><Download /></el-icon>导出当前明细</el-button>
        <el-button :loading="loading || analyticsLoading" @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
      </template>
    </MrrPageHeader>

    <el-form label-position="top" @submit.prevent>
      <MrrFilterBar layout="grid">
        <el-form-item label="关键字" for="audit-keyword"><el-input id="audit-keyword" v-model="query.keyword" clearable placeholder="病案号 / 上架号 / 身份证号 / URI" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="用户" for="audit-username"><el-input id="audit-username" v-model="query.username" clearable placeholder="用户名" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="客户端 IP" for="audit-client-ip"><el-input id="audit-client-ip" v-model="query.clientIp" clearable placeholder="127.0.0.1" @keyup.enter="handleSearch" /></el-form-item>
        <el-form-item label="访问类型" for="audit-action"><el-select id="audit-action" v-model="query.auditAction" clearable placeholder="全部"><el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="状态码" for="audit-status"><el-select id="audit-status" v-model="query.responseStatus" clearable placeholder="全部"><el-option v-for="item in statusOptions" :key="item.label" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="访问时间" class="mrr-filter-bar__range"><el-date-picker v-model="query.timeRange" type="datetimerange" :popper-options="rangePickerPopperOptions" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm:ss" /></el-form-item>
        <template #actions><MrrFilterActionButton action="search" :pending="loading || analyticsLoading" :busy="loading" type="primary" @click="handleSearch">查询</MrrFilterActionButton><MrrFilterActionButton action="reset" :pending="loading || analyticsLoading" :busy="loading" @click="resetFilters">重置</MrrFilterActionButton></template>
      </MrrFilterBar>
    </el-form>

    <el-alert v-if="analyticsError" type="error" show-icon :closable="false" :title="`分析数据加载失败：${analyticsError}`" />
    <AuditAnalytics :analytics="analytics" :loading="analyticsLoading" @export-user="value => exportAudit('user', value)" @export-target="value => exportAudit('target', value)" />

    <MrrDataTablePanel title="最近访问明细" description="病案号、上架号和身份证号独立展示" :count="total">
      <template #actions>
        <el-button size="small" :loading="exportLoading" @click="exportAudit('all')"><el-icon><Download /></el-icon>导出明细</el-button>
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
        <el-table-column label="操作" :width="detailActionColumnWidth" fixed="right" align="center">
          <template #default="{ row }">
            <MrrTableActions
              :actions="detailActions"
              :max-inline="detailMaxInlineActions"
              @select="handleDetailAction($event, row)"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #pagination><el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="loadListData" /></template>
    </MrrDataTablePanel>

    <el-dialog v-model="detailVisible" title="审计详情" width="min(820px, 92vw)" class="audit-detail">
      <el-descriptions v-if="currentLog" :column="isNarrowScreen ? 1 : 2" border>
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
  </MrrPageShell>
</template>

<style scoped>
:global(.audit-detail .el-descriptions__table) {
  table-layout: fixed;
}

:global(.audit-detail .el-descriptions__cell) {
  overflow-wrap: anywhere;
}
</style>

<script setup lang="ts">
import type { RuntimeLogInsight, RuntimeLogRow } from '@/api/modules/runtime-logs'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRuntimeLogFiles, getRuntimeLogHealth, getRuntimeLogInsights, getRuntimeLogs, importRuntimeLog } from '@/api/modules/runtime-logs'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import { rangePickerPopperOptions } from '@/ui/provider/picker-options'
import useAuth from '@/utils/composables/useAuth'
import { formatDateTime, isRedactedLogText } from '@/utils/display-format'

const route = useRoute()
const router = useRouter()
const { auth } = useAuth()
let queryVersion = 0
let statusVersion = 0
let insightVersion = 0
let disposed = false
const loading = ref(false)
const error = ref('')
const statusLoading = ref(false)
const healthError = ref('')
const filesError = ref('')
const rows = ref<RuntimeLogRow[]>([])
const health = ref<Record<string, unknown>>({})
const files = ref<{ fileName: string, bytes: number }[]>([])
const selectedFiles = ref<string[]>([])
const historyRange = ref<[string, string] | null>(null)
const importing = ref(false)
const importMessage = ref('')
const importError = ref('')
const requestId = ref(String(route.query.requestId || ''))
const errorId = ref(String(route.query.errorId || ''))
const keyword = ref('')
// 级别用「最低级别」表达：排障关心的是 WARN 及以上，而不是精确某一个级别。
const minLevel = ref('')
const logger = ref('')
const range = ref<[string, string] | null>([new Date(Date.now() - 86400000).toISOString(), new Date().toISOString()])
const hasMore = ref(false)
const cursor = ref<RuntimeLogRow | null>(null)
let retryCursor: RuntimeLogRow | null = null
const detail = ref<RuntimeLogRow | null>(null)
const detailVisible = ref(false)
const insight = ref<RuntimeLogInsight | null>(null)
const insightError = ref('')
const insightLoading = ref(false)
const autoRefresh = ref(false)
let autoTimer: ReturnType<typeof setInterval> | null = null
function bytes(value: unknown) { return typeof value === 'number' && Number.isFinite(value) ? `${(value / 1024 / 1024).toFixed(1)} MB` : '未知' }
function levelType(value: string) {
  if (value === 'ERROR') { return 'danger' }
  if (value === 'WARN') { return 'warning' }
  if (value === 'INFO') { return 'success' }
  return 'info'
}
const backlog = computed(() => health.value.backlog as Record<string, unknown> | undefined)
const selectableFiles = computed(() => files.value.filter((file) => {
  if (!historyRange.value) { return true }
  const day = file.fileName.match(/\.(\d{4}-\d{2}-\d{2})\./)?.[1]
  return Boolean(day && day >= historyRange.value[0] && day <= historyRange.value[1])
}))
const metrics = computed(() => [
  { label: '最近成功轮询', value: typeof health.value.pollAgeSeconds === 'number' ? `${health.value.pollAgeSeconds} 秒前` : '未知', note: '轮询成功不代表积压已处理完毕' },
  { label: '已登记待采集文件', value: typeof backlog.value?.pending_files === 'number' ? backlog.value.pending_files : '未知', note: `未登记文件在下一轮发现；原始积压 ${bytes(backlog.value?.pending_raw_bytes)}` },
  { label: '缓冲卷剩余空间', value: bytes(health.value.bufferDiskFreeBytes), note: '数据库卷空间需在服务器核实' },
  { label: '数据库日志容量', value: bytes(health.value.databaseBytes), note: '明细及索引；长期保留，不自动删除' },
])
const appliedFilters = ref<Record<string, unknown>>({})
function currentFilters() {
  return {
    from: range.value?.[0],
    to: range.value?.[1],
    requestId: requestId.value.trim() || undefined,
    minLevel: minLevel.value || undefined,
    keyword: keyword.value.trim() || undefined,
    logger: logger.value.trim() || undefined,
    errorId: errorId.value.trim() || undefined,
  }
}
const filtersChanged = computed(() => JSON.stringify(appliedFilters.value) !== JSON.stringify(currentFilters()))
/** 正文关键词走模糊匹配，服务端只接受24小时窗口；提前提示，别让用户提交后才看到 400。 */
const keywordBlocked = computed(() => {
  const from = range.value?.[0]
  const to = range.value?.[1]
  if (!from || !to) { return false }
  const span = Date.parse(to) - Date.parse(from)
  return Number.isFinite(span) && span > 86400000
})
const gap = computed(() => Boolean(health.value.unknownGap) || Number(health.value.invalidLines || 0) > 0)
const trendMax = computed(() => Math.max(1, ...(insight.value?.trend || []).map(point => Math.max(point.error, point.warn))))
const moduleMax = computed(() => Math.max(1, ...(insight.value?.topModules || []).map(item => item.occurrences)))
const trendStartsAt = computed(() => insight.value?.trend[0]?.bucketStart || '')
const trendEndsAt = computed(() => {
  const points = insight.value?.trend || []
  return points.length ? points[points.length - 1]!.bucketStart : ''
})
const insightWindow = computed(() => (insight.value?.range.bucket === 'day' ? '按天' : '按小时'))
function barHeight(value: number) { return `${value > 0 ? Math.max(4, Math.round(value / trendMax.value * 100)) : 0}%` }
function moduleWidth(occurrences: number) { return `${Math.round(occurrences / moduleMax.value * 100)}%` }
function bucketLabel(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) { return '—' }
  const pad = (input: number) => String(input).padStart(2, '0')
  return insight.value?.range.bucket === 'day'
    ? `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    : `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function load(reset = true, targetCursor: RuntimeLogRow | null = cursor.value) {
  if (disposed) { return }
  const version = ++queryVersion
  loading.value = true
  error.value = ''
  const pageCursor = reset ? null : targetCursor
  retryCursor = pageCursor
  try {
    if (reset) {
      if (!range.value) {
        const now = Date.now()
        range.value = [new Date(now - 86400000).toISOString(), new Date(now).toISOString()]
      }
      appliedFilters.value = currentFilters()
      void loadInsights()
    }
    const result = await getRuntimeLogs({ ...appliedFilters.value, cursorTime: pageCursor?.occurred_at, cursorId: pageCursor?.event_id, size: 50 })
    if (version !== queryVersion) { return }
    if (!Array.isArray(result.data?.list)) { throw new Error('日志响应格式异常') }
    rows.value = result.data.list
    hasMore.value = result.data?.hasMore || false
    cursor.value = pageCursor
  }
  catch (exception) {
    if (version === queryVersion) { error.value = exception instanceof Error ? exception.message : '运行日志读取失败' }
  }
  finally {
    if (version === queryVersion) { loading.value = false }
  }
}
async function loadInsights() {
  if (disposed) { return }
  const version = ++insightVersion
  insightLoading.value = true
  try {
    const result = await getRuntimeLogInsights({ from: appliedFilters.value.from, to: appliedFilters.value.to })
    if (disposed || version !== insightVersion) { return }
    insight.value = result.data || null
    insightError.value = ''
  }
  catch (exception) {
    if (!disposed && version === insightVersion) {
      insightError.value = exception instanceof Error ? exception.message : '问题概览读取失败'
    }
  }
  finally {
    if (!disposed && version === insightVersion) { insightLoading.value = false }
  }
}
async function refreshHealth() {
  if (disposed) { return }
  const version = ++statusVersion
  const isCurrent = () => !disposed && version === statusVersion
  statusLoading.value = true
  await Promise.all([
    (async () => {
      try {
        const status = await getRuntimeLogHealth()
        if (!isCurrent()) { return }
        health.value = status.data || {}
        healthError.value = ''
      }
      catch (exception) {
        if (isCurrent()) { healthError.value = exception instanceof Error ? exception.message : '请求失败' }
      }
    })(),
    (async () => {
      try {
        const inventory = await getRuntimeLogFiles()
        if (!isCurrent()) { return }
        if (!Array.isArray(inventory.data)) { throw new Error('文件清单响应格式异常') }
        files.value = inventory.data
        filesError.value = ''
      }
      catch (exception) {
        if (isCurrent()) { filesError.value = exception instanceof Error ? exception.message : '请求失败' }
      }
    })(),
  ])
  if (isCurrent()) { statusLoading.value = false }
}
async function importFile() {
  if (disposed || importing.value || statusLoading.value || filesError.value || !selectedFiles.value.length) { return }
  const selection = [...selectedFiles.value]
  importing.value = true
  importMessage.value = ''
  importError.value = ''
  let registered = 0
  try {
    for (const name of selection) {
      await importRuntimeLog(name)
      if (disposed) { return }
      registered++
      selectedFiles.value = selectedFiles.value.filter(file => file !== name)
    }
    importMessage.value = `已登记 ${registered} 个文件，后台将按批次处理；登记成功不代表已完成入库`
    ElMessage.success('补录登记成功')
  }
  catch {
    if (!disposed) { importError.value = `已登记 ${registered} 个文件，剩余 ${selection.length - registered} 个未确认登记；请检查文件和导入权限后重试` }
  }
  finally {
    if (!disposed) { importing.value = false; await refreshHealth() }
  }
}
function next() {
  if (loading.value || !hasMore.value || !rows.value.length) { return }
  void load(false, rows.value[rows.value.length - 1]!)
}
function retryQuery() { void load(false, retryCursor) }
function showDetail(row: RuntimeLogRow) { detail.value = row; detailVisible.value = true }
function detailText(row: RuntimeLogRow | null) {
  return [row?.message_summary ?? '', row?.stack_trace ?? ''].filter(Boolean).join('\n')
}
async function copyText(text: string, message: string) {
  if (!text.trim()) { return }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(message)
  }
  catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}
function copyDetail() { void copyText(detailText(detail.value), '日志正文已复制') }
function copyRequestId(value?: string) { void copyText(value || '', '追踪编号已复制') }
/** 从概览或详情跳到原始日志：清掉互斥条件，避免「按错误组筛选」被旧关键词挡住。 */
function filterByError(value?: string) {
  if (!value) { return }
  errorId.value = value
  requestId.value = ''
  keyword.value = ''
  detailVisible.value = false
  void load()
}
function filterByRequest(value?: string) {
  if (!value) { return }
  requestId.value = value
  errorId.value = ''
  keyword.value = ''
  detailVisible.value = false
  void load()
}
function openAccessLog(value?: string) {
  if (!value) { return }
  void router.push({ path: '/logs', query: { requestId: value } })
}
/** 恢复默认排查口径：最近24小时、无附加条件。 */
function resetFilters() {
  requestId.value = ''
  errorId.value = ''
  keyword.value = ''
  logger.value = ''
  minLevel.value = ''
  const now = Date.now()
  range.value = [new Date(now - 86400000).toISOString(), new Date(now).toISOString()]
  void load()
}
function setAutoRefresh(value: boolean) {
  autoRefresh.value = value
  if (autoTimer) { clearInterval(autoTimer); autoTimer = null }
  if (!value) { return }
  autoTimer = setInterval(() => {
    // 只在首批跟看时刷新，避免把正在翻页的人拉回第一页。
    if (disposed || loading.value || cursor.value) { return }
    void load(true)
  }, 10000)
}
onScopeDispose(() => {
  disposed = true
  queryVersion++
  statusVersion++
  insightVersion++
  if (autoTimer) { clearInterval(autoTimer); autoTimer = null }
})
/**
 * 页面被 keep-alive 缓存：从错误中心或访问日志跳转过来时不会重新 mount，
 * 必须跟随路由参数，否则会停留在上一次的筛选条件上。
 */
watch(() => route.query, (query) => {
  // 缓存组件里 route 指向全局当前路由；离开本页后跳到别处的参数不应改动本页筛选。
  if (route.path !== '/runtime-logs') { return }
  const nextRequest = typeof query.requestId === 'string' ? query.requestId : ''
  const nextError = typeof query.errorId === 'string' ? query.errorId : ''
  if (!nextRequest && !nextError) { return }
  requestId.value = nextRequest
  errorId.value = nextError
  keyword.value = ''
  // 从错误中心或旧访问记录跳过来时，事件往往早于默认的24小时窗口；
  // 编号检索走索引，放宽到最近7天可以避免「跳过来却是空列表」的死胡同。
  const now = Date.now()
  range.value = [new Date(now - 7 * 86400000).toISOString(), new Date(now).toISOString()]
  ElMessage.info('已按编号检索最近 7 天，可在时间范围里继续收窄')
  void load()
})
onMounted(() => { void load(); void refreshHealth() })
</script>

<template>
  <div class="page-shell runtime-log-page mrr-content-enter">
    <div class="page-header">
      <div><h2>运行日志</h2><p>按时间范围、追踪编号或错误编号定位问题；正文保留原始模板，只抹除编号、令牌、连接串与路径。正文关键词检索限定24小时内，默认最近24小时，单次范围最多31天。</p></div>
      <el-button :loading="loading" @click="load(false, null)">
        刷新日志
      </el-button>
    </div>
    <el-alert v-if="healthError" :title="`采集状态读取失败：${healthError}；页面中的采集指标与来源保留上次成功结果，请刷新状态重试`" type="error" :closable="false" show-icon />
    <el-alert v-if="gap" title="日志存在缺口或无法确认完整性，请检查采集状态" type="warning" :closable="false" show-icon />
    <el-alert v-if="health.failureCode || health.status === 'UNAVAILABLE'" title="日志采集暂不可用，业务请求仍保持服务；请检查数据库及磁盘" type="error" :closable="false" show-icon />
    <el-alert v-if="health.auditHmacConfigured === false" title="审计检索密钥尚未配置，编号只会隐藏，无法通过摘要检索；请由管理员配置独立密钥" type="warning" :closable="false" show-icon />
    <el-card shadow="never" class="insight-card">
      <template #header>
        <div class="collector-heading">
          <div class="section-heading">
            <strong>问题定位</strong>
            <span>所选时间范围内的 WARN/ERROR 分布；点击错误组可直接筛选原始日志</span>
          </div>
          <el-button link type="primary" :loading="insightLoading" @click="loadInsights">
            刷新概览
          </el-button>
        </div>
      </template>
      <el-alert v-if="insightError" :title="`问题概览读取失败：${insightError}；原始日志仍可正常查询`" type="warning" :closable="false" show-icon />
      <div v-if="insight" class="insight-body">
        <div class="insight-stats">
          <div class="insight-stat">
            <span class="insight-stat__label">ERROR</span>
            <strong class="insight-stat__value insight-stat__value--error">{{ insight.counts.ERROR }}</strong>
            <span class="insight-stat__note">条 · 窗口内</span>
          </div>
          <div class="insight-stat">
            <span class="insight-stat__label">WARN</span>
            <strong class="insight-stat__value insight-stat__value--warn">{{ insight.counts.WARN }}</strong>
            <span class="insight-stat__note">条 · 窗口内</span>
          </div>
          <div class="insight-stat">
            <span class="insight-stat__label">未处理错误组</span>
            <strong class="insight-stat__value">{{ insight.unresolvedGroups }}</strong>
            <span class="insight-stat__note">窗口内仍有发生</span>
          </div>
          <div class="insight-stat">
            <span class="insight-stat__label">活跃错误组</span>
            <strong class="insight-stat__value">{{ insight.topErrors.length }}</strong>
            <span class="insight-stat__note">窗口内有发生</span>
          </div>
        </div>
        <div class="trend-panel">
          <div class="trend-head">
            <strong>错误趋势</strong>
            <span>{{ insightWindow }}分桶 · {{ formatDateTime(trendStartsAt) }} 起</span>
          </div>
          <div v-if="insight.trend.length" class="trend">
            <div
              v-for="point in insight.trend" :key="point.bucketStart" class="trend-col"
              :title="`${formatDateTime(point.bucketStart)} · ERROR ${point.error} · WARN ${point.warn}`"
            >
              <div class="trend-bars">
                <span class="trend-bar trend-bar--error" :style="{ height: barHeight(point.error) }" />
                <span class="trend-bar trend-bar--warn" :style="{ height: barHeight(point.warn) }" />
              </div>
            </div>
          </div>
          <p v-else class="insight-empty">
            所选范围内没有 WARN/ERROR 记录。
          </p>
          <div v-if="insight.trend.length" class="trend-axis">
            <span>{{ bucketLabel(trendStartsAt) }}</span>
            <span class="trend-legend"><i class="trend-dot trend-dot--error" />ERROR<i class="trend-dot trend-dot--warn" />WARN</span>
            <span>{{ bucketLabel(trendEndsAt) }}</span>
          </div>
        </div>
      </div>
      <div v-if="insight" class="insight-lists">
        <div class="insight-list">
          <strong>活跃错误组</strong>
          <p v-if="!insight.topErrors.length" class="insight-empty">
            窗口内没有正在发生的错误组；未投影的新日志会在采集后补上编号。
          </p>
          <button
            v-for="item in insight.topErrors" :key="item.errorId" type="button" class="hot-row"
            @click="filterByError(item.errorId)"
          >
            <el-tag size="small" effect="light" :type="levelType(item.level)">
              {{ item.level }}
            </el-tag>
            <span class="hot-message">{{ item.message || item.errorId }}</span>
            <span class="hot-meta">累计 {{ item.occurrenceCount }} 次 · 最近 {{ formatDateTime(item.lastSeenAt) }}</span>
          </button>
        </div>
        <div class="insight-list">
          <strong>模块分布</strong>
          <p v-if="!insight.topModules.length" class="insight-empty">
            窗口内没有错误，暂无模块分布。
          </p>
          <div v-for="item in insight.topModules" :key="item.module" class="module-row">
            <span class="module-name">{{ item.module }}</span>
            <span class="module-bar"><i :style="{ width: moduleWidth(item.occurrences) }" /></span>
            <span class="hot-meta">{{ item.occurrences }} 次 / {{ item.groups }} 组</span>
          </div>
        </div>
      </div>
    </el-card>
    <section class="mrr-metric-grid mrr-metric-grid--four">
      <MrrMetricCard v-for="metric in metrics" :key="metric.label" v-bind="metric" />
    </section>
    <el-card shadow="never" class="filter-card">
      <template #header>
        <div class="section-heading">
          <strong>筛选日志</strong>
          <span>根据时间范围、追踪编号或错误编号定位记录</span>
        </div>
      </template>
      <el-form class="filter-form" @submit.prevent="load()">
        <el-form-item label="时间范围" class="filter-date">
          <el-date-picker v-model="range" type="datetimerange" :popper-options="rangePickerPopperOptions" value-format="YYYY-MM-DDTHH:mm:ssZ" start-placeholder="开始时间" end-placeholder="结束时间" />
        </el-form-item>
        <el-form-item label="追踪编号" class="filter-request">
          <el-input v-model="requestId" clearable placeholder="Request ID" />
        </el-form-item>
        <el-form-item label="错误编号" class="filter-error">
          <el-input v-model="errorId" clearable placeholder="ERR-YYYYMMDD-XXXXXXXX" />
        </el-form-item>
        <el-form-item label="正文关键词" class="filter-keyword">
          <el-input v-model="keyword" clearable placeholder="例如 超时、Connection refused" />
          <span v-if="keywordBlocked" class="filter-hint">正文检索限24小时内，长范围请用编号或模块定位</span>
        </el-form-item>
        <el-form-item label="模块" class="filter-logger">
          <el-input v-model="logger" clearable placeholder="logger 名称包含匹配" />
        </el-form-item>
        <el-form-item label="最低级别" class="filter-level">
          <el-select v-model="minLevel" clearable placeholder="全部级别" class="runtime-logs-level">
            <el-option v-for="item in ['INFO', 'WARN', 'ERROR']" :key="item" :label="`${item} 及以上`" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <MrrFilterActionButton action="search" :pending="loading" :busy="loading" native-type="submit" type="primary">
            查询日志
          </MrrFilterActionButton>
          <el-button :disabled="loading" @click="resetFilters">
            重置条件
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="filtersChanged" title="筛选已修改，点击查询后生效；下一批沿用已提交条件" type="info" :closable="false" />
      <AppError v-if="error" :message="error" @retry="retryQuery" />
      <AppEmpty v-else-if="!loading && !rows.length" description="所选条件下没有已入库的日志；可检查采集延迟或调整时间范围" />
      <div v-else class="results-table">
        <div class="results-heading">
          <div class="section-heading">
            <strong>日志结果</strong>
            <span>当前批次 {{ rows.length }} 条，每批最多 50 条</span>
          </div>
          <div class="log-pagination">
            <label class="auto-refresh">
              <el-switch :model-value="autoRefresh" @update:model-value="setAutoRefresh($event === true)" />
              <span>每10秒刷新首批</span>
            </label>
            <el-button :disabled="!cursor || loading" @click="load(false, null)">
              回到首批
            </el-button>
            <el-button type="primary" plain :disabled="!hasMore || loading" @click="next">
              下一批
            </el-button>
          </div>
        </div>
        <el-table v-loading="loading" :data="rows" row-key="event_id" stripe>
          <el-table-column label="时间" min-width="190">
            <template #default="scope">
              {{ formatDateTime(scope.row.occurred_at) }}
            </template>
          </el-table-column>
          <el-table-column label="级别" width="100" align="center">
            <template #default="scope">
              <el-tag size="small" effect="light" :type="levelType(scope.row.level)">
                {{ scope.row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="logger_name" label="模块" min-width="200" show-overflow-tooltip />
          <el-table-column prop="request_id" label="追踪编号" min-width="170" show-overflow-tooltip />
          <el-table-column label="正文" min-width="240" show-overflow-tooltip>
            <template #default="scope">
              <el-tag v-if="isRedactedLogText(scope.row.message_summary)" size="small" effect="plain" type="info">
                旧记录正文未保存
              </el-tag>
              <span v-else>{{ (scope.row as RuntimeLogRow).message_summary || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="scope">
              <el-button link type="primary" @click="showDetail(scope.row as RuntimeLogRow)">
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
    <el-card shadow="never" class="collector-card">
      <template #header>
        <div class="collector-heading">
          <div class="section-heading">
            <strong>采集与补录</strong>
            <span>查看来源状态，按需登记服务端历史日志文件</span>
          </div>
          <el-button :loading="statusLoading" @click="refreshHealth">
            刷新状态
          </el-button>
        </div>
      </template>
      <el-alert v-if="filesError" :title="`日志文件清单读取失败：${filesError}；文件选择暂不可用，请刷新状态重试`" type="error" :closable="false" show-icon />
      <p class="collector-description">
        长期数据库日志不自动删除。补录仅限服务端日志目录中的文件，后台按批次处理。
      </p>
      <el-form v-if="auth('system:log:import')" inline class="import-form" @submit.prevent="importFile">
        <el-form-item label="轮转日期">
          <el-date-picker v-model="historyRange" :disabled="importing" type="daterange" :popper-options="rangePickerPopperOptions" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="历史文件">
          <el-select v-model="selectedFiles" :disabled="importing || statusLoading || Boolean(filesError)" multiple :multiple-limit="20" placeholder="选择固定目录中的文件" class="runtime-logs-files">
            <el-option v-for="file in selectableFiles" :key="file.fileName" :label="file.fileName" :value="file.fileName" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button native-type="submit" :loading="importing" :disabled="!selectedFiles.length || statusLoading || Boolean(filesError)">
            登记补录
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="importMessage" :title="importMessage" type="success" :closable="false" show-icon />
      <el-alert v-if="importError" :title="importError" type="error" :closable="false" show-icon />
      <p class="collector-note">
        轮转日期筛选仅匹配文件名日期；每批最多20个文件，每文件解压上限128MB。旧格式正文默认隐藏，保留安全元数据；失败会显示明确缺口。
      </p>
      <el-table :data="(health.sources as Record<string, unknown>[]) || []" max-height="320">
        <el-table-column prop="file_name" label="来源文件" min-width="240" />
        <el-table-column prop="state" label="状态" width="120" />
        <el-table-column prop="byte_offset" label="已确认字节偏移" min-width="150" />
        <el-table-column prop="invalid_lines" label="已确认无效行" width="130" />
        <el-table-column prop="failure_code" label="故障原因" min-width="190" />
      </el-table>
      <el-collapse>
        <el-collapse-item title="容量、完整性与采集详情" name="health">
          <pre class="log-detail">{{ JSON.stringify(health, null, 2) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-card>
    <el-drawer v-model="detailVisible" title="日志详情" size="min(760px, 92vw)">
      <div class="detail-toolbar">
        <el-tag v-if="detail" size="small" effect="light" :type="levelType(detail.level)">
          {{ detail.level }}
        </el-tag>
        <span class="detail-meta">{{ formatDateTime(detail?.occurred_at) }} · {{ detail?.logger_name || '未知模块' }}</span>
      </div>
      <div class="detail-ids">
        <div v-if="detail?.request_id" class="detail-id">
          <span>追踪编号</span>
          <code>{{ detail.request_id }}</code>
          <el-button link type="primary" @click="copyRequestId(detail?.request_id)">
            复制
          </el-button>
        </div>
        <div v-if="detail?.error_id" class="detail-id">
          <span>错误编号</span>
          <code>{{ detail.error_id }}</code>
          <el-button link type="primary" @click="copyText(detail?.error_id || '', '错误编号已复制')">
            复制
          </el-button>
        </div>
      </div>
      <div class="detail-actions">
        <el-button size="small" :disabled="!detail?.request_id" @click="filterByRequest(detail?.request_id)">
          同一请求的全部日志
        </el-button>
        <el-button size="small" :disabled="!detail?.error_id" @click="filterByError(detail?.error_id)">
          同一错误组
        </el-button>
        <el-button v-if="detail?.request_id && auth('log:read')" size="small" @click="openAccessLog(detail?.request_id)">
          查看访问记录
        </el-button>
        <el-button size="small" :disabled="!detailText(detail)" @click="copyDetail">
          复制正文
        </el-button>
      </div>
      <el-alert
        v-if="isRedactedLogText(detail?.message_summary)" type="info" :closable="false" show-icon
        title="该记录写入时正文被整体脱敏，原始内容未落盘、无法恢复；修复后新产生的日志会保留模板正文。"
      />
      <pre class="log-detail" v-text="detailText(detail) || '该事件没有正文。'" />
    </el-drawer>
  </div>
</template>

<style scoped>
.runtime-log-page { display: grid; gap: 18px; min-width: 0; }
.runtime-logs-level { width: 140px; }
.runtime-logs-files { width: 340px; }
.page-header { display: flex; gap: 20px; align-items: flex-start; justify-content: space-between; }
.page-header h2 { margin: 0; font-size: 26px; line-height: 1.25; }
.page-header p { max-width: 72ch; margin: 8px 0 0; line-height: 1.6; color: var(--el-text-color-secondary); }
.section-heading { display: flex; flex-wrap: wrap; gap: 6px 12px; align-items: baseline; min-width: 0; }
.section-heading strong { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); }
.section-heading span { font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.filter-card :deep(.el-card__body) { display: grid; gap: 14px; }
.filter-form { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 12px 16px; }
.filter-form :deep(.el-form-item) { min-width: 0; margin: 0; }
.filter-form :deep(.el-form-item__content) { min-width: 0; }

.filter-form :deep(.el-input),
.filter-form :deep(.el-select),
.filter-form :deep(.el-date-editor) { width: 100% !important; }
.filter-date { grid-column: span 5; }
.filter-request { grid-column: span 3; }
.filter-error { grid-column: span 4; }
.filter-keyword { grid-column: span 5; }
.filter-logger { grid-column: span 4; }
.filter-level { grid-column: span 3; }
.filter-actions { grid-column: span 12; align-self: end; }
.filter-actions :deep(.el-form-item__content) { justify-content: flex-end; }
.filter-keyword :deep(.el-form-item__content) { flex-wrap: wrap; }
.filter-hint { flex-basis: 100%; margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-color-warning); }
.insight-card :deep(.el-card__body) { display: grid; gap: 16px; }
.insight-body { display: grid; gap: 16px; }
.insight-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.insight-stat { display: grid; gap: 2px; padding: 10px 12px; background: var(--el-fill-color-lighter); border: 1px solid var(--el-border-color-lighter); border-radius: var(--mrr-radius-md); }
.insight-stat__label { font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.insight-stat__value { font-size: 22px; line-height: 1.2; }
.insight-stat__value--error { color: var(--el-color-danger); }
.insight-stat__value--warn { color: var(--el-color-warning); }
.insight-stat__note { font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.trend-panel { padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: var(--mrr-radius-md); }
.trend-head { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; margin-bottom: 10px; }
.trend-head strong { font-size: 14px; }
.trend-head span { font-size: 12px; color: var(--el-text-color-secondary); }
.trend { display: flex; gap: 2px; align-items: flex-end; }
.trend-col { display: flex; flex: 1 1 0; align-items: flex-end; min-width: 2px; }
.trend-bars { display: flex; gap: 1px; align-items: flex-end; width: 100%; height: 64px; border-bottom: 1px solid var(--el-border-color); }
.trend-bar { flex: 1 1 0; min-height: 0; border-radius: 2px 2px 0 0; }
.trend-bar--error { background: var(--el-color-danger); }
.trend-bar--warn { background: var(--el-color-warning); }
.trend-axis { display: flex; gap: 8px; align-items: center; justify-content: space-between; margin-top: 6px; font-size: 12px; color: var(--el-text-color-secondary); }
.trend-legend { display: inline-flex; gap: 4px; align-items: center; }
.trend-dot { display: inline-block; width: 8px; height: 8px; margin-left: 6px; border-radius: 2px; }
.trend-dot--error { background: var(--el-color-danger); }
.trend-dot--warn { background: var(--el-color-warning); }
.insight-lists { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.insight-list { display: grid; gap: 4px; align-content: start; min-width: 0; }
.insight-list strong { font-size: 14px; }
.insight-empty { margin: 4px 0 0; font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary); }
.hot-row { display: flex; gap: 8px; align-items: center; width: 100%; padding: 8px 6px; font: inherit; color: inherit; text-align: left; cursor: pointer; background: transparent; border: 0; border-bottom: 1px dashed var(--el-border-color-lighter); }
.hot-row:hover { background: var(--el-fill-color-lighter); }
.hot-message { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; font-size: 13px; white-space: nowrap; }
.hot-meta { flex: 0 0 auto; font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
.module-row { display: grid; grid-template-columns: 96px minmax(0, 1fr) auto; gap: 8px; align-items: center; padding: 5px 0; }
.module-name { overflow: hidden; text-overflow: ellipsis; font-size: 13px; white-space: nowrap; }
.module-bar { height: 8px; overflow: hidden; background: var(--el-fill-color-lighter); border-radius: 4px; }
.module-bar i { display: block; height: 100%; background: var(--el-color-primary-light-5); border-radius: 4px; }
.auto-refresh { display: inline-flex; gap: 6px; align-items: center; margin-right: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.results-table { overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: var(--mrr-radius-md); }
.results-heading { display: flex; gap: 16px; align-items: center; justify-content: space-between; min-height: 56px; padding: 10px 14px; background: var(--el-fill-color-lighter); }
.log-pagination { display: flex; flex-wrap: wrap; gap: 8px; }
.collector-heading { display: flex; gap: 16px; align-items: center; justify-content: space-between; }
.collector-description { margin: 0 0 16px; line-height: 1.6; color: var(--el-text-color-secondary); }
.import-form { display: flex; flex-wrap: wrap; gap: 0 12px; align-items: center; }
.import-form :deep(.el-form-item) { margin-bottom: 12px; }
.collector-note { margin: 4px 0 16px; font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary); }
.log-detail { max-height: 440px; overflow: auto; font-family: Consolas, monospace; overflow-wrap: anywhere; white-space: pre-wrap; }
.detail-toolbar { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; margin-bottom: 12px; }
.detail-meta { font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.detail-ids { display: grid; gap: 6px; margin-bottom: 10px; }
.detail-id { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; font-size: 12px; color: var(--el-text-color-secondary); }
.detail-id code { font-family: Consolas, monospace; color: var(--el-text-color-primary); overflow-wrap: anywhere; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }

@media (max-width: 1080px) {
  .filter-form { grid-template-columns: repeat(6, minmax(0, 1fr)); }
  .filter-date { grid-column: span 6; }

  .filter-request,
  .filter-keyword,
  .filter-logger,
  .filter-error,
  .filter-level { grid-column: span 3; }
  .filter-actions { grid-column: span 6; }
  .insight-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .insight-lists { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 680px) {
  .runtime-log-page { gap: 14px; }

  .page-header,
  .collector-heading,
  .results-heading { flex-direction: column; align-items: stretch; }
  .page-header h2 { font-size: 22px; }
  .filter-form { grid-template-columns: minmax(0, 1fr); gap: 10px; }

  .filter-date,
  .filter-keyword,
  .filter-request,
  .filter-logger,
  .filter-error,
  .filter-level,
  .filter-actions { grid-column: 1; }
  .filter-actions :deep(.el-form-item__content) { justify-content: flex-start; }
  .insight-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .hot-row { flex-wrap: wrap; }
  .hot-meta { flex-basis: 100%; }
  .filter-form :deep(.el-form-item) { display: flex; flex-direction: column; align-items: stretch; }
  .filter-form :deep(.el-form-item__label) { margin-bottom: 4px; }
  .filter-actions :deep(.el-form-item__content) { justify-content: flex-start; }
  .results-heading { align-items: flex-start; }
  .log-pagination { width: 100%; }
  .log-pagination :deep(.el-button) { flex: 1; }
}
</style>

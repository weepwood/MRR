<script setup lang="ts">
import type {
  SystemErrorEvent,
  SystemErrorOverview,
} from '@/api/modules/system-errors'
/* eslint-disable vue/singleline-html-element-content-newline */
import { ElMessage } from 'element-plus'
import { RefreshCw as Refresh } from 'lucide-vue-next'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getSystemErrorDetail,
  getSystemErrorOverview,
  searchSystemErrors,
} from '@/api/modules/system-errors'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import { useUserStore } from '@/store/modules/user'
import { formatDateTime, isRedactedLogText } from '@/utils/display-format'
import { checkPermission } from '@/utils/permission'

defineOptions({ name: 'RuntimeErrorsPage' })

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
/** 访问日志只要求 log:read。没有权限就不显示入口，避免落到 403 页面。 */
const canReadAccessLogs = computed(() => checkPermission(userStore.permissions, 'log:read'))
const loading = ref(false)
const error = ref('')
const list = ref<SystemErrorEvent[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const detailVisible = ref(false)
const detailLoading = ref(false)
const current = ref<SystemErrorEvent | null>(null)
const lastRefreshedAt = ref<Date | null>(null)

const overview = ref<SystemErrorOverview>({
  totalGroups: 0,
  totalOccurrences: 0,
  openGroups: 0,
  acknowledgedGroups: 0,
  resolvedGroups: 0,
  errorGroups: 0,
  warnGroups: 0,
  recentOccurrences: 0,
})

const query = reactive({
  keyword: '',
  level: '',
  module: '',
  range: '',
})

/** 时间范围 → 后端 since 参数（本地时区 ISO 格式，后端 LocalDateTime 解析）。 */
function rangeStart(range: string) {
  const pad = (value: number) => String(value).padStart(2, '0')
  const format = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  const now = new Date()
  if (range === 'today') {
    const startOfDay = new Date(now)
    startOfDay.setHours(0, 0, 0, 0)
    return format(startOfDay)
  }
  if (range === '24h') { return format(new Date(now.getTime() - 24 * 3600 * 1000)) }
  if (range === '7d') { return format(new Date(now.getTime() - 7 * 24 * 3600 * 1000)) }
  return undefined
}

const since = computed(() => rangeStart(query.range))

async function fetchData(silent = false) {
  if (!silent) { loading.value = true }
  try {
    const params = {
      page: page.value,
      size: size.value,
      keyword: query.keyword.trim() || undefined,
      level: query.level || undefined,
      module: query.module.trim() || undefined,
      since: since.value,
    }
    const [pageResult, overviewResult] = await Promise.all([
      searchSystemErrors(params),
      getSystemErrorOverview(),
    ])
    list.value = pageResult.data?.list ?? []
    total.value = pageResult.data?.total ?? 0
    if (overviewResult.data) {
      overview.value = overviewResult.data
    }
    lastRefreshedAt.value = new Date()
  }
  catch (exception: unknown) {
    // 静默刷新失败时保留已展示的数据，不打断浏览。
    if (!silent) { error.value = exception instanceof Error ? exception.message : '加载运行错误失败' }
  }
  finally {
    if (!silent) { loading.value = false }
  }
}

function loadData() {
  return fetchData(false)
}

function handleSearch() {
  page.value = 1
  void loadData()
}

function resetFilters() {
  query.keyword = ''
  query.level = ''
  query.module = ''
  query.range = ''
  page.value = 1
  void loadData()
}

/**
 * 承接日志管理页「查看相关运行错误」的跳转：/runtime-errors?requestId=xxx
 * 关键字检索覆盖 request_id 模糊匹配。页面被缓存时跳转不重新 mount，
 * 与日志管理页同款：跟随 query 变化重新检索。
 */
watch(() => route.query.requestId, (value) => {
  if (route.path !== '/runtime-errors') { return }
  if (typeof value !== 'string' || !value) { return }
  query.keyword = value
  handleSearch()
})

/** 30 秒静默轮询：页面隐藏时暂停，重新可见立即补一次。 */
const REFRESH_INTERVAL_MS = 30_000
let refreshTimer: ReturnType<typeof setInterval> | undefined

function handleVisibilityChange() {
  if (!document.hidden) { void fetchData(true) }
}

onMounted(() => {
  void loadData()
  refreshTimer = setInterval(() => {
    if (!document.hidden) { void fetchData(true) }
  }, REFRESH_INTERVAL_MS)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  if (refreshTimer !== undefined) { clearInterval(refreshTimer) }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

async function openDetail(row: SystemErrorEvent) {
  detailVisible.value = true
  detailLoading.value = true
  current.value = row
  try {
    const response = await getSystemErrorDetail(row.id)
    if (response.data) {
      current.value = response.data
    }
  }
  finally {
    detailLoading.value = false
  }
}

function openAccessLog(requestId?: string) {
  if (!requestId || !canReadAccessLogs.value) { return }
  detailVisible.value = false
  void router.push({ path: '/logs', query: { requestId } })
}

/** 指标卡点击即按级别套用筛选；再点同卡视为取消。 */
function applyLevelFilter(level: string) {
  query.level = query.level === level ? '' : level
  handleSearch()
}

/** 表格里的模块名点击即按该模块筛选，再点同值取消。 */
function filterByModule(module?: string) {
  if (!module) { return }
  query.module = query.module.trim() === module ? '' : module
  handleSearch()
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

/**
 * 摘要展示统一口径：正文为占位符（历史整体脱敏）或为空时，
 * 回退显示异常类型——这是该记录上始终有值、最能说明问题的字段，
 * 不再渲染「正文未保存」之类的解释性标签。
 */
function summaryDisplay(row: SystemErrorEvent) {
  const summary = row.messageSummary || ''
  if (!summary.trim() || isRedactedLogText(summary)) { return row.exceptionType || '-' }
  return summary
}

function levelTagType(level: string) {
  return level === 'ERROR' ? 'danger' : 'warning'
}
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">Runtime Error Center</p>
        <h2>运行错误中心</h2>
        <p class="subtitle">集中查看后端 WARN / ERROR，按指纹合并重复事件，并通过错误编号、Request ID 关联接口访问日志。</p>
      </div>
      <div class="header-actions">
        <span class="auto-refresh-note">每 30 秒自动刷新<template v-if="lastRefreshedAt"> · 上次 {{ formatDateTime(lastRefreshedAt) }}</template></span>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <section class="mrr-metric-grid mrr-metric-grid--four">
      <button type="button" class="metric-action" title="点击仅看 ERROR 错误组" @click="applyLevelFilter('ERROR')">
        <MrrMetricCard label="ERROR 错误组" :value="loading || error ? '—' : overview.errorGroups" value-tone="danger" note="点击仅看 ERROR" />
      </button>
      <button type="button" class="metric-action" title="点击仅看 WARN 错误组" @click="applyLevelFilter('WARN')">
        <MrrMetricCard label="WARN 错误组" :value="loading || error ? '—' : overview.warnGroups" value-tone="warning" note="点击仅看 WARN" />
      </button>
      <MrrMetricCard label="近24小时活跃错误组" :value="loading || error ? '—' : overview.recentOccurrences" note="按最后发生时间统计" />
      <button type="button" class="metric-action" title="点击查看全部错误组" @click="resetFilters">
        <MrrMetricCard label="累计错误组" :value="loading || error ? '—' : overview.totalGroups" :note="`累计 ${overview.totalOccurrences} 次 · 点击查看全部`" />
      </button>
    </section>

    <el-card shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="错误编号 / Request ID / 异常 / 消息"
            class="runtime-errors-keyword"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="query.module" clearable placeholder="controller / storage" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" clearable placeholder="全部" class="runtime-errors-level">
            <el-option label="ERROR" value="ERROR" />
            <el-option label="WARN" value="WARN" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-select v-model="query.range" clearable placeholder="全部" class="runtime-errors-range" @change="handleSearch">
            <el-option label="今天" value="today" />
            <el-option label="近 24 小时" value="24h" />
            <el-option label="近 7 天" value="7d" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <MrrFilterActionButton action="search" :pending="loading" :busy="loading" type="primary" @click="handleSearch">
            查询
          </MrrFilterActionButton>
          <MrrFilterActionButton action="reset" :pending="loading" :busy="loading" @click="resetFilters">重置</MrrFilterActionButton>
        </el-form-item>
      </el-form>

      <AppLoading v-if="loading" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadData" />
      <AppEmpty v-else-if="!list.length" description="暂无运行错误事件" />
      <el-table v-else :data="list" stripe class="runtime-errors-table">
        <el-table-column prop="lastSeenAt" label="最后发生" min-width="175">
          <template #default="{ row }">{{ formatDateTime(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorId" label="错误编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <button type="button" class="module-filter" :title="`点击按模块 ${row.module} 筛选`" @click="filterByModule(row.module)">
              {{ row.module || '-' }}
            </button>
          </template>
        </el-table-column>
        <el-table-column label="错误摘要" min-width="360" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ summaryDisplay(row as SystemErrorEvent) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="requestId" label="Request ID" min-width="155" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="occurrenceCount" label="次数" width="90" align="right" />
        <el-table-column prop="recent24hCount" label="近24h" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'recent-hot': (row.recent24hCount ?? 0) >= 10 }" :title="`最近 24 小时发生 ${row.recent24hCount ?? 0} 次（最多统计最近 50 次）`">
              {{ row.recent24hCount ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as SystemErrorEvent)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="运行错误详情" width="880px" top="5vh">
      <div v-if="current && canReadAccessLogs && current?.requestId" class="detail-links">
        <el-button size="small" @click="openAccessLog(current?.requestId)">
          查看访问记录
        </el-button>
      </div>
      <AppLoading v-if="detailLoading" type="card" :cols="1" />
      <el-descriptions v-else-if="current" :column="2" border>
        <el-descriptions-item label="错误编号">
          <code class="detail-code">{{ current.errorId }}</code>
          <el-button link type="primary" @click="copyText(current?.errorId || '', '错误编号已复制')">
            复制
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="levelTagType(current.level)">{{ current.level }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="累计次数">{{ current.occurrenceCount }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ current.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="首次发生">{{ formatDateTime(current.firstSeenAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后发生">{{ formatDateTime(current.lastSeenAt) }}</el-descriptions-item>
        <el-descriptions-item label="线程" :span="2">{{ current.threadName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Request ID" :span="2">
          <code class="detail-code">{{ current.requestId || '-' }}</code>
          <el-button v-if="current?.requestId" link type="primary" @click="copyText(current?.requestId || '', 'Request ID 已复制')">
            复制
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="记录器" :span="2">{{ current.loggerName }}</el-descriptions-item>
        <el-descriptions-item label="异常类型" :span="2">{{ current.exceptionType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误摘要" :span="2">
          <pre class="detail-pre summary-pre">{{ summaryDisplay(current) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="异常堆栈" :span="2">
          <pre class="detail-pre">{{ current.stackTrace || '该事件没有异常堆栈。' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
/* stylelint-disable order/properties-order, declaration-property-value-keyword-no-deprecated, media-feature-range-notation */
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

/* 指标卡与模块名都承担「点击筛选」交互，外观退成卡片但语义是按钮。 */
.metric-action {
  padding: 0;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-md, 8px);
}

.metric-action:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.module-filter {
  padding: 0;
  font: inherit;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.module-filter:hover {
  color: var(--el-color-primary);
  text-decoration: underline;
}

.detail-code {
  font-family: Consolas, "Courier New", monospace;
  overflow-wrap: anywhere;
}

.header-actions {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  align-items: flex-end;
  gap: 4px;
}

.auto-refresh-note {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  margin: 4px 0 8px;
}

.eyebrow {
  margin: 0;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.subtitle {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-pre {
  box-sizing: border-box;
  max-height: 420px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.summary-pre {
  max-height: 180px;
}

.runtime-errors-keyword {
  width: 300px;
}

.runtime-errors-level {
  width: 130px;
}

.runtime-errors-range {
  width: 140px;
}

/* 频次热的错误组用数据编码色提示；纯色声明，Chrome 86 安全。 */
.recent-hot {
  color: var(--el-color-danger);
  font-weight: 600;
}

.runtime-errors-table {
  margin-top: 12px;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    align-items: flex-start;
  }
}
/* stylelint-enable order/properties-order, declaration-property-value-keyword-no-deprecated, media-feature-range-notation */
</style>

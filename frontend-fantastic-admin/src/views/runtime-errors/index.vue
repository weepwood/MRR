<script setup lang="ts">
import type {
  SystemErrorEvent,
  SystemErrorOverview,
} from '@/api/modules/system-errors'
/* eslint-disable vue/singleline-html-element-content-newline */
import { RefreshCw as Refresh } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getSystemErrorDetail,
  getSystemErrorOverview,
  listSystemErrorModules,
  searchSystemErrors,
} from '@/api/modules/system-errors'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import { useCrudList } from '@/composables/useCrudList'
import { useUserStore } from '@/store/modules/user'
import useVisibleInterval from '@/utils/composables/useVisibleInterval'
import { copyText as copyToClipboard } from '@/utils/copy-text'
import { formatDateTime, isRedactedLogText } from '@/utils/display-format'
import { checkSessionPermission } from '@/utils/permission'

defineOptions({ name: 'RuntimeErrorsPage' })

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
/** 访问日志只要求 log:read。没有权限就不显示入口，避免落到 403 页面。 */
const canReadAccessLogs = computed(() => checkSessionPermission(
  userStore.profile.roleCode,
  userStore.permissions,
  'log:read',
))
const error = ref('')
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

const defaultQuery = {
  keyword: '',
  level: '',
  module: '',
  range: '',
}
const routeRequestId = computed(() => typeof route.query.requestId === 'string' ? route.query.requestId.trim() : '')
const linkedRequestId = ref(routeRequestId.value)

/** 模块筛选项：去重后的已有模块名；拉取失败不阻断筛选（下拉仍可手输）。 */
const moduleOptions = ref<string[]>([])

async function loadModules() {
  try {
    const response = await listSystemErrorModules()
    moduleOptions.value = Array.isArray(response.data) ? response.data : []
  }
  catch {
    moduleOptions.value = []
  }
}

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

const { query, list, total, loading, resultVersion, pageNum: page, pageSize: size, loadData, handleSearch } = useCrudList<SystemErrorEvent, typeof defaultQuery>({
  defaultQuery: { ...defaultQuery, keyword: linkedRequestId.value },
  fetchApi: async ({ page, size, keyword, level, module, range }, { isCurrent, silent }) => {
    try {
      const [pageResult, overviewResult] = await Promise.all([
        searchSystemErrors({
          page,
          size,
          keyword: keyword.trim() || undefined,
          level: level || undefined,
          module: module.trim() || undefined,
          since: rangeStart(range),
        }),
        getSystemErrorOverview(),
      ])
      if (isCurrent()) {
        error.value = ''
        if (overviewResult.data) {
          overview.value = overviewResult.data
        }
        lastRefreshedAt.value = new Date()
      }
      return pageResult
    }
    catch (exception: unknown) {
      if (isCurrent() && !silent) {
        error.value = exception instanceof Error ? exception.message : '加载运行错误失败'
      }
      return undefined
    }
  },
})

function searchFromStart(keyword = '') {
  Object.assign(query, defaultQuery, { keyword })
  return handleSearch()
}

function resetFilters() {
  if (linkedRequestId.value) {
    return router.replace({ query: { ...route.query, requestId: undefined } })
  }
  return searchFromStart()
}

// 监听路径以覆盖两页之间携带同一个 Request ID 的跳转。
watch([() => route.path, routeRequestId], ([path, requestId]) => {
  if (path !== '/runtime-errors' || (!requestId && !linkedRequestId.value)) { return }
  linkedRequestId.value = requestId
  detailVisible.value = false
  void searchFromStart(requestId)
})

/** 30 秒静默轮询：页面隐藏时暂停，重新可见立即补一次。 */
const REFRESH_INTERVAL_MS = 30_000
function silentRefresh() {
  if (route.path === '/runtime-errors' && !document.hidden && !loading.value) {
    void loadData({ silent: true })
  }
}

onMounted(() => {
  void loadModules()
})

// 隐藏标签页、KeepAlive 失活时暂停，卸载自动清理；见 DESIGN.md §7。
useVisibleInterval(silentRefresh, REFRESH_INTERVAL_MS)

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
  await copyToClipboard(text, { success: message })
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
  <MrrPageShell class="runtime-errors-page">
    <MrrPageHeader
      size="compact"
      eyebrow="Runtime Error Center"
      title="运行错误中心"
      description="集中查看后端 WARN / ERROR，按指纹合并重复事件，并通过错误编号、Request ID 关联接口访问日志。"
    >
      <template #meta>
        <span class="auto-refresh-note" aria-live="polite">每 30 秒自动刷新<template v-if="lastRefreshedAt"> · 上次 {{ formatDateTime(lastRefreshedAt) }}</template></span>
      </template>
      <template #actions>
        <el-button :loading="loading" @click="loadData()">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </MrrPageHeader>

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

    <MrrDataTablePanel title="运行错误组" :count="total" :loading="loading" :result-key="resultVersion" class="runtime-errors-panel">
      <template #filters>
        <!-- 筛选栏对齐用户管理页模式：无可见标签，控件 aria-label + 自描述占位符 -->
        <MrrFilterBar embedded>
          <div class="runtime-errors-filter__field runtime-errors-filter__keyword">
            <el-input
              id="runtime-errors-keyword"
              v-model="query.keyword"
              clearable
              aria-label="关键字"
              placeholder="错误编号 / Request ID / 异常 / 消息"
              @keyup.enter="handleSearch"
            />
          </div>
          <div class="runtime-errors-filter__field runtime-errors-filter__select">
            <el-select
              id="runtime-errors-module"
              v-model="query.module"
              clearable
              filterable
              allow-create
              default-first-option
              aria-label="模块"
              placeholder="全部模块"
              @change="handleSearch"
            >
              <el-option v-for="moduleName in moduleOptions" :key="moduleName" :label="moduleName" :value="moduleName" />
            </el-select>
          </div>
          <div class="runtime-errors-filter__field runtime-errors-filter__select">
            <el-select id="runtime-errors-level" v-model="query.level" clearable aria-label="级别" placeholder="全部级别">
              <el-option label="ERROR" value="ERROR" />
              <el-option label="WARN" value="WARN" />
            </el-select>
          </div>
          <div class="runtime-errors-filter__field runtime-errors-filter__select">
            <el-select id="runtime-errors-range" v-model="query.range" clearable aria-label="时间范围" placeholder="全部时间" @change="handleSearch">
              <el-option label="今天" value="today" />
              <el-option label="近 24 小时" value="24h" />
              <el-option label="近 7 天" value="7d" />
            </el-select>
          </div>
          <template #actions>
            <MrrFilterActionButton action="search" :pending="loading" :busy="loading" @click="handleSearch">
              查询
            </MrrFilterActionButton>
            <MrrFilterActionButton action="reset" :pending="loading" :busy="loading" @click="resetFilters">重置</MrrFilterActionButton>
          </template>
        </MrrFilterBar>
      </template>

      <AppLoading v-if="loading && !list.length" type="table" :rows="8" />
      <AppError v-else-if="error" :message="error" @retry="loadData()" />
      <AppEmpty
        v-else-if="!list.length"
        :description="linkedRequestId && query.keyword === linkedRequestId ? '未找到相关运行错误。错误组仅保留最近一次关联的 Request ID。' : '暂无运行错误事件'"
      />
      <el-table v-else :data="list" stripe class="runtime-errors-table">
        <el-table-column prop="lastSeenAt" label="最后发生" min-width="175">
          <template #default="{ row }">{{ formatDateTime(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorId" label="错误编号" min-width="190" show-overflow-tooltip>
          <template #default="{ row }"><span class="detail-code runtime-errors__id">{{ row.errorId || '-' }}</span></template>
        </el-table-column>
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
          <template #default="{ row }"><span class="detail-code runtime-errors__id">{{ row.requestId || '-' }}</span></template>
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

      <template #pagination>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData()"
        />
      </template>
    </MrrDataTablePanel>

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
  </MrrPageShell>
</template>

<style scoped>
/* stylelint-disable order/properties-order, declaration-property-value-keyword-no-deprecated, media-feature-range-notation */
.detail-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  margin-bottom: var(--mrr-space-3);
}

/* 指标卡与模块名都承担「点击筛选」交互，外观退成卡片但语义是按钮。 */
.metric-action {
  padding: 0;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;

  /* 与内部指标卡同半径，焦点轮廓才会贴合卡片外形（原用 md，比卡片小一档，四角露出缝）。 */
  border-radius: var(--mrr-radius-card);
}

.metric-action:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

/*
 * 可点击的指标卡要有悬停反馈（DESIGN.md §2.7：可点击卡片悬停升一档阴影并加深描边，不靠位移）；
 * 原来三张能点、一张不能点，外观完全相同，用户无从分辨。
 */
.metric-action :deep(.mrr-metric-card) {
  transition: border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.metric-action:hover :deep(.mrr-metric-card) {
  border-color: var(--mrr-border-strong);
  box-shadow: var(--mrr-shadow-sm);
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

/* 原来只有悬停态，键盘聚焦时没有任何可见变化（DESIGN.md §5.2）。 */
.module-filter:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
  border-radius: max(0px, calc(var(--mrr-radius-sm) - 2px));
}

.runtime-errors__id {
  font-size: var(--mrr-font-size-xs);
}

.detail-code {
  /* 统一走等宽令牌：Windows 上 Consolas 无汉字，令牌里排了中文回退，避免落到宋体。 */
  font-family: var(--mrr-font-mono);
  overflow-wrap: anywhere;
}

.auto-refresh-note {
  font-variant-numeric: tabular-nums;
}

.detail-pre {
  box-sizing: border-box;
  max-height: 420px;
  margin: 0;
  padding: var(--mrr-space-3);
  overflow: auto;
  border-radius: var(--mrr-radius-md);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.summary-pre {
  max-height: 180px;
}

.runtime-errors-filter__field {
  display: grid;
  min-width: 0;
}

.runtime-errors-filter__keyword {
  flex: 1 1 320px;
  min-width: 260px;
}

.runtime-errors-filter__select {
  flex: 0 1 190px;
  width: 190px;
}

@media (max-width: 760px) {
  .runtime-errors-filter__keyword,
  .runtime-errors-filter__select {
    flex: 1 1 100%;
    width: 100%;
  }
}

/* 频次热的错误组用数据编码色提示；纯色声明，Chrome 86 安全。 */
.recent-hot {
  color: var(--el-color-danger);
  font-weight: 600;
}

/* stylelint-enable order/properties-order, declaration-property-value-keyword-no-deprecated, media-feature-range-notation */
</style>

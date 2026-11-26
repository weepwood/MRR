<script setup lang="ts">
import type { DbBrowseColumnView, DbBrowsePage, DbBrowseTableSchema, DbBrowseTableSummary } from '@/api/modules/database-browse'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { describeBrowseError, getBrowseRows, getBrowseSchema, getBrowseTables } from '@/api/modules/database-browse'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { useUserStore } from '@/store/modules/user'
import { hasPermission } from '@/utils/session'
import { tableSequenceColumn } from '@/utils/table-sequence'

defineOptions({ name: 'DatabaseBrowsePage' })

const userStore = useUserStore()
const canBrowse = computed(() => hasPermission('system:db:browse'))
let generation = 0
let controller: AbortController | undefined
const retryAction = ref<(() => Promise<void>)>()
const loading = ref(false)
const errorMessage = ref('')
const featureDisabled = ref(false)
const tables = ref<DbBrowseTableSummary[]>([])
const tableKey = ref('')
const schema = ref<DbBrowseTableSchema>()
const page = ref<DbBrowsePage>()
const filterValues = reactive<Record<string, string>>({})
const cursorStack = ref<string[]>([])
const rowOffsets = ref<number[]>([0])
const pageIndex = ref(1)
const loadedRowOffset = computed(() => rowOffsets.value[pageIndex.value - 1] ?? 0)
const activeFilter = ref<string>()

const filterableColumns = computed<DbBrowseColumnView[]>(
  () => (schema.value?.columns ?? []).filter(column => column.filterable),
)
const rows = computed(() => page.value?.rows ?? [])
const columns = computed(() => page.value?.columns ?? schema.value?.columns ?? [])

const estimateText = computed(() => formatRowCount(page.value?.estimatedRows, page.value?.estimated ?? true))

const currentTable = computed(() => tables.value.find(item => item.tableKey === tableKey.value))

/** 单页行数跟随注册表上限（后端还会再校验一次，这里只是少发一次必然失败的请求）。 */
const pageSizeOverride = ref<number>()
const maxPageSize = computed(() => Math.min(schema.value?.maxPageSize ?? 50, 100))
const pageSize = computed(() => pageSizeOverride.value ?? maxPageSize.value)
const pageSizes = computed(() => [...new Set([10, 20, 50, 100, maxPageSize.value].filter(size => size <= maxPageSize.value))].sort((a, b) => a - b))

const emptyText = computed(() => {
  if (schema.value?.requireFilter && !activeFilter.value) { return '该表必须提供过滤条件后才会返回数据' }
  if (activeFilter.value) { return '没有符合条件的数据' }
  return '该表暂无数据'
})

/**
 * 行数展示。{@code null} 表示后端拿不到估算（从未 ANALYZE 且不是空表），
 * 这时显示「未统计」而不是「0」，避免把「不知道」显示成「没有数据」。
 */
function formatRowCount(value?: number | null, estimated = true): string {
  if (value == null) { return '未统计' }
  if (value === 0) { return '0 行' }
  return `${estimated ? '约 ' : ''}${value.toLocaleString('zh-CN')} 行`
}

function tableOptionLabel(item: DbBrowseTableSummary): string {
  return `${item.label} · ${item.tableKey}`
}

/**
 * 生成过滤串。
 *
 * <p>只有「原值返回」的文本列才用前缀匹配；掩码列一律等值——后端按库内原值过滤，
 * 前缀匹配会把掩码变成可逐字符还原的旁路。</p>
 */
function buildFilter(): string | undefined {
  const parts: string[] = []
  for (const column of filterableColumns.value) {
    const raw = (filterValues[column.name] ?? '').trim()
    if (!raw) { continue }
    const prefixAllowed = column.type === 'TEXT' && column.mask === 'NONE' && column.allowedOps.includes('PREFIX')
    const op = prefixAllowed ? 'PREFIX' : column.allowedOps.includes('EQ') ? 'EQ' : undefined
    if (!op) { continue }
    // 后端按逗号切分条件，值里的逗号必须编码，否则会被当成下一个条件。
    parts.push(`${column.name}:${op}:${raw.replace(/,/g, '%2C')}`)
    if (parts.length >= 5) { break }
  }
  return parts.length ? parts.join(',') : undefined
}

/**
 * 错误按业务码给可执行提示；3007 单独处理成「功能未开启」，不显示成「没有数据」。
 *
 * <p>业务码的提取统一交给 describeBrowseError：HTTP 非 2xx 时请求层抛的是 AxiosError，
 * code 是字符串 ERR_BAD_REQUEST，业务码只在 response.data 里，直接读 error.code 会漏判。</p>
 */
function describeError(error: unknown): string {
  return describeBrowseError(error).message
}

function isFeatureDisabled(error: unknown): boolean {
  return describeBrowseError(error).featureDisabled
}

function cancelRequest() {
  generation++
  controller?.abort()
  controller = undefined
  loading.value = false
  retryAction.value = undefined
}

function clearSelection() {
  tableKey.value = ''
  schema.value = undefined
  pageSizeOverride.value = undefined
  page.value = undefined
  cursorStack.value = []
  rowOffsets.value = [0]
  pageIndex.value = 1
  activeFilter.value = undefined
  Object.keys(filterValues).forEach(name => delete filterValues[name])
}

function resetSession() {
  cancelRequest()
  clearSelection()
  tables.value = []
  errorMessage.value = ''
  featureDisabled.value = false
}

function startRequest(retry: () => Promise<void>) {
  cancelRequest()
  const current = generation
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  featureDisabled.value = false
  retryAction.value = retry
  return { signal: request.signal, isCurrent: () => current === generation && canBrowse.value }
}

function requestFailed(error: unknown) {
  const status = (error as { response?: { status?: number }, code?: number } | null)?.response?.status
  const failure = error as { code?: number, response?: { data?: { code?: number } }, payload?: { code?: number } } | null
  const code = failure?.response?.data?.code ?? failure?.payload?.code ?? failure?.code
  if (status === 401 || status === 403 || code === 401 || code === 403 || code === 3001 || isFeatureDisabled(error)) {
    clearSelection()
    tables.value = []
    retryAction.value = loadTables
  }
  errorMessage.value = describeError(error)
  featureDisabled.value = isFeatureDisabled(error)
}

async function loadTables() {
  if (!canBrowse.value) { return }
  const request = startRequest(loadTables)
  try {
    const result = await getBrowseTables(request.signal)
    if (request.isCurrent()) { tables.value = result }
  }
  catch (error) {
    if (request.isCurrent()) { requestFailed(error) }
  }
  finally {
    if (request.isCurrent()) { loading.value = false; controller = undefined }
  }
}

interface FetchTarget {
  page: number
  cursor?: string
  filter?: string
  stack: string[]
  size?: number
  offsets?: number[]
}

/**
 * 拉取一页数据。
 *
 * <p>页码、游标栈与生效中的过滤条件只在请求成功后提交：失败时界面保持上一次的
 * 一致状态，不会出现「页码显示第 2 页、表格还是第 1 页」。</p>
 */
async function fetchRows(target: FetchTarget) {
  const key = tableKey.value
  if (!key || !canBrowse.value || !schema.value) { return }
  if (schema.value.requireFilter && !target.filter) {
    cancelRequest()
    page.value = undefined
    pageIndex.value = 1
    cursorStack.value = []
    rowOffsets.value = [0]
    activeFilter.value = undefined
    errorMessage.value = ''
    return
  }
  const requestedSize = target.size ?? pageSize.value
  const request = startRequest(() => fetchRows(target))
  try {
    const result = await getBrowseRows(key, {
      page: target.page,
      size: requestedSize,
      filter: target.filter,
      cursor: target.cursor,
    }, request.signal)
    if (!request.isCurrent()) { return }
    if (!result || result.tableKey !== key || result.page !== target.page) { throw new Error('查询结果与当前数据表或页码不匹配') }
    page.value = result
    pageSizeOverride.value = requestedSize
    pageIndex.value = target.page
    cursorStack.value = target.stack
    rowOffsets.value = target.offsets ?? [0]
    activeFilter.value = target.filter
  }
  catch (error) {
    if (request.isCurrent()) { requestFailed(error) }
  }
  finally {
    if (request.isCurrent()) { loading.value = false; controller = undefined }
  }
}

async function selectTable(key: string) {
  if (!canBrowse.value || !tables.value.some(item => item.tableKey === key)) { return }
  clearSelection()
  tableKey.value = key
  const request = startRequest(() => selectTable(key))
  try {
    const result = await getBrowseSchema(key, request.signal)
    if (!request.isCurrent()) { return }
    if (!result || result.tableKey !== key) { throw new Error('数据表结构与当前选择不匹配') }
    schema.value = result
    await fetchRows({ page: 1, stack: [], filter: undefined })
  }
  catch (error) {
    if (request.isCurrent()) { requestFailed(error) }
  }
  finally {
    if (request.isCurrent()) { loading.value = false; controller = undefined }
  }
}

async function search() {
  await fetchRows({ page: 1, stack: [], filter: buildFilter() })
}

async function resetFilters() {
  Object.keys(filterValues).forEach(name => delete filterValues[name])
  await search()
}

async function changePageSize(size: number) {
  if (loading.value || !page.value || !pageSizes.value.includes(size) || size === pageSize.value) { return }
  await fetchRows({ page: 1, stack: [], filter: activeFilter.value, size })
}
const maxReachablePage = computed(() => pageIndex.value + (page.value?.hasMore && page.value.nextCursor ? 1 : 0))
async function jumpToPage(target: number) {
  if (loading.value || !page.value || !Number.isSafeInteger(target) || target < 1 || target > maxReachablePage.value || target === pageIndex.value) { return }
  const stack = target > pageIndex.value
    ? [...cursorStack.value, page.value.nextCursor!]
    : cursorStack.value.slice(0, target - 1)
  const offsets = target > pageIndex.value
    ? [...rowOffsets.value, loadedRowOffset.value + rows.value.length]
    : rowOffsets.value.slice(0, target)
  await fetchRows({ page: target, cursor: stack[stack.length - 1], filter: activeFilter.value, stack, offsets })
}

watch([() => userStore.token, canBrowse], () => {
  resetSession()
  if (canBrowse.value) { void loadTables() }
}, { flush: 'sync' })
onBeforeUnmount(resetSession)
onMounted(loadTables)
</script>

<template>
  <MrrPageShell class="db-browse-page">
    <MrrPageHeader
      eyebrow="Database Browser"
      title="数据表浏览"
      description="只读浏览已授权的数据表。含个人信息的字段已脱敏，大表限制单页行数；离开页面后结果即被丢弃，不做缓存。"
    />

    <!-- 两块原为无标题 el-card：补分区标题，与其他业务页的「条件卡 + 结果卡」结构一致（DESIGN.md §3.4）。 -->
    <MrrSectionCard title="选择数据表" description="只列出当前账号已授权的表；部分大表必须带过滤条件才会返回数据。" icon="i-lucide:database">
      <div class="toolbar">
        <label class="toolbar-label" for="db-table-select">数据表</label>
        <el-select
          id="db-table-select"
          v-model="tableKey"
          class="table-select"
          aria-label="数据表"
          :disabled="loading"
          placeholder="选择要查看的表"
          filterable
          @change="selectTable"
        >
          <el-option
            v-for="item in tables"
            :key="item.tableKey"
            :label="tableOptionLabel(item)"
            :value="item.tableKey"
          >
            <span class="option-name">{{ item.label }}</span>
            <span class="option-meta">{{ item.tableKey }} · {{ formatRowCount(item.estimatedRows, item.estimated) }}</span>
          </el-option>
        </el-select>
        <el-tag v-if="schema?.personalData" type="warning" effect="plain">
          含个人敏感信息，已脱敏
        </el-tag>
        <el-tag v-if="schema && schema.maxPageSize <= 50" type="info" effect="plain">
          大表 · 单页最多 {{ schema.maxPageSize }} 行
        </el-tag>
        <span v-if="!tables.length && !loading && !featureDisabled && !errorMessage" class="empty-hint">
          当前账号没有可浏览的表，请联系管理员分配权限
        </span>
      </div>

      <el-alert
        v-if="featureDisabled"
        class="state-alert"
        type="info"
        :closable="false"
        show-icon
        title="该功能未开启，请联系管理员"
      />
      <el-alert
        v-else-if="errorMessage"
        class="state-alert"
        type="error"
        :closable="false"
        show-icon
        :title="errorMessage"
      />

      <section v-if="!tableKey && tables.length && !loading && !featureDisabled && !errorMessage" class="table-directory" aria-label="已授权数据表">
        <p class="directory-caption">
          已授权 {{ tables.length }} 张数据表{{ tables.length > 6 ? '，下方展示前 6 张，更多可通过上方搜索选择' : '，点击下方卡片开始浏览' }}。
        </p>
        <div class="directory-grid">
          <button v-for="item in tables.slice(0, 6)" :key="item.tableKey" type="button" class="directory-item" :aria-label="`浏览${item.label}`" @click="selectTable(item.tableKey)">
            <span class="directory-name"><FaIcon name="i-lucide:table-2" aria-hidden="true" /> <strong>{{ item.label }}</strong></span>
            <span class="directory-meta">{{ item.tableKey }} · {{ formatRowCount(item.estimatedRows, item.estimated) }}</span>
            <span class="directory-description">{{ item.description }}</span>
            <span v-if="item.requireFilter" class="directory-meta">需要过滤条件</span>
          </button>
        </div>
      </section>

      <div v-if="errorMessage && !loading && retryAction" class="retry-row">
        <el-button @click="retryAction()">
          重试请求
        </el-button>
      </div>
      <div v-if="currentTable" class="table-meta">
        <div class="meta-line">
          <code class="meta-key">{{ currentTable.tableKey }}</code>
          <span class="meta-desc">{{ currentTable.description }}</span>
        </div>
      </div>

      <el-alert
        v-if="schema?.requireFilter"
        class="require-filter-alert"
        type="warning"
        :closable="false"
        show-icon
        title="该表必须提供至少一个过滤条件，后端才会返回数据"
      />

      <MrrFilterBar v-if="schema" class="filters" embedded labelled layout="grid">
        <div v-for="column in filterableColumns" :key="column.name" class="mrr-filter-field">
          <label :for="`db-filter-${column.name}`">{{ column.label }}</label>
          <el-input
            :id="`db-filter-${column.name}`"
            v-model="filterValues[column.name]"
            :aria-label="column.label"
            :disabled="loading"
            :placeholder="`输入${column.label}`"
            clearable
            @keyup.enter="search"
          />
        </div>
        <template #actions>
          <MrrFilterActionButton action="search" type="primary" feedback="request" :pending="loading" :busy="loading" :disabled="loading" @click="search">
            查询
          </MrrFilterActionButton>
          <MrrFilterActionButton action="reset" feedback="request" :busy="loading" :disabled="loading" @click="resetFilters">
            重置
          </MrrFilterActionButton>
        </template>
      </MrrFilterBar>
    </MrrSectionCard>

    <MrrDataTablePanel v-if="schema" title="查询结果" :description="currentTable?.label" :loading="loading && Boolean(page)" :result-key="page">
      <AppLoading v-if="loading && !page" type="table" :rows="3" />
      <el-table v-else :data="rows" stripe border class="result-table">
        <el-table-column v-bind="tableSequenceColumn(loadedRowOffset, rows.length)" />
        <el-table-column
          v-for="column in columns"
          :key="column.name"
          :label="column.label"
          :prop="column.name"
          min-width="140"
          show-overflow-tooltip
        >
          <template #header>
            <div class="column-header">
              <span class="column-label">{{ column.label }}</span>
              <span class="column-name">{{ column.name }}</span>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty v-if="!errorMessage || page" size="compact" :description="emptyText" />
          <span v-else class="empty-hint">查询未完成，请重试</span>
        </template>
      </el-table>

      <el-alert
        v-if="page?.truncatedColumns?.length"
        class="truncate-alert"
        type="info"
        :closable="false"
        show-icon
        :title="`以下列内容过长已截断：${page.truncatedColumns.join('、')}`"
      />
      <template #pagination>
        <MrrCursorPagination
          :page="pageIndex"
          :max-reachable-page="page ? maxReachablePage : undefined"
          :page-size="pageSize"
          :page-sizes="page ? pageSizes : []"
          :loading="loading"
          :can-previous="pageIndex > 1"
          :can-next="Boolean(page?.hasMore)"
          @jump="jumpToPage"
          @size-change="changePageSize"
        >
          <template #summary>
            本页 {{ rows.length }} 行 · 总行数 {{ estimateText }}<template v-if="page">
              · 耗时 {{ page.tookMs }} ms
            </template>
            <template v-if="errorMessage && page">
              · 保留上次成功结果
            </template>
            <template v-if="page?.omittedColumnCount">
              · {{ page.omittedColumnCount }} 列未展示
            </template>
          </template>
        </MrrCursorPagination>
      </template>
    </MrrDataTablePanel>
  </MrrPageShell>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: center;
}

.toolbar-label {
  font-size: var(--mrr-font-size-sm);
  font-weight: 500;
  color: var(--mrr-foreground);
}

/* 窄屏下不撑破工具条：最多 420px，容器更窄时跟随收缩。 */
.table-select {
  width: min(100%, 420px);
}

.table-directory {
  display: grid;
  gap: var(--mrr-space-3);
  margin-top: var(--mrr-space-4);
}

.directory-caption {
  margin: 0;
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-muted-foreground);
}

.directory-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: var(--mrr-space-3);
}

.directory-item {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
  padding: var(--mrr-space-4);
  color: var(--mrr-card-foreground);
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.directory-item:hover {
  border-color: var(--mrr-primary);
}

.directory-item:focus-visible {
  outline: 2px solid var(--mrr-primary);
  outline-offset: 2px;
}

.directory-name {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
}

.directory-name strong {
  font-size: var(--mrr-font-size-sm);
}

.directory-meta,
.directory-description {
  font-size: var(--mrr-font-size-xs);
  line-height: 1.5;
  color: var(--mrr-muted-foreground);
}

.option-name {
  margin-right: var(--mrr-space-2);
  color: var(--mrr-foreground);
}

.option-meta {
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.table-meta {
  margin-top: var(--mrr-space-3);
}

.state-alert {
  margin-top: var(--mrr-space-3);
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: baseline;
}

.meta-key {
  padding: 1px var(--mrr-space-2);
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  line-height: 18px;
  color: var(--mrr-foreground);
  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-sm);
}

.meta-desc {
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-muted-foreground);
}

.require-filter-alert {
  margin-top: var(--mrr-space-3);
}

.column-header {
  display: flex;
  flex-direction: column;
}

.column-label {
  font-weight: 500;
}

.column-name {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  font-weight: 400;
  color: var(--mrr-muted-foreground);
}

.filters {
  padding-bottom: 0;
  margin-top: var(--mrr-space-4);
  border-bottom: 0;
}

.retry-row {
  margin-top: var(--mrr-space-3);
}

/* Cursor pagination must keep the actual server result rather than invent a total page count. */

.empty-hint {
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-muted-foreground);
}

.truncate-alert {
  margin-top: var(--mrr-space-3);
}
</style>

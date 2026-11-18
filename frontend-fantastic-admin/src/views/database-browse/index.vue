<script setup lang="ts">
import type { DbBrowseColumnView, DbBrowsePage, DbBrowseTableSchema, DbBrowseTableSummary } from '@/api/modules/database-browse'
import { computed, onMounted, reactive, ref } from 'vue'
import { getBrowseRows, getBrowseSchema, getBrowseTables } from '@/api/modules/database-browse'

defineOptions({ name: 'DatabaseBrowsePage' })

const loading = ref(false)
const errorMessage = ref('')
const featureDisabled = ref(false)
const tables = ref<DbBrowseTableSummary[]>([])
const tableKey = ref('')
const schema = ref<DbBrowseTableSchema>()
const page = ref<DbBrowsePage>()
const filterValues = reactive<Record<string, string>>({})
const cursorStack = ref<string[]>([])
const pageIndex = ref(1)
const activeFilter = ref<string>()

const filterableColumns = computed<DbBrowseColumnView[]>(
  () => (schema.value?.columns ?? []).filter(column => column.filterable),
)
const rows = computed(() => page.value?.rows ?? [])
const columns = computed(() => page.value?.columns ?? [])

const estimateText = computed(() => formatRowCount(page.value?.estimatedRows))

const currentTable = computed(() => tables.value.find(item => item.tableKey === tableKey.value))

/** 单页行数跟随注册表上限（后端还会再校验一次，这里只是少发一次必然失败的请求）。 */
const pageSize = computed(() => Math.min(schema.value?.maxPageSize ?? 50, 100))

const emptyText = computed(() => {
  if (schema.value?.requireFilter && !activeFilter.value) { return '该表必须提供过滤条件后才会返回数据' }
  if (activeFilter.value) { return '没有符合条件的数据' }
  return '该表暂无数据'
})

/**
 * 行数展示。{@code null} 表示后端拿不到估算（从未 ANALYZE 且不是空表），
 * 这时显示「未统计」而不是「0」，避免把「不知道」显示成「没有数据」。
 */
function formatRowCount(value?: number | null): string {
  if (value == null) { return '未统计' }
  if (value === 0) { return '0 行' }
  return `约 ${value.toLocaleString('zh-CN')} 行`
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

/** 错误按业务码给可执行提示；3007 单独处理成「功能未开启」，不显示成「没有数据」。 */
function describeError(error: unknown): string {
  const code = (error as { code?: number } | undefined)?.code
  switch (code) {
    case 3003: return '该表必须提供过滤条件后才会返回数据'
    case 3004: return '查询超时，请缩小过滤范围后重试'
    case 3006: return '分页游标已失效，请从第一页重新查询'
    case 3007: return '该功能未开启，请联系管理员'
    default: return (error as Error | undefined)?.message || '请求失败，请稍后重试'
  }
}

async function loadTables() {
  loading.value = true
  errorMessage.value = ''
  try {
    tables.value = await getBrowseTables()
  }
  catch (error) {
    errorMessage.value = describeError(error)
    featureDisabled.value = (error as { code?: number } | undefined)?.code === 3007
  }
  finally {
    loading.value = false
  }
}

interface FetchTarget {
  page: number
  cursor?: string
  filter?: string
  stack: string[]
}

/**
 * 拉取一页数据。
 *
 * <p>页码、游标栈与生效中的过滤条件只在请求成功后提交：失败时界面保持上一次的
 * 一致状态，不会出现「页码显示第 2 页、表格还是第 1 页」。</p>
 */
async function fetchRows(target: FetchTarget) {
  if (!tableKey.value) { return }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getBrowseRows(tableKey.value, {
      page: target.page,
      size: pageSize.value,
      filter: target.filter,
      cursor: target.cursor,
    })
    page.value = result
    pageIndex.value = target.page
    cursorStack.value = target.stack
    activeFilter.value = target.filter
  }
  catch (error) {
    errorMessage.value = describeError(error)
    featureDisabled.value = (error as { code?: number } | undefined)?.code === 3007
  }
  finally {
    loading.value = false
  }
}

async function selectTable(key: string) {
  tableKey.value = key
  cursorStack.value = []
  pageIndex.value = 1
  activeFilter.value = undefined
  page.value = undefined
  schema.value = undefined
  errorMessage.value = ''
  Object.keys(filterValues).forEach(name => delete filterValues[name])
  loading.value = true
  try {
    schema.value = await getBrowseSchema(key)
  }
  catch (error) {
    errorMessage.value = describeError(error)
    loading.value = false
    return
  }
  loading.value = false
  await fetchRows({ page: 1, stack: [], filter: undefined })
}

async function search() {
  await fetchRows({ page: 1, stack: [], filter: buildFilter() })
}

async function resetFilters() {
  Object.keys(filterValues).forEach(name => delete filterValues[name])
  await search()
}

async function nextPage() {
  const cursor = page.value?.nextCursor
  if (!cursor) { return }
  await fetchRows({
    page: pageIndex.value + 1,
    cursor,
    filter: activeFilter.value,
    stack: [...cursorStack.value, cursor],
  })
}

async function prevPage() {
  if (pageIndex.value <= 1) { return }
  const stack = cursorStack.value.slice(0, -1)
  await fetchRows({
    page: pageIndex.value - 1,
    cursor: stack[stack.length - 1],
    filter: activeFilter.value,
    stack,
  })
}

onMounted(loadTables)
</script>

<template>
  <div class="db-browse-page">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-label">数据表</span>
        <el-select
          v-model="tableKey"
          class="table-select"
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
            <span class="option-meta">{{ item.tableKey }} · {{ formatRowCount(item.estimatedRows) }}</span>
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

      <div v-if="schema" class="filters">
        <div v-for="column in filterableColumns" :key="column.name" class="filter-item">
          <span class="filter-label">{{ column.label }}</span>
          <el-input
            v-model="filterValues[column.name]"
            :placeholder="`输入${column.label}`"
            clearable
            @keyup.enter="search"
          />
        </div>
        <div class="filter-actions">
          <el-button type="primary" :loading="loading" @click="search">
            查询
          </el-button>
          <el-button :disabled="loading" @click="resetFilters">
            重置
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card v-if="schema" shadow="never">
      <el-table v-loading="loading" :data="rows" stripe border class="result-table">
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
          <span class="empty-hint">{{ emptyText }}</span>
        </template>
      </el-table>

      <div class="pager">
        <span class="pager-note">
          第 {{ pageIndex }} 页 · 本页 {{ rows.length }} 行 · 总行数 {{ estimateText }} · 耗时 {{ page?.tookMs ?? 0 }} ms
          <template v-if="page?.omittedColumnCount">
            · {{ page.omittedColumnCount }} 列未展示
          </template>
        </span>
        <div class="pager-actions">
          <el-button :disabled="pageIndex <= 1 || loading" @click="prevPage">
            上一页
          </el-button>
          <el-button :disabled="!page?.hasMore || loading" @click="nextPage">
            下一页
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="page?.truncatedColumns?.length"
        class="truncate-alert"
        type="info"
        :closable="false"
        show-icon
        :title="`以下列内容过长已截断：${page.truncatedColumns.join('、')}`"
      />
    </el-card>
  </div>
</template>

<style scoped>
.db-browse-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.toolbar-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--mrr-foreground, #18181b);
}

.table-select {
  width: 420px;
}

.option-name {
  margin-right: 8px;
  color: var(--mrr-foreground, #18181b);
}

.option-meta {
  font-size: 12px;
  color: var(--mrr-muted-foreground, #5f5e5a);
}

.table-meta {
  margin-top: 12px;
}

.state-alert {
  margin-top: 12px;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
}

.meta-key {
  padding: 1px 6px;
  font-family: var(--mrr-font-mono);
  font-size: 12px;
  line-height: 18px;
  color: var(--mrr-foreground);
  background: var(--mrr-muted);
  border-radius: 4px;
}

.meta-desc {
  font-size: 13px;
  color: var(--mrr-muted-foreground, #5f5e5a);
}

.require-filter-alert {
  margin-top: 12px;
}

.column-header {
  display: flex;
  flex-direction: column;
}

.column-label {
  font-weight: 500;
}

.column-name {
  font-size: 11px;
  font-weight: 400;
  color: var(--mrr-muted-foreground, #5f5e5a);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  margin-top: 16px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
}

.filter-label {
  font-size: 12px;
  color: var(--mrr-muted-foreground, #5f5e5a);
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.pager {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.pager-note {
  font-size: 12px;
  color: var(--mrr-muted-foreground, #5f5e5a);
}

.pager-actions {
  display: flex;
  gap: 8px;
}

.empty-hint {
  font-size: 13px;
  color: var(--mrr-muted-foreground, #5f5e5a);
}

.truncate-alert {
  margin-top: 12px;
}
</style>

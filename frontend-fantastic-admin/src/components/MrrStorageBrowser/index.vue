<script setup lang="ts" generic="Entry extends StorageEntry">
import type { StorageEntry } from './presentation'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowUp, ChevronDown, ChevronRight, ChevronUp, Copy, File, FileArchive, FileImage, FileText, Folder, Info, LayoutGrid, List, RefreshCw, Search } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'
import { entryType, formatDate, formatFileSize, isImage } from './presentation'

const props = defineProps<{
  entries: readonly Entry[]
  location: (entry: Entry) => string
  breadcrumbs: { label: string, path: string }[]
  selected?: Entry | null
  pageLabel: string
  summary?: string
  sourceDetail?: { label: string, value: string }
  loading: boolean
  available: boolean
  canGoBack: boolean
  canGoUp: boolean
  canPreviousPage: boolean
  canNextPage: boolean
  locationLabel: string
  error?: string
  unavailableText: string
}>()

const emit = defineEmits<{
  back: []
  up: []
  refresh: []
  retry: []
  navigate: [path: string]
  select: [entry: Entry | undefined]
  open: [entry: Entry]
  previous: []
  next: []
}>()

const address = defineModel<string>('address', { required: true })
const filter = defineModel<string>('filter', { required: true })
const columns = [
  { key: 'name', label: '名称' },
  { key: 'type', label: '类型' },
  { key: 'size', label: '大小' },
  { key: 'modified', label: '修改时间' },
] as const
type SortKey = typeof columns[number]['key']

const names = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' })
const archiveTypes = new Set(['ZIP', '7Z', 'RAR', 'GZ', 'TAR'])
const documentTypes = new Set(['PDF', 'TXT', 'CSV', 'DOC', 'DOCX', 'XLS', 'XLSX'])
const mode = ref<'icons' | 'details'>('details')
const sort = ref<{ key: SortKey, ascending: boolean }>({ key: 'name', ascending: true })
const filePane = ref<HTMLElement>()
const keyword = computed(() => filter.value.trim().toLocaleLowerCase())
const visibleEntries = computed(() => {
  const entries = keyword.value
    ? props.entries.filter(entry => entry.name.toLocaleLowerCase().includes(keyword.value))
    : [...props.entries]
  const { key, ascending } = sort.value
  return entries.sort((a, b) => {
    if (a.directory !== b.directory) {
      return Number(b.directory) - Number(a.directory)
    }
    const order = compareColumn(key, a, b) || names.compare(a.name, b.name)
    return ascending ? order : -order
  })
})
const selected = computed(() => props.selected && visibleEntries.value.includes(props.selected) ? props.selected : undefined)

function compareColumn(key: SortKey, a: StorageEntry, b: StorageEntry) {
  switch (key) {
    case 'type':
      return names.compare(entryType(a), entryType(b))
    case 'size':
      return a.size - b.size
    case 'modified':
      return (Date.parse(a.lastModified ?? '') || 0) - (Date.parse(b.lastModified ?? '') || 0)
    default:
      return names.compare(a.name, b.name)
  }
}

function sortBy(key: SortKey) {
  sort.value = { key, ascending: sort.value.key !== key || !sort.value.ascending }
}

function icon(entry: StorageEntry) {
  if (entry.directory) {
    return Folder
  }
  if (isImage(entry)) {
    return FileImage
  }
  const type = entryType(entry)
  if (archiveTypes.has(type)) {
    return FileArchive
  }
  return documentTypes.has(type) ? FileText : File
}

function onNavigationKey(event: KeyboardEvent) {
  if (!event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return
  }
  if (event.key === 'ArrowLeft' && props.canGoBack && !props.loading) {
    event.preventDefault()
    emit('back')
  }
  if (event.key === 'ArrowUp' && props.canGoUp) {
    event.preventDefault()
    emit('up')
  }
}

function onEntryKey(event: KeyboardEvent, index: number) {
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return
  }
  const grid = mode.value === 'icons'
  const keys = grid ? ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'] : ['ArrowUp', 'ArrowDown', 'Home', 'End']
  if (!keys.includes(event.key)) {
    return
  }
  event.preventDefault()
  const parent = (event.currentTarget as HTMLElement).parentElement
  const stride = grid && parent ? getComputedStyle(parent).gridTemplateColumns.split(' ').length : 1
  const lastIndex = visibleEntries.value.length - 1
  const offsets: Record<string, number> = { ArrowUp: -stride, ArrowDown: stride, ArrowLeft: -1, ArrowRight: 1 }
  const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? lastIndex : index + offsets[event.key]
  if (nextIndex < 0 || nextIndex > lastIndex || nextIndex === index) {
    return
  }
  emit('select', visibleEntries.value[nextIndex])
  filePane.value?.querySelectorAll<HTMLButtonElement>('button[data-file-entry]')[nextIndex]?.focus()
}

function selectRow(entry: Entry, event: MouseEvent) {
  emit('select', entry)
  const row = event.currentTarget as HTMLElement
  row.querySelector('button')?.focus()
}

watch(() => props.entries, async () => {
  const pane = filePane.value
  if (!pane?.contains(document.activeElement)) {
    return
  }
  await nextTick()
  if (document.activeElement === document.body) {
    const target = pane.querySelector<HTMLElement>('button[data-file-entry]') ?? pane
    target.focus()
  }
})

async function copyLocation(entry: Entry) {
  try {
    await navigator.clipboard.writeText(props.location(entry))
    ElMessage.success('路径已复制')
  }
  catch {
    ElMessage.error('复制失败，请选中路径后手动复制')
  }
}
</script>

<template>
  <section class="storage-browser" aria-label="文件浏览工作区">
    <div class="source-bar">
      <div class="source-identity">
        <slot name="source" />
        <el-tooltip v-if="sourceDetail" :content="sourceDetail.value" placement="top">
          <button type="button" class="source-info" :aria-label="sourceDetail.label">
            <Info :size="16" aria-hidden="true" />
          </button>
        </el-tooltip>
      </div>
      <el-button :icon="RefreshCw" :loading="loading" @click="emit('refresh')">
        刷新
      </el-button>
    </div>

    <div class="browser-body">
      <div class="navigation-bar">
        <form class="location-bar" @submit.prevent="emit('navigate', address)">
          <el-button-group>
            <el-button :icon="ArrowLeft" native-type="button" aria-label="返回" title="返回（Alt + ←）" :disabled="loading || !canGoBack" @click="emit('back')" />
            <el-button :icon="ArrowUp" native-type="button" aria-label="上一级" title="上一级（Alt + ↑）" :disabled="!canGoUp" @click="emit('up')" />
          </el-button-group>
          <el-input v-model="address" :disabled="!available" aria-label="目录路径" placeholder="输入目录路径">
            <template #prefix>
              <el-icon><Folder /></el-icon>
            </template>
          </el-input>
          <el-button native-type="submit" :disabled="!available">
            前往
          </el-button>
        </form>
        <el-input v-model="filter" :disabled="!available" class="page-filter" clearable aria-label="筛选当前页" placeholder="筛选当前页">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div class="listing-toolbar">
        <nav aria-label="当前位置" class="breadcrumbs">
          <template v-for="(crumb, index) in breadcrumbs" :key="crumb.path">
            <ChevronRight v-if="index" class="crumb-divider" aria-hidden="true" />
            <span v-if="index === breadcrumbs.length - 1" aria-current="location">{{ crumb.label }}</span>
            <button v-else type="button" class="crumb-button" @click="emit('navigate', crumb.path)">
              {{ crumb.label }}
            </button>
          </template>
        </nav>
        <div class="listing-controls">
          <el-button-group aria-label="显示方式">
            <el-button :icon="LayoutGrid" aria-label="图标视图" title="图标视图" :aria-pressed="mode === 'icons'" :type="mode === 'icons' ? 'primary' : 'default'" plain @click="mode = 'icons'">
              图标
            </el-button>
            <el-button :icon="List" title="详细列表" aria-label="列表视图" :aria-pressed="mode === 'details'" :type="mode === 'details' ? 'primary' : 'default'" plain @click="mode = 'details'">
              详细列表
            </el-button>
          </el-button-group>
        </div>
      </div>

      <div v-if="error" class="browser-notice" role="status">
        <span>{{ error }}</span>
        <el-button size="small" :disabled="loading" @click="emit('retry')">
          重试
        </el-button>
      </div>

      <div class="explorer-layout">
        <section ref="filePane" v-loading="loading" class="file-pane" tabindex="-1" aria-label="目录内容" :aria-busy="loading" @keydown="onNavigationKey">
          <div v-if="!visibleEntries.length" class="empty-pane">
            <el-empty v-if="!loading && !error" :image-size="64" :description="!available ? unavailableText : keyword ? '当前页没有匹配的条目' : '此目录为空'">
              <el-button v-if="available && keyword" @click="filter = ''">
                清除筛选
              </el-button>
            </el-empty>
          </div>
          <div v-else-if="mode === 'icons'" class="file-grid">
            <button
              v-for="(entry, index) in visibleEntries"
              :key="location(entry)"
              type="button"
              class="file-tile"
              :class="{ selected: selected === entry }"
              :aria-pressed="selected === entry"
              :tabindex="entry === (selected ?? visibleEntries[0]) ? 0 : -1"
              data-file-entry
              @click="emit('select', entry)"
              @dblclick="emit('open', entry)"
              @keydown="onEntryKey($event, index)"
              @keydown.enter.prevent="emit('open', entry)"
            >
              <component :is="icon(entry)" class="entry-icon" aria-hidden="true" />
              <span class="file-tile-name" :title="entry.name">{{ entry.name }}</span>
            </button>
          </div>
          <table v-else class="file-table" aria-label="文件列表">
            <thead>
              <tr>
                <th
                  v-for="column in columns"
                  :key="column.key"
                  scope="col"
                  :class="`column-${column.key}`"
                  :aria-sort="sort.key === column.key ? (sort.ascending ? 'ascending' : 'descending') : undefined"
                >
                  <button type="button" class="sort-button" title="按当前页排序" @click="sortBy(column.key)">
                    {{ column.label }}
                    <component :is="sort.ascending ? ChevronUp : ChevronDown" v-if="sort.key === column.key" class="sort-indicator" aria-hidden="true" />
                  </button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(entry, index) in visibleEntries"
                :key="location(entry)"
                :class="{ selected: selected === entry }"
                @click="selectRow(entry, $event)"
                @dblclick="emit('open', entry)"
              >
                <td>
                  <button
                    type="button"
                    class="entry-name"
                    :title="entry.name"
                    :aria-pressed="selected === entry"
                    :tabindex="entry === (selected ?? visibleEntries[0]) ? 0 : -1"
                    data-file-entry
                    @click.stop="emit('select', entry)"
                    @keydown="onEntryKey($event, index)"
                    @keydown.enter.prevent="emit('open', entry)"
                  >
                    <component :is="icon(entry)" class="entry-icon" aria-hidden="true" />
                    <span>{{ entry.name }}</span>
                  </button>
                </td>
                <td class="column-type">
                  {{ entryType(entry) }}
                </td>
                <td>{{ entry.directory ? '—' : formatFileSize(entry.size) }}</td>
                <td class="column-modified">
                  {{ formatDate(entry.lastModified) }}
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <aside class="details-pane" aria-label="文件详情">
          <div class="details-heading">
            详情
          </div>
          <template v-if="selected">
            <h3 class="selection-name">
              <component :is="icon(selected)" class="entry-icon" aria-hidden="true" />
              {{ selected.name }}
            </h3>
            <slot v-if="!selected.directory" name="preview" :entry="selected" />
            <dl>
              <dt>类型</dt>
              <dd>{{ entryType(selected) }}</dd>
              <dt v-if="!selected.directory">
                大小
              </dt>
              <dd v-if="!selected.directory">
                {{ formatFileSize(selected.size) }}
              </dd>
              <dt>修改时间</dt>
              <dd>{{ formatDate(selected.lastModified) }}</dd>
              <slot name="metadata" :entry="selected" />
            </dl>
            <div class="entry-location">
              <span>{{ locationLabel }}</span>
              <code>{{ location(selected) }}</code>
            </div>
            <div class="details-actions">
              <slot name="actions" :entry="selected" />
              <el-button :icon="Copy" @click="copyLocation(selected)">
                复制路径
              </el-button>
            </div>
          </template>
          <p v-else class="details-hint">
            选择文件或目录查看详情
          </p>
        </aside>
      </div>

      <div class="browser-footer">
        <div class="page-statistics" role="status">
          <strong>{{ pageLabel }}</strong>
          <span v-if="keyword">匹配 {{ visibleEntries.length }} / {{ entries.length }} 项</span>
          <span v-else-if="summary">{{ summary }}</span>
          <span v-if="selected" class="selection-status">已选择 1 项</span>
        </div>
        <el-button-group aria-label="分页">
          <el-button :disabled="loading || !canPreviousPage" @click="emit('previous')">
            上一页
          </el-button>
          <el-button :disabled="loading || !canNextPage" @click="emit('next')">
            下一页
          </el-button>
        </el-button-group>
      </div>
    </div>
  </section>
</template>

<style scoped>
.storage-browser {
  min-width: 0;
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
}

.source-bar,
.source-identity,
.navigation-bar,
.location-bar,
.listing-toolbar,
.listing-controls,
.browser-notice,
.browser-footer,
.page-statistics,
.details-actions,
.selection-name {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.source-bar,
.listing-toolbar,
.browser-notice,
.browser-footer {
  justify-content: space-between;
}

.source-bar {
  min-height: 56px;
  padding: 10px 16px;
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-xl) var(--mrr-radius-xl) 0 0;
}

.source-identity {
  flex-wrap: wrap;
  overflow-wrap: anywhere;
}

.source-info {
  display: inline-flex;
  padding: 4px;
  color: var(--mrr-muted-foreground);
  cursor: help;
  background: none;
  border: 0;
}

.source-info:focus-visible {
  outline: 2px solid var(--mrr-ring);
}

.browser-body {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 16px;
}

.location-bar {
  flex: 1;
}

.location-bar > .el-input {
  flex: 1;
  min-width: 0;
}

.location-bar > .el-button-group,
.listing-controls > .el-button-group,
.browser-footer > .el-button-group {
  display: inline-flex;
  flex: none;
}

.breadcrumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  align-items: center;
  min-width: 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.breadcrumbs [aria-current] {
  font-weight: 600;
}

.crumb-divider {
  flex: none;
  width: 14px;
  height: 14px;
}

.crumb-divider,
.details-hint,
.page-statistics span,
.entry-location > span,
.details-pane dt {
  color: var(--mrr-muted-foreground);
}

.crumb-button,
.file-tile,
.entry-name,
.sort-button {
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}

.crumb-button {
  color: var(--mrr-primary);
  overflow-wrap: anywhere;
}

.crumb-button:hover {
  text-decoration: underline;
}

.crumb-button:focus-visible,
.file-tile:focus-visible,
.file-pane:focus-visible,
.entry-name:focus-visible,
.sort-button:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: -2px;
}

.page-filter {
  width: 214px;
}

.browser-notice {
  padding: 8px 12px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: var(--mrr-radius-md);
}

.explorer-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 28%);
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.file-pane {
  min-width: 0;
  min-height: 340px;
}

.empty-pane {
  display: grid;
  place-items: center;
  min-height: 260px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(106px, 1fr));
  gap: 4px;
  padding: 12px;
}

.file-tile {
  display: grid;
  gap: 8px;
  align-content: start;
  justify-items: center;
  min-width: 0;
  min-height: 86px;
  padding: 10px 8px;
  text-align: center;
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
}

.file-tile:hover,
.file-table tbody tr:hover {
  background: var(--el-fill-color-light);
}

.file-tile.selected,
.file-table tbody tr.selected {
  background: var(--el-color-primary-light-9);
}

.file-tile.selected {
  border-color: var(--el-color-primary-light-5);
}

.file-tile-name,
.entry-name span {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entry-icon {
  flex: none;
  width: 20px;
  height: 20px;
  color: var(--mrr-muted-foreground);
  fill: var(--el-fill-color);
  stroke-width: 1.5;
}

.file-tile .entry-icon {
  width: 38px;
  height: 38px;
}

.entry-icon.lucide-folder {
  color: var(--el-color-warning-dark-2);
  fill: var(--el-color-warning-light-5);
}

.entry-icon.lucide-file-image {
  color: var(--el-color-primary-dark-2);
  fill: var(--el-color-primary-light-8);
}

.entry-icon.lucide-file-archive {
  color: var(--el-color-success-dark-2);
  fill: var(--el-color-success-light-8);
}

.entry-icon.lucide-file-text {
  fill: var(--el-fill-color-light);
}

.file-table {
  width: 100%;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  table-layout: fixed;
  border-spacing: 0;
  border-collapse: separate;
}

.file-table th,
.file-table td {
  padding: 0 12px;
  text-align: left;
}

.file-table th {
  padding: 0;
  color: var(--mrr-muted-foreground);
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--mrr-border);
}

.sort-button {
  display: flex;
  gap: 4px;
  align-items: center;
  width: 100%;
  min-height: 36px;
  padding: 0 12px;
  white-space: nowrap;
}

.sort-button:hover {
  background: var(--el-fill-color-light);
}

.sort-indicator {
  flex: none;
  width: 14px;
  height: 14px;
  color: var(--mrr-primary);
}

.file-table tbody tr {
  cursor: pointer;
}

.file-table th:nth-child(2),
.file-table th:nth-child(3) {
  width: 74px;
}

.file-table th:last-child {
  width: 152px;
}

.entry-name {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 36px;
}

.details-pane {
  min-width: 0;
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border-left: 1px solid var(--mrr-border);
}

.details-heading {
  font-size: 13px;
  font-weight: 600;
}

.details-hint {
  margin: 12px 0 0;
  font-size: 13px;
}

.selection-name {
  margin: 12px 0;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.selection-name .entry-icon {
  width: 28px;
  height: 28px;
}

.details-pane dl {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.details-pane :deep(dd) {
  margin: 0;
  overflow-wrap: anywhere;
}

.entry-location {
  display: grid;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
}

.entry-location code {
  overflow-wrap: anywhere;
  user-select: text;
}

.details-actions {
  flex-wrap: wrap;
  margin-top: 12px;
}

.details-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.browser-footer,
.page-statistics {
  flex-wrap: wrap;
  font-size: 13px;
}

.selection-status {
  padding-left: 8px;
  border-left: 1px solid var(--mrr-border);
}

@media (max-width: 1100px) {
  .explorer-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .details-pane {
    grid-row: 1;
    border-bottom: 1px solid var(--mrr-border);
    border-left: 0;
  }
}

@media (max-width: 700px) {
  .browser-body {
    padding: 10px;
  }

  .listing-toolbar,
  .navigation-bar {
    flex-wrap: wrap;
  }

  .location-bar {
    flex-basis: 100%;
  }

  .page-filter {
    width: 100%;
    min-width: 0;
  }

  .column-type,
  .column-modified {
    display: none;
  }
}
</style>

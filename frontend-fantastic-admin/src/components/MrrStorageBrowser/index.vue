<script setup lang="ts" generic="Entry extends StorageEntry">
import type { StorageEntry } from './presentation'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowUp, Copy, File, Folder, Image, Info, LayoutGrid, List, RefreshCw, Search } from 'lucide-vue-next'
import { computed, ref } from 'vue'
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
const mode = ref<'icons' | 'details'>('icons')
const keyword = computed(() => filter.value.trim().toLocaleLowerCase())
const visibleEntries = computed(() => keyword.value
  ? props.entries.filter(entry => entry.name.toLocaleLowerCase().includes(keyword.value))
  : props.entries)
const selected = computed(() => props.selected && visibleEntries.value.includes(props.selected) ? props.selected : undefined)

function icon(entry: StorageEntry) {
  return entry.directory ? Folder : isImage(entry) ? Image : File
}

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
      <form class="location-bar" @submit.prevent="emit('navigate', address)">
        <el-button-group>
          <el-button :icon="ArrowLeft" native-type="button" aria-label="返回" title="返回" :disabled="loading || !canGoBack" @click="emit('back')" />
          <el-button :icon="ArrowUp" native-type="button" aria-label="上一级" title="上一级" :disabled="!canGoUp" @click="emit('up')" />
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

      <div class="listing-toolbar">
        <nav aria-label="当前位置" class="breadcrumbs">
          <template v-for="(crumb, index) in breadcrumbs" :key="crumb.path">
            <span v-if="index" class="crumb-divider" aria-hidden="true">/</span>
            <span v-if="index === breadcrumbs.length - 1" aria-current="location">{{ crumb.label }}</span>
            <button v-else type="button" class="crumb-button" @click="emit('navigate', crumb.path)">
              {{ crumb.label }}
            </button>
          </template>
        </nav>
        <div class="listing-controls">
          <el-input v-model="filter" :disabled="!available" class="page-filter" clearable aria-label="筛选当前页" placeholder="筛选当前页">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button-group aria-label="显示方式">
            <el-button :icon="LayoutGrid" aria-label="图标视图" title="图标视图" :aria-pressed="mode === 'icons'" :type="mode === 'icons' ? 'primary' : 'default'" @click="mode = 'icons'" />
            <el-button :icon="List" aria-label="列表视图" title="列表视图" :aria-pressed="mode === 'details'" :type="mode === 'details' ? 'primary' : 'default'" @click="mode = 'details'" />
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
        <section v-loading="loading" class="file-pane" aria-label="目录内容" :aria-busy="loading">
          <div v-if="!visibleEntries.length" class="empty-pane">
            <el-empty v-if="!loading && !error" :image-size="64" :description="!available ? unavailableText : keyword ? '当前页没有匹配的条目' : '此目录为空'">
              <el-button v-if="available && keyword" @click="filter = ''">
                清除筛选
              </el-button>
            </el-empty>
          </div>
          <div v-else-if="mode === 'icons'" class="file-grid">
            <button
              v-for="entry in visibleEntries"
              :key="location(entry)"
              type="button"
              class="file-tile"
              :class="{ selected: selected === entry }"
              :aria-pressed="selected === entry"
              @click="emit('select', entry)"
              @dblclick="emit('open', entry)"
              @keydown.enter.prevent="emit('open', entry)"
            >
              <component :is="icon(entry)" class="entry-icon" :class="{ folder: entry.directory, image: isImage(entry) }" aria-hidden="true" />
              <span class="file-tile-name" :title="entry.name">{{ entry.name }}</span>
              <small>{{ entry.directory ? '文件夹' : formatFileSize(entry.size) }}</small>
            </button>
          </div>
          <table v-else class="file-table" aria-label="文件列表">
            <thead>
              <tr>
                <th scope="col">
                  名称
                </th>
                <th scope="col" class="entry-type">
                  类型
                </th>
                <th scope="col">
                  大小
                </th>
                <th scope="col" class="entry-date">
                  修改时间
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in visibleEntries" :key="location(entry)" :class="{ selected: selected === entry }" @click="emit('select', entry)">
                <td>
                  <button
                    type="button"
                    class="entry-name"
                    :title="entry.name"
                    :aria-pressed="selected === entry"
                    @click.stop="emit('select', entry)"
                    @dblclick="emit('open', entry)"
                    @keydown.enter.prevent="emit('open', entry)"
                  >
                    <component :is="icon(entry)" class="entry-icon" :class="{ folder: entry.directory, image: isImage(entry) }" aria-hidden="true" />
                    <span>{{ entry.name }}</span>
                  </button>
                </td>
                <td class="entry-type">
                  {{ entryType(entry) }}
                </td>
                <td>{{ entry.directory ? '—' : formatFileSize(entry.size) }}</td>
                <td class="entry-date">
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
              <component :is="icon(selected)" class="entry-icon" :class="{ folder: selected.directory, image: isImage(selected) }" aria-hidden="true" />
              {{ selected.name }}
            </h3>
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
  gap: 4px 8px;
  align-items: baseline;
  min-width: 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.crumb-divider,
.details-hint,
.page-statistics span,
.file-tile small,
.entry-location > span,
.details-pane dt {
  color: var(--mrr-muted-foreground);
}

.crumb-button,
.file-tile,
.entry-name {
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
.entry-name:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: -2px;
}

.page-filter {
  width: 180px;
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
  grid-template-columns: repeat(auto-fill, minmax(116px, 1fr));
  gap: 8px;
  padding: 12px;
}

.file-tile {
  display: grid;
  gap: 6px;
  justify-items: center;
  min-width: 0;
  min-height: 92px;
  padding: 10px 6px;
  text-align: center;
  border: 1px solid var(--mrr-border);
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
  width: 18px;
  height: 18px;
  color: var(--mrr-muted-foreground);
}

.file-tile .entry-icon {
  width: 24px;
  height: 24px;
}

.entry-icon.folder {
  color: var(--el-color-warning);
}

.entry-icon.image {
  color: var(--mrr-primary);
}

.file-table {
  width: 100%;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  table-layout: fixed;
  border-collapse: collapse;
}

.file-table th,
.file-table td {
  padding: 0 10px;
  text-align: left;
  border-bottom: 1px solid var(--mrr-border);
}

.file-table th {
  height: 40px;
  color: var(--mrr-muted-foreground);
  background: var(--el-fill-color-lighter);
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
  min-height: 40px;
}

.details-pane {
  min-width: 0;
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border-left: 1px solid var(--mrr-border);
  border-radius: 0 var(--mrr-radius-lg) var(--mrr-radius-lg) 0;
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

@media (max-width: 1100px) {
  .explorer-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .details-pane {
    grid-row: 1;
    border-bottom: 1px solid var(--mrr-border);
    border-left: 0;
    border-radius: var(--mrr-radius-lg) var(--mrr-radius-lg) 0 0;
  }
}

@media (max-width: 700px) {
  .browser-body {
    padding: 10px;
  }

  .listing-toolbar {
    flex-wrap: wrap;
  }

  .listing-controls {
    justify-content: space-between;
    width: 100%;
  }

  .page-filter {
    flex: 1;
    width: auto;
    min-width: 0;
  }

  .entry-type,
  .entry-date {
    display: none;
  }
}
</style>

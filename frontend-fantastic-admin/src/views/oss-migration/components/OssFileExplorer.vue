<script setup lang="ts">
import type { OssBrowserEntry, OssBrowserPage } from '@/api/modules/oss'
import {
  ArrowLeft,
  Document,
  Folder,
  Grid,
  List,
  Picture,
  Refresh,
  Search,
  Top,
  View,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { browseOssObjects, getOssBrowserUrl } from '@/api/modules/oss'

defineOptions({ name: 'OssFileExplorer' })

type ViewMode = 'icons' | 'details'

interface BreadcrumbItem {
  label: string
  prefix: string
}

const DEFAULT_ROOT_PREFIX = 'medical-records/'
const MAX_KEYS = 200
const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'bmp', 'tif', 'tiff', 'webp'])

const loading = ref(false)
const previewLoading = ref(false)
const page = ref<OssBrowserPage>()
const currentPrefix = ref(DEFAULT_ROOT_PREFIX)
const addressInput = ref(DEFAULT_ROOT_PREFIX)
const localFilter = ref('')
const viewMode = ref<ViewMode>('icons')
const selectedEntry = ref<OssBrowserEntry>()
const previewEntry = ref<OssBrowserEntry>()
const previewUrl = ref('')
const previewVisible = ref(false)
const currentContinuationToken = ref<string>()
const pageTokenHistory = ref<Array<string | undefined>>([])
const folderHistory = ref<string[]>([])

const rootPrefix = computed(() => page.value?.rootPrefix || DEFAULT_ROOT_PREFIX)
const entries = computed(() => page.value?.entries ?? [])
const visibleEntries = computed(() => {
  const keyword = localFilter.value.trim().toLocaleLowerCase()
  if (!keyword) {
    return entries.value
  }
  return entries.value.filter(entry => entry.name.toLocaleLowerCase().includes(keyword))
})
const directoryEntries = computed(() => entries.value.filter(entry => entry.directory))
const canGoBack = computed(() => folderHistory.value.length > 0)
const canGoUp = computed(() => currentPrefix.value !== rootPrefix.value)
const canPreviousPage = computed(() => pageTokenHistory.value.length > 0)
const canNextPage = computed(() => Boolean(page.value?.truncated && page.value.nextContinuationToken))
const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const root = rootPrefix.value
  const rootLabel = root.replace(/\/$/, '') || 'medical-records'
  const relative = currentPrefix.value.startsWith(root)
    ? currentPrefix.value.slice(root.length).replace(/\/$/, '')
    : ''
  const result: BreadcrumbItem[] = [{ label: rootLabel, prefix: root }]
  if (!relative) {
    return result
  }
  let prefix = root
  for (const segment of relative.split('/').filter(Boolean)) {
    prefix += `${segment}/`
    result.push({ label: segment, prefix })
  }
  return result
})
const selectedIsImage = computed(() => isImage(selectedEntry.value))

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function formatFileSize(value?: number | null) {
  const bytes = Number(value ?? 0)
  if (bytes <= 0) {
    return bytes === 0 ? '0 B' : '-'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  const precision = unitIndex === 0 ? 0 : size >= 100 ? 0 : size >= 10 ? 1 : 2
  return `${size.toFixed(precision)} ${units[unitIndex]}`
}

function formatDate(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN')
}

function fileExtension(entry?: OssBrowserEntry) {
  if (!entry || entry.directory) {
    return ''
  }
  const index = entry.name.lastIndexOf('.')
  return index >= 0 ? entry.name.slice(index + 1).toLocaleLowerCase() : ''
}

function isImage(entry?: OssBrowserEntry) {
  return Boolean(entry && !entry.directory && IMAGE_EXTENSIONS.has(fileExtension(entry)))
}

function entryIcon(entry: OssBrowserEntry) {
  if (entry.directory) {
    return Folder
  }
  return isImage(entry) ? Picture : Document
}

function normalizeRequestedPrefix(value: string) {
  let normalized = value.trim()
  if (!normalized) {
    return rootPrefix.value
  }
  if (!normalized.startsWith(rootPrefix.value)) {
    normalized = `${rootPrefix.value}${normalized.replace(/^\/+/, '')}`
  }
  if (!normalized.endsWith('/')) {
    normalized += '/'
  }
  return normalized
}

async function loadDirectory(
  prefix: string,
  continuationToken?: string,
  options: { rememberFolder?: boolean, resetPageHistory?: boolean } = {},
) {
  const normalizedPrefix = normalizeRequestedPrefix(prefix)
  if (options.rememberFolder && currentPrefix.value !== normalizedPrefix) {
    folderHistory.value.push(currentPrefix.value)
  }
  if (options.resetPageHistory !== false) {
    pageTokenHistory.value = []
  }

  loading.value = true
  selectedEntry.value = undefined
  localFilter.value = ''
  try {
    const response = await browseOssObjects({
      prefix: normalizedPrefix,
      continuationToken,
      maxKeys: MAX_KEYS,
    })
    if (!response.data) {
      throw new Error('OSS 目录响应为空')
    }
    page.value = response.data
    currentPrefix.value = response.data.prefix
    addressInput.value = response.data.prefix
    currentContinuationToken.value = continuationToken
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '读取 OSS 目录失败'))
  }
  finally {
    loading.value = false
  }
}

function openFolder(entry: OssBrowserEntry) {
  if (!entry.directory) {
    return
  }
  void loadDirectory(entry.key, undefined, { rememberFolder: true })
}

function selectEntry(entry?: OssBrowserEntry) {
  selectedEntry.value = entry
}

function handleEntryDoubleClick(entry: OssBrowserEntry) {
  if (entry.directory) {
    openFolder(entry)
    return
  }
  void openFile(entry)
}

function goBack() {
  const previous = folderHistory.value.pop()
  if (previous) {
    void loadDirectory(previous)
  }
}

function parentPrefix(prefix: string) {
  const root = rootPrefix.value
  if (prefix === root) {
    return root
  }
  const withoutTrailingSlash = prefix.replace(/\/$/, '')
  const lastSlash = withoutTrailingSlash.lastIndexOf('/')
  const parent = lastSlash >= 0 ? withoutTrailingSlash.slice(0, lastSlash + 1) : root
  return parent.startsWith(root) ? parent : root
}

function goUp() {
  if (!canGoUp.value) {
    return
  }
  void loadDirectory(parentPrefix(currentPrefix.value), undefined, { rememberFolder: true })
}

function jumpToAddress() {
  void loadDirectory(addressInput.value, undefined, { rememberFolder: true })
}

function jumpToBreadcrumb(item: BreadcrumbItem) {
  if (item.prefix === currentPrefix.value) {
    return
  }
  void loadDirectory(item.prefix, undefined, { rememberFolder: true })
}

function nextPage() {
  const nextToken = page.value?.nextContinuationToken
  if (!nextToken) {
    return
  }
  pageTokenHistory.value.push(currentContinuationToken.value)
  void loadDirectory(currentPrefix.value, nextToken, { resetPageHistory: false })
}

function previousPage() {
  if (!pageTokenHistory.value.length) {
    return
  }
  const previousToken = pageTokenHistory.value.pop()
  void loadDirectory(currentPrefix.value, previousToken, { resetPageHistory: false })
}

async function requestSignedUrl(entry: OssBrowserEntry) {
  const response = await getOssBrowserUrl(entry.key)
  const signedUrl = response.data?.ossUrl
  if (!signedUrl) {
    throw new Error('未获取到 OSS 文件地址')
  }
  return signedUrl
}

async function openFile(entry = selectedEntry.value) {
  if (!entry || entry.directory) {
    return
  }
  const target = window.open('about:blank', '_blank')
  try {
    const signedUrl = await requestSignedUrl(entry)
    if (target) {
      target.opener = null
      target.location.href = signedUrl
    }
    else {
      window.open(signedUrl, '_blank', 'noopener,noreferrer')
    }
  }
  catch (error: unknown) {
    target?.close()
    ElMessage.error(errorMessage(error, '打开 OSS 文件失败'))
  }
}

async function previewFile(entry = selectedEntry.value) {
  if (!entry || entry.directory || !isImage(entry)) {
    return
  }
  previewEntry.value = entry
  previewUrl.value = ''
  previewVisible.value = true
  previewLoading.value = true
  try {
    previewUrl.value = await requestSignedUrl(entry)
  }
  catch (error: unknown) {
    previewVisible.value = false
    ElMessage.error(errorMessage(error, '加载 OSS 图片预览失败'))
  }
  finally {
    previewLoading.value = false
  }
}

onMounted(() => {
  void loadDirectory(DEFAULT_ROOT_PREFIX)
})
</script>

<template>
  <el-card shadow="never" class="oss-explorer-card">
    <template #header>
      <div class="explorer-title-row">
        <div>
          <strong>OSS 文件资源管理器</strong>
          <p>以只读方式浏览 medical-records 下的目录和文件，不执行全桶扫描或精确总数统计。</p>
        </div>
        <div class="explorer-status">
          <el-tag :type="page?.configured ? 'success' : 'danger'">
            {{ page?.configured ? 'OSS 已连接' : 'OSS 未配置' }}
          </el-tag>
          <el-button
            :icon="Refresh"
            :loading="loading"
            @click="loadDirectory(currentPrefix, currentContinuationToken, { resetPageHistory: false })"
          >
            刷新
          </el-button>
        </div>
      </div>
    </template>

    <div class="repository-summary">
      <div class="summary-item">
        <span>Bucket</span>
        <strong>{{ page?.bucket || '-' }}</strong>
      </div>
      <div class="summary-item">
        <span>Endpoint</span>
        <strong>{{ page?.endpoint || '-' }}</strong>
      </div>
      <div class="summary-item">
        <span>当前页目录</span>
        <strong>{{ page?.loadedDirectories ?? 0 }}</strong>
      </div>
      <div class="summary-item">
        <span>当前页文件</span>
        <strong>{{ page?.loadedFiles ?? 0 }}</strong>
      </div>
      <div class="summary-item">
        <span>当前页大小</span>
        <strong>{{ formatFileSize(page?.loadedBytes) }}</strong>
      </div>
    </div>

    <div class="explorer-toolbar">
      <div class="navigation-actions">
        <el-button :icon="ArrowLeft" circle :disabled="!canGoBack" title="返回" @click="goBack" />
        <el-button :icon="Top" circle :disabled="!canGoUp" title="上一级" @click="goUp" />
      </div>

      <div class="address-bar">
        <el-input v-model="addressInput" @keyup.enter="jumpToAddress">
          <template #prefix>
            <el-icon><Folder /></el-icon>
          </template>
          <template #append>
            <el-button :icon="Search" @click="jumpToAddress" />
          </template>
        </el-input>
      </div>

      <el-input v-model="localFilter" clearable class="local-search" placeholder="筛选当前页文件">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-button-group>
        <el-button :type="viewMode === 'icons' ? 'primary' : 'default'" :icon="Grid" @click="viewMode = 'icons'" />
        <el-button :type="viewMode === 'details' ? 'primary' : 'default'" :icon="List" @click="viewMode = 'details'" />
      </el-button-group>
    </div>

    <div class="breadcrumb-row">
      <el-breadcrumb separator=">">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.prefix">
          <button type="button" class="breadcrumb-button" @click="jumpToBreadcrumb(item)">
            {{ item.label }}
          </button>
        </el-breadcrumb-item>
      </el-breadcrumb>
      <span v-if="page?.truncated" class="pagination-hint">当前目录还有更多对象</span>
    </div>

    <div class="explorer-layout">
      <aside class="folder-sidebar">
        <button
          type="button"
          class="sidebar-item root"
          @click="loadDirectory(rootPrefix, undefined, { rememberFolder: true })"
        >
          <el-icon><Folder /></el-icon>
          <span>{{ rootPrefix.replace(/\/$/, '') }}</span>
        </button>
        <div class="sidebar-section-title">
          当前目录
        </div>
        <button
          v-for="entry in directoryEntries"
          :key="entry.key"
          type="button"
          class="sidebar-item"
          @click="openFolder(entry)"
        >
          <el-icon><Folder /></el-icon>
          <span>{{ entry.name }}</span>
        </button>
        <el-empty v-if="!directoryEntries.length && !loading" description="没有子目录" :image-size="48" />
      </aside>

      <main v-loading="loading" class="file-pane">
        <div v-if="viewMode === 'icons'" class="file-grid">
          <button
            v-for="entry in visibleEntries"
            :key="entry.key"
            type="button"
            class="file-tile"
            :class="{ selected: selectedEntry?.key === entry.key }"
            @click="selectEntry(entry)"
            @dblclick="handleEntryDoubleClick(entry)"
          >
            <el-icon class="file-tile-icon" :class="{ folder: entry.directory, image: isImage(entry) }">
              <component :is="entryIcon(entry)" />
            </el-icon>
            <span class="file-tile-name" :title="entry.name">{{ entry.name }}</span>
            <small>{{ entry.directory ? '文件夹' : formatFileSize(entry.size) }}</small>
          </button>
        </div>

        <el-table
          v-else
          :data="visibleEntries"
          highlight-current-row
          row-key="key"
          empty-text="当前目录没有文件"
          @current-change="selectEntry"
          @row-dblclick="handleEntryDoubleClick"
        >
          <el-table-column label="名称" min-width="260">
            <template #default="{ row }">
              <div class="name-cell">
                <el-icon :class="{ folder: row.directory, image: isImage(row) }">
                  <component :is="entryIcon(row)" />
                </el-icon>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="修改日期" width="190">
            <template #default="{ row }">
              {{ formatDate(row.lastModified) }}
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              {{ row.directory ? '文件夹' : (fileExtension(row).toUpperCase() || '文件') }}
            </template>
          </el-table-column>
          <el-table-column label="大小" width="120" align="right">
            <template #default="{ row }">
              {{ row.directory ? '-' : formatFileSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column prop="storageClass" label="存储类型" width="130">
            <template #default="{ row }">
              {{ row.storageClass || '-' }}
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="viewMode === 'icons' && !visibleEntries.length && !loading" description="当前目录没有文件" />

        <div class="cursor-pager">
          <span>每次最多读取 {{ page?.maxKeys ?? MAX_KEYS }} 个对象，不统计整个 Bucket。</span>
          <div>
            <el-button :disabled="!canPreviousPage" @click="previousPage">
              上一页
            </el-button>
            <el-button type="primary" plain :disabled="!canNextPage" @click="nextPage">
              下一页
            </el-button>
          </div>
        </div>
      </main>

      <aside class="details-pane">
        <template v-if="selectedEntry">
          <div class="details-icon-wrap">
            <el-icon class="details-icon" :class="{ folder: selectedEntry.directory, image: selectedIsImage }">
              <component :is="entryIcon(selectedEntry)" />
            </el-icon>
          </div>
          <h3>{{ selectedEntry.name }}</h3>
          <dl>
            <dt>类型</dt>
            <dd>
              {{ selectedEntry.directory ? '文件夹' : (fileExtension(selectedEntry).toUpperCase() || '文件') }}
            </dd>
            <dt>大小</dt>
            <dd>
              {{ selectedEntry.directory ? '-' : formatFileSize(selectedEntry.size) }}
            </dd>
            <dt>修改时间</dt>
            <dd>
              {{ formatDate(selectedEntry.lastModified) }}
            </dd>
            <dt>存储类型</dt>
            <dd>
              {{ selectedEntry.storageClass || '-' }}
            </dd>
            <dt>ETag</dt>
            <dd class="break-text">
              {{ selectedEntry.etag || '-' }}
            </dd>
            <dt>Object Key</dt>
            <dd class="break-text">
              {{ selectedEntry.key }}
            </dd>
          </dl>
          <div class="details-actions">
            <el-button
              v-if="selectedEntry.directory"
              type="primary"
              :icon="Folder"
              @click="openFolder(selectedEntry)"
            >
              打开文件夹
            </el-button>
            <template v-else>
              <el-button v-if="selectedIsImage" :icon="View" @click="previewFile(selectedEntry)">
                预览
              </el-button>
              <el-button type="primary" :icon="View" @click="openFile(selectedEntry)">
                新窗口打开
              </el-button>
            </template>
          </div>
        </template>
        <el-empty v-else description="选择文件查看详情" :image-size="72" />
      </aside>
    </div>

    <el-dialog v-model="previewVisible" width="min(920px, 92vw)" destroy-on-close>
      <template #header>
        <div class="preview-title">
          <strong>{{ previewEntry?.name }}</strong>
          <span>{{ previewEntry ? formatFileSize(previewEntry.size) : '' }}</span>
        </div>
      </template>
      <div v-loading="previewLoading" class="preview-content">
        <el-image
          v-if="previewUrl"
          :src="previewUrl"
          :preview-src-list="[previewUrl]"
          fit="contain"
          hide-on-click-modal
        />
      </div>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.oss-explorer-card {
  overflow: hidden;
}

.explorer-title-row,
.explorer-status,
.explorer-toolbar,
.navigation-actions,
.breadcrumb-row,
.cursor-pager,
.preview-title,
.name-cell,
.details-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.explorer-title-row,
.breadcrumb-row,
.cursor-pager,
.preview-title {
  justify-content: space-between;
}

.explorer-title-row p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.repository-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-item {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.summary-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explorer-toolbar {
  padding: 10px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.address-bar {
  flex: 1;
  min-width: 280px;
}

.local-search {
  width: 220px;
}

.breadcrumb-row {
  min-height: 42px;
  padding: 0 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.breadcrumb-button,
.sidebar-item,
.file-tile {
  padding: 0;
  font: inherit;
  color: inherit;
  background: transparent;
  cursor: pointer;
  border: 0;
}

.breadcrumb-button:hover {
  color: var(--el-color-primary);
}

.pagination-hint {
  font-size: 12px;
  color: var(--el-color-warning);
}

.explorer-layout {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr) 280px;
  min-height: 520px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.folder-sidebar,
.details-pane {
  padding: 12px;
  overflow: auto;
  background: var(--el-fill-color-lighter);
}

.folder-sidebar {
  border-right: 1px solid var(--el-border-color-lighter);
}

.details-pane {
  border-left: 1px solid var(--el-border-color-lighter);
}

.sidebar-section-title {
  margin: 18px 8px 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}

.sidebar-item {
  display: flex;
  gap: 9px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  overflow: hidden;
  text-align: left;
  border-radius: 6px;
}

.sidebar-item:hover,
.sidebar-item.root {
  background: var(--el-color-primary-light-9);
}

.sidebar-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-pane {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 520px;
  padding: 14px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(122px, 1fr));
  gap: 10px;
  flex: 1;
  align-content: start;
}

.file-tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  min-height: 126px;
  padding: 14px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
}

.file-tile:hover {
  background: var(--el-fill-color-light);
}

.file-tile.selected {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.file-tile-icon,
.details-icon {
  color: var(--el-text-color-secondary);
}

.file-tile-icon {
  font-size: 48px;
}

.file-tile-icon.folder,
.details-icon.folder,
.name-cell .folder {
  color: var(--el-color-warning);
}

.file-tile-icon.image,
.details-icon.image,
.name-cell .image {
  color: var(--el-color-primary);
}

.file-tile-name {
  display: -webkit-box;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.file-tile small {
  color: var(--el-text-color-secondary);
}

.name-cell {
  min-width: 0;
}

.name-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cursor-pager {
  padding-top: 14px;
  margin-top: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-lighter);
}

.details-icon-wrap {
  display: flex;
  justify-content: center;
  padding: 20px 0 10px;
}

.details-icon {
  font-size: 72px;
}

.details-pane h3 {
  margin: 8px 0 18px;
  text-align: center;
  overflow-wrap: anywhere;
}

.details-pane dl {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 10px;
  margin: 0;
  font-size: 13px;
}

.details-pane dt {
  color: var(--el-text-color-secondary);
}

.details-pane dd {
  min-width: 0;
  margin: 0;
}

.break-text {
  overflow-wrap: anywhere;
}

.details-actions {
  flex-wrap: wrap;
  margin-top: 22px;
}

.preview-title span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.preview-content {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 460px;
  background: var(--el-fill-color-darker);
}

.preview-content :deep(.el-image) {
  width: 100%;
  height: 70vh;
}

@media (width <= 1280px) {
  .repository-summary {
    grid-template-columns: repeat(3, minmax(140px, 1fr));
  }

  .explorer-layout {
    grid-template-columns: 190px minmax(0, 1fr);
  }

  .details-pane {
    display: none;
  }
}

@media (width <= 860px) {
  .repository-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .explorer-toolbar {
    flex-wrap: wrap;
  }

  .address-bar,
  .local-search {
    width: 100%;
    min-width: 0;
  }

  .explorer-layout {
    grid-template-columns: 1fr;
  }

  .folder-sidebar {
    display: none;
  }

  .cursor-pager {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

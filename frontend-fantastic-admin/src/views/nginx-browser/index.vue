<script setup lang="ts">
import type {
  NginxBrowserEntry,
  NginxBrowserPage,
  NginxBrowserServer,
} from '@/api/modules/nginx-browser'
import {
  ArrowLeft,
  ArrowRight,
  Document,
  Download,
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  browseNginxDirectory,
  fetchNginxBrowserFile,
  getNginxBrowserServers,
} from '@/api/modules/nginx-browser'

defineOptions({ name: 'NginxBrowserPage' })

type ViewMode = 'icons' | 'details'

interface BreadcrumbItem {
  label: string
  path: string
}

const PAGE_SIZE = 200
const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'bmp', 'tif', 'tiff', 'webp'])

const loading = ref(false)
const fileLoading = ref(false)
const servers = ref<NginxBrowserServer[]>([])
const selectedServer = ref('default')
const page = ref<NginxBrowserPage>()
const currentPath = ref('')
const addressInput = ref('')
const currentOffset = ref(0)
const folderHistory = ref<string[]>([])
const localFilter = ref('')
const viewMode = ref<ViewMode>('icons')
const selectedEntry = ref<NginxBrowserEntry>()
const previewVisible = ref(false)
const previewUrl = ref('')
const previewEntry = ref<NginxBrowserEntry>()

const currentServer = computed(() => servers.value.find(server => server.key === selectedServer.value))
const entries = computed(() => page.value?.entries ?? [])
const visibleEntries = computed(() => {
  const keyword = localFilter.value.trim().toLocaleLowerCase()
  return keyword
    ? entries.value.filter(entry => entry.name.toLocaleLowerCase().includes(keyword))
    : entries.value
})
const quickFolders = computed(() => entries.value.filter(entry => entry.directory).slice(0, 50))
const canGoBack = computed(() => folderHistory.value.length > 0)
const canGoUp = computed(() => Boolean(currentPath.value))
const canPreviousPage = computed(() => currentOffset.value > 0)
const canNextPage = computed(() => {
  const total = page.value?.totalEntries ?? 0
  return currentOffset.value + entries.value.length < total
})
const selectedIsImage = computed(() => isImage(selectedEntry.value))
const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const result: BreadcrumbItem[] = [{ label: currentServer.value?.name || '根目录', path: '' }]
  const segments = currentPath.value.split('/').filter(Boolean)
  let path = ''
  for (const segment of segments) {
    path += `${segment}/`
    result.push({ label: segment, path })
  }
  return result
})

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeDirectoryPath(value: string) {
  let path = value.trim().replace(/^\/+/, '')
  if (path && !path.endsWith('/')) {
    path += '/'
  }
  return path
}

function parentPath(path: string) {
  const normalized = path.replace(/\/$/, '')
  const index = normalized.lastIndexOf('/')
  return index < 0 ? '' : normalized.slice(0, index + 1)
}

function fileExtension(entry?: NginxBrowserEntry) {
  if (!entry || entry.directory) {
    return ''
  }
  const index = entry.name.lastIndexOf('.')
  return index < 0 ? '' : entry.name.slice(index + 1).toLocaleLowerCase()
}

function isImage(entry?: NginxBrowserEntry) {
  return Boolean(entry && !entry.directory && IMAGE_EXTENSIONS.has(fileExtension(entry)))
}

function entryIcon(entry: NginxBrowserEntry) {
  if (entry.directory) {
    return Folder
  }
  return isImage(entry) ? Picture : Document
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
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}

async function loadServers() {
  try {
    const response = await getNginxBrowserServers()
    servers.value = response.data ?? []
    const preferred = servers.value.find(server => server.key === selectedServer.value && server.configured)
      ?? servers.value.find(server => server.configured)
    if (preferred) {
      selectedServer.value = preferred.key
      await loadDirectory('')
    }
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '读取 Nginx 图片服务器配置失败'))
  }
}

async function loadDirectory(
  path: string,
  offset = 0,
  options: { rememberFolder?: boolean } = {},
) {
  const server = currentServer.value
  if (!server?.configured) {
    page.value = undefined
    return
  }
  const normalizedPath = normalizeDirectoryPath(path)
  if (options.rememberFolder && normalizedPath !== currentPath.value) {
    folderHistory.value.push(currentPath.value)
  }

  loading.value = true
  selectedEntry.value = undefined
  localFilter.value = ''
  try {
    const response = await browseNginxDirectory({
      server: selectedServer.value,
      path: normalizedPath,
      offset,
      limit: PAGE_SIZE,
    })
    if (!response.data) {
      throw new Error('Nginx 目录响应为空')
    }
    page.value = response.data
    currentPath.value = response.data.path
    addressInput.value = response.data.path
    currentOffset.value = response.data.offset
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '读取 Nginx 目录失败'))
  }
  finally {
    loading.value = false
  }
}

function changeServer() {
  folderHistory.value = []
  currentPath.value = ''
  addressInput.value = ''
  currentOffset.value = 0
  void loadDirectory('')
}

function selectEntry(entry?: NginxBrowserEntry) {
  selectedEntry.value = entry
}

function openFolder(entry: NginxBrowserEntry) {
  if (entry.directory) {
    void loadDirectory(entry.path, 0, { rememberFolder: true })
  }
}

function handleEntryDoubleClick(entry: NginxBrowserEntry) {
  if (entry.directory) {
    openFolder(entry)
    return
  }
  void openFile(entry)
}

function goBack() {
  const path = folderHistory.value.pop()
  if (path !== undefined) {
    void loadDirectory(path)
  }
}

function goUp() {
  if (canGoUp.value) {
    void loadDirectory(parentPath(currentPath.value), 0, { rememberFolder: true })
  }
}

function jumpToAddress() {
  void loadDirectory(addressInput.value, 0, { rememberFolder: true })
}

function jumpToBreadcrumb(item: BreadcrumbItem) {
  if (item.path !== currentPath.value) {
    void loadDirectory(item.path, 0, { rememberFolder: true })
  }
}

function previousPage() {
  void loadDirectory(currentPath.value, Math.max(0, currentOffset.value - PAGE_SIZE))
}

function nextPage() {
  void loadDirectory(currentPath.value, currentOffset.value + PAGE_SIZE)
}

async function loadFileBlob(entry: NginxBrowserEntry) {
  if (entry.directory) {
    throw new Error('目录不能作为文件读取')
  }
  return fetchNginxBrowserFile(selectedServer.value, entry.path)
}

async function openFile(entry = selectedEntry.value) {
  if (!entry || entry.directory) {
    return
  }
  const target = window.open('about:blank', '_blank')
  fileLoading.value = true
  try {
    const blob = await loadFileBlob(entry)
    const url = URL.createObjectURL(blob)
    if (target) {
      target.opener = null
      target.location.href = url
    }
    else {
      window.open(url, '_blank', 'noopener,noreferrer')
    }
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }
  catch (error: unknown) {
    target?.close()
    ElMessage.error(errorMessage(error, '打开 Nginx 文件失败'))
  }
  finally {
    fileLoading.value = false
  }
}

async function downloadFile(entry = selectedEntry.value) {
  if (!entry || entry.directory) {
    return
  }
  fileLoading.value = true
  try {
    const blob = await loadFileBlob(entry)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = entry.name
    link.click()
    URL.revokeObjectURL(url)
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '下载 Nginx 文件失败'))
  }
  finally {
    fileLoading.value = false
  }
}

async function previewFile(entry = selectedEntry.value) {
  if (!entry || !isImage(entry)) {
    return
  }
  fileLoading.value = true
  try {
    revokePreviewUrl()
    const blob = await loadFileBlob(entry)
    previewEntry.value = entry
    previewUrl.value = URL.createObjectURL(blob)
    previewVisible.value = true
  }
  catch (error: unknown) {
    ElMessage.error(errorMessage(error, '加载 Nginx 图片预览失败'))
  }
  finally {
    fileLoading.value = false
  }
}

function revokePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

onMounted(() => {
  void loadServers()
})

onBeforeUnmount(revokePreviewUrl)
</script>

<template>
  <div class="page-shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">Nginx Static Resource Browser</p>
        <h2>Nginx 文件浏览</h2>
        <p class="subtitle">
          浏览 IMAGE_SERVER_URL_DEFAULT 与 BA01、BA02、BA03 对应的静态资源目录。
        </p>
      </div>
    </header>

    <el-alert
      title="目录浏览依赖 Nginx autoindex"
      description="对应 location 需要启用 autoindex on；推荐使用 autoindex_format json。页面为只读模式，文件通过后端代理读取，不向浏览器暴露 Nginx Basic Auth 凭据。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-card shadow="never" class="browser-card">
      <template #header>
        <div class="browser-header">
          <div>
            <strong>Nginx 静态资源管理器</strong>
            <p>所有路径均限制在所选服务器的配置根目录内。</p>
          </div>
          <div class="header-actions">
            <el-select v-model="selectedServer" class="server-select" @change="changeServer">
              <el-option
                v-for="server in servers"
                :key="server.key"
                :label="server.name"
                :value="server.key"
                :disabled="!server.configured"
              >
                <span>{{ server.name }}</span>
                <el-tag class="server-option-tag" size="small" :type="server.configured ? 'success' : 'info'">
                  {{ server.configured ? '已配置' : '未配置' }}
                </el-tag>
              </el-option>
            </el-select>
            <el-tag :type="currentServer?.configured ? 'success' : 'danger'">
              {{ currentServer?.configured ? '可访问' : '未配置' }}
            </el-tag>
            <el-button :icon="Refresh" :loading="loading" @click="loadDirectory(currentPath, currentOffset)">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div class="summary-grid">
        <div class="summary-item summary-wide">
          <span>静态资源根地址</span>
          <strong>{{ page?.baseUrl || currentServer?.baseUrl || '-' }}</strong>
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
          <span>目录总条目</span>
          <strong>{{ page?.totalEntries ?? 0 }}</strong>
        </div>
        <div class="summary-item">
          <span>当前页大小</span>
          <strong>{{ formatFileSize(page?.loadedBytes) }}</strong>
        </div>
      </div>

      <div class="toolbar">
        <div class="navigation-actions">
          <el-button :icon="ArrowLeft" circle :disabled="!canGoBack" title="返回" @click="goBack" />
          <el-button :icon="Top" circle :disabled="!canGoUp" title="上一级" @click="goUp" />
        </div>
        <el-input v-model="addressInput" class="address-input" @keyup.enter="jumpToAddress">
          <template #prefix>
            <el-icon><Folder /></el-icon>
          </template>
          <template #append>
            <el-button :icon="Search" @click="jumpToAddress" />
          </template>
        </el-input>
        <el-input v-model="localFilter" clearable class="filter-input" placeholder="筛选当前页">
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
          <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
            <button type="button" class="breadcrumb-button" @click="jumpToBreadcrumb(item)">
              {{ item.label }}
            </button>
          </el-breadcrumb-item>
        </el-breadcrumb>
        <span>
          第 {{ page ? Math.floor(page.offset / page.limit) + 1 : 1 }} 页 ·
          显示 {{ entries.length }} / {{ page?.totalEntries ?? 0 }} 项
        </span>
      </div>

      <div v-loading="loading" class="explorer-layout">
        <aside class="folder-pane">
          <div class="pane-title">服务器</div>
          <button
            v-for="server in servers"
            :key="server.key"
            type="button"
            class="tree-item"
            :class="{ active: server.key === selectedServer }"
            :disabled="!server.configured"
            @click="selectedServer = server.key; changeServer()"
          >
            <el-icon><Folder /></el-icon>
            <span>{{ server.name }}</span>
          </button>
          <div class="pane-title folder-title">当前目录</div>
          <button
            v-for="folder in quickFolders"
            :key="folder.path"
            type="button"
            class="tree-item"
            @dblclick="openFolder(folder)"
            @click="selectEntry(folder)"
          >
            <el-icon><Folder /></el-icon>
            <span>{{ folder.name }}</span>
          </button>
        </aside>

        <main class="file-pane" @click.self="selectEntry()">
          <el-empty v-if="!loading && visibleEntries.length === 0" description="当前目录没有可显示的文件" />

          <div v-else-if="viewMode === 'icons'" class="icon-grid">
            <button
              v-for="entry in visibleEntries"
              :key="entry.path"
              type="button"
              class="file-tile"
              :class="{ selected: selectedEntry?.path === entry.path }"
              @click="selectEntry(entry)"
              @dblclick="handleEntryDoubleClick(entry)"
            >
              <el-icon class="file-icon" :class="{ folder: entry.directory }">
                <component :is="entryIcon(entry)" />
              </el-icon>
              <span class="file-name" :title="entry.name">{{ entry.name }}</span>
              <small>{{ entry.directory ? '文件夹' : formatFileSize(entry.size) }}</small>
            </button>
          </div>

          <el-table
            v-else
            :data="visibleEntries"
            height="100%"
            highlight-current-row
            @row-click="selectEntry"
            @row-dblclick="handleEntryDoubleClick"
          >
            <el-table-column label="名称" min-width="300">
              <template #default="scope">
                <div class="table-name">
                  <el-icon :class="{ folder: scope.row.directory }">
                    <component :is="entryIcon(scope.row)" />
                  </el-icon>
                  <span>{{ scope.row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="scope">
                {{ scope.row.directory ? '文件夹' : (fileExtension(scope.row).toUpperCase() || '文件') }}
              </template>
            </el-table-column>
            <el-table-column label="大小" width="130">
              <template #default="scope">
                {{ scope.row.directory ? '-' : formatFileSize(scope.row.size) }}
              </template>
            </el-table-column>
            <el-table-column label="修改时间" width="190">
              <template #default="scope">{{ formatDate(scope.row.lastModified) }}</template>
            </el-table-column>
          </el-table>
        </main>

        <aside class="detail-pane">
          <div class="pane-title">详细信息</div>
          <template v-if="selectedEntry">
            <el-icon class="detail-icon" :class="{ folder: selectedEntry.directory }">
              <component :is="entryIcon(selectedEntry)" />
            </el-icon>
            <h3>{{ selectedEntry.name }}</h3>
            <dl>
              <dt>类型</dt>
              <dd>{{ selectedEntry.directory ? '文件夹' : (fileExtension(selectedEntry).toUpperCase() || '文件') }}</dd>
              <dt>大小</dt>
              <dd>{{ selectedEntry.directory ? '-' : formatFileSize(selectedEntry.size) }}</dd>
              <dt>修改时间</dt>
              <dd>{{ formatDate(selectedEntry.lastModified) }}</dd>
              <dt>相对路径</dt>
              <dd class="path-value">{{ selectedEntry.path }}</dd>
              <dt>服务器</dt>
              <dd>{{ currentServer?.name || '-' }}</dd>
            </dl>
            <div v-if="!selectedEntry.directory" class="detail-actions">
              <el-button :icon="View" :loading="fileLoading" @click="openFile()">打开</el-button>
              <el-button v-if="selectedIsImage" :icon="Picture" :loading="fileLoading" @click="previewFile()">
                预览
              </el-button>
              <el-button :icon="Download" :loading="fileLoading" @click="downloadFile()">下载</el-button>
            </div>
          </template>
          <el-empty v-else description="选择一个文件或目录" :image-size="72" />
        </aside>
      </div>

      <div class="pagination-bar">
        <el-button :icon="ArrowLeft" :disabled="!canPreviousPage" @click="previousPage">上一页</el-button>
        <el-button :icon="ArrowRight" :disabled="!canNextPage" @click="nextPage">下一页</el-button>
      </div>
    </el-card>

    <el-dialog
      v-model="previewVisible"
      :title="previewEntry?.name || '图片预览'"
      width="min(92vw, 1200px)"
      destroy-on-close
      @closed="revokePreviewUrl"
    >
      <div class="preview-stage">
        <img v-if="previewUrl" :src="previewUrl" :alt="previewEntry?.name">
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 18px;
  padding: var(--g-main-padding);
}

.page-header h2,
.page-header p,
.browser-header p,
.detail-pane h3 {
  margin: 0;
}

.page-header h2 {
  margin-top: 4px;
  font-size: 26px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtitle,
.browser-header p {
  margin-top: 6px !important;
  color: var(--el-text-color-secondary);
}

.browser-header,
.header-actions,
.toolbar,
.navigation-actions,
.breadcrumb-row,
.pagination-bar,
.table-name,
.detail-actions {
  display: flex;
  align-items: center;
}

.browser-header,
.breadcrumb-row {
  justify-content: space-between;
  gap: 16px;
}

.header-actions,
.toolbar,
.detail-actions {
  gap: 10px;
}

.server-select {
  width: 220px;
}

.server-option-tag {
  float: right;
  margin-left: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(280px, 2fr) repeat(4, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.summary-item {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
}

.summary-item span,
.summary-item strong {
  display: block;
}

.summary-item span {
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar {
  margin-bottom: 12px;
}

.address-input {
  flex: 1;
  min-width: 260px;
}

.filter-input {
  width: 220px;
}

.breadcrumb-row {
  min-height: 36px;
  padding: 0 4px 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.breadcrumb-button {
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

.explorer-layout {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr) 280px;
  min-height: 560px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.folder-pane,
.detail-pane {
  min-width: 0;
  padding: 14px;
  background: var(--el-fill-color-extra-light);
}

.folder-pane {
  overflow: auto;
  border-right: 1px solid var(--el-border-color-lighter);
}

.detail-pane {
  overflow: auto;
  border-left: 1px solid var(--el-border-color-lighter);
}

.pane-title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.folder-title {
  margin-top: 18px;
}

.tree-item {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
  padding: 8px 9px;
  border: 0;
  border-radius: 7px;
  color: var(--el-text-color-primary);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.tree-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-item:hover,
.tree-item.active {
  background: var(--el-color-primary-light-9);
}

.tree-item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.file-pane {
  min-width: 0;
  padding: 12px;
  overflow: auto;
  background: var(--el-bg-color);
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  gap: 10px;
}

.file-tile {
  display: flex;
  align-items: center;
  flex-direction: column;
  min-height: 122px;
  padding: 14px 8px 10px;
  border: 1px solid transparent;
  border-radius: 9px;
  color: var(--el-text-color-primary);
  background: transparent;
  cursor: default;
}

.file-tile:hover,
.file-tile.selected {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.file-icon,
.detail-icon {
  color: var(--el-text-color-secondary);
}

.file-icon.folder,
.detail-icon.folder,
.table-name .folder {
  color: var(--el-color-warning);
}

.file-icon {
  margin-bottom: 9px;
  font-size: 42px;
}

.file-name {
  width: 100%;
  overflow: hidden;
  font-size: 13px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-tile small {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.table-name {
  gap: 8px;
}

.detail-pane {
  text-align: center;
}

.detail-icon {
  margin: 18px 0 10px;
  font-size: 64px;
}

.detail-pane h3 {
  overflow-wrap: anywhere;
}

.detail-pane dl {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  margin: 24px 0;
  text-align: left;
}

.detail-pane dt {
  color: var(--el-text-color-secondary);
}

.detail-pane dd {
  min-width: 0;
  margin: 0;
}

.path-value {
  overflow-wrap: anywhere;
}

.detail-actions {
  justify-content: center;
  flex-wrap: wrap;
}

.pagination-bar {
  justify-content: flex-end;
  margin-top: 12px;
  gap: 8px;
}

.preview-stage {
  display: grid;
  min-height: 320px;
  place-items: center;
  overflow: auto;
  background: var(--el-fill-color-dark);
}

.preview-stage img {
  max-width: 100%;
  max-height: 76vh;
  object-fit: contain;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-wide {
    grid-column: 1 / -1;
  }

  .explorer-layout {
    grid-template-columns: 180px minmax(0, 1fr);
  }

  .detail-pane {
    display: none;
  }
}

@media (max-width: 760px) {
  .browser-header,
  .header-actions,
  .toolbar,
  .breadcrumb-row {
    align-items: stretch;
    flex-direction: column;
  }

  .server-select,
  .filter-input {
    width: 100%;
  }

  .summary-grid,
  .explorer-layout {
    grid-template-columns: 1fr;
  }

  .folder-pane {
    display: none;
  }
}
</style>

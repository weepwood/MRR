<script setup lang="ts">
import type { NginxBrowserEntry, NginxBrowserPage, NginxBrowserServer } from '@/api/modules/nginx-browser'
import type { PreviewKind } from '@/components/MrrStorageBrowser/inline-preview'
import { ElMessage } from 'element-plus'
import { Download, ExternalLink, Eye, FolderOpen } from 'lucide-vue-next'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { browseNginxDirectory, fetchNginxBrowserFile, getNginxBrowserServers } from '@/api/modules/nginx-browser'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrStorageBrowser from '@/components/MrrStorageBrowser/index.vue'
import { decodePreviewText, inlinePreviewLimit } from '@/components/MrrStorageBrowser/inline-preview'
import MrrStorageInlinePreview from '@/components/MrrStorageBrowser/InlinePreview.vue'
import { accessDenied, formatFileSize, isImage } from '@/components/MrrStorageBrowser/presentation'
import MrrStoragePreview from '@/components/MrrStorageBrowser/Preview.vue'

defineOptions({ name: 'NginxBrowserPage' })

interface DirectoryLocation {
  server: string
  path: string
  offset: number
}

const PAGE_SIZE = 200
const inlineTypes = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp', 'image/tiff', 'application/pdf', 'text/plain'])
const servers = ref<NginxBrowserServer[]>([])
const page = ref<NginxBrowserPage>()
const loading = ref(false)
const error = ref('')
const addressInput = ref('')
const localFilter = ref('')
const selectedEntry = ref<NginxBrowserEntry>()
const history = ref<DirectoryLocation[]>([])
let directoryRequest: AbortController | undefined
let retryDirectory: (() => void) | undefined
let resumeDirectory: (() => void) | undefined

const fileLoading = ref(false)
const previewLoading = ref(false)
const previewVisible = ref(false)
const previewFailed = ref(false)
const previewEntry = ref<NginxBrowserEntry>()
const previewUrl = ref('')
let fileRequest: AbortController | undefined
let previewRequest: AbortController | undefined

const currentServer = computed(() => servers.value.find(server => server.key === page.value?.server))
const currentPath = computed(() => page.value?.path ?? '')
const canNextPage = computed(() => Boolean(page.value && page.value.offset + page.value.entries.length < page.value.totalEntries))
const breadcrumbs = computed(() => {
  const crumbs = [{ label: currentServer.value?.name || '根目录', path: '' }]
  let path = ''
  for (const segment of currentPath.value.split('/').filter(Boolean)) {
    path += `${segment}/`
    crumbs.push({ label: segment, path })
  }
  return crumbs
})

function entryLocation(entry: NginxBrowserEntry) {
  return entry.path
}

async function loadInlinePreview(entry: NginxBrowserEntry, kind: PreviewKind, signal: AbortSignal) {
  const server = currentServer.value
  if (!server) {
    throw new Error('No server selected')
  }
  const blob = await fetchNginxBrowserFile(server.key, entry.path, signal, inlinePreviewLimit[kind])
  return kind === 'image' ? blob : decodePreviewText(new Uint8Array(await blob.arrayBuffer()))
}

function beginDirectoryRequest(retry: () => void) {
  directoryRequest?.abort()
  const request = new AbortController()
  directoryRequest = request
  retryDirectory = retry
  loading.value = true
  error.value = ''
  return request
}

function endDirectoryRequest(request: AbortController) {
  if (directoryRequest === request) {
    directoryRequest = undefined
    loading.value = false
  }
}

async function loadServers() {
  const request = beginDirectoryRequest(() => void loadServers())
  try {
    const response = await getNginxBrowserServers(request.signal)
    if (request.signal.aborted) {
      return
    }
    servers.value = response.data ?? []
    const preferred = servers.value.find(server => server.configured && server.key === 'default')
      ?? servers.value.find(server => server.configured)
    if (preferred) {
      await loadDirectory({ server: preferred.key, path: '', offset: 0 }, [])
    }
  }
  catch (failure) {
    if (!request.signal.aborted) {
      error.value = accessDenied(failure) ? '当前账号没有文件浏览权限。' : '无法读取 Nginx 节点，请重试。'
    }
  }
  finally {
    endDirectoryRequest(request)
  }
}

async function loadDirectory(target: DirectoryLocation, nextHistory: DirectoryLocation[]) {
  const request = beginDirectoryRequest(() => void loadDirectory(target, nextHistory))
  previewVisible.value = false
  fileRequest?.abort()
  try {
    const response = await browseNginxDirectory({ ...target, limit: PAGE_SIZE }, request.signal)
    if (request.signal.aborted) {
      return
    }
    if (!response.data) {
      throw new Error('目录响应为空')
    }
    page.value = response.data
    history.value = nextHistory
    addressInput.value = response.data.path
    localFilter.value = ''
    selectedEntry.value = undefined
  }
  catch (failure) {
    if (!request.signal.aborted) {
      if (accessDenied(failure)) {
        page.value = undefined
        history.value = []
        selectedEntry.value = undefined
        localFilter.value = ''
        error.value = '当前账号没有文件浏览权限。'
      }
      else {
        error.value = page.value
          ? '目录读取失败，仍显示上次成功读取的内容。'
          : '无法读取 Nginx 目录，请重试。'
      }
      addressInput.value = currentPath.value
    }
  }
  finally {
    endDirectoryRequest(request)
  }
}

function refresh() {
  if (page.value) {
    const { server, path, offset } = page.value
    void loadDirectory({ server, path, offset }, history.value)
  }
  else if (error.value && retryDirectory) {
    retryDirectory()
  }
  else {
    void loadServers()
  }
}

function changeServer(server: string) {
  if (servers.value.some(item => item.key === server && item.configured)) {
    void loadDirectory({ server, path: '', offset: 0 }, [])
  }
}

function normalizePath(input: string) {
  const path = input.trim().replace(/^\/+/, '')
  return path && !path.endsWith('/') ? `${path}/` : path
}

function navigate(input: string) {
  const current = page.value
  if (!current) {
    return
  }
  const path = normalizePath(input)
  const { server, offset } = current
  const nextHistory = path === current.path ? history.value : [...history.value, { server, path: current.path, offset }]
  void loadDirectory({ server, path, offset: 0 }, nextHistory)
}

function goBack() {
  const target = history.value[history.value.length - 1]
  if (target && !loading.value) {
    void loadDirectory(target, history.value.slice(0, -1))
  }
}

function goUp() {
  const path = currentPath.value.replace(/\/$/, '')
  navigate(path.slice(0, path.lastIndexOf('/') + 1))
}

function turnPage(direction: -1 | 1) {
  const current = page.value
  if (!current || loading.value || (direction === 1 ? !canNextPage.value : !current.offset)) {
    return
  }
  const { server, path, offset, limit } = current
  void loadDirectory({ server, path, offset: Math.max(0, offset + direction * limit) }, history.value)
}

function selectEntry(entry?: NginxBrowserEntry) {
  if (selectedEntry.value !== entry) {
    previewVisible.value = false
  }
  selectedEntry.value = entry
}

function openEntry(entry: NginxBrowserEntry) {
  if (entry.directory) {
    navigate(entry.path)
  }
  else if (isImage(entry)) {
    void previewFile(entry)
  }
  else {
    void readFile(entry, 'open')
  }
}

async function readFile(entry: NginxBrowserEntry, action: 'open' | 'download') {
  const server = currentServer.value
  if (!server || entry.directory || fileLoading.value) {
    return
  }
  const request = new AbortController()
  fileRequest = request
  fileLoading.value = true
  const target = action === 'open' ? window.open('about:blank', '_blank') : null
  if (target) {
    target.opener = null
  }
  try {
    const blob = await fetchNginxBrowserFile(server.key, entry.path, request.signal)
    if (request.signal.aborted) {
      target?.close()
      return
    }
    const url = URL.createObjectURL(blob)
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    if (target && inlineTypes.has(blob.type.split(';')[0])) {
      target.location.href = url
    }
    else {
      target?.close()
      const link = document.createElement('a')
      link.href = url
      link.download = entry.name
      link.click()
    }
  }
  catch {
    target?.close()
    if (!request.signal.aborted) {
      ElMessage.error('文件读取失败，请重试。')
    }
  }
  finally {
    if (fileRequest === request) {
      fileRequest = undefined
      fileLoading.value = false
    }
  }
}

function clearPreview() {
  previewRequest?.abort()
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  previewEntry.value = undefined
  previewLoading.value = false
  previewFailed.value = false
}

async function previewFile(entry: NginxBrowserEntry) {
  const server = currentServer.value
  if (!server || !isImage(entry)) {
    return
  }
  clearPreview()
  const request = new AbortController()
  previewRequest = request
  previewEntry.value = entry
  previewVisible.value = true
  previewLoading.value = true
  try {
    const blob = await fetchNginxBrowserFile(server.key, entry.path, request.signal)
    if (!request.signal.aborted) {
      previewUrl.value = URL.createObjectURL(blob)
    }
  }
  catch {
    if (!request.signal.aborted) {
      previewFailed.value = true
    }
  }
  finally {
    if (!request.signal.aborted) {
      previewLoading.value = false
    }
  }
}

watch(previewVisible, (visible) => {
  if (!visible) {
    clearPreview()
  }
}, { flush: 'sync' })

function cancelRequests() {
  directoryRequest?.abort()
  directoryRequest = undefined
  loading.value = false
  fileRequest?.abort()
  previewVisible.value = false
}

onMounted(() => void loadServers())
onDeactivated(() => {
  if (loading.value) {
    resumeDirectory = page.value ? refresh : retryDirectory
  }
  cancelRequests()
})
onActivated(() => {
  const resume = resumeDirectory
  resumeDirectory = undefined
  resume?.()
})
onBeforeUnmount(cancelRequests)
</script>

<template>
  <MrrPageShell>
    <MrrPageHeader
      class="page-header"
      title="Nginx 文件浏览"
      description="浏览已配置图片节点中的目录与文件。"
    >
      <template #badge>
        <el-tag effect="plain" type="info" size="small">
          只读
        </el-tag>
      </template>
    </MrrPageHeader>

    <MrrStorageBrowser
      v-model:address="addressInput"
      v-model:filter="localFilter"
      :entries="page?.entries ?? []"
      :location="entryLocation"
      :breadcrumbs="breadcrumbs"
      :selected="selectedEntry"
      :page-label="page ? `第 ${Math.floor(page.offset / page.limit) + 1} 页 · 共 ${page.totalEntries} 项` : '目录内容'"
      :summary="page ? `${page.loadedDirectories} 个目录 · ${page.loadedFiles} 个文件 · ${formatFileSize(page.loadedBytes)}` : undefined"
      :source-detail="page?.baseUrl ? { label: '查看节点根地址', value: page.baseUrl } : undefined"
      :loading="loading"
      :available="Boolean(page)"
      :can-go-back="history.length > 0"
      :can-go-up="Boolean(currentPath)"
      :can-previous-page="Boolean(page?.offset)"
      :can-next-page="canNextPage"
      :error="error"
      unavailable-text="没有可用的 Nginx 节点"
      location-label="相对路径"
      @back="goBack"
      @up="goUp"
      @refresh="refresh"
      @retry="retryDirectory?.()"
      @navigate="navigate"
      @select="selectEntry"
      @open="openEntry"
      @previous="turnPage(-1)"
      @next="turnPage(1)"
    >
      <template #source>
        <strong>图片节点</strong>
        <el-select
          :model-value="currentServer?.key"
          class="node-select"
          aria-label="图片节点"
          :disabled="!servers.some(server => server.configured)"
          placeholder="选择节点"
          @change="changeServer"
        >
          <el-option
            v-for="server in servers"
            :key="server.key"
            :label="server.configured ? server.name : `${server.name}（未配置）`"
            :value="server.key"
            :disabled="!server.configured"
          />
        </el-select>
      </template>
      <template #preview="{ entry }">
        <MrrStorageInlinePreview :entry="entry" :paused="loading || previewVisible" :load="loadInlinePreview" @expand="previewFile(entry)" />
      </template>
      <template #actions="{ entry }">
        <el-button v-if="entry.directory" :icon="FolderOpen" type="primary" @click="navigate(entry.path)">
          打开文件夹
        </el-button>
        <template v-else>
          <el-button v-if="isImage(entry)" :icon="Eye" type="primary" @click="previewFile(entry)">
            预览
          </el-button>
          <el-button :icon="ExternalLink" :disabled="fileLoading" @click="readFile(entry, 'open')">
            打开
          </el-button>
          <el-button :icon="Download" :loading="fileLoading" @click="readFile(entry, 'download')">
            下载
          </el-button>
        </template>
      </template>
    </MrrStorageBrowser>

    <MrrStoragePreview
      v-model="previewVisible"
      :name="previewEntry?.name"
      :url="previewUrl"
      :loading="previewLoading"
      :failed="previewFailed"
      @retry="previewEntry && previewFile(previewEntry)"
    />
  </MrrPageShell>
</template>

<style scoped>
.node-select {
  width: 160px;
}
</style>

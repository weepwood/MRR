<script setup lang="ts">
import type { OssBrowserEntry, OssBrowserPage } from '@/api/modules/oss'
import type { PreviewKind } from '@/components/MrrStorageBrowser/inline-preview'
import { ElMessage } from 'element-plus'
import { ExternalLink, Eye, FolderOpen } from 'lucide-vue-next'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { browseOssObjects, getOssBrowserUrl } from '@/api/modules/oss'
import MrrStorageBrowser from '@/components/MrrStorageBrowser/index.vue'
import { readSignedPreviewText } from '@/components/MrrStorageBrowser/inline-preview'
import MrrStorageInlinePreview from '@/components/MrrStorageBrowser/InlinePreview.vue'
import { accessDenied, formatFileSize, isImage } from '@/components/MrrStorageBrowser/presentation'
import MrrStoragePreview from '@/components/MrrStorageBrowser/Preview.vue'

defineOptions({ name: 'OssFileExplorer' })

const DEFAULT_ROOT_PREFIX = 'medical-records/'
const MAX_KEYS = 200

const loading = ref(false)
const directoryError = ref('')
let directoryGeneration = 0
let retryDirectory: (() => void) | undefined
let previewGeneration = 0
let disposed = false
let suspended = false
let reloadOnActivation = false
const previewLoading = ref(false)
const page = ref<OssBrowserPage>()
const currentPrefix = ref(DEFAULT_ROOT_PREFIX)
const addressInput = ref(DEFAULT_ROOT_PREFIX)
const localFilter = ref('')
const selectedEntry = ref<OssBrowserEntry | null>(null)
const previewEntry = ref<OssBrowserEntry>()
const previewUrl = ref('')
const previewVisible = ref(false)
const currentContinuationToken = ref<string>()
const pageTokenHistory = ref<Array<string | undefined>>([])
const folderHistory = ref<string[]>([])

const rootPrefix = computed(() => page.value?.rootPrefix || DEFAULT_ROOT_PREFIX)
const entries = computed(() => page.value?.entries ?? [])
const canGoBack = computed(() => folderHistory.value.length > 0)
const canGoUp = computed(() => currentPrefix.value !== rootPrefix.value)
const canPreviousPage = computed(() => pageTokenHistory.value.length > 0)
const canNextPage = computed(() => Boolean(page.value?.truncated && page.value.nextContinuationToken))
const breadcrumbs = computed(() => {
  const root = rootPrefix.value
  const rootLabel = root.replace(/\/$/, '') || 'medical-records'
  const relative = currentPrefix.value.startsWith(root)
    ? currentPrefix.value.slice(root.length).replace(/\/$/, '')
    : ''
  const result = [{ label: rootLabel, path: root }]
  if (!relative) {
    return result
  }
  let prefix = root
  for (const segment of relative.split('/').filter(Boolean)) {
    prefix += `${segment}/`
    result.push({ label: segment, path: prefix })
  }
  return result
})

function entryLocation(entry: OssBrowserEntry) {
  return entry.key
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
  options: { rememberFolder?: boolean, resetPageHistory?: boolean, navigation?: 'back' | 'next' | 'previous' } = {},
) {
  if (disposed || suspended) {
    return
  }
  const generation = ++directoryGeneration
  const normalizedPrefix = normalizeRequestedPrefix(prefix)
  // Navigation history describes only successfully displayed pages.
  const folders = [...folderHistory.value]
  const tokens = options.resetPageHistory === false ? [...pageTokenHistory.value] : []
  if (options.rememberFolder && currentPrefix.value !== normalizedPrefix) {
    folders.push(currentPrefix.value)
  }
  if (options.navigation === 'back') {
    folders.pop()
  }
  if (options.navigation === 'next') {
    tokens.push(currentContinuationToken.value)
  }
  if (options.navigation === 'previous') {
    tokens.pop()
  }
  retryDirectory = () => void loadDirectory(normalizedPrefix, continuationToken, options)
  previewVisible.value = false
  loading.value = true
  directoryError.value = ''
  try {
    const response = await browseOssObjects({
      prefix: normalizedPrefix,
      continuationToken,
      maxKeys: MAX_KEYS,
    })
    if (generation !== directoryGeneration) {
      return
    }
    if (!response.data) {
      throw new Error('OSS 目录响应为空')
    }
    page.value = response.data
    currentPrefix.value = response.data.prefix
    addressInput.value = response.data.prefix
    currentContinuationToken.value = continuationToken
    folderHistory.value = folders
    pageTokenHistory.value = tokens
    selectedEntry.value = null
    localFilter.value = ''
  }
  catch (error) {
    if (generation === directoryGeneration) {
      if (accessDenied(error)) {
        page.value = undefined
        currentPrefix.value = rootPrefix.value
        folderHistory.value = []
        pageTokenHistory.value = []
        currentContinuationToken.value = undefined
        selectedEntry.value = null
        localFilter.value = ''
        directoryError.value = '当前账号没有文件浏览权限。'
      }
      else {
        directoryError.value = page.value
          ? '读取失败，当前仍显示上次成功读取的目录。请重试原操作或刷新。'
          : '目录读取失败，请检查 OSS 服务状态后刷新重试。'
      }
      addressInput.value = currentPrefix.value
    }
  }
  finally {
    if (generation === directoryGeneration) {
      loading.value = false
    }
  }
}

function openFolder(entry: OssBrowserEntry) {
  if (!entry.directory) {
    return
  }
  void loadDirectory(entry.key, undefined, { rememberFolder: true })
}

function selectEntry(entry: OssBrowserEntry | null = null) {
  if (selectedEntry.value !== entry) {
    previewVisible.value = false
  }
  selectedEntry.value = entry
}

function openEntry(entry: OssBrowserEntry) {
  if (entry.directory) {
    openFolder(entry)
  }
  else if (isImage(entry)) {
    void previewFile(entry)
  }
  else {
    void openFile(entry)
  }
}

function goBack() {
  if (loading.value) { return }
  const previous = folderHistory.value[folderHistory.value.length - 1]
  if (previous) {
    void loadDirectory(previous, undefined, { navigation: 'back' })
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

function navigate(path: string) {
  void loadDirectory(path, undefined, { rememberFolder: true })
}

function nextPage() {
  const nextToken = page.value?.nextContinuationToken
  if (loading.value || !nextToken) {
    return
  }
  void loadDirectory(currentPrefix.value, nextToken, { resetPageHistory: false, navigation: 'next' })
}

function previousPage() {
  if (loading.value || !pageTokenHistory.value.length) {
    return
  }
  const previousToken = pageTokenHistory.value[pageTokenHistory.value.length - 1]
  void loadDirectory(currentPrefix.value, previousToken, { resetPageHistory: false, navigation: 'previous' })
}

async function requestSignedUrl(entry: OssBrowserEntry, signal?: AbortSignal) {
  const response = await getOssBrowserUrl(entry.key, signal)
  const signedUrl = response.data?.ossUrl
  if (!signedUrl) {
    throw new Error('未获取到 OSS 文件地址')
  }
  return signedUrl
}

async function loadInlinePreview(entry: OssBrowserEntry, kind: PreviewKind, signal: AbortSignal) {
  const url = new URL(await requestSignedUrl(entry, signal), window.location.href)
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new Error('Unsupported preview URL')
  }
  return kind === 'image' ? url.href : readSignedPreviewText(url, signal)
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
  catch {
    target?.close()
    ElMessage.error('打开 OSS 文件失败，请重试。')
  }
}

async function previewFile(entry = selectedEntry.value) {
  if (!entry || entry.directory || !isImage(entry)) {
    return
  }
  if (disposed || suspended) { return }
  const generation = ++previewGeneration
  previewEntry.value = entry
  previewUrl.value = ''
  previewVisible.value = true
  previewLoading.value = true
  try {
    const url = await requestSignedUrl(entry)
    if (generation === previewGeneration) {
      previewUrl.value = url
    }
  }
  catch {
    if (generation === previewGeneration) {
      previewVisible.value = false
      ElMessage.error('加载 OSS 图片预览失败，请重试。')
    }
  }
  finally {
    if (generation === previewGeneration) {
      previewLoading.value = false
    }
  }
}

function clearPreview() {
  previewGeneration++
  previewUrl.value = ''
  previewEntry.value = undefined
  previewLoading.value = false
}

watch(previewVisible, (visible) => {
  if (!visible) { clearPreview() }
}, { flush: 'sync' })

onDeactivated(() => {
  suspended = true
  reloadOnActivation = loading.value
  directoryGeneration++
  loading.value = false
  previewVisible.value = false
  clearPreview()
})

onActivated(() => {
  suspended = false
  if (reloadOnActivation) {
    reloadOnActivation = false
    void loadDirectory(currentPrefix.value, currentContinuationToken.value, { resetPageHistory: false })
  }
})

onBeforeUnmount(() => {
  disposed = true
  directoryGeneration++
  clearPreview()
})

onMounted(() => {
  void loadDirectory(DEFAULT_ROOT_PREFIX)
})
</script>

<template>
  <MrrStorageBrowser
    v-model:address="addressInput"
    v-model:filter="localFilter"
    :entries="entries"
    :location="entryLocation"
    :breadcrumbs="breadcrumbs"
    :selected="selectedEntry"
    :page-label="page?.configured ? `第 ${pageTokenHistory.length + 1} 页` : '目录内容'"
    :summary="page?.configured ? `${page.loadedDirectories} 个目录 · ${page.loadedFiles} 个文件 · ${formatFileSize(page.loadedBytes)}` : undefined"
    :source-detail="page?.endpoint ? { label: '查看 Endpoint', value: page.endpoint } : undefined"
    :loading="loading"
    :available="Boolean(page?.configured)"
    :can-go-back="canGoBack"
    :can-go-up="canGoUp"
    :can-previous-page="canPreviousPage"
    :can-next-page="canNextPage"
    :error="directoryError"
    unavailable-text="OSS 未配置"
    location-label="对象路径"
    @back="goBack"
    @up="goUp"
    @refresh="loadDirectory(currentPrefix, currentContinuationToken, { resetPageHistory: false })"
    @retry="retryDirectory?.()"
    @navigate="navigate"
    @select="selectEntry"
    @open="openEntry"
    @previous="previousPage"
    @next="nextPage"
  >
    <template #source>
      <strong>Bucket</strong>
      <span v-if="page?.bucket">{{ page.bucket }}</span>
    </template>
    <template #preview="{ entry }">
      <MrrStorageInlinePreview :entry="entry" :paused="loading || previewVisible" :load="loadInlinePreview" @expand="previewFile(entry)" />
    </template>
    <template #metadata="{ entry }">
      <template v-if="!entry.directory">
        <dt>存储类型</dt>
        <dd>{{ entry.storageClass || '—' }}</dd>
        <dt>ETag</dt>
        <dd>{{ entry.etag || '—' }}</dd>
      </template>
    </template>
    <template #actions="{ entry }">
      <el-button v-if="entry.directory" :icon="FolderOpen" type="primary" @click="openFolder(entry)">
        打开文件夹
      </el-button>
      <template v-else>
        <el-button v-if="isImage(entry)" :icon="Eye" type="primary" @click="previewFile(entry)">
          预览
        </el-button>
        <el-button :icon="ExternalLink" @click="openFile(entry)">
          新窗口打开
        </el-button>
      </template>
    </template>
  </MrrStorageBrowser>

  <MrrStoragePreview
    v-model="previewVisible"
    :name="previewEntry?.name"
    :url="previewUrl"
    :loading="previewLoading"
    @retry="previewEntry && previewFile(previewEntry)"
  />
</template>

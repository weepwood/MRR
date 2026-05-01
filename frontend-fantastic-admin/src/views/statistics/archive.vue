<script setup lang="ts">
import type { BAHImageData, BAHRecord } from '@/api/types'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getImgApiByBah, updateImageType } from '@/api/modules/image'

defineOptions({ name: 'StatisticsArchivePage' })

// ==================== 路由 ====================
const route = useRoute()

// ==================== 图片扩展类型 ====================
interface GalleryImage extends BAHImageData {
  cx?: string
  blobUrl?: string
}

// ==================== 基础状态 ====================
const images = ref<GalleryImage[]>([])
const loading = ref(false)
const errorMsg = ref('')
const selectedType = ref<number | 'all'>('all')
const searchBah = ref(String(route.params.bah || ''))
const downloading = ref(false)

// ==================== 病案号范围 ====================
const startBah = ref('00787327')
const endBah = ref('00855320')
const contentNotice = ref(`查询 2018 - 2020 期间的住院记录，目前可访问的病案区间为 [${startBah.value}, ${endBah.value}]`)

// ==================== 身份证查询 ====================
const idSearchResults = ref<BAHRecord[]>([])
const selectedRecord = ref<BAHRecord | null>(null)

// ==================== 图片查看器 ====================
const selectedImageIndex = ref(0)
// @ts-ignore: used as template ref
const viewerContainer = ref<HTMLElement | null>(null)
const thumbsContainer = ref<HTMLElement | null>(null)
// @ts-ignore: used as template ref
const viewerSplitRef = ref<HTMLElement | null>(null)
const thumbRefs = ref<(HTMLElement | null)[]>([])

// ==================== 布局和视图模式 ====================
const thumbsPaneMin = 130
const gridGap = 8
const singleThumbMinWidth = 130
const thumbsPaneWidth = ref(200)
const THUMBS_VIEW_MODE_KEY = 'pmr.thumbsViewMode'
const thumbsViewMode = ref<'icons' | 'details'>('icons')
const LAYOUT_MODE_KEY = 'pmr.thumbsLayoutMode'
const thumbsLayoutMode = ref<'grid-1' | 'grid-2' | 'grid-3'>('grid-1')

// ==================== 类型选择器 ====================
const showTypePicker = ref(false)
const typeOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14]

function hideTypePicker() { showTypePicker.value = false }
function handleDocumentClick() { hideTypePicker() }

onMounted(() => { document.addEventListener('click', handleDocumentClick) })
onUnmounted(() => { document.removeEventListener('click', handleDocumentClick) })

// ==================== 透明占位图 ====================
const transparentPixel = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=='

// ==================== 计算属性 ====================
const filteredImages = computed(() =>
  selectedType.value === 'all'
    ? images.value
    : images.value.filter(img => img.btype === selectedType.value),
)

const isLoading = computed(() => loading.value)

const thumbsPaneMax = computed(() => singleThumbMinWidth * 3 + gridGap * 2 + 16)
const thumbsColumns = computed(() => {
  const c = Math.floor(thumbsPaneWidth.value / singleThumbMinWidth)
  return Math.min(3, Math.max(1, c))
})

const typeDisplayList = computed(() => {
  const counts = new Map<number, number>()
  for (const img of images.value) {
    const t = img.btype as number
    counts.set(t, (counts.get(t) || 0) + 1)
  }
  const allTypes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14]
  return allTypes.map(type => ({ type, count: counts.get(type) || 0 }))
})

const currentImage = computed<GalleryImage | null>(() => filteredImages.value[selectedImageIndex.value] || null)
const previewList = computed(() => filteredImages.value.map(img => img.blobUrl || transparentPixel))

// ==================== 全屏预览 ====================
const isPreviewOpen = ref(false)
const previewCurrentIndex = ref(0)
const currentPreviewImage = computed<GalleryImage | null>(() => filteredImages.value[previewCurrentIndex.value] || null)

async function onViewerShow() {
  isPreviewOpen.value = true
  previewCurrentIndex.value = selectedImageIndex.value
  await preloadAroundIndex(previewCurrentIndex.value, 3)
}

async function onViewerSwitch(newIndex: number) {
  if (typeof newIndex === 'number' && Number.isFinite(newIndex)) {
    previewCurrentIndex.value = newIndex
    await preloadAroundIndex(previewCurrentIndex.value, 3)
  }
}

function onViewerClose() {
  const idx = previewCurrentIndex.value
  if (typeof idx === 'number' && idx >= 0 && idx < filteredImages.value.length) {
    selectImage(idx, 'keyboard')
  }
  isPreviewOpen.value = false
}

// ==================== 数据加载 ====================
async function loadImages() {
  if (!searchBah.value) { return }
  loading.value = true
  errorMsg.value = ''
  try {
    const response = await getImgApiByBah(searchBah.value)
    const rawList = Array.isArray(response.data) ? response.data : []
    images.value = rawList.map((item: BAHImageData) => {
      const originalUrl = item?.img_url || ''
      return { ...item, cx: originalUrl, blobUrl: originalUrl } as GalleryImage
    })
    selectedType.value = 'all'
    selectedImageIndex.value = 0
    await nextTick()
    setupThumbObserver()
    ensureCurrentImageBlob()
  }
  catch {
    errorMsg.value = '网络错误，请检查网络连接'
  }
  finally {
    loading.value = false
  }
}

// ==================== 选择和导航 ====================
function selectRecord(rec: BAHRecord) {
  selectedRecord.value = rec
  searchBah.value = rec.bah || ''
  images.value = []
  selectedImageIndex.value = 0
  loadImages()
}

function selectImage(idx: number, source: 'keyboard' | 'click' = 'keyboard') {
  selectedImageIndex.value = idx
  hideTypePicker()
  nextTick().then(() => scrollActiveThumbIntoView(source))
}

function goPrevImage() {
  if (filteredImages.value.length === 0 || selectedImageIndex.value <= 0) { return }
  selectImage(selectedImageIndex.value - 1, 'keyboard')
}

function goNextImage() {
  const total = filteredImages.value.length
  if (total === 0 || selectedImageIndex.value >= total - 1) { return }
  selectImage(selectedImageIndex.value + 1, 'keyboard')
}

function scrollActiveThumbIntoView(source: string = 'keyboard') {
  const container = thumbsContainer.value
  const idx = selectedImageIndex.value
  const el = thumbRefs.value[idx]
  if (!container || !el) { return }
  if (thumbsColumns.value > 1 && source !== 'keyboard') { return }
  if (container.scrollHeight <= container.clientHeight) { return }
  const targetTop = el.offsetTop - (container.clientHeight - el.clientHeight) / 2
  container.scrollTo({ top: Math.max(0, targetTop), behavior: 'smooth' })
}

// ==================== 键盘和滚轮 ====================
function isTextInputLike(el: Element | null) {
  if (!el) { return false }
  const tag = el.tagName.toLowerCase()
  if ((el as HTMLElement).isContentEditable) { return true }
  return tag === 'input' || tag === 'textarea' || tag === 'select'
}

function onKeyDown(e: KeyboardEvent) {
  if (e.altKey || e.ctrlKey || e.metaKey) { return }
  if (isTextInputLike(e.target as Element)) { return }
  if (isPreviewOpen.value && ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) { return }
  switch (e.key) {
    case 'ArrowLeft':
    case 'ArrowUp':
      e.preventDefault(); e.stopPropagation(); goPrevImage(); break
    case 'ArrowRight':
    case 'ArrowDown':
      e.preventDefault(); e.stopPropagation(); goNextImage(); break
  }
}

let wheelSwitchLocked = false
function onViewerWheel(e: WheelEvent) {
  if (wheelSwitchLocked) { return }
  const delta = e.deltaY || 0
  if (delta === 0) { return }
  wheelSwitchLocked = true
  if (delta > 0) {
    goNextImage()
  }
  else { goPrevImage() }
  setTimeout(() => { wheelSwitchLocked = false }, 200)
}

// ==================== 病案号范围判断 ====================
function isBahClickable(bah?: string) {
  if (!bah) { return false }
  const num = Number(String(bah).replace(/\D/g, ''))
  if (!Number.isFinite(num)) { return false }
  return num >= Number(startBah.value) && num <= Number(endBah.value)
}

// ==================== 类型选择 ====================
async function onSelectType(type: number | 'all') {
  const previous = selectedType.value
  selectedType.value = type
  selectedImageIndex.value = 0
  try {
    await nextTick()
    try {
      if (thumbsContainer.value) { thumbsContainer.value.scrollTo({ top: 0, left: 0, behavior: 'auto' }) }
    }
    catch {}
  }
  catch {
    selectedType.value = previous
    ElMessage.error('类型切换失败')
  }
}

// ==================== 工具函数 ====================
function getTypeName(type?: number | null) {
  const typeNames: Record<number, string> = {
    1: '01-病案首页',
    2: '02-病程记录',
    3: '03-手术记录',
    4: '04-术后病程录',
    5: '05-护理记录',
    6: '06-会诊单',
    7: '07-特殊检查',
    8: '08-检验单',
    9: '09-医嘱',
    10: '10-体温单',
    12: '12-出院记录',
    13: '13-大病历',
    14: '14-其它',
  }
  return type != null ? (typeNames[type] || `类型${type}`) : '-'
}

function formatDate(dateString?: string) {
  if (!dateString) { return '-' }
  return new Date(dateString).toLocaleString('zh-CN')
}

function retryLoad() { loadImages() }

// ==================== 图片事件 ====================
function onImageLoad(event: Event) { (event.target as HTMLImageElement).style.opacity = '1' }
function onImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgdmlld0JveD0iMCAwIDIwMCAyMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjIwMCIgaGVpZ2h0PSIyMDAiIGZpbGw9IiNGNUY1RjUiLz48L3N2Zz4='
  target.style.opacity = '0.5'
}

// ==================== 按需加载和预加载 ====================
async function ensureBlobForImage(img: GalleryImage) {
  if (!img || !img.cx || img.blobUrl) { return }
  try { img.blobUrl = img.cx }
  catch {}
}

function ensureCurrentImageBlob() {
  const img = currentImage.value
  if (img) { ensureBlobForImage(img) }
}

const preloadedUrlSet = new Set<string>()
async function preloadImageUrl(url: string) {
  try {
    if (!url || preloadedUrlSet.has(url)) { return }
    await new Promise<void>((resolve) => {
      try {
        const img = new Image()
        img.decoding = 'async'
        img.loading = 'eager'
        img.src = url
        if (typeof img.decode === 'function') {
          img.decode().then(() => resolve()).catch(() => resolve())
        }
        else {
          img.onload = () => resolve()
          img.onerror = () => resolve()
        }
      }
      catch { resolve() }
    })
    preloadedUrlSet.add(url)
  }
  catch {}
}

async function preloadAroundIndex(centerIndex: number, radius = 3) {
  const total = filteredImages.value.length
  if (total === 0) { return }
  const start = Math.max(0, centerIndex - radius)
  const end = Math.min(total - 1, centerIndex + radius)
  const tasks: Promise<void>[] = []
  for (let i = start; i <= end; i++) {
    const it = filteredImages.value[i]
    const url = it && (it.blobUrl || it.cx)
    if (url) { tasks.push(preloadImageUrl(url)) }
  }
  try { await Promise.all(tasks) }
  catch {}
}

let thumbIO: IntersectionObserver | null = null
function setupThumbObserver() {
  try { if (thumbIO) { thumbIO.disconnect(); thumbIO = null } }
  catch {}
  if (!('IntersectionObserver' in window)) {
    const preload = Math.min(12, filteredImages.value.length)
    for (let i = 0; i < preload; i++) { ensureBlobForImage(filteredImages.value[i]) }
    return
  }
  thumbIO = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const el = entry.target
        const idxAttr = el && (el as HTMLElement).getAttribute ? (el as HTMLElement).getAttribute('data-index') : null
        const idx = idxAttr ? Number(idxAttr) : -1
        const img = filteredImages.value[idx]
        if (img) { ensureBlobForImage(img) }
        if (thumbIO && el) { thumbIO.unobserve(el) }
      }
    }
  }, { root: thumbsContainer.value || null, rootMargin: '100px', threshold: 0.01 })
  nextTick(() => {
    thumbRefs.value.forEach((el) => {
      if (el && thumbIO) { thumbIO.observe(el) }
    })
  })
}

// ==================== 布局 ====================
function applyThumbsLayout() {
  if (thumbsLayoutMode.value === 'grid-1') {
    thumbsPaneWidth.value = Math.max(thumbsPaneMin, singleThumbMinWidth + 16)
  }
  else if (thumbsLayoutMode.value === 'grid-2') {
    thumbsPaneWidth.value = Math.min(thumbsPaneMax.value, singleThumbMinWidth * 2 + gridGap + 16)
  }
  else if (thumbsLayoutMode.value === 'grid-3') {
    thumbsPaneWidth.value = Math.min(thumbsPaneMax.value, singleThumbMinWidth * 3 + gridGap * 2 + 16)
  }
}

function onWindowResize() {
  try {
    if (thumbsContainer.value) { thumbsPaneWidth.value = thumbsContainer.value.clientWidth || thumbsPaneWidth.value }
  }
  catch {}
}

// ==================== 视图模式 ====================
function setThumbsViewMode(mode: 'icons' | 'details') {
  thumbsViewMode.value = mode
  try { localStorage.setItem(THUMBS_VIEW_MODE_KEY, mode) }
  catch {}
  nextTick().then(() => {
    setupThumbObserver()
    scrollActiveThumbIntoView('keyboard')
  })
}

// ==================== 类型修改 ====================
async function onPickType(newType: number) {
  if (!currentImage.value) { return }
  const img = currentImage.value
  if (img.btype === newType) { hideTypePicker(); return }
  if (!img.id) { ElMessage.error('无法识别图片ID，无法修改类型'); return }
  const prevType = img.btype
  img.btype = newType
  try {
    const res = await updateImageType(img.id, { btype: newType })
    const ok = res && (res.status === 200 || res.status === 204 || (res.data && res.data.code === 200))
    if (!ok) { throw new Error('更新失败') }
    hideTypePicker()
    ElMessage({
      type: 'success',
      dangerouslyUseHTMLString: true,
      message: `类型切换成功 <strong>P${img.pages}</strong> 从 <strong class="type-success-prev">${getTypeName(prevType)}</strong> 切换到 <strong class="type-success-new">${getTypeName(newType)}</strong>`,
      duration: 5000,
    })
  }
  catch { img.btype = prevType; ElMessage.error('修改类型失败，请重试') }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  try {
    const saved = localStorage.getItem(LAYOUT_MODE_KEY)
    if (saved === 'grid-1' || saved === 'grid-2' || saved === 'grid-3') { thumbsLayoutMode.value = saved }
  }
  catch {}
  applyThumbsLayout()
  try {
    const savedMode = localStorage.getItem(THUMBS_VIEW_MODE_KEY)
    if (savedMode === 'icons' || savedMode === 'details') { thumbsViewMode.value = savedMode }
  }
  catch {}
  window.addEventListener('keydown', onKeyDown, { passive: false })
  window.addEventListener('resize', onWindowResize)
  try {
    if (window.ResizeObserver && thumbsContainer.value) {
      const ro = new ResizeObserver(() => {
        try {
          if (thumbsContainer.value) { thumbsPaneWidth.value = thumbsContainer.value.clientWidth || thumbsPaneWidth.value }
        }
        catch {}
      })
      ro.observe(thumbsContainer.value)
    }
  }
  catch {}
  // 如果路由带了 bah 参数，直接加载
  if (searchBah.value) {
    loadImages()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('keydown', onKeyDown)
  try { if (thumbIO) { thumbIO.disconnect(); thumbIO = null } }
  catch {}
})

// ==================== 监听器 ====================
watch(filteredImages, async () => {
  await nextTick()
  const maxIndex = Math.max(0, filteredImages.value.length - 1)
  if (selectedImageIndex.value > maxIndex) { selectedImageIndex.value = 0 }
  try {
    if (thumbsContainer.value) { thumbsContainer.value.scrollTo({ top: 0, left: 0, behavior: 'auto' }) }
  }
  catch {}
  setupThumbObserver()
})

watch(thumbsLayoutMode, (mode) => {
  try { localStorage.setItem(LAYOUT_MODE_KEY, mode) }
  catch {}
})
watch(selectedImageIndex, () => { ensureCurrentImageBlob() })
</script>

<template>
  <!-- 图片画廊主容器 -->
  <div class="image-gallery">
    <!-- 分割布局：左侧面板 + 右侧面板 -->
    <div class="split-layout">
      <!-- 左侧面板：病案列表和类型筛选 -->
      <div class="left-pane">
        <!-- 上半部分：病案查询结果列表 -->
        <div class="left-top">
          <h3>
            <el-tooltip class="box-item" effect="dark" :content="contentNotice" placement="right-start">
              <span>查询出 {{ idSearchResults.length }} 份住院病案</span>
            </el-tooltip>
          </h3>
          <!-- 病案结果列表 -->
          <div v-if="idSearchResults.length" class="id-results">
            <div
              v-for="rec in idSearchResults"
              :key="rec.id"
              class="id-result-item"
              :class="{ active: selectedRecord && selectedRecord.bah === rec.bah, disabled: !isBahClickable(rec.bah) }"
              @click="isBahClickable(rec.bah) && selectRecord(rec)"
            >
              <div class="rec-row">
                <span class="rec-bah">{{ rec.bah }}</span>
                <span class="rec-dept">{{ rec.department }}</span>
              </div>
              <div class="rec-row">
                <span class="rec-name">{{ rec.name }}</span>
                <span class="rec-time">{{ formatDate(rec.admissionTime) }}</span>
              </div>
            </div>
          </div>
          <div v-else class="id-results-empty">
            暂无结果
          </div>
        </div>

        <!-- 下半部分：病案类型筛选 -->
        <div class="left-bottom">
          <div class="type-header">
            <h3>
              <el-tooltip class="box-item" effect="dark" content="OCR分类，可能存在部分图片分类错误" placement="right-start">
                <span>病案类型</span>
              </el-tooltip>
            </h3>
            <!-- 缩略图视图模式切换按钮 -->
            <div class="thumbs-toolbar">
              <button class="thumbs-mode-btn" :class="{ active: thumbsViewMode === 'icons' }" @click="setThumbsViewMode('icons')">
                缩略图
              </button>
              <button class="thumbs-mode-btn" :class="{ active: thumbsViewMode === 'details' }" @click="setThumbsViewMode('details')">
                列表
              </button>
            </div>
          </div>
          <!-- 类型列表 -->
          <div class="type-list">
            <div class="type-item" :class="{ active: selectedType === 'all' }" @click="onSelectType('all')">
              <span class="type-name">全部</span>
              <span class="type-count">{{ images.length }}</span>
            </div>
            <div
              v-for="item in typeDisplayList"
              :key="item.type"
              class="type-item"
              :class="{ active: selectedType === item.type, disabled: item.count === 0 }"
              @click="item.count && onSelectType(item.type)"
            >
              <span class="type-name">{{ getTypeName(item.type) }}</span>
              <span class="type-count">{{ item.count }}</span>
            </div>
            <div v-if="!typeDisplayList.length" class="type-empty">
              暂无类型
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧面板：图片查看器 -->
      <div class="right-pane">
        <!-- 图片查看器分割区域 -->
        <div ref="viewerSplitRef" class="viewer-split">
          <!-- 缩略图区域 -->
          <div ref="thumbsContainer" class="thumbs">
            <!-- 图标视图模式 -->
            <template v-if="thumbsViewMode === 'icons'">
              <div
                v-for="(img, idx) in filteredImages"
                :key="img.cx || img.id || idx"
                :ref="(el: any) => { thumbRefs[idx] = el }"
                class="thumb-item"
                :class="{ active: idx === selectedImageIndex }"
                :data-index="idx"
                @click="selectImage(idx, 'click')"
              >
                <el-image
                  :src="img.blobUrl || transparentPixel"
                  class="thumb-image"
                  fit="fill"
                  loading="lazy"
                  :preview-src-list="[]"
                  @load="onImageLoad"
                  @error="onImageError"
                />
                <div class="thumb-meta">
                  P{{ img.pages }} - {{ getTypeName(img.btype) }}
                </div>
              </div>
            </template>
            <!-- 列表视图模式 -->
            <template v-else>
              <div
                v-for="(img, idx) in filteredImages"
                :key="img.cx || img.id || idx"
                :ref="(el: any) => { thumbRefs[idx] = el }"
                class="thumb-row"
                :class="{ active: idx === selectedImageIndex }"
                :data-index="idx"
                @click="selectImage(idx, 'click')"
              >
                <div class="thumb-row-info">
                  <div class="thumb-row-title">
                    P{{ img.pages }} - {{ getTypeName(img.btype) }}
                  </div>
                </div>
              </div>
            </template>
            <div v-if="!filteredImages.length" class="thumbs-empty">
              暂无图片
            </div>
          </div>

          <!-- 主视图区域 -->
          <div class="main-view">
            <!-- 图片查看器容器 -->
            <div ref="viewerContainer" class="viewer-source" @wheel.prevent="onViewerWheel">
              <el-image
                v-if="currentImage"
                class="viewer-image-el"
                :src="currentImage.blobUrl || transparentPixel"
                fit="contain"
                :preview-src-list="previewList"
                :initial-index="selectedImageIndex"
                :z-index="3000"
                :hide-on-click-modal="false"
                :preview-teleported="true"
                @show="onViewerShow"
                @switch="onViewerSwitch"
                @close="onViewerClose"
              />
            </div>
            <!-- 图片信息显示和类型选择器 -->
            <div v-if="currentImage && !isPreviewOpen" class="main-meta">
              P{{ currentImage.pages }} - {{ getTypeName(currentImage.btype) }}
              <div v-if="showTypePicker" class="type-picker" @click.stop>
                <div class="type-picker-title">
                  切换类型
                </div>
                <div class="type-options">
                  <div
                    v-for="t in typeOptions"
                    :key="t"
                    class="type-option"
                    :class="{ active: currentImage && currentImage.btype === t }"
                    @click.stop="onPickType(t)"
                  >
                    {{ getTypeName(t) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="isLoading" class="loading">
          <div class="spinner" />
          <p>加载中...</p>
        </div>

        <!-- 错误状态 -->
        <div v-if="errorMsg" class="error">
          <p>{{ errorMsg }}</p>
          <button class="retry-btn" @click="retryLoad">
            重试
          </button>
        </div>
      </div>
    </div>

    <!-- 全屏预览时的图片信息显示 -->
    <div v-if="isPreviewOpen && (currentPreviewImage || currentImage)" class="fullscreen-caption">
      P{{ (currentPreviewImage || currentImage)!.pages }} - {{ getTypeName((currentPreviewImage || currentImage)!.btype) }}
    </div>

    <!-- 下载状态遮罩 -->
    <div v-if="downloading" class="download-status">
      <div class="download-status-content">
        <div class="download-spinner-large" />
        <p>正在下载病案压缩包...</p>
        <p class="download-tip">
          请稍候，文件较大可能需要一些时间
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ==================== 基础布局 ==================== */
.image-gallery {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: calc(100vh - 60px);
  padding: 20px;
  margin: 0;
  overflow: hidden;
}

.split-layout {
  display: flex;
  flex: 1 1 auto;
  gap: 20px;
  min-height: 0;
}

/* ==================== 左侧面板 ==================== */
.left-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 30%;
  min-width: 280px;
  min-height: 0;
  overflow: hidden;
}

.left-top,
.left-bottom {
  display: flex;
  flex-direction: column;
  min-height: 220px;
  padding: 12px;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.left-top {
  flex: 1 1 50%;
}

.left-bottom {
  flex: 1 1 50%;
}

.left-top h3,
.left-bottom h3 {
  margin: 0 0 10px;
}

/* ==================== 病案列表 ==================== */
.id-results {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}

.id-results-empty {
  font-size: 14px;
  color: #94a3b8;
}

.id-result-item {
  padding: 8px;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.id-result-item:hover {
  background: #f8fafc;
}

.id-result-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.id-result-item.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.rec-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #1e293b;
}

.rec-bah {
  font-weight: 600;
}

.rec-dept,
.rec-time {
  color: #64748b;
}

/* ==================== 类型筛选 ==================== */
.type-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.thumbs-toolbar {
  display: flex;
  gap: 6px;
}

.thumbs-mode-btn {
  padding: 4px 10px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.thumbs-mode-btn.active,
.thumbs-mode-btn:hover {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.type-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  overflow: auto;
}

.type-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 10px;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.type-item:hover {
  background: #f8fafc;
}

.type-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.type-item.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.type-name {
  font-size: 12px;
  color: #1e293b;
}

.type-count {
  font-size: 12px;
  color: #64748b;
}

.type-empty {
  color: #94a3b8;
}

/* ==================== 右侧面板 ==================== */
.right-pane {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.viewer-split {
  display: flex;
  flex: 1 1 auto;
  gap: 0;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

/* ==================== 缩略图区域 ==================== */
.thumbs {
  display: grid;
  gap: 8px;
  align-content: start;
  width: 200px;
  min-width: 200px;
  max-width: 360px;
  height: 100%;
  padding: 8px;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  -webkit-overflow-scrolling: touch;
}

.thumb-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding: 6px;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.thumb-item:hover {
  background: #e2e8f0;
}

.thumb-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.thumb-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #f0f4f8;
  border-radius: 4px;
  opacity: 1;
  transition: opacity 0.3s ease;
}

.thumb-meta {
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
}

.thumbs-empty {
  margin-top: 20px;
  color: #94a3b8;
  text-align: center;
}

/* 列表视图模式 */
.thumb-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 6px;
  cursor: pointer;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.thumb-row:hover {
  background: #e2e8f0;
}

.thumb-row.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.thumb-row-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

/* ==================== 主视图 ==================== */
.main-view {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #fff;
}

.viewer-source {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f4f8;
}

.viewer-image-el {
  width: auto;
  max-width: 100%;
  height: 100%;
  max-height: 100%;
  object-fit: contain;
}

/* ==================== 图片信息 ==================== */
.main-meta {
  position: absolute;
  right: 10px;
  bottom: 10px;
  z-index: 3;
  padding: 8px 14px;
  font-size: 12px;
  color: #fff;
  pointer-events: none;
  background: rgb(59 130 246 / 85%);
  border-radius: 20px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 15%);
  opacity: 0.9;
  backdrop-filter: blur(4px);
}

/* ==================== 类型选择器 ==================== */
.type-picker {
  position: absolute;
  right: 0;
  bottom: 36px;
  min-width: 180px;
  padding: 8px;
  color: #fff;
  background: #1e3a5f;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 20%);
}

.type-picker-title {
  margin-bottom: 6px;
  font-size: 12px;
  opacity: 0.8;
}

.type-options {
  display: grid;
  grid-template-columns: 1fr;
  gap: 4px;
  max-height: 50vh;
  overflow: auto;
}

.type-option {
  padding: 6px 8px;
  font-size: 13px;
  cursor: pointer;
  background: rgb(255 255 255 / 6%);
  border-radius: 6px;
  transition: background 0.2s ease;
}

.type-option:hover {
  background: rgb(255 255 255 / 12%);
}

.type-option.active {
  background: rgb(59 130 246 / 40%);
}

/* ==================== 全屏预览 ==================== */
.fullscreen-caption {
  position: fixed;
  right: 20px;
  bottom: 30px;
  z-index: 4000;
  padding: 8px 14px;
  font-size: 12px;
  color: #fff;
  pointer-events: none;
  background: rgb(59 130 246 / 85%);
  border-radius: 20px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 15%);
  opacity: 0.9;
  backdrop-filter: blur(4px);
}

/* ==================== 状态 ==================== */
.loading {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #64748b;
  background: rgb(255 255 255 / 80%);
  backdrop-filter: blur(2px);
}

.spinner {
  width: 40px;
  height: 40px;
  margin-bottom: 20px;
  border: 4px solid #e2e8f0;
  border-top: 4px solid var(--el-color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error {
  padding: 40px;
  color: var(--el-color-danger);
  text-align: center;
}

.retry-btn {
  padding: 10px 20px;
  margin-top: 15px;
  color: #fff;
  cursor: pointer;
  background: var(--el-color-danger);
  border: none;
  border-radius: 6px;
  transition: opacity 0.2s ease;
}

.retry-btn:hover {
  opacity: 0.85;
}

/* ==================== 下载状态 ==================== */
.download-status {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 45%);
}

.download-status-content {
  width: 90%;
  max-width: 400px;
  padding: 30px;
  text-align: center;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 16px 48px rgb(0 0 0 / 20%);
}

.download-spinner-large {
  width: 50px;
  height: 50px;
  margin: 0 auto 20px;
  border: 4px solid #e2e8f0;
  border-top: 4px solid var(--el-color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.download-status-content p {
  margin: 10px 0;
  font-size: 16px;
  color: #1e293b;
}

.download-tip {
  margin-top: 15px;
  font-size: 14px !important;
  color: #64748b !important;
}

/* ==================== 响应式 ==================== */
@media (width <= 1200px) {
  .split-layout { gap: 15px; }
  .left-pane { min-width: 250px; }
}

@media (width <= 992px) {
  .image-gallery { padding: 15px; }
  .split-layout { gap: 10px; }
  .left-pane { min-width: 220px; }
  .type-list { grid-template-columns: 1fr; }
}

@media (width <= 768px) {
  .split-layout { flex-direction: column; }
  .left-pane { width: 100%; }
  .thumbs { width: 100px; min-width: 100px; }
}

@media (width <= 480px) {
  .image-gallery { padding: 10px; }
  .split-layout { gap: 8px; }

  .left-pane,
  .right-pane { gap: 8px; }

  .left-top,
  .left-bottom { padding: 10px; }
  .type-header { flex-direction: column; gap: 8px; align-items: flex-start; }
  .thumbs-toolbar { align-self: flex-end; }
}

/* ==================== 类型修改消息样式 ==================== */
:global(.type-success-prev) { color: var(--el-color-danger); }
:global(.type-success-new) { color: var(--el-color-success); }
</style>

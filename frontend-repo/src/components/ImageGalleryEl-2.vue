<template>
  <div class="image-gallery">
    <div class="split-layout">
      <div class="left-pane">
        <div class="left-top">
          <h3>
            <el-tooltip class="box-item" effect="dark" content="查询 2018 - 2020 期间的住院记录，目前可访问的病案区间为 [00787327, 00855320]"
              placement="right-start">
              <span>查询出 {{ idSearchResults.length }} 份住院病案</span>
            </el-tooltip>
          </h3>
          <div class="id-results" v-if="idSearchResults.length">
            <div v-for="rec in idSearchResults" :key="rec.id" class="id-result-item"
              :class="{ active: selectedRecord && selectedRecord.bah === rec.bah, disabled: !isBahClickable(rec.bah) }"
              @click="isBahClickable(rec.bah) && selectRecord(rec)">
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
          <div class="id-results-empty" v-else>暂无结果</div>
        </div>
        <div class="left-bottom">
          <div class="type-header">
            <h3>
              <el-tooltip class="box-item" effect="dark" content="OCR分类，可能存在部分图片分类错误" placement="right-start">
                <span>病案类型</span>
              </el-tooltip>
            </h3>
            <div class="thumbs-toolbar">
              <button class="thumbs-mode-btn" :class="{ active: thumbsViewMode === 'icons' }"
                @click="setThumbsViewMode('icons')">缩略图</button>
              <button class="thumbs-mode-btn" :class="{ active: thumbsViewMode === 'details' }"
                @click="setThumbsViewMode('details')">列表</button>
              <button class="print-btn" @click="printSelectedImages" :disabled="selectedImages.size === 0">
                打印选中({{ selectedImages.size }})
              </button>
            </div>
          </div>
          <div class="type-list">
            <div class="type-item" :class="{ active: selectedType === 'all' }" @click="onSelectType('all')">
              <span class="type-name">全部</span>
              <span class="type-count">{{ images.length }}</span>
            </div>
            <div v-for="item in typeDisplayList" :key="item.type" class="type-item"
              :class="{ active: selectedType === item.type, disabled: item.count === 0 }"
              @click="item.count && onSelectType(item.type)">
              <span class="type-name">{{ getTypeName(item.type) }}</span>
              <span class="type-count">{{ item.count }}</span>
            </div>
            <div v-if="!typeDisplayList.length" class="type-empty">暂无类型</div>
          </div>
        </div>
      </div>
      <div class="right-pane">
        <div class="viewer-split" ref="viewerSplitRef">
          <div class="thumbs" ref="thumbsContainer">

            <template v-if="thumbsViewMode === 'icons'">
              <div v-for="(img, idx) in filteredImages" :key="img.cx || img.id || idx" class="thumb-item"
                :class="{ active: idx === selectedImageIndex }" :ref="el => thumbRefs[idx] = el" :data-index="idx"
                @click="selectImage(idx, 'click')">
                <div class="thumb-selection" 
                     :class="{ selected: selectedImages.has(img.cx || img.id) }"
                     @click.stop="toggleImageSelection(img)">
                  <div class="selection-indicator"></div>
                </div>
                <el-image :src="img.blobUrl || transparentPixel" class="thumb-image" fit="fill" loading="lazy"
                  @load="onImageLoad" @error="onImageError" :preview-src-list="[]" />
                <div class="thumb-meta">P{{ img.pages }} - {{ getTypeName(img.btype) }} </div>
              </div>
            </template>
            <template v-else>
              <div v-for="(img, idx) in filteredImages" :key="img.cx || img.id || idx" class="thumb-row"
                :class="{ active: idx === selectedImageIndex }" :ref="el => thumbRefs[idx] = el" :data-index="idx"
                @click="selectImage(idx, 'click')">
                <div class="thumb-selection" 
                     :class="{ selected: selectedImages.has(img.cx || img.id) }"
                     @click.stop="toggleImageSelection(img)">
                  <div class="selection-indicator"></div>
                </div>
                <div class="thumb-row-info">
                  <div class="thumb-row-title">P{{ img.pages }} - {{ getTypeName(img.btype) }}</div>
                </div>
              </div>
            </template>
            <div v-if="!filteredImages.length" class="thumbs-empty">暂无图片</div>
          </div>

          <div class="main-view">
            <div class="viewer-source" ref="viewerContainer" @wheel.prevent="onViewerWheel">
              <el-image v-if="currentImage" class="viewer-image-el" :src="currentImage.blobUrl || transparentPixel"
                fit="contain" :preview-src-list="previewList" :initial-index="selectedImageIndex" :z-index="3000"
                :hide-on-click-modal="false" :preview-teleported="true" @show="onViewerShow" @switch="onViewerSwitch"
                @close="onViewerClose" />
            </div>
            <!-- <div v-if="currentImage" class="main-meta" @click.stop="toggleTypePicker"> -->
            <div v-if="currentImage && !isPreviewOpen" class="main-meta">
              P{{ currentImage.pages }} - {{ getTypeName(currentImage.btype) }}
              <div v-if="showTypePicker" class="type-picker" @click.stop>
                <div class="type-picker-title">切换类型</div>
                <div class="type-options">
                  <div v-for="t in typeOptions" :key="t" class="type-option"
                    :class="{ active: currentImage && currentImage.btype === t }" @click.stop="onPickType(t)">
                    {{ getTypeName(t) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="isLoading" class="loading">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-if="error" class="error">
          <p>{{ error }}</p>
          <button @click="retryLoad" class="retry-btn">重试</button>
        </div>
      </div>
    </div>

    <div v-if="isPreviewOpen && (currentPreviewImage || currentImage)" class="fullscreen-caption">
      P{{ (currentPreviewImage || currentImage).pages }} - {{ getTypeName((currentPreviewImage || currentImage).btype)
      }}
    </div>

    <div v-if="downloading" class="download-status">
      <div class="download-status-content">
        <div class="download-spinner-large"></div>
        <p>正在下载病案压缩包...</p>
        <p class="download-tip">请稍候，文件较大可能需要一些时间</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getImgApiByBah, downloadBah, getImg, getBAHByIdCard, updateImgType } from '@/utils/api'
import { en } from 'element-plus/es/locales.mjs'
import { clearSession } from '@/utils/session'

const props = defineProps({
  bah: { type: String, default: '' },
  idCard: { type: String, default: '' }
})

const images = ref([])
const loading = ref(false)
const error = ref('')
const selectedType = ref('all')
const searchBah = ref(props.bah)
const isValidBah = computed(() => /^\d{8}$/.test(searchBah.value))
const downloading = ref(false)
const selectedImages = ref(new Set())

const searchIdCard = ref('')
const idSearchResults = ref([])
const idSearchLoading = ref(false)
const selectedRecord = ref(null)

const selectedImageIndex = ref(0)
const viewerContainer = ref(null)
const thumbsContainer = ref(null)
const viewerSplitRef = ref(null)
const thumbRefs = ref([])
let resizing = false
let prevUserSelect = ''
const thumbsPaneMin = 130
const gridGap = 8
const thumbsPaneWidth = ref(200)
const THUMBS_VIEW_MODE_KEY = 'pmr.thumbsViewMode'
const thumbsViewMode = ref('icons') // 'icons' | 'details'

const showTypePicker = ref(false)
const typeOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14]

const toggleTypePicker = () => { showTypePicker.value = !showTypePicker.value }
const hideTypePicker = () => { showTypePicker.value = false }
const handleDocumentClick = () => { hideTypePicker() }
onMounted(() => { document.addEventListener('click', handleDocumentClick) })
onUnmounted(() => { document.removeEventListener('click', handleDocumentClick) })

const transparentPixel = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=='
const blobUrlCache = new Map()
const inFlightFetches = new Map()
const requestQueue = []
const maxConcurrent = 6
let activeCount = 0
const pendingFetches = ref(0)

// 起始病案号
const startBah = ref('00787327')
// 结束病案号
const endBah = ref('00855320')

const resetBlobResources = () => {
  try { for (const url of blobUrlCache.values()) URL.revokeObjectURL(url) } catch (e) {}
  blobUrlCache.clear(); inFlightFetches.clear(); requestQueue.length = 0; activeCount = 0; pendingFetches.value = 0
}

const processQueue = () => {
  while (activeCount < maxConcurrent && requestQueue.length > 0) {
    const task = requestQueue.shift(); if (!task) break
    const { cx, resolve, reject } = task
    activeCount++; pendingFetches.value++
    getImg(cx, { responseType: 'blob' })
      .then(res => {
        const url = URL.createObjectURL(res.data)
        blobUrlCache.set(cx, url); resolve(url)
      })
      .catch(err => { reject(err) })
      .finally(() => { activeCount--; pendingFetches.value--; processQueue() })
  }
}

const enqueueFetch = (cx) => new Promise((resolve, reject) => { requestQueue.push({ cx, resolve, reject }); processQueue() })
const getBlobUrlByCx = (cx) => {
  // 直接返回原始图片地址，无需通过 getImg 获取 blob
  if (!cx) return Promise.resolve(null)
  return Promise.resolve(cx)
}

const filteredImages = computed(() => selectedType.value === 'all' ? images.value : images.value.filter(img => img.btype === selectedType.value))
const isLoading = computed(() => loading.value)
const typeDisplayList = computed(() => {
  const counts = new Map(); for (const img of images.value) { const t = img.btype; counts.set(t, (counts.get(t) || 0) + 1) }
  const allTypes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14]
  return allTypes.map(type => ({ type, count: counts.get(type) || 0 }))
})

const extractCxFromImgUrl = (fullUrl) => {
  if (!fullUrl || typeof fullUrl !== 'string') return fullUrl
  const marker = '/img-api/image/'
  const index = fullUrl.indexOf(marker)
  if (index === -1) return fullUrl
  return fullUrl.substring(index + marker.length)
}

const loadImages = async () => {
  loading.value = true; error.value = ''
  try {
    const response = await getImgApiByBah(searchBah.value)
    if (response.data.code === 200) {
      resetBlobResources()
      const rawList = Array.isArray(response.data.data) ? response.data.data : []
      images.value = rawList.map(item => {
        const originalUrl = item?.img_url || ''
        const cx = originalUrl
        // 直接使用 originalUrl 作为展示地址
        return { ...item, cx, blobUrl: originalUrl }
      })
      selectedType.value = 'all'
      selectedImageIndex.value = 0
      await nextTick()
      // 按需加载：不一次性请求所有图片
      setupThumbObserver()
      ensureCurrentImageBlob()
    } else {
      error.value = response.data.message || '获取数据失败'
    }
  } catch (err) {
    error.value = '网络错误，请检查网络连接'
  } finally {
    loading.value = false
  }
}

const searchByIdCard = async () => {
  if (!searchIdCard.value) { idSearchResults.value = []; return }
  idSearchLoading.value = true
  try {
    const res = await getBAHByIdCard(searchIdCard.value)
    if (res.data && res.data.code === 200) {
      idSearchResults.value = Array.isArray(res.data.data) ? res.data.data : []
      // 默认选中病案号是 bah 的病案
      const firstRecordInRange = idSearchResults.value.find(record =>
        record.bah === props.bah
      )
      if (firstRecordInRange) selectRecord(firstRecordInRange)
      // 选择第一条记录
      // if (idSearchResults.value.length > 0) selectRecord(idSearchResults.value[0])
    } else { idSearchResults.value = [] }
  } catch (e) { idSearchResults.value = [] } finally { idSearchLoading.value = false }
}

const selectRecord = (rec) => { 
  selectedRecord.value = rec; 
  searchBah.value = rec.bah; 
  images.value = []; 
  selectedImageIndex.value = 0; 
  selectedImages.value.clear(); // 清空选中图片
  loadImages() 
}

const selectImage = (idx, source = 'keyboard') => { selectedImageIndex.value = idx; hideTypePicker(); nextTick().then(() => scrollActiveThumbIntoView(source)) }

const goPrevImage = () => { if (filteredImages.value.length === 0) return; if (selectedImageIndex.value <= 0) return; selectImage(selectedImageIndex.value - 1, 'keyboard') }
const goNextImage = () => { const total = filteredImages.value.length; if (total === 0) return; if (selectedImageIndex.value >= total - 1) return; selectImage(selectedImageIndex.value + 1, 'keyboard') }

const scrollActiveThumbIntoView = (source = 'keyboard') => {
  const container = thumbsContainer.value; const idx = selectedImageIndex.value; const el = thumbRefs.value[idx]
  if (!container || !el) return
  if (thumbsColumns.value > 1 && source !== 'keyboard') return
  if (container.scrollHeight <= container.clientHeight) return
  const targetTop = el.offsetTop - (container.clientHeight - el.clientHeight) / 2
  container.scrollTo({ top: Math.max(0, targetTop), behavior: 'smooth' })
}

const isTextInputLike = (el) => {
  const tag = el && el.tagName ? el.tagName.toLowerCase() : ''
  if (el && typeof el.isContentEditable === 'boolean' && el.isContentEditable) return true
  return tag === 'input' || tag === 'textarea' || tag === 'select'
}

// 全屏预览是否打开（响应式）
const isPreviewOpen = ref(false)

// 记录全屏预览当前索引，用于关闭时同步
const previewCurrentIndex = ref(0)
const currentPreviewImage = computed(() => filteredImages.value[previewCurrentIndex.value] || null)

// 预览显示时，初始化索引为当前选中图
const onViewerShow = async () => { 
  isPreviewOpen.value = true; 
  previewCurrentIndex.value = selectedImageIndex.value; 
  // 进入全屏时预加载相邻图片
  await preloadAroundIndex(previewCurrentIndex.value, 3)
}

// Element Plus el-image 预览切换事件回调
const onViewerSwitch = async (newIndex) => {
  // newIndex: 当前预览索引
  if (typeof newIndex === 'number' && Number.isFinite(newIndex)) {
    previewCurrentIndex.value = newIndex
    // 切换时继续预加载相邻图片
    await preloadAroundIndex(previewCurrentIndex.value, 3)
  }
}

// 预览关闭时，同步索引并滚动定位
const onViewerClose = () => {
  const idx = previewCurrentIndex.value
  if (typeof idx === 'number' && idx >= 0 && idx < filteredImages.value.length) {
    selectImage(idx, 'keyboard')
  }
  isPreviewOpen.value = false
}

const onKeyDown = (e) => {
  if (e.altKey || e.ctrlKey || e.metaKey) return
  if (isTextInputLike(e.target)) return
  // 在全屏预览时，禁止使用方向键在列表中切换图片，避免与预览冲突
  if (isPreviewOpen.value && (
    e.key === 'ArrowUp' || e.key === 'ArrowDown' || e.key === 'ArrowLeft' || e.key === 'ArrowRight'
  )) return
  switch (e.key) {
    case 'ArrowLeft': 
    case 'ArrowUp': e.preventDefault(); e.stopPropagation(); goPrevImage(); break
    case 'ArrowRight': 
    case 'ArrowDown': e.preventDefault(); e.stopPropagation(); goNextImage(); break
    default: break
  }
}

// 病案号点击权限：限定区间 [00787327, 00855320]
const isBahClickable = (bah) => {
  if (!bah) return false
  const num = Number(String(bah).replace(/\D/g, ''))
  if (!Number.isFinite(num)) return false
  // 需要后续进行更改
  return num >= startBah.value && num <= endBah.value
}

// 鼠标滚轮切换图片（主视图）
let wheelSwitchLocked = false
const onViewerWheel = (e) => {
  if (wheelSwitchLocked) return
  const delta = e && typeof e.deltaY === 'number' ? e.deltaY : 0
  if (delta === 0) return
  wheelSwitchLocked = true
  if (delta > 0) goNextImage(); else goPrevImage()
  setTimeout(() => { wheelSwitchLocked = false }, 200)
}

const onSelectType = async (type) => {
  const previous = selectedType.value
  selectedType.value = type
  selectedImageIndex.value = 0
  try {
    // ElMessage.success({ message: `已切换到${type === 'all' ? '全部类型' : getTypeName(type)}` })
    await nextTick()
    try { if (thumbsContainer.value) thumbsContainer.value.scrollTo({ top: 0, left: 0, behavior: 'auto' }) } catch (e) {}
  } catch (e) {
    selectedType.value = previous
    ElMessage.error('类型切换失败')
  }
}

const getTypeName = (type) => {
  const typeNames = { 1: '01-病案首页', 2: '02-病程记录', 3: '03-手术记录', 4: '04-术后病程录', 5: '05-护理记录', 6: '06-会诊单', 7: '07-特殊检查', 8: '08-检验单', 9: '09-医嘱', 10: '10-体温单', 12: '12-出院记录', 13: '13-大病历', 14: '14-其它' }
  return typeNames[type] || `类型${type}`
}

const formatDate = (dateString) => { const date = new Date(dateString); return date.toLocaleString('zh-CN') }
const retryLoad = () => { loadImages() }

const downloadBahZip = async () => {
  if (!searchBah.value.trim()) { ElMessage.warning('请先输入病案号'); return }
  if (images.value.length === 0) { ElMessage.warning('没有找到相关病案数据，无法下载'); return }
  downloading.value = true
  try {
    const response = await downloadBah(searchBah.value, { responseType: 'blob', timeout: 60000 })
    if (response.status !== 200) throw new Error('下载失败：服务器响应错误')
    const fileSize = response.data.size
    if (fileSize === 0) throw new Error('下载失败：文件为空')
    const blob = new Blob([response.data], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = `病案_${searchBah.value}_${new Date().toISOString().slice(0, 10)}.zip`
    document.body.appendChild(link); link.click(); document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (err) {
    let errorMessage = '下载失败，请重试'
    if (err?.response) {
      if (err.response.status === 404) errorMessage = '未找到该病案的压缩包'
      else if (err.response.status === 500) errorMessage = '服务器错误，请稍后重试'
      else errorMessage = `下载失败 (${err.response.status})`
    } else if (err?.code === 'ECONNABORTED') errorMessage = '下载超时，请检查网络连接'
    else if (err?.message) errorMessage = err.message
    ElMessage.error(errorMessage)
  } finally { downloading.value = false }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    clearSession(); window.location.reload()
  } catch (e) { /* 用户取消 */ }
}

const onImageLoad = (event) => { event.target.style.opacity = '1' }
const onImageError = (event) => { event.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgdmlld0JveD0iMCAwIDIwMCAyMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIyMDAiIGhlaWdodD0iMjAwIiBmaWxsPSIjRjVGNUY1Ii8+CjxwYXRoIGQ9Ik04MCAxMDBDODAgODkuNTQ0NyA4OC4wMDAxIDgxIDEwMCA4MUMxMTEuOTU2IDgxIDEyMCA4OS41NDQ3IDEyMCAxMEMxMjAgMTEwLjQ1NSAxMTEuOTU2IDExOSAxMDAgMTE5Qzg4LjAwMDEgMTE5IDgwIDExMC40NTUgODAgMTAwWiIgZmlsbD0iI0NDQ0NDQyIvPgo8L3N2Zz4K'; event.target.style.opacity = '0.5' }

const currentImage = computed(() => filteredImages.value[selectedImageIndex.value] || null)

const singleThumbMinWidth = 130
const thumbsColumns = computed(() => { const columns = Math.floor(thumbsPaneWidth.value / singleThumbMinWidth); if (columns < 1) return 1; if (columns > 3) return 3; return columns })
const thumbsPaneMax = computed(() => singleThumbMinWidth * 3 + gridGap * 2 + 16)
const LAYOUT_MODE_KEY = 'pmr.thumbsLayoutMode'
const thumbsLayoutMode = ref('grid-1')
const layoutTitle = computed(() => {
  switch (thumbsLayoutMode.value) {
    case 'grid-1': return '切换到两列缩略图'
    case 'grid-2': return '切换到三列缩略图'
    case 'grid-3': return '切换到单列缩略图'
    default: return '切换布局'
  }
})

const applyThumbsLayout = () => {
  if (thumbsLayoutMode.value === 'grid-1') {
    thumbsPaneWidth.value = Math.max(thumbsPaneMin, singleThumbMinWidth + 16)
  } else if (thumbsLayoutMode.value === 'grid-2') {
    thumbsPaneWidth.value = Math.min(thumbsPaneMax.value, singleThumbMinWidth * 2 + gridGap + 16)
  } else if (thumbsLayoutMode.value === 'grid-3') {
    thumbsPaneWidth.value = Math.min(thumbsPaneMax.value, singleThumbMinWidth * 3 + gridGap * 2 + 16)
  }
}
const cycleThumbsLayout = () => { if (thumbsLayoutMode.value === 'grid-1') thumbsLayoutMode.value = 'grid-2'; else if (thumbsLayoutMode.value === 'grid-2') thumbsLayoutMode.value = 'grid-3'; else thumbsLayoutMode.value = 'grid-1'; applyThumbsLayout() }

const onWindowResize = () => {
  // 同步左侧面板实际宽度用于计算列数
  try { if (thumbsContainer.value) thumbsPaneWidth.value = thumbsContainer.value.clientWidth || thumbsPaneWidth.value } catch (e) {}
}

// 按需加载：仅当缩略图进入可视区域或被选中时请求 blob
let thumbIO = null
const ensureBlobForImage = async (img) => {
  if (!img || !img.cx || img.blobUrl) return
  try { const url = await getBlobUrlByCx(img.cx); img.blobUrl = url } catch (e) {}
}
const ensureCurrentImageBlob = () => {
  const img = currentImage.value
  if (img) ensureBlobForImage(img)
}

// 预加载相邻图片到浏览器缓存，减少全屏切换时闪屏
const preloadedUrlSet = new Set()
const preloadImageUrl = async (url) => {
  try {
    if (!url || preloadedUrlSet.has(url)) return
    await new Promise((resolve) => {
      try {
        const img = new Image()
        img.decoding = 'async'
        img.loading = 'eager'
        img.src = url
        if (typeof img.decode === 'function') {
          img.decode().then(() => resolve()).catch(() => resolve())
        } else {
          img.onload = () => resolve()
          img.onerror = () => resolve()
        }
      } catch (e) { resolve() }
    })
    preloadedUrlSet.add(url)
  } catch (e) {}
}
const preloadAroundIndex = async (centerIndex, radius = 3) => {
  const total = filteredImages.value.length
  if (total === 0) return
  const start = Math.max(0, centerIndex - radius)
  const end = Math.min(total - 1, centerIndex + radius)
  const tasks = []
  for (let i = start; i <= end; i++) {
    const it = filteredImages.value[i]
    const url = it && (it.blobUrl || it.cx)
    if (url) tasks.push(preloadImageUrl(url))
  }
  try { await Promise.all(tasks) } catch (e) {}
}
const setupThumbObserver = () => {
  try { if (thumbIO) { thumbIO.disconnect(); thumbIO = null } } catch (e) {}
  if (!('IntersectionObserver' in window)) {
    // 无 IO 时，退化为预加载前若干张
    const preload = Math.min(12, filteredImages.value.length)
    for (let i = 0; i < preload; i++) ensureBlobForImage(filteredImages.value[i])
    return
  }
  thumbIO = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const el = entry.target
        const idxAttr = el && el.getAttribute ? el.getAttribute('data-index') : null
        const idx = idxAttr ? Number(idxAttr) : -1
        const img = filteredImages.value[idx]
        if (img) ensureBlobForImage(img)
        if (thumbIO && el) thumbIO.unobserve(el)
      }
    }
  }, { root: thumbsContainer.value || null, rootMargin: '100px', threshold: 0.01 })
  // 观察当前渲染的缩略图元素
  nextTick(() => {
    thumbRefs.value.forEach((el) => { if (el && thumbIO) thumbIO.observe(el) })
  })
}

const getIdCardFromUrl = () => {
  try {
    const params = new URLSearchParams(window.location.search)
    const v = params.get('idCard') || params.get('idcard') || params.get('id')
    if (v) return v
    const segments = (window.location.pathname || '').split('/').filter(Boolean)
    const last = segments[segments.length - 1] || ''
    if (/^[0-9Xx]{15,18}$/.test(last)) return last
  } catch (e) {}
  return ''
}

onMounted(async () => {
  const urlIdCard = props.idCard || getIdCardFromUrl()
  try { const saved = localStorage.getItem(LAYOUT_MODE_KEY); const allowed = new Set(['grid-1','grid-2','grid-3']); if (saved && allowed.has(saved)) { thumbsLayoutMode.value = saved } } catch (e) {}
  applyThumbsLayout()
  // 读取缩略图视图模式
  try { const savedMode = localStorage.getItem(THUMBS_VIEW_MODE_KEY); if (savedMode === 'icons' || savedMode === 'details') thumbsViewMode.value = savedMode } catch (e) {}
  window.addEventListener('keydown', onKeyDown, { passive: false })
  window.addEventListener('resize', onWindowResize)
  // 监听缩略图容器尺寸变化，动态更新列数计算参考宽度
  try {
    if (window.ResizeObserver && thumbsContainer.value) {
      const ro = new ResizeObserver(() => {
        try { thumbsPaneWidth.value = thumbsContainer.value.clientWidth || thumbsPaneWidth.value } catch (e) {}
      })
      ro.observe(thumbsContainer.value)
    }
  } catch (e) {}
  if (urlIdCard) {
    searchIdCard.value = String(urlIdCard)
    await searchByIdCard()
    if (idSearchResults.value.length === 1) { selectRecord(idSearchResults.value[0]); return }
  } else if (isValidBah.value) { loadImages() }
})

onUnmounted(() => {
  resetBlobResources()
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('keydown', onKeyDown)
  try { if (thumbIO) { thumbIO.disconnect(); thumbIO = null } } catch (e) {}
  selectedImages.value.clear() // 清理选中状态
})

watch(filteredImages, async () => {
  await nextTick()
  const maxIndex = Math.max(0, filteredImages.value.length - 1)
  if (selectedImageIndex.value > maxIndex) selectedImageIndex.value = 0
  try { if (thumbsContainer.value) thumbsContainer.value.scrollTo({ top: 0, left: 0, behavior: 'auto' }) } catch (e) {}
  setupThumbObserver()
})

watch(thumbsLayoutMode, (mode) => { try { localStorage.setItem(LAYOUT_MODE_KEY, mode) } catch (e) {} })
// 监听路由参数 idCard 变化
watch(() => props.idCard, async (newId) => {
  if (newId && typeof newId === 'string') {
    searchIdCard.value = String(newId)
    await searchByIdCard()
    if (idSearchResults.value.length === 1) selectRecord(idSearchResults.value[0])
  }
})
const setThumbsViewMode = (mode) => {
  if (mode !== 'icons' && mode !== 'details') return
  thumbsViewMode.value = mode
  try { localStorage.setItem(THUMBS_VIEW_MODE_KEY, mode) } catch (e) {}
  // 视图模式变化后，需重新绑定观察器
  nextTick().then(() => { 
    setupThumbObserver()
    // 切换视图后将滚动定位到当前选中的图片
    scrollActiveThumbIntoView('keyboard')
  })
}

const previewList = computed(() => filteredImages.value.map(img => img.blobUrl || transparentPixel))

const onPickType = async (newType) => {
  if (!currentImage.value) return
  const img = currentImage.value
  if (img.btype === newType) { hideTypePicker(); return }
  const imageId = img.id
  if (!imageId) { ElMessage.error('无法识别图片ID，无法修改类型'); return }
  const prevType = img.btype
  img.btype = newType
  try {
    const res = await updateImgType(imageId, { btype: String(newType) })
    const ok = (res && (res.status === 200 || res.status === 204 || (res.data && res.data.code === 200)))
    if (!ok) throw new Error(res && res.data && (res.data.message || res.data.msg) || '更新失败')
    hideTypePicker(); ElMessage({
      type: 'success',
      dangerouslyUseHTMLString: true,
      message: `类型切换成功 <strong>P${img.pages}</strong> 从 <strong class="type-success-prev">${getTypeName(prevType)}</strong> 切换到 <strong class="type-success-new">${getTypeName(newType)}</strong>`,
      placement: 'top-right',
      duration: 5000,
      })
  } catch (e) { img.btype = prevType; ElMessage.error('修改类型失败，请重试') }
}

const toggleImageSelection = (img) => {
  const imgId = img.cx || img.id
  if (selectedImages.value.has(imgId)) {
    selectedImages.value.delete(imgId)
  } else {
    selectedImages.value.add(imgId)
  }
}

const printSelectedImages = async () => {
  if (selectedImages.value.size === 0) {
    ElMessage.warning('请先选择要打印的图片')
    return
  }

  try {
    ElMessage.info('正在准备打印...')
    
    // 创建一个隐藏的iframe用于打印
    const printWindow = window.open('', '_blank')
    if (!printWindow) {
      ElMessage.error('无法打开打印窗口，请检查浏览器设置')
      return
    }
    
    printWindow.document.write(`
      <!DOCTYPE html>
      <html>
      <head>
        <title>打印图片</title>
      </head>
      <body>
        <script>
          window.printCallback = null;
          
          window.addEventListener('beforeprint', function() {
            // 打印对话框打开
          });
          
          window.addEventListener('afterprint', function() {
            // 打印对话框关闭（无论是打印还是取消）
            window.close();
          });
        <\/script>
      </body>
      </html>
    `);
    
    const printDocument = printWindow.document;
    
    // 添加样式
    const style = printDocument.createElement('style');
    style.innerHTML = `
      body {
        font-family: Arial, sans-serif;
        padding: 0;
        margin: 0;
      }
      .print-page {
        page-break-after: always;
        width: 210mm;  /* A4 width */
        height: 297mm; /* A4 height */
        display: flex;
        flex-direction: column;
        padding: 10mm;
        box-sizing: border-box;
      }
      .print-page:last-child {
        page-break-after: avoid;
      }
      .print-image-container {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
      }
      .print-image {
        max-width: 100%;
        max-height: 100%;
        object-fit: contain;
        margin-bottom: 10px;
      }
      .print-info {
        text-align: center;
        font-size: 14px;
        color: #333;
        padding: 10px;
      }
      @media print {
        @page {
          size: A4;
          margin: 0;
        }
        body {
          padding: 0;
          margin: 0;
        }
        .print-page {
          page-break-after: always;
          break-after: page;
        }
        .print-page:last-child {
          page-break-after: avoid;
          break-after: auto;
        }
      }
    `;
    printDocument.head.appendChild(style);
    
    // 添加选中的图片到打印窗口
    for (const img of filteredImages.value) {
      const imgId = img.cx || img.id
      if (selectedImages.value.has(imgId)) {
        const imageUrl = img.blobUrl || transparentPixel
        const typeName = getTypeName(img.btype)
        
        const pageDiv = printDocument.createElement('div');
        pageDiv.className = 'print-page';
        
        pageDiv.innerHTML = `
          <div class="print-image-container">
            <img class="print-image" src="${imageUrl}" alt="图片 P${img.pages}" />
          </div>
          <div class="print-info">
            <div>P${img.pages} - ${typeName}</div>
            <div>病案号: ${searchBah.value}</div>
          </div>
        `;
        
        printDocument.body.appendChild(pageDiv);
      }
    }
    
    // 等待内容加载完成后打印
    printWindow.onload = () => {
      printWindow.focus();
      
      // 使用setTimeout确保窗口完全加载
      setTimeout(() => {
        printWindow.print();
      }, 500);
    };
    
  } catch (error) {
    ElMessage.error('打印过程中出现错误')
    console.error('打印错误:', error)
  }
}

// 选中图片变化时，确保主图加载
watch(selectedImageIndex, () => { ensureCurrentImageBlob() })
</script>

<style scoped>

/* 基础布局 */
.image-gallery {
  padding: 20px;
  width: 100%;
  margin: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.split-layout {
  display: flex;
  gap: 20px;
  height: 100%;
  min-height: 0;
}

/* 左侧面板 */
.left-pane {
  width: 30%;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
  min-height: 0;
}

.left-top, .left-bottom {
  background: var(--white);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  padding: 12px;
  display: flex;
  flex-direction: column;
  min-height: 220px;
  overflow: hidden;
}

.left-top {
  flex: 1 1 50%;
}

.left-bottom {
  flex: 1 1 50%;
}

.left-top h3, .left-bottom h3 {
  margin: 0 0 10px 0;
}

/* 病案列表 */
.id-results {
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.id-results-empty {
  color: var(--text-secondary);
  font-size: 14px;
}

.id-result-item {
  border: 1px solid var(--gray-300);
  border-radius: 8px;
  padding: 8px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.id-result-item:hover {
  background: var(--gray-100);
}

.id-result-item.active {
  border-color: var(--primary-color);
  background: var(--primary-light);
}

.id-result-item.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.rec-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-primary);
}

.rec-bah {
  font-weight: 600;
}

.rec-dept {
  color: var(--text-secondary);
}

.rec-name {
  color: var(--text-primary);
}

.rec-time {
  color: var(--text-secondary);
}

/* 病案类型 */
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

.print-btn {
  padding: 4px 10px;
  border: 1px solid var(--primary-color);
  background: var(--primary-color);
  border-radius: 6px;
  cursor: pointer;
  color: white;
  font-size: 12px;
}

.print-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.print-btn:hover:not(:disabled) {
  background: var(--primary-dark);
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
  border: 1px solid var(--gray-300);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.type-item:hover {
  background: var(--gray-100);
}

.type-item.active {
  border-color: var(--primary-color);
  background: var(--primary-light);
}

.type-item.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.type-name {
  color: var(--text-primary);
}

.type-count {
  color: var(--text-secondary);
}

.type-empty {
  color: var(--text-secondary);
}

/* 右侧面板 */
.right-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  position: relative;
}

.viewer-split {
  display: flex;
  gap: 0;
  background: var(--white);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  flex: 1 1 auto;
  overflow: hidden;
  min-height: 0;
}

/* 缩略图区域 */
.thumbs {
  width: 200px;
  min-width: 200px;
  max-width: 360px;
  border-right: 1px solid var(--gray-300);
  background: var(--gray-50);
  overflow-y: auto;
  height: 100%;
  -webkit-overflow-scrolling: touch;
  padding: 8px;
  overscroll-behavior: contain;
  display: grid;
  gap: 8px;
  align-content: start;
}

/* 缩略图 - 图标视图 */
.thumb-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--gray-300);
  border-radius: 8px;
  padding: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative; /* 为选中按钮定位 */
}

.thumb-item:hover {
  background: var(--gray-200);
}

.thumb-item.active {
  border-color: var(--primary-color);
  background: var(--primary-light);
}

/* 选中按钮样式 */
.thumb-selection {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--gray-400);
  background-color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 2;
  transition: all 0.2s ease;
}

.thumb-selection:hover {
  border-color: var(--primary-color);
  background-color: var(--gray-100);
}

.thumb-selection.selected {
  background-color: var(--primary-color);
  border-color: var(--primary-color);
}

.selection-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: white;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.thumb-selection.selected .selection-indicator {
  opacity: 1;
}

.thumb-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: var(--bg-primary);
  border-radius: 4px;
  opacity: 1;
  transition: opacity 0.3s ease;
}

.thumb-meta {
  font-size: 12px;
  color: var(--gray-500);
  text-align: center;
}

.thumbs-empty {
  color: var(--text-secondary);
  text-align: center;
  margin-top: 20px;
}

/* 缩略图 - 列表视图 */
.thumb-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border: 1px solid var(--gray-300);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative; /* 为选中按钮定位 */
}

.thumb-row:hover {
  background: var(--gray-200);
}

.thumb-row.active {
  border-color: var(--primary-color);
  background: var(--primary-light);
}

/* 列表视图的选中按钮 */
.thumb-row .thumb-selection {
  position: absolute;
  top: 6px;
  right: 6px;
}

.thumb-row-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.thumb-row-title {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;
}

/* 主视图区域 */
.main-view {
  flex: 1;
  position: relative;
  background: var(--white);
  min-height: 0;
}

.viewer-source {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
}

.viewer-image-el {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: 100%;
  object-fit: contain;
}

/* 图片信息显示 */
.main-meta {
  position: absolute;
  bottom: 10px;
  right: 10px;
  background: var(--info-color);
  color: var(--white);
  padding: 8px 14px;
  border-radius: 20px;
  font-size: 12px;
  z-index: 3;
  box-shadow: var(--shadow-md);
  /* 毛玻璃效果 */
  backdrop-filter: blur(1px);
  -webkit-backdrop-filter: blur(6px);
  opacity: 0.8;
  pointer-events: none;
}

/* 类型选择器 */
.type-picker {
  position: absolute;
  bottom: 36px;
  right: 0;
  background: var(--primary-dark);
  color: var(--white);
  border-radius: 10px;
  padding: 8px;
  min-width: 180px;
  box-shadow: var(--shadow-lg);
}

.type-picker-title {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 6px;
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
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.06);
  transition: background 0.2s ease;
}

.type-option:hover {
  background: rgba(255, 255, 255, 0.12);
}

.type-option.active {
  background: rgba(102, 126, 234, 0.4);
}

/* 全屏预览说明文字 */
.fullscreen-caption {
  position: fixed;
  right: 20px;
  bottom: 30px;
  background: var(--info-color);
  color: var(--white);
  padding: 8px 14px;
  border-radius: 20px;
  font-size: 12px;
  z-index: 4000;
  pointer-events: none;
  box-shadow: var(--shadow-md);
  /* 毛玻璃效果 */
  backdrop-filter: blur(1px);
  -webkit-backdrop-filter: blur(6px);
  opacity: 0.8;
  pointer-events: none;
}

/* 加载状态 */
.loading {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(2px);
  z-index: 5;
  color: var(--text-secondary);
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--gray-200);
  border-top: 4px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 错误状态 */
.error {
  text-align: center;
  padding: 40px;
  color: var(--error-color);
}

.retry-btn {
  background: var(--error-color);
  color: var(--white);
  border: none;
  padding: 10px 20px;
  border-radius: 6px;
  cursor: pointer;
  margin-top: 15px;
  transition: background-color 0.3s ease;
}

.retry-btn:hover {
  background: #c0392b;
}

/* 下载状态 */
.download-status {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.download-status-content {
  background: var(--white);
  padding: 30px;
  border-radius: 12px;
  text-align: center;
  box-shadow: var(--shadow-xl);
  max-width: 400px;
  width: 90%;
}

.download-spinner-large {
  width: 50px;
  height: 50px;
  border: 4px solid var(--gray-200);
  border-top: 4px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

.download-status-content p {
  margin: 10px 0;
  color: var(--text-primary);
  font-size: 16px;
}

.download-tip {
  font-size: 14px !important;
  color: var(--text-secondary) !important;
  margin-top: 15px;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .split-layout {
    gap: 15px;
  }
  
  .left-pane {
    min-width: 250px;
  }
}

@media (max-width: 992px) {
  .image-gallery {
    padding: 15px;
  }
  
  .split-layout {
    gap: 10px;
  }
  
  .left-pane {
    min-width: 220px;
  }
  
  .type-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .split-layout {
    flex-direction: column;
  }
  
  .left-pane {
    width: 100%;
  }
  
  .thumbs {
    min-width: 100px;
    width: 100px;
  }
}

@media (max-width: 480px) {
  .image-gallery {
    padding: 10px;
  }
  
  .split-layout {
    gap: 8px;
  }
  
  .left-pane, .right-pane {
    gap: 8px;
  }
  
  .left-top, .left-bottom {
    padding: 10px;
  }
  
  .type-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .thumbs-toolbar {
    align-self: flex-end;
  }
}

/* 成功提示样式 */
.type-success-prev {
  color: var(--error-color);
}

.type-success-new {
  color: var(--success-color);
}
</style>

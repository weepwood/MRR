<template>
  <div class="image-gallery">
    <!-- 消息提示卡片 -->
    <MessageToast
      :visible="toastVisible"
      :type="toastType"
      :title="toastTitle"
      :description="toastDescription"
      :show-undo="toastShowUndo"
      @close="hideToast"
      @undo="handleToastUndo"
    />
    
    <div class="split-layout">
      <div class="left-pane">
        <div class="left-top">
          <h3>查询出 {{ idSearchResults.length }} 份已翻拍的住院病案</h3>
          <div class="id-results" v-if="idSearchResults.length">
            <div 
              v-for="rec in idSearchResults"
              :key="rec.id"
              class="id-result-item"
              :class="{ active: selectedRecord && selectedRecord.bah === rec.bah }"
              @click="selectRecord(rec)"
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
          <div class="id-results-empty" v-else>暂无结果</div>
        </div>
        <div class="left-bottom">
          <div class="type-header">
            <h3>病案类型</h3>
                         <div class="type-actions">
               <button class="layout-toggle-btn" @click="cycleThumbsLayout" :title="layoutTitle">
                 <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                   <path fill="currentColor" d="M3 5h8v6H3V5zm0 8h8v6H3v-6zm10-8h8v6h-8V5zm0 8h8v6h-8v-6z"/>
                 </svg>
               </button>
             </div>
          </div>
          <div class="type-list">
            <div 
              class="type-item"
              :class="{ active: selectedType === 'all' }"
              @click="onSelectType('all')"
            >
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
            <div v-if="!typeDisplayList.length" class="type-empty">暂无类型</div>
          </div>
        </div>
        <!-- <div class="viewer-toolbar">
          <div class="selected-info">
            <span v-if="selectedRecord">当前病案：{{ selectedRecord.bah }}（{{ selectedRecord.name }}）</span>
            <span v-else-if="isValidBah">当前病案：{{ searchBah }}</span>
            <span v-else>请选择左侧病案</span>
          </div>
          <div class="toolbar-actions">
            <button 
              @click="downloadBahZip" 
              :disabled="downloading || images.length === 0 || !(selectedRecord || isValidBah)"
              class="download-btn"
              title="下载该病案的所有图片压缩包"
            >
              <svg v-if="!downloading" viewBox="0 0 24 24" fill="currentColor" class="download-icon">
                <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
              </svg>
              <div v-else class="download-spinner"></div>
              {{ downloading ? '下载中...' : '下载压缩包' }}
            </button>
            <button @click="handleLogout" class="logout-btn" title="退出登录">
              <svg viewBox="0 0 24 24" fill="currentColor" class="logout-icon">
                <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"/>
              </svg>
              退出登录
            </button>
          </div>
        </div> -->
      </div>
      <div class="right-pane">

        <div class="viewer-split" ref="viewerSplitRef">
          <div 
            class="thumbs" 
            ref="thumbsContainer"
            :style="{
              width: thumbsPaneWidth + 'px',
              minWidth: thumbsPaneMin + 'px',
              maxWidth: thumbsPaneMax + 'px',
                             gridTemplateColumns: 'repeat(' + (
                   thumbsLayoutMode === 'grid-1' ? 1 : (
                     thumbsLayoutMode === 'grid-2' ? Math.min(2, thumbsColumns) : Math.min(3, thumbsColumns)
                   )
                 ) + ', 1fr)'
            }"
          >
                         <div 
               v-for="(img, idx) in filteredImages" 
               :key="img.cx || img.id || idx"
               class="thumb-item"
               :class="{ active: idx === selectedImageIndex }"
               :ref="el => thumbRefs[idx] = el"
               @click="selectImage(idx, 'click')"
             >
               <img 
                 :src="img.blobUrl || transparentPixel" 
                 class="thumb-image" 
                 decoding="async"
                 loading="lazy"
                 @load="onImageLoad"
                 @error="onImageError" 
               />
               <div class="thumb-meta">{{ getTypeName(img.btype) }} - P{{ img.pages }}</div>
             </div>
             <div v-if="!filteredImages.length" class="thumbs-empty">暂无图片</div>
          </div>
          <div class="thumbs-resizer" @mousedown.prevent="startThumbsResize"></div>
          <div class="main-view">
            <div class="viewer-source" ref="viewerContainer">
              <img 
                v-for="(img, idx) in filteredImages" 
                :key="img.cx || img.id || idx"
                :src="img.blobUrl || transparentPixel" 
                class="viewer-image" 
                :alt="`${getTypeName(img.btype)} 第${img.pages}页 - ${img.filename || ''}`"
                decoding="async"
                @load="onImageLoad"
              />
            </div>
            <div v-if="currentImage" class="main-meta" @click.stop="toggleTypePicker">
              {{ getTypeName(currentImage.btype) }} - P{{ currentImage.pages }}
              <div 
                v-if="showTypePicker" 
                class="type-picker" 
                @click.stop
              >
                <div class="type-picker-title">切换类型</div>
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
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <!-- 错误状态 -->
        <div v-if="error" class="error">
          <p>{{ error }}</p>
          <button @click="retryLoad" class="retry-btn">重试</button>
        </div>
      </div>
    </div>

    <!-- 下载状态提示 -->
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
/**
 * 组件：病案图片图库（ImageGallery）
 * 功能概览：
 * - 根据8位病案号拉取图片清单并分类型展示
 * - 支持类型筛选、图片大图预览（Viewer.js）
 * - 图片按需加载（Blob），带并发限制、结果缓存与去重
 * - 支持下载当前病案所有图片的压缩包
 * - 提供加载/错误/下载状态提示与退出登录
 */
import { ref, computed, onMounted, nextTick, onUnmounted, watch } from 'vue'
import { getImgApiByBah, downloadBah, getImg, getBAHByIdCard, updateImgType } from '@/utils/api'
import Viewer from 'viewerjs'
import 'viewerjs/dist/viewer.css'
import MessageToast from './MessageToast.vue'

// -----------------------------
// 组件入参与基础状态
// -----------------------------

const props = defineProps({
  bah: {
    type: String,
    default: '00788222'
  }
})

const images = ref([]) // 图片列表（后端返回项 + 运行时字段：cx、blobUrl）
const loading = ref(false) // 列表加载中
const error = ref('') // 错误消息
const selectedType = ref('all') // 当前筛选类型（将不展示筛选UI，但保留逻辑以兼容数据结构）
const searchBah = ref(props.bah) // 病案号（由身份证查询选择或URL传入）
const isValidBah = computed(() => /^\d{8}$/.test(searchBah.value)) // 病案号为8位数字
const downloading = ref(false) // 压缩包下载中

// 消息提示相关
const toastVisible = ref(false)
const toastType = ref('success')
const toastTitle = ref('')
const toastDescription = ref('')
const toastShowUndo = ref(false)
const previousType = ref(null) // 用于撤回操作

// 身份证相关
const searchIdCard = ref('')
const idSearchResults = ref([])
const idSearchLoading = ref(false)
const selectedRecord = ref(null)

// 预览相关
const selectedImageIndex = ref(0)
const viewerContainer = ref(null)
const thumbsContainer = ref(null)
const viewerSplitRef = ref(null)
const thumbRefs = ref([])
let viewerInstance = null
let viewerUpdateScheduled = false
let resizeObserver = null
let viewerEventsBound = false
let resizing = false
let prevUserSelect = ''
const thumbsPaneMin = 130 // 保证列宽不小于图片宽度
const gridGap = 8 // 与样式中 gap 保持一致，用于计算3列最大宽度
const thumbsPaneWidth = ref(150)
let resizeStartX = 0
let resizeStartWidth = 0

// 类型选择弹层显示状态
const showTypePicker = ref(false)
// 可选类型列表（与 getTypeName 覆盖一致）
const typeOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14]

const toggleTypePicker = () => {
  showTypePicker.value = !showTypePicker.value
}

const hideTypePicker = () => {
  showTypePicker.value = false
}

const handleDocumentClick = () => {
  hideTypePicker()
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
})

// 显示消息提示
const showToast = (type, title, description, showUndo = false) => {
  console.log('showToast called with:', { type, title, description, showUndo }) // 调试日志
  toastType.value = type
  toastTitle.value = title
  toastDescription.value = description
  toastShowUndo.value = showUndo
  toastVisible.value = true
  console.log('Toast state set to:', { // 调试日志
    visible: toastVisible.value,
    type: toastType.value,
    title: toastTitle.value,
    description: toastDescription.value,
    showUndo: toastShowUndo.value
  })
}

// 隐藏消息提示
const hideToast = () => {
  toastVisible.value = false
}

// 处理撤回操作
const handleToastUndo = () => {
  if (previousType.value !== null) {
    // 撤回类型切换
    selectedType.value = previousType.value
    selectedImageIndex.value = 0
    
    // 更新视图
    if (viewerInstance) {
      try {
        viewerInstance.update()
        viewerInstance.view(0)
      } catch (e) {}
    }
    
    // 显示撤回成功提示
    showToast('info', '操作已撤回', '已恢复到之前的类型选择', false)
  }
}

const onPickType = async (newType) => {
  if (!currentImage.value) return
  const img = currentImage.value
  if (img.btype === newType) {
    hideTypePicker()
    return
  }
  const imageId = img.id
  if (!imageId) {
    alert('无法识别图片ID，无法修改类型')
    return
  }
  const prevType = img.btype
  // 乐观更新
  img.btype = newType
  try {
    const res = await updateImgType(imageId, { btype: String(newType) })
    const ok = (res && (res.status === 200 || res.status === 204 || (res.data && res.data.code === 200)))
    if (!ok) throw new Error(res && res.data && (res.data.message || res.data.msg) || '更新失败')
    hideTypePicker()
    // 显示成功提示
    showToast('success', `类型切换成功`, `P${img.pages} 从 ${getTypeName(prevType)} 切换到 ${getTypeName(newType)}`, false)
  } catch (e) {
    img.btype = prevType
    alert('修改类型失败，请重试')
  }
}

// 根据面板宽度动态计算列数（最多3列，最少1列）
const singleThumbMinWidth = 130 // 与 thumbsPaneMin 对齐，避免列比图片窄
const thumbsColumns = computed(() => {
  const columns = Math.floor(thumbsPaneWidth.value / singleThumbMinWidth)
  if (columns < 1) return 1
  if (columns > 3) return 3
  return columns
})

// 允许的最大面板宽度：最多三列（3 * 单列最小宽度 + 2个列间距 + 左右内边距约16）
const thumbsPaneMax = computed(() => singleThumbMinWidth * 3 + gridGap * 2 + 16)

// 小图面板布局模式：'grid-1' | 'grid-2' | 'grid-3'
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

const cycleThumbsLayout = () => {
  if (thumbsLayoutMode.value === 'grid-1') thumbsLayoutMode.value = 'grid-2'
  else if (thumbsLayoutMode.value === 'grid-2') thumbsLayoutMode.value = 'grid-3'
  else thumbsLayoutMode.value = 'grid-1'
  applyThumbsLayout()
  // 更新 viewer 以适配右侧区域变化
  onWindowResize()
}



const handleViewerViewed = (e) => {
  try {
    const idx = e && e.detail && typeof e.detail.index === 'number' ? e.detail.index : (viewerInstance ? viewerInstance.index : 0)
    if (typeof idx === 'number') {
      selectedImageIndex.value = idx
      nextTick().then(() => scrollActiveThumbIntoView())
    }
  } catch (err) {}
}

// 节流更新 Viewer，避免频繁重建
const scheduleViewerUpdate = () => {
  if (viewerUpdateScheduled) return
  viewerUpdateScheduled = true
  requestAnimationFrame(() => {
    viewerUpdateScheduled = false
    if (viewerInstance) {
      try {
        viewerInstance.update()
        const maxIndex = Math.max(0, filteredImages.value.length - 1)
        if (selectedImageIndex.value > maxIndex) selectedImageIndex.value = 0
        if (filteredImages.value.length > 0) viewerInstance.view(selectedImageIndex.value)
      } catch (e) {}
    }
  })
}

// Blob 加载与缓存（并发受限）
// 说明：
// - 统一以 cx 作为图片二进制的唯一标识
// - blobUrlCache：缓存 cx -> objectURL，重复渲染时直接复用，降低网络与解码成本
// - inFlightFetches：缓存 cx -> Promise，去重同一资源的并发抓取
// - requestQueue + maxConcurrent：通过队列与并发计数控制请求风暴
// - pendingFetches：追踪尚未完成的抓取数量，用于在全部完成后再更新 Viewer，避免频繁重建
const transparentPixel = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==' // 占位透明像素
const blobUrlCache = new Map() // cx -> blobUrl
const inFlightFetches = new Map() // cx -> Promise
const requestQueue = [] // 待抓取任务队列
const maxConcurrent = 6 // 最大并发数
let activeCount = 0 // 当前活跃请求数
const pendingFetches = ref(0) // 尚未完成的抓取数量（响应式）

// 释放并重置与 Blob 相关的所有资源与计数，避免内存泄漏并保证每次搜索都是干净的流水线
const resetBlobResources = () => {
  try {
    for (const url of blobUrlCache.values()) {
      URL.revokeObjectURL(url)
    }
  } catch (e) {}
  blobUrlCache.clear()
  inFlightFetches.clear()
  requestQueue.length = 0
  activeCount = 0
  pendingFetches.value = 0
}

// 从请求队列中取任务并发执行；保证并发上限
const processQueue = () => {
  while (activeCount < maxConcurrent && requestQueue.length > 0) {
    const task = requestQueue.shift()
    if (!task) break
    const { cx, resolve, reject } = task
    activeCount++
    pendingFetches.value++
    getImg(cx, { responseType: 'blob' })
      .then(res => {
        const url = URL.createObjectURL(res.data)
        blobUrlCache.set(cx, url)
        resolve(url)
      })
      .catch(err => {
        reject(err)
      })
      .finally(() => {
        activeCount--
        pendingFetches.value--
        // 所有资源加载完后无额外操作
        processQueue()
      })
  }
}

// 将一个 cx 加入请求队列，返回在抓取完成后 resolve(blobUrl) 的 Promise
const enqueueFetch = (cx) => {
  return new Promise((resolve, reject) => {
    requestQueue.push({ cx, resolve, reject })
    processQueue()
  })
}

// 获取某 cx 的 blobUrl：优先返回缓存，其次返回进行中的 Promise，否则入队抓取
const getBlobUrlByCx = (cx) => {
  if (!cx) return Promise.resolve(null)
  if (blobUrlCache.has(cx)) return Promise.resolve(blobUrlCache.get(cx))
  if (inFlightFetches.has(cx)) return inFlightFetches.get(cx)
  const p = enqueueFetch(cx).finally(() => {
    inFlightFetches.delete(cx)
  })
  inFlightFetches.set(cx, p)
  return p
}

// 计算属性
// 根据类型筛选
const filteredImages = computed(() => {
  if (selectedType.value === 'all') return images.value
  return images.value.filter(img => img.btype === selectedType.value)
})

// 统一的加载中状态：元数据加载中或仍有图片Blob在加载
const isLoading = computed(() => loading.value || pendingFetches.value > 0)

// 病案类型统计用于左下角展示：显示所有预定义类型（无图片的类型计数为0，且不可点击）
const typeDisplayList = computed(() => {
  const counts = new Map()
  for (const img of images.value) {
    const t = img.btype
    counts.set(t, (counts.get(t) || 0) + 1)
  }
  const allTypes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14]
  return allTypes.map(type => ({ type, count: counts.get(type) || 0 }))
})

// 方法
// 从完整 URL 中提取后端图片接口路径后的 cx 段，便于后续按 cx 抓取 Blob
const extractCxFromImgUrl = (fullUrl) => {
  if (!fullUrl || typeof fullUrl !== 'string') return fullUrl
  const marker = '/img-api/image/'
  const index = fullUrl.indexOf(marker)
  if (index === -1) return fullUrl
  return fullUrl.substring(index + marker.length)
}

// 拉取图片清单并发 Blob 加载
// 流程：
// 1) 调后端接口获取元数据
// 2) 清理旧的 Blob 资源，基于 img_url 计算 cx 并尝试读取缓存
// 3) 先渲染占位图以避免布局抖动
// 4) 将各 cx 入队并发抓取，拿到 blobUrl 后回填到对应项
const loadImages = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const response = await getImgApiByBah(searchBah.value)
    if (response.data.code === 200) {
      // 清理旧的 blob 资源
      resetBlobResources()
      const rawList = Array.isArray(response.data.data) ? response.data.data : []
      images.value = rawList.map(item => {
        const originalUrl = item?.img_url || ''
        const cx = extractCxFromImgUrl(originalUrl)
        const cached = cx ? blobUrlCache.get(cx) : null
        return {
          ...item,
          cx,
          blobUrl: cached || null
        }
      })
      selectedType.value = 'all'
      selectedImageIndex.value = 0
      await nextTick()
      initInlineViewer()
      // 并发受限加载 blob，并写入缓存
      images.value.forEach(async (img) => {
        if (!img.cx) return
        try {
          const url = await getBlobUrlByCx(img.cx)
          img.blobUrl = url
          scheduleViewerUpdate()
        } catch (e) {
          // 单张失败忽略
        }
      })
    } else {
      error.value = response.data.message || '获取数据失败'
    }
  } catch (err) {
    error.value = '网络错误，请检查网络连接'
    console.error('加载图片失败:', err)
  } finally {
    loading.value = false
  }
}

const searchImages = () => {
  if (isValidBah.value) {
    loadImages()
  }
}

const onBahInput = (e) => {
  // 仅保留数字，并限制为最多8位
  const digitsOnly = String(e.target.value || '').replace(/\D+/g, '').slice(0, 8)
  searchBah.value = digitsOnly
}

// 身份证查询
const searchByIdCard = async () => {
  if (!searchIdCard.value) {
    idSearchResults.value = []
    return
  }
  idSearchLoading.value = true
  try {
    const res = await getBAHByIdCard(searchIdCard.value)
    if (res.data && res.data.code === 200) {
      idSearchResults.value = Array.isArray(res.data.data) ? res.data.data : []
      // 查询结果出来后默认选中第一个病案
      if (idSearchResults.value.length > 0) {
        selectRecord(idSearchResults.value[0])
      }
    } else {
      idSearchResults.value = []
    }
  } catch (e) {
    idSearchResults.value = []
  } finally {
    idSearchLoading.value = false
  }
}

const selectRecord = (rec) => {
  selectedRecord.value = rec
  searchBah.value = rec.bah
  images.value = []
  selectedImageIndex.value = 0
  loadImages()
}

const selectImage = (idx, source = 'keyboard') => {
  selectedImageIndex.value = idx
  hideTypePicker()
  if (viewerInstance) {
    try { viewerInstance.view(idx) } catch (e) {}
  }
  nextTick().then(() => {
    scrollActiveThumbIntoView(source)
  })
}

// 键盘与便捷导航：上一张/下一张
const goPrevImage = () => {
  if (filteredImages.value.length === 0) return
  if (selectedImageIndex.value <= 0) return
  selectImage(selectedImageIndex.value - 1, 'keyboard')
}

const goNextImage = () => {
  const total = filteredImages.value.length
  if (total === 0) return
  if (selectedImageIndex.value >= total - 1) return
  selectImage(selectedImageIndex.value + 1, 'keyboard')
}

const scrollActiveThumbIntoView = (source = 'keyboard') => {
  const container = thumbsContainer.value
  const idx = selectedImageIndex.value
  const el = thumbRefs.value[idx]
  if (!container || !el) return
  // 多列布局下，鼠标点击不滚动；键盘导航才滚动
  if (thumbsColumns.value > 1 && source !== 'keyboard') return
  // 仅在容器发生溢出时才滚动
  if (container.scrollHeight <= container.clientHeight) return
  const targetTop = el.offsetTop - (container.clientHeight - el.clientHeight) / 2
  container.scrollTo({ top: Math.max(0, targetTop), behavior: 'smooth' })
}

const isTextInputLike = (el) => {
  const tag = el && el.tagName ? el.tagName.toLowerCase() : ''
  if (el && typeof el.isContentEditable === 'boolean' && el.isContentEditable) return true
  return tag === 'input' || tag === 'textarea' || tag === 'select'
}

const onKeyDown = (e) => {
  // 忽略输入框内与组合键
  if (e.altKey || e.ctrlKey || e.metaKey) return
  if (isTextInputLike(e.target)) return
  switch (e.key) {
    case 'ArrowLeft':
    case 'ArrowUp':
      e.preventDefault()
      e.stopPropagation()
      goPrevImage()
      break
    case 'ArrowRight':
    case 'ArrowDown':
      e.preventDefault()
      e.stopPropagation()
      goNextImage()
      break
    default:
      break
  }
}

const onSelectType = async (type) => {
  console.log('onSelectType called with type:', type) // 调试日志
  
  // 保存之前的类型用于撤回
  previousType.value = selectedType.value
  
  selectedType.value = type
  selectedImageIndex.value = 0
  await nextTick()
  
  try {
    if (viewerInstance) {
      viewerInstance.update()
      viewerInstance.view(0)
    } else {
      initInlineViewer()
    }
  } catch (error) {
    console.error('Error in onSelectType:', error) // 调试日志
    // 回滚到之前的类型
    selectedType.value = previousType.value
  }
}

// 将 btype 数字映射到中文名称；未知类型回退为 `类型X`
const getTypeName = (type) => {
  const typeNames = {
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
    14: '14-其它'
  }
  const result = typeNames[type] || `类型${type}`
  // 删除前三个字符（数字和连字符）
  return result.substring(3)
}

// 类型计数与筛选不再显示，保留名称映射以展示信息

// 格式化日期时间（保留以备显示扩展使用）
const formatDate = (dateString) => {
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

// 重试加载
const retryLoad = () => {
  loadImages()
}

/**
 * 下载当前病案的所有图片压缩包：
 * - 前置校验：病案号合法且已有数据
 * - 发起后端下载（blob）
 * - 基于 blob 生成临时链接并触发下载
 * - 处理网络/超时/状态码等错误并反馈
 */
const downloadBahZip = async () => {
  if (!searchBah.value.trim()) {
    alert('请先输入病案号')
    return
  }

  if (images.value.length === 0) {
    alert('没有找到相关病案数据，无法下载')
    return
  }

  downloading.value = true
  
  try {
    const response = await downloadBah(searchBah.value, {
      responseType: 'blob', // 重要：设置响应类型为blob
      timeout: 60000 // 下载可能需要更长时间，设置60秒超时
    })
    
    // 检查响应状态
    if (response.status !== 200) {
      throw new Error('下载失败：服务器响应错误')
    }
    
    // 检查文件大小
    const fileSize = response.data.size
    if (fileSize === 0) {
      throw new Error('下载失败：文件为空')
    }
    
    // 创建下载链接
    const blob = new Blob([response.data], { 
      type: 'application/zip' 
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `病案_${searchBah.value}_${new Date().toISOString().slice(0, 10)}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    console.log(`下载成功，文件大小: ${(fileSize / 1024 / 1024).toFixed(2)} MB`)
  } catch (err) {
    console.error('下载失败:', err)
    
    let errorMessage = '下载失败，请重试'
    if (err.response) {
      // 服务器返回错误
      if (err.response.status === 404) {
        errorMessage = '未找到该病案的压缩包'
      } else if (err.response.status === 500) {
        errorMessage = '服务器错误，请稍后重试'
      } else {
        errorMessage = `下载失败 (${err.response.status})`
      }
    } else if (err.code === 'ECONNABORTED') {
      errorMessage = '下载超时，请检查网络连接'
    } else if (err.message) {
      errorMessage = err.message
    }
    
    alert(errorMessage)
  } finally {
    downloading.value = false
  }
}

// 退出登录：清除 token 并刷新页面
const handleLogout = () => {
  if (confirm('确定要退出登录吗？')) {
    localStorage.removeItem('token')
    window.location.reload()
  }
}

const onImageLoad = (event) => {
  // 图片加载成功
  event.target.style.opacity = '1'
}

const onImageError = (event) => {
  // 图片加载失败，显示占位符
  event.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgdmlld0JveD0iMCAwIDIwMCAyMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIyMDAiIGhlaWdodD0iMjAwIiBmaWxsPSIjRjVGNUY1Ii8+CjxwYXRoIGQ9Ik04MCAxMDBDODAgODkuNTQ0NyA4OC4wMDAxIDgxIDEwMCA4MUMxMTEuOTU2IDgxIDEyMCA4OS41NDQ3IDEyMCAxMEMxMjAgMTEwLjQ1NSAxMTEuOTU2IDExOSAxMDAgMTE5Qzg4LjAwMDEgMTE5IDgwIDExMC40NTUgODAgMTAwWiIgZmlsbD0iI0NDQ0NDQyIvPgo8L3N2Zz4K'
  event.target.style.opacity = '0.5'
}

// 当前预览图片（备用）
const currentImage = computed(() => filteredImages.value[selectedImageIndex.value] || null)

// 初始化/更新Viewer
const initInlineViewer = () => {
  if (!viewerContainer.value) return
  const options = {
    inline: true,
    button: true,
    navbar: false,
    title: false,
    toolbar: {
      zoomIn: true,
      zoomOut: true,
      oneToOne: true,
      reset: true,
      prev: true,
      next: true,
      rotateLeft: true,
      rotateRight: true,
      flipHorizontal: true,
      flipVertical: true,
      play: false
    },
    tooltip: true,
    movable: true,
    zoomable: true,
    zoomOnWheel: true,
    zoomOnClick: true,
    zoomRatio: 0.1,
    minZoomRatio: 0.1,
    maxZoomRatio: 5,
    toggleDrag: false,
    rotatable: true,
    scalable: true,
    transition: false,
    fullscreen: false,
    keyboard: true,
    viewed: handleViewerViewed
  }
  if (!viewerInstance) {
    viewerInstance = new Viewer(viewerContainer.value, options)
  } else {
    viewerInstance.update()
  }
  // 防止重复绑定事件
  if (!viewerEventsBound && viewerContainer.value) {
    try {
      viewerContainer.value.addEventListener('viewed', handleViewerViewed)
      viewerEventsBound = true
    } catch (e) {}
  }
  try { viewerInstance.view(selectedImageIndex.value || 0) } catch (e) {}
}

const onWindowResize = () => {
  if (!viewerInstance) return
  try {
    viewerInstance.update()
    if (filteredImages.value.length > 0) viewerInstance.view(selectedImageIndex.value || 0)
  } catch (e) {}
}

// 拖动调整缩略图面板宽度
const startThumbsResize = (e) => {
  try { e.preventDefault() } catch (err) {}
  resizing = true
  resizeStartX = e.clientX
  resizeStartWidth = thumbsPaneWidth.value
  // 禁止拖动时文字/图片被选中
  prevUserSelect = document.body.style.userSelect
  document.body.style.userSelect = 'none'
  try {
    const sel = window.getSelection && window.getSelection()
    if (sel && sel.removeAllRanges) sel.removeAllRanges()
  } catch (err) {}
  if (viewerSplitRef.value) {
    try { viewerSplitRef.value.classList.add('is-resizing') } catch (err) {}
  }
  document.addEventListener('mousemove', onThumbsResizing)
  document.addEventListener('mouseup', stopThumbsResize)
}

const onThumbsResizing = (e) => {
  if (!resizing) return
  const delta = e.clientX - resizeStartX
  let next = resizeStartWidth + delta
  if (next < thumbsPaneMin) next = thumbsPaneMin
  const maxW = typeof thumbsPaneMax === 'number' ? thumbsPaneMax : thumbsPaneMax.value
  if (next > maxW) next = maxW
  thumbsPaneWidth.value = next
}

const stopThumbsResize = () => {
  if (!resizing) return
  resizing = false
  document.removeEventListener('mousemove', onThumbsResizing)
  document.removeEventListener('mouseup', stopThumbsResize)
  // 恢复文本选择
  document.body.style.userSelect = prevUserSelect
  if (viewerSplitRef.value) {
    try { viewerSplitRef.value.classList.remove('is-resizing') } catch (err) {}
  }
  // 更新viewer尺寸并在布局稳定后强制居中当前页
  onWindowResize()
  try {
    nextTick().then(() => {
      requestAnimationFrame(() => {
        try {
          if (!viewerInstance) return
          viewerInstance.update()
          if (filteredImages.value.length > 0) {
            const idx = selectedImageIndex.value || 0
            viewerInstance.view(idx)
            viewerInstance.reset()
            viewerInstance.view(idx)
          }
        } catch (e) {}
      })
    })
  } catch (e) {}
}

// 生命周期
// 从URL解析身份证号：优先query，其次路径最后一段
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
  const urlIdCard = getIdCardFromUrl()
  // 恢复上次布局模式
  try {
    const saved = localStorage.getItem(LAYOUT_MODE_KEY)
    const allowed = new Set(['grid-1','grid-2','grid-3'])
    if (saved && allowed.has(saved)) {
      thumbsLayoutMode.value = saved
    }
  } catch (e) {}
  applyThumbsLayout()
  window.addEventListener('keydown', onKeyDown, { passive: false })
  window.addEventListener('resize', onWindowResize)
  // 监听容器尺寸变化，避免初始渲染时高度计算不准确导致底部空白
  try {
    const target = () => {
      if (!viewerContainer.value) return null
      // 监听主显示区域（viewer 的父级）更稳妥
      return viewerContainer.value.parentElement || viewerContainer.value
    }
    const el = target()
    if (window.ResizeObserver && el) {
      resizeObserver = new ResizeObserver(() => {
        scheduleViewerUpdate()
      })
      resizeObserver.observe(el)
    }
  } catch (e) {}
  if (urlIdCard) {
    searchIdCard.value = String(urlIdCard)
    await searchByIdCard()
    if (idSearchResults.value.length === 1) {
      selectRecord(idSearchResults.value[0])
      return
    }
  } else if (isValidBah.value) {
    // 没有传idCard时，兼容默认bah
    loadImages()
  }
})

onUnmounted(() => {
  // 组件卸载：释放 Blob 资源
  resetBlobResources()
  if (viewerInstance) {
    try { viewerInstance.destroy() } catch (e) {}
    viewerInstance = null
  }
  window.removeEventListener('resize', onWindowResize)
  if (resizeObserver) {
    try { resizeObserver.disconnect() } catch (e) {}
    resizeObserver = null
  }
  if (viewerEventsBound && viewerContainer.value) {
    try { viewerContainer.value.removeEventListener('viewed', handleViewerViewed) } catch (e) {}
    viewerEventsBound = false
  }
  window.removeEventListener('keydown', onKeyDown)
})

// 取消基于筛选的自动重置，避免点击缩略图后被重置
watch(filteredImages, async () => {
  await nextTick()
  if (viewerInstance) {
    try {
      viewerInstance.update()
      const maxIndex = Math.max(0, filteredImages.value.length - 1)
      if (selectedImageIndex.value > maxIndex) selectedImageIndex.value = 0
      if (filteredImages.value.length > 0) viewerInstance.view(selectedImageIndex.value)
    } catch (e) {}
  } else {
    initInlineViewer()
  }
})

// 监听布局模式变化并持久化
watch(thumbsLayoutMode, (mode) => {
  try { localStorage.setItem(LAYOUT_MODE_KEY, mode) } catch (e) {}
})

</script>

<style scoped>
.image-gallery {
  padding: 20px;
  width: 100%;
  margin: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 左右分栏整体布局 */
.split-layout {
  display: flex;
  gap: 20px;
  height: 100%;
  min-height: 0; /* 允许子项在flex中收缩，内部可滚动 */
}

.left-pane {
  width: 30%;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
  min-height: 0;
}

.left-top,
.left-bottom {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
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
.type-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.type-actions {
  display: inline-flex;
  gap: 6px;
}

.layout-toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
  color: #374151;
  cursor: pointer;
}

.layout-toggle-btn:hover {
  background: #eef2ff;
  border-color: #667eea;
  color: #374151;
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
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
}

.type-item.active {
  border-color: #667eea;
  background: #eef2ff;
}

.type-item.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.type-name {
  color: #333;
}

.type-count {
  color: #666;
}

.type-empty {
  color: #666;
}

.left-top h3,
.left-bottom h3 {
  margin: 0 0 10px 0;
}

.id-search-box {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.id-input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  font-size: 14px;
}

.id-results {
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.id-results-empty {
  color: #666;
  font-size: 14px;
}

.id-result-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.id-result-item:hover {
  background: #f8fafc;
}

.id-result-item.active {
  border-color: #667eea;
  background: #eef2ff;
}

.rec-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #333;
}

.rec-bah {
  font-weight: 600;
}

.rec-time {
  color: #666;
}

.header {
  background: linear-gradient(135deg, #667eea 0%, #334dbf 100%);
  color: white;
  padding: 20px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  flex-direction: row;
  gap: 15px;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  flex: 0 0 auto;
}

.header h2 {
  margin: 0 0 15px 0;
  font-size: 24px;
}

.search-section {
  margin-bottom: 15px;
  flex: 1 1 520px;
  min-width: 280px;
}

.search-box {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 让退出登录在同一行中靠右 */
.search-box .logout-btn {
  margin-left: auto;
}

/* 让搜索框、搜索按钮、下载按钮、退出登录同高同行 */
.search-input,
.search-btn,
.download-btn,
.logout-btn {
  height: 44px;
  box-sizing: border-box;
}

.search-btn,
.download-btn,
.logout-btn {
  display: inline-flex;
  align-items: center;
}

.search-input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  font-size: 16px;
  transition: all 0.3s ease;
}

.bah-input {
  max-width: 320px; /* 约等于8位数字的舒适宽度 */
  letter-spacing: 2px; /* 提升可读性 */
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.7);
}

.search-input:focus {
  outline: none;
  border-color: rgba(255, 255, 255, 0.8);
  background: rgba(255, 255, 255, 0.2);
}

.search-btn {
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.search-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  transform: translateY(-1px);
}

.stats {
  display: flex;
  gap: 20px;
  font-size: 14px;
  opacity: 0.9;
  align-items: center;
}

/* 优化头部在小屏下的排版：纵向堆叠，元素两端对齐 */
@media (max-width: 768px) {
  .header {
    flex-direction: column;
    align-items: stretch;
  }
  .header-actions {
    justify-content: space-between;
    width: 100%;
    flex-wrap: wrap;
    gap: 10px;
  }
  .stats {
    gap: 12px;
    font-size: 13px;
  }
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.download-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  transform: translateY(-1px);
}

.download-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.4);
  transform: translateY(-1px);
}

.logout-icon {
  width: 16px;
  height: 16px;
}

.download-icon {
  width: 16px;
  height: 16px;
}

.download-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

/* 复用现有筛选标签样式 */

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  /* 分散对齐 */
  /* justify-content: space-between; */
}

.filter-tab {
  padding: 8px 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  color: white;
}

.filter-tab:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  transform: translateY(-1px);
}

.filter-tab.active {
  background: rgba(255, 255, 255, 0.6);
  color: #110add;
  border-color: rgba(255, 255, 255, 0.9);
  /* font-weight: 600; */
  box-shadow: 5px 2px 8px rgba(0, 0, 0, 0.15);
  /* transform: translateY(-1px); */
}

.filter-tab.no-images {
  opacity: 0.5;
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  cursor: not-allowed;
}

.filter-tab.no-images:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  transform: none;
}



/* 右侧预览区域 */
.right-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0; /* 关键：让下方viewer区域可正确计算高度 */
  position: relative; /* 让加载遮罩覆盖右侧区域 */
}

.viewer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #667eea 0%, #334dbf 100%);
  color: #fff;
  padding: 12px 16px;
  border-radius: 12px;
  flex: 0 0 auto;
}

.viewer-split {
  display: flex;
  gap: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  flex: 1 1 auto;
  overflow: hidden;
  min-height: 0; /* 关键：使内部列可滚动 */
}

.thumbs {
  width: 200px;
  min-width: 150px;
  max-width: 360px;
  border-right: 1px solid #eee;
  background: #f9fafb;
  overflow-y: auto;
  height: 100%;
  -webkit-overflow-scrolling: touch;
  padding: 8px;
  overscroll-behavior: contain; /* 防止内部滚动牵连外层 */
  display: grid;
  /* grid-template-columns 改为由内联样式控制，保障最多3列 */
  gap: 8px;
  align-content: start; /* 图片较少时不拉伸填满容器，保持自然高度 */
}



.thumb-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 6px;
  cursor: pointer;
}

.thumb-item.active {
  border-color: #667eea;
  background: #eef2ff;
}

.thumb-image {
  width: 100%;
  height: auto; /* 允许图片根据自身比例自然高度 */
  max-height: 140px; /* 避免过高撑满列 */
  object-fit: contain;
  background: #c0c0c03f;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.thumb-meta {
  font-size: 12px;
  color: #555;
  text-align: center;
}

.thumbs-empty {
  color: #666;
  text-align: center;
  margin-top: 20px;
}

.thumbs-resizer {
  width: 10px;
  cursor: col-resize;
  background: linear-gradient(90deg, rgba(241,245,249,1) 0%, rgba(229,231,235,1) 100%);
  border-right: 1px solid #e5e7eb;
  position: relative;
}

.thumbs-resizer::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 2px;
  height: 24px;
  background: #cbd5e1; /* 中线 */
  border-radius: 1px;
}

.thumbs-resizer::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 28px;
  border-left: 1px dashed rgba(148,163,184,0.6);
  border-right: 1px dashed rgba(148,163,184,0.6);
}

.main-view {
  flex: 1;
  position: relative;
  background: #fff;
  min-height: 0; /* 允许内部图片区域自适应 */
}

.viewer-source {
  position: absolute;
  inset: 0;
}

/* 让 Viewer.js 在右侧区域内铺满空间 */
:deep(.viewer-container) {
  width: 100% !important;
  height: 100% !important;
  background: #fff;
}
:deep(.viewer-canvas) {
  background: #5e5e5e;
}

.viewer-source img.viewer-image {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

.main-meta {
  position: absolute;
  bottom: 10px;
  right: 10px;
  background: rgba(0,0,0,0.6);
  color: #fff;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  z-index: 3; /* 高于viewer画布 */
}

.main-meta { cursor: pointer; }

.type-picker {
  position: absolute;
  bottom: 36px;
  right: 0;
  background: #1f2937;
  color: #fff;
  border-radius: 10px;
  padding: 8px;
  min-width: 180px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.35);
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
  background: rgba(255,255,255,0.06);
  transition: background 0.2s ease;
}

.type-option:hover { background: rgba(255,255,255,0.12); }
.type-option.active { background: rgba(99,102,241,0.4); }

.main-empty {
  color: #666;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 20px;
  opacity: 0.6;
}

.empty-state h3 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 20px;
}

.empty-state p {
  margin: 0 0 25px 0;
  color: #666;
  font-size: 16px;
}

.back-to-all-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.back-to-all-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.image-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  cursor: pointer;
}

.image-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.image-container {
  position: relative;
  height: 250px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8f9fa;
}

.medical-image {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
  transition: transform 0.3s ease, opacity 0.3s ease;
  opacity: 0;
}

.image-card:hover .medical-image {
  transform: scale(1.05);
}

.image-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  color: white;
  padding: 10px;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.image-card:hover .image-overlay {
  opacity: 1;
}

.image-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.filename {
  font-weight: bold;
}

.image-details {
  padding: 15px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
}

.label {
  color: #666;
  font-weight: 500;
}

.value {
  color: #333;
}

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
  color: #666;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error {
  text-align: center;
  padding: 40px;
  color: #e74c3c;
}

.retry-btn {
  background: #e74c3c;
  color: white;
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

.download-status {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.download-status-content {
  background: white;
  padding: 30px;
  border-radius: 12px;
  text-align: center;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  max-width: 400px;
  width: 90%;
}

.download-spinner-large {
  width: 50px;
  height: 50px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

.download-status-content p {
  margin: 10px 0;
  color: #333;
  font-size: 16px;
}

.download-tip {
  font-size: 14px !important;
  color: #666 !important;
  margin-top: 15px;
}

@media (max-width: 768px) {
  .split-layout {
    flex-direction: column;
  }
  .left-pane {
    width: 100%;
  }
  .thumbs {
    min-width: 80px;
  }
}

@media (max-width: 480px) {
  .image-container {
    height: 180px;
  }
  
  .image-grid {
    grid-template-columns: 1fr;
  }
}
/* 可访问性焦点与悬停反馈 */
.download-btn:focus-visible,
.logout-btn:focus-visible,
.search-btn:focus-visible,
.thumb-item:focus-visible,
.type-item:focus-visible,
.id-result-item:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.45);
}

.type-item:hover {
  background: #f8fafc;
}

.thumb-item:hover {
  background: #f3f4f6;
}
</style>

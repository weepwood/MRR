<template>
  <div class="archive-image-page pmr-page">
    <section class="page-hero pmr-page-header pmr-fade-up">
      <div class="hero-copy">
        <p class="module-eyebrow">Archive Gallery</p>
        <h2 class="pmr-page-title">{{ archiveTitle }}</h2>
        <p class="pmr-page-subtitle">
          在这里查看当前档案袋对应的全部图片，支持多选、打印和导出 PDF。
        </p>
      </div>

      <div class="pmr-toolbar-actions">
        <el-button :icon="Refresh" :loading="loading" @click="refreshImages">刷新图片</el-button>
        <el-button :icon="ArrowLeft" @click="goBack">返回明细</el-button>
      </div>
    </section>

    <section class="summary-grid pmr-stagger">
      <el-card
        v-for="(card, index) in summaryCards"
        :key="card.label"
        class="pmr-panel summary-card pmr-stagger-item pmr-hover-lift"
        shadow="never"
        :style="{ '--pmr-stagger-index': index }"
      >
        <div class="summary-label">{{ card.label }}</div>
        <div class="summary-value">{{ card.value }}</div>
        <div class="summary-note">{{ card.note }}</div>
      </el-card>
    </section>

    <el-alert
      v-if="error"
      class="archive-alert"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <section class="workspace pmr-fade-up">
      <aside class="control-panel">
        <el-card class="pmr-panel control-card" shadow="never">
          <template #header>
            <div class="pmr-panel-header">
              <div>
                <h3 class="pmr-panel-title">档案信息</h3>
                <p class="pmr-panel-subtitle">来自统计明细的病案袋上下文。</p>
              </div>
              <span class="pmr-badge">{{ archiveInfo.bah || '未指定' }}</span>
            </div>
          </template>

          <dl class="info-grid">
            <div class="info-item">
              <dt>病案号</dt>
              <dd>{{ archiveInfo.bah || '-' }}</dd>
            </div>
            <div class="info-item">
              <dt>归档类型</dt>
              <dd>{{ archiveInfo.type || '-' }}</dd>
            </div>
            <div class="info-item">
              <dt>归档日期</dt>
              <dd>{{ formatDate(archiveInfo.date) }}</dd>
            </div>
            <div class="info-item">
              <dt>图片数量</dt>
              <dd>{{ images.length }}</dd>
            </div>
            <div class="info-item">
              <dt>扫描设备</dt>
              <dd>{{ archiveInfo.cid || '-' }}</dd>
            </div>
            <div class="info-item">
              <dt>扫描人员</dt>
              <dd>{{ archiveInfo.openerNo || '-' }}</dd>
            </div>
          </dl>
        </el-card>

        <el-card class="pmr-panel control-card" shadow="never">
          <template #header>
            <div class="pmr-panel-header">
              <div>
                <h3 class="pmr-panel-title">图片类型</h3>
                <p class="pmr-panel-subtitle">点击类型即可快速过滤当前页面图片。</p>
              </div>
              <span class="pmr-badge">{{ filteredImages.length }} 张</span>
            </div>
          </template>

          <div class="type-list">
            <button
              type="button"
              class="type-item"
              :class="{ active: selectedType === 'all' }"
              @click="onSelectType('all')"
            >
              <span class="type-name">全部</span>
              <span class="type-count">{{ images.length }}</span>
            </button>
            <button
              v-for="item in typeDisplayList"
              :key="item.type"
              type="button"
              class="type-item"
              :class="{ active: selectedType === item.type, disabled: item.count === 0 }"
              :disabled="item.count === 0"
              @click="onSelectType(item.type)"
            >
              <span class="type-name">{{ getTypeName(item.type) }}</span>
              <span class="type-count">{{ item.count }}</span>
            </button>
          </div>
        </el-card>

        <el-card class="pmr-panel control-card" shadow="never">
          <template #header>
            <div class="pmr-panel-header">
              <div>
                <h3 class="pmr-panel-title">批量操作</h3>
                <p class="pmr-panel-subtitle">先勾选图片，再选择打印或导出 PDF。</p>
              </div>
            </div>
          </template>

          <div class="batch-summary">
            <div class="batch-stat">
              <span>已选图片</span>
              <strong>{{ selectedImages.size }}</strong>
            </div>
            <div class="batch-stat">
              <span>当前筛选</span>
              <strong>{{ filteredImages.length }}</strong>
            </div>
          </div>

          <div class="batch-toggle">
            <el-switch v-model="showSelectedOnly" active-text="仅看已选" inactive-text="显示全部" />
          </div>

          <div class="batch-actions">
            <el-button type="primary" plain :disabled="!filteredImages.length" @click="selectAllVisible">
              选中当前筛选
            </el-button>
            <el-button :disabled="selectedImages.size === 0" @click="clearSelection">
              清空选择
            </el-button>
            <el-button type="success" :disabled="selectedImages.size === 0" @click="handlePrintSelected">
              <el-icon><Printer /></el-icon>
              打印选中
            </el-button>
            <el-button type="warning" :loading="exportingPdf" :disabled="selectedImages.size === 0" @click="handleExportPdf">
              <el-icon><Download /></el-icon>
              导出 PDF
            </el-button>
          </div>
        </el-card>
      </aside>

      <main class="viewer-panel">
        <el-card class="pmr-panel viewer-card" shadow="never">
          <template #header>
            <div class="pmr-panel-header viewer-header">
              <div>
                <h3 class="pmr-panel-title">图片展示</h3>
                <p class="pmr-panel-subtitle">
                  使用缩略图切换图片，支持多选与预览。当前显示 {{ filteredImages.length }} 张。
                </p>
              </div>

              <div class="viewer-toolbar">
                <button
                  type="button"
                  class="view-mode-btn pmr-hover-lift"
                  :class="{ active: thumbsViewMode === 'grid' }"
                  @click="setThumbsViewMode('grid')"
                >
                  缩略图
                </button>
                <button
                  type="button"
                  class="view-mode-btn pmr-hover-lift"
                  :class="{ active: thumbsViewMode === 'list' }"
                  @click="setThumbsViewMode('list')"
                >
                  列表
                </button>
              </div>
            </div>
          </template>

          <div v-if="loading" class="loading-shell">
            <el-skeleton :rows="8" animated />
          </div>

          <div v-else-if="!archiveInfo.bah" class="empty-shell">
            <el-empty description="缺少病案号，无法加载图片" />
          </div>

          <div v-else-if="error && !images.length" class="empty-shell">
            <el-empty :description="error" />
          </div>

          <div v-else class="gallery-shell">
            <section class="thumb-column">
              <div class="thumb-column-header">
                <span>缩略图</span>
                <span>{{ selectedImageLabel }}</span>
              </div>

              <div class="thumb-list pmr-stagger" :class="{ 'is-list': thumbsViewMode === 'list' }">
                <template v-if="thumbsViewMode === 'grid'">
                  <article
                    v-for="(img, idx) in filteredImages"
                    :key="getImageId(img)"
                    :ref="(el) => setThumbRef(el, idx)"
                    class="thumb-card pmr-stagger-item pmr-hover-lift"
                    :class="{ active: idx === selectedImageIndex, selected: isImageSelected(img) }"
                    :style="{ '--pmr-stagger-index': idx }"
                    @click="selectImage(idx)"
                  >
                    <div class="thumb-select" @click.stop="toggleImageSelection(img)">
                      <el-icon v-if="isImageSelected(img)"><CircleCheckFilled /></el-icon>
                      <el-icon v-else><CirclePlusFilled /></el-icon>
                    </div>
                    <el-image
                      :src="img.displayUrl"
                      class="thumb-image"
                      fit="cover"
                      loading="lazy"
                      :preview-src-list="[]"
                    />
                    <div class="thumb-meta">
                      <div class="thumb-title">P{{ img.pages || idx + 1 }} - {{ getTypeName(img.btype) }}</div>
                      <div class="thumb-subtitle">{{ formatDate(archiveInfo.date) }}</div>
                    </div>
                  </article>
                </template>

                <template v-else>
                  <article
                    v-for="(img, idx) in filteredImages"
                    :key="getImageId(img)"
                    :ref="(el) => setThumbRef(el, idx)"
                    class="thumb-row pmr-stagger-item pmr-hover-lift"
                    :class="{ active: idx === selectedImageIndex, selected: isImageSelected(img) }"
                    :style="{ '--pmr-stagger-index': idx }"
                    @click="selectImage(idx)"
                  >
                    <div class="thumb-select thumb-select-row" @click.stop="toggleImageSelection(img)">
                      <el-icon v-if="isImageSelected(img)"><CircleCheckFilled /></el-icon>
                      <el-icon v-else><CirclePlusFilled /></el-icon>
                    </div>
                    <div class="thumb-row-info">
                      <div class="thumb-row-title">P{{ img.pages || idx + 1 }} - {{ getTypeName(img.btype) }}</div>
                      <div class="thumb-row-subtitle">{{ formatDate(archiveInfo.date) }}</div>
                    </div>
                  </article>
                </template>

                <div v-if="!filteredImages.length" class="thumb-empty">
                  <el-empty description="当前筛选下没有图片" />
                </div>
              </div>
            </section>

            <section class="main-column">
              <div class="main-toolbar">
                <div>
                  <h4 class="main-title">{{ currentImageLabel }}</h4>
                  <p class="main-subtitle">
                    病案号 {{ archiveInfo.bah || '-' }} · {{ selectedImages.size }} 张已选
                  </p>
                </div>

                <div class="main-actions">
                  <el-button text @click="toggleCurrentSelection">
                    {{ currentImage && isImageSelected(currentImage) ? '取消当前选择' : '选择当前图片' }}
                  </el-button>
                </div>
              </div>

              <div class="viewer-frame">
                <el-image
                  v-if="currentImage"
                  :key="getImageId(currentImage)"
                  class="main-image pmr-fade-up"
                  :src="currentImage.displayUrl"
                  fit="contain"
                  :preview-src-list="previewList"
                  :initial-index="selectedImageIndex"
                  :z-index="3000"
                  :hide-on-click-modal="false"
                  :preview-teleported="true"
                  @switch="onViewerSwitch"
                />

                <el-empty v-else description="暂无可展示图片" />
              </div>

              <div class="selection-strip">
                <div class="selection-strip-header">
                  <span>已选图片</span>
                  <span>{{ selectedImages.size }} 张</span>
                </div>
                <div v-if="selectedPreviewImages.length" class="selection-strip-list">
                  <button
                    v-for="img in selectedPreviewImages"
                    :key="getImageId(img)"
                    type="button"
                    class="selection-chip"
                    @click="selectImageById(img)"
                  >
                    P{{ img.pages || '-' }}
                  </button>
                </div>
                <div v-else class="selection-strip-empty">尚未选择图片</div>
              </div>
            </section>
          </div>
        </el-card>
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCheckFilled, CirclePlusFilled, ArrowLeft, Download, Printer, Refresh } from '@element-plus/icons-vue'
import { getImgApiByBah } from '@/utils/api'
import { exportArchiveImagesToPdf } from '@/utils/archivePdf'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const images = ref([])
const selectedType = ref('all')
const selectedImages = ref(new Set())
const selectedImageIndex = ref(0)
const showSelectedOnly = ref(false)
const exportingPdf = ref(false)
const thumbRefs = ref([])
const THUMBS_VIEW_MODE_KEY = 'pmr.archiveThumbsViewMode'
const thumbsViewMode = ref(localStorage.getItem(THUMBS_VIEW_MODE_KEY) || 'grid')
const renderedObjectUrls = new Set()
const renderUrlCache = new Map()

const archiveInfo = computed(() => ({
  bah: String(route.params.bah || route.query.bah || ''),
  cid: String(route.query.cid || ''),
  type: String(route.query.type || ''),
  date: String(route.query.date || ''),
  pages: String(route.query.pages || ''),
  openerNo: String(route.query.openerNo || '')
}))

const archiveTitle = computed(() => `病案档案图片 · ${archiveInfo.value.bah || '未命名病案'}`)

const getTypeName = (type) => {
  const typeMap = {
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
    14: '14-其他'
  }
  return typeMap[type] || `类型${type || '-'}`
}

const formatDate = (dateValue) => {
  if (!dateValue) return '-'
  const text = String(dateValue)
  if (text.includes('T')) {
    return text.replace('T', ' ').replace('Z', '').slice(0, 19)
  }
  return text.replace(/\//g, '-')
}

const extractImageKey = (value) => {
  if (!value || typeof value !== 'string') return ''
  const marker = '/img-api/image/'
  const markerIndex = value.indexOf(marker)
  if (markerIndex >= 0) {
    return value.slice(markerIndex + marker.length)
  }
  return value
}

const extractImageMetaFromUrl = (value) => {
  if (!value || typeof value !== 'string') return {}

  try {
    const parsed = new URL(value, window.location.origin)
    const pathParts = parsed.pathname.split('/').filter(Boolean)
    if (pathParts.length < 5) return {}

    const filename = pathParts.pop() || ''
    const brxhBah = pathParts.pop() || ''
    const folder = pathParts.pop() || ''
    const [brxh = '', bah = ''] = brxhBah.split('-')

    return { bah, brxh, folder, filename }
  } catch {
    return {}
  }
}

const buildProxyImageUrl = (item) => {
  const meta = {
    ...extractImageMetaFromUrl(item?.img_url || item?.url || ''),
    bah: item?.bah,
    brxh: item?.brxh,
    folder: item?.folder,
    filename: item?.filename
  }

  if (!meta.bah || !meta.brxh || !meta.folder || !meta.filename) {
    return ''
  }

  return `/api/img-api/image/${encodeURIComponent(meta.bah)}/${encodeURIComponent(meta.brxh)}/${encodeURIComponent(meta.folder)}/${encodeURIComponent(meta.filename)}`
}

const getImageId = (img) => img?.id || img?.imageKey || img?.cx || `${img?.pages || ''}_${img?.btype || ''}`

const normalizeImageItem = (item, index) => {
  const sourceUrl = item?.img_url || item?.url || ''
  const proxyUrl = buildProxyImageUrl(item)
  const displayUrl = sourceUrl || proxyUrl
  const imageKey = extractImageKey(sourceUrl) || item?.cx || item?.id || ''
  return {
    ...item,
    displayUrl,
    sourceUrl,
    proxyUrl,
    imageKey,
    btype: item?.btype ?? item?.type ?? '',
    pages: item?.pages ?? index + 1,
    typeName: getTypeName(item?.btype ?? item?.type)
  }
}

const typeDisplayList = computed(() => {
  const counts = new Map()
  for (const img of images.value) {
    const key = String(img.btype ?? '')
    counts.set(key, (counts.get(key) || 0) + 1)
  }

  return [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14].map((type) => ({
    type,
    count: counts.get(String(type)) || 0
  }))
})

const filteredImages = computed(() => {
  let result = selectedType.value === 'all'
    ? images.value
    : images.value.filter((img) => String(img.btype) === String(selectedType.value))

  if (showSelectedOnly.value) {
    result = result.filter((img) => isImageSelected(img))
  }

  return result
})

const selectedPreviewImages = computed(() =>
  images.value.filter((img) => selectedImages.value.has(getImageId(img)))
)

const currentImage = computed(() => filteredImages.value[selectedImageIndex.value] || filteredImages.value[0] || null)
const previewList = computed(() => filteredImages.value.map((img) => img.displayUrl).filter(Boolean))
const currentImageLabel = computed(() => {
  if (!currentImage.value) return '当前没有可预览的图片'
  return `P${currentImage.value.pages || '-'} - ${getTypeName(currentImage.value.btype)}`
})
const selectedImageLabel = computed(() => {
  if (!selectedPreviewImages.value.length) return '未选择'
  const current = selectedPreviewImages.value[0]
  return current ? `已选 P${current.pages || '-'} · ${getTypeName(current.btype)}` : '未选择'
})

const summaryCards = computed(() => [
  {
    label: '病案号',
    value: archiveInfo.value.bah || '-',
    note: '来自统计明细页面的归档记录'
  },
  {
    label: '图片总数',
    value: images.value.length,
    note: '当前病案袋内所有图片'
  },
  {
    label: '已选图片',
    value: selectedImages.value.size,
    note: '可直接打印或导出 PDF'
  },
  {
    label: '当前筛选',
    value: selectedType.value === 'all' ? '全部' : getTypeName(selectedType.value),
    note: '支持按类型快速切换'
  }
])

const resetSelectionState = () => {
  selectedImages.value.clear()
  selectedImageIndex.value = 0
  showSelectedOnly.value = false
}

const resolveRenderableImageUrl = async (img) => {
  const cacheKey = `${getImageId(img)}::${img?.sourceUrl || img?.proxyUrl || img?.displayUrl || ''}`
  if (renderUrlCache.has(cacheKey)) {
    return renderUrlCache.get(cacheKey)
  }

  const candidates = [img?.proxyUrl, img?.sourceUrl, img?.displayUrl].filter(Boolean)
  let lastError = null

  for (const candidate of candidates) {
    try {
      const response = await fetch(candidate, {
        cache: 'no-store',
        credentials: 'same-origin'
      })

      if (!response.ok) {
        throw new Error(`图片加载失败: ${response.status}`)
      }

      const blob = await response.blob()
      const objectUrl = URL.createObjectURL(blob)
      renderedObjectUrls.add(objectUrl)
      renderUrlCache.set(cacheKey, objectUrl)
      return objectUrl
    } catch (err) {
      lastError = err
    }
  }

  throw lastError || new Error('图片加载失败')
}

const prepareRenderableImages = async (sourceImages) =>
  Promise.all(
    sourceImages.map(async (img) => ({
      ...img,
      blobUrl: await resolveRenderableImageUrl(img)
    }))
  )

const loadImages = async () => {
  if (!archiveInfo.value.bah) {
    error.value = '缺少病案号'
    images.value = []
    return
  }

  loading.value = true
  error.value = ''
  try {
    const response = await getImgApiByBah(archiveInfo.value.bah)
    const payload = response?.data
    if (payload && payload.code !== 200 && !Array.isArray(payload.data)) {
      throw new Error(payload.message || '加载档案图片失败')
    }

    const rawList = Array.isArray(payload?.data) ? payload.data : []
    images.value = rawList.map((item, index) => normalizeImageItem(item, index))
    selectedType.value = 'all'
    resetSelectionState()
    thumbRefs.value = []
    await nextTick()
  } catch (err) {
    console.error('加载档案图片失败:', err)
    error.value = err?.response?.data?.message || err?.message || '加载档案图片失败'
    images.value = []
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

const refreshImages = async () => {
  await loadImages()
}

const goBack = () => {
  router.push('/admin/statistics/detail')
}

const onSelectType = (type) => {
  selectedType.value = type
  selectedImageIndex.value = 0
  thumbRefs.value = []
  nextTick(() => scrollThumbIntoView(0))
}

const setThumbsViewMode = (mode) => {
  thumbsViewMode.value = mode
  localStorage.setItem(THUMBS_VIEW_MODE_KEY, mode)
}

const isImageSelected = (img) => selectedImages.value.has(getImageId(img))

const toggleImageSelection = (img) => {
  const imageId = getImageId(img)
  if (!imageId) return

  if (selectedImages.value.has(imageId)) {
    selectedImages.value.delete(imageId)
  } else {
    selectedImages.value.add(imageId)
  }
}

const clearSelection = () => {
  selectedImages.value.clear()
  showSelectedOnly.value = false
}

const selectAllVisible = () => {
  filteredImages.value.forEach((img) => {
    const imageId = getImageId(img)
    if (imageId) {
      selectedImages.value.add(imageId)
    }
  })
}

const selectImage = async (index) => {
  selectedImageIndex.value = index
  await nextTick()
  scrollThumbIntoView(index)
}

const selectImageById = async (img) => {
  const index = filteredImages.value.findIndex((item) => getImageId(item) === getImageId(img))
  if (index >= 0) {
    selectedImageIndex.value = index
    await nextTick()
    scrollThumbIntoView(index)
  }
}

const toggleCurrentSelection = () => {
  if (!currentImage.value) return
  toggleImageSelection(currentImage.value)
}

const onViewerSwitch = (index) => {
  selectedImageIndex.value = index
  scrollThumbIntoView(index)
}

const scrollThumbIntoView = (index) => {
  const el = thumbRefs.value[index]
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
  }
}

const setThumbRef = (el, index) => {
  if (el) {
    thumbRefs.value[index] = el
  }
}

const handlePrintSelected = () => {
  if (selectedPreviewImages.value.length === 0) {
    ElMessage.warning('请先选择要打印的图片')
    return
  }

  prepareRenderableImages(selectedPreviewImages.value)
    .then((renderableImages) => {
      sessionStorage.setItem('selectedImagesForPrint', JSON.stringify(renderableImages))
      sessionStorage.setItem('printBah', archiveInfo.value.bah || '')
      sessionStorage.setItem('printRecord', JSON.stringify(archiveInfo.value))
      window.open('/print', '_blank', 'noopener,noreferrer')
    })
    .catch((err) => {
      console.error('准备打印图片失败:', err)
      ElMessage.error(err?.message || '准备打印图片失败')
    })
}

const handleExportPdf = async () => {
  if (selectedPreviewImages.value.length === 0) {
    ElMessage.warning('请先选择要导出的图片')
    return
  }

  exportingPdf.value = true
  try {
    const renderableImages = await prepareRenderableImages(selectedPreviewImages.value)
    await exportArchiveImagesToPdf({
      images: renderableImages.map((img) => ({
        ...img,
        typeLabel: getTypeName(img.btype)
      })),
      record: archiveInfo.value,
      title: archiveTitle.value,
      fileName: `病案_${archiveInfo.value.bah || 'archive'}_档案图片.pdf`
    })
    ElMessage.success('PDF 已导出')
  } catch (err) {
    console.error('导出 PDF 失败:', err)
    ElMessage.error(err?.message || '导出 PDF 失败')
  } finally {
    exportingPdf.value = false
  }
}

watch(
  filteredImages,
  async () => {
    if (selectedImageIndex.value >= filteredImages.value.length) {
      selectedImageIndex.value = 0
    }
    await nextTick()
    scrollThumbIntoView(selectedImageIndex.value)
  },
  { deep: true }
)

watch(
  () => archiveInfo.value.bah,
  async () => {
    await loadImages()
  },
  { immediate: true }
)

onMounted(() => {
  if (!archiveInfo.value.bah) {
    error.value = '缺少病案号'
  }
})

</script>

<style scoped>
.archive-image-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 100%;
}

.page-hero {
  align-items: flex-end;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  min-height: 132px;
}

.summary-label {
  color: #64748b;
  font-size: 13px;
}

.summary-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  word-break: break-all;
}

.summary-note {
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.archive-alert {
  border-radius: 16px;
}

.workspace {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  min-height: 0;
}

.control-panel,
.viewer-panel {
  min-width: 0;
  min-height: 0;
}

.control-card + .control-card {
  margin-top: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.03);
}

.info-item dt {
  color: #64748b;
  font-size: 12px;
}

.info-item dd {
  margin: 6px 0 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  word-break: break-all;
}

.type-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  color: #334155;
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
}

.type-item:hover:not(:disabled),
.type-item.active {
  border-color: rgba(59, 130, 246, 0.35);
  background: rgba(219, 234, 254, 0.7);
  color: #0f172a;
  transform: translateY(-1px);
}

.type-item:disabled,
.type-item.disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.type-name {
  font-weight: 700;
}

.type-count {
  flex: 0 0 auto;
  min-width: 34px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  font-size: 12px;
  text-align: center;
}

.batch-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.batch-stat {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.batch-stat span {
  color: #64748b;
  font-size: 12px;
}

.batch-stat strong {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.batch-toggle {
  margin: 16px 0;
}

.batch-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.viewer-card {
  min-height: 100%;
}

.viewer-header {
  align-items: center;
}

.viewer-toolbar {
  display: inline-flex;
  gap: 8px;
}

.view-mode-btn {
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: #fff;
  color: #334155;
  border-radius: 999px;
  padding: 9px 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
}

.view-mode-btn.active {
  background: linear-gradient(135deg, #0f172a, #2563eb);
  color: #fff;
  border-color: transparent;
}

.loading-shell,
.empty-shell {
  min-height: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.gallery-shell {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 18px;
  min-height: 0;
}

.thumb-column {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.thumb-column-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.thumb-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  overflow: auto;
  max-height: 760px;
  padding-right: 4px;
}

.thumb-list.is-list {
  grid-template-columns: 1fr;
}

.thumb-card,
.thumb-row {
  position: relative;
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.86);
  cursor: pointer;
  transition: all 0.18s ease;
}

.thumb-card:hover,
.thumb-row:hover,
.thumb-card.active,
.thumb-row.active {
  border-color: rgba(59, 130, 246, 0.45);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
}

.thumb-card.selected,
.thumb-row.selected {
  outline: 2px solid rgba(37, 99, 235, 0.26);
}

.thumb-image {
  width: 100%;
  aspect-ratio: 0.82;
  display: block;
  background: #fff;
}

.thumb-select {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.76);
  color: #fff;
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.22);
}

.thumb-select-row {
  top: 50%;
  left: 12px;
  right: auto;
  transform: translateY(-50%);
}

.thumb-meta,
.thumb-row-info {
  padding: 12px;
}

.thumb-title,
.thumb-row-title {
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.thumb-subtitle,
.thumb-row-subtitle {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.thumb-row {
  display: flex;
  align-items: center;
  min-height: 72px;
  padding-left: 56px;
}

.thumb-row-info {
  padding-left: 0;
}

.thumb-empty {
  grid-column: 1 / -1;
}

.main-column {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.main-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.main-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.main-subtitle {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.main-actions {
  flex: 0 0 auto;
}

.viewer-frame {
  min-height: 560px;
  border-radius: 24px;
  padding: 18px;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.7), transparent 36%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.06), rgba(59, 130, 246, 0.05));
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-image {
  width: 100%;
  height: 100%;
  min-height: 520px;
}

.selection-strip {
  border-radius: 20px;
  padding: 14px 16px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.selection-strip-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: #475569;
  font-weight: 700;
}

.selection-strip-list {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.selection-chip {
  border: none;
  border-radius: 999px;
  padding: 8px 14px;
  background: linear-gradient(135deg, #0f172a, #2563eb);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.selection-strip-empty {
  margin-top: 10px;
  color: #94a3b8;
  font-size: 13px;
}

@media (max-width: 1400px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace,
  .gallery-shell {
    grid-template-columns: 1fr;
  }

  .thumb-list {
    max-height: 420px;
  }
}

@media (max-width: 768px) {
  .summary-grid,
  .batch-summary,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .main-toolbar {
    flex-direction: column;
  }

  .batch-actions {
    grid-template-columns: 1fr;
  }

  .thumb-list {
    grid-template-columns: 1fr;
  }

  .viewer-frame {
    min-height: 360px;
  }
}
</style>

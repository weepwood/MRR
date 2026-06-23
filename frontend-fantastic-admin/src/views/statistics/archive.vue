<script setup lang="ts">
import type { BAHImageData } from '@/api/types'
import { ArrowLeft, Download, Grid, List, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadBah, getImgApiByBah, updateImageType } from '@/api/modules/image'

defineOptions({ name: 'StatisticsArchivePage' })

interface GalleryImage extends BAHImageData {
  imageUrl?: string
}

const route = useRoute()
const router = useRouter()

const images = ref<GalleryImage[]>([])
const loading = ref(false)
const downloading = ref(false)
const savingType = ref(false)
const errorMsg = ref('')
const searchBah = ref(String(route.params.bah || route.query.bah || ''))
const selectedType = ref<number | 'all'>('all')
const selectedImageIndex = ref(0)
const viewMode = ref<'thumb' | 'list'>('thumb')
const thumbRefs = ref<(HTMLElement | null)[]>([])
const thumbsContainer = ref<HTMLElement | null>(null)

const routeArchive = computed(() => ({
  bah: String(route.query.bah || searchBah.value || ''),
  cid: String(route.query.cid || ''),
  type: String(route.query.type || ''),
  date: String(route.query.date || ''),
  pages: String(route.query.pages || ''),
  openerNo: String(route.query.openerNo || ''),
  sjh: String(route.query.sjh || ''),
}))

const typeOptions = [
  { value: 1, label: '01-病案首页' },
  { value: 2, label: '02-病程记录' },
  { value: 3, label: '03-手术记录' },
  { value: 4, label: '04-术后病程' },
  { value: 5, label: '05-护理记录' },
  { value: 6, label: '06-会诊单' },
  { value: 7, label: '07-特殊检查' },
  { value: 8, label: '08-检验单' },
  { value: 9, label: '09-医嘱' },
  { value: 10, label: '10-体温单' },
  { value: 12, label: '12-出院记录' },
  { value: 13, label: '13-大病历' },
  { value: 14, label: '14-其它' },
]

const filteredImages = computed(() =>
  selectedType.value === 'all'
    ? images.value
    : images.value.filter(item => Number(item.btype) === selectedType.value),
)

const currentImage = computed(() => filteredImages.value[selectedImageIndex.value] || null)
const previewList = computed(() => filteredImages.value.map(item => item.imageUrl || ''))

const typeStats = computed(() => {
  const counts = new Map<number, number>()
  images.value.forEach((item) => {
    const type = Number(item.btype || 0)
    counts.set(type, (counts.get(type) || 0) + 1)
  })
  return typeOptions.map(item => ({ ...item, count: counts.get(item.value) || 0 }))
})

const totalPages = computed(() =>
  images.value.reduce((sum, item) => sum + Number(item.pages || 0), 0),
)

const summaryCards = computed(() => [
  { label: '病案号', value: searchBah.value || '-', note: '当前影像档案袋' },
  { label: '影像数', value: images.value.length, note: '该病案下的扫描记录' },
  { label: '页码累计', value: totalPages.value, note: '按扫描记录页码汇总' },
  { label: '当前类型', value: selectedType.value === 'all' ? '全部' : typeLabel(selectedType.value), note: `${filteredImages.value.length} 张影像` },
])

function imageUrl(item: BAHImageData) {
  return item.ossUrl || item.img_url || ''
}

function typeLabel(type?: number | string | null) {
  const numericType = Number(type)
  return typeOptions.find(item => item.value === numericType)?.label || (type ? `类型 ${type}` : '未分类')
}

function normalizeText(value: unknown) {
  const text = String(value ?? '').trim()
  return text && text.toUpperCase() !== 'NULL' ? text : '-'
}

function formatDate(value: string | undefined) {
  if (!value) {
    return '-'
  }
  return String(value).replace(/\//g, '-')
}

function isValidBah(value: string) {
  return /^\d{8}$/.test(value)
}

async function loadImages() {
  const bah = searchBah.value.trim()
  if (!bah) {
    ElMessage.warning('请输入病案号')
    return
  }
  if (!isValidBah(bah)) {
    ElMessage.warning('请输入 8 位病案号')
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const response = await getImgApiByBah(bah)
    const rawList = Array.isArray((response as any).data) ? (response as any).data : []
    images.value = rawList.map((item: BAHImageData) => ({
      ...item,
      imageUrl: imageUrl(item),
    }))
    selectedType.value = 'all'
    selectedImageIndex.value = 0
    await nextTick()
    scrollCurrentIntoView(false)
  }
  catch (err: any) {
    errorMsg.value = err?.message || '影像加载失败'
    images.value = []
    ElMessage.error(errorMsg.value)
  }
  finally {
    loading.value = false
  }
}

function selectImage(index: number) {
  selectedImageIndex.value = index
  nextTick(() => scrollCurrentIntoView())
}

function scrollCurrentIntoView(smooth = true) {
  const container = thumbsContainer.value
  const target = thumbRefs.value[selectedImageIndex.value]
  if (!container || !target) {
    return
  }
  const top = target.offsetTop - (container.clientHeight - target.clientHeight) / 2
  container.scrollTo({ top: Math.max(0, top), behavior: smooth ? 'smooth' : 'auto' })
}

function selectType(type: number | 'all') {
  selectedType.value = type
  selectedImageIndex.value = 0
  nextTick(() => scrollCurrentIntoView(false))
}

async function saveCurrentType(nextType: number) {
  const img = currentImage.value
  if (!img?.id) {
    ElMessage.warning('当前影像缺少记录 ID，无法修改类型')
    return
  }
  const previousType = img.btype
  img.btype = nextType
  savingType.value = true
  try {
    const res = await updateImageType(img.id, { btype: nextType })
    const ok = (res as any)?.code === 200 || (res as any)?.data?.code === 200 || (res as any)?.status === 200
    if (!ok) {
      throw new Error((res as any)?.message || '类型更新失败')
    }
    ElMessage.success('影像类型已更新')
  }
  catch (err: any) {
    img.btype = previousType
    ElMessage.error(err?.message || '类型更新失败')
  }
  finally {
    savingType.value = false
  }
}

async function handleDownload() {
  const bah = searchBah.value.trim()
  if (!isValidBah(bah)) {
    ElMessage.warning('请输入 8 位病案号')
    return
  }
  downloading.value = true
  try {
    const result = await downloadBah(bah)
    const blob = result instanceof Blob ? result : (result as any)?.data
    if (!(blob instanceof Blob)) {
      throw new TypeError('下载响应不是文件')
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${bah}.zip`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('档案袋下载已开始')
  }
  catch (err: any) {
    ElMessage.error(err?.message || '下载失败')
  }
  finally {
    downloading.value = false
  }
}

function goBack() {
  router.push('/statistics-detail')
}

function onImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.style.opacity = '0.35'
}

watch(filteredImages, () => {
  if (selectedImageIndex.value >= filteredImages.value.length) {
    selectedImageIndex.value = 0
  }
})

onMounted(() => {
  if (searchBah.value) {
    loadImages()
  }
})
</script>

<template>
  <div class="archive-page">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Archive Images
        </p>
        <h2>影像档案袋</h2>
        <p class="subtitle">
          查看单个病案的扫描影像，支持类型筛选、预览、分类修正和整袋下载。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="ArrowLeft" @click="goBack">
          返回明细
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadImages">
          刷新
        </el-button>
        <el-button type="primary" :icon="Download" :loading="downloading" @click="handleDownload">
          下载档案袋
        </el-button>
      </div>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value">
          {{ Number.isFinite(Number(item.value)) ? Number(item.value).toLocaleString('zh-CN') : item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

    <el-card shadow="never">
      <div class="query-bar">
        <el-input v-model="searchBah" clearable placeholder="输入 8 位病案号" @keyup.enter="loadImages" />
        <el-button type="primary" :icon="Search" :loading="loading" @click="loadImages">
          查询
        </el-button>
        <el-segmented
          v-model="viewMode"
          :options="[
            { label: '缩略图', value: 'thumb', icon: Grid },
            { label: '列表', value: 'list', icon: List },
          ]"
        />
      </div>
      <div class="route-meta">
        <span>病案号：{{ normalizeText(routeArchive.bah) }}</span>
        <span>设备：{{ normalizeText(routeArchive.cid) }}</span>
        <span>类型：{{ normalizeText(routeArchive.type) }}</span>
        <span>日期：{{ formatDate(routeArchive.date) }}</span>
        <span>人员：{{ normalizeText(routeArchive.openerNo) }}</span>
        <span>上架号：{{ normalizeText(routeArchive.sjh) }}</span>
      </div>
    </el-card>

    <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon />

    <section class="workspace">
      <aside class="type-panel">
        <div class="panel-title">
          影像类型
        </div>
        <button class="type-item" :class="{ active: selectedType === 'all' }" @click="selectType('all')">
          <span>全部影像</span>
          <strong>{{ images.length }}</strong>
        </button>
        <button
          v-for="item in typeStats"
          :key="item.value"
          class="type-item"
          :class="{ active: selectedType === item.value, disabled: item.count === 0 }"
          :disabled="item.count === 0"
          @click="selectType(item.value)"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.count }}</strong>
        </button>
      </aside>

      <main class="viewer-shell">
        <div ref="thumbsContainer" class="image-list" :class="viewMode">
          <button
            v-for="(img, index) in filteredImages"
            :key="img.id || img.filename || index"
            :ref="(el: any) => { thumbRefs[index] = el }"
            class="image-list-item"
            :class="{ active: index === selectedImageIndex }"
            @click="selectImage(index)"
          >
            <img
              v-if="viewMode === 'thumb'"
              :src="img.imageUrl"
              alt=""
              loading="lazy"
              @error="onImageError"
            >
            <span>P{{ img.pages ?? '-' }}</span>
            <small>{{ typeLabel(img.btype) }}</small>
          </button>
          <div v-if="!loading && filteredImages.length === 0" class="empty-list">
            暂无影像
          </div>
        </div>

        <div v-loading="loading" class="main-view">
          <template v-if="currentImage">
            <el-image
              class="main-image"
              :src="currentImage.imageUrl"
              fit="contain"
              :preview-src-list="previewList"
              :initial-index="selectedImageIndex"
              :preview-teleported="true"
              :hide-on-click-modal="false"
            />
            <div class="image-meta">
              <div>
                <strong>P{{ currentImage.pages ?? '-' }}</strong>
                <span>{{ normalizeText(currentImage.filename) }}</span>
              </div>
              <div class="type-editor">
                <span>分类</span>
                <el-select
                  :model-value="Number(currentImage.btype || 0)"
                  :loading="savingType"
                  size="small"
                  style="width: 180px;"
                  @change="saveCurrentType"
                >
                  <el-option
                    v-for="item in typeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
            </div>
          </template>
          <el-empty v-else description="请选择影像" />
        </div>
      </main>
    </section>
  </div>
</template>

<style scoped>
.archive-page {
  display: grid;
  gap: 18px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.summary-value {
  margin-top: 8px;
  overflow-wrap: anywhere;
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
}

.summary-note {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.query-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  gap: 10px;
  align-items: center;
}

.route-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 12px;
  font-size: 13px;
  color: #64748b;
}

.workspace {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
  min-height: 620px;
}

.type-panel {
  display: grid;
  gap: 8px;
  align-content: start;
  padding: 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
}

.panel-title {
  margin-bottom: 4px;
  font-weight: 800;
  color: #172033;
}

.type-item {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 8px 10px;
  font-size: 13px;
  color: #24324b;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
}

.type-item.active,
.type-item:hover:not(.disabled) {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.type-item.disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.viewer-shell {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  min-height: 620px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
}

.image-list {
  display: grid;
  gap: 8px;
  align-content: start;
  max-height: 720px;
  padding: 10px;
  overflow: auto;
  background: #f8fafc;
  border-right: 1px solid #e5e7eb;
}

.image-list.thumb {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.image-list.list {
  grid-template-columns: 1fr;
}

.image-list-item {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 7px;
  color: #24324b;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
}

.image-list-item.active,
.image-list-item:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.image-list-item img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  background: #eef2f7;
  border-radius: 5px;
}

.image-list-item span {
  font-size: 12px;
  font-weight: 800;
}

.image-list-item small {
  overflow: hidden;
  font-size: 11px;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-list {
  padding: 40px 0;
  color: #94a3b8;
  text-align: center;
}

.main-view {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 620px;
  padding: 18px;
  background: #eef2f7;
}

.main-image {
  width: 100%;
  height: 560px;
}

.image-meta {
  position: absolute;
  right: 18px;
  bottom: 18px;
  left: 18px;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid #e5e7eb;
  border-radius: 7px;
  box-shadow: 0 10px 24px rgb(15 23 42 / 10%);
}

.image-meta strong {
  margin-right: 8px;
}

.type-editor {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: #64748b;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .type-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panel-title {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .page-header,
  .image-meta {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .query-bar,
  .viewer-shell,
  .type-panel {
    grid-template-columns: 1fr;
  }

  .image-list {
    max-height: 260px;
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
  }
}
</style>

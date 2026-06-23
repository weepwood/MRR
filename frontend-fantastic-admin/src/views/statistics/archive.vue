<script setup lang="ts">
import type { BAHImageData, BAHRecord } from '@/api/types'
import { ArrowLeft, Download, Grid, List, Refresh, Search, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadBah, getImgByCode, getImgApiByBah, updateImageType } from '@/api/modules/image'
import { getPatientByBah } from '@/api/modules/search'

defineOptions({ name: 'StatisticsArchivePage' })

interface GalleryImage extends BAHImageData {
  imageUrl?: string
}

const route = useRoute()
const router = useRouter()

function sanitizeParam(val: unknown) {
  const s = String(val ?? '').trim()
  return s.startsWith(':') ? '' : s
}

const images = ref<GalleryImage[]>([])
const loading = ref(false)
const downloading = ref(false)
const savingType = ref(false)
const errorMsg = ref('')
const patientList = ref<BAHRecord[]>([])
const patientLoading = ref(false)
const searchBah = ref(sanitizeParam(route.params.bah || route.query.bah))
const searchSjh = ref(sanitizeParam(route.query.sjh))
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

function padCode(value: string) {
  const trimmed = value.trim()
  if (trimmed.length > 0 && trimmed.length < 8 && /^\d+$/.test(trimmed)) {
    return trimmed.padStart(8, '0')
  }
  return trimmed
}

async function loadImages() {
  const bah = padCode(searchBah.value)
  const sjh = padCode(searchSjh.value)
  if (!bah && !sjh) {
    ElMessage.warning('请输入病案号或上架号')
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const response = bah && !sjh
      ? await getImgApiByBah(bah)
      : await getImgByCode(bah, sjh)
    const rawList = Array.isArray((response as any).data) ? (response as any).data : []
    images.value = rawList.map((item: BAHImageData) => ({
      ...item,
      imageUrl: imageUrl(item),
    }))
    selectedType.value = 'all'
    selectedImageIndex.value = 0
    loadPatient(bah || sjh)
    await nextTick()
    scrollCurrentIntoView(false)
  }
  catch (err: any) {
    errorMsg.value = err?.message || '影像加载失败'
    images.value = []
  }
  finally {
    loading.value = false
  }
}

async function loadPatient(bah: string) {
  patientLoading.value = true
  try {
    const res = await getPatientByBah(bah)
    patientList.value = Array.isArray((res as any).data) ? (res as any).data : []
  }
  catch {
    patientList.value = []
  }
  finally {
    patientLoading.value = false
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
  const bah = padCode(searchBah.value)
  if (!bah) {
    ElMessage.warning('请输入病案号')
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
  if (searchBah.value || searchSjh.value) {
    loadImages()
  }
})
</script>

<template>
  <div class="archive-page">
    <div class="page-header">
      <div>
        <h2>影像档案袋</h2>
        <p class="subtitle">
          病案影像检索、预览与归档
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

    <el-card shadow="never">
      <div class="search-bar">
        <div class="search-fields">
          <el-input v-model="searchBah" clearable placeholder="病案号" @keyup.enter="loadImages" />
          <el-input v-model="searchSjh" clearable placeholder="上架号" @keyup.enter="loadImages" />
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadImages">
            查询
          </el-button>
        </div>
        <el-segmented
          v-model="viewMode"
          :options="[
            { label: '缩略图', value: 'thumb', icon: Grid },
            { label: '列表', value: 'list', icon: List },
          ]"
        />
      </div>
      <div v-if="images.length" class="route-meta">
        <span>病案号：{{ normalizeText(routeArchive.bah) }}</span>
        <span>设备：{{ normalizeText(routeArchive.cid) }}</span>
        <span>类型：{{ normalizeText(routeArchive.type) }}</span>
        <span>日期：{{ formatDate(routeArchive.date) }}</span>
        <span>人员：{{ normalizeText(routeArchive.openerNo) }}</span>
        <span>上架号：{{ normalizeText(routeArchive.sjh) }}</span>
      </div>
    </el-card>

    <el-card v-if="patientList.length > 0" shadow="never" class="patient-card">
      <template #header>
        <div class="patient-header">
          <el-icon><User /></el-icon>
          <span>患者信息</span>
        </div>
      </template>
      <div v-for="p in patientList" :key="p.id" class="patient-body">
        <div class="patient-field">
          <span class="field-label">姓名</span>
          <span class="field-value">{{ p.name || '-' }}</span>
        </div>
        <div class="patient-field">
          <span class="field-label">病案号</span>
          <span class="field-value">{{ p.bah || '-' }}</span>
        </div>
        <div class="patient-field">
          <span class="field-label">科室</span>
          <span class="field-value">{{ p.department || '-' }}</span>
        </div>
        <div class="patient-field">
          <span class="field-label">入院时间</span>
          <span class="field-value">{{ p.admissionTime || '-' }}</span>
        </div>
      </div>
    </el-card>

    <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon />

    <template v-if="images.length">
      <div class="type-bar">
        <button class="type-tab" :class="{ active: selectedType === 'all' }" @click="selectType('all')">
          全部
          <el-tag size="small" class="type-count">{{ images.length }}</el-tag>
        </button>
        <button
          v-for="item in typeStats"
          :key="item.value"
          class="type-tab"
          :class="{ active: selectedType === item.value, disabled: item.count === 0 }"
          :disabled="item.count === 0"
          @click="selectType(item.value)"
        >
          {{ item.label }}
          <el-tag v-if="item.count" size="small" :type="selectedType === item.value ? 'primary' : 'info'" class="type-count">{{ item.count }}</el-tag>
        </button>
      </div>

      <div class="viewer-layout">
        <div ref="thumbsContainer" class="thumb-strip" :class="viewMode">
          <button
            v-for="(img, index) in filteredImages"
            :key="img.id || img.filename || index"
            :ref="(el: any) => { thumbRefs[index] = el }"
            class="thumb-item"
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
            <span class="thumb-page">P{{ img.pages ?? '-' }}</span>
            <small>{{ typeLabel(img.btype) }}</small>
          </button>
          <div v-if="!loading && filteredImages.length === 0" class="empty-list">
            暂无影像
          </div>
        </div>

        <div v-loading="loading" class="preview-panel">
          <template v-if="currentImage">
            <el-image
              class="preview-image"
              :src="currentImage.imageUrl"
              fit="contain"
              :preview-src-list="previewList"
              :initial-index="selectedImageIndex"
              :preview-teleported="true"
              :hide-on-click-modal="false"
            />
            <div class="preview-bar">
              <div class="preview-info">
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
      </div>
    </template>

    <div v-if="!images.length && !loading && !errorMsg" class="empty-state">
      <el-empty description="输入病案号或上架号查询影像" />
    </div>
  </div>
</template>

<style scoped>
.archive-page {
  display: grid;
  gap: 16px;
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
  gap: 8px;
}

h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
}

.subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}

.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.search-fields {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 1 1 360px;
}

.search-fields .el-input {
  flex: 1 1 160px;
}

.route-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-top: 14px;
  padding-top: 14px;
  font-size: 13px;
  color: #475569;
  border-top: 1px solid #e5e7eb;
}

.patient-card {
  margin-top: 0;
}

.patient-header {
  display: flex;
  gap: 8px;
  align-items: center;
  font-weight: 700;
  font-size: 14px;
}

.patient-body {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.patient-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-label {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.field-value {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.type-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
}

.type-tab {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 4px 10px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
}

.type-tab.active {
  color: var(--el-color-primary);
  font-weight: 700;
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.type-tab:hover:not(.disabled) {
  background: #f1f5f9;
}

.type-tab.disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.type-count {
  pointer-events: none;
}

.viewer-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 0;
  min-height: 600px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
}

.thumb-strip {
  display: grid;
  gap: 6px;
  align-content: start;
  max-height: 700px;
  padding: 8px;
  overflow-y: auto;
  background: #f8fafc;
  border-right: 1px solid #e5e7eb;
}

.thumb-strip.thumb {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.thumb-strip.list {
  grid-template-columns: 1fr;
}

.thumb-item {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 6px;
  color: #24324b;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.thumb-item.active,
.thumb-item:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.thumb-item img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  background: #eef2f7;
  border-radius: 4px;
}

.thumb-page {
  font-size: 12px;
  font-weight: 800;
}

.thumb-item small {
  overflow: hidden;
  font-size: 11px;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-list {
  padding: 30px 0;
  color: #94a3b8;
  text-align: center;
}

.preview-panel {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 600px;
  padding: 16px;
  background: #eef2f7;
}

.preview-image {
  width: 100%;
  height: 540px;
}

.preview-bar {
  position: absolute;
  right: 16px;
  bottom: 16px;
  left: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: rgb(255 255 255 / 93%);
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  box-shadow: 0 8px 20px rgb(15 23 42 / 10%);
}

.preview-info {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}

.preview-info strong {
  margin-right: 2px;
}

.type-editor {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: #64748b;
}

.empty-state {
  padding: 60px 0;
}

@media (max-width: 1100px) {
  .viewer-layout {
    grid-template-columns: 1fr;
  }

  .thumb-strip {
    max-height: 260px;
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
  }
}

@media (max-width: 720px) {
  .page-header,
  .preview-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-fields {
    flex-direction: column;
  }

  .search-fields .el-input {
    width: 100%;
  }

  .thumb-strip {
    max-height: 200px;
  }
}
</style>

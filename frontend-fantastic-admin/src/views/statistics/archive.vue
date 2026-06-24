<script setup lang="ts">
import type { BAHImageData, BAHRecord } from '@/api/types'
import { ArrowLeft, Download, Grid, List, Printer, Refresh, Search, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadBah, getImgApiByBah, getImgByCode, updateImageType } from '@/api/modules/image'
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
const selectedIds = ref<Set<string>>(new Set())
const printing = ref(false)
const thumbRefs = ref<(HTMLElement | null)[]>([])
const thumbsContainer = ref<HTMLElement | null>(null)
const thumbColumns = ref(2)
const thumbStripWidth = ref(220)
const thumbItemWidth = ref(96)
const pageSize = ref(20)

let resizeObserver: ResizeObserver | null = null

function calcThumbColumns() {
  if (!thumbsContainer.value) {
    thumbColumns.value = viewMode.value === 'thumb' ? 2 : 1
    return
  }
  const containerWidth = thumbsContainer.value.clientWidth
  thumbStripWidth.value = containerWidth
  if (viewMode.value === 'list') {
    thumbColumns.value = 1
    return
  }
  const gap = 6
  const minItemWidth = 80
  const maxItemWidth = 130
  const idealCols = Math.max(1, Math.floor((containerWidth + gap) / (minItemWidth + gap)))
  const actualItemWidth = Math.min(maxItemWidth, Math.max(minItemWidth, (containerWidth - (idealCols - 1) * gap) / idealCols))
  thumbItemWidth.value = actualItemWidth
  thumbColumns.value = idealCols
  const viewportHeight = window.innerHeight
  const stripTop = thumbsContainer.value.getBoundingClientRect().top
  const availableHeight = viewportHeight - stripTop - 24
  const itemHeight = actualItemWidth * 4 / 3 + 36
  const rows = Math.max(1, Math.floor(availableHeight / itemHeight))
  pageSize.value = thumbColumns.value * rows
}

onMounted(() => {
  resizeObserver = new ResizeObserver(() => { calcThumbColumns() })
  if (thumbsContainer.value) {
    resizeObserver.observe(thumbsContainer.value)
  }
  calcThumbColumns()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})

watch(viewMode, () => { nextTick(() => calcThumbColumns()) })

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

function imgKey(img: GalleryImage): string {
  return String(img.id || img.filename || '')
}

function toggleSelect(img: GalleryImage) {
  const key = imgKey(img)
  const next = new Set(selectedIds.value)
  if (next.has(key)) {
    next.delete(key)
  }
  else {
    next.add(key)
  }
  selectedIds.value = next
}

function isSelected(img: GalleryImage): boolean {
  return selectedIds.value.has(imgKey(img))
}

const selectedCount = computed(() => selectedIds.value.size)

function selectAllVisible() {
  if (selectedIds.value.size === filteredImages.value.length && filteredImages.value.length > 0) {
    selectedIds.value = new Set()
  }
  else {
    selectedIds.value = new Set(filteredImages.value.map(imgKey))
  }
}

const allVisibleSelected = computed(() =>
  filteredImages.value.length > 0 && filteredImages.value.every(isSelected),
)

watch(filteredImages, () => {
  if (selectedImageIndex.value >= filteredImages.value.length) {
    selectedImageIndex.value = 0
  }
  const validKeys = new Set(filteredImages.value.map(imgKey))
  const filtered = new Set([...selectedIds.value].filter(k => validKeys.has(k)))
  if (filtered.size !== selectedIds.value.size) {
    selectedIds.value = filtered
  }
})

async function printSelected() {
  const selected = filteredImages.value.filter(isSelected)
  if (!selected.length) {
    ElMessage.warning('请先选择要打印的影像')
    return
  }
  printing.value = true
  try {
    const patient = patientList.value[0]
    const bah = patient?.bah || searchBah.value || ''
    const headerHtml = `
      <div class="print-header">
        <h1>影像档案袋</h1>
        <div class="patient-info">
          ${patient ? `<span>姓名：${patient.name || '-'}</span>` : ''}
          <span>病案号：${bah}</span>
          ${patient?.department ? `<span>科室：${patient.department}</span>` : ''}
          ${patient?.admissionTime ? `<span>入院：${patient.admissionTime}</span>` : ''}
          <span>共 ${selected.length} 张</span>
          <span>打印：${new Date().toLocaleString('zh-CN')}</span>
        </div>
      </div>`

    const imagesHtml = selected.map((img) => {
      const src = img.imageUrl || ''
      return `
        <div class="print-page">
          <div class="print-img-meta">
            <span>P${img.pages ?? '-'}</span>
            <span>${typeLabel(img.btype)}</span>
          </div>
          <img src="${src}" alt="" />
        </div>`
    }).join('')

    const iframe = document.createElement('iframe')
    iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'
    document.body.appendChild(iframe)
    const doc = iframe.contentWindow?.document
    if (!doc) {
      document.body.removeChild(iframe)
      return
    }
    doc.open()
    doc.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>影像打印 - ${bah}</title>
      <style>
        @page { margin: 8mm; }
        * { box-sizing: border-box; }
        body { margin: 0; font-family: -apple-system, "Microsoft YaHei", sans-serif; color: #1e293b; }
        .print-header { text-align: center; padding: 6px 0 10px; border-bottom: 2px solid #1e293b; margin-bottom: 8px; }
        .print-header h1 { font-size: 16px; margin: 0 0 4px; }
        .patient-info { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px 14px; font-size: 11px; color: #64748b; }
        .print-page { page-break-after: always; text-align: center; }
        .print-page:last-child { page-break-after: auto; }
        .print-img-meta { display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; padding: 2px 4px; }
        .print-page img { max-width: 100%; max-height: 88vh; object-fit: contain; }
      </style></head><body>${headerHtml}${imagesHtml}</body></html>`)
    doc.close()

    const win = iframe.contentWindow
    if (!win) {
      document.body.removeChild(iframe)
      return
    }
    const cleanup = () => {
      setTimeout(() => {
        if (iframe.parentNode) {
          document.body.removeChild(iframe)
        }
      }, 2000)
    }
    iframe.onload = () => {
      win.focus()
      win.print()
      cleanup()
    }
    setTimeout(() => {
      if (iframe.parentNode) {
        win.focus()
        win.print()
        cleanup()
      }
    }, 1500)
  }
  catch (err: any) {
    ElMessage.error(err?.message || '打印失败')
  }
  finally {
    printing.value = false
  }
}

function onImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.style.opacity = '0.35'
}

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
        <el-button type="primary" :icon="Printer" :loading="printing" :disabled="!selectedCount" @click="printSelected">
          打印选中{{ selectedCount ? ` (${selectedCount})` : '' }}
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
        <div class="type-tabs">
          <button class="type-tab" :class="{ active: selectedType === 'all' }" @click="selectType('all')">
            全部
            <el-tag size="small" class="type-count">
              {{ images.length }}
            </el-tag>
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
            <el-tag v-if="item.count" size="small" :type="selectedType === item.value ? 'primary' : 'info'" class="type-count">
              {{ item.count }}
            </el-tag>
          </button>
        </div>
        <div class="type-bar-actions">
          <span class="select-count">已选 {{ selectedCount }}/{{ filteredImages.length }}</span>
          <el-button size="small" link @click="selectAllVisible">
            {{ allVisibleSelected ? '取消全选' : '全选' }}
          </el-button>
        </div>
      </div>

      <div class="viewer-layout">
        <div ref="thumbsContainer" class="thumb-strip" :class="viewMode">
          <div
            v-for="(img, index) in filteredImages"
            :key="img.id || img.filename || index"
            :ref="(el: any) => { thumbRefs[index] = el }"
            class="thumb-item"
            :class="{ active: index === selectedImageIndex, checked: isSelected(img) }"
            :style="viewMode === 'thumb' ? { width: `${thumbItemWidth}px` } : {}"
            @click="selectImage(index)"
          >
            <span class="thumb-check" :class="{ checked: isSelected(img) }" @click.stop="toggleSelect(img)">
              <svg v-if="isSelected(img)" viewBox="0 0 16 16" width="12" height="12"><path d="M13.485 4.485a1 1 0 0 1 0 1.415l-6.5 6.5a1 1 0 0 1-1.414 0l-3-3a1 1 0 1 1 1.414-1.414L6.278 10.586l5.793-5.793a1 1 0 0 1 1.414 0z" fill="currentColor" /></svg>
            </span>
            <img
              v-if="viewMode === 'thumb'"
              :src="img.imageUrl"
              alt=""
              loading="lazy"
              @error="onImageError"
            >
            <span class="thumb-page">P{{ img.pages ?? '-' }}</span>
            <small>{{ typeLabel(img.btype) }}</small>
          </div>
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
              <div class="preview-actions">
                <el-button size="small" :type="isSelected(currentImage) ? 'success' : 'default'" @click="toggleSelect(currentImage)">
                  {{ isSelected(currentImage) ? '已选' : '选中' }}
                </el-button>
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
  color: var(--text-secondary);
}

.search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.search-fields {
  display: flex;
  flex: 1 1 360px;
  gap: 8px;
  align-items: center;
}

.search-fields .el-input {
  flex: 1 1 160px;
}

.route-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  padding-top: 14px;
  margin-top: 14px;
  font-size: 13px;
  color: var(--text-secondary);
  border-top: 1px solid var(--divider);
}

.patient-card {
  margin-top: 0;
}

.patient-header {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
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
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.field-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.type-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 7px;
}

.type-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.type-bar-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.select-count {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.type-tab {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 4px 10px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
}

.type-tab.active {
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.type-tab:hover:not(.disabled) {
  background: var(--surface-alt);
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
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0;
  height: calc(100vh - 260px);
  min-height: 450px;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 7px;
}

.thumb-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
  width: 240px;
  min-width: 180px;
  max-width: 300px;
  padding: 8px;
  overflow-y: auto;
  resize: horizontal;
  background: var(--surface-muted);
  border-right: 1px solid var(--divider);
}

.thumb-strip.list {
  flex-flow: column nowrap;
  width: 220px;
  min-width: 160px;
  max-width: 280px;
}

.thumb-strip.list .thumb-item {
  width: 100% !important;
}

.thumb-item {
  position: relative;
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 6px;
  color: var(--text-primary);
  cursor: pointer;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 6px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.thumb-item.active,
.thumb-item:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.thumb-item.checked {
  border-color: hsl(var(--primary));
  box-shadow: 0 0 0 2px hsl(var(--primary) / 25%);
}

.thumb-check {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: #fff;
  background: rgb(255 255 255 / 80%);
  border: 1.5px solid var(--divider);
  border-radius: 4px;
  transition: all 0.15s;
}

.thumb-check.checked {
  color: #fff;
  background: hsl(var(--primary));
  border-color: hsl(var(--primary));
}

.thumb-item img {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  background: var(--surface-alt);
  border-radius: 4px;
}

.thumb-page {
  font-size: 12px;
  font-weight: 800;
}

.thumb-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.empty-list {
  padding: 30px 0;
  color: var(--text-tertiary);
  text-align: center;
}

.preview-panel {
  position: relative;
  display: grid;
  place-items: center;
  height: 100%;
  min-height: 0;
  padding: 16px;
  background: var(--surface-alt);
}

.preview-image {
  width: 100%;
  height: 100%;
  max-height: calc(100vh - 380px);
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
  background: var(--surface-overlay, rgb(255 255 255 / 93%));
  border: 1px solid var(--divider);
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

.preview-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.type-editor {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--text-secondary);
}

.empty-state {
  padding: 60px 0;
}

@media (width <= 1100px) {
  .viewer-layout {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .thumb-strip {
    width: 100% !important;
    max-width: none !important;
    max-height: 220px;
    border-right: 0;
    border-bottom: 1px solid var(--divider);
  }

  .thumb-strip.list {
    max-height: 200px;
  }
}

@media (width <= 720px) {
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
}
</style>

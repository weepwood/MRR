<script setup lang="ts">
import type { GalleryImage, RouteArchiveMeta, ViewMode } from './archive/types'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArchiveLookupValidationMessage } from '@/utils/medical-record-code'
import ArchiveHeader from './archive/components/ArchiveHeader.vue'
import ArchiveSearchBar from './archive/components/ArchiveSearchBar.vue'
import PatientCard from './archive/components/PatientCard.vue'
import PreviewPanel from './archive/components/PreviewPanel.vue'
import ThumbStrip from './archive/components/ThumbStrip.vue'
import TypeFilterBar from './archive/components/TypeFilterBar.vue'
import { useArchiveImages } from './archive/composables/useArchiveImages'
import { useArchivePrint } from './archive/composables/useArchivePrint'
import { useSelection } from './archive/composables/useSelection'
import { buildTypeStats, padCode } from './archive/constants'

defineOptions({ name: 'StatisticsArchivePage' })

const route = useRoute()
const router = useRouter()

const {
  images,
  patientList,
  loading,
  patientLoading,
  downloading,
  savingType,
  errorMsg,
  searchBah,
  searchSjh,
  loadImages,
  handleDownload,
  saveImageType,
} = useArchiveImages()

const selectedType = ref<number | 'all'>('all')
const selectedImageIndex = ref(0)
const viewMode = ref<ViewMode>('thumb')
const thumbStripRef = ref<InstanceType<typeof ThumbStrip> | null>(null)

const filteredImages = computed<GalleryImage[]>(() =>
  selectedType.value === 'all'
    ? images.value
    : images.value.filter(item => Number(item.btype) === selectedType.value),
)

const selection = useSelection<GalleryImage>(filteredImages)
const { selectedCount, allVisibleSelected, selectedItems, isSelected, toggleSelect, selectAllVisible } = selection

const { printing, printSelected } = useArchivePrint()

const currentImage = computed(() => filteredImages.value[selectedImageIndex.value] || null)
const previewList = computed(() => filteredImages.value.map(item => item.imageUrl || ''))
const typeStats = computed(() => buildTypeStats(images.value))
const patient = computed(() => patientList.value[0])

const routeArchive = computed<RouteArchiveMeta>(() => ({
  bah: searchBah.value || sanitizeParam(route.query.bah),
  cid: sanitizeParam(route.query.cid),
  type: sanitizeParam(route.query.type),
  date: sanitizeParam(route.query.date),
  pages: sanitizeParam(route.query.pages),
  openerNo: sanitizeParam(route.query.openerNo),
  sjh: searchSjh.value || sanitizeParam(route.query.sjh),
}))

function sanitizeParam(val: unknown): string {
  const value = Array.isArray(val) ? val[0] : val
  const text = String(value ?? '').trim()
  return text.startsWith(':') ? '' : text
}

function normalizeSearchParam(value: unknown): string {
  const text = sanitizeParam(value)
  return text ? padCode(text) : ''
}

function clearArchiveState(message = '') {
  images.value = []
  patientList.value = []
  errorMsg.value = message
  selectedImageIndex.value = 0
}

async function syncSearchFromRoute() {
  const legacyBah = normalizeSearchParam(route.params.bah)
  const bah = normalizeSearchParam(route.query.bah || legacyBah)
  const sjh = normalizeSearchParam(route.query.sjh)

  searchBah.value = bah
  searchSjh.value = sjh

  // 旧链接 /archive/:bah 自动转换为具名查询参数，避免病案号和上架号语义混淆。
  if (legacyBah) {
    await router.replace({
      path: '/archive',
      query: {
        ...route.query,
        bah,
        ...(sjh ? { sjh } : {}),
      },
    })
    return
  }

  if (bah || sjh) {
    await loadImages()
  }
  else {
    clearArchiveState()
  }
}

async function handleSearch() {
  const bah = normalizeSearchParam(searchBah.value)
  const sjh = normalizeSearchParam(searchSjh.value)
  const validationMessage = getArchiveLookupValidationMessage(bah, sjh)

  searchBah.value = bah
  searchSjh.value = sjh

  if (validationMessage) {
    clearArchiveState(validationMessage)
    ElMessage.warning(validationMessage)
    return
  }

  const query: Record<string, string> = {}
  if (bah) {
    query.bah = bah
  }
  if (sjh) {
    query.sjh = sjh
  }

  const location = {
    path: '/archive',
    query,
  }

  if (router.resolve(location).fullPath === route.fullPath) {
    await loadImages()
    return
  }

  await router.push(location)
}

function selectImage(index: number) {
  selectedImageIndex.value = index
  thumbStripRef.value?.scrollToIndex(index, true)
}

function selectType(value: number | 'all') {
  selectedType.value = value
  selectedImageIndex.value = 0
}

function toggleCurrent() {
  if (currentImage.value) {
    toggleSelect(currentImage.value)
  }
}

function handleSaveType(type: number) {
  if (currentImage.value) {
    saveImageType(currentImage.value, type)
  }
}

function handlePrint() {
  printSelected(selectedItems.value)
}

function goBack() {
  router.push('/statistics-detail')
}

function navigate(delta: number) {
  if (!filteredImages.value.length) {
    return
  }
  const next = Math.min(
    filteredImages.value.length - 1,
    Math.max(0, selectedImageIndex.value + delta),
  )
  selectImage(next)
}

function onKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement | null
  if (target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable) {
    return
  }
  if (!filteredImages.value.length) {
    return
  }
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    navigate(-1)
  }
  else if (e.key === 'ArrowRight') {
    e.preventDefault()
    navigate(1)
  }
  else if (e.key === 'Home') {
    e.preventDefault()
    selectImage(0)
  }
  else if (e.key === 'End') {
    e.preventDefault()
    selectImage(filteredImages.value.length - 1)
  }
}

watch(filteredImages, () => {
  if (selectedImageIndex.value >= filteredImages.value.length) {
    selectedImageIndex.value = 0
  }
  thumbStripRef.value?.resetVisible()
  nextTick(() => thumbStripRef.value?.scrollToIndex(selectedImageIndex.value, false))
})

// 缓存模式、浏览器前进后退或直接打开链接时，同步 URL 中的搜索条件并重新加载。
watch(
  () => [route.params.bah, route.query.bah, route.query.sjh],
  syncSearchFromRoute,
  { immediate: true },
)

onMounted(() => {
  document.body.classList.add('archive-immersive')
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.body.classList.remove('archive-immersive')
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="archive-page">
    <div
      class="archive-workspace"
      :class="{
        'has-images': images.length > 0,
        'is-empty': images.length === 0,
        'is-list-mode': viewMode === 'list',
      }"
    >
      <section class="archive-sidebar">
        <ArchiveHeader
          :loading="loading"
          :downloading="downloading"
          :printing="printing"
          :selected-count="selectedCount"
          @back="goBack"
          @refresh="handleSearch"
          @download="handleDownload"
          @print="handlePrint"
        />

        <ArchiveSearchBar
          v-model:search-bah="searchBah"
          v-model:search-sjh="searchSjh"
          :route-meta="routeArchive"
          :has-images="images.length > 0"
          :loading="loading"
          @search="handleSearch"
        />

        <PatientCard :patient="patient" :loading="patientLoading" />

        <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon />

        <template v-if="images.length > 0">
          <TypeFilterBar
            v-model:selected-type="selectedType"
            :type-stats="typeStats"
            :total-count="images.length"
            :selected-count="selectedCount"
            :filtered-count="filteredImages.length"
            :all-visible-selected="allVisibleSelected"
            @select-type="selectType"
            @select-all="selectAllVisible"
          />
        </template>

        <div v-else-if="!loading && !errorMsg" class="empty-state">
          <el-empty description="输入病案号或上架号查询影像" />
        </div>
      </section>

      <div v-if="images.length > 0" class="viewer-layout">
        <ThumbStrip
          ref="thumbStripRef"
          v-model:view-mode="viewMode"
          :images="filteredImages"
          :selected-index="selectedImageIndex"
          :is-selected="isSelected"
          @select="selectImage"
          @toggle="toggleSelect"
        />
      </div>

      <PreviewPanel
        v-if="images.length > 0"
        :image="currentImage"
        :preview-list="previewList"
        :index="selectedImageIndex"
        :total="filteredImages.length"
        :is-selected="currentImage ? isSelected(currentImage) : false"
        :saving-type="savingType"
        :loading="loading"
        @toggle="toggleCurrent"
        @save-type="handleSaveType"
        @navigate="navigate"
        @select="selectImage"
      />
    </div>
  </div>
</template>

<style scoped>
.archive-page {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  padding: 8px;
  background: var(--surface-muted);
}

.archive-workspace {
  min-width: 0;
  height: 100%;
}

.archive-workspace.has-images {
  display: grid;
  grid-template-columns: minmax(280px, 320px) 200px minmax(0, 1fr);
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.archive-workspace.has-images.is-list-mode {
  grid-template-columns: minmax(280px, 320px) 200px minmax(0, 1fr);
}

.archive-sidebar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.has-images .archive-sidebar {
  overflow: hidden auto;
}

:global(body.archive-immersive .toolbar-container) {
  display: none !important;
}

:global(body.archive-immersive .layout .wrapper .main-container) {
  height: 100%;
  min-height: 0;
}

:global(body.archive-immersive .layout .wrapper .main-container .main) {
  flex: 1 1 0;
  height: auto;
  min-height: 0;
  padding: 0 !important;
  margin-top: var(--g-tabbar-actual-height) !important;
  overflow: hidden;
}

.archive-workspace.is-empty {
  display: grid;
  place-items: center;
  padding: 24px;
}

.is-empty .archive-sidebar {
  width: min(100%, 560px);
  padding: 24px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 16px;
  box-shadow: 0 12px 32px rgb(0 0 0 / 5%);
}

.is-empty .search-card {
  padding: 0;
  background: transparent;
  border: 0;
}

.is-empty .empty-state {
  min-height: 120px;
}

.viewer-layout {
  display: flex;
  min-height: 0;
  overflow: hidden;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 240px;
}

@media (width <= 1100px) {
  .archive-workspace.has-images,
  .archive-workspace.has-images.is-list-mode {
    grid-template-columns: 1fr;
    height: auto;
    min-height: initial;
  }

  .archive-workspace.is-empty {
    padding: 16px;
  }

  .has-images .archive-sidebar {
    overflow: visible;
  }

  .archive-workspace.has-images .preview-panel {
    height: min(68vh, 640px);
    min-height: 420px;
  }
}
</style>

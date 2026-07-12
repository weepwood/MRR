<script setup lang="ts">
import type { GalleryImage, RouteArchiveMeta, ViewMode } from './archive/types'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArchiveHeader from './archive/components/ArchiveHeader.vue'
import ArchiveSearchBar from './archive/components/ArchiveSearchBar.vue'
import PatientCard from './archive/components/PatientCard.vue'
import PreviewPanel from './archive/components/PreviewPanel.vue'
import ThumbStrip from './archive/components/ThumbStrip.vue'
import TypeFilterBar from './archive/components/TypeFilterBar.vue'
import { useArchiveImages } from './archive/composables/useArchiveImages'
import { useArchivePrint } from './archive/composables/useArchivePrint'
import { useSelection } from './archive/composables/useSelection'
import { buildTypeStats } from './archive/constants'

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
  bah: String(route.query.bah || searchBah.value || ''),
  cid: String(route.query.cid || ''),
  type: String(route.query.type || ''),
  date: String(route.query.date || ''),
  pages: String(route.query.pages || ''),
  openerNo: String(route.query.openerNo || ''),
  sjh: String(route.query.sjh || ''),
}))

function sanitizeParam(val: unknown): string {
  const s = String(val ?? '').trim()
  return s.startsWith(':') ? '' : s
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

onMounted(() => {
  searchBah.value = sanitizeParam(route.params.bah || route.query.bah)
  searchSjh.value = sanitizeParam(route.query.sjh)
  if (searchBah.value || searchSjh.value) {
    loadImages()
  }
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="archive-page">
    <ArchiveHeader
      :loading="loading"
      :downloading="downloading"
      :printing="printing"
      :selected-count="selectedCount"
      @back="goBack"
      @refresh="loadImages"
      @download="handleDownload"
      @print="handlePrint"
    />

    <ArchiveSearchBar
      v-model:search-bah="searchBah"
      v-model:search-sjh="searchSjh"
      v-model:view-mode="viewMode"
      :route-meta="routeArchive"
      :has-images="images.length > 0"
      :loading="loading"
      @search="loadImages"
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

      <div class="viewer-layout">
        <ThumbStrip
          ref="thumbStripRef"
          :images="filteredImages"
          :view-mode="viewMode"
          :selected-index="selectedImageIndex"
          :is-selected="isSelected"
          @select="selectImage"
          @toggle="toggleSelect"
        />
        <PreviewPanel
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
        />
      </div>
    </template>

    <div v-else-if="!loading && !errorMsg" class="empty-state">
      <el-empty description="输入病案号或上架号查询影像" />
    </div>
  </div>
</template>

<style scoped>
.archive-page {
  display: grid;
  gap: 16px;
}

.viewer-layout {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0;
  height: calc(100vh - 280px);
  min-height: 450px;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 10px;
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
}
</style>

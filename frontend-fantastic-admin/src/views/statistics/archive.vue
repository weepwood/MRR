<script setup lang="ts">
import type { ArchiveLocalPreferences, ArchiveTypeDisplayMode } from './archive/composables/useArchiveLocalPreferences'
import type { GalleryImage, ViewMode } from './archive/types'
import type { IdCardArchiveCase } from '@/api/modules/search'
import type { ArchivePreviewMode, EffectiveSystemSettings } from '@/utils/system-settings'
import { Document, Download, Printer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArchiveLookupValidationMessage } from '@/utils/medical-record-code'
import {
  createDefaultSystemSettings,
  loadEffectiveSystemSettings,
  SYSTEM_SETTINGS_UPDATED_EVENT,
} from '@/utils/system-settings'
import ArchiveCaseList from './archive/components/ArchiveCaseList.vue'
import ArchiveHeader from './archive/components/ArchiveHeader.vue'
import ArchiveMoreSettings from './archive/components/ArchiveMoreSettings.vue'
import ArchiveSearchBar from './archive/components/ArchiveSearchBar.vue'
import PatientCard from './archive/components/PatientCard.vue'
import PreviewPanel from './archive/components/PreviewPanel.vue'
import ThumbStrip from './archive/components/ThumbStrip.vue'
import TypeFilterBar from './archive/components/TypeFilterBar.vue'
import { useArchiveImages } from './archive/composables/useArchiveImages'
import {
  clearArchiveLocalPreferences,
  readArchiveLocalPreferences,
  resolveArchiveDisplayPreferences,
  writeArchiveLocalPreferences,
} from './archive/composables/useArchiveLocalPreferences'
import { useArchivePrint } from './archive/composables/useArchivePrint'
import { useSelection } from './archive/composables/useSelection'
import { buildTypeStats, padCode } from './archive/constants'

defineOptions({ name: 'StatisticsArchivePage' })

const ID_CARD_PATTERN = /^\d{15}(?:\d{2}[\dX])?$/i
const route = useRoute()
const router = useRouter()

const {
  images,
  patientList,
  archiveCases,
  loading,
  patientLoading,
  idCardLoading,
  downloading,
  savingType,
  errorMsg,
  searchBah,
  searchSjh,
  searchIdCard,
  idCardToken,
  maskedIdCard,
  loadImages,
  loadArchiveCasesByIdCard,
  loadArchiveCasesByToken,
  setPatientFromArchiveCase,
  clearIdCardSearch,
  handleDownload,
  saveImageType,
} = useArchiveImages()

const archiveSettings = reactive<EffectiveSystemSettings>(createDefaultSystemSettings())
const selectedType = ref<number | 'all'>('all')
const selectedImageIndex = ref(0)
const viewMode = ref<ViewMode>('thumb')
const previewMode = ref<ArchivePreviewMode>('single')
const typeDisplayMode = ref<ArchiveTypeDisplayMode>('double-column')
const archiveLocalPreferences = ref<ArchiveLocalPreferences>(readArchiveLocalPreferences())
const thumbStripRef = ref<InstanceType<typeof ThumbStrip> | null>(null)

const filteredImages = computed<GalleryImage[]>(() =>
  selectedType.value === 'all'
    ? images.value
    : images.value.filter(item => Number(item.btype) === selectedType.value),
)

const selection = useSelection<GalleryImage>(computed(() => images.value))
const {
  selectedIds,
  selectedCount,
  selectedItems,
  isSelected,
  toggleSelect,
  toggleItems,
  keyOf,
} = selection

const { printing, exportingPdf, printSelected, exportSelectedPdf } = useArchivePrint()

const currentImage = computed(() => filteredImages.value[selectedImageIndex.value] || null)
const previewList = computed(() => filteredImages.value.map(item => item.imageUrl || ''))
const typeStats = computed(() => buildTypeStats(images.value))
const patient = computed(() => patientList.value[0])
const allImagesSelected = computed(() => images.value.length > 0 && images.value.every(isSelected))
const allImagesIndeterminate = computed(() => images.value.some(isSelected) && !allImagesSelected.value)
const showViewer = computed(() => images.value.length > 0 || Boolean(
  sanitizeParam(route.query.id) || sanitizeParam(route.query.bah) || sanitizeParam(route.query.sjh),
))
const archiveDisplaySettings = computed(() => resolveArchiveDisplayPreferences(
  archiveSettings,
  archiveLocalPreferences.value,
))
const archiveWorkspaceStyle = computed(() => ({
  '--archive-thumb-column-width': `${archiveDisplaySettings.value.archiveThumbnailSize + 18}px`,
}))
const archivePath = computed(() => route.name === 'archiveEmbedded' ? '/archive/embed' : '/archive')
const showBackToStatisticsDetail = computed(() => route.query.from === 'statistics-detail')

const selectionStorageKey = computed(() => {
  const bah = normalizeSearchParam(searchBah.value)
  const sjh = normalizeSearchParam(searchSjh.value)
  if (!bah && !sjh) {
    return ''
  }
  return `MRR-ADMIN:archive-selection:${bah || 'none'}:${sjh || 'none'}`
})

function sanitizeParam(val: unknown): string {
  const value = Array.isArray(val) ? val[0] : val
  const text = String(value ?? '').trim()
  return text.startsWith(':') ? '' : text
}

function normalizeSearchParam(value: unknown): string {
  const text = sanitizeParam(value)
  return text ? padCode(text) : ''
}

function caseMatches(item: IdCardArchiveCase, bah: string, sjh: string) {
  return normalizeSearchParam(item.bah) === bah
    && normalizeSearchParam(item.sjh) === sjh
}

function applyArchiveSettings(settings: EffectiveSystemSettings) {
  Object.assign(archiveSettings, settings)
  viewMode.value = settings.archiveDefaultView
  previewMode.value = archiveDisplaySettings.value.archivePreviewMode
  typeDisplayMode.value = archiveDisplaySettings.value.archiveTypeDisplayMode
}

function updateArchiveLocalPreferences(preferences: ArchiveLocalPreferences) {
  archiveLocalPreferences.value = { ...archiveLocalPreferences.value, ...preferences }
  writeArchiveLocalPreferences(archiveLocalPreferences.value)
  previewMode.value = archiveDisplaySettings.value.archivePreviewMode
  typeDisplayMode.value = archiveDisplaySettings.value.archiveTypeDisplayMode
}

function resetArchiveLocalPreferences() {
  clearArchiveLocalPreferences()
  archiveLocalPreferences.value = {}
  viewMode.value = archiveSettings.archiveDefaultView
  previewMode.value = archiveDisplaySettings.value.archivePreviewMode
  typeDisplayMode.value = archiveDisplaySettings.value.archiveTypeDisplayMode
}

async function loadArchiveSettings() {
  const { settings } = await loadEffectiveSystemSettings()
  applyArchiveSettings(settings)
}

function handleRuntimeSettingsUpdated(event: Event) {
  const settings = (event as CustomEvent<EffectiveSystemSettings>).detail
  if (settings) {
    applyArchiveSettings(settings)
  }
}

function restoreSelection() {
  const key = selectionStorageKey.value
  if (!archiveSettings.archiveRememberSelection || !key || !images.value.length) {
    selectedIds.value = new Set()
    return
  }

  try {
    const raw = localStorage.getItem(key)
    const stored = raw ? JSON.parse(raw) as string[] : []
    const validKeys = new Set(images.value.map(keyOf))
    selectedIds.value = new Set(stored.filter(item => validKeys.has(item)))
  }
  catch {
    selectedIds.value = new Set()
  }
}

function persistSelection() {
  const key = selectionStorageKey.value
  if (!archiveSettings.archiveRememberSelection || !key) {
    return
  }
  try {
    localStorage.setItem(key, JSON.stringify([...selectedIds.value]))
  }
  catch {
    // 存储不可用时不影响当前选择操作。
  }
}

function clearArchiveState(message = '') {
  images.value = []
  patientList.value = []
  errorMsg.value = message
  selectedType.value = 'all'
  selectedImageIndex.value = 0
  selectedIds.value = new Set()
}

async function loadSelectedArchiveCase(archiveCase: IdCardArchiveCase, forceRefresh = false) {
  searchBah.value = normalizeSearchParam(archiveCase.bah)
  searchSjh.value = normalizeSearchParam(archiveCase.sjh)
  selectedType.value = 'all'
  selectedImageIndex.value = 0
  selectedIds.value = new Set()
  await loadImages(forceRefresh)
  setPatientFromArchiveCase(archiveCase)
}

async function syncIdCardSearchFromRoute(idParam: string, bah: string, sjh: string, forceRefresh = false) {
  const isPlainIdCard = ID_CARD_PATTERN.test(idParam)
  if (forceRefresh || idCardToken.value !== idParam || !archiveCases.value.length) {
    const result = isPlainIdCard
      ? await loadArchiveCasesByIdCard(idParam)
      : await loadArchiveCasesByToken(idParam)
    if (!result) {
      clearArchiveState(errorMsg.value || '身份证查询链接无效')
      return
    }
  }

  const resolvedToken = idCardToken.value || (isPlainIdCard ? '' : idParam)
  if (!resolvedToken) {
    clearArchiveState('身份证查询链接生成失败')
    return
  }

  const selectedCase = archiveCases.value.find(item => caseMatches(item, bah, sjh))
    || archiveCases.value[0]
  if (!selectedCase) {
    clearArchiveState(errorMsg.value || '未查询到该患者的影像病案')
    return
  }

  const selectedBah = normalizeSearchParam(selectedCase.bah)
  const selectedSjh = normalizeSearchParam(selectedCase.sjh)
  if (resolvedToken !== idParam || selectedBah !== bah || selectedSjh !== sjh) {
    await router.replace({
      path: archivePath.value,
      query: {
        id: resolvedToken,
        bah: selectedBah,
        ...(selectedSjh ? { sjh: selectedSjh } : {}),
      },
    })
    return
  }

  await loadSelectedArchiveCase(selectedCase, forceRefresh)
}

async function syncSearchFromRoute(forceRefresh = false) {
  const bah = normalizeSearchParam(route.query.bah)
  const sjh = normalizeSearchParam(route.query.sjh)
  const idToken = sanitizeParam(route.query.id)

  searchBah.value = bah
  searchSjh.value = sjh

  if (idToken) {
    await syncIdCardSearchFromRoute(idToken, bah, sjh, forceRefresh)
    return
  }

  clearIdCardSearch()
  if (bah || sjh) {
    await loadImages(forceRefresh)
  }
  else {
    clearArchiveState()
  }
}

async function handleIdCardSearch() {
  const idCard = searchIdCard.value.trim()
  if (!ID_CARD_PATTERN.test(idCard)) {
    const message = '请输入正确的 15 位或 18 位身份证号'
    clearArchiveState(message)
    ElMessage.warning(message)
    return
  }

  const result = await loadArchiveCasesByIdCard(idCard)
  const firstCase = result?.cases?.[0]
  if (!result?.token || !firstCase) {
    clearArchiveState(errorMsg.value || '未查询到该患者的影像病案')
    return
  }

  const bah = normalizeSearchParam(firstCase.bah)
  const sjh = normalizeSearchParam(firstCase.sjh)
  await router.push({
    path: archivePath.value,
    query: {
      id: result.token,
      bah,
      ...(sjh ? { sjh } : {}),
    },
  })
}

async function handleSearch() {
  if (searchIdCard.value.trim()) {
    await handleIdCardSearch()
    return
  }

  clearIdCardSearch()
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

  const location = { path: archivePath.value, query }
  if (router.resolve(location).fullPath === route.fullPath) {
    await loadImages()
    return
  }

  await router.push(location)
}

async function handleRefresh() {
  const selectionKey = selectionStorageKey.value
  if (selectionKey) {
    localStorage.removeItem(selectionKey)
  }
  selectedIds.value = new Set()
  await syncSearchFromRoute(true)
}

async function selectArchiveCase(archiveCase: IdCardArchiveCase) {
  const token = idCardToken.value || sanitizeParam(route.query.id)
  if (!token) {
    return
  }
  const bah = normalizeSearchParam(archiveCase.bah)
  const sjh = normalizeSearchParam(archiveCase.sjh)
  const location = {
    path: archivePath.value,
    query: {
      id: token,
      bah,
      ...(sjh ? { sjh } : {}),
    },
  }
  if (router.resolve(location).fullPath === route.fullPath) {
    await loadSelectedArchiveCase(archiveCase)
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

function imagesOfType(type: number) {
  return images.value.filter(image => Number(image.btype) === type)
}

function isTypeSelected(type: number) {
  const typeImages = imagesOfType(type)
  return typeImages.length > 0 && typeImages.every(isSelected)
}

function isTypeIndeterminate(type: number) {
  const typeImages = imagesOfType(type)
  return typeImages.some(isSelected) && !isTypeSelected(type)
}

function toggleTypeSelection(type: number) {
  toggleItems(imagesOfType(type))
}

function toggleAllSelection() {
  toggleItems(images.value)
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

function handleExportPdf() {
  exportSelectedPdf(selectedItems.value)
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

function isImagePreviewOpen() {
  return [...document.querySelectorAll<HTMLElement>('.el-image-viewer__wrapper')].some((viewer) => {
    const style = window.getComputedStyle(viewer)
    return style.display !== 'none' && style.visibility !== 'hidden'
  })
}

function onKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement | null
  if (target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable) {
    return
  }
  if (!filteredImages.value.length) {
    return
  }
  if (isImagePreviewOpen() && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
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
  else if (previewMode.value === 'single' && e.key === 'ArrowUp') {
    e.preventDefault()
    navigate(-1)
  }
  else if (previewMode.value === 'single' && e.key === 'ArrowDown') {
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

watch(
  () => [route.query.bah, route.query.sjh, route.query.id],
  () => void syncSearchFromRoute(),
  { immediate: true },
)

watch(
  [images, selectionStorageKey, () => archiveSettings.archiveRememberSelection],
  () => nextTick(restoreSelection),
)

watch(selectedIds, persistSelection)

watch(() => archiveSettings.archiveRememberSelection, (enabled) => {
  if (!enabled && selectionStorageKey.value) {
    localStorage.removeItem(selectionStorageKey.value)
  }
})

onMounted(() => {
  document.body.classList.add('archive-immersive')
  window.addEventListener('keydown', onKeydown)
  window.addEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, handleRuntimeSettingsUpdated)
  void loadArchiveSettings()
})

onUnmounted(() => {
  document.body.classList.remove('archive-immersive')
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, handleRuntimeSettingsUpdated)
})
</script>

<template>
  <div class="archive-page" :class="`scrollbars-${archiveDisplaySettings.archiveScrollbarMode}`">
    <ArchiveMoreSettings
      class="archive-more-settings-float"
      :view-mode="viewMode"
      :preview-mode="previewMode"
      :type-display-mode="typeDisplayMode"
      :thumbnail-size="archiveDisplaySettings.archiveThumbnailSize"
      :fit-mode="archiveDisplaySettings.archiveFitMode"
      :scrollbar-mode="archiveDisplaySettings.archiveScrollbarMode"
      :department-colors-enabled="archiveDisplaySettings.archiveDepartmentColorsEnabled"
      :has-local-preferences="Object.keys(archiveLocalPreferences).length > 0"
      @update:view-mode="viewMode = $event"
      @update:preview-mode="updateArchiveLocalPreferences({ archivePreviewMode: $event })"
      @update:type-display-mode="updateArchiveLocalPreferences({ archiveTypeDisplayMode: $event })"
      @update:fit-mode="updateArchiveLocalPreferences({ archiveFitMode: $event })"
      @update:scrollbar-mode="updateArchiveLocalPreferences({ archiveScrollbarMode: $event })"
      @update:department-colors-enabled="updateArchiveLocalPreferences({ archiveDepartmentColorsEnabled: $event })"
      @update:thumbnail-size="updateArchiveLocalPreferences({ archiveThumbnailSize: $event })"
      @reset="resetArchiveLocalPreferences"
    />

    <div
      class="archive-workspace"
      :class="{
        'has-viewer': showViewer,
        'is-empty': !showViewer,
        'is-list-mode': viewMode === 'list',
      }"
      :style="archiveWorkspaceStyle"
    >
      <section class="archive-sidebar">
        <ArchiveHeader
          v-model:search-id-card="searchIdCard"
          v-model:search-bah="searchBah"
          v-model:search-sjh="searchSjh"
          :loading="loading || idCardLoading"
          :show-back="showBackToStatisticsDetail"
          @back="goBack"
          @refresh="handleRefresh"
          @search="handleSearch"
        />

        <ArchiveSearchBar
          v-model:search-id-card="searchIdCard"
          v-model:search-bah="searchBah"
          v-model:search-sjh="searchSjh"
          :loading="loading || idCardLoading"
          @search="handleSearch"
        />

        <ArchiveCaseList
          :cases="archiveCases"
          :active-bah="searchBah"
          :active-sjh="searchSjh"
          :masked-id-card="maskedIdCard"
          :department-colors-enabled="archiveDisplaySettings.archiveDepartmentColorsEnabled"
          :loading="idCardLoading"
          @select="selectArchiveCase"
        />

        <PatientCard :patient="patient" :sjh="currentImage?.sjh || searchSjh" :loading="patientLoading" />

        <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon />

        <template v-if="images.length > 0">
          <TypeFilterBar
            v-model:selected-type="selectedType"
            v-model:display-mode="typeDisplayMode"
            :type-stats="typeStats"
            :total-count="images.length"
            :all-selected="allImagesSelected"
            :all-indeterminate="allImagesIndeterminate"
            :is-type-selected="isTypeSelected"
            :is-type-indeterminate="isTypeIndeterminate"
            @select-type="selectType"
            @toggle-all-selection="toggleAllSelection"
            @toggle-type-selection="toggleTypeSelection"
          />
        </template>

        <div v-else-if="!showViewer && !loading && !idCardLoading && !errorMsg" class="empty-state">
          <el-empty description="输入身份证号、病案号或上架号查询影像" />
        </div>

        <div v-if="images.length > 0" class="archive-bottom-actions">
          <el-button class="download-action" :icon="Download" :loading="downloading" @click="handleDownload">
            下载档案袋
          </el-button>
          <el-button :icon="Printer" :loading="printing" :disabled="!selectedCount" @click="handlePrint">
            打印选中<template v-if="selectedCount">
              ({{ selectedCount }})
            </template>
          </el-button>
          <el-button type="primary" :icon="Document" :loading="exportingPdf" :disabled="!selectedCount" @click="handleExportPdf">
            导出 PDF<template v-if="selectedCount">
              ({{ selectedCount }})
            </template>
          </el-button>
        </div>
      </section>

      <div v-if="showViewer" class="viewer-layout">
        <ThumbStrip
          ref="thumbStripRef"
          v-model:view-mode="viewMode"
          :images="filteredImages"
          :selected-index="selectedImageIndex"
          :is-selected="isSelected"
          :thumbnail-size="archiveDisplaySettings.archiveThumbnailSize"
          :preload-count="archiveSettings.archivePreloadCount"
          @select="selectImage"
          @toggle="toggleSelect"
        />
      </div>

      <PreviewPanel
        v-if="showViewer"
        v-model:display-mode="previewMode"
        :image="currentImage"
        :preview-list="previewList"
        :index="selectedImageIndex"
        :total="filteredImages.length"
        :is-selected="currentImage ? isSelected(currentImage) : false"
        :saving-type="savingType"
        :loading="loading"
        :fit-mode="archiveDisplaySettings.archiveFitMode"
        :empty-description="images.length ? '当前类型暂无影像' : '未查询到影像'"
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
  position: relative;
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  padding: 8px;
  background: var(--surface-muted);
}

.archive-page.scrollbars-hidden :deep(.case-list),
.archive-page.scrollbars-hidden :deep(.archive-sidebar),
.archive-page.scrollbars-hidden :deep(.thumb-strip),
.archive-page.scrollbars-hidden :deep(.preview-stage) {
  scrollbar-width: none;
}

.archive-page.scrollbars-hidden :deep(.case-list::-webkit-scrollbar),
.archive-page.scrollbars-hidden :deep(.archive-sidebar::-webkit-scrollbar),
.archive-page.scrollbars-hidden :deep(.thumb-strip::-webkit-scrollbar),
.archive-page.scrollbars-hidden :deep(.preview-stage::-webkit-scrollbar) {
  display: none;
}

.archive-page.scrollbars-semi-hidden :deep(.case-list),
.archive-page.scrollbars-semi-hidden :deep(.archive-sidebar),
.archive-page.scrollbars-semi-hidden :deep(.thumb-strip),
.archive-page.scrollbars-semi-hidden :deep(.preview-stage) {
  scrollbar-gutter: stable both-edges;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.archive-page.scrollbars-semi-hidden :deep(.case-list::-webkit-scrollbar),
.archive-page.scrollbars-semi-hidden :deep(.archive-sidebar::-webkit-scrollbar),
.archive-page.scrollbars-semi-hidden :deep(.thumb-strip::-webkit-scrollbar),
.archive-page.scrollbars-semi-hidden :deep(.preview-stage::-webkit-scrollbar) {
  display: block;
  width: 6px;
  height: 6px;
}

.archive-page.scrollbars-semi-hidden :deep(.case-list::-webkit-scrollbar-thumb),
.archive-page.scrollbars-semi-hidden :deep(.archive-sidebar::-webkit-scrollbar-thumb),
.archive-page.scrollbars-semi-hidden :deep(.thumb-strip::-webkit-scrollbar-thumb),
.archive-page.scrollbars-semi-hidden :deep(.preview-stage::-webkit-scrollbar-thumb) {
  background: transparent;
}

.archive-page.scrollbars-semi-hidden :deep(.case-list:hover),
.archive-page.scrollbars-semi-hidden :deep(.archive-sidebar:hover),
.archive-page.scrollbars-semi-hidden :deep(.thumb-strip:hover),
.archive-page.scrollbars-semi-hidden :deep(.preview-stage:hover) {
  scrollbar-color: hsl(var(--scrollbar-color)) transparent;
}

.archive-page.scrollbars-semi-hidden :deep(.case-list:hover::-webkit-scrollbar-thumb),
.archive-page.scrollbars-semi-hidden :deep(.archive-sidebar:hover::-webkit-scrollbar-thumb),
.archive-page.scrollbars-semi-hidden :deep(.thumb-strip:hover::-webkit-scrollbar-thumb),
.archive-page.scrollbars-semi-hidden :deep(.preview-stage:hover::-webkit-scrollbar-thumb) {
  background: hsl(var(--scrollbar-color));
}

.archive-more-settings-float {
  position: fixed;
  top: calc(var(--g-header-actual-height) + var(--g-tabbar-actual-height) + 16px);
  right: 16px;
  z-index: 2010;
}

.archive-workspace {
  min-width: 0;
  height: 100%;
}

.archive-workspace.has-viewer {
  display: grid;
  grid-template-columns: minmax(280px, 320px) var(--archive-thumb-column-width, 218px) minmax(0, 1fr);
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.archive-workspace.has-viewer.is-list-mode {
  grid-template-columns: minmax(280px, 320px) var(--archive-thumb-column-width, 218px) minmax(0, 1fr);
}

.archive-sidebar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.has-viewer .archive-sidebar {
  overflow: hidden auto;
}

.archive-bottom-actions {
  display: grid;
  flex: none;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 10px;
  margin-top: auto;
  border-top: 1px solid var(--divider);
}

.archive-bottom-actions .download-action {
  grid-column: 1 / -1;
}

.archive-bottom-actions :deep(.el-button) {
  min-width: 0;
  margin: 0;
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
  .archive-more-settings-float {
    top: calc(var(--g-header-actual-height) + var(--g-tabbar-actual-height) + 12px);
    right: 12px;
  }

  .archive-workspace.has-viewer,
  .archive-workspace.has-viewer.is-list-mode {
    grid-template-columns: 1fr;
    height: auto;
    min-height: initial;
  }

  .archive-workspace.is-empty {
    padding: 16px;
  }

  .has-viewer .archive-sidebar {
    overflow: visible;
  }

  .archive-workspace.has-viewer .preview-panel {
    height: min(68vh, 640px);
    min-height: 420px;
  }
}
</style>

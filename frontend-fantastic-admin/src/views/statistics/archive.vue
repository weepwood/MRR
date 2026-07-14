<script setup lang="ts">
import type { IdCardArchiveCase } from '@/api/modules/search'
import type { GalleryImage, RouteArchiveMeta, ViewMode } from './archive/types'
import type { ArchivePreviewMode, EffectiveSystemSettings } from '@/utils/system-settings'
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

const ID_CARD_PATTERN = /^\d{15}(\d{2}[0-9Xx])?$/
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
const thumbStripRef = ref<InstanceType<typeof ThumbStrip> | null>(null)

const filteredImages = computed<GalleryImage[]>(() =>
  selectedType.value === 'all'
    ? images.value
    : images.value.filter(item => Number(item.btype) === selectedType.value),
)

const selection = useSelection<GalleryImage>(filteredImages)
const {
  selectedIds,
  selectedCount,
  allVisibleSelected,
  selectedItems,
  isSelected,
  toggleSelect,
  selectAllVisible,
  keyOf,
} = selection

const { printing, exportingPdf, printSelected, exportSelectedPdf } = useArchivePrint()

const currentImage = computed(() => filteredImages.value[selectedImageIndex.value] || null)
const previewList = computed(() => filteredImages.value.map(item => item.imageUrl || ''))
const typeStats = computed(() => buildTypeStats(images.value))
const patient = computed(() => patientList.value[0])
const archiveWorkspaceStyle = computed(() => ({
  '--archive-thumb-column-width': `${archiveSettings.archiveThumbnailSize + 18}px`,
}))

const routeArchive = computed<RouteArchiveMeta>(() => ({
  bah: searchBah.value || sanitizeParam(route.query.bah),
  cid: sanitizeParam(route.query.cid),
  type: sanitizeParam(route.query.type),
  date: sanitizeParam(route.query.date),
  pages: sanitizeParam(route.query.pages),
  openerNo: sanitizeParam(route.query.openerNo),
  sjh: searchSjh.value || sanitizeParam(route.query.sjh),
}))

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
  previewMode.value = settings.archivePreviewMode
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

async function loadSelectedArchiveCase(archiveCase: IdCardArchiveCase) {
  searchBah.value = normalizeSearchParam(archiveCase.bah)
  searchSjh.value = normalizeSearchParam(archiveCase.sjh)
  selectedType.value = 'all'
  selectedImageIndex.value = 0
  selectedIds.value = new Set()
  await loadImages()
  setPatientFromArchiveCase(archiveCase)
}

async function syncIdCardSearchFromRoute(idParam: string, bah: string, sjh: string) {
  searchIdCard.value = ''
  const isPlainIdCard = ID_CARD_PATTERN.test(idParam)
  if (idCardToken.value !== idParam || !archiveCases.value.length) {
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
      path: '/archive',
      query: {
        id: resolvedToken,
        bah: selectedBah,
        ...(selectedSjh ? { sjh: selectedSjh } : {}),
      },
    })
    return
  }

  await loadSelectedArchiveCase(selectedCase)
}

async function syncSearchFromRoute() {
  const legacyBah = normalizeSearchParam(route.params.bah)
  const bah = normalizeSearchParam(route.query.bah || legacyBah)
  const sjh = normalizeSearchParam(route.query.sjh)
  const idToken = sanitizeParam(route.query.id)

  searchBah.value = bah
  searchSjh.value = sjh

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

  if (idToken) {
    await syncIdCardSearchFromRoute(idToken, bah, sjh)
    return
  }

  clearIdCardSearch()
  if (bah || sjh) {
    await loadImages()
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
    path: '/archive',
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

  const location = { path: '/archive', query }
  if (router.resolve(location).fullPath === route.fullPath) {
    await loadImages()
    return
  }

  await router.push(location)
}

async function handleRefresh() {
  if (sanitizeParam(route.query.id)) {
    await syncSearchFromRoute()
  }
  else {
    await handleSearch()
  }
}

async function selectArchiveCase(archiveCase: IdCardArchiveCase) {
  const token = idCardToken.value || sanitizeParam(route.query.id)
  if (!token) {
    return
  }
  const bah = normalizeSearchParam(archiveCase.bah)
  const sjh = normalizeSearchParam(archiveCase.sjh)
  const location = {
    path: '/archive',
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

watch(
  () => [route.params.bah, route.query.bah, route.query.sjh, route.query.id],
  syncSearchFromRoute,
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
  <div class="archive-page">
    <div
      class="archive-workspace"
      :class="{
        'has-images': images.length > 0,
        'is-empty': images.length === 0,
        'is-list-mode': viewMode === 'list',
      }"
      :style="archiveWorkspaceStyle"
    >
      <section class="archive-sidebar">
        <ArchiveHeader
          :loading="loading || idCardLoading"
          :downloading="downloading"
          :printing="printing"
          :exporting-pdf="exportingPdf"
          :show-actions="images.length > 0"
          :selected-count="selectedCount"
          @back="goBack"
          @refresh="handleRefresh"
          @download="handleDownload"
          @print="handlePrint"
          @export-pdf="handleExportPdf"
        />

        <ArchiveSearchBar
          v-model:search-id-card="searchIdCard"
          v-model:search-bah="searchBah"
          v-model:search-sjh="searchSjh"
          :route-meta="routeArchive"
          :has-images="images.length > 0"
          :loading="loading || idCardLoading"
          @search="handleSearch"
        />

        <ArchiveCaseList
          :cases="archiveCases"
          :active-bah="searchBah"
          :active-sjh="searchSjh"
          :masked-id-card="maskedIdCard"
          :loading="idCardLoading"
          @select="selectArchiveCase"
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

        <div v-else-if="!loading && !idCardLoading && !errorMsg" class="empty-state">
          <el-empty description="输入身份证号、病案号或上架号查询影像" />
        </div>
      </section>

      <div v-if="images.length > 0" class="viewer-layout">
        <ThumbStrip
          ref="thumbStripRef"
          v-model:view-mode="viewMode"
          :images="filteredImages"
          :selected-index="selectedImageIndex"
          :is-selected="isSelected"
          :thumbnail-size="archiveSettings.archiveThumbnailSize"
          :preload-count="archiveSettings.archivePreloadCount"
          @select="selectImage"
          @toggle="toggleSelect"
        />
      </div>

      <PreviewPanel
        v-if="images.length > 0"
        v-model:display-mode="previewMode"
        :image="currentImage"
        :preview-list="previewList"
        :index="selectedImageIndex"
        :total="filteredImages.length"
        :is-selected="currentImage ? isSelected(currentImage) : false"
        :saving-type="savingType"
        :loading="loading"
        :auto-fit="archiveSettings.archiveAutoFit"
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
  grid-template-columns: minmax(280px, 320px) var(--archive-thumb-column-width, 218px) minmax(0, 1fr);
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.archive-workspace.has-images.is-list-mode {
  grid-template-columns: minmax(280px, 320px) var(--archive-thumb-column-width, 218px) minmax(0, 1fr);
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

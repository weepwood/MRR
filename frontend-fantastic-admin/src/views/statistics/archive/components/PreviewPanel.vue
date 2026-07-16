<script setup lang="ts">
import type { ArchiveFitMode } from '../composables/useArchiveLocalPreferences'
import type { GalleryImage } from '../types'
import type { ClassificationJob, ClassificationScope } from '@/api/modules/image'
import type { ArchivePreviewMode } from '@/utils/system-settings'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  confirmHighConfidenceClassifications,
  confirmImageClassification,
  getImageClassificationJob,
  startImageClassification,
} from '@/api/modules/image'
import { getTypeLabel, TYPE_OPTIONS } from '../constants'
import 'element-plus/es/components/message-box/style/css'

const CLASSIFICATION_UPDATED_EVENT = 'mrr:classification-updated'

function asErrorMessage(error: unknown, fallback: string) {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = String((error as { message?: unknown }).message || '').trim()
    if (message) {
      return message
    }
  }
  return fallback
}

defineOptions({ name: 'PreviewPanel' })

const props = withDefaults(defineProps<{
  image: GalleryImage | null
  previewList: string[]
  index: number
  total: number
  isSelected: boolean
  savingType?: boolean
  loading?: boolean
  fitMode?: ArchiveFitMode
  emptyDescription?: string
}>(), {
  savingType: false,
  loading: false,
  fitMode: 'height',
  emptyDescription: '请选择影像',
})

const emit = defineEmits<{
  toggle: []
  saveType: [type: number]
  navigate: [delta: number]
  select: [index: number]
}>()

const displayMode = defineModel<ArchivePreviewMode>('displayMode', { default: 'single' })
const previewScroller = ref<HTMLElement | null>(null)
const pageRefs = ref<(HTMLElement | null)[]>([])
const pendingType = ref(0)
const failedImageUrls = ref<Set<string>>(new Set())
const classificationJob = ref<ClassificationJob | null>(null)
const classificationBusy = ref(false)
let classificationPollTimer: ReturnType<typeof setTimeout> | null = null
let touchStartX = 0
let pageObserver: IntersectionObserver | null = null

const currentType = computed({
  get: () => Number(props.image?.btype || 0),
  set: (value: number) => emit('saveType', value),
})

const hasSuggestion = computed(() =>
  props.image?.classificationState === 'SUGGESTED'
  && Number.isInteger(Number(props.image.predictedBtype)),
)

const suggestionLabel = computed(() => getTypeLabel(props.image?.predictedBtype))
const confidenceText = computed(() => {
  const value = Number(props.image?.classificationConfidence)
  return Number.isFinite(value) ? `${(value * 100).toFixed(1)}%` : '-'
})
const jobRunning = computed(() => ['PENDING', 'RUNNING'].includes(classificationJob.value?.status || ''))
const jobProgress = computed(() => {
  const total = Number(classificationJob.value?.totalCount || 0)
  const processed = Number(classificationJob.value?.processedCount || 0)
  return total > 0 ? Math.min(100, Math.round(processed * 100 / total)) : 0
})

function handleTouchStart(event: TouchEvent) {
  touchStartX = event.touches[0]?.clientX ?? 0
}

function handleTouchEnd(event: TouchEvent) {
  const endX = event.changedTouches[0]?.clientX ?? touchStartX
  if (Math.abs(endX - touchStartX) < 48) {
    return
  }
  emit('navigate', endX < touchStartX ? 1 : -1)
}

function handlePreviewSwitch(index: number) {
  if (index !== props.index) {
    emit('select', index)
  }
}

function syncCurrentPage(index: number) {
  const target = pageRefs.value[index]
  if (!target) {
    return
  }
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function imageUnavailable(imageUrl: string | undefined) {
  return !imageUrl || failedImageUrls.value.has(imageUrl)
}

function markImageFailed(imageUrl: string | undefined) {
  if (!imageUrl) {
    return
  }
  const next = new Set(failedImageUrls.value)
  next.add(imageUrl)
  failedImageUrls.value = next
}

function observePages() {
  pageObserver?.disconnect()
  pageObserver = null
  nextTick(() => {
    if (displayMode.value !== 'scroll' || !previewScroller.value || typeof IntersectionObserver === 'undefined') {
      return
    }
    pageObserver = new IntersectionObserver((entries) => {
      const visiblePage = entries
        .filter(entry => entry.isIntersecting)
        .sort((first, second) => second.intersectionRatio - first.intersectionRatio)[0]
      const index = Number((visiblePage?.target as HTMLElement | undefined)?.dataset.index)
      if (Number.isInteger(index) && index !== props.index) {
        emit('select', index)
      }
    }, { root: previewScroller.value, threshold: 0.6 })
    pageRefs.value.forEach((page) => {
      if (page) {
        pageObserver?.observe(page)
      }
    })
    syncCurrentPage(props.index)
  })
}

async function confirmType() {
  if (pendingType.value === currentType.value) {
    return
  }
  const label = TYPE_OPTIONS.find(item => item.value === pendingType.value)?.label || '所选分类'
  try {
    await ElMessageBox.confirm(
      `确认将当前影像分类切换为“${label}”吗？`,
      '确认分类变更',
      { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' },
    )
    currentType.value = pendingType.value
  }
  catch {
    pendingType.value = currentType.value
  }
}

function dispatchClassificationUpdated() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(CLASSIFICATION_UPDATED_EVENT))
  }
}

function clearClassificationTimer() {
  if (classificationPollTimer) {
    clearTimeout(classificationPollTimer)
    classificationPollTimer = null
  }
}

function scheduleClassificationPoll(jobId: number) {
  clearClassificationTimer()
  classificationPollTimer = setTimeout(() => void pollClassificationJob(jobId), 1200)
}

async function pollClassificationJob(jobId: number) {
  try {
    const response = await getImageClassificationJob(jobId)
    const job = response.data
    if (!job) {
      throw new Error('识别任务状态为空')
    }
    classificationJob.value = job
    if (['PENDING', 'RUNNING'].includes(job.status)) {
      scheduleClassificationPoll(job.id)
      return
    }

    classificationBusy.value = false
    if (job.status === 'COMPLETED') {
      ElMessage.success(`智能分类完成：建议 ${job.suggestedCount} 张，未匹配 ${job.noMatchCount} 张`)
      dispatchClassificationUpdated()
    }
    else if (job.status === 'FAILED') {
      ElMessage.error(job.errorMessage || '智能分类任务执行失败')
    }
  }
  catch (error: unknown) {
    classificationBusy.value = false
    clearClassificationTimer()
    ElMessage.error(asErrorMessage(error, '查询智能分类进度失败'))
  }
}

async function startClassification(scope: ClassificationScope) {
  const archiveId = Number(props.image?.archiveId)
  if (!Number.isInteger(archiveId) || archiveId <= 0) {
    ElMessage.warning('当前影像缺少病案主键，无法创建识别任务')
    return
  }
  if (classificationBusy.value || jobRunning.value) {
    return
  }

  classificationBusy.value = true
  try {
    const response = await startImageClassification(archiveId, scope)
    const job = response.data
    if (!job) {
      throw new Error('后端未返回识别任务')
    }
    classificationJob.value = job
    if (job.status === 'COMPLETED') {
      classificationBusy.value = false
      ElMessage.info('当前范围没有需要识别的图片')
      dispatchClassificationUpdated()
      return
    }
    ElMessage.success('智能分类任务已创建')
    scheduleClassificationPoll(job.id)
  }
  catch (error: unknown) {
    classificationBusy.value = false
    ElMessage.error(asErrorMessage(error, '创建智能分类任务失败'))
  }
}

function handleScopeCommand(command: string | number | object) {
  const scope = String(command) as ClassificationScope
  if (['UNCLASSIFIED', 'LOW_CONFIDENCE', 'ALL'].includes(scope)) {
    void startClassification(scope)
  }
}

async function acceptSuggestion() {
  const scanId = Number(props.image?.id)
  const predictedBtype = Number(props.image?.predictedBtype)
  if (!Number.isInteger(scanId) || !Number.isInteger(predictedBtype)) {
    return
  }

  classificationBusy.value = true
  try {
    await confirmImageClassification(scanId, predictedBtype)
    ElMessage.success(`已采用“${suggestionLabel.value}”分类建议`)
    dispatchClassificationUpdated()
  }
  catch (error: unknown) {
    ElMessage.error(asErrorMessage(error, '确认分类建议失败'))
  }
  finally {
    classificationBusy.value = false
  }
}

async function acceptHighConfidenceSuggestions() {
  const archiveId = Number(props.image?.archiveId)
  if (!Number.isInteger(archiveId) || archiveId <= 0) {
    return
  }

  try {
    await ElMessageBox.confirm(
      '确认批量采用当前病案中置信度不低于 92% 的分类建议吗？',
      '批量确认智能分类',
      { confirmButtonText: '确认采用', cancelButtonText: '取消', type: 'warning' },
    )
  }
  catch {
    return
  }

  classificationBusy.value = true
  try {
    const response = await confirmHighConfidenceClassifications(archiveId, 0.92)
    const count = Number(response.data?.confirmedCount || 0)
    ElMessage.success(count > 0 ? `已确认 ${count} 张高置信度图片` : '没有可确认的高置信度建议')
    dispatchClassificationUpdated()
  }
  catch (error: unknown) {
    ElMessage.error(asErrorMessage(error, '批量确认分类建议失败'))
  }
  finally {
    classificationBusy.value = false
  }
}

watch(() => props.index, (index) => {
  if (displayMode.value === 'scroll') {
    nextTick(() => syncCurrentPage(index))
  }
})

watch(() => props.image?.btype, () => {
  pendingType.value = currentType.value
}, { immediate: true })

watch(() => props.image?.archiveId, () => {
  clearClassificationTimer()
  classificationJob.value = null
  classificationBusy.value = false
})

watch(displayMode, observePages)

onMounted(observePages)

onUnmounted(() => {
  pageObserver?.disconnect()
  pageObserver = null
  clearClassificationTimer()
})
</script>

<template>
  <div v-loading="props.loading" class="preview-panel" @touchstart.passive="handleTouchStart" @touchend="handleTouchEnd">
    <template v-if="props.image">
      <div class="preview-toolbar">
        <el-select
          v-model="pendingType"
          class="preview-type-select"
          aria-label="影像分类"
          :loading="props.savingType"
          size="small"
          @change="confirmType"
        >
          <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>

        <section class="classification-card" aria-label="智能分类">
          <div class="classification-heading">
            <strong>智能分类</strong>
            <el-tag v-if="props.image.classificationState" size="small" effect="plain">
              {{ props.image.classificationState }}
            </el-tag>
          </div>

          <template v-if="hasSuggestion">
            <div class="classification-suggestion">
              <span>{{ suggestionLabel }}</span>
              <strong>{{ confidenceText }}</strong>
            </div>
            <p v-if="props.image.classificationOcrTitle" class="classification-title" :title="props.image.classificationOcrTitle">
              {{ props.image.classificationOcrTitle }}
            </p>
            <el-button size="small" type="success" plain :loading="classificationBusy" @click="acceptSuggestion">
              采用当前建议
            </el-button>
          </template>
          <p v-else-if="props.image.classificationState === 'NO_MATCH'" class="classification-hint">
            OCR 已完成，但没有匹配到明确类型。
          </p>
          <p v-else-if="props.image.classificationState === 'FAILED'" class="classification-hint is-error">
            当前图片识别失败，可重新执行低置信度识别。
          </p>
          <p v-else class="classification-hint">
            当前图片还没有智能分类建议。
          </p>

          <div v-if="classificationJob" class="classification-progress">
            <div>
              <span>{{ classificationJob.status }}</span>
              <span>{{ classificationJob.processedCount }}/{{ classificationJob.totalCount }}</span>
            </div>
            <el-progress :percentage="jobProgress" :show-text="false" :stroke-width="5" />
          </div>

          <div class="classification-actions">
            <el-button size="small" :loading="classificationBusy || jobRunning" @click="startClassification('UNCLASSIFIED')">
              识别未分类
            </el-button>
            <el-dropdown :disabled="classificationBusy || jobRunning" @command="handleScopeCommand">
              <el-button size="small">
                更多范围
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="LOW_CONFIDENCE">
                    重试低置信度
                  </el-dropdown-item>
                  <el-dropdown-item command="ALL" divided>
                    重新识别全部
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <el-button size="small" text :disabled="classificationBusy || jobRunning" @click="acceptHighConfidenceSuggestions">
            批量采用 ≥92% 建议
          </el-button>
        </section>

        <div class="preview-controls">
          <div class="page-navigation" aria-label="影像翻页">
            <el-button circle size="small" :icon="ArrowLeft" :disabled="props.index === 0" aria-label="上一张影像" @click="emit('navigate', -1)" />
            <el-button circle size="small" :icon="ArrowRight" :disabled="props.index >= props.total - 1" aria-label="下一张影像" @click="emit('navigate', 1)" />
          </div>
          <el-button size="small" type="primary" :plain="!props.isSelected" @click="emit('toggle')">
            P{{ props.image.pages ?? '-' }} {{ props.isSelected ? '已选' : '选中' }}
          </el-button>
        </div>
      </div>

      <div v-if="displayMode === 'single'" class="preview-stage single-stage" :class="`is-fit-${props.fitMode}`">
        <div v-if="imageUnavailable(props.image.imageUrl)" class="preview-image-placeholder" role="img" :aria-label="`第 ${props.index + 1} 张影像加载失败`">
          <svg viewBox="0 0 48 48" aria-hidden="true">
            <path d="M8 11a3 3 0 0 1 3-3h26a3 3 0 0 1 3 3v19.76l-6.06-6.06a2 2 0 0 0-2.82 0l-2.4 2.4-6.68-6.68a2 2 0 0 0-2.82 0L8 31.64V11Zm0 26.28 12.64-12.64 6.68 6.68a2 2 0 0 0 2.82 0l2.4-2.4L40 36.4v.6a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3v.28ZM31 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" fill="currentColor" />
          </svg>
          <strong>影像加载失败</strong>
          <span>当前图片暂不可用，请检查影像服务或文件地址</span>
        </div>
        <el-image
          v-else
          class="preview-image fit-image"
          :class="`fit-${props.fitMode}`"
          :src="props.image.imageUrl"
          :alt="`第 ${props.index + 1} 张影像`"
          fit="contain"
          :preview-src-list="props.previewList"
          :initial-index="props.index"
          :preview-teleported="true"
          :hide-on-click-modal="false"
          :infinite="false"
          show-progress
          @switch="handlePreviewSwitch"
          @error="markImageFailed(props.image.imageUrl)"
        />
      </div>

      <div v-else ref="previewScroller" class="preview-stage">
        <article
          v-for="(imageUrl, pageIndex) in props.previewList"
          :key="imageUrl || pageIndex"
          :ref="(element: any) => { pageRefs[pageIndex] = element }"
          class="continuous-page"
          :class="`is-fit-${props.fitMode}`"
          :data-index="pageIndex"
        >
          <div v-if="imageUnavailable(imageUrl)" class="preview-image-placeholder continuous-placeholder" role="img" :aria-label="`第 ${pageIndex + 1} 张影像加载失败`">
            <svg viewBox="0 0 48 48" aria-hidden="true">
              <path d="M8 11a3 3 0 0 1 3-3h26a3 3 0 0 1 3 3v19.76l-6.06-6.06a2 2 0 0 0-2.82 0l-2.4 2.4-6.68-6.68a2 2 0 0 0-2.82 0L8 31.64V11Zm0 26.28 12.64-12.64 6.68 6.68a2 2 0 0 0 2.82 0l2.4-2.4L40 36.4v.6a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3v.28ZM31 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" fill="currentColor" />
            </svg>
            <strong>P{{ pageIndex + 1 }} 加载失败</strong>
            <span>当前图片暂不可用</span>
          </div>
          <img
            v-else
            class="preview-image fit-scroll-image"
            :class="`fit-${props.fitMode}`"
            :src="imageUrl"
            :alt="`第 ${pageIndex + 1} 张影像`"
            loading="lazy"
            @error="markImageFailed(imageUrl)"
          >
        </article>
      </div>
    </template>
    <el-empty v-else :description="props.emptyDescription" />
  </div>
</template>

<style scoped>
.preview-panel {
  position: relative;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.preview-stage {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 24px;
  overflow: auto;
  background: var(--surface-alt);
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.single-stage {
  display: grid;
  place-items: center;
  overflow: hidden;
}

.preview-image {
  display: block;
  height: auto;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
}

.single-stage .fit-image {
  cursor: zoom-in;
  background: transparent;
  box-shadow: none;
}

.single-stage .fit-image.fit-width {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  height: auto;
  max-height: 100%;
}

.single-stage .fit-image.fit-height {
  width: auto;
  max-width: 100%;
  height: 100%;
  min-height: 0;
  max-height: 100%;
}

.fit-scroll-image.fit-width {
  width: min(100%, 980px);
}

.fit-scroll-image.fit-height {
  width: auto;
  max-width: none;
  height: min(64vh, 720px);
}

.preview-image-placeholder {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  width: min(100%, 720px);
  min-height: min(56vh, 560px);
  padding: 32px;
  margin: 0 auto;
  color: var(--text-secondary);
  text-align: center;
  background: radial-gradient(circle at top, hsl(var(--primary) / 8%), transparent 48%), var(--surface);
  border: 1px dashed var(--divider);
  border-radius: 12px;
}

.preview-image-placeholder svg {
  width: 52px;
  height: 52px;
  color: var(--text-tertiary);
  opacity: 0.72;
}

.preview-image-placeholder strong {
  font-size: 16px;
  color: var(--text-primary);
}

.preview-image-placeholder span {
  max-width: 420px;
  font-size: 13px;
  line-height: 1.6;
}

.continuous-placeholder {
  width: min(100%, 980px);
  min-height: 420px;
}

.continuous-page {
  position: relative;
  width: max-content;
  min-width: 100%;
  padding: 8px;
  margin-bottom: 16px;
}

.continuous-page.is-fit-width {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}

.preview-toolbar {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 3001;
  display: grid;
  gap: 8px;
  width: min(280px, calc(100vw - 32px));
  min-width: 0;
  padding: 10px;
  background: hsl(var(--card) / 94%);
  border: 1px solid var(--divider);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 10%);
  backdrop-filter: blur(12px);
}

.preview-controls,
.page-navigation,
.classification-heading,
.classification-suggestion,
.classification-actions,
.classification-progress > div {
  display: flex;
  align-items: center;
}

.preview-controls {
  gap: 6px;
  justify-content: space-between;
  width: 100%;
}

.page-navigation {
  flex: none;
  gap: 2px;
}

.preview-type-select {
  width: 100%;
}

.preview-type-select :deep(.el-select__wrapper) {
  min-height: 24px;
}

.classification-card {
  display: grid;
  gap: 7px;
  padding: 9px;
  background: hsl(var(--muted) / 55%);
  border: 1px solid var(--divider);
  border-radius: 9px;
}

.classification-heading,
.classification-suggestion,
.classification-progress > div {
  justify-content: space-between;
  gap: 8px;
}

.classification-heading strong {
  font-size: 13px;
  color: var(--text-primary);
}

.classification-suggestion {
  padding: 7px 8px;
  font-size: 12px;
  color: var(--text-secondary);
  background: hsl(var(--primary) / 8%);
  border-radius: 7px;
}

.classification-suggestion strong {
  color: hsl(var(--primary));
}

.classification-title,
.classification-hint {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-tertiary);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.classification-hint.is-error {
  color: var(--el-color-danger);
}

.classification-progress {
  display: grid;
  gap: 4px;
}

.classification-progress > div {
  font-size: 10px;
  color: var(--text-tertiary);
}

.classification-actions {
  gap: 6px;
}

.classification-actions :deep(.el-button),
.classification-actions :deep(.el-dropdown) {
  flex: 1;
  min-width: 0;
}

.classification-actions :deep(.el-dropdown .el-button) {
  width: 100%;
}

@media (width <= 720px) {
  .preview-stage {
    padding: 16px;
  }

  .preview-toolbar {
    right: 8px;
    bottom: 8px;
    width: min(270px, calc(100vw - 16px));
  }

  .preview-controls {
    gap: 4px;
  }

  .preview-image-placeholder {
    min-height: 320px;
    padding: 20px;
  }
}
</style>

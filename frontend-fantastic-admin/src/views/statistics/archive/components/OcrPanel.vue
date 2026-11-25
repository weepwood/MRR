<script setup lang="ts">
import type { OcrFact } from '@/api/modules/ocr'
import { ElDrawer, ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { cancelOcrJob, createOcrJob, getOcrFact, getOcrJobs } from '@/api/modules/ocr'
import { Z_INDEX } from '@/constants/z-index'
import useAuth from '@/utils/composables/useAuth'
import { useOcrJob } from '../composables/useOcrJob'
import ClassificationReview from './ClassificationReview.vue'

const props = defineProps<{ scanId: number, imageUrl?: string, unclassified: boolean, expectedArchiveId?: number, inline?: boolean }>()
const { auth } = useAuth()
const { job, error: pollingError, tracking, follow, stop } = useOcrJob()
const open = ref(false)
const fact = ref<OcrFact | null>(null)
const busy = ref(false)
const loading = ref(false)
const cancelBusy = ref(false)
const canRead = computed(() => auth('ocr:read'))
const canText = computed(() => auth('ocr:text-read'))
const loadError = ref('')
let generation = 0
let actionGeneration = 0
const running = computed(() => !!job.value && ['PENDING', 'RUNNING', 'CANCEL_REQUESTED'].includes(job.value.status))
const canStart = computed(() => !!fact.value && !loading.value && !busy.value && !tracking.value && !running.value && !cancelBusy.value && !loadError.value && !pollingError.value)
const statusLabels: Record<string, string> = {
  PENDING: '待识别',
  PROCESSING: '识别中',
  SUCCEEDED: '识别完成',
  LOW_CONFIDENCE: '文字质量较低',
  FAILED: '识别失败',
  SKIPPED: '已跳过',
  STALE: '源文件已变化',
  INTERRUPTED: '已中断',
}

async function load(resume = false) {
  if (!open.value || !canRead.value) { return }
  const version = ++generation
  loading.value = true
  if (resume) { stop(); job.value = null; pollingError.value = '' }
  fact.value = null
  loadError.value = ''
  try {
    const result = await getOcrFact(props.scanId, canText.value)
    if (version !== generation) { return }
    if (result.data && (result.data.scanId !== props.scanId || (props.expectedArchiveId != null && result.data.archiveId !== props.expectedArchiveId))) {
      throw new Error('OCR identity mismatch')
    }
    fact.value = result.data ? { ...result.data, text: canText.value ? result.data.text : null } : null
    if (resume && fact.value) {
      const jobs = await getOcrJobs(fact.value.archiveId)
      if (version !== generation) { return }
      if (!Array.isArray(jobs.data)) { throw new Error('Invalid OCR job list') }
      if (jobs.data[0]) {
        if (!jobs.data[0].id || jobs.data[0].archiveId !== fact.value.archiveId) { throw new Error('OCR job identity mismatch') }
        follow(jobs.data[0].id, fact.value.archiveId)
      }
    }
  }
  catch {
    if (version === generation) { loadError.value = '无法读取文字识别结果' }
  }
  finally { if (version === generation) { loading.value = false } }
}

async function run(wholeArchive = false) {
  if (!open.value || !canRead.value || !auth('ocr:run') || !canStart.value || !fact.value || (!wholeArchive && !props.unclassified)) { return }
  busy.value = true
  const version = generation
  const action = ++actionGeneration
  try {
    const result = await createOcrJob(fact.value.archiveId, wholeArchive ? undefined : props.scanId)
    if (version === generation) {
      if (!result.data || !result.data.id || result.data.archiveId !== fact.value?.archiveId) { throw new Error('OCR job identity mismatch') }
      follow(result.data.id, result.data.archiveId)
    }
  }
  catch {
    if (version === generation) { loadError.value = '创建识别任务未确认，请刷新核对实际状态'; ElMessage.error('无法创建识别任务，请检查功能配置、病案关联或活动任务') }
  }
  finally {
    if (action === actionGeneration) { busy.value = false }
  }
}

async function cancel() {
  if (!open.value || !canRead.value || !auth('ocr:run') || !job.value || !running.value || cancelBusy.value) { return }
  const action = actionGeneration
  const id = job.value.id
  cancelBusy.value = true
  try { await cancelOcrJob(id) }
  catch { if (action === actionGeneration) { ElMessage.error('取消请求失败，请稍后重试') } }
  finally { if (action === actionGeneration) { cancelBusy.value = false } }
}

watch(open, (value) => {
  if (value) {
    if (auth('ocr:read')) { void load(true) }
  }
  else { generation++; actionGeneration++; stop(); fact.value = null; job.value = null; busy.value = false; loading.value = false; cancelBusy.value = false; pollingError.value = ''; loadError.value = '' }
})
watch(() => [props.scanId, props.expectedArchiveId], () => {
  generation++
  actionGeneration++
  stop()
  job.value = null
  fact.value = null
  busy.value = false
  cancelBusy.value = false
  loading.value = false
  pollingError.value = ''
  if (open.value && auth('ocr:read')) { void load(true) }
})
function clearSession() {
  generation++
  actionGeneration++
  stop()
  open.value = false
  fact.value = null
  job.value = null
  busy.value = false
  loading.value = false
  cancelBusy.value = false
  pollingError.value = ''
  loadError.value = ''
}
watch(canRead, (allowed) => { if (!allowed) { clearSession() } }, { flush: 'sync' })
watch(canText, () => { if (open.value && canRead.value) { void load(true) } }, { flush: 'sync' })
watch(() => useUserStore().token, clearSession, { flush: 'sync' })
onBeforeUnmount(clearSession)
watch(() => job.value?.status, (status) => {
  if (status && !['PENDING', 'RUNNING', 'CANCEL_REQUESTED'].includes(status) && open.value) { void load() }
})
</script>

<template>
  <el-config-provider v-if="canRead" :z-index="Z_INDEX.ocrDrawer">
    <el-button v-if="auth('ocr:read') && (!inline || !open)" size="small" @click="open = true">
      识别文字
    </el-button>
    <component :is="inline ? 'section' : ElDrawer" v-if="!inline || open" v-bind="inline ? { 'aria-label': '影像文字识别', 'class': 'ocr-inline' } : { modelValue: open, title: '影像文字识别', size: 'min(960px, 95vw)', appendToBody: true }" @update:model-value="open = $event">
      <div v-if="inline" class="ocr-inline-heading">
        <strong>影像文字识别</strong>
        <el-button size="small" @click="open = false">
          收起识别
        </el-button>
      </div>
      <el-alert v-if="loadError || pollingError" :title="loadError || pollingError" type="error" :closable="false" />
      <p class="ocr-note">
        识别文字供病案整理参考，请对照原图核实。
      </p>
      <div class="ocr-actions">
        <el-tag>{{ loading ? '正在读取' : fact ? (statusLabels[fact.status] || fact.status) : '暂无结果' }}</el-tag>
        <el-button :disabled="loading || busy || cancelBusy" @click="load(true)">
          刷新
        </el-button>
        <template v-if="auth('ocr:run')">
          <el-button :loading="busy" :disabled="!canStart || !unclassified" @click="run(false)">
            识别 / 重试本页
          </el-button>
          <el-button :disabled="!canStart" @click="run(true)">
            识别本病案未分类页
          </el-button>
          <el-button v-if="running" :disabled="job?.cancelRequested || cancelBusy" :loading="cancelBusy" @click="cancel">
            取消任务
          </el-button>
        </template>
      </div>
      <p v-if="tracking && !job" class="ocr-note" role="status">
        正在核对任务进度
      </p>
      <div v-if="job" aria-live="polite">
        <p>{{ job.status }} · {{ job.processedCount }} / {{ job.plannedCount }} 页 · 失败 {{ job.failedCount }} 页</p>
        <el-progress :percentage="job.plannedCount ? Math.round(job.processedCount / job.plannedCount * 100) : 0" />
      </div>
      <div class="ocr-compare">
        <img v-if="imageUrl" :src="imageUrl" alt="当前影像原图" referrerpolicy="no-referrer">
        <div>
          <p v-if="fact?.confidence != null">
            文字置信度：{{ (fact.confidence * 100).toFixed(1) }}%
          </p>
          <p v-if="fact?.processedAt" class="ocr-note">
            识别时间：{{ fact.processedAt }}
          </p>
          <textarea v-if="canText" :value="fact?.text || ''" readonly aria-label="识别原文，可选择复制" />
          <p v-else>
            当前账号没有查看完整识别文字的权限。
          </p>
        </div>
      </div>
      <ClassificationReview v-if="job && auth('classification:read')" :job-id="job.id" />
    </component>
  </el-config-provider>
</template>

<style scoped>
.ocr-note { font-size: var(--mrr-font-size-xs); color: var(--el-text-color-secondary); }
.ocr-inline { min-width: 0; padding: 12px; border: 1px solid var(--el-border-color-light); border-radius: var(--el-border-radius-base); }
.ocr-inline-heading { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; justify-content: space-between; }
.ocr-inline .ocr-compare { grid-template-columns: 1fr; }
.ocr-inline textarea { min-height: 240px; }
.ocr-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 16px 0; }
.ocr-compare { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
.ocr-compare img { align-self: start; width: 100%; height: auto; object-fit: contain; }
.ocr-compare textarea { box-sizing: border-box; width: 100%; min-height: 60vh; padding: 12px; color: var(--mrr-foreground); background: var(--mrr-card); border: 1px solid var(--mrr-border); }

@media (max-width: 700px) { .ocr-compare { grid-template-columns: 1fr; } }
</style>

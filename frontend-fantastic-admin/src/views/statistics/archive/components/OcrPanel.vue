<script setup lang="ts">
import type { OcrFact } from '@/api/modules/ocr'
import { computed, ref, watch } from 'vue'
import { cancelOcrJob, createOcrJob, getOcrFact, getOcrJobs } from '@/api/modules/ocr'
import useAuth from '@/utils/composables/useAuth'
import { useOcrJob } from '../composables/useOcrJob'
import ClassificationReview from './ClassificationReview.vue'

const props = defineProps<{ scanId: number, imageUrl?: string, unclassified: boolean }>()
const { auth } = useAuth()
const { job, error: pollingError, follow, stop } = useOcrJob()
const open = ref(false)
const fact = ref<OcrFact | null>(null)
const busy = ref(false)
const error = ref('')
let generation = 0
const running = computed(() => job.value && ['PENDING', 'RUNNING', 'CANCEL_REQUESTED'].includes(job.value.status))
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
  const version = ++generation
  fact.value = null
  error.value = ''
  try {
    const result = await getOcrFact(props.scanId, auth('ocr:text-read'))
    if (version !== generation) { return }
    fact.value = result.data ?? null
    if (resume && fact.value) {
      const jobs = await getOcrJobs(fact.value.archiveId)
      if (version === generation && jobs.data?.[0]) { follow(jobs.data[0].id) }
    }
  }
  catch {
    if (version === generation) { error.value = '无法读取文字识别结果' }
  }
}

async function run(wholeArchive = false) {
  if (!fact.value || busy.value) { return }
  busy.value = true
  const version = generation
  error.value = ''
  try {
    const result = await createOcrJob(fact.value.archiveId, wholeArchive ? undefined : props.scanId)
    if (version === generation && result.data) { follow(result.data.id) }
  }
  catch {
    if (version === generation) { error.value = '无法创建识别任务，请检查功能配置、病案关联或活动任务' }
  }
  finally { busy.value = false }
}

async function cancel() {
  if (!job.value) { return }
  try { await cancelOcrJob(job.value.id) }
  catch { error.value = '取消请求失败，请稍后重试' }
}

watch(open, (value) => {
  if (value) {
    void load(true)
  }
  else { generation++; stop() }
})
watch(() => props.scanId, () => {
  generation++
  stop()
  job.value = null
  if (open.value) { void load(true) }
})
watch(() => job.value?.status, (status) => {
  if (status && !['PENDING', 'RUNNING', 'CANCEL_REQUESTED'].includes(status) && open.value) { void load() }
})
</script>

<template>
  <el-config-provider :z-index="3100">
    <el-button v-if="auth('ocr:read')" size="small" @click="open = true">
      识别文字
    </el-button>
    <el-drawer v-model="open" title="影像文字识别" size="min(960px, 95vw)" append-to-body>
      <el-alert v-if="error || pollingError" :title="error || pollingError" type="error" :closable="false" />
      <p>识别文字供病案整理参考，请对照原图核实。</p>
      <div class="ocr-actions">
        <el-tag>{{ statusLabels[fact?.status || 'PENDING'] || fact?.status }}</el-tag>
        <el-button @click="load(true)">
          刷新
        </el-button>
        <template v-if="auth('ocr:run')">
          <el-button :loading="busy" :disabled="!fact || !unclassified || !!running" @click="run(false)">
            识别 / 重试本页
          </el-button>
          <el-button :disabled="!fact || !!running || busy" @click="run(true)">
            识别本病案未分类页
          </el-button>
          <el-button v-if="running" :disabled="job?.cancelRequested" @click="cancel">
            取消任务
          </el-button>
        </template>
      </div>
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
          <textarea v-if="auth('ocr:text-read')" :value="fact?.text || ''" readonly aria-label="识别原文，可选择复制" />
          <p v-else>
            当前账号没有查看完整识别文字的权限。
          </p>
        </div>
      </div>
      <ClassificationReview v-if="job && auth('classification:read')" :job-id="job.id" />
    </el-drawer>
  </el-config-provider>
</template>

<style scoped>
.ocr-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 16px 0; }
.ocr-compare { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
.ocr-compare img { align-self: start; width: 100%; height: auto; object-fit: contain; }
.ocr-compare textarea { box-sizing: border-box; width: 100%; min-height: 60vh; padding: 12px; color: var(--mrr-foreground); background: var(--mrr-card); border: 1px solid var(--mrr-border); }

@media (max-width: 700px) { .ocr-compare { grid-template-columns: 1fr; } }
</style>

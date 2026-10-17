<script setup lang="ts">
import type { OcrBatch, OcrBatchPreview, OcrBatchRequest, OcrRevert } from '@/api/modules/ocr-batches'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { actOnOcrBatch, createOcrBatch, listOcrBatches, listOcrBatchJobs, previewOcrBatch, previewOcrRollback, rollbackOcrBatchPage } from '@/api/modules/ocr-batches'
import useAuth from '@/utils/composables/useAuth'
import ClassificationReview from '../statistics/archive/components/ClassificationReview.vue'

defineOptions({ name: 'OcrBatchesPage' })
const { auth } = useAuth()
const form = reactive<OcrBatchRequest>({ afterId: 0, upperId: 0, maxPages: 10000, idempotencyKey: '', autoApply: false })
const batches = ref<OcrBatch[]>([])
const preview = ref<OcrBatchPreview | null>(null)
const previewScope = ref('')
const busy = ref(false)
const error = ref('')
const after = ref('')
const childBatch = ref('')
const childJobs = ref<string[]>([])
const selectedJob = ref('')
const visibility = useDocumentVisibility()
const scope = computed(() => `${form.afterId}:${form.upperId}:${form.maxPages}:${form.autoApply}`)
const validPreview = computed(() => preview.value && previewScope.value === scope.value && (!form.autoApply || preview.value.autoAvailable))
const autoAvailable = ref(false)
const rollbackId = ref('')
const rollbackOpen = ref(false)
const rollbackAfter = ref(0)
const rollbackRows = ref<OcrRevert[]>([])
const rollbackMessage = ref('')
let loading = false
let generation = 0

async function reload() {
  if (loading || visibility.value === 'hidden') { return }
  loading = true
  const version = generation
  try {
    const result = await listOcrBatches(after.value)
    if (version === generation) { batches.value = result.data || [] }
  }
  catch { error.value = '批次读取失败，请稍后刷新' }
  finally { loading = false }
}
const polling = useIntervalFn(() => void reload(), 3000, { immediate: false })
watch([visibility, batches], () => {
  if (visibility.value === 'visible' && batches.value.some(b => ['RUNNING', 'CANCEL_REQUESTED'].includes(b.status))) {
    polling.resume()
  }
  else { polling.pause() }
})
watch(visibility, (value) => {
  if (value === 'visible') { void reload() }
})
watch(scope, () => { preview.value = null; form.idempotencyKey = '' })
async function attempt(work: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try { await work() }
  catch { error.value = '操作失败，请检查权限、任务状态和 OCR 配置' }
  finally { busy.value = false }
}
async function inspect() {
  await attempt(async () => {
    const requestedScope = scope.value
    form.idempotencyKey ||= `batch-${Date.now()}-${Math.random().toString(36).slice(2)}`
    const result = await previewOcrBatch({ ...form })
    if (scope.value === requestedScope) { preview.value = result.data || null; previewScope.value = requestedScope; autoAvailable.value = !!result.data?.autoAvailable }
  })
}
async function create() {
  if (!validPreview.value || !preview.value) { return }
  await attempt(async () => {
    await createOcrBatch({ ...form, upperId: preview.value!.upperId, policyChecksum: form.autoApply ? preview.value!.policyChecksum : undefined })
    preview.value = null
    form.idempotencyKey = ''
    after.value = ''
    generation++
    await reload()
  })
}
async function action(id: string, value: 'pause' | 'resume' | 'cancel') {
  await attempt(async () => { await actOnOcrBatch(id, value); await reload() })
}
async function children(id: string, next = false) {
  await attempt(async () => {
    const cursor = next && childBatch.value === id ? childJobs.value[childJobs.value.length - 1] || '' : ''
    childBatch.value = id
    childJobs.value = (await listOcrBatchJobs(id, cursor)).data || []
    selectedJob.value = childJobs.value[0] || ''
  })
}
async function nextPage() { after.value = batches.value[batches.value.length - 1]?.id || ''; generation++; await reload() }
onMounted(() => void reload())
async function inspectRollback(id: string) {
  await attempt(async () => {
    rollbackId.value = id
    rollbackAfter.value = 0
    rollbackRows.value = (await previewOcrRollback(id)).data || []
    rollbackMessage.value = ''
    rollbackOpen.value = true
  })
}
async function rollbackPage() {
  await attempt(async () => {
    const result = (await rollbackOcrBatchPage(rollbackId.value, rollbackAfter.value)).data
    if (result) {
      rollbackMessage.value = `已回退 ${result.reverted} 页，跳过 ${result.skipped} 页`
      rollbackAfter.value = result.nextAfterAuditId
      rollbackRows.value = (await previewOcrRollback(rollbackId.value, rollbackAfter.value)).data || []
      await reload()
    }
  })
}
async function skipRollbackPage() {
  rollbackAfter.value = rollbackRows.value[rollbackRows.value.length - 1]?.auditId || rollbackAfter.value
  rollbackRows.value = (await previewOcrRollback(rollbackId.value, rollbackAfter.value)).data || []
}
</script>

<template>
  <div v-loading="busy" class="ocr-batches">
    <h1>历史未分类影像识别</h1>
    <p>只处理有效、病案关联已核验且仍未分类的页面。批次有明确上界，每次最多 10,000 页，主要在北京时间 22:00–06:00 运行。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form inline label-position="top">
      <el-form-item label="起点影像 ID（不包含）">
        <el-input-number v-model="form.afterId" :min="0" :max="2147483647" />
      </el-form-item>
      <el-form-item label="终点影像 ID（0 为当前最大值）">
        <el-input-number v-model="form.upperId" :min="0" :max="2147483647" />
      </el-form-item>
      <el-form-item label="本批次页数上限">
        <el-input-number v-model="form.maxPages" :min="1" :max="10000" />
      </el-form-item>
    </el-form>
    <p v-if="auth('classification:auto-apply') && auth('classification:review')">
      <el-checkbox v-model="form.autoApply" :disabled="!autoAvailable">
        授权本批次仅对已验收类别自动归类，其余页面进入人工审核
      </el-checkbox>
      <span v-if="!autoAvailable">请先预览；没有对应模型、规则与类别的独立验收证据时不可开启。</span>
    </p>
    <el-button @click="inspect">
      预览范围
    </el-button>
    <div v-if="validPreview && preview" class="batch-preview">
      <p>扫描上界 {{ preview.upperId }}；本次范围内最多处理 {{ preview.eligibleInLimit }} 页{{ preview.truncated ? '，仍有更多候选，后续批次继续处理' : '' }}。</p>
      <p>运行条件：{{ preview.readiness }}。未达到资源或延迟基线要求时保持等待。</p>
      <p v-if="form.autoApply">
        将自动归类的类型编号：{{ preview.autoTypes.join('、') }}。授权人由当前登录账号记录，已分类页面不会被自动覆盖。
      </p>
      <el-button type="primary" :disabled="!preview.eligibleInLimit" @click="create">
        创建此范围的识别批次
      </el-button>
    </div>
    <el-table :data="batches">
      <el-table-column prop="id" label="批次" min-width="180" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="进度">
        <template #default="{ row }">
          {{ row.processedCount }} / {{ row.dispatchedCount }} 已登记页
        </template>
      </el-table-column>
      <el-table-column prop="successCount" label="已识别" /><el-table-column prop="failedCount" label="失败" />
      <el-table-column prop="autoAppliedCount" label="自动归类" /><el-table-column prop="reviewRequiredCount" label="待审核建议" />
      <el-table-column prop="cursorId" label="续跑游标" /><el-table-column prop="reasonCode" label="等待原因" />
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <el-button v-if="row.status === 'RUNNING'" @click="action(row.id, 'pause')">
            暂停
          </el-button><el-button v-if="row.status === 'PAUSED'" @click="action(row.id, 'resume')">
            续跑
          </el-button><el-button v-if="['RUNNING', 'PAUSED'].includes(row.status)" @click="action(row.id, 'cancel')">
            取消
          </el-button><el-button @click="children(row.id)">
            查看明细与审核
          </el-button>
          <el-button v-if="row.autoApply && auth('classification:override')" @click="inspectRollback(row.id)">
            回退预览
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button @click="after = ''; generation++; reload()">
      首批 / 刷新
    </el-button><el-button :disabled="batches.length < 20" @click="nextPage">
      更多批次
    </el-button>
    <section v-if="childBatch && auth('classification:read')">
      <h2>批次任务与人工审核</h2>
      <el-select v-model="selectedJob" placeholder="选择病案任务">
        <el-option v-for="id in childJobs" :key="id" :value="id" :label="id" />
      </el-select>
      <el-button :disabled="childJobs.length < 50" @click="children(childBatch, true)">
        更多病案任务
      </el-button>
      <ClassificationReview v-if="selectedJob" :job-id="selectedJob" />
    </section>
    <el-dialog v-model="rollbackOpen" title="自动归类回退预览" width="min(900px, 95vw)">
      <p>先关闭自动归类总开关并暂停或取消批次。只撤销该批次仍为最后一次变更的自动归类，后续人工操作会跳过。</p>
      <p aria-live="polite">
        {{ rollbackMessage }}
      </p>
      <el-table :data="rollbackRows">
        <el-table-column prop="scanId" label="影像 ID" /><el-table-column prop="newType" label="自动类型" /><el-table-column label="可回退">
          <template #default="{ row }">
            {{ row.eligible ? '可以恢复为未分类' : '已有后续变化，跳过' }}
          </template>
        </el-table-column>
      </el-table>
      <el-button :disabled="!rollbackRows.some(row => row.eligible)" @click="rollbackPage">
        回退本页可撤销项（最多 50 页）
      </el-button>
      <el-button :disabled="!rollbackRows.length" @click="skipRollbackPage">
        跳过本页
      </el-button>
    </el-dialog>
  </div>
</template>

<style scoped>
.ocr-batches { padding: 24px; background: var(--mrr-card); }
.batch-preview { padding: 16px; margin: 16px 0; border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
</style>

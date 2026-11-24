<script setup lang="ts">
import type { OcrBatch, OcrBatchPreview, OcrBatchRequest, OcrRevert } from '@/api/modules/ocr-batches'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { actOnOcrBatch, createOcrBatch, listOcrBatches, listOcrBatchJobs, previewOcrBatch, previewOcrRollback, rollbackOcrBatchPage } from '@/api/modules/ocr-batches'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import MrrStatusTag from '@/components/MrrStatusTag/index.vue'
import { actionDialog } from '@/ui/components/ActionDialog'
import useAuth from '@/utils/composables/useAuth'
import { getRequestErrorMessage } from '@/utils/request-error-message'
import ClassificationReview from '../statistics/archive/components/ClassificationReview.vue'

defineOptions({ name: 'OcrBatchesPage' })

/*
 * 批次状态的展示文案与色调。只影响显示：轮询、按钮可见性仍按原始状态码判断。
 * 未登记的状态码原样显示为中性色，不猜测含义；原始码保留在 title 中便于对照日志。
 */
const batchStatusMeta: Record<string, { label: string, tone: 'success' | 'info' | 'warning' | 'danger' | 'neutral' }> = {
  RUNNING: { label: '运行中', tone: 'info' },
  PAUSED: { label: '已暂停', tone: 'warning' },
  CANCEL_REQUESTED: { label: '取消中', tone: 'warning' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  COMPLETED: { label: '已完成', tone: 'success' },
  FAILED: { label: '失败', tone: 'danger' },
}
function statusMeta(status: string) {
  return batchStatusMeta[status] ?? { label: status || '-', tone: 'neutral' as const }
}
const { auth } = useAuth()
const form = reactive<OcrBatchRequest>({ afterId: 0, upperId: 0, maxPages: 10000, idempotencyKey: '', autoApply: false })
const batches = ref<OcrBatch[]>([])
const preview = ref<OcrBatchPreview | null>(null)
const previewScope = ref('')
const busy = ref(false)
const loadError = ref('')
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
    if (version === generation) { batches.value = result.data || []; loadError.value = '' }
  }
  catch { loadError.value = '批次读取失败，请稍后刷新' }
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
/*
 * 统一操作包装。失败提示走仓库统一的中文映射（getRequestErrorMessage）：
 * 服务端带中文 message 时原样透出（夜班排障需要线索），网络/超时/状态码
 * 也有各自的中文文案——不把 axios 的 "Network Error" 这类英文原文丢给操作员。
 */
async function attempt(work: () => Promise<void>) {
  busy.value = true
  try { await work() }
  catch (error) { ElMessage.error(getRequestErrorMessage(error)) }
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
  /*
   * 取消是不可逆操作：批次进入 CANCEL 流程后无法恢复执行，且按钮就排在
   * 高频的暂停/续跑旁边。与档案袋导出取消一致，执行前必须确认。
   */
  if (value === 'cancel') {
    try {
      await actionDialog.confirm(
        `取消后批次「${id}」停止处理，状态不可恢复为执行中；已完成的页保持不变。`,
        '取消识别批次',
        { type: 'warning', confirmButtonText: '取消批次', cancelButtonText: '保持运行' },
      )
    }
    catch {
      // 用户选择保持运行（关闭确认框、Esc、遮罩点击都视为放弃取消）
      return
    }
  }
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
  await attempt(async () => {
    rollbackAfter.value = rollbackRows.value[rollbackRows.value.length - 1]?.auditId || rollbackAfter.value
    rollbackRows.value = (await previewOcrRollback(rollbackId.value, rollbackAfter.value)).data || []
  })
}
</script>

<template>
  <MrrPageShell v-loading="busy" class="ocr-batches" width="fluid">
    <MrrPageHeader
      size="compact"
      eyebrow="OCR BATCH PROCESSING"
      title="历史未分类影像识别"
      description="只处理有效、病案关联已核验且仍未分类的页面。批次有明确上界，每次最多 10,000 页，主要在北京时间 22:00–06:00 运行。"
    >
      <template #actions>
        <el-button @click="after = ''; generation++; reload()">
          <FaIcon name="i-lucide:refresh-cw" />
          首批 / 刷新
        </el-button>
      </template>
    </MrrPageHeader>

    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <MrrSectionCard
      title="批次范围"
      description="设置影像 ID 范围和本批次页数上限，再预览符合条件的页面。"
      body-padding="normal"
    >
      <template #actions>
        <el-button @click="inspect">
          预览范围
        </el-button>
      </template>

      <el-form class="ocr-batches__form" label-position="top">
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

      <div v-if="auth('classification:auto-apply') && auth('classification:review')" class="ocr-batches__auto-apply">
        <el-checkbox v-model="form.autoApply" :disabled="!autoAvailable">
          授权本批次仅对已验收类别自动归类，其余页面进入人工审核
        </el-checkbox>
        <span v-if="!autoAvailable" class="ocr-batches__hint">请先预览；没有对应模型、规则与类别的独立验收证据时不可开启。</span>
      </div>

      <div v-if="validPreview && preview" class="batch-preview">
        <p>扫描上界 {{ preview.upperId }}；本次范围内最多处理 {{ preview.eligibleInLimit }} 页{{ preview.truncated ? '，仍有更多候选，后续批次继续处理' : '' }}。</p>
        <p>运行条件：{{ preview.readiness }}。未达到资源或延迟基线要求时保持等待。</p>
        <p v-if="form.autoApply">
          将自动归类的类型编号：{{ preview.autoTypes.join('、') }}。授权人由当前登录账号记录，已分类页面不会被自动覆盖。
        </p>
        <div class="batch-preview__actions">
          <el-button type="primary" :disabled="!preview.eligibleInLimit" @click="create">
            创建此范围的识别批次
          </el-button>
        </div>
      </div>
    </MrrSectionCard>

    <MrrDataTablePanel title="识别批次" description="查看批次处理进度、审核建议和运行状态。" :count="batches.length" count-label="当前页">
      <template #actions>
        <el-button :disabled="batches.length < 20" @click="nextPage">
          更多批次
        </el-button>
      </template>

      <el-table :data="batches">
        <el-table-column prop="id" label="批次" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ocr-batches__mono">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="110">
          <template #default="{ row }">
            <MrrStatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" :title="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="170">
          <template #default="{ row }">
            <span class="ocr-batches__num">{{ row.processedCount }} / {{ row.dispatchedCount }}</span>
            <span class="ocr-batches__unit">已登记页</span>
          </template>
        </el-table-column>
        <!-- 计数列右对齐：数字按个位对齐，扫读一列时不必逐个比较位数。 -->
        <el-table-column prop="successCount" label="已识别" min-width="84" align="right" />
        <el-table-column prop="failedCount" label="失败" min-width="72" align="right">
          <template #default="{ row }">
            <span :class="{ 'ocr-batches__failed': row.failedCount > 0 }">{{ row.failedCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="autoAppliedCount" label="自动归类" min-width="90" align="right" />
        <el-table-column prop="reviewRequiredCount" label="待审核建议" min-width="104" align="right" />
        <el-table-column prop="cursorId" label="续跑游标" min-width="110">
          <template #default="{ row }">
            <span class="ocr-batches__mono">{{ row.cursorId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reasonCode" label="等待原因" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reasonCode" class="ocr-batches__mono">{{ row.reasonCode }}</span>
            <span v-else class="ocr-batches__unit">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <!--
              原来最多 5 个默认按钮挤在 280px 列里，会折成两三行且行高不齐。
              改为一组小号按钮：流程控制在前（暂停 / 续跑 / 取消），查看与回退在后；
              取消与回退是不可逆或高影响操作，用危险色文字区分，按钮名称与行为不变。
            -->
            <div class="ocr-batches__row-actions">
              <el-button v-if="row.status === 'RUNNING'" size="small" @click="action(row.id, 'pause')">
                暂停
              </el-button>
              <el-button v-if="row.status === 'PAUSED'" size="small" @click="action(row.id, 'resume')">
                续跑
              </el-button>
              <el-button v-if="['RUNNING', 'PAUSED'].includes(row.status)" size="small" type="danger" plain @click="action(row.id, 'cancel')">
                取消
              </el-button>
              <el-button size="small" @click="children(row.id)">
                查看明细与审核
              </el-button>
              <el-button v-if="row.autoApply && auth('classification:override')" size="small" type="danger" link @click="inspectRollback(row.id)">
                回退预览
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </MrrDataTablePanel>

    <MrrSectionCard
      v-if="childBatch && auth('classification:read')"
      title="批次任务与人工审核"
      description="选择批次内的任务，查看识别结果并处理人工审核建议。"
      body-padding="compact"
    >
      <div class="ocr-batches__job-toolbar">
        <el-select v-model="selectedJob" class="ocr-batches__job-select" aria-label="病案任务" placeholder="选择病案任务">
          <el-option v-for="id in childJobs" :key="id" :value="id" :label="id" />
        </el-select>
        <el-button :disabled="childJobs.length < 50" @click="children(childBatch, true)">
          更多病案任务
        </el-button>
      </div>
      <ClassificationReview v-if="selectedJob" :job-id="selectedJob" />
    </MrrSectionCard>

    <el-dialog v-model="rollbackOpen" title="自动归类回退预览" width="min(900px, 95vw)">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="先关闭自动归类总开关并暂停或取消批次。只撤销该批次仍为最后一次变更的自动归类，后续人工操作会跳过。"
      />
      <p v-if="rollbackMessage" class="ocr-batches__rollback-result" aria-live="polite">
        {{ rollbackMessage }}
      </p>
      <el-table :data="rollbackRows">
        <el-table-column prop="scanId" label="影像 ID" /><el-table-column prop="newType" label="自动类型" /><el-table-column label="可回退">
          <template #default="{ row }">
            {{ row.eligible ? '可以恢复为未分类' : '已有后续变化，跳过' }}
          </template>
        </el-table-column>
      </el-table>
      <!-- 操作按钮放进对话框底栏：原来紧跟在表格下方，长表格时要滚到底才看得到。 -->
      <template #footer>
        <el-button :disabled="!rollbackRows.length" @click="skipRollbackPage">
          跳过本页
        </el-button>
        <el-button type="danger" :disabled="!rollbackRows.some(row => row.eligible)" @click="rollbackPage">
          回退本页可撤销项（最多 50 页）
        </el-button>
      </template>
    </el-dialog>
  </MrrPageShell>
</template>

<style scoped>
.ocr-batches {
  min-width: 0;
}

.ocr-batches__form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  gap: var(--mrr-space-3) var(--mrr-space-4);
}

.ocr-batches__form :deep(.el-form-item) {
  min-width: 0;
  margin: 0;
}

.ocr-batches__form :deep(.el-input-number) {
  width: 100%;
}

.ocr-batches__auto-apply {
  display: grid;
  gap: var(--mrr-space-2);
  margin-top: var(--mrr-space-4);
}

.ocr-batches__auto-apply :deep(.el-checkbox) {
  white-space: normal;
}

.ocr-batches__hint {
  font-size: var(--mrr-font-size-xs);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.batch-preview {
  display: grid;
  gap: var(--mrr-space-2);
  padding: var(--mrr-space-3) var(--mrr-space-4);
  margin-top: var(--mrr-space-4);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.batch-preview p {
  margin: 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.6;
}

/* 创建按钮靠右、与说明文字之间留一条分隔线：说明是「读」，按钮是「做」。 */
.batch-preview__actions {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--mrr-space-3);
  margin-top: var(--mrr-space-1);
  border-top: 1px solid var(--mrr-border);
}

.ocr-batches__row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-1) var(--mrr-space-2);
  align-items: center;
}

.ocr-batches__row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.ocr-batches__mono {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
}

.ocr-batches__num {
  font-variant-numeric: tabular-nums;
}

.ocr-batches__unit {
  margin-left: var(--mrr-space-1);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.ocr-batches__failed {
  font-weight: 600;
  color: var(--mrr-tone-danger);
}

.ocr-batches__job-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  margin-bottom: var(--mrr-space-3);
}

.ocr-batches__job-select {
  flex: 0 1 320px;
  min-width: 200px;
}

.ocr-batches__rollback-result {
  margin: var(--mrr-space-3) 0 0;
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-foreground);
}

.el-dialog .el-table {
  margin-top: var(--mrr-space-3);
}
</style>

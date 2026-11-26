<script setup lang="ts">
import type { QualityRecheck, QualityReview, ReviewAction, ReviewContext, ReviewEvent } from '@/api/modules/quality-case-review'
import { ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { decideQualityReview, getQualityReview, getQualityReviewEvents, listQualityReviews, recheckQualityReview } from '@/api/modules/quality-case-review'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'
import { tableSequenceColumn } from '@/utils/table-sequence'

const props = defineProps<{ issueId?: number }>()
const allowed = computed(() => hasPermission('system:manage') && hasPermission('record:manage'))
const context = ref<ReviewContext>()
const rows = ref<QualityReview[]>([])
const selected = ref<QualityReview>()
const events = ref<ReviewEvent[]>([])
const busy = ref(false)
const observation = ref<QualityRecheck>()
const failed = ref(false)
const more = ref(false)
const labels: Record<string, string> = { OPEN: '重新待核对', CONFIRMED: '已确认样本', IGNORED: '暂缓处理' }
const recheckLabels: Record<string, string> = {
  STILL_MISSING: '当前仍缺少关联',
  LINK_PRESENT_VERIFIED: '当前唯一关联已核对',
  LINK_UNCONFIRMED: '当前关联无法确认',
  IDENTITY_CHANGED: '当前编号与历史样本不同',
  OUT_OF_SCOPE: '当前记录状态不在安全复验范围',
  RECORD_UNAVAILABLE: '当前记录不存在',
  EVIDENCE_UNAVAILABLE: '原样本证据不可用',
}
const actionLabels: Record<ReviewAction, string> = { CONFIRM: '确认异常', IGNORE: '暂缓处理', REOPEN: '重新打开' }
let generation = 0
let controller: AbortController | undefined

function reset() {
  generation++
  controller?.abort()
  controller = undefined
  observation.value = undefined
  context.value = undefined
  rows.value = []
  selected.value = undefined
  events.value = []
  busy.value = false
  failed.value = false
  more.value = false
}
async function load(append = false) {
  if (!allowed.value || busy.value) { return }
  if (!append) { reset() }
  const current = ++generation
  controller?.abort()
  controller = new AbortController()
  busy.value = true
  failed.value = false
  try {
    if (props.issueId != null) {
      const { data } = await getQualityReview(props.issueId, controller.signal)
      if (current !== generation) { return }
      if (!data) { throw new Error('Invalid review context') }
      context.value = data
      selected.value = data.review ?? undefined
      events.value = data.events
    }
    else {
      const { data } = await listQualityReviews(append ? rows.value.at(-1)?.id : undefined, controller.signal)
      if (current !== generation) { return }
      if (!data || data.reviews.length > 50) { throw new Error('Invalid review page') }
      rows.value.push(...data.reviews)
      more.value = data.reviews.length === 50
    }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { busy.value = false }
  }
}
async function history(row: QualityReview, append = false) {
  if (busy.value || !allowed.value) { return }
  const current = ++generation
  controller?.abort()
  controller = new AbortController()
  busy.value = true
  failed.value = false
  if (!append) { events.value = []; observation.value = undefined }
  selected.value = row
  try {
    const { data } = await getQualityReviewEvents(row.id, append ? events.value.at(-1)?.revision : 0, controller.signal)
    if (generation !== current || selected.value?.id !== row.id) { return }
    if (!Array.isArray(data)) { throw new Error('Invalid review history') }
    events.value.push(...data)
  }
  catch {
    if (generation === current) { failed.value = true }
  }
  finally {
    if (generation === current) { busy.value = false }
  }
}
async function recheck() {
  const row = selected.value
  if (!row || busy.value || !allowed.value) { return }
  const current = ++generation
  controller?.abort()
  controller = new AbortController()
  busy.value = true
  failed.value = false
  observation.value = undefined
  try {
    const { data } = await recheckQualityReview(row.id, controller.signal)
    if (current !== generation) { return }
    if (!data || data.caseId !== row.id || data.revision !== row.revision || !Object.hasOwn(recheckLabels, data.status)) {
      throw new Error('Invalid or stale recheck')
    }
    observation.value = data
  }
  catch { if (current === generation) { failed.value = true } }
  finally { if (current === generation) { busy.value = false } }
}
const canDecide = computed(() => allowed.value && !!context.value?.enabled && !!context.value.latest && !failed.value)
async function decide(action: ReviewAction) {
  const issue = props.issueId
  if (!canDecide.value || !issue || busy.value) { return }
  const current = ++generation
  const revision = context.value?.review?.revision ?? 0
  busy.value = true
  try {
    await ElMessageBox.confirm('仅记录对本次检查样本的人工决策，不修改病案、不隐藏原异常，也不证明异常已经恢复。', actionLabels[action], { type: 'warning' })
    if (current !== generation || !allowed.value || props.issueId !== issue) { return }
    await decideQualityReview(issue, revision, action)
    if (current !== generation) { return }
    observation.value = undefined
    busy.value = false
    await load()
  }
  catch (error) {
    if (current === generation && error !== 'cancel' && error !== 'close') { failed.value = true }
  }
  finally {
    if (current === generation) { busy.value = false }
  }
}
watch([() => props.issueId, allowed], () => { reset(); void load() }, { immediate: true, flush: 'sync' })
watch(() => useUserStore().token, reset, { flush: 'sync' })
onBeforeUnmount(reset)
</script>

<template>
  <MrrSectionCard v-if="allowed" title="异常人工决策" description="仅记录样本确认、暂缓处理和重新打开；不修改医疗数据，不影响原检查计数。">
    <template #actions>
      <el-button :loading="busy" @click="load()">
        刷新决策
      </el-button>
    </template>
    <AppError v-if="failed" message="决策读取或保存失败，请刷新核对实际状态" @retry="load()" />
    <template v-if="issueId != null && context">
      <p>{{ context.reviewedThisSample ? '本次样本已有决策' : '本次样本尚未记录决策' }} · {{ context.latest ? '最新成功检查' : '历史样本（只读）' }}</p>
      <p v-if="!context.enabled">
        决策写入已关闭，历史仅供查看。
      </p>
      <p v-if="context.review && !context.reviewedThisSample">
        下方是之前样本的决策，不代表本次异常已处理。
      </p>
      <div v-if="canDecide" class="review-actions">
        <el-button :disabled="busy || (context.reviewedThisSample && context.review?.status === 'CONFIRMED')" @click="decide('CONFIRM')">
          确认异常
        </el-button>
        <el-button :disabled="busy || (context.reviewedThisSample && context.review?.status === 'IGNORED')" @click="decide('IGNORE')">
          暂缓处理
        </el-button>
        <el-button v-if="context.review" :disabled="busy || (context.reviewedThisSample && context.review.status === 'OPEN')" @click="decide('REOPEN')">
          重新打开
        </el-button>
      </div>
    </template>
    <template v-if="issueId == null">
      <el-table v-if="rows.length" :data="rows" row-key="id" @row-click="row => history(row)">
        <el-table-column v-bind="tableSequenceColumn()" />
        <el-table-column prop="id" label="案件" width="90" />
        <el-table-column prop="entityType" label="记录类型" min-width="170" />
        <el-table-column prop="entityId" label="记录 ID" min-width="120" />
        <el-table-column label="最近样本决策" min-width="130">
          <template #default="{ row }">
            {{ labels[row.status] ?? row.status }}
          </template>
        </el-table-column>
        <el-table-column prop="issueId" label="样本 ID" min-width="110" />
      </el-table>
      <AppEmpty v-else-if="!busy && !failed" description="暂无人工决策历史" />
      <el-button v-if="more" :disabled="busy" @click="load(true)">
        更多案件
      </el-button>
    </template>
    <section v-if="selected">
      <h4>案件 #{{ selected.id }} · {{ labels[selected.status] ?? selected.status }} · 版本 {{ selected.revision }}</h4>
      <el-button :disabled="busy" @click="recheck">
        逐记录只读复验
      </el-button>
      <div v-if="observation" class="review-observation" role="status">
        <strong>{{ recheckLabels[observation.status] }}</strong>
        <p>观察时间 {{ formatDateTime(observation.observedAt) }} · 案件版本 {{ observation.revision }}</p>
        <p>仅为当前关联及保留样本的时点核对，不关闭案件，不代表其他异常已解决。</p>
      </div>
      <el-timeline>
        <el-timeline-item v-for="event in events" :key="event.revision" :timestamp="formatDateTime(event.occurredAt)">
          {{ labels[event.status] ?? event.status }} · 样本 #{{ event.issueId }} · 操作者 #{{ event.actorId }} · v{{ event.revision }}
        </el-timeline-item>
      </el-timeline>
      <el-button v-if="events.length && events.at(-1)!.revision > 1" :disabled="busy" @click="history(selected, true)">
        更多决策记录
      </el-button>
    </section>
  </MrrSectionCard>
</template>

<style scoped>
.review-observation {
  padding: var(--mrr-space-2);
  margin: var(--mrr-space-2) 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
}
</style>

<script setup lang="ts">
import type { RepairAction, RepairEvent, RepairOrder } from '@/api/modules/repair-orders'
import { ElMessageBox } from 'element-plus'
import { actOnRepairOrder, getRepairEvents, listRepairOrders, proposeRepairOrder } from '@/api/modules/repair-orders'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { isSessionAccessDenied } from '@/utils/session-access-denied'
import { tableSequenceColumn } from '@/utils/table-sequence'

const props = defineProps<{ archiveId?: number, issueId?: number, deterministic?: boolean }>()
const orders = ref<RepairOrder[]>([])
const events = ref<RepairEvent[]>([])
const selected = ref<RepairOrder | null>(null)
const actor = ref(0)
const enabled = ref(false)
const busy = ref(false)
const failed = ref(false)
const more = ref(false)
const labels: Record<string, string> = {
  PROPOSED: '待审批',
  APPROVED: '已审批',
  APPLIED: '已执行',
  VERIFIED: '已复验',
  CANCELLED: '已取消',
  COMPENSATED: '已补偿',
  SUPERSEDED: '异常重开（旧工单）',
}
const actionLabels: Record<RepairAction, string> = {
  approve: '审批通过',
  apply: '确认执行',
  verify: '复验',
  cancel: '取消工单',
  compensate: '条件补偿',
}
let generation = 0
let auditGeneration = 0
const auditBusy = ref(false)
// Only transaction events that actually observe a relationship carry evidence.
const relationEvents = computed(() => events.value.filter(event => ['APPLIED', 'VERIFIED', 'COMPENSATED'].includes(event.action)))
const latestRelationEvent = computed(() => relationEvents.value.at(-1))
function archiveLabel(id: number | null | undefined) {
  return id === null ? '未关联' : id === undefined ? '未取得' : `主档 #${id}`
}
function evidenceLabel(event: RepairEvent) {
  if (event.action === 'APPLIED') { return '执行时关联变化' }
  if (event.action === 'COMPENSATED') { return '补偿时关联变化' }
  if (event.action === 'VERIFIED') { return '复验时关联观察' }
  return '流程状态变更'
}

const auditFailed = ref(false)
const auditMore = ref(false)
let auditAppend = false

function clearAudit() {
  auditGeneration++
  selected.value = null
  events.value = []
  auditBusy.value = false
  auditFailed.value = false
  auditMore.value = false
  auditAppend = false
}
const recurrence = computed(() => orders.value.find(row => row.status === 'VERIFIED' && row.issueId !== props.issueId))
const canPropose = computed(() => enabled.value && props.deterministic && props.issueId && !orders.value.some(row =>
  !['CANCELLED', 'COMPENSATED', 'SUPERSEDED'].includes(row.status) && row.id !== recurrence.value?.id))

function entityLabel(row: RepairOrder) {
  const kind = row.entityType === 'mr_archive_box_record' ? '装箱' : '影像'
  return `${kind} #${row.entityId ?? row.scanId ?? '—'}`
}

function actions(row: RepairOrder): RepairAction[] {
  if (!enabled.value || !actor.value) {
    return []
  }
  if (row.status === 'PROPOSED') {
    return row.proposedBy === actor.value ? ['cancel'] : ['approve']
  }
  if (row.status === 'APPROVED') {
    if (row.approvedBy === actor.value) {
      return ['apply', 'cancel']
    }
    return row.proposedBy === actor.value ? ['cancel'] : []
  }
  if (row.status === 'APPLIED') {
    return row.approvedBy === actor.value ? ['verify', 'compensate'] : ['verify']
  }
  return row.status === 'VERIFIED' && row.approvedBy === actor.value ? ['compensate'] : []
}

function clear() {
  generation++
  orders.value = []
  clearAudit()
  actor.value = 0
  enabled.value = false
  busy.value = false
  failed.value = false
  more.value = false
}

async function load(append = false, focus?: number) {
  if (busy.value) {
    return
  }
  const current = ++generation
  busy.value = true
  failed.value = false
  const before = append ? orders.value.at(-1)?.id : undefined
  if (!append) {
    orders.value = []
    clearAudit()
  }
  try {
    const { data } = await listRepairOrders({ archiveId: props.archiveId, issueId: props.issueId, before })
    if (current !== generation) {
      return
    }
    if (!data || !Array.isArray(data.orders)) {
      throw new Error('Invalid repair response')
    }
    actor.value = data.actorId
    enabled.value = data.enabled
    orders.value = [...orders.value, ...data.orders]
    more.value = data.orders.length === 50
    const row = orders.value.find(item => item.id === focus)
    if (row) {
      void readEvents(row)
    }
  }
  catch {
    if (current === generation) {
      failed.value = true
      enabled.value = false
    }
  }
  finally {
    if (current === generation) {
      busy.value = false
    }
  }
}

async function readEvents(row: RepairOrder, append = false) {
  if (append && (auditBusy.value || selected.value?.id !== row.id || !auditMore.value)) { return }
  const current = ++auditGeneration
  const after = append ? events.value.at(-1)?.id ?? 0 : 0
  if (!append) {
    events.value = []
    auditMore.value = false
  }
  selected.value = row
  auditBusy.value = true
  auditFailed.value = false
  auditAppend = append
  try {
    const { data } = await getRepairEvents(row.id, after)
    if (current !== auditGeneration || selected.value?.id !== row.id) { return }
    if (!Array.isArray(data) || data.length > 100) { throw new Error('Invalid audit page') }
    let cursor = after
    for (const event of data) {
      if (!Number.isSafeInteger(event.id) || event.id <= cursor) { throw new Error('Invalid audit cursor') }
      cursor = event.id
    }
    events.value = [...events.value, ...data]
    auditMore.value = data.length === 100
  }
  catch (error) {
    if (current === auditGeneration) {
      if (isSessionAccessDenied(error)) {
        clear()
        failed.value = true
      }
      else { auditFailed.value = true }
    }
  }
  finally {
    if (current === auditGeneration) { auditBusy.value = false }
  }
}

async function select(row: RepairOrder) {
  if (busy.value || (selected.value?.id === row.id && !auditFailed.value)) { return }
  await readEvents(row)
}
function retryAudit() {
  if (selected.value && !busy.value && !auditBusy.value) { void readEvents(selected.value, auditAppend) }
}
function moreAudit() {
  if (selected.value && !busy.value) { void readEvents(selected.value, true) }
}

async function change(action: RepairAction | 'propose', row?: RepairOrder) {
  if (busy.value || !enabled.value || (action === 'propose' ? !canPropose.value : !row || !actions(row).includes(action))) {
    return
  }
  const current = ++generation
  const issue = props.issueId
  busy.value = true
  failed.value = false
  try {
    const label = action === 'propose' ? (recurrence.value ? '重新提交修复工单' : '提交修复工单') : actionLabels[action]
    const message = action === 'propose'
      ? (recurrence.value ? `为异常 #${issue} 重新提交工单，必须是复验后新成功检查发现的同一记录缺失关联；保留旧工单与审计，新工单独立审批，不修改病案。` : `为异常 #${issue} 提交唯一关联候选，提交本身不会修改病案。`)
      : `工单 #${row?.id}：${row ? entityLabel(row) : '—'}，目标主档 #${row?.targetArchiveId}。后端将重新检查身份、状态和关联事实。`
    try {
      await ElMessageBox.confirm(message, label, { type: action === 'apply' || action === 'compensate' ? 'warning' : 'info', confirmButtonText: label, cancelButtonText: '返回' })
    }
    catch {
      return
    }
    if (current !== generation) {
      return
    }
    clearAudit()
    const result = action === 'propose' ? await proposeRepairOrder(issue!) : await actOnRepairOrder(row!.id, action)
    if (current !== generation) {
      return
    }
    busy.value = false
    await load(false, result.data?.id)
  }
  catch {
    if (current === generation) {
      failed.value = true
      enabled.value = false
    }
  }
  finally {
    if (current === generation) {
      busy.value = false
    }
  }
}

watch(() => [props.archiveId, props.issueId], () => { clear(); void load() }, { immediate: true })
watch(() => useUserStore().token, clear, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<template>
  <MrrSectionCard title="关联修复工单" description="审批、执行与审计分离；仅处理唯一确定的关联。">
    <template #actions>
      <el-button :loading="busy" @click="load()">
        刷新工单
      </el-button>
      <el-button v-if="canPropose" type="primary" :disabled="busy" @click="change('propose')">
        {{ recurrence ? '重新提交工单' : '提交工单' }}
      </el-button>
    </template>
    <AppError v-if="failed" message="工单读取或操作失败，请刷新核对实际状态" @retry="load()" />
    <el-alert v-if="!enabled" title="修复写入不可用，现有工单仅供查看" type="info" :closable="false" />
    <el-table v-if="orders.length" :data="orders" row-key="id" highlight-current-row @row-click="select">
      <el-table-column v-bind="tableSequenceColumn()" />
      <el-table-column prop="id" label="工单" width="90" />
      <el-table-column label="关联记录" min-width="140">
        <template #default="{ row }">
          {{ entityLabel(row as RepairOrder) }}
        </template>
      </el-table-column>
      <el-table-column label="前序工单" width="105">
        <template #default="{ row }">
          {{ row.reopenedFrom ? `#${row.reopenedFrom}` : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="targetArchiveId" label="目标主档" min-width="120" />
      <el-table-column label="状态" width="110" class-name="mrr-table-status-column">
        <template #default="{ row }">
          <el-tag effect="plain">
            {{ labels[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="220">
        <template #default="{ row }">
          <el-button text type="primary" :disabled="busy" @click.stop="select(row as RepairOrder)">
            审计
          </el-button>
          <el-button v-for="action in actions(row as RepairOrder)" :key="action" text :type="action === 'apply' || action === 'compensate' ? 'warning' : 'primary'" :disabled="busy" @click.stop="change(action, row as RepairOrder)">
            {{ actionLabels[action] }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <AppEmpty v-else-if="!busy && !failed" description="暂无修复工单" />
    <el-button v-if="more" :disabled="busy" @click="load(true)">
      加载更多工单
    </el-button>
    <section v-if="selected" class="repair-audit" aria-label="工单事务审计" :aria-busy="auditBusy">
      <div class="repair-audit-heading">
        <h3>工单 #{{ selected.id }} · 事务审计</h3>
        <el-tag effect="plain">
          {{ labels[selected.status] || selected.status }}
        </el-tag>
      </div>
      <p>{{ entityLabel(selected) }} · 目标主档 #{{ selected.targetArchiveId }}</p>
      <p v-if="selected.reopenedFrom">
        前序工单 #{{ selected.reopenedFrom }} · 新工单独立审批
      </p>
      <p>提交人 #{{ selected.proposedBy }} · 审批人 {{ selected.approvedBy ? `#${selected.approvedBy}` : '未审批' }}</p>
      <section v-if="latestRelationEvent" class="repair-evidence" aria-label="最近已加载关联证据">
        <div class="repair-audit-heading">
          <strong>{{ evidenceLabel(latestRelationEvent) }}</strong>
          <span class="repair-audit-note">{{ formatDateTime(latestRelationEvent.createdAt) }}</span>
        </div>
        <div class="repair-evidence-grid">
          <div><span>事务前 / 观察前</span><strong>{{ archiveLabel(latestRelationEvent.beforeArchiveId) }}</strong></div>
          <div><span>事务后 / 观察后</span><strong>{{ archiveLabel(latestRelationEvent.afterArchiveId) }}</strong></div>
          <div><span>工单目标</span><strong>{{ archiveLabel(selected.targetArchiveId) }}</strong></div>
        </div>
        <p class="repair-audit-note">
          仅最近已加载的关联证据，不代表当前数据库状态{{ auditMore || auditFailed || auditBusy ? '；审计尚未完整读取' : '' }}。
        </p>
      </section>
      <p v-else-if="events.length" class="repair-audit-note">
        已加载流程事件未包含关联观察，不能判断记录当前关联。
      </p>
      <el-timeline v-if="events.length">
        <el-timeline-item v-for="event in events" :key="event.id" :timestamp="formatDateTime(event.createdAt)">
          {{ labels[event.action] || event.action }} · 操作者 #{{ event.actorId }}
          <div v-if="['APPLIED', 'VERIFIED', 'COMPENSATED'].includes(event.action)" class="repair-audit-note">
            {{ archiveLabel(event.beforeArchiveId) }} → {{ archiveLabel(event.afterArchiveId) }}
          </div>
          <div v-else class="repair-audit-note">
            流程事件，不表示实际关联变化
          </div>
        </el-timeline-item>
      </el-timeline>
      <p v-if="auditBusy" role="status" class="repair-audit-note">
        正在读取审计记录…
      </p>
      <div v-else-if="auditFailed" role="status" class="repair-audit-heading">
        <span>审计记录读取失败{{ events.length ? '，已加载记录保留' : '' }}。</span>
        <el-button size="small" :disabled="busy" @click="retryAudit">
          重试审计
        </el-button>
      </div>
      <AppEmpty v-else-if="!events.length" size="compact" description="暂无审计记录" />
      <div v-if="events.length" class="repair-audit-heading">
        <span class="repair-audit-note">已加载 {{ events.length }} 条审计记录</span>
        <el-button v-if="auditMore && !auditFailed" size="small" :disabled="busy" :loading="auditBusy" @click="moreAudit">
          加载更多审计
        </el-button>
      </div>
    </section>
  </MrrSectionCard>
</template>

<style scoped>
.repair-audit {
  display: grid;
  gap: var(--mrr-space-3);
  padding-top: var(--mrr-space-4);
  border-top: 1px solid var(--mrr-border);
}

.repair-audit-heading {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
}

.repair-evidence {
  display: grid;
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-3);
  background: var(--surface-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.repair-evidence-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 120px), 1fr));
  gap: var(--mrr-space-3);
}

.repair-evidence-grid > div {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
  overflow-wrap: anywhere;
}

.repair-evidence-grid span {
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.repair-audit-note {
  font-size: var(--mrr-font-size-xs);
  color: var(--el-text-color-secondary);
}

.repair-audit h3,
.repair-audit p {
  margin: 0;
}
</style>

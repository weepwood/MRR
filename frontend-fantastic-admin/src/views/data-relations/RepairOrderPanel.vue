<script setup lang="ts">
import type { RepairAction, RepairEvent, RepairOrder } from '@/api/modules/repair-orders'
import { ElMessageBox } from 'element-plus'
import { actOnRepairOrder, getRepairEvents, listRepairOrders, proposeRepairOrder } from '@/api/modules/repair-orders'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'

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
const recurrence = computed(() => orders.value.find(row => row.status === 'VERIFIED' && row.issueId !== props.issueId))
const canPropose = computed(() => enabled.value && props.deterministic && props.issueId && !orders.value.some(row =>
  !['CANCELLED', 'COMPENSATED', 'SUPERSEDED'].includes(row.status) && row.id !== recurrence.value?.id))

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
  events.value = []
  selected.value = null
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
    selected.value = null
    events.value = []
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
      await readEvents(row, current)
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

async function readEvents(row: RepairOrder, current: number) {
  selected.value = row
  events.value = []
  const { data } = await getRepairEvents(row.id)
  if (current === generation && selected.value?.id === row.id) {
    events.value = data ?? []
  }
}

async function select(row: RepairOrder) {
  if (busy.value) {
    return
  }
  const current = ++generation
  busy.value = true
  failed.value = false
  try {
    await readEvents(row, current)
  }
  catch {
    if (current === generation) {
      failed.value = true
    }
  }
  finally {
    if (current === generation) {
      busy.value = false
    }
  }
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
      ? (recurrence.value ? `为异常 #${issue} 重新提交工单，必须是复验后新成功检查发现的同一影像缺失关联；保留旧工单与审计，新工单独立审批，不修改病案。` : `为异常 #${issue} 提交唯一关联候选，提交本身不会修改病案。`)
      : `工单 #${row?.id}：影像 #${row?.scanId}，目标主档 #${row?.targetArchiveId}。后端将重新检查身份、状态和关联事实。`
    try {
      await ElMessageBox.confirm(message, label, { type: action === 'apply' || action === 'compensate' ? 'warning' : 'info', confirmButtonText: label, cancelButtonText: '返回' })
    }
    catch {
      return
    }
    if (current !== generation) {
      return
    }
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
      <el-table-column prop="id" label="工单" width="90" />
      <el-table-column prop="scanId" label="影像" width="100" />
      <el-table-column label="前序工单" width="105">
        <template #default="{ row }">
          {{ row.reopenedFrom ? `#${row.reopenedFrom}` : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="targetArchiveId" label="目标主档" min-width="120" />
      <el-table-column label="状态" width="110">
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
    <section v-if="selected" class="repair-audit">
      <h3>工单 #{{ selected.id }} · 事务审计</h3>
      <p v-if="selected.reopenedFrom">
        前序工单 #{{ selected.reopenedFrom }} · 新工单独立审批
      </p>
      <p>提交人 #{{ selected.proposedBy }} · 审批人 {{ selected.approvedBy ? `#${selected.approvedBy}` : '未审批' }}</p>
      <el-timeline>
        <el-timeline-item v-for="event in events" :key="event.id" :timestamp="formatDateTime(event.createdAt)">
          {{ labels[event.action] || event.action }} · 操作者 #{{ event.actorId }}
          <div>{{ event.beforeArchiveId ?? '未关联' }} → {{ event.afterArchiveId ?? '未关联' }}</div>
        </el-timeline-item>
      </el-timeline>
      <AppEmpty v-if="!events.length && !busy" description="暂无审计记录" />
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

.repair-audit h3,
.repair-audit p {
  margin: 0;
}
</style>

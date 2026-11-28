<script setup lang="ts">
import type { StorageCircuitHistory } from '@/api/modules/operations'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getStorageCircuitHistory } from '@/api/modules/operations'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'
import { tableSequenceColumn } from '@/utils/table-sequence'

const source = defineModel<string>('source', { default: '' })
const eventType = ref('')
const hours = ref<24 | 168>(24)
const data = ref<StorageCircuitHistory>()
const busy = ref(false)
const failed = ref(false)
const allowed = computed(() => hasPermission('system:read'))
const labels = { OPENED: '进入熔断', PROBE_STARTED: '开始半开探针', METADATA_ONLY: '仅元数据成功', RECOVERED: '节点恢复' }
const filteredEvents = computed(() => (data.value?.events || []).filter(event =>
  (!source.value || event.sourceKind === source.value) && (!eventType.value || event.eventType === eventType.value)))
function resetFilters() {
  source.value = ''
  eventType.value = ''
}
let generation = 0
let controller: AbortController | undefined

function clear() {
  generation++
  controller?.abort()
  controller = undefined
  data.value = undefined
  busy.value = false
  failed.value = false
}

async function load() {
  clear()
  if (!allowed.value) { return }
  const current = generation
  controller = new AbortController()
  busy.value = true
  try {
    const next = await getStorageCircuitHistory(hours.value, controller.signal)
    if (current !== generation) { return }
    if (next.hours !== hours.value || next.events.length > 100) { throw new Error('Invalid circuit history') }
    data.value = next
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { busy.value = false }
  }
}

watch([hours, allowed], () => void load(), { immediate: true, flush: 'sync' })
watch(() => useUserStore().token, () => { clear(); resetFilters() }, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<template>
  <MrrSectionCard v-if="allowed" title="持久存储熔断事件" description="已采集的真实熔断状态机事件，保留 7 天；进程退出、缓冲区覆盖或数据库异常可能导致缺失，不代表完整故障审计。">
    <div class="circuit-history-toolbar">
      <el-radio-group v-model="hours" aria-label="熔断事件时间范围">
        <el-radio-button :value="24">
          近 24 小时
        </el-radio-button>
        <el-radio-button :value="168">
          近 7 天
        </el-radio-button>
      </el-radio-group>
      <el-button :loading="busy" @click="load">
        刷新事件
      </el-button>
    </div>
    <AppError v-if="failed" message="熔断历史读取失败，请重试" @retry="load" />
    <el-skeleton v-else-if="busy" :rows="3" animated />
    <AppEmpty v-else-if="data && !data.enabled" size="compact" description="持久熔断事件采集尚未启用" />
    <AppEmpty v-else-if="!data?.events.length" size="compact" description="当前范围暂无已采集事件，不代表未发生故障" />
    <template v-else>
      <MrrFilterBar embedded :focus-ring="false">
        <el-select v-model="source" class="mrr-single-select" aria-label="存储历史来源">
          <el-option label="全部来源" value="" />
          <el-option v-for="kind in ['NAS', 'HTTP', 'NGINX', 'OSS']" :key="kind" :label="kind" :value="kind" />
        </el-select>
        <el-select v-model="eventType" class="mrr-single-select" aria-label="存储历史事件类型">
          <el-option label="全部事件" value="" />
          <el-option v-for="(label, value) in labels" :key="value" :label="label" :value="value" />
        </el-select>
        <template #actions>
          <MrrFilterActionButton action="reset" feedback="request" :disabled="!source && !eventType" aria-label="重置存储事件筛选" @click="resetFilters" />
        </template>
      </MrrFilterBar>
      <p class="circuit-history-note" role="status">
        匹配 {{ filteredEvents.length }} / 已加载 {{ data.events.length }} 条事件。
      </p>
      <p class="circuit-history-note">
        {{ data.truncated ? '仅展示最近 100 条事件；缩小时间范围查看更多细节。' : `已采集 ${data.events.length} 条事件。` }}
        节点序号为采集时的显示位置，运行实例标识不用于跨重启匹配节点。
      </p>
      <el-table :data="filteredEvents" empty-text="没有符合筛选条件的已加载事件" :row-key="row => `${row.circuitId}-${row.sequence}`" max-height="420">
        <el-table-column v-bind="tableSequenceColumn()" />
        <el-table-column label="发生时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column label="存储节点" min-width="120">
          <template #default="{ row }">
            {{ row.sourceKind }} · #{{ row.nodeOrdinal }}
          </template>
        </el-table-column>
        <el-table-column label="事件" min-width="145" class-name="mrr-table-status-column">
          <template #default="{ row }">
            <el-tag :type="row.eventType === 'OPENED' ? 'danger' : row.eventType === 'RECOVERED' ? 'success' : 'warning'">
              {{ labels[row.eventType as keyof typeof labels] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="运行实例 / 序号" min-width="150">
          <template #default="{ row }">
            {{ row.circuitId.slice(0, 8) }} / {{ row.sequence }}
          </template>
        </el-table-column>
        <el-table-column label="采集时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.collectedAt) }}
          </template>
        </el-table-column>
      </el-table>
    </template>
  </MrrSectionCard>
</template>

<style scoped>
.circuit-history-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
}

.circuit-history-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

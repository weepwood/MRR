<script setup lang="ts">
import type { ManifestIssue, ManifestObservation } from '@/api/modules/archive-manifest'
import { cancelManifest, getLatestManifest, getManifestIssues, startManifest } from '@/api/modules/archive-manifest'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'

const props = defineProps<{ archiveId: number }>()
const latest = ref<ManifestObservation | null>(null)
const issues = ref<ManifestIssue[]>([])
const enabled = ref(false)
const busy = ref(false)
const failed = ref(false)
const more = ref(false)
let generation = 0
const active = computed(() => ['PENDING', 'RUNNING'].includes(latest.value?.status ?? ''))
const canManage = computed(() => hasPermission('system:manage') && hasPermission('record:read'))
const labels: Record<string, string> = { PENDING: '等待校验', RUNNING: '校验中', COMPLETED: '本次观察结束', PARTIAL: '达到处理上限', FAILED: '任务失败', CANCELLED: '已取消' }

function clear() {
  generation++
  latest.value = null
  issues.value = []
  enabled.value = false
  busy.value = false
  failed.value = false
  more.value = false
}

async function load(mode: 'refresh' | 'start' | 'cancel' | 'more' = 'refresh') {
  if (busy.value) {
    return
  }
  const current = ++generation
  const archiveId = props.archiveId
  const currentVersion = latest.value?.version
  busy.value = true
  failed.value = false
  try {
    if (mode === 'start') {
      await startManifest(archiveId)
    }
    if (mode === 'cancel' && currentVersion != null) {
      await cancelManifest(currentVersion)
    }
    if (current !== generation) {
      return
    }
    const response = await getLatestManifest(archiveId)
    if (current !== generation) {
      return
    }
    if (!response.data || (response.data.latest && response.data.latest.archiveId !== archiveId)) {
      throw new Error('Manifest identity mismatch')
    }
    enabled.value = response.data.enabled
    const observation = response.data.latest
    const append = mode === 'more' && observation?.version === currentVersion
    const previous = append ? issues.value : []
    latest.value = observation
    issues.value = previous
    more.value = false
    if (observation && observation.anomalies > 0) {
      const page = await getManifestIssues(observation.version, previous.at(-1)?.cursor ?? 0)
      if (current !== generation) {
        return
      }
      issues.value = [...previous, ...(page.data ?? [])]
      more.value = (page.data?.length ?? 0) === 100 && issues.value.length < observation.anomalies
    }
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

watch(() => props.archiveId, () => { clear(); void load() }, { immediate: true })
watch(() => useUserStore().token, clear, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<template>
  <MrrSectionCard title="影像文件完整性" description="有界后台观察；不修改影像或病案关联。">
    <template #actions>
      <el-button :loading="busy" @click="load()">
        刷新结果
      </el-button>
      <el-button v-if="canManage && enabled && !active" type="primary" :disabled="busy" @click="load('start')">
        开始校验
      </el-button>
      <el-button v-if="canManage && active" :disabled="busy" @click="load('cancel')">
        取消任务
      </el-button>
    </template>
    <AppError v-if="failed" message="完整性观察读取或操作失败，请刷新核对实际状态" @retry="load()" />
    <template v-if="latest">
      <div class="mrr-stat-strip manifest-stats">
        <div v-for="item in [{ label: '已处理', value: latest.processed }, { label: '已验证', value: latest.verified }, { label: '异常', value: latest.anomalies }]" :key="item.label" class="mrr-stat-tile">
          <span class="mrr-stat-tile__label">{{ item.label }}</span>
          <strong class="mrr-stat-tile__value">{{ item.value }}</strong>
        </div>
      </div>
      <p>版本 #{{ latest.version }} · {{ labels[latest.status] || latest.status }} · {{ formatDateTime(latest.updatedAt) }}</p>
      <el-table v-if="issues.length" :data="issues" max-height="320">
        <el-table-column prop="scanId" label="影像 ID" width="120" />
        <el-table-column prop="status" label="异常类型" min-width="180" />
        <el-table-column prop="bytesRead" label="读取字节" width="120" />
        <el-table-column prop="checkedAt" label="校验时间" min-width="200" />
      </el-table>
      <el-button v-if="more" :disabled="busy" @click="load('more')">
        加载更多异常
      </el-button>
      <p class="manifest-note">
        本次观察结束不代表整个病案完整；未纳入记录与校验期间的变化须另行核对。
      </p>
    </template>
    <AppEmpty v-else-if="!busy && !failed" description="暂无完整性观察" />
    <el-alert v-if="!enabled" title="后台校验功能尚未启用" type="info" :closable="false" />
  </MrrSectionCard>
</template>

<style scoped>
.manifest-stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.manifest-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<script setup lang="ts">
import type { ManifestIssue, ManifestObservation } from '@/api/modules/archive-manifest'
import { cancelManifest, getLatestManifest, getManifestHistory, getManifestIssues, getManifestVersion, startManifest } from '@/api/modules/archive-manifest'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrScanContentDialog from '@/components/MrrScanContentDialog/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'

const props = defineProps<{ archiveId: number }>()
const scanPreview = ref<number | null>(null)
const latest = ref<ManifestObservation | null>(null)
const newest = ref<ManifestObservation | null>(null)
const history = ref<ManifestObservation[]>([])
const selectedVersion = ref<number>()
const historyMore = ref(false)
const issues = ref<ManifestIssue[]>([])
const enabled = ref(false)
const busy = ref(false)
const failed = ref(false)
const more = ref(false)
let generation = 0
const viewingLatest = computed(() => typeof selectedVersion.value !== 'number' || selectedVersion.value === newest.value?.version)
const active = computed(() => ['PENDING', 'RUNNING'].includes(newest.value?.status ?? ''))
const canManage = computed(() => hasPermission('system:manage') && hasPermission('record:read'))
const labels: Record<string, string> = { PENDING: '等待校验', RUNNING: '校验中', COMPLETED: '本次观察结束', PARTIAL: '达到处理上限', FAILED: '任务失败', CANCELLED: '已取消' }

function clear() {
  generation++
  scanPreview.value = null
  latest.value = null
  newest.value = null
  history.value = []
  selectedVersion.value = undefined
  historyMore.value = false
  issues.value = []
  enabled.value = false
  busy.value = false
  failed.value = false
  more.value = false
}

async function load(mode: 'refresh' | 'start' | 'cancel' | 'more' | 'history' | 'version' = 'refresh') {
  if (busy.value) {
    return
  }
  const current = ++generation
  const archiveId = props.archiveId
  const currentVersion = latest.value?.version
  const requestedVersion = typeof selectedVersion.value === 'number' ? selectedVersion.value : undefined
  busy.value = true
  failed.value = false
  try {
    if (mode === 'start') {
      await startManifest(archiveId)
    }
    if (mode === 'cancel' && newest.value?.version != null) {
      await cancelManifest(newest.value.version)
    }
    if (current !== generation) {
      return
    }
    if (mode === 'start' || mode === 'cancel') {
      selectedVersion.value = undefined
    }
    const response = await getLatestManifest(archiveId)
    if (current !== generation) {
      return
    }
    if (!response.data || (response.data.latest && response.data.latest.archiveId !== archiveId)) {
      throw new Error('Manifest identity mismatch')
    }
    enabled.value = response.data.enabled
    newest.value = response.data.latest
    if (mode !== 'more' && mode !== 'version') {
      const appendHistory = mode === 'history'
      const page = await getManifestHistory(archiveId, appendHistory ? history.value.at(-1)?.version : undefined)
      if (current !== generation) {
        return
      }
      if (page.data?.some(row => row.archiveId !== archiveId)) {
        throw new Error('History identity mismatch')
      }
      history.value = [...(appendHistory ? history.value : []), ...(page.data ?? [])]
      historyMore.value = (page.data?.length ?? 0) === 50
    }
    let observation = response.data.latest
    if (mode !== 'start' && mode !== 'cancel' && requestedVersion != null && requestedVersion !== observation?.version) {
      const historic = await getManifestVersion(requestedVersion)
      if (current !== generation) {
        return
      }
      if (!historic.data || historic.data.archiveId !== archiveId || historic.data.version !== requestedVersion) {
        throw new Error('History version mismatch')
      }
      observation = historic.data
    }
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
      <el-button v-if="canManage && enabled && !active && viewingLatest" type="primary" :disabled="busy" @click="load('start')">
        开始校验
      </el-button>
      <el-button v-if="canManage && active && viewingLatest" :disabled="busy" @click="load('cancel')">
        取消任务
      </el-button>
    </template>
    <AppError v-if="failed" message="完整性观察读取或操作失败，请刷新核对实际状态" @retry="load()" />
    <div v-if="history.length" class="manifest-history">
      <el-select v-model="selectedVersion" clearable placeholder="最新版本" aria-label="完整性历史版本" :disabled="busy" @change="load('version')">
        <el-option v-for="version in history" :key="version.version" :label="`#${version.version} · ${labels[version.status] || version.status} · ${formatDateTime(version.createdAt)}`" :value="version.version" />
      </el-select>
      <el-button v-if="historyMore" :disabled="busy" @click="load('history')">
        更多版本
      </el-button>
    </div>
    <template v-if="latest">
      <div class="mrr-stat-strip manifest-stats">
        <div v-for="item in [{ label: '已处理', value: latest.processed }, { label: '已验证', value: latest.verified }, { label: '异常', value: latest.anomalies }]" :key="item.label" class="mrr-stat-tile">
          <span class="mrr-stat-tile__label">{{ item.label }}</span>
          <strong class="mrr-stat-tile__value">{{ item.value }}</strong>
        </div>
      </div>
      <el-progress v-if="latest.snapshotCount != null && latest.snapshotCount > 0" :percentage="Math.min(100, Math.floor(latest.processed / latest.snapshotCount * 100))" />
      <p v-if="latest.snapshotCount != null">
        固定成员 {{ latest.processed }} / {{ latest.snapshotCount }} · 分段 {{ Math.min(Math.ceil(latest.snapshotCount / 5000), Math.floor(latest.processed / 5000) + 1) }} / {{ Math.ceil(latest.snapshotCount / 5000) }}
      </p>
      <p>版本 #{{ latest.version }} · {{ labels[latest.status] || latest.status }} · {{ formatDateTime(latest.updatedAt) }}</p>
      <el-table v-if="issues.length" :data="issues" max-height="320">
        <el-table-column prop="scanId" label="影像 ID" width="120" />
        <el-table-column prop="status" label="异常类型" min-width="180" />
        <el-table-column prop="bytesRead" label="读取字节" width="120" />
        <el-table-column prop="checkedAt" label="校验时间" min-width="200" />
        <el-table-column label="核对" width="95">
          <template #default="{ row }">
            <el-button link type="primary" @click="scanPreview = Number(row.scanId)">
              查看影像
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="more" :disabled="busy" @click="load('more')">
        加载更多异常
      </el-button>
      <p class="manifest-note">
        固定成员快照不等同于文件内容的瞬时快照；观察结束不代表当前病案全部完整。历史旧版本仍为有界观察。
      </p>
    </template>
    <AppEmpty v-else-if="!busy && !failed" description="暂无完整性观察" />
    <el-alert v-if="!enabled" title="后台校验功能尚未启用" type="info" :closable="false" />
    <MrrScanContentDialog v-if="scanPreview !== null" :scan-id="scanPreview" @close="scanPreview = null" />
  </MrrSectionCard>
</template>

<style scoped>
.manifest-stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.manifest-history {
  display: flex;
  gap: var(--mrr-space-2);
  margin-bottom: var(--mrr-space-3);
}

.manifest-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

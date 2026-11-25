<script setup lang="ts">
import type { ManifestComparison, ManifestIssue, ManifestObservation, ManifestReport } from '@/api/modules/archive-manifest'
import { ElMessageBox } from 'element-plus'
import { cancelManifest, getLatestManifest, getManifestComparison, getManifestHistory, getManifestIssues, getManifestReport, getManifestVersion, resumeManifest, startManifest } from '@/api/modules/archive-manifest'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrScanContentDialog from '@/components/MrrScanContentDialog/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { downloadBlob } from '@/utils/file-download'
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
const moreBusy = ref(false)
const moreFailed = ref(false)
const issueStatus = ref('')
const issueTypes = computed(() => [...new Set(issues.value.map(row => row.status).filter(Boolean))].sort())
const visibleIssues = computed(() => issueStatus.value ? issues.value.filter(row => row.status === issueStatus.value) : issues.value)

let generation = 0
let comparisonGeneration = 0
let reportGeneration = 0
const report = ref<ManifestReport | null>(null)
const reportBusy = ref(false)
const reportFailed = ref(false)

function resetReport() {
  reportGeneration++
  report.value = null
  reportBusy.value = false
  reportFailed.value = false
}

async function loadReport() {
  const archive = props.archiveId
  const version = latest.value?.version
  if (version == null || latest.value?.snapshotCount == null || busy.value || reportBusy.value) { return }
  const current = ++reportGeneration
  reportBusy.value = true
  reportFailed.value = false
  report.value = null
  try {
    const data = (await getManifestReport(archive, version)).data
    if (current !== reportGeneration) { return }
    if (!data || data.archiveId !== archive || data.version !== version || data.segments.length > 10) {
      throw new Error('Manifest report identity mismatch')
    }
    report.value = data
  }
  catch {
    if (current === reportGeneration) { reportFailed.value = true }
  }
  finally {
    if (current === reportGeneration) { reportBusy.value = false }
  }
}

function exportReport() {
  const data = report.value
  if (!data || data.archiveId !== props.archiveId || data.version !== latest.value?.version) { return }
  downloadBlob(new Blob([JSON.stringify({ reportVersion: 1, ...data, scope: 'FIXED_MEMBERSHIP_OBSERVATION', limitation: '仅为已持久化观察与异常汇总，不代表当前文件完整；未处理成员不视为通过。' }, null, 2)], { type: 'application/json;charset=utf-8' }), `manifest-report-${data.version}.json`)
}

const compareBase = ref<number>()
const comparison = ref<ManifestComparison | null>(null)
const comparing = ref(false)
const comparisonFailed = ref(false)
const compareOptions = computed(() => history.value.filter(row => row.version !== latest.value?.version && row.snapshotCount != null))
const digestLabels = { SAME: '完整摘要一致', DIFFERENT: '完整摘要不同', UNAVAILABLE: '完整摘要不可比较' }

function resetComparison() {
  comparisonGeneration++
  compareBase.value = undefined
  comparison.value = null
  comparing.value = false
  comparisonFailed.value = false
}

async function compareVersions() {
  const base = compareBase.value
  const target = latest.value?.version
  const archive = props.archiveId
  if (base == null || target == null || base === target || comparing.value || busy.value) { return }
  const current = ++comparisonGeneration
  comparing.value = true
  comparison.value = null
  comparisonFailed.value = false
  try {
    const result = (await getManifestComparison(archive, base, target)).data
    if (current !== comparisonGeneration) { return }
    if (!result || result.archiveId !== archive || result.baseVersion !== base || result.targetVersion !== target) {
      throw new Error('Manifest comparison identity mismatch')
    }
    comparison.value = result
  }
  catch {
    if (current === comparisonGeneration) { comparisonFailed.value = true }
  }
  finally {
    if (current === comparisonGeneration) { comparing.value = false }
  }
}

function exportComparison() {
  const row = comparison.value
  if (!row || row.archiveId !== props.archiveId || row.targetVersion !== latest.value?.version || row.baseVersion !== compareBase.value) { return }
  downloadBlob(new Blob([JSON.stringify({ ...row, scope: 'FIXED_MEMBERSHIP_SUMMARY', limitation: '固定成员摘要，不代表当前文件完整或单文件内容变更。' }, null, 2)], { type: 'application/json;charset=utf-8' }), `manifest-comparison-${row.baseVersion}-${row.targetVersion}.json`)
}

watch(compareBase, () => {
  comparisonGeneration++
  comparison.value = null
  comparing.value = false
  comparisonFailed.value = false
}, { flush: 'sync' })
const viewingLatest = computed(() => typeof selectedVersion.value !== 'number' || selectedVersion.value === newest.value?.version)
const active = computed(() => ['PENDING', 'RUNNING'].includes(newest.value?.status ?? ''))
const canResume = computed(() => {
  const row = newest.value
  return !!row && enabled.value && viewingLatest.value && row.status === 'FAILED'
    && row.snapshotCount != null && row.processed < row.snapshotCount && (row.recoveryCount ?? 0) < 3
})

const canManage = computed(() => hasPermission('system:manage') && hasPermission('record:read'))
const labels: Record<string, string> = { PENDING: '等待校验', RUNNING: '校验中', COMPLETED: '本次观察结束', PARTIAL: '达到处理上限', FAILED: '任务失败', CANCELLED: '已取消' }

function clear() {
  generation++
  resetComparison()
  resetReport()
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
  moreBusy.value = false
  moreFailed.value = false
  issueStatus.value = ''
}

async function loadMoreIssues() {
  const observation = latest.value
  if (!observation || !more.value || busy.value || moreBusy.value) { return }
  const current = generation
  const archive = props.archiveId
  const after = issues.value.at(-1)?.cursor ?? 0
  moreBusy.value = true
  moreFailed.value = false
  try {
    const data = (await getManifestIssues(observation.version, after)).data
    if (current !== generation || props.archiveId !== archive || latest.value?.version !== observation.version) { return }
    if (!Array.isArray(data) || data.length > 100) { throw new Error('Invalid anomaly page') }
    let cursor = after
    for (const row of data) {
      if (!Number.isSafeInteger(row.cursor) || row.cursor <= cursor) { throw new Error('Invalid anomaly cursor') }
      cursor = row.cursor
    }
    issues.value = [...issues.value, ...data]
    more.value = data.length === 100 && issues.value.length < observation.anomalies
  }
  catch {
    if (current === generation) { moreFailed.value = true }
  }
  finally {
    if (current === generation) { moreBusy.value = false }
  }
}

async function load(mode: 'refresh' | 'start' | 'cancel' | 'resume' | 'history' | 'version' = 'refresh') {
  if (busy.value) {
    return
  }
  resetComparison()
  resetReport()
  moreBusy.value = false
  moreFailed.value = false
  issueStatus.value = ''
  scanPreview.value = null
  const current = ++generation
  const archiveId = props.archiveId
  const requestedVersion = typeof selectedVersion.value === 'number' ? selectedVersion.value : undefined
  busy.value = true
  failed.value = false
  try {
    if (mode === 'start') {
      await startManifest(archiveId)
    }
    if (mode === 'resume' && canResume.value && newest.value) {
      const version = newest.value.version
      try {
        await ElMessageBox.confirm(`继续版本 #${version} 的固定成员校验？保留已处理结果，从持久游标恢复；最多恢复三次，不修改影像或病案事实。`, '恢复失败任务', { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '返回' })
      }
      catch { return }
      if (current !== generation || archiveId !== props.archiveId) { return }
      await resumeManifest(archiveId, version)
    }
    if (mode === 'cancel' && newest.value?.version != null) {
      await cancelManifest(newest.value.version)
    }
    if (current !== generation) {
      return
    }
    if (mode === 'start' || mode === 'cancel' || mode === 'resume') {
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
    if (mode !== 'version') {
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
    if (mode !== 'start' && mode !== 'cancel' && mode !== 'resume' && requestedVersion != null && requestedVersion !== observation?.version) {
      const historic = await getManifestVersion(requestedVersion)
      if (current !== generation) {
        return
      }
      if (!historic.data || historic.data.archiveId !== archiveId || historic.data.version !== requestedVersion) {
        throw new Error('History version mismatch')
      }
      observation = historic.data
    }
    latest.value = observation
    issues.value = []
    more.value = false
    if (observation && observation.anomalies > 0) {
      const page = await getManifestIssues(observation.version, 0)
      if (current !== generation) {
        return
      }
      issues.value = page.data ?? []
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
  <MrrSectionCard body-padding="compact" title="影像文件完整性" description="有界后台观察；不修改影像或病案关联。">
    <template #actions>
      <el-button :loading="busy" @click="load()">
        刷新结果
      </el-button>
      <el-button v-if="canManage && enabled && !active && viewingLatest" type="primary" :disabled="busy" @click="load('start')">
        开始校验
      </el-button>
      <el-button v-if="canManage && canResume" :disabled="busy" @click="load('resume')">
        恢复失败任务
      </el-button>
      <el-button v-if="canManage && active && viewingLatest" :disabled="busy" @click="load('cancel')">
        取消任务
      </el-button>
    </template>
    <AppError v-if="failed" message="完整性观察读取或操作失败，请刷新核对实际状态" @retry="load()" />
    <div v-if="history.length" class="manifest-history">
      <span class="manifest-note">历史版本</span>
      <el-select v-model="selectedVersion" clearable placeholder="最新版本" aria-label="完整性历史版本" :disabled="busy" @change="load('version')">
        <el-option v-for="version in history" :key="version.version" :label="`#${version.version} · ${labels[version.status] || version.status} · ${formatDateTime(version.createdAt)}`" :value="version.version" />
      </el-select>
      <el-button v-if="historyMore" :disabled="busy" @click="load('history')">
        更多版本
      </el-button>
    </div>
    <template v-if="latest">
      <div class="mrr-stat-strip manifest-stats">
        <div v-for="item in [{ label: '已处理', value: latest.processed }, { label: '已验证', value: latest.verified }, { label: '异常', value: latest.anomalies }, ...(latest.snapshotCount != null ? [{ label: '未处理', value: Math.max(0, latest.snapshotCount - latest.processed) }] : [])]" :key="item.label" class="mrr-stat-tile">
          <span class="mrr-stat-tile__label">{{ item.label }}</span>
          <strong class="mrr-stat-tile__value">{{ item.value }}</strong>
        </div>
      </div>
      <p v-if="latest.snapshotCount != null && latest.snapshotCount > 0" class="manifest-note manifest-progress-label">
        处理进度（不等于校验通过率）
      </p>
      <el-progress v-if="latest.snapshotCount != null && latest.snapshotCount > 0" :percentage="Math.min(100, Math.floor(latest.processed / latest.snapshotCount * 100))" />
      <p v-if="latest.snapshotCount != null">
        固定成员 {{ latest.processed }} / {{ latest.snapshotCount }}
        <span v-if="latest.snapshotCount > 0"> · 分段 {{ Math.min(Math.ceil(latest.snapshotCount / 5000), Math.floor(latest.processed / 5000) + 1) }} / {{ Math.ceil(latest.snapshotCount / 5000) }}</span>
      </p>
      <p>版本 #{{ latest.version }} · {{ labels[latest.status] || latest.status }} · {{ formatDateTime(latest.updatedAt) }}</p>
      <p v-if="latest.recoveryCount || latest.status === 'FAILED'" class="manifest-note">
        已恢复 {{ latest.recoveryCount ?? 0 }} / 3 次；恢复保留原成员与处理结果，已取消任务不允许恢复。
      </p>
      <section v-if="latest.snapshotCount != null && compareOptions.length" class="manifest-compare" aria-label="版本比较">
        <h3 class="manifest-subtitle">
          版本比较
        </h3>
        <div class="manifest-history">
          <el-select v-model="compareBase" class="manifest-base-select mrr-single-select" aria-label="完整性比较基准版本" placeholder="选择基准版本" clearable :value-on-clear="() => undefined" :disabled="busy || comparing">
            <el-option v-for="version in compareOptions" :key="version.version" :value="version.version" :label="`#${version.version} · ${formatDateTime(version.createdAt)}`" />
          </el-select>
          <el-button :disabled="busy || compareBase == null" :loading="comparing" @click="compareVersions">
            比较版本
          </el-button>
        </div>
        <AppError v-if="comparisonFailed" message="比较失败，版本可能已清理或不具备固定成员快照" @retry="compareVersions" />
        <template v-if="comparison">
          <p>#{{ comparison.baseVersion }} → #{{ comparison.targetVersion }} · {{ digestLabels[comparison.digestComparison] }}</p>
          <div class="mrr-stat-strip manifest-stats">
            <div v-for="item in [{ label: '新增成员', value: comparison.added }, { label: '移除成员', value: comparison.removed }, { label: '指纹变化', value: comparison.changed }, { label: '未变化', value: comparison.unchanged }]" :key="item.label" class="mrr-stat-tile">
              <span class="mrr-stat-tile__label">{{ item.label }}</span>
              <strong class="mrr-stat-tile__value">{{ item.value }}</strong>
            </div>
          </div>
          <p class="manifest-note">
            指纹变化为固定成员记录的变化，不等同于单文件内容发生变化。
          </p>
          <el-button @click="exportComparison">
            下载比较摘要 JSON
          </el-button>
        </template>
      </section>
      <section v-if="latest.snapshotCount != null" class="manifest-compare" aria-label="完整性报告">
        <h3 class="manifest-subtitle">
          完整性报告
        </h3>
        <el-button :disabled="busy" :loading="reportBusy" @click="loadReport">
          查看完整性报告
        </el-button>
        <AppError v-if="reportFailed" message="报告读取失败，固定成员可能已清理或观察不一致" @retry="loadReport" />
        <template v-if="report">
          <p>报告版本 #{{ report.version }} · 持久检查时间 {{ formatDateTime(report.updatedAt) }}</p>
          <p>计划 {{ report.snapshotCount }} · 已处理 {{ report.processed }} · 未处理 {{ report.snapshotCount - report.processed }} · 已验证 {{ report.verified }} · 异常 {{ report.anomalies }}</p>
          <el-table v-if="report.segments.length" :data="report.segments" size="small">
            <el-table-column prop="number" label="分段" width="72" />
            <el-table-column label="固定序号" min-width="120">
              <template #default="{ row }">
                {{ row.firstOrdinal }}–{{ row.lastOrdinal }}
              </template>
            </el-table-column>
            <el-table-column prop="planned" label="计划" width="88" />
            <el-table-column prop="processed" label="已处理" width="88" />
            <el-table-column prop="verified" label="已验证" width="88" />
            <el-table-column prop="anomalies" label="异常" width="88" />
          </el-table>
          <p v-if="Object.keys(report.anomalyCounts).length" class="manifest-note">
            异常全量汇总：{{ Object.entries(report.anomalyCounts).map(([kind, count]) => `${kind} ${count}`).join(' · ') }}
          </p>
          <p class="manifest-note">
            固定成员报告不是当前文件健康证明；未处理项不视为通过。
          </p>
          <el-button @click="exportReport">
            下载完整性报告 JSON
          </el-button>
        </template>
      </section>
      <section v-if="issues.length" class="manifest-compare" aria-label="已加载异常">
        <div class="manifest-issue-toolbar">
          <h3 class="manifest-subtitle">
            异常明细
          </h3>
          <span class="manifest-note">已加载 {{ issues.length }} / {{ latest.anomalies }}</span>
          <el-select v-model="issueStatus" class="manifest-base-select mrr-single-select" aria-label="筛选已加载异常" placeholder="全部已加载异常" :empty-values="[null, undefined]">
            <el-option value="" label="全部已加载异常" />
            <el-option v-for="status in issueTypes" :key="status" :value="status" :label="status" />
          </el-select>
        </div>
        <p v-if="issueStatus" class="manifest-note">
          仅筛选已加载的 {{ issues.length }} 条异常，当前 {{ visibleIssues.length }} 条；不是全量筛选。
        </p>
        <el-table :data="visibleIssues" max-height="320" empty-text="已加载异常中无匹配记录">
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
        <div v-if="moreFailed" class="manifest-issue-toolbar" role="status">
          <span>更多异常读取失败，已加载结果保留。</span>
          <el-button size="small" :disabled="busy" :loading="moreBusy" @click="loadMoreIssues">
            重试加载更多
          </el-button>
        </div>
        <el-button v-else-if="more" :disabled="busy" :loading="moreBusy" @click="loadMoreIssues">
          加载更多异常
        </el-button>
      </section>
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
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 100px), 1fr));
}

.manifest-history {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  margin-bottom: var(--mrr-space-3);
}

.manifest-base-select {
  flex: 1 1 180px;
  min-width: 0;
  max-width: 100%;
}

.manifest-compare {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
  padding-block: var(--mrr-space-3);
  border-top: 1px solid var(--el-border-color-light);
}

.manifest-compare > :deep(.el-button) {
  justify-self: start;
  margin-left: 0;
}

.manifest-subtitle {
  margin: 0;
  font-size: var(--mrr-font-size-sm);
  font-weight: 600;
}

.manifest-issue-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.manifest-issue-toolbar .manifest-base-select {
  flex: 0 1 220px;
}

.manifest-progress-label {
  margin-block: var(--mrr-space-2) var(--mrr-space-1);
}

.manifest-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<script setup lang="ts">
import type { InputInstance } from 'element-plus'
import type {
  ArchiveRelationDetail,
  ArchiveSearchResult,
  ComparisonStatus,
  DataQualityIssue,
  DataQualitySummary,
  DataRelationOverview,
  QualityCheck,
  RelationCoverage,
  RepairPreview,
} from '@/api/modules/data-relations'
/* eslint-disable antfu/if-newline, curly */
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  getArchiveRelationDetail,
  getDataQualityIssues,
  getDataQualityRepairPreview,
  getDataQualitySummary,
  getDataRelationOverview,
  runDataQualityChecks,
  searchDataRelationArchives,
} from '@/api/modules/data-relations'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrResizeHandle from '@/components/MrrResizeHandle/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import MrrStatusTag from '@/components/MrrStatusTag/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'
import { isSessionAccessDenied } from '@/utils/session-access-denied'
import { tableSequenceColumn } from '@/utils/table-sequence'
import ArchiveManifestCard from './ArchiveManifestCard.vue'
import ArchiveScanInspector from './components/ArchiveScanInspector.vue'
import QualityCaseReview from './components/QualityCaseReview.vue'
import RepairOrderPanel from './RepairOrderPanel.vue'

defineOptions({ name: 'DataRelationWorkbenchPage' })

const props = withDefaults(defineProps<{ archiveOnly?: boolean }>(), { archiveOnly: false })

const canReadQualitySamples = computed(() => hasPermission('record:read') && hasPermission('system:read'))
const canRepair = computed(() => hasPermission('system:manage') && hasPermission('record:manage'))
const overviewLoading = ref(false)
const searchLoading = ref(false)
const detailLoading = ref(false)
const checking = ref(false)
const previewLoading = ref(false)
const activeTab = ref(props.archiveOnly ? 'archive' : 'overview')
const overview = ref<DataRelationOverview | null>(null)
const qualitySummary = ref<DataQualitySummary | null>(null)
const issues = ref<DataQualityIssue[]>([])
const qualityLoading = ref(false)
const qualityFailed = ref(false)
const qualityLoaded = ref(false)
const sampleRegion = ref<HTMLElement>()

const railWidth = ref(260)
const railDivider = ref<InstanceType<typeof MrrResizeHandle>>()

const searchType = ref('BAH')
const searchValue = ref('')
const archiveSearchInput = ref<InputInstance>()
const searchPrompt = computed(() => searchType.value === 'ARCHIVE_ID' ? '输入病案主档 ID' : searchType.value === 'SJH' ? '输入上架号' : '输入病案号')
function focusSearch() {
  archiveSearchInput.value?.focus()
}

const searchResults = ref<ArchiveSearchResult[]>([])
const selectedArchive = ref<ArchiveSearchResult | null>(null)
const searchError = ref(false)
const searchCompleted = ref(false)
const detailError = ref(false)
const detailRegion = ref<HTMLElement>()
let searchVersion = 0
let detailVersion = 0
let searchController: AbortController | undefined
let detailController: AbortController | undefined
let overviewVersion = 0
let qualityVersion = 0
let sessionVersion = 0
const relationDetail = ref<ArchiveRelationDetail | null>(null)
const issueSeverity = ref('ALL')
const issueKeyword = ref('')
const issueRule = ref('')
const issueFiltersActive = computed(() => issueSeverity.value !== 'ALL' || !!issueRule.value || !!issueKeyword.value.trim())
function resetIssueFilters() {
  issueSeverity.value = 'ALL'
  issueRule.value = ''
  issueKeyword.value = ''
}
const repairPreview = ref<RepairPreview | null>(null)
const previewVisible = ref(false)
const previewError = ref(false)
const previewIssue = ref<DataQualityIssue>()
let previewVersion = 0
function invalidatePreview() {
  previewVersion++
  previewLoading.value = false
  repairPreview.value = null
  previewError.value = false
  previewIssue.value = undefined
}
watch(previewVisible, (visible) => {
  if (!visible) invalidatePreview()
}, { flush: 'sync' })
function invalidateArchiveSearch() {
  ++searchVersion
  ++detailVersion
  searchController?.abort()
  detailController?.abort()
  searchController = undefined
  detailController = undefined
  searchLoading.value = false
  detailLoading.value = false
  searchResults.value = []
  selectedArchive.value = null
  relationDetail.value = null
  searchError.value = false
  searchCompleted.value = false
  detailError.value = false
}
function clearArchiveSearch() {
  invalidateArchiveSearch()
  searchValue.value = ''
  focusSearch()
}
watch([searchType, () => searchValue.value.trim()], invalidateArchiveSearch, { flush: 'sync' })

function resetPage() {
  previewVisible.value = false
  invalidatePreview()
  invalidateArchiveSearch()
  ++overviewVersion
  ++qualityVersion
  ++sessionVersion
  overview.value = null
  qualitySummary.value = null
  issues.value = []
  qualityLoading.value = false
  qualityFailed.value = false
  qualityLoaded.value = false
  resetIssueFilters()
  overviewLoading.value = false
  checking.value = false
  activeTab.value = props.archiveOnly ? 'archive' : 'overview'
  searchValue.value = ''
  searchType.value = 'BAH'
  railDivider.value?.cancel()
  railWidth.value = 260
  issueSeverity.value = 'ALL'
}
watch(() => useUserStore().token, resetPage, { flush: 'sync' })
watch(canReadQualitySamples, (allowed) => {
  if (!allowed) {
    ++qualityVersion
    qualityLoading.value = false
    qualityLoaded.value = false
    issues.value = []
    resetIssueFilters()
    invalidatePreview()
    previewVisible.value = false
  }
  if (!props.archiveOnly) void loadQualityData().catch(() => {})
}, { flush: 'sync' })
watch(() => props.archiveOnly, () => {
  resetPage()
  if (!props.archiveOnly) void loadDashboard()
}, { flush: 'sync' })
onBeforeUnmount(() => {
  railDivider.value?.cancel()
  invalidatePreview()
  invalidateArchiveSearch()
  ++overviewVersion
  ++qualityVersion
  ++sessionVersion
})

const issueActions: MrrTableAction[] = [
  {
    key: 'locate',
    label: '定位病案',
    icon: 'i-lucide:map-pin',
    tone: 'primary',
    placement: 'inline',
  },
  {
    key: 'preview',
    label: '修复预览',
    icon: 'i-lucide:wrench',
    tone: 'warning',
  },
]
const {
  maxInlineActions: issueMaxInlineActions,
  actionColumnWidth: issueActionColumnWidth,
} = useTableActionLayout(issueActions.length, 2)

const { maxInlineActions: archiveMaxInlineActions, actionColumnWidth: archiveActionColumnWidth } = useTableActionLayout(1, 1)
function archiveActions(row: ArchiveSearchResult): MrrTableAction[] {
  return [{ key: 'detail', label: `查看病案主档 ${row.id} 的关系`, icon: 'i-lucide:eye', tone: 'primary', placement: 'inline' }]
}

const latestRun = computed(() => qualitySummary.value?.latestRun ?? overview.value?.latestQualityRun ?? null)
const checks = computed<QualityCheck[]>(() => qualitySummary.value?.checks ?? [])
const issueRules = computed(() => [...new Map([...checks.value.map(check => [checkCode(check), checkName(check)] as const), ...issues.value.map(issue => [issueCode(issue), issueName(issue)] as const)])].filter(([code]) => !!code).map(([code, name]) => ({ code, name })))
watch(issueRules, (rules) => {
  if (issueRule.value && !rules.some(rule => rule.code === issueRule.value)) issueRule.value = ''
})
function sampleCountForRule(code: string) {
  return issues.value.filter(issue => issueCode(issue) === code).length
}
async function inspectRule(check: QualityCheck) {
  resetIssueFilters()
  issueRule.value = checkCode(check)
  await nextTick()
  sampleRegion.value?.focus({ preventScroll: true })
  sampleRegion.value?.scrollIntoView({ block: 'start' })
}
const filteredIssues = computed(() => {
  const keyword = issueKeyword.value.trim().toLowerCase()
  return issues.value.filter((issue) => {
    if (issueSeverity.value !== 'ALL' && issue.severity !== issueSeverity.value) return false
    if (issueRule.value && issueCode(issue) !== issueRule.value) return false
    if (!keyword) return true
    return [
      issue.checkCode,
      issue.check_code,
      issue.checkName,
      issue.check_name,
      issue.entityType,
      issue.entity_type,
      issue.entityId,
      issue.entity_id,
      issue.bah,
      issue.sjh,
      issue.detail,
    ].some(value => String(value ?? '').toLowerCase().includes(keyword))
  })
})

function numberValue(value: unknown) {
  return typeof value === 'number' ? value : Number(value ?? 0)
}

function formatNumber(value: unknown) {
  if (value == null) return '未知'
  return numberValue(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function formatPercent(value: unknown) {
  if (value == null) return '未知'
  return `${formatNumber(value)}%`
}

function checkCode(check: QualityCheck) {
  return check.checkCode ?? check.check_code ?? 'UNKNOWN'
}

function checkName(check: QualityCheck) {
  return check.checkName ?? check.check_name ?? checkCode(check)
}

function checkIssueCount(check: QualityCheck) {
  return check.issueCount ?? check.issue_count ?? 0
}

function issueCode(issue: DataQualityIssue) {
  return issue.checkCode ?? issue.check_code ?? 'UNKNOWN'
}

function issueName(issue: DataQualityIssue) {
  return issue.checkName ?? issue.check_name ?? issueCode(issue)
}

function issueEntityType(issue: DataQualityIssue) {
  return issue.entityType ?? issue.entity_type ?? '—'
}

function issueEntityId(issue: DataQualityIssue) {
  return issue.entityId ?? issue.entity_id ?? '—'
}

function relationTagType(status: RelationCoverage['status']) {
  if (status === 'UNKNOWN') return 'neutral'
  if (status === 'HEALTHY') return 'success'
  if (status === 'WARNING' || status === 'LEGACY') return 'warning'
  return 'danger'
}

function relationStatusLabel(status: RelationCoverage['status']) {
  return { HEALTHY: '正常', WARNING: '警告', CRITICAL: '严重', LEGACY: '遗留关联', UNKNOWN: '未知' }[status] ?? status
}

function comparisonTagType(status: ComparisonStatus) {
  if (status === 'EXACT') return 'success'
  if (status === 'FORMAT_ONLY' || status === 'NO_CANONICAL') return 'warning'
  if (status === 'MISSING') return 'neutral'
  return 'danger'
}

function comparisonLabel(status: ComparisonStatus) {
  return {
    EXACT: '一致',
    FORMAT_ONLY: '仅格式差异',
    CONFLICT: '真实冲突',
    MISSING: '来源缺失',
    NO_CANONICAL: '主档缺失',
  }[status]
}

async function loadOverview() {
  const current = ++overviewVersion
  overviewLoading.value = true
  try {
    const response = await getDataRelationOverview()
    if (current === overviewVersion) overview.value = response.data ?? null
  }
  finally {
    if (current === overviewVersion) overviewLoading.value = false
  }
}

async function loadQualityData() {
  const current = ++qualityVersion
  qualityLoading.value = true
  qualityFailed.value = false
  try {
    // Pin sample reads to the summary's run; on summary failure still check
    // sample access so a transport failure cannot mask an authorization denial.
    const summaryRequest = getDataQualitySummary()
    const sampleRequest = canReadQualitySamples.value
      ? summaryRequest.then(
          response => response.data?.latestRun === null ? { data: [] as DataQualityIssue[] } : getDataQualityIssues(300, response.data?.latestRun?.id),
          () => getDataQualityIssues(300),
        )
      : Promise.resolve({ data: [] as DataQualityIssue[] })
    const [summaryResult, issueResult] = await Promise.allSettled([summaryRequest, sampleRequest])
    if (current !== qualityVersion) return
    const failures = [summaryResult, issueResult].filter(result => result.status === 'rejected')
    const failure = failures.find(result => isSessionAccessDenied(result.reason)) ?? failures[0]
    if (failure) throw failure.reason
    if (summaryResult.status !== 'fulfilled' || issueResult.status !== 'fulfilled') throw new Error('Invalid quality response')
    const summaryResponse = summaryResult.value
    const issueResponse = issueResult.value
    if (!summaryResponse.data || !Array.isArray(issueResponse.data)) throw new Error('Invalid quality response')
    qualitySummary.value = summaryResponse.data
    issues.value = canReadQualitySamples.value ? issueResponse.data : []
    qualityLoaded.value = canReadQualitySamples.value
  }
  catch (error) {
    if (current === qualityVersion) {
      qualityFailed.value = true
      if (isSessionAccessDenied(error)) {
        qualityLoaded.value = false
        qualitySummary.value = null
        issues.value = []
        resetIssueFilters()
      }
    }
    throw error
  }
  finally {
    if (current === qualityVersion) qualityLoading.value = false
  }
}

function retryQualityData() {
  void loadQualityData().catch(() => {})
}

async function loadDashboard() {
  try {
    await Promise.all([loadOverview(), loadQualityData()])
  }
  catch {
    ElMessage.error('数据关系工作台加载失败')
  }
}

async function searchArchives() {
  const value = searchValue.value.trim()
  if (!value) {
    ElMessage.warning('请输入查询值')
    return
  }

  if (searchLoading.value) return
  invalidateArchiveSearch()
  const current = searchVersion
  const controller = new AbortController()
  searchController = controller
  searchLoading.value = true
  try {
    const response = await searchDataRelationArchives(searchType.value, value, 20, controller.signal)
    if (current !== searchVersion) return
    searchResults.value = response.data ?? []
    searchCompleted.value = true
    if (searchResults.value.length === 1) {
      void openArchive(searchResults.value[0])
    }
  }
  catch {
    if (current === searchVersion) searchError.value = true
  }
  finally {
    if (current === searchVersion) {
      searchLoading.value = false
      searchController = undefined
    }
  }
}

async function openArchive(row: ArchiveSearchResult) {
  if (selectedArchive.value?.id === row.id && (detailLoading.value || relationDetail.value)) return
  const current = ++detailVersion
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  selectedArchive.value = row
  relationDetail.value = null
  detailError.value = false
  detailLoading.value = true
  activeTab.value = 'archive'
  try {
    const response = await getArchiveRelationDetail(row.id, controller.signal)
    if (current !== detailVersion) return
    if (!response.data || Number(response.data.archive.id) !== row.id) throw new Error('Archive detail identity mismatch')
    relationDetail.value = response.data
  }
  catch {
    if (current === detailVersion) detailError.value = true
  }
  finally {
    if (current === detailVersion) {
      detailLoading.value = false
      detailController = undefined
    }
  }
}

function focusArchiveSection(section: string) {
  const target = detailRegion.value?.querySelector<HTMLElement>(`[data-archive-section="${section}"]`)
  if (!target) return
  target.focus({ preventScroll: true })
  target.scrollIntoView({ block: 'start', behavior: 'auto' })
}

function retryArchive() {
  if (selectedArchive.value) void openArchive(selectedArchive.value)
}

async function runChecks() {
  const current = sessionVersion
  checking.value = true
  try {
    await runDataQualityChecks()
    if (current !== sessionVersion) return
    ElMessage.success('数据质量检查已完成')
    await Promise.all([loadOverview(), loadQualityData()])
    if (current === sessionVersion) activeTab.value = 'quality'
  }
  catch {
    if (current === sessionVersion) ElMessage.error('检查或结果读取失败，请核对实际状态')
  }
  finally {
    if (current === sessionVersion) checking.value = false
  }
}

async function showRepairPreview(issue: DataQualityIssue) {
  const current = ++previewVersion
  previewIssue.value = issue
  previewVisible.value = true
  previewLoading.value = true
  previewError.value = false
  repairPreview.value = null
  try {
    const response = await getDataQualityRepairPreview(issue.id)
    if (current !== previewVersion) return
    if (!response.data || response.data.issue.id !== issue.id) throw new Error('Preview identity mismatch')
    repairPreview.value = response.data
  }
  catch {
    if (current === previewVersion) previewError.value = true
  }
  finally {
    if (current === previewVersion) previewLoading.value = false
  }
}

function retryPreview() {
  if (previewIssue.value) void showRepairPreview(previewIssue.value)
}

async function locateIssueArchive(issue: DataQualityIssue) {
  const sjh = String(issue.sjh ?? '').trim()
  const bah = String(issue.bah ?? '').trim()
  if (!sjh && !bah) {
    ElMessage.info('该异常没有可用于定位病案的 BAH/SJH')
    return
  }
  searchType.value = sjh ? 'SJH' : 'BAH'
  searchValue.value = sjh || bah
  activeTab.value = 'archive'
  await searchArchives()
}

function handleIssueAction(action: string, issue: DataQualityIssue) {
  if (action === 'locate') {
    void locateIssueArchive(issue)
  }
  else if (action === 'preview') {
    void showRepairPreview(issue)
  }
}

onMounted(() => {
  if (!props.archiveOnly) void loadDashboard()
})
</script>

<template>
  <MrrPageShell class="relation-workbench">
    <MrrPageHeader
      :title="archiveOnly ? '病案工作台' : '数据关系工作台'"
      :eyebrow="archiveOnly ? 'Medical Records' : 'Data Governance'"
      :description="archiveOnly ? '按病案号、上架号或主档 ID 查找病案，集中核对患者、扫描影像、文件完整性及关联异常。' : '以 mr_archive 为中心查看统计、患者、扫描、装箱和迁移数据，发现缺失关联与字段冲突。关联检查、影像完整性观察与修复预览集中查看。'"
    >
      <template #actions>
        <router-link v-if="archiveOnly" class="el-button" to="/data-relations">
          数据关系工作台
        </router-link>
        <router-link v-else class="el-button" to="/archive-workbench">
          打开病案工作台
        </router-link>
        <el-button v-if="!archiveOnly" :loading="overviewLoading" @click="loadDashboard">
          刷新
        </el-button>
        <el-button v-if="!archiveOnly" type="primary" :loading="checking" @click="runChecks">
          执行检查
        </el-button>
      </template>
    </MrrPageHeader>

    <section v-if="!archiveOnly" class="mrr-metric-grid mrr-metric-grid--four" aria-label="数据关系健康总览">
      <MrrMetricCard
        icon="i-lucide:heart-pulse"
        label="关系健康度"
        :value="formatPercent(overview?.healthScore)"
        note="基于可用统计估算；未知项不计入"
      />
      <MrrMetricCard
        icon="i-lucide:folders"
        label="病案主档（估算）"
        :value="formatNumber(overview?.archiveCount)"
        note="PostgreSQL统计快照，非实时COUNT"
      />
      <MrrMetricCard
        icon="i-lucide:circle-x"
        label="严重异常"
        tone="danger"
        :value="formatNumber(latestRun?.criticalCount ?? latestRun?.critical_count)"
        note="最近一次质量检查结果"
      />
      <MrrMetricCard
        icon="i-lucide:triangle-alert"
        label="警告异常"
        tone="warning"
        :value="formatNumber(latestRun?.warningCount ?? latestRun?.warning_count)"
        :note="latestRun ? formatDateTime(latestRun.completedAt ?? latestRun.completed_at) : '尚未执行检查'"
      />
    </section>

    <el-tabs v-model="activeTab" class="workbench-tabs" :class="{ 'workbench-tabs--archive-only': archiveOnly }">
      <el-tab-pane v-if="!archiveOnly" label="关系总览" name="overview">
        <AppLoading v-if="overviewLoading && !overview" type="card" :cols="3" />
        <div v-else v-loading="overviewLoading" class="overview-stack">
          <section class="relation-grid" aria-label="各数据表关联覆盖率">
            <el-card
              v-for="relation in overview?.relations ?? []"
              :key="relation.tableName"
              shadow="never"
              class="relation-card"
            >
              <div class="relation-card__header">
                <div>
                  <strong>{{ relation.label }}</strong>
                  <code>{{ relation.tableName }}</code>
                </div>
                <MrrStatusTag :label="relationStatusLabel(relation.status)" :title="relation.status" :tone="relationTagType(relation.status)" />
              </div>
              <p>{{ relation.relation }}</p>
              <div class="coverage-row">
                <strong>{{ relation.source === 'STATISTICS_UNAVAILABLE' ? '统计不可用' : relation.coverageIncluded ? formatPercent(relation.coverage) : '遗留关联' }}</strong>
                <span v-if="relation.estimated">估算值</span>
              </div>
              <el-progress
                v-if="relation.coverageIncluded"
                :percentage="Math.min(100, Math.max(0, relation.coverage ?? 0))"
                :stroke-width="8"
                :show-text="false"
              />
              <dl>
                <div>
                  <dt>总数</dt>
                  <dd>{{ formatNumber(relation.totalCount) }}</dd>
                </div>
                <div>
                  <dt>已关联</dt>
                  <dd>{{ formatNumber(relation.linkedCount) }}</dd>
                </div>
                <div>
                  <dt>缺失</dt>
                  <dd :class="{ 'relation-count--missing': Number(relation.missingCount) > 0 }">
                    {{ formatNumber(relation.missingCount) }}
                  </dd>
                </div>
              </dl>
              <small class="relation-card__provenance">来源：{{ relation.source }} · 统计时间：{{ formatDateTime(relation.analyzedAt) }}</small>
            </el-card>
          </section>

          <MrrSectionCard class="section-card" title="关联检查结果" description="最近一次运行中与 archive_id、孤立数据和编号冲突相关的检查">
            <el-table :data="overview?.relationChecks ?? []" stripe empty-text="尚无关联检查结果">
              <el-table-column v-bind="tableSequenceColumn()" />
              <el-table-column label="检查项" min-width="260">
                <template #default="{ row }">
                  <strong>{{ checkName(row as QualityCheck) }}</strong>
                  <div class="muted-code">
                    {{ checkCode(row as QualityCheck) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="级别" width="100" class-name="mrr-table-status-column">
                <template #default="{ row }">
                  <MrrStatusTag :label="row.severity" :tone="row.severity === 'CRITICAL' ? 'danger' : 'warning'" />
                </template>
              </el-table-column>
              <el-table-column label="异常数量" width="130">
                <template #default="{ row }">
                  {{ formatNumber(checkIssueCount(row as QualityCheck)) }}
                </template>
              </el-table-column>
              <el-table-column label="检查时间" min-width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.checkedAt ?? row.checked_at) }}
                </template>
              </el-table-column>
            </el-table>
          </MrrSectionCard>

          <el-alert
            v-for="note in overview?.notes ?? []"
            :key="note"
            :title="note"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="病案工作台" name="archive">
        <div class="archive-stack" :class="{ 'archive-stack--workspace': archiveOnly }" :style="archiveOnly ? { '--archive-rail-width': `${railWidth}px` } : undefined">
          <div :id="archiveOnly ? 'archive-search-pane' : undefined" class="archive-search-rail" :class="{ 'archive-search-rail--legacy': !archiveOnly }" role="region" aria-label="病案检索及候选">
            <MrrSectionCard :body-padding="archiveOnly ? 'compact' : 'normal'" title="查找病案" description="选择编号类型，查询病案主档及其跨表关系。">
              <div class="search-row">
                <div class="search-field search-field--type">
                  <label for="relation-search-type">查询类型</label>
                  <el-select id="relation-search-type" v-model="searchType" class="search-type" aria-label="查询类型">
                    <el-option label="病案主档 ID" value="ARCHIVE_ID" />
                    <el-option label="病案号 BAH" value="BAH" />
                    <el-option label="上架号 SJH" value="SJH" />
                  </el-select>
                </div>
                <div class="search-field search-field--value">
                  <label for="relation-search-value">查询值</label>
                  <el-input id="relation-search-value" ref="archiveSearchInput" v-model="searchValue" aria-label="病案查询值" clearable :placeholder="searchPrompt" @keyup.enter="searchArchives" />
                </div>
                <MrrFilterActionButton type="primary" action="search" :pending="searchLoading" :busy="searchLoading" @click="searchArchives">
                  查询
                </MrrFilterActionButton>
                <el-button v-if="searchValue || searchLoading || searchCompleted || searchError" @click="clearArchiveSearch">
                  清空检索
                </el-button>
              </div>
            </MrrSectionCard>
            <MrrSectionCard v-if="archiveOnly && (searchLoading || searchCompleted || searchError)" body-padding="compact" title="病案主档候选" :description="searchCompleted ? `本次返回 ${searchResults.length} 条，最多 20 条` : '按编号检索后选择病案'">
              <AppLoading v-if="searchLoading" />
              <AppError v-else-if="searchError" message="病案查询失败，请重试" @retry="searchArchives" />
              <AppEmpty v-else-if="!searchResults.length" size="compact" :description="searchCompleted ? '当前条件未找到病案主档' : '等待检索'" />
              <div v-else class="archive-candidates">
                <button v-for="candidate in searchResults" :key="candidate.id" type="button" class="archive-candidate" :aria-pressed="selectedArchive?.id === candidate.id" :aria-label="`查看病案主档 ${candidate.id} 的关系`" @click="openArchive(candidate)">
                  <span class="candidate-heading"><strong>{{ candidate.bah || '未记录病案号' }}</strong><small>#{{ candidate.id }}</small></span>
                  <span>{{ candidate.patientName || '未记录姓名' }} · {{ candidate.department || '—' }}</span>
                  <small>上架号 {{ candidate.sjh || '—' }} · {{ candidate.scanCount ?? '未知' }} 张扫描</small>
                  <el-tag size="small" effect="plain" :type="candidate.matchType === 'EXACT' ? 'success' : 'warning'">
                    {{ candidate.matchType === 'EXACT' ? '精确匹配' : '格式差异' }}
                  </el-tag>
                </button>
              </div>
            </MrrSectionCard>
          </div>

          <MrrResizeHandle v-if="archiveOnly" ref="railDivider" v-model="railWidth" class="archive-rail-divider" :min="220" :max="360" :default-width="260" label="调整检索栏宽度" controls="archive-search-pane" />

          <MrrSectionCard v-if="!archiveOnly && searchResults.length" class="section-card" title="病案主档候选" description="精确匹配优先；仅前导零差异标记为格式差异（FORMAT_ONLY）">
            <el-table :data="searchResults" row-key="id" :current-row-key="selectedArchive?.id" highlight-current-row @row-click="openArchive">
              <el-table-column v-bind="tableSequenceColumn()" />
              <el-table-column prop="id" label="主档 ID" width="120" />
              <el-table-column prop="bah" label="病案号（BAH）" min-width="150" />
              <el-table-column prop="sjh" label="上架号（SJH）" min-width="150" />
              <el-table-column prop="patientName" label="患者" width="120" />
              <el-table-column prop="department" label="科室" min-width="140" />
              <el-table-column prop="scanCount" label="扫描数" width="100" />
              <el-table-column label="匹配方式" min-width="180" class-name="mrr-table-status-column">
                <template #default="{ row }">
                  <el-tag :type="row.matchType === 'EXACT' ? 'success' : 'warning'">
                    {{ row.matchType === 'EXACT' ? '精确匹配（EXACT）' : row.matchType === 'FORMAT_ONLY' ? '格式差异（FORMAT_ONLY）' : row.matchType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" :width="archiveActionColumnWidth" fixed="right" align="center">
                <template #default="{ row }">
                  <MrrTableActions :actions="archiveActions(row as ArchiveSearchResult)" :max-inline="archiveMaxInlineActions" @select="openArchive(row as ArchiveSearchResult)" />
                </template>
              </el-table-column>
            </el-table>
          </MrrSectionCard>
          <AppError v-else-if="!archiveOnly && searchError" message="病案查询失败，请重试" @retry="searchArchives" />
          <AppEmpty v-else-if="!archiveOnly && searchCompleted && !searchLoading" icon="i-lucide:search-x" description="当前条件未找到病案主档" />

          <div ref="detailRegion" v-loading="detailLoading" class="archive-detail-stack" :aria-busy="detailLoading">
            <p v-if="detailLoading && selectedArchive" class="archive-detail-status" role="status">
              正在加载病案主档 #{{ selectedArchive.id }} 的关系…
            </p>
            <AppError v-else-if="detailError" message="病案关系加载失败，请重试" @retry="retryArchive" />
            <template v-else-if="relationDetail">
              <el-card shadow="never" class="section-card archive-header-card">
                <div class="archive-header">
                  <div>
                    <span class="archive-header__id">病案主档 #{{ relationDetail.archive.id }}</span>
                    <h3>{{ relationDetail.archive.patientName || '未记录患者姓名' }}</h3>
                    <p>
                      病案号（BAH）：{{ relationDetail.archive.bah || '—' }} ·
                      上架号（SJH）：{{ relationDetail.archive.sjh || '—' }} ·
                      科室：{{ relationDetail.archive.department || '—' }}
                    </p>
                  </div>
                  <div class="flex flex-wrap items-center gap-2">
                    <el-tag type="info" effect="plain">
                      只读关系视图
                    </el-tag>
                    <router-link v-if="hasPermission('record:read') && selectedArchive?.sjh && /^\d{4,}$/.test(selectedArchive.sjh)" class="el-button" :to="{ path: '/archive', query: { sjh: selectedArchive.sjh } }">
                      浏览影像
                    </router-link>
                  </div>
                </div>
              </el-card>

              <!-- 五个计数只是关系概况，用卡内摘要块；原来是五张带外框的 el-card，与下方真正的分区卡同一层级。 -->
              <section class="relation-count-grid mrr-stat-strip" aria-label="关联记录数">
                <div class="mrr-stat-tile">
                  <span class="mrr-stat-tile__label">统计记录</span>
                  <strong class="mrr-stat-tile__value">{{ relationDetail.statistics.length }}</strong>
                </div>
                <div class="mrr-stat-tile">
                  <span class="mrr-stat-tile__label">患者记录</span>
                  <strong class="mrr-stat-tile__value">{{ relationDetail.patients.length }}</strong>
                </div>
                <div class="mrr-stat-tile">
                  <span class="mrr-stat-tile__label">装箱记录</span>
                  <strong class="mrr-stat-tile__value">{{ relationDetail.boxes.length }}</strong>
                </div>
                <div class="mrr-stat-tile">
                  <span class="mrr-stat-tile__label">有效扫描</span>
                  <strong class="mrr-stat-tile__value">{{ formatNumber(relationDetail.scanSummary.activeCount) }}</strong>
                </div>
                <div class="mrr-stat-tile">
                  <span class="mrr-stat-tile__label">未关联候选扫描</span>
                  <strong class="mrr-stat-tile__value">{{ formatNumber(relationDetail.scanSummary.unlinkedCandidateCount) }}</strong>
                </div>
              </section>

              <el-alert
                v-for="warning in relationDetail.warnings"
                :key="warning"
                :title="warning"
                type="warning"
                :closable="false"
                show-icon
              />

              <nav v-if="archiveOnly" class="archive-section-nav" aria-label="病案详情分区">
                <el-button v-if="hasPermission('record:read')" @click="focusArchiveSection('images')">
                  影像核对
                </el-button>
                <el-button v-if="hasPermission('record:read')" @click="focusArchiveSection('manifest')">
                  文件完整性
                </el-button>
                <el-button v-if="canRepair" @click="focusArchiveSection('repairs')">
                  修复工单
                </el-button>
                <el-button @click="focusArchiveSection('comparison')">
                  字段对比
                </el-button>
                <el-button @click="focusArchiveSection('records')">
                  关联记录
                </el-button>
              </nav>
              <section v-if="archiveOnly && selectedArchive && hasPermission('record:read')" data-archive-section="images" tabindex="-1" aria-label="影像核对分区">
                <ArchiveScanInspector :key="`scan-${selectedArchive.id}`" :archive-id="selectedArchive.id" />
              </section>
              <section v-if="selectedArchive && hasPermission('system:read') && hasPermission('record:read')" data-archive-section="manifest" tabindex="-1" aria-label="文件完整性分区">
                <ArchiveManifestCard :key="selectedArchive.id" :archive-id="selectedArchive.id" />
              </section>
              <section v-if="selectedArchive && canRepair" data-archive-section="repairs" tabindex="-1" aria-label="修复工单分区">
                <RepairOrderPanel :key="`repair-${selectedArchive.id}`" :archive-id="selectedArchive.id" />
              </section>

              <MrrSectionCard data-archive-section="comparison" tabindex="-1" class="section-card" title="跨表字段对比" description="区分完全一致、仅前导零差异、字段缺失和真实冲突">
                <el-table :data="relationDetail.comparisons" stripe>
                  <el-table-column v-bind="tableSequenceColumn()" />
                  <el-table-column prop="field" label="字段" width="120" />
                  <el-table-column prop="canonicalValue" label="mr_archive" min-width="150" />
                  <el-table-column prop="source" label="来源表" min-width="180" />
                  <el-table-column prop="sourceValue" label="来源值" min-width="150" />
                  <el-table-column label="判断" width="140" class-name="mrr-table-status-column">
                    <template #default="{ row }">
                      <MrrStatusTag :label="comparisonLabel(row.status) || row.status" :tone="comparisonTagType(row.status)" />
                    </template>
                  </el-table-column>
                </el-table>
              </MrrSectionCard>

              <el-tabs data-archive-section="records" tabindex="-1" class="detail-tabs" type="border-card">
                <el-tab-pane :label="`统计记录 (${relationDetail.statistics.length})`">
                  <el-table :data="relationDetail.statistics" max-height="420">
                    <el-table-column v-bind="tableSequenceColumn()" />
                    <el-table-column prop="id" label="ID" width="90" />
                    <el-table-column prop="relationMode" label="关联方式" width="130" />
                    <el-table-column prop="bah" label="BAH" width="120" />
                    <el-table-column prop="sjh" label="SJH" width="120" />
                    <el-table-column prop="patientName" label="患者" width="120" />
                    <el-table-column prop="department" label="科室" min-width="140" />
                    <el-table-column prop="pages" label="页数" width="90" />
                  </el-table>
                </el-tab-pane>
                <el-tab-pane :label="`患者记录 (${relationDetail.patients.length})`">
                  <el-table :data="relationDetail.patients" max-height="420">
                    <el-table-column v-bind="tableSequenceColumn()" />
                    <el-table-column prop="id" label="ID" width="90" />
                    <el-table-column prop="relationMode" label="关联方式" width="130" />
                    <el-table-column prop="bah" label="BAH" width="120" />
                    <el-table-column prop="name" label="姓名" width="120" />
                    <el-table-column prop="idcard" label="患者标识" min-width="170" show-overflow-tooltip />
                    <el-table-column prop="department" label="科室" min-width="130" />
                    <el-table-column prop="bingqu" label="病区" min-width="120" />
                    <el-table-column prop="chuangwei" label="床位" width="100" />
                  </el-table>
                </el-tab-pane>
                <el-tab-pane :label="`装箱记录 (${relationDetail.boxes.length})`">
                  <el-table :data="relationDetail.boxes" max-height="420">
                    <el-table-column v-bind="tableSequenceColumn()" />
                    <el-table-column prop="id" label="ID" width="90" />
                    <el-table-column prop="relationMode" label="关联方式" width="130" />
                    <el-table-column prop="boxNo" label="实际箱号" min-width="130" />
                    <el-table-column prop="expectedBoxNo" label="预期箱号" min-width="130" />
                    <el-table-column prop="status" label="状态" width="110" />
                    <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
                  </el-table>
                </el-tab-pane>
                <el-tab-pane :label="`扫描样本 (${relationDetail.scanSamples.length})`">
                  <el-table :data="relationDetail.scanSamples" max-height="420">
                    <el-table-column v-bind="tableSequenceColumn()" />
                    <el-table-column prop="id" label="ID" width="100" />
                    <el-table-column prop="pages" label="页码" width="90" />
                    <el-table-column prop="filename" label="文件名" min-width="180" show-overflow-tooltip />
                    <el-table-column prop="btype" label="类型" width="90" />
                    <el-table-column prop="migrationStatus" label="迁移状态" width="130" />
                    <el-table-column prop="hasOss" label="OSS" width="80" />
                  </el-table>
                </el-tab-pane>
              </el-tabs>
            </template>
            <MrrSectionCard v-else-if="archiveOnly && !searchLoading" class="archive-start-card" title="病案详情" description="选择病案后在此核对相关信息">
              <div class="archive-start-content" role="status">
                <span class="archive-start-icon" aria-hidden="true"><FaIcon name="i-lucide:folder-search" /></span>
                <h3>{{ searchError ? '病案查询未完成' : searchCompleted && !searchResults.length ? '未找到匹配病案' : searchResults.length ? '请选择病案主档' : '开始病案核对' }}</h3>
                <p>{{ searchError ? '请在检索区重试查询。' : searchCompleted && !searchResults.length ? '检查查询类型与原始编号，或换用其他编号重新查询。' : searchResults.length ? '从检索结果选择对应主档，核对其关联记录。' : '使用病案号、上架号或主档 ID 进行检索。选中病案后即可查看当前权限允许的内容。' }}</p>
                <div v-if="!searchCompleted && !searchError" class="archive-start-capabilities" aria-label="核对内容">
                  <span><FaIcon name="i-lucide:git-fork" aria-hidden="true" /> 关联记录</span>
                  <span><FaIcon name="i-lucide:shield-check" aria-hidden="true" /> 文件完整性</span>
                  <span v-if="hasPermission('record:read')"><FaIcon name="i-lucide:images" aria-hidden="true" /> 影像核对</span>
                </div>
              </div>
              <div v-if="!searchResults.length" class="archive-start-actions">
                <el-button @click="focusSearch">
                  返回检索
                </el-button>
              </div>
            </MrrSectionCard>
            <AppEmpty v-else-if="!archiveOnly && !searchLoading && !searchError && (!searchCompleted || searchResults.length)" icon="i-lucide:git-fork" description="查询并选择一份病案后查看跨表关系" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="!archiveOnly && canRepair" label="修复工单" name="repairs">
        <RepairOrderPanel v-if="activeTab === 'repairs'" />
        <QualityCaseReview v-if="activeTab === 'repairs'" />
      </el-tab-pane>
      <el-tab-pane v-if="!archiveOnly" label="检查与异常" name="quality">
        <div class="quality-stack">
          <AppError v-if="qualityFailed && !qualityLoaded" message="异常样本读取失败，不能判断当前是否存在异常" @retry="retryQualityData" />
          <el-alert v-else-if="qualityFailed" title="异常样本刷新失败，以下为上次成功读取的结果" type="warning" :closable="false" />
          <MrrSectionCard class="section-card" title="检查规则结果" description="关联、编号、文件元数据、重复扫描和迁移一致性">
            <template #actions>
              <MrrStatusTag v-if="qualitySummary?.running" label="检查运行中" tone="warning" />
            </template>
            <el-table :data="checks" stripe :empty-text="qualitySummary ? '尚未执行数据质量检查' : '规则结果尚未取得'">
              <el-table-column v-bind="tableSequenceColumn()" />
              <el-table-column label="规则" min-width="280">
                <template #default="{ row }">
                  <strong>{{ checkName(row as QualityCheck) }}</strong>
                  <div class="muted-code">
                    {{ checkCode(row as QualityCheck) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="严重程度" width="120" class-name="mrr-table-status-column">
                <template #default="{ row }">
                  <MrrStatusTag :label="row.severity" :tone="row.severity === 'CRITICAL' ? 'danger' : 'warning'" />
                </template>
              </el-table-column>
              <el-table-column label="异常数" width="120">
                <template #default="{ row }">
                  {{ formatNumber(checkIssueCount(row as QualityCheck)) }}
                </template>
              </el-table-column>
              <el-table-column label="样本数" width="110">
                <template #default="{ row }">
                  {{ formatNumber(row.sampledCount ?? row.sampled_count) }}
                </template>
              </el-table-column>
              <el-table-column label="已加载样本" width="130">
                <template #default="{ row }">
                  <el-button v-if="qualityLoaded" link type="primary" :aria-label="`查看规则 ${checkName(row as QualityCheck)} 的样本`" @click="inspectRule(row as QualityCheck)">
                    {{ sampleCountForRule(checkCode(row as QualityCheck)) }} · 查看
                  </el-button>
                  <span v-else>—</span>
                </template>
              </el-table-column>
              <el-table-column label="检查时间" min-width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.checkedAt ?? row.checked_at) }}
                </template>
              </el-table-column>
            </el-table>
          </MrrSectionCard>

          <el-alert v-if="!canReadQualitySamples" title="查看异常样本需要病案读取权限" type="info" :closable="false" />
          <section v-else ref="sampleRegion" class="quality-samples-region" tabindex="-1" aria-label="异常样本检查区">
            <MrrSectionCard v-loading="qualityLoading" class="section-card" title="异常样本" description="当前显示最近一次检查保存的有界样本，不直接返回全量异常">
              <template #actions>
                <el-button :loading="qualityLoading" @click="retryQualityData">
                  刷新样本
                </el-button>
              </template>
              <MrrFilterBar embedded :focus-ring="false">
                <div class="mrr-filter-field">
                  <el-select v-model="issueSeverity" aria-label="异常级别筛选">
                    <el-option label="全部级别" value="ALL" />
                    <el-option label="严重" value="CRITICAL" />
                    <el-option label="警告" value="WARNING" />
                  </el-select>
                </div>
                <div class="mrr-filter-field mrr-filter-field--code">
                  <el-select v-model="issueRule" aria-label="异常规则筛选" class="issue-rule-select" clearable placeholder="全部规则">
                    <el-option v-for="rule in issueRules" :key="rule.code" :label="rule.name || rule.code" :value="rule.code" />
                  </el-select>
                </div>
                <div class="mrr-filter-field mrr-filter-field--keyword">
                  <el-input v-model="issueKeyword" aria-label="异常关键字筛选" clearable placeholder="筛选规则、表、BAH 或 SJH" />
                </div>
                <template #actions>
                  <MrrFilterActionButton action="reset" feedback="request" :disabled="!issueFiltersActive" aria-label="重置异常样本筛选" @click="resetIssueFilters" />
                </template>
              </MrrFilterBar>
              <div class="issue-sample-count" role="status">
                {{ qualityLoaded ? `匹配 ${filteredIssues.length} / 已加载 ${issues.length} 个样本` : '尚未取得样本' }}
              </div>
              <el-table :data="filteredIssues" stripe :empty-text="!qualityLoaded ? '样本尚未取得' : issues.length && issueFiltersActive ? '没有符合筛选条件的样本' : '当前没有异常样本'">
                <el-table-column v-bind="tableSequenceColumn()" />
                <el-table-column label="级别" width="95" class-name="mrr-table-status-column">
                  <template #default="{ row }">
                    <MrrStatusTag :label="row.severity === 'CRITICAL' ? '严重' : '警告'" :tone="row.severity === 'CRITICAL' ? 'danger' : 'warning'" />
                  </template>
                </el-table-column>
                <el-table-column label="规则" min-width="250">
                  <template #default="{ row }">
                    <strong>{{ issueName(row as DataQualityIssue) }}</strong>
                    <div class="muted-code">
                      {{ issueCode(row as DataQualityIssue) }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="记录" min-width="180">
                  <template #default="{ row }">
                    {{ issueEntityType(row as DataQualityIssue) }} #{{ issueEntityId(row as DataQualityIssue) }}
                  </template>
                </el-table-column>
                <el-table-column prop="bah" label="BAH" width="120" />
                <el-table-column prop="sjh" label="SJH" width="120" />
                <el-table-column prop="detail" label="说明" min-width="300" show-overflow-tooltip />
                <el-table-column
                  label="操作"
                  :width="issueActionColumnWidth"
                  fixed="right"
                  align="center"
                >
                  <template #default="{ row }">
                    <MrrTableActions
                      :actions="issueActions"
                      :max-inline="issueMaxInlineActions"
                      @select="handleIssueAction($event, row as DataQualityIssue)"
                    />
                  </template>
                </el-table-column>
              </el-table>
            </MrrSectionCard>
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="previewVisible" title="只读修复预览" size="min(520px, 100%)">
      <div v-loading="previewLoading" class="preview-content">
        <AppError v-if="previewError" message="修复预览读取失败" @retry="retryPreview" />
        <template v-else-if="repairPreview">
          <el-alert
            title="预览不会修改数据库；提交工单后须独立审批和显式执行"
            type="info"
            :closable="false"
            show-icon
          />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="建议操作">
              {{ repairPreview.suggestedAction }}
            </el-descriptions-item>
            <el-descriptions-item label="是否唯一确定">
              {{ repairPreview.deterministic ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="能否直接执行">
              {{ repairPreview.canApply ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="原因">
              {{ repairPreview.reason }}
            </el-descriptions-item>
          </el-descriptions>

          <h4>当前记录</h4>
          <el-descriptions :column="1" border>
            <el-descriptions-item v-for="field in (issueEntityType(repairPreview.issue) === 'mr_archive_box_record' ? ['entityId', 'archiveId', 'bah', 'sjh', 'boxNo', 'status'] : ['entityId', 'archiveId', 'bah', 'sjh', 'pages', 'uploadFlag'])" :key="field" :label="field">
              {{ repairPreview.currentEntity[field] ?? '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <h4>候选病案主档</h4>
          <el-table :data="repairPreview.candidateArchives" empty-text="没有候选病案主档">
            <el-table-column v-bind="tableSequenceColumn()" />
            <el-table-column prop="id" label="archive_id" width="110" />
            <el-table-column prop="bah" label="BAH" />
            <el-table-column prop="sjh" label="SJH" />
            <el-table-column prop="matchType" label="匹配方式" width="120" />
          </el-table>
          <QualityCaseReview v-if="canRepair && repairPreview.issue.id && ((issueCode(repairPreview.issue) === 'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED' && issueEntityType(repairPreview.issue) === 'mr_scan') || (issueCode(repairPreview.issue) === 'BOX_ARCHIVE_LINK_MISSING' && issueEntityType(repairPreview.issue) === 'mr_archive_box_record'))" :key="`case-${repairPreview.issue.id}`" :issue-id="repairPreview.issue.id" />
          <RepairOrderPanel v-if="canRepair && repairPreview.issue.id" :key="repairPreview.issue.id" :issue-id="repairPreview.issue.id" :deterministic="repairPreview.deterministic && ((issueCode(repairPreview.issue) === 'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED' && issueEntityType(repairPreview.issue) === 'mr_scan') || (issueCode(repairPreview.issue) === 'BOX_ARCHIVE_LINK_MISSING' && issueEntityType(repairPreview.issue) === 'mr_archive_box_record'))" />
        </template>
      </div>
    </el-drawer>
  </MrrPageShell>
</template>

<style scoped>
.relation-workbench {
  display: grid;
  min-width: 0;
}

.search-row,
.archive-header,
.relation-card__header,
.coverage-row {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
}

.archive-header,
.relation-card__header,
.coverage-row {
  justify-content: space-between;
}

h3,
h4,
p {
  margin-top: 0;
}

.relation-grid,
.relation-count-grid {
  display: grid;
  gap: var(--mrr-space-4);
}

.relation-card small {
  color: var(--el-text-color-secondary);
}

.workbench-tabs--archive-only > :deep(.el-tabs__header) {
  display: none;
}

.workbench-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.overview-stack,
.archive-stack,
.archive-detail-stack,
.quality-stack,
.preview-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--mrr-space-4);
  min-width: 0;
}

/* The standalone workspace keeps search context beside the current record. */
.archive-stack--workspace {
  grid-template-columns: minmax(220px, min(var(--archive-rail-width, 260px), 34%)) 12px minmax(0, 1fr);
  gap: 0;
  align-items: start;
}

.archive-start-content {
  display: grid;
  gap: var(--mrr-space-3);
  justify-items: center;
  min-width: 0;
  padding: var(--mrr-space-5) var(--mrr-space-3);
  text-align: center;
}

.archive-start-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  font-size: 24px;
  color: var(--mrr-tone-primary-ink);
  background: var(--mrr-tone-primary-soft);
  border: 1px solid var(--mrr-tone-primary-border);
  border-radius: var(--mrr-radius-lg);
}

.archive-start-content h3,
.archive-start-content p {
  margin: 0;
}

.archive-start-content h3 {
  font-size: var(--mrr-font-size-md);
  color: var(--mrr-card-foreground);
}

.archive-start-content p {
  max-width: 420px;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.65;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.archive-start-capabilities {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  justify-content: center;
}

.archive-start-capabilities span {
  display: inline-flex;
  gap: var(--mrr-space-1);
  align-items: center;
  padding: var(--mrr-space-1) var(--mrr-space-2);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-sm);
}

.archive-start-actions {
  display: flex;
  justify-content: center;
  padding-bottom: var(--mrr-space-3);
}

.archive-search-rail {
  display: grid;
  gap: var(--mrr-space-3);
  min-width: 0;
}

.archive-search-rail:not(.archive-search-rail--legacy) .search-row {
  flex-direction: column;
  align-items: stretch;
}

.archive-search-rail:not(.archive-search-rail--legacy) .search-field {
  flex: 0 1 auto;
}

.archive-candidates {
  display: grid;
  gap: var(--mrr-space-2);
  align-content: start;
  max-height: 540px;
  overflow: auto;
}

.archive-candidate {
  display: grid;
  gap: var(--mrr-space-1);
  justify-items: start;
  min-width: 0;
  padding: var(--mrr-space-2);
  color: var(--el-text-color-regular);
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
  transition: background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.archive-candidate[aria-pressed="false"]:hover {
  background: var(--mrr-control-bg-hover);
  border-color: var(--mrr-border-strong);
}

.archive-candidate[aria-pressed="true"] {
  background: var(--mrr-tone-primary-soft);
  border-color: var(--mrr-primary);
}

.archive-candidate:focus-visible,
[data-archive-section]:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: -2px;
}

.candidate-heading {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: baseline;
  justify-content: space-between;
  width: 100%;
}

.archive-candidate small {
  color: var(--el-text-color-secondary);
}

.archive-section-nav {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  padding: var(--mrr-space-2);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--mrr-radius-card);
}

.archive-section-nav :deep(.el-button + .el-button) {
  margin-left: 0;
}

[data-archive-section] {
  min-width: 0;
  scroll-margin-top: 16px;
}

@media (max-width: 1050px) {
  .archive-rail-divider {
    display: none;
  }

  .archive-stack--workspace {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--mrr-space-3);
  }

  .archive-candidates {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 190px), 1fr));
    max-height: 250px;
  }
}

.relation-grid {
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
}

.relation-card {
  min-width: 0;
  border-radius: var(--mrr-radius-card);
}

.relation-card__header {
  flex-wrap: wrap;
  align-items: flex-start;
}

.relation-card__header > div {
  display: grid;
  flex: 1 1 auto;
  gap: var(--mrr-space-1);
  min-width: 0;
  overflow-wrap: anywhere;
}

.relation-card code,
.muted-code {
  font-size: var(--mrr-font-size-xs);
  color: var(--el-text-color-secondary);
}

.relation-card p {
  margin: var(--mrr-space-2) 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.coverage-row strong {
  font-size: var(--mrr-font-size-metric);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.relation-card dl {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 96px), 1fr));
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-2) 0;
  margin: var(--mrr-space-2) 0;
  border-top: 1px solid var(--mrr-border);
  border-bottom: 1px solid var(--mrr-border);
}

.relation-count--missing {
  color: var(--mrr-tone-warning-ink);
}

.relation-card__provenance {
  display: block;
  font-size: var(--mrr-font-size-xs);
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.relation-card dl div {
  display: grid;
  gap: var(--mrr-space-1);
}

.relation-card dt {
  font-size: var(--mrr-font-size-xs);
  color: var(--el-text-color-secondary);
}

.relation-card dd {
  margin: 0;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.section-card {
  min-width: 0;
  overflow: hidden;
  border-radius: var(--mrr-radius-card);
}

.search-row {
  flex-wrap: wrap;
  align-items: flex-end;
}

.search-field {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
}

.search-field label {
  font-size: var(--mrr-font-size-xs);
  font-weight: 600;
  color: var(--mrr-foreground);
}

.search-field--type {
  flex: 0 1 180px;
}

.search-field--value {
  flex: 1 1 240px;
}

.search-type {
  width: 100%;
}

.archive-detail-status {
  margin: 0;
  color: var(--mrr-muted-foreground);
}

/* 当前对象的强调条与 OSS 迁移「当前任务」卡同宽（3px），两处语义相同：这张卡是当前焦点对象。 */
.archive-header-card {
  border-left: 3px solid var(--el-color-primary);
}

.archive-header__id {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  color: var(--el-text-color-secondary);
}

.archive-header > div { min-width: 0; overflow-wrap: anywhere; }

.archive-header { flex-wrap: wrap; }

.archive-header h3 {
  margin: var(--mrr-space-1) 0;
  font-size: var(--mrr-font-size-title-compact);
  font-weight: 650;
  line-height: 1.3;
}

.archive-header p {
  margin-bottom: 0;
  color: var(--el-text-color-secondary);
}

.relation-count-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.detail-tabs {
  border-radius: var(--mrr-radius-card);
}

.preview-content pre {
  padding: var(--mrr-space-3);
  overflow: auto;
  font-size: var(--mrr-font-size-xs);
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: var(--mrr-radius-md);
}

@media (max-width: 1200px) {
  .relation-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .relation-count-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.quality-samples-region {
  min-width: 0;
  scroll-margin-top: 12px;
}

.quality-samples-region:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 2px;
}

.issue-sample-count {
  padding-block: var(--mrr-space-2);
  font-size: var(--mrr-font-size-xs);
  font-variant-numeric: tabular-nums;
  color: var(--mrr-muted-foreground);
}

@media (max-width: 760px) {
  .search-row {
    flex-direction: column;
    align-items: stretch;
  }

  .search-field--type,
  .search-field--value {
    flex: 0 1 auto;
  }

  .relation-grid,
  .relation-count-grid {
    grid-template-columns: 1fr;
  }

  .search-type {
    width: 100%;
  }
}
</style>

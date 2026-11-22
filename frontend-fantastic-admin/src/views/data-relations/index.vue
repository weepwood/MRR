<script setup lang="ts">
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
import { computed, onMounted, ref } from 'vue'
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
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import MrrStatusTag from '@/components/MrrStatusTag/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { formatDateTime } from '@/utils/display-format'

defineOptions({ name: 'DataRelationWorkbenchPage' })

const overviewLoading = ref(false)
const searchLoading = ref(false)
const detailLoading = ref(false)
const checking = ref(false)
const previewLoading = ref(false)
const activeTab = ref('overview')
const overview = ref<DataRelationOverview | null>(null)
const qualitySummary = ref<DataQualitySummary | null>(null)
const issues = ref<DataQualityIssue[]>([])
const searchType = ref('BAH')
const searchValue = ref('')
const searchResults = ref<ArchiveSearchResult[]>([])
const selectedArchive = ref<ArchiveSearchResult | null>(null)
const searchError = ref(false)
const searchCompleted = ref(false)
const detailError = ref(false)
let searchVersion = 0
let detailVersion = 0
const relationDetail = ref<ArchiveRelationDetail | null>(null)
const issueSeverity = ref('ALL')
const issueKeyword = ref('')
const repairPreview = ref<RepairPreview | null>(null)
const previewVisible = ref(false)

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

const latestRun = computed(() => qualitySummary.value?.latestRun ?? overview.value?.latestQualityRun ?? null)
const checks = computed<QualityCheck[]>(() => qualitySummary.value?.checks ?? overview.value?.relationChecks ?? [])
const filteredIssues = computed(() => {
  const keyword = issueKeyword.value.trim().toLowerCase()
  return issues.value.filter((issue) => {
    if (issueSeverity.value !== 'ALL' && issue.severity !== issueSeverity.value) return false
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
  overviewLoading.value = true
  try {
    const response = await getDataRelationOverview()
    overview.value = response.data ?? null
  }
  finally {
    overviewLoading.value = false
  }
}

async function loadQualityData() {
  const [summaryResponse, issueResponse] = await Promise.all([
    getDataQualitySummary(),
    getDataQualityIssues(300),
  ])
  qualitySummary.value = summaryResponse.data ?? null
  issues.value = issueResponse.data ?? []
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

  const current = ++searchVersion
  ++detailVersion
  searchLoading.value = true
  detailLoading.value = false
  searchError.value = false
  searchCompleted.value = false
  detailError.value = false
  searchResults.value = []
  relationDetail.value = null
  selectedArchive.value = null
  try {
    const response = await searchDataRelationArchives(searchType.value, value, 20)
    if (current !== searchVersion) return
    searchResults.value = response.data ?? []
    searchCompleted.value = true
    if (searchResults.value.length === 1) {
      await openArchive(searchResults.value[0])
    }
  }
  catch {
    if (current === searchVersion) searchError.value = true
  }
  finally {
    if (current === searchVersion) searchLoading.value = false
  }
}

async function openArchive(row: ArchiveSearchResult) {
  const current = ++detailVersion
  selectedArchive.value = row
  relationDetail.value = null
  detailError.value = false
  detailLoading.value = true
  activeTab.value = 'archive'
  try {
    const response = await getArchiveRelationDetail(row.id)
    if (current !== detailVersion) return
    if (!response.data) throw new Error('Empty archive detail')
    relationDetail.value = response.data
  }
  catch {
    if (current === detailVersion) detailError.value = true
  }
  finally {
    if (current === detailVersion) detailLoading.value = false
  }
}

function retryArchive() {
  if (selectedArchive.value) void openArchive(selectedArchive.value)
}

async function runChecks() {
  checking.value = true
  try {
    await runDataQualityChecks()
    ElMessage.success('数据质量检查已完成')
    await Promise.all([loadOverview(), loadQualityData()])
    activeTab.value = 'quality'
  }
  finally {
    checking.value = false
  }
}

async function showRepairPreview(issue: DataQualityIssue) {
  previewVisible.value = true
  previewLoading.value = true
  repairPreview.value = null
  try {
    const response = await getDataQualityRepairPreview(issue.id)
    repairPreview.value = response.data ?? null
  }
  finally {
    previewLoading.value = false
  }
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

onMounted(loadDashboard)
</script>

<template>
  <MrrPageShell class="relation-workbench">
    <MrrPageHeader
      title="数据关系工作台"
      eyebrow="Data Governance"
      description="以 mr_archive 为中心查看统计、患者、扫描、装箱和迁移数据，发现缺失关联与字段冲突。第一阶段仅提供只读检查和修复预览。"
    >
      <template #actions>
        <el-button :loading="overviewLoading" @click="loadDashboard">
          刷新
        </el-button>
        <el-button type="primary" :loading="checking" @click="runChecks">
          执行检查
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="mrr-metric-grid mrr-metric-grid--four" aria-label="数据关系健康总览">
      <MrrMetricCard
        label="关系健康度"
        :value="formatPercent(overview?.healthScore)"
        note="基于可用统计估算；未知项不计入"
      />
      <MrrMetricCard
        label="病案主档（估算）"
        :value="formatNumber(overview?.archiveCount)"
        note="PostgreSQL统计快照，非实时COUNT"
      />
      <MrrMetricCard
        label="严重异常"
        tone="danger"
        :value="formatNumber(latestRun?.criticalCount ?? latestRun?.critical_count)"
        note="最近一次质量检查结果"
      />
      <MrrMetricCard
        label="警告异常"
        tone="warning"
        :value="formatNumber(latestRun?.warningCount ?? latestRun?.warning_count)"
        :note="latestRun ? formatDateTime(latestRun.completedAt ?? latestRun.completed_at) : '尚未执行检查'"
      />
    </section>

    <el-tabs v-model="activeTab" class="workbench-tabs">
      <el-tab-pane label="关系总览" name="overview">
        <AppLoading v-if="overviewLoading && !overview" type="card" :cols="3" />
        <div v-else v-loading="overviewLoading" class="overview-stack">
          <section class="relation-grid">
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
                <MrrStatusTag :label="relation.status" :tone="relationTagType(relation.status)" />
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
                  <dd>{{ formatNumber(relation.missingCount) }}</dd>
                </div>
              </dl>
              <small>来源：{{ relation.source }} · 统计时间：{{ formatDateTime(relation.analyzedAt) }}</small>
            </el-card>
          </section>

          <MrrSectionCard class="section-card" title="关联检查结果" description="最近一次运行中与 archive_id、孤立数据和编号冲突相关的检查">
            <el-table :data="overview?.relationChecks ?? []" stripe empty-text="尚无关联检查结果">
              <el-table-column label="检查项" min-width="260">
                <template #default="{ row }">
                  <strong>{{ checkName(row as QualityCheck) }}</strong>
                  <div class="muted-code">
                    {{ checkCode(row as QualityCheck) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="级别" width="100">
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

      <el-tab-pane label="病案关系查询" name="archive">
        <div class="archive-stack">
          <MrrSectionCard class="search-card" title="查找病案" description="选择编号类型，查询病案主档及其跨表关系。">
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
                <el-input
                  id="relation-search-value"
                  v-model="searchValue"
                  aria-label="病案查询值"
                  clearable
                  placeholder="输入主档 ID、病案号或上架号"
                  @keyup.enter="searchArchives"
                />
              </div>
              <MrrFilterActionButton type="primary" action="search" :pending="searchLoading" :busy="searchLoading" @click="searchArchives">
                查询
              </MrrFilterActionButton>
            </div>
          </MrrSectionCard>

          <MrrSectionCard v-if="searchResults.length" class="section-card" title="病案主档候选" description="精确匹配优先；仅前导零差异标记为格式差异（FORMAT_ONLY）">
            <el-table :data="searchResults" highlight-current-row @row-click="openArchive">
              <el-table-column prop="id" label="主档 ID" width="120" />
              <el-table-column prop="bah" label="病案号（BAH）" min-width="150" />
              <el-table-column prop="sjh" label="上架号（SJH）" min-width="150" />
              <el-table-column prop="patientName" label="患者" width="120" />
              <el-table-column prop="department" label="科室" min-width="140" />
              <el-table-column prop="scanCount" label="扫描数" width="100" />
              <el-table-column label="匹配方式" min-width="180">
                <template #default="{ row }">
                  <el-tag :type="row.matchType === 'EXACT' ? 'success' : 'warning'">
                    {{ row.matchType === 'EXACT' ? '精确匹配（EXACT）' : row.matchType === 'FORMAT_ONLY' ? '格式差异（FORMAT_ONLY）' : row.matchType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button text type="primary" :aria-label="`查看病案主档 ${row.id} 的关系`" @click.stop="openArchive(row as ArchiveSearchResult)">
                    查看关系
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </MrrSectionCard>
          <AppError v-else-if="searchError" message="病案查询失败，请重试" @retry="searchArchives" />
          <AppEmpty v-else-if="searchCompleted && !searchLoading" icon="i-lucide:search-x" description="当前条件未找到病案主档" />

          <div v-loading="detailLoading" class="archive-detail-stack">
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
                  <el-tag type="info" effect="plain">
                    只读关系视图
                  </el-tag>
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

              <MrrSectionCard class="section-card" title="跨表字段对比" description="区分完全一致、仅前导零差异、字段缺失和真实冲突">
                <el-table :data="relationDetail.comparisons" stripe>
                  <el-table-column prop="field" label="字段" width="120" />
                  <el-table-column prop="canonicalValue" label="mr_archive" min-width="150" />
                  <el-table-column prop="source" label="来源表" min-width="180" />
                  <el-table-column prop="sourceValue" label="来源值" min-width="150" />
                  <el-table-column label="判断" width="140">
                    <template #default="{ row }">
                      <MrrStatusTag :label="comparisonLabel(row.status) || row.status" :tone="comparisonTagType(row.status)" />
                    </template>
                  </el-table-column>
                </el-table>
              </MrrSectionCard>

              <el-tabs class="detail-tabs" type="border-card">
                <el-tab-pane :label="`统计记录 (${relationDetail.statistics.length})`">
                  <el-table :data="relationDetail.statistics" max-height="420">
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
            <AppEmpty v-else-if="!searchLoading && !searchError && (!searchCompleted || searchResults.length)" icon="i-lucide:git-fork" description="查询并选择一份病案后查看跨表关系" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="检查与异常" name="quality">
        <div class="quality-stack">
          <MrrSectionCard class="section-card" title="检查规则结果" description="关联、编号、文件元数据、重复扫描和迁移一致性">
            <template #actions>
              <MrrStatusTag v-if="qualitySummary?.running" label="检查运行中" tone="warning" />
            </template>
            <el-table :data="checks" stripe empty-text="尚未执行数据质量检查">
              <el-table-column label="规则" min-width="280">
                <template #default="{ row }">
                  <strong>{{ checkName(row as QualityCheck) }}</strong>
                  <div class="muted-code">
                    {{ checkCode(row as QualityCheck) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="严重程度" width="120">
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
              <el-table-column label="检查时间" min-width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.checkedAt ?? row.checked_at) }}
                </template>
              </el-table-column>
            </el-table>
          </MrrSectionCard>

          <MrrSectionCard class="section-card" title="异常样本" description="当前显示最近一次检查保存的有界样本，不直接返回全量异常">
            <template #actions>
              <div class="issue-filters">
                <el-select v-model="issueSeverity" aria-label="异常级别筛选" class="severity-select">
                  <el-option label="全部级别" value="ALL" />
                  <el-option label="严重" value="CRITICAL" />
                  <el-option label="警告" value="WARNING" />
                </el-select>
                <el-input v-model="issueKeyword" aria-label="异常关键字筛选" clearable placeholder="筛选规则、表、BAH 或 SJH" />
              </div>
            </template>
            <el-table :data="filteredIssues" stripe empty-text="当前没有异常样本">
              <el-table-column label="级别" width="95">
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
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="previewVisible" title="只读修复预览" size="min(520px, 100%)">
      <div v-loading="previewLoading" class="preview-content">
        <template v-if="repairPreview">
          <el-alert
            title="第一阶段不会修改任何数据库记录"
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
          <pre>{{ JSON.stringify(repairPreview.currentEntity, null, 2) }}</pre>

          <h4>候选病案主档</h4>
          <el-table :data="repairPreview.candidateArchives" empty-text="没有候选病案主档">
            <el-table-column prop="id" label="archive_id" width="110" />
            <el-table-column prop="bah" label="BAH" />
            <el-table-column prop="sjh" label="SJH" />
            <el-table-column prop="matchType" label="匹配方式" width="120" />
          </el-table>
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

.issue-filters,
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

.relation-grid {
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

.relation-card {
  min-width: 0;
  border-radius: var(--mrr-radius-card);
}

.relation-card__header > div {
  display: grid;
  gap: var(--mrr-space-1);
}

.relation-card code,
.muted-code {
  font-size: var(--mrr-font-size-xs);
  color: var(--el-text-color-secondary);
}

.relation-card p {
  min-height: 42px;
  margin: var(--mrr-space-4) 0;
  color: var(--el-text-color-secondary);
}

.coverage-row strong {
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.relation-card dl {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(96px, 1fr));
  gap: var(--mrr-space-3);
  margin: var(--mrr-space-4) 0;
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

.severity-select {
  width: 180px;
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

.issue-filters {
  flex: 1 1 360px;
  flex-wrap: wrap;
  min-width: 0;
  max-width: 100%;
}

.issue-filters .el-input {
  flex: 1 1 230px;
  min-width: 0;
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

@media (max-width: 760px) {
  .issue-filters,
  .search-row {
    flex-direction: column;
    align-items: stretch;
  }

  .search-field--type,
  .search-field--value {
    flex: 0 1 auto;
  }

  .issue-filters {
    flex-wrap: wrap;
    width: 100%;
  }

  .issue-filters,
  .issue-filters .el-input {
    flex: 0 1 auto;
  }

  .relation-grid,
  .relation-count-grid {
    grid-template-columns: 1fr;
  }

  .search-type,
  .severity-select {
    width: 100%;
  }
}
</style>

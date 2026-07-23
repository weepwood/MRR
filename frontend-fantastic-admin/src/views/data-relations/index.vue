<script setup lang="ts">
/* eslint-disable antfu/if-newline, curly */
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
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'

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
const selectedArchiveId = ref<number | null>(null)
const relationDetail = ref<ArchiveRelationDetail | null>(null)
const issueSeverity = ref('ALL')
const issueKeyword = ref('')
const repairPreview = ref<RepairPreview | null>(null)
const previewVisible = ref(false)

const issueActions: MrrTableAction[] = [
  {
    key: 'locate',
    label: '定位病案',
    icon: 'i-ri:map-pin-line',
    tone: 'primary',
    placement: 'inline',
  },
  {
    key: 'preview',
    label: '修复预览',
    icon: 'i-ri:tools-line',
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
  return numberValue(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function formatPercent(value: unknown) {
  return `${formatNumber(value)}%`
}

function formatDateTime(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN')
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
  if (status === 'HEALTHY') return 'success'
  if (status === 'WARNING' || status === 'LEGACY') return 'warning'
  return 'danger'
}

function comparisonTagType(status: ComparisonStatus) {
  if (status === 'EXACT') return 'success'
  if (status === 'FORMAT_ONLY' || status === 'NO_CANONICAL') return 'warning'
  if (status === 'MISSING') return 'info'
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

  searchLoading.value = true
  relationDetail.value = null
  selectedArchiveId.value = null
  try {
    const response = await searchDataRelationArchives(searchType.value, value, 20)
    searchResults.value = response.data ?? []
    if (searchResults.value.length === 1) {
      await openArchive(searchResults.value[0])
    }
    else if (searchResults.value.length === 0) {
      ElMessage.info('没有找到匹配的病案主档')
    }
  }
  finally {
    searchLoading.value = false
  }
}

async function openArchive(row: ArchiveSearchResult) {
  selectedArchiveId.value = row.id
  detailLoading.value = true
  activeTab.value = 'archive'
  try {
    const response = await getArchiveRelationDetail(row.id)
    relationDetail.value = response.data ?? null
  }
  finally {
    detailLoading.value = false
  }
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
  <div class="relation-workbench">
    <header class="page-header">
      <div>
        <p class="eyebrow">
          Data Governance
        </p>
        <h2>数据关系工作台</h2>
        <p class="subtitle">
          以 mr_archive 为中心查看统计、患者、扫描、装箱和迁移数据，发现缺失关联与字段冲突。第一阶段仅提供只读检查和修复预览。
        </p>
      </div>
      <div class="page-actions">
        <el-button :loading="overviewLoading" @click="loadDashboard">
          刷新
        </el-button>
        <el-button type="primary" :loading="checking" @click="runChecks">
          执行检查
        </el-button>
      </div>
    </header>

    <section class="metric-grid" aria-label="数据关系健康总览">
      <el-card shadow="never" class="metric-card">
        <span>关系健康度</span>
        <strong>{{ formatPercent(overview?.healthScore) }}</strong>
        <small>不包含尚无 archive_id 的患者表</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>病案主档</span>
        <strong>{{ formatNumber(overview?.archiveCount) }}</strong>
        <small>mr_archive 当前记录数</small>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--danger">
        <span>严重异常</span>
        <strong>{{ formatNumber(latestRun?.criticalCount ?? latestRun?.critical_count) }}</strong>
        <small>最近一次质量检查结果</small>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--warning">
        <span>警告异常</span>
        <strong>{{ formatNumber(latestRun?.warningCount ?? latestRun?.warning_count) }}</strong>
        <small>{{ latestRun ? formatDateTime(latestRun.completedAt ?? latestRun.completed_at) : '尚未执行检查' }}</small>
      </el-card>
    </section>

    <el-tabs v-model="activeTab" class="workbench-tabs">
      <el-tab-pane label="关系总览" name="overview">
        <div v-loading="overviewLoading" class="overview-stack">
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
                <el-tag :type="relationTagType(relation.status)" effect="light">
                  {{ relation.status }}
                </el-tag>
              </div>
              <p>{{ relation.relation }}</p>
              <div class="coverage-row">
                <strong>{{ relation.coverageIncluded ? formatPercent(relation.coverage) : '遗留关联' }}</strong>
                <span v-if="relation.estimated">估算值</span>
              </div>
              <el-progress
                v-if="relation.coverageIncluded"
                :percentage="Math.min(100, Math.max(0, relation.coverage))"
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
              <small>来源：{{ relation.source }}</small>
            </el-card>
          </section>

          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>关联检查结果</strong>
                  <span>最近一次运行中与 archive_id、孤立数据和编号冲突相关的检查</span>
                </div>
              </div>
            </template>
            <el-table :data="overview?.relationChecks ?? []" stripe empty-text="尚无关联检查结果">
              <el-table-column label="检查项" min-width="260">
                <template #default="{ row }">
                  <strong>{{ checkName(row) }}</strong>
                  <div class="muted-code">
                    {{ checkCode(row) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="级别" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.severity === 'CRITICAL' ? 'danger' : 'warning'">
                    {{ row.severity }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="异常数量" width="130">
                <template #default="{ row }">
                  {{ formatNumber(checkIssueCount(row)) }}
                </template>
              </el-table-column>
              <el-table-column label="检查时间" min-width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.checkedAt ?? row.checked_at) }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>

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
          <el-card shadow="never" class="section-card search-card">
            <div class="search-row">
              <el-select v-model="searchType" class="search-type" aria-label="查询类型">
                <el-option label="病案主档 ID" value="ARCHIVE_ID" />
                <el-option label="病案号 BAH" value="BAH" />
                <el-option label="上架号 SJH" value="SJH" />
              </el-select>
              <el-input
                v-model="searchValue"
                clearable
                placeholder="输入 archive_id、病案号或上架号"
                @keyup.enter="searchArchives"
              />
              <el-button type="primary" :loading="searchLoading" @click="searchArchives">
                查询
              </el-button>
            </div>
          </el-card>

          <el-card v-if="searchResults.length" shadow="never" class="section-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>病案主档候选</strong>
                  <span>精确匹配优先，补零差异会标记为 FORMAT_ONLY</span>
                </div>
              </div>
            </template>
            <el-table :data="searchResults" highlight-current-row @row-click="openArchive">
              <el-table-column prop="id" label="archive_id" width="120" />
              <el-table-column prop="bah" label="BAH" width="130" />
              <el-table-column prop="sjh" label="SJH" width="130" />
              <el-table-column prop="patientName" label="患者" width="120" />
              <el-table-column prop="department" label="科室" min-width="140" />
              <el-table-column prop="scanCount" label="扫描数" width="100" />
              <el-table-column label="匹配方式" width="130">
                <template #default="{ row }">
                  <el-tag :type="row.matchType === 'EXACT' ? 'success' : 'warning'">
                    {{ row.matchType }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <div v-loading="detailLoading">
            <template v-if="relationDetail">
              <el-card shadow="never" class="section-card archive-header-card">
                <div class="archive-header">
                  <div>
                    <span>病案主档 #{{ relationDetail.archive.id }}</span>
                    <h3>{{ relationDetail.archive.patientName || '未记录患者姓名' }}</h3>
                    <p>
                      BAH：{{ relationDetail.archive.bah || '—' }} ·
                      SJH：{{ relationDetail.archive.sjh || '—' }} ·
                      科室：{{ relationDetail.archive.department || '—' }}
                    </p>
                  </div>
                  <el-tag type="info" effect="plain">
                    只读关系视图
                  </el-tag>
                </div>
              </el-card>

              <section class="relation-count-grid">
                <el-card shadow="never">
                  <span>统计记录</span><strong>{{ relationDetail.statistics.length }}</strong>
                </el-card>
                <el-card shadow="never">
                  <span>患者记录</span><strong>{{ relationDetail.patients.length }}</strong>
                </el-card>
                <el-card shadow="never">
                  <span>装箱记录</span><strong>{{ relationDetail.boxes.length }}</strong>
                </el-card>
                <el-card shadow="never">
                  <span>有效扫描</span><strong>{{ formatNumber(relationDetail.scanSummary.activeCount) }}</strong>
                </el-card>
                <el-card shadow="never">
                  <span>未关联候选扫描</span><strong>{{ formatNumber(relationDetail.scanSummary.unlinkedCandidateCount) }}</strong>
                </el-card>
              </section>

              <el-alert
                v-for="warning in relationDetail.warnings"
                :key="warning"
                :title="warning"
                type="warning"
                :closable="false"
                show-icon
              />

              <el-card shadow="never" class="section-card">
                <template #header>
                  <div class="card-header">
                    <div>
                      <strong>跨表字段对比</strong>
                      <span>区分完全一致、仅前导零差异、字段缺失和真实冲突</span>
                    </div>
                  </div>
                </template>
                <el-table :data="relationDetail.comparisons" stripe>
                  <el-table-column prop="field" label="字段" width="120" />
                  <el-table-column prop="canonicalValue" label="mr_archive" min-width="150" />
                  <el-table-column prop="source" label="来源表" min-width="180" />
                  <el-table-column prop="sourceValue" label="来源值" min-width="150" />
                  <el-table-column label="判断" width="140">
                    <template #default="{ row }">
                      <el-tag :type="comparisonTagType(row.status)">
                        {{ comparisonLabel(row.status) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>

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
            <el-empty v-else description="查询并选择一份病案后查看跨表关系" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="检查与异常" name="quality">
        <div class="quality-stack">
          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>检查规则结果</strong>
                  <span>关联、编号、文件元数据、重复扫描和迁移一致性</span>
                </div>
                <el-tag v-if="qualitySummary?.running" type="warning">
                  检查运行中
                </el-tag>
              </div>
            </template>
            <el-table :data="checks" stripe empty-text="尚未执行数据质量检查">
              <el-table-column label="规则" min-width="280">
                <template #default="{ row }">
                  <strong>{{ checkName(row) }}</strong>
                  <div class="muted-code">
                    {{ checkCode(row) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="严重程度" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.severity === 'CRITICAL' ? 'danger' : 'warning'">
                    {{ row.severity }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="异常数" width="120">
                <template #default="{ row }">
                  {{ formatNumber(checkIssueCount(row)) }}
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
          </el-card>

          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="issue-header">
                <div>
                  <strong>异常样本</strong>
                  <span>当前显示最近一次检查保存的有界样本，不直接返回全量异常</span>
                </div>
                <div class="issue-filters">
                  <el-select v-model="issueSeverity" class="severity-select">
                    <el-option label="全部级别" value="ALL" />
                    <el-option label="严重" value="CRITICAL" />
                    <el-option label="警告" value="WARNING" />
                  </el-select>
                  <el-input v-model="issueKeyword" clearable placeholder="筛选规则、表、BAH 或 SJH" />
                </div>
              </div>
            </template>
            <el-table :data="filteredIssues" stripe empty-text="当前没有异常样本">
              <el-table-column label="级别" width="95">
                <template #default="{ row }">
                  <el-tag :type="row.severity === 'CRITICAL' ? 'danger' : 'warning'">
                    {{ row.severity === 'CRITICAL' ? '严重' : '警告' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="规则" min-width="250">
                <template #default="{ row }">
                  <strong>{{ issueName(row) }}</strong>
                  <div class="muted-code">
                    {{ issueCode(row) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="记录" min-width="180">
                <template #default="{ row }">
                  {{ issueEntityType(row) }} #{{ issueEntityId(row) }}
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
                    @select="handleIssueAction($event, row)"
                  />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="previewVisible" title="只读修复预览" size="520px">
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
  </div>
</template>

<style scoped>
.relation-workbench {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.page-header,
.page-actions,
.card-header,
.issue-header,
.issue-filters,
.search-row,
.archive-header,
.relation-card__header,
.coverage-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.page-header,
.card-header,
.issue-header,
.archive-header,
.relation-card__header,
.coverage-row {
  justify-content: space-between;
}

.page-header {
  align-items: flex-start;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h2,
h3,
h4,
p {
  margin-top: 0;
}

h2 {
  margin-bottom: 8px;
  font-size: 26px;
}

.subtitle {
  max-width: 860px;
  margin-bottom: 0;
  color: var(--el-text-color-secondary);
}

.metric-grid,
.relation-grid,
.relation-count-grid {
  display: grid;
  gap: 16px;
}

.metric-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-card :deep(.el-card__body) {
  display: grid;
  gap: 8px;
}

.metric-card span,
.relation-count-grid span {
  color: var(--el-text-color-secondary);
}

.metric-card strong {
  font-size: 28px;
}

.metric-card small,
.relation-card small {
  color: var(--el-text-color-secondary);
}

.metric-card--danger strong {
  color: var(--el-color-danger);
}

.metric-card--warning strong {
  color: var(--el-color-warning);
}

.workbench-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.overview-stack,
.archive-stack,
.quality-stack,
.preview-content {
  display: grid;
  gap: 16px;
}

.relation-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.relation-card {
  min-width: 0;
  border-radius: 12px;
}

.relation-card__header > div,
.card-header > div,
.issue-header > div:first-child {
  display: grid;
  gap: 4px;
}

.relation-card code,
.muted-code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.relation-card p {
  min-height: 42px;
  margin: 16px 0;
  color: var(--el-text-color-secondary);
}

.coverage-row strong {
  font-size: 24px;
}

.relation-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 16px 0;
}

.relation-card dl div {
  display: grid;
  gap: 4px;
}

.relation-card dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.relation-card dd {
  margin: 0;
  font-weight: 600;
}

.section-card {
  overflow: hidden;
  border-radius: 12px;
}

.search-row .el-input {
  flex: 1;
}

.search-type,
.severity-select {
  width: 180px;
}

.archive-header-card {
  border-left: 4px solid var(--el-color-primary);
}

.archive-header h3 {
  margin: 4px 0;
  font-size: 22px;
}

.archive-header p {
  margin-bottom: 0;
  color: var(--el-text-color-secondary);
}

.relation-count-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.relation-count-grid :deep(.el-card__body) {
  display: grid;
  gap: 6px;
}

.relation-count-grid strong {
  font-size: 22px;
}

.detail-tabs {
  border-radius: 12px;
}

.issue-header {
  align-items: flex-start;
}

.issue-filters {
  width: min(520px, 100%);
}

.issue-filters .el-input {
  flex: 1;
}

.preview-content pre {
  padding: 12px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

@media (width <= 1200px) {
  .metric-grid,
  .relation-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .relation-count-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 760px) {
  .page-header,
  .issue-header,
  .search-row {
    flex-direction: column;
    align-items: stretch;
  }

  .page-actions,
  .issue-filters {
    flex-wrap: wrap;
    width: 100%;
  }

  .metric-grid,
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

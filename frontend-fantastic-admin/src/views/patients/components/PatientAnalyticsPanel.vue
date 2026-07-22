<script setup lang="ts">
import type {
  PatientAnalyticsSummary,
  PatientMultiRecordGroup,
  PatientRecord,
} from '@/api/modules/patients'
import type { ECOption } from '@/plugins/echarts'
import { Calendar, OfficeBuilding, Refresh, User, WarningFilled } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import {
  getMissingIdCardPatients,
  getPatientAnalyticsSummary,
  getPatientMultiRecordGroups,
} from '@/api/modules/patients'
import MrrChart from '@/components/charts/MrrChart.vue'

const props = withDefaults(defineProps<{
  refreshKey?: number
}>(), {
  refreshKey: 0,
})

const currentYear = new Date().getFullYear()
const selectedYear = ref(currentYear)
const yearOptions = Array.from({ length: 10 }, (_, index) => currentYear - index)

const summaryLoading = ref(false)
const summaryError = ref('')
const summary = ref<PatientAnalyticsSummary>()

const activeDetailTab = ref<'missing' | 'multi'>('missing')
const missingLoading = ref(false)
const missingRecords = ref<PatientRecord[]>([])
const missingPage = ref(1)
const missingSize = ref(10)
const missingTotal = ref(0)

const multiLoading = ref(false)
const multiGroups = ref<PatientMultiRecordGroup[]>([])
const multiPage = ref(1)
const multiSize = ref(10)
const multiTotal = ref(0)
const includeSuspected = ref(true)

const trendOption = computed<ECOption>(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: value => `${Number(value || 0)} 份`,
  },
  grid: {
    left: 48,
    right: 24,
    top: 24,
    bottom: 58,
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: summary.value?.dateCounts.map(item => item.date) ?? [],
    axisLabel: {
      formatter: (value: string) => value.slice(5),
    },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    name: '病案数',
  },
  dataZoom: [
    { type: 'inside' },
    { type: 'slider', height: 18, bottom: 8 },
  ],
  series: [
    {
      name: '当日病案',
      type: 'line',
      smooth: true,
      showSymbol: false,
      areaStyle: {},
      data: summary.value?.dateCounts.map(item => item.count) ?? [],
    },
  ],
}))

const departmentOption = computed<ECOption>(() => {
  const items = [...(summary.value?.departmentCounts ?? [])].slice(0, 15).reverse()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: value => `${Number(value || 0)} 份`,
    },
    grid: {
      left: 110,
      right: 28,
      top: 20,
      bottom: 34,
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      name: '病案数',
    },
    yAxis: {
      type: 'category',
      data: items.map(item => item.department),
      axisLabel: {
        width: 92,
        overflow: 'truncate',
      },
    },
    series: [
      {
        name: '科室病案',
        type: 'bar',
        barMaxWidth: 24,
        data: items.map(item => item.count),
      },
    ],
  }
})

async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    const response = await getPatientAnalyticsSummary(selectedYear.value)
    summary.value = response.data
  }
  catch (error: unknown) {
    summary.value = undefined
    summaryError.value = error instanceof Error ? error.message : '患者统计加载失败'
  }
  finally {
    summaryLoading.value = false
  }
}

async function loadMissingRecords() {
  missingLoading.value = true
  try {
    const response = await getMissingIdCardPatients({
      page: missingPage.value,
      size: missingSize.value,
    })
    missingRecords.value = response.data?.list ?? []
    missingTotal.value = Number(response.data?.total || 0)
  }
  finally {
    missingLoading.value = false
  }
}

async function loadMultiGroups() {
  multiLoading.value = true
  try {
    const response = await getPatientMultiRecordGroups({
      page: multiPage.value,
      size: multiSize.value,
      includeSuspected: includeSuspected.value,
    })
    multiGroups.value = response.data?.list ?? []
    multiTotal.value = Number(response.data?.total || 0)
  }
  finally {
    multiLoading.value = false
  }
}

function reloadAll() {
  void loadSummary()
  void loadMissingRecords()
  void loadMultiGroups()
}

function handleMissingPageChange(page: number) {
  missingPage.value = page
  void loadMissingRecords()
}

function handleMissingSizeChange(size: number) {
  missingSize.value = size
  missingPage.value = 1
  void loadMissingRecords()
}

function handleMultiPageChange(page: number) {
  multiPage.value = page
  void loadMultiGroups()
}

function handleMultiSizeChange(size: number) {
  multiSize.value = size
  multiPage.value = 1
  void loadMultiGroups()
}

watch(selectedYear, () => {
  void loadSummary()
})

watch(includeSuspected, () => {
  multiPage.value = 1
  void loadMultiGroups()
})

watch(() => props.refreshKey, reloadAll)

onMounted(reloadAll)
</script>

<template>
  <section class="analytics-panel">
    <div class="analytics-heading">
      <div>
        <h3>患者数据概览</h3>
        <p>统计身份证完整性、同一患者多病案、年度日期趋势与科室分布。</p>
      </div>
      <div class="analytics-actions">
        <el-select v-model="selectedYear" class="year-select" aria-label="统计年份">
          <el-option v-for="year in yearOptions" :key="year" :label="`${year} 年`" :value="year" />
        </el-select>
        <el-button :icon="Refresh" :loading="summaryLoading" @click="reloadAll">
          刷新统计
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="summaryError"
      :title="summaryError"
      type="error"
      show-icon
      :closable="false"
    />

    <div v-loading="summaryLoading" class="metric-grid">
      <article class="metric-card">
        <span class="metric-icon"><el-icon><User /></el-icon></span>
        <div>
          <p>患者数据行</p>
          <strong>{{ summary?.totalRecords ?? 0 }}</strong>
          <small>去重病案号 {{ summary?.totalArchives ?? 0 }} 个</small>
        </div>
      </article>
      <article class="metric-card">
        <span class="metric-icon"><el-icon><Calendar /></el-icon></span>
        <div>
          <p>{{ selectedYear }} 年病案</p>
          <strong>{{ summary?.yearArchives ?? 0 }}</strong>
          <small>按入院日期去重统计</small>
        </div>
      </article>
      <article class="metric-card metric-card--warning">
        <span class="metric-icon"><el-icon><WarningFilled /></el-icon></span>
        <div>
          <p>身份证号为空</p>
          <strong>{{ summary?.missingIdCardRecords ?? 0 }}</strong>
          <small>需要补录或人工核对</small>
        </div>
      </article>
      <article class="metric-card">
        <span class="metric-icon"><el-icon><OfficeBuilding /></el-icon></span>
        <div>
          <p>同一患者多病案</p>
          <strong>{{ summary?.confirmedMultiRecordGroups ?? 0 }}</strong>
          <small>另有疑似 {{ summary?.suspectedMultiRecordGroups ?? 0 }} 组</small>
        </div>
      </article>
    </div>

    <div class="chart-grid">
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-title">
            <span>{{ selectedYear }} 年每日病案趋势</span>
            <small>可拖动底部滑块查看具体日期</small>
          </div>
        </template>
        <MrrChart
          :option="trendOption"
          :loading="summaryLoading"
          :empty="!summary?.dateCounts.some(item => item.count > 0)"
          empty-description="该年度暂无可解析入院日期的病案"
          height="340px"
          aria-label="患者年度每日病案趋势图"
        />
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-title">
            <span>{{ selectedYear }} 年科室病案分布</span>
            <small>展示病案数最多的 15 个科室</small>
          </div>
        </template>
        <MrrChart
          :option="departmentOption"
          :loading="summaryLoading"
          :empty="!summary?.departmentCounts.length"
          empty-description="该年度暂无科室统计数据"
          height="340px"
          aria-label="患者科室病案分布图"
        />
      </el-card>
    </div>

    <el-card shadow="never" class="detail-card">
      <el-tabs v-model="activeDetailTab">
        <el-tab-pane name="missing">
          <template #label>
            身份证为空病案（{{ summary?.missingIdCardRecords ?? missingTotal }}）
          </template>
          <el-table v-loading="missingLoading" :data="missingRecords" stripe>
            <el-table-column prop="bah" label="病案号" min-width="140">
              <template #default="{ row }">
                <router-link
                  v-if="row.bah"
                  :to="{ path: '/archive', query: { bah: row.bah, from: 'patients' } }"
                  target="_blank"
                  class="record-link"
                >
                  {{ row.bah }}
                </router-link>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="姓名" min-width="100" />
            <el-table-column prop="ruyuan" label="入院日期" min-width="120" />
            <el-table-column prop="admissiontime" label="入院时间" min-width="170" />
            <el-table-column prop="department" label="科室" min-width="140" show-overflow-tooltip />
            <el-table-column prop="bingqu" label="病区" min-width="130" show-overflow-tooltip />
            <el-table-column prop="chuangwei" label="床位" min-width="100" />
          </el-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="missingPage"
              v-model:page-size="missingSize"
              :total="missingTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleMissingPageChange"
              @size-change="handleMissingSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="multi">
          <template #label>
            同一患者多病案（{{ multiTotal }}）
          </template>
          <div class="multi-toolbar">
            <el-switch v-model="includeSuspected" active-text="包含仅姓名相同的疑似分组" />
            <span>身份证号一致为高可信；仅姓名相同可能是同名患者，需要人工核对。</span>
          </div>
          <el-table v-loading="multiLoading" :data="multiGroups" stripe>
            <el-table-column label="识别依据" width="130">
              <template #default="{ row }">
                <el-tag :type="row.matchType === 'IDCARD' ? 'success' : 'warning'">
                  {{ row.matchType === 'IDCARD' ? '身份证一致' : '仅姓名相同' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="patientName" label="患者姓名" min-width="140" show-overflow-tooltip />
            <el-table-column prop="maskedIdCard" label="身份证号" min-width="190">
              <template #default="{ row }">
                {{ row.maskedIdCard || '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="archiveCount" label="病案数" width="90" />
            <el-table-column label="病案号" min-width="280">
              <template #default="{ row }">
                <div class="archive-tags">
                  <router-link
                    v-for="archiveNumber in row.archiveNumbers"
                    :key="archiveNumber"
                    :to="{ path: '/archive', query: { bah: archiveNumber, from: 'patients' } }"
                    target="_blank"
                    class="record-link"
                  >
                    {{ archiveNumber }}
                  </router-link>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="入院日期范围" min-width="210">
              <template #default="{ row }">
                {{ row.firstAdmissionDate || '—' }} ～ {{ row.lastAdmissionDate || '—' }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="multiPage"
              v-model:page-size="multiSize"
              :total="multiTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleMultiPageChange"
              @size-change="handleMultiSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<style scoped>
.analytics-panel {
  display: grid;
  gap: 16px;
}

.analytics-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.analytics-heading h3 {
  margin: 0;
  font-size: 20px;
}

.analytics-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.analytics-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.year-select {
  width: 112px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  min-height: 112px;
}

.metric-card {
  display: flex;
  gap: 14px;
  align-items: center;
  min-width: 0;
  padding: 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
}

.metric-card--warning {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}

.metric-icon {
  display: grid;
  flex: 0 0 42px;
  place-items: center;
  width: 42px;
  height: 42px;
  font-size: 21px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 12px;
}

.metric-card--warning .metric-icon {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-8);
}

.metric-card p {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metric-card strong {
  display: block;
  font-size: 26px;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

.metric-card small {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  gap: 16px;
}

.chart-card :deep(.el-card__header) {
  padding: 15px 18px;
}

.card-title {
  display: flex;
  gap: 10px;
  align-items: baseline;
  justify-content: space-between;
}

.card-title span {
  font-weight: 600;
}

.card-title small {
  color: var(--el-text-color-placeholder);
}

.detail-card :deep(.el-card__body) {
  padding-top: 4px;
}

.multi-toolbar {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.record-link {
  color: var(--el-color-primary);
  text-decoration: none;
}

.record-link:hover {
  text-decoration: underline;
}

.archive-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
}

@media (width <= 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (width <= 720px) {
  .analytics-heading {
    flex-direction: column;
  }

  .analytics-actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .multi-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .card-title {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

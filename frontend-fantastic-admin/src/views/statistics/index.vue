<script setup lang="ts">
import {
  ArrowRight,
  Calendar,
  DataAnalysis,
  Document,
  Files,
  Refresh,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  getDashboardData,
  getStatisticsDateSummary,
  getStatisticsSummary,
} from '@/api/modules/statistics'

defineOptions({ name: 'StatisticsPage' })

interface TypeDisplayItem {
  type: string
  records: number
  pages: number
}

interface TrendDisplayItem {
  date: string
  records: number
  pages: number
}

const router = useRouter()
const loading = ref(false)
const summaryData = ref<any>({ total: {}, byType: [] })
const dashboardData = ref<any>({ recentTrend: [], topBAH: [] })
const dateSummaryData = ref<any[]>([])

const totalRecords = computed(() => Number(summaryData.value.total?.totalRecords || 0))
const totalPages = computed(() => Number(summaryData.value.total?.totalPages || 0))
const uniqueBahCount = computed(() => Number(summaryData.value.uniqueBAHCount || 0))

const typeList = computed<TypeDisplayItem[]>(() => {
  const source = Array.isArray(summaryData.value.byType) ? summaryData.value.byType : []
  return source
    .map((item: any) => ({
      type: String(item?.type || '未分类'),
      records: Number(item?.recordCount || item?.totalRecords || 0),
      pages: Number(item?.totalPages || item?.pageCount || 0),
    }))
    .sort((a: TypeDisplayItem, b: TypeDisplayItem) => b.records - a.records)
})

const typeRecordTotal = computed(() => typeList.value.reduce((total, item) => total + item.records, 0))

const trendDates = computed<TrendDisplayItem[]>(() => {
  const source = Array.isArray(dateSummaryData.value) ? dateSummaryData.value.slice(-10) : []
  return source.map((item: any) => ({
    date: String(item?.date || '-'),
    records: Number(item?.recordCount || 0),
    pages: Number(item?.totalPages || 0),
  }))
})

const maxTrendPages = computed(() => Math.max(1, ...trendDates.value.map(item => item.pages)))
const recentRecordTotal = computed(() => trendDates.value.reduce((total, item) => total + item.records, 0))
const recentPageTotal = computed(() => trendDates.value.reduce((total, item) => total + item.pages, 0))
const averageDailyPages = computed(() => trendDates.value.length
  ? Math.round(recentPageTotal.value / trendDates.value.length)
  : 0)

const summaryCards = computed(() => [
  {
    label: '总记录数',
    value: totalRecords.value,
    note: '统计表内累计扫描记录',
    icon: Document,
    tone: 'blue',
  },
  {
    label: '总扫描页数',
    value: totalPages.value,
    note: '所有病案影像累计页数',
    icon: Files,
    tone: 'violet',
  },
  {
    label: '唯一病案号',
    value: uniqueBahCount.value,
    note: '已完成归档的病案数量',
    icon: DataAnalysis,
    tone: 'green',
  },
  {
    label: '趋势统计天数',
    value: trendDates.value.length,
    note: '当前展示的近期扫描周期',
    icon: Calendar,
    tone: 'amber',
  },
])

const latestStatisticsDate = computed(() => trendDates.value.at(-1)?.date || '暂无数据')

async function loadData() {
  loading.value = true
  try {
    const [summaryRes, dashboardRes, dateRes] = await Promise.all([
      getStatisticsSummary(),
      getDashboardData(),
      getStatisticsDateSummary(),
    ])
    summaryData.value = summaryRes.data || { total: {}, byType: [] }
    dashboardData.value = dashboardRes.data || { recentTrend: [], topBAH: [] }
    dateSummaryData.value = Array.isArray(dateRes.data) ? dateRes.data : []
  }
  catch (error: any) {
    ElMessage.error(error?.message || '统计数据加载失败')
  }
  finally {
    loading.value = false
  }
}

function formatNumber(value: number) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatShortDate(value: string) {
  const normalized = String(value || '').replace(/\//g, '-')
  const parts = normalized.split('-')
  return parts.length >= 3 ? `${parts.at(-2)}-${parts.at(-1)}` : normalized
}

function getTypePercentage(records: number) {
  const total = typeRecordTotal.value || totalRecords.value
  if (!total) { return 0 }
  return Math.min(100, Math.round((records / total) * 100))
}

function getTrendHeight(pages: number) {
  return `${Math.max(8, Math.round((pages / maxTrendPages.value) * 100))}%`
}

function goToDetail() {
  router.push('/statistics-detail')
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <section class="hero-panel">
      <div class="hero-orb hero-orb--one" />
      <div class="hero-orb hero-orb--two" />

      <div class="hero-content">
        <div class="hero-copy">
          <div class="eyebrow">
            <span class="status-dot" />
            Records Statistics
          </div>
          <h2>病案扫描数据统计</h2>
          <p class="subtitle">
            汇总病案扫描规模、类型分布与近期变化，快速定位异常数据波动。
          </p>
          <div class="hero-meta">
            <span>最新统计日期：{{ latestStatisticsDate }}</span>
            <span>{{ typeList.length }} 个病案类型</span>
          </div>
        </div>

        <div class="hero-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadData">
            刷新数据
          </el-button>
          <el-button type="primary" @click="goToDetail">
            查看统计明细
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </section>

    <section class="mrr-metric-grid" aria-label="核心统计指标">
      <el-card
        v-for="item in summaryCards"
        :key="item.label"
        shadow="never"
        class="mrr-metric-card"
        :class="`mrr-metric-card--${item.tone}`"
      >
        <div class="mrr-metric-card__icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">{{ item.label }}</span>
          <strong class="mrr-metric-card__value">{{ formatNumber(item.value) }}</strong>
          <p class="mrr-metric-card__note">
            {{ item.note }}
          </p>
        </div>
      </el-card>
    </section>

    <section class="analytics-grid">
      <article class="data-panel type-panel">
        <header class="panel-header">
          <div>
            <p class="panel-kicker">
              Category Overview
            </p>
            <h3>病案类型分布</h3>
            <p>按记录数量统计各扫描类型的占比与页数。</p>
          </div>
          <span class="panel-badge">{{ typeList.length }} 类</span>
        </header>

        <div v-loading="loading" class="type-list">
          <template v-if="typeList.length">
            <article v-for="(item, index) in typeList" :key="`${item.type}-${index}`" class="type-item">
              <div class="type-item__top">
                <div class="type-name">
                  <span class="type-index">{{ String(index + 1).padStart(2, '0') }}</span>
                  <strong>{{ item.type }}</strong>
                </div>
                <strong>{{ formatNumber(item.records) }} 条</strong>
              </div>
              <div class="progress-track" aria-hidden="true">
                <div class="progress-fill" :style="{ width: `${getTypePercentage(item.records)}%` }" />
              </div>
              <div class="type-item__footer">
                <span>{{ formatNumber(item.pages) }} 扫描页</span>
                <span>{{ getTypePercentage(item.records) }}%</span>
              </div>
            </article>
          </template>
          <el-empty v-else-if="!loading" description="暂无类型分布数据" :image-size="72" />
        </div>
      </article>

      <article class="data-panel trend-panel">
        <header class="panel-header">
          <div>
            <p class="panel-kicker">
              Recent Activity
            </p>
            <h3>近 10 日扫描趋势</h3>
            <p>以扫描页数展示每日工作量变化，辅助观察处理节奏。</p>
          </div>
          <span class="panel-badge panel-badge--success">按页数</span>
        </header>

        <div v-loading="loading" class="trend-content">
          <template v-if="trendDates.length">
            <div class="trend-chart" role="img" aria-label="近十日扫描页数柱状趋势图">
              <div v-for="item in trendDates" :key="item.date" class="trend-column">
                <span class="trend-value">{{ formatNumber(item.pages) }}</span>
                <div class="trend-bar-track">
                  <div
                    class="trend-bar"
                    :style="{ height: getTrendHeight(item.pages) }"
                    :title="`${item.date}：${formatNumber(item.pages)} 页，${formatNumber(item.records)} 条记录`"
                  >
                    <span />
                  </div>
                </div>
                <span class="trend-date">{{ formatShortDate(item.date) }}</span>
              </div>
            </div>

            <div class="trend-summary">
              <div>
                <span>近期记录</span>
                <strong>{{ formatNumber(recentRecordTotal) }}</strong>
              </div>
              <div>
                <span>近期页数</span>
                <strong>{{ formatNumber(recentPageTotal) }}</strong>
              </div>
              <div>
                <span>日均页数</span>
                <strong>{{ formatNumber(averageDailyPages) }}</strong>
              </div>
            </div>
          </template>
          <el-empty v-else-if="!loading" description="暂无趋势数据" :image-size="72" />
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
  padding-bottom: 20px;
}

.hero-panel {
  position: relative;
  min-height: 220px;
  padding: 34px 38px;
  overflow: hidden;
  color: var(--text-primary);
  background: linear-gradient(120deg, color-mix(in srgb, #2563eb 10%, var(--surface)) 0%, var(--surface) 48%, color-mix(in srgb, #7c3aed 8%, var(--surface)) 100%);
  border: 1px solid color-mix(in srgb, #2563eb 15%, var(--divider));
  border-radius: 22px;
  box-shadow: 0 16px 42px rgb(15 23 42 / 7%);
}

.hero-panel::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: "";
  background-image:
    linear-gradient(rgb(37 99 235 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(37 99 235 / 4%) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: linear-gradient(90deg, black, transparent 78%);
}

.hero-orb {
  position: absolute;
  pointer-events: none;
  border-radius: 50%;
  filter: blur(4px);
}

.hero-orb--one {
  top: -78px;
  right: 10%;
  width: 220px;
  height: 220px;
  background: rgb(37 99 235 / 11%);
}

.hero-orb--two {
  right: -55px;
  bottom: -115px;
  width: 260px;
  height: 260px;
  background: rgb(124 58 237 / 9%);
}

.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 32px;
  align-items: center;
  justify-content: space-between;
  min-height: 150px;
}

.hero-copy {
  max-width: 720px;
}

.eyebrow,
.panel-kicker {
  margin: 0;
  font-size: 11px;
  font-weight: 800;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0.16em;
}

.eyebrow {
  display: inline-flex;
  gap: 9px;
  align-items: center;
  padding: 6px 10px;
  background: rgb(37 99 235 / 8%);
  border: 1px solid rgb(37 99 235 / 12%);
  border-radius: 999px;
}

.status-dot {
  width: 7px;
  height: 7px;
  background: #22c55e;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgb(34 197 94 / 12%);
}

h2 {
  margin: 18px 0 0;
  font-size: clamp(28px, 3vw, 38px);
  line-height: 1.2;
  letter-spacing: -0.04em;
}

.subtitle {
  max-width: 660px;
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 22px;
}

.hero-meta span {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--surface) 75%, transparent);
  border: 1px solid var(--divider);
  border-radius: 8px;
}

.hero-actions {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
}

.hero-actions :deep(.el-button) {
  min-height: 40px;
  padding-inline: 17px;
  border-radius: 10px;
}

.analytics-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 20px;
}

.data-panel {
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 20px;
  box-shadow: 0 10px 32px rgb(15 23 42 / 5%);
}

.panel-header {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--divider);
}

.panel-header h3 {
  margin: 7px 0 0;
  font-size: 18px;
  letter-spacing: -0.02em;
}

.panel-header p:not(.panel-kicker) {
  margin: 7px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.panel-badge {
  flex-shrink: 0;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: 700;
  color: #2563eb;
  background: rgb(37 99 235 / 8%);
  border: 1px solid rgb(37 99 235 / 12%);
  border-radius: 999px;
}

.panel-badge--success {
  color: #047857;
  background: rgb(5 150 105 / 8%);
  border-color: rgb(5 150 105 / 12%);
}

.type-list {
  display: grid;
  gap: 10px;
  min-height: 310px;
  padding: 18px 20px 22px;
}

.type-item {
  padding: 14px 15px;
  background: color-mix(in srgb, var(--surface-alt) 62%, var(--surface));
  border: 1px solid transparent;
  border-radius: 13px;
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.type-item:hover {
  border-color: color-mix(in srgb, #2563eb 16%, var(--divider));
  transform: translateX(2px);
}

.type-item__top,
.type-item__footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.type-item__top > strong {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}

.type-name {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.type-name strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  white-space: nowrap;
}

.type-index {
  font-size: 10px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: #2563eb;
}

.progress-track {
  height: 6px;
  margin-top: 12px;
  overflow: hidden;
  background: color-mix(in srgb, var(--divider) 62%, transparent);
  border-radius: 999px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #60a5fa);
  border-radius: inherit;
  transition: width 0.45s ease;
}

.type-item__footer {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-secondary);
}

.trend-content {
  min-height: 310px;
  padding: 20px 22px 22px;
}

.trend-chart {
  display: grid;
  grid-template-columns: repeat(10, minmax(28px, 1fr));
  gap: 9px;
  align-items: end;
  min-height: 218px;
  padding: 16px 8px 10px;
  background: repeating-linear-gradient(to top, transparent 0, transparent 43px, color-mix(in srgb, var(--divider) 55%, transparent) 44px);
  border-bottom: 1px solid var(--divider);
}

.trend-column {
  display: grid;
  grid-template-rows: 22px 150px 22px;
  gap: 7px;
  min-width: 0;
  text-align: center;
}

.trend-value {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  white-space: nowrap;
}

.trend-bar-track {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 150px;
}

.trend-bar {
  position: relative;
  width: min(28px, 74%);
  min-height: 8px;
  overflow: hidden;
  background: linear-gradient(180deg, #60a5fa 0%, #2563eb 100%);
  border-radius: 7px 7px 3px 3px;
  box-shadow: 0 7px 14px rgb(37 99 235 / 16%);
  transition: height 0.35s ease, transform 0.18s ease, filter 0.18s ease;
}

.trend-bar::after {
  position: absolute;
  inset: 0;
  content: "";
  background: linear-gradient(90deg, rgb(255 255 255 / 18%), transparent 60%);
}

.trend-bar:hover {
  filter: saturate(1.15);
  transform: translateY(-3px);
}

.trend-date {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  white-space: nowrap;
}

.trend-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 16px;
}

.trend-summary > div {
  padding: 12px 14px;
  background: color-mix(in srgb, var(--surface-alt) 60%, var(--surface));
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.trend-summary span,
.trend-summary strong {
  display: block;
}

.trend-summary span {
  font-size: 10px;
  color: var(--text-secondary);
}

.trend-summary strong {
  margin-top: 6px;
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}

@media (width <= 1180px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}

@media (width <= 760px) {
  .hero-panel {
    padding: 26px 22px;
  }

  .hero-content {
    display: grid;
  }

  .hero-actions {
    flex-wrap: wrap;
  }

  .panel-header {
    padding-inline: 18px;
  }

  .type-list,
  .trend-content {
    padding-inline: 16px;
  }

  .trend-chart {
    gap: 5px;
    overflow-x: auto;
  }

  .trend-column {
    min-width: 34px;
  }
}

@media (width <= 560px) {
  .hero-actions :deep(.el-button) {
    flex: 1;
  }

  .hero-meta {
    display: grid;
  }

  .trend-summary {
    grid-template-columns: 1fr;
  }

  .ranking-header {
    align-items: flex-start;
  }
}
</style>

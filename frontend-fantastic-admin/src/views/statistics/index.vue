<script setup lang="ts">
import type { MrrBarSeries, MrrLineSeries } from '@/types/chart'
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
import { MrrChartCard, MrrDualAxisChart } from '@/components/charts'
import {
  getStatisticsDateSummary,
  getStatisticsSummary,
} from '@/api/modules/statistics'

defineOptions({ name: 'StatisticsPage' })

interface TrendDisplayItem {
  date: string
  records: number
  pages: number
}

const router = useRouter()
const loading = ref(false)
const summaryData = ref<any>({ total: {} })
const dateSummaryData = ref<any[]>([])

const totalRecords = computed(() => Number(summaryData.value.total?.totalRecords || 0))
const totalPages = computed(() => Number(summaryData.value.total?.totalPages || 0))
const uniqueBahCount = computed(() => Number(summaryData.value.uniqueBAHCount || 0))

const trendDates = computed<TrendDisplayItem[]>(() => {
  const source = Array.isArray(dateSummaryData.value) ? dateSummaryData.value : []
  return source
    .map((item: any) => ({
      date: String(item?.date || '-'),
      records: Number(item?.recordCount || 0),
      pages: Number(item?.totalPages || item?.pages || 0),
    }))
    .sort((a: TrendDisplayItem, b: TrendDisplayItem) => a.date.localeCompare(b.date))
})

const trendCategories = computed(() => trendDates.value.map(item => formatShortDate(item.date)))
const trendBars = computed<MrrBarSeries[]>(() => [{
  name: '扫描页数',
  data: trendDates.value.map(item => item.pages),
}])
const trendLines = computed<MrrLineSeries[]>(() => [{
  name: '扫描记录数',
  data: trendDates.value.map(item => item.records),
  smooth: true,
}])

const trendRecordTotal = computed(() => trendDates.value.reduce((total, item) => total + item.records, 0))
const trendPageTotal = computed(() => trendDates.value.reduce((total, item) => total + item.pages, 0))
const averageDailyPages = computed(() => trendDates.value.length
  ? Math.round(trendPageTotal.value / trendDates.value.length)
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
    note: '可通过滚轮缩放查看统计周期',
    icon: Calendar,
    tone: 'amber',
  },
])

const latestStatisticsDate = computed(() => trendDates.value.at(-1)?.date || '暂无数据')

async function loadData() {
  loading.value = true
  try {
    const [summaryRes, dateRes] = await Promise.all([
      getStatisticsSummary(),
      getStatisticsDateSummary(),
    ])
    summaryData.value = summaryRes.data || { total: {} }
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
            汇总病案扫描规模与每日变化，通过滚轮缩放时间范围，快速定位异常数据波动。
          </p>
          <div class="hero-meta">
            <span>最新统计日期：{{ latestStatisticsDate }}</span>
            <span>共 {{ trendDates.length }} 个统计日</span>
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
      <MrrChartCard
        title="扫描趋势"
        description="默认展示近 90 日；在图表内滚动鼠标滚轮可放大或缩小时间范围"
        :loading="loading"
        :empty="!trendDates.length"
        empty-description="暂无趋势数据"
      >
        <template #actions>
          <span class="panel-badge panel-badge--success">{{ trendDates.length }} 天可浏览</span>
        </template>
        <template #summary>
          <div class="trend-summary">
            <div>
              <span>全部记录</span>
              <strong>{{ formatNumber(trendRecordTotal) }}</strong>
            </div>
            <div>
              <span>全部页数</span>
              <strong>{{ formatNumber(trendPageTotal) }}</strong>
            </div>
            <div>
              <span>日均页数</span>
              <strong>{{ formatNumber(averageDailyPages) }}</strong>
            </div>
          </div>
        </template>
        <MrrDualAxisChart
          :categories="trendCategories"
          :bars="trendBars"
          :lines="trendLines"
          :loading="loading"
          left-axis-name="扫描页数"
          right-axis-name="记录数"
          left-unit="页"
          right-unit="条"
          :initial-visible-count="90"
          :height="340"
        />
      </MrrChartCard>
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
  background: rgb(124 58 237 / 10%);
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

.eyebrow {
  display: inline-flex;
  gap: 9px;
  align-items: center;
  padding: 6px 10px;
  margin: 0;
  font-size: 11px;
  font-weight: 800;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0.16em;
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
  grid-template-columns: minmax(0, 1fr);
  gap: 20px;
}

.panel-badge {
  display: inline-flex;
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

.trend-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
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
}

@media (width <= 560px) {
  .hero-actions :deep(.el-button) {
    flex: 1;
  }

  .hero-meta,
  .trend-summary {
    grid-template-columns: 1fr;
  }

  .hero-meta {
    display: grid;
  }
}
</style>
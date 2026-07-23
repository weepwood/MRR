<script setup lang="ts">
import type { ResponseMetricAnalysis } from '@/api/types'
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { getResponseMetricAnalysis } from '@/api/modules/response-metrics'
import ResponseTrendChart from './components/ResponseTrendChart.vue'

defineOptions({ name: 'ResponseAnalysisPage' })

const ANALYSIS_DAYS = 365

const loading = ref(false)
const error = ref('')
const analysis = ref<ResponseMetricAnalysis | null>(null)

const isEmpty = computed(() => {
  if (loading.value || error.value || !analysis.value) {
    return false
  }
  return (analysis.value.overview.totalRequests ?? 0) === 0
    && (analysis.value.overview.frontendSampleCount ?? 0) === 0
    && analysis.value.trend.length === 0
    && analysis.value.slowEndpoints.length === 0
})

function formatNumber(value: number | undefined) {
  return (value ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function formatPercent(value: number | undefined) {
  return `${formatNumber(value)}%`
}

function formatDuration(value: number | undefined) {
  return `${formatNumber(value)} ms`
}

function errorRate(errorCount: number, requestCount: number) {
  return requestCount > 0 ? errorCount / requestCount * 100 : 0
}

function updateSpotlight(event: PointerEvent) {
  if (event.pointerType === 'touch' || !(event.currentTarget instanceof HTMLElement)) {
    return
  }

  const card = event.currentTarget
  const rect = card.getBoundingClientRect()
  card.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  card.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const response = await getResponseMetricAnalysis(ANALYSIS_DAYS)
    analysis.value = response.data ?? null
  }
  catch {
    analysis.value = null
    error.value = '响应分析数据加载失败'
  }
  finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="response-analysis-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">
          Response Insights
        </p>
        <h2>接口响应分析</h2>
        <p class="subtitle">
          默认汇总最近 365 天每日请求量、错误情况及前后端响应耗时，定位长期波动与异常日期。
        </p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </header>

    <div v-if="error" class="state-panel error-panel" role="alert">
      <strong>{{ error }}</strong>
      <span>请稍后重试或检查后端服务状态。</span>
      <el-button @click="loadData">
        重新加载
      </el-button>
    </div>

    <el-empty v-else-if="isEmpty" description="近一年暂无响应指标数据" />

    <template v-else>
      <section class="mrr-metric-grid" aria-label="近一年响应指标总览">
        <el-card
          shadow="never"
          class="mrr-metric-card spotlight-card spotlight-card--blue"
          @pointermove="updateSpotlight"
        >
          <div class="mrr-metric-card__icon">
            <i class="i-ant-design:api-twotone" />
          </div>
          <div class="mrr-metric-card__body">
            <span class="mrr-metric-card__label">总请求数</span>
            <strong class="mrr-metric-card__value">{{ formatNumber(analysis?.overview.totalRequests) }}</strong>
            <p class="mrr-metric-card__note">
              最近 365 天完成的请求总量
            </p>
          </div>
        </el-card>
        <el-card
          shadow="never"
          class="mrr-metric-card mrr-metric-card--green spotlight-card spotlight-card--green"
          @pointermove="updateSpotlight"
        >
          <div class="mrr-metric-card__icon">
            <i class="i-ant-design:check-circle-twotone" />
          </div>
          <div class="mrr-metric-card__body">
            <span class="mrr-metric-card__label">HTTP 成功率</span>
            <strong class="mrr-metric-card__value">{{ formatPercent(analysis?.overview.successRate) }}</strong>
            <p class="mrr-metric-card__note">
              HTTP 2xx 响应占近一年请求的比例
            </p>
          </div>
        </el-card>
        <el-card
          shadow="never"
          class="mrr-metric-card mrr-metric-card--violet spotlight-card spotlight-card--violet"
          @pointermove="updateSpotlight"
        >
          <div class="mrr-metric-card__icon">
            <i class="i-ant-design:field-time-outlined" />
          </div>
          <div class="mrr-metric-card__body">
            <span class="mrr-metric-card__label">客户端 P95</span>
            <strong class="mrr-metric-card__value">{{ formatDuration(analysis?.overview.p95ClientDurationMs) }}</strong>
            <p class="mrr-metric-card__note">
              包含网络与浏览器处理耗时
            </p>
          </div>
        </el-card>
        <el-card
          shadow="never"
          class="mrr-metric-card mrr-metric-card--amber spotlight-card spotlight-card--amber"
          @pointermove="updateSpotlight"
        >
          <div class="mrr-metric-card__icon">
            <i class="i-ant-design:dashboard-twotone" />
          </div>
          <div class="mrr-metric-card__body">
            <span class="mrr-metric-card__label">服务端平均耗时</span>
            <strong class="mrr-metric-card__value">{{ formatDuration(analysis?.overview.avgServerDurationMs) }}</strong>
            <p class="mrr-metric-card__note">
              最近一年服务端请求处理平均值
            </p>
          </div>
        </el-card>
      </section>

      <div class="analysis-stack">
        <el-card
          v-loading="loading"
          shadow="never"
          class="analysis-card response-trend-card spotlight-card spotlight-card--soft"
          @pointermove="updateSpotlight"
        >
          <template #header>
            <div class="card-header">
              <div>
                <strong>近一年响应趋势</strong>
                <span>每日请求热力图及客户端、服务端平均耗时</span>
              </div>
            </div>
          </template>
          <ResponseTrendChart v-if="analysis?.trend.length" :data="analysis.trend" />
          <el-empty v-else description="近一年暂无趋势数据" :image-size="64" />
        </el-card>

        <el-card
          v-loading="loading"
          shadow="never"
          class="analysis-card slow-endpoint-card spotlight-card spotlight-card--soft"
          @pointermove="updateSpotlight"
        >
          <template #header>
            <div class="card-header">
              <div>
                <strong>慢接口排行</strong>
                <span>按最近一年客户端 P95 响应耗时排序</span>
              </div>
            </div>
          </template>
          <el-table
            v-if="analysis?.slowEndpoints.length"
            :data="analysis.slowEndpoints"
            stripe
            empty-text="暂无慢接口数据"
          >
            <el-table-column prop="method" label="方法" width="90" />
            <el-table-column prop="routePattern" label="接口模板" min-width="220" show-overflow-tooltip />
            <el-table-column prop="requestCount" label="请求数" width="100" />
            <el-table-column label="错误率" width="110">
              <template #default="{ row }">
                {{ formatPercent(errorRate(row.errorCount, row.requestCount)) }}
              </template>
            </el-table-column>
            <el-table-column label="客户端平均" width="130">
              <template #default="{ row }">
                {{ formatDuration(row.avgClientDurationMs) }}
              </template>
            </el-table-column>
            <el-table-column label="客户端 P95" width="130">
              <template #default="{ row }">
                {{ formatDuration(row.p95ClientDurationMs) }}
              </template>
            </el-table-column>
            <el-table-column label="服务端平均" width="130">
              <template #default="{ row }">
                {{ formatDuration(row.avgServerDurationMs) }}
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无慢接口数据" :image-size="64" />
        </el-card>
      </div>
    </template>
  </div>
</template>

<style scoped>
.response-analysis-page {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.page-header > div:first-child {
  min-width: 0;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: #097fe8;
  text-transform: uppercase;
  letter-spacing: 0.125px;
}

h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.23;
  color: rgb(0 0 0 / 95%);
  letter-spacing: -0.625px;
}

.subtitle {
  max-width: 760px;
  margin: 8px 0 0;
  color: #615d59;
}

.page-actions {
  display: flex;
  flex: none;
  gap: 12px;
  align-items: center;
}

.spotlight-card {
  --spotlight-x: 50%;
  --spotlight-y: 50%;
  --spotlight-color: rgb(9 127 232 / 16%);

  position: relative;
  isolation: isolate;
}

.spotlight-card::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  content: "";
  background: radial-gradient(
    circle at var(--spotlight-x) var(--spotlight-y),
    var(--spotlight-color) 0%,
    transparent 72%
  );
  border-radius: inherit;
  opacity: 0;
  transition: opacity 320ms ease;
}

.spotlight-card:hover::before,
.spotlight-card:focus-within::before {
  opacity: 1;
}

.spotlight-card :deep(.el-card__header),
.spotlight-card :deep(.el-card__body) {
  position: relative;
  z-index: 1;
}

.spotlight-card--green {
  --spotlight-color: rgb(22 128 60 / 16%);
}

.spotlight-card--violet {
  --spotlight-color: rgb(124 58 237 / 16%);
}

.spotlight-card--amber {
  --spotlight-color: rgb(180 95 6 / 16%);
}

.spotlight-card--soft {
  --spotlight-color: rgb(9 127 232 / 11%);
}

.analysis-card {
  min-width: 0;
  border: 1px solid rgb(0 0 0 / 10%);
  border-radius: 12px;
}

.analysis-stack {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.response-trend-card {
  overflow: visible;
}

.slow-endpoint-card {
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header div {
  display: grid;
  gap: 4px;
}

.card-header strong {
  font-size: 16px;
  font-weight: 600;
  color: rgb(0 0 0 / 95%);
}

.card-header span {
  font-size: 12px;
  color: #615d59;
}

.state-panel {
  display: grid;
  gap: 10px;
  justify-items: start;
  padding: 24px;
  color: #615d59;
  background: #f6f5f4;
  border: 1px solid rgb(0 0 0 / 10%);
  border-radius: 12px;
}

.error-panel strong {
  color: #dd5b00;
}

@media (width >= 1440px) {
  .analysis-stack {
    grid-template-columns: max-content minmax(520px, 1fr);
    align-items: start;
  }

  .response-trend-card {
    width: max-content;
    max-width: 100%;
    justify-self: start;
  }

  .response-trend-card :deep(.el-card__body) {
    width: max-content;
    max-width: calc(100vw - 620px);
  }
}

@media (width <= 600px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spotlight-card::before {
    transition: none;
  }
}
</style>

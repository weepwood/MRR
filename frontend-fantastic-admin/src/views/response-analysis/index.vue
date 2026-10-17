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
        <MrrMetricCard
          label="总请求数"
          icon="i-ant-design:api-twotone"
          note="最近 365 天完成的请求总量"
          :value="formatNumber(analysis?.overview.totalRequests)"
        />
        <MrrMetricCard
          label="HTTP 成功率"
          icon="i-ant-design:check-circle-twotone"
          tone="green"
          note="HTTP 2xx 响应占近一年请求的比例"
          :value="formatPercent(analysis?.overview.successRate)"
        />
        <MrrMetricCard
          label="客户端 P95"
          icon="i-ant-design:field-time-outlined"
          tone="violet"
          note="包含网络与浏览器处理耗时"
          :value="formatDuration(analysis?.overview.p95ClientDurationMs)"
        />
        <MrrMetricCard
          label="服务端平均耗时"
          icon="i-ant-design:dashboard-twotone"
          tone="amber"
          note="最近一年服务端请求处理平均值"
          :value="formatDuration(analysis?.overview.avgServerDurationMs)"
        />
      </section>

      <div class="analysis-stack">
        <el-card v-loading="loading" shadow="never" class="analysis-card response-trend-card">
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

        <el-card v-loading="loading" shadow="never" class="analysis-card slow-endpoint-card">
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
  color: var(--mrr-primary);
  text-transform: uppercase;
  letter-spacing: 0.125px;
}

h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.23;
  color: var(--mrr-foreground);
  letter-spacing: -0.625px;
}

.subtitle {
  max-width: 760px;
  margin: 8px 0 0;
  color: var(--mrr-muted-foreground);
}

.page-actions {
  display: flex;
  flex: none;
  gap: 12px;
  align-items: center;
}

.analysis-card {
  min-width: 0;
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
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
  color: var(--mrr-foreground);
}

.card-header span {
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

.state-panel {
  display: grid;
  gap: 10px;
  justify-items: start;
  padding: 24px;
  color: var(--mrr-muted-foreground);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.error-panel strong {
  color: var(--mrr-tone-warning);
}

@media (min-width: 1440px) {
  .analysis-stack {
    grid-template-columns: max-content minmax(520px, 1fr);
    align-items: start;
  }

  .response-trend-card {
    justify-self: start;
    width: max-content;
    max-width: 100%;
  }

  .response-trend-card :deep(.el-card__body) {
    width: max-content;
    max-width: calc(100vw - 620px);
  }
}

@media (max-width: 600px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
  }
}
</style>

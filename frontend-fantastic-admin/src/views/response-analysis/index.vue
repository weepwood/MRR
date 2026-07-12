<script setup lang="ts">
import type { ResponseMetricAnalysis } from '@/api/types'
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { getResponseMetricAnalysis } from '@/api/modules/response-metrics'
import ResponseTrendChart from './components/ResponseTrendChart.vue'

defineOptions({ name: 'ResponseAnalysisPage' })

type AnalysisDays = 1 | 7 | 30

const days = ref<AnalysisDays>(7)
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
    const response = await getResponseMetricAnalysis(days.value)
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
          对比浏览器端到端耗时与服务端处理耗时，定位慢接口和异常趋势。
        </p>
      </div>
      <div class="page-actions">
        <el-select v-model="days" class="range-select" aria-label="统计时间范围" @change="loadData">
          <el-option :value="1" label="近 1 天" />
          <el-option :value="7" label="近 7 天" />
          <el-option :value="30" label="近 30 天" />
        </el-select>
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

    <el-empty v-else-if="isEmpty" description="暂无响应指标数据" />

    <template v-else>
      <section class="summary-grid" aria-label="响应指标总览">
        <el-card shadow="never">
          <p class="metric-label">
            总请求数
          </p>
          <p class="metric-value">
            {{ formatNumber(analysis?.overview.totalRequests) }}
          </p>
          <p class="metric-note">
            所选时间范围内完成的请求
          </p>
        </el-card>
        <el-card shadow="never">
          <p class="metric-label">
            HTTP 成功率
          </p>
          <p class="metric-value">
            {{ formatPercent(analysis?.overview.successRate) }}
          </p>
          <p class="metric-note">
            HTTP 2xx 响应占全部请求的比例
          </p>
        </el-card>
        <el-card shadow="never">
          <p class="metric-label">
            客户端 P95
          </p>
          <p class="metric-value">
            {{ formatDuration(analysis?.overview.p95ClientDurationMs) }}
          </p>
          <p class="metric-note">
            包含网络与浏览器处理耗时
          </p>
        </el-card>
        <el-card shadow="never">
          <p class="metric-label">
            服务端平均耗时
          </p>
          <p class="metric-value">
            {{ formatDuration(analysis?.overview.avgServerDurationMs) }}
          </p>
          <p class="metric-note">
            服务端请求处理平均值
          </p>
        </el-card>
      </section>

      <el-card v-loading="loading" shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <div>
              <strong>响应趋势</strong>
              <span>请求量及客户端、服务端平均耗时</span>
            </div>
          </div>
        </template>
        <ResponseTrendChart v-if="analysis?.trend.length" :data="analysis.trend" />
        <el-empty v-else description="暂无趋势数据" :image-size="64" />
      </el-card>

      <el-card v-loading="loading" shadow="never" class="analysis-card">
        <template #header>
          <div class="card-header">
            <div>
              <strong>慢接口排行</strong>
              <span>按客户端 P95 响应耗时排序</span>
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
          <el-table-column prop="routePattern" label="接口模板" min-width="260" show-overflow-tooltip />
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
  margin: 8px 0 0;
  color: #615d59;
}

.page-actions {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
}

.range-select {
  width: 120px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-grid :deep(.el-card) {
  border: 1px solid rgb(0 0 0 / 10%);
  border-radius: 12px;
}

.metric-label,
.metric-note,
.metric-value {
  margin: 0;
}

.metric-label {
  font-size: 12px;
  font-weight: 600;
  color: #615d59;
  letter-spacing: 0.125px;
}

.metric-value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.23;
  color: rgb(0 0 0 / 95%);
  letter-spacing: -0.625px;
}

.metric-note {
  margin-top: 8px;
  font-size: 12px;
  color: #a39e98;
}

.analysis-card {
  border: 1px solid rgb(0 0 0 / 10%);
  border-radius: 12px;
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

@media (width <= 1000px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 600px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
  }

  .range-select {
    flex: 1;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

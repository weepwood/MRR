<script setup lang="ts">
import type { ResponseMetricAnalysis } from '@/api/types'
import { RefreshCw as Refresh } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { getResponseMetricAnalysis } from '@/api/modules/response-metrics'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
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
  <MrrPageShell class="response-analysis-page">
    <MrrPageHeader
      eyebrow="Response Insights"
      title="接口响应分析"
      description="默认汇总最近 365 天每日请求量、错误情况及前后端响应耗时，定位长期波动与异常日期。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </MrrPageHeader>

    <AppError v-if="error" :message="`${error}，请稍后重试或检查后端服务状态。`" retry-text="重新加载" @retry="loadData" />

    <AppEmpty v-else-if="isEmpty" icon="i-lucide:activity" description="近一年暂无响应指标数据" />

    <!-- 首次加载用骨架屏；刷新时保留上一份结果，只在卡片上叠加 v-loading。 -->
    <AppLoading v-else-if="loading && !analysis" type="stats" :cols="4" />

    <template v-else>
      <section class="mrr-metric-grid" aria-label="近一年响应指标总览">
        <MrrMetricCard
          label="总请求数"
          icon="i-lucide:network"
          note="最近 365 天完成的请求总量"
          :value="formatNumber(analysis?.overview.totalRequests)"
        />
        <MrrMetricCard
          label="HTTP 成功率"
          icon="i-lucide:circle-check"
          tone="green"
          note="HTTP 2xx 响应占近一年请求的比例"
          :value="formatPercent(analysis?.overview.successRate)"
        />
        <MrrMetricCard
          label="客户端 P95"
          icon="i-lucide:calendar-clock"
          tone="violet"
          note="包含网络与浏览器处理耗时"
          :value="formatDuration(analysis?.overview.p95ClientDurationMs)"
        />
        <MrrMetricCard
          label="服务端平均耗时"
          icon="i-lucide:layout-dashboard"
          tone="amber"
          note="最近一年服务端请求处理平均值"
          :value="formatDuration(analysis?.overview.avgServerDurationMs)"
        />
      </section>

      <div class="analysis-stack">
        <MrrSectionCard v-loading="loading" class="analysis-card response-trend-card" title="近一年响应趋势" description="每日请求热力图及客户端、服务端平均耗时">
          <ResponseTrendChart v-if="analysis?.trend.length" :data="analysis.trend" />
          <AppEmpty v-else icon="i-lucide:chart-line" description="近一年暂无趋势数据" />
        </MrrSectionCard>

        <MrrSectionCard v-loading="loading" class="analysis-card slow-endpoint-card" title="慢接口排行" description="按最近一年客户端 P95 响应耗时排序">
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
          <AppEmpty v-else icon="i-lucide:gauge" description="暂无慢接口数据" />
        </MrrSectionCard>
      </div>
    </template>
  </MrrPageShell>
</template>

<style scoped>
/* 页面壳层、页头排版与窄屏折行由 MrrPageShell / MrrPageHeader 统一提供。 */
.analysis-card {
  min-width: 0;
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.analysis-stack {
  display: grid;
  gap: var(--mrr-page-gap);
  min-width: 0;
}

.response-trend-card {
  overflow: visible;
}

.slow-endpoint-card {
  overflow: hidden;
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
</style>

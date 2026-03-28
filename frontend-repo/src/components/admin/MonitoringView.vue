<template>
  <div class="pmr-page admin-tool-view">
    <section class="pmr-page-header">
      <div>
        <p class="module-eyebrow">Pressure Test Monitor</p>
        <h2 class="pmr-page-title">压力测试监控</h2>
        <p class="pmr-page-subtitle">统一查看系统状态、执行压测并跟踪历史结果和资源变化。</p>
      </div>
      <div class="pmr-toolbar-actions">
        <el-button type="primary" :loading="loadingSystem" @click="refreshSystem">刷新系统</el-button>
        <el-button type="primary" :loading="runningPressureTest" @click="runPressureTestNow">开始压测</el-button>
      </div>
    </section>

    <section class="summary-grid">
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">健康状态</div>
        <div class="summary-value" :class="healthTone">{{ systemOverview.health?.status || 'UNKNOWN' }}</div>
        <div class="summary-note">接口：/v1/system/health</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">运行时长</div>
        <div class="summary-value">{{ systemOverview.runtime?.uptimeFormatted || '-' }}</div>
        <div class="summary-note">接口：/v1/system/runtime</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">堆内存</div>
        <div class="summary-value">{{ systemOverview.memory?.usagePercent || '-' }}</div>
        <div class="summary-note">接口：/v1/system/memory</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">最近压测</div>
        <div class="summary-value">{{ latestReport?.runId || '暂无' }}</div>
        <div class="summary-note">{{ latestReport?.targetUrl || '等待执行' }}</div>
      </el-card>
    </section>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">压测配置</h3>
            <p class="pmr-panel-subtitle">目标地址建议填写可直连的后端接口，例如 `/v1/system/health` 的完整地址。</p>
          </div>
          <span class="pmr-badge">Live</span>
        </div>
      </template>

      <el-form :model="pressureForm" label-width="104px" class="pressure-form" @submit.prevent>
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="名称">
              <el-input v-model="pressureForm.name" placeholder="admin-pressure-test" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="目标地址">
              <el-input
                v-model="pressureForm.targetUrl"
                placeholder="http://127.0.0.1:18045/v1/system/health"
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :xs="24" :md="6">
            <el-form-item label="方法">
              <el-select v-model="pressureForm.method" style="width: 100%;">
                <el-option v-for="method in methods" :key="method" :label="method" :value="method" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="并发数">
              <el-input-number v-model="pressureForm.concurrency" :min="1" :max="128" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="总请求数">
              <el-input-number v-model="pressureForm.totalRequests" :min="1" :max="10000" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="超时">
              <el-input-number v-model="pressureForm.timeoutMillis" :min="100" :max="30000" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="请求头">
          <el-input
            v-model="pressureForm.headers"
            type="textarea"
            :rows="3"
            placeholder='{"Accept":"application/json"}'
          />
        </el-form-item>

        <el-form-item label="请求体">
          <el-input
            v-model="pressureForm.body"
            type="textarea"
            :rows="5"
            placeholder='{"name":"demo"}'
          />
        </el-form-item>

        <div class="pmr-actions-row">
          <el-button type="primary" :loading="runningPressureTest" @click="runPressureTestNow">执行压测</el-button>
          <el-button @click="resetPressureForm">重置</el-button>
          <el-button @click="loadLatestReport">刷新最新结果</el-button>
          <el-button @click="clearHistory">清空历史</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">压测结果</h3>
            <p class="pmr-panel-subtitle">关注成功率、P95、最大耗时和吞吐量的变化。</p>
          </div>
          <span class="pmr-badge" v-if="latestReport">{{ latestReport.name }}</span>
        </div>
      </template>

      <div v-if="latestReport" class="report-grid">
        <el-card class="pmr-panel report-card" shadow="never">
          <div class="summary-label">成功率</div>
          <div class="summary-value">{{ latestReport.successRate }}%</div>
          <div class="summary-note">{{ latestReport.successCount }}/{{ latestReport.totalRequests }} 成功</div>
        </el-card>
        <el-card class="pmr-panel report-card" shadow="never">
          <div class="summary-label">平均耗时</div>
          <div class="summary-value">{{ latestReport.avgLatencyMs }}ms</div>
          <div class="summary-note">P95 {{ latestReport.p95LatencyMs }}ms</div>
        </el-card>
        <el-card class="pmr-panel report-card" shadow="never">
          <div class="summary-label">最大耗时</div>
          <div class="summary-value">{{ latestReport.maxLatencyMs }}ms</div>
          <div class="summary-note">最小 {{ latestReport.minLatencyMs }}ms</div>
        </el-card>
        <el-card class="pmr-panel report-card" shadow="never">
          <div class="summary-label">吞吐量</div>
          <div class="summary-value">{{ latestReport.requestsPerSecond }} rps</div>
          <div class="summary-note">耗时 {{ latestReport.durationMillis }}ms</div>
        </el-card>
      </div>

      <div v-if="latestReport" class="report-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="运行编号">{{ latestReport.runId }}</el-descriptions-item>
          <el-descriptions-item label="测试名称">{{ latestReport.name }}</el-descriptions-item>
          <el-descriptions-item label="目标地址">{{ latestReport.targetUrl }}</el-descriptions-item>
          <el-descriptions-item label="方法">{{ latestReport.method }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ latestReport.startedAt }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ latestReport.finishedAt }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-empty v-else description="暂无压测结果" />
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">最近历史</h3>
            <p class="pmr-panel-subtitle">保留最近 20 次压测记录，便于回看趋势。</p>
          </div>
          <span class="pmr-badge">{{ history.length }} 条</span>
        </div>
      </template>

      <el-table :data="history" border stripe empty-text="暂无历史记录">
        <el-table-column prop="runId" label="运行编号" min-width="180" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="targetUrl" label="目标地址" min-width="260" show-overflow-tooltip />
        <el-table-column prop="successRate" label="成功率" width="110">
          <template #default="{ row }">{{ row.successRate }}%</template>
        </el-table-column>
        <el-table-column prop="avgLatencyMs" label="平均" width="100">
          <template #default="{ row }">{{ row.avgLatencyMs }}ms</template>
        </el-table-column>
        <el-table-column prop="p95LatencyMs" label="P95" width="100">
          <template #default="{ row }">{{ row.p95LatencyMs }}ms</template>
        </el-table-column>
        <el-table-column prop="requestsPerSecond" label="RPS" width="100">
          <template #default="{ row }">{{ row.requestsPerSecond }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" @click="showReport(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">系统快照</h3>
            <p class="pmr-panel-subtitle">压测前后资源变化，用于快速判断是否出现资源抖动。</p>
          </div>
        </div>
      </template>

      <div v-if="latestReport" class="snapshot-grid">
        <article class="snapshot-card">
          <h4>压测前</h4>
          <p>堆内存：{{ formatBytes(latestReport.beforeSnapshot.heapUsedBytes) }}</p>
          <p>堆占用：{{ latestReport.beforeSnapshot.heapUsagePercent }}%</p>
          <p>系统负载：{{ latestReport.beforeSnapshot.systemLoadAverage }}</p>
        </article>
        <article class="snapshot-card">
          <h4>压测后</h4>
          <p>堆内存：{{ formatBytes(latestReport.afterSnapshot.heapUsedBytes) }}</p>
          <p>堆占用：{{ latestReport.afterSnapshot.heapUsagePercent }}%</p>
          <p>系统负载：{{ latestReport.afterSnapshot.systemLoadAverage }}</p>
        </article>
      </div>

      <el-empty v-else description="暂无快照数据" />
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">最近样本</h3>
            <p class="pmr-panel-subtitle">展示最新一次压测返回的部分请求样本。</p>
          </div>
        </div>
      </template>

      <el-table v-if="sampleRows.length" :data="sampleRows" border stripe>
        <el-table-column prop="index" label="序号" width="90" />
        <el-table-column prop="statusCode" label="状态码" width="100" />
        <el-table-column prop="success" label="成功" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="latencyMillis" label="耗时" width="110">
          <template #default="{ row }">{{ row.latencyMillis }}ms</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误" min-width="240" show-overflow-tooltip />
      </el-table>

      <el-empty v-else description="暂无样本数据" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  clearPressureTestHistory,
  getLatestPressureTest,
  getPressureTestHistory,
  getSystemOverview,
  runPressureTest
} from '@/utils/api'

const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const loadingSystem = ref(false)
const runningPressureTest = ref(false)
const history = ref([])
const latestReport = ref(null)
const systemOverview = ref({})

const pressureForm = reactive({
  name: 'admin-pressure-test',
  targetUrl: 'http://127.0.0.1:18045/v1/system/health',
  method: 'GET',
  concurrency: 4,
  totalRequests: 20,
  timeoutMillis: 5000,
  headers: '',
  body: ''
})

const sampleRows = computed(() => (latestReport.value?.samples || []).slice(0, 12))

const healthTone = computed(() => {
  const value = String(systemOverview.value?.health?.status || '').toUpperCase()
  if (!value || value === 'UNKNOWN') return 'neutral'
  if (value === 'UP') return 'success'
  if (value === 'WARNING') return 'warning'
  return 'danger'
})

const formatBytes = (bytes) => {
  const value = Number(bytes || 0)
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let size = value
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(2)} ${units[index]}`
}

const parseJson = (text, fallback = null) => {
  if (!text || !String(text).trim()) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

const loadSystem = async () => {
  loadingSystem.value = true
  try {
    const response = await getSystemOverview()
    const result = response?.data
    systemOverview.value = result?.code === 200 ? result.data || {} : result || {}
  } catch (error) {
    ElMessage.error(error?.message || '系统信息加载失败')
  } finally {
    loadingSystem.value = false
  }
}

const loadHistory = async () => {
  try {
    const response = await getPressureTestHistory()
    const result = response?.data
    history.value = result?.code === 200 ? (Array.isArray(result.data) ? result.data : []) : []
  } catch {
    history.value = []
  }
}

const loadLatestReport = async () => {
  try {
    const response = await getLatestPressureTest()
    const result = response?.data
    latestReport.value = result?.code === 200 ? result.data || null : null
  } catch {
    latestReport.value = null
  }
}

const refreshSystem = async () => {
  await loadSystem()
  await loadLatestReport()
  await loadHistory()
  ElMessage.success('系统信息已刷新')
}

const buildPayload = () => ({
  name: pressureForm.name,
  targetUrl: pressureForm.targetUrl,
  method: pressureForm.method,
  concurrency: pressureForm.concurrency,
  totalRequests: pressureForm.totalRequests,
  timeoutMillis: pressureForm.timeoutMillis,
  headers: parseJson(pressureForm.headers, {}),
  body: pressureForm.body || ''
})

const runPressureTestNow = async () => {
  runningPressureTest.value = true
  try {
    const response = await runPressureTest(buildPayload())
    const result = response?.data
    if (!result || result.code !== 200) {
      throw new Error(result?.message || '压测执行失败')
    }
    latestReport.value = result.data || null
    await loadHistory()
    ElMessage.success('压测完成')
  } catch (error) {
    ElMessage.error(error?.message || '压测执行失败')
  } finally {
    runningPressureTest.value = false
  }
}

const showReport = (row) => {
  latestReport.value = row
}

const clearHistory = async () => {
  try {
    await ElMessageBox.confirm('确认清空压测历史吗？', '提示', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    const response = await clearPressureTestHistory()
    if (response?.data?.code !== 200) {
      throw new Error(response?.data?.message || '清空失败')
    }
    history.value = []
    latestReport.value = null
    ElMessage.success('历史已清空')
  } catch (error) {
    ElMessage.error(error?.message || '清空失败')
  }
}

const resetPressureForm = () => {
  pressureForm.name = 'admin-pressure-test'
  pressureForm.targetUrl = 'http://127.0.0.1:18045/v1/system/health'
  pressureForm.method = 'GET'
  pressureForm.concurrency = 4
  pressureForm.totalRequests = 20
  pressureForm.timeoutMillis = 5000
  pressureForm.headers = ''
  pressureForm.body = ''
}

onMounted(async () => {
  await refreshSystem()
})
</script>

<style scoped>
.module-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--pmr-color-text-secondary);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card,
.report-card {
  padding: 20px;
}

.summary-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--pmr-color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.summary-value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  color: var(--pmr-color-text-primary);
  word-break: break-all;
}

.summary-value.success {
  color: var(--pmr-color-success-500);
}

.summary-value.warning {
  color: var(--pmr-color-warning-500);
}

.summary-value.danger {
  color: var(--pmr-color-danger-500);
}

.summary-note {
  margin-top: 8px;
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
}

.pmr-section {
  margin-top: 20px;
}

.pressure-form {
  display: grid;
  gap: 4px;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.report-detail,
.snapshot-grid {
  margin-top: 16px;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.snapshot-card {
  padding: 18px;
  border-radius: var(--pmr-radius-2xl);
  border: 1px solid var(--pmr-color-border-default);
  background: #ffffff;
}

.snapshot-card h4 {
  margin: 0 0 12px;
  font-size: 16px;
}

.snapshot-card p {
  margin: 6px 0;
  color: var(--pmr-color-text-secondary);
}

@media (max-width: 1180px) {
  .summary-grid,
  .report-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-grid,
  .report-grid,
  .snapshot-grid {
    grid-template-columns: 1fr;
  }
}
</style>

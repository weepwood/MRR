<template>
  <section class="pmr-section pressure-test-shell">
    <el-card class="pmr-panel" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">压力测试</h3>
            <p class="pmr-panel-subtitle">执行后端压测、查看历史记录和最近一次结果。</p>
          </div>
          <span class="pmr-badge">Pressure</span>
        </div>
      </template>

      <el-form :model="form" label-width="96px" class="pressure-form" @submit.prevent>
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="名称">
              <el-input v-model="form.name" placeholder="admin-pressure-test" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="目标地址">
              <el-input v-model="form.targetUrl" placeholder="http://127.0.0.1:18045/v1/system/health" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :md="6">
            <el-form-item label="方法">
              <el-select v-model="form.method" style="width: 100%;">
                <el-option v-for="method in methods" :key="method" :label="method" :value="method" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="并发数">
              <el-input-number v-model="form.concurrency" :min="1" :max="128" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="总请求数">
              <el-input-number v-model="form.totalRequests" :min="1" :max="10000" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="超时">
              <el-input-number v-model="form.timeoutMillis" :min="100" :max="30000" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="请求头">
          <el-input v-model="form.headers" type="textarea" :rows="3" placeholder='{"Accept":"application/json"}' />
        </el-form-item>
        <el-form-item label="请求体">
          <el-input v-model="form.body" type="textarea" :rows="4" placeholder='{"name":"demo"}' />
        </el-form-item>

        <div class="pmr-actions-row">
          <el-button type="primary" :loading="running" @click="runTest">执行压测</el-button>
          <el-button @click="refreshHistory">刷新历史</el-button>
          <el-button @click="clearHistory">清空历史</el-button>
          <el-button @click="resetForm">重置</el-button>
        </div>
      </el-form>
    </el-card>

    <div class="pressure-grid">
      <el-card class="pmr-panel pressure-card" shadow="never">
        <div class="summary-label">最近运行</div>
        <div class="summary-value">{{ latestReport?.runId || '暂无' }}</div>
        <div class="summary-note">{{ latestReport?.targetUrl || '等待执行' }}</div>
      </el-card>
      <el-card class="pmr-panel pressure-card" shadow="never">
        <div class="summary-label">成功率</div>
        <div class="summary-value">{{ latestReport?.successRate ?? '-' }}%</div>
        <div class="summary-note">{{ latestReport ? `${latestReport.successCount}/${latestReport.totalRequests}` : '暂无数据' }}</div>
      </el-card>
      <el-card class="pmr-panel pressure-card" shadow="never">
        <div class="summary-label">P95</div>
        <div class="summary-value">{{ latestReport?.p95LatencyMs ?? '-' }}ms</div>
        <div class="summary-note">平均 {{ latestReport?.avgLatencyMs ?? '-' }}ms</div>
      </el-card>
      <el-card class="pmr-panel pressure-card" shadow="never">
        <div class="summary-label">吞吐量</div>
        <div class="summary-value">{{ latestReport?.requestsPerSecond ?? '-' }} rps</div>
        <div class="summary-note">总耗时 {{ latestReport?.durationMillis ?? '-' }}ms</div>
      </el-card>
    </div>

    <el-card class="pmr-panel pressure-card" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">压测历史</h3>
            <p class="pmr-panel-subtitle">保留最近 20 次结果，支持快速回看。</p>
          </div>
          <span class="pmr-badge">{{ history.length }} 条</span>
        </div>
      </template>

      <el-table :data="history" border stripe empty-text="暂无历史记录">
        <el-table-column prop="runId" label="运行编号" min-width="180" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="targetUrl" label="目标地址" min-width="260" show-overflow-tooltip />
        <el-table-column prop="successRate" label="成功率" width="100">
          <template #default="{ row }">{{ row.successRate }}%</template>
        </el-table-column>
        <el-table-column prop="avgLatencyMs" label="平均" width="90">
          <template #default="{ row }">{{ row.avgLatencyMs }}ms</template>
        </el-table-column>
        <el-table-column prop="p95LatencyMs" label="P95" width="90">
          <template #default="{ row }">{{ row.p95LatencyMs }}ms</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button type="primary" @click="showReport(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearPressureTestHistory, getLatestPressureTest, getPressureTestHistory, runPressureTest } from '@/utils/api'

const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const running = ref(false)
const history = ref([])
const latestReport = ref(null)

const form = reactive({
  name: 'admin-pressure-test',
  targetUrl: 'http://127.0.0.1:18045/v1/system/health',
  method: 'GET',
  concurrency: 4,
  totalRequests: 20,
  timeoutMillis: 5000,
  headers: '',
  body: ''
})

const parseJson = (text, fallback = null) => {
  if (!text || !String(text).trim()) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
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

const loadLatest = async () => {
  try {
    const response = await getLatestPressureTest()
    const result = response?.data
    latestReport.value = result?.code === 200 ? result.data || null : null
  } catch {
    latestReport.value = null
  }
}

const refreshHistory = async () => {
  await loadHistory()
  await loadLatest()
  ElMessage.success('历史已刷新')
}

const showReport = (row) => {
  latestReport.value = row
}

const runTest = async () => {
  running.value = true
  try {
    const response = await runPressureTest({
      name: form.name,
      targetUrl: form.targetUrl,
      method: form.method,
      concurrency: form.concurrency,
      totalRequests: form.totalRequests,
      timeoutMillis: form.timeoutMillis,
      headers: parseJson(form.headers, {}),
      body: form.body || ''
    })
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
    running.value = false
  }
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

const resetForm = () => {
  form.name = 'admin-pressure-test'
  form.targetUrl = 'http://127.0.0.1:18045/v1/system/health'
  form.method = 'GET'
  form.concurrency = 4
  form.totalRequests = 20
  form.timeoutMillis = 5000
  form.headers = ''
  form.body = ''
}

onMounted(async () => {
  await refreshHistory()
})
</script>

<style scoped>
.pressure-test-shell {
  display: grid;
  gap: 20px;
}

.pressure-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.pressure-card {
  padding: 18px;
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

.summary-note {
  margin-top: 8px;
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
}

.pressure-form {
  display: grid;
  gap: 4px;
}

@media (max-width: 1180px) {
  .pressure-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .pressure-grid {
    grid-template-columns: 1fr;
  }
}
</style>

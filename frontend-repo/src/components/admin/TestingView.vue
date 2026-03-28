<template>
  <div class="pmr-page admin-tool-view">
    <section class="pmr-page-header">
      <div>
        <p class="module-eyebrow">Backend Test Lab</p>
        <h2 class="pmr-page-title">后端测试</h2>
        <p class="pmr-page-subtitle">统一跑通健康检查、Swagger、系统概览和关键接口冒烟测试。</p>
      </div>
      <div class="pmr-toolbar-actions">
        <el-button type="primary" :disabled="!resolvedSwaggerUrl" @click="openSwagger">
          打开 Swagger
        </el-button>
        <el-button type="primary" :loading="runningSmokeSuite" @click="runSmokeSuite">
          运行烟测
        </el-button>
        <el-button @click="clearResults">清空结果</el-button>
      </div>
    </section>

    <section class="summary-grid">
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">状态</div>
        <div class="summary-value" :class="statusTone">{{ smokeSummary.status }}</div>
        <div class="summary-note">{{ smokeSummary.note }}</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">Swagger</div>
        <div class="summary-value summary-url" :title="resolvedSwaggerUrl || '未配置'">
          {{ resolvedSwaggerUrl || '未配置' }}
        </div>
        <div class="summary-note">地址可在系统设置中修改</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">本次测试</div>
        <div class="summary-value">{{ smokeResults.length }}</div>
        <div class="summary-note">已执行接口数量</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">最近运行</div>
        <div class="summary-value">{{ lastRunLabel }}</div>
        <div class="summary-note">最后一次冒烟测试时间</div>
      </el-card>
    </section>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">手动接口测试</h3>
            <p class="pmr-panel-subtitle">直接调用后端 `/v1` 接口，适合排查单个问题。</p>
          </div>
          <span class="pmr-badge">Manual</span>
        </div>
      </template>

      <el-form :model="manualRequest" class="manual-form" label-width="96px" @submit.prevent>
        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <el-form-item label="方法">
              <el-select v-model="manualRequest.method" style="width: 100%;">
                <el-option v-for="method in methods" :key="method" :label="method" :value="method" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="16">
            <el-form-item label="路径">
              <el-input v-model="manualRequest.path" placeholder="/system/health" clearable />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="请求头">
          <el-input
            v-model="manualRequest.headers"
            type="textarea"
            :rows="3"
            placeholder='{"Accept":"application/json"}'
          />
        </el-form-item>

        <el-form-item label="请求体">
          <el-input
            v-model="manualRequest.body"
            type="textarea"
            :rows="5"
            placeholder='{"keyword":"example"}'
          />
        </el-form-item>

        <div class="pmr-actions-row">
          <el-button type="primary" :loading="runningManualRequest" @click="sendManualRequest">发送请求</el-button>
          <el-button @click="resetManualRequest">重置</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">冒烟测试结果</h3>
            <p class="pmr-panel-subtitle">一次性检查关键接口可用性，并记录耗时和响应摘要。</p>
          </div>
          <span class="pmr-badge">{{ smokeResults.length }} 项</span>
        </div>
      </template>

      <el-table :data="smokeResults" border stripe empty-text="尚未运行烟测">
        <el-table-column prop="name" label="测试项" min-width="160" />
        <el-table-column prop="method" label="方法" width="96">
          <template #default="{ row }">
            <el-tag :type="methodTone(row.method)">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.ok ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="110">
          <template #default="{ row }">{{ row.durationMs }}ms</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" @click="openResult(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">响应详情</h3>
            <p class="pmr-panel-subtitle">显示最新一次手动请求或冒烟测试的返回内容。</p>
          </div>
        </div>
      </template>

      <div v-if="selectedResult" class="response-shell">
        <div class="response-meta">
          <span>接口：{{ selectedResult.path }}</span>
          <span>状态：{{ selectedResult.status }}</span>
          <span>耗时：{{ selectedResult.durationMs }}ms</span>
        </div>
        <pre class="response-body">{{ selectedResult.preview }}</pre>
      </div>
      <el-empty v-else description="请选择一条结果查看详情" />
    </el-card>

    <PressureTestView />
    <LogCleanupTestView />
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAdminSettings } from '@/shared/composables/useAdminSettings'
import PressureTestView from './PressureTestView.vue'
import LogCleanupTestView from './LogCleanupTestView.vue'

const { resolvedSwaggerUrl } = useAdminSettings()

const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const runningSmokeSuite = ref(false)
const runningManualRequest = ref(false)
const smokeResults = ref([])
const selectedResult = ref(null)
const lastRunAt = ref('')

const manualRequest = reactive({
  method: 'GET',
  path: '/system/health',
  headers: '',
  body: ''
})

const smokeSummary = computed(() => {
  if (!smokeResults.value.length) {
    return {
      status: '未运行',
      note: '点击“运行烟测”开始检查'
    }
  }

  const passed = smokeResults.value.filter((item) => item.ok).length
  return {
    status: passed === smokeResults.value.length ? '全部通过' : '部分失败',
    note: `通过 ${passed}/${smokeResults.value.length} 个接口`
  }
})

const statusTone = computed(() => {
  if (!smokeResults.value.length) return 'neutral'
  return smokeResults.value.every((item) => item.ok) ? 'success' : 'danger'
})

const lastRunLabel = computed(() => lastRunAt.value || '暂无')

const buildApiUrl = (path) => {
  const cleanPath = String(path || '').trim().replace(/^\/+/, '')
  return `/api/${cleanPath}`
}

const parseJson = (text, fallback = null) => {
  if (!text || !String(text).trim()) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

const normalizeBody = (body) => {
  if (body == null) return ''
  if (typeof body === 'string') return body
  return JSON.stringify(body, null, 2)
}

const requestOnce = async ({ name, method, path, headers = {}, body = '' }) => {
  const start = performance.now()
  const finalHeaders = { ...headers }
  if (body && !Object.keys(finalHeaders).some((key) => key.toLowerCase() === 'content-type')) {
    finalHeaders['Content-Type'] = 'application/json'
  }
  const response = await fetch(buildApiUrl(path), {
    method,
    headers: finalHeaders,
    body: ['GET', 'DELETE'].includes(method) || !body ? undefined : body
  })
  const durationMs = Math.round(performance.now() - start)
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  const preview = typeof payload === 'string'
    ? payload
    : JSON.stringify(payload, null, 2)

  return {
    name,
    method,
    path,
    status: `${response.status} ${response.statusText}`.trim(),
    ok: response.ok,
    durationMs,
    summary: typeof payload === 'object' && payload !== null
      ? payload.message || payload?.data?.message || 'JSON response'
      : String(payload || '').slice(0, 140) || 'empty response',
    preview
  }
}

const runSmokeSuite = async () => {
  runningSmokeSuite.value = true
  try {
    const suite = [
      { name: '系统健康', method: 'GET', path: '/system/health' },
      { name: '系统信息', method: 'GET', path: '/system/info' },
      { name: '系统概览', method: 'GET', path: '/system/overview' },
      { name: '统计摘要', method: 'GET', path: '/statistics-api/summary' },
      { name: '图像心跳', method: 'GET', path: '/img-api/hello' },
      { name: '数据库心跳', method: 'GET', path: '/db-api/hello' }
    ]

    const results = []
    for (const item of suite) {
      results.push(await requestOnce(item))
    }

    smokeResults.value = results
    selectedResult.value = results[0] || null
    lastRunAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
    ElMessage.success('冒烟测试完成')
  } catch (error) {
    ElMessage.error(error?.message || '冒烟测试失败')
  } finally {
    runningSmokeSuite.value = false
  }
}

const sendManualRequest = async () => {
  runningManualRequest.value = true
  try {
    const headers = parseJson(manualRequest.headers, {})
    const bodyPayload = parseJson(manualRequest.body, manualRequest.body || '')
    const result = await requestOnce({
      name: '手动请求',
      method: manualRequest.method,
      path: manualRequest.path,
      headers,
      body: typeof bodyPayload === 'string' ? bodyPayload : normalizeBody(bodyPayload)
    })
    selectedResult.value = result
    smokeResults.value = [result, ...smokeResults.value].slice(0, 10)
    lastRunAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
    ElMessage.success('请求完成')
  } catch (error) {
    ElMessage.error(error?.message || '请求失败')
  } finally {
    runningManualRequest.value = false
  }
}

const openResult = (row) => {
  selectedResult.value = row
}

const clearResults = () => {
  smokeResults.value = []
  selectedResult.value = null
  lastRunAt.value = ''
}

const openSwagger = () => {
  if (!resolvedSwaggerUrl.value) {
    ElMessage.warning('请先在系统设置中配置 Swagger 地址')
    return
  }
  window.open(resolvedSwaggerUrl.value, '_blank', 'noopener,noreferrer')
}

const resetManualRequest = () => {
  manualRequest.method = 'GET'
  manualRequest.path = '/system/health'
  manualRequest.headers = ''
  manualRequest.body = ''
}

const methodTone = (method) => {
  const value = String(method || '').toUpperCase()
  if (value === 'GET') return 'primary'
  if (value === 'POST') return 'success'
  if (value === 'PUT' || value === 'PATCH') return 'warning'
  if (value === 'DELETE') return 'danger'
  return 'info'
}
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

.summary-card {
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

.summary-value.danger {
  color: var(--pmr-color-danger-500);
}

.summary-value.neutral {
  color: var(--pmr-color-text-primary);
}

.summary-url {
  font-size: 16px;
}

.summary-note {
  margin-top: 8px;
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
}

.pmr-section {
  margin-top: 20px;
}

.manual-form {
  display: grid;
  gap: 4px;
}

.response-shell {
  display: grid;
  gap: 12px;
}

.response-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
}

.response-body {
  margin: 0;
  padding: 16px;
  border-radius: var(--pmr-radius-xl);
  background: #0f172a;
  color: #e2e8f0;
  font-family: var(--pmr-font-family-mono);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>

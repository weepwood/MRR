<script setup lang="ts">
import type { Method } from 'axios'
import type { AuthTestHistoryEvent } from '../types'
import type { AuthTestResult } from '@/api/modules/auth-test'
import { Connection } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { executeAuthTestRequest } from '@/api/modules/auth-test'
import { useUserStore } from '@/store/modules/user'

const emit = defineEmits<{
  record: [event: AuthTestHistoryEvent]
}>()

const userStore = useUserStore()
const loading = ref(false)
const result = ref<AuthTestResult | null>(null)
const request = reactive({
  method: 'GET' as Method,
  path: '/api/v1/auth/me',
  useToken: true,
  token: userStore.token || '',
  headersText: '{\n  "Accept": "application/json"\n}',
  bodyText: '',
})

function parseJsonObject(value: string, label: string): Record<string, string> {
  if (!value.trim()) {
    return {}
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(value)
  }
  catch {
    throw new Error(`${label}不是有效 JSON`)
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error(`${label}必须是 JSON 对象`)
  }
  return Object.fromEntries(
    Object.entries(parsed as Record<string, unknown>).map(([key, item]) => [key, String(item)]),
  )
}

function parseJsonBody(value: string): unknown {
  if (!value.trim()) {
    return undefined
  }
  try {
    return JSON.parse(value)
  }
  catch {
    throw new Error('请求体不是有效 JSON')
  }
}

function formatJson(value: unknown): string {
  if (value === undefined) {
    return ''
  }
  try {
    return JSON.stringify(value, null, 2)
  }
  catch {
    return String(value)
  }
}

function responseType(value: AuthTestResult | null) {
  if (!value) {
    return 'info'
  }
  if (value.status >= 200 && value.status < 300) {
    return 'success'
  }
  if ([401, 403].includes(value.status)) {
    return 'warning'
  }
  return 'danger'
}

async function sendRequest() {
  loading.value = true
  try {
    const response = await executeAuthTestRequest({
      method: request.method,
      path: request.path,
      token: request.useToken ? request.token : '',
      headers: parseJsonObject(request.headersText, '请求头'),
      body: parseJsonBody(request.bodyText),
    })
    result.value = response
    emit('record', {
      name: '自定义请求',
      method: String(request.method),
      path: request.path,
      status: response.status,
      durationMs: response.durationMs,
    })
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '自定义请求失败')
  }
  finally {
    loading.value = false
  }
}

function useCurrentToken() {
  request.token = userStore.token || ''
  request.useToken = true
}
</script>

<template>
  <div class="tester-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>自定义当前后端请求</strong>
            <span>故意测试 401/403 不会触发后台全局退出</span>
          </div>
          <el-button text @click="useCurrentToken">
            使用当前 Token
          </el-button>
        </div>
      </template>

      <el-form label-position="top">
        <div class="method-grid">
          <el-form-item label="方法">
            <el-select v-model="request.method">
              <el-option v-for="method in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']" :key="method" :label="method" :value="method" />
            </el-select>
          </el-form-item>
          <el-form-item label="接口路径">
            <el-input v-model="request.path" placeholder="/api/v1/auth/me" />
          </el-form-item>
        </div>
        <el-form-item>
          <el-checkbox v-model="request.useToken">
            添加 Authorization: Bearer Token
          </el-checkbox>
        </el-form-item>
        <el-form-item v-if="request.useToken" label="Bearer Token">
          <el-input v-model="request.token" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="附加请求头 JSON">
          <el-input v-model="request.headersText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="请求体 JSON">
          <el-input v-model="request.bodyText" type="textarea" :rows="7" placeholder="GET 请求可以留空" />
        </el-form-item>
        <el-button type="primary" :icon="Connection" :loading="loading" @click="sendRequest">
          发送请求
        </el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>接口响应</strong>
            <span>保留 HTTP 状态、响应头和业务响应体</span>
          </div>
          <el-tag :type="responseType(result)" effect="plain">
            {{ result?.status ?? '未发送' }}
          </el-tag>
        </div>
      </template>
      <div class="response-summary">
        <span>状态文本</span>
        <strong>{{ result?.statusText || '—' }}</strong>
        <span>耗时</span>
        <strong>{{ result ? `${result.durationMs} ms` : '—' }}</strong>
      </div>
      <span class="code-label">Response Headers</span>
      <pre class="code-panel compact">{{ formatJson(result?.headers) || '—' }}</pre>
      <span class="code-label">Response Body</span>
      <pre class="code-panel response-body">{{ formatJson(result?.data) || '响应将在这里显示' }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.tester-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.method-grid {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 14px;
}

.card-header,
.response-summary {
  display: flex;
  gap: 10px;
  align-items: center;
}

.card-header {
  justify-content: space-between;
}

.card-header strong,
.card-header span {
  display: block;
}

.card-header span,
.response-summary span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.response-summary {
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.code-label {
  display: block;
  margin: 14px 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
}

.code-panel {
  box-sizing: border-box;
  width: 100%;
  min-height: 120px;
  padding: 14px;
  margin: 0;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.code-panel.compact {
  min-height: 88px;
}

.code-panel.response-body {
  min-height: 220px;
}

@media (width <= 900px) {
  .tester-grid,
  .method-grid {
    grid-template-columns: 1fr;
  }
}
</style>

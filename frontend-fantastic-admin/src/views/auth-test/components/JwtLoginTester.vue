<script setup lang="ts">
import type { AuthTestResult } from '@/api/modules/auth-test'
import type { AuthTestHistoryEvent } from '../types'
import { CopyDocument, Key, Refresh, SwitchButton, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { executeAuthTestRequest } from '@/api/modules/auth-test'
import { useUserStore } from '@/store/modules/user'

const emit = defineEmits<{
  record: [event: AuthTestHistoryEvent]
}>()

type JsonRecord = Record<string, any>

const userStore = useUserStore()
const loading = ref(false)
const result = ref<AuthTestResult | null>(null)
const form = reactive({ account: '', password: '' })
const testedToken = ref(userStore.token || '')
const testedUser = ref<JsonRecord>({ ...userStore.profile })

const hasToken = computed(() => Boolean(testedToken.value.trim()))
const tokenIsCurrent = computed(() => Boolean(
  testedToken.value
  && userStore.token
  && testedToken.value.trim() === userStore.token.trim(),
))
const loginSucceeded = computed(() => Boolean(extractLoginData(result.value?.data).token))
const decodedJwt = computed(() => {
  const token = testedToken.value.trim()
  if (!token) {
    return { header: null, payload: null, error: '' }
  }
  try {
    const parts = token.split('.')
    if (parts.length !== 3) {
      throw new Error('JWT 应包含三个片段')
    }
    return {
      header: decodeJwtPart(parts[0]),
      payload: decodeJwtPart(parts[1]),
      error: '',
    }
  }
  catch (error: unknown) {
    return {
      header: null,
      payload: null,
      error: (error as Error).message || 'JWT 解析失败',
    }
  }
})

function unwrapResultData(value: unknown): JsonRecord {
  if (!value || typeof value !== 'object') {
    return {}
  }
  const root = value as JsonRecord
  return root.data && typeof root.data === 'object' ? root.data as JsonRecord : root
}

function extractLoginData(value: unknown): { token: string, user: JsonRecord } {
  const payload = unwrapResultData(value)
  const nested = payload.data && typeof payload.data === 'object' ? payload.data as JsonRecord : payload
  return {
    token: String(nested.token || nested.accessToken || nested.jwt || ''),
    user: (nested.user || nested.profile || payload.user || {}) as JsonRecord,
  }
}

function decodeJwtPart(value: string): unknown {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, char => char.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(bytes))
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
  return value.status >= 200 && value.status < 300 ? 'success' : 'danger'
}

function record(name: string, method: string, path: string, response: AuthTestResult) {
  emit('record', {
    name,
    method,
    path,
    status: response.status,
    durationMs: response.durationMs,
  })
}

async function sendLogin() {
  if (!form.account.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const response = await executeAuthTestRequest({
      method: 'POST',
      path: '/api/v1/auth/login',
      headers: { 'Content-Type': 'application/json' },
      body: {
        username: form.account.trim(),
        password: form.password,
      },
    })
    result.value = response
    record('登录', 'POST', '/api/v1/auth/login', response)

    const loginData = extractLoginData(response.data)
    if (response.status >= 200 && response.status < 300 && loginData.token) {
      testedToken.value = loginData.token
      testedUser.value = loginData.user
      ElMessage.success('已取得测试 Token，当前后台会话尚未改变')
    }
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '登录接口测试失败')
  }
  finally {
    loading.value = false
  }
}

async function applyTestSession() {
  const loginData = extractLoginData(result.value?.data)
  const token = testedToken.value.trim() || loginData.token
  if (!token) {
    ElMessage.warning('当前没有可应用的测试 Token')
    return
  }
  const user = Object.keys(testedUser.value).length ? testedUser.value : loginData.user
  userStore.setSession({ token, user })
  try {
    await userStore.getPermissions()
    ElMessage.success('测试 Token 已应用为当前后台会话')
  }
  catch {
    ElMessage.warning('Token 已写入当前会话，但读取用户权限失败')
  }
}

async function testCurrentUser() {
  const token = testedToken.value.trim()
  if (!token) {
    ElMessage.warning('请输入或先获取 JWT Token')
    return
  }
  loading.value = true
  try {
    const response = await executeAuthTestRequest({
      method: 'GET',
      path: '/api/v1/auth/me',
      token,
    })
    result.value = response
    record('当前用户', 'GET', '/api/v1/auth/me', response)
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '当前用户接口测试失败')
  }
  finally {
    loading.value = false
  }
}

async function testLogout() {
  const token = testedToken.value.trim()
  if (!token) {
    ElMessage.warning('请输入或先获取 JWT Token')
    return
  }
  try {
    if (tokenIsCurrent.value) {
      await ElMessageBox.confirm(
        '测试 Token 与当前后台会话相同。继续后当前 Token 会被撤销，需要重新登录。',
        '确认测试注销',
        { type: 'warning', confirmButtonText: '继续注销', cancelButtonText: '取消' },
      )
    }
    loading.value = true
    const response = await executeAuthTestRequest({
      method: 'POST',
      path: '/api/v1/auth/logout',
      token,
    })
    result.value = response
    record('注销', 'POST', '/api/v1/auth/logout', response)
    if (tokenIsCurrent.value && response.status >= 200 && response.status < 300) {
      userStore.requestLogout()
    }
  }
  catch (error: unknown) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '注销接口测试失败')
    }
  }
  finally {
    loading.value = false
  }
}

function useCurrentToken() {
  testedToken.value = userStore.token || ''
  testedUser.value = { ...userStore.profile }
}

async function copyToken() {
  if (!testedToken.value) {
    ElMessage.warning('Token 为空')
    return
  }
  try {
    await navigator.clipboard.writeText(testedToken.value)
    ElMessage.success('JWT Token 已复制')
  }
  catch {
    ElMessage.error('浏览器未允许写入剪贴板')
  }
}
</script>

<template>
  <div class="tester-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>用户名密码登录</strong>
            <span>POST /api/v1/auth/login</span>
          </div>
          <el-tag :type="loginSucceeded ? 'success' : 'info'" effect="plain">
            {{ loginSucceeded ? '已取得 Token' : '等待测试' }}
          </el-tag>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.account" autocomplete="username" placeholder="例如 admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="输入测试账号密码"
            @keyup.enter="sendLogin"
          />
        </el-form-item>
        <div class="button-row">
          <el-button type="primary" :icon="Key" :loading="loading" @click="sendLogin">
            发送登录请求
          </el-button>
          <el-button :disabled="!hasToken" @click="applyTestSession">应用到当前会话</el-button>
        </div>
      </el-form>

      <div class="response-summary">
        <span>HTTP 状态</span>
        <el-tag :type="responseType(result)" effect="plain">{{ result?.status ?? '—' }}</el-tag>
        <span>耗时</span>
        <strong>{{ result ? `${result.durationMs} ms` : '—' }}</strong>
      </div>
      <pre class="code-panel">{{ formatJson(result?.data) || '登录响应将在这里显示' }}</pre>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>JWT 查看与验证</strong>
            <span>本地解析不代表后端签名验证通过</span>
          </div>
          <el-button text :icon="Refresh" @click="useCurrentToken">使用当前 Token</el-button>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="测试 JWT Token">
          <el-input v-model="testedToken" type="textarea" :rows="4" placeholder="登录成功后自动填入，也可手动粘贴" />
        </el-form-item>
        <div class="button-row">
          <el-button :icon="View" :loading="loading" @click="testCurrentUser">调用 /auth/me</el-button>
          <el-button type="danger" plain :icon="SwitchButton" :loading="loading" @click="testLogout">
            测试注销
          </el-button>
          <el-button :icon="CopyDocument" @click="copyToken">复制</el-button>
        </div>
      </el-form>

      <el-alert v-if="decodedJwt.error" type="error" :closable="false" :title="decodedJwt.error" />
      <div v-else class="jwt-grid">
        <div>
          <span class="code-label">Header</span>
          <pre class="code-panel compact">{{ formatJson(decodedJwt.header) || '—' }}</pre>
        </div>
        <div>
          <span class="code-label">Payload</span>
          <pre class="code-panel compact">{{ formatJson(decodedJwt.payload) || '—' }}</pre>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.tester-grid,
.jwt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.card-header,
.button-row,
.response-summary {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-header {
  justify-content: space-between;
}

.card-header span,
.response-summary span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.card-header strong,
.card-header span {
  display: block;
}

.button-row,
.response-summary {
  flex-wrap: wrap;
}

.response-summary {
  margin: 14px 0;
}

.code-label {
  display: block;
  margin: 14px 0 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
}

.code-panel {
  box-sizing: border-box;
  width: 100%;
  min-height: 120px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.code-panel.compact {
  min-height: 88px;
}

@media (max-width: 900px) {
  .tester-grid,
  .jwt-grid {
    grid-template-columns: 1fr;
  }
}
</style>

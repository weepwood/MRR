<script setup lang="ts">
import type { Method } from 'axios'
import type { AuthTestResult } from '@/api/modules/auth-test'
import { Connection, CopyDocument, Key, Promotion, Refresh, SwitchButton, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { executeAuthTestRequest } from '@/api/modules/auth-test'
import { useUserStore } from '@/store/modules/user'
import {
  createExternalArchiveSignature,
  createRequestNonce,
} from '@/utils/external-archive-signature'

defineOptions({ name: 'AuthenticationApiTestPage' })

type JsonRecord = Record<string, any>

interface RequestHistoryItem {
  id: number
  name: string
  method: string
  path: string
  status: number
  durationMs: number
  requestedAt: string
}

const userStore = useUserStore()
const activeTab = ref('login')
const requestHistory = ref<RequestHistoryItem[]>([])
let historySequence = 0

const loginLoading = ref(false)
const loginForm = reactive({
  account: '',
  password: '',
})
const loginResult = ref<AuthTestResult | null>(null)
const testedToken = ref(userStore.token || '')
const testedUser = ref<JsonRecord>({ ...userStore.profile })

const customLoading = ref(false)
const customResult = ref<AuthTestResult | null>(null)
const customRequest = reactive({
  method: 'GET' as Method,
  path: '/api/v1/auth/me',
  useToken: true,
  token: userStore.token || '',
  headersText: '{\n  "Accept": "application/json"\n}',
  bodyText: '',
})

const externalLoading = ref(false)
const externalSigning = ref(false)
const externalResult = ref<AuthTestResult | null>(null)
const externalRawBody = ref('')
const externalBodyHash = ref('')
const externalCanonicalText = ref('')
const externalSignature = ref('')
const externalLaunchUrl = ref('')
const externalForm = reactive({
  path: '/api/v1/integration/archive/tickets',
  clientId: 'his-system',
  secret: '',
  timestamp: String(Math.floor(Date.now() / 1000)),
  nonce: createRequestNonce(),
  externalUserId: 'HIS-USER-10086',
  idCard: '',
  bah: '',
  sjh: '',
  bahsText: '',
  sjhsText: '',
  archivesText: '',
  allowDownload: false,
})

const apiBaseUrl = computed(() => import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL)
const currentPermissionCount = computed(() => userStore.permissions.length)
const hasTestToken = computed(() => Boolean(testedToken.value.trim()))
const loginSucceeded = computed(() => Boolean(extractLoginData(loginResult.value?.data).token))
const loginTokenIsCurrent = computed(() => Boolean(
  testedToken.value
  && userStore.token
  && testedToken.value.trim() === userStore.token.trim(),
))

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

function parseJsonRecord(value: string, label: string): Record<string, string> {
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
    Object.entries(parsed as Record<string, unknown>)
      .map(([key, item]) => [key, String(item)]),
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

function unwrapResultData(value: unknown): JsonRecord {
  if (!value || typeof value !== 'object') {
    return {}
  }
  const root = value as JsonRecord
  if (root.data && typeof root.data === 'object') {
    return root.data as JsonRecord
  }
  return root
}

function extractLoginData(value: unknown): { token: string, user: JsonRecord } {
  const payload = unwrapResultData(value)
  const nested = payload.data && typeof payload.data === 'object' ? payload.data as JsonRecord : payload
  return {
    token: String(nested.token || nested.accessToken || nested.jwt || ''),
    user: (nested.user || nested.profile || payload.user || {}) as JsonRecord,
  }
}

function recordHistory(name: string, method: string, path: string, result: AuthTestResult) {
  requestHistory.value.unshift({
    id: ++historySequence,
    name,
    method,
    path,
    status: result.status,
    durationMs: result.durationMs,
    requestedAt: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
  })
  requestHistory.value = requestHistory.value.slice(0, 12)
}

function responseType(result: AuthTestResult | null) {
  if (!result) {
    return 'info'
  }
  if (result.status >= 200 && result.status < 300) {
    return 'success'
  }
  if (result.status === 401 || result.status === 403) {
    return 'warning'
  }
  return 'danger'
}

async function sendLogin() {
  if (!loginForm.account.trim() || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loginLoading.value = true
  try {
    const result = await executeAuthTestRequest({
      method: 'POST',
      path: '/api/v1/auth/login',
      headers: { 'Content-Type': 'application/json' },
      body: {
        username: loginForm.account.trim(),
        password: loginForm.password,
      },
    })
    loginResult.value = result
    recordHistory('登录', 'POST', '/api/v1/auth/login', result)
    const loginData = extractLoginData(result.data)
    if (result.status >= 200 && result.status < 300 && loginData.token) {
      testedToken.value = loginData.token
      customRequest.token = loginData.token
      testedUser.value = loginData.user
      ElMessage.success('登录接口返回了可用 Token，尚未替换当前会话')
    }
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '登录接口测试失败')
  }
  finally {
    loginLoading.value = false
  }
}

async function applyTestSession() {
  const loginData = extractLoginData(loginResult.value?.data)
  const token = testedToken.value.trim() || loginData.token
  if (!token) {
    ElMessage.warning('当前没有可应用的测试 Token')
    return
  }
  const user = Object.keys(testedUser.value).length ? testedUser.value : loginData.user
  userStore.setSession({ token, user })
  customRequest.token = token
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
  customLoading.value = true
  try {
    const result = await executeAuthTestRequest({
      method: 'GET',
      path: '/api/v1/auth/me',
      token,
    })
    customResult.value = result
    recordHistory('当前用户', 'GET', '/api/v1/auth/me', result)
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '当前用户接口测试失败')
  }
  finally {
    customLoading.value = false
  }
}

async function testLogout() {
  const token = testedToken.value.trim()
  if (!token) {
    ElMessage.warning('请输入或先获取 JWT Token')
    return
  }
  if (loginTokenIsCurrent.value) {
    await ElMessageBox.confirm(
      '测试 Token 与当前后台会话相同。继续后，当前 Token 会被后端撤销，需要重新登录。',
      '确认测试注销',
      { type: 'warning', confirmButtonText: '继续注销', cancelButtonText: '取消' },
    )
  }
  customLoading.value = true
  try {
    const result = await executeAuthTestRequest({
      method: 'POST',
      path: '/api/v1/auth/logout',
      token,
    })
    customResult.value = result
    recordHistory('注销', 'POST', '/api/v1/auth/logout', result)
    if (loginTokenIsCurrent.value && result.status >= 200 && result.status < 300) {
      userStore.requestLogout()
    }
  }
  catch (error: unknown) {
    if ((error as string) !== 'cancel') {
      ElMessage.error((error as Error).message || '注销接口测试失败')
    }
  }
  finally {
    customLoading.value = false
  }
}

async function sendCustomRequest() {
  customLoading.value = true
  try {
    const headers = parseJsonRecord(customRequest.headersText, '请求头')
    const body = parseJsonBody(customRequest.bodyText)
    const result = await executeAuthTestRequest({
      method: customRequest.method,
      path: customRequest.path,
      token: customRequest.useToken ? customRequest.token : '',
      headers,
      body,
    })
    customResult.value = result
    recordHistory('自定义请求', String(customRequest.method), customRequest.path, result)
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '自定义请求失败')
  }
  finally {
    customLoading.value = false
  }
}

function splitValues(value: string): string[] {
  return value
    .split(/[\r\n,，]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function parseArchivePairs(value: string): Array<{ bah: string, sjh: string }> {
  return value
    .split(/\r?\n/)
    .map(item => item.trim())
    .filter(Boolean)
    .map((item, index) => {
      const parts = item.split(/[:：,，\s]+/).map(part => part.trim()).filter(Boolean)
      if (parts.length !== 2) {
        throw new Error(`精确病案第 ${index + 1} 行应为“病案号:上架号”`)
      }
      return { bah: parts[0], sjh: parts[1] }
    })
}

function buildExternalPayload(): JsonRecord {
  const payload: JsonRecord = {
    externalUserId: externalForm.externalUserId.trim(),
    allowDownload: externalForm.allowDownload,
  }
  if (!payload.externalUserId) {
    throw new Error('externalUserId 不能为空')
  }

  const optionalTextFields: Array<[string, string]> = [
    ['idCard', externalForm.idCard],
    ['bah', externalForm.bah],
    ['sjh', externalForm.sjh],
  ]
  optionalTextFields.forEach(([key, value]) => {
    if (value.trim()) {
      payload[key] = value.trim()
    }
  })

  const bahs = splitValues(externalForm.bahsText)
  const sjhs = splitValues(externalForm.sjhsText)
  const archives = parseArchivePairs(externalForm.archivesText)
  if (bahs.length) {
    payload.bahs = bahs
  }
  if (sjhs.length) {
    payload.sjhs = sjhs
  }
  if (archives.length) {
    payload.archives = archives
  }
  if (!payload.idCard && !payload.bah && !payload.sjh && !bahs.length && !sjhs.length && !archives.length) {
    throw new Error('至少填写身份证、病案号、上架号或精确病案中的一种')
  }
  return payload
}

function syncExternalBody() {
  try {
    externalRawBody.value = JSON.stringify(buildExternalPayload())
    externalBodyHash.value = ''
    externalCanonicalText.value = ''
    externalSignature.value = ''
    ElMessage.success('已根据表单生成实际发送的紧凑 JSON')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '生成请求体失败')
  }
}

function refreshSigningParameters() {
  externalForm.timestamp = String(Math.floor(Date.now() / 1000))
  externalForm.nonce = createRequestNonce()
  externalBodyHash.value = ''
  externalCanonicalText.value = ''
  externalSignature.value = ''
}

async function generateExternalSignature() {
  if (!externalForm.clientId.trim()) {
    throw new Error('clientId 不能为空')
  }
  if (!externalForm.secret) {
    throw new Error('HMAC 密钥不能为空')
  }
  if (!externalForm.timestamp.trim() || !externalForm.nonce.trim()) {
    throw new Error('时间戳和 nonce 不能为空')
  }
  if (!externalRawBody.value.trim()) {
    externalRawBody.value = JSON.stringify(buildExternalPayload())
  }

  externalSigning.value = true
  try {
    const signed = await createExternalArchiveSignature({
      method: 'POST',
      path: externalForm.path,
      timestamp: externalForm.timestamp,
      nonce: externalForm.nonce,
      rawBody: externalRawBody.value,
      secret: externalForm.secret,
    })
    externalBodyHash.value = signed.bodyHash
    externalCanonicalText.value = signed.canonicalText
    externalSignature.value = signed.signature
    return signed.signature
  }
  finally {
    externalSigning.value = false
  }
}

async function signOnly() {
  try {
    await generateExternalSignature()
    ElMessage.success('签名已生成，可手动调整后再发送')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '签名生成失败')
  }
}

async function sendExternalTicket() {
  externalLoading.value = true
  externalLaunchUrl.value = ''
  try {
    if (!externalRawBody.value.trim()) {
      externalRawBody.value = JSON.stringify(buildExternalPayload())
    }
    const signature = externalSignature.value.trim() || await generateExternalSignature()
    const result = await executeAuthTestRequest({
      method: 'POST',
      path: externalForm.path,
      headers: {
        'Content-Type': 'application/json',
        'X-MRR-Client-Id': externalForm.clientId.trim(),
        'X-MRR-Timestamp': externalForm.timestamp.trim(),
        'X-MRR-Nonce': externalForm.nonce.trim(),
        'X-MRR-Signature': signature,
      },
      rawBody: externalRawBody.value,
    })
    externalResult.value = result
    recordHistory('外部访问票据', 'POST', externalForm.path, result)
    const payload = unwrapResultData(result.data)
    externalLaunchUrl.value = String(payload.launchUrl || payload.data?.launchUrl || '')
    if (result.status >= 200 && result.status < 300) {
      ElMessage.success('一次性外部访问票据创建成功')
    }
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '外部票据请求失败')
  }
  finally {
    externalLoading.value = false
  }
}

function openExternalLaunchUrl() {
  if (!externalLaunchUrl.value) {
    ElMessage.warning('响应中没有可打开的 launchUrl')
    return
  }
  window.open(externalLaunchUrl.value, '_blank', 'noopener,noreferrer')
}

async function copyText(value: string, label: string) {
  if (!value) {
    ElMessage.warning(`${label}为空`)
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(`${label}已复制`)
  }
  catch {
    ElMessage.error('浏览器未允许写入剪贴板')
  }
}

function useCurrentToken() {
  testedToken.value = userStore.token || ''
  customRequest.token = userStore.token || ''
}

syncExternalBody()
</script>

<template>
  <div class="auth-test-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">
          Authentication Playground
        </p>
        <h2>认证与外部访问测试台</h2>
        <p class="subtitle">
          可视化测试内部 JWT 登录、受保护接口和外部影像票据签名。所有测试请求仅发送到当前 MRR 后端。
        </p>
      </div>
      <el-tag type="warning" effect="plain">
        仅管理员测试使用
      </el-tag>
    </header>

    <section class="metric-grid" aria-label="测试环境概览">
      <el-card shadow="never" class="metric-card">
        <span>当前账号</span>
        <strong>{{ userStore.profile.displayName || userStore.profile.username || userStore.account || '未登录' }}</strong>
        <small>{{ userStore.profile.roleName || userStore.profile.roleCode || '无角色信息' }}</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>当前权限</span>
        <strong>{{ currentPermissionCount }}</strong>
        <small>实时从数据库加载的权限数量</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>测试 Token</span>
        <strong>{{ hasTestToken ? '已就绪' : '未设置' }}</strong>
        <small>{{ loginTokenIsCurrent ? '与当前会话相同' : '与当前会话隔离' }}</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>API Base URL</span>
        <strong class="base-url">{{ apiBaseUrl }}</strong>
        <small>不允许测试台请求外部域名</small>
      </el-card>
    </section>

    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="外部系统 HMAC 密钥只保存在当前页面内存，不会写入 localStorage；请勿在录屏、截图或共享环境中输入生产密钥。"
    />

    <el-tabs v-model="activeTab" class="test-tabs">
      <el-tab-pane name="login" label="内部登录与 JWT">
        <div class="two-column-grid">
          <el-card shadow="never" class="test-card">
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
                <el-input v-model="loginForm.account" autocomplete="username" placeholder="例如 admin" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  placeholder="输入测试账号密码"
                  @keyup.enter="sendLogin"
                />
              </el-form-item>
              <div class="button-row">
                <el-button type="primary" :icon="Key" :loading="loginLoading" @click="sendLogin">
                  发送登录请求
                </el-button>
                <el-button :disabled="!hasTestToken" @click="applyTestSession">
                  应用到当前会话
                </el-button>
              </div>
            </el-form>

            <div class="response-summary">
              <span>HTTP 状态</span>
              <el-tag :type="responseType(loginResult)" effect="plain">
                {{ loginResult?.status ?? '—' }}
              </el-tag>
              <span>耗时</span>
              <strong>{{ loginResult ? `${loginResult.durationMs} ms` : '—' }}</strong>
            </div>
            <pre class="code-panel">{{ formatJson(loginResult?.data) || '登录响应将在这里显示' }}</pre>
          </el-card>

          <el-card shadow="never" class="test-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>JWT 查看与验证</strong>
                  <span>本地解析不代表签名验证通过</span>
                </div>
                <el-button text :icon="Refresh" @click="useCurrentToken">
                  使用当前 Token
                </el-button>
              </div>
            </template>

            <el-form label-position="top">
              <el-form-item label="测试 JWT Token">
                <el-input
                  v-model="testedToken"
                  type="textarea"
                  :rows="4"
                  placeholder="登录成功后自动填入，也可手动粘贴"
                  @input="customRequest.token = testedToken"
                />
              </el-form-item>
              <div class="button-row">
                <el-button :icon="View" :loading="customLoading" @click="testCurrentUser">
                  调用 /auth/me
                </el-button>
                <el-button type="danger" plain :icon="SwitchButton" :loading="customLoading" @click="testLogout">
                  测试注销
                </el-button>
                <el-button :icon="CopyDocument" @click="copyText(testedToken, 'JWT Token')">
                  复制
                </el-button>
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
      </el-tab-pane>

      <el-tab-pane name="request" label="受保护接口调试">
        <div class="two-column-grid request-grid">
          <el-card shadow="never" class="test-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>自定义当前后端请求</strong>
                  <span>故意测试 401/403 不会触发全局自动退出</span>
                </div>
              </div>
            </template>

            <el-form label-position="top">
              <div class="method-path-grid">
                <el-form-item label="方法">
                  <el-select v-model="customRequest.method">
                    <el-option v-for="method in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']" :key="method" :label="method" :value="method" />
                  </el-select>
                </el-form-item>
                <el-form-item label="接口路径">
                  <el-input v-model="customRequest.path" placeholder="/api/v1/auth/me" />
                </el-form-item>
              </div>

              <el-form-item>
                <el-checkbox v-model="customRequest.useToken">
                  添加 Authorization: Bearer Token
                </el-checkbox>
              </el-form-item>
              <el-form-item v-if="customRequest.useToken" label="Bearer Token">
                <el-input v-model="customRequest.token" type="textarea" :rows="3" />
              </el-form-item>
              <el-form-item label="附加请求头 JSON">
                <el-input v-model="customRequest.headersText" type="textarea" :rows="4" />
              </el-form-item>
              <el-form-item label="请求体 JSON">
                <el-input
                  v-model="customRequest.bodyText"
                  type="textarea"
                  :rows="7"
                  placeholder="GET 请求可以留空"
                />
              </el-form-item>
              <el-button type="primary" :icon="Connection" :loading="customLoading" @click="sendCustomRequest">
                发送请求
              </el-button>
            </el-form>
          </el-card>

          <el-card shadow="never" class="test-card result-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>接口响应</strong>
                  <span>保留 HTTP 状态、响应头和业务响应体</span>
                </div>
                <el-tag :type="responseType(customResult)" effect="plain">
                  {{ customResult?.status ?? '未发送' }}
                </el-tag>
              </div>
            </template>
            <div class="response-summary">
              <span>状态文本</span>
              <strong>{{ customResult?.statusText || '—' }}</strong>
              <span>耗时</span>
              <strong>{{ customResult ? `${customResult.durationMs} ms` : '—' }}</strong>
            </div>
            <span class="code-label">Response Headers</span>
            <pre class="code-panel compact">{{ formatJson(customResult?.headers) || '—' }}</pre>
            <span class="code-label">Response Body</span>
            <pre class="code-panel response-body">{{ formatJson(customResult?.data) || '响应将在这里显示' }}</pre>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane name="external" label="外部影像票据">
        <div class="external-stack">
          <el-card shadow="never" class="test-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>票据业务参数</strong>
                  <span>所有定位条件按并集解析；精确配对使用 archives</span>
                </div>
                <el-switch v-model="externalForm.allowDownload" active-text="允许下载" inactive-text="只读" />
              </div>
            </template>

            <el-form label-position="top">
              <div class="form-grid three">
                <el-form-item label="外部用户 ID">
                  <el-input v-model="externalForm.externalUserId" />
                </el-form-item>
                <el-form-item label="身份证号">
                  <el-input v-model="externalForm.idCard" placeholder="可选" />
                </el-form-item>
                <el-form-item label="单个病案号">
                  <el-input v-model="externalForm.bah" placeholder="可选" />
                </el-form-item>
                <el-form-item label="单个上架号">
                  <el-input v-model="externalForm.sjh" placeholder="可选" />
                </el-form-item>
                <el-form-item label="多个病案号">
                  <el-input v-model="externalForm.bahsText" type="textarea" :rows="4" placeholder="每行一个，或用逗号分隔" />
                </el-form-item>
                <el-form-item label="多个上架号">
                  <el-input v-model="externalForm.sjhsText" type="textarea" :rows="4" placeholder="每行一个，或用逗号分隔" />
                </el-form-item>
              </div>
              <el-form-item label="精确病案号与上架号组合">
                <el-input
                  v-model="externalForm.archivesText"
                  type="textarea"
                  :rows="4"
                  placeholder="每行一个，例如：10000001:20000001"
                />
              </el-form-item>
              <el-button @click="syncExternalBody">
                从表单生成实际 JSON
              </el-button>
            </el-form>
          </el-card>

          <div class="two-column-grid">
            <el-card shadow="never" class="test-card">
              <template #header>
                <div class="card-header">
                  <div>
                    <strong>签名参数</strong>
                    <span>可手动修改时间戳、nonce 和签名测试异常情况</span>
                  </div>
                  <el-button text :icon="Refresh" @click="refreshSigningParameters">
                    更新时间与 nonce
                  </el-button>
                </div>
              </template>

              <el-form label-position="top">
                <el-form-item label="接口路径">
                  <el-input v-model="externalForm.path" />
                </el-form-item>
                <div class="form-grid two">
                  <el-form-item label="Client ID">
                    <el-input v-model="externalForm.clientId" />
                  </el-form-item>
                  <el-form-item label="HMAC Secret">
                    <el-input v-model="externalForm.secret" type="password" show-password autocomplete="off" />
                  </el-form-item>
                  <el-form-item label="Unix 时间戳（秒）">
                    <el-input v-model="externalForm.timestamp" />
                  </el-form-item>
                  <el-form-item label="Nonce">
                    <el-input v-model="externalForm.nonce" />
                  </el-form-item>
                </div>
                <el-form-item label="实际发送的原始 JSON">
                  <el-input v-model="externalRawBody" type="textarea" :rows="9" />
                </el-form-item>
                <div class="button-row">
                  <el-button :loading="externalSigning" @click="signOnly">
                    仅生成签名
                  </el-button>
                  <el-button type="primary" :icon="Promotion" :loading="externalLoading" @click="sendExternalTicket">
                    发送票据请求
                  </el-button>
                </div>
              </el-form>
            </el-card>

            <el-card shadow="never" class="test-card">
              <template #header>
                <div class="card-header">
                  <div>
                    <strong>签名计算结果</strong>
                    <span>签名原文与实际请求体必须完全一致</span>
                  </div>
                </div>
              </template>
              <span class="code-label">SHA-256(rawBody)</span>
              <div class="copy-line">
                <code>{{ externalBodyHash || '尚未计算' }}</code>
                <el-button text :icon="CopyDocument" @click="copyText(externalBodyHash, 'Body Hash')" />
              </div>
              <span class="code-label">Canonical Text</span>
              <pre class="code-panel compact">{{ externalCanonicalText || '尚未计算' }}</pre>
              <span class="code-label">X-MRR-Signature</span>
              <el-input v-model="externalSignature" type="textarea" :rows="3" />
            </el-card>
          </div>

          <el-card shadow="never" class="test-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>票据响应</strong>
                  <span>成功后可在新窗口打开一次性 launchUrl</span>
                </div>
                <el-tag :type="responseType(externalResult)" effect="plain">
                  {{ externalResult?.status ?? '未发送' }}
                </el-tag>
              </div>
            </template>
            <div class="response-summary">
              <span>耗时</span>
              <strong>{{ externalResult ? `${externalResult.durationMs} ms` : '—' }}</strong>
              <span>Launch URL</span>
              <strong class="launch-url">{{ externalLaunchUrl || '—' }}</strong>
              <el-button :disabled="!externalLaunchUrl" @click="openExternalLaunchUrl">
                打开影像档案袋
              </el-button>
            </div>
            <pre class="code-panel response-body">{{ formatJson(externalResult?.data) || '票据响应将在这里显示' }}</pre>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane name="history" label="请求记录">
        <el-card shadow="never" class="test-card">
          <template #header>
            <div class="card-header">
              <div>
                <strong>本页请求记录</strong>
                <span>仅保存在页面内存，刷新后清空</span>
              </div>
              <el-button text @click="requestHistory = []">
                清空
              </el-button>
            </div>
          </template>
          <el-table :data="requestHistory" empty-text="尚未发送测试请求">
            <el-table-column prop="requestedAt" label="时间" width="100" />
            <el-table-column prop="name" label="场景" width="130" />
            <el-table-column prop="method" label="方法" width="90" />
            <el-table-column prop="path" label="路径" min-width="260" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status >= 200 && row.status < 300 ? 'success' : 'danger'" effect="plain">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="110">
              <template #default="{ row }">
                {{ row.durationMs }} ms
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.auth-test-page {
  display: grid;
  gap: 18px;
}

.page-header,
.card-header,
.response-summary,
.button-row,
.copy-line {
  display: flex;
  align-items: center;
}

.page-header,
.card-header {
  justify-content: space-between;
}

.page-header {
  gap: 18px;
  align-items: flex-start;
}

.page-header h2,
.page-header p {
  margin: 0;
}

.eyebrow {
  margin-bottom: 5px !important;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  letter-spacing: .08em;
  text-transform: uppercase;
}

.subtitle {
  margin-top: 8px !important;
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card :deep(.el-card__body) {
  display: grid;
  gap: 5px;
  min-height: 86px;
}

.metric-card span,
.metric-card small,
.card-header span,
.code-label {
  color: var(--el-text-color-secondary);
}

.metric-card strong {
  overflow: hidden;
  font-size: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card .base-url {
  font-size: 15px;
}

.test-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.two-column-grid,
.form-grid {
  display: grid;
  gap: 14px;
}

.two-column-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.external-stack {
  display: grid;
  gap: 14px;
}

.test-card {
  min-width: 0;
}

.card-header {
  gap: 12px;
}

.card-header > div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.button-row {
  flex-wrap: wrap;
  gap: 8px;
}

.method-path-grid {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 12px;
}

.response-summary {
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 0 10px;
}

.response-summary span {
  color: var(--el-text-color-secondary);
}

.jwt-grid {
  display: grid;
  grid-template-columns: minmax(0, .8fr) minmax(0, 1.2fr);
  gap: 10px;
  margin-top: 14px;
}

.code-label {
  display: block;
  margin: 12px 0 6px;
  font-size: 13px;
}

.code-panel {
  box-sizing: border-box;
  min-height: 160px;
  max-height: 420px;
  padding: 14px;
  margin: 0;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.code-panel.compact {
  min-height: 92px;
  max-height: 260px;
}

.code-panel.response-body {
  min-height: 260px;
}

.copy-line {
  gap: 8px;
  justify-content: space-between;
  min-height: 38px;
  padding: 7px 10px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.copy-line code,
.launch-url {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width <= 1200px) {
  .metric-grid,
  .form-grid.three {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 860px) {
  .two-column-grid,
  .jwt-grid,
  .form-grid.two,
  .form-grid.three,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .card-header {
    align-items: flex-start;
  }

  .page-header {
    flex-direction: column;
  }
}

@media (width <= 560px) {
  .method-path-grid {
    grid-template-columns: 1fr;
  }
}
</style>

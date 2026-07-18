<script setup lang="ts">
import type { Method } from 'axios'
import type { AuthTestResult } from '@/api/modules/auth-test'
import type { ExternalArchiveIntegrationStatus } from '@/utils/external-archive-test-guide'
import {
  Connection,
  CopyDocument,
  Key,
  MagicStick,
  Promotion,
  Refresh,
  Setting,
  SwitchButton,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { executeAuthTestRequest } from '@/api/modules/auth-test'
import { useUserStore } from '@/store/modules/user'
import {
  createExternalArchiveSignature,
  createRequestNonce,
} from '@/utils/external-archive-signature'
import {
  buildExternalArchiveIntegrationConfig,
  createRandomHmacSecret,
  explainExternalArchiveTicketFailure,
  findExternalArchiveClient,
  getExternalArchiveReadiness,
} from '@/utils/external-archive-test-guide'

defineOptions({ name: 'AuthenticationApiTestPage' })

type JsonRecord = Record<string, any>
type TicketScenario = 'normal' | 'expired' | 'bad-signature'

interface RequestHistoryItem {
  id: number
  name: string
  method: string
  path: string
  status: number
  durationMs: number
  requestedAt: string
}

interface TicketRequestSnapshot {
  timestamp: string
  nonce: string
  rawBody: string
  signature: string
}

const userStore = useUserStore()
const activeTab = ref('external')
const requestHistory = ref<RequestHistoryItem[]>([])
let historySequence = 0

const loginLoading = ref(false)
const loginForm = reactive({ account: '', password: '' })
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

const integrationStatusLoading = ref(false)
const integrationStatusResult = ref<AuthTestResult | null>(null)
const integrationStatus = ref<ExternalArchiveIntegrationStatus | null>(null)
const integrationStatusError = ref('')

const externalLoading = ref(false)
const externalSigning = ref(false)
const externalResult = ref<AuthTestResult | null>(null)
const externalRawBody = ref('')
const externalBodyHash = ref('')
const externalCanonicalText = ref('')
const externalSignature = ref('')
const externalLaunchUrl = ref('')
const externalFailureAdvice = ref('')
const generatedSecretLocally = ref(false)
const scenarioNote = ref('正常请求会自动生成当前时间戳和新 nonce。')
const lastSuccessfulTicketRequest = ref<TicketRequestSnapshot | null>(null)

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
  manualSignature: false,
})

const apiBaseUrl = computed(() => import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL)
const currentPermissionCount = computed(() => userStore.permissions.length)
const hasTestToken = computed(() => Boolean(testedToken.value.trim()))
const loginTokenIsCurrent = computed(() => Boolean(
  testedToken.value
  && userStore.token
  && testedToken.value.trim() === userStore.token.trim(),
))
const loginSucceeded = computed(() => Boolean(extractLoginData(loginResult.value?.data).token))

const selectedIntegrationClient = computed(() => findExternalArchiveClient(
  integrationStatus.value,
  externalForm.clientId,
))
const integrationReadiness = computed(() => getExternalArchiveReadiness(
  integrationStatus.value,
  externalForm.clientId,
  externalForm.secret,
))
const integrationAlertType = computed(() => integrationReadiness.value.level === 'danger'
  ? 'error'
  : integrationReadiness.value.level)
const configClientIndex = computed(() => {
  const clients = integrationStatus.value?.clients ?? []
  const index = clients.findIndex(client => client.clientId === externalForm.clientId.trim())
  return index >= 0 ? index : clients.length
})
const integrationConfigSnippet = computed(() => buildExternalArchiveIntegrationConfig({
  clientId: externalForm.clientId,
  secret: externalForm.secret,
  allowedIp: integrationStatus.value?.requestIp || '127.0.0.1',
  clientIndex: configClientIndex.value,
  ticketTtlSeconds: integrationStatus.value?.ticketTtlSeconds,
  sessionTtlSeconds: integrationStatus.value?.sessionTtlSeconds,
  timestampToleranceSeconds: integrationStatus.value?.timestampToleranceSeconds,
  maxArchivesPerTicket: integrationStatus.value?.maxArchivesPerTicket,
}))
const externalConfigChecks = computed(() => [
  {
    label: '集成功能',
    ok: integrationStatus.value?.enabled === true,
    detail: integrationStatus.value?.enabled ? '已启用' : '需要设置 enabled=true',
  },
  {
    label: 'Client ID',
    ok: Boolean(selectedIntegrationClient.value),
    detail: selectedIntegrationClient.value ? '后端已配置' : '后端未找到',
  },
  {
    label: '服务端 Secret',
    ok: selectedIntegrationClient.value?.secretConfigured === true,
    detail: selectedIntegrationClient.value?.secretConfigured ? '已配置（明文不返回）' : '未配置',
  },
  {
    label: '来源 IP',
    ok: selectedIntegrationClient.value?.requestIpAllowed === true,
    detail: integrationStatus.value?.requestIp || '尚未识别',
  },
  {
    label: '页面 Secret',
    ok: Boolean(externalForm.secret.trim()),
    detail: externalForm.secret.trim() ? '已填写' : '尚未填写',
  },
])

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

function responseType(result: AuthTestResult | null) {
  if (!result) {
    return 'info'
  }
  if (result.status >= 200 && result.status < 300) {
    return 'success'
  }
  if (result.status === 401 || result.status === 403 || result.status === 409) {
    return 'warning'
  }
  return 'danger'
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
  requestHistory.value = requestHistory.value.slice(0, 20)
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
      ElMessage.success('已取得测试 Token，当前后台会话尚未改变')
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

  try {
    if (loginTokenIsCurrent.value) {
      await ElMessageBox.confirm(
        '测试 Token 与当前后台会话相同。继续后，当前 Token 会被后端撤销，需要重新登录。',
        '确认测试注销',
        { type: 'warning', confirmButtonText: '继续注销', cancelButtonText: '取消' },
      )
    }

    customLoading.value = true
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
    if (error !== 'cancel' && error !== 'close') {
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
    const result = await executeAuthTestRequest({
      method: customRequest.method,
      path: customRequest.path,
      token: customRequest.useToken ? customRequest.token : '',
      headers: parseJsonObject(customRequest.headersText, '请求头'),
      body: parseJsonBody(customRequest.bodyText),
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

async function checkIntegrationStatus(showMessage = true) {
  integrationStatusLoading.value = true
  integrationStatusError.value = ''
  try {
    const result = await executeAuthTestRequest({
      method: 'GET',
      path: '/api/v1/integration/archive/status',
      token: userStore.token,
    })
    integrationStatusResult.value = result
    recordHistory('集成配置检查', 'GET', '/api/v1/integration/archive/status', result)

    if (result.status < 200 || result.status >= 300) {
      integrationStatus.value = null
      const body = result.data as JsonRecord
      throw new Error(String(body?.message || `配置检查失败：HTTP ${result.status}`))
    }

    integrationStatus.value = unwrapResultData(result.data) as unknown as ExternalArchiveIntegrationStatus
    const clients = integrationStatus.value.clients ?? []
    if (clients.length && !clients.some(client => client.clientId === externalForm.clientId)) {
      externalForm.clientId = clients[0].clientId
    }
    if (showMessage) {
      ElMessage.success('已读取后端外部集成状态，Secret 明文不会返回')
    }
  }
  catch (error: unknown) {
    integrationStatus.value = null
    integrationStatusError.value = (error as Error).message || '无法读取后端集成状态'
    if (showMessage) {
      ElMessage.error(integrationStatusError.value)
    }
  }
  finally {
    integrationStatusLoading.value = false
  }
}

function generateHmacSecret() {
  try {
    externalForm.secret = createRandomHmacSecret()
    generatedSecretLocally.value = true
    resetSignatureResult()
    ElMessage.success('已生成 256 位随机 Secret。下一步必须复制配置片段到后端并重启服务。')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '生成 HMAC Secret 失败')
  }
}

function markSecretAsManual() {
  generatedSecretLocally.value = false
  resetSignatureResult()
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

  const optionalFields: Array<[string, string]> = [
    ['idCard', externalForm.idCard],
    ['bah', externalForm.bah],
    ['sjh', externalForm.sjh],
  ]
  optionalFields.forEach(([key, value]) => {
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

function resetSignatureResult() {
  externalBodyHash.value = ''
  externalCanonicalText.value = ''
  externalSignature.value = ''
  externalFailureAdvice.value = ''
}

function syncExternalBody(showMessage = true) {
  try {
    externalRawBody.value = JSON.stringify(buildExternalPayload())
    resetSignatureResult()
    if (showMessage) {
      ElMessage.success('已根据业务参数生成实际发送的紧凑 JSON')
    }
    return true
  }
  catch (error: unknown) {
    if (showMessage) {
      ElMessage.error((error as Error).message || '生成请求体失败')
    }
    return false
  }
}

function refreshSigningParameters() {
  externalForm.timestamp = String(Math.floor(Date.now() / 1000))
  externalForm.nonce = createRequestNonce()
  resetSignatureResult()
}

async function generateExternalSignature() {
  if (!externalForm.clientId.trim()) {
    throw new Error('Client ID 不能为空')
  }
  if (!externalForm.secret.trim()) {
    throw new Error('HMAC Secret 不能为空。它必须与后端配置中的对应客户端 Secret 完全相同。')
  }
  if (!externalForm.timestamp.trim() || !externalForm.nonce.trim()) {
    throw new Error('时间戳和 nonce 不能为空')
  }
  if (!externalRawBody.value.trim() && !syncExternalBody(false)) {
    throw new Error('请先填写有效的病案定位条件')
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
    ElMessage.success('签名已生成，可检查 Canonical Text 或切换手工签名')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '签名生成失败')
  }
}

async function prepareTicketScenario(scenario: TicketScenario) {
  externalLaunchUrl.value = ''
  externalResult.value = null
  if (scenario === 'normal') {
    refreshSigningParameters()
    externalForm.manualSignature = false
    scenarioNote.value = '正常请求：使用当前时间、新 nonce，并在发送前自动重新签名。'
    ElMessage.success('已准备正常票据请求')
    return
  }

  if (scenario === 'expired') {
    const tolerance = integrationStatus.value?.timestampToleranceSeconds ?? 300
    externalForm.timestamp = String(Math.floor(Date.now() / 1000) - tolerance - 60)
    externalForm.nonce = createRequestNonce()
    externalForm.manualSignature = false
    resetSignatureResult()
    scenarioNote.value = `过期时间戳：当前时间向前偏移 ${tolerance + 60} 秒，预期返回 401。`
    ElMessage.warning('已准备过期时间戳场景')
    return
  }

  refreshSigningParameters()
  externalForm.manualSignature = true
  try {
    await generateExternalSignature()
    externalSignature.value = '0'.repeat(64)
    scenarioNote.value = '错误签名：请求体和时间戳有效，但签名被替换为 64 个 0，预期返回 401。'
    ElMessage.warning('已准备错误签名场景')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '准备错误签名场景失败')
  }
}

async function sendExternalTicket(options: { skipReadinessCheck?: boolean } = {}) {
  externalLoading.value = true
  externalLaunchUrl.value = ''
  externalFailureAdvice.value = ''
  try {
    if (!integrationStatus.value) {
      await checkIntegrationStatus(false)
    }
    if (!options.skipReadinessCheck && !integrationReadiness.value.ready) {
      throw new Error(`${integrationReadiness.value.title}：${integrationReadiness.value.description}`)
    }
    if (!externalRawBody.value.trim() && !syncExternalBody(false)) {
      throw new Error('请先填写有效的病案定位条件')
    }

    const signature = externalForm.manualSignature
      ? externalSignature.value.trim()
      : await generateExternalSignature()
    if (!signature) {
      throw new Error('手工签名模式下 X-MRR-Signature 不能为空')
    }

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
      lastSuccessfulTicketRequest.value = {
        timestamp: externalForm.timestamp,
        nonce: externalForm.nonce,
        rawBody: externalRawBody.value,
        signature,
      }
      scenarioNote.value = '票据创建成功。本次 nonce 和签名已保存，可点击“重放上次成功请求”验证 409 防重放。'
      ElMessage.success('一次性外部访问票据创建成功')
    }
    else {
      externalFailureAdvice.value = explainExternalArchiveTicketFailure(result.status, result.data)
      ElMessage.warning(externalFailureAdvice.value)
      if (result.status === 503) {
        await checkIntegrationStatus(false)
      }
    }
  }
  catch (error: unknown) {
    externalFailureAdvice.value = (error as Error).message || '外部票据请求失败'
    ElMessage.error(externalFailureAdvice.value)
  }
  finally {
    externalLoading.value = false
  }
}

async function replayLastTicketRequest() {
  const snapshot = lastSuccessfulTicketRequest.value
  if (!snapshot) {
    ElMessage.warning('先成功创建一次票据，才能重放相同 nonce 和签名')
    return
  }
  externalForm.timestamp = snapshot.timestamp
  externalForm.nonce = snapshot.nonce
  externalRawBody.value = snapshot.rawBody
  externalSignature.value = snapshot.signature
  externalForm.manualSignature = true
  scenarioNote.value = '重放请求：复用上次成功请求的时间戳、nonce、原始 JSON 和签名，预期返回 409。'
  await sendExternalTicket({ skipReadinessCheck: true })
}

function openExternalLaunchUrl() {
  if (!externalLaunchUrl.value) {
    ElMessage.warning('响应中没有可打开的 launchUrl')
    return
  }
  window.open(externalLaunchUrl.value, '_blank', 'noopener,noreferrer')
}

function useCurrentToken() {
  testedToken.value = userStore.token || ''
  customRequest.token = userStore.token || ''
}

async function copyText(value: string, label: string) {
  if (!value) {
    ElMessage.warning(`${label}为空`)
    return
  }
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
    }
    else {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      textarea.remove()
    }
    ElMessage.success(`${label}已复制`)
  }
  catch {
    ElMessage.error('浏览器未允许写入剪贴板，请手工复制')
  }
}

onMounted(() => {
  void checkIntegrationStatus(false)
})
</script>

<template>
  <div class="auth-test-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Authentication Playground</p>
        <h2>认证与外部访问测试台</h2>
        <p class="subtitle">
          可视化测试内部 JWT、受保护接口和外部影像票据。外部票据页面会先检查后端配置，避免把“页面随机 Secret”误认为“后端已启用”。
        </p>
      </div>
      <el-tag type="warning" effect="plain">仅 user:manage 权限可见</el-tag>
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
        <small>每次请求按数据库当前权限校验</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>外部集成</span>
        <strong>{{ integrationStatus?.enabled ? '已启用' : '未启用' }}</strong>
        <small>{{ integrationStatus ? `请求 IP：${integrationStatus.requestIp || '未知'}` : '等待配置检查' }}</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>API Base URL</span>
        <strong class="base-url">{{ apiBaseUrl }}</strong>
        <small>禁止向外部域名发送测试请求</small>
      </el-card>
    </section>

    <el-tabs v-model="activeTab" class="test-tabs">
      <el-tab-pane name="external" label="外部影像票据">
        <div class="external-stack">
          <el-alert
            type="info"
            show-icon
            :closable="false"
            title="HMAC Secret 的正确用法"
          >
            <template #default>
              页面生成的 Secret 只用于帮你创建密钥，不会自动修改后端。必须把同一个 Secret 写入
              <code>application-secrets.properties</code>，将 <code>mrr.integration.enabled=true</code>，重启后端，然后点击“检查后端配置”。
            </template>
          </el-alert>

          <el-card shadow="never" class="test-card setup-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>第一步：检查并启用后端集成</strong>
                  <span>状态接口只返回是否配置，不返回 Secret 明文</span>
                </div>
                <el-button :icon="Refresh" :loading="integrationStatusLoading" @click="checkIntegrationStatus()">
                  检查后端配置
                </el-button>
              </div>
            </template>

            <el-alert
              :type="integrationAlertType"
              :closable="false"
              show-icon
              :title="integrationReadiness.title"
              :description="integrationReadiness.description"
            />

            <div v-if="integrationStatusError" class="inline-error">
              {{ integrationStatusError }}
            </div>

            <div class="check-grid">
              <div v-for="item in externalConfigChecks" :key="item.label" class="check-item">
                <div>
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.detail }}</span>
                </div>
                <el-tag :type="item.ok ? 'success' : 'danger'" effect="plain">
                  {{ item.ok ? '通过' : '待处理' }}
                </el-tag>
              </div>
            </div>

            <div v-if="integrationStatus" class="status-meta">
              <span>Ticket：{{ integrationStatus.ticketTtlSeconds }} 秒</span>
              <span>Session：{{ integrationStatus.sessionTtlSeconds }} 秒</span>
              <span>时间容差：±{{ integrationStatus.timestampToleranceSeconds }} 秒</span>
              <span>单次最多：{{ integrationStatus.maxArchivesPerTicket }} 份病案</span>
            </div>

            <el-table
              v-if="integrationStatus?.clients?.length"
              :data="integrationStatus.clients"
              size="small"
              class="client-table"
            >
              <el-table-column prop="clientId" label="已配置 Client ID" min-width="180" />
              <el-table-column label="启用" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'danger'" effect="plain">
                    {{ row.enabled ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Secret" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.secretConfigured ? 'success' : 'danger'" effect="plain">
                    {{ row.secretConfigured ? '已配置' : '未配置' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="当前 IP" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.requestIpAllowed ? 'success' : 'danger'" effect="plain">
                    {{ row.requestIpAllowed ? '允许' : '拒绝' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="白名单" min-width="220">
                <template #default="{ row }">
                  {{ row.allowedIps?.length ? row.allowedIps.join(', ') : '未限制' }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="test-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>第二步：生成或填写同一个 HMAC Secret</strong>
                  <span>随机生成后还必须写入后端配置并重启</span>
                </div>
                <el-button :icon="MagicStick" @click="generateHmacSecret">
                  生成 256 位 Secret
                </el-button>
              </div>
            </template>

            <div class="two-column-grid setup-grid">
              <el-form label-position="top">
                <el-form-item label="Client ID">
                  <el-select
                    v-model="externalForm.clientId"
                    filterable
                    allow-create
                    default-first-option
                    placeholder="例如 his-system"
                    @change="resetSignatureResult"
                  >
                    <el-option
                      v-for="client in integrationStatus?.clients || []"
                      :key="client.clientId"
                      :label="client.clientId"
                      :value="client.clientId"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="HMAC Secret">
                  <el-input
                    v-model="externalForm.secret"
                    type="password"
                    show-password
                    autocomplete="off"
                    placeholder="填写与后端对应客户端完全相同的 Secret"
                    @input="markSecretAsManual"
                  />
                </el-form-item>
                <el-alert
                  :type="generatedSecretLocally ? 'warning' : 'info'"
                  :closable="false"
                  :title="generatedSecretLocally ? '这个 Secret 目前只存在于页面中' : '页面无法读取后端已经配置的 Secret'"
                  :description="generatedSecretLocally
                    ? '复制右侧配置片段到后端 secrets 文件，重启后端后再检查状态。'
                    : '如果后端已经配置，请从安全配置中取出同一个值粘贴到这里。'"
                />
              </el-form>

              <div class="config-panel">
                <div class="config-panel-header">
                  <div>
                    <strong>application-secrets.properties 配置片段</strong>
                    <span>已有客户端时请确认 clients[n] 索引没有覆盖其他系统</span>
                  </div>
                  <el-button text :icon="CopyDocument" @click="copyText(integrationConfigSnippet, '后端配置片段')">
                    复制
                  </el-button>
                </div>
                <pre class="code-panel config-code">{{ integrationConfigSnippet }}</pre>
                <ol class="setup-steps">
                  <li>复制上面的配置到实际部署使用的 <code>application-secrets.properties</code>。</li>
                  <li>确认 <code>mrr.integration.enabled=true</code>。</li>
                  <li>重启 MRR 后端，配置不会在运行中自动刷新。</li>
                  <li>回到本页点击“检查后端配置”，五项全部通过后再发送正常票据。</li>
                </ol>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="test-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>第三步：填写票据授权范围</strong>
                  <span>所有条件按并集解析，精确配对使用 archives</span>
                </div>
                <el-switch v-model="externalForm.allowDownload" active-text="允许下载" inactive-text="只读" />
              </div>
            </template>

            <el-form label-position="top">
              <div class="form-grid three">
                <el-form-item label="外部用户 ID">
                  <el-input v-model="externalForm.externalUserId" placeholder="例如 HIS-U10086" />
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
              <el-button @click="syncExternalBody()">从表单生成实际 JSON</el-button>
            </el-form>
          </el-card>

          <div class="two-column-grid">
            <el-card shadow="never" class="test-card">
              <template #header>
                <div class="card-header">
                  <div>
                    <strong>第四步：签名与模拟场景</strong>
                    <span>正常流程与异常安全校验分开准备</span>
                  </div>
                  <el-button text :icon="Refresh" @click="refreshSigningParameters">
                    更新时间与 nonce
                  </el-button>
                </div>
              </template>

              <div class="scenario-buttons">
                <el-button @click="prepareTicketScenario('normal')">正常请求</el-button>
                <el-button type="warning" plain @click="prepareTicketScenario('expired')">过期时间戳</el-button>
                <el-button type="danger" plain @click="prepareTicketScenario('bad-signature')">错误签名</el-button>
                <el-button
                  type="warning"
                  plain
                  :disabled="!lastSuccessfulTicketRequest"
                  @click="replayLastTicketRequest"
                >
                  重放上次成功请求
                </el-button>
              </div>
              <el-alert type="info" :closable="false" :title="scenarioNote" />

              <el-form label-position="top" class="request-form">
                <el-form-item label="接口路径">
                  <el-input v-model="externalForm.path" />
                </el-form-item>
                <div class="form-grid two">
                  <el-form-item label="Unix 时间戳（秒）">
                    <el-input v-model="externalForm.timestamp" />
                  </el-form-item>
                  <el-form-item label="Nonce">
                    <el-input v-model="externalForm.nonce" />
                  </el-form-item>
                </div>
                <el-form-item label="实际发送的原始 JSON">
                  <el-input
                    v-model="externalRawBody"
                    type="textarea"
                    :rows="9"
                    placeholder="先填写业务参数并点击“从表单生成实际 JSON”"
                  />
                </el-form-item>
                <el-form-item>
                  <el-switch
                    v-model="externalForm.manualSignature"
                    active-text="使用手工签名"
                    inactive-text="发送前自动重新签名"
                  />
                </el-form-item>
                <div class="button-row">
                  <el-button :loading="externalSigning" @click="signOnly">仅生成签名</el-button>
                  <el-button
                    type="primary"
                    :icon="Promotion"
                    :loading="externalLoading"
                    @click="sendExternalTicket()"
                  >
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
                    <span>签名原文与实际请求体必须逐字节一致</span>
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
              <el-input v-model="externalSignature" type="textarea" :rows="4" />
            </el-card>
          </div>

          <el-card shadow="never" class="test-card">
            <template #header>
              <div class="card-header">
                <div>
                  <strong>票据响应与处理建议</strong>
                  <span>成功后可打开一次性 launchUrl，失败时给出配置级解释</span>
                </div>
                <el-tag :type="responseType(externalResult)" effect="plain">
                  {{ externalResult?.status ?? '未发送' }}
                </el-tag>
              </div>
            </template>

            <el-alert
              v-if="externalFailureAdvice"
              type="warning"
              show-icon
              :closable="false"
              title="当前失败原因与下一步"
              :description="externalFailureAdvice"
            />

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
                  <span>本地解析不代表后端签名验证通过</span>
                </div>
                <el-button text :icon="Refresh" @click="useCurrentToken">使用当前 Token</el-button>
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
                <el-button :icon="View" :loading="customLoading" @click="testCurrentUser">调用 /auth/me</el-button>
                <el-button type="danger" plain :icon="SwitchButton" :loading="customLoading" @click="testLogout">
                  测试注销
                </el-button>
                <el-button :icon="CopyDocument" @click="copyText(testedToken, 'JWT Token')">复制</el-button>
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
        <div class="two-column-grid">
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
                <el-checkbox v-model="customRequest.useToken">添加 Authorization: Bearer Token</el-checkbox>
              </el-form-item>
              <el-form-item v-if="customRequest.useToken" label="Bearer Token">
                <el-input v-model="customRequest.token" type="textarea" :rows="3" />
              </el-form-item>
              <el-form-item label="附加请求头 JSON">
                <el-input v-model="customRequest.headersText" type="textarea" :rows="4" />
              </el-form-item>
              <el-form-item label="请求体 JSON">
                <el-input v-model="customRequest.bodyText" type="textarea" :rows="7" placeholder="GET 请求可以留空" />
              </el-form-item>
              <el-button type="primary" :icon="Connection" :loading="customLoading" @click="sendCustomRequest">
                发送请求
              </el-button>
            </el-form>
          </el-card>

          <el-card shadow="never" class="test-card">
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

      <el-tab-pane name="history" label="请求记录">
        <el-card shadow="never" class="test-card">
          <template #header>
            <div class="card-header">
              <div>
                <strong>本页请求记录</strong>
                <span>仅保存在页面内存，刷新后清空</span>
              </div>
              <el-button text @click="requestHistory = []">清空</el-button>
            </div>
          </template>
          <el-table :data="requestHistory" empty-text="尚未发送测试请求">
            <el-table-column prop="requestedAt" label="时间" width="100" />
            <el-table-column prop="name" label="场景" width="150" />
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
              <template #default="{ row }">{{ row.durationMs }} ms</template>
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
.copy-line,
.config-panel-header,
.scenario-buttons,
.status-meta {
  display: flex;
  align-items: center;
}

.page-header,
.card-header,
.config-panel-header {
  justify-content: space-between;
  gap: 16px;
}

.page-header > div:first-child,
.card-header > div:first-child,
.config-panel-header > div:first-child {
  min-width: 0;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h2 {
  margin: 0;
  font-size: 24px;
}

.subtitle {
  max-width: 820px;
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card :deep(.el-card__body) {
  display: grid;
  gap: 5px;
}

.metric-card span,
.metric-card small,
.card-header span,
.config-panel-header span,
.check-item span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-card strong {
  overflow: hidden;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-url {
  font-size: 14px !important;
}

.external-stack,
.request-form,
.config-panel {
  display: grid;
  gap: 16px;
}

.two-column-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.setup-grid {
  align-items: start;
}

.form-grid {
  display: grid;
  gap: 14px;
}

.form-grid.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.method-path-grid {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 14px;
}

.card-header strong,
.config-panel-header strong {
  display: block;
}

.card-header span,
.config-panel-header span {
  display: block;
  margin-top: 3px;
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.check-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
}

.check-item strong,
.check-item span {
  display: block;
}

.status-meta {
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 14px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.client-table {
  margin-top: 14px;
}

.inline-error {
  margin-top: 12px;
  color: var(--el-color-danger);
}

.config-panel {
  min-width: 0;
}

.setup-steps {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 22px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.scenario-buttons,
.button-row,
.response-summary,
.copy-line {
  flex-wrap: wrap;
  gap: 10px;
}

.scenario-buttons {
  margin-bottom: 12px;
}

.response-summary {
  margin: 14px 0;
}

.response-summary span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.launch-url {
  min-width: 0;
  max-width: 48%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.jwt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.code-label {
  display: block;
  margin: 14px 0 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
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
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-all;
}

.code-panel.compact {
  min-height: 88px;
}

.code-panel.response-body {
  min-height: 180px;
}

.config-code {
  min-height: 220px;
}

.copy-line code {
  min-width: 0;
  overflow-wrap: anywhere;
}

.test-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

@media (max-width: 1180px) {
  .metric-grid,
  .check-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-grid.three {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 780px) {
  .page-header,
  .card-header,
  .config-panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .two-column-grid,
  .form-grid.two,
  .form-grid.three,
  .check-grid,
  .jwt-grid,
  .method-path-grid {
    grid-template-columns: 1fr;
  }

  .launch-url {
    max-width: 100%;
  }
}
</style>

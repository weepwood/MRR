<script setup lang="ts">
import type { AuthTestHistoryEvent } from '../types'
import type { AuthTestResult } from '@/api/modules/auth-test'
import type { ExternalArchiveIntegrationStatus } from '@/utils/external-archive-test-guide'
import { CopyDocument, MagicStick, Promotion, Refresh, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
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

const emit = defineEmits<{
  record: [event: AuthTestHistoryEvent]
}>()

type JsonRecord = Record<string, any>
type TicketScenario = 'normal' | 'expired' | 'bad-signature'

interface TicketRequestSnapshot {
  timestamp: string
  nonce: string
  rawBody: string
  signature: string
}

const userStore = useUserStore()
const statusLoading = ref(false)
const integrationStatus = ref<ExternalArchiveIntegrationStatus | null>(null)
const integrationStatusError = ref('')
const statusHttpCode = ref<number | null>(null)

const loading = ref(false)
const signing = ref(false)
const result = ref<AuthTestResult | null>(null)
const rawBody = ref('')
const bodyHash = ref('')
const canonicalText = ref('')
const signature = ref('')
const launchUrl = ref('')
const failureAdvice = ref('')
const generatedSecretLocally = ref(false)
const scenarioNote = ref('正常请求会使用当前时间、新 nonce，并在发送前自动重新签名。')
const lastSuccessfulRequest = ref<TicketRequestSnapshot | null>(null)

const form = reactive({
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

const selectedClient = computed(() => findExternalArchiveClient(
  integrationStatus.value,
  form.clientId,
))
const readiness = computed(() => getExternalArchiveReadiness(
  integrationStatus.value,
  form.clientId,
  form.secret,
))
const alertType = computed(() => readiness.value.level === 'danger' ? 'error' : readiness.value.level)
const configClientIndex = computed(() => {
  const clients = integrationStatus.value?.clients ?? []
  const index = clients.findIndex(client => client.clientId === form.clientId.trim())
  return index >= 0 ? index : clients.length
})
const configSnippet = computed(() => buildExternalArchiveIntegrationConfig({
  clientId: form.clientId,
  secret: form.secret,
  allowedIp: integrationStatus.value?.requestIp || '127.0.0.1',
  clientIndex: configClientIndex.value,
  ticketTtlSeconds: integrationStatus.value?.ticketTtlSeconds,
  sessionTtlSeconds: integrationStatus.value?.sessionTtlSeconds,
  timestampToleranceSeconds: integrationStatus.value?.timestampToleranceSeconds,
  maxArchivesPerTicket: integrationStatus.value?.maxArchivesPerTicket,
}))
const configChecks = computed(() => [
  {
    label: '集成功能',
    ok: integrationStatus.value?.enabled === true,
    detail: integrationStatus.value?.enabled ? '已启用' : '需要 enabled=true',
  },
  {
    label: 'Client ID',
    ok: Boolean(selectedClient.value),
    detail: selectedClient.value ? '后端已配置' : '后端未找到',
  },
  {
    label: '服务端 Secret',
    ok: selectedClient.value?.secretConfigured === true,
    detail: selectedClient.value?.secretConfigured ? '已配置（不返回明文）' : '未配置',
  },
  {
    label: '来源 IP',
    ok: selectedClient.value?.requestIpAllowed === true,
    detail: integrationStatus.value?.requestIp || '尚未识别',
  },
  {
    label: '页面 Secret',
    ok: Boolean(form.secret.trim()),
    detail: form.secret.trim() ? '已填写' : '尚未填写',
  },
])

function record(name: string, method: string, path: string, response: AuthTestResult) {
  emit('record', {
    name,
    method,
    path,
    status: response.status,
    durationMs: response.durationMs,
  })
}

function unwrapResultData(value: unknown): JsonRecord {
  if (!value || typeof value !== 'object') {
    return {}
  }
  const root = value as JsonRecord
  return root.data && typeof root.data === 'object' ? root.data as JsonRecord : root
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
  if ([401, 403, 409].includes(value.status)) {
    return 'warning'
  }
  return 'danger'
}

async function checkStatus(showMessage = true) {
  statusLoading.value = true
  integrationStatusError.value = ''
  try {
    const response = await executeAuthTestRequest({
      method: 'GET',
      path: '/api/v1/integration/archive/status',
      token: userStore.token,
    })
    statusHttpCode.value = response.status
    record('集成配置检查', 'GET', '/api/v1/integration/archive/status', response)

    if (response.status < 200 || response.status >= 300) {
      integrationStatus.value = null
      const payload = response.data as JsonRecord
      throw new Error(String(payload?.message || `配置检查失败：HTTP ${response.status}`))
    }

    integrationStatus.value = unwrapResultData(response.data) as unknown as ExternalArchiveIntegrationStatus
    const clients = integrationStatus.value.clients ?? []
    if (clients.length && !clients.some(client => client.clientId === form.clientId)) {
      form.clientId = clients[0].clientId
    }
    if (showMessage) {
      ElMessage.success('已读取后端集成状态；Secret 明文不会返回')
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
    statusLoading.value = false
  }
}

function generateSecret() {
  try {
    form.secret = createRandomHmacSecret()
    generatedSecretLocally.value = true
    resetSignature()
    ElMessage.success('已生成 256 位随机 Secret；还需要复制配置、重启后端')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '生成 HMAC Secret 失败')
  }
}

function markSecretAsManual() {
  generatedSecretLocally.value = false
  resetSignature()
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

function buildPayload(): JsonRecord {
  const payload: JsonRecord = {
    externalUserId: form.externalUserId.trim(),
    allowDownload: form.allowDownload,
  }
  if (!payload.externalUserId) {
    throw new Error('externalUserId 不能为空')
  }

  const optionalFields: Array<[string, string]> = [
    ['idCard', form.idCard],
    ['bah', form.bah],
    ['sjh', form.sjh],
  ]
  optionalFields.forEach(([key, value]) => {
    if (value.trim()) {
      payload[key] = value.trim()
    }
  })

  const bahs = splitValues(form.bahsText)
  const sjhs = splitValues(form.sjhsText)
  const archives = parseArchivePairs(form.archivesText)
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

function resetSignature() {
  bodyHash.value = ''
  canonicalText.value = ''
  signature.value = ''
  failureAdvice.value = ''
}

function syncRawBody(showMessage = true) {
  try {
    rawBody.value = JSON.stringify(buildPayload())
    resetSignature()
    if (showMessage) {
      ElMessage.success('已生成实际发送的紧凑 JSON')
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

function refreshTimestampAndNonce() {
  form.timestamp = String(Math.floor(Date.now() / 1000))
  form.nonce = createRequestNonce()
  resetSignature()
}

async function createSignature() {
  if (!form.clientId.trim()) {
    throw new Error('Client ID 不能为空')
  }
  if (!form.secret.trim()) {
    throw new Error('HMAC Secret 不能为空，且必须与后端对应客户端配置完全相同')
  }
  if (!form.timestamp.trim() || !form.nonce.trim()) {
    throw new Error('时间戳和 nonce 不能为空')
  }
  if (!rawBody.value.trim() && !syncRawBody(false)) {
    throw new Error('请先填写有效的病案定位条件')
  }

  signing.value = true
  try {
    const signed = await createExternalArchiveSignature({
      method: 'POST',
      path: form.path,
      timestamp: form.timestamp,
      nonce: form.nonce,
      rawBody: rawBody.value,
      secret: form.secret,
    })
    bodyHash.value = signed.bodyHash
    canonicalText.value = signed.canonicalText
    signature.value = signed.signature
    return signed.signature
  }
  finally {
    signing.value = false
  }
}

async function signOnly() {
  try {
    await createSignature()
    ElMessage.success('签名已生成')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '签名生成失败')
  }
}

async function prepareScenario(scenario: TicketScenario) {
  launchUrl.value = ''
  result.value = null
  if (scenario === 'normal') {
    refreshTimestampAndNonce()
    form.manualSignature = false
    scenarioNote.value = '正常请求：当前时间、新 nonce，发送前自动重新签名。'
    ElMessage.success('已准备正常请求')
    return
  }

  if (scenario === 'expired') {
    const tolerance = integrationStatus.value?.timestampToleranceSeconds ?? 300
    form.timestamp = String(Math.floor(Date.now() / 1000) - tolerance - 60)
    form.nonce = createRequestNonce()
    form.manualSignature = false
    resetSignature()
    scenarioNote.value = `过期时间戳：向前偏移 ${tolerance + 60} 秒，预期返回 401。`
    ElMessage.warning('已准备过期时间戳场景')
    return
  }

  refreshTimestampAndNonce()
  form.manualSignature = true
  try {
    await createSignature()
    signature.value = '0'.repeat(64)
    scenarioNote.value = '错误签名：签名替换为 64 个 0，预期返回 401。'
    ElMessage.warning('已准备错误签名场景')
  }
  catch (error: unknown) {
    ElMessage.error((error as Error).message || '准备错误签名场景失败')
  }
}

async function sendTicket(options: { skipReadinessCheck?: boolean } = {}) {
  loading.value = true
  launchUrl.value = ''
  failureAdvice.value = ''
  try {
    if (!integrationStatus.value) {
      await checkStatus(false)
    }
    if (!options.skipReadinessCheck && !readiness.value.ready) {
      throw new Error(`${readiness.value.title}：${readiness.value.description}`)
    }
    if (!rawBody.value.trim() && !syncRawBody(false)) {
      throw new Error('请先填写有效的病案定位条件')
    }

    const requestSignature = form.manualSignature
      ? signature.value.trim()
      : await createSignature()
    if (!requestSignature) {
      throw new Error('手工签名模式下 X-MRR-Signature 不能为空')
    }

    const response = await executeAuthTestRequest({
      method: 'POST',
      path: form.path,
      headers: {
        'Content-Type': 'application/json',
        'X-MRR-Client-Id': form.clientId.trim(),
        'X-MRR-Timestamp': form.timestamp.trim(),
        'X-MRR-Nonce': form.nonce.trim(),
        'X-MRR-Signature': requestSignature,
      },
      rawBody: rawBody.value,
    })
    result.value = response
    record('外部访问票据', 'POST', form.path, response)
    const payload = unwrapResultData(response.data)
    launchUrl.value = String(payload.launchUrl || payload.data?.launchUrl || '')

    if (response.status >= 200 && response.status < 300) {
      lastSuccessfulRequest.value = {
        timestamp: form.timestamp,
        nonce: form.nonce,
        rawBody: rawBody.value,
        signature: requestSignature,
      }
      scenarioNote.value = '票据创建成功；现在可以重放相同 nonce，验证 409 防重放。'
      ElMessage.success('一次性外部访问票据创建成功')
    }
    else {
      failureAdvice.value = explainExternalArchiveTicketFailure(response.status, response.data)
      ElMessage.warning(failureAdvice.value)
      if (response.status === 503) {
        await checkStatus(false)
      }
    }
  }
  catch (error: unknown) {
    failureAdvice.value = (error as Error).message || '外部票据请求失败'
    ElMessage.error(failureAdvice.value)
  }
  finally {
    loading.value = false
  }
}

async function replayLastRequest() {
  const snapshot = lastSuccessfulRequest.value
  if (!snapshot) {
    ElMessage.warning('先成功创建一次票据，才能重放相同 nonce')
    return
  }
  form.timestamp = snapshot.timestamp
  form.nonce = snapshot.nonce
  rawBody.value = snapshot.rawBody
  signature.value = snapshot.signature
  form.manualSignature = true
  scenarioNote.value = '重放请求：复用上次成功请求的时间戳、nonce、JSON 和签名，预期返回 409。'
  await sendTicket({ skipReadinessCheck: true })
}

function openLaunchUrl() {
  if (!launchUrl.value) {
    ElMessage.warning('响应中没有 launchUrl')
    return
  }
  window.open(launchUrl.value, '_blank', 'noopener,noreferrer')
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
  void checkStatus(false)
})
</script>

<template>
  <div class="external-stack">
    <el-alert type="info" show-icon :closable="false" title="为什么随机 Secret 仍然会返回 503？">
      <template #default>
        页面随机生成 Secret 只会填写浏览器表单，不会修改 MRR 后端。必须把同一个 Secret 写入
        <code>application-secrets.properties</code>，设置 <code>mrr.integration.enabled=true</code>，重启后端后再检查状态。
      </template>
    </el-alert>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>第一步：检查后端集成状态</strong>
            <span>诊断接口受 JWT 与 user:manage 权限保护，不返回 Secret 明文</span>
          </div>
          <el-button :icon="Setting" :loading="statusLoading" @click="checkStatus()">
            检查后端配置
          </el-button>
        </div>
      </template>

      <el-alert
        :type="alertType"
        :closable="false"
        show-icon
        :title="readiness.title"
        :description="readiness.description"
      />

      <div v-if="integrationStatusError" class="inline-error">
        {{ integrationStatusError }}
      </div>

      <div class="check-grid">
        <div v-for="item in configChecks" :key="item.label" class="check-item">
          <div>
            <strong>{{ item.label }}</strong>
            <span>{{ item.detail }}</span>
          </div>
          <el-tag :type="item.ok ? 'success' : 'danger'" effect="plain">
            {{ item.ok ? '通过' : '待处理' }}
          </el-tag>
        </div>
      </div>

      <div class="status-meta">
        <span>状态接口 HTTP：{{ statusHttpCode ?? '—' }}</span>
        <span>Ticket：{{ integrationStatus?.ticketTtlSeconds ?? 90 }} 秒</span>
        <span>Session：{{ integrationStatus?.sessionTtlSeconds ?? 1800 }} 秒</span>
        <span>时间容差：±{{ integrationStatus?.timestampToleranceSeconds ?? 300 }} 秒</span>
        <span>单次最多：{{ integrationStatus?.maxArchivesPerTicket ?? 100 }} 份</span>
      </div>

      <el-table v-if="integrationStatus?.clients?.length" :data="integrationStatus.clients" size="small" class="client-table">
        <el-table-column prop="clientId" label="Client ID" min-width="180" />
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
        <el-table-column label="IP 白名单" min-width="220">
          <template #default="{ row }">
            {{ row.allowedIps?.length ? row.allowedIps.join(', ') : '未限制' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>第二步：生成或填写相同的 HMAC Secret</strong>
            <span>生成后必须复制到后端 secrets 文件并重启</span>
          </div>
          <el-button :icon="MagicStick" @click="generateSecret">
            生成 256 位 Secret
          </el-button>
        </div>
      </template>

      <div class="two-column-grid">
        <el-form label-position="top">
          <el-form-item label="Client ID">
            <el-select
              v-model="form.clientId"
              filterable
              allow-create
              default-first-option
              placeholder="例如 his-system"
              @change="resetSignature"
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
              v-model="form.secret"
              type="password"
              show-password
              autocomplete="off"
              placeholder="必须与后端对应客户端完全相同"
              @input="markSecretAsManual"
            />
          </el-form-item>
          <el-alert
            :type="generatedSecretLocally ? 'warning' : 'info'"
            :closable="false"
            :title="generatedSecretLocally ? 'Secret 目前只存在于页面内存' : '页面无法读取服务端 Secret 明文'"
            :description="generatedSecretLocally
              ? '复制右侧配置到后端，重启服务，再点击检查配置。'
              : '后端已有 Secret 时，请从安全配置中取出同一个值粘贴到这里。'"
          />
        </el-form>

        <div class="config-panel">
          <div class="config-header">
            <div>
              <strong>application-secrets.properties</strong>
              <span>已有客户端时请检查 clients[n] 索引</span>
            </div>
            <el-button text :icon="CopyDocument" @click="copyText(configSnippet, '后端配置片段')">
              复制
            </el-button>
          </div>
          <pre class="code-panel config-code">{{ configSnippet }}</pre>
          <ol class="setup-steps">
            <li>复制配置到实际部署使用的 secrets 文件。</li>
            <li>确认 <code>mrr.integration.enabled=true</code>。</li>
            <li>重启 MRR 后端，Spring 配置不会自动热刷新。</li>
            <li>回到本页检查配置，五项全部通过后发送正常票据。</li>
          </ol>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>第三步：填写票据授权范围</strong>
            <span>所有条件按并集解析；精确病案使用 archives</span>
          </div>
          <el-switch v-model="form.allowDownload" active-text="允许下载" inactive-text="只读" />
        </div>
      </template>

      <el-form label-position="top">
        <div class="form-grid three">
          <el-form-item label="外部用户 ID">
            <el-input v-model="form.externalUserId" placeholder="例如 HIS-U10086" />
          </el-form-item>
          <el-form-item label="身份证号">
            <el-input v-model="form.idCard" placeholder="可选" />
          </el-form-item>
          <el-form-item label="单个病案号">
            <el-input v-model="form.bah" placeholder="可选" />
          </el-form-item>
          <el-form-item label="单个上架号">
            <el-input v-model="form.sjh" placeholder="可选" />
          </el-form-item>
          <el-form-item label="多个病案号">
            <el-input v-model="form.bahsText" type="textarea" :rows="4" placeholder="每行一个，或逗号分隔" />
          </el-form-item>
          <el-form-item label="多个上架号">
            <el-input v-model="form.sjhsText" type="textarea" :rows="4" placeholder="每行一个，或逗号分隔" />
          </el-form-item>
        </div>
        <el-form-item label="精确病案号与上架号组合">
          <el-input v-model="form.archivesText" type="textarea" :rows="4" placeholder="每行一个，例如：10000001:20000001" />
        </el-form-item>
        <el-button @click="syncRawBody()">
          从表单生成实际 JSON
        </el-button>
      </el-form>
    </el-card>

    <div class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <strong>第四步：签名与模拟场景</strong>
              <span>正常流程和安全失败场景分开准备</span>
            </div>
            <el-button text :icon="Refresh" @click="refreshTimestampAndNonce">
              更新时间与 nonce
            </el-button>
          </div>
        </template>

        <div class="scenario-buttons">
          <el-button @click="prepareScenario('normal')">
            正常请求
          </el-button>
          <el-button type="warning" plain @click="prepareScenario('expired')">
            过期时间戳
          </el-button>
          <el-button type="danger" plain @click="prepareScenario('bad-signature')">
            错误签名
          </el-button>
          <el-button type="warning" plain :disabled="!lastSuccessfulRequest" @click="replayLastRequest">
            重放上次成功请求
          </el-button>
        </div>
        <el-alert type="info" :closable="false" :title="scenarioNote" />

        <el-form label-position="top" class="request-form">
          <el-form-item label="接口路径">
            <el-input v-model="form.path" />
          </el-form-item>
          <div class="form-grid two">
            <el-form-item label="Unix 时间戳（秒）">
              <el-input v-model="form.timestamp" />
            </el-form-item>
            <el-form-item label="Nonce">
              <el-input v-model="form.nonce" />
            </el-form-item>
          </div>
          <el-form-item label="实际发送的原始 JSON">
            <el-input v-model="rawBody" type="textarea" :rows="9" placeholder="先填写业务参数并生成 JSON" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="form.manualSignature" active-text="使用手工签名" inactive-text="发送前自动重新签名" />
          </el-form-item>
          <div class="button-row">
            <el-button :loading="signing" @click="signOnly">
              仅生成签名
            </el-button>
            <el-button type="primary" :icon="Promotion" :loading="loading" @click="sendTicket()">
              发送票据请求
            </el-button>
          </div>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <strong>签名计算结果</strong>
              <span>Canonical Text 与原始请求体必须逐字节一致</span>
            </div>
          </div>
        </template>
        <span class="code-label">SHA-256(rawBody)</span>
        <div class="copy-line">
          <code>{{ bodyHash || '尚未计算' }}</code>
          <el-button text :icon="CopyDocument" @click="copyText(bodyHash, 'Body Hash')" />
        </div>
        <span class="code-label">Canonical Text</span>
        <pre class="code-panel compact">{{ canonicalText || '尚未计算' }}</pre>
        <span class="code-label">X-MRR-Signature</span>
        <el-input v-model="signature" type="textarea" :rows="4" />
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>票据响应与处理建议</strong>
            <span>成功后打开 launchUrl；失败时显示可执行的下一步</span>
          </div>
          <el-tag :type="responseType(result)" effect="plain">
            {{ result?.status ?? '未发送' }}
          </el-tag>
        </div>
      </template>

      <el-alert
        v-if="failureAdvice"
        type="warning"
        show-icon
        :closable="false"
        title="当前失败原因与下一步"
        :description="failureAdvice"
      />

      <div class="response-summary">
        <span>耗时</span>
        <strong>{{ result ? `${result.durationMs} ms` : '—' }}</strong>
        <span>Launch URL</span>
        <strong class="launch-url">{{ launchUrl || '—' }}</strong>
        <el-button :disabled="!launchUrl" @click="openLaunchUrl">
          打开影像档案袋
        </el-button>
      </div>
      <pre class="code-panel response-body">{{ formatJson(result?.data) || '票据响应将在这里显示' }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.external-stack,
.config-panel,
.request-form {
  display: grid;
  gap: 16px;
}

.two-column-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
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

.card-header,
.config-header,
.button-row,
.scenario-buttons,
.response-summary,
.copy-line,
.status-meta {
  display: flex;
  gap: 10px;
  align-items: center;
}

.card-header,
.config-header {
  justify-content: space-between;
}

.card-header strong,
.card-header span,
.config-header strong,
.config-header span,
.check-item strong,
.check-item span {
  display: block;
}

.card-header span,
.config-header span,
.check-item span,
.response-summary span,
.status-meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.check-item {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.status-meta,
.button-row,
.scenario-buttons,
.response-summary {
  flex-wrap: wrap;
}

.status-meta {
  gap: 8px 18px;
  margin-top: 14px;
}

.client-table {
  margin-top: 14px;
}

.inline-error {
  margin-top: 12px;
  color: var(--el-color-danger);
}

.setup-steps {
  display: grid;
  gap: 6px;
  padding-left: 22px;
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.scenario-buttons {
  margin-bottom: 12px;
}

.response-summary {
  margin: 14px 0;
}

.launch-url {
  min-width: 0;
  max-width: 48%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  line-height: 1.65;
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
  min-height: 180px;
}

.config-code {
  min-height: 220px;
}

.copy-line code {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (width <= 1180px) {
  .check-grid,
  .form-grid.three {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 820px) {
  .two-column-grid,
  .form-grid.two,
  .form-grid.three,
  .check-grid {
    grid-template-columns: 1fr;
  }

  .card-header,
  .config-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .launch-url {
    max-width: 100%;
  }
}
</style>

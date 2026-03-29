<template>
  <div class="pmr-page admin-tool-view">
    <section class="pmr-page-header">
      <div>
        <p class="module-eyebrow">Backend Test Lab</p>
        <h2 class="pmr-page-title">后端测试</h2>
        <p class="pmr-page-subtitle">统一查看健康检查、Swagger、系统概览、冒烟测试和身份证加解密测试。</p>
      </div>
      <div class="pmr-toolbar-actions">
      </div>
    </section>
    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">身份证加解密测试</h3>
            <p class="pmr-panel-subtitle">将身份证加密、解密和端到端验证合并到测试中心中。</p>
          </div>
          <span class="pmr-badge">ID Card</span>
        </div>
      </template>

      <div class="idcard-grid">
        <el-card class="pmr-panel idcard-card" shadow="never">
          <template #header>加密测试</template>
          <el-form class="idcard-form" label-width="96px" @submit.prevent>
            <el-form-item label="身份证号">
              <el-input v-model="idCardToEncrypt" placeholder="请输入身份证号码" clearable />
            </el-form-item>

            <div class="pmr-actions-row">
              <el-button type="primary" :disabled="!idCardToEncrypt" @click="handleEncrypt">
                加密
              </el-button>
              <el-button :disabled="!encryptResult" @click="copyToClipboard(encryptResult?.ciphertext)">
                复制密文
              </el-button>
            </div>

            <div v-if="encryptResult" class="idcard-result">
              <div class="idcard-result-item">
                <span>密文</span>
                <code>{{ encryptResult.ciphertext }}</code>
              </div>
              <div class="idcard-result-item">
                <span>IV</span>
                <code>{{ encryptResult.iv }}</code>
              </div>
            </div>
          </el-form>
        </el-card>

        <el-card class="pmr-panel idcard-card" shadow="never">
          <template #header>解密测试</template>
          <el-form class="idcard-form" label-width="96px" @submit.prevent>
            <el-form-item label="密文">
              <el-input v-model="cipherToDecrypt" placeholder="请输入密文" clearable />
            </el-form-item>
            <el-form-item label="IV">
              <el-input v-model="ivToDecrypt" placeholder="请输入 IV" clearable />
            </el-form-item>

            <div class="pmr-actions-row">
              <el-button type="primary" :disabled="!cipherToDecrypt || !ivToDecrypt" @click="handleDecrypt">
                解密
              </el-button>
              <el-button :disabled="decryptResult === null" @click="copyToClipboard(decryptResult)">
                复制结果
              </el-button>
            </div>

            <div v-if="decryptResult !== null" class="idcard-result">
              <div class="idcard-result-item">
                <span>身份证号</span>
                <code>{{ decryptResult }}</code>
              </div>
            </div>

            <el-alert v-if="decryptError" :title="decryptError" type="error" show-icon :closable="false"
              class="idcard-alert" />
          </el-form>
        </el-card>

        <el-card class="pmr-panel idcard-card" shadow="never">
          <template #header>端到端测试</template>
          <el-form class="idcard-form" label-width="96px" @submit.prevent>
            <el-form-item label="身份证号">
              <el-input v-model="idCardToTest" placeholder="请输入身份证号码" clearable />
            </el-form-item>

            <div class="pmr-actions-row">
              <el-button type="primary" :disabled="!idCardToTest" @click="handleEndToEndTest">
                测试
              </el-button>
              <el-button :disabled="testResult === null" @click="copyToClipboard(testResult)">
                复制结果
              </el-button>
            </div>

            <div v-if="testResult !== null" class="idcard-result">
              <div class="idcard-result-item">
                <span>原始值</span>
                <code>{{ idCardToTest }}</code>
              </div>
              <div class="idcard-result-item">
                <span>解密值</span>
                <code :class="{ 'idcard-error-text': testResult !== idCardToTest }">{{ testResult }}</code>
              </div>
              <div class="idcard-status" :class="testResult === idCardToTest ? 'is-success' : 'is-error'">
                {{ testResult === idCardToTest ? '通过' : '失败' }}
              </div>
            </div>
          </el-form>
        </el-card>
      </div>
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <PasswordCipherView />
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <PressureTestView />
    </el-card>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <LogCleanupTestView />
    </el-card>

  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAdminSettings } from '@/shared/composables/useAdminSettings'
import { decryptIdCard, encryptIdCard } from '@/utils/decrypt'
import PasswordCipherView from './PasswordCipherView.vue'
import PressureTestView from './PressureTestView.vue'
import LogCleanupTestView from './LogCleanupTestView.vue'

const { resolvedSwaggerUrl } = useAdminSettings()

const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const runningSmokeSuite = ref(false)
const runningManualRequest = ref(false)
const smokeResults = ref([])
const selectedResult = ref(null)
const lastRunAt = ref('')
const activeTab = ref('manual')

const manualRequest = reactive({
  method: 'GET',
  path: '/system/health',
  headers: '',
  body: ''
})

const idCardToEncrypt = ref('')
const encryptResult = ref(null)
const cipherToDecrypt = ref('')
const ivToDecrypt = ref('')
const decryptResult = ref(null)
const decryptError = ref('')
const idCardToTest = ref('')
const testResult = ref(null)

const smokeSummary = computed(() => {
  if (!smokeResults.value.length) {
    return {
      status: '未运行',
      note: '点击“运行烟测”开始检查接口可用性。'
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
  const payload = contentType.includes('application/json') ? await response.json() : await response.text()
  const preview = typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)

  return {
    name,
    method,
    path,
    status: `${response.status} ${response.statusText}`.trim(),
    ok: response.ok,
    durationMs,
    summary:
      typeof payload === 'object' && payload !== null
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
    ElMessage.success('烟测完成')
  } catch (error) {
    ElMessage.error(error?.message || '烟测失败')
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

const handleEncrypt = () => {
  try {
    encryptResult.value = encryptIdCard(idCardToEncrypt.value)
  } catch (error) {
    ElMessage.error(error?.message || '加密失败')
  }
}

const handleDecrypt = () => {
  try {
    decryptResult.value = decryptIdCard(cipherToDecrypt.value, ivToDecrypt.value)
    decryptError.value = ''
  } catch (error) {
    decryptError.value = error?.message || '解密失败'
    decryptResult.value = null
  }
}

const handleEndToEndTest = () => {
  try {
    const { ciphertext, iv } = encryptIdCard(idCardToTest.value)
    testResult.value = decryptIdCard(ciphertext, iv)
  } catch (error) {
    testResult.value = null
    ElMessage.error(error?.message || '测试失败')
  }
}

const copyToClipboard = async (text) => {
  if (!text) {
    ElMessage.warning('没有可复制的内容')
    return
  }

  try {
    await navigator.clipboard.writeText(String(text))
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.error(error?.message || '复制失败')
  }
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

.pmr-section {
  margin-top: 20px;
}

.test-lab-layout {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 20px;
  align-items: start;
  margin-top: 20px;
}

.layout-main {
  min-width: 0;
}

.layout-side {
  min-width: 0;
}

/* Tab Styling Overrides */
.testing-tabs {
  padding: 0;
  overflow: hidden;
}

.testing-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 20px;
  background: var(--pmr-color-neutral-50);
  border-bottom: 1px solid var(--pmr-color-border-default);
}

.testing-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.testing-tabs :deep(.el-tabs__item) {
  height: 52px;
  font-weight: 600;
  color: var(--pmr-color-text-secondary);
}

.testing-tabs :deep(.el-tabs__item.is-active) {
  color: var(--pmr-color-action-primary);
}

.tab-content-wrapper {
  padding: 0;
}

.pane-inner-header,
.inspector-header {
  height: 84px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--pmr-color-border-low);
  background: white;
}

.pane-body {
  padding: 24px;
}

.sticky-info {
  position: sticky;
  top: 20px;
}

.manual-form {
  display: grid;
  gap: 4px;
}

.pmr-actions-row {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px dashed var(--pmr-color-border-default);
}

.response-shell {
  display: grid;
  gap: 16px;
}

.response-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 6px 0;
}

.meta-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}

.meta-item .label {
  font-weight: 700;
  color: var(--pmr-color-text-secondary);
  width: 42px;
  flex-shrink: 0;
}

.meta-item .value {
  color: var(--pmr-color-text-primary);
  font-family: var(--pmr-font-family-mono);
  word-break: break-all;
}

.response-body {
  margin: 0;
  padding: 20px;
  border-radius: var(--pmr-radius-xl);
  background: #0f172a;
  color: #e2e8f0;
  font-family: var(--pmr-font-family-mono);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: calc(100vh - 460px);
  min-height: 400px;
  overflow-y: auto;
}

.idcard-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.idcard-card {
  min-width: 0;
}

.idcard-form {
  display: grid;
  gap: 12px;
}

.idcard-result {
  display: grid;
  gap: 10px;
  margin-top: 4px;
}

.idcard-result-item {
  display: grid;
  gap: 6px;
}

.idcard-result-item span {
  font-size: 12px;
  font-weight: 700;
  color: var(--pmr-color-text-secondary);
}

.idcard-result-item code {
  padding: 10px 12px;
  border-radius: var(--pmr-radius-lg);
  background: #f8fafc;
  color: var(--pmr-color-text-primary);
  font-family: var(--pmr-font-family-mono);
  font-size: 12px;
  word-break: break-all;
}

.idcard-alert {
  margin-top: 4px;
}

.idcard-status {
  font-size: 13px;
  font-weight: 700;
}

.idcard-status.is-success {
  color: var(--pmr-color-success-500);
}

.idcard-status.is-error {
  color: var(--pmr-color-danger-500);
}

.idcard-error-text {
  color: var(--pmr-color-danger-500);
}

@media (max-width: 1400px) {
  .test-lab-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .idcard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
}
</style>

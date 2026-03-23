<template>
  <div class="testing-view">
    <el-tabs v-model="activeTestTab" type="card">
      <!-- API接口测试 -->
      <el-tab-pane label="接口测试" name="api">
        <div class="test-section">
          <h3>API接口测试</h3>
          <div class="form">
            <div class="form-row">
              <label>请求地址</label>
              <el-input v-model="requestUrl" placeholder="https://example.com/api" />
            </div>
            <div class="form-row">
              <label>请求方法</label>
              <el-select v-model="requestMethod" style="width: 120px;">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
              <el-button type="primary" @click="sendRequest" :loading="sending">
                {{ sending ? '发送中...' : '发送请求' }}
              </el-button>
            </div>
            <div class="form-row">
              <label>请求头 (JSON)</label>
              <el-input
                v-model="headersInput"
                type="textarea"
                :rows="4"
                placeholder='{"Content-Type":"application/json"}'
              />
            </div>
            <div class="form-row" v-if="requestMethod !== 'GET'">
              <label>请求体 (JSON)</label>
              <el-input
                v-model="bodyInput"
                type="textarea"
                :rows="6"
                placeholder='{"key":"value"}'
              />
            </div>
          </div>

          <div v-if="response" class="response-section">
            <h4>响应结果</h4>
            <div class="response-meta">
              <span>状态: <strong>{{ response.status }}</strong> {{ response.statusText }}</span>
              <span>耗时: <strong>{{ responseTimeMs }}ms</strong></span>
              <span>大小: <strong>{{ prettySize(responseSizeBytes) }}</strong></span>
            </div>
            <el-input
              :model-value="formattedResponse"
              type="textarea"
              :rows="10"
              readonly
              class="response-content"
            />
          </div>
          <div v-if="error" class="error-section">
            <el-alert :title="error" type="error" show-icon />
          </div>
        </div>
      </el-tab-pane>

      <!-- 身份证加解密测试 -->
      <el-tab-pane label="身份证加解密测试" name="idcard">
        <div class="test-section">
          <h3>身份证加解密测试</h3>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-card>
                <template #header>加密测试</template>
                <div class="form">
                  <div class="form-row">
                    <label>身份证号</label>
                    <el-input v-model="idCardToEncrypt" placeholder="请输入身份证号" />
                  </div>
                  <el-button @click="handleEncrypt" :disabled="!idCardToEncrypt" type="primary">
                    加密
                  </el-button>
                  <div v-if="encryptResult" class="result-section">
                    <h4>加密结果:</h4>
                    <div class="result-item">
                      <label>密文:</label>
                      <el-input :model-value="encryptResult.ciphertext" readonly />
                      <el-button @click="copyToClipboard(encryptResult.ciphertext)" size="small">复制</el-button>
                    </div>
                    <div class="result-item">
                      <label>IV向量:</label>
                      <el-input :model-value="encryptResult.iv" readonly />
                      <el-button @click="copyToClipboard(encryptResult.iv)" size="small">复制</el-button>
                    </div>
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card>
                <template #header>解密测试</template>
                <div class="form">
                  <div class="form-row">
                    <label>密文</label>
                    <el-input v-model="cipherToDecrypt" placeholder="请输入密文" />
                  </div>
                  <div class="form-row">
                    <label>IV向量</label>
                    <el-input v-model="ivToDecrypt" placeholder="请输入IV向量" />
                  </div>
                  <el-button @click="handleDecrypt" :disabled="!cipherToDecrypt || !ivToDecrypt" type="primary">
                    解密
                  </el-button>
                  <div v-if="decryptResult !== null" class="result-section">
                    <h4>解密结果:</h4>
                    <div class="result-item">
                      <label>身份证号:</label>
                      <el-input :model-value="decryptResult" readonly />
                      <el-button @click="copyToClipboard(decryptResult)" size="small">复制</el-button>
                    </div>
                  </div>
                  <div v-if="decryptError" class="error-section">
                    <el-alert :title="decryptError" type="error" show-icon />
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card>
                <template #header>端到端测试</template>
                <div class="form">
                  <div class="form-row">
                    <label>身份证号</label>
                    <el-input v-model="idCardToTest" placeholder="请输入身份证号" />
                  </div>
                  <el-button @click="handleEndToEndTest" :disabled="!idCardToTest" type="primary">
                    测试
                  </el-button>
                  <div v-if="testResult !== null" class="result-section">
                    <h4>测试结果:</h4>
                    <div class="result-item">
                      <label>原始:</label>
                      <el-input :model-value="idCardToTest" readonly />
                    </div>
                    <div class="result-item">
                      <label>解密:</label>
                      <el-input 
                        :model-value="testResult" 
                        readonly 
                        :class="{ 'error-input': testResult !== idCardToTest }"
                      />
                    </div>
                    <div class="result-item">
                      <label>结果:</label>
                      <el-tag :type="testResult === idCardToTest ? 'success' : 'danger'">
                        {{ testResult === idCardToTest ? '✅ 通过' : '❌ 失败' }}
                      </el-tag>
                    </div>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
          
          <!-- 用户密钥加密测试 -->
          <div style="margin-top: 30px;">
            <h3>用户密钥加密测试</h3>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-card>
                  <template #header>用户密钥加密</template>
                  <div class="form">
                    <div class="form-row">
                      <label>用户ID</label>
                      <el-input v-model="userIdForEncrypt" placeholder="请输入用户ID" />
                    </div>
                    <div class="form-row">
                      <label>身份证号</label>
                      <el-input v-model="idCardToEncryptWithUser" placeholder="请输入身份证号" />
                    </div>
                    <el-button @click="handleEncryptWithUser" :disabled="!idCardToEncryptWithUser || !userIdForEncrypt" type="primary">
                      用户密钥加密
                    </el-button>
                    <div v-if="encryptWithUserResult" class="result-section">
                      <h4>加密结果:</h4>
                      <div class="result-item">
                        <label>密文:</label>
                        <el-input :model-value="encryptWithUserResult.ciphertext" readonly />
                        <el-button @click="copyToClipboard(encryptWithUserResult.ciphertext)" size="small">复制</el-button>
                      </div>
                      <div class="result-item">
                        <label>IV向量:</label>
                        <el-input :model-value="encryptWithUserResult.iv" readonly />
                        <el-button @click="copyToClipboard(encryptWithUserResult.iv)" size="small">复制</el-button>
                      </div>
                      <div class="result-item">
                        <label>时间戳:</label>
                        <el-input :model-value="encryptWithUserResult.timestamp" readonly />
                        <el-button @click="copyToClipboard(encryptWithUserResult.timestamp)" size="small">复制</el-button>
                      </div>
                    </div>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card>
                  <template #header>用户密钥解密</template>
                  <div class="form">
                    <div class="form-row">
                      <label>用户ID</label>
                      <el-input v-model="userIdForDecrypt" placeholder="请输入用户ID" />
                    </div>
                    <div class="form-row">
                      <label>密文</label>
                      <el-input v-model="cipherToDecryptWithUser" placeholder="请输入密文" />
                    </div>
                    <div class="form-row">
                      <label>IV向量</label>
                      <el-input v-model="ivToDecryptWithUser" placeholder="请输入IV向量" />
                    </div>
                    <div class="form-row">
                      <label>时间戳</label>
                      <el-input v-model="timestampToDecryptWithUser" placeholder="请输入时间戳" />
                    </div>
                    <el-button @click="handleDecryptWithUser" :disabled="!cipherToDecryptWithUser || !ivToDecryptWithUser || !userIdForDecrypt || !timestampToDecryptWithUser" type="primary">
                      用户密钥解密
                    </el-button>
                    <div v-if="decryptWithUserResult !== null" class="result-section">
                      <h4>解密结果:</h4>
                      <div class="result-item">
                        <label>身份证号:</label>
                        <el-input :model-value="decryptWithUserResult" readonly />
                        <el-button @click="copyToClipboard(decryptWithUserResult)" size="small">复制</el-button>
                      </div>
                    </div>
                    <div v-if="decryptWithUserError" class="error-section">
                      <el-alert :title="decryptWithUserError" type="error" show-icon />
                    </div>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card>
                  <template #header>用户密钥端到端测试</template>
                  <div class="form">
                    <div class="form-row">
                      <label>用户ID</label>
                      <el-input v-model="userIdForTest" placeholder="请输入用户ID" />
                    </div>
                    <div class="form-row">
                      <label>身份证号</label>
                      <el-input v-model="idCardToTestWithUser" placeholder="请输入身份证号" />
                    </div>
                    <el-button @click="handleEndToEndTestWithUser" :disabled="!idCardToTestWithUser || !userIdForTest" type="primary">
                      用户密钥测试
                    </el-button>
                    <div v-if="testWithUserResult !== null" class="result-section">
                      <h4>测试结果:</h4>
                      <div class="result-item">
                        <label>原始:</label>
                        <el-input :model-value="idCardToTestWithUser" readonly />
                      </div>
                      <div class="result-item">
                        <label>解密:</label>
                        <el-input 
                          :model-value="testWithUserResult" 
                          readonly 
                          :class="{ 'error-input': testWithUserResult !== idCardToTestWithUser }"
                        />
                      </div>
                      <div class="result-item">
                        <label>结果:</label>
                        <el-tag :type="testWithUserResult === idCardToTestWithUser ? 'success' : 'danger'">
                          {{ testWithUserResult === idCardToTestWithUser ? '✅ 通过' : '❌ 失败' }}
                        </el-tag>
                      </div>
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-tab-pane>

      <!-- 功能测试 -->
      <el-tab-pane label="功能测试" name="features">
        <div class="test-section">
          <h3>功能测试</h3>
          <div class="feature-tests">
            <el-button @click="testMultiSelect" type="primary">测试多选功能</el-button>
            <el-button @click="testPrintPage" type="primary">测试打印页面</el-button>
            <el-button @click="testImageGallery" type="primary">测试图片画廊</el-button>
            <el-button @click="testLogin" type="primary">测试登录功能</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { encryptIdCard, decryptIdCard, encryptIdCardWithUserKey, decryptIdCardWithUserKey } from '../../utils/decrypt.js'

// API测试相关
const activeTestTab = ref('api')
const requestUrl = ref('')
const requestMethod = ref('GET')
const headersInput = ref('')
const bodyInput = ref('')
const sending = ref(false)
const error = ref('')
const response = ref(null)
const responseBody = ref(null)
const responseTimeMs = ref(0)
const responseSizeBytes = ref(0)

// 身份证加解密测试相关
const idCardToEncrypt = ref('')
const encryptResult = ref(null)
const cipherToDecrypt = ref('')
const ivToDecrypt = ref('')
const decryptResult = ref(null)
const decryptError = ref('')
const idCardToTest = ref('')
const testResult = ref(null)

// 用户密钥加密测试相关
const idCardToEncryptWithUser = ref('')
const userIdForEncrypt = ref('')
const encryptWithUserResult = ref(null)
const cipherToDecryptWithUser = ref('')
const ivToDecryptWithUser = ref('')
const timestampToDecryptWithUser = ref('')
const userIdForDecrypt = ref('')
const decryptWithUserResult = ref(null)
const decryptWithUserError = ref('')
const idCardToTestWithUser = ref('')
const userIdForTest = ref('')
const testWithUserResult = ref(null)

// 安全解析JSON
const safeParseJson = (text, fallback) => {
  if (!text) return fallback
  try { 
    return JSON.parse(text) 
  } catch { 
    return fallback 
  }
}

// 发送API请求
const sendRequest = async () => {
  error.value = ''
  response.value = null
  responseBody.value = null
  responseTimeMs.value = 0
  responseSizeBytes.value = 0
  
  if (!requestUrl.value) { 
    error.value = '请输入请求地址'
    return 
  }
  
  const headers = safeParseJson(headersInput.value, {})
  const bodyObj = safeParseJson(bodyInput.value, undefined)
  const init = { method: requestMethod.value, headers }
  
  if (requestMethod.value !== 'GET' && bodyObj !== undefined) {
    init.body = JSON.stringify(bodyObj)
    if (!init.headers) init.headers = {}
    if (!init.headers['Content-Type'] && !init.headers['content-type']) {
      init.headers['Content-Type'] = 'application/json'
    }
  }
  
  sending.value = true
  const start = performance.now()
  
  try {
    const res = await fetch(requestUrl.value, init)
    const end = performance.now()
    response.value = { 
      status: res.status, 
      statusText: res.statusText, 
      ok: res.ok, 
      headers: {} 
    }
    res.headers.forEach((v, k) => { 
      response.value.headers[k] = v 
    })
    responseTimeMs.value = Math.round(end - start)
    
    const ct = res.headers.get('content-type') || ''
    if (ct.includes('application/json')) {
      const json = await res.json()
      const jsonStr = JSON.stringify(json)
      responseSizeBytes.value = jsonStr ? new Blob([jsonStr]).size : 0
      responseBody.value = json
    } else {
      const text = await res.text()
      responseSizeBytes.value = text ? new Blob([text]).size : 0
      responseBody.value = text
    }
  } catch (e) {
    error.value = e?.message || '请求失败'
  } finally {
    sending.value = false
  }
}

// 格式化响应
const formattedResponse = computed(() => {
  if (responseBody.value == null) return ''
  try { 
    return typeof responseBody.value === 'string' 
      ? responseBody.value 
      : JSON.stringify(responseBody.value, null, 2) 
  } catch { 
    return String(responseBody.value) 
  }
})

// 格式化文件大小
const prettySize = (bytes) => {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B','KB','MB','GB']
  let i = 0
  let n = bytes
  while (n >= 1024 && i < units.length - 1) { 
    n /= 1024
    i++ 
  }
  return `${n.toFixed(2)} ${units[i]}`
}

// 身份证加密
const handleEncrypt = () => {
  try {
    encryptResult.value = encryptIdCard(idCardToEncrypt.value)
  } catch (error) {
    console.error('加密失败:', error)
  }
}

// 身份证解密
const handleDecrypt = () => {
  try {
    decryptResult.value = decryptIdCard(cipherToDecrypt.value, ivToDecrypt.value)
    decryptError.value = ''
  } catch (error) {
    decryptError.value = '解密失败: ' + error.message
    decryptResult.value = null
  }
}

// 端到端测试
const handleEndToEndTest = () => {
  try {
    const { ciphertext, iv } = encryptIdCard(idCardToTest.value)
    testResult.value = decryptIdCard(ciphertext, iv)
  } catch (error) {
    testResult.value = null
    console.error('端到端测试失败:', error)
  }
}

// 用户密钥加密
const handleEncryptWithUser = () => {
  try {
    encryptWithUserResult.value = encryptIdCardWithUserKey(idCardToEncryptWithUser.value, userIdForEncrypt.value)
  } catch (error) {
    console.error('用户密钥加密失败:', error)
  }
}

// 用户密钥解密
const handleDecryptWithUser = () => {
  try {
    decryptWithUserResult.value = decryptIdCardWithUserKey(cipherToDecryptWithUser.value, ivToDecryptWithUser.value, userIdForDecrypt.value, timestampToDecryptWithUser.value)
    decryptWithUserError.value = ''
  } catch (error) {
    decryptWithUserError.value = '用户密钥解密失败: ' + error.message
    decryptWithUserResult.value = null
  }
}

// 用户密钥端到端测试
const handleEndToEndTestWithUser = () => {
  try {
    const { ciphertext, iv, timestamp } = encryptIdCardWithUserKey(idCardToTestWithUser.value, userIdForTest.value)
    testWithUserResult.value = decryptIdCardWithUserKey(ciphertext, iv, userIdForTest.value, timestamp)
  } catch (error) {
    testWithUserResult.value = null
    console.error('用户密钥端到端测试失败:', error)
  }
}

// 复制到剪贴板
const copyToClipboard = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(err => {
    console.error('复制失败:', err)
    ElMessage.error('复制失败')
  })
}

// 功能测试
const testMultiSelect = () => {
  window.location.href = '/00788222'
}

const testPrintPage = () => {
  const testImages = [
    {
      id: 1,
      pages: 1,
      btype: 1,
      cx: 'https://via.placeholder.com/800x600/667eea/ffffff?text=Test+Image+1',
      blobUrl: 'https://via.placeholder.com/800x600/667eea/ffffff?text=Test+Image+1'
    },
    {
      id: 2,
      pages: 2,
      btype: 2,
      cx: 'https://via.placeholder.com/800x600/48bb78/ffffff?text=Test+Image+2',
      blobUrl: 'https://via.placeholder.com/800x600/48bb78/ffffff?text=Test+Image+2'
    }
  ]
  
  const testRecord = {
    bah: '00788222',
    name: '测试患者',
    department: '测试科室'
  }
  
  sessionStorage.setItem('selectedImagesForPrint', JSON.stringify(testImages))
  sessionStorage.setItem('printBah', '00788222')
  sessionStorage.setItem('printRecord', JSON.stringify(testRecord))
  
  window.open('/print', '_blank')
}

const testImageGallery = () => {
  window.open('/00788222', '_blank')
}

const testLogin = () => {
  window.open('/login', '_blank')
}
</script>

<style scoped>
.testing-view {
  height: 100%;
}

.test-section {
  padding: 20px 0;
}

.test-section h3 {
  margin-bottom: 20px;
  color: #333;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-row label {
  min-width: 120px;
  color: #333;
  font-weight: 500;
}

.response-section {
  margin-top: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.response-section h4 {
  margin-top: 0;
  margin-bottom: 12px;
  color: #333;
}

.response-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #666;
}

.response-content {
  font-family: 'Courier New', monospace;
}

.error-section {
  margin-top: 16px;
}

.result-section {
  margin-top: 16px;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
}

.result-section h4 {
  margin-top: 0;
  margin-bottom: 12px;
  font-size: 14px;
  color: #333;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-item label {
  min-width: 60px;
  font-size: 12px;
  color: #666;
}

.result-item .el-input {
  flex: 1;
}

.error-input {
  border-color: #f56c6c;
}

.feature-tests {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .form-row {
    flex-direction: column;
    align-items: stretch;
  }
  
  .form-row label {
    min-width: auto;
    margin-bottom: 4px;
  }
  
  .response-meta {
    flex-direction: column;
    gap: 8px;
  }
  
  .feature-tests {
    flex-direction: column;
  }
}
</style>

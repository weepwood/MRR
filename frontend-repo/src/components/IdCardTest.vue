<template>
  <div class="idcard-test-page">
    <h2>身份证加解密测试</h2>
    
    <div class="grid">
      <section class="card">
        <h3>加密测试</h3>
        <div class="form">
          <div class="row">
            <label>身份证号</label>
            <input v-model="idCardToEncrypt" placeholder="请输入身份证号" />
            <button @click="handleEncrypt" :disabled="!idCardToEncrypt">加密</button>
          </div>
          
          <div class="result" v-if="encryptResult">
            <h4>加密结果:</h4>
            <div class="result-item">
              <label>密文:</label>
              <code>{{ encryptResult.ciphertext }}</code>
              <button @click="copyToClipboard(encryptResult.ciphertext)">复制</button>
            </div>
            <div class="result-item">
              <label>IV向量:</label>
              <code>{{ encryptResult.iv }}</code>
              <button @click="copyToClipboard(encryptResult.iv)">复制</button>
            </div>
          </div>
        </div>
      </section>
      
      <section class="card">
        <h3>解密测试</h3>
        <div class="form">
          <div class="row">
            <label>密文</label>
            <input v-model="cipherToDecrypt" placeholder="请输入密文" />
          </div>
          <div class="row">
            <label>IV向量</label>
            <input v-model="ivToDecrypt" placeholder="请输入IV向量" />
          </div>
          <div class="row">
            <label></label>
            <button @click="handleDecrypt" :disabled="!cipherToDecrypt || !ivToDecrypt">解密</button>
          </div>
          
          <div class="result" v-if="decryptResult !== null">
            <h4>解密结果:</h4>
            <div class="result-item">
              <label>身份证号:</label>
              <code>{{ decryptResult }}</code>
              <button @click="copyToClipboard(decryptResult)">复制</button>
            </div>
          </div>
          
          <div class="error" v-if="decryptError">
            {{ decryptError }}
          </div>
        </div>
      </section>
      
      <section class="card">
        <h3>端到端测试</h3>
        <div class="form">
          <div class="row">
            <label>身份证号</label>
            <input v-model="idCardToTest" placeholder="请输入身份证号" />
            <button @click="handleEndToEndTest" :disabled="!idCardToTest">测试</button>
          </div>
          
          <div class="result" v-if="testResult !== null">
            <h4>测试结果:</h4>
            <div class="result-item">
              <label>原始:</label>
              <code>{{ idCardToTest }}</code>
            </div>
            <div class="result-item">
              <label>解密:</label>
              <code :class="{ 'error-text': testResult !== idCardToTest }">{{ testResult }}</code>
            </div>
            <div class="result-item">
              <label>结果:</label>
              <span :class="testResult === idCardToTest ? 'success' : 'error'">
                {{ testResult === idCardToTest ? '✅ 通过' : '❌ 失败' }}
              </span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { encryptIdCard, decryptIdCard } from '../utils/decrypt'

const idCardToEncrypt = ref('')
const encryptResult = ref(null)

const cipherToDecrypt = ref('')
const ivToDecrypt = ref('')
const decryptResult = ref(null)
const decryptError = ref('')

const idCardToTest = ref('')
const testResult = ref(null)

const handleEncrypt = () => {
  try {
    encryptResult.value = encryptIdCard(idCardToEncrypt.value)
  } catch (error) {
    console.error('加密失败:', error)
  }
}

const handleDecrypt = () => {
  try {
    decryptResult.value = decryptIdCard(cipherToDecrypt.value, ivToDecrypt.value)
    decryptError.value = ''
  } catch (error) {
    decryptError.value = '解密失败: ' + error.message
    decryptResult.value = null
  }
}

const handleEndToEndTest = () => {
  try {
    const { ciphertext, iv } = encryptIdCard(idCardToTest.value)
    testResult.value = decryptIdCard(ciphertext, iv)
  } catch (error) {
    testResult.value = null
    console.error('端到端测试失败:', error)
  }
}

const copyToClipboard = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    alert('已复制到剪贴板')
  }).catch(err => {
    console.error('复制失败:', err)
  })
}
</script>

<style scoped>
.idcard-test-page {
  padding: 20px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 16px;
}

.card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  padding: 16px;
}

.card h3 {
  margin: 0 0 12px 0;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.row {
  display: grid;
  grid-template-columns: 80px 1fr auto;
  align-items: center;
  gap: 8px;
}

label {
  color: #333;
  font-weight: 500;
}

input {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px;
  font-size: 14px;
}

button {
  padding: 8px 14px;
  background: #667eea;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

button:disabled {
  opacity: .6;
  cursor: not-allowed;
}

.result {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.result h4 {
  margin-top: 0;
}

.result-item {
  display: grid;
  grid-template-columns: 60px 1fr auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-item label {
  font-weight: normal;
}

.result-item code {
  background: #0b1020;
  color: #e2e8f0;
  padding: 4px 6px;
  border-radius: 4px;
  word-break: break-all;
  font-size: 13px;
}

.result-item button {
  padding: 4px 8px;
  font-size: 12px;
}

.error {
  color: #ef4444;
  background: #fef2f2;
  padding: 8px;
  border-radius: 4px;
  margin-top: 8px;
}

.error-text {
  color: #ef4444;
}

.success {
  color: #10b981;
  font-weight: bold;
}

.error {
  color: #ef4444;
  font-weight: bold;
}

@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
  }
  
  .row {
    grid-template-columns: 1fr;
  }
  
  .result-item {
    grid-template-columns: 1fr;
  }
}
</style>

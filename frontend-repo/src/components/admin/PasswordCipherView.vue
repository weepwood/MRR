<template>
  <el-card class="pmr-panel password-cipher-view" shadow="never">
    <template #header>
      <div class="pmr-panel-header">
        <div>
          <h3 class="pmr-panel-title">密码密文生成器</h3>
          <p class="pmr-panel-subtitle">输入明文后生成与后端 `PasswordUtil.sha256()` 一致的密文结果。</p>
        </div>
        <span class="pmr-badge">SHA-256</span>
      </div>
    </template>

    <el-alert
      title="当前生成规则与后端登录校验一致：对原始密码做 SHA-256 并输出小写十六进制字符串。"
      type="info"
      show-icon
      :closable="false"
      class="cipher-alert"
    />

    <el-form label-width="96px" class="cipher-form" @submit.prevent>
      <el-form-item label="明文密码">
        <el-input
          v-model="plainText"
          type="textarea"
          :rows="4"
          placeholder="输入要生成密文的密码，例如 br_password"
          show-word-limit
          maxlength="256"
        />
      </el-form-item>

      <div class="pmr-actions-row">
        <el-button type="primary" :loading="generating" @click="generateCipher">生成密文</el-button>
        <el-button @click="fillSample('br_password')">示例密码</el-button>
        <el-button @click="clearForm">清空</el-button>
      </div>
    </el-form>

    <section class="result-grid">
      <article class="result-card">
        <span class="result-label">输入长度</span>
        <strong>{{ plainText.length }}</strong>
        <small>字符</small>
      </article>
      <article class="result-card">
        <span class="result-label">算法</span>
        <strong>SHA-256</strong>
        <small>与后端保持一致</small>
      </article>
      <article class="result-card">
        <span class="result-label">密文长度</span>
        <strong>{{ cipherText.length || 0 }}</strong>
        <small>十六进制字符</small>
      </article>
      <article class="result-card">
        <span class="result-label">输出格式</span>
        <strong>hex</strong>
        <small>小写十六进制</small>
      </article>
    </section>

    <section class="cipher-output">
      <div class="output-head">
        <div>
          <h4>生成结果</h4>
          <p>可直接用于测试账号的 `password_hash` 字段或登录校验。</p>
        </div>
        <div class="pmr-actions-row">
          <el-button :disabled="!cipherText" @click="copyCipher">复制密文</el-button>
        </div>
      </div>

      <el-input
        v-model="cipherText"
        type="textarea"
        :rows="4"
        readonly
        placeholder="生成后的密文会显示在这里"
      />
    </section>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { SHA256 } from 'crypto-js'

const plainText = ref('')
const cipherText = ref('')
const generating = ref(false)

const generateCipher = async () => {
  const password = plainText.value
  if (!password) {
    cipherText.value = ''
    ElMessage.warning('请输入明文密码')
    return
  }

  generating.value = true
  try {
    cipherText.value = SHA256(password).toString()
    ElMessage.success('密文已生成')
  } catch (error) {
    console.error('生成密文失败:', error)
    ElMessage.error('生成密文失败')
  } finally {
    generating.value = false
  }
}

const copyCipher = async () => {
  if (!cipherText.value) {
    ElMessage.warning('请先生成密文')
    return
  }

  try {
    await navigator.clipboard.writeText(cipherText.value)
    ElMessage.success('密文已复制')
  } catch (error) {
    console.error('复制密文失败:', error)
    ElMessage.error('复制失败，请手动复制')
  }
}

const fillSample = (value) => {
  plainText.value = value
  generateCipher()
}

const clearForm = () => {
  plainText.value = ''
  cipherText.value = ''
}
</script>

<style scoped>
.password-cipher-view {
  margin-top: 20px;
}

.cipher-alert {
  margin-bottom: 16px;
}

.cipher-form {
  display: grid;
  gap: 4px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.result-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfdff 0%, #f4f8ff 100%);
  border: 1px solid #e6edf7;
}

.result-label {
  display: block;
  font-size: 12px;
  color: var(--pmr-color-text-secondary);
}

.result-card strong {
  display: block;
  margin-top: 6px;
  font-size: 24px;
  color: var(--pmr-color-text-primary);
  word-break: break-all;
}

.result-card small {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #7a889f;
}

.cipher-output {
  margin-top: 18px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e6edf7;
  background: #fff;
}

.output-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.output-head h4 {
  margin: 0;
  font-size: 16px;
}

.output-head p {
  margin: 6px 0 0;
  color: var(--pmr-color-text-secondary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .result-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .result-grid {
    grid-template-columns: 1fr;
  }

  .output-head {
    flex-direction: column;
  }
}
</style>

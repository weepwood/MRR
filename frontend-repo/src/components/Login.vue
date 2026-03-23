<template>
  <div class="login-container">
    <div class="login-box">
      <h2>病案翻拍</h2>
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <div class="input-with-icon">
            <input
              id="username"
              v-model="formData.username"
              type="text"
              placeholder="请输入用户名"
              required
            />
          </div>
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <div class="input-with-icon">
            <input
              id="password"
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              required
            />
          </div>
        </div>
        <button type="submit" :disabled="loading" class="login-btn">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <div v-if="error" class="error-message">
        {{ error }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../utils/api.js'

const router = useRouter()

const loading = ref(false)
const error = ref('')

const formData = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  if (!formData.username || !formData.password) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const response = await login(formData)
    if (response.data.code === 200) {
      localStorage.setItem('token', response.data.data.token)
      router.push('/admin')
    } else {
      error.value = response.data.message || '登录失败'
    }
  } catch (err) {
    console.error('登录错误:', err)
    error.value = err.response?.data?.message || '登录失败，请检查网络连接'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #dce3f168 0%, #9b9eab 100%);
}

.login-box {
  background: white;
  padding: 2rem;
  border-radius: 10px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}

.login-box h2 {
  text-align: center;
  color: #333;
  margin-bottom: 2rem;
  font-size: 1.8rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-group label {
  font-weight: 500;
  color: #555;
  font-size: 0.9rem;
}

.input-with-icon {
  position: relative;
  display: flex;
  align-items: center;
}

.input-with-icon :deep(.el-icon) {
  position: absolute;
  left: 10px;
  color: #999;
  z-index: 1;
}

.input-with-icon input {
  padding: 0.75rem 0.75rem 0.75rem 1.25rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 1rem;
  transition: border-color 0.3s ease;
  width: 100%;
}

.input-with-icon input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.25);
}

.login-btn {
  background: linear-gradient(135deg, #667eea 0%, #667eea 100%);
  color: white;
  padding: 0.75rem;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.35);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.login-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.45), 0 8px 20px rgba(102, 126, 234, 0.35);
}

.error-message {
  margin-top: 1rem;
  padding: 0.75rem;
  background: #fee;
  color: #c53030;
  border-radius: 6px;
  text-align: center;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}
</style>
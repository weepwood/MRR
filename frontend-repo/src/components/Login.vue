<template>
  <div class="login-page">
    <div class="glow-bg"></div>
    <div class="light-orb orb-a"></div>
    <div class="light-orb orb-b"></div>

    <div class="login-layout pmr-fade-up">
      <aside class="brand-panel">
        <p class="brand-tag">Medical Record Platform</p>
        <h1>病案管理系统 v0.0.7</h1>
        <p class="brand-subtitle">
          统一管理病案影像、日志与统计数据，让后台操作更安全、更高效。
        </p>

        <ul class="feature-list">
          <li>
            <el-icon><DataBoard /></el-icon>
            可视化统计总览
          </li>
          <li>
            <el-icon><Document /></el-icon>
            病案资料集中管理
          </li>
          <li>
            <el-icon><Lock /></el-icon>
            安全登录与权限隔离
          </li>
        </ul>
      </aside>

      <section class="form-panel">
        <h2>欢迎回来</h2>
        <p class="form-subtitle">请输入账号密码进入后台管理。</p>

        <form class="login-form" @submit.prevent="handleLogin">
          <label for="username">用户名</label>
          <div class="input-shell">
            <span class="input-icon">
              <el-icon><UserFilled /></el-icon>
            </span>
            <input
              id="username"
              v-model.trim="formData.username"
              type="text"
              autocomplete="username"
              placeholder="Enter your username"
              required
            />
          </div>

          <label for="password">密码</label>
          <div class="input-shell">
            <span class="input-icon">
              <el-icon><Key /></el-icon>
            </span>
            <input
              id="password"
              v-model="formData.password"
              type="password"
              autocomplete="current-password"
              placeholder="Enter your password"
              required
            />
          </div>

          <button
            class="login-btn"
            type="submit"
            :disabled="loading"
            @mouseenter="animateButtonHover(true)"
            @mouseleave="animateButtonHover(false)"
          >
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { ElMessage } from 'element-plus'
import { DataBoard, Document, Key, Lock, UserFilled, Warning } from '@element-plus/icons-vue'
import { login } from '@/utils/api'
import { getSession, isAdminUser, setSession } from '@/utils/session'

const router = useRouter()
const loading = ref(false)
const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches

const formData = reactive({
  username: '',
  password: ''
})

function resolveLoginUser(payload) {
  const user = payload?.user || payload?.profile || payload?.currentUser || payload?.data
  if (user && typeof user === 'object' && !Array.isArray(user)) {
    return user
  }

  if (
    payload &&
    typeof payload === 'object' &&
    !Array.isArray(payload) &&
    (payload.username || payload.displayName || payload.roleCode || payload.id)
  ) {
    return payload
  }

  return null
}

function resolveMessage(payload, fallback) {
  return payload?.message || payload?.msg || payload?.error || payload?.data?.message || payload?.data?.msg || fallback
}

const redirectIfLoggedIn = () => {
  const session = getSession()
  if (session?.token) {
    router.replace(isAdminUser() ? '/admin' : '/print')
  }
}

onMounted(() => {
  redirectIfLoggedIn()

  if (prefersReducedMotion) {
    gsap.set('.login-layout', { opacity: 1, y: 0, scale: 1 })
    return
  }

  gsap.fromTo(
    '.login-layout',
    { opacity: 0, y: 30, scale: 0.96 },
    { opacity: 1, y: 0, scale: 1, duration: 0.8, ease: 'power1.inOut' }
  )

  gsap.to('.glow-bg', {
    scale: 1.08,
    duration: 0.8,
    ease: 'sine.inOut',
    repeat: -1,
    yoyo: true
  })

  gsap.to('.light-orb.orb-a', {
    x: 80,
    y: -70,
    duration: 6,
    ease: 'sine.inOut',
    yoyo: true,
    repeat: -1
  })

  gsap.to('.light-orb.orb-b', {
    x: -80,
    y: 70,
    duration: 7,
    ease: 'sine.inOut',
    yoyo: true,
    repeat: -1
  })
})

const animateLoginSuccess = (nextRoute) => {
  if (prefersReducedMotion) {
    router.push(nextRoute)
    return
  }

  const tl = gsap.timeline({ defaults: { duration: 0.28, ease: 'power1.inOut' } })

  // 简洁流畅的出场动画。
  gsap.to('.glow-bg', { opacity: 1, duration: 0.18, ease: 'power1.inOut', yoyo: true, repeat: 1 })

  tl.to('.login-layout', { scale: 1.03, boxShadow: '0 22px 40px rgba(89, 109, 255, 0.3)' })
    .to('.login-layout', { rotate: 4, filter: 'grayscale(30%)', opacity: 0.75 })
    .to('.login-layout', { scale: 0.25, opacity: 0, rotate: 8 })

  gsap.to('.login-page', { filter: 'grayscale(40%)', duration: 0.5, ease: 'power1.out' })

  // 通过 GSAP 延迟调用, 避免 setTimeout
  gsap.delayedCall(0.85, () => {
    router.push(nextRoute)
  })
}

const animateLoginFailure = () => {
  if (prefersReducedMotion) {
    return
  }

  const tl = gsap.timeline({ defaults: { duration: 0.06, ease: 'power1.inOut' } })
  tl.to('.login-layout', { x: -10 })
    .to('.login-layout', { x: 10 })
    .to('.login-layout', { x: -8 })
    .to('.login-layout', { x: 8 })
    .to('.login-layout', { x: 0 })

  gsap.to('.login-layout', {
    boxShadow: '0 0 0 rgba(89, 109, 255, 0)',
    duration: 0.7,
    ease: 'power1.inOut'
  })
}

const animateButtonHover = (isHovering) => {
  gsap.to('.login-btn', {
    scale: isHovering ? 1.04 : 1,
    boxShadow: isHovering ? '0 12px 24px rgba(47, 111, 255, 0.25)' : '0 8px 18px rgba(47, 111, 255, 0.12)',
    duration: 0.25,
    ease: 'power2.out'
  })
}

const handleLogin = async () => {
  if (!formData.username || !formData.password) {
    ElMessage({ message: 'Please enter username and password', type: 'error', position: 'top-right', offset: 90 })
    return
  }

  loading.value = true

  try {
    const response = await login(formData)
    const payload = response?.data || {}
    const loginData = payload?.data || payload
    const token = loginData?.token || loginData?.accessToken || loginData?.jwt

    if (token) {
      setSession({
        token,
        user: resolveLoginUser(loginData) || resolveLoginUser(payload)
      })
      animateLoginSuccess(isAdminUser() ? '/admin' : '/print')
      return
    }

    const msg = resolveMessage(payload, 'Login failed, please check your account')
    ElMessage({ message: msg, type: 'error', position: 'top-right', offset: 90 })
    animateLoginFailure()
  } catch (err) {
    console.error('Login failed:', err)
    const msg = resolveMessage(err.response?.data, 'Login failed, please try again')
    ElMessage({ message: msg, type: 'error', position: 'top-right', offset: 90 })
    animateLoginFailure()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  padding: 24px;
  display: grid;
  place-items: center;
  overflow: hidden;
  background-image:
    linear-gradient(rgba(156, 163, 175, 0.18) 1px, transparent 1px),
    linear-gradient(90deg, rgba(156, 163, 175, 0.18) 1px, transparent 1px);
  background-size: 40px 40px;
  background-color: #f5f7fb;
}

.glow-bg {
  position: absolute;
  width: 740px;
  height: 740px;
  border-radius: 50%;
  top: 20%;
  left: 15%;
  background: rgba(88, 101, 242, 0.35);
  filter: blur(100px);
  pointer-events: none;
  z-index: 0;
}

.light-orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(2px);
}

.orb-a {
  width: 460px;
  height: 460px;
  left: -120px;
  top: -120px;
  background: radial-gradient(circle at center, rgba(47, 111, 255, 0.3), transparent 70%);
}

.orb-b {
  width: 420px;
  height: 420px;
  right: -120px;
  bottom: -160px;
  background: radial-gradient(circle at center, rgba(20, 184, 166, 0.3), transparent 70%);
}

.login-layout {
  position: relative;
  z-index: 1;
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  box-shadow: var(--pmr-shadow-surface-lg);
}

.brand-panel {
  padding: 44px;
  color: #f8fbff;
  background: linear-gradient(140deg, #1e54d6 0%, #2f6fff 48%, #0f766e 100%);
}

.brand-tag {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  opacity: 0.82;
}

.brand-panel h1 {
  margin: 14px 0 10px;
  font-size: 36px;
  line-height: 1.2;
  letter-spacing: 0.01em;
}

.brand-subtitle {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.92;
  max-width: 320px;
}

.feature-list {
  margin: 26px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 12px;
  padding: 10px 12px;
}

.form-panel {
  padding: 44px 40px;
  background: rgba(255, 255, 255, 0.92);
  position: relative;
}

.form-panel h2 {
  margin: 0;
  font-size: 28px;
  color: var(--pmr-color-text-primary);
  letter-spacing: 0.01em;
}

.form-subtitle {
  margin: 10px 0 0;
  color: var(--pmr-color-text-secondary);
  font-size: 14px;
}

.login-form {
  margin-top: 24px;
  display: grid;
  gap: 10px;
}

.login-form label {
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
  font-weight: 600;
}

.input-shell {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  display: grid;
  place-items: center;
  color: #6d7d96;
}

.input-shell input {
  width: 100%;
  height: 46px;
  border: 1px solid #d7e4fa;
  background: #ffffff;
  border-radius: 12px;
  padding: 0 14px 0 42px;
  font-size: 14px;
  color: var(--pmr-color-text-primary);
  transition:
    border-color var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard),
    box-shadow var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard);
}

.input-shell input:focus {
  outline: none;
  border-color: var(--pmr-color-brand-500);
  box-shadow: 0 0 0 4px rgba(47, 111, 255, 0.14);
}

.login-btn {
  margin-top: 12px;
  height: 46px;
  border: none;
  border-radius: 12px;
  background: var(--pmr-color-action-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard),
    background-color var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard),
    opacity var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard);
  box-shadow: 0 8px 18px rgba(47, 111, 255, 0.12);
}

.login-btn:hover:not(:disabled) {
  background: var(--pmr-color-action-primary-pressed);
  transform: translateY(-1px);
}

.login-btn:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

@media (max-width: 900px) {
  .login-layout {
    grid-template-columns: 1fr;
    max-width: 520px;
  }

  .brand-panel {
    padding: 28px;
  }

  .brand-panel h1 {
    font-size: 28px;
  }

  .brand-subtitle {
    max-width: none;
  }

  .form-panel {
    padding: 28px;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 14px;
  }

  .brand-panel {
    display: none;
  }

  .form-panel h2 {
    font-size: 24px;
  }
}
</style>

<route lang="yaml">
meta:
  title: 登录
  constant: true
  layout: false
</route>

<script setup lang="ts">
import { DataBoard, Document, Lock } from '@element-plus/icons-vue'
import gsap from 'gsap'
import { onMounted } from 'vue'
import LoginForm from '@/components/AccountForm/LoginForm.vue'
import RegisterForm from '@/components/AccountForm/RegisterForm.vue'
import ResetPasswordForm from '@/components/AccountForm/ResetPasswordForm.vue'
import ColorScheme from '@/layouts/components/Topbar/Toolbar/ColorScheme/index.vue'

defineOptions({
  name: 'Login',
})

const route = useRoute()
const router = useRouter()
const settingsStore = useSettingsStore()

const redirect = ref(route.query.redirect?.toString() ?? settingsStore.settings.home.fullPath)
const loginAccount = ref<string>()
const formType = ref<'login' | 'register' | 'resetPassword'>('login')

function handleLogin(account?: string) {
  loginAccount.value = account
  const target = redirect.value
  router.push(target)
}

const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false

function onLoginSuccess() {
  if (prefersReducedMotion) {
    handleLogin()
    return
  }
  const tl = gsap.timeline({ defaults: { duration: 0.28, ease: 'power1.inOut' } })
  tl.to('.login-layout', { scale: 1.03, boxShadow: '0 22px 40px rgba(89, 109, 255, 0.3)' })
    .to('.login-layout', { rotate: 4, filter: 'grayscale(30%)', opacity: 0.75 })
    .to('.login-layout', { scale: 0.25, opacity: 0, rotate: 8 })
  gsap.to('.login-page', { filter: 'grayscale(40%)', duration: 0.5, ease: 'power1.out' })
  gsap.delayedCall(0.85, () => {
    handleLogin()
  })
}

onMounted(() => {
  if (prefersReducedMotion) {
    gsap.set('.login-layout', { opacity: 1, y: 0, scale: 1 })
    return
  }

  gsap.fromTo(
    '.login-layout',
    { opacity: 0, y: 30, scale: 0.96 },
    { opacity: 1, y: 0, scale: 1, duration: 0.8, ease: 'power1.inOut' },
  )

  gsap.to('.glow-bg', {
    scale: 1.08,
    duration: 0.8,
    ease: 'sine.inOut',
    repeat: -1,
    yoyo: true,
  })

  gsap.to('.light-orb.orb-a', {
    x: 80,
    y: -70,
    duration: 6,
    ease: 'sine.inOut',
    yoyo: true,
    repeat: -1,
  })

  gsap.to('.light-orb.orb-b', {
    x: -80,
    y: 70,
    duration: 7,
    ease: 'sine.inOut',
    yoyo: true,
    repeat: -1,
  })
})
</script>

<template>
  <div class="login-page">
    <div class="glow-bg" />
    <div class="light-orb orb-a" />
    <div class="light-orb orb-b" />

    <div class="absolute right-4 top-4 z-1 flex-center border rounded-lg bg-background p-1 text-base">
      <ColorScheme v-if="settingsStore.settings.toolbar.colorScheme" />
    </div>

    <div class="login-layout">
      <aside class="brand-panel">
        <p class="brand-tag">
          Medical Record Platform
        </p>
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
        <Transition name="fade" mode="out-in">
          <div v-if="formType === 'login'">
            <h2>欢迎回来</h2>
            <p class="form-subtitle">
              请输入账号密码进入后台管理。
            </p>
            <LoginForm
              :account="loginAccount"
              @on-login="onLoginSuccess"
            />
            <div class="mt-4 flex-center gap-2 text-sm">
              <span class="text-secondary-foreground op-50">还没有帐号?</span>
              <FaButton variant="link" class="h-auto p-0" type="button" @click="formType = 'register'">
                注册新帐号
              </FaButton>
            </div>
            <div class="mt-1 text-center">
              <FaButton variant="link" class="h-auto p-0" type="button" @click="formType = 'resetPassword'">
                忘记密码了?
              </FaButton>
            </div>
          </div>
          <div v-else-if="formType === 'register'">
            <h2>注册新帐号</h2>
            <p class="form-subtitle">
              创建你的管理平台帐号
            </p>
            <RegisterForm
              :account="loginAccount"
              @on-register="(val) => { loginAccount = val; formType = 'login' }"
              @on-login="formType = 'login'"
            />
          </div>
          <div v-else-if="formType === 'resetPassword'">
            <h2>重置密码</h2>
            <p class="form-subtitle">
              请输入用户名以重置密码
            </p>
            <ResetPasswordForm
              :account="loginAccount"
              @on-reset-password="(val) => { loginAccount = val; formType = 'login' }"
              @on-login="formType = 'login'"
            />
          </div>
        </Transition>
      </section>
    </div>

    <FaCopyright class="copyright" />
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  overflow: hidden;
  background-color: #f5f7fb;
  background-image:
    linear-gradient(rgb(156 163 175 / 18%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(156 163 175 / 18%) 1px, transparent 1px);
  background-size: 40px 40px;
}

[data-mode="dark"] .login-page {
  background-color: hsl(var(--background));
  background-image:
    linear-gradient(rgb(156 163 175 / 8%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(156 163 175 / 8%) 1px, transparent 1px);
}

.glow-bg {
  position: absolute;
  top: 20%;
  left: 15%;
  z-index: 0;
  width: 740px;
  height: 740px;
  pointer-events: none;
  background: rgb(88 101 242 / 35%);
  border-radius: 50%;
  filter: blur(100px);
}

.light-orb {
  position: absolute;
  pointer-events: none;
  border-radius: 50%;
  filter: blur(2px);
}

.orb-a {
  top: -120px;
  left: -120px;
  width: 460px;
  height: 460px;
  background: radial-gradient(circle at center, rgb(47 111 255 / 30%), transparent 70%);
}

.orb-b {
  right: -120px;
  bottom: -160px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle at center, rgb(20 184 166 / 30%), transparent 70%);
}

.login-layout {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  width: min(980px, 100%);
  overflow: hidden;
  background: rgb(255 255 255 / 70%);
  border: 1px solid rgb(255 255 255 / 45%);
  border-radius: 24px;
  box-shadow: 0 20px 40px rgb(0 0 0 / 12%);
  backdrop-filter: blur(12px);
}

[data-mode="dark"] .login-layout {
  background: hsl(var(--background));
  border-color: hsl(var(--border));
}

.brand-panel {
  padding: 44px;
  color: #f8fbff;
  background: linear-gradient(140deg, #1e54d6 0%, #2f6fff 48%, #0f766e 100%);
}

.brand-tag {
  margin: 0;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.09em;
  opacity: 0.82;
}

.brand-panel h1 {
  margin: 14px 0 10px;
  font-size: 36px;
  line-height: 1.2;
  letter-spacing: 0.01em;
}

.brand-subtitle {
  max-width: 320px;
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.92;
}

.feature-list {
  display: grid;
  gap: 12px;
  padding: 0;
  margin: 26px 0 0;
  list-style: none;
}

.feature-list li {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  font-size: 14px;
  background: rgb(255 255 255 / 14%);
  border: 1px solid rgb(255 255 255 / 28%);
  border-radius: 12px;
}

.form-panel {
  position: relative;
  padding: 44px 40px;
  background: rgb(255 255 255 / 92%);
}

[data-mode="dark"] .form-panel {
  background: hsl(var(--background));
}

.form-panel h2 {
  margin: 0;
  font-size: 28px;
  color: var(--el-text-color-primary);
  letter-spacing: 0.01em;
}

.form-subtitle {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary, hsl(var(--muted-foreground, #6d7d96)));
}

.copyright {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 20px;
  margin: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (width <= 900px) {
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

@media (width <= 640px) {
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

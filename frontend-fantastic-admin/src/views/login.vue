<route lang="yaml">
meta:
  title: 登录
  constant: true
  layout: false
</route>

<script setup lang="ts">
/* eslint-disable antfu/if-newline, curly, import/consistent-type-specifier-style */
import { DataBoard, Document, Lock } from '@element-plus/icons-vue'
import {
  DEFAULT_LOGIN_PAGE_SETTINGS,
  getPublicLoginPageSettings,
  LOGIN_PAGE_SETTINGS_UPDATED_EVENT,
  type LoginPageSettings,
} from '@/api/modules/login-page-settings'
import LoginForm from '@/components/AccountForm/LoginForm.vue'
import RegisterForm from '@/components/AccountForm/RegisterForm.vue'
import SystemAdminContactPopover from '@/components/SystemAdminContactPopover/index.vue'

defineOptions({ name: 'Login' })

type AuthMode = 'login' | 'register'

const route = useRoute()
const router = useRouter()
const settingsStore = useSettingsStore()
const redirect = ref(route.query.redirect?.toString() ?? settingsStore.settings.home.fullPath)
const copy = reactive<LoginPageSettings>({ ...DEFAULT_LOGIN_PAGE_SETTINGS })
const productVersion = __SYSTEM_INFO__.product.version
const authMode = ref<AuthMode>(route.query.mode === 'register' ? 'register' : 'login')
const account = ref('')

const features = computed(() => [
  { icon: Document, title: copy.loginFeature1Title, description: copy.loginFeature1Description },
  { icon: DataBoard, title: copy.loginFeature2Title, description: copy.loginFeature2Description },
  { icon: Lock, title: copy.loginFeature3Title, description: copy.loginFeature3Description },
])
const loginTitle = computed(() => authMode.value === 'register'
  ? '申请系统账号'
  : `登录 ${copy.systemShortName || 'MRR'}`)
const formDescription = computed(() => authMode.value === 'register'
  ? '填写申请信息。管理员审核通过后，才可以使用注册账号登录。'
  : copy.loginFormDescription)
const footerText = computed(() => [copy.organizationName, copy.loginFooterText].filter(Boolean).join(' · '))
const sessionNotice = computed(() => {
  if (authMode.value !== 'login') return undefined
  const status = route.query.session?.toString()
  if (status === 'unavailable') {
    return {
      type: 'warning' as const,
      title: '认证服务暂时不可用，原登录状态已保留。可以稍后刷新重试，或直接重新登录。',
    }
  }
  if (status === 'expired') {
    return {
      type: 'info' as const,
      title: '原登录状态已失效或账号凭据发生变化，请重新登录。',
    }
  }
  return undefined
})

async function loadCopy() {
  Object.assign(copy, await getPublicLoginPageSettings())
}

function handleUpdated(event: Event) {
  const next = (event as CustomEvent<LoginPageSettings>).detail
  if (next) Object.assign(copy, next)
}

function handleLogin() {
  router.push(redirect.value)
}

function showLogin(nextAccount?: string) {
  account.value = nextAccount?.trim() || account.value
  authMode.value = 'login'
}

function showRegister(nextAccount?: string) {
  account.value = nextAccount?.trim() || account.value
  authMode.value = 'register'
}

function handleRegistrationSubmitted(nextAccount?: string) {
  showLogin(nextAccount)
}

onMounted(() => {
  void loadCopy()
  window.addEventListener(LOGIN_PAGE_SETTINGS_UPDATED_EVENT, handleUpdated)
})

onBeforeUnmount(() => {
  window.removeEventListener(LOGIN_PAGE_SETTINGS_UPDATED_EVENT, handleUpdated)
})
</script>

<template>
  <main class="login-page">
    <header class="login-toolbar">
      <div class="environment-badge">
        <span class="status-dot" />
        {{ copy.loginEnvironmentLabel }}
        <span class="version-separator">·</span>
        <span class="product-version">MRR v{{ productVersion }}</span>
      </div>
    </header>

    <section class="login-shell" :class="{ 'is-register': authMode === 'register' }" :aria-label="`${copy.systemName}${authMode === 'register' ? '注册申请' : '登录'}`">
      <aside class="brand-panel">
        <div class="brand-heading">
          <span>{{ copy.systemEnglishName }}</span>
          <h1>{{ copy.systemName }}</h1>
          <small v-if="copy.organizationName">{{ copy.organizationName }}</small>
          <p>{{ copy.systemDescription }}</p>
        </div>

        <div v-if="copy.loginFeatureEnabled" class="feature-list">
          <article v-for="item in features" :key="item.title">
            <span class="feature-icon"><el-icon><component :is="item.icon" /></el-icon></span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
          </article>
        </div>

        <div v-if="footerText" class="brand-footer">
          <FaIcon name="i-ri:server-line" />
          <span>{{ footerText }}</span>
        </div>
      </aside>

      <section class="form-panel">
        <div class="auth-mode-switch" role="tablist" aria-label="登录或注册">
          <button type="button" :class="{ active: authMode === 'login' }" role="tab" :aria-selected="authMode === 'login'" @click="showLogin()">
            账号登录
          </button>
          <button type="button" :class="{ active: authMode === 'register' }" role="tab" :aria-selected="authMode === 'register'" @click="showRegister()">
            注册申请
          </button>
        </div>

        <div class="form-header">
          <span class="form-eyebrow">{{ authMode === 'register' ? 'Account application' : 'Secure sign in' }}</span>
          <h2>{{ loginTitle }}</h2>
          <p>{{ formDescription }}</p>
        </div>

        <el-alert
          v-if="sessionNotice"
          class="session-notice"
          :type="sessionNotice.type"
          :title="sessionNotice.title"
          :closable="false"
          show-icon
        />

        <LoginForm
          v-if="authMode === 'login'"
          :key="`login-${account}`"
          :account="account"
          @on-login="handleLogin"
          @on-register="showRegister"
        />
        <RegisterForm
          v-else
          :key="`register-${account}`"
          :account="account"
          @on-login="showLogin"
          @on-register="handleRegistrationSubmitted"
        />

        <div class="login-help">
          <FaIcon name="i-ri:information-line" />
          <p>
            {{ authMode === 'register' ? '注册申请由系统管理员审核。请填写可核验的显示名称、联系方式或申请说明。' : copy.loginHelpText }}
            <SystemAdminContactPopover
              :visible="copy.systemAdminContactVisible"
              :display-name="copy.systemAdminDisplayName"
              :department="copy.systemAdminDepartment"
              :phone="copy.systemAdminPhone"
              :extension="copy.systemAdminExtension"
              :email="copy.systemAdminEmail"
              :service-hours="copy.systemAdminServiceHours"
              :description="copy.systemAdminDescription"
            />
          </p>
        </div>
      </section>
    </section>

    <FaCopyright class="copyright" />
  </main>
</template>

<style scoped>
/* stylelint-disable @stylistic/block-closing-brace-newline-after, @stylistic/selector-list-comma-newline-after, @stylistic/string-quotes, at-rule-empty-line-before, media-feature-range-notation, order/properties-order, rule-empty-line-before */
.login-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 100vh;
  min-height: 100dvh;
  padding: var(--mrr-space-5);
  color: var(--mrr-foreground);
  background-color: var(--mrr-app-shell-bg);
  background-image:
    linear-gradient(var(--mrr-app-shell-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--mrr-app-shell-grid) 1px, transparent 1px);
  background-size: 32px 32px;
}
.login-toolbar { display: flex; align-items: center; width: min(1120px, 100%); margin: 0 auto; }
.environment-badge {
  display: inline-flex;
  gap: var(--mrr-space-2);
  align-items: center;
  padding: 7px 11px;
  font-size: 12px;
  font-weight: 650;
  color: var(--mrr-muted-foreground);
  background: color-mix(in srgb, var(--mrr-card) 90%, transparent);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
  box-shadow: var(--mrr-shadow-xs);
  backdrop-filter: blur(12px);
}
.status-dot { width: 7px; height: 7px; background: var(--color-success); border-radius: 50%; box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-success) 15%, transparent); }
.version-separator { color: var(--mrr-border-strong); }
.product-version { font-family: var(--font-mono, monospace); font-size: 11px; color: var(--mrr-primary); }
.login-shell {
  align-self: center;
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(420px, 0.98fr);
  width: min(1120px, 100%);
  min-height: 610px;
  margin: var(--mrr-space-6) auto;
  overflow: hidden;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-2xl);
  box-shadow: var(--mrr-shadow-md);
}
.login-shell.is-register { min-height: 760px; }
.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 48px;
  overflow: hidden;
  background: radial-gradient(circle at 10% 0%, color-mix(in srgb, var(--mrr-primary) 14%, transparent), transparent 36%), var(--mrr-muted);
  border-right: 1px solid var(--mrr-border);
}
.brand-panel::after {
  position: absolute;
  right: -96px;
  bottom: -96px;
  width: 280px;
  height: 280px;
  pointer-events: none;
  content: '';
  border: 1px solid color-mix(in srgb, var(--mrr-primary) 14%, transparent);
  border-radius: 50%;
  box-shadow: 0 0 0 38px color-mix(in srgb, var(--mrr-primary) 4%, transparent), 0 0 0 76px color-mix(in srgb, var(--mrr-primary) 3%, transparent);
}
.brand-heading { position: relative; z-index: 1; max-width: 500px; }
.brand-heading > span, .form-eyebrow { font-size: 11px; font-weight: 750; color: var(--mrr-primary); text-transform: uppercase; letter-spacing: 0.1em; }
.brand-heading h1 { margin: var(--mrr-space-3) 0 0; font-size: clamp(30px, 4vw, 42px); line-height: 1.14; letter-spacing: -0.035em; }
.brand-heading small { display: block; margin-top: var(--mrr-space-2); font-size: 12px; color: var(--mrr-primary); }
.brand-heading p { max-width: 440px; margin: var(--mrr-space-4) 0 0; font-size: 14px; line-height: 1.75; color: var(--mrr-muted-foreground); }
.feature-list { position: relative; z-index: 1; display: grid; gap: var(--mrr-space-3); margin-top: var(--mrr-space-8); }
.feature-list article { display: flex; gap: var(--mrr-space-3); align-items: flex-start; padding: var(--mrr-space-4); background: color-mix(in srgb, var(--mrr-card) 72%, transparent); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.feature-icon { display: grid; flex: 0 0 auto; width: 34px; height: 34px; color: var(--mrr-primary); background: color-mix(in srgb, var(--mrr-primary) 10%, var(--mrr-card)); border: 1px solid color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border)); border-radius: var(--mrr-radius-md); place-items: center; }
.feature-list strong { display: block; font-size: 13px; }
.feature-list p { margin: 4px 0 0; font-size: 11px; line-height: 1.5; color: var(--mrr-muted-foreground); }
.brand-footer { position: relative; z-index: 1; display: flex; gap: var(--mrr-space-2); align-items: center; margin-top: auto; padding-top: var(--mrr-space-6); font-size: 11px; color: var(--mrr-muted-foreground); }
.form-panel { display: flex; flex-direction: column; justify-content: center; padding: 44px 48px; background: var(--mrr-card); }
.auth-mode-switch { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; padding: 4px; margin-bottom: var(--mrr-space-5); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-md); }
.auth-mode-switch button { min-height: 36px; padding: 0 14px; font-size: 13px; font-weight: 650; color: var(--mrr-muted-foreground); background: transparent; border-radius: calc(var(--mrr-radius-md) - 3px); }
.auth-mode-switch button.active { color: var(--mrr-foreground); background: var(--mrr-card); box-shadow: var(--mrr-shadow-xs); }
.form-header h2 { margin: var(--mrr-space-2) 0 0; font-size: 28px; letter-spacing: -0.025em; }
.form-header p { margin: var(--mrr-space-2) 0 0; font-size: 13px; line-height: 1.6; color: var(--mrr-muted-foreground); }
.session-notice { margin-top: var(--mrr-space-4); }
.login-help { display: flex; gap: var(--mrr-space-3); align-items: flex-start; padding: var(--mrr-space-4); margin-top: var(--mrr-space-5); color: var(--color-info); background: color-mix(in srgb, var(--color-info) 7%, var(--mrr-card)); border: 1px solid color-mix(in srgb, var(--color-info) 20%, var(--mrr-border)); border-radius: var(--mrr-radius-md); }
.login-help p { margin: 0; font-size: 11px; line-height: 1.7; color: var(--mrr-muted-foreground); }
.copyright { width: 100%; padding: var(--mrr-space-4) 0 0; margin: 0; }
@media (prefers-reduced-motion: no-preference) { .login-shell { animation: login-enter 0.35s ease-out both; } @keyframes login-enter { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } } }
@media (max-width: 900px) {
  .login-shell { grid-template-columns: 1fr; width: min(640px, 100%); }
  .brand-panel { padding: 28px; border-right: 0; border-bottom: 1px solid var(--mrr-border); }
  .brand-heading h1 { font-size: 30px; }
  .feature-list { grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: var(--mrr-space-5); }
  .feature-list article { display: block; padding: var(--mrr-space-3); }
  .feature-list article > div:last-child { margin-top: var(--mrr-space-2); }
  .brand-footer { display: none; }
  .form-panel { padding: 36px 32px; }
}
@media (max-width: 620px) {
  .login-page { padding: var(--mrr-space-3); }
  .login-toolbar { padding: 0 var(--mrr-space-1); }
  .login-shell { min-height: 0; margin: var(--mrr-space-4) auto; border-radius: var(--mrr-radius-xl); }
  .brand-panel { padding: var(--mrr-space-5); }
  .brand-heading h1 { font-size: 25px; }
  .brand-heading p, .feature-list, .brand-panel::after { display: none; }
  .form-panel { padding: 30px var(--mrr-space-5); }
  .form-header h2 { font-size: 24px; }
}
</style>

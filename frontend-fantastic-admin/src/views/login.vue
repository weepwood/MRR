<route lang="yaml">
meta:
  title: 登录
  constant: true
  layout: false
</route>

<script setup lang="ts">
/* eslint-disable antfu/if-newline, curly, import/consistent-type-specifier-style */
import { DataBoard, Document, Lock } from '@element-plus/icons-vue'
import { toast } from 'vue-sonner'
import {
  type ClientEnvironmentProbe,
  getPublicClientEnvironment,
} from '@/api/modules/client-environment'
import {
  DEFAULT_LOGIN_PAGE_SETTINGS,
  getPublicLoginPageSettings,
  LOGIN_PAGE_SETTINGS_UPDATED_EVENT,
  type LoginPageSettings,
} from '@/api/modules/login-page-settings'
import LoginForm from '@/components/AccountForm/LoginForm.vue'
import RegisterForm from '@/components/AccountForm/RegisterForm.vue'
import SystemAdminContactPopover from '@/components/SystemAdminContactPopover/index.vue'
import {
  collectDeviceSnapshot,
  collectNetworkSnapshot,
  type DeviceSnapshot,
  evaluateBrowserSupport,
  type NetworkSnapshot,
  parseUserAgent,
  subscribeNetworkChanges,
} from '@/utils/client-environment'
import { buildLoginEnvironmentView } from '@/utils/login-environment-report'

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

/* ---------------------------------------------------------------------------
 * 接入环境自检：浏览器侧探测 + 服务端回显客户端地址。
 * 探测失败不影响登录流程，仅把徽标状态置为「后端不可达」。
 * ------------------------------------------------------------------------- */
const environmentPanelId = 'login-environment-panel'
const environmentOpen = ref(false)
const environmentProbing = ref(false)
const environmentUserAgent = typeof navigator === 'undefined' ? '' : navigator.userAgent
const browserInfo = parseUserAgent(environmentUserAgent)
const browserSupport = evaluateBrowserSupport(browserInfo)
const networkSnapshot = ref<NetworkSnapshot>(collectNetworkSnapshot())
const deviceSnapshot = ref<DeviceSnapshot>(collectDeviceSnapshot())
const clientProbe = ref<ClientEnvironmentProbe | null>(null)
const environmentSampledAt = ref(Date.now())
const environmentView = computed(() => buildLoginEnvironmentView(
  browserInfo,
  networkSnapshot.value,
  deviceSnapshot.value,
  browserSupport,
  clientProbe.value,
  environmentUserAgent,
  environmentSampledAt.value,
))

async function probeEnvironment() {
  if (environmentProbing.value) return
  environmentProbing.value = true
  networkSnapshot.value = collectNetworkSnapshot()
  deviceSnapshot.value = collectDeviceSnapshot()
  try {
    clientProbe.value = await getPublicClientEnvironment()
  }
  finally {
    environmentSampledAt.value = Date.now()
    environmentProbing.value = false
  }
}

function handleNetworkChange() {
  networkSnapshot.value = collectNetworkSnapshot()
  void probeEnvironment()
}

function toggleEnvironment() {
  environmentOpen.value = !environmentOpen.value
  if (environmentOpen.value) void probeEnvironment()
}

async function copyEnvironmentReport() {
  try {
    if (typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
      throw new Error('clipboard unavailable')
    }
    await navigator.clipboard.writeText(environmentView.value.reportText)
    toast.success('环境信息已复制，可粘贴给管理员排查')
  }
  catch {
    toast.error('当前浏览器不支持自动复制，请手动选择面板内容')
  }
}

let unsubscribeNetworkChanges: (() => void) | undefined

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
  unsubscribeNetworkChanges = subscribeNetworkChanges(handleNetworkChange)
  void probeEnvironment()
})

onBeforeUnmount(() => {
  window.removeEventListener(LOGIN_PAGE_SETTINGS_UPDATED_EVENT, handleUpdated)
  if (unsubscribeNetworkChanges) unsubscribeNetworkChanges()
})
</script>

<template>
  <main class="login-page">
    <header class="login-toolbar">
      <div class="toolbar-row">
        <button
          type="button"
          class="environment-badge"
          :aria-expanded="environmentOpen"
          :aria-controls="environmentPanelId"
          @click="toggleEnvironment"
        >
          <span class="status-dot" :class="`tone-${environmentView.statusTone}`" />
          <span>{{ copy.loginEnvironmentLabel }}</span>
          <span class="version-separator">·</span>
          <span class="product-version">MRR v{{ productVersion }}</span>
          <span class="environment-separator">|</span>
          <span :class="`tone-text-${environmentView.statusTone}`">{{ environmentView.statusLabel }}</span>
          <template v-if="environmentView.clientIpLabel">
            <span class="environment-separator environment-detail">|</span>
            <span class="environment-mono environment-detail">{{ environmentView.clientIpLabel }}</span>
          </template>
          <template v-if="environmentView.browserLabel">
            <span class="environment-separator environment-detail">|</span>
            <span class="environment-detail">{{ environmentView.browserLabel }}</span>
          </template>
          <FaIcon
            class="environment-chevron"
            :name="environmentOpen ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'"
          />
        </button>
      </div>

      <section
        v-show="environmentOpen"
        :id="environmentPanelId"
        class="environment-panel"
        aria-label="客户端接入环境"
      >
        <div class="environment-groups">
          <section v-for="group in environmentView.groups" :key="group.key" class="environment-group">
            <h3>{{ group.title }}</h3>
            <dl>
              <div v-for="item in group.items" :key="item.label" class="environment-item">
                <dt>{{ item.label }}</dt>
                <dd :class="item.tone ? `tone-text-${item.tone}` : undefined" v-text="item.value" />
              </div>
            </dl>
          </section>
        </div>

        <div class="environment-footer">
          <p v-if="!browserSupport.supported" class="environment-warning">
            <FaIcon name="i-ri:error-warning-line" />
            <span>{{ browserSupport.reason }}</span>
          </p>
          <p class="environment-hint">
            客户端地址由服务端识别；若与预期不符，请联系管理员核对反向代理配置。
          </p>
          <div class="environment-actions">
            <button type="button" :disabled="environmentProbing" @click="probeEnvironment">
              {{ environmentProbing ? '检测中…' : '重新检测' }}
            </button>
            <button type="button" class="is-primary" @click="copyEnvironmentReport">
              复制环境信息
            </button>
          </div>
        </div>
      </section>
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
.login-toolbar { width: min(1120px, 100%); margin: 0 auto; }
.toolbar-row { display: flex; flex-wrap: wrap; gap: var(--mrr-space-2); align-items: center; }
.environment-badge {
  display: inline-flex;
  gap: var(--mrr-space-2);
  align-items: center;
  padding: 7px 12px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 650;
  color: var(--mrr-muted-foreground);
  cursor: pointer;
  background: color-mix(in srgb, var(--mrr-card) 90%, transparent);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
  box-shadow: var(--mrr-shadow-xs);
  backdrop-filter: blur(12px);
  transition: color 120ms ease, border-color 120ms ease;
}
.environment-badge:hover { color: var(--mrr-foreground); border-color: var(--mrr-border-strong); }
.environment-badge[aria-expanded='true'] { border-color: color-mix(in srgb, var(--mrr-primary) 45%, var(--mrr-border)); }
.environment-separator { color: var(--mrr-border-strong); }
.environment-mono { font-family: var(--font-mono, monospace); font-size: 11px; }
.environment-chevron { font-size: 13px; color: var(--mrr-muted-foreground); }
.status-dot { flex: 0 0 auto; width: 7px; height: 7px; border-radius: 50%; }
.status-dot.tone-ok { background: var(--color-success); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-success) 15%, transparent); }
.status-dot.tone-warn { background: var(--color-warning); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-warning) 15%, transparent); }
.status-dot.tone-danger { background: var(--color-danger); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-danger) 15%, transparent); }
.status-dot.tone-neutral { background: var(--mrr-border-strong); box-shadow: 0 0 0 3px color-mix(in srgb, var(--mrr-border-strong) 18%, transparent); }
.tone-text-ok { color: var(--color-success); }
.tone-text-warn { color: var(--color-warning); }
.tone-text-danger { color: var(--color-danger); }
.tone-text-neutral { color: var(--mrr-muted-foreground); }
.version-separator { color: var(--mrr-border-strong); }
.product-version { font-family: var(--font-mono, monospace); font-size: 11px; color: var(--mrr-primary); }
.environment-panel { padding: var(--mrr-space-5); margin-top: var(--mrr-space-3); background: color-mix(in srgb, var(--mrr-card) 96%, transparent); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); box-shadow: var(--mrr-shadow-xs); backdrop-filter: blur(12px); }
.environment-groups { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: var(--mrr-space-5); }
.environment-group h3 { margin: 0 0 var(--mrr-space-3); font-size: 11px; font-weight: 750; color: var(--mrr-primary); text-transform: uppercase; letter-spacing: 0.08em; }
.environment-group dl { display: grid; gap: 6px; margin: 0; }
.environment-item { display: flex; gap: var(--mrr-space-3); align-items: baseline; justify-content: space-between; }
.environment-item dt { flex: 0 0 auto; font-size: 11px; color: var(--mrr-muted-foreground); }
.environment-item dd { margin: 0; font-size: 12px; font-weight: 600; color: var(--mrr-foreground); text-align: right; overflow-wrap: anywhere; }
.environment-footer { display: flex; flex-wrap: wrap; gap: var(--mrr-space-3); align-items: center; justify-content: space-between; padding-top: var(--mrr-space-4); margin-top: var(--mrr-space-4); border-top: 1px solid var(--mrr-border); }
.environment-hint { flex: 1 1 260px; margin: 0; font-size: 11px; line-height: 1.6; color: var(--mrr-muted-foreground); }
.environment-warning { display: flex; flex: 1 1 100%; gap: var(--mrr-space-2); align-items: center; margin: 0; font-size: 11px; color: var(--color-warning); }
.environment-actions { display: flex; gap: var(--mrr-space-2); }
.environment-actions button { padding: 6px 12px; font-family: inherit; font-size: 11px; font-weight: 650; color: var(--mrr-muted-foreground); cursor: pointer; background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-pill); }
.environment-actions button:hover:not(:disabled) { color: var(--mrr-foreground); border-color: var(--mrr-border-strong); }
.environment-actions button:disabled { cursor: default; opacity: 0.6; }
.environment-actions button.is-primary { color: var(--mrr-primary); background: color-mix(in srgb, var(--mrr-primary) 8%, var(--mrr-card)); border-color: color-mix(in srgb, var(--mrr-primary) 32%, var(--mrr-border)); }
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

  /* 渐变层在 Chrome 86 会整条失效，实色底单独声明，避免品牌区与表单区失去分层。 */
  background-color: var(--mrr-muted);
  background-image: radial-gradient(circle at 10% 0%, color-mix(in srgb, var(--mrr-primary) 14%, transparent), transparent 36%);
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
  .environment-detail { display: none; }
  .environment-panel { padding: var(--mrr-space-4); }
  .environment-group dl { gap: 5px; }
  .form-panel { padding: 30px var(--mrr-space-5); }
  .form-header h2 { font-size: 24px; }
}
</style>

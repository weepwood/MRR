<route lang="yaml">
meta:
  title: 登录
  constant: true
  layout: false
</route>

<script setup lang="ts">
/* eslint-disable antfu/if-newline, curly, import/consistent-type-specifier-style */
import { LayoutDashboard as DataBoard, FileText as Document, LockKeyhole as Lock } from 'lucide-vue-next'
import { nextTick, onBeforeUnmount } from 'vue'
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
import { getEnvironmentProbeRetryDelay } from '@/utils/login-environment-retry'
import { isMotionEnabled } from '@/utils/motion-preference'
import { notify } from '@/utils/notification'

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
const environmentBadge = ref<HTMLButtonElement | null>(null)
const environmentOpen = ref(false)
let environmentKeyboardFocusRequested = false
const environmentProbing = ref(false)
const environmentRetryScheduled = ref(false)
const environmentUserAgent = typeof navigator === 'undefined' ? '' : navigator.userAgent
const browserInfo = parseUserAgent(environmentUserAgent)
const browserSupport = evaluateBrowserSupport(browserInfo)
const networkSnapshot = ref<NetworkSnapshot>(collectNetworkSnapshot())
const deviceSnapshot = ref<DeviceSnapshot>(collectDeviceSnapshot())
const clientProbe = ref<ClientEnvironmentProbe | null>(null)
const environmentSampledAt = ref(Date.now())
const localCurrentTime = ref(formatLocalDateTime(new Date()))
let localClockTimer: number | undefined
let environmentRetryTimer: number | undefined
let environmentProbeFailureCount = 0
let loginViewActive = true
const environmentView = computed(() => buildLoginEnvironmentView(
  browserInfo,
  networkSnapshot.value,
  deviceSnapshot.value,
  browserSupport,
  clientProbe.value,
  environmentUserAgent,
  environmentSampledAt.value,
))

function formatLocalDateTime(value: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

function updateLocalCurrentTime() {
  localCurrentTime.value = formatLocalDateTime(new Date())
}

function focusEnvironmentActionForKeyboard() {
  if (!environmentKeyboardFocusRequested || !environmentOpen.value) return
  document.getElementById(environmentPanelId)
    ?.querySelector<HTMLButtonElement>('.environment-actions button')
    ?.focus()
}

function openEnvironmentForKeyboard() {
  environmentKeyboardFocusRequested = true
  environmentOpen.value = true
  // Teleported popover content can mount after the first Vue update.
  void nextTick(focusEnvironmentActionForKeyboard)
}

function onEnvironmentPopoverShow() {
  updateLocalCurrentTime()
  void nextTick(focusEnvironmentActionForKeyboard)
}

function closeEnvironmentForKeyboard() {
  environmentKeyboardFocusRequested = false
  environmentOpen.value = false
  void nextTick(() => environmentBadge.value?.focus())
}

function clearEnvironmentRetry() {
  if (environmentRetryTimer !== undefined) {
    window.clearTimeout(environmentRetryTimer)
    environmentRetryTimer = undefined
  }
  environmentRetryScheduled.value = false
}

function scheduleEnvironmentRetry() {
  clearEnvironmentRetry()
  environmentProbeFailureCount += 1
  const delay = getEnvironmentProbeRetryDelay(environmentProbeFailureCount)
  environmentRetryScheduled.value = true
  environmentRetryTimer = window.setTimeout(() => {
    environmentRetryTimer = undefined
    environmentRetryScheduled.value = false
    void probeEnvironment(true)
  }, delay)
}

async function probeEnvironment(isAutomaticRetry = false) {
  if (environmentProbing.value || !loginViewActive) return
  if (!isAutomaticRetry) {
    clearEnvironmentRetry()
    environmentProbeFailureCount = 0
  }
  environmentProbing.value = true
  networkSnapshot.value = collectNetworkSnapshot()
  deviceSnapshot.value = collectDeviceSnapshot()
  try {
    const probe = await getPublicClientEnvironment()
    if (!loginViewActive) return
    clientProbe.value = probe
    if (networkSnapshot.value.online && probe.reachable) {
      clearEnvironmentRetry()
      environmentProbeFailureCount = 0
    }
    else {
      scheduleEnvironmentRetry()
    }
  }
  finally {
    if (loginViewActive) {
      environmentSampledAt.value = Date.now()
      environmentProbing.value = false
    }
  }
}

function handleNetworkChange() {
  networkSnapshot.value = collectNetworkSnapshot()
  void probeEnvironment()
}

async function copyEnvironmentReport() {
  try {
    if (typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
      throw new Error('clipboard unavailable')
    }
    await navigator.clipboard.writeText(environmentView.value.reportText)
    notify.success('环境信息已复制，可粘贴给管理员排查')
  }
  catch {
    notify.error('当前浏览器不支持自动复制，请手动选择面板内容')
  }
}

let unsubscribeNetworkChanges: (() => void) | undefined

const features = computed(() => [
  { icon: Document, title: copy.loginFeature1Title, description: copy.loginFeature1Description },
  { icon: DataBoard, title: copy.loginFeature2Title, description: copy.loginFeature2Description },
  { icon: Lock, title: copy.loginFeature3Title, description: copy.loginFeature3Description },
])
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
  localClockTimer = window.setInterval(updateLocalCurrentTime, 1000)
  window.addEventListener(LOGIN_PAGE_SETTINGS_UPDATED_EVENT, handleUpdated)
  unsubscribeNetworkChanges = subscribeNetworkChanges(handleNetworkChange)
  void probeEnvironment()
})

onBeforeUnmount(() => {
  loginViewActive = false
  window.removeEventListener(LOGIN_PAGE_SETTINGS_UPDATED_EVENT, handleUpdated)
  if (localClockTimer !== undefined) window.clearInterval(localClockTimer)
  clearEnvironmentRetry()
  if (unsubscribeNetworkChanges) unsubscribeNetworkChanges()
})
</script>

<template>
  <main class="login-page">
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

        <div class="brand-footer">
          <el-popover
            v-model:visible="environmentOpen"
            placement="top-start"
            :width="760"
            :popper-style="{ maxWidth: 'calc(100vw - 24px)' }"
            trigger="hover"
            :show-after="120"
            :hide-after="220"
            popper-class="login-environment-popper"
            @show="onEnvironmentPopoverShow"
            @after-enter="focusEnvironmentActionForKeyboard"
            @hide="environmentKeyboardFocusRequested = false"
          >
            <template #reference>
              <button
                ref="environmentBadge"
                type="button"
                class="environment-badge"
                :aria-expanded="environmentOpen"
                :aria-controls="environmentPanelId"
                @click="environmentOpen = true"
                @keydown.enter.prevent="openEnvironmentForKeyboard"
                @keydown.space.prevent="openEnvironmentForKeyboard"
                @keydown.esc.prevent.stop="closeEnvironmentForKeyboard"
              >
                <span class="status-dot" :class="`tone-${environmentView.statusTone}`" />
                <span class="product-version">MRR v{{ productVersion }}</span>
                <span class="environment-separator">|</span>
                <span :class="`tone-text-${environmentView.statusTone}`">{{ environmentView.statusLabel }}</span>
                <template v-if="environmentView.clientIpLabel">
                  <span class="environment-separator environment-detail">|</span>
                  <span class="environment-ip-label environment-detail">客户端 IP</span>
                  <span class="environment-mono environment-detail">{{ environmentView.clientIpLabel }}</span>
                </template>
                <template v-if="environmentView.browserLabel">
                  <span class="environment-separator environment-detail">|</span>
                  <span class="environment-detail">{{ environmentView.browserLabel }}</span>
                </template>
              </button>
            </template>

            <section
              :id="environmentPanelId"
              class="environment-panel"
              aria-label="客户端接入环境"
              @keydown.esc.prevent.stop="closeEnvironmentForKeyboard"
            >
              <header class="environment-panel__header">
                <div>
                  <span class="environment-panel__eyebrow">连接诊断</span>
                  <h2>客户端环境</h2>
                </div>
                <span class="environment-status" :class="`tone-text-${environmentView.statusTone}`">
                  <span class="status-dot" :class="`tone-${environmentView.statusTone}`" />
                  {{ environmentView.statusLabel }}
                </span>
              </header>

              <div class="environment-groups">
                <section
                  v-for="group in environmentView.groups"
                  :key="group.key"
                  class="environment-group"
                  :class="`environment-group--${group.key}`"
                >
                  <h3>{{ group.title }}</h3>
                  <dl>
                    <div
                      v-for="item in group.items"
                      :key="item.label"
                      class="environment-item"
                      :class="{ 'environment-item--single-line': item.label === '服务器时间' }"
                    >
                      <dt>
                        <span>{{ item.label }}</span>
                        <FaTooltip
                          v-if="group.key === 'address' && item.label === '客户端 IP'"
                          text="客户端 IP 由服务端识别；若与预期不符，请联系管理员核对反向代理配置。"
                        >
                          <button type="button" class="environment-item-info" aria-label="客户端 IP 说明">
                            <FaIcon name="i-lucide:info" aria-hidden="true" />
                          </button>
                        </FaTooltip>
                      </dt>
                      <dd
                        :class="item.tone ? `tone-text-${item.tone}` : undefined"
                        v-text="item.value"
                      />
                    </div>
                    <div v-if="group.key === 'client'" class="environment-item">
                      <dt>本机当前时间</dt>
                      <dd>{{ localCurrentTime }}</dd>
                    </div>
                  </dl>
                </section>
              </div>

              <div class="environment-footer">
                <p v-if="!browserSupport.supported" class="environment-warning">
                  <FaIcon name="i-lucide:triangle-alert" />
                  <span>{{ browserSupport.reason }}</span>
                </p>
                <p v-if="environmentRetryScheduled" class="environment-retry-status" role="status">
                  网络连接异常，将自动重试；重试间隔会逐步延长。
                </p>
                <div class="environment-actions">
                  <button type="button" :aria-disabled="environmentProbing" @click="probeEnvironment()">
                    {{ environmentProbing ? '检测中…' : '重新检测' }}
                  </button>
                  <button type="button" class="is-primary" @click="copyEnvironmentReport">
                    复制环境信息
                  </button>
                </div>
              </div>
            </section>
          </el-popover>
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

        <el-alert
          v-if="sessionNotice"
          class="session-notice"
          :type="sessionNotice.type"
          :title="sessionNotice.title"
          :closable="false"
          show-icon
        />

        <div class="auth-mode-stage">
          <Transition :name="authMode === 'register' ? 'auth-mode-forward' : 'auth-mode-backward'" :css="isMotionEnabled()">
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
          </Transition>
        </div>

        <SystemAdminContactPopover
          class="login-admin-contact"
          :visible="copy.systemAdminContactVisible"
          :display-name="copy.systemAdminDisplayName"
          :department="copy.systemAdminDepartment"
          :phone="copy.systemAdminPhone"
          :extension="copy.systemAdminExtension"
          :email="copy.systemAdminEmail"
          :service-hours="copy.systemAdminServiceHours"
          :description="copy.systemAdminDescription"
        />
      </section>
    </section>

    <FaCopyright class="copyright" />
  </main>
</template>

<style scoped>
/* stylelint-disable @stylistic/block-closing-brace-newline-after, @stylistic/selector-list-comma-newline-after, @stylistic/string-quotes, at-rule-empty-line-before, media-feature-range-notation, order/properties-order, rule-empty-line-before */
.login-page {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
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
.environment-badge {
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  max-width: 100%;
  padding: 7px 12px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 650;
  color: var(--mrr-muted-foreground);
  text-align: left;
  cursor: pointer;
  background: color-mix(in srgb, var(--mrr-card) 90%, transparent);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
  box-shadow: var(--mrr-shadow-xs);
  backdrop-filter: blur(12px);
  transition: color 160ms ease, background-color 160ms ease, border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}
.environment-badge:hover,
.environment-badge[aria-expanded='true'] {
  color: var(--mrr-foreground);
  background: var(--mrr-card);
  border-color: var(--mrr-primary);
  box-shadow: 0 0 0 3px rgb(37 99 235 / 8%), var(--mrr-shadow-xs);
  transform: translateY(-1px);
}
.environment-badge:focus-visible { outline: 2px solid var(--mrr-primary); outline-offset: 3px; }
.environment-badge[aria-expanded='true'] { border-color: color-mix(in srgb, var(--mrr-primary) 58%, var(--mrr-border)); }
.environment-separator { color: var(--mrr-border-strong); }
.environment-ip-label { font-size: 10px; font-weight: 600; color: var(--mrr-muted-foreground); }
.environment-mono { font-family: inherit; font-size: 11px; }
.status-dot { flex: 0 0 auto; width: 7px; height: 7px; border-radius: 50%; }
.status-dot.tone-ok { background: var(--color-success); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-success) 15%, transparent); }
.status-dot.tone-warn { background: var(--color-warning); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-warning) 15%, transparent); }
.status-dot.tone-danger { background: var(--color-danger); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-danger) 15%, transparent); }
.status-dot.tone-neutral { background: var(--mrr-border-strong); box-shadow: 0 0 0 3px color-mix(in srgb, var(--mrr-border-strong) 18%, transparent); }
.tone-text-ok { color: var(--color-success); }
.tone-text-warn { color: var(--color-warning); }
.tone-text-danger { color: var(--color-danger); }
.tone-text-neutral { color: var(--mrr-muted-foreground); }
.product-version { font-family: inherit; font-size: 11px; color: var(--mrr-primary); }
.environment-panel { max-height: min(68vh, 620px); padding: 0; margin: 0; overflow-y: auto; background: transparent; border: 0; box-shadow: none; scrollbar-color: var(--mrr-border-strong) transparent; scrollbar-width: thin; }
.environment-panel__header { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; padding: 0 0 var(--mrr-space-4); margin-bottom: var(--mrr-space-4); border-bottom: 1px solid var(--mrr-border); }
.environment-panel__eyebrow { font-size: 10px; font-weight: 750; color: var(--mrr-primary); letter-spacing: 0.08em; }
.environment-panel__header h2 { margin: 3px 0 0; font-size: 16px; font-weight: 750; color: var(--mrr-foreground); }
.environment-status { display: inline-flex; flex: 0 0 auto; gap: var(--mrr-space-2); align-items: center; padding: 6px 10px; font-size: 11px; font-weight: 700; background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-pill); }
.environment-status .status-dot { width: 6px; height: 6px; }
.environment-groups { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-3); }
.environment-group { min-width: 0; padding: var(--mrr-space-3); background: var(--mrr-card); background: color-mix(in srgb, var(--mrr-card) 68%, transparent); border: 1px solid var(--mrr-border); border-color: color-mix(in srgb, var(--mrr-border) 72%, transparent); border-radius: var(--mrr-radius-md); }
.environment-group--client { grid-column: 1 / -1; }
.environment-group h3 { margin: 0 0 var(--mrr-space-3); font-size: 10px; font-weight: 750; color: var(--mrr-primary); letter-spacing: 0.08em; }
.environment-group dl { display: grid; gap: 7px; margin: 0; }
.environment-group--client dl { grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: var(--mrr-space-5); }
.environment-item { display: flex; gap: var(--mrr-space-3); align-items: baseline; justify-content: space-between; }
.environment-group--client .environment-item--wide { grid-column: 1 / -1; }
.environment-item dt { display: inline-flex; flex: 0 0 auto; gap: 5px; align-items: center; font-size: 11px; color: var(--mrr-muted-foreground); }
.environment-item-info { display: inline-flex; width: 16px; height: 16px; align-items: center; justify-content: center; padding: 0; color: var(--mrr-muted-foreground); cursor: help; background: transparent; border: 0; border-radius: 50%; }
.environment-item-info:hover, .environment-item-info:focus-visible { color: var(--mrr-primary); outline: none; }
.environment-item-info :deep(svg) { width: 13px; height: 13px; }
.environment-item dd { margin: 0; font-size: 12px; font-weight: 600; color: var(--mrr-foreground); text-align: right; overflow-wrap: anywhere; }
.environment-item--single-line dd { white-space: nowrap; }
.environment-footer { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--mrr-space-3); align-items: center; padding-top: var(--mrr-space-4); margin-top: var(--mrr-space-4); border-top: 1px solid var(--mrr-border); }
.environment-warning { display: flex; flex: 1 1 100%; gap: var(--mrr-space-2); align-items: center; margin: 0; font-size: 11px; color: var(--color-warning); }
.environment-retry-status { grid-column: 1 / -1; margin: 0; font-size: 11px; line-height: 1.5; color: var(--color-warning); }
.environment-actions { grid-column: 1 / -1; display: flex; gap: var(--mrr-space-2); justify-content: flex-end; }
.environment-actions button { height: var(--mrr-control-height); padding: 0 12px; font-family: inherit; font-size: 11px; font-weight: 650; color: var(--mrr-muted-foreground); cursor: pointer; background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-pill); }
.environment-actions button:hover:not(:disabled) { color: var(--mrr-foreground); border-color: var(--mrr-border-strong); }
.environment-actions button[aria-disabled="true"] { cursor: default; opacity: 0.6; }
.environment-actions button.is-primary { color: var(--mrr-primary); background: color-mix(in srgb, var(--mrr-primary) 8%, var(--mrr-card)); border-color: color-mix(in srgb, var(--mrr-primary) 32%, var(--mrr-border)); }
:global(.login-environment-popper.el-popover) { padding: 18px; color: var(--mrr-popover-foreground); background: var(--mrr-popover); background: color-mix(in srgb, var(--mrr-popover) 88%, transparent); border-color: var(--mrr-border); border-color: color-mix(in srgb, var(--mrr-border) 78%, transparent); border-radius: var(--mrr-radius-lg); box-shadow: var(--mrr-shadow-lg); backdrop-filter: blur(18px) saturate(145%); }
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
.brand-heading > span { font-size: 11px; font-weight: 750; color: var(--mrr-primary); text-transform: uppercase; letter-spacing: 0.1em; }
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
.auth-mode-switch button { height: var(--mrr-control-height); min-height: var(--mrr-control-height); padding: 0 14px; font-size: 13px; font-weight: 650; color: var(--mrr-muted-foreground); background: transparent; border-radius: calc(var(--mrr-radius-md) - 3px); }
.auth-mode-switch button.active { color: var(--mrr-foreground); background: var(--mrr-card); box-shadow: var(--mrr-shadow-xs); }
.auth-mode-stage { display: grid; }
.auth-mode-stage > :deep(*) { grid-area: 1 / 1; min-width: 0; }
.session-notice { margin-top: var(--mrr-space-4); }
.login-admin-contact { display: flex; justify-content: flex-end; margin-top: var(--mrr-space-3); }
.copyright { width: 100%; padding: var(--mrr-space-4) 0 0; margin: 0; }
.login-shell { animation: login-enter 0.35s ease-out both; }
@keyframes login-enter { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
:global(.auth-mode-forward-enter-active),
:global(.auth-mode-forward-leave-active),
:global(.auth-mode-backward-enter-active),
:global(.auth-mode-backward-leave-active) { transition: opacity 220ms ease, transform 220ms cubic-bezier(0.22, 1, 0.36, 1); }
:global(.auth-mode-forward-enter-from) { opacity: 0; transform: translateX(24px); }
:global(.auth-mode-forward-leave-to) { opacity: 0; transform: translateX(-24px); }
:global(.auth-mode-backward-enter-from) { opacity: 0; transform: translateX(-24px); }
:global(.auth-mode-backward-leave-to) { opacity: 0; transform: translateX(24px); }
@media (min-width: 901px) {
  .auth-mode-stage { min-height: 520px; }
}
@media (max-width: 900px) {
  .login-shell { grid-template-columns: 1fr; width: min(640px, 100%); }
  .brand-panel { padding: 28px; border-right: 0; border-bottom: 1px solid var(--mrr-border); }
  .brand-heading h1 { font-size: 30px; }
  .feature-list { grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: var(--mrr-space-5); }
  .feature-list article { display: block; padding: var(--mrr-space-3); }
  .feature-list article > div:last-child { margin-top: var(--mrr-space-2); }
  .form-panel { padding: 36px 32px; }
}
@media (max-width: 620px) {
  .login-page { padding: var(--mrr-space-3); }
  .login-shell { min-height: 0; margin: var(--mrr-space-4) auto; border-radius: var(--mrr-radius-xl); }
  .login-shell.is-register { min-height: 0; }
  .brand-panel { padding: var(--mrr-space-5); }
  .brand-heading h1 { font-size: 25px; }
  .brand-heading p, .feature-list, .brand-panel::after { display: none; }
  .environment-detail { display: none; }
  :global(.login-environment-popper.el-popover) { padding: var(--mrr-space-4); }
  .environment-groups { grid-template-columns: 1fr; }
  .environment-group--client { grid-column: auto; }
  .environment-group--client dl { grid-template-columns: 1fr; }
  .environment-group dl { gap: 5px; }
  .environment-item--single-line dd { white-space: normal; }
  .environment-footer { grid-template-columns: 1fr; }
  .environment-actions { justify-content: flex-end; }
  .form-panel { padding: 30px var(--mrr-space-5); }
}
@media (min-width: 901px) and (min-height: 900px) {
  .login-shell,
  .login-shell.is-register { min-height: 760px; }
}
@media (orientation: portrait) {
  .login-shell { align-self: start; }
}
@media (prefers-reduced-motion: reduce) {
  :global(.auth-mode-forward-enter-active),
  :global(.auth-mode-forward-leave-active),
  :global(.auth-mode-backward-enter-active),
  :global(.auth-mode-backward-leave-active) { transition-duration: 0.01ms; }
}
</style>

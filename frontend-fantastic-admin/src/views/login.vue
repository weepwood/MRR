<route lang="yaml">
meta:
  title: 登录
  constant: true
  layout: false
</route>

<script setup lang="ts">
import { DataBoard, Document, Lock } from '@element-plus/icons-vue'
import LoginForm from '@/components/AccountForm/LoginForm.vue'
import ColorScheme from '@/layouts/components/Topbar/Toolbar/ColorScheme/index.vue'

defineOptions({ name: 'Login' })

const route = useRoute()
const router = useRouter()
const settingsStore = useSettingsStore()
const redirect = ref(route.query.redirect?.toString() ?? settingsStore.settings.home.fullPath)

function handleLogin() {
  router.push(redirect.value)
}
</script>

<template>
  <main class="login-page">
    <header class="login-toolbar">
      <div class="environment-badge">
        <span class="status-dot" />
        MRR Console
      </div>
      <div class="theme-control">
        <ColorScheme v-if="settingsStore.settings.toolbar.colorScheme" />
      </div>
    </header>

    <section class="login-shell" aria-label="MRR 系统登录">
      <aside class="brand-panel">
        <div class="brand-mark">
          <FaIcon name="i-ri:archive-stack-line" />
        </div>
        <div class="brand-heading">
          <span>Medical Record Repository</span>
          <h1>病案文件管理系统</h1>
          <p>面向病案影像、档案记录与运行审计的一体化工作平台。</p>
        </div>

        <div class="feature-list">
          <article>
            <span class="feature-icon"><el-icon><Document /></el-icon></span>
            <div>
              <strong>统一档案管理</strong>
              <p>集中检索病案、影像和装箱记录。</p>
            </div>
          </article>
          <article>
            <span class="feature-icon"><el-icon><DataBoard /></el-icon></span>
            <div>
              <strong>运行数据可视化</strong>
              <p>查看扫描、访问和服务状态。</p>
            </div>
          </article>
          <article>
            <span class="feature-icon"><el-icon><Lock /></el-icon></span>
            <div>
              <strong>权限与审计</strong>
              <p>按角色控制功能并保留访问记录。</p>
            </div>
          </article>
        </div>

        <div class="brand-footer">
          <FaIcon name="i-ri:server-line" />
          <span>医院内网部署 · 数据由本地服务管理</span>
        </div>
      </aside>

      <section class="form-panel">
        <div class="form-header">
          <span class="form-eyebrow">Secure sign in</span>
          <h2>登录 MRR</h2>
          <p>使用管理员分配的账号进入系统工作区。</p>
        </div>

        <LoginForm @on-login="handleLogin" />

        <div class="login-help">
          <FaIcon name="i-ri:information-line" />
          <p>系统不开放自助注册和在线重置密码。账号创建、角色调整或密码问题请联系系统管理员。</p>
        </div>
      </section>
    </section>

    <FaCopyright class="copyright" />
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
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

.login-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1080px, 100%);
  margin: 0 auto;
}

.environment-badge,
.theme-control {
  display: inline-flex;
  align-items: center;
  background: color-mix(in srgb, var(--mrr-card) 90%, transparent);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
  box-shadow: var(--mrr-shadow-xs);
  backdrop-filter: blur(12px);
}

.environment-badge {
  gap: var(--mrr-space-2);
  padding: 7px 11px;
  font-size: 12px;
  font-weight: 650;
  color: var(--mrr-muted-foreground);
}

.status-dot {
  width: 7px;
  height: 7px;
  background: var(--color-success);
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-success) 15%, transparent);
}

.theme-control {
  min-width: 38px;
  min-height: 38px;
  padding: 3px;
  border-radius: var(--mrr-radius-md);
}

.login-shell {
  align-self: center;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(380px, 0.92fr);
  width: min(1080px, 100%);
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
  background:
    radial-gradient(circle at 10% 0%, color-mix(in srgb, var(--mrr-primary) 14%, transparent), transparent 36%),
    var(--mrr-muted);
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
  box-shadow:
    0 0 0 38px color-mix(in srgb, var(--mrr-primary) 4%, transparent),
    0 0 0 76px color-mix(in srgb, var(--mrr-primary) 3%, transparent);
}

.brand-mark,
.feature-icon {
  display: grid;
  color: var(--mrr-primary);
  background: color-mix(in srgb, var(--mrr-primary) 10%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border));
  place-items: center;
}

.brand-mark {
  width: 52px;
  height: 52px;
  font-size: 25px;
  border-radius: var(--mrr-radius-xl);
  box-shadow: var(--mrr-shadow-xs);
}

.brand-heading {
  max-width: 500px;
  margin-top: var(--mrr-space-8);
}

.brand-heading > span,
.form-eyebrow {
  font-size: 11px;
  font-weight: 750;
  color: var(--mrr-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.brand-heading h1 {
  margin: var(--mrr-space-3) 0 0;
  font-size: clamp(30px, 4vw, 42px);
  line-height: 1.14;
  letter-spacing: -0.035em;
}

.brand-heading p {
  max-width: 440px;
  margin: var(--mrr-space-4) 0 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--mrr-muted-foreground);
}

.feature-list {
  display: grid;
  gap: var(--mrr-space-3);
  margin-top: var(--mrr-space-8);
}

.feature-list article {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: flex-start;
  padding: var(--mrr-space-4);
  background: color-mix(in srgb, var(--mrr-card) 72%, transparent);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.feature-icon {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: var(--mrr-radius-md);
}

.feature-list strong {
  display: block;
  font-size: 13px;
}

.feature-list p {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--mrr-muted-foreground);
}

.brand-footer {
  position: relative;
  z-index: 1;
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  margin-top: auto;
  padding-top: var(--mrr-space-6);
  font-size: 11px;
  color: var(--mrr-muted-foreground);
}

.form-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 52px 48px;
  background: var(--mrr-card);
}

.form-header h2 {
  margin: var(--mrr-space-2) 0 0;
  font-size: 28px;
  letter-spacing: -0.025em;
}

.form-header p {
  margin: var(--mrr-space-2) 0 0;
  font-size: 13px;
  color: var(--mrr-muted-foreground);
}

.login-help {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: flex-start;
  padding: var(--mrr-space-4);
  margin-top: var(--mrr-space-5);
  color: var(--color-info);
  background: color-mix(in srgb, var(--color-info) 7%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--color-info) 20%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
}

.login-help p {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.copyright {
  width: 100%;
  padding: var(--mrr-space-4) 0 0;
  margin: 0;
}

@media (prefers-reduced-motion: no-preference) {
  .login-shell {
    animation: login-enter 0.35s ease-out both;
  }

  @keyframes login-enter {
    from {
      opacity: 0;
      transform: translateY(12px);
    }

    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    width: min(560px, 100%);
  }

  .brand-panel {
    padding: 28px;
    border-right: 0;
    border-bottom: 1px solid var(--mrr-border);
  }

  .brand-heading {
    margin-top: var(--mrr-space-5);
  }

  .brand-heading h1 {
    font-size: 30px;
  }

  .feature-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    margin-top: var(--mrr-space-5);
  }

  .feature-list article {
    display: block;
    padding: var(--mrr-space-3);
  }

  .feature-list article > div:last-child {
    margin-top: var(--mrr-space-2);
  }

  .brand-footer {
    display: none;
  }

  .form-panel {
    padding: 36px 32px;
  }
}

@media (max-width: 620px) {
  .login-page {
    padding: var(--mrr-space-3);
  }

  .login-toolbar {
    padding: 0 var(--mrr-space-1);
  }

  .login-shell {
    min-height: 0;
    margin: var(--mrr-space-4) auto;
    border-radius: var(--mrr-radius-xl);
  }

  .brand-panel {
    padding: var(--mrr-space-5);
  }

  .brand-heading h1 {
    font-size: 25px;
  }

  .brand-heading p,
  .feature-list,
  .brand-panel::after {
    display: none;
  }

  .form-panel {
    padding: 30px var(--mrr-space-5);
  }

  .form-header h2 {
    font-size: 24px;
  }
}
</style>

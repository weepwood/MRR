<route lang="yaml">
name: passwordChangeRequired
path: /password/change-required
meta:
  title: 修改初始密码
  constant: true
  layout: false
</route>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import apiUser from '@/api/modules/user'
import ColorScheme from '@/layouts/components/Topbar/Toolbar/ColorScheme/index.vue'

defineOptions({ name: 'PasswordChangeRequired' })

const userStore = useUserStore()
const loading = ref(false)
const showCurrentPassword = ref(false)
const showNewPassword = ref(false)
const formRef = ref()
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  currentPassword: [{ required: true, message: '请输入当前使用的初始密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 12, max: 64, message: '新密码长度应为 12 到 64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        callback(value === form.newPassword ? undefined : new Error('两次输入的新密码不一致'))
      },
      trigger: ['blur', 'change'],
    },
  ],
}

const passwordStrength = computed(() => {
  const value = form.newPassword
  let score = 0
  if (value.length >= 12) { score += 1 }
  if (value.length >= 16) { score += 1 }
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) { score += 1 }
  if (/\d/.test(value)) { score += 1 }
  if (/[^\w\s]/.test(value)) { score += 1 }
  if (score >= 4) { return { label: '较强', type: 'success' as const, percent: 100 } }
  if (score >= 2) { return { label: '一般', type: 'warning' as const, percent: 58 } }
  return { label: '较弱', type: 'exception' as const, percent: value ? 28 : 0 }
})

async function submit() {
  await formRef.value?.validate()
  if (form.currentPassword === form.newPassword) {
    ElMessage.warning('新密码不能与当前密码相同')
    return
  }

  loading.value = true
  try {
    await apiUser.requiredPasswordChange({ ...form })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    await userStore.requestLogout()
  }
  catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '密码修改失败')
  }
  finally {
    loading.value = false
  }
}

async function logout() {
  await userStore.logout()
}
</script>

<template>
  <main class="password-page">
    <header class="page-toolbar">
      <div class="environment-badge">
        <span class="status-dot" />MRR Credential Setup
      </div>
      <ColorScheme />
    </header>

    <section class="password-card" aria-labelledby="password-change-title">
      <div class="security-mark">
        <FaIcon name="i-ri:shield-keyhole-line" />
      </div>
      <div class="card-heading">
        <span>Required security action</span>
        <h1 id="password-change-title">
          首次登录，请设置新密码
        </h1>
        <p>
          账号 <strong>{{ userStore.profile.username || userStore.account }}</strong>
          当前使用初始密码或管理员重置后的临时密码。完成修改前无法访问病案、患者和系统管理功能。
        </p>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="当前密码仅用于完成本次身份确认；修改成功后当前登录状态会立即失效。"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="password-form">
        <el-form-item label="当前初始密码" prop="currentPassword">
          <el-input
            v-model="form.currentPassword"
            :type="showCurrentPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="输入当前使用的初始密码"
          >
            <template #suffix>
              <button type="button" class="visibility-button" @click="showCurrentPassword = !showCurrentPassword">
                <FaIcon :name="showCurrentPassword ? 'i-ri:eye-off-line' : 'i-ri:eye-line'" />
              </button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            :type="showNewPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="至少 12 位，建议使用较长的易记口令"
          >
            <template #suffix>
              <button type="button" class="visibility-button" @click="showNewPassword = !showNewPassword">
                <FaIcon :name="showNewPassword ? 'i-ri:eye-off-line' : 'i-ri:eye-line'" />
              </button>
            </template>
          </el-input>
          <div class="strength-row">
            <el-progress :percentage="passwordStrength.percent" :show-text="false" :status="passwordStrength.type" />
            <span>密码强度：{{ passwordStrength.label }}</span>
          </div>
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="再次输入新密码"
            @keyup.enter="submit"
          />
        </el-form-item>
      </el-form>

      <div class="password-rules">
        <strong>密码要求</strong>
        <span>长度 12–64 位；不能与当前密码相同；建议使用多个无关词语组成长口令。</span>
      </div>

      <div class="card-actions">
        <el-button @click="logout">
          退出登录
        </el-button>
        <el-button type="primary" :loading="loading" @click="submit">
          保存新密码并重新登录
        </el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.password-page {
  display: grid;
  grid-template-rows: auto 1fr;
  min-height: 100vh;
  padding: var(--mrr-space-5);
  color: var(--mrr-foreground);
  background-color: var(--mrr-app-shell-bg);
  background-image:
    linear-gradient(var(--mrr-app-shell-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--mrr-app-shell-grid) 1px, transparent 1px);
  background-size: 32px 32px;
}
.page-toolbar { display: flex; align-items: center; justify-content: space-between; width: min(620px, 100%); margin: 0 auto; }
.environment-badge { display: inline-flex; gap: 8px; align-items: center; padding: 7px 11px; font-size: 12px; font-weight: 650; color: var(--mrr-muted-foreground); background: color-mix(in srgb, var(--mrr-card) 90%, transparent); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-pill); }
.status-dot { width: 7px; height: 7px; background: var(--color-warning); border-radius: 50%; box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-warning) 15%, transparent); }
.password-card { align-self: center; width: min(620px, 100%); padding: clamp(28px, 5vw, 48px); margin: var(--mrr-space-6) auto; background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-2xl); box-shadow: var(--mrr-shadow-md); }
.security-mark { display: grid; place-items: center; width: 52px; height: 52px; font-size: 25px; color: var(--color-warning); background: color-mix(in srgb, var(--color-warning) 12%, var(--mrr-card)); border: 1px solid color-mix(in srgb, var(--color-warning) 22%, var(--mrr-border)); border-radius: var(--mrr-radius-xl); }
.card-heading { margin: var(--mrr-space-5) 0; }
.card-heading > span { font-size: 11px; font-weight: 750; color: var(--color-warning); text-transform: uppercase; letter-spacing: 0.1em; }
.card-heading h1 { margin: var(--mrr-space-2) 0 0; font-size: clamp(25px, 4vw, 32px); letter-spacing: -0.03em; }
.card-heading p { margin: var(--mrr-space-3) 0 0; font-size: 13px; line-height: 1.7; color: var(--mrr-muted-foreground); }
.password-form { margin-top: var(--mrr-space-5); }
.visibility-button { display: grid; place-items: center; padding: 4px; color: var(--mrr-muted-foreground); cursor: pointer; background: transparent; border: 0; }
.strength-row { display: grid; grid-template-columns: 1fr auto; gap: 12px; align-items: center; width: 100%; margin-top: 8px; font-size: 11px; color: var(--mrr-muted-foreground); }
.password-rules { display: grid; gap: 4px; padding: var(--mrr-space-4); font-size: 12px; line-height: 1.6; color: var(--mrr-muted-foreground); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-md); }
.password-rules strong { color: var(--mrr-foreground); }
.card-actions { display: flex; gap: var(--mrr-space-3); justify-content: flex-end; margin-top: var(--mrr-space-5); }

@media (width <= 620px) {
  .password-page { padding: var(--mrr-space-3); }
  .password-card { padding: var(--mrr-space-5); }
  .card-actions { flex-direction: column-reverse; }
  .card-actions :deep(.el-button) { width: 100%; margin-left: 0; }
}
</style>

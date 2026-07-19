<route lang="yaml">
meta:
  title: 登录页文案
  auth:
    - system:read
  cache: false
</route>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DEFAULT_LOGIN_PAGE_SETTINGS,
  getManagedLoginPageSettings,
  saveManagedLoginPageSettings,
  type LoginPageSettings,
} from '@/api/modules/login-page-settings'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'

defineOptions({ name: 'LoginPageSettings' })

const loading = ref(false)
const saving = ref(false)
const form = reactive<LoginPageSettings>({ ...DEFAULT_LOGIN_PAGE_SETTINGS })
const savedSnapshot = ref('')

const isDirty = computed(() => JSON.stringify(form) !== savedSnapshot.value)

function snapshot() {
  savedSnapshot.value = JSON.stringify(form)
}

async function loadSettings(showMessage = false) {
  loading.value = true
  try {
    Object.assign(form, await getManagedLoginPageSettings())
    snapshot()
    if (showMessage) ElMessage.success('登录页文案已重新加载')
  }
  catch (error: any) {
    ElMessage.error(error?.message || '登录页文案加载失败')
  }
  finally {
    loading.value = false
  }
}

function validate() {
  const entries = Object.entries(form)
  if (entries.some(([, value]) => !String(value).trim())) {
    ElMessage.warning('登录页文案不能为空')
    return false
  }
  if (entries.some(([, value]) => String(value).length > 240)) {
    ElMessage.warning('单项文案不能超过 240 个字符')
    return false
  }
  return true
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    await saveManagedLoginPageSettings({ ...form })
    snapshot()
    ElMessage.success('登录页文案已保存，刷新登录页后生效')
  }
  catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '保存失败')
  }
  finally {
    saving.value = false
  }
}

async function resetDefaults() {
  try {
    await ElMessageBox.confirm('确认恢复登录页默认文案吗？保存后才会生效。', '恢复默认', {
      type: 'warning',
      confirmButtonText: '恢复默认',
      cancelButtonText: '取消',
    })
    Object.assign(form, DEFAULT_LOGIN_PAGE_SETTINGS)
  }
  catch {}
}

onMounted(() => loadSettings())
</script>

<template>
  <MrrPageShell width="standard">
    <MrrPageHeader
      eyebrow="Login Page Copy"
      title="登录页文案"
      description="配置未登录页面的品牌介绍、功能说明、登录提示与部署说明。"
    >
      <template #actions>
        <el-button :disabled="loading || saving" @click="loadSettings(true)">重新加载</el-button>
        <el-button :disabled="loading || saving" @click="resetDefaults">恢复默认</el-button>
        <el-button type="primary" :loading="saving" :disabled="loading || !isDirty" @click="save">
          保存文案
        </el-button>
      </template>
    </MrrPageHeader>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="该页面只配置公开展示文案。用户名、密码字段、登录按钮和错误提示保持固定，避免误配置影响登录。"
    />

    <el-form v-loading="loading" :model="form" label-position="top" class="copy-form">
      <section class="copy-card">
        <header><strong>顶部与品牌区域</strong><span>登录页左侧主要说明</span></header>
        <div class="field-grid">
          <el-form-item label="环境标签"><el-input v-model="form.loginEnvironmentLabel" maxlength="60" show-word-limit /></el-form-item>
          <el-form-item label="品牌英文标题"><el-input v-model="form.loginBrandEyebrow" maxlength="80" show-word-limit /></el-form-item>
        </div>
        <el-form-item label="系统主标题"><el-input v-model="form.loginBrandTitle" maxlength="80" show-word-limit /></el-form-item>
        <el-form-item label="品牌说明"><el-input v-model="form.loginBrandDescription" type="textarea" :rows="2" maxlength="160" show-word-limit /></el-form-item>
        <el-form-item label="底部部署说明"><el-input v-model="form.loginFooterText" maxlength="120" show-word-limit /></el-form-item>
      </section>

      <section class="copy-card">
        <header><strong>功能说明</strong><span>登录页左侧三项能力介绍</span></header>
        <div class="feature-editor">
          <el-form-item label="功能 1 标题"><el-input v-model="form.loginFeature1Title" maxlength="60" show-word-limit /></el-form-item>
          <el-form-item label="功能 1 说明"><el-input v-model="form.loginFeature1Description" maxlength="120" show-word-limit /></el-form-item>
        </div>
        <div class="feature-editor">
          <el-form-item label="功能 2 标题"><el-input v-model="form.loginFeature2Title" maxlength="60" show-word-limit /></el-form-item>
          <el-form-item label="功能 2 说明"><el-input v-model="form.loginFeature2Description" maxlength="120" show-word-limit /></el-form-item>
        </div>
        <div class="feature-editor">
          <el-form-item label="功能 3 标题"><el-input v-model="form.loginFeature3Title" maxlength="60" show-word-limit /></el-form-item>
          <el-form-item label="功能 3 说明"><el-input v-model="form.loginFeature3Description" maxlength="120" show-word-limit /></el-form-item>
        </div>
      </section>

      <section class="copy-card">
        <header><strong>登录区域</strong><span>账号输入区域上方与下方说明</span></header>
        <div class="field-grid">
          <el-form-item label="登录区英文标题"><el-input v-model="form.loginFormEyebrow" maxlength="80" show-word-limit /></el-form-item>
          <el-form-item label="登录区主标题"><el-input v-model="form.loginFormTitle" maxlength="80" show-word-limit /></el-form-item>
        </div>
        <el-form-item label="登录区说明"><el-input v-model="form.loginFormDescription" maxlength="160" show-word-limit /></el-form-item>
        <el-form-item label="管理员提示"><el-input v-model="form.loginHelpText" type="textarea" :rows="3" maxlength="240" show-word-limit /></el-form-item>
      </section>
    </el-form>
  </MrrPageShell>
</template>

<style scoped>
.copy-form { display: grid; gap: var(--mrr-space-5); margin-top: var(--mrr-space-5); }
.copy-card { padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.copy-card header { display: flex; gap: var(--mrr-space-3); align-items: baseline; margin-bottom: var(--mrr-space-5); }
.copy-card header strong { font-size: 15px; }
.copy-card header span { font-size: 12px; color: var(--mrr-muted-foreground); }
.field-grid, .feature-editor { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.feature-editor + .feature-editor { padding-top: var(--mrr-space-3); border-top: 1px solid var(--mrr-border); }
@media (max-width: 720px) { .field-grid, .feature-editor { grid-template-columns: 1fr; gap: 0; } }
</style>

<script setup lang="ts">
import type { AuthProfile } from '@/utils/auth-storage'
import { ElMessage } from 'element-plus'
import { Eye, EyeOff, KeyRound as Key, UserRound as UserFilled } from 'lucide-vue-next'
import { useForm } from 'vee-validate'
import { computed, ref } from 'vue'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import { readRememberedAccount, writeRememberedAccount } from '@/utils/auth-storage'
import { toAccountSchema } from './account-schema'

defineOptions({ name: 'LoginForm' })

const props = defineProps<{
  account?: string
}>()

const emits = defineEmits<{
  onLogin: [account?: string]
  onRegister: [account?: string]
  onResetPassword: [account?: string]
}>()

const userStore = useUserStore()
const loading = ref(false)
const showPassword = ref(false)
const buttonText = computed(() => loading.value ? '正在验证...' : '登录系统')
const rememberedAccount = readRememberedAccount()

function resolveMessage(payload: any, fallback: string): string {
  return payload?.message || payload?.msg || payload?.error || payload?.data?.message || payload?.data?.msg || fallback
}

const form = useForm({
  validationSchema: toAccountSchema(z.object({
    account: z.string().trim().min(1, '请输入用户名'),
    password: z.string().min(1, '请输入密码'),
    remember: z.boolean(),
  })),
  initialValues: {
    account: props.account ?? rememberedAccount,
    password: '',
    remember: Boolean(rememberedAccount),
  },
})

const onSubmit = form.handleSubmit(async (values) => {
  loading.value = true
  try {
    const res = await apiUser.login({ account: values.account.trim(), password: values.password })
    const payload = res.data || {}
    const loginData = payload.data || payload
    const token = loginData?.token || loginData?.accessToken || loginData?.jwt

    if (!token) {
      ElMessage.error(resolveMessage(payload, '登录失败，请检查账号信息'))
      return
    }

    userStore.setSession({
      token,
      user: (loginData?.user || loginData?.profile || payload?.user || {}) as AuthProfile,
    })
    writeRememberedAccount(values.remember ? values.account : '')
    ElMessage.success('登录成功')
    emits('onLogin', values.account.trim())
  }
  catch (error: any) {
    console.error('Login failed:', error)
    ElMessage({
      message: resolveMessage(error.response?.data, '登录失败，请重试'),
      type: 'error',
      grouping: true,
      offset: 72,
    })
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <form class="login-form" @submit="onSubmit">
    <aside class="login-notice" aria-label="登录须知">
      <span class="login-notice__icon"><FaIcon aria-hidden="true" name="i-lucide:lock-keyhole" /></span>
      <div>
        <strong>内网专用系统</strong>
        <p>请勿外借账号，所有操作将被记录用于安全审计。</p>
      </div>
    </aside>

    <section class="login-fields-card" aria-labelledby="login-fields-title">
      <header class="login-fields-card__heading">
        <span class="login-fields-card__icon"><FaIcon aria-hidden="true" name="i-lucide:shield-check" /></span>
        <div>
          <h2 id="login-fields-title">
            账号信息
          </h2>
          <p>输入系统账号和密码进行登录。</p>
        </div>
      </header>

      <div class="login-fields-card__body">
        <FormField v-slot="{ componentField, errors }" name="account">
          <FormItem class="field-item space-y-0">
            <div class="field-label-row">
              <label for="login-account">用户名</label>
              <FormMessage class="field-message" />
            </div>
            <FormControl>
              <div class="input-shell" :class="{ invalid: errors.length > 0 }">
                <span class="input-icon">
                  <el-icon><UserFilled /></el-icon>
                </span>
                <el-input
                  id="login-account"
                  type="text"
                  placeholder="请输入系统用户名"
                  autocomplete="username"
                  class="w-full"
                  :aria-invalid="errors.length > 0"
                  v-bind="componentField"
                />
              </div>
            </FormControl>
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField, errors }" name="password">
          <FormItem class="field-item space-y-0">
            <div class="field-label-row">
              <label for="login-password">密码</label>
              <FormMessage class="field-message" />
            </div>
            <FormControl>
              <div class="input-shell password-shell" :class="{ invalid: errors.length > 0 }">
                <span class="input-icon">
                  <el-icon><Key /></el-icon>
                </span>
                <el-input
                  id="login-password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入登录密码"
                  autocomplete="current-password"
                  class="w-full"
                  :aria-invalid="errors.length > 0"
                  v-bind="componentField"
                />
                <button
                  type="button"
                  class="password-toggle"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <Eye v-if="!showPassword" class="size-4" />
                  <EyeOff v-else class="size-4" />
                </button>
              </div>
            </FormControl>
          </FormItem>
        </FormField>

        <div class="form-options">
          <div class="remember-control">
            <FormField v-slot="{ value, setValue, handleBlur }" name="remember">
              <FormItem>
                <FormControl>
                  <el-checkbox class="remember-checkbox" name="remember" :model-value="value" @update:model-value="val => setValue(val === true)" @blur="handleBlur">
                    记住用户名
                  </el-checkbox>
                </FormControl>
              </FormItem>
            </FormField>
          </div>
          <span>密码问题请联系系统管理员</span>
        </div>
      </div>
    </section>

    <el-button
      :auto-insert-space="false"
      :loading="loading"
      size="large"
      class="login-submit-btn"
      native-type="submit" type="primary"
    >
      <FaIcon v-if="!loading" name="i-lucide:log-in" />
      {{ buttonText }}
    </el-button>
  </form>
</template>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-3);
  min-height: var(--auth-form-height, 0);
  margin-top: 0;
}

/*
 * 登录须知卡：与注册表单的审核流程卡（review-flow）同一套配方——info 色浅底、
 * 40px 图标对齐「20 + 2 + 18」两行文案。它吸收登录表单富余的垂直空间，
 * 消除切换器与账号信息卡之间的大片空白。
 */
.login-notice {
  display: flex;
  flex-shrink: 0;
  gap: var(--mrr-space-3);
  align-items: flex-start;
  padding: var(--mrr-space-4);
  color: var(--mrr-foreground);
  background: color-mix(in srgb, var(--color-info) 7%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--color-info) 22%, var(--mrr-border));
  border-radius: var(--mrr-radius-lg);
}

.login-notice strong {
  display: block;
  font-size: 14px;
  line-height: 20px;
}

.login-notice p {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--mrr-muted-foreground);
}

.login-notice__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 40px;
  height: 40px;
  font-size: 18px;
  color: var(--color-info);
  background: color-mix(in srgb, var(--color-info) 11%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--color-info) 20%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
}

.login-fields-card {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-4);
  background: color-mix(in srgb, var(--mrr-muted) 42%, var(--mrr-card));
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.login-fields-card__heading {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: flex-start;
  padding-bottom: var(--mrr-space-3);
  border-bottom: 1px solid var(--mrr-border);
}

/*
 * 图标盒子与右侧「标题 + 副标题」两行文案同高（20 + 2 + 18 = 40），
 * 因此两行文案必须写死行高，否则字号一动图标就不再对齐。
 */
.login-fields-card__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 40px;
  height: 40px;

  /* FaIcon 走 UnoCSS 分支时外层是 size-[1em]，图标实际大小由 font-size 决定（写 svg 选择器无效）。 */
  font-size: 20px;
  color: var(--mrr-primary);
  background: color-mix(in srgb, var(--mrr-primary) 9%, var(--mrr-card));
  border: 1px solid var(--el-color-primary-light-7);
  border-color: color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
}

.login-fields-card__heading h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  color: var(--mrr-foreground);
}

.login-fields-card__heading p {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--mrr-muted-foreground);
}

.login-fields-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-3);
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-1);
}

.field-item label {
  font-size: 13px;
  font-weight: 650;
  line-height: 18px;
  color: var(--mrr-foreground);
}

.field-label-row {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
  min-height: 18px;
}

.input-shell {
  position: relative;
  display: flex;
  align-items: center;
  border-radius: var(--mrr-control-radius);
}

.input-icon {
  position: absolute;
  left: 14px;
  z-index: 2;
  display: grid;
  place-items: center;
  color: var(--mrr-muted-foreground);
  pointer-events: none;
}

/* One field surface, with the original login-specific icon gutters. */
.input-shell :deep(.el-input__wrapper) {
  box-sizing: border-box;
  height: var(--auth-input-height, var(--mrr-input-height));
  min-height: var(--auth-input-height, var(--mrr-input-height));
  padding: 0 14px 0 42px;
  background: var(--mrr-control-bg);
  border-radius: var(--mrr-control-radius);
  box-shadow: 0 0 0 1px var(--mrr-control-border) inset;
}

.input-shell :deep(.el-input__inner) {
  height: 100%;
  padding: 0;
  font-size: 14px;
  color: var(--mrr-foreground);
  background: transparent;
}

.password-shell :deep(.el-input__wrapper) {
  padding-right: 44px;
}

.input-shell:focus-within :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--mrr-ring) inset, var(--mrr-focus-ring);
}

.input-shell.invalid :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--mrr-destructive) inset;
}

.password-toggle {
  position: absolute;
  right: 12px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--mrr-muted-foreground);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
}

.password-toggle:hover,
.password-toggle:focus-visible {
  color: var(--mrr-foreground);
  outline: none;
  background: var(--mrr-muted);
}

.field-message {
  margin: 0;
  font-size: 11px;
  line-height: 1.4;
  text-align: right;
  white-space: nowrap;
}

.form-options {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

.form-options :deep(.form-item) {
  margin: 0;
}

.remember-control {
  display: grid;
  gap: var(--mrr-space-1);
}

/* Preserve the existing compact, rounded login checkbox; business checkboxes
 * continue to use the shared default appearance. */
.remember-checkbox {
  display: flex;
  gap: 8px;
  align-items: center;
  width: fit-content;
  height: 20px;
  margin: 0;
  font-weight: 400;
  color: var(--mrr-muted-foreground);
}

.remember-control .remember-checkbox :deep(.el-checkbox__inner) {
  width: 16px;
  height: 16px;
  font-size: 12px;
  font-weight: 400;
  color: var(--mrr-muted-foreground);
  background: transparent;
  border-color: hsl(var(--primary));
  border-radius: 8px;
}

.remember-control .remember-checkbox :deep(.is-checked .el-checkbox__inner) {
  color: hsl(var(--primary-foreground));
  background: hsl(var(--primary));
  border-color: hsl(var(--primary));
}

.remember-checkbox :deep(.el-checkbox__inner::after) {
  border-color: hsl(var(--primary-foreground));
}

.remember-checkbox :deep(.el-checkbox__input) {
  display: flex;
  align-items: center;
  line-height: 1;
}

.remember-checkbox :deep(.el-checkbox__label) {
  padding-left: 0;
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;
  color: var(--mrr-muted-foreground);
}

/*
 * 登录面板的输入框用局部 --auth-input-height（38px），提交按钮与它们同高；
 * 注册面板的提交按钮必须写同一个值，否则切换时两个主按钮的顶边相差 5px
 * （e2e/login-switch-spacing 会逐帧比对这条基线）。
 */
.login-submit-btn {
  flex-shrink: 0;
  width: 100%;
  height: var(--auth-input-height, var(--mrr-control-height));
  margin-top: auto;
  border-radius: var(--mrr-control-radius);
}

/*
 * 窄屏没有「平分留白」的等高空间，间距必须显式成节奏：
 * 标签贴控件 8px，字段组之间 16px，卡片与主按钮之间同为 16px。
 */
@media (max-width: 900px) {
  .login-form {
    gap: var(--mrr-space-4);
  }

  .login-fields-card {
    gap: var(--mrr-space-4);
  }

  .login-fields-card__body {
    gap: var(--mrr-space-4);
  }

  .field-item {
    gap: var(--mrr-space-2);
  }
}

@media (min-width: 901px) {
  /*
   * 外壳高度由内容更高的注册表单决定（login.vue 固定 628px），登录表单内容更矮，
   * 富余的垂直空间由顶部的「登录须知」卡吸收，卡内间隙保持固定节奏（标签贴控件
   * 8px、字段组之间 16px），元素之间统一 16px；少量残余交给提交按钮的
   * margin-top: auto（按钮仍贴底，与注册按钮基线对齐，见切换动画 e2e）。
   */
  .login-form {
    gap: var(--mrr-space-4);
  }

  .login-fields-card {
    gap: var(--mrr-space-4);
  }

  .login-fields-card__body {
    gap: var(--mrr-space-4);
  }

  .field-item {
    gap: var(--mrr-space-2);
  }
}

@media (max-width: 520px) {
  .form-options {
    flex-direction: column;
    gap: var(--mrr-space-2);
    align-items: flex-start;
  }
}

/* Chrome 86 回退：登录字段分组卡与登录须知卡都是实色分层底，失效后与页面底色融为一体。 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  .login-fields-card {
    background: var(--mrr-card);
  }

  .login-fields-card__icon {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-7);
  }

  .login-notice {
    background: var(--mrr-tone-info-soft);
    border-color: var(--mrr-tone-info-border);
  }

  .login-notice__icon {
    background: var(--mrr-tone-info-soft);
    border-color: var(--mrr-tone-info-border);
  }
}
</style>

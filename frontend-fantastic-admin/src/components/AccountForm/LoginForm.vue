<script setup lang="ts">
import type { AuthProfile } from '@/utils/auth-storage'
import { toTypedSchema } from '@vee-validate/zod'
import { ElMessage } from 'element-plus'
import { Eye, EyeOff, KeyRound as Key, UserRound as UserFilled } from 'lucide-vue-next'
import { useForm } from 'vee-validate'
import { computed, ref } from 'vue'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import { readRememberedAccount, writeRememberedAccount } from '@/utils/auth-storage'
import LoginSlideCheck from './LoginSlideCheck.vue'

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
const slideComplete = ref(false)
const slideRequired = ref(false)
const buttonText = computed(() => loading.value ? '正在验证...' : '登录系统')
const rememberedAccount = readRememberedAccount()

function resolveMessage(payload: any, fallback: string): string {
  return payload?.message || payload?.msg || payload?.error || payload?.data?.message || payload?.data?.msg || fallback
}

const form = useForm({
  validationSchema: toTypedSchema(z.object({
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
  if (!slideComplete.value) {
    slideRequired.value = true
    return
  }
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
    slideComplete.value = false
  }
})
</script>

<template>
  <form class="login-form" @submit="onSubmit">
    <section class="login-fields-card" aria-labelledby="login-fields-title">
      <header class="login-fields-card__heading">
        <span class="login-fields-card__icon"><FaIcon aria-hidden="true" name="i-lucide:key-round" /></span>
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
                <FaInput
                  id="login-account"
                  type="text"
                  placeholder="请输入系统用户名"
                  autocomplete="username"
                  class="w-full"
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
                <FaInput
                  id="login-password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入登录密码"
                  autocomplete="current-password"
                  class="w-full"
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
        <LoginSlideCheck
          v-model="slideComplete"
          :disabled="loading"
          :invalid="slideRequired"
          @update:model-value="slideRequired = false"
        />

        <div class="form-options">
          <div class="remember-control">
            <FormField v-slot="{ componentField }" type="checkbox" name="remember">
              <FormItem>
                <FormControl>
                  <FaCheckbox v-bind="componentField">
                    记住用户名
                  </FaCheckbox>
                </FormControl>
              </FormItem>
            </FormField>
          </div>
          <span>密码问题请联系系统管理员</span>
        </div>
      </div>
    </section>

    <FaButton
      :loading="loading"
      size="lg"
      class="login-submit-btn"
      type="submit"
    >
      <FaIcon v-if="!loading" name="i-lucide:log-in" />
      {{ buttonText }}
    </FaButton>
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

.login-fields-card {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-4);
  background: var(--mrr-card);
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

.login-fields-card__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--mrr-primary);
  background: var(--el-color-primary-light-9);
  background: color-mix(in srgb, var(--mrr-primary) 9%, var(--mrr-card));
  border: 1px solid var(--el-color-primary-light-7);
  border-color: color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
}

.login-fields-card__icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.login-fields-card__heading h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--mrr-foreground);
}

.login-fields-card__heading p {
  margin: var(--mrr-space-1) 0 0;
  font-size: 11px;
  line-height: 1.5;
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

.input-shell :deep(.fa-input__wrapper),
.input-shell :deep(input) {
  width: 100%;
  height: var(--auth-input-height, var(--mrr-input-height));
  padding-right: 14px;
  padding-left: 42px;
  font-size: 14px;
  color: var(--mrr-foreground);
  background: var(--mrr-control-bg);
  border-color: var(--mrr-control-border);
  border-radius: var(--mrr-control-radius);
  box-shadow: none;
}

.password-shell :deep(input) {
  padding-right: 44px;
}

.input-shell:focus-within :deep(.fa-input__wrapper),
.input-shell:focus-within :deep(input) {
  border-color: var(--mrr-ring);
  box-shadow: var(--mrr-focus-ring);
}

.input-shell.invalid :deep(.fa-input__wrapper),
.input-shell.invalid :deep(input) {
  border-color: var(--mrr-destructive);
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

.login-submit-btn {
  flex-shrink: 0;
  width: 100%;
  height: var(--mrr-control-height);
  margin-top: auto;
  border-radius: var(--mrr-control-radius);
}

@media (min-width: 901px) {
  .login-form {
    gap: var(--mrr-space-4);
  }

  .login-fields-card {
    display: grid;
    flex-grow: 1;
    grid-template-columns: minmax(0, 1fr);
    grid-auto-rows: max-content;
    gap: var(--mrr-space-2);
    align-content: space-between;
  }

  .login-fields-card__body,
  .field-item,
  .login-fields-card__body > :deep(.slide-check) {
    display: contents;
  }

  .form-options {
    margin-top: 0;
  }

  .login-submit-btn {
    margin-top: 0;
  }
}

@media (max-width: 520px) {
  .form-options {
    flex-direction: column;
    gap: var(--mrr-space-2);
    align-items: flex-start;
  }
}
</style>

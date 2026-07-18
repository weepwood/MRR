<script setup lang="ts">
import { Key, UserFilled } from '@element-plus/icons-vue'
import { Eye, EyeOff } from 'lucide-vue-next'
import { toTypedSchema } from '@vee-validate/zod'
import { ElMessage } from 'element-plus'
import { useForm } from 'vee-validate'
import { computed, ref } from 'vue'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'

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
    account: props.account ?? localStorage.getItem('login_account') ?? '',
    password: '',
    remember: !!localStorage.getItem('login_account'),
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
      user: loginData?.user || loginData?.profile || payload?.user || {},
    })
    if (values.remember) {
      localStorage.setItem('login_account', values.account.trim())
    }
    else {
      localStorage.removeItem('login_account')
    }
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
    <FormField v-slot="{ componentField, errors }" name="account">
      <FormItem class="field-item">
        <label for="login-account">用户名</label>
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
        <FormMessage class="field-message" />
      </FormItem>
    </FormField>

    <FormField v-slot="{ componentField, errors }" name="password">
      <FormItem class="field-item">
        <label for="login-password">密码</label>
        <FormControl>
          <div class="input-shell" :class="{ invalid: errors.length > 0 }">
            <span class="input-icon">
              <el-icon><Key /></el-icon>
            </span>
            <FaInput
              id="login-password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入登录密码"
              autocomplete="current-password"
              class="w-full password-input"
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
        <FormMessage class="field-message" />
      </FormItem>
    </FormField>

    <div class="form-options">
      <FormField v-slot="{ componentField }" type="checkbox" name="remember">
        <FormItem>
          <FormControl>
            <FaCheckbox v-bind="componentField">
              记住用户名
            </FaCheckbox>
          </FormControl>
        </FormItem>
      </FormField>
      <span>密码问题请联系系统管理员</span>
    </div>

    <FaButton
      :loading="loading"
      size="lg"
      class="login-submit-btn"
      type="submit"
    >
      <FaIcon v-if="!loading" name="i-ri:login-box-line" />
      {{ buttonText }}
    </FaButton>
  </form>
</template>

<style scoped>
.login-form {
  display: grid;
  gap: var(--mrr-space-4);
  margin-top: var(--mrr-space-6);
}

.field-item {
  position: relative;
  display: grid;
  gap: var(--mrr-space-2);
  padding-bottom: var(--mrr-space-3);
}

.field-item label {
  font-size: 13px;
  font-weight: 650;
  color: var(--mrr-foreground);
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
  color: var(--mrr-muted-foreground);
  pointer-events: none;
  place-items: center;
}

.input-shell :deep(.fa-input__wrapper),
.input-shell :deep(input) {
  width: 100%;
  height: 44px;
  padding-right: 14px;
  padding-left: 42px;
  font-size: 14px;
  color: var(--mrr-foreground);
  background: var(--mrr-control-bg);
  border-color: var(--mrr-control-border);
  border-radius: var(--mrr-control-radius);
  box-shadow: none;
}

.input-shell :deep(.password-input input),
.input-shell :deep(input[type='password']) {
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
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--mrr-muted-foreground);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
  place-items: center;
}

.password-toggle:hover,
.password-toggle:focus-visible {
  color: var(--mrr-foreground);
  background: var(--mrr-muted);
  outline: none;
}

.field-message {
  position: absolute;
  bottom: -2px;
  left: 0;
  font-size: 11px;
}

.form-options {
  display: flex;
  gap: var(--mrr-space-4);
  align-items: center;
  justify-content: space-between;
  min-height: 28px;
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

.form-options :deep(.form-item) {
  margin: 0;
}

.login-submit-btn {
  width: 100%;
  height: 44px;
  margin-top: var(--mrr-space-1);
  border-radius: var(--mrr-control-radius);
}

@media (max-width: 520px) {
  .form-options {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--mrr-space-2);
  }
}
</style>

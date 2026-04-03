<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import gsap from 'gsap'
import { UserFilled, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import apiUser from '@/api/modules/user'
import { FormControl, FormField, FormItem } from '@/ui/shadcn/ui/form'

defineOptions({
  name: 'LoginForm',
})

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
const buttonText = computed(() => loading.value ? '登录中...' : '登录')

const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false

function resolveMessage(payload: any, fallback: string): string {
  return payload?.message || payload?.msg || payload?.error || payload?.data?.message || payload?.data?.msg || fallback
}

const form = useForm({
  validationSchema: toTypedSchema(z.object({
    account: z.string().min(1, '请输入用户名'),
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
  if (!prefersReducedMotion) {
    gsap.to('.login-form-inner', { opacity: 0.88, duration: 0.18, ease: 'power1.out' })
  }
  try {
    const res = await apiUser.login({ account: values.account, password: values.password })
    const payload = res.data || {}
    const loginData = payload.data || payload
    const token = loginData?.token || loginData?.accessToken || loginData?.jwt

    if (token) {
      userStore.setSession({
        token,
        user: loginData?.user || loginData?.profile || payload?.user || {},
      })
      if (values.remember) {
        localStorage.setItem('login_account', values.account)
      }
      else {
        localStorage.removeItem('login_account')
      }
      ElMessage({ message: '登录成功，欢迎回来！', type: 'success', position: 'top-right', offset: 90 })
      emits('onLogin', values.account)
    }
    else {
      const msg = resolveMessage(payload, '登录失败，请检查账号信息')
      ElMessage({ message: msg, type: 'error', position: 'top-right', offset: 90 })
      animateFailure()
    }
  }
  catch (err: any) {
    console.error('Login failed:', err)
    const msg = resolveMessage(err.response?.data, '登录失败，请重试')
    ElMessage({ message: msg, type: 'error', position: 'top-right', offset: 90 })
    animateFailure()
  }
  finally {
    loading.value = false
    if (!prefersReducedMotion) {
      gsap.to('.login-form-inner', { opacity: 1, duration: 0.2, ease: 'power1.inOut' })
    }
  }
})

function animateFailure() {
  if (prefersReducedMotion) return
  const tl = gsap.timeline({ defaults: { duration: 0.06, ease: 'power1.inOut' } })
  tl.to('.login-form-inner', { x: -10 })
    .to('.login-form-inner', { x: 10 })
    .to('.login-form-inner', { x: -8 })
    .to('.login-form-inner', { x: 8 })
    .to('.login-form-inner', { x: 0 })
}

function animateButtonHover(isHovering: boolean) {
  const btn = document.querySelector('.login-submit-btn') as HTMLElement | null
  if (!btn) return
  gsap.to(btn, {
    scale: isHovering ? 1.04 : 1,
    boxShadow: isHovering ? '0 12px 24px rgba(47, 111, 255, 0.25)' : '0 8px 18px rgba(47, 111, 255, 0.12)',
    duration: 0.25,
    ease: 'power2.out',
  })
}

watch(loading, (isLoading) => {
  if (prefersReducedMotion) return
  gsap.fromTo(
    '.login-form-inner',
    { opacity: 0.6, scale: 0.97 },
    { opacity: 1, scale: 1, duration: 0.35, ease: 'power1.inOut' },
  )
})
</script>

<template>
  <div class="login-form-inner">
    <form class="login-form" @submit="onSubmit">
      <FormField v-slot="{ componentField }" name="account">
        <label for="login-account">用户名</label>
        <div class="input-shell">
          <span class="input-icon">
            <el-icon><UserFilled /></el-icon>
          </span>
          <FaInput
            id="login-account"
            type="text"
            placeholder="Enter your username"
            autocomplete="username"
            class="w-full"
            v-bind="componentField"
          />
        </div>
      </FormField>
      <FormField v-slot="{ componentField }" name="password">
        <label for="login-password">密码</label>
        <div class="input-shell">
          <span class="input-icon">
            <el-icon><Key /></el-icon>
          </span>
          <FaInput
            id="login-password"
            type="password"
            placeholder="Enter your password"
            autocomplete="current-password"
            class="w-full"
            v-bind="componentField"
          />
        </div>
      </FormField>

      <div class="mb-2 flex-center-start">
        <FormField v-slot="{ componentField }" type="checkbox" name="remember">
          <FormItem>
            <FormControl>
              <FaCheckbox v-bind="componentField">
                记住我
              </FaCheckbox>
            </FormControl>
          </FormItem>
        </FormField>
      </div>

      <button
        class="login-submit-btn"
        type="submit"
        :disabled="loading"
        @mouseenter="animateButtonHover(true)"
        @mouseleave="animateButtonHover(false)"
      >
        {{ buttonText }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.login-form-inner {
  min-height: 300px;
}

.login-form {
  margin-top: 24px;
  display: grid;
  gap: 12px;
}

.login-form label {
  font-size: 13px;
  color: var(--el-text-color-secondary, hsl(var(--muted-foreground)));
  font-weight: 600;
}

.input-shell {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  display: grid;
  place-items: center;
  color: #6d7d96;
  z-index: 1;
}

.input-shell :deep(.fa-input__wrapper),
.input-shell :deep(input) {
  width: 100%;
  height: 46px;
  border: 1px solid #d7e4fa;
  background: #ffffff;
  border-radius: 12px;
  padding: 0 14px 0 42px;
  font-size: 14px;
  color: var(--el-text-color-primary);
  transition:
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

[data-mode="dark"] .input-shell :deep(.fa-input__wrapper),
[data-mode="dark"] .input-shell :deep(input) {
  border-color: hsl(var(--border));
  background: hsl(var(--background));
  color: var(--el-text-color-primary);
}

.input-shell :deep(.fa-input__wrapper):focus-within,
.input-shell :deep(input:focus) {
  outline: none;
  border-color: #2f6fff;
  box-shadow: 0 0 0 4px rgba(47, 111, 255, 0.14);
}

.login-submit-btn {
  margin-top: 8px;
  height: 46px;
  border: none;
  border-radius: 12px;
  background: #2f6fff;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 0.25s ease,
    background-color 0.25s ease,
    opacity 0.25s ease;
  box-shadow: 0 8px 18px rgba(47, 111, 255, 0.12);
}

.login-submit-btn:hover:not(:disabled) {
  background: #1e54d6;
  transform: translateY(-1px);
}

.login-submit-btn:disabled {
  cursor: not-allowed;
  opacity: 0.72;
  pointer-events: none;
}
</style>

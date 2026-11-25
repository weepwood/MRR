<script setup lang="ts">
import { useForm } from 'vee-validate'
import * as z from 'zod'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import { toAccountSchema } from './account-schema'

defineOptions({
  name: 'ResetPasswordForm',
})

const props = defineProps<{
  account?: string
}>()

const emits = defineEmits<{
  onLogin: [account?: string]
  onResetPassword: [account?: string]
}>()

const loading = ref(false)

const form = useForm({
  validationSchema: toAccountSchema(z.object({
    account: z.string().min(1, '请输入用户名'),
    captcha: z.string().min(6, '请输入验证码'),
    newPassword: z.string().min(1, '请输入新密码').min(6, '密码长度为6到64位').max(64, '密码长度为6到64位'),
  })),
  initialValues: {
    account: props.account ?? '',
    captcha: '',
    newPassword: '',
  },
})
const onSubmit = form.handleSubmit((values) => {
  loading.value = true
  emits('onResetPassword', values.account)
})

const countdown = ref(0)
const countdownInterval = ref(Number.NaN)
function handleSendCaptcha() {
  countdown.value = 60
  countdownInterval.value = window.setInterval(() => {
    countdown.value--
    if (countdown.value === 0) {
      clearInterval(countdownInterval.value)
    }
  }, 1000)
}
</script>

<template>
  <div class="min-h-500px w-full flex-col-stretch-center p-12">
    <form @submit="onSubmit">
      <div class="mb-8 space-y-2">
        <h3 class="text-4xl color-[var(--el-text-color-primary)] font-bold">
          忘记密码了? 🔒
        </h3>
        <p class="text-sm text-muted-foreground lg:text-base">
          演示系统未提供该功能
        </p>
      </div>
      <FormField v-slot="{ componentField, errors }" name="account">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <el-input type="text" placeholder="请输入用户名" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <div class="flex-start-between gap-2">
        <FormField v-slot="{ componentField, value, setValue }" name="captcha">
          <FormItem class="relative pb-6 space-y-0">
            <FormControl>
              <FaPinInput :model-value="value" :name="componentField.name" :length="6" class="border-destructive" @update:model-value="val => setValue(val)" />
            </FormControl>
            <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
              <FormMessage class="absolute bottom-1 text-xs" />
            </Transition>
          </FormItem>
        </FormField>
        <el-button size="large" :auto-insert-space="false" :disabled="countdown > 0" class="mrr-button-flow flex-1 px-4" @click="handleSendCaptcha">
          {{ countdown === 0 ? '发送验证码' : `${countdown} 秒后可重新发送` }}
        </el-button>
      </div>
      <FormField v-slot="{ componentField, errors }" name="newPassword">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <el-input type="password" placeholder="请输入 6 到 64 位新密码" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <el-button size="large" type="primary" :auto-insert-space="false" :loading="loading" :aria-busy="(loading) || undefined" class="mrr-button-flow mt-4 w-full" native-type="submit">
        <template #loading>
          <FaIcon name="i-lucide:loader-circle" class="animate-spin" aria-hidden="true" />
        </template>
        确认
      </el-button>
      <div class="mt-4 flex-center gap-2 text-sm color-[var(--el-text-color-secondary)]">
        <el-button link type="primary" :auto-insert-space="false" class="mrr-button-flow p-0" @click="emits('onLogin', form.values.account)">
          去登录
        </el-button>
      </div>
    </form>
  </div>
</template>

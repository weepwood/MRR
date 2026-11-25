<script setup lang="ts">
import { useForm } from 'vee-validate'
import * as z from 'zod'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import { notify } from '@/utils/notification'
import { toAccountSchema } from './account-schema'

defineOptions({
  name: 'EditPasswordForm',
})

const userStore = useUserStore()

const loading = ref(false)

const form = useForm({
  validationSchema: toAccountSchema(
    z.object({
      password: z.string().min(1, '请输入原密码'),
      newPassword: z.string().min(6, '密码长度为 6 到 64 位').max(64, '密码长度为 6 到 64 位'),
      checkPassword: z.string().min(1, '请确认新密码'),
    }).refine(data => data.newPassword === data.checkPassword, {
      message: '两次输入的密码不一致',
      path: ['checkPassword'],
    }).refine(data => data.password !== data.newPassword, {
      message: '新密码不能与原密码相同',
      path: ['newPassword'],
    }),
  ),
  initialValues: {
    password: '',
    newPassword: '',
    checkPassword: '',
  },
})
const submitValidated = form.handleSubmit(async (values) => {
  loading.value = true
  try {
    await userStore.editPassword(values)
    notify.success('密码修改成功，请使用新密码重新登录')
    await userStore.requestLogout()
  }
  finally {
    loading.value = false
  }
})

// Guard the whole validation/request lifecycle, including repeated Enter before
// the loading button has rendered.
let submitting = false
async function onSubmit(event: SubmitEvent) {
  event.preventDefault()
  if (submitting) { return }
  submitting = true
  try {
    await submitValidated(event)
  }
  finally {
    submitting = false
  }
}
</script>

<template>
  <div class="w-full flex-col-stretch-center">
    <div class="mb-6 space-y-2">
      <h3 class="text-4xl color-[var(--el-text-color-primary)] font-bold">
        修改密码
      </h3>
      <p class="text-sm text-muted-foreground lg:text-base">
        请输入原密码、新密码和确认密码。新密码长度应为 6 到 64 位，建议使用更长且容易记忆的口令。
      </p>
    </div>
    <form @submit="onSubmit">
      <FormField v-slot="{ componentField, errors }" name="password">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <el-input type="password" autocomplete="current-password" placeholder="请输入原密码" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="newPassword">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <el-input type="password" autocomplete="new-password" placeholder="请输入 6 到 64 位新密码" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="checkPassword">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <el-input type="password" autocomplete="new-password" placeholder="请确认新密码" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <el-button :auto-insert-space="false" :loading="loading" size="large" class="mt-8 w-full" native-type="submit" type="primary">
        保存
      </el-button>
    </form>
  </div>
</template>

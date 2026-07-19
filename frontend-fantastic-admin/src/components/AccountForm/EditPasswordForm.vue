<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { toast } from 'vue-sonner'
import * as z from 'zod'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'

defineOptions({
  name: 'EditPasswordForm',
})

const userStore = useUserStore()

const loading = ref(false)

const form = useForm({
  validationSchema: toTypedSchema(
    z.object({
      password: z.string().min(1, '请输入原密码'),
      newPassword: z.string().min(12, '密码长度为 12 到 64 位').max(64, '密码长度为 12 到 64 位'),
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
const onSubmit = form.handleSubmit(async (values) => {
  loading.value = true
  try {
    await userStore.editPassword(values)
    toast.success('密码修改成功，请使用新密码重新登录')
    await userStore.requestLogout()
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="w-full flex-col-stretch-center">
    <div class="mb-6 space-y-2">
      <h3 class="text-4xl color-[var(--el-text-color-primary)] font-bold">
        修改密码
      </h3>
      <p class="text-sm text-muted-foreground lg:text-base">
        请输入原密码、新密码和确认密码。新密码长度应为 12 到 64 位。
      </p>
    </div>
    <form @submit="onSubmit">
      <FormField v-slot="{ componentField, errors }" name="password">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <FaInput type="password" autocomplete="current-password" placeholder="请输入原密码" class="w-full" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="newPassword">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <FaInput type="password" autocomplete="new-password" placeholder="请输入 12 到 64 位新密码" class="w-full" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="checkPassword">
        <FormItem class="relative pb-6 space-y-0">
          <FormControl>
            <FaInput type="password" autocomplete="new-password" placeholder="请确认新密码" class="w-full" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="absolute bottom-1 text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <FaButton :loading="loading" size="lg" class="mt-8 w-full" type="submit">
        保存
      </FaButton>
    </form>
  </div>
</template>

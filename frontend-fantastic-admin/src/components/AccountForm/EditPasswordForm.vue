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
  <div class="password-settings">
    <div class="password-settings__heading">
      <h3 class="password-settings__title">
        修改密码
      </h3>
      <p class="password-settings__description">
        新密码长度为 6 到 64 位，建议使用较长且容易记忆的口令。
      </p>
    </div>
    <form @submit="onSubmit">
      <FormField v-slot="{ componentField, errors }" name="password">
        <FormItem class="password-settings__field space-y-0">
          <div class="password-settings__label">
            原密码
          </div>
          <FormControl>
            <el-input type="password" autocomplete="current-password" aria-label="原密码" placeholder="请输入原密码" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="newPassword">
        <FormItem class="password-settings__field space-y-0">
          <div class="password-settings__label">
            新密码
          </div>
          <FormControl>
            <el-input type="password" autocomplete="new-password" aria-label="新密码" placeholder="请输入 6 到 64 位新密码" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="checkPassword">
        <FormItem class="password-settings__field space-y-0">
          <div class="password-settings__label">
            确认新密码
          </div>
          <FormControl>
            <el-input type="password" autocomplete="new-password" aria-label="确认新密码" placeholder="请确认新密码" class="w-full" :class="{ 'account-field-invalid': errors.length > 0 }" :aria-invalid="errors.length > 0" v-bind="componentField" />
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="text-xs" />
          </Transition>
        </FormItem>
      </FormField>
      <div class="password-settings__actions">
        <el-button :auto-insert-space="false" :loading="loading" native-type="submit" type="primary">
          保存
        </el-button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.password-settings {
  display: grid;
  gap: var(--mrr-space-4);
  min-width: 0;
}

.password-settings__heading {
  display: grid;
  gap: var(--mrr-space-1);
}

.password-settings__title {
  margin: 0;
  font-size: var(--mrr-font-size-lg);
  font-weight: 600;
}

.password-settings__description {
  margin: 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.password-settings form {
  display: grid;
  gap: var(--mrr-space-3);
}

.password-settings__field {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
}

.password-settings__label {
  font-size: var(--mrr-font-size-sm);
  font-weight: 500;
}

.password-settings__actions {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--mrr-space-3);
  border-top: 1px solid var(--mrr-border);
}

.password-settings__actions .el-button {
  min-width: 80px;
}
</style>

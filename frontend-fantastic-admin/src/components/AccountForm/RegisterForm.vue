<script setup lang="ts">
/* eslint-disable antfu/consistent-chaining, regexp/prefer-w, regexp/use-ignore-case */
import { toTypedSchema } from '@vee-validate/zod'
import { ElMessage } from 'element-plus'
import { Eye, EyeOff } from 'lucide-vue-next'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'

defineOptions({ name: 'RegisterForm' })

const props = defineProps<{
  account?: string
}>()

const emits = defineEmits<{
  onLogin: [account?: string]
  onRegister: [account?: string]
}>()

const loading = ref(false)
const showPwd = ref(false)
const showCheckPwd = ref(false)

function resolveMessage(payload: any, fallback: string): string {
  return payload?.message || payload?.msg || payload?.error || payload?.data?.message || payload?.data?.msg || fallback
}

const form = useForm({
  validationSchema: toTypedSchema(
    z.object({
      account: z.string().trim()
        .min(3, '用户名长度应为3到40位')
        .max(40, '用户名长度应为3到40位')
        .regex(/^[A-Za-z0-9._-]+$/, '只能包含字母、数字、点、下划线和短横线'),
      displayName: z.string().trim().min(1, '请输入显示名称').max(80, '显示名称不能超过80个字符'),
      contactInfo: z.string().trim().max(200, '联系方式不能超过200个字符').optional(),
      applyRemark: z.string().trim().max(500, '申请说明不能超过500个字符').optional(),
      password: z.string().min(12, '密码长度应为12到64位').max(64, '密码长度应为12到64位'),
      checkPassword: z.string().min(1, '请再次输入密码'),
    }).refine(data => data.password === data.checkPassword, {
      message: '两次输入的密码不一致',
      path: ['checkPassword'],
    }),
  ),
  initialValues: {
    account: props.account ?? '',
    displayName: '',
    contactInfo: '',
    applyRemark: '',
    password: '',
    checkPassword: '',
  },
})

const onSubmit = form.handleSubmit(async (values) => {
  loading.value = true
  try {
    await apiUser.register({
      username: values.account.trim(),
      password: values.password,
      displayName: values.displayName.trim(),
      contactInfo: values.contactInfo?.trim() || undefined,
      applyRemark: values.applyRemark?.trim() || undefined,
    })
    ElMessage({
      message: '注册申请已提交，请等待管理员审核。审核通过后即可登录。',
      type: 'success',
      duration: 5000,
    })
    emits('onRegister', values.account.trim())
  }
  catch (err: any) {
    console.error('Registration failed:', err)
    const msg = resolveMessage(err?.response?.data ?? err, '注册申请提交失败，请重试')
    ElMessage({ message: msg, type: 'error', grouping: true, offset: 90 })
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <form class="register-form" @submit="onSubmit">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="注册后账号处于待审核状态，管理员审核通过前无法登录。"
    />

    <div class="field-grid">
      <FormField v-slot="{ componentField, errors }" name="account">
        <FormItem class="field-item">
          <label for="register-account">用户名</label>
          <FormControl>
            <FaInput id="register-account" type="text" autocomplete="username" placeholder="例如 zhangsan" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>

      <FormField v-slot="{ componentField, errors }" name="displayName">
        <FormItem class="field-item">
          <label for="register-display-name">显示名称</label>
          <FormControl>
            <FaInput id="register-display-name" type="text" placeholder="例如 张三" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>
    </div>

    <FormField v-slot="{ componentField, errors }" name="contactInfo">
      <FormItem class="field-item">
        <label for="register-contact">联系方式（可选）</label>
        <FormControl>
          <FaInput id="register-contact" type="text" placeholder="手机号、工号、科室内线等" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
        </FormControl>
        <FormMessage class="field-message" />
      </FormItem>
    </FormField>

    <FormField v-slot="{ componentField, errors }" name="applyRemark">
      <FormItem class="field-item">
        <label for="register-remark">申请说明（可选）</label>
        <FormControl>
          <FaTextarea id="register-remark" :rows="3" placeholder="说明所在科室、岗位及使用目的，便于管理员审核" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
        </FormControl>
        <FormMessage class="field-message" />
      </FormItem>
    </FormField>

    <div class="field-grid">
      <FormField v-slot="{ componentField, value, errors }" name="password">
        <FormItem class="field-item">
          <label for="register-password">密码</label>
          <FormControl>
            <div class="password-shell">
              <FaInput id="register-password" :type="showPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="12到64位" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
              <button type="button" class="password-toggle" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
                <Eye v-if="!showPwd" class="size-4" />
                <EyeOff v-else class="size-4" />
              </button>
            </div>
          </FormControl>
          <FaPasswordStrength :password="value" class="mt-2" />
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>

      <FormField v-slot="{ componentField, errors }" name="checkPassword">
        <FormItem class="field-item">
          <label for="register-password-confirm">确认密码</label>
          <FormControl>
            <div class="password-shell">
              <FaInput id="register-password-confirm" :type="showCheckPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入密码" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
              <button type="button" class="password-toggle" :aria-label="showCheckPwd ? '隐藏密码' : '显示密码'" @click="showCheckPwd = !showCheckPwd">
                <Eye v-if="!showCheckPwd" class="size-4" />
                <EyeOff v-else class="size-4" />
              </button>
            </div>
          </FormControl>
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>
    </div>

    <FaButton :loading="loading" size="lg" class="w-full" type="submit">
      提交注册申请
    </FaButton>

    <div class="switch-link">
      <span>已有账号？</span>
      <FaButton type="button" variant="link" class="h-auto p-0" @click="emits('onLogin', form.values.account)">
        返回登录
      </FaButton>
    </div>
  </form>
</template>

<style scoped>
/* stylelint-disable @stylistic/selector-list-comma-newline-after, order/properties-order, at-rule-empty-line-before, media-feature-range-notation */
.register-form { display: grid; gap: var(--mrr-space-4); margin-top: var(--mrr-space-5); }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-3); }
.field-item { position: relative; display: grid; gap: var(--mrr-space-2); padding-bottom: var(--mrr-space-3); }
.field-item label { font-size: 13px; font-weight: 650; color: var(--mrr-foreground); }
.field-item :deep(.fa-input__wrapper), .field-item :deep(input) { width: 100%; }
.field-message { position: absolute; bottom: 0; font-size: 11px; }
.password-shell { position: relative; }
.password-shell :deep(input) { padding-right: 42px; }
.password-toggle { position: absolute; top: 50%; right: 12px; display: grid; padding: 4px; color: var(--mrr-muted-foreground); background: transparent; transform: translateY(-50%); place-items: center; }
.switch-link { display: flex; gap: var(--mrr-space-2); align-items: center; justify-content: center; font-size: 13px; color: var(--mrr-muted-foreground); }
@media (max-width: 620px) { .field-grid { grid-template-columns: 1fr; gap: 0; } }
</style>

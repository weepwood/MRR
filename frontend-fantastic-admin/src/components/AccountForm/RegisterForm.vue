<script setup lang="ts">
/* eslint-disable antfu/consistent-chaining, regexp/prefer-w, regexp/use-ignore-case */
import { toTypedSchema } from '@vee-validate/zod'
import { Eye, EyeOff } from 'lucide-vue-next'
import { useForm } from 'vee-validate'
import { nextTick } from 'vue'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import { notify } from '@/utils/notification'
import RegisterFieldLabel from './RegisterFieldLabel.vue'

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
const isApplicationStep = ref(false)

function resolveMessage(payload: any, fallback: string): string {
  return payload?.message || payload?.msg || payload?.error || payload?.data?.message || payload?.data?.msg || fallback
}

const form = useForm({
  keepValuesOnUnmount: true,
  validationSchema: toTypedSchema(
    z.object({
      account: z.string().trim()
        .min(3, '用户名长度应为 3 到 40 位')
        .max(40, '用户名长度应为 3 到 40 位')
        .regex(/^[A-Za-z0-9._-]+$/, '只能包含字母、数字、点、下划线和短横线'),
      displayName: z.string().trim().min(1, '请输入显示名称').max(80, '显示名称不能超过80个字符'),
      contactInfo: z.string().trim().max(200, '联系方式不能超过200个字符').optional(),
      applyRemark: z.string().trim().max(500, '申请说明不能超过500个字符').optional(),
      password: z.string().min(6, '密码长度应为 6 到 64 位').max(64, '密码长度应为 6 到 64 位'),
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
  if (!isApplicationStep.value) {
    isApplicationStep.value = true
    await nextTick()
    document.getElementById('register-contact')?.focus()
    return
  }

  loading.value = true
  try {
    await apiUser.register({
      username: values.account.trim(),
      password: values.password,
      displayName: values.displayName.trim(),
      contactInfo: values.contactInfo?.trim() || undefined,
      applyRemark: values.applyRemark?.trim() || undefined,
    })
    notify.success('注册申请已提交，请等待管理员审核。审核通过后即可登录。')
    emits('onRegister', values.account.trim())
  }
  catch (err: any) {
    console.error('Registration failed:', err)
    const msg = resolveMessage(err?.response?.data ?? err, '注册申请提交失败，请重试')
    notify.error(msg, { offset: 90 })
  }
  finally {
    loading.value = false
  }
})

async function handlePrevious() {
  if (isApplicationStep.value) {
    isApplicationStep.value = false
    await nextTick()
    document.getElementById('register-account')?.focus()
    return
  }

  emits('onLogin', form.values.account)
}
</script>

<template>
  <form class="register-form" @submit="onSubmit">
    <section class="review-flow" aria-label="账号申请流程">
      <div class="review-flow__summary">
        <span class="review-flow__icon"><FaIcon name="i-lucide:user-round-check" /></span>
        <div>
          <strong>账号需要管理员审核</strong>
          <p>提交后账号进入待审核状态，审核通过前无法登录系统。</p>
        </div>
      </div>
      <div class="review-flow__steps" aria-label="注册申请进度">
        <span :class="{ 'is-current': !isApplicationStep }" :aria-current="isApplicationStep ? undefined : 'step'"><b>1</b>身份信息</span>
        <FaIcon aria-hidden="true" name="i-lucide:chevron-right" />
        <span :class="{ 'is-current': isApplicationStep }" :aria-current="isApplicationStep ? 'step' : undefined"><b>2</b>申请说明</span>
        <FaIcon aria-hidden="true" name="i-lucide:chevron-right" />
        <span><b>3</b>管理员审核</span>
      </div>
    </section>

    <section v-if="!isApplicationStep" class="form-section" aria-label="注册账号信息">
      <div class="field-grid">
        <FormField v-slot="{ componentField, errors }" name="account">
          <FormItem class="field-item">
            <RegisterFieldLabel html-for="register-account" label="用户名" status="必填" description="3–40 位，可使用字母、数字、点、下划线和短横线" />
            <FormControl>
              <FaInput id="register-account" type="text" autocomplete="username" placeholder="例如 zhangsan" aria-required="true" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
            </FormControl>
            <FormMessage class="field-message" />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField, errors }" name="displayName">
          <FormItem class="field-item">
            <RegisterFieldLabel html-for="register-display-name" label="显示名称" status="必填" description="建议填写真实姓名，便于管理员确认身份" />
            <FormControl>
              <FaInput id="register-display-name" type="text" autocomplete="name" placeholder="例如 张三" aria-required="true" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
            </FormControl>
            <FormMessage class="field-message" />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField, errors }" name="password">
          <FormItem class="field-item">
            <RegisterFieldLabel
              html-for="register-password"
              label="密码"
              status="必填"
              description="长度为 6 到 64 位"
            />
            <FormControl>
              <div class="password-shell">
                <FaInput id="register-password" :type="showPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="6 到 64 位" aria-required="true" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
                <button type="button" class="password-toggle" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
                  <Eye v-if="!showPwd" class="size-4" />
                  <EyeOff v-else class="size-4" />
                </button>
              </div>
            </FormControl>
            <FormMessage class="field-message" />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField, errors }" name="checkPassword">
          <FormItem class="field-item">
            <RegisterFieldLabel html-for="register-password-confirm" label="确认密码" status="必填" description="请与上方密码保持一致" />
            <FormControl>
              <div class="password-shell">
                <FaInput id="register-password-confirm" :type="showCheckPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入密码" aria-required="true" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
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
    </section>

    <section v-else class="form-section form-section--remark" aria-label="注册申请补充信息">
      <FormField v-slot="{ componentField, errors }" name="contactInfo">
        <FormItem class="field-item">
          <RegisterFieldLabel html-for="register-contact" label="联系方式" status="选填" description="仅用于注册审核和账号问题联系" />
          <FormControl>
            <FaInput id="register-contact" type="text" autocomplete="tel" placeholder="手机号、工号、科室内线等" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>

      <FormField v-slot="{ componentField, errors }" name="applyRemark">
        <FormItem class="field-item field-item--remark">
          <RegisterFieldLabel html-for="register-remark" label="申请说明" status="选填" description="最多 500 个字符" />
          <FormControl>
            <FaTextarea id="register-remark" :rows="2" maxlength="500" show-word-limit placeholder="例如：肿瘤科医生，需要查阅本科室住院病案" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>
    </section>

    <div class="form-actions">
      <FaButton type="button" variant="outline" size="lg" @click="handlePrevious">
        <FaIcon name="i-lucide:arrow-left" />
        {{ isApplicationStep ? '返回身份信息' : '返回登录' }}
      </FaButton>
      <FaButton :loading="loading" size="lg" type="submit">
        <FaIcon v-if="!loading" :name="isApplicationStep ? 'i-lucide:send' : 'i-lucide:arrow-right'" />
        {{ isApplicationStep ? '提交注册' : '填写申请说明' }}
      </FaButton>
    </div>
  </form>
</template>

<style scoped>
/* stylelint-disable @stylistic/selector-list-comma-newline-after, order/properties-order, at-rule-empty-line-before, media-feature-range-notation */
.register-form { display: grid; gap: var(--mrr-space-4); margin-top: 0; }
.review-flow { display: grid; gap: var(--mrr-space-3); padding: var(--mrr-space-4); color: var(--mrr-foreground); background: color-mix(in srgb, var(--color-info) 7%, var(--mrr-card)); border: 1px solid color-mix(in srgb, var(--color-info) 22%, var(--mrr-border)); border-radius: var(--mrr-radius-lg); }
.review-flow__summary { display: flex; gap: var(--mrr-space-3); align-items: flex-start; }
.review-flow__summary strong { display: block; font-size: 13px; }
.review-flow__summary p { margin: 3px 0 0; font-size: 11px; line-height: 1.6; color: var(--mrr-muted-foreground); }
.review-flow__icon { display: grid; flex: 0 0 auto; width: 34px; height: 34px; font-size: 17px; color: var(--color-info); background: color-mix(in srgb, var(--color-info) 11%, var(--mrr-card)); border: 1px solid color-mix(in srgb, var(--color-info) 20%, var(--mrr-border)); border-radius: var(--mrr-radius-md); place-items: center; }
.review-flow__steps { display: grid; grid-template-columns: 1fr auto 1fr auto 1fr; gap: var(--mrr-space-2); align-items: center; padding-top: var(--mrr-space-3); font-size: 11px; color: var(--mrr-muted-foreground); border-top: 1px solid color-mix(in srgb, var(--color-info) 16%, var(--mrr-border)); }
.review-flow__steps span { display: inline-flex; gap: 6px; align-items: center; justify-content: center; white-space: nowrap; }
.review-flow__steps b { display: grid; width: 20px; height: 20px; font-size: 10px; color: var(--mrr-muted-foreground); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: 50%; place-items: center; }
.review-flow__steps .is-current { font-weight: 650; color: var(--color-info); }
.review-flow__steps .is-current b { color: var(--mrr-primary-foreground); background: var(--color-info); border-color: var(--color-info); }
.form-section { display: grid; gap: var(--mrr-space-3); padding: var(--mrr-space-4); background: color-mix(in srgb, var(--mrr-muted) 42%, var(--mrr-card)); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.form-section--remark { gap: var(--mrr-space-2); }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-3); }
.field-item { position: relative; display: grid; gap: var(--mrr-space-2); min-width: 0; padding-bottom: 18px; }
.field-item :deep(.fa-input__wrapper), .field-item :deep(input), .field-item :deep(textarea) { width: 100%; }
.field-message { position: absolute; bottom: 0; font-size: 10px; }
.field-item--remark { padding-bottom: 16px; }
.field-item--remark :deep(textarea) { min-height: 64px; }
.password-shell { position: relative; }
.password-shell :deep(input) { padding-right: 42px; }
.password-toggle { position: absolute; top: 50%; right: 12px; display: grid; padding: 4px; color: var(--mrr-muted-foreground); background: transparent; transform: translateY(-50%); place-items: center; }
.form-actions { display: grid; grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr); gap: var(--mrr-space-3); }
.form-actions :deep(button) { width: 100%; }
@media (max-width: 768px) {
  .review-flow__steps { grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto minmax(0, 1fr); gap: var(--mrr-space-1); align-items: start; }
  .review-flow__steps span { min-width: 0; flex-direction: column; gap: var(--mrr-space-1); justify-content: flex-start; text-align: center; white-space: normal; }
  .review-flow__steps > svg { align-self: start; margin-top: 3px; }
}
@media (max-width: 620px) {
  .field-grid, .form-actions { grid-template-columns: 1fr; }
  .form-actions { display: flex; flex-direction: column-reverse; }
}
</style>

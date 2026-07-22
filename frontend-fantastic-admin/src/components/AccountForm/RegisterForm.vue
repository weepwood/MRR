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
      password: z.string().min(6, '密码长度应为6到64位').max(64, '密码长度应为6到64位'),
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
    <section class="review-flow" aria-label="注册审核流程">
      <div class="review-flow__summary">
        <span class="review-flow__icon"><FaIcon name="i-ri:user-received-2-line" /></span>
        <div>
          <strong>账号需要管理员审核</strong>
          <p>提交后账号进入待审核状态，审核通过前无法登录系统。</p>
        </div>
      </div>
      <div class="review-flow__steps" aria-hidden="true">
        <span class="is-current"><b>1</b>填写申请</span>
        <FaIcon name="i-ri:arrow-right-s-line" />
        <span><b>2</b>管理员审核</span>
        <FaIcon name="i-ri:arrow-right-s-line" />
        <span><b>3</b>账号登录</span>
      </div>
    </section>

    <section class="form-section">
      <header class="section-heading">
        <span><FaIcon name="i-ri:id-card-line" /></span>
        <div>
          <strong>身份信息</strong>
          <p>填写便于管理员核验的账号和人员信息。</p>
        </div>
      </header>

      <div class="field-grid">
        <FormField v-slot="{ componentField, errors }" name="account">
          <FormItem class="field-item">
            <label for="register-account">用户名 <em>必填</em></label>
            <FormControl>
              <FaInput id="register-account" type="text" autocomplete="username" placeholder="例如 zhangsan" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
            </FormControl>
            <small class="field-hint">3–40 位，可使用字母、数字、点、下划线和短横线</small>
            <FormMessage class="field-message" />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField, errors }" name="displayName">
          <FormItem class="field-item">
            <label for="register-display-name">显示名称 <em>必填</em></label>
            <FormControl>
              <FaInput id="register-display-name" type="text" autocomplete="name" placeholder="例如 张三" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
            </FormControl>
            <small class="field-hint">建议填写真实姓名，便于管理员确认身份</small>
            <FormMessage class="field-message" />
          </FormItem>
        </FormField>
      </div>

      <FormField v-slot="{ componentField, errors }" name="contactInfo">
        <FormItem class="field-item">
          <label for="register-contact">联系方式 <span>选填</span></label>
          <FormControl>
            <FaInput id="register-contact" type="text" autocomplete="tel" placeholder="手机号、工号、科室内线等" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <small class="field-hint">仅用于注册审核和账号问题联系</small>
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>
    </section>

    <section class="form-section">
      <header class="section-heading">
        <span><FaIcon name="i-ri:shield-keyhole-line" /></span>
        <div>
          <strong>账号安全</strong>
          <p>设置登录密码。最低 6 位，建议使用更长且容易记忆的口令。</p>
        </div>
      </header>

      <div class="field-grid password-grid">
        <FormField v-slot="{ componentField, value, errors }" name="password">
          <FormItem class="field-item">
            <label for="register-password">密码 <em>必填</em></label>
            <FormControl>
              <div class="password-shell">
                <FaInput id="register-password" :type="showPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="6 到 64 位" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
                <button type="button" class="password-toggle" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
                  <Eye v-if="!showPwd" class="size-4" />
                  <EyeOff v-else class="size-4" />
                </button>
              </div>
            </FormControl>
            <FaPasswordStrength :password="value" class="password-strength" />
            <FormMessage class="field-message" />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField, errors }" name="checkPassword">
          <FormItem class="field-item">
            <label for="register-password-confirm">确认密码 <em>必填</em></label>
            <FormControl>
              <div class="password-shell">
                <FaInput id="register-password-confirm" :type="showCheckPwd ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入密码" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
                <button type="button" class="password-toggle" :aria-label="showCheckPwd ? '隐藏密码' : '显示密码'" @click="showCheckPwd = !showCheckPwd">
                  <Eye v-if="!showCheckPwd" class="size-4" />
                  <EyeOff v-else class="size-4" />
                </button>
              </div>
            </FormControl>
            <small class="field-hint">请与上方密码保持一致</small>
            <FormMessage class="field-message" />
          </FormItem>
        </FormField>
      </div>
    </section>

    <section class="form-section form-section--remark">
      <header class="section-heading">
        <span><FaIcon name="i-ri:file-text-line" /></span>
        <div>
          <strong>申请说明</strong>
          <p>补充所在科室、岗位或使用目的，可以提高审核效率。</p>
        </div>
      </header>

      <FormField v-slot="{ componentField, errors }" name="applyRemark">
        <FormItem class="field-item field-item--remark">
          <label for="register-remark">申请说明 <span>选填</span></label>
          <FormControl>
            <FaTextarea id="register-remark" :rows="3" maxlength="500" show-word-limit placeholder="例如：肿瘤科医生，需要查阅本科室住院病案" :class="errors.length > 0 && 'border-destructive'" v-bind="componentField" />
          </FormControl>
          <FormMessage class="field-message" />
        </FormItem>
      </FormField>
    </section>

    <div class="form-actions">
      <FaButton type="button" variant="outline" size="lg" @click="emits('onLogin', form.values.account)">
        <FaIcon name="i-ri:arrow-left-line" />
        返回登录
      </FaButton>
      <FaButton :loading="loading" size="lg" type="submit">
        <FaIcon v-if="!loading" name="i-ri:send-plane-2-line" />
        提交注册申请
      </FaButton>
    </div>
  </form>
</template>

<style scoped>
/* stylelint-disable @stylistic/selector-list-comma-newline-after, order/properties-order, at-rule-empty-line-before, media-feature-range-notation */
.register-form { display: grid; gap: var(--mrr-space-4); margin-top: var(--mrr-space-5); }
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
.section-heading { display: flex; gap: var(--mrr-space-3); align-items: flex-start; padding-bottom: var(--mrr-space-3); border-bottom: 1px solid var(--mrr-border); }
.section-heading > span { display: grid; flex: 0 0 auto; width: 30px; height: 30px; color: var(--mrr-primary); background: color-mix(in srgb, var(--mrr-primary) 9%, var(--mrr-card)); border: 1px solid color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border)); border-radius: var(--mrr-radius-md); place-items: center; }
.section-heading strong { display: block; font-size: 13px; }
.section-heading p { margin: 3px 0 0; font-size: 11px; line-height: 1.55; color: var(--mrr-muted-foreground); }
.field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-3); }
.field-item { position: relative; display: grid; gap: var(--mrr-space-2); min-width: 0; padding-bottom: 18px; }
.field-item label { display: flex; gap: 6px; align-items: center; font-size: 12px; font-weight: 650; color: var(--mrr-foreground); }
.field-item label em { font-size: 10px; font-style: normal; font-weight: 500; color: var(--color-danger); }
.field-item label span { font-size: 10px; font-weight: 500; color: var(--mrr-muted-foreground); }
.field-item :deep(.fa-input__wrapper), .field-item :deep(input), .field-item :deep(textarea) { width: 100%; }
.field-hint { min-height: 16px; font-size: 10px; line-height: 1.5; color: var(--mrr-muted-foreground); }
.field-message { position: absolute; bottom: 0; font-size: 10px; }
.field-item--remark { padding-bottom: 16px; }
.password-shell { position: relative; }
.password-shell :deep(input) { padding-right: 42px; }
.password-toggle { position: absolute; top: 50%; right: 12px; display: grid; padding: 4px; color: var(--mrr-muted-foreground); background: transparent; transform: translateY(-50%); place-items: center; }
.password-strength { margin-top: 0; }
.form-actions { display: grid; grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr); gap: var(--mrr-space-3); }
.form-actions :deep(button) { width: 100%; }
@media (max-width: 620px) {
  .review-flow__steps { grid-template-columns: 1fr; justify-items: start; }
  .review-flow__steps > svg { display: none; }
  .review-flow__steps span { justify-content: flex-start; }
  .field-grid, .form-actions { grid-template-columns: 1fr; }
  .password-grid { gap: 0; }
  .form-actions { display: flex; flex-direction: column-reverse; }
}
</style>

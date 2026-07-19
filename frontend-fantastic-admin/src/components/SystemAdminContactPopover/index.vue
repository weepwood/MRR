<script setup lang="ts">
import { ElMessage } from 'element-plus'

const props = withDefaults(defineProps<{
  visible?: boolean
  displayName?: string
  department?: string
  phone?: string
  extension?: string
  email?: string
  serviceHours?: string
  description?: string
}>(), {
  visible: false,
  displayName: '系统管理员',
  department: '信息科',
  phone: '',
  extension: '',
  email: '',
  serviceHours: '',
  description: '',
})

const opened = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | undefined

const hasContact = computed(() => props.visible && Boolean(
  props.phone || props.email || props.extension || props.serviceHours || props.description,
))

function show() {
  if (closeTimer) clearTimeout(closeTimer)
  opened.value = true
}

function hideLater() {
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = setTimeout(() => {
    opened.value = false
  }, 140)
}

async function copy(value: string, label: string) {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(`${label}已复制`)
  }
  catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

onBeforeUnmount(() => {
  if (closeTimer) clearTimeout(closeTimer)
})
</script>

<template>
  <span v-if="hasContact" class="admin-contact" @mouseenter="show" @mouseleave="hideLater">
    <el-popover
      v-model:visible="opened"
      placement="top-start"
      :width="330"
      trigger="click"
      :teleported="false"
      popper-class="mrr-admin-contact-popper"
    >
      <template #reference>
        <button
          type="button"
          class="admin-contact__trigger"
          aria-label="查看系统管理员信息"
          @focus="show"
          @blur="hideLater"
          @click="opened = !opened"
        >
          查看系统管理员信息
        </button>
      </template>

      <section class="contact-card" @mouseenter="show" @mouseleave="hideLater">
        <header>
          <span class="contact-card__icon"><FaIcon name="i-ri:customer-service-2-line" /></span>
          <div>
            <strong>{{ displayName || '系统管理员' }}</strong>
            <small v-if="department">{{ department }}</small>
          </div>
        </header>

        <div class="contact-card__list">
          <div v-if="phone" class="contact-row">
            <span>联系电话</span>
            <div>
              <a :href="`tel:${phone}`">{{ phone }}</a>
              <button type="button" title="复制联系电话" @click="copy(phone, '联系电话')">
                <FaIcon name="i-ri:file-copy-line" />
              </button>
            </div>
          </div>
          <div v-if="extension" class="contact-row">
            <span>分机号</span>
            <div><strong>{{ extension }}</strong></div>
          </div>
          <div v-if="email" class="contact-row">
            <span>联系邮箱</span>
            <div>
              <a :href="`mailto:${email}`">{{ email }}</a>
              <button type="button" title="复制联系邮箱" @click="copy(email, '联系邮箱')">
                <FaIcon name="i-ri:file-copy-line" />
              </button>
            </div>
          </div>
          <div v-if="serviceHours" class="contact-row">
            <span>服务时间</span>
            <div><strong>{{ serviceHours }}</strong></div>
          </div>
        </div>

        <p v-if="description" class="contact-card__description">{{ description }}</p>
      </section>
    </el-popover>
  </span>
</template>

<style scoped>
.admin-contact { display: inline-flex; }
.admin-contact__trigger {
  padding: 0;
  font: inherit;
  font-weight: 650;
  color: var(--mrr-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
  background: transparent;
  border: 0;
}
.admin-contact__trigger:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--mrr-primary) 42%, transparent);
  outline-offset: 3px;
  border-radius: 3px;
}
.contact-card { display: grid; gap: var(--mrr-space-4); color: var(--mrr-foreground); }
.contact-card header { display: flex; gap: var(--mrr-space-3); align-items: center; }
.contact-card header strong { display: block; font-size: 14px; }
.contact-card header small { display: block; margin-top: 2px; color: var(--mrr-muted-foreground); }
.contact-card__icon {
  display: grid;
  width: 38px;
  height: 38px;
  color: var(--mrr-primary);
  background: color-mix(in srgb, var(--mrr-primary) 10%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
  place-items: center;
}
.contact-card__list { display: grid; gap: var(--mrr-space-3); }
.contact-row { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: var(--mrr-space-3); font-size: 12px; }
.contact-row > span { color: var(--mrr-muted-foreground); }
.contact-row > div { display: flex; gap: var(--mrr-space-2); align-items: center; min-width: 0; }
.contact-row a, .contact-row strong { overflow-wrap: anywhere; color: var(--mrr-foreground); }
.contact-row button {
  display: inline-grid;
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  color: var(--mrr-muted-foreground);
  cursor: pointer;
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-sm);
  place-items: center;
}
.contact-card__description {
  padding: var(--mrr-space-3);
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-md);
}
</style>

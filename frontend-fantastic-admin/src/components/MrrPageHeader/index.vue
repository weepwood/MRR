<script setup lang="ts">
defineOptions({ name: 'MrrPageHeader' })

const props = defineProps<{
  title: string
  description?: string
  eyebrow?: string
  icon?: string
}>()

const slots = useSlots()
const hasAside = computed(() => Boolean(slots.actions))
const isUsersHeader = computed(() => props.title === '用户管理')
</script>

<template>
  <header class="mrr-page-header">
    <div class="mrr-page-header__main">
      <span v-if="props.icon && !isUsersHeader" class="mrr-page-header__icon" aria-hidden="true">
        <FaIcon :name="props.icon" />
      </span>
      <div class="mrr-page-header__copy" :class="{ 'has-eyebrow': props.eyebrow || isUsersHeader }">
        <p v-if="props.eyebrow || isUsersHeader" class="mrr-page-header__eyebrow">
          {{ props.eyebrow || 'User Management' }}
        </p>
        <div class="mrr-page-header__title-row">
          <h1>{{ props.title }}</h1>
          <slot name="badge" />
        </div>
        <p v-if="props.description" class="mrr-page-header__description">
          {{ props.description }}
        </p>
        <div v-if="slots.meta" class="mrr-page-header__meta">
          <slot name="meta" />
        </div>
      </div>
    </div>

    <div v-if="hasAside" class="mrr-page-header__aside">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.mrr-page-header {
  display: flex;
  gap: var(--mrr-space-6);
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
  padding: 2px 2px 18px;
  border-bottom: 1px solid var(--mrr-shell-divider);
}

.mrr-page-header__main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}

.mrr-page-header__icon {
  display: grid;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  font-size: 19px;
  color: var(--mrr-primary);
  background:
    linear-gradient(145deg, color-mix(in srgb, var(--mrr-primary) 12%, var(--mrr-card)), color-mix(in srgb, var(--mrr-primary) 5%, var(--mrr-card)));
  border: 1px solid color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border));
  border-radius: var(--mrr-radius-lg);
  box-shadow: var(--mrr-shadow-xs);
  place-items: center;
}

.mrr-page-header__copy {
  min-width: 0;
}

.mrr-page-header__eyebrow {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.5;
  color: var(--mrr-primary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
  opacity: 0.82;
}

.mrr-page-header__title-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.mrr-page-header h1 {
  margin: 0;
  font-size: clamp(22px, 2vw, 25px);
  font-weight: 680;
  line-height: 1.24;
  color: var(--mrr-foreground);
  letter-spacing: -0.035em;
}

.mrr-page-header__description {
  max-width: 780px;
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--mrr-muted-foreground);
}

.mrr-page-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  margin-top: var(--mrr-space-2);
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

.mrr-page-header__aside {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
  padding-top: 2px;
}

.mrr-page-header__aside :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (width <= 760px) {
  .mrr-page-header {
    flex-direction: column;
    padding-bottom: 16px;
  }

  .mrr-page-header__aside {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>

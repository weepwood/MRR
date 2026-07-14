<script setup lang="ts">
defineOptions({ name: 'MrrPageHeader' })

const props = defineProps<{
  title: string
  description?: string
  icon?: string
}>()

const slots = useSlots()
const hasAside = computed(() => Boolean(slots.actions))
</script>

<template>
  <header class="mrr-page-header">
    <div class="mrr-page-header__main">
      <span v-if="props.icon" class="mrr-page-header__icon" aria-hidden="true">
        <FaIcon :name="props.icon" />
      </span>
      <div class="mrr-page-header__copy">
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
  gap: var(--mrr-space-5);
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.mrr-page-header__main {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: flex-start;
  min-width: 0;
}

.mrr-page-header__icon {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  font-size: 17px;
  color: var(--mrr-primary);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
  place-items: center;
}

.mrr-page-header__copy {
  min-width: 0;
}

.mrr-page-header__title-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.mrr-page-header h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--mrr-foreground);
  letter-spacing: -0.02em;
}

.mrr-page-header__description {
  max-width: 760px;
  margin: 5px 0 0;
  font-size: 13px;
  line-height: 1.5;
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
}

.mrr-page-header__aside :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (width <= 760px) {
  .mrr-page-header {
    flex-direction: column;
  }

  .mrr-page-header__aside {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>

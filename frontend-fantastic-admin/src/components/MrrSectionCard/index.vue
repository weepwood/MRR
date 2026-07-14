<script setup lang="ts">
defineOptions({ name: 'MrrSectionCard' })

type BodyPadding = 'none' | 'compact' | 'normal'

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  icon?: string
  bodyPadding?: BodyPadding
}>(), {
  bodyPadding: 'normal',
})

const slots = useSlots()
const hasHeader = computed(() => Boolean(
  props.title || props.description || props.icon || slots.header || slots.actions,
))
</script>

<template>
  <el-card
    shadow="never"
    class="mrr-section-card"
    :class="`mrr-section-card--padding-${props.bodyPadding}`"
  >
    <template v-if="hasHeader" #header>
      <slot name="header">
        <div class="mrr-section-card__header">
          <div class="mrr-section-card__heading">
            <span v-if="props.icon" class="mrr-section-card__icon" aria-hidden="true">
              <FaIcon :name="props.icon" />
            </span>
            <div>
              <h2 v-if="props.title">{{ props.title }}</h2>
              <p v-if="props.description">{{ props.description }}</p>
            </div>
          </div>
          <div v-if="slots.actions" class="mrr-section-card__actions">
            <slot name="actions" />
          </div>
        </div>
      </slot>
    </template>
    <slot />
  </el-card>
</template>

<style scoped>
.mrr-section-card {
  min-width: 0;
  overflow: hidden;
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border-color: var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
  box-shadow: var(--mrr-shadow-xs);
}

.mrr-section-card :deep(.el-card__header) {
  padding: 15px 18px;
  background: color-mix(in srgb, var(--mrr-muted) 18%, var(--mrr-card));
  border-bottom-color: var(--mrr-border);
}

.mrr-section-card--padding-none :deep(.el-card__body) {
  padding: 0;
}

.mrr-section-card--padding-compact :deep(.el-card__body) {
  padding: var(--mrr-space-3);
}

.mrr-section-card--padding-normal :deep(.el-card__body) {
  padding: 18px;
}

.mrr-section-card__header {
  display: flex;
  gap: var(--mrr-space-4);
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.mrr-section-card__heading {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.mrr-section-card__icon {
  display: grid;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  font-size: 15px;
  color: var(--mrr-primary);
  background: color-mix(in srgb, var(--mrr-primary) 8%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--mrr-primary) 16%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
  place-items: center;
}

.mrr-section-card h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--mrr-card-foreground);
  letter-spacing: -0.01em;
}

.mrr-section-card p {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--mrr-muted-foreground);
}

.mrr-section-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
}

.mrr-section-card__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (width <= 640px) {
  .mrr-section-card__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .mrr-section-card__actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
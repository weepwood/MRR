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
</script>

<template>
  <header class="mrr-page-header">
    <div class="mrr-page-header__main">
      <span v-if="props.icon" class="mrr-page-header__icon" aria-hidden="true">
        <FaIcon :name="props.icon" />
      </span>
      <div class="mrr-page-header__copy" :class="{ 'has-eyebrow': props.eyebrow }">
        <p v-if="props.eyebrow" class="mrr-page-header__eyebrow">
          {{ props.eyebrow }}
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
/*
 * 排版与容器的模式相关样式（plain / card）由全局 page-header.css 统一提供，
 * 本组件只负责自身结构（图标、文案区、右侧操作区）。
 * 下面保留的字号/字色取值与全局规范刻意保持一致：全局规则的选择器优先级更高，
 * 若两处取值不同，组件内的声明就会变成永不生效的死代码。
 */
.mrr-page-header {
  display: flex;
  gap: var(--mrr-space-6);
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
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
  place-items: center;
  width: 42px;
  height: 42px;
  font-size: 19px;
  color: var(--mrr-primary);
  background: linear-gradient(145deg, color-mix(in srgb, var(--mrr-primary) 12%, var(--mrr-card)), color-mix(in srgb, var(--mrr-primary) 5%, var(--mrr-card)));
  border: 1px solid color-mix(in srgb, var(--mrr-primary) 18%, var(--mrr-border));
  border-radius: var(--mrr-radius-lg);
  box-shadow: var(--mrr-shadow-xs);
}

.mrr-page-header__copy {
  min-width: 0;
}

.mrr-page-header__eyebrow {
  margin: 0 0 18px;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.5;
  color: var(--mrr-primary);
  text-transform: uppercase;
  letter-spacing: 0.16em;
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
  font-size: clamp(30px, 3vw, 40px);
  font-weight: 650;
  line-height: 1.18;
  color: var(--mrr-foreground);
  letter-spacing: -0.045em;
}

.mrr-page-header__description {
  max-width: 780px;
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
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

@media (max-width: 760px) {
  .mrr-page-header {
    flex-direction: column;
  }

  .mrr-page-header__aside {
    justify-content: flex-start;
    width: 100%;
  }
}
</style>

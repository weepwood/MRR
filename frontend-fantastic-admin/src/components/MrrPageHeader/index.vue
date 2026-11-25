<script setup lang="ts">
defineOptions({ name: 'MrrPageHeader' })

const props = defineProps<{
  title: string
  description?: string
  eyebrow?: string
  icon?: string
}>()
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
        <div v-if="$slots.meta" class="mrr-page-header__meta">
          <slot name="meta" />
        </div>
      </div>
    </div>

    <div v-if="$slots.actions" class="mrr-page-header__aside">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
/* 结构由本组件负责，所有几何与排版统一由 page-header.css 持有。 */
.mrr-page-header__main {
  display: flex;
  flex: 1 1 auto;
  gap: var(--mrr-space-4);
  align-items: flex-start;
  min-width: 0;
}

.mrr-page-header__icon {
  display: grid;
  flex: 0 0 32px;
  place-items: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: var(--mrr-primary);

  /*
   * 实色浅底 + 细描边，与分区卡图标同一做法；DESIGN.md 约定页头不叠加渐变与阴影。
   * 直接用品牌色 tone 令牌：调色板对浅底 / 描边强度的调整会一并生效，Chrome 86 也有静态回退。
   */
  background: var(--mrr-tone-primary-soft);
  border: 1px solid var(--mrr-tone-primary-border);
  border-radius: var(--mrr-radius-md);
}

.mrr-page-header__copy {
  min-width: 0;
  overflow-wrap: anywhere;
}

.mrr-page-header__title-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.mrr-page-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  margin-top: var(--mrr-space-1);
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

.mrr-page-header__aside {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
  max-width: 100%;
}

.mrr-page-header__aside :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>

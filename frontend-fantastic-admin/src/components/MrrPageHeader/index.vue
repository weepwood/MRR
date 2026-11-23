<script setup lang="ts">
defineOptions({ name: 'MrrPageHeader' })

const props = withDefaults(defineProps<{
  title: string
  description?: string
  eyebrow?: string
  icon?: string
  /**
   * compact：查询 / 列表 / 浏览页使用。card 模式下去掉 110px 最小高度、标题 22px、
   * 说明仍固定在标题下方。plain 模式不受影响。样式见 page-header.css 第 5 节。
   */
  size?: 'default' | 'compact'
}>(), {
  size: 'default',
})

const slots = useSlots()
const hasAside = computed(() => Boolean(slots.actions))
</script>

<template>
  <header class="mrr-page-header" :class="{ 'mrr-page-header--compact': props.size === 'compact' }">
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
 * 下面保留的字号/字色取值是全局规范（page-header.css）的同值镜像：全局选择器
 * 带 html[data-page-title-style] 前缀、优先级更高且恒存在（settings.ts 保证），
 * 平时这些声明被压住；若前缀意外缺失，组件取值可保证页头不会跳变。
 */
.mrr-page-header {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-6);
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.mrr-page-header__main {
  display: flex;
  flex: 1 1 auto;
  gap: var(--mrr-space-4);
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

  /*
   * 实色浅底 + 细描边，与分区卡图标同一做法；DESIGN.md 约定页头不叠加渐变与阴影。
   * 直接用品牌色 tone 令牌：调色板对浅底 / 描边强度的调整会一并生效，Chrome 86 也有静态回退。
   */
  background: var(--mrr-tone-primary-soft);
  border: 1px solid var(--mrr-tone-primary-border);
  border-radius: var(--mrr-radius-lg);
}

/* 紧凑页头的图标降一档，与 22px 标题等高。 */
.mrr-page-header--compact .mrr-page-header__icon {
  flex-basis: 32px;
  width: 32px;
  height: 32px;
  font-size: 16px;
  border-radius: var(--mrr-radius-md);
}

.mrr-page-header__copy {
  min-width: 0;
  overflow-wrap: anywhere;
}

.mrr-page-header__eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.5;
  color: var(--mrr-primary);
  text-transform: uppercase;
  letter-spacing: 0.16em;
}

.mrr-page-header__title-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.mrr-page-header h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--mrr-foreground);
  letter-spacing: -0.02em;
}

.mrr-page-header__description {
  max-width: 780px;
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
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
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
  max-width: 100%;
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

/* Chrome 86：图标胶囊已直接使用带静态回退的 tone 令牌，不再需要 @supports 分支。 */
</style>

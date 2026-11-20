<script setup lang="ts">
defineOptions({ name: 'MrrFilterBar' })

const props = withDefaults(defineProps<{
  compact?: boolean
  embedded?: boolean
  focusRing?: boolean
  layout?: 'inline' | 'grid'
}>(), {
  compact: false,
  embedded: false,
  focusRing: true,
  layout: 'inline',
})

const slots = useSlots()
</script>

<template>
  <div class="mrr-filter-bar" :class="{ 'mrr-filter-bar--compact': props.compact, 'mrr-filter-bar--embedded': props.embedded, 'mrr-filter-bar--focus-ring': props.focusRing, 'mrr-filter-bar--grid': props.layout === 'grid' }">
    <div class="mrr-filter-bar__fields">
      <slot />
    </div>
    <div v-if="slots.actions" class="mrr-filter-bar__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.mrr-filter-bar {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 11px 12px;
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
  box-shadow: var(--mrr-shadow-xs);
  transition: border-color 140ms ease, box-shadow 140ms ease;
}

.mrr-filter-bar--focus-ring:focus-within {
  border-color: color-mix(in srgb, var(--mrr-ring) 46%, var(--mrr-border));
  box-shadow: var(--mrr-shadow-xs), 0 0 0 3px color-mix(in srgb, var(--mrr-ring) 10%, transparent);
}

.mrr-filter-bar--compact {
  padding: 8px 9px;
  border-radius: var(--mrr-radius-md);
}

.mrr-filter-bar.mrr-filter-bar--embedded {
  padding: 0 0 var(--mrr-space-3);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--mrr-shell-divider);
  border-radius: 0;
  box-shadow: none;
}

/*
 * embedded（无标签）模式下字段允许拉伸填满所在行：换行后的次行控件不再
 * 以固定宽度孤零零靠左（N4「差半个字段」观感），而是与首行一样铺满行宽。
 * 单个次行字段由上限约束，避免被拉成整行长的超大控件。
 */
.mrr-filter-bar--embedded .mrr-filter-bar__fields > :deep(*) {
  flex-grow: 1;
  max-width: 460px;
}

@media (max-width: 760px) {
  /* 窄屏下字段已是整行宽，解除上限避免被压成 460px。 */
  .mrr-filter-bar--embedded .mrr-filter-bar__fields > :deep(*) {
    max-width: 100%;
  }
}

.mrr-filter-bar__fields {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
}

.mrr-filter-bar__actions {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  padding-left: var(--mrr-space-3);
  border-left: 1px solid var(--mrr-shell-divider);
}

.mrr-filter-bar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.mrr-filter-bar--labelled :deep(.mrr-filter-field) {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
}

.mrr-filter-bar--labelled :deep(.mrr-filter-field > label) {
  font-size: 12px;
  font-weight: 600;
  color: var(--mrr-card-foreground);
}

.mrr-filter-bar--labelled .mrr-filter-bar__actions {
  flex: 0 0 auto;
  flex-wrap: nowrap;
  align-self: flex-end;
}

.mrr-filter-bar--grid {
  flex-direction: column;
  align-items: stretch;
}

.mrr-filter-bar--grid .mrr-filter-bar__fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--mrr-space-3) var(--mrr-space-4);
  align-items: end;
}

.mrr-filter-bar--grid :deep(.el-form-item) {
  min-width: 0;
  margin: 0;
}

.mrr-filter-bar--grid :deep(.el-form-item__label) {
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--mrr-muted-foreground);
}

.mrr-filter-bar--grid :deep(.el-input),
.mrr-filter-bar--grid :deep(.el-select),
.mrr-filter-bar--grid :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}

.mrr-filter-bar--grid :deep(.mrr-filter-bar__range) {
  grid-column: span 2;
}

.mrr-filter-bar--grid .mrr-filter-bar__actions {
  justify-content: flex-end;
  padding-left: 0;
  border-left: 0;
}

@media (max-width: 1100px) {
  .mrr-filter-bar--grid .mrr-filter-bar__fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/*
 * 控件底色 / 最小高度 / 焦点态背景此前在此用 color-mix 局部覆盖；现已统一提升到
 * tokens.css 的 --mrr-control-bg（含 Chrome 86 静态回退），此处不再重复定义。
 */

@media (max-width: 760px) {
  .mrr-filter-bar--grid .mrr-filter-bar__fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .mrr-filter-bar--grid :deep(.mrr-filter-bar__range) {
    grid-column: auto;
  }

  .mrr-filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .mrr-filter-bar__fields,
  .mrr-filter-bar__actions {
    width: 100%;
  }

  .mrr-filter-bar__fields > :deep(*) {
    min-width: 0;
    max-width: 100%;
  }

  .mrr-filter-bar__actions {
    padding-top: var(--mrr-space-3);
    padding-left: 0;
    border-top: 1px solid var(--mrr-shell-divider);
    border-left: 0;
  }

  .mrr-filter-bar__actions :deep(.el-button) {
    flex: 1 1 120px;
  }
}

/*
 * Chrome 86 回退：焦点态必须保留焦点环——这里原本只有一条含 color-mix 的 box-shadow，
 * 失效后连卡片自身的阴影都会一起消失，聚焦与失焦完全无从分辨。
 * （控件底色的回退已随全局令牌移至 browser-compatibility.css。）
 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  .mrr-filter-bar--focus-ring:focus-within {
    border-color: var(--mrr-primary);
    box-shadow: var(--mrr-shadow-xs), var(--mrr-focus-ring);
  }
}
</style>

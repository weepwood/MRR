<script setup lang="ts">
import { useElementSize } from '@vueuse/core'
import { computed, ref, useId } from 'vue'

defineOptions({ name: 'MrrFilterBar' })

const props = withDefaults(defineProps<{
  compact?: boolean
  embedded?: boolean
  focusRing?: boolean
  /**
   * 字段上方显示可见标签（`.mrr-filter-field > label + 控件`），操作按钮与控件底边对齐。
   * 此前只能靠页面手写 `class="mrr-filter-bar--labelled"` 开启，
   * 记录、用户、运行错误等页面因此各自复制了一套 label 样式。
   */
  labelled?: boolean
  layout?: 'inline' | 'grid'
  /** Number of filled advanced fields; never implies a successful query. */
  advancedCount?: number
  /** Count of advanced fields in the last successfully returned list, not draft values. */
  appliedAdvancedCount?: number
  advancedDirty?: boolean
}>(), {
  compact: false,
  embedded: false,
  focusRing: true,
  labelled: false,
  layout: 'inline',
  advancedCount: 0,
})

const slots = useSlots()
const root = ref<HTMLElement>()
const { width: rootWidth } = useElementSize(root)
const narrow = computed(() => rootWidth.value > 0 && rootWidth.value < 560)
const tiny = computed(() => rootWidth.value > 0 && rootWidth.value < 280)
const fields = ref<HTMLElement>()
const { width: fieldsWidth } = useElementSize(computed(() => props.layout === 'grid' ? fields.value : undefined))
const advancedExpanded = ref(true)
const advancedId = useId()
// Chrome 86 supports ResizeObserver, but not CSS container queries. Use actual
// available width (sidebar/inspector included), not the browser viewport.
const columns = computed(() => {
  if (props.layout !== 'grid' || fieldsWidth.value <= 0) { return undefined }
  return fieldsWidth.value >= 760 ? 3 : fieldsWidth.value >= 480 ? 2 : 1
})
</script>

<template>
  <div ref="root" class="mrr-filter-bar" :class="{ 'mrr-filter-bar--narrow': narrow, 'mrr-filter-bar--tiny': tiny, 'mrr-filter-bar--compact': props.compact, 'mrr-filter-bar--embedded': props.embedded, 'mrr-filter-bar--focus-ring': props.focusRing, 'mrr-filter-bar--labelled': props.labelled, 'mrr-filter-bar--grid': props.layout === 'grid' }">
    <div ref="fields" class="mrr-filter-bar__fields" :data-grid-columns="columns">
      <slot />
    </div>
    <div v-if="slots.actions || slots.advanced" class="mrr-filter-bar__actions">
      <slot name="actions" />
      <el-button v-if="slots.advanced" class="mrr-filter-bar__disclosure" native-type="button" :aria-controls="advancedId" :aria-expanded="advancedExpanded" @click="advancedExpanded = !advancedExpanded">
        <FaIcon :name="advancedExpanded ? 'i-lucide:chevron-up' : 'i-lucide:chevron-down'" aria-hidden="true" />
        {{ advancedExpanded ? '收起高级筛选' : '展开高级筛选' }}（已填写 {{ props.advancedCount }} 项）
      </el-button>
    </div>
    <div v-if="slots.business" class="mrr-filter-bar__business">
      <slot name="business" />
    </div>
    <div v-if="slots.advanced" v-show="advancedExpanded" :id="advancedId" class="mrr-filter-bar__advanced">
      <slot name="advanced" />
    </div>
    <p v-if="slots.advanced && props.appliedAdvancedCount !== undefined && (props.appliedAdvancedCount > 0 || props.advancedDirty)" class="mrr-filter-bar__summary" role="status">
      列表已应用 {{ props.appliedAdvancedCount }} 项高级条件<span v-if="props.advancedDirty"> · 当前高级条件与列表结果不同</span>
    </p>
  </div>
</template>

<style scoped>
.mrr-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3) var(--mrr-space-2);
  align-items: center;
  justify-content: flex-start;
  min-width: 0;
  padding: var(--mrr-space-3) var(--mrr-card-inset);
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
  box-shadow: var(--mrr-shadow-xs);
  transition: border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.mrr-filter-bar--focus-ring:focus-within {
  border-color: color-mix(in srgb, var(--mrr-ring) 46%, var(--mrr-border));
  box-shadow: var(--mrr-shadow-xs), 0 0 0 3px color-mix(in srgb, var(--mrr-ring) 10%, transparent);
}

.mrr-filter-bar--compact {
  padding: var(--mrr-space-2) var(--mrr-card-inset-compact);
  border-radius: var(--mrr-radius-md);
}

.mrr-filter-bar.mrr-filter-bar--embedded {
  padding: 0 0 var(--mrr-embedded-filter-bottom-space, var(--mrr-space-3));
  background: transparent;
  border: 0;
  border-bottom: var(--mrr-embedded-filter-divider-width, 1px) solid var(--mrr-shell-divider);
  border-radius: 0;
  box-shadow: none;
}

/* Shared semantic field widths. Native controls still own height, colors and focus. */
.mrr-filter-bar__fields,
.mrr-filter-bar__advanced {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-3) var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  max-width: 100%;
}

/* On wide/medium containers, individual fields and the query pair share one
 * wrapping flow. This avoids stranding the query pair on a third row. */
.mrr-filter-bar:not(.mrr-filter-bar--narrow):not(.mrr-filter-bar--grid) .mrr-filter-bar__fields {
  display: contents;
}

.mrr-filter-bar__fields > :deep(*),
.mrr-filter-bar__advanced > :deep(*) {
  flex: 0 1 144px;
  width: 144px;
  min-width: 0;
  max-width: 100%;
  margin: 0;
}

.mrr-filter-bar :deep(.mrr-filter-field--code) {
  flex: 0 1 160px;
  width: 160px;
}

.mrr-filter-bar :deep(.mrr-filter-field--keyword) {
  flex: 1 1 280px;
  width: 280px;
  max-width: 360px;
}

.mrr-filter-bar :deep(.mrr-filter-field--range) {
  flex: 1 1 300px;
  width: 300px;
  max-width: 360px;
}

.mrr-filter-bar :deep(.mrr-filter-field--datetime) {
  flex: 1 1 380px;
  width: 380px;
  max-width: 100%;
}

/* Form-item labels have their own width; don't steal from usable input space. */
.mrr-filter-bar :deep(.el-form-item:not(.mrr-filter-field--keyword):not(.mrr-filter-field--range):not(.mrr-filter-field--datetime)) {
  flex-basis: 200px;
  width: 200px;
}

.mrr-filter-bar :deep(.el-form-item.mrr-filter-field--datetime) {
  flex-basis: 420px;
  width: 420px;
}

.mrr-filter-bar__summary {
  flex: 1 1 100%;
  margin: 0;
  font-size: var(--mrr-font-size-xs);
  line-height: var(--mrr-line-height-label);
  color: var(--mrr-muted-foreground);
}

.mrr-filter-bar :deep(.mrr-filter-field--option) {
  flex: 0 1 auto;
  width: auto;
}

.mrr-filter-bar :deep(.mrr-filter-field > .el-input),
.mrr-filter-bar :deep(.mrr-filter-field > .el-select),
.mrr-filter-bar :deep(.el-form-item__content > .el-input),
.mrr-filter-bar :deep(.el-form-item__content > .el-select),
.mrr-filter-bar :deep(.el-form-item__content > .el-date-editor),
.mrr-filter-bar :deep(.el-form-item__content > .el-input-number) {
  width: 100%;
  min-width: 0;
}

.mrr-filter-bar :deep(.mrr-filter-prefix) {
  flex: none;
  font-size: var(--mrr-font-size-xs);
  font-weight: var(--mrr-font-weight-label);
  color: var(--mrr-muted-foreground);
  white-space: nowrap;
}

.mrr-filter-bar :deep(.el-form-item) {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin: 0;
}

.mrr-filter-bar :deep(.el-form-item__content) {
  min-width: 0;
}

.mrr-filter-bar :deep(.el-form-item__label) {
  flex: none;
  padding-right: var(--mrr-space-2);
  font-size: var(--mrr-font-size-xs);
  font-weight: var(--mrr-font-weight-label);
  line-height: var(--mrr-line-height-label);
  color: var(--mrr-muted-foreground);
}

.mrr-filter-bar__actions,
.mrr-filter-bar__business {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  max-width: 100%;
}

.mrr-filter-bar__actions :deep(.el-button),
.mrr-filter-bar__business :deep(.el-button) {
  flex: none;
  min-width: 72px;
  margin: 0;
}

.mrr-filter-bar__business {
  margin-inline-start: auto;
}

.mrr-filter-bar__advanced {
  flex: 1 1 100%;
  padding-top: var(--mrr-space-3);
  border-top: 1px solid var(--mrr-shell-divider);
}

/* Explicit grid stays available for schema-driven or complex labelled fields. */
.mrr-filter-bar--labelled :deep(.mrr-filter-field) {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
}

.mrr-filter-bar--labelled :deep(.mrr-filter-field > label),
.mrr-filter-bar--labelled :deep(.mrr-filter-field__label) {
  font-size: var(--mrr-font-size-xs);
  font-weight: var(--mrr-font-weight-label);
  line-height: var(--mrr-line-height-label);
  color: var(--mrr-card-foreground);
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
  width: 100%;
}

.mrr-filter-bar--grid .mrr-filter-bar__fields > :deep(*) {
  width: 100%;
  max-width: 100%;
}

.mrr-filter-bar--grid .mrr-filter-bar__fields[data-grid-columns="2"] {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mrr-filter-bar--grid .mrr-filter-bar__fields[data-grid-columns="1"] {
  grid-template-columns: minmax(0, 1fr);
}

.mrr-filter-bar--grid .mrr-filter-bar__actions {
  justify-content: flex-end;
}

.mrr-filter-bar--narrow {
  flex-direction: column;
  align-items: stretch;
}

.mrr-filter-bar--narrow:not(.mrr-filter-bar--grid) .mrr-filter-bar__fields,
.mrr-filter-bar--narrow .mrr-filter-bar__advanced {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}

.mrr-filter-bar--tiny:not(.mrr-filter-bar--grid) .mrr-filter-bar__fields,
.mrr-filter-bar--tiny .mrr-filter-bar__advanced {
  grid-template-columns: minmax(0, 1fr);
}

.mrr-filter-bar--narrow .mrr-filter-bar__fields > :deep(*),
.mrr-filter-bar--narrow .mrr-filter-bar__advanced > :deep(*) {
  width: 100%;
  max-width: 100%;
}

.mrr-filter-bar--narrow .mrr-filter-bar__advanced > :deep(.el-form-item),
.mrr-filter-bar--narrow .mrr-filter-bar__fields > :deep(.el-form-item) {
  width: 100%;
}

.mrr-filter-bar--narrow :deep(.mrr-filter-field--keyword),
.mrr-filter-bar--narrow :deep(.mrr-filter-field--range),
.mrr-filter-bar--narrow :deep(.mrr-filter-field--datetime),
.mrr-filter-bar--narrow :deep(.mrr-filter-field--option),
.mrr-filter-bar--narrow :deep(.el-form-item) {
  grid-column: 1 / -1;
}

.mrr-filter-bar--narrow .mrr-filter-bar__actions,
.mrr-filter-bar--narrow .mrr-filter-bar__business {
  justify-content: flex-end;
  width: 100%;
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

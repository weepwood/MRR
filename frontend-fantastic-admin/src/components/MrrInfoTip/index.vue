<script setup lang="ts">
import { Info } from 'lucide-vue-next'

defineOptions({ name: 'MrrInfoTip' })

/**
 * 标题旁的说明图标：悬停或键盘聚焦时显示补充说明。
 *
 * 此前权限管理、数据交换、认证测试台各写一份 `button.info-trigger` + 样式，
 * 颜色分别取 `--text-secondary` 与 Element Plus 变量，焦点环也不一致。
 * 说明文字较长或需要换行时使用默认插槽，否则用 `content`。
 */
withDefaults(defineProps<{
  /** 按钮的可访问名称，读屏只会读到它，必须说明「这是什么的说明」。 */
  label: string
  content?: string
  placement?: 'top' | 'bottom' | 'left' | 'right'
}>(), {
  placement: 'right',
})
</script>

<template>
  <el-tooltip :content="content" :placement="placement" :show-after="200">
    <template v-if="$slots.default" #content>
      <slot />
    </template>
    <button type="button" class="mrr-info-tip" :aria-label="label" data-corner="round">
      <el-icon aria-hidden="true">
        <Info />
      </el-icon>
    </button>
  </el-tooltip>
</template>

<style scoped>
.mrr-info-tip {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: var(--mrr-space-1);
  color: var(--mrr-muted-foreground);
  cursor: help;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-pill);
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out, ease);
}

.mrr-info-tip:hover,
.mrr-info-tip:focus-visible {
  color: var(--color-primary);
}

/* 焦点只用 outline（DESIGN.md §5.2）；--mrr-ring 依赖 color-mix()，Chrome 86 下无效，这里用主色。 */
.mrr-info-tip:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
</style>

<script setup lang="ts">
import type { TooltipInstance } from 'element-plus'

defineOptions({ name: 'MrrTooltip', inheritAttrs: false })

withDefaults(defineProps<{
  content?: string
  placement?: 'top' | 'bottom' | 'left' | 'right'
  showAfter?: number
  disabled?: boolean
  ariaLabel?: string
}>(), {
  placement: 'top',
  showAfter: 300,
})

const tooltip = ref<TooltipInstance>()

function dismiss(event: KeyboardEvent) {
  // Do not consume Escape: an enclosing dialog may also need it.
  if (event.key === 'Escape') { tooltip.value?.hide() }
}
function listen() {
  document.addEventListener('keydown', dismiss)
}
function unlisten() {
  document.removeEventListener('keydown', dismiss)
}
watch(() => tooltip.value, (instance) => {
  if (!instance) { unlisten() }
})
onBeforeUnmount(unlisten)
</script>

<template>
  <el-tooltip
    v-if="!disabled"
    ref="tooltip"
    v-bind="$attrs"
    :content="content"
    :placement="placement"
    :show-after="showAfter"
    :hide-after="0"
    :aria-label="ariaLabel"
    :trigger="['hover', 'focus']"
    :trigger-keys="[]"
    :show-arrow="false"
    :offset="4"
    effect="light"
    popper-class="mrr-tooltip"
    @before-show="listen"
    @before-hide="unlisten"
  >
    <slot />
    <template v-if="$slots.content" #content>
      <slot name="content" />
    </template>
  </el-tooltip>
  <slot v-else />
</template>

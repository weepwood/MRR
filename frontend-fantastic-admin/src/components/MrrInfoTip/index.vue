<script setup lang="ts">
import { onClickOutside, useEventListener } from '@vueuse/core'

defineOptions({ name: 'MrrInfoTip' })

/**
 * Shared information trigger. Hover/focus opens, click pins the explanation
 * for touch and mouse users; Escape, outside click or second click dismisses.
 */
const props = withDefaults(defineProps<{
  label: string
  content?: string
  placement?: 'top' | 'bottom' | 'left' | 'right'
}>(), {
  placement: 'right',
})
const trigger = ref<HTMLElement>()
const contentRef = ref<HTMLElement>()
const visible = ref(false)
const pinned = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | undefined
function show() {
  clearTimeout(closeTimer)
  visible.value = true
}
function close() {
  clearTimeout(closeTimer)
  pinned.value = false
  visible.value = false
}
function leave() {
  clearTimeout(closeTimer)
  if (!pinned.value && document.activeElement !== trigger.value) {
    closeTimer = setTimeout(close, 180)
  }
}
function activate() {
  if (pinned.value) {
    close()
  }
  else {
    pinned.value = true
    show()
  }
}
function blur() {
  if (!pinned.value) { leave() }
}
onClickOutside(trigger, close, { ignore: [contentRef] })
useEventListener(document, 'keydown', (event) => {
  if (event.key === 'Escape' && visible.value) { close() }
})
onBeforeUnmount(() => clearTimeout(closeTimer))
</script>

<template>
  <span class="mrr-info-tip-wrap">
    <MrrTooltip :visible="visible" :placement="props.placement" :show-after="0" :enterable="true">
      <template #content>
        <div ref="contentRef" class="mrr-info-tip-content" @pointerenter="show" @pointerleave="leave">
          <slot>{{ props.content }}</slot>
        </div>
      </template>
      <button
        ref="trigger"
        type="button"
        class="mrr-info-tip"
        :aria-label="props.label"
        :aria-description="props.content"
        :aria-expanded="visible"
        data-corner="round"
        @pointerenter="show"
        @pointerleave="leave"
        @focus="show"
        @blur="blur"
        @click.stop="activate"
      >
        <FaIcon name="i-lucide:info" aria-hidden="true" />
      </button>
    </MrrTooltip>
  </span>
</template>

<style scoped>
.mrr-info-tip-wrap {
  display: inline-flex;
  flex: none;
}

.mrr-info-tip {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 16px;
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

/* Tooltip is teleported; scoped selector still follows the actual slot node. */
.mrr-info-tip-content {
  max-width: min(320px, calc(100vw - 48px));
  overflow-wrap: anywhere;
  white-space: normal;
}
</style>

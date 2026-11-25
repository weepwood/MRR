<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: number
  min: number
  max: number
  defaultWidth: number
  label: string
  controls: string
  side?: 'left' | 'right'
}>(), { side: 'left' })
const emit = defineEmits<{ 'update:modelValue': [width: number] }>()
const handle = ref<HTMLElement>()
const dragging = ref(false)
let drag: { pointerId: number, x: number, width: number } | undefined
function setWidth(width: number) {
  emit('update:modelValue', Math.round(Math.max(props.min, Math.min(props.max, width))))
}
function cancel(event?: PointerEvent) {
  if (event && drag?.pointerId !== event.pointerId) { return }
  const active = drag
  drag = undefined
  dragging.value = false
  if (active && handle.value?.hasPointerCapture(active.pointerId)) { handle.value.releasePointerCapture(active.pointerId) }
}
function start(event: PointerEvent) {
  const el = handle.value
  const pane = props.side === 'left' ? el?.previousElementSibling : el?.nextElementSibling
  if (!el || !pane || event.button !== 0 || drag) { return }
  event.preventDefault()
  el.focus({ preventScroll: true })
  el.setPointerCapture(event.pointerId)
  drag = { pointerId: event.pointerId, x: event.clientX, width: pane.getBoundingClientRect().width }
  dragging.value = true
}
function move(event: PointerEvent) {
  if (!drag || drag.pointerId !== event.pointerId) { return }
  if ((event.pointerType === 'mouse' && !(event.buttons & 1)) || !handle.value?.getClientRects().length) { cancel(event); return }
  setWidth(drag.width + (event.clientX - drag.x) * (props.side === 'left' ? 1 : -1))
}
function reset() {
  cancel()
  setWidth(props.defaultWidth)
}
function keyboard(event: KeyboardEvent) {
  if (event.ctrlKey || event.altKey || event.metaKey) { return }
  const step = props.side === 'left' ? 10 : -10
  const changes: Record<string, number> = {
    ArrowLeft: props.modelValue - step,
    ArrowRight: props.modelValue + step,
    Home: props.min,
    End: props.max,
    Enter: props.defaultWidth,
  }
  const next = changes[event.key]
  if (typeof next !== 'number') { return }
  event.preventDefault()
  cancel()
  setWidth(next)
}
defineExpose({ cancel })
onBeforeUnmount(() => cancel())
</script>

<template>
  <div ref="handle" class="mrr-resize-handle" :class="{ 'is-dragging': dragging }" role="separator" tabindex="0" :aria-label="label" aria-orientation="vertical" :aria-controls="controls" :aria-valuenow="modelValue" :aria-valuemin="min" :aria-valuemax="max" :aria-valuetext="`目标宽度 ${modelValue} 像素`" title="拖动或方向键调整；双击或 Enter 复位" @pointerdown="start" @pointermove="move" @pointerup="cancel" @pointercancel="cancel" @lostpointercapture="cancel" @keydown="keyboard" @dblclick="reset">
    <span aria-hidden="true" />
  </div>
</template>

<style scoped>
.mrr-resize-handle {
  display: flex;
  align-self: stretch;
  justify-content: center;
  min-height: 44px;
  touch-action: none;
  cursor: col-resize;
  user-select: none;
}

.mrr-resize-handle span {
  width: 2px;
  background: var(--mrr-border);
  border-radius: var(--mrr-radius-sm);
}

.mrr-resize-handle:hover span,
.mrr-resize-handle.is-dragging span {
  background: var(--mrr-primary);
}

.mrr-resize-handle:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: -2px;
}
</style>

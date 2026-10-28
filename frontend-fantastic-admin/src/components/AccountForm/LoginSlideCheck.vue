<script setup lang="ts">
import { useResizeObserver } from '@vueuse/core'
import { ArrowRight, Check } from 'lucide-vue-next'
import { computed, ref, useId, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  disabled?: boolean
  invalid?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const labelId = useId()
const hintId = useId()
const track = ref<HTMLElement | null>(null)
const handle = ref<HTMLButtonElement | null>(null)
const progress = ref(props.modelValue ? 100 : 0)
const travel = ref(0)
const dragging = ref(false)
let pointerId: number | null = null
let startX = 0
let startProgress = 0
const hint = computed(() => props.invalid && !props.modelValue ? '请先向右拖动滑块至尽头' : '')

function measureTravel() {
  travel.value = Math.max(0, (track.value?.clientWidth ?? 0) - (handle.value?.offsetWidth ?? 0) - 6)
}
useResizeObserver(track, measureTravel)

function reset() {
  dragging.value = false
  const capturedPointer = pointerId
  pointerId = null
  if (capturedPointer !== null && handle.value?.hasPointerCapture(capturedPointer)) {
    handle.value.releasePointerCapture(capturedPointer)
  }
  progress.value = props.modelValue ? 100 : 0
}

watch(() => props.modelValue, reset)
watch(() => props.disabled, (disabled) => {
  if (disabled) { reset() }
})

function startDrag(event: PointerEvent) {
  if (props.disabled || props.modelValue || !event.isPrimary || event.button !== 0) { return }
  measureTravel()
  if (travel.value <= 0) { return }
  startX = event.clientX
  startProgress = progress.value
  pointerId = event.pointerId
  dragging.value = true
  handle.value?.focus({ preventScroll: true })
  handle.value?.setPointerCapture(event.pointerId)
}

function moveDrag(event: PointerEvent) {
  if (!dragging.value || pointerId !== event.pointerId) { return }
  progress.value = Math.max(0, Math.min(100, startProgress + (event.clientX - startX) / travel.value * 100))
}

function finishDrag(event: PointerEvent) {
  if (!dragging.value || pointerId !== event.pointerId) { return }
  const complete = progress.value >= 98
  reset()
  if (complete) {
    progress.value = 100
    emit('update:modelValue', true)
  }
}

function cancelDrag(event: PointerEvent) {
  if (pointerId === event.pointerId) { reset() }
}

function handleKeydown(event: KeyboardEvent) {
  if (props.disabled || props.modelValue || dragging.value) { return }
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) { return }
  event.preventDefault()
  if (event.key === 'ArrowRight') { progress.value = Math.min(100, progress.value + 10) }
  if (event.key === 'ArrowLeft') { progress.value = Math.max(0, progress.value - 10) }
  if (event.key === 'Home') { progress.value = 0 }
  if (event.key === 'End') { progress.value = 100 }
  if (progress.value === 100) { emit('update:modelValue', true) }
}
</script>

<template>
  <div class="slide-check" :class="{ 'is-complete': modelValue, 'is-dragging': dragging, 'is-invalid': invalid, 'is-disabled': disabled }">
    <div class="slide-check__heading">
      <span :id="labelId" class="slide-check__label">滑动验证</span>
      <span :id="hintId" class="slide-check__hint" role="status">{{ hint }}</span>
    </div>
    <div ref="track" class="slide-check__track">
      <div class="slide-check__fill" :style="{ transform: `scaleX(${progress / 100})` }" />
      <span class="slide-check__text" aria-hidden="true">{{ modelValue ? '验证成功' : '按住滑块，向右拖动' }}</span>
      <button
        ref="handle"
        class="slide-check__handle"
        type="button"
        role="slider"
        aria-orientation="horizontal"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="Math.round(progress)"
        :aria-valuetext="modelValue ? '验证成功' : `已向右滑动 ${Math.round(progress)}%`"
        :aria-labelledby="labelId"
        :aria-describedby="hintId"
        :aria-invalid="invalid || undefined"
        :disabled="disabled || modelValue"
        :style="{ transform: `translate3d(${travel * progress / 100}px, 0, 0)` }"
        @pointerdown.prevent="startDrag"
        @pointermove="moveDrag"
        @pointerup="finishDrag"
        @pointercancel="cancelDrag"
        @lostpointercapture="cancelDrag"
        @keydown="handleKeydown"
        @blur="reset"
      >
        <Check v-if="modelValue" aria-hidden="true" :size="16" />
        <ArrowRight v-else aria-hidden="true" :size="16" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.slide-check { display: flex; flex-direction: column; gap: var(--mrr-space-1); }
.slide-check__heading { display: flex; gap: var(--mrr-space-2); align-items: center; justify-content: space-between; min-height: 18px; }
.slide-check__label { font-size: 13px; font-weight: 650; color: var(--mrr-foreground); }
.slide-check__track { position: relative; height: var(--auth-input-height, var(--mrr-input-height)); overflow: hidden; background: var(--mrr-muted); border: 1px solid var(--mrr-control-border); border-radius: var(--mrr-control-radius); }
.slide-check__fill { position: absolute; top: 0; bottom: 0; left: 0; width: 100%; pointer-events: none; background: var(--el-color-primary-light-9); transform-origin: left center; transition: transform 240ms cubic-bezier(0.4, 0, 0.2, 1); }
.slide-check__text { position: relative; display: flex; align-items: center; justify-content: center; height: 100%; font-size: 11px; color: var(--mrr-muted-foreground); pointer-events: none; user-select: none; }
.slide-check__handle { position: absolute; top: 3px; left: 3px; display: grid; place-items: center; width: var(--mrr-control-height); height: calc(100% - 6px); padding: 0; color: var(--mrr-primary); touch-action: none; cursor: grab; user-select: none; background: var(--mrr-card); border: 1px solid var(--mrr-control-border); border-radius: var(--mrr-radius-sm); transition: transform 240ms cubic-bezier(0.4, 0, 0.2, 1); }
.slide-check__handle:focus-visible { outline: 2px solid var(--mrr-ring); outline-offset: -2px; }
.slide-check__hint { min-height: 16px; margin: 0; font-size: 11px; line-height: 16px; color: var(--mrr-muted-foreground); }
.is-dragging .slide-check__handle { cursor: grabbing; transition: none; }
.is-dragging .slide-check__fill { transition: none; }
.is-complete .slide-check__track { border-color: var(--el-color-success); }
.is-complete .slide-check__fill { background: var(--el-color-success-light-9); }
.is-complete .slide-check__handle { color: var(--el-color-success); cursor: default; }
.is-complete .slide-check__text { color: var(--mrr-muted-foreground); }
.is-invalid .slide-check__track { border-color: var(--mrr-destructive); }
.is-invalid .slide-check__hint { color: var(--mrr-destructive); }
.is-disabled { opacity: 0.6; }
.is-disabled .slide-check__handle { cursor: wait; }

@media (prefers-reduced-motion: reduce) {
  .slide-check__handle,
  .slide-check__fill { transition: none; }
}
</style>

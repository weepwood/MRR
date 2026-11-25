<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useResizeObserver, useScroll } from '@vueuse/core'
import { cn } from '@/utils'
import { isMotionEnabled } from '@/utils/motion-preference'
import { ScrollArea, ScrollBar } from './scroll-area'

defineOptions({
  name: 'FaScrollArea',
})

const props = withDefaults(
  defineProps<{
    horizontal?: boolean
    scrollbar?: boolean
    mask?: boolean
    gradientColor?: string
    class?: HTMLAttributes['class']
    contentClass?: HTMLAttributes['class']
  }>(),
  {
    horizontal: false,
    scrollbar: true,
    mask: false,
    gradientColor: 'hsl(var(--background))',
  },
)

const emits = defineEmits<{
  onScroll: [Event]
}>()

const scrollAreaRef = useTemplateRef('scrollAreaRef')

// Consumers depend on this stable viewport contract, not the nested Reka refs.
const viewport = computed(() => scrollAreaRef.value?.el?.viewportElement)
const content = computed(() => viewport.value?.firstElementChild as HTMLElement | null)
const { arrivedState, measure } = useScroll(viewport)
const measured = ref(false)
function measureViewport() {
  if (viewport.value) {
    measure()
    measured.value = true
  }
}
watch(viewport, measureViewport, { flush: 'post' })
useResizeObserver([viewport, content], measureViewport)

const showMaskStart = computed(() => measured.value && !(props.horizontal ? arrivedState.left : arrivedState.top))
const showMaskEnd = computed(() => measured.value && !(props.horizontal ? arrivedState.right : arrivedState.bottom))

function onScroll(event: Event) {
  emits('onScroll', event)
}

function onWheel(event: WheelEvent) {
  const el = viewport.value
  // Preserve native horizontal trackpad gestures and browser zoom.
  if (!props.horizontal || !el || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || Math.abs(event.deltaX) >= Math.abs(event.deltaY)) { return }
  const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? el.clientWidth : 1
  const direction = getComputedStyle(el).direction === 'rtl' ? -1 : 1
  const delta = event.deltaY * unit * direction
  const max = Math.max(0, el.scrollWidth - el.clientWidth)
  const target = direction === 1
    ? Math.max(0, Math.min(max, el.scrollLeft + delta))
    : Math.min(0, Math.max(-max, el.scrollLeft + delta))
  // Only consume a wheel gesture when the strip can actually scroll.
  if (Math.abs(target - el.scrollLeft) < 0.5) { return }
  event.preventDefault()
  el.scrollLeft = target
}

function scrollTo(scrollNumber: number) {
  // JS 的 behavior 选项不受 CSS scroll-behavior 约束，必须在这里单独读取开关。
  const behavior = isMotionEnabled() ? 'smooth' : 'auto'
  if (props.horizontal) {
    viewport.value?.scrollTo({
      left: scrollNumber,
      behavior,
    })
  }
  else {
    viewport.value?.scrollTo({
      top: scrollNumber,
      behavior,
    })
  }
}

defineExpose({
  ref: scrollAreaRef,
  viewport,
  scrollTo,
})
</script>

<template>
  <div
    :data-mask-start="props.mask && showMaskStart" :data-mask-end="props.mask && showMaskEnd" :class="cn('relative flex overflow-hidden after:(pointer-events-none absolute z-1 from-transparent to-[var(--mask-scroll-container-gradient-color)] opacity-0 transition-opacity content-empty) before:(pointer-events-none absolute z-1 from-transparent to-[var(--mask-scroll-container-gradient-color)] opacity-0 transition-opacity content-empty)', {
      'after:(bg-gradient-to-r rtl:bg-gradient-to-l h-full w-12 end-0) before:(bg-gradient-to-l rtl:bg-gradient-to-r h-full w-12 start-0)': props.horizontal,
      'after:(bg-gradient-to-b h-12 w-full bottom-0) before:(bg-gradient-to-t h-12 w-full)': !props.horizontal,
      'before:(opacity-100!)': props.mask && showMaskStart,
      'after:(opacity-100!)': props.mask && showMaskEnd,
    }, props.class)" :style="props.mask ? {
      '--mask-scroll-container-gradient-color': props.gradientColor,
    } : {}"
  >
    <ScrollArea ref="scrollAreaRef" :class="cn('relative z-0 flex-1', props.contentClass)" :scrollbar="props.scrollbar" :on-scroll="onScroll" :on-wheel="onWheel">
      <slot />
      <ScrollBar v-if="props.horizontal" orientation="horizontal" :class="{ 'opacity-0 pointer-events-none': !props.scrollbar }" />
    </ScrollArea>
  </div>
</template>

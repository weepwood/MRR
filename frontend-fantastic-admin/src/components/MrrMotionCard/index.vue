<script setup lang="ts">
import { motion } from 'motion-v'
import { computed } from 'vue'

/**
 * MrrMotionCard —— Motion(motion-v) 试点组件（ADR-0007）
 *
 * 仅在「spring / layout / 手势 / scroll-linked」这类纯 CSS 难做、且能带来
 * 明确体验增益的场景下使用。通用入场/淡入仍走 motion.css + UnoCSS + Vue Transition。
 *
 * 无障碍：尊重 prefers-reduced-motion，降级为无动画静态渲染。
 */
const props = withDefaults(
  defineProps<{
    /** 是否使用 spring 物理动画，默认 true */
    spring?: boolean
    /** 入场纵向位移(px)，默认 8 */
    offset?: number
  }>(),
  { spring: true, offset: 8 },
)

const reduced = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
  : false

const initial = computed(() =>
  reduced ? { opacity: 1, y: 0 } : { opacity: 0, y: props.offset },
)
const animate = { opacity: 1, y: 0 }
const transition = computed(() =>
  reduced
    ? { duration: 0 }
    : props.spring
      ? { type: 'spring', stiffness: 300, damping: 24 }
      : { duration: 0.18 },
)
</script>

<template>
  <motion.div
    :initial="initial"
    :animate="animate"
    :transition="transition"
    class="mrr-motion-card"
  >
    <slot />
  </motion.div>
</template>

<style scoped>
.mrr-motion-card {
  will-change: transform, opacity;
}
</style>

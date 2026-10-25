<script setup lang="ts">
import { motion } from 'motion-v'
import { computed } from 'vue'

/**
 * MrrStagger —— Motion(motion-v) 列表级 stagger 容器（ADR-0007 局部增强层）
 *
 * 用途：让直接子级（应为 MrrStaggerItem）以「依次入场」方式出现。
 * 仅用 opacity / translateY + spring，不依赖 Chrome 86 不支持的
 * ScrollTimeline / View Transitions / CSS linear() 等特性。
 *
 * 无障碍：尊重 prefers-reduced-motion，降级为静态（无 stagger、无位移）。
 */
const props = withDefaults(
  defineProps<{
    /** 子项入场间隔（秒），默认 0.05 */
    stagger?: number
    /** 是否使用 spring 物理动画，默认 true */
    spring?: boolean
    /** 容器标签，默认 div */
    as?: 'div' | 'ul' | 'section'
    /** 仅入场一次（mount 触发），默认 true；false 则进入视口时触发 */
    once?: boolean
  }>(),
  { stagger: 0.05, spring: true, as: 'div', once: true },
)

const reduced = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
  : false

const Tag = computed(() => (motion as Record<string, unknown>)[props.as] ?? motion.div)

const containerVariants = computed(() => ({
  hidden: {},
  show: {
    transition: reduced
      ? { duration: 0 }
      : { staggerChildren: props.stagger, delayChildren: 0.02 },
  },
}))

const whileInView = computed(() => (props.once ? undefined : 'show'))
</script>

<template>
  <component
    :is="Tag"
    :variants="containerVariants"
    initial="hidden"
    animate="show"
    :while-in-view="whileInView"
  >
    <slot />
  </component>
</template>

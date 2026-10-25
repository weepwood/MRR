<script setup lang="ts">
import { motion } from 'motion-v'
import { computed } from 'vue'
import { useMotionReduced } from '@/composables/useMotionReduced'

/**
 * MrrStaggerItem —— Motion(motion-v) 列表单元（ADR-0007 局部增强层）
 *
 * 必须与 MrrStagger 配合使用：父容器 orchestrate 变体后，本组件按
 * 相同 "hidden"/"show" 变体名自动参与 stagger。
 *
 * 为避免动画结束后残留的 inline transform 覆盖宿主元素的 :hover/:active
 * 变形，本组件只负责「入场」；交互态（hover/按压）请放在本组件内部元素上。
 *
 * 无障碍：尊重 prefers-reduced-motion，降级为静态渲染。
 */
const props = withDefaults(
  defineProps<{
    /** 是否使用 spring 物理动画，默认 true */
    spring?: boolean
    /** 渲染标签，默认 div（仪表盘用 div 作网格单元，列表可用 button/li） */
    as?: 'div' | 'li' | 'article' | 'button' | 'section'
    /** 入场纵向位移(px)，默认 10 */
    offset?: number
  }>(),
  { spring: true, as: 'div', offset: 10 },
)

const reduced = useMotionReduced()

const Tag = computed(() => (motion as Record<string, unknown>)[props.as] ?? motion.div)

const variants = computed(() => ({
  hidden: reduced ? { opacity: 1, y: 0 } : { opacity: 0, y: props.offset },
  show: {
    opacity: 1,
    y: 0,
    transition: reduced
      ? { duration: 0 }
      : props.spring
        ? { type: 'spring', stiffness: 320, damping: 26 }
        : { duration: 0.2, ease: 'easeOut' },
  },
}))
</script>

<template>
  <component :is="Tag" :variants="variants" initial="hidden">
    <slot />
  </component>
</template>

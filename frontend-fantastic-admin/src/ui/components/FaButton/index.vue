<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import type { ButtonVariants } from './button'
import { Button } from './button'

defineOptions({
  name: 'FaButton',
})

const props = defineProps<{
  variant?: ButtonVariants['variant']
  size?: ButtonVariants['size']
  class?: HTMLAttributes['class']
  disabled?: boolean
  loading?: boolean
}>()
</script>

<template>
  <!--
    data-size / data-variant / data-loading 是对外观契约的显式声明：
    组件层（form-controls.css）与主题层按这三个属性接管状态样式，
    测试也靠它们比对与 el-button 是否同口径。加载态保留变体配色（与 el-button 一致），
    因此额外标记 data-loading，避免被禁用态的灰底覆盖。
  -->
  <Button
    :variant
    :size
    :disabled="props.disabled || props.loading"
    :aria-busy="props.loading || undefined"
    :data-variant="props.variant ?? 'default'"
    :data-size="props.size ?? 'default'"
    :data-loading="props.loading || undefined"
    :class="[props.class]"
  >
    <FaIcon v-if="loading" name="i-lucide:loader-circle" class="animate-spin" aria-hidden="true" />
    <slot />
  </Button>
</template>

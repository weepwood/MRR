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
  <Button :variant :size :disabled="props.disabled || props.loading" :aria-busy="props.loading || undefined" :data-variant="props.variant ?? 'default'" class="fa-button-control" :class="[props.class]">
    <FaIcon v-if="loading" name="i-lucide:loader-circle" class="animate-spin" aria-hidden="true" />
    <slot />
  </Button>
</template>

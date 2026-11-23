<script setup lang="ts">
defineProps<{ navigationLabel?: string }>()
</script>

<template>
  <component :is="navigationLabel ? 'nav' : 'div'" class="flex items-center text-sm" :aria-label="navigationLabel">
    <slot />
  </component>
</template>

<style scoped>
:deep(.breadcrumb-item) {
  &:first-child {
    .separator {
      display: none;
    }
  }

  &:last-child {
    .text {
      opacity: 1;
    }
  }
}
</style>

<script setup lang="ts">
type PageWidth = 'fluid' | 'standard' | 'narrow'

const props = withDefaults(defineProps<{
  width?: PageWidth
  compact?: boolean
}>(), {
  width: 'fluid',
  compact: false,
})
</script>

<template>
  <div
    class="mrr-page-shell"
    :class="[
      `mrr-page-shell--${props.width}`,
      { 'mrr-page-shell--compact': props.compact },
    ]"
  >
    <slot />
  </div>
</template>

<style scoped>
.mrr-page-shell {
  display: grid;
  gap: var(--mrr-page-gap);
  width: 100%;
  min-width: 0;
  margin: 0 auto;
}

.mrr-page-shell--fluid {
  max-width: var(--mrr-content-fluid);
}

.mrr-page-shell--standard {
  max-width: var(--mrr-content-standard);
}

.mrr-page-shell--narrow {
  max-width: var(--mrr-content-narrow);
}

.mrr-page-shell--compact {
  gap: var(--mrr-space-4);
}

@media (width <= 640px) {
  .mrr-page-shell {
    gap: var(--mrr-space-4);
  }
}
</style>

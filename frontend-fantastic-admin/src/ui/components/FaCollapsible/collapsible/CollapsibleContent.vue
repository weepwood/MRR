<script setup lang="ts">
import type { CollapsibleContentProps } from 'reka-ui'
import { CollapsibleContent } from 'reka-ui'

const props = defineProps<CollapsibleContentProps>()
</script>

<template>
  <CollapsibleContent v-bind="props" class="CollapsibleContent">
    <slot />
  </CollapsibleContent>
</template>

<style scoped>
.CollapsibleContent {
  overflow: hidden;
}

.CollapsibleContent[data-state="open"] {
  animation: collapsible-down var(--mrr-motion-move) var(--mrr-ease-out);
}

.CollapsibleContent[data-state="closed"] {
  animation: collapsible-up var(--mrr-motion-move) var(--mrr-ease-in);
}

@keyframes collapsible-down {
  from {
    height: 0;
  }

  to {
    height: var(--reka-collapsible-content-height);
  }
}

@keyframes collapsible-up {
  from {
    height: var(--reka-collapsible-content-height);
  }

  to {
    height: 0;
  }
}
</style>

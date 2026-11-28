<script setup lang="ts">
defineProps<{ variant: 'missing' | 'forbidden' | 'failure' }>()
</script>

<template>
  <svg class="mrr-error-art" :data-error-art="variant" viewBox="0 0 240 176" fill="none" aria-hidden="true" focusable="false">
    <rect x="16" y="17" width="208" height="142" rx="24" class="art-surface" />
    <path d="M32 42V33h10m156 0h10v9M32 134v9h10m156 0h10v-9" class="art-detail" />
    <rect x="63" y="29" width="99" height="121" rx="9" class="art-back" transform="rotate(7 112 89)" />
    <path d="M64 26h61l25 25v85a8 8 0 0 1-8 8H64a8 8 0 0 1-8-8V34a8 8 0 0 1 8-8Z" class="art-paper" />
    <path d="M125 26v17a8 8 0 0 0 8 8h17" class="art-line" />
    <path d="M74 69h56M74 82h42M74 95h30" class="art-detail" stroke-width="3" />
    <template v-if="variant === 'missing'">
      <circle cx="155" cy="113" r="26" class="art-paper" />
      <path d="m174 132 18 18" class="art-accent" stroke-width="6" />
      <path d="M144 113h22" class="art-accent" stroke-width="3" />
    </template>
    <template v-else-if="variant === 'forbidden'">
      <rect x="132" y="101" width="49" height="40" rx="8" class="art-paper" />
      <path d="M143 101V90a13.5 13.5 0 0 1 27 0v11" class="art-accent" stroke-width="3" />
      <path d="M156.5 117v10" class="art-accent" stroke-width="4" />
    </template>
    <template v-else>
      <circle cx="156" cy="117" r="25" class="art-paper" />
      <path d="M156 104v15m0 9v1" class="art-accent" stroke-width="4" />
    </template>
  </svg>
</template>

<style scoped>
.mrr-error-art {
  display: block;
  width: 100%;
  height: auto;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.art-surface { fill: var(--mrr-muted); }
.art-back { fill: var(--mrr-muted); stroke: var(--mrr-border-strong); }
.art-paper { fill: var(--mrr-card); stroke: var(--mrr-muted-foreground); }
.art-line { stroke: var(--mrr-muted-foreground); }
.art-detail { opacity: 0.45; stroke: var(--mrr-muted-foreground); }
.art-accent { stroke: var(--mrr-primary); }
</style>

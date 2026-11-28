<script setup lang="ts">
withDefaults(defineProps<{ variant?: 'preview' | 'thumb' }>(), {
  variant: 'preview',
})
</script>

<template>
  <!-- Abstract document outlines only; never resemble an actual patient image. -->
  <svg v-if="variant === 'preview'" class="archive-empty-art" viewBox="0 0 240 176" fill="none" aria-hidden="true" focusable="false">
    <rect x="19" y="14" width="202" height="148" rx="24" class="art-surface" />
    <path d="M37 48V35h13m140 0h13v13M37 128v13h13m140 0h13v-13" class="art-muted" stroke-width="1.5" stroke-linecap="round" />
    <rect x="75" y="31" width="91" height="114" rx="9" class="art-back" transform="rotate(8 120 88)" />
    <path d="M67 35h67l25 25v76a8 8 0 0 1-8 8H67a8 8 0 0 1-8-8V43a8 8 0 0 1 8-8Z" class="art-paper" stroke-width="1.6" />
    <path d="M134 35v18a7 7 0 0 0 7 7h18" class="art-line" stroke-width="1.6" stroke-linejoin="round" />
    <rect x="76" y="68" width="65" height="36" rx="5" class="art-well" />
    <path d="M87 80h43m-43 10h27M77 116h42m-42 9h27" class="art-muted" stroke-width="2.5" stroke-linecap="round" />
    <circle cx="153" cy="115" r="23" class="art-paper" stroke-width="2.4" />
    <path d="m170 132 16 16" class="art-accent" stroke-width="5" stroke-linecap="round" />
    <path d="M145 115h16" class="art-accent" stroke-width="2.5" stroke-linecap="round" />
    <circle cx="45" cy="88" r="2" class="art-dot" />
    <circle cx="191" cy="79" r="2" class="art-dot" />
  </svg>
  <svg v-else class="archive-empty-art archive-empty-art--thumb" viewBox="0 0 100 88" fill="none" aria-hidden="true" focusable="false">
    <rect x="17" y="9" width="65" height="70" rx="15" class="art-surface" />
    <rect x="34" y="18" width="44" height="51" rx="5" class="art-back" transform="rotate(9 56 44)" />
    <rect x="23" y="24" width="44" height="51" rx="5" class="art-paper" stroke-width="1.4" />
    <rect x="31" y="33" width="28" height="22" rx="3" class="art-well" />
    <path d="M32 64h26" class="art-muted" stroke-width="2" stroke-linecap="round" />
    <path d="M40 44h10" class="art-accent" stroke-width="2" stroke-linecap="round" />
  </svg>
</template>

<style scoped>
.archive-empty-art {
  display: block;
  flex: none;
  width: min(240px, 100%);
  height: auto;
  color: var(--mrr-muted-foreground);
}

.archive-empty-art--thumb {
  width: min(100px, 100%);
}

.art-surface { fill: var(--mrr-muted); }
.art-back { fill: var(--mrr-muted); stroke: var(--mrr-border-strong); }
.art-paper { fill: var(--mrr-card); stroke: var(--mrr-muted-foreground); }
.art-well { fill: var(--mrr-muted); }
.art-line { stroke: var(--mrr-muted-foreground); }
.art-muted { opacity: 0.38; stroke: var(--mrr-muted-foreground); }
.art-accent { stroke: var(--mrr-primary); }
.art-dot { opacity: 0.38; fill: var(--mrr-muted-foreground); }
</style>

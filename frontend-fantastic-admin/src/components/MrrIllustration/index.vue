<script setup lang="ts">
withDefaults(defineProps<{
  variant: 'archive' | 'relations' | 'folder' | 'search' | 'repair' | 'standard' | 'wall'
  compact?: boolean
}>(), { compact: false })
</script>

<template>
  <!-- Decorative schematics, not actual medical records, results or state indicators. -->
  <svg class="mrr-illustration" :class="{ 'is-compact': compact, 'is-layout': variant === 'standard' || variant === 'wall' }" :data-illustration="variant" viewBox="0 0 160 112" fill="none" aria-hidden="true" focusable="false">
    <template v-if="variant === 'standard' || variant === 'wall'">
      <rect x="10" y="12" width="140" height="88" rx="8" class="art-paper" />
      <rect x="17" y="19" width="30" height="74" rx="4" class="art-soft" />
      <path d="M23 27h18m-18 8h12m-12 9h18m-18 8h12" class="art-detail" />
      <template v-if="variant === 'standard'">
        <rect x="53" y="19" width="22" height="74" rx="4" class="art-soft" />
        <rect v-for="y in [25, 46, 67]" :key="y" x="58" :y="y" width="12" height="16" rx="2" class="art-paper" />
        <rect x="81" y="19" width="62" height="74" rx="4" class="art-soft" />
        <rect x="94" y="27" width="36" height="58" rx="3" class="art-accent-paper" />
        <path d="M101 38h22m-22 8h22m-22 8h15" class="art-detail" />
      </template>
      <template v-else>
        <rect x="53" y="19" width="90" height="74" rx="4" class="art-soft" />
        <rect v-for="(position, index) in [[58, 25], [86, 25], [114, 25], [58, 58], [86, 58], [114, 58]]" :key="index" :x="position[0]" :y="position[1]" width="24" height="29" rx="3" :class="index === 0 ? 'art-accent-paper' : 'art-paper'" />
      </template>
    </template>
    <template v-else>
      <rect x="10" y="9" width="140" height="94" rx="18" class="art-soft" />
      <template v-if="variant === 'archive' || variant === 'relations'">
        <path d="M57 56H38V34m65 22h19V34M80 72v17" class="art-connector" />
        <rect x="19" y="18" width="38" height="25" rx="5" class="art-paper" />
        <rect x="103" y="18" width="38" height="25" rx="5" class="art-paper" />
        <rect x="61" y="84" width="38" height="15" rx="4" class="art-paper" />
        <path d="M27 26h22m-22 7h14m62-7h22m-22 7h14M69 92h22" class="art-detail" />
        <rect x="58" y="34" width="44" height="42" rx="7" class="art-accent-paper" />
        <path v-if="variant === 'archive'" d="M69 43h14l8 8v16H69zM83 43v8h8M74 58h12" class="art-accent-line" />
        <template v-else>
          <path d="M68 48h24v18H68zM68 54h24M76 54v12" class="art-accent-line" />
          <circle cx="80" cy="42" r="2" class="art-accent-dot" />
        </template>
      </template>
      <template v-else-if="variant === 'folder' || variant === 'search'">
        <path d="M30 42V33a6 6 0 0 1 6-6h26l12 12h47a6 6 0 0 1 6 6v42H30Z" class="art-accent-paper" />
        <path d="M30 48h102l-11 39H30z" class="art-paper" />
        <path d="M63 66h24" class="art-detail" />
        <g v-if="variant === 'search'">
          <circle cx="116" cy="76" r="18" class="art-paper" />
          <path d="m129 89 10 10M110 76h12" class="art-accent-line" stroke-width="3" />
        </g>
      </template>
      <template v-else>
        <rect x="48" y="17" width="64" height="81" rx="8" class="art-paper" />
        <rect x="66" y="12" width="28" height="13" rx="4" class="art-accent-paper" />
        <template v-for="y in [36, 54, 72]" :key="y">
          <rect x="58" :y="y" width="10" height="10" rx="2" class="art-paper" />
          <path :d="`M76 ${y + 5}h24`" class="art-detail" />
        </template>
      </template>
    </template>
  </svg>
</template>

<style scoped>
.mrr-illustration {
  display: block;
  flex: none;
  width: min(208px, 100%);
  height: auto;
  margin: 0 auto var(--mrr-space-2);
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.mrr-illustration.is-compact {
  width: min(112px, 100%);
}

.mrr-illustration.is-layout {
  width: min(100px, 100%);
  margin: 0 auto;
}

.art-soft { fill: var(--mrr-muted); }
.art-paper { fill: var(--mrr-card); stroke: var(--mrr-border-strong); }
.art-accent-paper { fill: var(--mrr-tone-primary-soft); stroke: var(--mrr-primary); }
.art-detail { opacity: 0.48; stroke: var(--mrr-muted-foreground); }
.art-connector { opacity: 0.6; stroke: var(--mrr-muted-foreground); }
.art-accent-line { stroke: var(--mrr-primary); }
.art-accent-dot { fill: var(--mrr-primary); }
</style>

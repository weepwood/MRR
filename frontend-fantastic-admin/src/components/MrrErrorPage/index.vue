<script setup lang="ts">
import MrrErrorIllustration from '@/components/MrrErrorIllustration/index.vue'

defineProps<{
  code: '403' | '404'
  heading: string
  description: string
}>()
</script>

<template>
  <main class="mrr-error-page" :data-error-code="code">
    <div class="mrr-error-page__inner">
      <MrrErrorIllustration class="mrr-error-page__art" :variant="code === '404' ? 'missing' : 'forbidden'" />
      <div class="mrr-error-page__copy">
        <span class="mrr-error-page__code">{{ code }}</span>
        <h1>{{ heading }}</h1>
        <p>{{ description }}</p>
        <div class="mrr-error-page__actions">
          <slot />
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.mrr-error-page {
  box-sizing: border-box;
  display: grid;
  place-items: center;
  min-width: 0;
  min-height: 100vh;
  padding: var(--mrr-space-8);
  background: var(--mrr-background);
}

.mrr-error-page__inner {
  display: grid;
  grid-template-columns: minmax(0, 280px) minmax(0, 360px);
  gap: 48px;
  align-items: center;
  width: 100%;
  max-width: 688px;
}

.mrr-error-page__art {
  max-width: 280px;
  margin: auto;
}

.mrr-error-page__copy {
  display: grid;
  gap: var(--mrr-space-3);
  min-width: 0;
}

.mrr-error-page__code {
  font-size: var(--mrr-font-size-md);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--mrr-muted-foreground);
  letter-spacing: 0.08em;
}

.mrr-error-page__copy h1 {
  margin: 0;
  font-size: var(--mrr-font-size-title);
  font-weight: 600;
  line-height: 1.35;
  color: var(--mrr-foreground);
  overflow-wrap: anywhere;
}

.mrr-error-page__copy p {
  margin: 0;
  font-size: var(--mrr-font-size-md);
  line-height: 1.75;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.mrr-error-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  margin-top: var(--mrr-space-2);
}

.mrr-error-page__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 640px) {
  .mrr-error-page {
    padding: var(--mrr-space-5);
  }

  .mrr-error-page__inner {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--mrr-space-5);
    max-width: 360px;
    text-align: center;
  }

  .mrr-error-page__art {
    width: 200px;
    max-width: 100%;
  }

  .mrr-error-page__actions {
    justify-content: center;
  }
}
</style>

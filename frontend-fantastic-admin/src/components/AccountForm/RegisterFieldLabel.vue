<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'RegisterFieldLabel' })

const props = defineProps<{
  htmlFor: string
  label: string
  status: '必填' | '选填'
  description: string
  suggestions?: string[]
}>()

const triggerLabel = computed(() => {
  const fieldDescription = `${props.label}说明：${props.status}。${props.description}`
  return props.suggestions?.length
    ? `${fieldDescription}。密码建议：${props.suggestions.join('；')}`
    : fieldDescription
})
</script>

<template>
  <div class="field-label">
    <label :for="props.htmlFor">{{ props.label }}</label>
    <FaTooltip side="top" :delay="200">
      <button type="button" class="field-info-trigger" :aria-label="triggerLabel">
        <FaIcon aria-hidden="true" name="i-lucide:info" />
      </button>
      <template #content>
        <div class="field-info-content">
          <div class="field-info-summary">
            <strong :class="props.status === '必填' ? 'is-required' : 'is-optional'">{{ props.status }}</strong>
            <span>· {{ props.description }}</span>
          </div>
          <div v-if="props.suggestions?.length" class="field-info-suggestions">
            <strong>密码建议</strong>
            <ul>
              <li v-for="suggestion in props.suggestions" :key="suggestion">
                {{ suggestion }}
              </li>
            </ul>
          </div>
        </div>
      </template>
    </FaTooltip>
  </div>
</template>

<style scoped>
.field-label { display: flex; gap: 6px; align-items: center; }
.field-label label { font-size: 12px; font-weight: 650; color: var(--mrr-foreground); }
.field-info-trigger { display: grid; place-items: center; width: 22px; height: 22px; padding: 0; color: var(--mrr-muted-foreground); cursor: help; background: transparent; border: 0; border-radius: var(--mrr-radius-pill); }

.field-info-trigger:hover,
.field-info-trigger:focus-visible { color: var(--mrr-primary); background: var(--mrr-muted); }

.field-info-trigger:focus-visible { outline: 2px solid var(--mrr-primary); outline-offset: 2px; }
.field-info-trigger :deep(svg) { width: 15px; height: 15px; }
.field-info-content { display: grid; gap: var(--mrr-space-2); max-width: min(360px, calc(100vw - 48px)); line-height: 1.5; white-space: normal; }
.field-info-summary { display: flex; gap: var(--mrr-space-1); align-items: baseline; }
.field-info-suggestions > strong { font-size: 11px; font-weight: 600; }
.field-info-suggestions ul { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-1) var(--mrr-space-3); padding-left: var(--mrr-space-4); margin: var(--mrr-space-1) 0 0; list-style: disc; }
.field-info-suggestions li { font-size: 11px; line-height: 1.5; }
.field-info-content .is-required { color: var(--color-danger); }
.field-info-content .is-optional { color: var(--mrr-muted-foreground); }
</style>

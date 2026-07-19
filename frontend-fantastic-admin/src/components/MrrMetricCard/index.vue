<script setup lang="ts">
defineOptions({ name: 'MrrMetricCard' })

const props = withDefaults(defineProps<{
  label: string
  value: string | number
  note?: string
  suffix?: string
  icon?: string
  tone?: MetricTone
  compactValue?: boolean
}>(), {
  tone: 'blue',
  compactValue: false,
})

type MetricTone = 'blue' | 'green' | 'amber' | 'rose' | 'danger' | 'violet' | 'teal' | 'slate'

const toneClass = computed(() => props.tone === 'blue' ? '' : `mrr-metric-card--${props.tone}`)
</script>

<template>
  <el-card shadow="never" class="mrr-metric-card" :class="toneClass">
    <div v-if="props.icon" class="mrr-metric-card__icon" aria-hidden="true">
      <FaIcon :name="props.icon" />
    </div>
    <div class="mrr-metric-card__body">
      <span class="mrr-metric-card__label">{{ props.label }}</span>
      <div class="mrr-metric-card__value-row">
        <strong
          class="mrr-metric-card__value"
          :class="{ 'mrr-metric-card__value--compact': props.compactValue }"
        >
          {{ props.value }}
        </strong>
        <span v-if="props.suffix" class="mrr-metric-card__suffix">{{ props.suffix }}</span>
      </div>
      <p v-if="props.note" class="mrr-metric-card__note">
        {{ props.note }}
      </p>
    </div>
  </el-card>
</template>

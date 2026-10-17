<script setup lang="ts">
import type { MetricTone, MetricValueTone } from './types'

defineOptions({ name: 'MrrMetricCard' })

const props = withDefaults(defineProps<{
  label: string
  value: string | number
  note?: string
  suffix?: string
  icon?: string
  tone?: MetricTone
  valueTone?: MetricValueTone
  compactValue?: boolean
}>(), {
  tone: 'blue',
  valueTone: 'default',
  compactValue: false,
})

const hasIcon = computed(() => Boolean(props.icon))

const cardClass = computed(() => [
  props.tone === 'blue' ? '' : `mrr-metric-card--${props.tone}`,
  hasIcon.value ? '' : 'mrr-metric-card--no-icon',
])

const valueClass = computed(() => [
  props.compactValue ? 'mrr-metric-card__value--compact' : '',
  props.valueTone === 'default' ? '' : `mrr-metric-card__value--${props.valueTone}`,
])
</script>

<template>
  <el-card shadow="never" class="mrr-metric-card" :class="cardClass">
    <div v-if="hasIcon" class="mrr-metric-card__icon" aria-hidden="true">
      <FaIcon :name="props.icon ?? ''" />
    </div>
    <div class="mrr-metric-card__body">
      <span class="mrr-metric-card__label">{{ props.label }}</span>
      <div class="mrr-metric-card__value-row">
        <strong class="mrr-metric-card__value" :class="valueClass">
          {{ props.value }}
        </strong>
        <span v-if="props.suffix" class="mrr-metric-card__suffix">{{ props.suffix }}</span>
      </div>
      <p v-if="props.note" class="mrr-metric-card__note">
        {{ props.note }}
      </p>
      <div v-if="$slots.extra" class="mrr-metric-card__extra">
        <slot name="extra" />
      </div>
    </div>
  </el-card>
</template>

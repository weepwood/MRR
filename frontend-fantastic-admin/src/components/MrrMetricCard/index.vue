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

/*
 * 数值变化提示：首屏渲染不提示，之后每次 value 变化（轮询刷新、筛选切换）重建数值节点，
 * 播放一次 mrr-value-update。key 变化即重播，不依赖定时器清类名。
 */
const updateRevision = ref(0)
watch(() => props.value, (next, previous) => {
  if (next !== previous) {
    updateRevision.value += 1
  }
})

const valueClass = computed(() => [
  props.compactValue ? 'mrr-metric-card__value--compact' : '',
  props.valueTone === 'default' ? '' : `mrr-metric-card__value--${props.valueTone}`,
  updateRevision.value > 0 ? 'is-updated' : '',
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
        <strong :key="updateRevision" class="mrr-metric-card__value" :class="valueClass">
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

<script setup lang="ts">
import type { MetricScope, MetricTone, MetricValueTone } from './types'

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
  scope?: MetricScope
  anchor?: string
}>(), {
  tone: 'blue',
  valueTone: 'default',
  compactValue: false,
})

const hasIcon = computed(() => Boolean(props.icon))

const cardClass = computed(() => [
  props.tone === 'blue' ? '' : `mrr-metric-card--${props.tone}`,
  hasIcon.value ? '' : 'mrr-metric-card--no-icon',
  props.anchor ? 'mrr-metric-card--anchor' : '',
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
function navigateAnchor(event: MouseEvent) {
  if (!props.anchor?.startsWith('#')) { return }
  const target = document.getElementById(props.anchor.slice(1))
  if (!target) { return }
  event.preventDefault()
  target.focus({ preventScroll: true })
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  target.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'start' })
}
</script>

<template>
  <el-card shadow="never" class="mrr-metric-card" :class="cardClass">
    <a
      v-if="props.anchor"
      :href="props.anchor"
      :aria-label="`查看${props.label}明细`"
      class="mrr-metric-card__anchor"
      @click="navigateAnchor"
    />
    <div v-if="hasIcon" class="mrr-metric-card__icon" aria-hidden="true">
      <FaIcon :name="props.icon ?? ''" />
    </div>
    <div class="mrr-metric-card__body">
      <span class="mrr-metric-card__label">
        {{ props.label }}
        <span v-if="props.scope" class="mrr-metric-card__scope" :data-scope="props.scope">{{ props.scope === 'page' ? '本页' : '全库' }}</span>
      </span>
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

<style scoped>
.mrr-metric-card--anchor {
  position: relative;
}

.mrr-metric-card__anchor {
  position: absolute;
  inset: 0;
  z-index: 1;
  color: inherit;
  text-decoration: none;
  border-radius: inherit;
}

.mrr-metric-card__anchor:hover,
.mrr-metric-card__anchor:focus-visible {
  box-shadow: inset 0 0 0 2px var(--mrr-primary);
}
</style>

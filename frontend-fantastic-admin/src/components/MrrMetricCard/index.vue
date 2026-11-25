<script setup lang="ts">
import type { MetricTone, MetricValueTone } from './types'

defineOptions({ name: 'MrrMetricCard' })

const props = withDefaults(defineProps<{
  label: string
  value: string | number
  note?: string
  suffix?: string
  icon: string
  tone?: MetricTone
  valueTone?: MetricValueTone
  compactValue?: boolean
  anchor?: string
  actionLabel?: string
}>(), {
  tone: 'blue',
  valueTone: 'default',
  compactValue: false,
})

const emit = defineEmits<{ activate: [] }>()

const cardClass = computed(() => [
  props.tone === 'blue' ? '' : `mrr-metric-card--${props.tone}`,
  props.anchor || props.actionLabel ? 'mrr-metric-card--interactive' : '',
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
    <button
      v-else-if="props.actionLabel"
      type="button"
      class="mrr-metric-card__anchor"
      :title="props.actionLabel"
      :aria-label="props.actionLabel"
      @click="emit('activate')"
    />
    <div class="mrr-metric-card__icon" aria-hidden="true">
      <FaIcon :name="props.icon" />
    </div>
    <div class="mrr-metric-card__body">
      <div class="mrr-metric-card__heading">
        <span class="mrr-metric-card__label">{{ props.label }}</span>
        <MrrInfoTip v-if="props.note" class="mrr-metric-card__info" :label="`查看${props.label}说明`" :content="props.note" placement="top" />
        <FaIcon v-if="props.anchor || props.actionLabel" name="i-lucide:arrow-up-right" class="mrr-metric-card__action-mark" aria-hidden="true" />
      </div>
      <div class="mrr-metric-card__value-row">
        <strong :key="updateRevision" class="mrr-metric-card__value" :class="valueClass">
          {{ props.value }}
        </strong>
        <span v-if="props.suffix" class="mrr-metric-card__suffix">{{ props.suffix }}</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.mrr-metric-card--interactive {
  position: relative;
}

.mrr-metric-card__anchor {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  padding: 0;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: inherit;
}

.mrr-metric-card__anchor:hover,
.mrr-metric-card__anchor:focus-visible {
  box-shadow: inset 0 0 0 2px var(--mrr-primary);
}
</style>

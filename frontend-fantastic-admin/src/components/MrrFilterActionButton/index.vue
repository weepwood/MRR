<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useAttrs, watch } from 'vue'
import FaIcon from '@/ui/components/FaIcon/index.vue'

defineOptions({
  name: 'MrrFilterActionButton',
  inheritAttrs: false,
})

const props = withDefaults(defineProps<{
  action: 'search' | 'reset'
  pending?: boolean
  busy?: boolean
}>(), {
  pending: false,
  busy: false,
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const attrs = useAttrs()
const isSpinning = ref(false)
const pendingWasObserved = ref(false)
const minSpinDuration = 320
let spinStartedAt = 0
let finishTimer: ReturnType<typeof setTimeout> | undefined

function finishAfterMinimumDuration() {
  if (finishTimer) { clearTimeout(finishTimer) }
  const remaining = Math.max(0, minSpinDuration - (Date.now() - spinStartedAt))
  finishTimer = setTimeout(() => {
    isSpinning.value = false
    pendingWasObserved.value = false
    finishTimer = undefined
  }, remaining)
}

watch(() => props.pending, (pending) => {
  if (!isSpinning.value) { return }
  if (pending) {
    pendingWasObserved.value = true
    if (finishTimer) {
      clearTimeout(finishTimer)
      finishTimer = undefined
    }
  }
  else if (pendingWasObserved.value) {
    finishAfterMinimumDuration()
  }
}, { flush: 'sync' })

function handleClick(event: MouseEvent) {
  if (props.busy || attrs.disabled || isSpinning.value) { return }

  spinStartedAt = Date.now()
  isSpinning.value = true
  pendingWasObserved.value = props.pending
  emit('click', event)

  if (!pendingWasObserved.value && !props.pending) {
    finishAfterMinimumDuration()
  }
}

onBeforeUnmount(() => {
  if (finishTimer) { clearTimeout(finishTimer) }
})

const iconName = computed(() => {
  if (isSpinning.value) { return 'i-lucide:loader-circle' }
  return props.action === 'search' ? 'i-lucide:search' : 'i-lucide:rotate-ccw'
})
</script>

<template>
  <el-button
    v-bind="attrs"
    :aria-busy="isSpinning"
    :aria-disabled="isSpinning || props.busy || Boolean(attrs.disabled)"
    @click="handleClick"
  >
    <FaIcon :name="iconName" class="mrr-filter-action-button__icon" :class="{ 'is-spinning': isSpinning }" />
    <slot />
  </el-button>
</template>

<style scoped>
.mrr-filter-action-button__icon {
  margin-right: 6px;
}

.mrr-filter-action-button__icon.is-spinning {
  animation: mrr-filter-action-spin 900ms linear infinite;
}

@keyframes mrr-filter-action-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

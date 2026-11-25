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
  /** Request-owned feedback does not extend a completed query. */
  feedback?: 'minimum' | 'request'
}>(), {
  pending: false,
  busy: false,
  feedback: 'minimum',
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const attrs = useAttrs()
const isSpinning = ref(false)
const pendingWasObserved = ref(false)
const showingBusy = computed(() => props.feedback === 'request' ? props.pending : isSpinning.value)
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
  if (props.busy || attrs.disabled || showingBusy.value) { return }
  if (props.feedback === 'request') {
    emit('click', event)
    return
  }

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
  if (showingBusy.value) { return 'i-lucide:loader-circle' }
  return props.action === 'search' ? 'i-lucide:search' : 'i-lucide:rotate-ccw'
})
</script>

<template>
  <el-button
    v-bind="attrs"
    :aria-busy="showingBusy"
    :aria-disabled="showingBusy || props.busy || Boolean(attrs.disabled)"
    @click="handleClick"
  >
    <FaIcon :name="iconName" class="mrr-filter-action-button__icon" :class="{ 'is-spinning': showingBusy }" />
    <slot />
  </el-button>
</template>

<style scoped>
.mrr-filter-action-button__icon {
  /* 间距交给全局 .el-button > span 的 6px flex gap，图标不再叠加自身 margin
     （原先 6+6=12px，侧栏窄按钮里显得松散） */
  margin-right: 0;
}

.mrr-filter-action-button__icon.is-spinning {
  animation: mrr-filter-action-spin var(--mrr-motion-loop) linear infinite;
}

@keyframes mrr-filter-action-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

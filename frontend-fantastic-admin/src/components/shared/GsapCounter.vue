<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { animateCounter } from '@/utils/animations'

const props = defineProps({
  value: {
    type: Number,
    default: 0
  },
  duration: {
    type: Number,
    default: 1.6
  }
})

const counterRef = ref<HTMLElement | null>(null)

const startAnimation = () => {
  if (counterRef.value && typeof props.value === 'number') {
    animateCounter(counterRef.value, props.value, props.duration)
  }
}

onMounted(() => {
  startAnimation()
})

watch(() => props.value, () => {
  startAnimation()
})
</script>

<template>
  <span ref="counterRef" class="gsap-counter">0</span>
</template>

<style scoped>
.gsap-counter {
  display: inline-block;
  font-variant-numeric: tabular-nums;
}
</style>

<script setup lang="ts">
import { PinInput, PinInputGroup, PinInputSlot } from './pin-input'

defineOptions({
  name: 'FaPinInput',
})

withDefaults(
  defineProps<{
    length?: number
  }>(),
  {
    length: 6,
  },
)

const emit = defineEmits<{
  complete: []
}>()

const modelValue = defineModel<string>()

const value = ref<string[]>([])

watch(modelValue, (val) => {
  // External reset / preset, without collapsing the internal empty slot array on our own update.
  if ((val ?? '') !== value.value.join('')) {
    value.value = val?.split('') ?? []
  }
}, { immediate: true })

function handleInput(next: string[]) {
  value.value = next
  modelValue.value = next.join('')
}

function handleComplete(e: string[]) {
  handleInput(e)
  emit('complete')
}

const id = useId()
</script>

<template>
  <PinInput :id :model-value="value" placeholder="○" otp @update:model-value="handleInput" @complete="handleComplete">
    <PinInputGroup>
      <PinInputSlot v-for="(item, index) in length" :key="item" :index="index" :aria-label="`验证码第 ${index + 1} 位`" />
    </PinInputGroup>
  </PinInput>
</template>

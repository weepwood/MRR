<script setup lang="ts">
import type { SelectTriggerProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { ChevronDown } from 'lucide-vue-next'
import { SelectIcon, SelectTrigger, useForwardProps } from 'reka-ui'
import { cn } from '@/utils'

const props = defineProps<SelectTriggerProps & { class?: HTMLAttributes['class'] }>()

const delegatedProps = reactiveOmit(props, 'class')

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <SelectTrigger
    v-bind="forwardedProps"
    :class="cn(
      'fa-select-trigger flex w-full items-center justify-between text-start [&>span]:truncate',
      props.class,
    )"
  >
    <slot />
    <SelectIcon as-child>
      <ChevronDown class="shrink-0" />
    </SelectIcon>
  </SelectTrigger>
</template>

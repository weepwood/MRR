<script setup lang="ts">
import type { TabsInstance } from 'element-plus'
import { onMounted, ref } from 'vue'
import { handleSegmentedTabKey } from '@/utils/segmented-tabs'

const props = defineProps<{ label?: string }>()
const model = defineModel<string | number>({ required: true })
const tabs = ref<TabsInstance>()
onMounted(() => {
  if (props.label) { tabs.value?.tabNavRef?.tabListRef?.setAttribute('aria-label', props.label) }
})
</script>

<template>
  <el-tabs ref="tabs" v-model="model" class="mrr-segmented-tabs" @keydown="handleSegmentedTabKey">
    <slot />
  </el-tabs>
</template>

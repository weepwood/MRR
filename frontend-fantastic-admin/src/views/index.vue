<route lang="yaml">
meta:
  title: 管理概览
  icon: i-lucide:house
</route>

<script setup lang="ts">
import DashboardWorkspace from './home/DashboardWorkspace.vue'

defineOptions({ name: 'HomePage' })

const pagesScenarioEnabled = import.meta.env.MODE === 'pages' && String(import.meta.env.VITE_BUILD_MOCK) === 'true'
const DemoScenarioControl = pagesScenarioEnabled ? defineAsyncComponent(() => import('./home/DemoScenarioControl.vue')) : undefined
</script>

<template>
  <DemoScenarioControl v-if="pagesScenarioEnabled && DemoScenarioControl" />
  <DashboardWorkspace />
</template>

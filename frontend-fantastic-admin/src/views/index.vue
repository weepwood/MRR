<route lang="yaml">
meta:
  title: 管理概览
  icon: ant-design:home-twotone
</route>

<script setup lang="ts">
import DashboardWorkspace from './home/DashboardWorkspace.vue'

defineOptions({ name: 'HomePage' })
</script>

<template>
  <DashboardWorkspace />
</template>

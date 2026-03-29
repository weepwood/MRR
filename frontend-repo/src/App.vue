<script setup>
import { onMounted } from 'vue'
import { useAdminSettings } from '@/shared/composables/useAdminSettings'

const { loadSettings } = useAdminSettings()

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <div id="app" class="pmr-app">
    <router-view />
  </div>
</template>

<style>
#app {
  min-height: 100%;
}

.pmr-app {
  min-height: 100vh;
}
</style>

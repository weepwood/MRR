<script setup lang="ts">
import hotkeys from 'hotkeys-js'

defineOptions({
  name: 'NavSearch',
})

const settingsStore = useSettingsStore()

const Search = defineAsyncComponent(() => import('./search.vue'))
const isShow = ref(false)
const hasOpened = ref(false)

function openSearch() {
  hasOpened.value = true
  isShow.value = true
}

function handleSearchShortcut(event: KeyboardEvent) {
  if (settingsStore.settings.navSearch.enableHotkeys) {
    event.preventDefault()
    openSearch()
  }
}

onMounted(() => {
  hotkeys('command+k, ctrl+k', handleSearchShortcut)
})

onUnmounted(() => {
  hotkeys.unbind('command+k, ctrl+k', handleSearchShortcut)
})
</script>

<template>
  <FaButton :variant="settingsStore.mode === 'pc' ? 'outline' : 'ghost'" :size="settingsStore.mode === 'pc' ? undefined : 'icon'" :class="{ 'mx-2 px-3 group': settingsStore.mode === 'pc' }" @click="openSearch">
    <FaIcon name="i-lucide:search" class="size-4" />
    <template v-if="settingsStore.mode === 'pc'">
      <span class="text-sm text-muted-foreground transition group-hover-text-foreground">搜索</span>
      <FaKbd v-if="settingsStore.settings.navSearch.enableHotkeys" class="-me-1">
        {{ settingsStore.os === 'mac' ? '⌘' : 'Ctrl' }} K
      </FaKbd>
    </template>
  </FaButton>
  <Search v-if="hasOpened" v-model="isShow" />
</template>

<script setup lang="ts">
import type { SettingsSearchResult } from '../settings-search'
import { computed, ref, useId } from 'vue'
import { searchSettings } from '../settings-search'

const emit = defineEmits<{
  select: [result: SettingsSearchResult]
}>()

const searchId = useId()
const query = ref('')
const noResults = computed(() => Boolean(query.value.trim()) && searchSettings(query.value).length === 0)

function selectResult(result: SettingsSearchResult) {
  query.value = ''
  emit('select', result)
}
</script>

<template>
  <div class="settings-search">
    <div class="settings-search-caption">
      <label :for="searchId" class="settings-search-label">搜索设置</label>
      <span role="status" class="settings-search-status">{{ noResults ? '未找到匹配的设置' : '' }}</span>
    </div>
    <el-autocomplete
      :id="searchId"
      v-model="query"
      class="settings-search-input"
      placeholder="例如水印、缩略图"
      value-key="title"
      :fetch-suggestions="searchSettings"
      :trigger-on-focus="false"
      :debounce="0"
      clearable
      fit-input-width
      highlight-first-item
      hide-loading
      @select="selectResult($event as SettingsSearchResult)"
    >
      <template #prefix>
        <FaIcon name="i-lucide:search" aria-hidden="true" />
      </template>
      <template #default="{ item }">
        <div class="settings-search-result">
          <span>{{ item.title }}</span>
          <small v-if="item.category">{{ item.category }}</small>
        </div>
      </template>
    </el-autocomplete>
  </div>
</template>

<style scoped>
.settings-search-caption { display: flex; gap: var(--mrr-space-2); justify-content: space-between; margin-bottom: var(--mrr-space-1); }
.settings-search-label { font-size: 12px; font-weight: 600; }
.settings-search-input { width: 100%; }
.settings-search-result { display: grid; padding-block: var(--mrr-space-1); line-height: 1.5; white-space: normal; }

.settings-search-result small,
.settings-search-status { font-size: 12px; color: var(--mrr-muted-foreground); }
</style>

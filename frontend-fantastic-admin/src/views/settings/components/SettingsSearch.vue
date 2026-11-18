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
    <label :for="searchId" class="settings-search-label">搜索设置</label>
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
    <div role="status" class="settings-search-status">
      <span v-if="noResults">未找到匹配的设置</span>
    </div>
  </div>
</template>

<style scoped>
.settings-search-label { display: block; margin-bottom: var(--mrr-space-2); font-size: 12px; font-weight: 600; }
.settings-search-input { width: 100%; }
.settings-search-result { display: grid; padding-block: var(--mrr-space-1); line-height: 1.5; white-space: normal; }

.settings-search-result small,
.settings-search-status { font-size: 12px; color: var(--mrr-muted-foreground); }
.settings-search-status:not(:empty) { padding-top: var(--mrr-space-2); }
</style>

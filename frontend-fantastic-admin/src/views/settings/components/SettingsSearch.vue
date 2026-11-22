<script setup lang="ts">
import type { SettingsSearchResult } from '../settings-search'
import { computed, ref } from 'vue'
import { searchSettings } from '../settings-search'

const emit = defineEmits<{
  select: [result: SettingsSearchResult]
}>()

const query = ref('')
const searchActive = ref(false)
const showEmptyState = computed(() => searchActive.value
  && Boolean(query.value.trim()) && searchSettings(query.value).length === 0)

function dismissEmptyState(event: KeyboardEvent) {
  if (showEmptyState.value && !event.isComposing) {
    searchActive.value = false
  }
}

function selectResult(result: SettingsSearchResult) {
  query.value = ''
  emit('select', result)
}
</script>

<template>
  <div class="settings-search" @keydown.esc="dismissEmptyState">
    <el-autocomplete
      v-model="query"
      class="settings-search-input"
      aria-label="搜索设置"
      placeholder="例如水印、缩略图"
      popper-class="settings-search-popper"
      value-key="title"
      :fetch-suggestions="searchSettings"
      :trigger-on-focus="false"
      :show-arrow="false"
      :debounce="0"
      clearable
      fit-input-width
      highlight-first-item
      hide-loading
      @focus="searchActive = true"
      @input="searchActive = true"
      @blur="searchActive = false"
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
      <div v-if="showEmptyState" class="settings-search-empty">
        <FaIcon name="i-lucide:search" class="settings-search-empty-icon" aria-hidden="true" />
        <strong>未找到匹配的设置</strong>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-search { position: relative; }
.settings-search-input { width: 100%; }
.settings-search-result { display: grid; padding-block: var(--mrr-space-1); line-height: 1.5; white-space: normal; }
.settings-search-result > span { font-weight: 600; }

.settings-search-result small { font-size: 12px; color: var(--mrr-muted-foreground); }

.settings-search-empty,
:global(.settings-search-popper.el-autocomplete__popper.el-popper) {
  background: var(--mrr-popover);
  border: 1px solid var(--mrr-border);
  border-color: color-mix(in srgb, var(--mrr-primary) 20%, var(--mrr-border));
  border-radius: var(--mrr-radius-lg);
  box-shadow: var(--mrr-shadow-md);
}

.settings-search-status {
  position: absolute;
  inset-inline: 0;
  top: calc(100% + var(--mrr-space-3));
  z-index: 1;
  font-size: 13px;
}

.settings-search-empty {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  padding: var(--mrr-space-3);
  color: var(--mrr-popover-foreground);
}

.settings-search-empty-icon { flex-shrink: 0; color: var(--mrr-muted-foreground); }

:global(.settings-search-popper .el-autocomplete-suggestion__wrap) {
  padding: var(--mrr-space-1);
}

:global(.settings-search-popper .el-autocomplete-suggestion__list > li) {
  padding-inline: var(--mrr-space-3);
  color: var(--mrr-popover-foreground);
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
  transition: background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

:global(.settings-search-popper .el-autocomplete-suggestion__list > li:hover) {
  background: var(--mrr-navigation-hover);
}

:global(.settings-search-popper .el-autocomplete-suggestion__list > [aria-selected="true"]) {
  background: color-mix(in srgb, var(--mrr-primary) 9%, var(--mrr-popover));
  border-color: var(--mrr-primary);
}

/* Chrome 86 回退：选中项必须与 hover 项（--mrr-navigation-hover）区分开，
 * 否则键盘上下移动时看不出当前落在哪一条。 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  :global(.settings-search-popper .el-autocomplete-suggestion__list > [aria-selected="true"]) {
    background: var(--mrr-tone-primary-soft);
  }
}
</style>

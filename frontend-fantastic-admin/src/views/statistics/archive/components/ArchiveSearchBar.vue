<script setup lang="ts">
import type { ArchiveSearchHistoryItem } from '../composables/useArchiveSearchHistory'
import { Clock, Delete, Search } from '@element-plus/icons-vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { requiresSjhForBah } from '@/utils/medical-record-code'
import {
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY,
  ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT,
  clearArchiveSearchHistory,
  readArchiveSearchHistory,
  removeArchiveSearchHistory,
} from '../composables/useArchiveSearchHistory'

defineOptions({ name: 'ArchiveSearchBar' })

defineProps<{
  loading?: boolean
}>()

const emit = defineEmits<{
  search: []
}>()
const searchBah = defineModel<string>('searchBah', { default: '' })
const searchSjh = defineModel<string>('searchSjh', { default: '' })
const searchIdCard = defineModel<string>('searchIdCard', { default: '' })

const searchHistory = ref<ArchiveSearchHistoryItem[]>(readArchiveSearchHistory())
const historyVisible = ref(false)
const sjhRequired = computed(() => !searchIdCard.value.trim() && requiresSjhForBah(searchBah.value))
const historyTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function formatHistoryTime(timestamp: number): string {
  return historyTimeFormatter.format(new Date(timestamp))
}

function handleHistoryUpdated(event: Event) {
  const detail = (event as CustomEvent<ArchiveSearchHistoryItem[]>).detail
  searchHistory.value = Array.isArray(detail) ? detail : readArchiveSearchHistory()
}

function handleStorage(event: StorageEvent) {
  if (event.key === ARCHIVE_SEARCH_HISTORY_STORAGE_KEY || event.key === null) {
    searchHistory.value = readArchiveSearchHistory()
  }
}

function selectHistory(item: ArchiveSearchHistoryItem) {
  historyVisible.value = false
  searchIdCard.value = ''
  searchBah.value = item.bah
  searchSjh.value = item.sjh
  emit('search')
}

function removeHistoryItem(key: string) {
  searchHistory.value = removeArchiveSearchHistory(key)
}

function clearHistory() {
  clearArchiveSearchHistory()
  searchHistory.value = []
}

onMounted(() => {
  window.addEventListener(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, handleHistoryUpdated)
  window.addEventListener('storage', handleStorage)
})

onUnmounted(() => {
  window.removeEventListener(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, handleHistoryUpdated)
  window.removeEventListener('storage', handleStorage)
})
</script>

<template>
  <section class="search-card">
    <div class="search-card-heading">
      <div>
        <strong>档案查询</strong>
        <span>支持病案号、上架号或身份证号</span>
      </div>

      <el-popover
        v-model:visible="historyVisible"
        placement="bottom-end"
        trigger="click"
        :width="340"
      >
        <template #reference>
          <el-button text size="small" :icon="Clock">
            最近查询
            <span v-if="searchHistory.length" class="history-count">{{ searchHistory.length }}</span>
          </el-button>
        </template>

        <div class="history-panel">
          <div class="history-header">
            <div>
              <strong>本地搜索记录</strong>
              <p>仅保存在当前浏览器，最多 20 条</p>
            </div>
            <el-button v-if="searchHistory.length" text size="small" :icon="Delete" @click="clearHistory">
              清空
            </el-button>
          </div>

          <div v-if="searchHistory.length" class="history-list">
            <div v-for="item in searchHistory" :key="item.key" class="history-item">
              <button class="history-main" type="button" @click="selectHistory(item)">
                <span class="history-primary">
                  病案号 {{ item.bah || '-' }}
                </span>
                <span class="history-secondary">
                  上架号 {{ item.sjh || '-' }} · {{ item.imageCount }} 张影像
                </span>
                <time :datetime="new Date(item.searchedAt).toISOString()">
                  {{ formatHistoryTime(item.searchedAt) }}
                </time>
              </button>
              <el-button
                text
                circle
                size="small"
                :icon="Delete"
                :aria-label="`删除病案号 ${item.bah || item.sjh} 的搜索记录`"
                @click.stop="removeHistoryItem(item.key)"
              />
            </div>
          </div>
          <el-empty v-else :image-size="54" description="暂无本地搜索记录" />
        </div>
      </el-popover>
    </div>

    <div class="search-bar">
      <div class="search-fields">
        <div class="record-search-fields">
          <el-input v-model="searchBah" name="archive-bah" autocomplete="off" aria-label="病案号" clearable placeholder="病案号" @keyup.enter="emit('search')" />
          <el-input
            v-model="searchSjh"
            name="archive-sjh"
            autocomplete="off"
            aria-label="上架号"
            :aria-required="sjhRequired"
            clearable
            :placeholder="sjhRequired ? '上架号（当前病案号必填）' : '上架号'"
            @keyup.enter="emit('search')"
          />
        </div>
        <div class="id-card-search-fields">
          <el-input
            v-model="searchIdCard"
            name="archive-id-card"
            autocomplete="off"
            aria-label="身份证号"
            clearable
            maxlength="18"
            placeholder="身份证号"
            @keyup.enter="emit('search')"
          />
          <el-button type="primary" :icon="Search" :loading="loading" @click="emit('search')">
            查询
          </el-button>
        </div>
      </div>
      <p v-if="sjhRequired" class="search-rule-hint">
        病案号大于等于 10000000 时不再唯一，必须同时输入唯一上架号。
      </p>
    </div>
  </section>
</template>

<style scoped>
.search-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.search-card-heading,
.search-card-heading > div:first-child,
.history-header,
.history-item {
  display: flex;
  align-items: center;
}

.search-card-heading {
  justify-content: space-between;
  gap: 10px;
}

.search-card-heading > div:first-child {
  min-width: 0;
  gap: 8px;
}

.search-card-heading strong {
  flex: none;
  font-size: 13px;
  color: var(--text-primary);
}

.search-card-heading > div:first-child span {
  overflow: hidden;
  font-size: 12px;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 4px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.history-panel {
  display: grid;
  gap: 10px;
}

.history-header {
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--divider);
}

.history-header p {
  margin: 3px 0 0;
  font-size: 11px;
  color: var(--text-secondary);
}

.history-list {
  display: grid;
  gap: 4px;
  max-height: 360px;
  overflow: hidden auto;
}

.history-item {
  gap: 4px;
  padding: 4px;
  border-radius: 8px;
}

.history-item:hover {
  background: var(--surface-muted);
}

.history-main {
  display: grid;
  flex: 1;
  gap: 3px;
  min-width: 0;
  padding: 6px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.history-main:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 1px;
}

.history-primary,
.history-secondary,
.history-main time {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-primary {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.history-secondary,
.history-main time {
  font-size: 11px;
  color: var(--text-secondary);
}

.search-bar {
  display: grid;
  gap: 8px;
}

.search-fields {
  display: grid;
  gap: 8px;
}

.record-search-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.record-search-fields .el-input {
  min-width: 0;
}

.id-card-search-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.search-rule-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-warning-dark-2);
}

@media (width <= 520px) {
  .search-card-heading > div:first-child span {
    display: none;
  }
}
</style>

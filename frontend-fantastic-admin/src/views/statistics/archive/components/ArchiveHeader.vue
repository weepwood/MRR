<script setup lang="ts">
import type { ArchiveSearchHistoryItem } from '../composables/useArchiveSearchHistory'
import { ArrowLeft, Clock, Delete, Refresh, Star, StarFilled } from '@element-plus/icons-vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  ARCHIVE_SEARCH_HISTORY_DISPLAY_LIMIT,
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY,
  ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT,
  clearArchiveSearchHistory,
  readArchiveSearchHistory,
  removeArchiveSearchHistory,
  toggleArchiveSearchHistoryFavorite,
} from '../composables/useArchiveSearchHistory'

defineOptions({ name: 'ArchiveHeader' })

const props = withDefaults(defineProps<{
  loading?: boolean
  showBack?: boolean
}>(), {
  loading: false,
  showBack: false,
})

const emit = defineEmits<{
  back: []
  refresh: []
  search: []
}>()
const searchBah = defineModel<string>('searchBah', { default: '' })
const searchSjh = defineModel<string>('searchSjh', { default: '' })
const searchIdCard = defineModel<string>('searchIdCard', { default: '' })

const searchHistory = ref<ArchiveSearchHistoryItem[]>(readArchiveSearchHistory())
const historyVisible = ref(false)
const allHistoryVisible = ref(false)
const historyStatus = ref<'success' | 'failure' | 'favorite'>('success')
const displayedSuccessfulHistory = computed(() => searchHistory.value
  .filter(item => item.status === 'success')
  .slice(0, ARCHIVE_SEARCH_HISTORY_DISPLAY_LIMIT))
const successfulHistory = computed(() => searchHistory.value.filter(item => item.status === 'success'))
const failedHistory = computed(() => searchHistory.value.filter(item => item.status === 'failure'))
const favoriteHistory = computed(() => searchHistory.value.filter(item => item.favorite))
const activeHistory = computed(() => {
  if (historyStatus.value === 'favorite') {
    return favoriteHistory.value
  }
  return historyStatus.value === 'success' ? successfulHistory.value : failedHistory.value
})
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
  allHistoryVisible.value = false
  searchIdCard.value = ''
  searchBah.value = item.bah
  searchSjh.value = item.sjh
  emit('search')
}

function removeHistoryItem(key: string) {
  searchHistory.value = removeArchiveSearchHistory(key)
}

function toggleHistoryFavorite(key: string) {
  searchHistory.value = toggleArchiveSearchHistoryFavorite(key)
}

function clearHistory() {
  clearArchiveSearchHistory()
  searchHistory.value = []
}

function openAllHistory() {
  historyVisible.value = false
  historyStatus.value = 'success'
  allHistoryVisible.value = true
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
  <header class="archive-header">
    <div class="archive-heading">
      <div class="archive-title">
        <h2>住院病案</h2>
      </div>
      <div class="heading-actions">
        <el-button v-if="props.showBack" text size="small" :icon="ArrowLeft" @click="emit('back')">
          返回
        </el-button>
        <el-button text size="small" :icon="Refresh" :loading="props.loading" @click="emit('refresh')">
          刷新
        </el-button>
        <el-popover
          v-model:visible="historyVisible"
          placement="bottom-end"
          trigger="click"
          :width="340"
        >
          <template #reference>
            <el-button text size="small" :icon="Clock">
              最近查询
              <span v-if="successfulHistory.length" class="history-count">{{ successfulHistory.length }}</span>
            </el-button>
          </template>

          <div class="history-panel">
            <div class="history-header">
              <strong>搜索记录</strong>
              <div class="history-actions">
                <el-button v-if="searchHistory.length" text size="small" @click="openAllHistory">
                  查看全部
                </el-button>
                <el-button v-if="searchHistory.length" text size="small" :icon="Delete" @click="clearHistory">
                  清空
                </el-button>
              </div>
            </div>

            <div v-if="displayedSuccessfulHistory.length" class="history-groups">
              <section class="history-group">
                <p class="history-group-title">
                  成功查询
                </p>
                <div class="history-list">
                  <div v-for="item in displayedSuccessfulHistory" :key="item.key" class="history-item">
                    <button class="history-main" type="button" @click="selectHistory(item)">
                      <span class="history-primary">病案号 {{ item.bah || '-' }}</span>
                      <span class="history-secondary">上架号 {{ item.sjh || '-' }} · {{ item.imageCount }} 张影像</span>
                      <time :datetime="new Date(item.searchedAt).toISOString()">{{ formatHistoryTime(item.searchedAt) }}</time>
                    </button>
                    <el-button
                      text
                      circle
                      size="small"
                      :class="{ 'is-favorite': item.favorite }"
                      :icon="item.favorite ? StarFilled : Star"
                      :aria-label="`${item.favorite ? '取消收藏' : '收藏'}病案号 ${item.bah || item.sjh}`"
                      @click.stop="toggleHistoryFavorite(item.key)"
                    />
                    <el-button text circle size="small" :icon="Delete" :aria-label="`删除病案号 ${item.bah || item.sjh} 的搜索记录`" @click.stop="removeHistoryItem(item.key)" />
                  </div>
                </div>
              </section>
            </div>
            <el-empty v-else :image-size="54" description="暂无成功查询记录" />
          </div>
        </el-popover>

        <el-dialog v-model="allHistoryVisible" width="min(560px, calc(100vw - 32px))">
          <template #header>
            <div class="history-dialog-header">
              <strong>搜索记录</strong>
              <div class="history-filter-actions">
                <el-button :type="historyStatus === 'success' ? 'primary' : 'default'" @click="historyStatus = 'success'">
                  成功查询 ({{ successfulHistory.length }})
                </el-button>
                <el-button :type="historyStatus === 'failure' ? 'danger' : 'default'" @click="historyStatus = 'failure'">
                  失败查询 ({{ failedHistory.length }})
                </el-button>
                <el-button :type="historyStatus === 'favorite' ? 'warning' : 'default'" :icon="StarFilled" @click="historyStatus = 'favorite'">
                  收藏病案 ({{ favoriteHistory.length }})
                </el-button>
              </div>
            </div>
          </template>
          <div v-if="activeHistory.length" class="history-list history-dialog-list">
            <div v-for="item in activeHistory" :key="item.key" class="history-item">
              <button class="history-main" type="button" @click="selectHistory(item)">
                <span class="history-primary">病案号 {{ item.bah || '-' }}</span>
                <span v-if="item.status === 'success'" class="history-secondary">上架号 {{ item.sjh || '-' }} · {{ item.imageCount }} 张影像</span>
                <span v-else class="history-secondary">{{ item.failureReason }}</span>
                <time :datetime="new Date(item.searchedAt).toISOString()">{{ formatHistoryTime(item.searchedAt) }}</time>
              </button>
              <el-button
                text
                circle
                size="small"
                :class="{ 'is-favorite': item.favorite }"
                :icon="item.favorite ? StarFilled : Star"
                :aria-label="`${item.favorite ? '取消收藏' : '收藏'}病案号 ${item.bah || item.sjh}`"
                @click.stop="toggleHistoryFavorite(item.key)"
              />
              <el-button text circle size="small" :icon="Delete" :aria-label="`删除${item.status === 'success' ? '' : '失败'}病案号 ${item.bah || item.sjh} 的搜索记录`" @click.stop="removeHistoryItem(item.key)" />
            </div>
          </div>
          <el-empty v-else :image-size="54" :description="historyStatus === 'success' ? '暂无成功查询记录' : historyStatus === 'failure' ? '暂无失败查询记录' : '暂无收藏病案'" />
          <template #footer>
            <el-button v-if="searchHistory.length" :icon="Delete" @click="clearHistory">
              清空记录
            </el-button>
          </template>
        </el-dialog>
      </div>
    </div>
  </header>
</template>

<style scoped>
.archive-header {
  display: grid;
  gap: 10px;
  padding: 2px;
}

.archive-heading,
.heading-actions {
  display: flex;
  align-items: center;
}

.archive-heading {
  gap: 12px;
  justify-content: space-between;
  padding: 2px;
}

.heading-actions {
  flex: none;
  gap: 2px;
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

.history-header,
.history-item {
  display: flex;
  align-items: center;
}

.history-header {
  gap: 12px;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--divider);
}

.history-actions {
  display: flex;
  gap: 4px;
}

.history-list {
  display: grid;
  gap: 4px;
}

.history-groups {
  display: grid;
  gap: 12px;
  max-height: 360px;
  overflow: hidden auto;
}

.history-dialog-list {
  max-height: 360px;
  overflow: hidden auto;
}

.history-filter-actions {
  display: flex;
  gap: 8px;
}

.history-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}

.history-group-title {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.history-group-title-failed {
  color: var(--el-color-danger);
}

.history-item {
  gap: 4px;
  padding: 4px;
  border-radius: 8px;
}

.history-item:hover {
  background: var(--surface-muted);
}

.history-item :deep(.is-favorite) {
  color: var(--el-color-warning);
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

.archive-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}
</style>

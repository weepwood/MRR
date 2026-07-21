<script setup lang="ts">
import type { ArchiveSearchHistoryItem } from '../composables/useArchiveSearchHistory'
import { Clock, Refresh, Star, StarFilled } from '@element-plus/icons-vue'
import { AnimatePresence, motion } from 'motion-v'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { motionDurations, motionEasings, motionSprings } from '@/motion/presets'
import {
  ARCHIVE_SEARCH_HISTORY_DISPLAY_LIMIT,
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY,
  ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT,
  loadArchiveSearchHistory,
  readArchiveSearchHistory,
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
const historyFilters = computed(() => [
  { key: 'success' as const, label: '成功查询', count: successfulHistory.value.length },
  { key: 'failure' as const, label: '失败查询', count: failedHistory.value.length },
  { key: 'favorite' as const, label: '收藏病案', count: favoriteHistory.value.length },
])
const activeHistoryEmptyDescription = computed(() => {
  if (historyStatus.value === 'success') {
    return '暂无成功查询记录'
  }
  if (historyStatus.value === 'failure') {
    return '暂无失败查询记录'
  }
  return '暂无收藏病案'
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

function toggleHistoryFavorite(key: string) {
  searchHistory.value = toggleArchiveSearchHistoryFavorite(key)
}

function openAllHistory() {
  historyVisible.value = false
  historyStatus.value = 'success'
  allHistoryVisible.value = true
}

onMounted(() => {
  void loadArchiveSearchHistory().then((history) => {
    searchHistory.value = history
  })
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
              </div>
            </div>

            <div v-if="displayedSuccessfulHistory.length" class="history-groups">
              <section class="history-group">
                <p class="history-group-title">
                  成功查询
                </p>
                <div class="history-list">
                  <motion.div
                    v-for="item in displayedSuccessfulHistory"
                    :key="item.key"
                    layout
                    class="history-item"
                    :transition="motionSprings.layout"
                  >
                    <button class="history-main" type="button" @click="selectHistory(item)">
                      <span class="history-primary">病案号 {{ item.bah || '-' }}</span>
                      <span class="history-secondary">上架号 {{ item.sjh || '-' }} · {{ item.imageCount }} 张影像 · 查询 {{ item.queryCount }} 次</span>
                      <time :datetime="new Date(item.searchedAt).toISOString()">{{ formatHistoryTime(item.searchedAt) }}</time>
                    </button>
                    <motion.span
                      :key="`${item.key}:${item.favorite}`"
                      class="favorite-motion"
                      :initial="{ scale: 0.86, rotate: item.favorite ? -8 : 0 }"
                      :animate="{ scale: 1, rotate: 0 }"
                      :transition="motionSprings.interaction"
                    >
                      <el-button
                        text
                        circle
                        size="small"
                        :class="{ 'is-favorite': item.favorite }"
                        :aria-label="`${item.favorite ? '取消收藏' : '收藏'}病案号 ${item.bah || item.sjh}`"
                        @click.stop="toggleHistoryFavorite(item.key)"
                      >
                        <template #icon>
                          <component :is="item.favorite ? StarFilled : Star" />
                        </template>
                      </el-button>
                    </motion.span>
                  </motion.div>
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
              <div class="history-filter-actions" role="tablist" aria-label="搜索记录筛选">
                <motion.button
                  v-for="filter in historyFilters"
                  :key="filter.key"
                  type="button"
                  role="tab"
                  class="history-filter-button"
                  :class="`history-filter-button--${filter.key}`"
                  :aria-selected="historyStatus === filter.key"
                  @click="historyStatus = filter.key"
                >
                  <motion.span
                    v-if="historyStatus === filter.key"
                    layout-id="archive-history-active-filter"
                    class="history-filter-indicator"
                    :class="`history-filter-indicator--${filter.key}`"
                    :transition="motionSprings.layout"
                    aria-hidden="true"
                  />
                  <span class="history-filter-label">
                    <el-icon v-if="filter.key === 'favorite'"><StarFilled /></el-icon>
                    {{ filter.label }} ({{ filter.count }})
                  </span>
                </motion.button>
              </div>
            </div>
          </template>

          <AnimatePresence mode="wait" :initial="false">
            <motion.div
              v-if="activeHistory.length"
              :key="historyStatus"
              class="history-list history-dialog-list"
              :initial="{ opacity: 0, y: 4 }"
              :animate="{ opacity: 1, y: 0 }"
              :exit="{ opacity: 0, y: -2 }"
              :transition="{ duration: motionDurations.fast, ease: motionEasings.emphasized }"
            >
              <motion.div
                v-for="item in activeHistory"
                :key="item.key"
                layout
                class="history-item"
                :transition="motionSprings.layout"
              >
                <button class="history-main" type="button" @click="selectHistory(item)">
                  <span class="history-primary">病案号 {{ item.bah || '-' }}</span>
                  <span v-if="item.status === 'success'" class="history-secondary">上架号 {{ item.sjh || '-' }} · {{ item.imageCount }} 张影像 · 查询 {{ item.queryCount }} 次</span>
                  <span v-else class="history-secondary">{{ item.failureReason }}</span>
                  <time :datetime="new Date(item.searchedAt).toISOString()">{{ formatHistoryTime(item.searchedAt) }}</time>
                </button>
                <motion.span
                  :key="`${item.key}:${item.favorite}`"
                  class="favorite-motion"
                  :initial="{ scale: 0.86, rotate: item.favorite ? -8 : 0 }"
                  :animate="{ scale: 1, rotate: 0 }"
                  :transition="motionSprings.interaction"
                >
                  <el-button
                    text
                    circle
                    size="small"
                    :class="{ 'is-favorite': item.favorite }"
                    :aria-label="`${item.favorite ? '取消收藏' : '收藏'}病案号 ${item.bah || item.sjh}`"
                    @click.stop="toggleHistoryFavorite(item.key)"
                  >
                    <template #icon>
                      <component :is="item.favorite ? StarFilled : Star" />
                    </template>
                  </el-button>
                </motion.span>
              </motion.div>
            </motion.div>
            <motion.div
              v-else
              :key="`empty-${historyStatus}`"
              :initial="{ opacity: 0 }"
              :animate="{ opacity: 1 }"
              :exit="{ opacity: 0 }"
              :transition="{ duration: motionDurations.fast }"
            >
              <el-empty :image-size="54" :description="activeHistoryEmptyDescription" />
            </motion.div>
          </AnimatePresence>
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
  position: relative;
  display: flex;
  gap: 6px;
}

.history-filter-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 11px;
  font: inherit;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
}

.history-filter-button:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 1px;
}

.history-filter-label {
  position: relative;
  z-index: 1;
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.history-filter-indicator {
  position: absolute;
  inset: -1px;
  z-index: 0;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 7px;
}

.history-filter-indicator--failure {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-5);
}

.history-filter-indicator--favorite {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}

.history-dialog-header {
  display: flex;
  gap: 12px;
  align-items: center;
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

.favorite-motion {
  display: inline-flex;
  flex: none;
  transform-origin: center;
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

@media (max-width: 720px) {
  .history-dialog-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .history-filter-actions {
    flex-wrap: wrap;
  }
}
</style>

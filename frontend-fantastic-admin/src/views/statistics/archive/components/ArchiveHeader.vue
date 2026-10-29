<script setup lang="ts">
import type { ArchiveSearchHistoryItem } from '../composables/useArchiveSearchHistory'
import { CircleCheck, CircleX, Clock3 as Clock, RefreshCw as Refresh } from 'lucide-vue-next'
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { AUTH_STORAGE_KEYS } from '@/utils/auth-storage'
import { isMotionEnabled } from '@/utils/motion-preference'
import {
  archiveAccessMode,
  isExternalArchiveAccessMode,
} from '../access-mode'
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

const externalAccess = computed(() => isExternalArchiveAccessMode(archiveAccessMode.value))
const userStore = useUserStore()
const accessModeLabel = computed(() => {
  if (archiveAccessMode.value === 'external-ticket') {
    return '外部访问'
  }
  return archiveAccessMode.value === 'archive-legacy' ? '开发者模式' : ''
})
const searchHistory = ref<ArchiveSearchHistoryItem[]>([])
const historyVisible = ref(false)
const allHistoryVisible = ref(false)
const historyStatus = ref<'success' | 'failure' | 'favorite'>('success')
let historyListenersAttached = false
let historyGeneration = 0

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

function handleHistoryUpdated() {
  searchHistory.value = externalAccess.value ? [] : readArchiveSearchHistory()
}

function handleStorage(event: StorageEvent) {
  if (event.key === ARCHIVE_SEARCH_HISTORY_STORAGE_KEY || event.key === AUTH_STORAGE_KEYS.token || event.key === null) {
    void syncHistoryAccess()
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

function animateFavorite(button: HTMLButtonElement) {
  if (!isMotionEnabled()) {
    return
  }

  const icon = button.querySelector<SVGSVGElement>('.bookmark-icon')
  const bookmark = button.querySelector<HTMLElement>('.bookmark')

  icon?.animate([
    { transform: 'scaleY(1)' },
    { transform: 'scaleY(0.6)', offset: 0.5 },
    { transform: 'scaleY(1)' },
  ], {
    duration: 300,
    easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
  })

  const circleSize = getComputedStyle(button).getPropertyValue('--icon-circle-size').trim() || '35px'
  bookmark?.animate([
    { width: '0px', height: '0px', opacity: 0, transform: 'translate(-50%, -50%)' },
    { width: circleSize, height: circleSize, opacity: 1, transform: 'translate(-50%, -50%)', offset: 0.9 },
    { opacity: 0, transform: 'translate(-50%, -50%)' },
  ], {
    duration: 300,
    delay: 300,
    easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
    fill: 'forwards',
    pseudoElement: '::before',
  })
  bookmark?.animate([
    { transform: 'translate(-50%, -50%) scale(0)', opacity: 0 },
    { transform: 'translate(-50%, -50%) scale(0.8)', opacity: 1, offset: 0.4 },
    { transform: 'translate(-50%, -50%) scale(0.8)', opacity: 0 },
  ], {
    duration: 300,
    delay: 300,
    easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
    fill: 'forwards',
    pseudoElement: '::after',
  })
}

function toggleHistoryFavorite(item: ArchiveSearchHistoryItem, event: MouseEvent) {
  if (!item.favorite) {
    animateFavorite(event.currentTarget as HTMLButtonElement)
  }
  searchHistory.value = toggleArchiveSearchHistoryFavorite(item.key)
}

function openAllHistory() {
  historyVisible.value = false
  historyStatus.value = 'success'
  allHistoryVisible.value = true
}

function attachHistoryListeners() {
  if (historyListenersAttached) {
    return
  }
  window.addEventListener(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, handleHistoryUpdated)
  window.addEventListener('storage', handleStorage)
  historyListenersAttached = true
}

function detachHistoryListeners() {
  if (!historyListenersAttached) {
    return
  }
  window.removeEventListener(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, handleHistoryUpdated)
  window.removeEventListener('storage', handleStorage)
  historyListenersAttached = false
}

async function syncHistoryAccess() {
  const generation = ++historyGeneration
  searchHistory.value = []
  historyVisible.value = false
  allHistoryVisible.value = false
  // Invalidate old memory immediately; login persists auth storage later in the same tick.
  readArchiveSearchHistory()
  if (externalAccess.value) {
    detachHistoryListeners()
    searchHistory.value = []
    historyVisible.value = false
    allHistoryVisible.value = false
    return
  }

  attachHistoryListeners()
  await nextTick()
  if (generation !== historyGeneration || externalAccess.value) { return }
  searchHistory.value = readArchiveSearchHistory()
  const history = await loadArchiveSearchHistory()
  if (generation === historyGeneration && !externalAccess.value) {
    searchHistory.value = history
  }
}

watch([externalAccess, () => userStore.token, () => userStore.isSessionVerified, () => userStore.profile.id], () => { void syncHistoryAccess() }, { immediate: true, flush: 'sync' })

onUnmounted(() => {
  historyGeneration++
  detachHistoryListeners()
})
</script>

<template>
  <header class="archive-header">
    <div class="archive-heading">
      <div class="archive-title">
        <h2>住院病案</h2>
        <span v-if="accessModeLabel" class="access-mode-label">{{ accessModeLabel }}</span>
      </div>
      <div class="heading-actions">
        <el-button text size="small" :icon="Refresh" :loading="props.loading" @click="emit('refresh')">
          刷新
        </el-button>
        <template v-if="!externalAccess">
          <el-popover
            v-model:visible="historyVisible"
            placement="bottom-end"
            trigger="click"
            width="min(380px, calc(100vw - 24px))"
          >
            <template #reference>
              <el-button text size="small" :icon="Clock">
                最近查询
                <span v-if="successfulHistory.length" class="history-count">{{ successfulHistory.length }}</span>
              </el-button>
            </template>

            <div class="history-panel">
              <div class="history-header">
                <div class="history-heading-copy">
                  <strong>搜索记录</strong>
                  <span>{{ successfulHistory.length }} 条成功查询</span>
                </div>
                <div class="history-actions">
                  <el-button v-if="searchHistory.length" text size="small" aria-label="查看全部搜索记录" @click="openAllHistory">
                    查看全部
                  </el-button>
                </div>
              </div>

              <div v-if="displayedSuccessfulHistory.length" class="history-groups">
                <section class="history-group">
                  <div class="history-group-heading">
                    <span>最近查询</span>
                    <span>最多显示 {{ ARCHIVE_SEARCH_HISTORY_DISPLAY_LIMIT }} 条</span>
                  </div>
                  <div class="history-list">
                    <div v-for="item in displayedSuccessfulHistory" :key="item.key" class="history-item">
                      <button class="history-main" type="button" @click="selectHistory(item)">
                        <span class="history-main-content">
                          <span class="history-primary">{{ item.bah ? `病案号 ${item.bah}` : `上架号 ${item.sjh}` }}</span>
                          <span class="history-secondary">上架号 {{ item.sjh || '-' }} · {{ item.imageCount }} 张影像 · 查询 {{ item.queryCount }} 次</span>
                        </span>
                        <span class="history-main-trailing">
                          <time :datetime="new Date(item.searchedAt).toISOString()">{{ formatHistoryTime(item.searchedAt) }}</time>
                        </span>
                      </button>
                      <button
                        type="button"
                        class="history-favorite-button"
                        :class="{ 'is-favorite': item.favorite }"
                        :aria-pressed="item.favorite"
                        :aria-label="`${item.favorite ? '取消收藏' : '收藏'}${item.bah ? `病案号 ${item.bah}` : `上架号 ${item.sjh}`}`"
                        @click.stop="toggleHistoryFavorite(item, $event)"
                      >
                        <span class="bookmark" aria-hidden="true">
                          <svg class="bookmark-icon" viewBox="0 0 32 32" focusable="false">
                            <path d="M27 4v27a1 1 0 0 1-1.625.781L16 24.281l-9.375 7.5A1 1 0 0 1 5 31V4a4 4 0 0 1 4-4h14a4 4 0 0 1 4 4z" />
                          </svg>
                        </span>
                      </button>
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
                <div class="history-filter-actions" role="group" aria-label="筛选搜索记录">
                  <button
                    type="button"
                    class="history-filter-button"
                    :class="{ 'is-active': historyStatus === 'success' }"
                    :aria-pressed="historyStatus === 'success'"
                    @click="historyStatus = 'success'"
                  >
                    <CircleCheck :size="14" aria-hidden="true" />
                    <span>成功查询</span><span class="history-filter-count">{{ successfulHistory.length }}</span>
                  </button>
                  <button
                    type="button"
                    class="history-filter-button"
                    :class="{ 'is-active': historyStatus === 'failure' }"
                    :aria-pressed="historyStatus === 'failure'"
                    @click="historyStatus = 'failure'"
                  >
                    <CircleX :size="14" aria-hidden="true" />
                    <span>失败查询</span><span class="history-filter-count">{{ failedHistory.length }}</span>
                  </button>
                  <button
                    type="button"
                    class="history-filter-button"
                    :class="{ 'is-active': historyStatus === 'favorite' }"
                    :aria-pressed="historyStatus === 'favorite'"
                    @click="historyStatus = 'favorite'"
                  >
                    <span>收藏病案</span><span class="history-filter-count">{{ favoriteHistory.length }}</span>
                  </button>
                </div>
              </div>
            </template>
            <Transition name="history-tab" mode="out-in">
              <div v-if="activeHistory.length" :key="historyStatus" class="history-list history-dialog-list">
                <div v-for="item in activeHistory" :key="item.key" class="history-item">
                  <button class="history-main" type="button" @click="selectHistory(item)">
                    <span class="history-main-content">
                      <span class="history-primary">{{ item.bah ? `病案号 ${item.bah}` : `上架号 ${item.sjh}` }}</span>
                      <span v-if="item.status === 'success'" class="history-secondary">上架号 {{ item.sjh || '-' }} · {{ item.imageCount }} 张影像 · 查询 {{ item.queryCount }} 次</span>
                      <span v-else class="history-secondary">{{ item.failureReason }}</span>
                    </span>
                    <span class="history-main-trailing">
                      <span v-if="item.status === 'failure'" class="history-status">查询失败</span>
                      <time :datetime="new Date(item.searchedAt).toISOString()">{{ formatHistoryTime(item.searchedAt) }}</time>
                    </span>
                  </button>
                  <button
                    type="button"
                    class="history-favorite-button"
                    :class="{ 'is-favorite': item.favorite }"
                    :aria-pressed="item.favorite"
                    :aria-label="`${item.favorite ? '取消收藏' : '收藏'}${item.bah ? `病案号 ${item.bah}` : `上架号 ${item.sjh}`}`"
                    @click.stop="toggleHistoryFavorite(item, $event)"
                  >
                    <span class="bookmark" aria-hidden="true">
                      <svg class="bookmark-icon" viewBox="0 0 32 32" focusable="false">
                        <path d="M27 4v27a1 1 0 0 1-1.625.781L16 24.281l-9.375 7.5A1 1 0 0 1 5 31V4a4 4 0 0 1 4-4h14a4 4 0 0 1 4 4z" />
                      </svg>
                    </span>
                  </button>
                </div>
              </div>
              <el-empty
                v-else
                :key="historyStatus"
                :image-size="54"
                :description="historyStatus === 'success' ? '暂无成功查询记录' : historyStatus === 'failure' ? '暂无失败查询记录' : '暂无收藏病案'"
              />
            </Transition>
          </el-dialog>
        </template>
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
  border-radius: var(--mrr-radius-pill);
}

.history-panel {
  display: grid;
  gap: 8px;
}

.history-header,
.history-item {
  display: flex;
  align-items: center;
}

.history-header {
  gap: 12px;
  justify-content: space-between;
  padding: 0 2px 8px;
  border-bottom: 1px solid var(--divider);
}

.history-heading-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.history-heading-copy strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.history-heading-copy > span {
  font-size: 11px;
  color: var(--text-secondary);
}

.history-actions {
  display: flex;
  flex: none;
  gap: 4px;
}

.history-list {
  display: grid;
  gap: 4px;
}

.history-groups {
  display: grid;
  gap: 6px;
  max-height: min(420px, calc(100vh - 180px));
  padding-right: 3px;
  overflow: hidden auto;
  overscroll-behavior: contain;
}

.history-group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
}

.history-dialog-list {
  max-height: min(56vh, 520px);
  padding-right: 4px;
  overflow: hidden auto;
  overscroll-behavior: contain;
}

.history-filter-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 3px;
  width: 100%;
  padding: 3px;
  background: var(--surface-muted);
  border-radius: var(--mrr-radius-md);
}

.history-dialog-header {
  display: grid;
  gap: 10px;
  width: 100%;
  padding-right: 16px;
}

.history-filter-button {
  display: flex;
  gap: 5px;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: var(--mrr-control-height);
  min-height: var(--mrr-control-height);
  padding: 0 5px;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
  transition:
    color var(--mrr-motion-fast) cubic-bezier(0.22, 1, 0.36, 1),
    background-color var(--mrr-motion-fast) cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow var(--mrr-motion-fast) cubic-bezier(0.22, 1, 0.36, 1);
}

.history-tab-enter-active,
.history-tab-leave-active {
  transition:
    opacity 180ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.history-tab-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.history-tab-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.history-filter-button:hover {
  color: var(--text-primary);
}

.history-filter-button.is-active {
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}

.history-filter-button:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 1px;
}

.history-filter-count {
  min-width: 18px;
  padding: 1px 5px;
  font-size: 11px;
  line-height: 16px;
  color: inherit;
  text-align: center;
  background: var(--surface-muted);
  border-radius: var(--mrr-radius-pill);
}

.history-filter-button.is-active .history-filter-count {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.history-status {
  flex: none;
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 600;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-radius: var(--mrr-radius-sm);
}

.history-item {
  gap: 6px;
  padding: 5px 4px;
  border-radius: var(--mrr-radius-md);
  transition: background-color var(--mrr-motion-fast) ease;
}

.history-item:hover {
  background: var(--surface-muted);
}

.history-favorite-button {
  --icon-size: 18px;
  --icon-secondary-color: var(--text-secondary);
  --icon-hover-color: var(--text-primary);
  --icon-primary-color: gold;
  --icon-circle-border: 1px solid var(--icon-primary-color);
  --icon-circle-size: 35px;

  position: relative;
  display: grid;
  flex: 0 0 var(--mrr-control-height);
  place-items: center;
  width: var(--mrr-control-height);
  height: var(--mrr-control-height);
  padding: 0;
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 50%;
}

.history-favorite-button:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 1px;
}

.bookmark {
  position: relative;
  display: grid;
  place-items: center;
  width: var(--icon-size);
  height: var(--icon-size);
  overflow: visible;
}

.bookmark-icon {
  width: var(--icon-size);
  height: var(--icon-size);
  fill: var(--icon-secondary-color);
  transform-origin: top;
  transition: fill 0.2s, transform 0.2s;
}

.bookmark::after {
  position: absolute;
  /* stylelint-disable-next-line declaration-block-no-redundant-longhand-properties -- Chrome 86 does not support inset. */
  top: 50%;
  right: auto;
  bottom: auto;
  left: 50%;
  width: 10px;
  height: 10px;
  margin: 0;
  content: "";
  border-radius: 50%;
  box-shadow:
    0 30px 0 -4px var(--icon-primary-color),
    30px 0 0 -4px var(--icon-primary-color),
    0 -30px 0 -4px var(--icon-primary-color),
    -30px 0 0 -4px var(--icon-primary-color),
    -22px 22px 0 -4px var(--icon-primary-color),
    -22px -22px 0 -4px var(--icon-primary-color),
    22px -22px 0 -4px var(--icon-primary-color),
    22px 22px 0 -4px var(--icon-primary-color);
  opacity: 0;
  transform: translate(-50%, -50%) scale(0);
}

.bookmark::before {
  position: absolute;
  /* stylelint-disable-next-line declaration-block-no-redundant-longhand-properties -- Chrome 86 does not support inset. */
  top: 50%;
  right: auto;
  bottom: auto;
  left: 50%;
  width: 0;
  height: 0;
  margin: 0;
  content: "";
  border: var(--icon-circle-border);
  border-radius: 50%;
  opacity: 0;
  transform: translate(-50%, -50%);
}

.history-favorite-button:hover .bookmark-icon {
  fill: var(--icon-hover-color);
}

.history-favorite-button:active .bookmark-icon {
  transform: scale(0.86);
}

.history-favorite-button.is-favorite .bookmark-icon {
  fill: var(--icon-primary-color);
}

.history-main {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 12px;
  align-items: center;
  min-width: 0;
  padding: 5px 6px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
}

.history-main-content {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.history-main-trailing {
  display: flex;
  gap: 6px;
  align-items: center;
  align-self: center;
  justify-content: flex-end;
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
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.history-secondary,
.history-main time {
  font-size: 11px;
  color: var(--text-secondary);
}

.history-secondary {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-title {
  display: flex;
  gap: 8px;
  align-items: center;
}

.archive-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.access-mode-label {
  padding: 2px 7px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: var(--mrr-radius-pill);
}
</style>

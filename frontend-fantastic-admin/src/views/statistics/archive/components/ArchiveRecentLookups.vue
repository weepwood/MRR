<script setup lang="ts">
import type { ArchiveSearchHistoryItem } from '@/views/statistics/archive/composables/useArchiveSearchHistory'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { AUTH_STORAGE_KEYS } from '@/utils/auth-storage'
import {
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY,
  ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT,
  loadArchiveSearchHistory,
  readArchiveSearchHistory,
} from '@/views/statistics/archive/composables/useArchiveSearchHistory'

/*
 * 最近成功查询的只读列表，首页与档案袋空态共用。
 *
 * - load=true：挂载时向服务端读取一次（首页）；
 * - load=false：只读当前会话内存并监听更新事件（档案袋页，服务端读取由 ArchiveHeader 负责，避免重复请求）。
 * 删除、收藏与「查看全部」仍在档案袋头部的搜索记录面板中完成。
 */

defineOptions({ name: 'ArchiveRecentLookups' })

const props = withDefaults(defineProps<{
  limit?: number
  load?: boolean
  heading?: string
  headingTag?: 'h2' | 'h3'
}>(), {
  limit: 6,
  load: false,
  heading: '最近查询',
  headingTag: 'h2',
})

const emit = defineEmits<{
  select: [item: ArchiveSearchHistoryItem]
}>()

const userStore = useUserStore()
let historyGeneration = 0
const history = ref<ArchiveSearchHistoryItem[]>([])
const loading = ref(props.load)
const headingId = `recent-lookups-${useId()}`

/** 收藏在前，其余按查询时间倒序；只保留成功的查询。 */
const recent = computed(() => history.value
  .filter(item => item.status === 'success')
  .sort((left, right) => Number(right.favorite) - Number(left.favorite) || right.searchedAt - left.searchedAt)
  .slice(0, Math.max(1, props.limit)))

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function formatTime(timestamp: number) {
  return timeFormatter.format(new Date(timestamp))
}

function itemLabel(item: ArchiveSearchHistoryItem) {
  const codes = [item.bah ? `病案号 ${item.bah}` : '', item.sjh ? `上架号 ${item.sjh}` : ''].filter(Boolean).join('，')
  return `打开${codes}，${item.imageCount} 张影像${item.favorite ? '，已收藏' : ''}`
}

function refreshFromSession() {
  history.value = readArchiveSearchHistory()
}

async function syncHistoryAccess() {
  const generation = ++historyGeneration
  history.value = []
  loading.value = props.load && userStore.isSessionVerified
  // Invalidates the previous memory session before the store persists the next token.
  readArchiveSearchHistory()
  await nextTick()
  if (generation !== historyGeneration) {
    return
  }
  refreshFromSession()
  if (!loading.value) {
    return
  }
  try {
    const items = await loadArchiveSearchHistory()
    if (generation === historyGeneration) {
      history.value = items
    }
  }
  finally {
    if (generation === historyGeneration) {
      loading.value = false
    }
  }
}

function handleStorage(event: StorageEvent) {
  if (event.key === AUTH_STORAGE_KEYS.token || event.key === ARCHIVE_SEARCH_HISTORY_STORAGE_KEY || event.key === null) {
    void syncHistoryAccess()
  }
}

watch([() => userStore.token, () => userStore.isSessionVerified, () => userStore.profile.id], () => {
  void syncHistoryAccess()
}, { flush: 'sync' })

onMounted(() => {
  window.addEventListener(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, refreshFromSession)
  window.addEventListener('storage', handleStorage)
  void syncHistoryAccess()
})

onBeforeUnmount(() => {
  historyGeneration++
  window.removeEventListener(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, refreshFromSession)
  window.removeEventListener('storage', handleStorage)
})
</script>

<template>
  <section class="recent-lookups" :aria-labelledby="headingId">
    <header class="recent-lookups__head">
      <component :is="props.headingTag" :id="headingId" class="recent-lookups__title">
        {{ props.heading }}
      </component>
      <slot name="actions" />
    </header>

    <ul v-if="loading" class="recent-lookups__list" aria-busy="true" aria-label="正在读取最近查询">
      <li v-for="index in 3" :key="index" class="recent-lookups__placeholder" />
    </ul>
    <ol v-else-if="recent.length" class="recent-lookups__list">
      <li v-for="item in recent" :key="item.key">
        <button type="button" class="recent-lookups__item" :aria-label="itemLabel(item)" @click="emit('select', item)">
          <FaIcon v-if="item.favorite" name="i-lucide:star" class="recent-lookups__star" aria-hidden="true" />
          <span class="recent-lookups__codes">
            <span class="recent-lookups__code">{{ item.bah || item.sjh }}</span>
            <span v-if="item.bah && item.sjh" class="recent-lookups__code is-secondary">{{ item.sjh }}</span>
          </span>
          <span class="recent-lookups__count">{{ item.imageCount.toLocaleString('zh-CN') }} 张</span>
          <time class="recent-lookups__time" :datetime="new Date(item.searchedAt).toISOString()">
            {{ formatTime(item.searchedAt) }}
          </time>
        </button>
      </li>
    </ol>
    <p v-else class="recent-lookups__empty">
      <slot name="empty">
        还没有查询记录，查询成功后最近 {{ props.limit }} 次会显示在这里。
      </slot>
    </p>
  </section>
</template>

<style scoped>
.recent-lookups {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: var(--mrr-space-3);
  min-width: 0;
}

.recent-lookups__head {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: baseline;
  justify-content: space-between;
}

.recent-lookups__title {
  margin: 0;
  font-size: var(--mrr-font-size-md);
  font-weight: 650;
  line-height: 1.35;
  color: var(--mrr-foreground);
}

.recent-lookups__list {
  display: grid;
  gap: 2px;
  align-content: start;
  padding: 0;
  margin: 0;
  list-style: none;
}

.recent-lookups__item {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto auto;
  gap: var(--mrr-space-3);
  align-items: center;
  width: 100%;
  min-height: 36px;
  padding: var(--mrr-space-1) var(--mrr-space-2);
  font: inherit;
  color: var(--mrr-foreground);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-md);
  transition: background-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

/* 所在底色不同（首页右栏是工作区底，档案袋卡片是卡面），悬停底色由外层覆盖。 */
.recent-lookups__item:hover {
  background: var(--recent-lookups-hover, var(--mrr-muted));
}

.recent-lookups__item:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 2px;
}

.recent-lookups__star {
  grid-column: 1;
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-tone-warning);
}

.recent-lookups__codes {
  display: flex;
  grid-column: 2;
  gap: var(--mrr-space-2);
  align-items: baseline;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.recent-lookups__code {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-md);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.recent-lookups__code.is-secondary {
  font-size: var(--mrr-font-size-sm);
  font-weight: 400;
  color: var(--mrr-muted-foreground);
}

.recent-lookups__count,
.recent-lookups__time {
  font-size: var(--mrr-font-size-xs);
  font-variant-numeric: tabular-nums;
  color: var(--mrr-muted-foreground);
  white-space: nowrap;
}

.recent-lookups__count {
  text-align: right;
}

.recent-lookups__placeholder {
  height: 36px;
  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-md);
}

.recent-lookups__empty {
  align-self: start;
  margin: 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

@media (max-width: 760px) {
  .recent-lookups__item {
    grid-template-columns: 16px minmax(0, 1fr) auto;
  }

  .recent-lookups__time {
    display: none;
  }
}
</style>

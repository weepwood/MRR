<script setup lang="ts">
import type { ArchiveSearchHistoryItem } from '@/views/statistics/archive/composables/useArchiveSearchHistory'
import { nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import ArchiveRecentLookups from '@/views/statistics/archive/components/ArchiveRecentLookups.vue'

const router = useRouter()
const open = ref(false)
const mounted = ref(false)
const trigger = ref<{ $el?: HTMLElement }>()
const panel = ref<HTMLElement>()

function prepareHistory() {
  mounted.value = true
}
function closeHistory(event?: KeyboardEvent) {
  if (!open.value) { return }
  event?.preventDefault()
  open.value = false
  trigger.value?.$el?.focus()
}
async function focusHistory() {
  await nextTick()
  if (open.value && document.activeElement === trigger.value?.$el) { panel.value?.focus() }
}
function selectHistory(item: ArchiveSearchHistoryItem) {
  open.value = false
  const query: Record<string, string> = {}
  if (item.bah) { query.bah = item.bah }
  if (item.sjh) { query.sjh = item.sjh }
  return router.push({ path: '/archive/embed', query })
}
</script>

<template>
  <el-popover
    v-model:visible="open"
    trigger="click"
    role="dialog"
    title="最近查询"
    :hide-after="0"
    placement="bottom-end"
    width="min(380px, calc(100vw - 24px))"
    popper-class="mrr-light-popover home-archive-history-popper"
    :teleported="true"
    :show-arrow="false"
    @before-enter="prepareHistory"
    @after-enter="focusHistory"
  >
    <template #reference>
      <el-button ref="trigger" class="home-archive-history-trigger" text circle aria-label="最近查询" aria-haspopup="dialog" :aria-expanded="open" title="最近查询" @keydown.esc.stop="closeHistory">
        <FaIcon name="i-lucide:history" />
      </el-button>
    </template>
    <div ref="panel" class="home-archive-history-panel" tabindex="-1" @click.stop @keydown.esc.stop="closeHistory">
      <ArchiveRecentLookups v-if="mounted" load heading-tag="h3" @select="selectHistory">
        <template #actions>
          <el-button text circle class="home-history-close" aria-label="关闭最近查询" @click="closeHistory()">
            <FaIcon name="i-lucide:x" />
          </el-button>
        </template>
        <template #empty>
          <span class="home-history-empty">
            <FaIcon name="i-lucide:history" class="home-history-empty__icon" aria-hidden="true" />
            <strong>暂无查询记录</strong>
            <span>最近成功查询的病案将显示在这里</span>
          </span>
        </template>
      </ArchiveRecentLookups>
    </div>
  </el-popover>
</template>

<style scoped>
/* Reuse the lightweight popover surface, retaining a bounded width. */
:global(body .el-popover.el-popper.mrr-light-popover.home-archive-history-popper) {
  width: min(380px, calc(100vw - 24px));
  min-width: 0;
  padding: var(--mrr-space-3);
}

/* The shared history header already renders the visible title. */
:global(.home-archive-history-popper .el-popover__title) {
  display: none;
}

.home-archive-history-panel {
  min-width: 0;
  outline: none;
}

.home-archive-history-panel :deep(.recent-lookups__head) {
  align-items: center;
  padding: 0 var(--mrr-space-1) var(--mrr-space-2);
  border-bottom: 1px solid var(--mrr-border);
}

.home-archive-history-panel :deep(.recent-lookups__title) {
  font-size: var(--mrr-font-size-md);
  font-weight: 600;
}

.home-archive-history-panel .home-history-close.el-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  min-width: 28px;
  height: 28px;
  min-height: 28px;
  padding: 0;
  color: var(--mrr-muted-foreground);
  border-radius: var(--mrr-radius-sm);
}

.home-archive-history-panel .home-history-close.el-button:hover {
  background: var(--mrr-muted);
}

.home-history-close :deep(svg) {
  width: 16px;
  height: 16px;
}

.home-history-empty {
  display: grid;
  gap: var(--mrr-space-1);
  place-items: center;
  padding: var(--mrr-space-3) var(--mrr-space-1);
  text-align: center;
}

.home-history-empty strong {
  font-size: var(--mrr-font-size-sm);
  font-weight: 500;
  color: var(--mrr-foreground);
}

.home-history-empty > span:last-child {
  font-size: var(--mrr-font-size-xs);
}

.home-history-empty__icon {
  width: 24px;
  height: 24px;
  margin-bottom: var(--mrr-space-1);
  color: var(--mrr-muted-foreground);
}

.home-archive-history-panel :deep(.recent-lookups__list) {
  max-height: min(240px, 45vh);
  overflow-y: auto;
}

@media (pointer: coarse) {
  .home-archive-history-trigger,
  .home-archive-history-panel .home-history-close.el-button {
    min-width: 44px;
    min-height: 44px;
  }

  .home-archive-history-panel :deep(.recent-lookups__item) {
    min-height: 44px;
  }
}
</style>

<script setup lang="ts">
import type { ArchiveScanCursor } from '@/api/modules/data-relations'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getArchiveScanPage } from '@/api/modules/data-relations'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import MrrScanContentDialog from '@/components/MrrScanContentDialog/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { hasPermission } from '@/utils/session'
import OcrPanel from '@/views/statistics/archive/components/OcrPanel.vue'

const props = defineProps<{ archiveId: number }>()
const selectedId = ref<number | null>(null)
const rows = ref<Record<string, unknown>[]>([])
const nextCursor = ref<ArchiveScanCursor | null>(null)
const cursors = ref<(ArchiveScanCursor | null)[]>([null])
const page = ref(0)
const busy = ref(false)
const failed = ref(false)
let generation = 0
let controller: AbortController | undefined
interface NavigationIntent { edge: 'first' | 'last', focusMode: boolean }
let retryIntent: NavigationIntent | undefined
const crossing = ref(false)
const selectedIndex = computed(() => rows.value.findIndex(row => Number(row.id) === selectedId.value))
const selected = computed(() => rows.value[selectedIndex.value])
const focusMode = ref(false)
const listRegion = ref<HTMLElement>()
const canRead = computed(() => hasPermission('system:read') && hasPermission('record:read'))
const canOcr = computed(() => canRead.value && hasPermission('ocr:read'))

function clear() {
  selectedId.value = null
  focusMode.value = false
}
function selectRow(index: number, focus = false) {
  if (!canRead.value || busy.value || index < 0 || index >= rows.value.length) { return }
  selectedId.value = Number(rows.value[index].id)
  const target = listRegion.value?.querySelector<HTMLButtonElement>(`[data-scan-id="${selectedId.value}"]`)
  if (focus) { target?.focus() }
  else if (target && listRegion.value) {
    const bounds = target.getBoundingClientRect()
    const list = listRegion.value.getBoundingClientRect()
    if (bounds.top < list.top) { listRegion.value.scrollTop -= list.top - bounds.top }
    else if (bounds.bottom > list.bottom) { listRegion.value.scrollTop += bounds.bottom - list.bottom }
  }
}
function navigateImage(direction: -1 | 1, index = selectedIndex.value, focus = false) {
  if (!canRead.value || busy.value || index < 0) { return }
  const next = index + direction
  if (next >= 0 && next < rows.value.length) {
    selectRow(next, focus)
    return
  }
  if ((direction === 1 && nextCursor.value) || (direction === -1 && page.value > 0)) {
    void load(page.value + direction, { edge: direction === 1 ? 'first' : 'last', focusMode: focusMode.value })
  }
}
function navigateList(event: KeyboardEvent) {
  if (!(event.target instanceof HTMLButtonElement) || !event.target.matches('.scan-row')) { return }
  const index = rows.value.findIndex(row => String(row.id) === (event.target as HTMLButtonElement).dataset.scanId)
  if (index < 0) { return }
  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    event.preventDefault()
    navigateImage(event.key === 'ArrowUp' ? -1 : 1, index, true)
  }
  else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    selectRow(event.key === 'Home' ? 0 : rows.value.length - 1, true)
  }
}
function comparePosition(a: ArchiveScanCursor, b: ArchiveScanCursor) {
  if (a.pages === null && b.pages !== null) { return 1 }
  if (a.pages !== null && b.pages === null) { return -1 }
  return (a.pages ?? 0) - (b.pages ?? 0) || a.id - b.id
}
function reset() {
  generation++
  controller?.abort()
  controller = undefined
  clear()
  rows.value = []
  nextCursor.value = null
  cursors.value = [null]
  page.value = 0
  busy.value = false
  failed.value = false
  crossing.value = false
  retryIntent = undefined
}
async function load(index = 0, intent?: NavigationIntent) {
  if (!canRead.value || busy.value || index < 0 || index >= cursors.value.length) { return }
  const archive = props.archiveId
  const cursor = cursors.value[index]
  const current = ++generation
  controller?.abort()
  controller = new AbortController()
  clear()
  rows.value = []
  nextCursor.value = null
  page.value = index
  busy.value = true
  failed.value = false
  crossing.value = !!intent
  retryIntent = intent
  try {
    const data = (await getArchiveScanPage(archive, cursor, controller.signal)).data
    if (generation !== current || !canRead.value || props.archiveId !== archive) { return }
    if (!data || data.archiveId !== archive || data.rows.length > 50) { throw new Error('Invalid archive page') }
    const ids = new Set<number>()
    let last = cursor
    for (const row of data.rows) {
      const id = Number(row.id)
      if (!Number.isSafeInteger(id) || id <= 0 || ids.has(id) || Number(row.archiveId) !== archive || !Number.isFinite(Number(row.uploadFlag)) || Number(row.uploadFlag) === 0) { throw new Error('Invalid scan identity') }
      const pages = row.pages
      if (pages !== null && (typeof pages !== 'number' || !Number.isSafeInteger(pages))) { throw new Error('Invalid page number') }
      const position = { id, pages }
      if (last && comparePosition(position, last) <= 0) { throw new Error('Invalid scan order') }
      ids.add(id)
      last = position
    }
    if (data.nextCursor && (!data.rows.length || !last || data.nextCursor.id !== last.id || data.nextCursor.pages !== last.pages)) { throw new Error('Invalid cursor') }
    rows.value = data.rows
    nextCursor.value = data.nextCursor
    cursors.value = cursors.value.slice(0, index + 1)
    if (data.nextCursor) { cursors.value.push(data.nextCursor) }
    retryIntent = undefined
    if (intent && rows.value.length) {
      const target = intent.edge === 'first' ? 0 : rows.value.length - 1
      // Only select after validating the new page. Restore focus after the list is rendered.
      busy.value = false
      focusMode.value = intent.focusMode
      selectRow(target)
      const targetId = selectedId.value
      await nextTick()
      if (generation === current && canRead.value && selectedId.value === targetId) {
        listRegion.value?.querySelector<HTMLButtonElement>(`[data-scan-id="${targetId}"]`)?.focus()
      }
    }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { busy.value = false; crossing.value = false }
  }
}

function retryPage() {
  void load(page.value, retryIntent)
}

watch([() => props.archiveId, canRead], () => { reset(); void load() }, { immediate: true, flush: 'sync' })
watch(() => useUserStore().token, reset, { flush: 'sync' })
onBeforeUnmount(reset)
</script>

<template>
  <MrrSectionCard v-if="canRead" title="影像核对" description="按页码和影像 ID 分页读取当前有效影像，每页最多 50 条；实时列表并非固定快照。">
    <div class="scan-inspector" :class="{ 'scan-inspector--focus': focusMode }">
      <div>
        <div class="scan-pagination">
          <el-button size="small" :disabled="busy || page === 0" @click="load(page - 1)">
            上一页
          </el-button>
          <span>第 {{ page + 1 }} 组</span>
          <el-button size="small" :disabled="busy || !nextCursor" @click="load(page + 1)">
            下一页
          </el-button>
          <el-button size="small" :disabled="busy" @click="reset(); load()">
            刷新列表
          </el-button>
        </div>
        <AppError v-if="failed" size="compact" message="影像列表读取失败，请重试" @retry="retryPage" />
        <el-skeleton v-else-if="busy" :rows="3" animated />
        <div v-else ref="listRegion" class="scan-list" role="group" aria-label="有效影像列表" @keydown="navigateList">
          <button v-for="(row, index) in rows" :key="Number(row.id)" type="button" class="scan-row" :data-scan-id="row.id" :aria-pressed="selectedId === Number(row.id)" @click="selectRow(index)">
            <span>第 {{ row.pages ?? '—' }} 页</span>
            <small>影像 #{{ row.id }} · 类型 {{ row.btype ?? '未知' }}</small>
          </button>
          <p v-if="rows.length" class="scan-list-note">
            本组 {{ rows.length }} 条 · 方向键可跨组
          </p>
          <AppEmpty v-if="!rows.length" size="compact" description="暂无可核对的有效影像" />
        </div>
      </div>
      <div class="scan-main">
        <template v-if="selected && selectedId !== null">
          <div class="scan-toolbar">
            <strong>影像 #{{ selectedId }} · 第 {{ selected.pages ?? '—' }} 页</strong>
            <div class="scan-actions">
              <span class="scan-position">第 {{ page + 1 }} 组 · {{ selectedIndex + 1 }} / {{ rows.length }}</span>
              <el-button size="small" :disabled="selectedIndex <= 0 && page === 0" @click="navigateImage(-1)">
                上一张
              </el-button>
              <el-button size="small" :disabled="selectedIndex >= rows.length - 1 && !nextCursor" @click="navigateImage(1)">
                下一张
              </el-button>
              <el-button size="small" :aria-pressed="focusMode" @click="focusMode = !focusMode">
                {{ focusMode ? '恢复布局' : '专注原图' }}
              </el-button>
              <el-button size="small" @click="clear">
                关闭原图
              </el-button>
            </div>
          </div>
          <div class="scan-review" :class="{ 'scan-review--ocr': canOcr && !focusMode }">
            <MrrScanContentDialog :scan-id="selectedId" inline @close="clear" />
            <OcrPanel v-if="canOcr && !focusMode" :scan-id="selectedId" :expected-archive-id="archiveId" :unclassified="selected.btype != null && Number(selected.btype) === 0" inline />
          </div>
          <p class="scan-note">
            类型：{{ selected.btype ?? '未知' }} · 迁移：{{ selected.migrationStatus ?? '未知' }}
          </p>
        </template>
        <p v-else-if="busy && crossing" class="scan-note" role="status">
          正在读取相邻影像组…
        </p>
        <AppEmpty v-else size="compact" description="从影像列表选择一张原图开始核对" />
      </div>
    </div>
  </MrrSectionCard>
</template>

<style scoped>
.scan-inspector {
  display: grid;
  grid-template-columns: minmax(130px, 168px) minmax(0, 1fr);
  gap: var(--mrr-space-3);
  min-width: 0;
}

.scan-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-1);
  align-items: center;
  margin-bottom: var(--mrr-space-2);
}

.scan-review {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--mrr-space-3);
  align-items: start;
}

.scan-review--ocr {
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
}

.scan-inspector--focus .scan-review {
  grid-template-columns: minmax(0, 1fr);
}

.scan-list {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-1);
  max-height: 420px;
  overflow: auto;
}

.scan-row {
  display: grid;
  gap: var(--mrr-space-1);
  padding: var(--mrr-space-2);
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.scan-row[aria-pressed="true"] {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.scan-row:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}

.scan-row small,
.scan-note,
.scan-list-note,
.scan-position {
  color: var(--el-text-color-secondary);
}

.scan-main {
  min-width: 0;
}

.scan-toolbar,
.scan-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
}

.scan-toolbar {
  margin-bottom: var(--mrr-space-2);
}

.scan-note,
.scan-list-note {
  margin-top: var(--mrr-space-2);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .scan-review--ocr {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .scan-inspector {
    grid-template-columns: minmax(0, 1fr);
  }

  .scan-list {
    max-height: 180px;
  }
}
</style>

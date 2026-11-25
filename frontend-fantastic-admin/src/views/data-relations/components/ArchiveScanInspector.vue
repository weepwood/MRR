<script setup lang="ts">
import type { ArchiveScanCursor } from '@/api/modules/data-relations'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
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
const selected = computed(() => rows.value.find(row => Number(row.id) === selectedId.value))
const canRead = computed(() => hasPermission('system:read') && hasPermission('record:read'))
const canOcr = computed(() => canRead.value && hasPermission('ocr:read'))

function clear() {
  selectedId.value = null
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
}
async function load(index = 0) {
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
  try {
    const data = (await getArchiveScanPage(archive, cursor, controller.signal)).data
    if (generation !== current) { return }
    if (!data || data.archiveId !== archive || data.rows.length > 50) { throw new Error('Invalid archive page') }
    const ids = new Set<number>()
    for (const row of data.rows) {
      const id = Number(row.id)
      if (!Number.isSafeInteger(id) || id <= 0 || ids.has(id) || Number(row.archiveId) !== archive || !Number.isFinite(Number(row.uploadFlag)) || Number(row.uploadFlag) === 0) { throw new Error('Invalid scan identity') }
      ids.add(id)
    }
    if (data.nextCursor && (!data.rows.length || data.nextCursor.id !== Number(data.rows.at(-1)?.id) || (data.nextCursor.id === cursor?.id && data.nextCursor.pages === cursor?.pages))) { throw new Error('Invalid cursor') }
    rows.value = data.rows
    nextCursor.value = data.nextCursor
    cursors.value = cursors.value.slice(0, index + 1)
    if (data.nextCursor) { cursors.value.push(data.nextCursor) }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { busy.value = false }
  }
}

watch([() => props.archiveId, canRead], () => { reset(); void load() }, { immediate: true, flush: 'sync' })
watch(() => useUserStore().token, reset, { flush: 'sync' })
onBeforeUnmount(reset)
</script>

<template>
  <MrrSectionCard v-if="canRead" title="影像核对" description="按页码和影像 ID 分页读取当前有效影像，每页最多 50 条；实时列表并非固定快照。">
    <div class="scan-inspector">
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
        <AppError v-if="failed" message="影像列表读取失败，请重试" @retry="load(page)" />
        <el-skeleton v-else-if="busy" :rows="3" animated />
        <div v-else class="scan-list" role="group" aria-label="有效影像列表">
          <button v-for="row in rows" :key="Number(row.id)" type="button" class="scan-row" :aria-pressed="selectedId === Number(row.id)" @click="selectedId = Number(row.id)">
            <span>第 {{ row.pages ?? '—' }} 页</span>
            <small>影像 #{{ row.id }}</small>
          </button>
          <AppEmpty v-if="!rows.length" size="compact" description="暂无可核对的有效影像" />
        </div>
      </div>
      <div class="scan-main">
        <template v-if="selected && selectedId !== null">
          <div class="scan-toolbar">
            <strong>影像 #{{ selectedId }} · 第 {{ selected.pages ?? '—' }} 页</strong>
            <div class="scan-actions">
              <el-button size="small" @click="clear">
                关闭原图
              </el-button>
            </div>
          </div>
          <div class="scan-review">
            <MrrScanContentDialog :scan-id="selectedId" inline @close="clear" />
            <OcrPanel v-if="canOcr" :scan-id="selectedId" :expected-archive-id="archiveId" :unclassified="selected.btype != null && Number(selected.btype) === 0" inline />
          </div>
          <p class="scan-note">
            类型：{{ selected.btype ?? '未知' }} · 迁移：{{ selected.migrationStatus ?? '未知' }}
          </p>
        </template>
        <AppEmpty v-else size="compact" description="从左侧选择影像核对原图" />
      </div>
    </div>
  </MrrSectionCard>
</template>

<style scoped>
.scan-inspector {
  display: grid;
  grid-template-columns: minmax(120px, 190px) minmax(0, 1fr);
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
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  gap: var(--mrr-space-3);
  align-items: start;
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
.scan-note {
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

.scan-note {
  margin-top: var(--mrr-space-2);
  font-size: 12px;
}

@media (max-width: 640px) {
  .scan-inspector {
    grid-template-columns: minmax(0, 1fr);
  }

  .scan-list {
    max-height: 180px;
  }
}
</style>

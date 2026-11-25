<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppEmpty from '@/components/AppEmpty/index.vue'
import MrrScanContentDialog from '@/components/MrrScanContentDialog/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { hasPermission } from '@/utils/session'
import OcrPanel from '@/views/statistics/archive/components/OcrPanel.vue'

const props = defineProps<{ archiveId: number, samples: Record<string, unknown>[] }>()
const selectedId = ref<number | null>(null)
const activeSamples = computed(() => props.samples.filter((row) => {
  const id = Number(row.id)
  const flag = Number(row.uploadFlag)
  return Number.isSafeInteger(id) && id > 0 && Number(row.archiveId) === props.archiveId
    && Number.isFinite(flag) && flag !== 0
}))
const selected = computed(() => activeSamples.value.find(row => Number(row.id) === selectedId.value))
const canRead = computed(() => hasPermission('record:read'))
const canOcr = computed(() => canRead.value && hasPermission('ocr:read'))

function clear() {
  selectedId.value = null
}

watch(() => props.archiveId, clear, { flush: 'sync' })
watch(() => useUserStore().token, clear, { flush: 'sync' })
watch(activeSamples, () => {
  if (!selected.value) { clear() }
}, { flush: 'sync' })
watch(canRead, (allowed) => {
  if (!allowed) { clear() }
}, { flush: 'sync' })
</script>

<template>
  <MrrSectionCard v-if="canRead" title="影像核对" description="最多展示 50 条跨表扫描样本；仅按需读取已选有效影像。全量浏览请使用影像档案袋。">
    <div class="scan-inspector">
      <div class="scan-list" role="group" aria-label="有效影像样本">
        <button v-for="row in activeSamples" :key="Number(row.id)" type="button" class="scan-row" :aria-pressed="selectedId === Number(row.id)" @click="selectedId = Number(row.id)">
          <span>第 {{ row.pages ?? '—' }} 页</span>
          <small>影像 #{{ row.id }}</small>
        </button>
        <AppEmpty v-if="!activeSamples.length" size="compact" description="暂无可核对的有效扫描样本" />
      </div>
      <div class="scan-main">
        <template v-if="selected && selectedId !== null">
          <div class="scan-toolbar">
            <strong>影像 #{{ selectedId }} · 第 {{ selected.pages ?? '—' }} 页</strong>
            <div class="scan-actions">
              <OcrPanel v-if="canOcr" :key="selectedId" :scan-id="selectedId" :expected-archive-id="archiveId" :unclassified="selected.btype != null && Number(selected.btype) === 0" />
              <el-button size="small" @click="clear">
                关闭原图
              </el-button>
            </div>
          </div>
          <MrrScanContentDialog :scan-id="selectedId" inline @close="clear" />
          <p class="scan-note">
            类型：{{ selected.btype ?? '未知' }} · 迁移：{{ selected.migrationStatus ?? '未知' }}
          </p>
        </template>
        <AppEmpty v-else size="compact" description="从左侧选择一条扫描样本核对原图" />
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

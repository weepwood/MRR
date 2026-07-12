<script setup lang="ts">
import type { RouteArchiveMeta, ViewMode } from '../types'
import { Grid, List, Search } from '@element-plus/icons-vue'
import { formatDate, normalizeText } from '../constants'

defineOptions({ name: 'ArchiveSearchBar' })

const props = defineProps<{
  routeMeta: RouteArchiveMeta
  hasImages: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  search: []
}>()
const searchBah = defineModel<string>('searchBah', { default: '' })
const searchSjh = defineModel<string>('searchSjh', { default: '' })
const viewMode = defineModel<ViewMode>('viewMode', { default: 'thumb' })

const metaItems = computed(() => [
  { label: '病案号', value: normalizeText(props.routeMeta.bah) },
  { label: '设备', value: normalizeText(props.routeMeta.cid) },
  { label: '类型', value: normalizeText(props.routeMeta.type) },
  { label: '日期', value: formatDate(props.routeMeta.date) },
  { label: '人员', value: normalizeText(props.routeMeta.openerNo) },
  { label: '上架号', value: normalizeText(props.routeMeta.sjh) },
])
</script>

<template>
  <section class="search-card">
    <div class="search-bar">
      <div class="search-fields">
        <el-input v-model="searchBah" clearable placeholder="病案号" @keyup.enter="emit('search')" />
        <el-input v-model="searchSjh" clearable placeholder="上架号" @keyup.enter="emit('search')" />
        <el-button type="primary" :icon="Search" :loading="loading" @click="emit('search')">
          查询
        </el-button>
      </div>
      <el-segmented
        v-model="viewMode"
        :options="[
          { label: '缩略图', value: 'thumb', icon: Grid },
          { label: '列表', value: 'list', icon: List },
        ]"
      />
    </div>
    <div v-if="hasImages" class="route-meta">
      <span v-for="item in metaItems" :key="item.label" class="meta-item">
        <small>{{ item.label }}</small>
        <span>{{ item.value }}</span>
      </span>
    </div>
  </section>
</template>

<style scoped>
.search-card {
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.search-bar {
  display: grid;
  gap: 10px;
}

.search-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 8px;
}

.search-fields .el-input {
  min-width: 0;
}

.search-bar :deep(.el-segmented) {
  justify-self: start;
}

.route-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 12px;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--divider);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-item small {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.meta-item span {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width <= 720px) {
  .search-fields {
    grid-template-columns: 1fr;
  }

  .route-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

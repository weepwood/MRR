<script setup lang="ts">
import type { TypeStatItem } from '../types'

interface DirectoryNode {
  id: number | 'all'
  label: string
  count: number
  disabled?: boolean
  children?: DirectoryNode[]
}

defineOptions({ name: 'TypeFilterBar' })

const props = defineProps<{
  typeStats: TypeStatItem[]
  totalCount: number
  selectedCount: number
  filteredCount: number
  allVisibleSelected: boolean
}>()

const emit = defineEmits<{
  selectType: [value: number | 'all']
  selectAll: []
}>()

const selectedType = defineModel<number | 'all'>('selectedType', { default: 'all' })
const displayMode = ref<'buttons' | 'tree'>('buttons')

const directoryTree = computed<DirectoryNode[]>(() => [
  {
    id: 'all',
    label: '全部影像',
    count: props.totalCount,
    children: props.typeStats.map(item => ({
      id: item.value,
      label: item.label,
      count: item.count,
      disabled: item.count === 0,
    })),
  },
])

function onSelect(value: number | 'all') {
  selectedType.value = value
  emit('selectType', value)
}

function onNodeClick(data: DirectoryNode) {
  if (!data.disabled) {
    onSelect(data.id)
  }
}
</script>

<template>
  <div class="type-bar">
    <div class="type-bar-actions">
      <span class="select-count">已选 {{ props.selectedCount }}/{{ props.filteredCount }}</span>
      <div class="type-actions">
        <el-button size="small" link @click="emit('selectAll')">
          {{ props.allVisibleSelected ? '取消全选' : '全选' }}
        </el-button>
        <el-segmented
          v-model="displayMode"
          size="small"
          aria-label="分类展示方式"
          :options="[
            { label: '按钮', value: 'buttons' },
            { label: '目录', value: 'tree' },
          ]"
        />
      </div>
    </div>
    <div v-if="displayMode === 'buttons'" class="type-tabs">
      <button type="button" class="type-tab" :class="{ active: selectedType === 'all' }" :aria-pressed="selectedType === 'all'" @click="onSelect('all')">
        全部
        <span class="type-count">{{ props.totalCount }}</span>
      </button>
      <button
        v-for="item in props.typeStats"
        :key="item.value"
        type="button"
        class="type-tab"
        :class="{ active: selectedType === item.value, disabled: item.count === 0 }"
        :aria-pressed="selectedType === item.value"
        :disabled="item.count === 0"
        @click="onSelect(item.value)"
      >
        {{ item.label }}
        <span v-if="item.count" class="type-count">{{ item.count }}</span>
      </button>
    </div>
    <el-tree
      v-else
      class="type-tree"
      :data="directoryTree"
      :props="{ children: 'children', label: 'label', disabled: 'disabled' }"
      node-key="id"
      :current-node-key="selectedType"
      :default-expanded-keys="['all']"
      :expand-on-click-node="false"
      highlight-current
      @node-click="onNodeClick"
    >
      <template #default="{ data }">
        <span class="tree-node">
          <span class="tree-label">{{ data.label }}</span>
          <span class="tree-count">{{ data.count }}</span>
        </span>
      </template>
    </el-tree>
  </div>
</template>

<style scoped>
.type-bar {
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.type-bar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.select-count {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.type-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.type-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
}

.type-tab {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 4px 10px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}

.type-tab.active {
  font-weight: 600;
  color: hsl(var(--primary));
  background: hsl(var(--primary) / 10%);
  border-color: hsl(var(--primary) / 30%);
}

.type-tab:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 2px;
}

.type-tab:hover:not(.disabled, .active) {
  background: var(--surface-alt);
}

.type-tab.disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.type-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  background: var(--surface-alt);
  border-radius: 9999px;
}

.type-tree {
  max-height: 300px;
  overflow-y: auto;
  background: transparent;
}

.type-tree :deep(.el-tree-node__content) {
  height: 32px;
  margin-bottom: 2px;
  border-radius: 6px;
}

.tree-node {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.tree-label {
  overflow: hidden;
  font-size: 13px;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-count {
  margin-left: auto;
  padding-right: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>

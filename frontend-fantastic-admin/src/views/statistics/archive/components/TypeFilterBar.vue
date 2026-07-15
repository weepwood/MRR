<script setup lang="ts">
import type { ArchiveTypeDisplayMode } from '../composables/useArchiveLocalPreferences'
import type { TypeStatItem } from '../types'

interface DirectoryNode {
  id: number | 'all'
  label: string
  count: number
  disabled?: boolean
}

defineOptions({ name: 'TypeFilterBar' })

const props = defineProps<{
  typeStats: TypeStatItem[]
  totalCount: number
  allSelected: boolean
  allIndeterminate: boolean
  isTypeSelected: (type: number) => boolean
  isTypeIndeterminate: (type: number) => boolean
}>()

const emit = defineEmits<{
  selectType: [value: number | 'all']
  toggleAllSelection: []
  toggleTypeSelection: [type: number]
}>()

const selectedType = defineModel<number | 'all'>('selectedType', { default: 'all' })
const displayMode = defineModel<ArchiveTypeDisplayMode>('displayMode', { default: 'buttons' })

const directoryTree = computed<DirectoryNode[]>(() => [
  {
    id: 'all',
    label: '全部影像',
    count: props.totalCount,
  },
  ...props.typeStats.map(item => ({
    id: item.value,
    label: item.label,
    count: item.count,
    disabled: item.count === 0,
  })),
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

function onSelection(value: number | 'all') {
  if (value === 'all') {
    emit('toggleAllSelection')
  }
  else {
    emit('toggleTypeSelection', value)
  }
}
</script>

<template>
  <div class="type-bar">
    <div v-if="displayMode === 'buttons'" class="type-tabs">
      <div class="type-tab-entry">
        <el-checkbox
          :model-value="props.allSelected"
          :indeterminate="props.allIndeterminate"
          :disabled="props.totalCount === 0"
          aria-label="选择全部病案"
          @click.stop
          @change="onSelection('all')"
        />
        <button type="button" class="type-tab" :class="{ active: selectedType === 'all' }" :aria-pressed="selectedType === 'all'" @click="onSelect('all')">
          全部
          <span class="type-count">{{ props.totalCount }}</span>
        </button>
      </div>
      <div
        v-for="item in props.typeStats"
        :key="item.value"
        class="type-tab-entry"
      >
        <el-checkbox
          :model-value="props.isTypeSelected(item.value)"
          :indeterminate="props.isTypeIndeterminate(item.value)"
          :disabled="item.count === 0"
          :aria-label="`选择${item.label}病案`"
          @click.stop
          @change="onSelection(item.value)"
        />
        <button
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
    </div>
    <el-tree
      v-else
      class="type-tree"
      :data="directoryTree"
      :props="{ children: 'children', label: 'label', disabled: 'disabled' }"
      node-key="id"
      :current-node-key="selectedType"
      :expand-on-click-node="false"
      highlight-current
      @node-click="onNodeClick"
    >
      <template #default="{ data }">
        <span class="tree-node">
          <el-checkbox
            :model-value="data.id === 'all' ? props.allSelected : props.isTypeSelected(data.id)"
            :indeterminate="data.id === 'all' ? props.allIndeterminate : props.isTypeIndeterminate(data.id)"
            :disabled="data.id === 'all' ? props.totalCount === 0 : data.disabled"
            :aria-label="`选择${data.label}病案`"
            @click.stop
            @change="onSelection(data.id)"
          />
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

.type-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
}

.type-tab {
  display: inline-flex;
  flex: 1;
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

.type-tab-entry {
  display: flex;
  gap: 4px;
  align-items: center;
  min-width: 0;
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

.tree-node :deep(.el-checkbox) {
  flex: none;
}

.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.tree-count {
  padding-right: 8px;
  margin-left: auto;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>

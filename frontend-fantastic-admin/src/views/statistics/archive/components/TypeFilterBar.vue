<script setup lang="ts">
import type { TypeStatItem } from '../types'

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

function onSelect(value: number | 'all') {
  selectedType.value = value
  emit('selectType', value)
}
</script>

<template>
  <div class="type-bar">
    <div class="type-tabs">
      <button
        class="type-tab"
        :class="{ active: selectedType === 'all' }"
        @click="onSelect('all')"
      >
        全部
        <span class="type-count">{{ props.totalCount }}</span>
      </button>
      <button
        v-for="item in props.typeStats"
        :key="item.value"
        class="type-tab"
        :class="{ active: selectedType === item.value, disabled: item.count === 0 }"
        :disabled="item.count === 0"
        @click="onSelect(item.value)"
      >
        {{ item.label }}
        <span v-if="item.count" class="type-count" :class="{ active: selectedType === item.value }">{{ item.count }}</span>
      </button>
    </div>
    <div class="type-bar-actions">
      <span class="select-count">已选 {{ props.selectedCount }}/{{ props.filteredCount }}</span>
      <el-button size="small" link @click="emit('selectAll')">
        {{ props.allVisibleSelected ? '取消全选' : '全选' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.type-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 10px;
}

.type-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.type-bar-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.select-count {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.type-tab {
  display: inline-flex;
  gap: 6px;
  align-items: center;
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

.type-count.active {
  color: hsl(var(--primary));
  background: hsl(var(--primary) / 15%);
}
</style>

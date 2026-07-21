<script setup lang="ts">
import type { ArchiveTypeDisplayMode } from '../composables/useArchiveLocalPreferences'
import type { TypeStatItem } from '../types'

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
const displayMode = defineModel<ArchiveTypeDisplayMode>('displayMode', { default: 'double-column' })

function onSelect(value: number | 'all') {
  selectedType.value = value
  emit('selectType', value)
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
    <div class="type-tabs" :class="{ 'type-tabs--single-column': displayMode === 'single-column' }">
      <div class="type-tab-entry">
        <button type="button" class="type-tab" :class="{ active: selectedType === 'all' }" :aria-pressed="selectedType === 'all'" @click="onSelect('all')">
          <el-checkbox
            :model-value="props.allSelected"
            :indeterminate="props.allIndeterminate"
            :disabled="props.totalCount === 0"
            aria-label="选择全部病案"
            @click.stop
            @change="onSelection('all')"
          />
          <span class="type-tab-label">全部</span>
          <span class="type-count">{{ props.totalCount }}</span>
        </button>
      </div>
      <div
        v-for="item in props.typeStats"
        :key="item.value"
        class="type-tab-entry"
      >
        <button
          type="button"
          class="type-tab"
          :class="{ active: selectedType === item.value, disabled: item.count === 0 }"
          :aria-pressed="selectedType === item.value"
          :disabled="item.count === 0"
          @click="onSelect(item.value)"
        >
          <el-checkbox
            :model-value="props.isTypeSelected(item.value)"
            :indeterminate="props.isTypeIndeterminate(item.value)"
            :disabled="item.count === 0"
            :aria-label="`选择${item.label}病案`"
            @click.stop
            @change="onSelection(item.value)"
          />
          <span class="type-tab-label">{{ item.label }}</span>
          <span v-if="item.count" class="type-count">{{ item.count }}</span>
        </button>
      </div>
    </div>
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
  gap: 3px;
}

.type-tabs--single-column {
  grid-template-columns: 1fr;
}

.type-tab {
  display: inline-flex;
  flex: 1;
  gap: 4px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  padding: 4px 8px;
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  transition: color 0.15s, background 0.15s, border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.type-tab-entry {
  min-width: 0;
}

.type-tab-label {
  margin-right: auto;
}

.type-tab :deep(.el-checkbox__inner) {
  border-radius: var(--mrr-control-radius);
}

.type-tab.active {
  font-weight: 600;
  color: var(--color-primary);
  background: var(--mrr-navigation-active);
  border-color: var(--mrr-navigation-active-border);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-primary) 14%, transparent);
  animation: type-tab-selected 0.2s ease-out;
}

.type-tab:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.type-tab:hover:not(.disabled, .active) {
  background: var(--mrr-navigation-hover);
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

@keyframes type-tab-selected {
  0% {
    transform: scale(0.96);
  }

  70% {
    transform: scale(1.02);
  }

  100% {
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .type-tab {
    transition: none;
  }

  .type-tab.active {
    animation: none;
  }
}

</style>

<script setup lang="ts">
import type { ArchiveTypeDisplayMode } from '../composables/useArchiveLocalPreferences'
import type { TypeStatItem } from '../types'
import { computed, ref, watch } from 'vue'

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

/*
 * 0 计数类型默认收起：正式类型共 15 个，单个病案通常只命中其中 3–5 个，
 * 全量平铺会把高频页变成「灰项墙」，还会让操作员误读为「没权限还是坏了」。
 * 需要核对类型全集时（如分类整理），用摘要行右侧的开关展开全部。
 */
const showEmptyTypes = ref(false)
/*
 * 展开状态不跨病案记忆：调用方不随查询重建本组件（archive.vue 的 v-if 只看
 * images.length，切病案时不变），而 typeStats 由 buildTypeStats 每次查询整体
 * 重建、引用必然变化——监听它即可在切换病案 / 刷新后回到收敛默认，
 * 否则「展开 → 直接搜索下一病案」会让灰项墙在新病案上重现。
 */
watch(() => props.typeStats, () => {
  showEmptyTypes.value = false
})
const availableTypeCount = computed(() => props.typeStats.filter(item => item.count > 0).length)
const hasEmptyTypes = computed(() => availableTypeCount.value < props.typeStats.length)
const visibleTypeStats = computed(() => {
  if (showEmptyTypes.value) {
    return props.typeStats
  }
  // 0 计数类型默认收起；当前选中的类型即使计数归 0 也保持可见，不丢操作目标。
  return props.typeStats.filter(item => item.count > 0 || item.value === selectedType.value)
})

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
    <div v-if="hasEmptyTypes" class="type-bar-head">
      <span class="type-bar-summary">{{ availableTypeCount }}/{{ props.typeStats.length }} 类有影像</span>
      <button type="button" class="type-bar-toggle" :aria-pressed="showEmptyTypes" @click="showEmptyTypes = !showEmptyTypes">
        {{ showEmptyTypes ? '收起无影像类型' : '显示无影像类型' }}
      </button>
    </div>
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
        v-for="item in visibleTypeStats"
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
  padding: var(--mrr-space-3);
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-card);
}

.type-bar-head {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
  margin-bottom: var(--mrr-space-2);
}

.type-bar-summary {
  font-size: 12px;
  color: var(--text-tertiary);
}

.type-bar-toggle {
  padding: 2px 8px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-sm);
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.type-bar-toggle[aria-pressed="true"] {
  color: var(--color-primary);
  background: var(--mrr-navigation-active);
  border-color: var(--mrr-navigation-active-border);
}

.type-bar-toggle:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.type-bar-toggle:hover:not([aria-pressed="true"]) {
  background: var(--mrr-navigation-hover);
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
  height: var(--mrr-control-height);
  padding: 0 8px;
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-sm);
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out);
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

.type-tab :deep(.el-checkbox) {
  height: auto;
}

.type-tab.active {
  font-weight: 600;
  color: var(--color-primary);
  background: var(--mrr-navigation-active);
  border-color: var(--mrr-navigation-active-border);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-primary) 14%, transparent);

  /*
   * 选中只靠颜色、描边与字重的过渡表达，不再播放 0.96→1.02→1 的弹跳：
   * DESIGN.md 约定「状态确认动效不使用超过最终尺寸的弹跳」，且 keyframes 会在
   * 每次重新挂载（切换病案、刷新分类计数）时对当前选中项重播一次，读起来像又被点了一下。
   */
}

.type-tab:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.type-tab:hover:not(.disabled):not(.active) {
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
  border-radius: var(--mrr-radius-pill);
}
</style>

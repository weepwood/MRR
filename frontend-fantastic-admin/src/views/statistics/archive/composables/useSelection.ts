import type { ComputedRef } from 'vue'
import { computed, ref, watch } from 'vue'

export interface Selectable {
  id?: number | string
  filename?: string
}

export function useSelection<T extends Selectable>(validItems: ComputedRef<T[]>) {
  const selectedIds = ref<Set<string>>(new Set())
  const selectedCount = computed(() => selectedIds.value.size)
  const allVisibleSelected = computed(() =>
    validItems.value.length > 0 && validItems.value.every(isSelected),
  )

  function keyOf(item: T): string {
    return String(item.id || item.filename || '')
  }

  function isSelected(item: T): boolean {
    return selectedIds.value.has(keyOf(item))
  }

  function toggleSelect(item: T): void {
    const key = keyOf(item)
    const next = new Set(selectedIds.value)
    if (next.has(key)) {
      next.delete(key)
    }
    else {
      next.add(key)
    }
    selectedIds.value = next
  }

  function toggleItems(items: T[]): void {
    const keys = items.map(keyOf).filter(Boolean)
    if (!keys.length) {
      return
    }

    const next = new Set(selectedIds.value)
    if (keys.every(key => next.has(key))) {
      keys.forEach(key => next.delete(key))
    }
    else {
      keys.forEach(key => next.add(key))
    }
    selectedIds.value = next
  }

  function selectAllVisible(): void {
    if (selectedIds.value.size === validItems.value.length && validItems.value.length > 0) {
      selectedIds.value = new Set()
    }
    else {
      selectedIds.value = new Set(validItems.value.map(keyOf))
    }
  }

  function clear(): void {
    selectedIds.value = new Set()
  }

  const selectedItems = computed(() => validItems.value.filter(isSelected))

  watch(validItems, () => {
    const validKeys = new Set(validItems.value.map(keyOf))
    const filtered = new Set([...selectedIds.value].filter(k => validKeys.has(k)))
    if (filtered.size !== selectedIds.value.size) {
      selectedIds.value = filtered
    }
  })

  return {
    selectedIds,
    selectedCount,
    allVisibleSelected,
    selectedItems,
    keyOf,
    isSelected,
    toggleSelect,
    toggleItems,
    selectAllVisible,
    clear,
  }
}

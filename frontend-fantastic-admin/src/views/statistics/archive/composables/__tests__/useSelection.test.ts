import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { useSelection } from '../useSelection'

describe('useSelection', () => {
  it('toggles a group of images without clearing selections from other groups', () => {
    const images = ref([{ id: 1 }, { id: 2 }, { id: 3 }])
    const selection = useSelection(computed(() => images.value))

    selection.toggleItems(images.value.slice(0, 2))
    selection.toggleSelect(images.value[2])
    selection.toggleItems(images.value.slice(0, 2))

    expect(selection.selectedIds.value).toEqual(new Set(['3']))
  })
})

import { useMediaQuery } from '@vueuse/core'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import { useTableActionLayout } from '../useTableActionLayout'

vi.mock('@vueuse/core', () => ({ useMediaQuery: vi.fn() }))

const width = ref(1440)

beforeEach(() => {
  width.value = 1440
  vi.mocked(useMediaQuery).mockImplementation(query => computed(() => {
    if (query === '(max-width: 1279px)') { return width.value <= 1279 }
    if (query === '(max-width: 899px)') { return width.value <= 899 }
    throw new Error(`未覆盖的断点: ${query}`)
  }))
})

describe('表格行操作响应式布局', () => {
  it('在同一实例上随断点双向更新操作数量与列宽', () => {
    const { maxInlineActions, actionColumnWidth } = useTableActionLayout(3, 1)
    for (const [viewport, inline, column] of [[1280, 3, 136], [1279, 1, 96], [900, 1, 96], [899, 0, 56], [375, 0, 56], [1440, 3, 136]]) {
      width.value = viewport
      expect([maxInlineActions.value, actionColumnWidth.value]).toEqual([inline, column])
    }
  })

  it('单操作不预留多余菜单宽度，多操作为溢出按钮预留宽度', () => {
    const single = useTableActionLayout(1, 1)
    const many = useTableActionLayout(4, 2)
    expect(single.actionColumnWidth.value).toBe(56)
    expect(many.actionColumnWidth.value).toBe(176)
    width.value = 768
    expect(single.actionColumnWidth.value).toBe(56)
    expect(many.actionColumnWidth.value).toBe(56)
  })
})

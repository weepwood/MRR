import type { Ref } from 'vue'
import type { ViewMode } from '../types'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const GAP = 6
const MIN_ITEM_WIDTH = 80
const MAX_ITEM_WIDTH = 130
const SCROLL_LOAD_THRESHOLD = 80

export function useThumbLayout(
  thumbsContainer: Ref<HTMLElement | null>,
  viewMode: Ref<ViewMode>,
) {
  const thumbColumns = ref(2)
  const thumbItemWidth = ref(96)
  const pageSize = ref(20)
  const visibleCount = ref(20)

  let resizeObserver: ResizeObserver | null = null

  function calc(): void {
    const container = thumbsContainer.value
    if (!container) {
      thumbColumns.value = viewMode.value === 'thumb' ? 2 : 1
      return
    }
    const containerWidth = container.clientWidth
    if (viewMode.value === 'list') {
      thumbColumns.value = 1
      pageSize.value = 40
      return
    }
    const idealCols = Math.max(1, Math.floor((containerWidth + GAP) / (MIN_ITEM_WIDTH + GAP)))
    const actualItemWidth = Math.min(
      MAX_ITEM_WIDTH,
      Math.max(MIN_ITEM_WIDTH, (containerWidth - (idealCols - 1) * GAP) / idealCols),
    )
    thumbItemWidth.value = actualItemWidth
    thumbColumns.value = idealCols

    const viewportHeight = window.innerHeight
    const stripTop = container.getBoundingClientRect().top
    const availableHeight = viewportHeight - stripTop - 24
    const itemHeight = actualItemWidth * 4 / 3 + 36
    const rows = Math.max(1, Math.floor(availableHeight / itemHeight))
    pageSize.value = thumbColumns.value * rows
  }

  function resetVisible(): void {
    visibleCount.value = pageSize.value
  }

  function loadMore(): void {
    visibleCount.value += pageSize.value
  }

  function onScroll(): void {
    const el = thumbsContainer.value
    if (!el) {
      return
    }
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - SCROLL_LOAD_THRESHOLD) {
      loadMore()
    }
  }

  onMounted(() => {
    resizeObserver = new ResizeObserver(() => calc())
    if (thumbsContainer.value) {
      resizeObserver.observe(thumbsContainer.value)
      thumbsContainer.value.addEventListener('scroll', onScroll, { passive: true })
    }
    calc()
    resetVisible()
  })

  onUnmounted(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
    if (thumbsContainer.value) {
      thumbsContainer.value.removeEventListener('scroll', onScroll)
    }
  })

  watch(viewMode, () => {
    nextTick(() => {
      calc()
      resetVisible()
    })
  })

  return {
    thumbColumns,
    thumbItemWidth,
    pageSize,
    visibleCount,
    resetVisible,
    loadMore,
    onScroll,
  }
}

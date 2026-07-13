import type { Ref } from 'vue'
import type { ViewMode } from '../types'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const GAP = 6
const CONTAINER_HORIZONTAL_PADDING = 16
const MIN_ITEM_WIDTH = 156
const MAX_ITEM_WIDTH = 220
const SCROLL_LOAD_THRESHOLD = 80

export function useThumbLayout(
  thumbsContainer: Ref<HTMLElement | null>,
  viewMode: Ref<ViewMode>,
) {
  const thumbColumns = ref(1)
  const thumbItemWidth = ref(184)
  const pageSize = ref(20)
  const visibleCount = ref(20)

  let resizeObserver: ResizeObserver | null = null
  let usesWindowResize = false

  function updatePageSize(nextPageSize: number): void {
    pageSize.value = Math.max(1, nextPageSize)
    // When the panel grows, keep enough items rendered to fill the new viewport.
    // Never shrink visibleCount here, otherwise a resize could hide items the user
    // has already loaded.
    visibleCount.value = Math.max(visibleCount.value, pageSize.value)
  }

  function calc(): void {
    const container = thumbsContainer.value
    if (!container) {
      thumbColumns.value = 1
      return
    }
    const containerWidth = Math.max(1, container.clientWidth - CONTAINER_HORIZONTAL_PADDING)
    if (viewMode.value === 'list') {
      thumbColumns.value = 1
      updatePageSize(40)
      return
    }
    const idealCols = Math.max(1, Math.floor((containerWidth + GAP) / (MIN_ITEM_WIDTH + GAP)))
    const actualItemWidth = Math.min(
      MAX_ITEM_WIDTH,
      Math.max(MIN_ITEM_WIDTH, (containerWidth - (idealCols - 1) * GAP) / idealCols),
    )
    thumbItemWidth.value = actualItemWidth
    thumbColumns.value = idealCols

    const itemHeight = actualItemWidth * 4 / 3 + 42
    const visibleRows = Math.ceil(container.clientHeight / itemHeight)
    updatePageSize(thumbColumns.value * Math.max(2, visibleRows + 1))
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
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => calc())
      if (thumbsContainer.value) {
        resizeObserver.observe(thumbsContainer.value)
      }
    }
    else {
      usesWindowResize = true
      window.addEventListener('resize', calc)
    }
    if (thumbsContainer.value) {
      thumbsContainer.value.addEventListener('scroll', onScroll, { passive: true })
    }
    calc()
    resetVisible()
  })

  onUnmounted(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
    if (usesWindowResize) {
      window.removeEventListener('resize', calc)
    }
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

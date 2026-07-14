import type { Ref } from 'vue'
import type { ViewMode } from '../types'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const GAP = 6
const CONTAINER_HORIZONTAL_PADDING = 16
const SCROLL_LOAD_THRESHOLD = 80

export function useThumbLayout(
  thumbsContainer: Ref<HTMLElement | null>,
  viewMode: Ref<ViewMode>,
  preferredWidth: Ref<number>,
  preloadCount: Ref<number>,
) {
  const thumbColumns = ref(1)
  const thumbItemWidth = ref(preferredWidth.value)
  const pageSize = ref(preloadCount.value)
  const visibleCount = ref(preloadCount.value)

  let resizeObserver: ResizeObserver | null = null
  let usesWindowResize = false

  function updatePageSize(nextPageSize: number): void {
    pageSize.value = Math.max(1, nextPageSize)
    visibleCount.value = Math.max(visibleCount.value, pageSize.value, preloadCount.value)
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
      thumbItemWidth.value = containerWidth
      updatePageSize(Math.max(40, preloadCount.value))
      return
    }

    const targetWidth = Math.min(320, Math.max(160, preferredWidth.value))
    const idealCols = Math.max(1, Math.floor((containerWidth + GAP) / (targetWidth + GAP)))
    const actualItemWidth = Math.max(140, (containerWidth - (idealCols - 1) * GAP) / idealCols)
    thumbItemWidth.value = actualItemWidth
    thumbColumns.value = idealCols

    const estimatedItemHeight = actualItemWidth + 52
    const visibleRows = Math.ceil(container.clientHeight / estimatedItemHeight)
    updatePageSize(Math.max(preloadCount.value, thumbColumns.value * Math.max(2, visibleRows + 1)))
  }

  function resetVisible(): void {
    visibleCount.value = Math.max(pageSize.value, preloadCount.value)
  }

  function loadMore(): void {
    visibleCount.value += Math.max(pageSize.value, preloadCount.value)
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

  watch([viewMode, preferredWidth, preloadCount], () => {
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

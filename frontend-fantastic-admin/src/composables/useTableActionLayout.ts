import { useMediaQuery } from '@vueuse/core'
import { computed } from 'vue'

export function useTableActionLayout(maxActionCount: number, desktopInline = 2) {
  const mediumScreen = useMediaQuery('(max-width: 1279px)')
  const narrowScreen = useMediaQuery('(max-width: 899px)')

  const maxInlineActions = computed(() => {
    if (narrowScreen.value) {
      return 0
    }
    if (mediumScreen.value) {
      return Math.min(desktopInline, 1)
    }
    return desktopInline
  })

  const actionColumnWidth = computed(() => {
    const visibleInline = Math.min(Math.max(0, maxActionCount), maxInlineActions.value)
    const hasOverflow = maxActionCount > visibleInline
    const visibleButtons = visibleInline + (hasOverflow ? 1 : 0)
    return Math.max(56, 16 + visibleButtons * 40)
  })

  return {
    maxInlineActions,
    actionColumnWidth,
  }
}

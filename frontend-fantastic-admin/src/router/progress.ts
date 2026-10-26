import type { RouteLocationNormalized, Router } from 'vue-router'
import { useNProgress } from '@vueuse/integrations/useNProgress'
import { useSettingsStore } from '@/store/modules/settings'

export default function setupProgress(router: Router) {
  const { isLoading } = useNProgress(null, { showSpinner: false })
  let startTimer: ReturnType<typeof setTimeout> | undefined
  let activeRoute: RouteLocationNormalized | undefined

  function clearStartTimer() {
    if (startTimer !== undefined) {
      clearTimeout(startTimer)
      startTimer = undefined
    }
  }

  function finishProgress(to: RouteLocationNormalized) {
    // 旧导航的取消或异常不能结束较新的导航。
    if (to !== activeRoute) {
      return
    }
    clearStartTimer()
    activeRoute = undefined
    isLoading.value = false
  }

  router.beforeEach((to) => {
    clearStartTimer()
    activeRoute = to
    const settingsStore = useSettingsStore()
    if (!settingsStore.settings.app.enableProgress) {
      isLoading.value = false
      return
    }
    if (isLoading.value) {
      return
    }
    startTimer = setTimeout(() => {
      startTimer = undefined
      if (to === activeRoute && settingsStore.settings.app.enableProgress) {
        isLoading.value = true
      }
    }, 200)
  })

  router.afterEach(finishProgress)
  router.onError((_error, to) => finishProgress(to))
}

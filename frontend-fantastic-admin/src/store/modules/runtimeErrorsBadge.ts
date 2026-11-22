import { getSystemErrorOverview } from '@/api/modules/system-errors'
import { checkPermission } from '@/utils/permission'

/**
 * 运行错误中心菜单角标：轮询 overview 拿"近 24 小时活跃 ERROR 错误组数"。
 *
 * 只有具备 system:error:read 权限的会话才轮询；无权限或接口失败时角标静默归零，
 * 不弹任何提示——角标是辅助信号，不能成为新的噪音源。
 */
const POLL_INTERVAL_MS = 30_000

export const useRuntimeErrorsBadgeStore = defineStore(
  'runtimeErrorsBadge',
  () => {
    const recentErrorGroups = ref(0)
    const started = ref(false)
    let timer: ReturnType<typeof setInterval> | undefined
    let lastRefreshAt = 0

    async function refresh() {
      lastRefreshAt = Date.now()
      const userStore = useUserStore()
      if (!checkPermission(userStore.permissions, 'system:error:read')) { return }
      try {
        const response = await getSystemErrorOverview()
        recentErrorGroups.value = response.data?.recentErrorGroups ?? 0
      }
      catch {
        // 拉取失败保留旧值，下个周期重试；角标展示不要求强一致。
      }
    }

    function handleVisibilityChange() {
      // 回到前台时若已错过一个周期，立即补一次，角标不必等满 30 秒。
      if (!document.hidden && Date.now() - lastRefreshAt >= POLL_INTERVAL_MS) {
        void refresh()
      }
    }

    /** 30 秒轮询，页面隐藏时跳过；由布局挂载时调用一次即可。 */
    function start() {
      if (started.value) { return }
      started.value = true
      void refresh()
      timer = setInterval(() => {
        if (!document.hidden) { void refresh() }
      }, POLL_INTERVAL_MS)
      document.addEventListener('visibilitychange', handleVisibilityChange)
    }

    /**
     * 布局卸载（退出登录回到登录页）时停止轮询。
     * 原实现的计时器从不清理，退出后仍每 30 秒唤醒一次；再次登录时 start() 因幂等直接返回，
     * 角标要等下一个周期才更新。
     */
    function stop() {
      if (timer !== undefined) {
        clearInterval(timer)
        timer = undefined
      }
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      started.value = false
      recentErrorGroups.value = 0
    }

    return { recentErrorGroups, refresh, start, stop }
  },
)

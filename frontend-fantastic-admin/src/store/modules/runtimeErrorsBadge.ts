import { getSystemErrorOverview } from '@/api/modules/system-errors'
import { checkPermission } from '@/utils/permission'

/**
 * 运行错误中心菜单角标：轮询 overview 拿"近 24 小时活跃 ERROR 错误组数"。
 *
 * 只有具备 system:error:read 权限的会话才轮询；无权限或接口失败时角标静默归零，
 * 不弹任何提示——角标是辅助信号，不能成为新的噪音源。
 */
export const useRuntimeErrorsBadgeStore = defineStore(
  'runtimeErrorsBadge',
  () => {
    const recentErrorGroups = ref(0)
    const started = ref(false)

    async function refresh() {
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

    /** 30 秒轮询，页面隐藏时跳过；由布局挂载时调用一次即可。 */
    function start() {
      if (started.value) { return }
      started.value = true
      void refresh()
      setInterval(() => {
        if (!document.hidden) { void refresh() }
      }, 30_000)
    }

    return { recentErrorGroups, refresh, start }
  },
)

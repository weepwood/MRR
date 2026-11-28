import { ref } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { actionDialog } from '@/ui/components/ActionDialog'

/** Only explicit user actions are confirmed; automatic session invalidation remains immediate. */
export function useConfirmedLogout() {
  const userStore = useUserStore()
  const pending = ref(false)
  async function confirmLogout(redirect?: string) {
    if (pending.value || !userStore.isLogin) { return }
    const epoch = userStore.sessionEpoch
    pending.value = true
    try {
      try {
        await actionDialog.confirm('退出后需要重新登录，当前未保存的操作可能丢失。', '确认退出登录？', {
          confirmButtonText: '退出登录',
          confirmButtonType: 'danger',
          cancelButtonText: '取消',
          closeOnClickModal: false,
        })
      }
      catch { return }
      // A confirmation opened by an old session cannot log out its replacement.
      if (userStore.isLogin && userStore.sessionEpoch === epoch) {
        await userStore.logout(redirect)
      }
    }
    finally { pending.value = false }
  }
  return { confirmLogout, logoutPending: pending }
}

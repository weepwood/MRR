import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import { useSettingsStore } from '@/store/modules/settings'
import { readAuthStorage } from './auth-storage'

/** 首次加载失败后的最小重试间隔，避免在每个路由跳转上重复打请求。 */
const RETRY_INTERVAL_MS = 5000

/**
 * 安装全站界面配置同步。
 * <p>
 * 背景：界面外观与"其他"菜单的外部链接原先只保存在浏览器 localStorage，
 * 换台电脑或换浏览器就丢失，同一科室不同终端看到的导航也不一致。
 * 现在这两部分配置改为在服务端保存全局默认值，本函数负责在用户已登录后拉取并应用。
 * </p>
 * <p>
 * 时序上不能在应用启动时立即拉取：那时登录令牌可能还不存在。
 * 因此这里挂在路由跳转之后重试（登录成功后会立即跳转一次），
 * 成功一次即停止，失败则按 {@link RETRY_INTERVAL_MS} 限流重试。
 * </p>
 */
export function installUiSettingsSync(router: Router, pinia: Pinia): void {
  const settingsStore = useSettingsStore(pinia)
  let synced = false
  let lastAttemptAt = 0

  function attemptSync() {
    if (synced) {
      return
    }
    if (!readAuthStorage().token) {
      return
    }
    const now = Date.now()
    if (now - lastAttemptAt < RETRY_INTERVAL_MS) {
      return
    }
    lastAttemptAt = now
    void settingsStore.loadUiSettings().then((ok) => {
      synced = ok
    })
  }

  router.afterEach(() => {
    attemptSync()
  })

  attemptSync()
}

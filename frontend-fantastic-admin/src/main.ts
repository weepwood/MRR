import iconConfig from '@/iconify/index.json'
// 自定义指令
import directive from '@/utils/directive'
import { installArchiveUserIdRouting } from '@/utils/archive-userid-routing'
import { installArchiveWatermark } from '@/utils/archive-watermark-installer'
import { installClipboardFallback } from '@/utils/clipboard-fallback'
import { installMedicalRecordCodeInterceptors } from '@/utils/medical-record-code-interceptors'
import { installRequestErrorFallback } from '@/utils/request-error-notification'
import { installSettingsWorkspaceFocus } from '@/utils/settings-workspace-focus'
import { installSystemSettingsRuntime } from '@/utils/system-settings'
import { installMotionEnhancements } from '@/motion/installMotionEnhancements'

import App from './App.vue'
import router from './router'
import pinia from './store'
import uiProvider from './ui/provider'
import '@/utils/systemCopyright'

// 加载 svg 图标
import 'virtual:svg-icons-register'
// UnoCSS
import '@unocss/reset/tailwind-compat.css'
import 'virtual:uno.css'
// 全局样式
import '@/assets/styles/globals.css'
import '@/assets/styles/pages/archive-boxes.css'
import '@/assets/styles/pages/settings-navigation.css'
import '@/assets/styles/pages/statistics-detail-typography.css'
import '@/assets/styles/motion-enhancements.css'

installMedicalRecordCodeInterceptors()
installSystemSettingsRuntime()
installArchiveUserIdRouting(router)
installClipboardFallback()
// 点击完整高度的设置工作区时，将其对齐到桌面视口顶部。
installSettingsWorkspaceFocus()

const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(uiProvider)
installArchiveWatermark(router, pinia)
directive(app)
installRequestErrorFallback(app)
app.mount('#app')
installMotionEnhancements(router)

async function installOfflineIcons() {
  const { downloadAndInstall } = await import('@/iconify')
  await Promise.allSettled(
    iconConfig.collections.map(collection => downloadAndInstall(collection)),
  )
}

// 离线 Iconify 集合体积较大，仅在配置启用时才加载运行时与图标数据，
// 避免默认在线模式阻塞应用首屏启动。
if (iconConfig.isOfflineUse) {
  void installOfflineIcons()
}

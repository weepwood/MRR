import iconConfig from '@/iconify/index.json'
// 自定义指令
import directive from '@/utils/directive'

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

const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(uiProvider)
directive(app)
app.mount('#app')

// 离线 Iconify 集合体积较大，仅在配置启用时才加载运行时与图标数据，
// 避免默认在线模式阻塞应用首屏启动。
if (iconConfig.isOfflineUse) {
  void import('@/iconify').then(async ({ downloadAndInstall }) => {
    await Promise.allSettled(
      iconConfig.collections.map(collection => downloadAndInstall(collection)),
    )
  })
}

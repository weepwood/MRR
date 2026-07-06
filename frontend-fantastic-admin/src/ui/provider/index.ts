import type { App } from 'vue'
import { ElLoading } from 'element-plus'
import 'element-plus/es/components/loading/style/css'

/**
 * Element Plus 按需引入策略（配合 vite/plugins.ts 中的 ElementPlusResolver）：
 *
 * - 组件与基础样式：由 unplugin-vue-components 的 ElementPlusResolver({ importStyle: 'css' })
 *   在模板使用到 <el-xxx> 时自动按需引入，无需在此全量注册。
 * - ElMessage / ElMessageBox 等 API：由 unplugin-auto-import 的 ElementPlusResolver
 *   在代码中使用到时自动按需引入。
 * - v-loading 指令：ElementPlusResolver 不会自动注册自定义指令，需在此手动注册。
 *   项目中大量使用 v-loading（records/logs/audit-images/monitoring 等十余处），
 *   不注册会导致运行时 "[Vue warn]: Failed to resolve directive: loading" 错误。
 * - 暗色模式 CSS 变量：必须全局加载（按需引入不会自动包含），这里保留。
 *
 * 移除 `app.use(ElementPlus)` 与 `import 'element-plus/dist/index.css'` 后，
 * bundle 不再包含未使用的 Element Plus 组件，体积显著减小。
 */
function install(app: App) {
  // 手动注册 v-loading 指令（按需引入模式下 resolver 不会自动注册指令）
  app.directive('loading', ElLoading.directive)

  // 暗色模式变量需全局加载，供 [data-mode="dark"] 切换使用
  import('element-plus/theme-chalk/dark/css-vars.css')
}

export default { install }

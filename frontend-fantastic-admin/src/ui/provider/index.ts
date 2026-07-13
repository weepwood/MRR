import type { App } from 'vue'
import { ElLoading } from 'element-plus'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

/**
 * Element Plus 按需引入策略（配合 vite/plugins.ts 中的 ElementPlusResolver）：
 *
 * - 组件与基础样式：由 unplugin-vue-components 的 ElementPlusResolver({ importStyle: 'css' })
 *   在模板使用到 <el-xxx> 时自动按需引入，无需在此全量注册。
 * - ElMessage / ElMessageBox 等服务式 API：代码中既存在自动导入，也存在显式导入。
 *   显式导入不会触发 resolver 补充样式，因此在 Provider 中统一引入对应 CSS，
 *   避免消息提示无样式、定位异常或确认弹窗不可见。
 * - v-loading 指令：ElementPlusResolver 不会自动注册自定义指令，需在此手动注册。
 *   项目中大量使用 v-loading（records/logs/audit-images/monitoring 等十余处），
 *   不注册会导致运行时 "[Vue warn]: Failed to resolve directive: loading" 错误。
 * - 暗色模式变量：由 Provider 组件在真正切换到暗色模式时按需加载，
 *   避免明亮模式首屏请求不需要的样式文件。
 *
 * 移除 `app.use(ElementPlus)` 与 `import 'element-plus/dist/index.css'` 后，
 * bundle 不再包含未使用的 Element Plus 组件，体积显著减小。
 */
function install(app: App) {
  // 手动注册 v-loading 指令（按需引入模式下 resolver 不会自动注册指令）
  app.directive('loading', ElLoading.directive)
}

export default { install }

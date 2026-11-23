/**
 * 页面层级（z-index）的 JS 副本。
 *
 * 唯一数值源是 assets/styles/tokens.css 里的 `--mrr-z-*`。CSS 一律写 `var(--mrr-z-*)`
 * 或 `calc(var(--mrr-z-*) + n)`；只有必须传数字的地方才用这里：
 * Element Plus 的 `z-index` prop、`ElConfigProvider`、行内样式、命令式创建的节点。
 * `__tests__/z-index.test.ts` 保证两边逐项一致，改数值时两处一起改。
 *
 * 沉浸层（档案袋全屏看图）的顺序：
 * 影像查看器 3000 < 预览工具条 3001 < 工具条内下拉 3002 < OCR 抽屉 3100
 * < 确认弹窗 3200 < 消息提示 3300 < 水印 4000（pointer-events: none）。
 */
export const Z_INDEX = {
  /** 吸顶块、页内浮动工具条、存储浏览器最大化 */
  sticky: 1000,
  sidebarMask: 1009,
  sidebar: 1010,
  /** 页内阻断遮罩，必须低于弹层基准 2000 */
  blocking: 1900,
  /** 顶部导航；Element Plus / Fa 弹层也从 2000 起递增 */
  header: 2000,
  /** 路由进度条 */
  progress: 3000,
  /** 全屏影像查看器（el-image / ElImageViewer） */
  viewer: 3000,
  /** 档案袋 OCR 抽屉基座（ElConfigProvider） */
  ocrDrawer: 3100,
  /** ActionDialog 确认/输入弹窗 */
  actionDialog: 3200,
  /** ElMessage 提示条 */
  message: 3300,
  /** 全屏看图说明条、跟随鼠标的图表提示 */
  overlayTop: 4000,
  /** 档案水印 */
  watermark: 4000,
} as const

export type ZIndexLayer = keyof typeof Z_INDEX

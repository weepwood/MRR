export const categories = [
  { id: 'all', label: '全部组件', icon: 'i-lucide:layout-grid' },
  { id: 'basic', label: '基础与导航', icon: 'i-lucide:mouse-pointer-2' },
  { id: 'form', label: '表单与选择', icon: 'i-lucide:sliders-horizontal' },
  { id: 'data', label: '数据与组合', icon: 'i-lucide:table-2' },
  { id: 'feedback', label: '反馈与浮层', icon: 'i-lucide:message-square' },
  { id: 'tokens', label: '设计令牌', icon: 'i-lucide:palette' },
] as const

export const examples = [
  { id: 'buttons', category: 'basic', title: '按钮与操作', description: '比较主次、尺寸，以及禁用和加载状态。', components: ['FaButton', 'FaButtonGroup'] },
  { id: 'navigation', category: 'basic', title: '页签与提示', description: '切换内容分区，查看键盘提示和悬停说明。', components: ['FaTabs', 'FaTooltip', 'FaKbd'] },
  { id: 'page-header', category: 'basic', title: '页头尺寸', description: '概览页用默认尺寸，查询、列表与浏览页用紧凑尺寸；可切换长说明查看折行。', components: ['MrrPageHeader size="compact"'] },
  { id: 'fields', category: 'form', title: '输入与筛选', description: '字段保留可见标签；输入、选择和日期都可以直接操作。', components: ['FaInput', 'FaTextarea', 'FaSelect', 'ElDatePicker', 'ElInputNumber'] },
  { id: 'selection', category: 'form', title: '选择与开关', description: '复选框、开关和滑块使用独立的本页示例状态。', components: ['FaCheckbox', 'FaSwitch', 'FaSlider'] },
  { id: 'metrics', category: 'data', title: '指标卡片', description: '展示图标、无图标、文本值及状态色；数值均为演示。', components: ['MrrMetricCard'] },
  { id: 'table', category: 'data', title: '筛选与数据面板', description: '组合筛选、结果、状态和行操作，体验本地查询与翻页。', components: ['MrrDataTablePanel', 'MrrSectionCard', 'MrrFilterBar', 'MrrTableActions', 'MrrStatusTag', 'ElTag', 'ElPagination'] },
  { id: 'feedback', category: 'feedback', title: '加载、空态与错误', description: '主动切换状态，体验错误重试和空态恢复入口。', components: ['AppLoading', 'AppEmpty', 'AppError', 'FaProgress', 'ElAlert'] },
  { id: 'motion', category: 'feedback', title: '动效与刷新反馈', description: '进行中光圈、数值更新、表格刷新与区块入场；关闭动效后状态仍可读。', components: ['MrrStatusTag live', 'MrrMetricCard', 'MrrDataTablePanel loading / result-key', 'mrr-page-enter'] },
  { id: 'overlays', category: 'feedback', title: '对话框、抽屉与气泡', description: '检查浮层、关闭行为和键盘操作；确认只更新示例反馈。', components: ['FaModal', 'FaDrawer', 'FaPopover'] },
  { id: 'glass', category: 'feedback', title: '毛玻璃与背景模糊（演示）', description: 'backdrop-filter 毛玻璃效果仅供设计比较；生产 UI 当前统一使用不透明表面。', components: ['backdrop-filter'] },
  { id: 'tokens', category: 'tokens', title: '颜色、圆角与排版', description: '直接读取当前设计令牌；外观跟随全站设置。', components: ['tokens.css', 'MrrPageShell', 'MrrPageHeader'] },
] as const

export type ExampleId = typeof examples[number]['id']

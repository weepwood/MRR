/**
 * 指标卡语义色与数值色的公共类型。
 *
 * 页面往往通过 `v-for` 渲染指标组，`tone` 若在页面内散落为 `string`，
 * 就无法在模板类型检查阶段发现拼写错误（例如 `tone="gree"`）。
 * 统一从这里取类型，可让页面数组与组件 prop 保持同一份定义。
 */
export type MetricTone = 'blue' | 'green' | 'amber' | 'warning' | 'rose' | 'danger' | 'violet' | 'teal' | 'slate'

export type MetricValueTone = 'default' | 'success' | 'warning' | 'danger'

/**
 * 指标口径：`page` 为当前页/当前筛选结果的计数，`total` 为系统级总数。
 * 用户页等指标组常混用两种口径，页面数组必须显式标注，避免扫读时看错。
 */
export type MetricScope = 'page' | 'total'

/**
 * 指标卡语义色与数值色的公共类型。
 *
 * 页面往往通过 `v-for` 渲染指标组，`tone` 若在页面内散落为 `string`，
 * 就无法在模板类型检查阶段发现拼写错误（例如 `tone="gree"`）。
 * 统一从这里取类型，可让页面数组与组件 prop 保持同一份定义。
 */
export type MetricTone = 'blue' | 'green' | 'amber' | 'warning' | 'rose' | 'danger' | 'violet' | 'teal' | 'slate'

export type MetricValueTone = 'default' | 'success' | 'warning' | 'danger'

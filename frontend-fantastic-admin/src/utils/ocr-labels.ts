/** OCR 状态与原因码的中文展示；未知编码原样回显，不吞掉后端新增枚举。 */

/** 单页识别事实（mr_scan_ocr）状态。 */
export const ocrPageStatusLabels: Record<string, string> = {
  PENDING: '待识别',
  PROCESSING: '识别中',
  SUCCEEDED: '识别完成',
  LOW_CONFIDENCE: '文字质量较低',
  FAILED: '识别失败',
  SKIPPED: '已跳过',
  STALE: '源文件已变化',
  INTERRUPTED: '已中断',
}

/** 识别任务与历史批次共用的执行状态。 */
export const ocrJobStatusLabels: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '运行中',
  PAUSED: '已暂停',
  CANCEL_REQUESTED: '取消中',
  SUCCESS: '已完成',
  PARTIAL_SUCCESS: '部分完成',
  FAILED: '失败',
  CANCELLED: '已取消',
  INTERRUPTED: '已中断',
}

/** 任务子项状态；未匹配与低置信度页面进入人工处理清单。 */
export const ocrItemStatusLabels: Record<string, string> = {
  PENDING: '待识别',
  PROCESSING: '识别中',
  NO_MATCH: '未匹配',
  SUGGESTED: '已生成建议',
  LOW_CONFIDENCE: '文字质量较低',
  FAILED: '识别失败',
  SKIPPED: '已跳过',
  CANCELLED: '已取消',
}

export const ocrReviewStatusLabels: Record<string, string> = {
  PENDING: '待审核',
  ACCEPTED: '已确认',
  REJECTED: '已拒绝',
  OVERRIDDEN: '已人工改类',
}

export const ocrAutoStatusLabels: Record<string, string> = {
  PENDING: '待处理',
  APPLIED: '已自动归类',
  REVIEW_REQUIRED: '待人工复核',
}

/** 分类依据与批次/预览等待原因共用一张映射，两侧编码无冲突。 */
export const ocrReasonLabels: Record<string, string> = {
  NO_MATCH: '未匹配',
  CONFLICT: '规则冲突',
  EXACT_TITLE: '精确标题命中',
  REVIEW_REQUIRED: '需人工复核',
  READY: '就绪',
  OUTSIDE_WINDOW: '不在夜间窗口',
  P95_UNAVAILABLE: '缺少延迟基线',
  MEASUREMENT_UNAVAILABLE: '资源测量不可用',
  MEMORY_PRESSURE: '内存压力',
  CPU_PRESSURE: 'CPU 压力',
  IMAGE_LATENCY: '影像延迟恶化',
  WAITING_AUTO_APPLY: '等待自动归类',
  AUTO_APPLY_DISABLED: '自动归类未启用',
  QUEUE_FULL: '识别队列已满',
  USER_PAUSED: '用户暂停',
  AUTH_OR_QUEUE_BLOCKED: '授权或队列受阻',
}

export function ocrLabel(labels: Record<string, string>, code: string | null | undefined): string {
  return (code && labels[code]) || code || ''
}

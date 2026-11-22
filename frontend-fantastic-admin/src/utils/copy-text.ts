import { ElMessage } from 'element-plus'

export interface CopyTextOptions {
  /** 成功提示；传 false 时不提示（调用方自行反馈）。 */
  success?: string | false
  /** 失败提示。默认给出可执行的手动复制指引。 */
  failure?: string
}

export const COPY_FAILURE_MESSAGE = '复制失败，请选中内容后按 Ctrl+C 手动复制'

/**
 * 复制文本并给出统一反馈。
 *
 * 此前 10 余个页面各写一份 `try { navigator.clipboard.writeText } catch { ElMessage }`，
 * 失败文案有「复制失败」「请手动选择复制」「请选中后按 Ctrl+C」等五六种说法，
 * 空串也会被写进剪贴板并提示「已复制」。这里统一：
 * - 空白文本直接返回 false，不打扰用户；
 * - 内网 HTTP 页面没有原生 Clipboard API，由 main.ts 安装的 clipboard-fallback 兜底；
 *   兜底也不可用时走失败提示，不抛异常。
 */
export async function copyText(text: string | null | undefined, options: CopyTextOptions = {}): Promise<boolean> {
  const value = text ?? ''
  if (!value.trim()) {
    return false
  }
  const { success = '已复制', failure = COPY_FAILURE_MESSAGE } = options
  try {
    if (typeof navigator === 'undefined' || typeof navigator.clipboard?.writeText !== 'function') {
      throw new TypeError('Clipboard API unavailable')
    }
    await navigator.clipboard.writeText(value)
    if (success !== false) {
      ElMessage.success(success)
    }
    return true
  }
  catch {
    ElMessage.error(failure)
    return false
  }
}

import axios from 'axios'

const HTTP_ERROR_MESSAGES: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  401: '登录已失效，请重新登录',
  403: '没有权限执行此操作',
  404: '请求的资源不存在',
  408: '请求超时，请稍后重试',
  409: '数据状态已变更，请刷新后重试',
  413: '提交内容过大，请调整后重试',
  429: '请求过于频繁，请稍后重试',
  500: '服务器内部异常，请稍后重试',
  502: '服务暂时不可用，请稍后重试',
  503: '服务暂不可用，请稍后重试',
  504: '服务响应超时，请稍后重试',
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isChineseMessage(value: unknown): value is string {
  return typeof value === 'string' && /[\u4E00-\u9FFF]/.test(value)
}

function getRecordMessage(value: unknown): unknown {
  if (!isRecord(value)) { return undefined }
  return value.message ?? value.msg
}

function getGenericResponse(error: unknown): Record<string, unknown> | undefined {
  if (!isRecord(error) || !isRecord(error.response)) { return undefined }
  return error.response
}

function getResponseData(error: unknown): unknown {
  if (axios.isAxiosError<unknown>(error)) { return error.response?.data }
  return getGenericResponse(error)?.data
}

function getResponseStatus(error: unknown): unknown {
  if (axios.isAxiosError(error)) { return error.response?.status }
  return getGenericResponse(error)?.status ?? (isRecord(error) ? error.status : undefined)
}

function getErrorCode(error: unknown): string | undefined {
  if (axios.isAxiosError(error)) { return error.code }
  if (!isRecord(error)) { return undefined }
  return typeof error.code === 'string' ? error.code : undefined
}

/** 将 HTTP、业务响应和网络请求失败统一为面向用户的中文提示。 */
export function getRequestErrorMessage(error: unknown): string {
  const serverMessage = getRecordMessage(getResponseData(error))
    ?? getRecordMessage(error)
    ?? (error instanceof Error ? error.message : undefined)

  if (isChineseMessage(serverMessage)) { return serverMessage }

  const status = Number(getResponseStatus(error))
  if (HTTP_ERROR_MESSAGES[status]) { return HTTP_ERROR_MESSAGES[status] }
  if (Number.isFinite(status) && status >= 500) { return '服务器异常，请稍后重试' }

  const code = getErrorCode(error)
  const message = error instanceof Error ? error.message : String(getRecordMessage(error) ?? '')
  if (message === 'Network Error' || code === 'ERR_NETWORK') {
    return '网络连接异常，请检查网络后重试'
  }
  if (message.includes('timeout') || code === 'ECONNABORTED' || code === 'ETIMEDOUT') {
    return '请求超时，请稍后重试'
  }
  if (code === 'ERR_CANCELED') { return '请求已取消' }
  return '请求失败，请稍后重试'
}

/**
 * Blob 下载失败时，Axios 会按 responseType=blob 保留服务器返回的 JSON 错误体。
 * 仅解析小型 JSON 错误响应，继续复用既有的业务消息与 HTTP 状态映射。
 * 不读取大体积或非 JSON 的文件内容，避免把 HTML/二进制错误页当作业务提示。
 */
export async function getRequestErrorMessageAsync(error: unknown): Promise<string> {
  const fallback = getRequestErrorMessage(error)
  const response = axios.isAxiosError<unknown>(error) ? error.response : getGenericResponse(error)
  const payload = response?.data
  if (typeof Blob === 'undefined' || !(payload instanceof Blob) || payload.size > 64 * 1024) {
    return fallback
  }

  const headers = response?.headers
  const headerType = isRecord(headers) ? headers['content-type'] : undefined
  const mediaType = (payload.type || String(headerType ?? '')).split(';')[0].trim().toLowerCase()
  if (mediaType !== 'application/json' && !mediaType.endsWith('+json')) {
    return fallback
  }

  try {
    const parsed: unknown = JSON.parse((await payload.text()).replace(/^\uFEFF/, ''))
    if (isChineseMessage(getRecordMessage(parsed))) {
      return getRequestErrorMessage({ response: { status: response?.status, data: parsed } })
    }
  }
  catch {
    // 无效 JSON 返回原有的标准 HTTP 错误提示，不暴露原始响应。
  }
  return fallback
}

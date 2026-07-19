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

function isChineseMessage(value: unknown): value is string {
  return typeof value === 'string' && /[\u4E00-\u9FFF]/.test(value)
}

function getCorrelationSuffix(error: any): string {
  const requestId = error?.response?.data?.requestId
    ?? error?.response?.headers?.['x-request-id']
    ?? error?.requestId
  const errorCode = error?.response?.data?.errorCode
    ?? error?.response?.headers?.['x-error-code']

  const parts: string[] = []
  if (typeof errorCode === 'string' && errorCode.trim()) {
    parts.push(`错误码：${errorCode.trim()}`)
  }
  if (typeof requestId === 'string' && requestId.trim()) {
    parts.push(`请求编号：${requestId.trim()}`)
  }
  return parts.length > 0 ? `（${parts.join('；')}）` : ''
}

function withCorrelation(message: string, error: any): string {
  return `${message}${getCorrelationSuffix(error)}`
}

/** 将 HTTP 和网络请求失败统一为面向用户的中文提示，并附带可供运维检索的错误码和请求编号。 */
export function getRequestErrorMessage(error: any): string {
  const serverMessage = error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? error?.msg
  if (isChineseMessage(serverMessage)) {
    return withCorrelation(serverMessage, error)
  }

  const status = Number(error?.response?.status ?? error?.status)
  if (HTTP_ERROR_MESSAGES[status]) {
    return withCorrelation(HTTP_ERROR_MESSAGES[status], error)
  }
  if (Number.isFinite(status) && status >= 500) {
    return withCorrelation('服务器异常，请稍后重试', error)
  }

  const message = String(error?.message || '')
  if (message === 'Network Error' || error?.code === 'ERR_NETWORK') {
    return '网络连接异常，请检查网络后重试'
  }
  if (message.includes('timeout') || error?.code === 'ECONNABORTED') {
    return withCorrelation('请求超时，请稍后重试', error)
  }
  if (error?.code === 'ERR_CANCELED') {
    return '请求已取消'
  }
  return withCorrelation('请求失败，请稍后重试', error)
}

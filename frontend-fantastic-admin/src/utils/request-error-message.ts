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

/** 将 HTTP 和网络请求失败统一为面向用户的中文提示。 */
export function getRequestErrorMessage(error: any): string {
  const serverMessage = error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? error?.msg
  if (isChineseMessage(serverMessage)) {
    return serverMessage
  }

  const status = Number(error?.response?.status ?? error?.status)
  if (HTTP_ERROR_MESSAGES[status]) {
    return HTTP_ERROR_MESSAGES[status]
  }
  if (Number.isFinite(status) && status >= 500) {
    return '服务器异常，请稍后重试'
  }

  const message = String(error?.message || '')
  if (message === 'Network Error' || error?.code === 'ERR_NETWORK') {
    return '网络连接异常，请检查网络后重试'
  }
  if (message.includes('timeout') || error?.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试'
  }
  if (error?.code === 'ERR_CANCELED') {
    return '请求已取消'
  }
  return '请求失败，请稍后重试'
}

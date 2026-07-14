type ErrorNotifier = () => void

const pendingErrorNotifiers = new WeakMap<object, ErrorNotifier>()
let listenerInstalled = false

function isReference(value: unknown): value is object {
  return (typeof value === 'object' && value !== null) || typeof value === 'function'
}

function handleUnhandledRejection(event: PromiseRejectionEvent) {
  if (!isReference(event.reason)) {
    return
  }

  const notify = pendingErrorNotifiers.get(event.reason)
  if (!notify) {
    return
  }

  pendingErrorNotifiers.delete(event.reason)
  notify()
}

function installUnhandledRejectionListener() {
  if (listenerInstalled || typeof window === 'undefined') {
    return
  }

  window.addEventListener('unhandledrejection', handleUnhandledRejection)
  listenerInstalled = true
}

/**
 * 为请求错误注册全局兜底提示。
 *
 * Axios 拦截器先登记错误并继续 reject。若页面通过 try/catch 或 Promise.catch
 * 处理了该错误，浏览器不会触发 unhandledrejection，因此只保留页面自己的业务提示；
 * 只有错误最终无人处理时，才显示请求层兜底，避免同一错误出现两套弹窗。
 */
export function registerUnhandledRequestError(
  error: unknown,
  notify: ErrorNotifier,
): void {
  if (!isReference(error)) {
    return
  }

  installUnhandledRejectionListener()
  pendingErrorNotifiers.set(error, notify)
}

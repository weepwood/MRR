import type { App } from 'vue'

type ErrorNotifier = () => void

const pendingErrorNotifiers = new WeakMap<object, ErrorNotifier>()
const installedVueApps = new WeakSet<App>()
let rejectionListenerInstalled = false

function isReference(value: unknown): value is object {
  return (typeof value === 'object' && value !== null) || typeof value === 'function'
}

function notifyRegisteredRequestError(error: unknown): boolean {
  if (!isReference(error)) {
    return false
  }

  const notify = pendingErrorNotifiers.get(error)
  if (!notify) {
    return false
  }

  pendingErrorNotifiers.delete(error)
  notify()
  return true
}

function handleUnhandledRejection(event: PromiseRejectionEvent) {
  notifyRegisteredRequestError(event.reason)
}

function installUnhandledRejectionListener() {
  if (rejectionListenerInstalled || typeof window === 'undefined') {
    return
  }

  window.addEventListener('unhandledrejection', handleUnhandledRejection)
  rejectionListenerInstalled = true
}

/**
 * 登记一个请求错误的兜底提示。
 *
 * 页面通过 try/catch 或 Promise.catch 正常处理时不会触发兜底；错误进入 Vue
 * 全局错误处理器或浏览器 unhandledrejection 时，才显示统一请求错误。
 */
export function registerRequestErrorFallback(
  error: unknown,
  notify: ErrorNotifier,
): void {
  if (!isReference(error)) {
    return
  }

  installUnhandledRejectionListener()
  pendingErrorNotifiers.set(error, notify)
}

/**
 * 将 Vue 捕获到的异步错误接入请求错误兜底。
 * 请求错误由本模块消费，避免继续进入框架通用错误提示；其他错误仍交给原处理器。
 */
export function installRequestErrorFallback(app: App): void {
  if (installedVueApps.has(app)) {
    return
  }

  const previousErrorHandler = app.config.errorHandler
  app.config.errorHandler = (error, instance, info) => {
    if (notifyRegisteredRequestError(error)) {
      console.error(`[Request error] ${info}`, error)
      return
    }

    if (previousErrorHandler) {
      previousErrorHandler(error, instance, info)
      return
    }

    console.error(`[Vue error] ${info}`, error)
  }
  installedVueApps.add(app)
}

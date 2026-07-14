const DEFAULT_FALLBACK_DELAY_MS = 80
const ELEMENT_PLUS_MESSAGE_SELECTOR = '.el-message'

function containsElementPlusMessage(node: Node): boolean {
  if (!(node instanceof Element)) {
    return false
  }
  return node.matches(ELEMENT_PLUS_MESSAGE_SELECTOR)
    || Boolean(node.querySelector(ELEMENT_PLUS_MESSAGE_SELECTOR))
}

/**
 * 延迟显示请求层兜底错误。
 *
 * Axios 拦截器先收到异常，页面的 catch 随后才执行。等待一个很短的窗口并监听
 * Element Plus 消息节点，可以让页面优先展示更具体的业务提示；只有页面没有提示时，
 * 才执行请求层的统一兜底，避免同一错误同时出现两套弹窗。
 */
export function scheduleUnhandledRequestError(
  notify: () => void,
  delayMs = DEFAULT_FALLBACK_DELAY_MS,
): () => void {
  let localMessageShown = false
  let observer: MutationObserver | null = null
  let timer: ReturnType<typeof setTimeout> | null = null

  const cleanup = () => {
    observer?.disconnect()
    observer = null
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  if (typeof document !== 'undefined' && typeof MutationObserver !== 'undefined' && document.body) {
    observer = new MutationObserver((records) => {
      if (records.some(record => [...record.addedNodes].some(containsElementPlusMessage))) {
        localMessageShown = true
      }
    })
    observer.observe(document.body, { childList: true, subtree: true })
  }

  timer = setTimeout(() => {
    observer?.disconnect()
    observer = null
    timer = null
    if (!localMessageShown) {
      notify()
    }
  }, Math.max(0, delayMs))

  return cleanup
}

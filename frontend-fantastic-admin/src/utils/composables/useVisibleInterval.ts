import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue'

export interface VisibleIntervalOptions {
  /** 挂载后是否立即执行一次（页面通常已在 onMounted 自行首载，默认不重复）。 */
  immediate?: boolean
}

/**
 * 只在「页面可见且组件处于激活状态」时运行的轮询。
 *
 * 项目里有多处独立实现的 setInterval 轮询（状态页、开发者模式标识、错误角标、日志等），
 * 其中一部分在浏览器标签隐藏、或页面被 KeepAlive 缓存后仍持续请求后端。
 * 医院终端常年开着多个标签页，这类后台请求会长期占用连接与服务端资源。
 *
 * 行为约定：
 * - 页面隐藏时跳过本周期；重新可见时，如已错过一个周期则立即补一次；
 * - KeepAlive 失活时暂停计时，激活时恢复；
 * - 卸载时自动清理计时器与监听，调用方无需再写 onBeforeUnmount。
 */
export default function useVisibleInterval(
  task: () => unknown,
  intervalMs: number,
  options: VisibleIntervalOptions = {},
) {
  let timer: ReturnType<typeof setInterval> | undefined
  let lastRunAt = 0
  let paused = false

  function run() {
    if (paused || document.hidden) {
      return
    }
    lastRunAt = Date.now()
    void task()
  }

  function stop() {
    if (timer !== undefined) {
      clearInterval(timer)
      timer = undefined
    }
  }

  function start() {
    stop()
    timer = setInterval(run, intervalMs)
  }

  function handleVisibilityChange() {
    if (!document.hidden && !paused && Date.now() - lastRunAt >= intervalMs) {
      run()
    }
  }

  onMounted(() => {
    lastRunAt = Date.now()
    if (options.immediate) {
      run()
    }
    start()
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })

  onActivated(() => {
    if (!paused) {
      return
    }
    paused = false
    handleVisibilityChange()
    start()
  })

  onDeactivated(() => {
    paused = true
    stop()
  })

  onBeforeUnmount(() => {
    stop()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return { start, stop, runNow: run }
}

import type { App, Directive, DirectiveBinding, WatchStopHandle } from 'vue'
import { watch } from 'vue'

export default function directive(app: App) {
  // 用 mounted/unmounted 钩子替代简写函数，确保 watch 在元素卸载时被清理，避免内存泄漏
  const authDirective: Directive<HTMLElement & { _authStop?: WatchStopHandle }, string | string[]> = {
    mounted(el, binding: DirectiveBinding) {
      const stop = watch(
        () => binding.modifiers.all ? useAuth().authAll(binding.value) : useAuth().auth(binding.value),
        (val) => {
          el.style.display = val ? '' : 'none'
        },
        { immediate: true },
      )
      el._authStop = stop
    },
    unmounted(el) {
      if (el._authStop) {
        el._authStop()
        el._authStop = undefined
      }
    },
  }
  app.directive('auth', authDirective)
}

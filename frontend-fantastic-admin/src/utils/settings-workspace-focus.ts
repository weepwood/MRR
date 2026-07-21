const SETTINGS_WORKSPACE_SELECTOR = '.settings-page > .settings-workspace-frame'
const DESKTOP_SETTINGS_MEDIA_QUERY = '(min-width: 981px)'
const REDUCED_MOTION_MEDIA_QUERY = '(prefers-reduced-motion: reduce)'

let installed = false

function focusSettingsWorkspace(event: MouseEvent) {
  if (!window.matchMedia(DESKTOP_SETTINGS_MEDIA_QUERY).matches) {
    return
  }

  const target = event.target
  if (!(target instanceof Element)) {
    return
  }

  const workspaceFrame = target.closest<HTMLElement>(SETTINGS_WORKSPACE_SELECTOR)
  if (!workspaceFrame) {
    return
  }

  const workspaceTop = workspaceFrame.getBoundingClientRect().top
  if (Math.abs(workspaceTop) <= 1) {
    return
  }

  window.scrollTo({
    top: Math.max(0, window.scrollY + workspaceTop),
    behavior: window.matchMedia(REDUCED_MOTION_MEDIA_QUERY).matches ? 'auto' : 'smooth',
  })
}

/**
 * 桌面端点击设置工作区时，将完整的视口框架对齐到视口顶部。
 * 卡片位于框架内部，因此会自然保留上下各 10px 的布局空白。
 * 监听器只安装一次，并通过事件委托兼容设置页 KeepAlive 和路由切换。
 */
export function installSettingsWorkspaceFocus() {
  if (installed || typeof window === 'undefined') {
    return
  }

  installed = true
  document.addEventListener('click', focusSettingsWorkspace)
}

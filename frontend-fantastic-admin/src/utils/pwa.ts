import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'

const UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000

let updatePromptVisible = false
let reloadOnControllerChange = false
let reloadStarted = false

export function buildPwaServiceWorkerUrl(origin: string, buildId: string): string {
  const url = new URL('/sw.js', origin)
  url.searchParams.set('v', buildId)
  return url.toString()
}

function resolveBuildId(): string {
  const { version, gitCommit, buildTime } = __SYSTEM_INFO__.product
  return `${version}-${gitCommit}-${buildTime}`
}

async function promptForUpdate(registration: ServiceWorkerRegistration): Promise<void> {
  const waitingWorker = registration.waiting
  if (!waitingWorker || updatePromptVisible) {
    return
  }

  updatePromptVisible = true
  try {
    await ElMessageBox.confirm(
      'MRR 已发布新版本，重新加载后即可完成更新。',
      '应用更新',
      {
        confirmButtonText: '立即更新',
        cancelButtonText: '稍后',
        type: 'info',
        closeOnClickModal: false,
        closeOnPressEscape: false,
      },
    )
    reloadOnControllerChange = true
    waitingWorker.postMessage({ type: 'SKIP_WAITING' })
  }
  catch {
    // 用户选择稍后更新，保留当前页面和等待中的 Service Worker。
  }
  finally {
    updatePromptVisible = false
  }
}

async function registerPwa(): Promise<void> {
  try {
    const scriptUrl = buildPwaServiceWorkerUrl(window.location.origin, resolveBuildId())
    const registration = await navigator.serviceWorker.register(scriptUrl, {
      scope: '/',
      updateViaCache: 'none',
    })

    if (registration.waiting && navigator.serviceWorker.controller) {
      void promptForUpdate(registration)
    }

    registration.addEventListener('updatefound', () => {
      const installingWorker = registration.installing
      installingWorker?.addEventListener('statechange', () => {
        if (installingWorker.state === 'installed' && navigator.serviceWorker.controller) {
          void promptForUpdate(registration)
        }
      })
    })

    window.setInterval(() => {
      void registration.update()
    }, UPDATE_CHECK_INTERVAL_MS)

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        void registration.update()
      }
    })
  }
  catch (error) {
    // PWA 能力不能阻断病案系统主流程，注册失败时仅记录诊断信息。
    // eslint-disable-next-line no-console
    console.warn('[MRR PWA] Service Worker 注册失败', error)
  }
}

export function installPwa(): void {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) {
    return
  }

  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!reloadOnControllerChange || reloadStarted) {
      return
    }
    reloadStarted = true
    window.location.reload()
  })

  if (document.readyState === 'complete') {
    void registerPwa()
  }
  else {
    window.addEventListener('load', () => void registerPwa(), { once: true })
  }
}

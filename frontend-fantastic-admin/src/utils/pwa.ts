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

function promptForUpdate(registration: ServiceWorkerRegistration): void {
  const waitingWorker = registration.waiting
  if (!waitingWorker || updatePromptVisible) {
    return
  }

  updatePromptVisible = true
  const accepted = window.confirm('MRR 已发布新版本。是否立即重新加载并更新应用？')
  updatePromptVisible = false

  if (accepted) {
    reloadOnControllerChange = true
    waitingWorker.postMessage({ type: 'SKIP_WAITING' })
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
      promptForUpdate(registration)
    }

    registration.addEventListener('updatefound', () => {
      const installingWorker = registration.installing
      installingWorker?.addEventListener('statechange', () => {
        if (installingWorker.state === 'installed' && navigator.serviceWorker.controller) {
          promptForUpdate(registration)
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

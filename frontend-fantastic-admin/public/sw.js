const CACHE_PREFIX = 'mrr-pwa-'
const rawVersion = new URL(self.location.href).searchParams.get('v') || 'dev'
const version = rawVersion.replace(/[^a-zA-Z0-9._-]/g, '-').slice(0, 64)
const STATIC_CACHE = `${CACHE_PREFIX}static-${version}`
const APP_SHELL = [
  '/offline.html',
  '/manifest.webmanifest',
  '/favicon.svg',
  '/pwa/icon-192.svg',
  '/pwa/icon-512.svg',
  '/pwa/icon-maskable.svg',
]

const SENSITIVE_PATH_PREFIXES = [
  '/api/',
  '/proxy/',
  '/actuator/',
  '/swagger-ui/',
  '/v3/api-docs',
]

function isSensitivePath(pathname) {
  return SENSITIVE_PATH_PREFIXES.some(prefix => pathname.startsWith(prefix))
}

function isStaticApplicationAsset(pathname) {
  return pathname.startsWith('/assets/')
    || pathname.startsWith('/browser_upgrade/')
    || pathname.startsWith('/pwa/')
    || pathname === '/favicon.svg'
    || pathname === '/manifest.webmanifest'
}

async function staleWhileRevalidate(request, event) {
  const cache = await caches.open(STATIC_CACHE)
  const cachedResponse = await cache.match(request)
  const networkResponse = fetch(request).then(async (response) => {
    if (response.ok && response.type === 'basic') {
      await cache.put(request, response.clone())
    }
    return response
  })

  if (cachedResponse) {
    event.waitUntil(networkResponse.catch(() => undefined))
    return cachedResponse
  }

  return networkResponse
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC_CACHE).then(cache => cache.addAll(APP_SHELL)),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    Promise.all([
      caches.keys().then(keys => Promise.all(
        keys
          .filter(key => key.startsWith(CACHE_PREFIX) && key !== STATIC_CACHE)
          .map(key => caches.delete(key)),
      )),
      self.clients.claim(),
    ]),
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') {
    return
  }

  const url = new URL(request.url)
  if (url.origin !== self.location.origin || isSensitivePath(url.pathname)) {
    return
  }

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() => caches.match('/offline.html')),
    )
    return
  }

  if (isStaticApplicationAsset(url.pathname)) {
    event.respondWith(staleWhileRevalidate(request, event))
  }
})

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') {
    self.skipWaiting()
  }
})

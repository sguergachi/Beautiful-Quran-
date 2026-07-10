/**
 * Offline shell for the GitHub Pages reader.
 *
 * Strategy:
 *   - Navigations / HTML → network-first (so deploys always take effect)
 *   - Hashed JS/CSS, wasm, fonts, quran.db → cache-first
 *   - sw.js itself is never cached through this worker
 *
 * Bump CACHE whenever the shell contract changes. Activate deletes every
 * other cache name and reloads open clients so a poisoned index.html
 * (pointing at deleted hashed assets) cannot leave a blank page.
 */
const CACHE = 'beautiful-quran-web-v5'
const BASE = self.registration.scope

function isNavigationRequest(req, url) {
  if (req.mode === 'navigate') return true
  if (url.pathname.endsWith('.html')) return true
  // Scope root (…/app/ or …/app)
  const path = url.pathname.endsWith('/') ? url.pathname : `${url.pathname}/`
  const basePath = new URL(BASE).pathname
  return path === basePath
}

function isServiceWorkerScript(url) {
  return url.pathname.endsWith('/sw.js') || url.pathname.endsWith('sw.js')
}

function shouldCacheAsset(url) {
  if (!url.href.startsWith(BASE)) return false
  if (isServiceWorkerScript(url)) return false
  return (
    url.pathname.endsWith('.db') ||
    url.pathname.endsWith('.js') ||
    url.pathname.endsWith('.css') ||
    url.pathname.endsWith('.ttf') ||
    url.pathname.endsWith('.woff2') ||
    url.pathname.endsWith('.wasm') ||
    url.pathname.endsWith('.svg') ||
    url.pathname.endsWith('.webmanifest')
  )
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) =>
        cache
          .addAll([
            BASE,
            `${BASE}index.html`,
            `${BASE}quran.db`,
            `${BASE}manifest.webmanifest`,
            `${BASE}fonts.css`,
          ])
          .catch(() => undefined),
      ),
  )
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys()
      const hadStale = keys.some((k) => k !== CACHE)
      await Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
      await self.clients.claim()
      // Force a reload so clients that already painted a blank root from a
      // stale cached index pick up the fresh network-first shell.
      if (hadStale) {
        const clients = await self.clients.matchAll({
          type: 'window',
          includeUncontrolled: true,
        })
        await Promise.all(
          clients.map((client) => {
            if ('navigate' in client) return client.navigate(client.url)
            return undefined
          }),
        )
      }
    })(),
  )
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  if (req.method !== 'GET') return

  const url = new URL(req.url)
  if (!url.href.startsWith(BASE)) return

  // Always hit the network for the worker script so updates are not pinned.
  if (isServiceWorkerScript(url)) {
    event.respondWith(fetch(req))
    return
  }

  if (isNavigationRequest(req, url)) {
    event.respondWith(networkFirstNavigation(req))
    return
  }

  event.respondWith(cacheFirstAsset(req, url))
})

async function networkFirstNavigation(req) {
  try {
    const res = await fetch(req)
    if (res.ok) {
      const cache = await caches.open(CACHE)
      await cache.put(req, res.clone())
      // Also key the bare scope URL so offline fallbacks resolve.
      if (req.mode === 'navigate') {
        await cache.put(BASE, res.clone())
        await cache.put(`${BASE}index.html`, res.clone())
      }
    }
    return res
  } catch {
    const cached =
      (await caches.match(req)) ||
      (await caches.match(`${BASE}index.html`)) ||
      (await caches.match(BASE))
    return cached || Response.error()
  }
}

async function cacheFirstAsset(req, url) {
  const cached = await caches.match(req)
  if (cached) return cached
  try {
    const res = await fetch(req)
    if (res.ok && shouldCacheAsset(url)) {
      const copy = res.clone()
      void caches.open(CACHE).then((c) => c.put(req, copy))
    }
    return res
  } catch {
    return cached || Response.error()
  }
}

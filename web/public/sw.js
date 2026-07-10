/* Minimal service worker: cache shell + quran.db after first visit. */
const CACHE = 'beautiful-quran-v1'

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) =>
      cache.addAll(['/', '/index.html', '/quran.db', '/manifest.webmanifest']).catch(() => undefined),
    ),
  )
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))),
    ),
  )
  self.clients.claim()
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  if (req.method !== 'GET') return
  event.respondWith(
    caches.match(req).then((cached) => {
      if (cached) return cached
      return fetch(req)
        .then((res) => {
          const copy = res.clone()
          const url = new URL(req.url)
          if (
            res.ok &&
            (url.pathname.endsWith('.db') ||
              url.pathname.endsWith('.js') ||
              url.pathname.endsWith('.css') ||
              url.pathname.endsWith('.ttf') ||
              url.pathname.endsWith('.wasm') ||
              url.pathname.endsWith('.html') ||
              url.pathname === '/')
          ) {
            void caches.open(CACHE).then((c) => c.put(req, copy))
          }
          return res
        })
        .catch(() => cached || Response.error())
    }),
  )
})

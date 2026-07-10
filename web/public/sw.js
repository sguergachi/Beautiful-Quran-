/* Cache shell + quran.db after first visit. Scope is the app subdirectory. */
const CACHE = 'beautiful-quran-web-v2'
const BASE = self.registration.scope

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
            url.href.startsWith(BASE) &&
            (url.pathname.endsWith('.db') ||
              url.pathname.endsWith('.js') ||
              url.pathname.endsWith('.css') ||
              url.pathname.endsWith('.ttf') ||
              url.pathname.endsWith('.wasm') ||
              url.pathname.endsWith('.html') ||
              url.pathname.endsWith('/'))
          ) {
            void caches.open(CACHE).then((c) => c.put(req, copy))
          }
          return res
        })
        .catch(() => cached || Response.error())
    }),
  )
})

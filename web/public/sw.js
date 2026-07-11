/**
 * Offline shell for the GitHub Pages reader.
 *
 * Strategy:
 *   - Navigations / HTML → network-only (never Cache API)
 *   - Hashed JS/CSS, wasm, fonts, quran.db → cache-first
 *   - sw.js itself is never cached through this worker
 *
 * Caching index.html is what blanked phones after each Pages deploy: a
 * cache-first (or stale network-first fallback) shell pointed at deleted
 * hashed assets. HTML must not enter the Cache API.
 *
 * Bump CACHE whenever this contract changes. Activate deletes every other
 * cache name and reloads open clients so a poisoned shell cannot stick.
 */
const CACHE = 'beautiful-quran-web-v7'
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

function isHtmlRequest(url) {
  if (url.pathname.endsWith('.html')) return true
  const path = url.pathname.endsWith('/') ? url.pathname : `${url.pathname}/`
  const basePath = new URL(BASE).pathname
  return path === basePath
}

function shouldCacheAsset(url) {
  if (!url.href.startsWith(BASE)) return false
  if (isServiceWorkerScript(url)) return false
  if (isHtmlRequest(url)) return false
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

self.addEventListener('install', () => {
  // Do not precache HTML or the 27 MB DB — HTML must stay network-only, and
  // addAll(quran.db) blows mobile cache quotas and can stall install.
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys()
      const hadStale = keys.some((k) => k !== CACHE)
      await Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))

      // Older workers may have stored index.html under this or prior names.
      // Strip any HTML out of the current cache so it can never be replayed.
      let purgedHtml = false
      try {
        const cache = await caches.open(CACHE)
        const reqs = await cache.keys()
        const htmlReqs = reqs.filter((r) => isHtmlRequest(new URL(r.url)))
        if (htmlReqs.length > 0) {
          purgedHtml = true
          await Promise.all(htmlReqs.map((r) => cache.delete(r)))
        }
      } catch {
        /* ignore */
      }

      await self.clients.claim()

      // Only force-reload when we actually cleared a poisoned shell — a bare
      // first install must not bounce the user who just finished booting.
      if (hadStale || purgedHtml) {
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
    event.respondWith(networkOnlyNavigation(req))
    return
  }

  event.respondWith(cacheFirstAsset(req, url))
})

async function networkOnlyNavigation(req) {
  try {
    return await fetch(req)
  } catch {
    return new Response(
      `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><title>Beautiful Quran</title></head><body style="margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#FAF3E8;color:#1C1B18;font-family:Georgia,serif;text-align:center;padding:2rem"><div><h1 style="font-weight:500;letter-spacing:.02em">Beautiful Quran</h1><p style="opacity:.7">You appear to be offline. Reconnect and reload.</p></div></body></html>`,
      {
        status: 503,
        headers: { 'Content-Type': 'text/html; charset=utf-8' },
      },
    )
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

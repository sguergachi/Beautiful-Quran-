import { assetUrl } from './assetUrl'

/**
 * Register the offline service worker only after the app has booted.
 *
 * Registering from a blank/failed shell is what left phones on a poisoned
 * Cache API entry pointing at deleted hashed assets. Wait until sql.js +
 * quran.db have loaded successfully, then install the worker.
 */
export function registerServiceWorker(): void {
  if (!('serviceWorker' in navigator)) return
  // Dev server HMR and the worker fight over the same scope.
  if (import.meta.env.DEV) return

  window.setTimeout(() => {
    void navigator.serviceWorker
      .register(assetUrl('sw.js'), { scope: import.meta.env.BASE_URL })
      .catch(() => {
        /* optional */
      })
  }, 0)
}

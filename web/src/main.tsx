import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './ui/App'
import { assetUrl } from './assetUrl'
import './ui/theme/styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker
      .register(assetUrl('sw.js'), { scope: import.meta.env.BASE_URL })
      .catch(() => {
        /* optional */
      })
  })
}

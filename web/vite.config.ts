import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/** GitHub Pages serves this repo from /Beautiful-Quran-/ (docs/). The app lives at /app/. */
const pagesBase = process.env.VITE_BASE ?? '/'

export default defineConfig({
  plugins: [react()],
  base: pagesBase,
  server: {
    port: 5173,
  },
  test: {
    globals: true,
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})

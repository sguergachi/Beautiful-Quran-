import { copyFile, mkdir, stat } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const source = resolve(webRoot, '..', 'data', 'quran.db')
const destination = resolve(webRoot, 'public', 'quran.db')

let sourceStat
try {
  sourceStat = await stat(source)
} catch {
  throw new Error(`Missing canonical Quran database: ${source}`)
}

if (!sourceStat.isFile() || sourceStat.size === 0) {
  throw new Error(`Canonical Quran database is empty or invalid: ${source}`)
}

await mkdir(dirname(destination), { recursive: true })
await copyFile(source, destination)

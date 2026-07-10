/**
 * WASM SQLite wrapper over the committed quran.db asset.
 */
import initSqlJs, { type Database, type SqlJsStatic } from 'sql.js'
import { assetUrl } from '../assetUrl'

let SQL: SqlJsStatic | null = null
let db: Database | null = null
let loadPromise: Promise<Database> | null = null

export type LoadProgress = { loaded: number; total: number; phase: 'wasm' | 'db' }

async function loadSqlJs(): Promise<SqlJsStatic> {
  if (SQL) return SQL
  SQL = await initSqlJs({
    locateFile: (file) => assetUrl(file),
  })
  return SQL
}

async function fetchWithProgress(
  url: string,
  onProgress?: (loaded: number, total: number) => void,
): Promise<ArrayBuffer> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`Failed to load quran.db (${res.status})`)
  const total = Number(res.headers.get('content-length') || 0)
  if (!res.body || !onProgress) return res.arrayBuffer()

  const reader = res.body.getReader()
  const chunks: Uint8Array[] = []
  let loaded = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
    loaded += value.byteLength
    onProgress(loaded, total || loaded)
  }
  const out = new Uint8Array(loaded)
  let offset = 0
  for (const chunk of chunks) {
    out.set(chunk, offset)
    offset += chunk.byteLength
  }
  return out.buffer
}

/** Open (or return) the read-only Quran database. Cached after first load. */
export async function openDatabase(
  dbUrl = assetUrl('quran.db'),
  onProgress?: (p: LoadProgress) => void,
): Promise<Database> {
  if (db) return db
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    onProgress?.({ loaded: 0, total: 0, phase: 'wasm' })
    const sql = await loadSqlJs()
    onProgress?.({ loaded: 0, total: 0, phase: 'db' })
    const buf = await fetchWithProgress(dbUrl, (loaded, total) => {
      onProgress?.({ loaded, total, phase: 'db' })
    })
    db = new sql.Database(new Uint8Array(buf))
    return db
  })()
  return loadPromise
}

export function getDatabase(): Database {
  if (!db) throw new Error('Database not open — call openDatabase() first')
  return db
}

export function queryAll<T>(
  sql: string,
  params: (string | number)[] = [],
  map: (row: Record<string, unknown>) => T,
): T[] {
  const database = getDatabase()
  const stmt = database.prepare(sql)
  stmt.bind(params)
  const out: T[] = []
  while (stmt.step()) {
    out.push(map(stmt.getAsObject() as Record<string, unknown>))
  }
  stmt.free()
  return out
}

export function queryOne<T>(
  sql: string,
  params: (string | number)[] = [],
  map: (row: Record<string, unknown>) => T,
): T | null {
  const rows = queryAll(sql, params, map)
  return rows[0] ?? null
}

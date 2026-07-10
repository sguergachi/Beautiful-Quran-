/**
 * WASM SQLite wrapper over the committed quran.db asset.
 */
import initSqlJs, { type Database, type SqlJsStatic } from 'sql.js'

let SQL: SqlJsStatic | null = null
let db: Database | null = null
let loadPromise: Promise<Database> | null = null

async function loadSqlJs(): Promise<SqlJsStatic> {
  if (SQL) return SQL
  SQL = await initSqlJs({
    locateFile: (file) => `/${file}`,
  })
  return SQL
}

/** Open (or return) the read-only Quran database. Cached after first load. */
export async function openDatabase(dbUrl = '/quran.db'): Promise<Database> {
  if (db) return db
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    const sql = await loadSqlJs()
    const res = await fetch(dbUrl)
    if (!res.ok) throw new Error(`Failed to load quran.db (${res.status})`)
    const buf = await res.arrayBuffer()
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

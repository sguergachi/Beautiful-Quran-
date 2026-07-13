# Shared Quran data

`quran.db` is the canonical, committed database consumed by both applications.
Normal Android and web builds copy it into generated platform-specific asset
directories; those copies are not source files and must not be committed.

Regenerate the database only when deliberately changing Quran content or
timings:

```bash
python3 tools/build_db.py
```

Any database content change must also bump `QuranDatabase.DB_FILE_NAME` so
existing Android installs extract the new asset instead of retaining a stale
cached database.

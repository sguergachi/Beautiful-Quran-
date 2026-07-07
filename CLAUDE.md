# CLAUDE.md

All agent guidance for this repository lives in **[AGENTS.md](AGENTS.md)**.
Read it before making changes — it covers the repo map, build/test commands,
the invariants you must not break, code conventions, and where the detailed
documentation lives.

Quick essentials (details and rationale in AGENTS.md):

- Test with `./gradlew testDebugUnitTest` (JDK 17); run it before committing.
- `app/src/main/assets/quran.db` is a committed asset — only regenerate it
  (`python3 tools/build_db.py`) when deliberately changing data, and bump
  `QuranDatabase.DB_FILE_NAME` whenever its content changes.
- UI follows a strict paper metaphor: no dialogs, ripples, shadows, or cards.
  Read `docs/DESIGN.md` before any UI change.

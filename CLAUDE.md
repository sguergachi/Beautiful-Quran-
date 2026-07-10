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

## PR Workflow

When creating pull requests:
- Create the PR and push your changes
- Do not monitor PR status, CI, or reviews after opening it
- Do not follow up on CI failures, review comments, or merge conflicts
- Do not worry about PR issues or whether it's mergeable
- Let the user review and check the PR status themselves

Just make the PR and move on.

**Before starting follow-up work, check whether the current PR is already
merged** (e.g. `mcp__github__pull_request_read` with method `get`, or `gh pr
view`). A merged PR is finished — never push follow-up commits onto its branch
expecting them to reappear in it, and never reuse it. If it is merged, restart
the branch from the latest default branch (keep the same branch name),
re-apply the outstanding work, and open a **new** PR. If it is still open,
continue on the same branch/PR.

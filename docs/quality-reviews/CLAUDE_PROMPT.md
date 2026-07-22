Deep Android codebase quality review for Beautiful Quran (this repo).

Context:
- Single-module Android Kotlin/Compose app under app/
- Read AGENTS.md and docs/COMPLEXITY.md first (invariants matter)
- Labs (Timings Lab, brush/check tuning, ornaments lab, ink lab) are STILL being tuned — note them but deprioritize; do not recommend deleting labs
- We already know ReaderScreen/ReaderComponents are large; go deeper on QUALITY (correctness, lifecycle races, coupling, test gaps, review risk), not vanity LOC
- Optional: read docs/quality-reviews/ANDROID_QUALITY_CODEX.md and ANDROID_QUALITY_GROK.md for cross-check, but form your own evidence-based view

Method:
1. Measure current Android source sizes, package breakdown, LaunchedEffect/mutable-state density
2. Inspect highest-risk coordinators: ReaderScreen, ReaderViewModel, MainActivity, PlayerController/PlaybackService, assistant/AppFunctions, share if present
3. Compare pure domain (tested) vs untested mutable glue
4. Note post-merge features: gather/share, annotations, chapter navigation, rail

Deliverable — write ONE markdown file only:
docs/quality-reviews/ANDROID_QUALITY_CLAUDE.md

Required sections:
1. Executive verdict (1 short paragraph + overall grade)
2. Scorecard table (architecture, purity, hygiene, lifecycle correctness, tests, reviewability)
3. Ranked findings (P0–P3) with file paths and why it is quality risk
4. Top 5 actionable priorities (ordered, concrete, no lab deletion)
5. What NOT to change (preserve list)
6. Evidence appendix (LOC tables, effect counts, untested large files)

Rules:
- Do NOT modify production source or tests
- Do NOT open a PR
- Only write the review markdown file above
- Be specific; cite files and mechanisms, not vibes

# QCF V2 page fonts (tracked, not bundled)

These are the split `qcf-v2-fonts.tar.xz.part*` archives of the 604 QCF/QPC V2
per-page Mushaf fonts. They are **kept under version control but deliberately
excluded from the app bundle**: they live here, outside `app/src/main/assets/`,
so Gradle never packages them into the APK (~100 MB of fonts).

The app currently renders Arabic-only mode with the responsive Hafs renderer
only; the QCF ("Mushaf") renderer and its `QcfFontProvider` were removed. These
archives are retained so a future full Mushaf-style reader can re-enable QCF
rendering without re-downloading the fonts.

To re-bundle: move the parts back into `app/src/main/assets/`, restore the
`noCompress += "part0"/"part1"` entries in `app/build.gradle.kts`, and restore
the QCF render path (see git history around `QcfFontProvider`).

Regenerate with `scripts/fetch_qcf_v2_fonts.sh`.

See `docs/CONNECTED_ARABIC_RENDERING.md` → "QCF renderer status" for context.

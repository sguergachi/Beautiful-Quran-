# Beautiful Quran

A beautiful, simple Quran app for Android. Its signature feature is a lyric-style
**follow-along view**: as the reciter recites, each Arabic word lights up in time
with the audio — with its English meaning right beneath it — like a karaoke or
Apple Music lyrics view.

- 📖 Full Quran, Uthmani script, in the KFGQPC Hafs typeface
- ✨ Word-by-word highlighting synced to reciters with bundled timing data
- 🎙️ 7 reciters, with ayah audio streamed and cached
- 🈯 Word-by-word English gloss + Saheeh International translation
- 🔁 Repeat one ayah, the whole surah, or any ayah range you choose
- 🔍 Search the English translation and word glosses within a surah
- 🌙 Warm paper light theme and near-black charcoal dark theme
- 📴 All text and timing data bundled offline; audio streams and caches
- 🚫 No ads, no accounts, no analytics

## Install on your phone

Grab **BeautifulQuran.apk** from the
[latest release](../../releases/latest), open it on your Android phone
(Android 8.0+), and allow the install when prompted.

## Building

```bash
python3 tools/build_db.py     # builds app/src/main/assets/quran.db
./gradlew assembleDebug
```

`tools/build_db.py` downloads the Quran text, word-by-word data, and word-level
audio timings, validates them against each other, and packs them into a single
SQLite asset. CI (GitHub Actions) does this on every push and publishes the APK.

## Run in an Android emulator on Linux

From a fresh Arch/CachyOS-style Linux install:

```bash
scripts/setup_android_emulator.sh
scripts/run_android_app.sh
```

The setup script installs/verifies JDK 17, using
`~/.local/share/android-dev/jdk-17` if no system Java is available, downloads
Android command-line tools to `~/Android/Sdk`, installs API 35 emulator
packages, writes `local.properties`, builds `app/src/main/assets/quran.db` if
needed, and creates an AVD named `BeautifulQuran_API_35`.

For future runs, use:

```bash
scripts/run_android_app.sh
```

If the emulator window does not appear or booting times out, check
`.android-emulator.log`. To make the SDK tools available in your current shell:

```bash
source scripts/android_env.sh
```

## Documentation

- [PLAN.md](PLAN.md) — the original product/engineering plan and research
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — how the app is put together: pipeline, sync engine, modules, conventions
- [docs/DESIGN.md](docs/DESIGN.md) — the design language: the sheet, ink, color, type, motion
- [docs/PERFORMANCE.md](docs/PERFORMANCE.md) — every smoothness technique in use and why
- [docs/REPEAT_HIGHLIGHTING.md](docs/REPEAT_HIGHLIGHTING.md) — the orange second fade for words a reciter repeats, and where the repeat-aware timing data comes from
- [docs/ROOT_VIEWER.md](docs/ROOT_VIEWER.md) — hold-to-reveal root lexicon: counts, ayah concordance, jump-to-chapter
- [docs/TIMINGS_LAB.md](docs/TIMINGS_LAB.md) — in-app timing editor (developer mode)

## Data & attribution

| Content | Source | License |
|---|---|---|
| Uthmani text + Saheeh Intl. translation | [quran-json](https://github.com/risan/quran-json) (Tanzil / Al Quran Cloud) | free with attribution |
| Word-by-word gloss + transliteration | Quran.com dataset via npm | free with attribution |
| Root / lemma / morphology | [Quranic Arabic Corpus](http://corpus.quran.com) v0.4 | free with attribution + link |
| Word timing segments | [cpfair/quran-align](https://github.com/cpfair/quran-align) | CC-BY 4.0 |
| Repeat-aware timing segments | [quran.com](https://quran.com) `qdc` audio API | free with attribution |
| Recitation audio | [everyayah.com](https://everyayah.com) | free; rights remain with reciters |
| Arabic typeface | KFGQPC HAFS Uthmanic Script, King Fahd Complex | free redistribution |

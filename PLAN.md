# Beautiful Quran — Android App Plan

A beautiful, simple Quran app for Android whose signature feature is a **lyric-style
"follow-along" view**: as the reciter recites, each word lights up in the Arabic text —
with its English meaning directly beneath it — perfectly in time with the audio,
the way a karaoke or Apple Music lyrics view works.

This document is the full plan: product vision, the research behind every content
source and library choice, architecture, data pipeline, screen specs, and milestones.

---

## 1. Product vision

**One thing, done beautifully.** This is not a 40-feature Quran suite. Version 1 is:

1. Pick a surah (and optionally a reciter).
2. A serene, focused reading screen appears.
3. Press play — the recitation flows, and words illuminate one-by-one in Arabic with
   the English gloss under each word, plus a full flowing translation per ayah.
4. The view auto-scrolls gently, keeping the active ayah centered, like lyrics.

Everything else (bookmarks, resume position, reciter choice, dark mode) exists only to
support that core loop. No ads, no accounts, no clutter.

### Design principles

- **Calm & reverent** — generous whitespace, muted motion, no visual noise competing
  with the words themselves.
- **The word is the hero** — typography carries the entire design. Ornamentation
  (geometric dividers, surah headers) is used sparingly, like punctuation.
- **Immediate** — offline-first; opening a surah is instant, no spinners for text.
- **Honest highlighting** — timing must feel *right*. A karaoke view that lags or
  jumps feels broken; sync accuracy is a first-class engineering requirement.

---

## 2. The follow-along feature — how it actually works

This is the crux of the app, so the mechanics are worth spelling out.

### The data that makes it possible

Word-level timestamp data for Quranic recitations **already exists — we do not need
to run speech alignment ourselves**:

- **[quran-align](https://github.com/cpfair/quran-align)** (cpfair) — word-accurate
  timestamps produced with a CMU Sphinx forced-alignment pipeline, average error
  < 73 ms per word. Pre-generated JSON files are on the
  [Releases page](https://github.com/cpfair/quran-align/releases) for **12 reciter
  recordings**, keyed to [everyayah.com](https://everyayah.com) per-ayah MP3s:
  Abdul Basit (Murattal + Mujawwad), Alafasy, Husary (+ Muallim), Minshawi
  (Murattal + Mujawwad), Sudais, Shuraym, Abu Bakr ash-Shatri, Hani Rifai,
  Mohammad al-Tablaway. **License: CC-BY 4.0** (attribution required — easy).
  Format per ayah: `segments: [[word_start_idx, word_end_idx, start_ms, end_ms], …]`.

- **[QUL — Quranic Universal Library](https://qul.tarteel.ai/)** (Tarteel) — downloadable
  segment databases for both **ayah-by-ayah** and **gapless surah-by-surah** recitations
  (tagged ["with segments"](https://qul.tarteel.ai/docs/with-segments)), format
  `[[word_number, start_ms, end_ms], …]` per ayah. QUL is also our source for the
  Quran text, word-by-word translations, translations, and fonts (see §3). Free
  account required to download; data is redistributable with attribution.

- **[Quran Foundation Content API](https://api-docs.quran.foundation/)** (quran.com's
  API) exposes the same segments at runtime via `/recitations/{id}/audio_files` —
  but it now **requires OAuth2 client-credentials** (the old open
  `api.quran.com/api/v4` is deprecated). That's fine for a server, awkward for a
  client-only Android app.

**Decision:** ship timing data **bundled offline** in the app's SQLite database
(built from QUL exports / quran-align releases at build time), and stream only the
audio. No API keys, no backend, no network dependency for text — and the follow-along
works in airplane mode once audio is cached.

### The sync engine (runtime)

```
Media3 ExoPlayer ──► position ticker (every ~33 ms while playing)
                        │
                        ▼
        binary-search current position in the ayah's
        sorted word-segment list  →  activeWordIndex
                        │
                        ▼
        StateFlow<HighlightState(surah, ayah, wordIndex)>
                        │
                        ▼
        Compose UI: the word composable whose index matches
        animates its highlight; LazyColumn auto-scrolls to
        keep the active ayah in the reading zone
```

Implementation notes:

- ExoPlayer gives `currentPosition` on demand; a coroutine ticks with
  `while(isActive) { emit(player.currentPosition); delay(33) }` on the main
  dispatcher (cheap), feeding a `StateFlow`. Only the *derived* word index is
  published, so recomposition happens **once per word change**, not 30×/sec.
- Per-ayah audio files (everyayah style): playlist of `MediaItem`s, one per ayah;
  `currentMediaItemIndex` maps to the ayah, position maps into that ayah's segments.
  This also gives free ayah-repeat and tap-an-ayah-to-seek.
- Gapless surah files (QUL): single MediaItem, segments carry absolute offsets —
  same binary search, and `timestamp_from/to` per ayah drives ayah highlighting.
  **V1 uses per-ayah files** (simpler seeking, resumable downloads); gapless is a
  V2 upgrade for a more "continuous" listening feel.
- Highlight animation: `animateColorAsState` + a soft background pill behind the
  active word (~150 ms ease-out) reads as "flowing" rather than "blinking".
  Words already recited stay in a slightly dimmed "sung" state (classic karaoke
  affordance); upcoming words are the default color.
- Auto-scroll: `LazyListState.animateScrollToItem` with an offset that keeps the
  active ayah in the upper third — scroll *only* when the active ayah changes, and
  suspend auto-scroll for ~4 s after the user scrolls manually (resume chip appears,
  like YouTube Music lyrics).

### Why this will feel accurate

quran-align's published accuracy (mean < 73 ms, σ ≈ 139 ms) is well inside the
~150–200 ms window humans perceive as "in sync" for karaoke-style highlighting, and
the highlight animation itself masks small errors. The bundled-data approach means
zero network jitter in the timing path.

---

## 3. Content sourcing (all researched & verified)

| Content | Source | Format | License / notes |
|---|---|---|---|
| Arabic text (Uthmani) | [QUL exports](https://qul.tarteel.ai/) or [Tanzil](https://tanzil.net/download/) | SQLite/JSON, word-segmented (QPC Hafs word-by-word dataset) | Free; attribution. Word-segmented text is required so word N in text = word N in segments |
| Word-by-word English gloss | QUL "word by word translation" dataset (same data as quran.com/quranwbw) | per-word English + transliteration | Attribution required |
| Full English translation | **Dr. Mustafa Khattab, The Clear Quran** (modern, readable — what quran.com defaults to) and/or **Saheeh International** | QUL / Tanzil downloads | Both are distributed free for non-commercial apps with attribution; include attribution screen |
| Word timings (ayah-by-ayah) | [quran-align releases](https://github.com/cpfair/quran-align/releases) + QUL segments | JSON | **CC-BY 4.0** |
| Audio (per-ayah MP3) | [everyayah.com](https://everyayah.com) (`…/data/Alafasy_128kbps/001001.mp3`) and/or QUL/quran.com audio CDN | MP3, 64–192 kbps | Free to use; recitations are freely distributed; credit reciters + everyayah |
| Arabic font | **KFGQPC Uthmanic Script Hafs** (v2 "Hafs smart devices" build) via [QUL fonts](https://qul.tarteel.ai/resources/font) / [qpc-fonts mirror](https://github.com/nuqayah/qpc-fonts) | TTF/OTF | Free from King Fahd Complex; designed exactly for verse-level rendering on devices |
| Latin font | **Figtree** or **Inter** (UI), optional serif (e.g. Source Serif 4) for translation text | Google Fonts | OFL |
| Surah metadata | QUL / Tanzil (names, English names, revelation place, ayah counts) | JSON | Free |

**Font note:** the *page-exact* QCF mushaf fonts (604 per-page ligature fonts) are
gorgeous but are a mushaf-page technology — one font file per page, glyph = whole word,
**cannot mix with per-word English glosses**. For a lyric view, KFGQPC Uthmanic Hafs
(real Unicode text, ligature-rich, the standard for verse-level display) is the right
choice. A "Mushaf mode" with QCF fonts is a possible V3 feature, not V1.

**Reciters shipped in V1** (all have quran-align/QUL word segments + everyayah audio):

1. Mishary Alafasy (128 kbps) — default; clear, popular, very well-aligned
2. Mahmoud Khalil al-Husary (64 kbps) — the gold standard for tajwīd clarity
3. Abdul Basit Abdus-Samad, Murattal (64 kbps)
4. Mohamed Siddiq al-Minshawi, Murattal (128 kbps)
5. Abdurrahman as-Sudais (192 kbps)
6. Saud ash-Shuraym (128 kbps)

---

## 4. Tech stack & libraries

| Concern | Choice | Why |
|---|---|---|
| Language | **Kotlin** (2.x), coroutines + Flow | Standard |
| UI | **Jetpack Compose + Material 3** | Animation APIs (`animateColorAsState`, `AnimatedVisibility`, `LazyColumn` control) are exactly what a lyrics view needs; M3 dynamic color optional |
| Audio | **Media3 ExoPlayer** (`androidx.media3:media3-exoplayer`, `media3-session`) | Playlist of per-ayah MP3s, gapless-capable, `MediaSessionService` gives lock-screen/notification controls + background playback for free |
| Audio caching | **Media3 `CacheDataSource` + `SimpleCache`** | Stream-once-then-offline without a download manager UI; optional explicit "download surah" later |
| Local data | **Room** (over a **prebuilt, bundled SQLite asset** via `createFromAsset`) | Text + WBW + timings ship in the APK; Room gives typed DAOs & paging |
| Preferences | **DataStore (Preferences)** | Last position, reciter, font size, theme |
| DI | **Hilt** | Small graph, but ViewModels + MediaSessionService wiring is cleaner with it |
| Navigation | **Navigation Compose** | 3 destinations; keep it trivial |
| Images/ornament | Vector drawables only | No image lib needed |
| Build | AGP 8.x, version catalog (`libs.versions.toml`), minSdk 26, targetSdk 35 | minSdk 26 keeps 95%+ devices, simplifies fonts/audio |
| Testing | JUnit5 + Turbine (flows), Compose UI tests, Robolectric for DAO | Sync engine gets dedicated unit tests with fake clocks |

**Deliberately not used:** no backend, no Firebase, no analytics SDK, no login.
The prebuilt DB (~15–25 MB with text + WBW + timings for 6 reciters) keeps the APK
lean; audio streams and caches on demand.

Reference codebases to learn from (both open source):
[quran_android](https://github.com/quran/quran_android) (quran.com's app — mature
audio/timing handling) and [quranwbw.com](https://quranwbw.com) (word-by-word UX).

---

## 5. Architecture & data model

Single-module (`app`) to start, clean package-by-feature layout; MVVM + unidirectional
data flow.

```
com.beautifulquran
├── data/
│   ├── db/          Room: entities, DAOs, prebuilt asset DB
│   ├── audio/       PlayerController (Media3 wrapper), PlaybackService
│   └── repo/        QuranRepository, SettingsRepository
├── domain/          HighlightEngine (pure Kotlin: position → word index)
├── ui/
│   ├── home/        Surah list
│   ├── reader/      Follow-along screen (the app)
│   ├── settings/    Reciter, font size, theme, attributions
│   └── theme/       Color, Type, Shape, motion specs
└── di/
```

### Database schema (prebuilt at build time)

```sql
surahs   (id, name_arabic, name_english, translation_en, revelation_place, ayah_count)
ayahs    (id, surah_id, ayah_number, text_uthmani, translation_en, juz, page)
words    (id, ayah_id, position, text_uthmani, translation_en, transliteration)
reciters (id, name, slug, bitrate, audio_base_url)
timings  (reciter_id, surah_id, ayah_number, word_position, start_ms, end_ms)
-- PK (reciter_id, surah_id, ayah_number, word_position); indexed for range scan
```

`HighlightEngine` is a pure function: `(ayahSegments, positionMs) -> wordIndex?`
(binary search) — trivially unit-testable against recorded fixtures.

### Data pipeline (build-time, in `tools/`)

A Python script (run manually, output committed or attached to releases):

1. Download QUL word-by-word dataset (Arabic QPC-Hafs word text + English gloss),
   translation DB, surah metadata.
2. Download quran-align JSON per reciter; normalize both segment formats to
   `(word_position, start_ms, end_ms)`; sanity-check word counts per ayah against
   the text (alignment data occasionally merges words — the script must validate
   and log mismatches; mismatched ayahs fall back to whole-ayah highlight).
3. Emit `quran.db` (SQLite) → `app/src/main/assets/`.

---

## 6. Screens & visual design

### Theme

- **Light "Paper"**: warm off-white `#FAF6EF`, ink `#1C1B18`, accent **deep teal-green
  `#0E5C4A`**, highlight gold `#C9A227` (active word pill uses a soft translucent
  gold/teal wash, never pure yellow).
- **Dark "Night prayer"**: near-black `#12140F`, warm parchment text `#E8E2D5`, same
  accents desaturated. True dark, OLED-friendly.
- Arabic: KFGQPC Uthmanic Hafs, base 26–34 sp, line height ~2.1× (Uthmani diacritics
  need air). English gloss: 11–12 sp, muted. Translation paragraph: 15–16 sp serif.
- Ornament: a single thin geometric divider + octagonal ayah-number medallion
  (vector); surah header card with subtle 8-pointed-star pattern at ~4% opacity.

### Screen 1 — Home (surah list)

Search-as-you-type over 114 surahs; each row: calligraphic surah name (Arabic),
English name + translation, ayah count, revelation place icon. A "Continue
listening" hero card resumes the last position. That's the whole screen.

### Screen 2 — Reader / Follow-along (the product)

- `LazyColumn` of **ayah blocks**. Each block: right-to-left `FlowRow` of **word
  units** — Arabic word on top, tiny English gloss beneath — then the full ayah
  translation as a paragraph, then a hairline divider with the ayah medallion.
- States per word: *upcoming* (default ink) → *active* (gold pill, accent text,
  150 ms ease) → *recited* (accent-tinted ink). Ayah-level: active ayah at full
  opacity, others slightly faded (like Apple Music lyrics).
- Tap a word → seek to its `start_ms`. Long-press ayah → repeat-ayah toggle /
  copy / bookmark.
- **Player bar** (docked bottom, blurred surface): play/pause, prev/next ayah,
  reciter avatar + name (tap to switch), speed (0.75–1.5×), repeat. Slides away
  on scroll-down, returns on scroll-up.
- "Follow" resume chip when the user has scrolled away from the active ayah.
- Reading-only mode: without playback it's simply a beautiful reader.

### Screen 3 — Settings (a sheet, not a screen)

Reciter picker (with 5-second audio preview), Arabic font size slider (live
preview), toggles: word-by-word gloss, transliteration, translation; theme;
**Attributions** page (required by CC-BY & translation licenses).

### Motion language

Everything ≤ 300 ms, standard M3 easing. The only "showy" motion is the word
highlight and the gentle lyric scroll — both functional. No splash animations.

---

## 7. Milestones

**M0 — Scaffold (small)**
Project setup, version catalog, theme tokens, fonts embedded, CI (GitHub Actions:
assemble + unit tests + ktlint).

**M1 — Data (the pipeline)**
`tools/build_db.py`; validated `quran.db` with text, WBW, translation, metadata,
timings for Alafasy + Husary; Room layer + DAO tests. *Exit: query any ayah with
words + timings in a unit test.*

**M2 — Reader (static)**
Home list → reader screen rendering Arabic + gloss + translation beautifully in
both themes; font-size setting. *Exit: it already looks like the screenshots we'd
put on the Play Store.*

**M3 — Audio + karaoke sync (the feature)**
Media3 playlist per surah, PlaybackService + notification, position ticker,
HighlightEngine, word highlight animation, auto-scroll, tap-to-seek.
*Exit: play Al-Fātiḥah and Al-Mulk end-to-end with sync that feels perfect.*

**M4 — Polish & resilience**
Remaining 4 reciters, audio caching, resume position, bookmark, repeat modes,
speed control, follow chip, error/offline states, attribution screen, accessibility
pass (TalkBack reads ayah + translation; highlight has non-color affordance).

**M5 — Release**
Icon, Play listing screenshots, R8 config, versioning, internal testing track.

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Segment word-count ≠ text word-count for some ayahs (known quirk of aligned data) | Pipeline validates every ayah; mismatches degrade gracefully to whole-ayah highlight; log & fix in data, not in app code |
| everyayah.com availability/throttling | Audio base URL per reciter is data-driven; QUL/quran.com CDN mirrors the same recitations; aggressive Media3 caching |
| Uthmani rendering issues on old OEM devices | We bundle the font (no reliance on system Arabic shaping quality beyond HarfBuzz, which Android ships); test matrix incl. API 26 |
| APK size creep | Timings are ints — SQLite compresses well; audio never bundled; target ≤ 30 MB |
| Translation licensing | Use only translations explicitly cleared for free redistribution (Clear Quran, Saheeh Intl.) with in-app attribution; keep license texts in repo |
| Battery during long playback | Ticker runs only while playing & screen on; notification playback doesn't tick UI |

---

## 9. Attribution (shipped in-app)

- Word timings: © cpfair/quran-align contributors, CC-BY 4.0; Tarteel QUL.
- Text & fonts: King Fahd Glorious Quran Printing Complex; Tanzil project.
- Translations: Dr. Mustafa Khattab (The Clear Quran); Saheeh International.
- Audio: the respective reciters & everyayah.com.

---

## 10. Sources (research)

- https://github.com/cpfair/quran-align (+ /releases — 12 reciter timing datasets, CC-BY 4.0)
- https://qul.tarteel.ai/ and https://qul.tarteel.ai/docs/with-segments (segments format & downloads)
- https://api-docs.quran.foundation/ (Content API v4, OAuth2 requirement, audio segments endpoint)
- https://everyayah.com (per-ayah audio hosting & URL scheme)
- https://tanzil.net/docs/quranic_fonts and https://github.com/nuqayah/qpc-fonts (font landscape)
- https://github.com/quran/quran_android, https://quranwbw.com (prior art)

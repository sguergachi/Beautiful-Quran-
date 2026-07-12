# Android rendering performance audit — 2026-07-12

## Scope and fidelity constraint

This audit traced the Android app from playback position and UI state through
Compose invalidation, layout, drawing, graphics layers, and the platform
renderer. The permitted change was deliberately narrow: fix only the highest
confidence hot path, and do not alter pixels, timing, easing, input behaviour,
audio behaviour, or animation feel.

## Pipeline findings

### Reader lyric path

The core lyric path is already well bounded:

1. `ReaderViewModel` polls the Media3 position at 33 ms only while relevant.
2. `HighlightEngine.PreparedTimings` performs an allocation-free binary search
   and O(1) repeat lookup.
3. `distinctUntilChanged` publishes word boundaries instead of poll ticks.
4. Per-ayah `derivedStateOf` confines a boundary change to the affected lazy
   item.
5. Ink, recess, repeat wash, chrome, and mark animation values are deferred to
   `graphicsLayer` or custom draw callbacks. They do not intentionally drive
   text re-layout each frame.
6. Arabic-only text remains one shaped run; its bloom overlays preserve Hafs
   shaping and reuse `TextLayoutResult` geometry.

No safe higher-impact change was found in this path. Replacing the 33 ms clock,
splitting shaping, reducing animation cadence, or simplifying bloom effects
would either be unmeasured speculation or risk sync and visual fidelity.

### Scroll and focus path

`LazyColumn` virtualization, stable item keys, derived layout read-outs, and the
single-writer focus controller keep scroll invalidation localized. Focus motion
does live geometry reads from its coroutine because unmeasured lazy items make
a fixed precomputed trajectory incorrect; this is intentional and preserves
the current continuous landing feel.

### Custom drawing and overlays

Edge fades use solid-paper gradient overlays and avoid full-list offscreen
compositing. Generated ornament geometry uses `drawWithCache`. Ink-reveal
offscreen composition is bounded to a short, explicit overlay animation where
blend modes require it. These are appropriate trade-offs for the visual design.

### Paper-stack navigation — top issue

`PaperStackApp` read `stackPosition.value` in the root composition and passed a
plain `Float` to all three sheet transforms. Since `stackPosition` changes every
animation frame, a drag or 460 ms settle invalidated the root paper-stack
composition every frame. The transform and shadow are render operations, so
composition was unnecessary work on the app's most global animation.

The fix passes a stable deferred read into `graphicsLayer` and
`drawWithContent`. The three booleans that genuinely affect composition use
`derivedStateOf`, so they notify composition only when their thresholds change:

- whether Back handles a partially turned cover;
- whether the cover playback control is visible;
- whether the concordance return control clears the reader player.

All transform equations, shadow equations, layer ordering, thresholds, gesture
physics, tween duration/easing, and page-turn audio sampling are unchanged.
The expected improvement is removal of frame-rate root recomposition during
page turns; the GPU still receives the same per-frame render-node properties and
draw commands.

## Profiling record

The minified release APK was built and installed on the repository's API 35
x86_64 AVD with KVM acceleration. The intended comparison was repeated 460 ms
paper-stack swipes with `dumpsys gfxinfo` reset before each run.

Usable frame numbers could not be collected on this host:

- emulator 36.6.11 booted successfully with KVM;
- both `swiftshader_indirect` and `swiftshader` rendered the app, then the
  emulator process exited with status 139 before the gesture sample completed;
- the host backend detected the NVIDIA GPU but could not create an EGL display
  in the headless session;
- the first `gfxinfo` output therefore contained `No process found`, not a
  valid measurement.

These failures are recorded so emulator-renderer instability is not presented
as an app regression and so no fabricated before/after frame metric enters the
performance history. Repeat the comparison on a stable hardware-backed emulator
or physical 90/120 Hz device before attaching numerical frame claims.

Suggested reproducible device run:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell dumpsys gfxinfo com.beautifulquran reset
# Perform repeated cover ↔ reader and reader ↔ settings turns.
adb shell dumpsys gfxinfo com.beautifulquran > gfxinfo.txt
```

Use the same device, refresh rate, thermal state, release APK, navigation path,
and gesture duration for both revisions. Capture a Perfetto system trace with
frame timeline and Compose tracing when available; `gfxinfo` alone can confirm
frame timing but cannot attribute Compose work.

## Verification

The change is source-equivalent for rendered values: every draw callback reads
the same `Animatable` value that composition previously copied into the
modifier. Unit tests and the minified release build are the required automated
gates; device visual comparison remains advisable because this environment's
renderer could not complete a stable trace.

## Deferred observations (not changed)

The audit intentionally made no second optimization. If later device traces
show remaining pressure, investigate these in measured order:

- allocation inside per-frame custom draw lambdas (especially temporary bloom
  lists and gradient construction);
- the number and retained memory of per-word graphics layers in gloss mode;
- long-ayah text shaping/prefetch on first exposure;
- a dedicated Macrobenchmark + Baseline Profile module for startup and scroll
  compilation.

Each can trade memory, shaping correctness, or animation appearance for speed,
so none should change without a representative device trace and pixel/motion
comparison.

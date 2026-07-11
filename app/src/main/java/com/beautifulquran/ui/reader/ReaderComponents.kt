package com.beautifulquran.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.beautifulquran.QuranApp
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.data.model.Ayah
import com.beautifulquran.data.model.Word
import com.beautifulquran.domain.EnglishTypography
import com.beautifulquran.ui.theme.ArabicTitleStyle
import com.beautifulquran.ui.theme.ArabicWordStyle
import com.beautifulquran.ui.theme.GildedFlourish
import com.beautifulquran.ui.theme.GildedRosette
import com.beautifulquran.ui.theme.HafsFontFamily
import com.beautifulquran.ui.theme.IslamicBackToOriginCapsule
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.ShapedWordBloom
import com.beautifulquran.ui.theme.TranslationFontFamily
import com.beautifulquran.ui.theme.gilded
import com.beautifulquran.ui.theme.letterFadeIn
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.shapedWordBloom
import com.beautifulquran.ui.theme.starAndCrossWeave
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlinx.coroutines.flow.StateFlow

private fun Int.toArabicIndic(): String =
    toString().map { '٠' + (it - '0') }.joinToString("")

private fun wordFadeAlpha(progress: Float): Float {
    val resting = InkEngine.State.Upcoming.inkAlpha()
    return resting + (InkEngine.State.Active.inkAlpha() - resting) * progress.coerceIn(0f, 1f)
}

private data class RenderedLineText(
    val text: AnnotatedString,
    val wordRanges: List<IntRange>,
    /** Inclusive range of the trailing ﴿N﴾ mark in [text], if present. */
    val markRange: IntRange,
)

/**
 * Ayah-number opacity shared by every reading mode: recessed verses sit at
 * upcoming ink; when the verse is in focus the mark fades up to full.
 */
@Composable
private fun rememberAyahMarkAlpha(focused: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (focused) 1f else InkEngine.State.Upcoming.inkAlpha(),
        animationSpec = tween(
            InkEngine.tuning.ayahMarkFadeMs,
            easing = FastOutSlowInEasing,
        ),
        label = "ayahMarkAlpha",
    )

private fun TextLayoutResult.wordIndexAt(
    tap: Offset,
    ranges: List<IntRange>,
    hitSlopPx: Float,
): Int =
    ranges.indexOfFirst { range ->
        range
            .map { offset -> getBoundingBox(offset) }
            .reduceOrNull { acc, rect -> acc.expandToInclude(rect) }
            ?.inflate(hitSlopPx)
            ?.contains(tap) == true
    }

private fun Rect.expandToInclude(other: Rect): Rect =
    Rect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

/**
 * Returns the ink alpha as [State] so callers can defer the read to the draw
 * phase (inside a graphicsLayer block): the fade animates every frame without
 * recomposing or re-laying-out a single word.
 *
 * Upcoming (including recessed non-active ayahs during playback) uses a short
 * tween so leaving a verse soft-dims; handoff onto a verse that was already
 * Upcoming does not flash full ink.
 */
@Composable
private fun animatedInkAlpha(state: InkEngine.State): State<Float> =
    animateFloatAsState(
        targetValue = state.inkAlpha(),
        // The active word's base ink is carried by the letter sweep, not this
        // value, so snap it straight to full: that way a word recited faster
        // than the tween (short words at speed) is already at full ink the
        // instant it flips to Recited, instead of dipping to a stale mid-fade
        // value and animating back up (a visible flicker on hand-off).
        animationSpec = if (state == InkEngine.State.Active) {
            snap<Float>()
        } else {
            tween(InkEngine.tuning.inkFadeMs, easing = FastOutSlowInEasing)
        },
        label = "inkAlpha",
    )

private const val ARABIC_ONLY_HAFS_FONT_MULTIPLIER = 1.0f

// The inline ayah end-marker (﴿N﴾) is set smaller than the Quranic words it
// closes, matching the standalone [AyahNumberMark] used in the other reading
// modes (20sp against the 30sp word body). Without this the marker inherits the
// full word size and reads as oversized in the Arabic-only view.
private const val AYAH_MARK_SIZE_RATIO = 20f / 30f

private data class RepeatWash(
    val progress: State<Float>,
    val alpha: State<Float>,
)

@Composable
private fun rememberRepeatWash(repeat: Boolean, sweepMs: Int?): RepeatWash {
    val progress = remember { Animatable(if (repeat) 0f else 1f) }
    val alpha = remember { Animatable(if (repeat) 1f else 0f) }
    // Restart only when the word enters or leaves the repeat chain. While a
    // repeated phrase advances, earlier words remain orange but stop being the
    // active word, so their sweepMs becomes null; that must not restart the
    // wash and briefly clear the held orange.
    LaunchedEffect(repeat) {
        // The repeat tint uses the same word-paced sweep as the initial ink
        // reveal, then dissolves back to normal ink slowly once the repeated
        // phrase releases.
        if (repeat) {
            alpha.snapTo(1f)
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                tween(sweepMs ?: InkEngine.tuning.repeatSweepMs, easing = InkEngine.sweepEasing),
            )
        } else {
            progress.snapTo(1f)
            alpha.animateTo(
                0f,
                tween(InkEngine.tuning.repeatFadeOutMs, easing = InkEngine.sweepEasing),
            )
        }
    }
    return RepeatWash(progress = progress.asState(), alpha = alpha.asState())
}

/**
 * One-shot search-hit flash: the same directional orange wash as
 * [rememberRepeatWash], run [SearchHitFlash.PULSES] times (wash in → dissolve
 * out → wash in → dissolve out). Independent of karaoke `ink.repeat` so a
 * real repeat chain is never cancelled or restarted.
 */
@Composable
private fun rememberSearchHitWash(active: Boolean): RepeatWash {
    val progress = remember { Animatable(1f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) {
            progress.snapTo(1f)
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        val sweepMs = InkEngine.tuning.repeatSweepMs
        val fadeMs = InkEngine.tuning.repeatFadeOutMs
        repeat(SearchHitFlash.PULSES) {
            alpha.snapTo(1f)
            progress.snapTo(0f)
            progress.animateTo(1f, tween(sweepMs, easing = InkEngine.sweepEasing))
            alpha.animateTo(0f, tween(fadeMs, easing = InkEngine.sweepEasing))
        }
    }
    return RepeatWash(progress = progress.asState(), alpha = alpha.asState())
}

private fun Modifier.repeatInkLayer(
    wash: RepeatWash,
    rtl: Boolean,
): Modifier =
    graphicsLayer { alpha = wash.alpha.value }
        .letterFadeIn(
            progress = { wash.progress.value },
            rtl = rtl,
            restingAlpha = 0f,
            feather = InkEngine.tuning.washFeather,
        )

/**
 * Drives the letter-fade sweep for the active word: restarts at 0 each time
 * the word lights up and runs for [sweepMs] — the time the word stays lit
 * (karaoke hold until the next word) — so the last letter finishes inking
 * exactly as the voice moves on.
 *
 * When [startRevealed] is true the word begins the sweep already fully inked
 * (progress 1) instead of snapping to the faint "upcoming" floor. That is the
 * case for a word that lights up directly from a full-ink state — after a
 * seek/jump or repeat re-entry — where there is no preceding "upcoming" dim
 * to breathe out of. Snapping such a word to faint made it flash
 * full → faint → sweep; holding it revealed simply skips the reveal for that
 * one already-read word and removes the flicker.
 *
 * [sweepMs] is captured at Active entry only: mid-word retunes (speed, etc.)
 * must not cancel and restart the wash — that is itself a flicker.
 */
@Composable
private fun rememberLetterSweep(
    active: Boolean,
    sweepMs: Int?,
    startRevealed: Boolean = false,
): State<Float> {
    val runSweep = active && sweepMs != null && !startRevealed
    val sweep = remember(active) { Animatable(if (runSweep) 0f else 1f) }
    // Key on active + startRevealed only — not sweepMs — so a duration tweak
    // while the word is lit cannot snap progress back to 0.
    LaunchedEffect(active, startRevealed) {
        val ms = sweepMs
        if (active && ms != null && !startRevealed) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(ms, easing = InkEngine.sweepEasing))
        } else {
            sweep.snapTo(1f)
        }
    }
    return sweep.asState()
}

/**
 * Compose wrapper over [InkEngine.startRevealed] (the rule and its rationale
 * live there): tracks the word's previous state and captures the decision the
 * moment the word activates, so it stays stable for the whole time the word
 * is lit and is recomputed fresh on the next activation.
 */
@Composable
private fun rememberStartRevealed(state: InkEngine.State): Boolean {
    val active = state == InkEngine.State.Active
    val previousState = remember { mutableStateOf(state) }
    val startRevealed = InkEngine.startRevealed(previous = previousState.value, current = state)
    SideEffect { previousState.value = state }
    return remember(active) { startRevealed }
}

/** Comfortable reading band the active word is kept inside while follow mode
 * scrolls the sheet (see [wordUnitBehavior]). */
private val ActiveWordTopMargin = 144.dp
private val ActiveWordBottomMargin = 132.dp

/**
 * Bundles the three animations every word unit runs: the lyric ink fade
 * between visual states, the letter sweep of the active word, and the orange
 * wash of a repeated word. All values are [State]s read in the draw phase.
 */
private class WordHighlight(
    val isActive: Boolean,
    private val repeat: Boolean,
    private val lyricInk: State<Float>,
    private val sweep: State<Float>,
    val repeatWash: RepeatWash,
) {
    /** Modifier for the base text layer: letters sweep in while the word is
     * active, rest at the lyric ink otherwise. While the word is repeating,
     * the base layer stays untouched — the orange overlay carries the motion. */
    fun baseLayer(rtl: Boolean): Modifier = when {
        repeat -> Modifier
        isActive -> Modifier.letterFadeIn(
            progress = { sweep.value },
            rtl = rtl,
            restingAlpha = InkEngine.State.Upcoming.inkAlpha(),
            feather = InkEngine.tuning.washFeather,
        )
        else -> Modifier.graphicsLayer { alpha = lyricInk.value }
    }

    /** Whether the orange repeat overlay still has any ink to show. */
    val showRepeatLayer: Boolean get() = repeatWash.alpha.value > 0f

    /** Draw-phase alpha for secondary lines (gloss, transliteration): they
     * fade with the word's sweep but never letter-reveal. */
    fun secondaryAlpha(): Float =
        if (isActive && !repeat) wordFadeAlpha(sweep.value) else lyricInk.value
}

@Composable
private fun rememberWordHighlight(
    ink: InkEngine.Word,
    sweepMs: Int?,
): WordHighlight {
    val isActive = ink.state == InkEngine.State.Active
    return WordHighlight(
        isActive = isActive,
        repeat = ink.repeat,
        lyricInk = animatedInkAlpha(ink.state),
        sweep = rememberLetterSweep(
            active = isActive,
            sweepMs = sweepMs,
            startRevealed = rememberStartRevealed(ink.state),
        ),
        repeatWash = rememberRepeatWash(ink.repeat, sweepMs.takeIf { isActive }),
    )
}

/**
 * Shared word-unit chrome: keeps the word inside the comfortable reading band
 * while it is active and follow mode is on, and makes it tappable (quietly)
 * when [onClick] is provided. Apply before the unit's own padding.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.wordUnitBehavior(
    active: Boolean,
    keepInView: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val topMarginPx = with(density) { ActiveWordTopMargin.toPx() }
    val bottomMarginPx = with(density) { ActiveWordBottomMargin.toPx() }
    var wordSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(active, keepInView, wordSize) {
        if (active && keepInView && wordSize != IntSize.Zero) {
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = -topMarginPx,
                    right = wordSize.width.toFloat(),
                    bottom = wordSize.height + bottomMarginPx,
                ),
            )
        }
    }
    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onSizeChanged { wordSize = it }
        .let { m ->
            when {
                onClick != null && onLongClick != null ->
                    m.quietClickable(onLongClick = onLongClick, onClick = onClick)
                onClick != null -> m.quietClickable(onClick = onClick)
                onLongClick != null -> m.quietClickable(onLongClick = onLongClick, onClick = {})
                else -> m
            }
        }
}

/** The two-layer karaoke text every word unit renders: the base ink, plus an
 * orange overlay that sweeps in while the word belongs to a repeat chain and
 * dissolves back out once the chain releases. An optional [searchHitWash]
 * reuses that same overlay (matchParentSize + [repeatInkLayer]) for the home
 * search-hit flash — never a second measured Text that would shift layout. */
@Composable
private fun HighlightLayeredText(
    text: String,
    highlight: WordHighlight,
    rtl: Boolean,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    searchHitWash: RepeatWash? = null,
) {
    val repeatInk = LocalQuranAccents.current.repeatInk
    // Prefer a live repeat chain; otherwise the one-shot search-hit wash.
    val orangeWash = when {
        highlight.showRepeatLayer -> highlight.repeatWash
        searchHitWash != null && searchHitWash.alpha.value > 0f -> searchHitWash
        else -> null
    }
    Box(modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = highlight.baseLayer(rtl),
        )
        if (orangeWash != null) {
            Text(
                text = text,
                style = style,
                color = repeatInk,
                // matchParentSize: overlay must not contribute to Box measure —
                // composing a second Text was shifting FlowRow words.
                modifier = Modifier
                    .matchParentSize()
                    .repeatInkLayer(orangeWash, rtl),
            )
        }
    }
}

@Composable
fun WordUnit(
    word: Word,
    ink: InkEngine.Word,
    fontScale: Float,
    sweepMs: Int?,
    showGloss: Boolean,
    showTransliteration: Boolean,
    searchHit: Boolean,
    keepInView: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    /** When true, run the orange search-hit wash on Arabic + gloss. */
    showFlash: Boolean = false,
) {
    val highlight = rememberWordHighlight(ink, sweepMs)
    val searchHitWash = rememberSearchHitWash(showFlash)
    val repeatInk = LocalQuranAccents.current.repeatInk
    val glossWeight = if (searchHit) FontWeight.Bold else null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .wordUnitBehavior(highlight.isActive, keepInView, onClick, onLongClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        HighlightLayeredText(
            text = word.arabic,
            highlight = highlight,
            rtl = true,
            color = MaterialTheme.colorScheme.onBackground,
            style = ArabicWordStyle.copy(fontSize = ArabicWordStyle.fontSize * fontScale),
            searchHitWash = searchHitWash,
        )
        if (showGloss) {
            Box {
                Text(
                    text = word.translation,
                    fontSize = 12.sp * fontScale,
                    lineHeight = 15.sp * fontScale,
                    fontWeight = glossWeight,
                    color = if (searchHit) {
                        LocalQuranAccents.current.gold
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = highlight.secondaryAlpha() },
                )
                if (searchHitWash.alpha.value > 0f) {
                    Text(
                        text = word.translation,
                        fontSize = 12.sp * fontScale,
                        lineHeight = 15.sp * fontScale,
                        fontWeight = glossWeight,
                        color = repeatInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .matchParentSize()
                            .repeatInkLayer(searchHitWash, rtl = false),
                    )
                }
            }
        }
        if (showTransliteration) {
            Text(
                text = word.transliteration,
                fontSize = 11.sp * fontScale,
                lineHeight = 14.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = highlight.secondaryAlpha() },
            )
        }
    }
}

/**
 * One Arabic word in the connected (word-for-word gloss disabled) flow. Same
 * recitation letter-fade as [WordUnit], but with no gloss column and tight
 * spacing so the words read as continuous Quranic script rather than boxed
 * tokens.
 */
@Composable
fun ConnectedArabicWordUnit(
    word: Word,
    ink: InkEngine.Word,
    fontScale: Float,
    sweepMs: Int?,
    keepInView: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
) {
    val highlight = rememberWordHighlight(ink, sweepMs)
    HighlightLayeredText(
        text = word.arabic,
        highlight = highlight,
        rtl = true,
        color = MaterialTheme.colorScheme.onBackground,
        style = ArabicWordStyle.copy(fontSize = ArabicWordStyle.fontSize * fontScale),
        modifier = Modifier
            .wordUnitBehavior(highlight.isActive, keepInView, onClick, onLongClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    )
}

/**
 * Ink colours for the Arabic-only shaped ayah. Base spans stay full ink —
 * upcoming dim, first-pass bloom, and orange repeat are all draw-phase
 * overlays so word/ayah transitions never reshape the run or flash span
 * colours.
 */
private class WordInkPalette(
    private val fullInk: Color,
    private val paper: Color,
    private val repeatInk: Color,
) {
    val fullInkColor: Color get() = fullInk
    val paperColor: Color get() = paper
    val repeatInkColor: Color get() = repeatInk
}

@Composable
private fun rememberWordInkPalette(): WordInkPalette {
    val fullInk = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background
    val repeatInk = LocalQuranAccents.current.repeatInk
    return remember(fullInk, paper, repeatInk) {
        WordInkPalette(fullInk = fullInk, paper = paper, repeatInk = repeatInk)
    }
}

/** One letter sweep per word, running only for the active one. The returned
 * [State]s must be read in the draw phase only — never while building the
 * annotated string — so the sweep does not reshape the ayah every frame. */
@Composable
private fun rememberLetterSweeps(
    inks: List<InkEngine.Word>,
    activeSweepMs: Int?,
): List<State<Float>> = inks.map { ink ->
    val active = ink.state == InkEngine.State.Active
    rememberLetterSweep(
        active = active,
        sweepMs = activeSweepMs.takeIf { active },
        startRevealed = rememberStartRevealed(ink.state),
    )
}

/** Orange wash per word in the repeat chain — same timing as gloss mode. */
@Composable
private fun rememberRepeatWashes(
    inks: List<InkEngine.Word>,
    activeSweepMs: Int?,
): List<RepeatWash> = inks.map { ink ->
    rememberRepeatWash(
        repeat = ink.repeat,
        sweepMs = activeSweepMs.takeIf { ink.state == InkEngine.State.Active },
    )
}

/**
 * English-only lyric set as one continuous prose line. Word ranges retain
 * independent karaoke ink and hit targets without turning natural spaces into
 * layout gaps, so the paragraph keeps a single baseline and browser-like wrap.
 */
@Composable
private fun ResponsiveEnglishAyah(
    ayah: Ayah,
    inks: List<InkEngine.Word>,
    markAlpha: () -> Float,
    fontScale: Float,
    activeSweepMs: Int?,
    searchQuery: String?,
    flashWordPosition: Int?,
    keepActiveWordInView: Boolean,
    onAyahClick: () -> Unit,
    onWordClick: ((Word) -> Unit)?,
    onWordLongClick: ((Word) -> Unit)?,
) {
    val palette = rememberWordInkPalette()
    val gold = LocalQuranAccents.current.gold
    val sweeps = rememberLetterSweeps(inks, activeSweepMs)
    val repeatWashes = rememberRepeatWashes(inks, activeSweepMs)
    val searchHitWash = rememberSearchHitWash(flashWordPosition != null)
    val activeIndex = inks.indexOfFirst { it.state == InkEngine.State.Active }
    val activeIsRepeat = activeIndex >= 0 && inks[activeIndex].repeat
    val upcomingCover = 1f - InkEngine.State.Upcoming.inkAlpha()
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = TranslationFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp * fontScale,
        lineHeight = 1.5.em,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Start,
    )
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hitSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val punctuatedGlosses = remember(ayah) {
        EnglishTypography.punctuate(ayah.words.map { it.translation })
    }

    val rendered = remember(ayah, palette.fullInkColor, gold, searchQuery, fontScale) {
        val ranges = ArrayList<IntRange>(ayah.words.size)
        var markRange = 0..-1
        val text = buildAnnotatedString {
            ayah.words.forEachIndexed { index, word ->
                val start = length
                withStyle(
                    SpanStyle(
                        color = if (
                            searchQuery != null &&
                            word.translation.contains(searchQuery, ignoreCase = true)
                        ) {
                            gold
                        } else {
                            palette.fullInkColor
                        },
                    ),
                ) {
                    append(punctuatedGlosses[index])
                }
                ranges += start until length
                append(" ")
            }
            val markStart = length
            withStyle(
                SpanStyle(
                    color = gold,
                    fontFamily = HafsFontFamily,
                    // 17/22 keeps the ornament proportional. Sharing the prose
                    // baseline avoids a font-metric paint lift on Android.
                    fontSize = 17.sp * fontScale,
                ),
            ) {
                append("﴿")
                append(ayah.number.toString())
                append("﴾")
            }
            markRange = markStart until length
        }
        RenderedLineText(text = text, wordRanges = ranges, markRange = markRange)
    }

    LaunchedEffect(keepActiveWordInView, activeIndex, layoutResult) {
        if (!keepActiveWordInView || activeIndex < 0) return@LaunchedEffect
        val layout = layoutResult ?: return@LaunchedEffect
        val range = rendered.wordRanges.getOrNull(activeIndex) ?: return@LaunchedEffect
        if (range.isEmpty()) return@LaunchedEffect
        val first = layout.getBoundingBox(range.first)
        val last = layout.getBoundingBox(range.last)
        bringIntoViewRequester.bringIntoView(
            Rect(
                left = minOf(first.left, last.left),
                top = minOf(first.top, last.top),
                right = maxOf(first.right, last.right),
                bottom = maxOf(first.bottom, last.bottom),
            ),
        )
    }

    Text(
        text = rendered.text,
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .shapedWordBloom(
                blooms = {
                    val blooms = ArrayList<ShapedWordBloom>(inks.size + 2)
                    inks.forEachIndexed { index, ink ->
                        if (ink.state == InkEngine.State.Active) return@forEachIndexed
                        val coverAlpha = if (ink.state == InkEngine.State.Upcoming) upcomingCover else 0f
                        if (coverAlpha <= 0f) return@forEachIndexed
                        blooms += ShapedWordBloom.UpcomingDim(
                            range = rendered.wordRanges[index],
                            paper = palette.paperColor,
                            coverAlpha = coverAlpha,
                        )
                    }
                    val markCover = (1f - markAlpha()).coerceIn(0f, 1f)
                    if (markCover > 0f && !rendered.markRange.isEmpty()) {
                        blooms += ShapedWordBloom.UpcomingDim(
                            range = rendered.markRange,
                            paper = palette.paperColor,
                            coverAlpha = markCover,
                        )
                    }
                    if (activeIndex >= 0 && !activeIsRepeat) {
                        blooms += ShapedWordBloom.InkReveal(
                            range = rendered.wordRanges[activeIndex],
                            progress = sweeps[activeIndex].value,
                            paper = palette.paperColor,
                            restingAlpha = InkEngine.State.Upcoming.inkAlpha(),
                        )
                    }
                    repeatWashes.forEachIndexed { index, wash ->
                        if (wash.alpha.value <= 0f) return@forEachIndexed
                        blooms += ShapedWordBloom.ColorReveal(
                            range = rendered.wordRanges[index],
                            progress = wash.progress.value,
                            color = palette.repeatInkColor,
                            restingAlpha = 0f,
                            layerAlpha = wash.alpha.value,
                        )
                    }
                    val flashIndex = ayah.words.indexOfFirst { it.position == flashWordPosition }
                    if (flashIndex >= 0 && searchHitWash.alpha.value > 0f) {
                        blooms += ShapedWordBloom.ColorReveal(
                            range = rendered.wordRanges[flashIndex],
                            progress = searchHitWash.progress.value,
                            color = palette.repeatInkColor,
                            restingAlpha = 0f,
                            layerAlpha = searchHitWash.alpha.value,
                        )
                    }
                    blooms
                },
                layout = { layoutResult },
                rtl = false,
                feather = InkEngine.tuning.washFeather,
            )
            .then(
                if (onWordClick == null) {
                    Modifier.quietClickable(onClick = onAyahClick)
                } else {
                    Modifier.wordTapTarget(
                        words = ayah.words,
                        ranges = rendered.wordRanges,
                        layoutResult = layoutResult,
                        hitSlopPx = hitSlopPx,
                        onWordClick = onWordClick,
                        onWordLongClick = onWordLongClick,
                        onMiss = onAyahClick,
                    )
                },
            ),
        onTextLayout = { layoutResult = it },
    )
}

/**
 * Resolves taps on an annotated ayah line to the word whose glyph bounds
 * (inflated by [hitSlopPx]) contain the tap; taps that miss every word go to
 * [onMiss] (null = ignored).
 */
private fun Modifier.wordTapTarget(
    words: List<Word>,
    ranges: List<IntRange>,
    layoutResult: TextLayoutResult?,
    hitSlopPx: Float,
    onWordClick: (Word) -> Unit,
    onWordLongClick: ((Word) -> Unit)? = null,
    onMiss: (() -> Unit)? = null,
): Modifier = pointerInput(ranges, words, layoutResult, onWordLongClick) {
    detectTapGestures(
        onTap = { tap ->
            val wordIndex = layoutResult?.wordIndexAt(tap, ranges, hitSlopPx) ?: -1
            if (wordIndex >= 0) onWordClick(words[wordIndex]) else onMiss?.invoke()
        },
        onLongPress = if (onWordLongClick == null) null else { pos ->
            val wordIndex = layoutResult?.wordIndexAt(pos, ranges, hitSlopPx) ?: -1
            if (wordIndex >= 0) onWordLongClick(words[wordIndex])
        },
    )
}

@Composable
private fun ResponsiveHafsAyah(
    ayah: Ayah,
    inks: List<InkEngine.Word>,
    /** True while another ayah is the lyric line — cover every word with the
     * same upcoming paper so unread ink does not change when this ayah
     * becomes active. */
    dimmed: Boolean,
    /** 0..1 opacity for the trailing ﴿N﴾ mark — fades to full when focused. */
    markAlpha: () -> Float,
    fontSize: TextUnit,
    activeSweepMs: Int?,
    flashWordPosition: Int? = null,
    onAyahClick: () -> Unit,
    onWordClick: ((Word) -> Unit)?,
    onWordLongClick: ((Word) -> Unit)? = null,
) {
    val palette = rememberWordInkPalette()
    val ayahMarkInk = LocalQuranAccents.current.gold
    val sweeps = rememberLetterSweeps(inks, activeSweepMs)
    val repeatWashes = rememberRepeatWashes(inks, activeSweepMs)
    val searchHitWash = rememberSearchHitWash(flashWordPosition != null)
    val activeIndex = inks.indexOfFirst { it.state == InkEngine.State.Active }
    val activeIsRepeat = activeIndex >= 0 && inks[activeIndex].repeat
    val upcomingCover = 1f - InkEngine.State.Upcoming.inkAlpha()
    // While recessed, the same upcoming paper cover sits on every word.
    // Tween both directions so play-start and pause breathe; Upcoming words
    // keep a floor of [upcomingCover] so ayah handoff never flashes full ink.
    val recessCover = animateFloatAsState(
        targetValue = if (dimmed) upcomingCover else 0f,
        animationSpec = tween(InkEngine.tuning.recessMs, easing = FastOutSlowInEasing),
        label = "recessCover",
    )
    val style = ArabicWordStyle.merge(
        TextStyle(
            fontFamily = HafsFontFamily,
            fontSize = fontSize,
            lineHeight = 1.95.em,
            textAlign = TextAlign.Center,
        ),
    )
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hitSlopPx = with(LocalDensity.current) { 8.dp.toPx() }

    // Full-ink spans only — never bake upcoming/active into the annotated
    // string. Dim, bloom, and orange are draw-phase overlays, so word and
    // ayah boundaries do not reshape or flash the run.
    val rendered = remember(ayah, palette.fullInkColor, ayahMarkInk, fontSize) {
        val ranges = ArrayList<IntRange>(ayah.words.size)
        var markRange = 0..-1
        val text = buildAnnotatedString {
            ayah.words.forEach { word ->
                val start = length
                // One contiguous colour span per word keeps Uthmanic Hafs
                // joining/ligatures intact. Per-glyph spans split shaping runs
                // and caused a visible font flip (#133).
                withStyle(SpanStyle(color = palette.fullInkColor)) {
                    append(word.arabic)
                }
                ranges += start until length
                append(" ")
            }
            val markStart = length
            withStyle(
                SpanStyle(
                    color = ayahMarkInk,
                    fontSize = fontSize * AYAH_MARK_SIZE_RATIO,
                ),
            ) {
                append("﴿")
                append(ayah.number.toArabicIndic())
                append("﴾")
            }
            markRange = markStart until length
        }
        RenderedLineText(text = text, wordRanges = ranges, markRange = markRange)
    }
    Text(
        text = rendered.text,
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .shapedWordBloom(
                blooms = {
                    val recess = recessCover.value
                    val blooms = ArrayList<ShapedWordBloom>(inks.size + 2)
                    // Faint cover while recessed (all words) or Upcoming while
                    // active. Same cover strength — ayah handoff does not
                    // change unread ink; only the active word starts its bloom.
                    inks.forEachIndexed { index, ink ->
                        val coverAlpha = when {
                            // Active word is revealed by InkReveal, not recess.
                            ink.state == InkEngine.State.Active -> 0f
                            // Upcoming keeps a dim floor during recess lift so
                            // handoff never flashes full ink.
                            ink.state == InkEngine.State.Upcoming ->
                                maxOf(recess, upcomingCover)
                            recess > 0f -> recess
                            else -> 0f
                        }
                        if (coverAlpha <= 0f) return@forEachIndexed
                        val range = rendered.wordRanges.getOrNull(index)
                            ?: return@forEachIndexed
                        blooms += ShapedWordBloom.UpcomingDim(
                            range = range,
                            paper = palette.paperColor,
                            coverAlpha = coverAlpha,
                        )
                    }
                    // ﴿N﴾ mark: paper cover of (1 − markAlpha) so it fades up
                    // to full gold with the shared ayah-mark animation.
                    val markCover = (1f - markAlpha()).coerceIn(0f, 1f)
                    if (markCover > 0f && !rendered.markRange.isEmpty()) {
                        blooms += ShapedWordBloom.UpcomingDim(
                            range = rendered.markRange,
                            paper = palette.paperColor,
                            coverAlpha = markCover,
                        )
                    }
                    // First-pass ink reveal: paper cover over the shaped full-ink
                    // glyphs, pulled back on the letterFadeIn curve. Skipped
                    // while repeating — orange carries the motion, same as
                    // WordUnit. At progress 0 this matches UpcomingDim, so the
                    // first word hands off without a flash.
                    if (activeIndex >= 0 && !activeIsRepeat) {
                        val range = rendered.wordRanges.getOrNull(activeIndex)
                        if (range != null) {
                            blooms += ShapedWordBloom.InkReveal(
                                range = range,
                                progress = sweeps[activeIndex].value,
                                paper = palette.paperColor,
                                restingAlpha = InkEngine.State.Upcoming.inkAlpha(),
                            )
                        }
                    }
                    // Orange directional bloom: SrcIn-tint the shaped glyphs,
                    // then DstIn-wash — same motion as gloss mode's orange
                    // overlay, without re-shaping or painting neighbour rects.
                    repeatWashes.forEachIndexed { index, wash ->
                        if (wash.alpha.value <= 0f) return@forEachIndexed
                        val range = rendered.wordRanges.getOrNull(index)
                            ?: return@forEachIndexed
                        blooms += ShapedWordBloom.ColorReveal(
                            range = range,
                            progress = wash.progress.value,
                            color = palette.repeatInkColor,
                            restingAlpha = 0f,
                            layerAlpha = wash.alpha.value,
                        )
                    }
                    // Home search-hit flash: same ColorReveal wash as the
                    // orange repeat bloom — directional mask + dissolve × 2.
                    val flashPos = flashWordPosition
                    if (flashPos != null && searchHitWash.alpha.value > 0f) {
                        val flashIndex = ayah.words.indexOfFirst { it.position == flashPos }
                        val range = rendered.wordRanges.getOrNull(flashIndex)
                        if (range != null) {
                            blooms += ShapedWordBloom.ColorReveal(
                                range = range,
                                progress = searchHitWash.progress.value,
                                color = palette.repeatInkColor,
                                restingAlpha = 0f,
                                layerAlpha = searchHitWash.alpha.value,
                            )
                        }
                    }
                    blooms
                },
                layout = { layoutResult },
                rtl = true,
                feather = InkEngine.tuning.washFeather,
            )
            .then(
                if (onWordClick == null) {
                    Modifier.quietClickable(onClick = onAyahClick)
                } else {
                    Modifier.wordTapTarget(
                        words = ayah.words,
                        ranges = rendered.wordRanges,
                        layoutResult = layoutResult,
                        hitSlopPx = hitSlopPx,
                        onWordClick = onWordClick,
                        onWordLongClick = onWordLongClick,
                        onMiss = onAyahClick,
                    )
                },
            ),
        onTextLayout = { layoutResult = it },
    )
}

/** Marks every occurrence of [query] in [text] with a soft gold wash. */
private fun highlightMatches(text: String, query: String?, mark: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (query.isNullOrEmpty()) return@buildAnnotatedString
        var i = text.indexOf(query, ignoreCase = true)
        while (i >= 0) {
            addStyle(SpanStyle(background = mark), i, i + query.length)
            i = text.indexOf(query, i + query.length, ignoreCase = true)
        }
    }

/** Quiet typographic ayah marker: ornate brackets leafed in gradient gold. */
@Composable
fun AyahNumberMark(
    number: Int,
    fontScale: Float,
    verticalNudge: Dp = 0.dp,
    useArabicIndicDigits: Boolean = true,
) {
    val accents = LocalQuranAccents.current
    Text(
        text = "﴿${if (useArabicIndicDigits) number.toArabicIndic() else number.toString()}﴾",
        fontFamily = HafsFontFamily,
        fontSize = 20.sp * fontScale,
        color = accents.gold,
        modifier = Modifier
            .offset(y = verticalNudge)
            .gilded(
                bright = accents.goldBright.copy(alpha = 0.9f),
                deep = accents.goldDeep.copy(alpha = 0.9f),
            ),
    )
}

@Composable
private fun ArabicAyahNumberUnit(
    number: Int,
    fontScale: Float,
) {
    val density = LocalDensity.current
    val arabicLineHeight = with(density) {
        (ArabicWordStyle.fontSize * fontScale * 1.9f).toDp()
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .requiredHeight(arabicLineHeight),
        contentAlignment = Alignment.Center,
    ) {
        AyahNumberMark(number, fontScale)
    }
}

/**
 * One ayah on the sheet. In Arabic mode the words flow right-to-left with the
 * English gloss beneath each word; in English mode the gloss itself becomes
 * the lyric line, flowing left-to-right. Either way the letters fade in and
 * out with the recitation — no blocks, no backgrounds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AyahBlock(
    ayah: Ayah,
    readingMode: ReadingMode,
    activeWord: ActiveWord?,
    playbackSpeed: Float,
    isActiveAyah: Boolean,
    dimmed: Boolean,
    obscuredBySelector: Boolean,
    fontScale: Float,
    showGloss: Boolean,
    showTransliteration: Boolean,
    showTranslation: Boolean,
    searchQuery: String? = null,
    /** 1-based word to orange-flash (home search hit); null = no flash. */
    flashWordPosition: Int? = null,
    keepActiveWordInView: Boolean = false,
    /** Bookmark ribbon lives in this block's outer margin (opposite the
     * ayah selector). Null hides the ribbon entirely. */
    bookmarkSide: AyahSelectorSide? = null,
    bookmarked: Boolean = false,
    bookmarkFocused: Boolean = false,
    bookmarkChromeAlpha: () -> Float = { 1f },
    bookmarkInteractive: Boolean = true,
    onToggleBookmark: (() -> Boolean)? = null,
    onWordClick: ((Word) -> Unit)?,
    onWordLongClick: ((Word) -> Unit)? = null,
    onAyahClick: () -> Unit,
) {
    fun hits(word: Word) =
        searchQuery != null && word.translation.contains(searchQuery, ignoreCase = true)
    // Non-active ayahs recede while another is being recited. Dim is applied
    // at the word level (Upcoming ink / paper cover) in every mode so ayah
    // handoff does not brighten the verse — block alpha stays at 1 except
    // when the selector obscures the page. Soft tween when receding; snap
    // when becoming the lyric line.
    val blockAlpha = animateFloatAsState(
        targetValue = if (obscuredBySelector) 0.07f else 1f,
        animationSpec = when {
            obscuredBySelector -> tween(600)
            else -> snap()
        },
        label = "ayahAlpha",
    )

    // The letter fade paces itself to how long the reciter dwells on the
    // word, corrected for the chosen playback speed.
    val sweepMs = InkEngine.sweepMs(activeWord, playbackSpeed)

    // Each word's ink behaviour, derived once for the whole ayah. All the
    // policy (upcoming/active/recited/high-water, repeat chain) lives in
    // InkEngine; the render branches below only draw what it decided.
    val inks = ayah.words.map { word ->
        InkEngine.word(
            position = word.position,
            activeWord = activeWord,
            isActiveAyah = isActiveAyah,
            dimmed = dimmed,
        )
    }

    // Shared across gloss, English, and Arabic-only: mark sits at upcoming
    // ink while recessed, then fades up to full when this verse is in focus.
    val ayahMarkAlpha = rememberAyahMarkAlpha(focused = !dimmed)
    // Translation recess matches word ink. Animate a 0..1 multiplier read
    // only in graphicsLayer — never in composition (docs/PERFORMANCE.md).
    val translationRecess = animateFloatAsState(
        targetValue = if (dimmed) InkEngine.State.Upcoming.inkAlpha() else 1f,
        animationSpec = tween(InkEngine.tuning.inkFadeMs, easing = FastOutSlowInEasing),
        label = "translationRecess",
    )

    // The ribbon is part of the verse block itself — same Box, same height —
    // so it never "follows" from a floating overlay. Text keeps the existing
    // horizontal inset; the ribbon sits in the outer margin opposite the
    // ayah selector.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = blockAlpha.value },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            if (readingMode == ReadingMode.ENGLISH_ONLY) {
                ResponsiveEnglishAyah(
                    ayah = ayah,
                    inks = inks,
                    markAlpha = { ayahMarkAlpha.value },
                    fontScale = fontScale,
                    activeSweepMs = sweepMs,
                    searchQuery = searchQuery,
                    flashWordPosition = flashWordPosition,
                    keepActiveWordInView = keepActiveWordInView,
                    onAyahClick = onAyahClick,
                    onWordClick = onWordClick,
                    onWordLongClick = onWordLongClick,
                )
            } else if (showGloss) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(if (showGloss) 12.dp else 4.dp),
                    ) {
                        ayah.words.forEachIndexed { index, word ->
                            val ink = inks[index]
                            val isActiveWord = ink.state == InkEngine.State.Active
                            val flashing = flashWordPosition == word.position
                            WordUnit(
                                word = word,
                                ink = ink,
                                fontScale = fontScale,
                                sweepMs = sweepMs.takeIf { isActiveWord },
                                showGloss = showGloss,
                                showTransliteration = showTransliteration,
                                searchHit = hits(word),
                                keepInView = keepActiveWordInView && isActiveWord,
                                onClick = onWordClick?.let { handler -> { handler(word) } },
                                onLongClick = onWordLongClick?.let { handler -> { handler(word) } },
                                showFlash = flashing,
                            )
                        }
                        Box(
                            modifier = Modifier.graphicsLayer { alpha = ayahMarkAlpha.value },
                        ) {
                            ArabicAyahNumberUnit(ayah.number, fontScale)
                        }
                    }
                }
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ResponsiveHafsAyah(
                        ayah = ayah,
                        inks = inks,
                        // Same faint cover while another ayah is playing, so
                        // landing on this verse does not change unread ink.
                        dimmed = dimmed,
                        markAlpha = { ayahMarkAlpha.value },
                        fontSize = ArabicWordStyle.fontSize * fontScale * ARABIC_ONLY_HAFS_FONT_MULTIPLIER,
                        activeSweepMs = sweepMs,
                        flashWordPosition = flashWordPosition,
                        onAyahClick = onAyahClick,
                        onWordClick = onWordClick?.let { handler -> { word -> handler(word) } },
                        onWordLongClick = onWordLongClick?.let { handler -> { word -> handler(word) } },
                    )
                }
            }
            if (showTranslation && readingMode == ReadingMode.ARABIC_ENGLISH) {
                Spacer(Modifier.height(12.dp))
                // Block alpha stays 1 while recessed (word-level dim); the
                // translation still needs to recede with the verse.
                Text(
                    text = highlightMatches(
                        text = ayah.translation,
                        query = searchQuery,
                        mark = LocalQuranAccents.current.gold.copy(alpha = 0.28f),
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = TranslationFontFamily,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * (0.9f + 0.1f * fontScale),
                        lineHeight = 26.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = translationRecess.value }
                        .quietClickable(onClick = onAyahClick),
                )
            }
            // Whitespace is the divider.
            Spacer(Modifier.height(if (readingMode == ReadingMode.ENGLISH_ONLY) 18.dp else 26.dp))
        }

        if (bookmarkSide != null && onToggleBookmark != null) {
            // matchParentSize (not fillMaxHeight): the ayah Box is wrap-content,
            // so fillMaxHeight would measure to 0 and the ribbon would vanish.
            // This sizes to the Column after layout, keeping the ribbon in-block.
            Box(Modifier.matchParentSize()) {
                VerseBookmarkRibbon(
                    bookmarked = bookmarked,
                    focused = bookmarkFocused,
                    side = bookmarkSide,
                    chromeAlpha = bookmarkChromeAlpha,
                    interactive = bookmarkInteractive,
                    onToggle = onToggleBookmark,
                    modifier = Modifier
                        .align(
                            if (bookmarkSide == AyahSelectorSide.RIGHT) {
                                AbsoluteAlignment.TopRight
                            } else {
                                AbsoluteAlignment.TopLeft
                            },
                        )
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Surah opening: quiet centered typography over a whisper-faint embossed
 * star-and-cross weave, crowned by a gilded eight-fold rosette whose sheen
 * shifts with the page ([sheen] is read at draw time only). The chapter-opening
 * basmalah (when present) is a separate list item beneath this header so the
 * focus engine can home onto it independently — see [BasmalahBlock].
 */
@Composable
fun SurahHeader(
    chapterNumber: Int,
    nameArabic: String,
    nameTransliteration: String,
    nameTranslation: String,
    revelationPlace: String,
    ayahCount: Int,
    sheen: State<Float>,
) {
    val accents = LocalQuranAccents.current
    val weaveFade = MaterialTheme.colorScheme.background
    val hasBasmalahBelow = surahOpensWithBasmalahPreface(chapterNumber)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .starAndCrossWeave(
                ink = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f),
                embossLight = accents.embossLight.copy(alpha = 0.05f),
            )
            .verticalFadingEdges(color = weaveFade, top = 12.dp, bottom = 36.dp)
            .padding(
                top = 36.dp,
                // Tighter bottom when the basmalah block follows immediately.
                bottom = if (hasBasmalahBelow) 8.dp else 30.dp,
                start = 24.dp,
                end = 24.dp,
            ),
    ) {
        GildedRosette(
            size = 44.dp,
            brightGold = accents.goldBright,
            deepGold = accents.goldDeep,
            embossDark = accents.embossDark,
            embossLight = accents.embossLight,
            sheen = sheen,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "سُورَةُ $nameArabic",
            style = ArabicTitleStyle,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "$nameTransliteration · $nameTranslation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Chapter $chapterNumber · ${revelationPlace.replaceFirstChar { it.uppercase() }} · $ayahCount ayahs",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

/**
 * Chapter-opening basmalah as its own LazyColumn item — the focus engine's
 * target while the lead-in clip plays ([BASMALAH_PLAYLIST_AYAH]). Kept separate
 * from [SurahHeader] so placement, lyric-follow, and return-to-verse use the
 * calligraphy's own geometry (same path as any verse), not the taller title block.
 */
@Composable
fun BasmalahBlock(
    active: Boolean,
    dimmed: Boolean,
    washProgress: StateFlow<Float?>? = null,
    onClick: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp),
    ) {
        BasmalahCalligraphy(
            active = active,
            dimmed = dimmed,
            washProgress = washProgress,
            onClick = onClick,
        )
    }
}

/**
 * The surah name as it reappears in the top bar once the opening header has
 * scrolled off the page: flanked by gilded khatam flourishes, with the
 * transliteration whispered beneath. [sheen] keeps the gold lit in step with
 * the header rosette.
 */
@Composable
fun OrnateSurahTitle(
    chapterNumber: Int,
    nameArabic: String,
    nameTransliteration: String,
    sheen: State<Float>,
) {
    val accents = LocalQuranAccents.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GildedFlourish(
            width = 36.dp,
            height = 13.dp,
            brightGold = accents.goldBright,
            deepGold = accents.goldDeep,
            embossDark = accents.embossDark,
            embossLight = accents.embossLight,
            sheen = sheen,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = "سُورَةُ $nameArabic",
                style = ArabicTitleStyle,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "$chapterNumber · ${nameTransliteration.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
        GildedFlourish(
            width = 36.dp,
            height = 13.dp,
            brightGold = accents.goldBright,
            deepGold = accents.goldDeep,
            embossDark = accents.embossDark,
            embossLight = accents.embossLight,
            sheen = sheen,
            mirrored = true,
        )
    }
}

/**
 * Subtle page break: a thin gold line across the sheet with the page number
 * set in small digits on the right — marking the division
 * between mushaf pages without breaking the continuous scroll.
 */
@Composable
fun PageBreak(page: Int, useArabicIndicDigits: Boolean = true) {
    val accents = LocalQuranAccents.current
    val pageNumberSize = 12.sp
    val pageNumberColor = accents.gold.copy(alpha = 0.68f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = page.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = pageNumberSize,
                color = pageNumberColor,
            )
            Spacer(Modifier.width(8.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.5.dp,
                color = accents.gold.copy(alpha = 0.2f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (useArabicIndicDigits) page.toArabicIndic() else page.toString(),
                // Keep the Arabic-Indic digits at the same 12sp as the Western
                // numeral, but ask for a serif fallback so they stay in the
                // same family class as the EB Garamond label.
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                ),
                fontSize = pageNumberSize,
                color = pageNumberColor,
            )
        }
    }
}

/**
 * Opaque floating capsule after a Root Viewer concordance jump — stadium twin
 * of the return-to-ayah roundel (paper fill, gilt rim, drawn qalam arrow).
 * Hosted above the paper stack in MainActivity so it survives closing the
 * reader; tap returns, scroll or page-turn arms the 30s fade. See
 * docs/ROOT_VIEWER.md and docs/DESIGN.md.
 */
@Composable
fun BackToOriginPill(
    target: RootReturnTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IslamicBackToOriginCapsule(
        chapterLabel = target.chapterLabel,
        ayahLabel = target.ayahLabel,
        contentDescription = target.label,
        onClick = onClick,
        modifier = modifier,
    )
}

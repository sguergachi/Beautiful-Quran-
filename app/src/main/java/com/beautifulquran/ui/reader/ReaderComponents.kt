package com.beautifulquran.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.data.model.Ayah
import com.beautifulquran.data.model.Word
import com.beautifulquran.ui.theme.ArabicTitleStyle
import com.beautifulquran.ui.theme.ArabicWordStyle
import com.beautifulquran.ui.theme.GildedFlourish
import com.beautifulquran.ui.theme.GildedRosette
import com.beautifulquran.ui.theme.HafsFontFamily
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.TranslationFontFamily
import com.beautifulquran.ui.theme.gilded
import com.beautifulquran.ui.theme.letterFadeIn
import com.beautifulquran.ui.theme.starAndCrossWeave
import com.beautifulquran.ui.theme.verticalFadingEdges

enum class WordVisualState { Plain, Upcoming, Active, Recited }

private fun Int.toArabicIndic(): String =
    toString().map { '٠' + (it - '0') }.joinToString("")

/**
 * Apple-Music-lyrics treatment: the letters themselves carry the highlight.
 * Upcoming words rest faint on the page; the word being recited breathes in
 * to full ink; words already recited hold that full strength.
 */
private fun WordVisualState.inkAlpha(): Float = when (this) {
    WordVisualState.Plain -> 1f
    WordVisualState.Upcoming -> 0.22f
    WordVisualState.Active -> 1f
    WordVisualState.Recited -> 1f
}

private fun wordFadeAlpha(progress: Float): Float {
    val resting = WordVisualState.Upcoming.inkAlpha()
    return resting + (WordVisualState.Active.inkAlpha() - resting) * progress.coerceIn(0f, 1f)
}

/**
 * Returns the ink alpha as [State] so callers can defer the read to the draw
 * phase (inside a graphicsLayer block): the fade animates every frame without
 * recomposing or re-laying-out a single word.
 */
@Composable
private fun animatedInkAlpha(state: WordVisualState): State<Float> =
    animateFloatAsState(
        targetValue = state.inkAlpha(),
        animationSpec = tween(if (state == WordVisualState.Active) 250 else 450),
        label = "inkAlpha",
    )

const val MIN_SWEEP_MS = 140
const val MAX_SWEEP_MS = 8_000

/**
 * The wash moves at an unhurried, deliberate pace: essentially a steady
 * glide, softened only at the very ends so it never snaps into or out of
 * motion. All the slowness lives in the width of the ink feather (see
 * [com.beautifulquran.ui.theme.letterFadeIn]); the curve still lands on 1 at
 * exactly the word's duration, so the reveal stays locked to the recitation.
 */
private val InkSweepEasing = CubicBezierEasing(0.3f, 0.24f, 0.7f, 0.78f)

/**
 * Drives the letter-fade sweep for the active word: restarts at 0 each time
 * the word lights up and runs for [sweepMs] — the time the reciter actually
 * spends on this word — so the last letter finishes inking exactly as the
 * voice moves on.
 */
@Composable
private fun rememberLetterSweep(active: Boolean, sweepMs: Int?): State<Float> {
    val initialProgress = if (active && sweepMs != null) 0f else 1f
    val sweep = remember(active) { Animatable(initialProgress) }
    LaunchedEffect(active, sweepMs) {
        if (active && sweepMs != null) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(sweepMs, easing = InkSweepEasing))
        } else {
            sweep.snapTo(1f)
        }
    }
    return sweep.asState()
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun WordUnit(
    word: Word,
    state: WordVisualState,
    fontScale: Float,
    sweepMs: Int?,
    showGloss: Boolean,
    showTransliteration: Boolean,
    searchHit: Boolean,
    keepInView: Boolean,
    onClick: (() -> Unit)?,
) {
    val isActive = state == WordVisualState.Active
    val lyricInk = animatedInkAlpha(state)
    val sweep = rememberLetterSweep(isActive, sweepMs)
    val interaction = remember { MutableInteractionSource() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val activeWordTopMarginPx = with(density) { 144.dp.toPx() }
    val activeWordBottomMarginPx = with(density) { 132.dp.toPx() }
    var wordSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(isActive, keepInView, wordSize) {
        if (isActive && keepInView && wordSize != IntSize.Zero) {
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = -activeWordTopMarginPx,
                    right = wordSize.width.toFloat(),
                    bottom = wordSize.height + activeWordBottomMarginPx,
                ),
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged { wordSize = it }
            .let { m ->
                if (onClick != null) {
                    m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    m
                }
            }
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = word.arabic,
            style = ArabicWordStyle,
            fontSize = ArabicWordStyle.fontSize * fontScale,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = if (isActive) {
                Modifier.letterFadeIn(
                    progress = { sweep.value },
                    rtl = true,
                    restingAlpha = WordVisualState.Upcoming.inkAlpha(),
                )
            } else {
                Modifier.graphicsLayer { alpha = lyricInk.value }
            },
        )
        if (showGloss) {
            Text(
                text = word.translation,
                fontSize = 12.sp * fontScale,
                lineHeight = 15.sp * fontScale,
                fontWeight = if (searchHit) FontWeight.Bold else null,
                color = if (searchHit) {
                    LocalQuranAccents.current.gold
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = if (isActive) wordFadeAlpha(sweep.value) else lyricInk.value
                },
            )
        }
        if (showTransliteration) {
            Text(
                text = word.transliteration,
                fontSize = 11.sp * fontScale,
                lineHeight = 14.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = if (isActive) wordFadeAlpha(sweep.value) else lyricInk.value
                },
            )
        }
    }
}

/** One word of the English-only lyric flow. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun EnglishWordUnit(
    word: Word,
    state: WordVisualState,
    fontScale: Float,
    sweepMs: Int?,
    searchHit: Boolean,
    keepInView: Boolean,
    onClick: (() -> Unit)?,
) {
    val isActive = state == WordVisualState.Active
    val lyricInk = animatedInkAlpha(state)
    val sweep = rememberLetterSweep(isActive, sweepMs)
    val interaction = remember { MutableInteractionSource() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val activeWordTopMarginPx = with(density) { 144.dp.toPx() }
    val activeWordBottomMarginPx = with(density) { 132.dp.toPx() }
    var wordSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(isActive, keepInView, wordSize) {
        if (isActive && keepInView && wordSize != IntSize.Zero) {
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = -activeWordTopMarginPx,
                    right = wordSize.width.toFloat(),
                    bottom = wordSize.height + activeWordBottomMarginPx,
                ),
            )
        }
    }
    Text(
        text = word.translation,
        fontFamily = TranslationFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp * fontScale,
        lineHeight = 1.55.em,
        color = if (searchHit) {
            LocalQuranAccents.current.gold
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged { wordSize = it }
            .then(
                if (isActive) {
                    Modifier.letterFadeIn(
                        progress = { sweep.value },
                        rtl = false,
                        restingAlpha = WordVisualState.Upcoming.inkAlpha(),
                    )
                } else {
                    Modifier.graphicsLayer { alpha = lyricInk.value }
                },
            )
            .let { m ->
                if (onClick != null) {
                    m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    m
                }
            }
            .padding(horizontal = 3.dp, vertical = 2.dp),
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
    keepActiveWordInView: Boolean = false,
    onWordClick: ((Word) -> Unit)?,
    onAyahClick: () -> Unit,
) {
    fun hits(word: Word) =
        searchQuery != null && word.translation.contains(searchQuery, ignoreCase = true)
    // Non-active ayahs recede softly while another is being recited.
    // State is read inside graphicsLayer so the dim animates draw-phase-only.
    val blockAlpha = animateFloatAsState(
        targetValue = when {
            obscuredBySelector -> 0.07f
            dimmed -> 0.32f
            else -> 1f
        },
        animationSpec = tween(600),
        label = "ayahAlpha",
    )

    val activeWordPosition = activeWord?.wordPosition
    // The letter fade paces itself to how long the reciter dwells on the
    // word, corrected for the chosen playback speed.
    val sweepMs = activeWord
        ?.let { (it.durationMs / playbackSpeed).toInt() }
        ?.coerceIn(MIN_SWEEP_MS, MAX_SWEEP_MS)

    fun stateFor(word: Word): WordVisualState = when {
        !isActiveAyah -> WordVisualState.Plain
        activeWordPosition == null -> WordVisualState.Upcoming
        word.position == activeWordPosition -> WordVisualState.Active
        word.position < activeWordPosition -> WordVisualState.Recited
        else -> WordVisualState.Upcoming
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = blockAlpha.value }
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        if (readingMode == ReadingMode.ENGLISH_ONLY) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ayah.words.forEach { word ->
                    val wordState = stateFor(word)
                    EnglishWordUnit(
                        word = word,
                        state = wordState,
                        fontScale = fontScale,
                        sweepMs = sweepMs.takeIf { wordState == WordVisualState.Active },
                        searchHit = hits(word),
                        keepInView = keepActiveWordInView && wordState == WordVisualState.Active,
                        onClick = onWordClick?.let { handler -> { handler(word) } },
                    )
                }
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AyahNumberMark(
                        number = ayah.number,
                        fontScale = fontScale * 0.8f,
                        verticalNudge = 4.dp * fontScale,
                        useArabicIndicDigits = false,
                    )
                }
            }
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(if (showGloss) 12.dp else 4.dp),
                ) {
                    ayah.words.forEach { word ->
                        val wordState = stateFor(word)
                        WordUnit(
                            word = word,
                            state = wordState,
                            fontScale = fontScale,
                            sweepMs = sweepMs.takeIf { wordState == WordVisualState.Active },
                            showGloss = showGloss,
                            showTransliteration = showTransliteration,
                            searchHit = hits(word),
                            keepInView = keepActiveWordInView && wordState == WordVisualState.Active,
                            onClick = onWordClick?.let { handler -> { handler(word) } },
                        )
                    }
                    ArabicAyahNumberUnit(ayah.number, fontScale)
                }
            }
        }
        if (showTranslation && readingMode == ReadingMode.ARABIC_ENGLISH) {
            Spacer(Modifier.height(12.dp))
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAyahClick,
                    ),
            )
        }
        // Whitespace is the divider.
        Spacer(Modifier.height(if (readingMode == ReadingMode.ENGLISH_ONLY) 18.dp else 26.dp))
    }
}

/**
 * Surah opening: quiet centered typography over a whisper-faint embossed
 * star-and-cross weave, crowned by a gilded eight-fold rosette whose sheen
 * shifts with the page ([sheen] is read at draw time only).
 */
@Composable
fun SurahHeader(
    nameArabic: String,
    nameTransliteration: String,
    nameTranslation: String,
    revelationPlace: String,
    ayahCount: Int,
    sheen: State<Float>,
) {
    val accents = LocalQuranAccents.current
    val weaveFade = MaterialTheme.colorScheme.background
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .starAndCrossWeave(
                ink = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f),
                embossLight = accents.embossLight.copy(alpha = 0.05f),
            )
            .verticalFadingEdges(color = weaveFade, top = 12.dp, bottom = 36.dp)
            .padding(top = 36.dp, bottom = 30.dp, start = 24.dp, end = 24.dp),
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
            text = "${revelationPlace.replaceFirstChar { it.uppercase() }} · $ayahCount ayahs",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                text = nameTransliteration.uppercase(),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.5.dp,
                color = accents.gold.copy(alpha = 0.2f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (useArabicIndicDigits) page.toArabicIndic() else page.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = accents.gold.copy(alpha = 0.45f),
            )
        }
    }
}

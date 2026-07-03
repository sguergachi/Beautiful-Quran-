package com.beautifulquran.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.data.model.Ayah
import com.beautifulquran.data.model.Word
import com.beautifulquran.ui.theme.ArabicTitleStyle
import com.beautifulquran.ui.theme.ArabicWordStyle
import com.beautifulquran.ui.theme.HafsFontFamily
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.TranslationFontFamily

enum class WordVisualState { Plain, Upcoming, Active, Recited }

private fun Int.toArabicIndic(): String =
    toString().map { '٠' + (it - '0') }.joinToString("")

/**
 * Apple-Music-lyrics treatment: the letters themselves carry the highlight.
 * Upcoming words rest faint on the page; the word being recited breathes in
 * to full ink; words already recited settle just below full strength.
 */
private fun WordVisualState.inkAlpha(): Float = when (this) {
    WordVisualState.Plain -> 1f
    WordVisualState.Upcoming -> 0.3f
    WordVisualState.Active -> 1f
    WordVisualState.Recited -> 0.8f
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

@Composable
fun WordUnit(
    word: Word,
    state: WordVisualState,
    fontScale: Float,
    showGloss: Boolean,
    showTransliteration: Boolean,
    onClick: (() -> Unit)?,
) {
    val ink = animatedInkAlpha(state)
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { alpha = ink.value }
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
        )
        if (showGloss) {
            Text(
                text = word.translation,
                fontSize = 11.sp * fontScale,
                lineHeight = 14.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
        if (showTransliteration) {
            Text(
                text = word.transliteration,
                fontSize = 10.sp * fontScale,
                lineHeight = 13.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One word of the English-only lyric flow. */
@Composable
fun EnglishWordUnit(
    word: Word,
    state: WordVisualState,
    fontScale: Float,
    onClick: (() -> Unit)?,
) {
    val ink = animatedInkAlpha(state)
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = word.translation,
        fontFamily = TranslationFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp * fontScale,
        lineHeight = 1.55.em,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .graphicsLayer { alpha = ink.value }
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

/** Quiet typographic ayah marker: gold ornate brackets, no borders. */
@Composable
fun AyahNumberMark(number: Int, fontScale: Float) {
    val accents = LocalQuranAccents.current
    Text(
        text = "﴿${number.toArabicIndic()}﴾",
        fontFamily = HafsFontFamily,
        fontSize = 20.sp * fontScale,
        color = accents.gold.copy(alpha = 0.75f),
    )
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
    activeWordPosition: Int?,
    isActiveAyah: Boolean,
    dimmed: Boolean,
    fontScale: Float,
    showGloss: Boolean,
    showTransliteration: Boolean,
    showTranslation: Boolean,
    onWordClick: ((Word) -> Unit)?,
    onAyahClick: () -> Unit,
) {
    // Non-active ayahs recede softly while another is being recited.
    // State is read inside graphicsLayer so the dim animates draw-phase-only.
    val blockAlpha = animateFloatAsState(
        targetValue = if (dimmed) 0.32f else 1f,
        animationSpec = tween(600),
        label = "ayahAlpha",
    )

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
                    EnglishWordUnit(
                        word = word,
                        state = stateFor(word),
                        fontScale = fontScale,
                        onClick = onWordClick?.let { handler -> { handler(word) } },
                    )
                }
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AyahNumberMark(ayah.number, fontScale * 0.8f)
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
                        WordUnit(
                            word = word,
                            state = stateFor(word),
                            fontScale = fontScale,
                            showGloss = showGloss,
                            showTransliteration = showTransliteration,
                            onClick = onWordClick?.let { handler -> { handler(word) } },
                        )
                    }
                    Box(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AyahNumberMark(ayah.number, fontScale)
                    }
                }
            }
        }
        if (showTranslation && readingMode == ReadingMode.ARABIC_ENGLISH) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = ayah.translation,
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

/** Surah opening: quiet centered typography, held by whitespace alone. */
@Composable
fun SurahHeader(
    nameArabic: String,
    nameTransliteration: String,
    nameTranslation: String,
    revelationPlace: String,
    ayahCount: Int,
) {
    val accents = LocalQuranAccents.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 30.dp, start = 24.dp, end = 24.dp),
    ) {
        Text(
            text = "۞",
            fontFamily = HafsFontFamily,
            fontSize = 22.sp,
            color = accents.gold.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(14.dp))
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

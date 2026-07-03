package com.beautifulquran.ui.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun WordUnit(
    word: Word,
    state: WordVisualState,
    fontScale: Float,
    showGloss: Boolean,
    showTransliteration: Boolean,
    onClick: (() -> Unit)?,
) {
    val accents = LocalQuranAccents.current
    val background by animateColorAsState(
        targetValue = if (state == WordVisualState.Active) accents.goldWash else Color.Transparent,
        animationSpec = tween(220),
        label = "wordBg",
    )
    val arabicColor by animateColorAsState(
        targetValue = when (state) {
            WordVisualState.Active -> accents.gold
            WordVisualState.Recited -> accents.recitedInk
            else -> MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(220),
        label = "wordColor",
    )
    // Words that were already recited settle back with a gentle fade.
    val wordAlpha by animateFloatAsState(
        targetValue = if (state == WordVisualState.Recited) 0.72f else 1f,
        animationSpec = tween(400),
        label = "wordAlpha",
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(wordAlpha)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
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
            color = arabicColor,
        )
        if (showGloss) {
            Text(
                text = word.translation,
                fontSize = 11.sp * fontScale,
                lineHeight = 14.sp * fontScale,
                color = if (state == WordVisualState.Active) {
                    accents.gold
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                },
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
 * One ayah on the sheet: flowing Arabic words (RTL) with the English gloss
 * beneath each word, then the ayah translation. Separation from neighbours
 * is pure whitespace — no lines, no cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AyahBlock(
    ayah: Ayah,
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
    val blockAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.38f else 1f,
        animationSpec = tween(600),
        label = "ayahAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(blockAlpha)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(if (showGloss) 12.dp else 4.dp),
            ) {
                ayah.words.forEach { word ->
                    val state = when {
                        !isActiveAyah -> WordVisualState.Plain
                        activeWordPosition == null -> WordVisualState.Upcoming
                        word.position == activeWordPosition -> WordVisualState.Active
                        word.position < activeWordPosition -> WordVisualState.Recited
                        else -> WordVisualState.Upcoming
                    }
                    WordUnit(
                        word = word,
                        state = state,
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
        if (showTranslation) {
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
        Spacer(Modifier.height(26.dp))
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

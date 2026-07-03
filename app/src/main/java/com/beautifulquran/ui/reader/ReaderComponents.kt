package com.beautifulquran.ui.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beautifulquran.data.model.Ayah
import com.beautifulquran.data.model.Word
import com.beautifulquran.ui.theme.ArabicTitleStyle
import com.beautifulquran.ui.theme.ArabicWordStyle
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
        animationSpec = tween(150),
        label = "wordBg",
    )
    val arabicColor by animateColorAsState(
        targetValue = when (state) {
            WordVisualState.Active -> accents.gold
            WordVisualState.Recited -> accents.recitedInk
            else -> MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(150),
        label = "wordColor",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
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
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
        if (showTransliteration) {
            Text(
                text = word.transliteration,
                fontSize = 10.sp * fontScale,
                lineHeight = 13.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun AyahNumberMedallion(number: Int, size: Dp = 34.dp) {
    val accents = LocalQuranAccents.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .border(1.5.dp, accents.gold.copy(alpha = 0.7f), CircleShape),
    ) {
        Text(
            text = number.toArabicIndic(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

/**
 * One ayah in the follow-along view: flowing Arabic words (RTL) with the
 * English gloss under each word, then the ayah translation.
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
    val accents = LocalQuranAccents.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.5f else 1f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(if (showGloss) 10.dp else 2.dp),
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
                    modifier = Modifier.padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AyahNumberMedallion(ayah.number)
                }
            }
        }
        if (showTranslation) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = ayah.translation,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = TranslationFontFamily,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * (0.9f + 0.1f * fontScale),
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAyahClick),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(accents.divider),
            )
        }
    }
}

/** Ornamental surah header. */
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
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, accents.gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(vertical = 22.dp, horizontal = 16.dp),
    ) {
        Text(
            text = "سُورَةُ $nameArabic",
            style = ArabicTitleStyle,
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$nameTransliteration — $nameTranslation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${revelationPlace.replaceFirstChar { it.uppercase() }} • $ayahCount ayahs",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

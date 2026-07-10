package com.beautifulquran.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.beautifulquran.R
import com.beautifulquran.domain.BASMALAH_UTHMANI as DomainBasmalahUthmani
import com.beautifulquran.domain.SURAH_WITHOUT_BASMALAH as DomainSurahWithoutBasmalah
import com.beautifulquran.domain.surahOpensWithBasmalahPreface as domainSurahOpensWithBasmalahPreface
import com.beautifulquran.ui.theme.letterFadeIn
import com.beautifulquran.ui.theme.quietClickable

/** Re-export for reader UI and existing tests. */
const val BASMALAH_UTHMANI = DomainBasmalahUthmani

/** Re-export for reader UI and existing tests. */
const val SURAH_WITHOUT_BASMALAH = DomainSurahWithoutBasmalah

/** Re-export for reader UI and existing tests. */
fun surahOpensWithBasmalahPreface(surahId: Int): Boolean =
    domainSurahOpensWithBasmalahPreface(surahId)

/**
 * Traditional Naskh manuscript calligraphy of the basmalah — a VectorDrawable
 * rendered through [InkEngine]'s calligraphy path. Used as the quiet opening
 * under every surah header except Al-Fatihah and At-Tawbah.
 *
 * While the lead-in clip plays, an RTL [letterFadeIn] wash advances across the
 * SVG in step with the Al-Fatihah 1:1 word timings (four words). While another
 * ayah is recited the glyph recesses to Upcoming ink; at rest it is Plain.
 * Tap ([onClick]) restarts playback from the basmalah clip, then ayah 1.
 *
 * Artwork adapted from Wikimedia Commons File:Basmala.svg (Baba66, CC BY-SA 3.0).
 */
@Composable
fun BasmalahCalligraphy(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    dimmed: Boolean = false,
    activeWord: ActiveWord? = null,
    playbackSpeed: Float = 1f,
    onClick: (() -> Unit)? = null,
) {
    val inkState = InkEngine.prefaceState(isActive = active, dimmed = dimmed)
    val lyricInk by animateFloatAsState(
        targetValue = inkState.inkAlpha(),
        animationSpec = if (inkState == InkEngine.State.Active) {
            snap()
        } else {
            tween(InkEngine.tuning.inkFadeMs)
        },
        label = "basmalahLyricInk",
    )

    val sweepMs = InkEngine.sweepMs(activeWord, playbackSpeed)
    val wordSweep = remember { Animatable(1f) }
    val wordSweepState = wordSweep.asState()
    LaunchedEffect(active, activeWord?.wordPosition, sweepMs) {
        if (active && activeWord != null && sweepMs != null) {
            wordSweep.snapTo(0f)
            wordSweep.animateTo(1f, tween(sweepMs, easing = InkEngine.sweepEasing))
        } else {
            wordSweep.snapTo(1f)
        }
    }

    // Hold the wash at its furthest point after the last word ends (engine
    // returns null past last.endMs) so the glyph does not flash back to faint.
    var heldWash by remember { mutableFloatStateOf(0f) }
    val liveWash = activeWord?.let {
        InkEngine.prefaceWashProgress(it, wordSweepState.value)
    }
    SideEffect {
        if (!active) {
            heldWash = 0f
        } else if (liveWash != null && liveWash > heldWash) {
            heldWash = liveWash
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    Image(
        painter = painterResource(R.drawable.basmalah_naskh),
        contentDescription = BASMALAH_UTHMANI,
        colorFilter = ColorFilter.tint(ink),
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .then(if (onClick != null) Modifier.quietClickable(onClick = onClick) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(
                if (active) {
                    Modifier.letterFadeIn(
                        progress = {
                            when {
                                activeWord != null ->
                                    InkEngine.prefaceWashProgress(
                                        activeWord,
                                        wordSweepState.value,
                                    )
                                heldWash > 0f -> 1f
                                else -> 0f
                            }
                        },
                        rtl = true,
                        restingAlpha = InkEngine.State.Upcoming.inkAlpha(),
                        feather = InkEngine.tuning.washFeather,
                    )
                } else {
                    Modifier.graphicsLayer { alpha = lyricInk }
                },
            ),
    )
}

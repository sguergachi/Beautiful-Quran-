package com.beautifulquran.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.beautifulquran.R
import com.beautifulquran.domain.BASMALAH_UTHMANI as DomainBasmalahUthmani
import com.beautifulquran.domain.SURAH_WITHOUT_BASMALAH as DomainSurahWithoutBasmalah
import com.beautifulquran.domain.surahOpensWithBasmalahPreface as domainSurahOpensWithBasmalahPreface
import com.beautifulquran.ui.theme.quietClickable

/** Re-export for reader UI and existing tests. */
const val BASMALAH_UTHMANI = DomainBasmalahUthmani

/** Re-export for reader UI and existing tests. */
const val SURAH_WITHOUT_BASMALAH = DomainSurahWithoutBasmalah

/** Re-export for reader UI and existing tests. */
fun surahOpensWithBasmalahPreface(surahId: Int): Boolean =
    domainSurahOpensWithBasmalahPreface(surahId)

/**
 * Traditional Naskh manuscript calligraphy of the basmalah — ink on the page
 * via [InkEngine], tinted to the current surface ink. Used as the quiet
 * opening under every surah header except Al-Fatihah and At-Tawbah.
 *
 * While the dedicated basmalah lead-in plays, the glyph sits at Active ink;
 * while another ayah is recited it recesses to Upcoming; at rest it is Plain.
 * Tap ([onClick]) restarts playback from the basmalah clip.
 *
 * Artwork adapted from Wikimedia Commons File:Basmala.svg (Baba66, CC BY-SA 3.0).
 */
@Composable
fun BasmalahCalligraphy(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val inkState = InkEngine.prefaceState(isActive = active, dimmed = dimmed)
    val inkAlpha by animateFloatAsState(
        targetValue = inkState.inkAlpha(),
        animationSpec = if (inkState == InkEngine.State.Active) {
            snap()
        } else {
            tween(InkEngine.tuning.inkFadeMs)
        },
        label = "basmalahInk",
    )
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = inkAlpha)
    Image(
        painter = painterResource(R.drawable.basmalah_naskh),
        contentDescription = BASMALAH_UTHMANI,
        colorFilter = ColorFilter.tint(ink),
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .then(if (onClick != null) Modifier.quietClickable(onClick = onClick) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    )
}

package com.beautifulquran.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.R

/**
 * The basmalah as it appears in the Uthmani text (Al-Fatihah 1:1). Kept for
 * tests and for accessibility labels on the calligraphic render.
 */
const val BASMALAH_UTHMANI = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

/** At-Tawbah: the one surah that does not open with the basmalah. */
const val SURAH_WITHOUT_BASMALAH = 9

/** Al-Fatihah: the basmalah is counted as ayah 1, so it is not prefaced. */
private const val SURAH_BASMALAH_IS_AYAH_ONE = 1

/**
 * Whether the reader should draw the basmalah as a preface under the surah
 * header. False for Al-Fatihah (already ayah 1) and At-Tawbah (none).
 */
fun surahOpensWithBasmalahPreface(surahId: Int): Boolean =
    surahId != SURAH_BASMALAH_IS_AYAH_ONE && surahId != SURAH_WITHOUT_BASMALAH

/**
 * Traditional Naskh manuscript calligraphy of the basmalah — ink on the page,
 * tinted to the current surface ink. Used as the quiet opening under every
 * surah header except Al-Fatihah and At-Tawbah.
 *
 * Artwork adapted from Wikimedia Commons File:Basmala.svg (Baba66, CC BY-SA 3.0).
 */
@Composable
fun BasmalahCalligraphy(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else 0.88f)
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

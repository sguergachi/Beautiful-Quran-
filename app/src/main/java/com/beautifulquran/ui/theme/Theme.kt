package com.beautifulquran.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.beautifulquran.data.ThemeMode

// "Paper" light palette
private val PaperBackground = Color(0xFFFAF6EF)
private val PaperSurface = Color(0xFFFFFDF7)
private val PaperSurfaceHigh = Color(0xFFF3EDE0)
private val Ink = Color(0xFF1C1B18)
private val InkMuted = Color(0xFF6E6858)
private val DeepGreen = Color(0xFF0E5C4A)
private val DeepGreenDim = Color(0xFF9BB8AF)

// "Night prayer" dark palette
private val NightBackground = Color(0xFF12140F)
private val NightSurface = Color(0xFF1A1D17)
private val NightSurfaceHigh = Color(0xFF232720)
private val Parchment = Color(0xFFE8E2D5)
private val ParchmentMuted = Color(0xFF97917F)
private val SoftGreen = Color(0xFF7FB8A4)

/** Accent colors that sit outside the Material scheme. */
data class QuranAccents(
    val gold: Color,
    val goldWash: Color,
    val recitedInk: Color,
    val divider: Color,
)

val LocalQuranAccents = staticCompositionLocalOf {
    QuranAccents(
        gold = Color(0xFFC9A227),
        goldWash = Color(0x2EC9A227),
        recitedInk = Color(0xFF0E5C4A),
        divider = Color(0x1F1C1B18),
    )
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = DeepGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8E1),
    onPrimaryContainer = Color(0xFF06382D),
    secondary = InkMuted,
    onSecondary = Color.White,
    background = PaperBackground,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFBFB8A6),
    surfaceContainer = PaperSurfaceHigh,
    surfaceContainerHigh = Color(0xFFEDE6D6),
    surfaceContainerHighest = Color(0xFFE7DFCC),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = SoftGreen,
    onPrimary = Color(0xFF06382D),
    primaryContainer = Color(0xFF0E5C4A),
    onPrimaryContainer = Color(0xFFD7E8E1),
    secondary = ParchmentMuted,
    onSecondary = NightBackground,
    background = NightBackground,
    onBackground = Parchment,
    surface = NightSurface,
    onSurface = Parchment,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = ParchmentMuted,
    outline = Color(0xFF4A4E44),
    surfaceContainer = NightSurfaceHigh,
    surfaceContainerHigh = Color(0xFF2A2E26),
    surfaceContainerHighest = Color(0xFF31352C),
)

private val LightAccents = QuranAccents(
    gold = Color(0xFFB8901C),
    goldWash = Color(0x33C9A227),
    recitedInk = DeepGreen,
    divider = Color(0x1F1C1B18),
)

private val DarkAccents = QuranAccents(
    gold = Color(0xFFD9B44A),
    goldWash = Color(0x40D9B44A),
    recitedInk = SoftGreen,
    divider = Color(0x26E8E2D5),
)

@Composable
fun BeautifulQuranTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalQuranAccents provides if (dark) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = QuranTypography,
            content = content,
        )
    }
}

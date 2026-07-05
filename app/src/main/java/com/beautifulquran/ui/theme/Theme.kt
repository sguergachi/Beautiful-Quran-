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
private val PaperBackground = Color(0xFFFAF3E8)
private val PaperSurface = Color(0xFFFFFBF2)
private val PaperSurfaceHigh = Color(0xFFF2E7D5)
private val Ink = Color(0xFF1C1B18)
private val InkMuted = Color(0xFF6E6858)
private val DeepGreen = Color(0xFF0E5C4A)
private val DeepGreenDim = Color(0xFF9BB8AF)
private val DeepGreenPale = Color(0xFFD7E8E1)
private val DeepGreenFaint = Color(0xFFE8F3EE)

// "Night prayer" dark palette
private val NightBackground = Color(0xFF010F0C)
private val NightSurface = Color(0xFF031813)
private val NightSurfaceHigh = Color(0xFF05241D)
private val Parchment = Color(0xFFE8E2D5)
private val ParchmentMuted = Color(0xFF97917F)
private val SoftGreen = Color(0xFF7FB8A4)

// "Royal green" dark palette
private val RoyalGreenBackground = Color(0xFF062C24)
private val RoyalGreenSurface = Color(0xFF0A382E)
private val RoyalGreenSurfaceHigh = Color(0xFF10483B)

/**
 * Accent colors that sit outside the Material scheme.
 * Gold is never flat: [goldBright]/[goldDeep] are the gilding gradient stops,
 * and [embossDark]/[embossLight] are the relief shadows for ornament pressed
 * into the paper (light from the upper-left).
 */
data class QuranAccents(
    val gold: Color,
    val goldBright: Color,
    val goldDeep: Color,
    val embossDark: Color,
    val embossLight: Color,
    /** Warm ink for words the reciter is repeating — a second, orange fade that
     * dissolves back to normal ink once the recitation moves past the repeat. */
    val repeatInk: Color,
)

val LocalQuranAccents = staticCompositionLocalOf {
    QuranAccents(
        gold = Color(0xFFC9A227),
        goldBright = Color(0xFFE9CD7A),
        goldDeep = Color(0xFF8A6B1E),
        embossDark = Color(0x24000000),
        embossLight = Color(0x59FFFFFF),
        repeatInk = Color(0xFFC2622A),
    )
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = DeepGreen,
    onPrimary = Color.White,
    primaryContainer = DeepGreenPale,
    onPrimaryContainer = Color(0xFF06382D),
    inversePrimary = Color(0xFF7FB8A4),
    secondary = DeepGreen,
    onSecondary = Color.White,
    secondaryContainer = DeepGreenFaint,
    onSecondaryContainer = Color(0xFF073E32),
    tertiary = Color(0xFF2F6F5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0EFE9),
    onTertiaryContainer = Color(0xFF0A3E32),
    background = PaperBackground,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = InkMuted,
    surfaceTint = DeepGreen,
    inverseSurface = Color(0xFF32302A),
    inverseOnSurface = Color(0xFFF5ECDC),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFC0B49E),
    outlineVariant = Color(0xFFDACCB6),
    scrim = Color.Black,
    surfaceBright = PaperSurface,
    surfaceDim = Color(0xFFE8DCC5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF5E9),
    surfaceContainer = PaperSurfaceHigh,
    surfaceContainerHigh = Color(0xFFEDE1CC),
    surfaceContainerHighest = Color(0xFFE8DCC5),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = SoftGreen,
    onPrimary = Color(0xFF06382D),
    primaryContainer = Color(0xFF0E5C4A),
    onPrimaryContainer = Color(0xFFD7E8E1),
    inversePrimary = DeepGreen,
    secondary = SoftGreen,
    onSecondary = NightBackground,
    secondaryContainer = Color(0xFF244E43),
    onSecondaryContainer = Color(0xFFD7E8E1),
    tertiary = Color(0xFF9BC9B8),
    onTertiary = Color(0xFF0B332A),
    tertiaryContainer = Color(0xFF21493E),
    onTertiaryContainer = Color(0xFFE0EFE9),
    background = NightBackground,
    onBackground = Parchment,
    surface = NightSurface,
    onSurface = Parchment,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = ParchmentMuted,
    surfaceTint = SoftGreen,
    inverseSurface = Color(0xFFE8E2D5),
    inverseOnSurface = Color(0xFF2F332B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF365F55),
    outlineVariant = Color(0xFF153D34),
    scrim = Color.Black,
    surfaceBright = Color(0xFF0B3B31),
    surfaceDim = NightBackground,
    surfaceContainerLowest = Color(0xFF000907),
    surfaceContainerLow = Color(0xFF02130F),
    surfaceContainer = NightSurfaceHigh,
    surfaceContainerHigh = Color(0xFF082F27),
    surfaceContainerHighest = Color(0xFF0B3B31),
)

private val RoyalGreenColors: ColorScheme = darkColorScheme(
    primary = SoftGreen,
    onPrimary = Color(0xFF06382D),
    primaryContainer = Color(0xFF0E5C4A),
    onPrimaryContainer = Color(0xFFD7E8E1),
    inversePrimary = DeepGreen,
    secondary = SoftGreen,
    onSecondary = RoyalGreenBackground,
    secondaryContainer = Color(0xFF244E43),
    onSecondaryContainer = Color(0xFFD7E8E1),
    tertiary = Color(0xFF9BC9B8),
    onTertiary = Color(0xFF0B332A),
    tertiaryContainer = Color(0xFF21493E),
    onTertiaryContainer = Color(0xFFE0EFE9),
    background = RoyalGreenBackground,
    onBackground = Parchment,
    surface = RoyalGreenSurface,
    onSurface = Parchment,
    surfaceVariant = RoyalGreenSurfaceHigh,
    onSurfaceVariant = ParchmentMuted,
    surfaceTint = SoftGreen,
    inverseSurface = Color(0xFFE8E2D5),
    inverseOnSurface = Color(0xFF2F332B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF568276),
    outlineVariant = Color(0xFF2C5E51),
    scrim = Color.Black,
    surfaceBright = Color(0xFF1C5E4E),
    surfaceDim = RoyalGreenBackground,
    surfaceContainerLowest = Color(0xFF04221C),
    surfaceContainerLow = Color(0xFF083229),
    surfaceContainer = RoyalGreenSurfaceHigh,
    surfaceContainerHigh = Color(0xFF155242),
    surfaceContainerHighest = Color(0xFF1A5D4B),
)

private val LightAccents = QuranAccents(
    gold = Color(0xFFB8901C),
    goldBright = Color(0xFFE9CD7A),
    goldDeep = Color(0xFF8A6B1E),
    embossDark = Color(0x24000000),
    embossLight = Color(0x59FFFFFF),
    repeatInk = Color(0xFFB4551E),
)

private val DarkAccents = QuranAccents(
    gold = Color(0xFFD9B44A),
    goldBright = Color(0xFFEDD188),
    goldDeep = Color(0xFF9A7B2A),
    embossDark = Color(0x66000000),
    embossLight = Color(0x1FFFFFFF),
    repeatInk = Color(0xFFE0904E),
)

@Composable
fun themePreviewColors(themeMode: ThemeMode): List<Color> {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) {
            listOf(NightBackground, NightSurfaceHigh, SoftGreen, Parchment)
        } else {
            listOf(PaperBackground, PaperSurfaceHigh, DeepGreen, Ink)
        }
        ThemeMode.LIGHT -> listOf(PaperBackground, PaperSurfaceHigh, DeepGreen, Ink)
        ThemeMode.DARK -> listOf(NightBackground, NightSurfaceHigh, SoftGreen, Parchment)
        ThemeMode.ROYAL_GREEN -> listOf(RoyalGreenBackground, RoyalGreenSurfaceHigh, SoftGreen, Parchment)
    }
}

@Composable
fun BeautifulQuranTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkAccents = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.ROYAL_GREEN -> true
    }
    val colors = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.ROYAL_GREEN -> RoyalGreenColors
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalQuranAccents provides if (darkAccents) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = QuranTypography,
            content = content,
        )
    }
}

package com.dhruw.autoflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = OnAccentContainerDark,
    secondary = TextSecondaryDark,
    onSecondary = Ink,
    secondaryContainer = SurfaceDarkHighest,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = Mint,
    onTertiary = OnMint,
    tertiaryContainer = MintContainerDark,
    onTertiaryContainer = OnMintContainerDark,
    error = DangerDark,
    onError = OnDangerDark,
    errorContainer = DangerContainerDark,
    onErrorContainer = OnDangerContainerDark,
    background = Ink,
    onBackground = TextPrimaryDark,
    surface = Ink,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = SurfaceDarkLowest,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDarkContainer,
    surfaceContainerHigh = SurfaceDarkHigh,
    surfaceContainerHighest = SurfaceDarkHighest,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = SurfaceLight,
    primaryContainer = AccentContainerLight,
    onPrimaryContainer = OnAccentContainerLight,
    background = PaperLight,
    onBackground = TextPrimaryLight,
    surface = PaperLight,
    onSurface = TextPrimaryLight,
    surfaceContainerLow = SurfaceLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = DangerLight
)

/**
 * AutoFlow is a dark-first app: dark is the default regardless of system
 * setting until an appearance preference exists in Settings.
 */
@Composable
fun AutoFlowTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

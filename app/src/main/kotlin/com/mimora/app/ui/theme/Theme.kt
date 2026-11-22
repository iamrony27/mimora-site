package com.mimora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.mimora.app.design.WritingCanvasTheme

private val DarkColorScheme = darkColorScheme(
    primary = StitchDarkPrimary,
    onPrimary = StitchDarkOnPrimary,
    primaryContainer = StitchDarkPrimaryContainer,
    onPrimaryContainer = StitchDarkOnPrimaryContainer,
    secondary = StitchDarkSecondary,
    onSecondary = StitchDarkOnSecondary,
    secondaryContainer = StitchDarkSecondaryContainer,
    onSecondaryContainer = StitchDarkOnSecondaryContainer,
    tertiary = StitchDarkTertiary,
    onTertiary = StitchDarkOnTertiary,
    tertiaryContainer = StitchDarkTertiaryContainer,
    onTertiaryContainer = StitchDarkOnTertiaryContainer,
    background = StitchDarkBackground,
    surface = StitchDarkSurface,
    surfaceVariant = StitchDarkSurfaceContainerHigh,
    surfaceTint = StitchDarkPrimary,
    surfaceBright = StitchDarkSurfaceBright,
    surfaceDim = StitchDarkSurfaceDim,
    surfaceContainer = StitchDarkSurfaceContainer,
    surfaceContainerLow = StitchDarkSurfaceContainerLow,
    surfaceContainerLowest = StitchDarkSurfaceContainerLowest,
    surfaceContainerHigh = StitchDarkSurfaceContainerHigh,
    surfaceContainerHighest = StitchDarkSurfaceContainerHighest,
    onSurface = StitchDarkOnSurface,
    onSurfaceVariant = StitchDarkOnSurfaceVariant,
    outline = StitchDarkOutline,
    outlineVariant = StitchDarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = StitchPrimary,
    onPrimary = StitchOnPrimary,
    primaryContainer = StitchPrimaryContainer,
    onPrimaryContainer = StitchOnPrimaryContainer,
    secondary = StitchSecondary,
    onSecondary = StitchOnSecondary,
    secondaryContainer = StitchSecondaryContainer,
    onSecondaryContainer = StitchOnSecondaryContainer,
    tertiary = StitchTertiary,
    onTertiary = StitchOnTertiary,
    tertiaryContainer = StitchTertiaryContainer,
    onTertiaryContainer = StitchOnTertiaryContainer,
    background = StitchBackground,
    surface = StitchSurface,
    surfaceVariant = StitchSurfaceVariant,
    surfaceTint = StitchSurfaceTint,
    surfaceBright = StitchSurfaceBright,
    surfaceDim = StitchSurfaceDim,
    surfaceContainer = StitchSurfaceContainer,
    surfaceContainerLow = StitchSurfaceContainerLow,
    surfaceContainerLowest = StitchSurfaceContainerLowest,
    surfaceContainerHigh = StitchSurfaceContainerHigh,
    surfaceContainerHighest = StitchSurfaceContainerHighest,
    onSurface = StitchOnSurface,
    onSurfaceVariant = StitchOnSurfaceVariant,
    outline = StitchOutline,
    outlineVariant = StitchOutlineVariant
)

@Composable
fun MIMORATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    writingTheme: WritingCanvasTheme = WritingCanvasTheme.LAVENDER,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val accent = writingTheme.accentColor

    // Buttons and accents across the whole app follow the writing canvas color.
    // Keep contrast readable by deriving text colors from the chosen accent.
    val primary = if (darkTheme) lerp(accent, Color.White, 0.45f) else accent
    val onPrimary = readableTextOn(primary)
    val primaryContainer = lerp(base.surface, accent, if (darkTheme) 0.30f else 0.16f)
    val onPrimaryContainer = lerp(base.onSurface, accent, 0.45f)

    val colorScheme = base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        surfaceTint = primary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** Picks dark or light text for the given button/background color. */
private fun readableTextOn(background: Color): Color {
    val brightness = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (brightness > 0.5f) Color(0xFF1E1924) else Color.White
}

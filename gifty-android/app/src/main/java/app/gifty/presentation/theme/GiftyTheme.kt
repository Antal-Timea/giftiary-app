package app.gifty.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Gifty uses its own palette rather than dynamic color, so the brand looks the same on every device.
 */
@Composable
fun GiftyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkGiftyColors else LightGiftyColors
    CompositionLocalProvider(LocalGiftyColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = GiftyTypography,
            shapes = GiftyMaterialShapes,
            content = content,
        )
    }
}

object GiftyTheme {
    val colors: GiftyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGiftyColors.current
}

/**
 * Material components that are not styled explicitly still pick Gifty roles through this mapping.
 */
private fun GiftyColors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primaryAction,
        onPrimary = onPrimaryAction,
        primaryContainer = surfaceContext,
        onPrimaryContainer = textPrimary,
        secondary = support,
        // Not text-safe in dark: onSupport on support is about 1.5:1. Do not use Material
        // components that put onSecondary text on a secondary container (e.g. a filled
        // button or chip colored secondary) for normal-size text.
        onSecondary = onSupport,
        secondaryContainer = surfaceContextQuiet,
        onSecondaryContainer = textPrimary,
        tertiary = heroFallback,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceContext,
        onSurfaceVariant = textSecondary,
        surfaceTint = surface,
        surfaceBright = surfaceElevated,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceElevated,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        inversePrimary = primaryActionText,
        error = error,
        outline = border,
        outlineVariant = divider,
        scrim = scrim,
    )
}

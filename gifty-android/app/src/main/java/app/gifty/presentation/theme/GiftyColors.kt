package app.gifty.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic Gifty colors. Feature screens use these roles, never raw palette values.
 */
@Immutable
data class GiftyColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceContext: Color,
    val surfaceContextQuiet: Color,
    val inputSurface: Color,
    val navigationSurface: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textLabel: Color,
    val textMuted: Color,
    val placeholder: Color,

    val primaryAction: Color,
    val onPrimaryAction: Color,
    /** Coral for text-only actions; [primaryAction] is too dark for text on dark surfaces. */
    val primaryActionText: Color,

    val support: Color,
    val onSupport: Color,

    val selected: Color,

    val directionReceived: Color,
    val directionGiven: Color,

    val navigationSelected: Color,
    val navigationUnselected: Color,

    val border: Color,
    val divider: Color,
    val inputFocused: Color,
    val error: Color,

    val skeleton: Color,
    val scrim: Color,
    val heroFallback: Color,
)

internal val DarkGiftyColors = GiftyColors(
    background = Color(0xFF121F1D),
    surface = Color(0xFF172725),
    surfaceElevated = Color(0xFF1C302D),
    surfaceContext = Color(0xFF1D3B36),
    surfaceContextQuiet = Color(0xFF2A5A52),
    inputSurface = Color(0xFF192B28),
    navigationSurface = Color(0xFF182927),

    textPrimary = Color(0xFFF2EDE1),
    textSecondary = Color(0xFFAFC3BC),
    textLabel = Color(0xFFBCD0C9),
    textMuted = Color(0xFF8AA09A),
    placeholder = Color(0xFF829993),

    primaryAction = Color(0xFFD6285A),
    onPrimaryAction = Color(0xFFFFF7F4),
    primaryActionText = Color(0xFFFA8399),

    support = Color(0xFF4FD6C2),
    onSupport = Color(0xFFF2EDE1),

    selected = Color(0xFFF2EDE1),

    directionReceived = Color(0xFF79D9C6),
    directionGiven = Color(0xFFFA8399),

    navigationSelected = Color(0xFFF2EDE1),
    navigationUnselected = Color(0xFF96ABA5),

    border = Color(0xFF404E49),
    divider = Color(0xFF2D3835),
    inputFocused = Color(0xFFF2EDE1),
    error = Color(0xFFFFB4AB),

    skeleton = Color(0xFF283431),
    scrim = Color(0xFF0A100F),
    heroFallback = Color(0xFFE3A73A),
)

internal val LightGiftyColors = GiftyColors(
    background = Color(0xFFF7F2E8),
    surface = Color(0xFFFBF8F2),
    surfaceElevated = Color(0xFFFFFDF9),
    surfaceContext = Color(0xFFE4EEEA),
    surfaceContextQuiet = Color(0xFFCFE2DC),
    inputSurface = Color(0xFFFFFDF9),
    navigationSurface = Color(0xFFF1EBDF),

    textPrimary = Color(0xFF143A34),
    textSecondary = Color(0xFF43605A),
    textLabel = Color(0xFF36534D),
    textMuted = Color(0xFF586E69),
    placeholder = Color(0xFF677B76),

    primaryAction = Color(0xFFC8234F),
    onPrimaryAction = Color(0xFFFFF7F4),
    primaryActionText = Color(0xFFB01E47),

    support = Color(0xFF1A7A6D),
    onSupport = Color(0xFFFBF8F2),

    selected = Color(0xFF143A34),

    directionReceived = Color(0xFF1A7A6D),
    directionGiven = Color(0xFFB01E47),

    navigationSelected = Color(0xFF143A34),
    navigationUnselected = Color(0xFF586E69),

    border = Color(0xFFC9C1B2),
    divider = Color(0xFFE3DCCF),
    inputFocused = Color(0xFF143A34),
    error = Color(0xFFB3261E),

    skeleton = Color(0xFFEAE3D6),
    scrim = Color(0xFF0A100F),
    heroFallback = Color(0xFFE3A73A),
)

internal val LocalGiftyColors = staticCompositionLocalOf { LightGiftyColors }

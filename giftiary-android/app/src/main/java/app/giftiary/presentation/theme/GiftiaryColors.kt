package app.giftiary.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic Giftiary colors. Feature screens use these roles, never raw palette values.
 */
@Immutable
data class GiftiaryColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceContext: Color,
    val surfaceContextQuiet: Color,
    /** A second warm ground behind onboarding chapters and quiet grouped areas. */
    val surfaceAlt: Color,
    /** Aqua controls: the back circle, status pills, the details toggle. */
    val supportSurface: Color,
    /** Behind a person's monogram when there is no photo. */
    val avatarFallback: Color,
    /** Behind the gift glyph when a gift has no photo. */
    val thumbnailFallback: Color,
    val inputSurface: Color,
    val navigationSurface: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textLabel: Color,
    val textMuted: Color,
    /** Metadata only (prices, counts): below normal-text contrast in light, never load-bearing. */
    val textTertiary: Color,
    val placeholder: Color,

    val primaryAction: Color,
    val onPrimaryAction: Color,
    /** Coral for text-only actions; [primaryAction] is too dark for text on dark surfaces. */
    val primaryActionText: Color,

    val support: Color,

    /**
     * Do not use for small or normal-size text directly on [support]: the agreed dark pair
     * (#F2EDE1 on #4FD6C2) is about 1.5:1, far below WCAG AA (4.5:1).
     */
    val onSupport: Color,

    val selected: Color,

    val directionReceived: Color,
    val directionGiven: Color,

    val navigationSelected: Color,
    val navigationUnselected: Color,

    val border: Color,
    /** Secondary-action outlines and fields holding a value that can be cleared. */
    val borderStrong: Color,
    val divider: Color,
    val inputFocused: Color,
    /** The text caret of a focused field; light keeps it coral against the teal focus border (DS-03). */
    val caret: Color,
    val error: Color,

    val skeleton: Color,
    val scrim: Color,
    val heroFallback: Color,
)

internal val DarkGiftiaryColors = GiftiaryColors(
    background = Color(0xFF121F1D),
    surface = Color(0xFF172725),
    surfaceElevated = Color(0xFF1C302D),
    surfaceContext = Color(0xFF1D3B36),
    surfaceContextQuiet = Color(0xFF2A5A52),
    // R3: dark is out of scope for the redesign. Tokens added for the light design reuse existing
    // dark values until a dark design is approved.
    surfaceAlt = Color(0xFF1C302D),
    supportSurface = Color(0xFF1D3B36),
    avatarFallback = Color(0xFF2A5A52),
    thumbnailFallback = Color(0xFF1D3B36),
    inputSurface = Color(0xFF192B28),
    navigationSurface = Color(0xFF182927),

    textPrimary = Color(0xFFF2EDE1),
    textSecondary = Color(0xFFAFC3BC),
    textLabel = Color(0xFFBCD0C9),
    textMuted = Color(0xFF8AA09A),
    textTertiary = Color(0xFF8AA09A),
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
    borderStrong = Color(0xFF404E49),
    divider = Color(0xFF2D3835),
    inputFocused = Color(0xFFF2EDE1),
    caret = Color(0xFFF2EDE1),
    error = Color(0xFFFFB4AB),

    skeleton = Color(0xFF283431),
    scrim = Color(0xFF0A100F),
    heroFallback = Color(0xFFE3A73A),
)

// The approved light palette (design system board DS-01).
internal val LightGiftiaryColors = GiftiaryColors(
    background = Color(0xFFFBF7EE),
    surface = Color(0xFFFBF7EE),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceContext = Color(0xFFBFEAE3),
    surfaceContextQuiet = Color(0xFFDDF5F0),
    surfaceAlt = Color(0xFFF4EEDF),
    supportSurface = Color(0x2E4FD6C2), // aqua at 0.18
    avatarFallback = Color(0x594FD6C2), // aqua at 0.35
    thumbnailFallback = Color(0x73BFEAE3), // aqua band at 0.45
    inputSurface = Color(0xFFFFFFFF),
    navigationSurface = Color(0xFFFBF7EE),

    textPrimary = Color(0xFF17423E),
    textSecondary = Color(0xFF47615D),
    textLabel = Color(0xFF2C5852),
    // DS-01 reserves #547370 for unselected navigation; it is also the only muted teal that keeps
    // normal-text contrast, so existing muted text and icons use it.
    textMuted = Color(0xFF547370),
    textTertiary = Color(0xFF6E837D),
    placeholder = Color(0xFF7C948F),

    primaryAction = Color(0xFFD6285A),
    onPrimaryAction = Color(0xFFFFFFFF),
    primaryActionText = Color(0xFFD6285A),

    support = Color(0xFF4FD6C2),
    onSupport = Color(0xFF17423E),

    selected = Color(0xFF17423E),

    directionReceived = Color(0xFF0F5F56),
    directionGiven = Color(0xFFD6285A),

    navigationSelected = Color(0xFF17423E),
    navigationUnselected = Color(0xFF547370),

    border = Color(0x2417423E), // teal at 0.14
    borderStrong = Color(0x3817423E), // teal at 0.22
    divider = Color(0x1A17423E), // teal at 0.10
    inputFocused = Color(0xFF17423E),
    caret = Color(0xFFD6285A),
    // Giftiary has no danger red: a field error is a coral border with a teal sentence.
    error = Color(0xFFD6285A),

    skeleton = Color(0x0F17423E), // teal at 0.06
    scrim = Color(0x9417423E), // teal at 0.58
    heroFallback = Color(0xFFE3A73A),
)

internal val LocalGiftiaryColors = staticCompositionLocalOf { LightGiftiaryColors }

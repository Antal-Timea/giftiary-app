package app.giftiary.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.giftiary.R

// All fonts are bundled variable fonts; each weight selects a point on the wght axis.
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Editorial voice: titles, section headings, the days-until number. */
val Lora = FontFamily(
    variableFont(R.font.lora, FontWeight.Normal),
    variableFont(R.font.lora, FontWeight.Medium),
    variableFont(R.font.lora, FontWeight.SemiBold),
    variableFont(R.font.lora, FontWeight.Bold),
)

/** Functional voice: body, labels, buttons, inputs, navigation. */
val PublicSans = FontFamily(
    variableFont(R.font.public_sans, FontWeight.Normal),
    variableFont(R.font.public_sans, FontWeight.Medium),
    variableFont(R.font.public_sans, FontWeight.SemiBold),
    variableFont(R.font.public_sans, FontWeight.Bold),
    variableFont(R.font.public_sans, FontWeight.ExtraBold),
)

/** One handwritten line on editorial photography; never a label or a control. */
val Caveat = FontFamily(variableFont(R.font.caveat, FontWeight.Medium))

private fun lora(size: Float, weight: FontWeight, lineHeight: Float? = null) = TextStyle(
    fontFamily = Lora,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextStyle.Default.lineHeight,
)

private fun publicSans(size: Float, weight: FontWeight, lineHeight: Float? = null, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = PublicSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextStyle.Default.lineHeight,
    letterSpacing = letterSpacing.em,
)

/**
 * The type roles of the approved design system (DS-02) that Material's slots do not name. Sizes
 * and line heights are the board's; a new role should be suspicious.
 */
object GiftiaryType {
    /** The one big line on a task screen: Add a gift, Create list. */
    val display = lora(36f, FontWeight.Bold, lineHeight = 1.05f)

    /** Root destinations: People, Gift history, Lists. */
    val screenTitle = lora(28f, FontWeight.Bold, lineHeight = 1.15f)

    /** Bottom sheets and empty-state headlines. */
    val sheetTitle = lora(23f, FontWeight.Bold, lineHeight = 1.15f)

    /** A section that carries emotional weight, at most once per screen. */
    val editorialSection = lora(22f, FontWeight.Bold)

    /** The ordinary section heading: Coming up, All people, Gift ideas. */
    val sectionTitle = lora(17f, FontWeight.SemiBold)

    /** The name or title of a row. */
    val rowTitle = publicSans(15f, FontWeight.Bold)

    /** A gift, idea or wish in a dense list, where bold would shout. */
    val contentTitle = publicSans(14.5f, FontWeight.SemiBold)

    /** Standfirsts and explanations. */
    val body = publicSans(13.5f, FontWeight.Normal, lineHeight = 1.5f)

    /** A note inside a row, the second line of a list item. */
    val bodySmall = publicSans(13f, FontWeight.Normal, lineHeight = 1.35f)

    /** What the user typed into a field. */
    val fieldValue = publicSans(14f, FontWeight.SemiBold)

    /** A multi-line note field. */
    val note = publicSans(13f, FontWeight.Normal, lineHeight = 1.45f)

    /** Every form label. */
    val label = publicSans(12.5f, FontWeight.Bold)

    /** Prices, counts, dates. */
    val metadata = publicSans(12f, FontWeight.Normal)

    /** Month dividers and small structural labels, set in capitals by the caller. */
    val overline = publicSans(11f, FontWeight.Bold, letterSpacing = 0.09f)

    val wordmark = publicSans(11.5f, FontWeight.ExtraBold, letterSpacing = 0.2f)

    /** The full-width primary action. */
    val actionLarge = publicSans(15.5f, FontWeight.Bold)

    /** Pill actions. */
    val action = publicSans(14f, FontWeight.Bold)

    /** Text actions, secondary actions, chips' neighbours. */
    val actionSmall = publicSans(13f, FontWeight.Bold)

    val chip = publicSans(12.5f, FontWeight.Bold)

    val navigationLabel = publicSans(10.5f, FontWeight.SemiBold)

    /** Days until an occasion; the caps unit under it is [numberUnit]. */
    val number = lora(23f, FontWeight.Bold, lineHeight = 1f)
    val numberUnit = publicSans(8.5f, FontWeight.Bold, letterSpacing = 0.1f)

    val handwriting = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Medium, fontSize = 21.sp)
}

private val base = Typography()

private fun TextStyle.lora(weight: FontWeight) = copy(fontFamily = Lora, fontWeight = weight)

private fun TextStyle.publicSans(weight: FontWeight = FontWeight.Normal) =
    copy(fontFamily = PublicSans, fontWeight = weight)

/**
 * Material slots, so existing screens and unstyled Material components pick the approved faces:
 * display and headline roles are Lora 700, everything functional is Public Sans.
 */
internal val GiftiaryTypography = Typography(
    displayLarge = base.displayLarge.lora(FontWeight.Bold),
    displayMedium = base.displayMedium.lora(FontWeight.Bold),
    displaySmall = base.displaySmall.lora(FontWeight.Bold),
    headlineLarge = base.headlineLarge.lora(FontWeight.Bold),
    headlineMedium = base.headlineMedium.lora(FontWeight.Bold),
    headlineSmall = base.headlineSmall.lora(FontWeight.Bold),
    titleLarge = base.titleLarge.publicSans(FontWeight.Bold),
    titleMedium = base.titleMedium.publicSans(FontWeight.SemiBold),
    titleSmall = base.titleSmall.publicSans(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.publicSans(),
    bodyMedium = base.bodyMedium.publicSans(),
    bodySmall = base.bodySmall.publicSans(),
    labelLarge = base.labelLarge.publicSans(FontWeight.Bold),
    labelMedium = base.labelMedium.publicSans(FontWeight.SemiBold),
    labelSmall = base.labelSmall.publicSans(FontWeight.SemiBold),
)

package app.giftiary.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.giftiary.R

// Both fonts are bundled variable fonts; each weight selects a point on the wght axis.
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Editorial voice: screen headings and empty-state titles. */
val Lora = FontFamily(
    variableFont(R.font.lora, FontWeight.Normal),
    variableFont(R.font.lora, FontWeight.Medium),
    variableFont(R.font.lora, FontWeight.SemiBold),
)

/** Functional voice: body, labels, buttons, inputs. */
val PublicSans = FontFamily(
    variableFont(R.font.public_sans, FontWeight.Normal),
    variableFont(R.font.public_sans, FontWeight.Medium),
    variableFont(R.font.public_sans, FontWeight.SemiBold),
)

private val base = Typography()

private fun TextStyle.lora(weight: FontWeight = FontWeight.Medium) = copy(fontFamily = Lora, fontWeight = weight)

private fun TextStyle.publicSans(weight: FontWeight = FontWeight.Normal) =
    copy(fontFamily = PublicSans, fontWeight = weight)

/**
 * Display and headline roles are Lora; everything functional (titles, body, labels) is Public Sans.
 */
internal val GiftiaryTypography = Typography(
    displayLarge = base.displayLarge.lora(FontWeight.Normal),
    displayMedium = base.displayMedium.lora(FontWeight.Normal),
    displaySmall = base.displaySmall.lora(),
    headlineLarge = base.headlineLarge.lora(),
    headlineMedium = base.headlineMedium.lora(),
    headlineSmall = base.headlineSmall.lora(),
    titleLarge = base.titleLarge.publicSans(FontWeight.SemiBold),
    titleMedium = base.titleMedium.publicSans(FontWeight.SemiBold),
    titleSmall = base.titleSmall.publicSans(FontWeight.Medium),
    bodyLarge = base.bodyLarge.publicSans(),
    bodyMedium = base.bodyMedium.publicSans(),
    bodySmall = base.bodySmall.publicSans(),
    labelLarge = base.labelLarge.publicSans(FontWeight.SemiBold),
    labelMedium = base.labelMedium.publicSans(FontWeight.Medium),
    labelSmall = base.labelSmall.publicSans(FontWeight.Medium),
)

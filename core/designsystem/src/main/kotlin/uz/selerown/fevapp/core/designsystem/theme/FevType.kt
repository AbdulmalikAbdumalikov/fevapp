package uz.selerown.fevapp.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import uz.selerown.fevapp.core.designsystem.R

/** Manrope variable font, bundled in res/font (no runtime downloads). */
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private val base = Typography()

private fun TextStyle.manrope() = copy(fontFamily = Manrope)

/** Material 3 type scale with Manrope everywhere. */
internal val FevTypography = Typography(
    displayLarge = base.displayLarge.manrope(),
    displayMedium = base.displayMedium.manrope(),
    displaySmall = base.displaySmall.manrope(),
    headlineLarge = base.headlineLarge.manrope().copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.manrope().copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.manrope().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.manrope().copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.manrope().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.manrope().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.manrope(),
    bodyMedium = base.bodyMedium.manrope(),
    bodySmall = base.bodySmall.manrope(),
    labelLarge = base.labelLarge.manrope().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.manrope().copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.manrope().copy(fontWeight = FontWeight.Medium),
)

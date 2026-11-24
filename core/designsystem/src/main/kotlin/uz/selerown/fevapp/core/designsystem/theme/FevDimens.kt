package uz.selerown.fevapp.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Sizes and shapes from docs/DESIGN.md. */
object FevDimens {
    /** Minimum touch target for every control (accessibility). */
    val MinTouch = 48.dp

    val RadiusCard = 14.dp
    val RadiusButton = 14.dp
    val RadiusSmall = 8.dp

    val ScreenPadding = 16.dp

    val CardShape = RoundedCornerShape(RadiusCard)
    val ButtonShape = RoundedCornerShape(RadiusButton)
    val SmallShape = RoundedCornerShape(RadiusSmall)
}

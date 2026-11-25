package uz.selerown.fevapp.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * All colors used by Fevapp. UI code must read colors ONLY from here
 * (via `FevTheme.colors`), never hard-code them, so a light palette can be
 * added later by creating a second instance.
 */
@Immutable
data class FevColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val clipFill: Color,
    val textClipFill: Color,
    val audioClipFill: Color,
    val overlayClipFill: Color,
    val playhead: Color,
)

/** The only palette for now (dark). Values come from docs/DESIGN.md. */
val DarkFevColors = FevColors(
    background = Color(0xFF111214),
    surface = Color(0xFF1C1D21),
    surfaceHigh = Color(0xFF24252A),
    border = Color(0xFF2A2B30),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFFA1A1AA),
    accent = Color(0xFF6EA8FE),
    onAccent = Color(0xFF0B1220),
    danger = Color(0xFFF87171),
    clipFill = Color(0xFF2E3440),
    textClipFill = Color(0xFF4B3B7A),
    audioClipFill = Color(0xFF1E5A45),
    overlayClipFill = Color(0xFF7A4B2E),
    playhead = Color(0xFFF3F4F6),
)

internal val LocalFevColors = staticCompositionLocalOf { DarkFevColors }

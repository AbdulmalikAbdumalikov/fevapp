package uz.selerown.fevapp.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Root theme. Dark only for now; to add a light theme later, create a
 * `LightFevColors` and choose it here.
 */
@Composable
fun FevTheme(content: @Composable () -> Unit) {
    val colors = DarkFevColors
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.accent,
        onSecondary = colors.onAccent,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceHigh,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surfaceHigh,
        surfaceContainerLow = colors.surface,
        outline = colors.border,
        outlineVariant = colors.border,
        error = colors.danger,
    )
    CompositionLocalProvider(LocalFevColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FevTypography,
            shapes = Shapes(
                small = FevDimens.SmallShape,
                medium = FevDimens.ButtonShape,
                large = FevDimens.CardShape,
            ),
            content = content,
        )
    }
}

/** Shortcut: `FevTheme.colors.accent`. */
object FevTheme {
    val colors: FevColors
        @Composable @ReadOnlyComposable get() = LocalFevColors.current
}

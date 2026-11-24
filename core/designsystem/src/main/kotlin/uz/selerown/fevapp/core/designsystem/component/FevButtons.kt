package uz.selerown.fevapp.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.designsystem.theme.FevDimens
import uz.selerown.fevapp.core.designsystem.theme.FevTheme

/** Filled accent button (main action on a screen). */
@Composable
fun FevPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = FevTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = FevDimens.ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = FevDimens.MinTouch),
    ) { Text(text) }
}

/** Outlined neutral button (secondary action). */
@Composable
fun FevSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = FevTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = FevDimens.ButtonShape,
        border = BorderStroke(1.dp, c.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = FevDimens.MinTouch),
    ) { Text(text) }
}

/** 48dp icon button. [contentDescription] is required for screen readers. */
@Composable
fun FevIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = FevTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = c.textPrimary,
            disabledContentColor = c.textSecondary.copy(alpha = 0.4f),
        ),
        modifier = modifier.size(FevDimens.MinTouch),
    ) { Icon(icon, contentDescription = contentDescription) }
}

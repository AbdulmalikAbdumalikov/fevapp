package uz.selerown.fevapp.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.designsystem.theme.FevDimens
import uz.selerown.fevapp.core.designsystem.theme.FevTheme

/**
 * Bottom-toolbar tool: icon ABOVE a text label (never icon-only). Fixed
 * 72 dp wide (long labels wrap to two small lines instead of widening the
 * button) and at least 56 dp tall — still well above the 48 dp target.
 */
@Composable
fun FevToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val colors = FevTheme.colors
    val tint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.4f)
        selected -> colors.accent
        else -> colors.textPrimary
    }
    Column(
        modifier = modifier
            .width(72.dp)
            .sizeIn(minHeight = 56.dp)
            .clip(FevDimens.SmallShape)
            .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

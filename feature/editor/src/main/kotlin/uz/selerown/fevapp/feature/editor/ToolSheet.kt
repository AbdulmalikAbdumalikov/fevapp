package uz.selerown.fevapp.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.designsystem.component.FevToolSheet
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.CanvasFormat

/** Bottom sheet for tools. Format works now; the rest arrive in Phase 2+. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolSheet(
    tool: EditorTool,
    currentFormat: CanvasFormat?,
    onDismiss: () -> Unit,
    onApplyFormat: (CanvasFormat) -> Unit,
) {
    var format by remember { mutableStateOf(currentFormat ?: CanvasFormat.ORIGINAL) }
    FevToolSheet(
        title = stringResource(tool.label),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { if (tool == EditorTool.FORMAT) onApplyFormat(format) else onDismiss() },
    ) {
        when (tool) {
            EditorTool.FORMAT -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                CanvasFormat.entries.forEach { option ->
                    FilterChip(
                        selected = option == format,
                        onClick = { format = option },
                        label = { Text(option.label()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FevTheme.colors.accent,
                            selectedLabelColor = FevTheme.colors.onAccent,
                        ),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            else -> Text(
                stringResource(R.string.sheet_coming_soon),
                style = MaterialTheme.typography.bodyLarge,
                color = FevTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun CanvasFormat.label(): String =
    if (this == CanvasFormat.ORIGINAL) stringResource(R.string.format_original) else "$widthRatio:$heightRatio"

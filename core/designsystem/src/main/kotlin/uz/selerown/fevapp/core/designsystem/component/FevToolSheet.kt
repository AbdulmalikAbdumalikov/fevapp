package uz.selerown.fevapp.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.designsystem.theme.FevTheme

/**
 * Bottom sheet used by every editor tool: title in the middle, close (X) on
 * the left and apply (✓) on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FevToolSheet(
    title: String,
    closeLabel: String,
    applyLabel: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = FevTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.textPrimary,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FevIconButton(Icons.Rounded.Close, closeLabel, onDismiss)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                FevIconButton(Icons.Rounded.Check, applyLabel, onApply)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
        }
    }
}

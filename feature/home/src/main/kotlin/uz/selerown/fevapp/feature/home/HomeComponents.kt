package uz.selerown.fevapp.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import uz.selerown.fevapp.core.common.TimeFormat
import uz.selerown.fevapp.core.designsystem.component.FevIconButton
import uz.selerown.fevapp.core.designsystem.theme.FevDimens
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.ProjectSummary

/** Large "+ Yangi loyiha" card at the top of Home. */
@Composable
internal fun NewProjectCard(enabled: Boolean, onClick: () -> Unit) {
    val c = FevTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(FevDimens.CardShape)
            .background(c.surface)
            .border(1.dp, c.border, FevDimens.CardShape)
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(stringResource(R.string.home_new_project), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.home_new_project_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

/** 9:16 project tile. Tap = open, long-press or ⋮ = options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectCard(
    project: ProjectSummary,
    optionsLabel: String,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
) {
    val c = FevTheme.colors
    Column(
        Modifier
            .clip(FevDimens.CardShape)
            .combinedClickable(onClick = onOpen, onLongClick = onOptions),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(FevDimens.CardShape)
                .background(c.surface),
        ) {
            if (project.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(project.thumbnailUri)
                        .videoFrameMillis(0)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                )
            }
            Text(
                TimeFormat.clock(project.durationUs),
                style = MaterialTheme.typography.labelMedium,
                color = c.textPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(FevDimens.SmallShape)
                    .background(c.background.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                project.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            // Visible alternative to the long-press gesture.
            FevIconButton(Icons.Rounded.MoreVert, optionsLabel, onOptions)
        }
    }
}

@Composable
internal fun ProjectMenu(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_menu_rename)) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = onRename,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_menu_duplicate)) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            onClick = onDuplicate,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_menu_delete), color = FevTheme.colors.danger) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = FevTheme.colors.danger) },
            onClick = onDelete,
        )
    }
}

@Composable
internal fun RenameDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_rename_title)) },
        text = { OutlinedTextField(value = text, onValueChange = onTextChange, singleLine = true) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.isNotBlank()) { Text(stringResource(R.string.home_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_action_cancel)) } },
        containerColor = FevTheme.colors.surface,
    )
}

@Composable
internal fun DeleteDialog(projectName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_delete_title)) },
        text = { Text(stringResource(R.string.home_delete_text, projectName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.home_action_delete), color = FevTheme.colors.danger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_action_cancel)) } },
        containerColor = FevTheme.colors.surface,
    )
}
